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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.ParseException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.axiom.mime.ContentType;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMEException;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.as2.util.CryptoAlgorithmHelper;
import org.holodeckb2b.common.events.impl.EncryptionFailure;
import org.holodeckb2b.common.handlers.AbstractUserMessageHandler;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.pmode.IEncryptionConfiguration;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.ISecurityConfiguration;
import org.holodeckb2b.interfaces.pmode.ITradingPartnerConfiguration;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.interfaces.security.SecurityProcessingException;
import org.holodeckb2b.interfaces.security.X509ReferenceType;

/**
 * Is the <i>out_flow</i> handler responsible for encryption of the AS2 message. It creates the S/MIME package with the
 * encrypted content based on the <b>Responder's</b> encryption configuration from the P-Mode. As the P-Mode settings
 * focus on XML Encryption their usage is changed for encryption of the AS2 message as described below:<ul>
 * <li><code>KeystoreAlias</code> : same use, pointing to the receiver's certificate to be used for encryption.</li>
 * <li><code>KeyTransport</code> : only the <code>KeyReferenceMethod</code> parameter can be used as the others do not
 * apply to SMIME encryption. For the <code>KeyReferenceMethod</code> parameter the allowed values are also limited to
 * <i>IssuerSerial</i> and <i>KeyIdentifier</i> it is not allowed to include the certificate when encrypting.
 * <p>If no value is specified in the P-Mode the <i>Issuer And Serial</i> method will be used. If the <i>
 * SubjectKeyIdentifier</i> is used the certificate MUST include the SKI extension.</li>
 * <li><code>Algorithm</code> : similar use, indicating the algorithm to be used for encryption. The values to be used
 * should be taken from the following list:<ul>
 *      <li>3DES</li>
 *      <li>RC2</li>
 *      <li>AES128_CBC</li>
 *      <li>AES192_CBC</li>
 *      <li>AES256_CBC</li>
 *      <li>AES128_CCM</li>
 *      <li>AES192_CCM</li>
 *      <li>AES256_CCM</li>
 *      <li>AES128_GCM</li>
 *      <li>AES192_GCM</li>
 *      <li>AES256_GCM</li></ul>
 * If there is no algorithm specified in the P-Mode the AES128-GCM algorithm is used. Note however that support for
 * this algorithm is only defined as a SHOULD in RFC3851 (S/MIME 3.1 ref'd by the AS2 RFC).
 * </ul>
 * 
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class EncryptMessage extends AbstractUserMessageHandler {
    /**
     * The default encryption algorithm if nothing is specified in the P-Mode
     */
    private static final String DEFAULT_ALGORITHM = "AES128_GCM";

    @Override
    protected InvocationResponse doProcessing(final IUserMessageEntity userMessage, 
											  final IMessageProcessingContext procCtx, final Logger log) 
													  												throws Exception {
        // Get encryption configuration from P-Mode of the User Message
        IEncryptionConfiguration encryptionCfg = getEncryptionConfiguration(userMessage);

        if (encryptionCfg == null) {
            log.debug("The message doesn't need to be encrypted");
            return InvocationResponse.CONTINUE;
        }
        
        try {
        	// First check that there is content that can be compressed
        	final MimeBodyPart  msgToEncrypt = (MimeBodyPart) procCtx.getProperty(Constants.CTX_MIME_ENVELOPE);
            if (msgToEncrypt == null)
            	return InvocationResponse.CONTINUE;
            
        	// Get the certificate to be used for encryption
            final X509Certificate encryptionCert = HolodeckB2BCoreInterface.getCertificateManager()
                                                                      .getCertificate(encryptionCfg.getKeystoreAlias());

            if (encryptionCert == null) {
                log.error("The configured certificate for encryption is not available!");
                throw new SecurityProcessingException("Certificate for encryption not available");
            }
            final String encryptAlg = !Utils.isNullOrEmpty(encryptionCfg.getAlgorithm()) ? encryptionCfg.getAlgorithm()
                                                                                         : DEFAULT_ALGORITHM;
            if (!CryptoAlgorithmHelper.isSupported(encryptAlg)) {
                log.error("The configured encryption algorithm [" + encryptAlg + "] is not supported!");
                throw new SecurityProcessingException(encryptAlg + " is not supported");
            }

            log.debug("Message will be encrypted using " + encryptAlg
                      + " and certificate [Issuer/Serial=" + encryptionCert.getIssuerX500Principal().getName() + "/"
                      + encryptionCert.getSerialNumber().toString() + "]");

            try {
                // Create the S/MIME generator
                log.debug("Prepare S/MIME generator");
                final SMIMEEnvelopedGenerator smimeGenerator = new SMIMEEnvelopedGenerator();

                // Check if "SubjectKeyIdentifier" Key Reference Method is specified and create the key transport info
                // generator accordingly
                JceKeyTransRecipientInfoGenerator keyInfoGenerator;
                if (encryptionCfg.getKeyTransport() != null &&
                    encryptionCfg.getKeyTransport().getKeyReferenceMethod() == X509ReferenceType.KeyIdentifier) {
                    log.debug("Using SubjectKeyIdentifier as key reference");
                    final PublicKey key = encryptionCert.getPublicKey();
                    final byte[] ski = MessageDigest.getInstance("SHA1").digest(key.getEncoded());
                    keyInfoGenerator = new JceKeyTransRecipientInfoGenerator(ski, key);
                } else {
                    log.debug("Using Issuer And Serial as key reference");
                    keyInfoGenerator = new JceKeyTransRecipientInfoGenerator(encryptionCert);
                }
                smimeGenerator.addRecipientInfoGenerator(keyInfoGenerator.setProvider(new BouncyCastleProvider()));

                log.debug("Encrypting the message");
                final MimeBodyPart encryptedMsg = smimeGenerator.generate(msgToEncrypt,
                                            new JceCMSContentEncryptorBuilder(CryptoAlgorithmHelper.getOID(encryptAlg))
                                                                             .setProvider(new BouncyCastleProvider())
                                                                             .build()
                                                                         );
                log.debug("Message MIME part successfully encrypted, set as new MIME Envelope");
                // Create the MIME body part to include in message context
                procCtx.setProperty(Constants.CTX_MIME_ENVELOPE, encryptedMsg);
                final ContentType contentType = new ContentType(encryptedMsg.getContentType());
                procCtx.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE, contentType);

                log.debug("Completed message encryption succesfully");
            } catch (CertificateEncodingException | ParseException | MessagingException
                    | SMIMEException | NoSuchAlgorithmException | CMSException encryptFailure) {
                log.error("An error occurred while encrypting the message. Error details: "
                         + Utils.getExceptionTrace(encryptFailure));
                throw new SecurityProcessingException("Encryption failed", encryptFailure);
            }

        } catch (SecurityProcessingException encryptionFailed) {
        	// Change the processing state of the message to failure and raise event to inform others
        	HolodeckB2BCore.getStorageManager().setProcessingState(userMessage, ProcessingState.SUSPENDED);
        	HolodeckB2BCore.getEventProcessor().raiseEvent(new EncryptionFailure(userMessage, encryptionFailed));
        	// It makes no sense to continue processing, so abort here
        	return InvocationResponse.ABORT;
        }
        
        return InvocationResponse.CONTINUE;        	
    }

    /**
     * Gets the configuration for encryption of the user message from the P-Mode
     *
     * @param  um   The User Message that is sent and may need to be encrypted
     * @return      The encryption configuration if included in the P-Mode,<br>
     *              <code>null</code> if encryption is not configured
     */
    private IEncryptionConfiguration getEncryptionConfiguration(final IUserMessage um) {
        final IPMode pmode = HolodeckB2BCoreInterface.getPModeSet().get(um.getPModeId());
        if (pmode == null)
            return null;
        else {
            // Get the encryption configuration for the receiving partner
            ITradingPartnerConfiguration receiver = pmode.getResponder();
            ISecurityConfiguration securityCfg = receiver != null ? receiver.getSecurityConfiguration() : null;
            return securityCfg != null ? securityCfg.getEncryptionConfiguration(): null;
        }
    }
}

