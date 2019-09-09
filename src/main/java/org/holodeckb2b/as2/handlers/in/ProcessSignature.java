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

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.security.auth.x500.X500Principal;

import org.apache.axiom.mime.ContentType;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMESignedParser;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.as2.util.CryptoAlgorithmHelper;
import org.holodeckb2b.as2.util.SignedContentMetadata;
import org.holodeckb2b.common.errors.FailedAuthentication;
import org.holodeckb2b.common.events.impl.SignatureVerificationFailure;
import org.holodeckb2b.common.events.impl.SignatureVerified;
import org.holodeckb2b.common.events.impl.SignatureVerifiedWithWarning;
import org.holodeckb2b.common.handlers.AbstractBaseHandler;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.events.security.ISignatureVerified;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.messagemodel.ISignalMessage;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;
import org.holodeckb2b.interfaces.persistency.entities.IMessageUnitEntity;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.interfaces.security.ISignatureProcessingResult;
import org.holodeckb2b.interfaces.security.ISignedPartMetadata;
import org.holodeckb2b.interfaces.security.SecurityProcessingException;
import org.holodeckb2b.interfaces.security.SignatureTrustException;
import org.holodeckb2b.interfaces.security.X509ReferenceType;
import org.holodeckb2b.interfaces.security.trust.ICertificateManager;
import org.holodeckb2b.interfaces.security.trust.IValidationResult;
import org.holodeckb2b.interfaces.security.trust.IValidationResult.Trust;
import org.holodeckb2b.security.results.SignatureProcessingResult;

