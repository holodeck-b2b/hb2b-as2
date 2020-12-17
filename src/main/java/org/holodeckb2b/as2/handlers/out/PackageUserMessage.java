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

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.internet.MimeBodyPart;

import org.apache.axiom.mime.ContentType;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.as2.util.CryptoAlgorithmHelper;
import org.holodeckb2b.common.handlers.AbstractUserMessageHandler;
import org.holodeckb2b.commons.util.FileUtils;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.pmode.ILeg.Label;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.IReceiptConfiguration;
import org.holodeckb2b.interfaces.pmode.ISecurityConfiguration;
import org.holodeckb2b.interfaces.pmode.ISigningConfiguration;
import org.holodeckb2b.interfaces.security.SecurityProcessingException;

/**
 * Is the <i>out_flow</i> handler responsible for checking if the message contains a <i>User Message</i> message unit
 * and package its payload as a MIME body part. The User Message to be sent is included in the message processing 
 * context. As the AS2 message can contain only one MIME part (we don't support multiple attachments) the User Message 
 * shall also contain only one payload.
 * <p>This handler also adds the HTTP headers for requesting a MDN from the receiver of the message. This will only
 * be done when the P-Mode of the User Message contains a Receipt Configuration. When this User Message is (to be) 
 * signed also a signed MDN will be requested using the same digest algorithm. An asynchronous Receipt/MDN can be 
 * requested by setting the <b>PMode[1].Receipt.To</b> parameter. Although this parameter can for AS2 only be used
 * when receiving it is now also used for sending.<br>
 * Note that this handler does not add the generic AS2 related HTTP headers to the message as these as generic to any 
 * sent message and therefore added in the {@link AddHeaders} handler.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class PackageUserMessage extends AbstractUserMessageHandler {

    @Override
    protected InvocationResponse doProcessing(final IUserMessageEntity userMessage, 
											  final IMessageProcessingContext procCtx, final Logger log) 
													  												throws Exception {
        // Check that the user message contains just one payload
        if (Utils.isNullOrEmpty(userMessage.getPayloads()) || userMessage.getPayloads().size() > 1) {
            log.error("The user message [msgId=" + userMessage.getMessageId()
                      + "] to be sent does contain no or more than one payload => can not be sent using AS2!");
            return InvocationResponse.ABORT;
        }

        log.debug("Create MIME part to contain payload of the User Message");
        final IPayload payload = userMessage.getPayloads().iterator().next();
        final File payloadFile = new File(payload.getContentLocation());
        MimeBodyPart    mimePart = new MimeBodyPart();
        mimePart.setDataHandler(new DataHandler(new FileDataSource(payloadFile)));
        mimePart.setHeader("Content-Transfer-Encoding", "binary");

        // Use specified MIME type or detect it when none is specified
        String mimeType = payload.getMimeType();
        if (Utils.isNullOrEmpty(mimeType)) {
            log.debug("Detecting MIME type of payload");
            mimeType = FileUtils.detectMimeType(payloadFile);
        }
        log.debug("Setting Content-Type to " + mimeType);
        mimePart.setHeader(HTTPConstants.CONTENT_TYPE, mimeType);

        log.debug("Add MIME part to message");
        procCtx.setProperty(Constants.CTX_MIME_ENVELOPE, mimePart);
        procCtx.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE,
                                                  new ContentType(mimePart.getContentType()).getMediaType().toString());

        log.debug("Check if Receipt is requested");
        final IPMode pmode = HolodeckB2BCoreInterface.getPModeSet().get(userMessage.getPModeId());
        IReceiptConfiguration rcptConfig = pmode.getLeg(Label.REQUEST).getReceiptConfiguration();
        if (rcptConfig != null) {
        	log.debug("A Receipt is requested, adding request for MDN");
        	Map<String, String> mdnReqHeaders = new HashMap<>();
        	// The "disposition-notification-to" header should have an email address value, construct this by
        	// concatenating the sender's partyid and configured HB2B hostname
        	final String senderId = userMessage.getSender().getPartyIds().iterator().next().getId();
        	mdnReqHeaders.put(Constants.MDN_REQUEST_HEADER, senderId + "@" 
        												+ HolodeckB2BCoreInterface.getConfiguration().getHostName());
        	// Check if sync or async Receipt is requested.
        	final String rcptTo = rcptConfig.getTo();
        	if (!Utils.isNullOrEmpty(rcptTo)) {
        		log.debug("Async Receipt request to URL: " + rcptTo);
        		mdnReqHeaders.put(Constants.MDNREQ_REPLY_TO_HEADER, rcptTo);
        	}
        	// Check if this message is to be signed and request MDN accordingly
        	final ISecurityConfiguration senderSecCfg = pmode.getInitiator().getSecurityConfiguration();
        	if (senderSecCfg != null && senderSecCfg.getSignatureConfiguration() != null) {
        		log.debug("Message is signed => request signed MDN");
        		final String mdnOptions = Constants.MDNREQ_SIGNATURE_FORMAT + "=required,pkcs7-signature;"
										  + Constants.MDNREQ_MIC_ALGORITHMS + "=optional," 
										  + getSignatureDigestAlgorithm(senderSecCfg.getSignatureConfiguration());
				mdnReqHeaders.put(Constants.MDNREQ_SIGNING_OPTIONS_HEADER, mdnOptions);				        		
        	}
        	log.debug("Add MDN request headers to the message");
        	procCtx.getParentContext().setProperty(HTTPConstants.HTTP_HEADERS,  mdnReqHeaders);
        }
        
        return InvocationResponse.CONTINUE;
    }
    
    /**
     * Gets the name of digest algorithm to include in the MDN request
     * 
     * @param signatureCfg	The P-Mode signing configuration
     * @return	The digest algorithm name to include in the MDN request
     * @throws SecurityProcessingException	When the configured key pair is not available  
     */
    private String getSignatureDigestAlgorithm(final ISigningConfiguration signatureCfg) throws SecurityProcessingException {    	
    	String signatureAlg = signatureCfg.getSignatureAlgorithm();
		if (Utils.isNullOrEmpty(signatureAlg)) {
			// If no algorithm is specified in the P-Mode, use the default algorithm as used by certificate
			signatureAlg = CryptoAlgorithmHelper.getName(
												((X509Certificate) HolodeckB2BCoreInterface
																	.getCertificateManager()
            														.getKeyPair(signatureCfg.getKeystoreAlias(),
            																	signatureCfg.getCertificatePassword())
            														.getCertificate()).getSigAlgOID());						
		}		
		// Derive digest algorithm from signature algorithm
		String digestAlg = CryptoAlgorithmHelper.getDefaultDigestAlgorithm(signatureAlg);		
		// And check which format should be used, RFC3851 or RFC5751
		if (!"RFC5751".equalsIgnoreCase(signatureCfg.getHashFunction()))
			digestAlg = CryptoAlgorithmHelper.getRFC3851Name(digestAlg);
		
		return digestAlg;    	
    }
}
