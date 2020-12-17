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
package org.holodeckb2b.as2.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.holodeckb2b.commons.util.Utils;

/**
 * Contains functions to support the handling the cryptographic algorithms used in securing the AS2 messages. Because
 * AS2 messages use S/MIME for message level security ASN.1 OIDs are used in the messages which needs to be mapped to
 * the algorithm names used by Holodeck B2B (and BouncyCastle). 
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class CryptoAlgorithmHelper {
    /**
     * Map of algorithm names as used in P-Mode and JCA to ASN.1 OID
     */
    private static final Map<String, ASN1ObjectIdentifier>  JCA_ASN1_MAP;
    
    static {
        JCA_ASN1_MAP = new HashMap<>();
        /*
        Digest algorithms
        */
        JCA_ASN1_MAP.put("MD5", PKCSObjectIdentifiers.md5);
        JCA_ASN1_MAP.put("SHA-1", OIWObjectIdentifiers.idSHA1);
        JCA_ASN1_MAP.put("SHA-224", NISTObjectIdentifiers.id_sha224);
        JCA_ASN1_MAP.put("SHA-256", NISTObjectIdentifiers.id_sha256);
        JCA_ASN1_MAP.put("SHA-384", NISTObjectIdentifiers.id_sha384);
        JCA_ASN1_MAP.put("SHA-512", NISTObjectIdentifiers.id_sha512);
        /*
        Signature algorithms
        */
        // RSA based signatures (defined in RFC3370)
        JCA_ASN1_MAP.put("MD5WITHRSA", PKCSObjectIdentifiers.md5WithRSAEncryption);
        JCA_ASN1_MAP.put("SHA1WITHRSA", PKCSObjectIdentifiers.sha1WithRSAEncryption);
        JCA_ASN1_MAP.put("SHA256WITHRSA", PKCSObjectIdentifiers.sha256WithRSAEncryption);
        JCA_ASN1_MAP.put("SHA384WITHRSA", PKCSObjectIdentifiers.sha384WithRSAEncryption);
        JCA_ASN1_MAP.put("SHA512WITHRSA", PKCSObjectIdentifiers.sha512WithRSAEncryption);
        // DSA based signatures (defined in RFC3370)
        JCA_ASN1_MAP.put("SHA1WITHDSA", X9ObjectIdentifiers.id_dsa_with_sha1);
        JCA_ASN1_MAP.put("SHA224WITHDSA", NISTObjectIdentifiers.dsa_with_sha224);
        JCA_ASN1_MAP.put("SHA256WITHDSA", NISTObjectIdentifiers.dsa_with_sha256);
        JCA_ASN1_MAP.put("SHA384WITHDSA", NISTObjectIdentifiers.dsa_with_sha384);
        JCA_ASN1_MAP.put("SHA512WITHDSA", NISTObjectIdentifiers.dsa_with_sha512);
        // ECDSA based signatures (defined in RFC5754 which update RFC3370)
        JCA_ASN1_MAP.put("SHA1WITHECDSA", X9ObjectIdentifiers.ecdsa_with_SHA1);
        JCA_ASN1_MAP.put("SHA224WITHECDSA", X9ObjectIdentifiers.ecdsa_with_SHA224);
        JCA_ASN1_MAP.put("SHA256WITHECDSA", X9ObjectIdentifiers.ecdsa_with_SHA256);
        JCA_ASN1_MAP.put("SHA384WITHECDSA", X9ObjectIdentifiers.ecdsa_with_SHA384);
        JCA_ASN1_MAP.put("SHA512WITHECDSA", X9ObjectIdentifiers.ecdsa_with_SHA512);
        
        /*
        Encryption algorithms
        */
        JCA_ASN1_MAP.put("RSA", PKCSObjectIdentifiers.rsaEncryption);
        JCA_ASN1_MAP.put("DSA", X9ObjectIdentifiers.id_dsa);
        
        JCA_ASN1_MAP.put("3DES", PKCSObjectIdentifiers.des_EDE3_CBC);
        JCA_ASN1_MAP.put("RC2", PKCSObjectIdentifiers.RC2_CBC);
        JCA_ASN1_MAP.put("AES128_CBC", NISTObjectIdentifiers.id_aes128_CBC);
        JCA_ASN1_MAP.put("AES192_CBC", NISTObjectIdentifiers.id_aes192_CBC);
        JCA_ASN1_MAP.put("AES256_CBC", NISTObjectIdentifiers.id_aes256_CBC);
        JCA_ASN1_MAP.put("AES128_CCM", NISTObjectIdentifiers.id_aes128_CCM);
        JCA_ASN1_MAP.put("AES192_CCM", NISTObjectIdentifiers.id_aes192_CCM);
        JCA_ASN1_MAP.put("AES256_CCM", NISTObjectIdentifiers.id_aes256_CCM);
        JCA_ASN1_MAP.put("AES128_GCM", NISTObjectIdentifiers.id_aes128_GCM);
        JCA_ASN1_MAP.put("AES192_GCM", NISTObjectIdentifiers.id_aes192_GCM);
        JCA_ASN1_MAP.put("AES256_GCM", NISTObjectIdentifiers.id_aes256_GCM);
    }
    
    /**
     * Gets name of the algorithm as used internally, based on the ASN.1 object identifier.
     *
     * @param oid   The ASN.1 OID object
     * @return      The name of the algorithm if a matching value is found, <code>null</code> otherwise
     */
    public static String getName(final ASN1ObjectIdentifier oid) {
        Optional<Map.Entry<String, ASN1ObjectIdentifier>> entry = JCA_ASN1_MAP.entrySet()
                                                                            .parallelStream()
                                                                            .filter(e -> e.getValue().equals(oid))
                                                                            .findFirst();
        return entry.isPresent() ? entry.get().getKey() : null;
    }

    /**
     * Gets name of the algorithm as used internally, based on the ASN.1 object identifier.
     *
     * @param oid   The ASN.1 OID string representation
     * @return      The name of the algorithm if a matching value is found, <code>null</code> otherwise
     */
    public static String getName(final String oid) {
        return CryptoAlgorithmHelper.getName(new ASN1ObjectIdentifier(oid));
    }

    /**
     * Gets the ASN.1 object identifier for the given algorithm
     *
     * @param algName   The name of the algorithm as used internally, i.e. the JCA name
     * @return          ASN.1 object identifier for the given algorithm if supported,<br><code>null</code> otherwise
     */
    public static ASN1ObjectIdentifier getOID(final String algName) {
        return JCA_ASN1_MAP.get(algName);
    }

    /**
     * Gets the composed signature name, e.g. "SHA256WITHRSA", based on the digest and encryption algorithm used for
     * creating the signature.
     *
     * @param digestOID         String containing the ASN.1 OID of the digest algorithm used by the signature
     * @param encryptionOID     String containing the ASN.1 OID of the encryption algorithm used by the signature
     * @return
     */
    public static String getSigningAlgName(final String digestOID, final String encryptionOID) {
        return getName(digestOID).replaceAll("-", "") + "WITH" + getName(encryptionOID);
    }

    /**
     * Indicates if the given algorithm is supported by Holodeck B2B.
     *
     * @param algorithm The JCA name of the algorithm to check
     * @return          <code>true</code> if the algorithm is supported, <code>false</code> otherwise.
     */
    public static boolean isSupported(final String algorithm) {
        return JCA_ASN1_MAP.containsKey(ensureJCAName(algorithm));
    }

    /**
     * Gets the name of the default digest algorithm used for the given signature algorithm
     *
     * @param signingAlgorithm      The name of the signature algorithm
     * @return  The name of the default digest algorithm
     */
    public static String getDefaultDigestAlgorithm(final String signingAlgorithm) {
        return CryptoAlgorithmHelper.getName(new DefaultDigestAlgorithmIdentifierFinder()
                                .find(new DefaultSignatureAlgorithmIdentifierFinder().find(signingAlgorithm))
                                .getAlgorithm());
    }

    /**
     * Checks whether the provided names refer to the same digest algorithm.
     *
     * @param name1     The first name
     * @param name2     The second name
     * @return          <code>true</code> if both names are <code>null</code> or if they identify the same algorithm,
     *                  <code>false</code> otherwise
     */
    public static boolean areSameDigestAlgorithm(final String name1, final String name2) {
        return name1 == name2 || name1.replaceAll("-", "").equalsIgnoreCase(name2.replaceAll("-", ""));
    }

    /**
     * Gets the name of the digest algorithm as specified in RFC3851.
     *
     * @param digestAlgorithm   The JCA name of the digest algorithm as used internally
     * @return  The name in the format as specified in RFC3851
     */
    public static String getRFC3851Name(final String digestAlgorithm) {
        if (digestAlgorithm.toLowerCase().startsWith("sha-"))
            return digestAlgorithm.replaceAll("-", "").toLowerCase();
        else
            return digestAlgorithm;
    }
    
    /**
     * Ensures that the digest algorithm name is in JCA format as used in P-Modes and by BouncyCastle.
     * <p>As AS2 is based on RFC3851 the digest algorithm names used are in lower case and don't contain a '-', i.e.
     * "sha224" instead of "SHA-224".
     *
     * @param digestAlgorithm   The digest algorithm name
     * @return  The digest algorithm name in JCA format
     */
    public static String ensureJCAName(final String digestAlgorithm) {
        final String ucName = digestAlgorithm.toUpperCase();
        if (ucName.matches("SHA\\d+"))
            return "SHA-" + ucName.substring(3);
        else
            return ucName;
    }

    /**
     * Checks if the given digest algorithm name is in the format defined in section 3.4.3.2 of RFC5751.  
     * 
     * @param algorithm		The digest algorithm name
     * @return				<code>true</code> if it is a algorithm name as defined in RFC5751,
     * 						<code>false</code> otherwise
     * @since 1.1.0
     */
    public static boolean isRFC5751Name(final String algorithm) {
    	return !Utils.isNullOrEmpty(algorithm) && ( algorithm.equals("md5") || algorithm.matches("sha\\d+"));
    }
}
