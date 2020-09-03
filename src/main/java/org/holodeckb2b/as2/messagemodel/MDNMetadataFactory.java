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

import java.util.Collection;
import java.util.Optional;

import javax.mail.internet.MimeBodyPart;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.packaging.GenericMessageInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.as2.util.CryptoAlgorithmHelper;
import org.holodeckb2b.as2.util.DigestHelper;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.ISecurityConfiguration;
import org.holodeckb2b.interfaces.pmode.ISigningConfiguration;
import org.holodeckb2b.interfaces.pmode.ITradingPartnerConfiguration;
import org.holodeckb2b.interfaces.security.ISignatureProcessingResult;
import org.holodeckb2b.interfaces.security.SecurityProcessingException;

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
										final IMessageProcessingContext procCtx) {
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
			Collection<ISignatureProcessingResult> signatureResult =  
												procCtx.getSecurityProcessingResults(ISignatureProcessingResult.class);
			if (!Utils.isNullOrEmpty(signatureResult)) {
				log.debug("Using digest algorithm from original message's signature");
				// There is always just one signature and one payload
				digestAlgorithm = signatureResult.iterator().next().getPayloadDigests().values().iterator().next()
																   .getDigestAlgorithm();
			} else {
				if (mdnRequest != null && !Utils.isNullOrEmpty(mdnRequest.getPreferredHashingAlgorithms())) {
					log.debug("Using digest algorithm from MDN reqest");
					Optional<String> supportedAlg = mdnRequest.getPreferredHashingAlgorithms().parallelStream()
							.filter(a -> CryptoAlgorithmHelper.isSupported(a)).findFirst();
					digestAlgorithm = supportedAlg.isPresent() ? supportedAlg.get() : null;
				}
				if (digestAlgorithm == null && pModeSignatureCfg != null 
						&& !Utils.isNullOrEmpty(pModeSignatureCfg.getSignatureAlgorithm())) {
					log.debug("Getting digest algorithm from P-Mode");
					digestAlgorithm = CryptoAlgorithmHelper.getDefaultDigestAlgorithm(
																			pModeSignatureCfg.getSignatureAlgorithm());							
				}
			}
			// If a digest algorithm is specified, calculate the MIC of the original message
			if (digestAlgorithm != null) {
				log.debug("Calculate digest of original message using " + digestAlgorithm);
				// Headers must be included in MIC when original message was signed or encrypted
				try {
					base64Digest = DigestHelper.calculateDigestAsString(digestAlgorithm, 
															(MimeBodyPart) procCtx.getProperty(Constants.CTX_MAIN_MIME_PART),
															signatureResult != null 
																|| procCtx.getProperty(Constants.CTX_WAS_ENCRYPTED) != null);
				} catch (SecurityProcessingException digestError) {
					log.error("Could not calculate the digest for the original message! Error details: {}", 
								digestError.getMessage());
					base64Digest = null;
					digestAlgorithm = null;
				}
			}
		}

		GenericMessageInfo generalMetaData = (GenericMessageInfo) procCtx.getProperty(Constants.CTX_AS2_GENERAL_DATA);
		return new MDNMetadata(mdnRequest, 
							   generalMetaData.getToPartyId(), 
							   generalMetaData.getFromPartyId(),
							   generalMetaData.getMessageId(), 
							   generalMetaData.getOriginalRecipient(), 
							   Constants.AS2_ADDRESS_TYPE + ";" + generalMetaData.getFromPartyId(),
							   base64Digest, 
							   digestAlgorithm);
	}
}
