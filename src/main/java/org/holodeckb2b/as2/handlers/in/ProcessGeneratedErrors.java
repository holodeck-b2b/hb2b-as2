/*
 * Copyright (C) 2018 The Holodeck B2B Team, Sander Fieten
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.holodeckb2b.as2.handlers.in;

import java.util.ArrayList;

import org.apache.axis2.context.MessageContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.messagemodel.MDNMetadataFactory;
import org.holodeckb2b.as2.messagemodel.MDNRequestOptions;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.handler.BaseHandler;
import org.holodeckb2b.common.messagemodel.EbmsError;
import org.holodeckb2b.common.messagemodel.ErrorMessage;
import org.holodeckb2b.common.messagemodel.util.MessageUnitUtils;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.axis2.MessageContextUtils;
import org.holodeckb2b.ebms3.constants.MessageContextProperties;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.general.ReplyPattern;
import org.holodeckb2b.interfaces.messagemodel.IEbmsError;
import org.holodeckb2b.interfaces.messagemodel.IErrorMessage;
import org.holodeckb2b.interfaces.messagemodel.IReceipt;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;
import org.holodeckb2b.interfaces.persistency.entities.IErrorMessageEntity;
import org.holodeckb2b.interfaces.persistency.entities.IMessageUnitEntity;
import org.holodeckb2b.interfaces.pmode.IErrorHandling;
import org.holodeckb2b.interfaces.pmode.ILeg.Label;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.module.HolodeckB2BCore;
import org.holodeckb2b.persistency.dao.StorageManager;

/**
 * Is the in flow handler that collects all Errors generated during the processing of the received message. 
 * <p>Depending on the P-Mode settings or the MDN request included in the message, the error is also reported back to
 * the sender. Because in AS2 the incoming message can only contain one message unit all errors refer always to this
 * single message unit and there is no need for grouping as in AS4. Determining however if the error must be reported 
 * is however a bit more complicated because not only the P-Mode must be checked but also the MDN request that can be
 * included with the message. 
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class ProcessGeneratedErrors extends BaseHandler {
    /**
     * Errors will always be logged to a special error log. Using the logging
     * configuration users can decide if this logging should be enabled and
     * how errors should be logged.
     */
    private final Logger     errorLog = LogManager.getLogger("org.holodeckb2b.msgproc.errors.generated.AS2");

	/**
	 * To handle also cases where the other implementation returns a negative MDN with HTTP 400/500 this handler
	 * also runs in the <i>IN_FAULT_FLOW≤/i>
	 */
    @Override
    protected byte inFlows() {
        return IN_FLOW | IN_FAULT_FLOW;
    }

	@Override
	protected InvocationResponse doProcessing(MessageContext mc) throws Exception {
        log.debug("Check if errors were generated");
        @SuppressWarnings("unchecked")
		final ArrayList<IEbmsError> errors = (ArrayList<IEbmsError>)
                                                            mc.getProperty(MessageContextProperties.GENERATED_ERRORS);

        if (Utils.isNullOrEmpty(errors)) {
            log.debug("No errors were generated during this in flow, nothing to do");
        } else {
        	final StorageManager storageManager = HolodeckB2BCore.getStorageManager();        
        	log.debug(errors.size() + " error(s) were generated during this in flow");
        	// Get the message unit that caused the error (may be null if error in header)
        	final IMessageUnitEntity msgInError = MessageContextUtils.getPrimaryMessageUnit(mc);
        	        	
        	// Check if the Error must be reported back to the sender of the message and if it is for a User Message. 
        	// If so it must be communicated as a MDN and we need to prepare the Error Signal as such 
        	// P-Mode is leading, but in case of a User Message the sender's MDN request options are used as fall back         	
        	IPMode pmode = null;
        	MDNRequestOptions mdnRequest = (MDNRequestOptions) mc.getProperty(Constants.MC_AS2_MDN_REQUEST);
        	final String pmodeId = msgInError != null ? msgInError.getPModeId() : null;
        	if (!Utils.isNullOrEmpty(pmodeId)) 
        		pmode = HolodeckB2BCoreInterface.getPModeSet().get(pmodeId);        	
        	final IErrorHandling errHandlingCfg = getErrorHandlingConfiguration(pmode);
        	boolean sendError = false;                    	
        	if (msgInError instanceof IUserMessage) {        		
        		if (errHandlingCfg == null) 
        			log.debug("No error handling configuration in P-Mode, checking message if sender requested MDN");
        		//sendError = (errHandlingCfg != null && errHandlingCfg.getPattern() != null) || mdnRequest != null; 
        		sendError = errHandlingCfg.getPattern() != null || mdnRequest != null; 
                if (sendError) {
                    log.debug("Error must be reported for this message exchange, include MDN data");
                    // Add a first error in the signal containing the MDN meta-data related to the Error Signal                    
                    final EbmsError firstError = new EbmsError();
                    firstError.setErrorCode("AS2:0000");
                    firstError.setOrigin("AS2");
                    firstError.setErrorDetail(MDNMetadataFactory.createMDN(pmode, mdnRequest, mc)
                    											.getAsXML().toString());
                    firstError.setMessage(determineDispositionModifierText(errors));
                    errors.add(0, firstError);
                }
    		} else if (msgInError instanceof IErrorMessage)
                sendError = errHandlingCfg != null && errHandlingCfg.shouldReportErrorOnError() != null ?
                			errHandlingCfg.shouldReportErrorOnError() :
                            HolodeckB2BCore.getConfiguration().shouldReportErrorOnError();
            else if (msgInError instanceof IReceipt)
                sendError = errHandlingCfg != null && errHandlingCfg.shouldReportErrorOnReceipt() != null?
                			errHandlingCfg.shouldReportErrorOnReceipt() :
                            HolodeckB2BCore.getConfiguration().shouldReportErrorOnReceipt();
            
			log.debug("Create and log Error Signal for generated errors");
        	ErrorMessage errorMessage = new ErrorMessage(errors);  
        	// Always log the error
        	if (MessageUnitUtils.isWarning(errorMessage))
        		errorLog.warn(MessageUnitUtils.errorSignalToString(errorMessage));
        	else
        		errorLog.error(MessageUnitUtils.errorSignalToString(errorMessage));        	
        	if (msgInError != null) {
        		// Add reference to the message in error 
        		errorMessage.setRefToMessageId(msgInError.getMessageId());
        		// and set the processing state of the ref'd message to FAILURE
        		log.info("Errors were generated for " + MessageUnitUtils.getMessageUnitName(msgInError) 
        				+ " with msgId=" + msgInError.getMessageId());
        		storageManager.setProcessingState(msgInError, ProcessingState.FAILURE);
        	}        	        	
        	log.debug("Save error message in database");
    		final IErrorMessageEntity storedError = storageManager.storeOutGoingMessageUnit(errorMessage);
    		
            // First check if the Error should be send sync, async or not at all, this can be configured both in P-Mode 
    		// and by sender's request. The latter takes precedence
    		log.debug("Check if errors must be reported");
            // For signals error reporting is optional, so check if error is for a signal and if P-Mode
            // is configured to report errors. Default is not to report errors for signals
			if (sendError) {
                log.debug("Error signal should be reported, check whether it should be reported sync or async");
        		if ((errHandlingCfg != null && errHandlingCfg.getPattern() == ReplyPattern.CALLBACK)
	               || (mdnRequest != null && !Utils.isNullOrEmpty(mdnRequest.getReplyTo()))) {
	                log.debug("The Error should be send back to sender asynchronously");
	                HolodeckB2BCore.getStorageManager().setProcessingState(storedError, ProcessingState.READY_TO_PUSH);
	            } else {
	                log.debug("The Error should be send back to sender synchronously");
	                MessageContextUtils.addErrorSignalToSend(mc, storedError);
	                mc.setProperty(MessageContextProperties.RESPONSE_REQUIRED, true);                
	            } 
        	} else {
                log.debug("Error doesn't need to be sent. Processing completed.");
                storageManager.setProcessingState(storedError, ProcessingState.DONE);
            }
        }
            
        return InvocationResponse.CONTINUE;
	}

	/**
	 * Gets the text to use as disposition modifier in the MDN that should be generated for this set of errors.
	 * <p>In the current implementation there can be at most two errors in the set of errors, which can be the <i>
	 * ProcessingModeMismatch</i> in combination with either the <i>FailedAuthentication</i> or the <i>
	 * ValueInconsistent</i> error. In these cases the <i>ProcessingModeMismatch<i> error will be used to determine
	 * the disposition modifier text.
	 * 
	 * 
	 * @param errors	The errors generated for the received message
	 * @return			The disposition modifier text to use 
	 */
	private String determineDispositionModifierText(ArrayList<IEbmsError> errors) {
		String dispositionText = "Error: unexpected-processing-error";
		
		
		
		return dispositionText;
	}

	/**
	 * Get the P-Mode configuration for handling errors.
	 * 
	 * @param pmode	The P-Mode of the received message
	 * @return	The error handling configuration, or <code>null</code> if the P-Mode doesn't contain such 
	 */
	private IErrorHandling getErrorHandlingConfiguration(final IPMode pmode) {
		try {
			return pmode.getLeg(Label.REQUEST).getUserMessageFlow().getErrorHandlingConfiguration();
		} catch (NullPointerException npe) {
			return null;
		}
	}
}
