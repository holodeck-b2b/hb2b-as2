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

import java.util.List;

/**
 * Represents the parameters for sending a MDN back to the sender of the original messages. In AS2 the MDN is explicitly
 * request by the sender of the original message by providing the parameters for the MDN in the HTTP headers of the
 * message.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class MDNRequestOptions {
    /**
     * Enumerates the signature options for a requested MDN which can be unsigned, optional and required.
     */
    public enum SignatureRequest { unsigned, optional, required };
    /**
     * The signature option specified by the sender of the original message in the <i>signed-receipt-protocol</i>
     * parameter of the <i>Disposition-notification-options</i> [HTTP] header.
     */
    private SignatureRequest   signed;
    /**
     * The signature format specified by the sender of the original message in the <i>signed-receipt-protocol</i>
     * parameter of the <i>Disposition-notification-options</i> [HTTP] header. Although this must always be <i>
     * application/pkcs-7-signature</i> 
     */
    private String signatureFormat;
    /**
     * Includes the list of preferred hashing algorithms to be used for signing the MDN. The first listed algorithm
     * is the most preferred one. This list is provided by the sender of the message to which the MDN is a reply in the
     * <i>signed-receipt-micalg</i> parameter of the <i>Disposition-notification-options</i> [HTTP] header.
     */
    private List<String> hashingAlgorithms;
    /**
     * Is the URL where the MDN should be send to. If <code>null</code> it indicates a synchronous MDN. Provided by the
     * sender of the original message in the "Receipt-Delivery-Option" [HTTP] header.
     */
    private String replyTo;

    /**
     * Gets the indication whether the MDN should or must (not) be signed as requested by the sender of the original
     * message in the <i>signed-receipt-protocol</i> parameter of the <i>Disposition-notification-options</i> [HTTP]
     * header.
     *
     * @return The signature option as requested by the sender of the original message
     */
    public SignatureRequest getSignatureRequest() {
        return signed;
    }

    /**
     * Sets the indication whether the MDN should or must (not) be signed.
     *
     * @param requestedSigning The requested signature option
     */
    public void setSignatureRequest(final SignatureRequest requestedSigning) {
        this.signed = requestedSigning;
    }

    /**
     * Gets the signature format that should be used for the MDN as requested by the sender of the original message
     * in the <i>signed-receipt-protocol</i> parameter of the <i>Disposition-notification-options</i> [HTTP] header.
     *
     * @return The signature format as requested by the sender of the original message
     */
    public String getSignatureFormat() {
    	return signatureFormat;
    }
    
    /**
     * Gets the signature format that should be used for the MDN.
     * 
     * @param signatureFormat	The requested signature format.
     */
    public void setSignatureFormat(final String format) {
    	this.signatureFormat = format;
    }
    
    /**
     * Gets the list of preferred hashing algorithms to beused in the MDN as specified by the sender in the
     * <i>signed-receipt-micalg</i> parameter of the <i>Disposition-notification-options</i> [HTTP] header.
     *
     * @return The list of hashing algorithms in the preferred order with highest preference first
     */
    public List<String> getPreferredHashingAlgorithms() {
        return hashingAlgorithms;
    }

    /**
     * Sets the list of preferred MDN hashing algorithms.
     *
     * @param algorithms The list of hashing algorithms in the preferred order with highest preference first
     */
    public void setPreferredHashingAlgorithms(final List<String> algorithms) {
        this.hashingAlgorithms = algorithms;
    }

    /**
     * Gets the URL where the MDN must be send to in case an asynchronous MDN is requested. This parameter is specified
     * in the <i>Receipt-Delivery-Option</i> [HTTP] header.
     *
     * @return The URL where to sent the async MDN to as specified by the sender. <code>null</code> indicates that a
     *         sync MDN is requested.
     */
    public String getReplyTo() {
        return replyTo;
    }

    /**
     * Sets the URL where the asynchronous MDN should be send to.
     *
     * @param replyTo The URL where to sent the async MDN to as specified by the sender. <code>null</code> to indicate a
     *                sync MDN is requested.
     */
    public void setReplyTo(final String replyTo) {
        this.replyTo = replyTo;
    }
}

