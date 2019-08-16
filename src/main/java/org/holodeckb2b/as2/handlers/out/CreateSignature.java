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

import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.apache.axiom.mime.ContentType;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.operator.OperatorCreationException;
import org.holodeckb2b.as2.messagemodel.MDNRequestOptions;
import org.holodeckb2b.as2.packaging.MDNInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.as2.util.CryptoAlgorithmHelper;
import org.holodeckb2b.as2.util.DigestHelper;
import org.holodeckb2b.as2.util.SignedContentMetadata;
import org.holodeckb2b.common.events.impl.SignatureCreated;
import org.holodeckb2b.common.events.impl.SigningFailure;
import org.holodeckb2b.common.handlers.AbstractBaseHandler;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.core.handlers.MessageProcessingContext;
import org.holodeckb2b.core.pmode.PModeUtils;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.messagemodel.IMessageUnit;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.messagemodel.ISignalMessage;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;
import org.holodeckb2b.interfaces.persistency.entities.IMessageUnitEntity;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.ISecurityConfiguration;
import org.holodeckb2b.interfaces.pmode.ISigningConfiguration;
import org.holodeckb2b.interfaces.pmode.ITradingPartnerConfiguration;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.interfaces.security.ISignedPartMetadata;
import org.holodeckb2b.interfaces.security.SecurityProcessingException;
import org.holodeckb2b.interfaces.security.X509ReferenceType;

