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
package org.holodeckb2b.as2.packaging;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.mail.internet.MailDateFormat;

import org.holodeckb2b.as2.util.MimeDateParser;
import org.holodeckb2b.common.messagemodel.util.MessageUnitUtils;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.interfaces.general.IProperty;
import org.holodeckb2b.interfaces.messagemodel.IMessageUnit;
import org.holodeckb2b.interfaces.messagemodel.ISignalMessage;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;

/**
 * Represents the generic AS2 message meta-data included in the HTTP headers. These headers are either defined directly
 * in the AS2 specification <a href="https://tools.ietf.org/html/rfc4130">[RFC4230]</a> or indirectly through the mail
 * message specification <a href="https://tools.ietf.org/html/rfc5322">[RFC5322]</a>. This class only supports a sub-set
 * of all headers defined in the RFCs which are considered necessary for automated processing.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class GenericMessageInfo {

    /**
     * The messageId of the message as included in the "Message-ID" header
     */
    private String  messageId;
    /**
     * The HTTP header name for the messageId field.
     */
    private static final String MESSAGE_ID_HEADER = "message-id";
    /**
     * The messageId of the message this message is related to and which id is included in the "Original-Message-Id"
     * header
     */
    private String  refToMessageId;
    /**
     * The HTTP header name for the refToMessageId field.
     */
    private static final String REF_TO_MESSAGE_ID_HEADER = "original-message-id";
    /**
     * Timestamp of the message as included in the "Date" field.
     */
    private Date    timestamp;
    /**
     * The HTTP header name for the timestamp field.
     */
    private static final String TIMESTAMP_HEADER = "date";
    /**
     * The subject of the message as indicated in the "Subject" field
     */
    private String  subject;
    /**
     * The HTTP header name for the subject field.
     */
    private static final String SUBJECT_HEADER = "subject";
    /**
     * The identifier of the sending partner as indicated in the "AS2-From" header
     */
    private String  fromPartyId;
    /**
     * The HTTP header name for the sender's id field.
     */
    private static final String SENDER_ID_HEADER = "as2-from";
    /**
     * The identifier of the receiving partner as indicated in the "AS2-To" header
     */
    private String  toPartyId;
    /**
     * The HTTP header name for the receiver's id field.
     */
    private static final String RECEIVER_ID_HEADER = "as2-to";
    /**
     * The address of the receiving partner as specified by the sender and indicated in the "Original-Recipient" header
     */
    private String  originalRecipient;
    /**
     * The HTTP header name for the field specifying the receiver's address as initially provided by the sender.
     */
    private static final String ORIGINAL_RECIPIENT = "original-recipient";
    /**
     * The address of the receiving partner as indicated in the "Final-Recipient" header
     */
    private String  finalRecipient;
    /**
     * The HTTP header name for the field specifying the receiver's address as received.
     */
    private static final String FINAL_RECIPIENT = "final-recipient";

    /**
     * Creates a new instance using the information available in the given set of HTTP headers.
     * Note that if the HTTP header fields are not available the returned object will be "empty", i.e. have none of its
     * data elements set.
     *
     * @param httpHeaders   The set of HTTP headers as name,value pairs
     */
    public GenericMessageInfo(final Map<String, String> httpHeaders) {
        // If there are no HTTP headers, no information is avaible and an "empty" object is created
        if (Utils.isNullOrEmpty(httpHeaders))
            return;

        messageId = httpHeaders.get(MESSAGE_ID_HEADER);
        refToMessageId = httpHeaders.get(REF_TO_MESSAGE_ID_HEADER);
        try {
        	timestamp = new MimeDateParser(httpHeaders.get(TIMESTAMP_HEADER)).parse();
        } catch (NullPointerException | ParseException notaDate) {
            timestamp = null;
        }
        subject = httpHeaders.get(SUBJECT_HEADER);
        fromPartyId = httpHeaders.get(SENDER_ID_HEADER);
        toPartyId = httpHeaders.get(RECEIVER_ID_HEADER);
        originalRecipient = httpHeaders.get(ORIGINAL_RECIPIENT);
        finalRecipient = httpHeaders.get(FINAL_RECIPIENT);
    }

    /**
     * Creates a new instance using the information available in the given <i>User Message</i>.
     * For the identifiers of the sender and receiver of the message the value of the first PartyId (type is ignored) of
     * the sender and receiver of the User Message are used. The subject field is filled with the value of the <i>
     * Message Property</i> named "Subject".
     *
     * @param userMessage   The User Message message unit. Must include information on the sender and receiver of the
     *                      message
     */
    public GenericMessageInfo(final IUserMessage userMessage) {
        this((IMessageUnit) userMessage);

        this.fromPartyId = userMessage.getSender().getPartyIds().iterator().next().getId();
        this.toPartyId = userMessage.getReceiver().getPartyIds().iterator().next().getId();
        // The subject of a message can be specfied using a Message Property with name "Subject"
        IProperty subjectProperty = MessageUnitUtils.getMessageProperty(userMessage, "Subject");
        this.subject = subjectProperty != null ? subjectProperty.getValue() : null;
    }

    /**
     * Creates a new instance using the information available in the given <i>Signal Message</i>.
     * The subject of the MDN is automatically set to <i>"MDN for message" + (message-id | "received in request"</i>)
     * depending on whether a message-id of the message to which this MDN applies is available (it should, but there may
     * be cases it isn't).<br>
     * Because the sender and receiver identifiers of the Signal Message are not included in the signal object itself,
     * but in the contained {@link MDNMetadataXML} object, this info needs to be set later.
     *
     * @param signalMessage     The Signal Message message unit
     */
    public GenericMessageInfo(final ISignalMessage signalMessage) {
        this((IMessageUnit) signalMessage);
        if (!Utils.isNullOrEmpty(refToMessageId))
            this.subject = "MDN for message: " + refToMessageId;
        else
            this.subject = "MDN for message received in request";
    }

    /**
     * Initializes the information that is generic for all types of message units using the information available in the
     * {@link IMessageUnit} interface.
     *
     * @param msgUnit       The message unit
     */
    private GenericMessageInfo(final IMessageUnit msgUnit) {
        if (msgUnit == null)
            throw new IllegalArgumentException("A message unit must be specified");

        this.messageId = msgUnit.getMessageId();
        this.refToMessageId = msgUnit.getRefToMessageId();
        this.timestamp = msgUnit.getTimestamp();
    }

    /**
     * Initializes a new instance using the meta-data from another instance.
     *
     * @param source    The source instance
     */
    protected GenericMessageInfo(final GenericMessageInfo source) {
        this.messageId = source.messageId;
        this.refToMessageId = source.refToMessageId;
        this.timestamp = source.timestamp;
        this.fromPartyId = source.fromPartyId;
        this.toPartyId = source.toPartyId;
        this.subject = source.subject;
    }

    /**
     * Creates a set of HTTP headers containing the generic meta-data of the AS2 message.
     *
     * @return  A <code>Map&lt;String, String&gt;</code> with the headers. If none of meta-data has a value, the Map is
     *          empty.
     */
    public Map<String, String> getAsHTTPHeaders() {
       HashMap<String, String>  headers = new HashMap<>(6);

       if (!Utils.isNullOrEmpty(messageId)) 
    	   // Brackets should be added as stated in spec 
		   headers.put(MESSAGE_ID_HEADER, "<" + messageId + ">");
       if (!Utils.isNullOrEmpty(refToMessageId))
           headers.put(REF_TO_MESSAGE_ID_HEADER, refToMessageId);
       if (timestamp != null)
           headers.put(TIMESTAMP_HEADER, new MailDateFormat().format(timestamp));
       if (!Utils.isNullOrEmpty(fromPartyId))
           headers.put(SENDER_ID_HEADER, fromPartyId);
       if (!Utils.isNullOrEmpty(toPartyId))
           headers.put(RECEIVER_ID_HEADER, toPartyId);
       if (!Utils.isNullOrEmpty(subject))
           headers.put(SUBJECT_HEADER, subject);
       if (!Utils.isNullOrEmpty(originalRecipient))
           headers.put(ORIGINAL_RECIPIENT, originalRecipient);
       if (!Utils.isNullOrEmpty(finalRecipient))
           headers.put(FINAL_RECIPIENT, finalRecipient);

       return headers;
    }

    /**
     * @return the messageId
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * @param messageId the messageId to set
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * @return the refToMessageId
     */
    public String getRefToMessageId() {
        return refToMessageId;
    }

    /**
     * @param refToMessageId the refToMessageId to set
     */
    public void setRefToMessageId(String refToMessageId) {
        this.refToMessageId = refToMessageId;
    }

    /**
     * @return the timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp the timestamp to set
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * @return the subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * @param subject the subject to set
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * @return the fromPartyId
     */
    public String getFromPartyId() {
        return fromPartyId;
    }

    /**
     * @param fromPartyId the fromPartyId to set
     */
    public void setFromPartyId(String fromPartyId) {
        this.fromPartyId = fromPartyId;
    }

    /**
     * @return the toPartyId
     */
    public String getToPartyId() {
        return toPartyId;
    }

    /**
     * @param toPartyId the toPartyId to set
     */
    public void setToPartyId(String toPartyId) {
        this.toPartyId = toPartyId;
    }

    /**
     * @return the originalRecipient
     */
    public String getOriginalRecipient() {
        return originalRecipient;
    }

    /**
     * @param originalRecipient the originalRecipient to set
     */
    public void setOriginalRecipient(String originalRecipient) {
        this.originalRecipient = originalRecipient;
    }

    /**
     * @return the finalRecipient
     */
    public String getFinalRecipient() {
        return finalRecipient;
    }

    /**
     * @param finalRecipient the finalRecipientto set
     */
    public void setFinalRecipient(String finalRecipient) {
        this.finalRecipient = finalRecipient;
    }

}
