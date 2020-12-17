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

import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.messagemodel.MDNMetadata;
import org.holodeckb2b.as2.packaging.MDNInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.handlers.AbstractBaseHandler;
import org.holodeckb2b.common.messagemodel.EbmsError;
import org.holodeckb2b.common.messagemodel.ErrorMessage;
import org.holodeckb2b.commons.util.MessageIdUtils;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.messagemodel.IEbmsError;

/**
 * Is the <i>in_flow</i> handler that converts the (already parsed) MDN into a Error message unit when the MDN is a 
 * negative acknowledgement, i.e contains a disposition modifier or other failures, errors or warnings.
 * 
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class ReadErrorMessage extends AbstractBaseHandler {

    @Override
    protected InvocationResponse doProcessing(IMessageProcessingContext procCtx, Logger log) throws Exception {

        MDNInfo mdn = (MDNInfo) procCtx.getProperty(Constants.CTX_AS2_MDN_DATA);
        // Check if this is a negative MDN to be converted to an Error Message
        if (mdn != null && ( mdn.getModifierSeverity() != null
                            || !Utils.isNullOrEmpty(mdn.getFailures()) || !Utils.isNullOrEmpty(mdn.getErrors())
                            || !Utils.isNullOrEmpty(mdn.getWarnings()))) {
            log.debug("Received MDN is a negative acknowledgement, convert into a ErrorMessage");
            final ErrorMessage errorMsg = new ErrorMessage();
            /* First get the generic meta-data as messageId, timestamp and referenced message. If either the messageId
               or timestamp are not included in the message they will be set now. That these are generated by HB2B will
               be indicated in the MDNMetadataXML object which will automatically set the correct indicators based on
               the availibility of these fields
            */
            errorMsg.setMessageId(MessageIdUtils.stripBrackets(mdn.getMessageId()));
            errorMsg.setTimestamp(mdn.getTimestamp());
            // Because HB2B sends the messageId with brackets they should be part of the reference in the MDN, but
            // need to stripped for further processing
            errorMsg.setRefToMessageId(MessageIdUtils.stripBrackets(mdn.getOrigMessageId()));            
            log.debug("Convert disposition modifier into Error");
            // Now create the first error of the message which will contain the MDN specific fields which cannot be
            // stored in the ErrorMessage itself.
            EbmsError error = new EbmsError();
            error.setErrorCode("AS2:0000");
            error.setOrigin("AS2");
            error.setCategory(mdn.getDispositionType() == MDNInfo.DispositionType.failed ? "Content" : "Processing");
            // The error in the disposition modifier is also included in this first error
            if (mdn.getModifierSeverity() != null)
                error.setSeverity(mdn.getModifierSeverity() == MDNInfo.ModifierSeverity.warning
                                                         ? IEbmsError.Severity.warning : IEbmsError.Severity.failure);
            error.setMessage(mdn.getModifierText());
            // Convert the MDN data to XML representation and store as ErrorDetails
            error.setErrorDetail(new MDNMetadata(mdn).toString());
            errorMsg.addError(error);
            // Now convert the separate errors in the MDN to Errors in the message
            log.debug("Convert individual error messages from MDN into Errors");
            for(String f : mdn.getFailures()) {
                final EbmsError err = new EbmsError();
                error.setErrorCode("AS2:0001");
                err.setOrigin("AS2");
                err.setSeverity(IEbmsError.Severity.failure);
                err.setMessage(f);
                errorMsg.addError(err);
            }
            for(String e : mdn.getErrors()) {
                final EbmsError err = new EbmsError();
                error.setErrorCode("AS2:0001");
                err.setOrigin("AS2");
                err.setSeverity(IEbmsError.Severity.failure);
                err.setMessage(e);
                errorMsg.addError(err);
            }
            for(String w : mdn.getWarnings()) {
                final EbmsError err = new EbmsError();
                error.setErrorCode("AS2:0001");
                err.setOrigin("AS2");
                err.setSeverity(IEbmsError.Severity.warning);
                err.setMessage(w);
                errorMsg.addError(err);
            }
            log.debug("Converted AS2 MDN into Error, storing Error in database");
            procCtx.addReceivedError(HolodeckB2BCore.getStorageManager().storeIncomingMessageUnit(errorMsg));
            log.info("Negative MDN/Error [msgId=" + errorMsg.getMessageId() + "] received for message with id:"
                     + errorMsg.getRefToMessageId());
        }

        return InvocationResponse.CONTINUE;
    }
    
}
