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

import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.axiom.mime.ContentType;
import org.apache.axis2.context.MessageContext;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientId;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMEEnveloped;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.mail.smime.SMIMEUtil;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.axis2.MessageContextUtils;
import org.holodeckb2b.ebms3.errors.FailedDecryption;
import org.holodeckb2b.ebms3.util.AbstractUserMessageHandler;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.pmode.IEncryptionConfiguration;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.ITradingPartnerConfiguration;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.interfaces.security.ICertificateManager;
import org.holodeckb2b.interfaces.security.SecurityProcessingException;
import org.holodeckb2b.module.HolodeckB2BCore;

/**
 * Is the <i>in_flow</i> handler responsible for the decryption of a received AS2 User Message. As the Holodeck B2B 
 * <i>security provider</i> can only handle SOAP messages secured using WS-Security it cannot be used for decryption 
 * of the AS2 message which is S/MIME based. Therefore this handler uses classes from the <b>BouncyCastle</b> crypto 
 * framework directly to perform the decryption. The private key however is retrieved from the {@link 
 * ICertificateManager} from the installed Holodeck B2B <i>security provider</i>.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class DecryptMessage extends AbstractUserMessageHandler {

	/**
	 * User Messages carrying business data can only be send as request, therefore this handler needs to run only
	 * as responder.
	 */
    @Override
    protected byte inFlows() {
        return IN_FLOW | RESPONDER;
    }

    @Override
    protected InvocationResponse doProcessing(MessageContext mc, IUserMessageEntity userMessage) throws Exception {

        // First check if received message does contain a signed message
        if (!isEncrypted(mc))
            return InvocationResponse.CONTINUE;

        log.debug("Received message is encrypted, get decryption parameters");
        IEncryptionConfiguration decryptionCfg = null;
        final String pmodeId = userMessage.getPModeId();
        if (!Utils.isNullOrEmpty(pmodeId)) {
            log.debug("Get the security configuration for decryption from P-Mode [" + pmodeId + "]");
            final IPMode pmode = HolodeckB2BCoreInterface.getPModeSet().get(pmodeId);
            // Get the security configuration for decryption of the message, as AS2 is push only this is always the
            // Responder's configuration
            final ITradingPartnerConfiguration hb2bPartner = pmode.getResponder();
            decryptionCfg = hb2bPartner != null && hb2bPartner.getSecurityConfiguration() != null ?
                               hb2bPartner.getSecurityConfiguration().getEncryptionConfiguration() : null;
        }
        // Get the keypair of the receiver
        KeyStore.PrivateKeyEntry receiverKeyPair = null;
        if (decryptionCfg != null) {
            log.debug("Get key pair of receiver based on configuration from P-Mode [" + pmodeId + "]");
            receiverKeyPair = HolodeckB2BCoreInterface.getCertificateManager().getKeyPair(
                                                                              decryptionCfg.getKeystoreAlias(),
                                                                              decryptionCfg.getCertificatePassword());
        } 
        
        if (receiverKeyPair == null) {
            log.error("Keypair for receiver is not available. Unable to decrypt the message!");
            MessageContextUtils.addGeneratedError(mc, new FailedDecryption("Encryption configuration error",
                                                                            userMessage.getMessageId()));
            log.debug("Set processing state of message to failed");
            HolodeckB2BCore.getStorageManager().setProcessingState(userMessage, ProcessingState.FAILURE);
            return InvocationResponse.CONTINUE;
        }
        try {
            final MimeBodyPart mimeEnvelope = (MimeBodyPart) mc.getProperty(Constants.MC_MIME_ENVELOPE);
            log.debug("Decrypting the message");
            final MimeBodyPart decryptedData = decrypt(mimeEnvelope, receiverKeyPair);
            log.debug("Successfully decrypted the message, replacing encrypted data with decrypted version");
            mc.setProperty(Constants.MC_WAS_ENCRYPTED, Boolean.TRUE);
            mc.setProperty(Constants.MC_MIME_ENVELOPE, decryptedData);            
            mc.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE,
                                                                     new ContentType(decryptedData.getContentType()));
        } catch (SecurityProcessingException decryptionFailure) {
            log.error("An error occurred during the decryption of the message! Details:\n\t"
                      + Utils.getExceptionTrace(decryptionFailure));
            MessageContextUtils.addGeneratedError(mc, new FailedDecryption("Decryption of message failed",
                                                                            userMessage.getMessageId()));
            log.debug("Set processing state of message to failed");
            HolodeckB2BCore.getStorageManager().setProcessingState(userMessage, ProcessingState.FAILURE);
        }

        return InvocationResponse.CONTINUE;
    }

    /**
	 * Performs the actual decryption of the encrypted MIME part.
	 *
	 * @param encryptedData     The encrypted MIME part
	 * @param receiverKeyPair   The keypair of the receiver
	 * @return                  The decrypted MIME part
	 * @throws SecurityProcessingException  When an error occurs during the decryption of the MIME part
	 */
	private MimeBodyPart decrypt(final MimeBodyPart encryptedData, final KeyStore.PrivateKeyEntry receiverKeyPair)
	                                                                              throws SecurityProcessingException {
	    try {
	        // Parse the MIME body into an SMIME envelope object
	        final SMIMEEnveloped aEnvelope = new SMIMEEnveloped(encryptedData);
	        final RecipientInformation aRecipient = aEnvelope.getRecipientInfos()
	                                                         .get(new JceKeyTransRecipientId(
	                                                             (X509Certificate) receiverKeyPair.getCertificate()));
	        if (aRecipient == null)
	            throw new SecurityProcessingException("Provided keypair does not match to one used for encryption");
	        
	        // try to decrypt the data
	        final byte[] aDecryptedData = aRecipient.getContent(
	        										new JceKeyTransEnvelopedRecipient(receiverKeyPair.getPrivateKey())
	                                                               	.setProvider(BouncyCastleProvider.PROVIDER_NAME));
	        return SMIMEUtil.toMimeBodyPart(aDecryptedData);
	    } catch (CMSException | MessagingException | SMIMEException decryptFailure) {
	        throw new SecurityProcessingException("Decryption failed!", decryptFailure);
	    }
	}

    /**
     * Determines whether the received message is encrypted by checking the Content-Type header.
     *
     * @param mc  The message context
     * @return    <code>true</code> if the Content-Type indicate a encrypted SMIME, <br>
     *            <code>false</code> otherwise
     */
    private boolean isEncrypted(MessageContext mc) {
        final ContentType contentType = (ContentType)
                                                mc.getProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE);
        final String sBaseType = contentType.getMediaType().toString();
        final String smimeType = contentType.getParameter("smime-type");

        return sBaseType.equalsIgnoreCase("application/pkcs7-mime") &&
               smimeType != null && smimeType.equalsIgnoreCase("enveloped-data");
    }
}

