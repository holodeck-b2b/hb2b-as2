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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMXMLBuilderFactory;
import org.holodeckb2b.as2.packaging.MDNInfo;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.interfaces.messagemodel.IEbmsError;
import org.holodeckb2b.interfaces.messagemodel.IErrorMessage;
import org.holodeckb2b.interfaces.messagemodel.IReceipt;
import org.holodeckb2b.interfaces.messagemodel.ISignalMessage;

/**
 * Represents the AS2 MDN specific meta-data that needs to be included in the regular {@link IReceipt} and {@link
 * IErrorMessage} objects but which can not be represented in these objects directly. Instances of this class can be
 * serialized to an XML document which can be included in the Signal object. In the receipts the XML can simply
 * be contained as the content, for error messages this information is contained in the first {@link IEbmsError} of 
 * the message. Note that this XML can also used when notifying the Receipt to the business application.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class MDNMetadata {
    /**
     * Namespace URI of the XML schema defining the elements
     */
    private static final String NS_URI = "http://holodeck-b2b.org/schemas/2018/04/as2/mdnmetadata";
    /**
     * QName of the root element
     */
    public static final QName Q_ROOT_ELEMENT_NAME = new QName(NS_URI, "AS2MDN");

    /**  QName of the ReplyParameters element */
    private static final QName Q_REPLY_PARAM = new QName(NS_URI, "ReplyParameters");
    /**  QName of the Signature element */
    private static final QName Q_SIGNATURE = new QName(NS_URI, "Signature");
    /** Name of the attribute on the Signature element that indicates whether signing of the MDN is required **/
    private static final String A_REQUIRED = "required";
    /**  QName of the ReplyTo element */
    private static final QName Q_REPLY_TO = new QName(NS_URI, "ReplyTo");
    /**  QName of the GeneralMessageInfo element */
    private static final QName Q_GENERAL_MSG_INFO = new QName(NS_URI, "GeneralMessageInfo");
    /**  QName of the MessageInfo element */
    private static final QName Q_MESSAGE_INFO = new QName(NS_URI, "MessageInfo");
    /**  QName of the MessageIdInMessage element */
    private static final QName Q_MESSAGEID_IN_MSG = new QName(NS_URI, "MessageIdInMessage");
    /**  QName of the TimestampInMessage element */
    private static final QName Q_TIMESTAMP_IN_MSG = new QName(NS_URI, "TimestampInMessage");
    /**  QName of the RefToOriginalMessageId element */
    private static final QName Q_ORIG_MESSAGE_ID = new QName(NS_URI, "OriginalMessageId");
    /**  QName of the PartyInfo element */
    private static final QName Q_PARTY_INFO = new QName(NS_URI, "PartyInfo");
    /**  QName of the SenderId element */
    private static final QName Q_SENDER_ID = new QName(NS_URI, "SenderId");
    /**  QName of the ReceiverId element */
    private static final QName Q_RECEIVER_ID = new QName(NS_URI, "ReceiverId");
    /**  QName of the Fields element */
    private static final QName Q_FIELDS = new QName(NS_URI, "Fields");
    /**  QName of the Reporting-UA element */
    private static final QName Q_REPORTING_UA = new QName(NS_URI, "Reporting-UA");
    /**  QName of the Original-Recipient element */
    private static final QName Q_ORIG_RECIPIENT = new QName(NS_URI, "Original-Recipient");
    /**  QName of the Final-Recipient element */
    private static final QName Q_FINAL_RECIPIENT = new QName(NS_URI, "Final-Recipient");
    /**  QName of the Disposition-Mode element */
    private static final QName Q_DISPOSITION_MODE = new QName(NS_URI, "Disposition-Mode");
    /**  QName of the Received-Content-MIC element */
    private static final QName Q_RCVD_CONTENT_MIC = new QName(NS_URI, "Received-Content-MIC");
    /**  QName of the Digest element */
    private static final QName Q_DIGEST = new QName(NS_URI, "Digest");
    /**  QName of the HashingAlgorithm element */
    private static final QName Q_HASHING_ALGORITHM = new QName(NS_URI, "HashingAlgorithm");
    /**  QName of the Description element */
    private static final QName Q_DESCRIPTION = new QName(NS_URI, "Description");

    /**
     * The parameters for sending the MDN back to the sender of the original message. As these are not provided in the
     * P-Mode but by the sender in the original message they must be stored in the MDN metda-data.
     */
    private MDNRequestOptions   replyParameters;
    /**
     * Indicates whether the MessageId included in the Signal was included in the AS2 MDN as well or whether it was
     * generated by Holodeck B2B. Applies only to Signals that represent a received MDN.
     */
    private boolean messageIdInMessage = true;
    /**
     * Indicates whether the Timestamp included in the Signal was included in the AS2 MDN as well or whether it was
     * generated by Holodeck B2B. Applies only to Signals that represent a received MDN.
     */
    private boolean timestampInMessage = true;
    /**
     * The messageId value as it was included in the HTTP header that this MDN is a response to.
     */
    private String originalMessageId;
    /**
     * The PartyId of the sender of the Signal. For Signals send by Holodeck B2B this must be derived from the
     * acknowledged message as {@link ISignalMessage}s do not include this information. For received MDNs this
     * identifier is included in the message.
     */
    private String senderId;
    /**
     * The PartyId of the receiver of the Signal. Like the sender this is a derived item for sent Signal and included
     * in received ones.
     */
    private String receiverId;
    /**
     * Represents the Reporting-UA field of the MDN
     */
    private String reportingUA;
    /**
     * Represents the Original-Recipient field of the MDN
     */
    private String origRecipient;
    /**
     * Represents the Final-Recipient field of the MDN
     */
    private String finalRecipient;
    /**
     * Represents the disposition mode of the MDN
     */
    private String dispositionMode;
    /**
     * Represents the base64 encoded digest of the original message as included in the Received-Content-MIC field of the
     * MDN
     */
    private String b64Digest;
    /**
     * Represents the hashing algorithm to calculate the digest of the original message as included in the
     * Received-Content-MIC field of the MDN
     */
    private String hashingAlgorithm;
    /**
     * Represents the human readable part of the MDN.
     */
    private String description;

    /**
     * Creates a new instance with the given values for the MDN meta-data.
     *
     * @param replyParameters   The parameters how to send back the MDN
     * @param senderId          The identifier of the sender of the Receipt
     * @param receiverId        The identifier of the receiver of the Receipt
     * @param originalMessageId The original messageId as included in the HTTP header of the received AS2 message
     * @param origRecipient     The value of the "Original-Recipient" header in the AS2 message
     * @param finalRecipient    The value of the "Final-Recipient" header in the AS2 message
     * @param b64Digest         The base64 encoded digest of the AS2 message
     * @param hashAlgorithm     The hashing algorithm used for calculation of the digest
     */
    public MDNMetadata(final MDNRequestOptions replyParameters, final String senderId, final String receiverId,
                       final String originalMessageId, final String origRecipient, final String finalRecipient,
                       final String b64Digest, final String hashAlgorithm) {
        this.replyParameters = replyParameters;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.originalMessageId = originalMessageId;
        this.origRecipient = origRecipient;
        this.finalRecipient = finalRecipient;
        this.b64Digest = b64Digest;
        this.hashingAlgorithm = hashAlgorithm;
    }

    /**
     * Creates a new instance by parsing the XML content contained in a Signal Message so the
     * MDN data can be retrieved.
     *
     * @param xml   The XML element that is contained in the Signal Message
     */
    public MDNMetadata(final OMElement xml) {
        // Parse the MDN reply parameters
        final OMElement mdnParameters = xml.getFirstChildWithName(Q_REPLY_PARAM);
        if (mdnParameters != null) {
            this.replyParameters = new MDNRequestOptions();
            OMElement signature = mdnParameters.getFirstChildWithName(Q_SIGNATURE);
            if (signature == null)
                this.replyParameters.setSignatureRequest(MDNRequestOptions.SignatureRequest.unsigned);
            else {
                @SuppressWarnings("unchecked")
				Iterator<OMElement> algorithms = mdnParameters.getChildrenWithName(Q_HASHING_ALGORITHM);
                if (!Utils.isNullOrEmpty(algorithms)) {
                    List<String> hashingAlgorithms = new ArrayList<>();
                    while(algorithms.hasNext())
                        hashingAlgorithms.add(algorithms.next().getText());
                    this.replyParameters.setPreferredHashingAlgorithms(hashingAlgorithms);
                }
            }
            this.replyParameters.setReplyTo(mdnParameters.getFirstChildWithName(Q_REPLY_TO).getText());
        }
        // Parse the general message info
        final OMElement generalInfo = xml.getFirstChildWithName(Q_GENERAL_MSG_INFO);
        final OMElement partyInfo = generalInfo.getFirstChildWithName(Q_PARTY_INFO);
        this.senderId = partyInfo.getFirstChildWithName(Q_SENDER_ID).getText();
        this.receiverId = partyInfo.getFirstChildWithName(Q_RECEIVER_ID).getText();
        final OMElement messageInfo = generalInfo.getFirstChildWithName(Q_MESSAGE_INFO);
        if (messageInfo != null) {
            this.messageIdInMessage = Boolean.parseBoolean(messageInfo.getFirstChildWithName(Q_MESSAGEID_IN_MSG)
                                                                                                           .getText());
            this.timestampInMessage = Boolean.parseBoolean(messageInfo.getFirstChildWithName(Q_TIMESTAMP_IN_MSG)
                                                                                                           .getText());
            this.originalMessageId = messageInfo.getFirstChildWithName(Q_ORIG_MESSAGE_ID).getText();
        }
        // Read the MDN specific fields
        final OMElement fields = xml.getFirstChildWithName(Q_FIELDS);
        this.reportingUA = getElementValue(fields, Q_REPORTING_UA);
        this.origRecipient = getElementValue(fields, Q_ORIG_RECIPIENT);
        this.finalRecipient = getElementValue(fields, Q_FINAL_RECIPIENT);
        this.dispositionMode = getElementValue(fields, Q_DISPOSITION_MODE);
        final OMElement contentMIC = fields.getFirstChildWithName(Q_RCVD_CONTENT_MIC);
        if (contentMIC != null) {
            this.b64Digest = getElementValue(contentMIC, Q_DIGEST);
            this.hashingAlgorithm = getElementValue(contentMIC, Q_HASHING_ALGORITHM);
        }
        // Get the human readable text
        this.description = getElementValue(xml, Q_DESCRIPTION);
    }

    /**
     * Creates a new instance by parsing the XML content contained in a Signal Message so the
     * MDN data can be retrieved.
     *
     * @param xmlString    String that conatins the XML contained in the Signal Message
     */
    public MDNMetadata(final String xmlString) {
        // Just use the XML element constructed, but first parse string before
        this(OMXMLBuilderFactory.createOMBuilder(new StringReader(xmlString)).getDocumentElement());
    }

    /**
     * Creates a new instance using the meta-data from the given {@link AS2MDN} object.
     *
     * @param mdn   The AS2 MDN meta-data
     */
    public MDNMetadata(final MDNInfo mdn) {
        this.senderId = mdn.getFromPartyId();
        this.receiverId = mdn.getToPartyId();
        this.messageIdInMessage = !Utils.isNullOrEmpty(mdn.getMessageId());
        this.timestampInMessage = mdn.getTimestamp() != null;
        this.originalMessageId = mdn.getOrigMessageId();
        this.origRecipient = mdn.getOriginalRecipient();
        this.finalRecipient = mdn.getFinalRecipient();
        this.b64Digest = mdn.getBase64Digest();
        this.hashingAlgorithm = mdn.getHashingAlgorithm();
    }

    /**
     * Creates the XML element to include as the content of a {@link IReceipt}.
     *
     * @return {@link OMElement} to be included as content of the Receipt
     */
    public OMElement getAsXML() {
        // Create root AS2MDN element
        final OMFactory f = OMAbstractFactory.getOMFactory();
        final OMElement root = f.createOMElement(Q_ROOT_ELEMENT_NAME);
        root.declareNamespace(NS_URI, "as2mdn");
        // Create the element holding the MDN reply parameters
        if (replyParameters != null) {
            final OMElement mdnParameters = f.createOMElement(Q_REPLY_PARAM, root);
            final List<String> hashingAlgorithms = replyParameters.getPreferredHashingAlgorithms();
            if (replyParameters.getSignatureRequest() != MDNRequestOptions.SignatureRequest.unsigned) {
                final OMElement signature = f.createOMElement(Q_SIGNATURE, mdnParameters);
                if (replyParameters.getSignatureRequest() == MDNRequestOptions.SignatureRequest.required)
                    signature.addAttribute(A_REQUIRED, "true", null);
                if (!Utils.isNullOrEmpty(hashingAlgorithms))
                    hashingAlgorithms.forEach(a -> f.createOMElement(Q_HASHING_ALGORITHM, mdnParameters).setText(a));
            }
            if (!Utils.isNullOrEmpty(replyParameters.getReplyTo()))
                f.createOMElement(Q_REPLY_TO, mdnParameters).setText(replyParameters.getReplyTo());
        }
        // Create GeneralMessageInfo element
        final OMElement generalInfo = f.createOMElement(Q_GENERAL_MSG_INFO, root);
        // Create GeneralMessageInfo/PartyInfo and child elements
        final OMElement partyInfo = f.createOMElement(Q_PARTY_INFO, generalInfo);
        f.createOMElement(Q_SENDER_ID, partyInfo).setText(this.senderId);
        f.createOMElement(Q_RECEIVER_ID, partyInfo).setText(this.receiverId);
        // Create MessageInfo element
        final OMElement messageInfo = f.createOMElement(Q_MESSAGE_INFO, generalInfo);
        f.createOMElement(Q_MESSAGEID_IN_MSG, messageInfo).setText(Boolean.toString(this.messageIdInMessage));
        f.createOMElement(Q_TIMESTAMP_IN_MSG, messageInfo).setText(Boolean.toString(this.timestampInMessage));
        if (!Utils.isNullOrEmpty(this.originalMessageId))
        	f.createOMElement(Q_ORIG_MESSAGE_ID, messageInfo).setText(this.originalMessageId);
        // Create the Fields element
        final OMElement fields = f.createOMElement(Q_FIELDS, root);
        if (!Utils.isNullOrEmpty(this.reportingUA))
            f.createOMElement(Q_REPORTING_UA, fields).setText(this.reportingUA);
        if (!Utils.isNullOrEmpty(this.origRecipient))
            f.createOMElement(Q_ORIG_RECIPIENT, fields).setText(this.origRecipient);
        f.createOMElement(Q_FINAL_RECIPIENT, fields).setText(this.finalRecipient);
        f.createOMElement(Q_DISPOSITION_MODE, fields).setText(this.dispositionMode);
        if (!Utils.isNullOrEmpty(b64Digest)) {
            final OMElement contentMIC = f.createOMElement(Q_RCVD_CONTENT_MIC, fields);
            f.createOMElement(Q_DIGEST, contentMIC).setText(this.b64Digest);
            f.createOMElement(Q_HASHING_ALGORITHM, contentMIC).setText(this.hashingAlgorithm);
        }
        // Create element with the human readable text
        if (!Utils.isNullOrEmpty(this.description))
            f.createOMElement(Q_DESCRIPTION, root).setText(this.description);

        return root;
    }

    /**
     * Gets the XML as a String.
     *
     * @return String containing the XML representation of the MDN meta-data
     */
    @Override
    public String toString() {
        return getAsXML().toString();
    }

    /**
     * Gets the parameters for sending the MDN back to the sender of the original messsage.
     * <p><b>NOTE:</b> The parameters only apply to Signals that represent <b>sent</b> MDNs.
     *
     * @return The parameters for sending the MDN
     */
    public MDNRequestOptions getReplyParameters() {
        return replyParameters;
    }

    /**
     * Gets the indicator whether the <code>MessageId</code> included in the Receipt was available in the received AS2
     * MDN that is represented by this Receipt.
     * <p><b>NOTE:</b> This indicator only applies to Receipts that represent <b>received</b> MDNs as Holodeck B2B will
     * always include this header.
     *
     * @return <code>true</code> if the MessageId is taken from the  "message-id" header of the AS2 MDN,<br>
     *         <code>false</code> otherwise.
     */
    public boolean isMessageIdInMessage() {
        return messageIdInMessage;
    }

    /**
     * Gets the indicator whether the <code>Timestamp</code> included in the Receipt was available in the received AS2
     * MDN that is represented by this Receipt.
     * <p><b>NOTE:</b> This indicator only applies to Receipts that represent <b>received</b> MDNs as Holodeck B2B will
     * always include this header.
     *
     * @return <code>true</code> if the Timestamp is taken from the  "date" header of the AS2 MDN,<br>
     *         <code>false</code> otherwise.
     */
    public boolean isTimestampInMessage() {
        return timestampInMessage;
    }

    /**
     * Gets the messageId value that was included in the HTTP header of the received AS2 message that this Receipt is 
     * a response to.
     * 
     * @return The Original MessageId
     */
    public String getOriginalMessageId() {
        return originalMessageId;
    }

    /**
     * Gets the identifier of the sender of the Receipt.
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Gets the identifier of the receiver of the Receipt.
     */
    public String getReceiverId() {
        return receiverId;
    }

    /**
     * Gets the value of the "Reporting-UA" field describing the system that generated the MDN
     */
    public String getReportingUA() {
        return reportingUA;
    }

    /**
     * Gets the value of the "Original-Recipient" field which includes the address of the receiver of the original AS2
     * message as set by the sender of that message. Although the value of this field should be equal to the value for
     * the "Final-Recipient" it is included for completeness.
     */
    public String getOrigRecipient() {
        return origRecipient;
    }

    /**
     * Gets the value of the "Final-Recipient" field which includes the address of the receiver of the original AS2
     * message as retrieved from the message.
     */
    public String getFinalRecipient() {
        return finalRecipient;
    }

    /**
     * Gets the value for the disposition mode of the MDN that indicates how the MDN was generated and sent. The
     * disposition mode is part of the "Disposition" field of the MDN.
     */
    public String getDispositionMode() {
        return dispositionMode;
    }

    /**
     * Gets the base64 encoded digest of the original message as included in the "Received-Content-MIC" MDN field.
     */
    public String getB64Digest() {
        return b64Digest;
    }

    /**
     * Gets the hashing algorithm used to calculate the digest of the original message as included in the
     * "Received-Content-MIC" MDN field.
     */
    public String getHashingAlgorithm() {
        return hashingAlgorithm;
    }

    /**
     * Gets the human readable description of the MDN as shoudl be included in the first part of the multipart/report
     */
    public String getDescription() {
        return description;
    }

    /**
     * Helper method to read the value from a child element.
     *
     * @param parent    The parent element
     * @param name      QName of the child element to get value of
     * @return          The value of the child element if it exists, or <code>null</code> if there is no such element.
     */
    private String getElementValue(final OMElement parent, final QName name) {
        final OMElement e = parent.getFirstChildWithName(name);
        return (e != null) ? e.getText() : null;
    }
}

