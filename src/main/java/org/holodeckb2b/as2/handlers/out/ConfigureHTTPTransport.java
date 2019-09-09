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
package org.holodeckb2b.as2.handlers.out;

import org.apache.axis2.Constants;
import org.apache.axis2.client.Options;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.messagemodel.MDNRequestOptions;
import org.holodeckb2b.as2.packaging.MDNInfo;
import org.holodeckb2b.common.handlers.AbstractBaseHandler;
import org.holodeckb2b.common.messagemodel.util.MessageUnitUtils;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.messagemodel.IReceipt;
import org.holodeckb2b.interfaces.messagemodel.ISignalMessage;
import org.holodeckb2b.interfaces.persistency.PersistenceException;
import org.holodeckb2b.interfaces.persistency.entities.IMessageUnitEntity;
import org.holodeckb2b.interfaces.pmode.ILeg;
import org.holodeckb2b.interfaces.pmode.ILeg.Label;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.IProtocol;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;

/**
 * Is the <i>out_flow</i> handler that configures the actual message transport over the HTTP protocol. When this 
 * message is sent as a HTTP request this handler will find the determine the destination URL based on the P-Mode of
 * the primary message unit. It will also enable <i>HTTP gzip compression</i> and <i>HTTP chunking</i> based on the 
 * P-Mode settings (in <b>PMode[1].Protocol</b>). The actual configuration is done by setting specific {@link Options} 
 * which define the:<ul>
 * <li>Transfer-encoding : When sending messages with large payloads included in the SOAP Body it is useful to compress
 *      the messages during transport. This is done by the standard compression feature of HTTP/1.1 by using the
 *      <i>gzip</i> Transfer-Encoding.<br>
 *      Whether compression should be enabled is configured in the P-Mode that controls the message transfer. Only if
 *      parameter <code>PMode.Protocol.HTTP.Compression</code> has value <code>true</code> compression is enabled.<br>
 *      When compression is enable two options are set to "true": {@see HTTPConstants.#CHUNKED} and
 *      {@see HTTPConstants#MC_GZIP_REQUEST} or {@see HTTPConstants#MC_GZIP_RESPONSE} depending on whether Holodeck B2B
 *      is the initiator or responder in the current message transfer.<br>
 *      That both the chunked and gzip encodings are enabled is a requirement from HTTP
 *      (see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6">http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6</a>).</li>
 * <li>EndpointReference : Defines where the message must be delivered. Only relevant when Holodeck B2B is initiator 
 * 		of the message transfer, if Holodeck B2B is responding to a request received from another MSH, the message is 
 * 		just added in the response. When the message unit to be send is a <i>Receipt</i> or <i>Error</i> the 
 * 		destination URL can beside in the P-Mode also be provided by the Sender of the message in the MDN request.
 * 		If the Sender supplied a URL it will take precedence over the one set in the P-Mode.</li>
 * </ul>
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class ConfigureHTTPTransport extends AbstractBaseHandler {

    @Override
    protected InvocationResponse doProcessing(final IMessageProcessingContext procCtx, Logger log) throws PersistenceException {
        final IMessageUnitEntity primaryMU = procCtx.getPrimaryMessageUnit();
        // Only when message contains a message unit there is something to do
        if (primaryMU != null) {
            log.debug("Get P-Mode configuration for primary MU");
            final IPMode pmode = HolodeckB2BCore.getPModeSet().get(primaryMU.getPModeId());
            // For response error messages the P-Mode may be unknown, so no special HTTP configuration
            if (pmode == null) {
                log.debug("No P-Mode given for primary message unit, using default HTTP configuration");
                return InvocationResponse.CONTINUE;
            }
            // Get current set of options
            final Options options = procCtx.getParentContext().getOptions();
            // AS2 is always a One-Way MEP
            final ILeg leg = pmode.getLeg(Label.REQUEST);

            // If Holodeck B2B is initiator the destination URL must be set
            if (procCtx.isHB2BInitiated()) {
                // Get the destination URL via the P-Mode of this message unit
                String destURL = getDestinationURL(primaryMU, leg, procCtx);
                
                if (Utils.isNullOrEmpty(destURL)) {
                	// No destination URL available, unable to sent this message!
                    log.error("No destination URL availabel for " + MessageUnitUtils.getMessageUnitName(primaryMU) 
                    			+ " with msgId: " + primaryMU.getMessageId());
                    HolodeckB2BCore.getStorageManager().setProcessingState(primaryMU, ProcessingState.FAILURE);
                    return InvocationResponse.ABORT;
                }                
                log.debug("Destination URL=" + destURL);
                procCtx.getParentContext().setProperty(Constants.Configuration.TRANSPORT_URL, destURL);
            }

            // Check if HTTP compression and/or chunking should be used and set options accordingly
            final IProtocol protocolCfg = leg.getProtocol();
            final boolean compress = (protocolCfg != null ? protocolCfg.useHTTPCompression() : false);
            if (compress) {
                log.debug("Enable HTTP compression using gzip Content-Encoding");
                log.debug("Enable gzip content-encoding");
                if (procCtx.isHB2BInitiated())
                    // Holodeck B2B is sending the message, so request has to be compressed
                    options.setProperty(HTTPConstants.MC_GZIP_REQUEST, Boolean.TRUE);
                else
                    // Holodeck B2B is responding the message, so request has to be compressed
                    options.setProperty(HTTPConstants.MC_GZIP_RESPONSE, Boolean.TRUE);
            }

            // Check if HTTP "chunking" should be used. In case of gzip CE, chunked TE is required. But as Axis2 does
            // not automaticly enable this we also enable chunking here when compression is used
            if (compress || (protocolCfg != null ? protocolCfg.useChunking() : false)) {
                log.debug("Enable chunked transfer-encoding");
                options.setProperty(HTTPConstants.CHUNKED, Boolean.TRUE);
            } else {
                log.debug("Disable chunked transfer-encoding");
                options.setProperty(HTTPConstants.CHUNKED, Boolean.FALSE);
            }

            // If the message does not contain any attachments we can disable SwA
            if (procCtx.getParentContext().getAttachmentMap().getContentIDSet().isEmpty()) {
                log.debug("Disable SwA as message does not contain attachments");
                options.setProperty(Constants.Configuration.ENABLE_SWA, Boolean.FALSE);
            }

            log.debug("HTTP configuration done");
        } else
            log.debug("Message does not contain ebMS message unit, nothing to do");

        return InvocationResponse.CONTINUE;
    }

	/**
	 * Gets the destination URL for the given message unit. In case of a Signal Message the URL can be given by the
	 * Sender of the User Message the Signal applies to in the MDN request options.
	 *  
	 * @param msgToSend		The message unit being send
	 * @param leg			The P-Mode configuration parameters for this leg
	 * @param mc			The message processing context
	 * @return				The destination URL, <code>null</code> if URL cannot be determined 
	 */
	private String getDestinationURL(IMessageUnitEntity msgToSend, ILeg leg, IMessageProcessingContext procCtx) {
		String destURL = null;
		
		if (msgToSend instanceof ISignalMessage) {
			final MDNInfo mdn = (MDNInfo) procCtx.getProperty(org.holodeckb2b.as2.util.Constants.CTX_AS2_MDN_DATA);
			MDNRequestOptions mdnRequest = mdn.getMDNRequestOptions();
			destURL = mdnRequest != null ? mdnRequest.getReplyTo() : null;
			if (Utils.isNullOrEmpty(destURL))
	            try {
	                if (msgToSend instanceof IReceipt)
	                     destURL = leg.getReceiptConfiguration().getTo();
	                 else
	                     destURL = leg.getUserMessageFlow().getErrorHandlingConfiguration().getReceiverErrorsTo();
	            } catch (NullPointerException npe) {}
		} else
			destURL = leg.getProtocol() != null ? leg.getProtocol().getAddress() : null;
	
        return destURL;
	}


}