/**
 * Is the <i>in_flow</i> handler responsible for processing the signature of a received AS2 User Message. As the
 * Holodeck B2B <i>security provider</i> can only handle SOAP messages secured using WS-Security it cannot be used for
 * processing the signed  AS2 message. Therefore this handler uses classes from the <b>BouncyCastle</b> crypto 
 * framework directly to verify the signature. However the {@link ICertificateManager} from the installed Holodeck B2B 
 * <i>security provider</i> is used for trust verification.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class ProcessSignature extends AbstractBaseHandler {

    @Override
    protected InvocationResponse doProcessing(IMessageProcessingContext procCtx, Logger log) throws Exception {

        // First check if received message does contain a signed message
        final IMessageUnitEntity msgUnit = procCtx.getPrimaryMessageUnit();
        final ContentType contentType = (ContentType)
                                            procCtx.getProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE);
        if (msgUnit == null || !"multipart/signed".equalsIgnoreCase(contentType.getMediaType().toString())) {
        	// If the message is not signed, the main part is the current MIME envelope 
        	procCtx.setProperty(Constants.CTX_MAIN_MIME_PART, procCtx.getProperty(Constants.CTX_MIME_ENVELOPE));
            return InvocationResponse.CONTINUE;
        }

        ISignatureProcessingResult result = null;
        log.debug("Received message is signed, verify signature");
        final MimeMultipart mimeEnvelope = (MimeMultipart) 
        											  ((MimeBodyPart) procCtx.getProperty(Constants.CTX_MIME_ENVELOPE))
                                                                             .getContent();
        result = verify(mimeEnvelope, contentType.getParameter("micalg"), log);
        if (result.isSuccessful()) {
            log.debug("Message signature successfully verified");
            // If the processed message is a User Message, the signature result applies to the (single) payload,
            if (msgUnit instanceof IUserMessage) {
            	final HashMap<IPayload, ISignedPartMetadata> signedInfo = new HashMap<>(1);
            	signedInfo.put(((IUserMessage) msgUnit).getPayloads().iterator().next(), result.getHeaderDigest());
            	result = new SignatureProcessingResult(result.getSigningCertificate(), 
            										   result.getTrustValidation(),
            										   result.getCertificateReferenceType(),
            										   result.getSignatureAlgorithm(),
            										   null, signedInfo
            										   );		
            }
            // Store result in message context for creating the Receipt
            procCtx.addSecurityProcessingResult(result);
            // We don't need the signature info anymore, so we can replace the Mime Envelope in the message context
            // with only the signed data.
            final MimeBodyPart signedPart = (MimeBodyPart) mimeEnvelope.getBodyPart(0);
            procCtx.setProperty(Constants.CTX_MIME_ENVELOPE, signedPart);      
            procCtx.setProperty(Constants.CTX_MAIN_MIME_PART, signedPart);                  
            procCtx.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE,
                                                                    new ContentType(signedPart.getContentType()));
            // Raise event to inform external components about the successful verification
            ISignatureVerified event;
            boolean trustWarnings = result.getTrustValidation().getTrust() == Trust.WITH_WARNINGS;                
            if (msgUnit instanceof IUserMessage)                    
            	event = trustWarnings ? new SignatureVerifiedWithWarning((IUserMessage) msgUnit, 
												            			  result.getHeaderDigest(),
												            			  result.getPayloadDigests(),
												            			  result.getTrustValidation())
            						  :  new SignatureVerified((IUserMessage) msgUnit, 
					            								  result.getHeaderDigest(),
					            								  result.getPayloadDigests(),
					            								  result.getTrustValidation());
            else
            	event = trustWarnings ? new SignatureVerifiedWithWarning((ISignalMessage) msgUnit, 
												            			result.getHeaderDigest(),
												            			result.getTrustValidation())
								      :  new SignatureVerified((ISignalMessage) msgUnit, 
													    		  result.getHeaderDigest(),
													    		  result.getTrustValidation());                        
            HolodeckB2BCoreInterface.getEventProcessor().raiseEvent(event);               
        } else {
            log.warn("Signature verification failed!");
            procCtx.addGeneratedError(new FailedAuthentication("Signature validation failed", msgUnit.getMessageId()));
            log.debug("Set processing state of message to failed");
            HolodeckB2BCore.getStorageManager().setProcessingState(msgUnit, ProcessingState.FAILURE);
            // Raise event to inform external components about failure
            HolodeckB2BCoreInterface.getEventProcessor().raiseEvent(
            												new SignatureVerificationFailure(msgUnit, 
            																			 	result.getFailureReason()));               
        }

        return InvocationResponse.CONTINUE;
    }

    /**
     * Performs the actual verification of the signed MIME envelope. The verification process consists of four main
     * steps:<ol>
     * <li>Retrieving the meta-data of the signer of the message</li>
     * <li>Getting the certificate of the signer</li>
     * <li>Validating the trust in the certificate</li>
     * <li>Verifying the signature by comparing digests</li></ol>
     * Although SMIME, which is used for signing the AS2 message, allows for multiple signatures on the message only 
     * the first signature will be processed. In step 2 it is first checked if the certificate is embedded in the 
     * message and only if it isn't the key store with trusted certificates as managed by the Holodeck B2B <i>security 
     * provider</i> is checked.
     *
     * @param signedData     		The signed MIME multipart
     * @param mimeMicAlgParameter	The MIC algorithm as specified in the MIME header
     * @param log					The log to be used
     * @return The result of the signature verification.
     * @throws SecurityProcessingException  When an error occurs during the verification of the signature
     */
    private ISignatureProcessingResult verify(final MimeMultipart signedData, final String mimeMicAlgParameter,
    										  final Logger log) throws SecurityProcessingException {
        try {
            log.debug("Parsing the SMIME envelope");
            // SMIMESignedParser uses "7bit" as the default - AS2 is "binary"
            final SMIMESignedParser aSignedParser = new SMIMESignedParser(
                                                                  new JcaDigestCalculatorProviderBuilder()
                                                                      .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                                                      .build(),
                                                                   signedData, "binary");
            log.debug("Retrieving meta-data on the signer(s) of the message");
            // Get information about the signers of the message. This can be multiple, but for AS2 there should be 
            // just one and we use only the first signer
            final Collection <SignerInformation> signers = aSignedParser.getSignerInfos().getSigners();
            if (Utils.isNullOrEmpty(signers)) {
                log.error("Signed message does not contain information on signers!");
                throw new SecurityProcessingException("Signed message does not contain information on signers!");
            } else if (signers.size() > 1)
                log.warn("Message is signed multiple times, using first signature ignoring others.");

            SignerInformation signatureInfo = signers.iterator().next();

            log.debug("Check digest algorithm used and reported in Content-Type parameter");
            final String digestAlgorithm = CryptoAlgorithmHelper.getName(signatureInfo.getDigestAlgorithmID()
                                                                                                .getAlgorithm());
            if (!CryptoAlgorithmHelper.areSameDigestAlgorithm(digestAlgorithm, mimeMicAlgParameter))  {
                log.error("Difference between digest algorithm used (" + digestAlgorithm
                          + ") and reported in the Content-Type header (" + mimeMicAlgParameter + ")!");
                return new SignatureProcessingResult(new SecurityProcessingException("Invalid S/MIME structure!"));
            }

            log.debug("Getting certificate of the (first) signer");
            X509ReferenceType keyReference = null;
            X509Certificate signingCert = null;
            // Get id of the signer, needed to get the certificate
            SignerId signerID = signatureInfo.getSID();
            final Collection<?> containedCerts = aSignedParser.getCertificates().getMatches(signerID);
            if (!containedCerts.isEmpty()) {
                if (containedCerts.size () > 1)
                    log.warn("Message contains " + containedCerts.size () + " certificates - using the first one!");
                try {
                	signingCert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                                                   .getCertificate((X509CertificateHolder) 
                                                                		   			  containedCerts.iterator().next());
                } catch (CertificateException conversionFailed) {
                    log.error("Certificate included in message is not a X509 Certificate! Details: {}",
                                conversionFailed.getMessage());
                    throw new SecurityProcessingException ("Unsupported certificate used for signing");
                }                
                // As the BST reference type also indicates a certificate included in the message, we use it here to
                // indicate an included certificate
                keyReference = X509ReferenceType.BSTReference;
            } else {
                log.trace("Certificate of signer is not included in message, get it from key store");
                try {
                	if (signerID.getIssuer() != null && signerID.getSerialNumber() != null) {
                		final X500Principal issuer = new X500Principal(signerID.getIssuer().getEncoded("DER"));
                		final BigInteger serial = signerID.getSerialNumber();
                		log.trace("Retrieve signing certificate [Issuer/Serial={}/{}] from Certificate Manager",
                					issuer.toString(), serial.toString());
                		signingCert = HolodeckB2BCoreInterface.getCertificateManager().findCertificate(issuer, serial);
                		keyReference = X509ReferenceType.IssuerAndSerial;
                	} else if (signerID.getSubjectKeyIdentifier() != null) {                	
                		byte[] ski = signerID.getSubjectKeyIdentifier();
                		log.trace("Retrieve signing certificate [SKI={}] from Certificate Manager", 
                					Hex.encodeHexString(ski));
                		signingCert = HolodeckB2BCoreInterface.getCertificateManager().findCertificate(ski);
                		keyReference = X509ReferenceType.KeyIdentifier;                		
                	} else 
                		log.error("Cannot retrieve certificate a no Issuer & SerialNo or SKI available!");
                } catch (IOException ex) {
                    log.error("Error while retrieving Certificate of signer from the Certificate Manager! Details: {}",
                              ex.getMessage());
                }
            }
            
            if (signingCert == null) {
                log.error("Could not retrieve the certificate used for signed. Unable to verify signature!");
                return new SignatureProcessingResult(new SecurityProcessingException("Certificate unavailable"));
            }
            
            log.debug("Retrieved signing certificate [Issuer/Serial="
                       + signingCert.getIssuerX500Principal().getName() + "/"
                       + signingCert.getSerialNumber().toString() + "] from "
                       + (keyReference == X509ReferenceType.BSTReference ? "message" : "Certificate Manager"));

            log.trace("Validate trust in certificate");
            IValidationResult trust = HolodeckB2BCoreInterface.getCertificateManager()
            													.validateTrust(Collections.singletonList(signingCert));
            
            if (trust.getTrust() == Trust.NOK) {
				log.error("Signing certificate is not trusted by Certificate Manager! Details: {}", trust.getMessage());
				return new SignatureProcessingResult(new SignatureTrustException(trust));
            } else if (trust.getTrust() == Trust.WITH_WARNINGS)
            	log.warn("Signing certificate is trusted, but with warnings! Details: {}", trust.getMessage());
            else
            	log.debug("Signing certificate is trusted");
            
            log.debug("Verify message's signature by comparing digests");
            final SignerInformationVerifier signatureVerifier = new JcaSimpleSignerInfoVerifierBuilder()
                                                                      .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                                                      .build(signingCert.getPublicKey());
            final boolean isValid = signatureInfo.verify(signatureVerifier);
            log.debug("Signature completely processed");
            if (isValid) {
                // When reporting the result we use the digest algorithm name from the Content-Type header as it
                // expressed which naming format the sender used
                return new SignatureProcessingResult(signingCert, trust, keyReference,
                                             CryptoAlgorithmHelper.getSigningAlgName(signatureInfo.getDigestAlgOID(),
                                                                                 signatureInfo.getEncryptionAlgOID()),
                                             new SignedContentMetadata(mimeMicAlgParameter,
                                                     				   signatureInfo.getContentDigest()), 
                                             null);
            } else {
                return new SignatureProcessingResult(new SecurityProcessingException("Digest mismatch"));
            }
        } catch (CMSException | MessagingException | OperatorCreationException ex) {
            throw new SecurityProcessingException("Signature verification failed!", ex);
        }
    }
}

