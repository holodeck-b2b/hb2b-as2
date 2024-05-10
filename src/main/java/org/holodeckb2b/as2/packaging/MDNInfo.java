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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.kernel.http.HTTPConstants;
import org.holodeckb2b.as2.messagemodel.MDNMetadata;
import org.holodeckb2b.as2.messagemodel.MDNRequestOptions;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.messagemodel.IEbmsError;
import org.holodeckb2b.interfaces.messagemodel.IErrorMessage;
import org.holodeckb2b.interfaces.messagemodel.IReceipt;

/**
 * Represents the information available in an AS2 MDN message as defined in <a href=
 * "https://tools.ietf.org/html/rfc4130">[RFC430]</a>. The AS2 MDN itself is again based on the general <i>message
 * disposition notification</i> as specified in <a href="https://tools.ietf.org/html/rfc3798">[RFC3798]</a>.
 * <p>Instances of this class can be created by parsing a Mime part containing a MDN or from a {@link IReceipt} or
 * {@link IErrorMessage}. It can also create the MDN Mime part.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class MDNInfo extends GenericMessageInfo {

    /**
     * MIME type indicating that the human readable part of the MDN is in itself a multi-part that contains alternative
     * presentations of the MDN
     */
    private static final String MIME_MULTIPART_ALTERNATIVE = "multipart/alternative";
    /**
     * MIME type indicating that the human readable part of the created MDN contains plain text
     */
    private static final String MIME_PLAIN_TEXT = "text/plain";
    /**
     * Name of the header containing information on the system that created the MDN
     */
    private static final String HEADER_REPORTING_UA = "Reporting-UA";
    /**
     * Name of the header containing the receiver of the message this MDN applies to
     */
    private static final String HEADER_ORIGINAL_RECIPIENT = "Original-Recipient";
    /**
     * Name of the header containing the final receiver of the message this MDN applies to
     */
    private static final String HEADER_FINAL_RECIPIENT = "Final-Recipient";
    /**
     * Name of the header containing the message-id of the message this MDN applies to
     */
    private static final String HEADER_ORIGINAL_MESSAGE_ID = "Original-Message-ID";
    /**
     * Name of the header containing the details of the MDN
     */
    private static final String HEADER_DISPOSITION = "Disposition";
    /**
     * Name of the header containing the information on errors with severity level <i>failure</i> that occurred while
     * processing the original message.
     */
    private static final String HEADER_FAILURE = "Failure";
    /**
     * Name of the header containing the information on errors with severity level <i>error</i> that occurred while
     * processing the original message.
     */
    private static final String HEADER_ERROR = "Error";
    /**
     * Name of the header containing the information on errors with severity level <i>warning</i> that occurred while
     * processing the original message.
     */
    private static final String HEADER_WARNING = "Warning";
    /**
     * name of the header containing the meta-data on the MIC (Message Integrity Check) of the original message
     */
    private static final String HEADER_RECEIVED_CONTENT_MIC = "Received-Content-MIC";
    /**
     * End-of-line characters to use when constructing the Mime parts
     */
    private static final String EOL = "\r\n";
    /**
     * Enumerates the disposition types that indicate whether the original message was processed successfully or not.
     */
    public enum DispositionType { processed, failed };
    /**
     * Enumerates the severity level that can apply to the error contained in the disposition modifier.
     */
    public enum ModifierSeverity { failure, error, warning };

    /**
     * The (supposedly) human readable text contained in the first part of the MDN
     */
    private String readableText;
    /**
     * The identification of the system sending the MDN
     */
    private String reportingUA;
    /**
     * Identifier of the original recipient (as indicated by the sender) the message this MDN applies to
     */
    private String originalRecipient;
    /**
     * Identifier of the final recipient of the message this MDN applies to
     */
    private String finalRecipient;
    /**
     * The message-id of the message the MDN applies to
     */
    private String origMessageId;
    /**
     * The disposition mode indicating whether the MDN was automatically created and sent or after a manual action.
     */
    private String dispositionMode;
    /**
     * Indication of the type of MDN, i.e. whether it acknowledges successful processing of the original message or not
     */
    private DispositionType dispositionType;
    /**
     * The text description of the disposition modifier
     */
    private String dispositionModifierText;
    /**
     * The severity level of the disposition modifier
     */
    private ModifierSeverity dispositionSeverity;
    /**
     * List of errors with severity <i>failure</i>. Note that the severity levels are not further specified in neither
     * the AS2 nor the MDN specification.
     */
    private List<String> failures = new ArrayList<>();
    /**
     * List of errors with severity <i>error</i>. Note that the severity levels are not further specified in neither
     * the AS2 nor the MDN specification.
     */
    private List<String> errors = new ArrayList<>();
    /**
     * List of errors with severity <i>warning</i>. Note that the severity levels are not further specified in neither
     * the AS2 nor the MDN specification.
     */
    private List<String> warnings = new ArrayList<>();
    /**
     * The digest of the original message
     */
    private String  base64Digest;
    /**
     * The hashing algorithm used to calculate the digest
     */
    private String  hashingAlgorithm;
    /**
     * The parameters for sending the MDN as requested by the sender of the acknowledged message
     */
    private MDNRequestOptions   mdnRequestOptions;

    /**
     * Creates a new instance using the provided general meta-data and parsing the given Mime part for the MDN
     * specific meta-data.
     *
     * @param generalInfo       The general meta-data
     * @param mdnPart           The Mime part containing the MDN
     * @throws MDNTransformationException   When the Mime part could not be parsed, i.e. does not contain a valid AS2 MDN
     */
    public MDNInfo(final GenericMessageInfo generalInfo, final BodyPart mdnPart) throws MDNTransformationException {
        super(generalInfo);

        try {
            parseMimePart(mdnPart);
        } catch (MessagingException | IOException parsingError) {
            throw new MDNTransformationException("Could not parse the MDN!", parsingError);
        }
    }

    /**
     * Creates a new instance using the meta-data from the given Receipt Signal Message. A Receipt that represent an
     * AS2 MDN must contain a <code>AS2MDN</code> XML element with the MDN specific meta-data as its content.
     *
     * @param receipt       The Receipt to use as base for creating the MDN
     * @throws MDNTransformationException   When the Receipt does not represent an AS2 MDN.
     */
    public MDNInfo(final IReceipt receipt) throws MDNTransformationException {
        // Initialize general meta-data field
        super(receipt);
        // Set default values for reporting UA and action and sending modes
        //
        this.reportingUA = HolodeckB2BCoreInterface.getConfiguration().getHostName() + ";"
                            + "HolodeckB2B " + HolodeckB2BCoreInterface.getVersion().getFullVersion();
        this.dispositionMode = "automatic-action/MDN-sent-automatically";
        // Set the other fields based on the XML contained as content of the Receipt
        final OMElement rcptContent = !Utils.isNullOrEmpty(receipt.getContent()) ? receipt.getContent().get(0) : null;
        if (rcptContent == null || !MDNMetadata.Q_ROOT_ELEMENT_NAME.equals(rcptContent.getQName()))
            throw new MDNTransformationException("Receipt does not contain MDN data, cannot transform into MDN");

        try {
        	final MDNMetadata mdnInfo = new MDNMetadata(rcptContent);
	        // The Sender and Receiver identifiers can be get from the MDN meta-data
	        setFromPartyId(mdnInfo.getSenderId());
	        setToPartyId(mdnInfo.getReceiverId());

	        this.dispositionType = DispositionType.processed;
	        this.originalRecipient = mdnInfo.getOrigRecipient();
	        this.finalRecipient = mdnInfo.getFinalRecipient();
	        this.origMessageId = mdnInfo.getOriginalMessageId();
	        this.base64Digest = mdnInfo.getB64Digest();
	        this.hashingAlgorithm = mdnInfo.getHashingAlgorithm();
	        this.readableText = !Utils.isNullOrEmpty(mdnInfo.getDescription()) ? mdnInfo.getDescription() :
	                                                                             createReadableText();
	        this.mdnRequestOptions = mdnInfo.getReplyParameters();
        } catch (Exception noMDNMetadata) {
        	throw new MDNTransformationException("Missing MDN meta-data");
        }
    }

    /**
     * Creates a new instance using the meta-data from the given Error Signal Message. An Error Signal that represent
     * an AS2 MDN must contain one <code>Error</code> element that holds an <code>AS2MDN</code> XML element with the
     * MDN specific meta-data its <i>ErrorDetail</i> field.
     *
     * @param errorMessage   The Error to to use as base for creating the MDN
     * @throws MDNTransformationException   When the Error does not represent an AS2 MDN.
     */
    public MDNInfo(final IErrorMessage errorMessage) throws MDNTransformationException {
        // Initialize general meta-data field
        super(errorMessage);
        // Set default values for reporting UA and action and sending modes
        //
        this.reportingUA = HolodeckB2BCoreInterface.getConfiguration().getHostName() + ";"
        					+ "HolodeckB2B " + HolodeckB2BCoreInterface.getVersion().getFullVersion();
        this.dispositionMode = "automatic-action/MDN-sent-automatically";
        // Set the other fields based on the information in the first Error contained in the message
        Iterator<IEbmsError> errors = errorMessage.getErrors().iterator();
        if (!errors.hasNext())
            throw new MDNTransformationException("An AS2 error must contain at least one error!");

        IEbmsError err = errors.next();
        // Only when the error related to a problem in satisfying the MDN request the dispositionType should be set to
        // failed.
        this.dispositionType = "MDNRequest".equals(err.getCategory()) ? DispositionType.failed : DispositionType.processed;
        // HB2B / AS4 Errors only have warning and failure severity, but since error is used in disposition failure is
        // converted to error
        this.dispositionSeverity = IEbmsError.Severity.warning == err.getSeverity() ? ModifierSeverity.warning
                                                                                    : ModifierSeverity.error;
        this.dispositionModifierText = err.getMessage();
        try {
        	// Use XML with MDN meta-data in ErrorDetail of error for other fields
	        final MDNMetadata mdnInfo = new MDNMetadata(err.getErrorDetail());
	        // The Sender and Receiver identifiers can be get from the MDN meta-data
	        setFromPartyId(mdnInfo.getSenderId());
	        setToPartyId(mdnInfo.getReceiverId());

	        this.originalRecipient = mdnInfo.getOrigRecipient();
	        this.finalRecipient = mdnInfo.getFinalRecipient();
	        this.origMessageId = mdnInfo.getOriginalMessageId();
	        this.base64Digest = mdnInfo.getB64Digest();
	        this.hashingAlgorithm = mdnInfo.getHashingAlgorithm();
	        this.readableText = !Utils.isNullOrEmpty(mdnInfo.getDescription()) ? mdnInfo.getDescription() :
	                                                                             createReadableText();
	        this.mdnRequestOptions = mdnInfo.getReplyParameters();
        } catch (Exception noMDNMetadata) {
        	throw new MDNTransformationException("Missing MDN meta-data");
        }

        // Now add all other errors
        while (errors.hasNext()) {
            err = errors.next();
            if (IEbmsError.Severity.failure == err.getSeverity())
                this.errors.add(err.getMessage());
            else
                this.warnings.add(err.getMessage());
        }
    }

    /**
     * Creates a MIME multi-part as specified in [RFC3798] using the information from this object.
     *
     * @return The MIME multi-part representation of the MDN
     * @throws MDNTransformationException   When an error occurs when the MIME part is created
     */
    public MimeBodyPart toMimePart() throws MDNTransformationException {
        try {
            // Create the multi-part report
            final MimeMultipart multipart = new MimeMultipart();

            // Create the text part
            final MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(readableText, MIME_PLAIN_TEXT);
            textPart.setHeader(HTTPConstants.CONTENT_TYPE, MIME_PLAIN_TEXT);
            multipart.addBodyPart(textPart);

            // Create the report part, which *content* is a set of headers
            final MimeBodyPart reportPart = new MimeBodyPart();

            final StringBuilder reportPartContent = new StringBuilder ();
            reportPartContent.append(HEADER_REPORTING_UA).append(':').append(reportingUA).append(EOL);
            if (!Utils.isNullOrEmpty(originalRecipient))
                reportPartContent.append(HEADER_ORIGINAL_RECIPIENT).append(':').append(originalRecipient).append(EOL);
            reportPartContent.append(HEADER_FINAL_RECIPIENT).append(':').append(finalRecipient).append(EOL);
            if (!Utils.isNullOrEmpty(origMessageId))
                reportPartContent.append(HEADER_ORIGINAL_MESSAGE_ID).append(':').append(origMessageId).append(EOL);
            reportPartContent.append(HEADER_DISPOSITION).append(':').append(dispositionMode).append(';')
                    .append(dispositionType.toString());
            if (!Utils.isNullOrEmpty(dispositionModifierText))
                reportPartContent.append('/').append(dispositionModifierText);
            reportPartContent.append(EOL);

            // If the MDN contains a MIC, compose the value for this header
            if (!Utils.isNullOrEmpty(base64Digest))
                reportPartContent.append(HEADER_RECEIVED_CONTENT_MIC).append(':')
                                 .append(base64Digest).append(',')
                                 .append(hashingAlgorithm)
                                 .append(EOL);
            reportPart.setContent(reportPartContent.toString(), Constants.MDN_DISPOSITION_MIME_TYPE);
            reportPart.setHeader("Content-Transfer-Encoding", "7bit");
            reportPart.setHeader(HTTPConstants.CONTENT_TYPE, Constants.MDN_DISPOSITION_MIME_TYPE);
            multipart.addBodyPart(reportPart);

            // Convert the multi-part to MimeBodyPart
            final MimeBodyPart bodyPart = new MimeBodyPart();
            multipart.setSubType("report; report-type=disposition-notification");
            bodyPart.setContent(multipart);
            bodyPart.setHeader(HTTPConstants.CONTENT_TYPE, multipart.getContentType());

            return bodyPart;
        } catch (MessagingException ex) {
            throw new MDNTransformationException("Could not create MIME Part", ex);
        }
    }

    /**
     * Gets the human readable description of the MDN as contained in the first part of the multipart/report. Note that
     * the text is as it is retrieved from the first mime part, therefore it cannot be guaranteed that the text is
     * really acceptable for display purposes.
     *
     * @return  The content of the first mime part of the multipart/report or <code>null</code> if the MDN is not a
     *          multi-part or the first part's content cannot be retrieved
     */
    public String getReadableText() {
        return readableText;
    }

    /**
     * Gets the value of the "Reporting-UA" field
     *
     * @return  The string contained in the field, or <code>null</code> if the field was not included in the MDN
     */
    public String getReportingUA() {
        return reportingUA;
    }

    /**
     * Gets the value of the "Original-Recipient" field
     *
     * @return  The string contained in the field, or <code>null</code> if the field was not included in the MDN
     */
    @Override
	public String getOriginalRecipient() {
        return originalRecipient;
    }

    /**
     * Gets the value of the "Final-Recipient" field
     *
     * @return  The string contained in the field, or <code>null</code> if the field was not included in the MDN
     */
    @Override
	public String getFinalRecipient() {
        return finalRecipient;
    }

    /**
     * Gets the value of the "Original-Message-Id" field
     *
     * @return  The string contained in the field, or <code>null</code> if the field was not included in the MDN
     */
    public String getOrigMessageId() {
        return origMessageId;
    }

    /**
     * Gets the disposition mode as contained in the "Disposition" field
     *
     * @return  The disposition mode as string as contained in the field, or <code>null</code> if the field was not
     *          included in the MDN or could not be parsed
     */
    public String getDispositionMode() {
        return dispositionMode;
    }

    /**
     * Gets the disposition type as contained in the "Disposition" field
     *
     * @return  The disposition type as contained in the field, or <code>null</code> if the field was not included in
     *          the MDN or could not be parsed
     */
    public DispositionType getDispositionType() {
        return dispositionType;
    }

    /**
     * Gets the severity of the problem included in the "Disposition" field.
     *
     * @return  The severity level of the problem indicated in the field, or <code>null</code> if the field was not
     *          included in the MDN or could not be parsed
     */
    public ModifierSeverity getModifierSeverity() {
        return dispositionSeverity;
    }

    /**
     * Gets the text of the disposition modifier as included in the "Disposition" field. Note that this text is optional
     * and the disposition modifier can exist of only the severity.
     *
     * @return The text of disposition modifier, or <code>null</code> if the field was not included in the MDN or did
     *         not include a text for the disposition modifier or when the field could not be parsed
     */
    public String getModifierText() {
        return dispositionModifierText;
    }

    /**
     * Gets the list of failure descriptions that were included in the "Failure" fields
     *
     * @return  List of strings containing the values of the "Failure" fields in the MDN. The list is empty if no such
     *          header was present
     */
    public List<String> getFailures() {
        return failures;
    }

    /**
     * Gets the list of error descriptions that were included in the "Error" fields and in the disposition modifier of
     * the "Disposition" field
     *
     * @return  List of strings containing the values of the "Error" fields and/or the disposition modifier part of the
     *          "Disposition" field in the MDN. The list is empty if no errors were present
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * Gets the list of error descriptions that were included in the "Warning" fields and in the disposition modifier of
     * the "Disposition" field
     *
     * @return  List of strings containing the values of the "Warning" fields and/or the disposition modifier part of
     *          the "Disposition" field in the MDN. The list is empty if no errors were present
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Gets the base64 encoded digest of the original message as contained in the "Received-Content-MIC" field
     *
     * @return  The digest as contained in the field, or <code>null</code> if the field was not included in the MDN or
     *          could not be parsed
     */
    public String getBase64Digest() {
        return base64Digest;
    }

    /**
     * Gets the hashing algorithm used to calculate the digest of the original message as contained in the
     * "Received-Content-MIC" field
     *
     * @return  The hashing algorithm as contained in the field, or <code>null</code> if the field was not included in
     *          the MDN or could not be parsed
     */
    public String getHashingAlgorithm() {
        return hashingAlgorithm;
    }

    /**
     * Gets the parameters for sending the MDN as requested by the sender of the acknowledged message. As this only
     * applies to MDN sent by Holodeck B2B this method should only be used for outgoing MDNs.
     *
     * @return The options as provided by the sender, or <code>null</code> if nothing was specified by the sender.
     */
    public MDNRequestOptions getMDNRequestOptions() {
        return mdnRequestOptions;
    }

    /**
     * Parses the MIME part containing the MDN to retrieve the MDN specific meta-data.
     *
     * @param mdnPart   The Mime body part containing the MDN which can be a multipart/report or single
     *                  message/disposition
     * @throws MessagingException When the MIME part could not be parsed
     * @throws IOException        When the data could not be read from the MIME part
     */
    private void parseMimePart(final BodyPart mdnPart) throws MessagingException, IOException {
        BodyPart dispositionPart = null;

        // First check if the MDN is a multipart/report or single message/disposition
        if (mdnPart.isMimeType(Constants.REPORT_MIME_TYPE)) {
            final MimeMultipart multiPart = (MimeMultipart) mdnPart.getContent();
            // Although there must be at least two parts, we will accept also a single part when it is just the
            // message/disposition part
            if (multiPart.getCount() > 1) {
                // The MDN is a multipart with human readable text as first part
                try {
                    handleTextPart(multiPart.getBodyPart(0));
                } catch (Exception e) {
                    // As we don't really use the human readable part, we ignore problems in parsing it.
                }
                dispositionPart = multiPart.getBodyPart(1);
            }
        } else if (mdnPart.isMimeType(Constants.MDN_DISPOSITION_MIME_TYPE)) {
            dispositionPart = mdnPart;
        }

        handleDispositionPart(dispositionPart);
    }

    /**
     * Get the text from the human readable part of the MIME mutlipart/report and stores it in the object. According to
     * RFC3642 the human readable part can again be a multipart/alternative. If it is we just use the first alternative.
     *
     * @param textPart  The MIME body part containing the human readable version of the MDN
     * @throws MessagingException When the MIME part could not be parsed
     * @throws IOException        When the data could not be read from the MIME part
     */
    private void handleTextPart(final BodyPart textPart) throws MessagingException, IOException  {
        BodyPart plainTxtPart = textPart;
        if (textPart.isMimeType(MIME_MULTIPART_ALTERNATIVE))
            plainTxtPart = ((Multipart) textPart.getContent()).getBodyPart(0);

        this.readableText =  plainTxtPart.getContent().toString();
    }

    /**
     * Parses the machine processable part of the MIME mutlipart/report and stores the meta-data in the object.
     *
     * @param dispositionPart   The MIME body part containing the machine processable version of the MDN
     * @throws MessagingException When the MIME part could not be parsed
     * @throws IOException        When the data could not be read from the MIME part
     */
    private void handleDispositionPart(BodyPart dispositionPart) throws IOException, MessagingException {
        // The content of the disposition is a set of headers, so parse as such
        final InternetHeaders dispositionHdrs = new InternetHeaders(dispositionPart.getInputStream ());

        // Using getHeader(..., null) to get only the first (and if compliant with RFC3798 only) instance
        reportingUA = dispositionHdrs.getHeader(HEADER_REPORTING_UA, null);
        originalRecipient = dispositionHdrs.getHeader(HEADER_ORIGINAL_RECIPIENT, null);
        finalRecipient = dispositionHdrs.getHeader(HEADER_FINAL_RECIPIENT, null);
        origMessageId = dispositionHdrs.getHeader(HEADER_ORIGINAL_MESSAGE_ID, null);

        // The Disposition field itself needs to parsed
        final String disposition = dispositionHdrs.getHeader(HEADER_DISPOSITION, null);
        final int modeEnd = disposition.indexOf(';');
        if (modeEnd < 0)
            throw new MessagingException("Invalid MDN - Invalid content of Disposition header [" + disposition
            							+ "], no mode included!");

        dispositionMode = disposition.substring(0, modeEnd);
        int typeEnd = disposition.indexOf('/', modeEnd);
        if (typeEnd < 0)
            typeEnd = disposition.length();
        final String dispTypeText = disposition.substring(modeEnd + 1, typeEnd).trim().toLowerCase();
        try {
            dispositionType = DispositionType.valueOf(dispTypeText);
        } catch (IllegalArgumentException invalidDispositionType) {
            throw new MessagingException("Invalid MDN - Unknown disposition type set in MDN: " + dispTypeText);
        }
        if (typeEnd < disposition.length()) {
            final String dispositionModifier = disposition.substring(typeEnd + 1);
            if (dispositionModifier.toLowerCase().startsWith("failure"))
                dispositionSeverity = ModifierSeverity.failure;
            else if (dispositionModifier.toLowerCase().startsWith("error"))
                dispositionSeverity = ModifierSeverity.error;
            else if (dispositionModifier.toLowerCase().startsWith("warning"))
                dispositionSeverity = ModifierSeverity.warning;
            else {
                throw new MessagingException("Invalid MDN - Unknown disposition modifier used: "
                							+ dispositionModifier);
            }
            final int dispTxtStart = dispositionModifier.indexOf(':') + 1;
            this.dispositionModifierText = dispTxtStart > 0 ? dispositionModifier.substring(dispTxtStart) : "";
        }
        final String[] failureHdrs = dispositionHdrs.getHeader(HEADER_FAILURE);
        if (failureHdrs != null)
            for(String f : failureHdrs)
                if (!Utils.isNullOrEmpty(f)) getFailures().add(f);
        final String[] errorHdrs = dispositionHdrs.getHeader(HEADER_ERROR);
        if (errorHdrs != null)
            for(String e : errorHdrs)
                if (!Utils.isNullOrEmpty(e)) getErrors().add(e);
        final String[] warningHdrs = dispositionHdrs.getHeader(HEADER_WARNING);
        if (warningHdrs != null)
            for(String w : warningHdrs)
                if (!Utils.isNullOrEmpty(w)) getFailures().add(w);

        final String rcvdContentMIC = dispositionHdrs.getHeader(HEADER_RECEIVED_CONTENT_MIC, null);
        if (!Utils.isNullOrEmpty(rcvdContentMIC)) {
            // The Received-content-MIC is also a complex string that needs to be parsed
            try {
                final int digestEnd = rcvdContentMIC.indexOf(',');
                base64Digest = rcvdContentMIC.substring(0, digestEnd);
                hashingAlgorithm = rcvdContentMIC.substring(digestEnd + 1);
            } catch (IndexOutOfBoundsException parseError) {
                throw new MessagingException("Invalid MDN - Error in received MIC: " + rcvdContentMIC);
            }
        }
    }

    /**
     * Creates the text to include in the human readable part of the MDN.
     *
     * @return Human readable summary of the MDN
     */
    private String createReadableText() {
        StringBuilder txt = new StringBuilder("This is an automatically generated MDN for the AS2 message");
        if (!Utils.isNullOrEmpty(origMessageId))
            txt.append("[message-id:").append(getOrigMessageId()).append(']');
        txt.append(" received from ");
        txt.append(getToPartyId());
        txt.append(".").append(EOL).append(EOL);

        if (getDispositionType() == DispositionType.processed) {
            txt.append("The message was processed");
            if (Utils.isNullOrEmpty(getFailures()) && Utils.isNullOrEmpty(getErrors()) && Utils.isNullOrEmpty(getWarnings()))
                txt.append(" successfully.");
            else
                txt.append(", but the following problems were found during processing:");
        } else {
            txt.append("The message failed to processed");
            if (Utils.isNullOrEmpty(getFailures()) && Utils.isNullOrEmpty(getErrors()) && Utils.isNullOrEmpty(getWarnings()))
                txt.append(" for unknown reasons.");
            else
                txt.append(" because of the following problems:");
        }

        if (!Utils.isNullOrEmpty(failures)) {
            txt.append(EOL).append(EOL).append("Errors with severity failure:").append(EOL);
            txt.append("-----------------------------");
            getFailures().forEach(f -> txt.append(EOL).append("- ").append(f));
        }
        if (!Utils.isNullOrEmpty(errors)) {
            txt.append(EOL).append(EOL).append("Errors with severity error:").append(EOL);
            txt.append("---------------------------");
            getErrors().forEach(e -> txt.append(EOL).append("- ").append(e));
        }
        if (!Utils.isNullOrEmpty(warnings)) {
            txt.append(EOL).append(EOL).append("Errors with severity warning:").append(EOL);
            txt.append("----------------------------");
            getWarnings().forEach(w -> txt.append("EOL").append("- ").append(w));
        }
        txt.append(EOL);
        return txt.toString();
    }


}