/**
 * Is the <i>out_flow</i> handler responsible for signing the AS2 message. It creates the S/MIME package with the
 * detached signature based on the signature configuration from the P-Mode. As the P-Mode settings focus on XML
 * Signatures their usage is slightly changed for signing the AS2 message and used as described below:<ul>
 * <li><code>KeystoreAlias</code> : same use, pointing to the private key pair to be used for signing.</li>
 * <li><code>KeyReferenceMethod</code> : is now used to indicate whether the certificate of the signer must be included
 * in the S/MIME signature. The value of this parameter should be set to <i>BSTReference</i> to include the certificate.</li>
 * <li><code>Algorithm</code> : similar use, indicating the algorithm to be used for signing. For AS2 this parameter
 * however includes also the digest algorithm. The values to be used should be taken from the following list:<ul>
 *      <li>MD5WITHRSA</li>
 *      <li>SHA1WITHRSA</li>
 *      <li>SHA256WITHRSA</li>
 *      <li>SHA384WITHRSA</li>
 *      <li>SHA512WITHRSA</li>
 *      <li>SHA1WITHDSA</li>
 *      <li>SHA224WITHDSA</li>
 *      <li>SHA256WITHDSA</li>
 *      <li>SHA384WITHDSA</li>
 *      <li>SHA512WITHDSA</li>
 *      <li>SHA1WITHECDSA</li>
 *      <li>SHA224WITHECDSA</li>
 *      <li>SHA256WITHECDSA</li>
 *      <li>SHA384WITHECDSA</li>
 *      <li>SHA512WITHECDSA</li></ul>
 * If the algorithm is not specified in the P-Mode the algorithm as requested by the sender of the acknowledged message
 * will be used and if that is not specified/supported the algorithm from the certificate will be used. Note that
 * the specified algorithm must be compatible with the public key included in the certificate.
 * <li><code>HashFunction</code> : is now used to indicate how the used hash function should be identified in the S/MIME
 * package and MDN request. Should be set to "RFC5751" to use the RFC5751 algorithm names, otherwise the RFC3851 names 
 * are used as this RFC is ref'd in AS2 spec</li>
 * </ul>
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class CreateSignature extends AbstractBaseHandler {

    @Override
    protected InvocationResponse doProcessing(MessageProcessingContext procCtx, Log log) throws Exception {
    	// First check that there is content that can be signed
    	final MimeBodyPart  msgToSign = (MimeBodyPart) procCtx.getProperty(Constants.CTX_MIME_ENVELOPE);
        if (msgToSign == null)
        	return InvocationResponse.CONTINUE;
        
    	// Get signature configuration from P-Mode of the primary message unit
        final IMessageUnitEntity primaryMsgUnit = procCtx.getPrimaryMessageUnit();
        final ISigningConfiguration signingCfg = getSignatureConfiguration(primaryMsgUnit);

        if (signingCfg != null) {
            log.debug("The message must be signed, getting signature configuration");
            // Get the keypair to be used for signing
            final KeyStore.PrivateKeyEntry signKeyPair = HolodeckB2BCoreInterface.getCertificateManager().getKeyPair(
                                                                                   signingCfg.getKeystoreAlias(),
                                                                                   signingCfg.getCertificatePassword());
            if (signKeyPair == null) {
                log.error("The configured key pair for signing is not available!");
            	// Change the processing state of the message to failure and raise event to inform others                
            	HolodeckB2BCore.getStorageManager().setProcessingState(primaryMsgUnit, ProcessingState.FAILURE);                
                HolodeckB2BCoreInterface.getEventProcessor().raiseEvent(new SigningFailure(primaryMsgUnit,
										new SecurityProcessingException("Private key for signing not available")));                
                return InvocationResponse.ABORT;
            }
            final X509Certificate signingCert = (X509Certificate) signKeyPair.getCertificate();
            log.debug("Certificate used for signing: Issuer/Serial=" + signingCert.getIssuerX500Principal().getName()
                        + "/" + signingCert.getSerialNumber().toString());

            /* Determine the signing algorithm. If this is a MDN, the signature algorithm (or better the digest
               algorithm from which we will derive the signing algorithm) can also be requested by the sender. However
               the P-Mode specified algorithm will take precedence.
            */
            String signatureAlg = signingCfg.getSignatureAlgorithm();
            String requestedAlg = null;
            if (Utils.isNullOrEmpty(signatureAlg) && primaryMsgUnit instanceof ISignalMessage) {
                final MDNInfo mdn = (MDNInfo) procCtx.getProperty(Constants.CTX_AS2_MDN_DATA);
                final MDNRequestOptions mdnRequest = mdn.getMDNRequestOptions();
                if (mdnRequest != null && !Utils.isNullOrEmpty(mdnRequest.getPreferredHashingAlgorithms())) {
                    log.debug("No algorithm specified in the P-Mode, getting the signing algorithm from MDN request");
                    Optional<String> supportedAlg = mdnRequest.getPreferredHashingAlgorithms().parallelStream()
                                                                    .filter(a -> CryptoAlgorithmHelper.isSupported(a))
                                                                    .findFirst();
                    // If a supported digest algorithm is specified, combine with encryption algorithm of the public key
                    if (supportedAlg.isPresent()) {
                    	requestedAlg = supportedAlg.get();                  
                    	signatureAlg =  CryptoAlgorithmHelper.getRFC3851Name(requestedAlg)                    															   
                                                              + "WITH" + signingCert.getPublicKey().getAlgorithm();
                    }
                }
            }
            if (signatureAlg == null) {
                log.debug("No algorithm in P-Mode or in request from sender, use certificate's algorithm");
                signatureAlg = CryptoAlgorithmHelper.getName(signingCert.getSigAlgOID());
            }
            signatureAlg = signatureAlg.toUpperCase();
            log.debug("Signing algorithm to be used " + signatureAlg);

            try {
            	/* Create the S/MIME generator using the RFC3851 or RFC5751 algorithm names. If this is a MDN and the 
            	   MDN request contained a specific digest algorithm, we will use that one.
            	   Otherwise use the setting from the P-Mode or if nothing specified the RFC3851 names 
            	*/            	
                final Map digestAlgoNames = (requestedAlg != null && CryptoAlgorithmHelper.isRFC5751Name(requestedAlg))
                							|| "RFC5751".equalsIgnoreCase(signingCfg.getHashFunction()) ?
                                            SMIMESignedGenerator.RFC5751_MICALGS : SMIMESignedGenerator.RFC3851_MICALGS;
                log.debug("Prepare S/MIME generator");
                final SMIMESignedGenerator smimeGenerator = new SMIMESignedGenerator(digestAlgoNames);

                // Add a SignerInfo generator so the message is signed using the configured key and algorithm. As the
                // digest algorithm is included in the name of the signature algorithm we only use the Signature Algoithm
                // setting from the P-Mode ignoring a setting for the hash algorithm
                smimeGenerator.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder()
												                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
												                    .build(signatureAlg,
												                           signKeyPair.getPrivateKey(), signingCert));

                // We use the BST Key Reference Method as indicator to include the certificate with the signature
                if (signingCfg.getKeyReferenceMethod() == X509ReferenceType.BSTReference) {
                    log.debug("Adding the signing certificate in signature");
                    smimeGenerator.addCertificates(new JcaCertStore(Collections.singletonList(signingCert)));
                }

                log.debug("Signing the message using S/MIME");
                final MimeMultipart signedMultipart = smimeGenerator.generate(msgToSign);
                log.debug("Message MIME part successfully signed, set as MIME Envelope");
                // Create the MIME body part to include in message context
                final MimeBodyPart mimeEnvelope = new MimeBodyPart();
                mimeEnvelope.setContent(signedMultipart);
                mimeEnvelope.setHeader(HTTPConstants.CONTENT_TYPE, signedMultipart.getContentType());
                procCtx.setProperty(Constants.CTX_MIME_ENVELOPE, mimeEnvelope);
                final ContentType contentType = new ContentType(mimeEnvelope.getContentType());
                procCtx.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE, contentType);

                log.debug("Completed message signing succesfully");
                // Raise event to inform external components about the successful signing of the user message
                if (primaryMsgUnit instanceof IUserMessage) {
                	final IUserMessage userMessage = (IUserMessage) primaryMsgUnit;
                	final HashMap<IPayload, ISignedPartMetadata> signedInfo = new HashMap<>();
                	final String digestAlg = CryptoAlgorithmHelper.getDefaultDigestAlgorithm(signatureAlg);
                	signedInfo.put(userMessage.getPayloads().iterator().next(),
                					new SignedContentMetadata(digestAlg, DigestHelper.calculateDigest(digestAlg,
                																					msgToSign, true))
                					);
                	HolodeckB2BCoreInterface.getEventProcessor().raiseEvent(
                													new SignatureCreated(userMessage, signedInfo));
                }
            } catch (CertificateEncodingException | ParseException | MessagingException
                    | SMIMEException | IllegalArgumentException | OperatorCreationException signingFailure) {
                log.error("An error occurred while siging the message. Error details: "
                         + Utils.getExceptionTrace(signingFailure));
            	// Change the processing state of the message to failure and raise event to inform others                
            	HolodeckB2BCore.getStorageManager().setProcessingState(primaryMsgUnit, ProcessingState.FAILURE);                
                HolodeckB2BCoreInterface.getEventProcessor().raiseEvent(new SigningFailure(primaryMsgUnit,
										new SecurityProcessingException("Signing of message failed", signingFailure)));                
                return InvocationResponse.ABORT;
            }
        }

        return InvocationResponse.CONTINUE;
    }

    /**
     * Gets the configuration for signing the message from the P-Mode of the [given] primary message unit.
     *
     * @param primaryMU    The primary message unit
     * @return      The signature configuration if included in the P-Mode of the message unit,<br>
     *              <code>null</code> if signing is not configured
     */
    private ISigningConfiguration getSignatureConfiguration(final IMessageUnit primaryMU) {
        final IPMode pmode = primaryMU == null ? null :
                                                HolodeckB2BCoreInterface.getPModeSet().get(primaryMU.getPModeId());
        if (pmode == null)
            return null;
        else {
            // Get the signature configuration for the partner HB2B represents
            ITradingPartnerConfiguration hb2bPartner = PModeUtils.isHolodeckB2BInitiator(pmode) ? pmode.getInitiator()
                                                                                                : pmode.getResponder();
            ISecurityConfiguration securityCfg = hb2bPartner != null ? hb2bPartner.getSecurityConfiguration() : null;
            return securityCfg != null ? securityCfg.getSignatureConfiguration() : null;
        }
    }
}

