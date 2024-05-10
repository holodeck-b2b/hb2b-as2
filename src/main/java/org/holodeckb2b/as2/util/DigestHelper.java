/**
 *
 */
package org.holodeckb2b.as2.util;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeUtility;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.holodeckb2b.interfaces.security.SecurityProcessingException;

/**
 * Helper class for calculating the digest of a message.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class DigestHelper {

	/**
	 * Calculates the digest for the given MIME part and returns it as base64 encoded string.
	 *
	 * @param digestAlgorithm	The digest algorithm to use for the calculation
	 * @param mimePart			The mime part
	 * @param includeHeaders	Indicator whether to include the headers in the digest
	 * @return					The Base64 encoded digest
	 * @throws SecurityProcessingException If there is a problem that prevents calculation of the digest
	 */
	public static String calculateDigestAsString(final String digestAlgorithm, final MimeBodyPart mimePart,
			final boolean includeHeaders) throws SecurityProcessingException {
		return Base64.encodeBase64String(calculateDigest(digestAlgorithm, mimePart, includeHeaders));
	}

	/**
	 * Calculates the digest for the given MIME part.
	 *
	 * @param digestAlgorithm	The digest algorithm to use for the calculation
	 * @param mimePart			The mime part
	 * @param includeHeaders	Indicator whether to include the headers in the digest
	 * @return					The digest
	 * @throws SecurityProcessingException If there is a problem that prevents calculation of the digest
	 */
	public static byte[] calculateDigest(final String digestAlgorithm, final MimeBodyPart mimePart,
	                               		 final boolean includeHeaders) throws SecurityProcessingException {
	    try {
	        final MessageDigest digester = MessageDigest.getInstance(
	                                                             CryptoAlgorithmHelper.ensureJCAName(digestAlgorithm),
	                                                             BouncyCastleProvider.PROVIDER_NAME);
	        if (includeHeaders) {
	            final byte[] CRLF = new byte[] { 13, 10 };
	            final Enumeration<?> headers = mimePart.getAllHeaderLines();
	            while (headers.hasMoreElements()) {
	                digester.update(convertToBytes((String) headers.nextElement()));
	                digester.update(CRLF);
	            }
	            // The CRLF separator between header and content
	            digester.update(CRLF);
	        }

	        try (final DigestOutputStream digestOS = new DigestOutputStream (NULL_OUTPUT_STREAM, digester);
	             final OutputStream encodedOS = MimeUtility.encode(digestOS, mimePart.getEncoding())) {
	            mimePart.getDataHandler().writeTo(encodedOS);
	        }

	        return digester.digest();
	    } catch (NoSuchAlgorithmException | MessagingException | IOException | NoSuchProviderException ex) {
	        throw new SecurityProcessingException("Could not calculate the digest");
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
