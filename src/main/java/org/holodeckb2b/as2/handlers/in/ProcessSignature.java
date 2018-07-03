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
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.apache.axiom.mime.ContentType;
import org.apache.axis2.context.MessageContext;
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
import org.bouncycastle.util.CollectionStore;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.as2.util.CryptoAlgorithmHelper;
import org.holodeckb2b.as2.util.SignedContentMetadata;
import org.holodeckb2b.common.handler.BaseHandler;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.axis2.MessageContextUtils;
import org.holodeckb2b.ebms3.constants.MessageContextProperties;
import org.holodeckb2b.ebms3.errors.FailedAuthentication;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.persistency.entities.IMessageUnitEntity;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.interfaces.security.ICertificateManager;
import org.holodeckb2b.interfaces.security.ISignatureProcessingResult;
import org.holodeckb2b.interfaces.security.SecurityProcessingException;
import org.holodeckb2b.interfaces.security.X509ReferenceType;
import org.holodeckb2b.module.HolodeckB2BCore;
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
public class ProcessSignature extends BaseHandler {

    @Override
    protected byte inFlows() {
        return IN_FLOW;
    }

    @Override
    protected InvocationResponse doProcessing(MessageContext mc) throws Exception {

        // First check if received message does contain a signed message
        final IMessageUnitEntity msgUnit = MessageContextUtils.getPrimaryMessageUnit(mc);
        final ContentType contentType = (ContentType)
                                                mc.getProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE);
        if (msgUnit == null || !"multipart/signed".equalsIgnoreCase(contentType.getMediaType().toString()))
            return InvocationResponse.CONTINUE;

