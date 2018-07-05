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
package org.holodeckb2b.as2.messagemodel;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Enumeration;
import java.util.Optional;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeUtility;

import org.apache.axis2.context.MessageContext;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.holodeckb2b.as2.packaging.GenericMessageInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.as2.util.CryptoAlgorithmHelper;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.constants.MessageContextProperties;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.ISecurityConfiguration;
import org.holodeckb2b.interfaces.pmode.ISigningConfiguration;
import org.holodeckb2b.interfaces.pmode.ITradingPartnerConfiguration;
import org.holodeckb2b.interfaces.security.ISignatureProcessingResult;

/**
 * Is a <i>factory<i> class for {@link MDNMetadata} object that will create a new object based on the settings in the 
 * P-Mode of the received message or MDN request as provided by the sender of the message.
 * 
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class MDNMetadataFactory {
	private static final Logger log = LogManager.getLogger(MDNMetadataFactory.class);

	/**
	 * Creates a new {@link MDNMetadata} instance based on the P-Mode of the received message or the MDN request as 
	 * provided by the sender of the message.
	 * <p>If the MDN should be signed, either because configured as such in the P-Mode or by request, a MIC of the 
	 * received message must be included. To calculate the MIC for the received message, either the digest algorithm 
	 * used in the original message, the one specified in the MDN request or if neither has been specified the digest 
	 * algorithm defined in the P-Mode should be used.
	 * 
	 * @return	The meta-data of the MDN for the received message  
	 */
	public static MDNMetadata createMDN(final IPMode pmode, final MDNRequestOptions mdnRequest,
										final MessageContext mc) {
		// First check if the MDN should be signed, based on P-Mode configuration or MDN request
		boolean signedMDN = false;
		final ITradingPartnerConfiguration responderCfg = pmode != null ? pmode.getResponder() : null;
		ISigningConfiguration pModeSignatureCfg = null;
		if (responderCfg != null) {
			final ISecurityConfiguration secConfig = responderCfg.getSecurityConfiguration();
			if (secConfig != null) {
				pModeSignatureCfg = secConfig.getSignatureConfiguration();
				signedMDN = pModeSignatureCfg != null;
			}
		}
		if (!signedMDN && mdnRequest != null) {
			// P-Mode does not support signed Receipts, check if requested by sender
			signedMDN = mdnRequest.getSignatureRequest() != null
						&& mdnRequest.getSignatureRequest() != MDNRequestOptions.SignatureRequest.unsigned;
			if (signedMDN)
				log.warn("Signed MDN requested by sender, but P-Mode does not support signing!");
		}

		String digestAlgorithm = null;
		String base64Digest = null;
		if (signedMDN) {
			log.debug("MDN should be signed => determine digest algorithm to use for MIC");
			ISignatureProcessingResult signatureResult = (ISignatureProcessingResult) 
													 mc.getProperty(MessageContextProperties.SIG_VERIFICATION_RESULT);
			if (signatureResult != null) {
				log.debug("Using digest algorithm from original message's signature");
				digestAlgorithm = signatureResult.getHeaderDigest().getDigestAlgorithm();
			} else {
				if (mdnRequest != null && !Utils.isNullOrEmpty(mdnRequest.getPreferredHashingAlgorithms())) {
					log.debug("Using digest algorithm from MDN reqest");
					Optional<String> supportedAlg = mdnRequest.getPreferredHashingAlgorithms().parallelStream()
							.filter(a -> CryptoAlgorithmHelper.isSupported(a)).findFirst();
					digestAlgorithm = supportedAlg.isPresent() ? supportedAlg.get() : null;
				}
				if (digestAlgorithm == null) {
					log.debug("Getting digest algorithm from P-Mode");
					digestAlgorithm = pModeSignatureCfg != null
							? CryptoAlgorithmHelper.getDefaultDigestAlgorithm(pModeSignatureCfg.getSignatureAlgorithm())
							: null;
				}
			}
			// If a digest algorithm is specified, calculate the MIC of the original message
			if (digestAlgorithm != null) {
				log.debug("Calculate digest of original message using " + digestAlgorithm);
				// Headers must be included in MIC when original message was signed or encrypted
				base64Digest = calculateDigest(digestAlgorithm, (MimeBodyPart) mc.getProperty(Constants.MC_MAIN_MIME_PART),
						signatureResult != null || mc.getProperty(Constants.MC_WAS_ENCRYPTED) != null);
			}
		}

		GenericMessageInfo generalMetaData = (GenericMessageInfo) mc.getProperty(Constants.MC_AS2_GENERAL_DATA);
		return new MDNMetadata(mdnRequest, 
							   generalMetaData.getToPartyId(), 
							   generalMetaData.getFromPartyId(),
							   generalMetaData.getMessageId(), 
							   generalMetaData.getOriginalRecipient(), 
							   Constants.AS2_ADDRESS_TYPE + ";" + generalMetaData.getFromPartyId(),
							   base64Digest, 
							   digestAlgorithm);
	}
	
    /**	
     * Calculates the digest for the given MIME part.}
     * 
     * @param digestAlgorithm	The digest algorithm to use for the calculation
     * @param mainPart			The mime part
     * @param includeHeaders	Indicator whether to include the headers in the digest
     * @return					The Base64 encoded digest
     */
    private static String calculateDigest(final String digestAlgorithm, final MimeBodyPart mainPart,
                                   final boolean includeHeaders) {
        try {
            log.debug("Getting digester from crypto provider");
            final MessageDigest digester = MessageDigest.getInstance(
                                                                 CryptoAlgorithmHelper.ensureJCAName(digestAlgorithm),
                                                                 BouncyCastleProvider.PROVIDER_NAME);
            if (includeHeaders) {
                log.debug("Headers must be included in digest");
                final byte[] CRLF = new byte[] { 13, 10 };
                final Enumeration<?> headers = mainPart.getAllHeaderLines();
                while (headers.hasMoreElements()) {
                    digester.update(convertToBytes((String) headers.nextElement()));
                    digester.update(CRLF);
                }
                // The CRLF separator between header and content
                digester.update(CRLF);
            }

            log.debug("Digest the MIME part content");
            try (final DigestOutputStream digestOS = new DigestOutputStream (new NullOutputStream (), digester);
                 final OutputStream encodedOS = MimeUtility.encode(digestOS, mainPart.getEncoding())) {
                mainPart.getDataHandler().writeTo(encodedOS);
            }

            log.debug("Completed digest calculation, returning Base64 encoded value");
            return Base64.encodeBase64String(digester.digest());
        } catch (NoSuchAlgorithmException | MessagingException | IOException | NoSuchProviderException ex) {
            log.error("An error occurred while calculating the digest of the received message. Details: " +
                      ex.getMessage());
            return null;
		}
    }

    /**
     * Helper method to convert a String to a byte array
     *
     * @param s The String to convert
     * @return  The byte array representation of the string
     */
    private static byte[] convertToBytes(final String s) {
        final char[] chars = s.toCharArray();
        final int N = chars.length;
        final byte[] ret = new byte[N];
        for (int i = 0; i < N; i++)
          ret[i] = (byte) chars[i];
        return ret;
    }
}