        ISignatureProcessingResult result = null;
        log.debug("Received message is signed, verify signature");
        final MimeMultipart mimeEnvelope = (MimeMultipart) ((MimeBodyPart) mc.getProperty(Constants.MC_MIME_ENVELOPE))
                                                                             .getContent();
        result = verify(mimeEnvelope, contentType.getParameter("micalg"));
        if (result.isSuccessful()) {
            log.debug("Message signature successfully verified");
            // Store result in message context for creating the Receipt
            mc.setProperty(MessageContextProperties.SIG_VERIFICATION_RESULT, result);
            // We don't need the signature info anymore, so we can replace the Mime Envelope in the message context
            // with only the signed data.
            final MimeBodyPart signedPart = (MimeBodyPart) mimeEnvelope.getBodyPart(0);
            mc.setProperty(Constants.MC_MIME_ENVELOPE, signedPart);
            mc.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE,
                                                                    new ContentType(signedPart.getContentType()));
        } else {
            log.warn("Signature verification failed!");
            MessageContextUtils.addGeneratedError(mc, new FailedAuthentication("Signature validation failed",
                                                                               msgUnit.getMessageId()));
            log.debug("Set processing state of message to failed");
            HolodeckB2BCore.getStorageManager().setProcessingState(msgUnit, ProcessingState.FAILURE);
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
     * @param signedData     The signed MIME multipart
     * @return The result of the signature verification.
     * @throws SecurityProcessingException  When an error occurs during the verification of the signature
     */
    private ISignatureProcessingResult verify(final MimeMultipart signedData, final String mimeMicAlgParameter)
                                                                                  throws SecurityProcessingException {
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
            X509CertificateHolder signingCert = null;
            // Get id of the signer, needed to get the certificate
            SignerId signerID = signatureInfo.getSID();
            final Collection<?> containedCerts = aSignedParser.getCertificates().getMatches(signerID);
            if (!containedCerts.isEmpty()) {
                if (containedCerts.size () > 1)
                    log.warn("Message contains " + containedCerts.size () + " certificates - using the first one!");
                signingCert = (X509CertificateHolder) containedCerts.iterator().next();
                log.debug("Signer's certificate retrieved from message");
                // As the BST reference type also indicates a certificate included in the message, we use it here to
                // indicate an included certificate
                keyReference = X509ReferenceType.BSTReference;
            } else {
                log.debug("Certificate of signer is not included in message, get it from key store");
                try {
                    Collection<X509CertificateHolder> validationCerts = new ArrayList<>();
                    for(X509Certificate c : HolodeckB2BCoreInterface.getCertificateManager()
                                                                    .getValidationCertificates())
                        validationCerts.add(new X509CertificateHolder(c.getEncoded()));

                    CollectionStore certStore = new CollectionStore(validationCerts);
                    final Collection<?> knownCerts = certStore.getMatches(signerID);
                    if (!knownCerts.isEmpty()) {
                        if (knownCerts.size () > 1)
                            log.warn("Found " + containedCerts.size ()
                                     + " certificates in keystore - using the first one!");
                        signingCert = (X509CertificateHolder) knownCerts.iterator().next();
                    }
                    log.debug("Signer's certificate retrieved from local key store");
                    // Check how the certificate was referenced in the message
                    keyReference = signerID.getIssuer() != null && signerID.getSerialNumber() != null ?
                                                  X509ReferenceType.IssuerAndSerial : X509ReferenceType.KeyIdentifier;
                } catch (CertificateEncodingException | IOException ex) {
                    log.error("An error occurred when retrieving Certificate of signer from the key store! Details:"
                             + ex.getMessage());
                }
            }
            if (signingCert == null) {
                log.error("Could not retrieve the certificate used for signed. Unable to verify signature!");
                return new SignatureProcessingResult(new SecurityProcessingException("Certificate unavailable"));
            }
            // When searching the certificate are contained in a X509CertificateHolder which now needs to be converted
            // (back) into a X509Certificate object
            X509Certificate x509Certificate = null;
            try {
                x509Certificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                                                   .getCertificate(signingCert);
            } catch (CertificateException conversionFailed) {
                log.error("An error occurred when converting between certificate formats! Details:"
                            + conversionFailed.getMessage());
                throw new SecurityProcessingException ("Could not convert between certificate formats");
            }
            log.debug("Retrieved signing certificate [Issuer/Serial="
                       + x509Certificate.getIssuerX500Principal().getName() + "/"
                       + x509Certificate.getSerialNumber().toString() + "] from "
                       + (keyReference == X509ReferenceType.BSTReference ? "message" : "key store"));
            try {
                // Check if the certificate is expired or active.
                x509Certificate.checkValidity();
            } catch (CertificateExpiredException | CertificateNotYetValidException ex) {
                log.error("Signing certificate [Issuer/Serial=" + x509Certificate.getIssuerX500Principal().getName()
                            + "/" + x509Certificate.getSerialNumber().toString() + "] is not valid "
                            + (ex instanceof CertificateExpiredException ? "anymore" : "yet"));
                return new SignatureProcessingResult(new SecurityProcessingException("Certificate is not valid "
                                                  + (ex instanceof CertificateExpiredException ? "anymore" : "yet")));
            }
            log.debug("Validating trust of the certificate chain");
            try {
                final CertificateFactory cf = CertificateFactory.getInstance("X.509");
                final CertPath cp = cf.generateCertPath(Collections.singletonList(x509Certificate));
                final Collection<X509Certificate> trustedCerts = HolodeckB2BCore.getCertificateManager()
                																.getValidationCertificates();
                final Set<TrustAnchor> trustedAnchors = new HashSet<>(trustedCerts.size());
                trustedCerts.forEach((c) -> trustedAnchors.add(new TrustAnchor(c, null)));
                PKIXParameters params = new PKIXParameters(trustedAnchors);
                params.setRevocationEnabled(false);
                CertPathValidator.getInstance("PKIX").validate(cp, params);
                log.debug("Successfully validated trust of the certificate chain");
            } catch (CertPathValidatorException untrustedPath) {
                log.error("Signing certificate [Issuer/Serial=" + x509Certificate.getIssuerX500Principal().getName()
                            + "/" + x509Certificate.getSerialNumber().toString()
                            + "] has no valid path to a trusted certificate");
                return new SignatureProcessingResult(new SecurityProcessingException("Certificate is not trusted"));
            } catch (CertificateException | InvalidAlgorithmParameterException | NoSuchAlgorithmException ex) {
                log.error("An error occurred while verifying the certificate chain. Unable to verify signature!"
                            + "\n\tDetails: " + ex.getMessage());
                throw new SecurityProcessingException("Error during certificate path validation", ex);
            }

            log.debug("Verify the signature of the message by comparing digests");
            final SignerInformationVerifier signatureVerifier = new JcaSimpleSignerInfoVerifierBuilder()
                                                                      .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                                                      .build(x509Certificate.getPublicKey());
            final boolean isValid = signatureInfo.verify(signatureVerifier);
            log.debug("Signature completely processed");
            if (isValid) {
                // When reporting the result we use the digest algorithm name from the Content-Type header as it
                // expressed which naming format the sender used
                final SignedContentMetadata signedInfo = new SignedContentMetadata(mimeMicAlgParameter,
                                                                                   signatureInfo.getContentDigest());
                return new SignatureProcessingResult(x509Certificate, keyReference,
                                             CryptoAlgorithmHelper.getSigningAlgName(signatureInfo.getDigestAlgOID(),
                                                                                 signatureInfo.getEncryptionAlgOID()),
                                             signedInfo, null);
            } else {
                return new SignatureProcessingResult(new SecurityProcessingException("Digest mismatch"));
            }
        } catch (CMSException | MessagingException | OperatorCreationException ex) {
            throw new SecurityProcessingException("Signature verification failed!", ex);
        }
    }
}

