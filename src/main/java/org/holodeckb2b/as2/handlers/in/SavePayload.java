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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.axiom.mime.ContentType;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.errors.OtherContentError;
import org.holodeckb2b.common.handlers.AbstractUserMessageHandler;
import org.holodeckb2b.common.messagemodel.Payload;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;

/**
 * Is the <i>in_flow</i> handler that reads the payload data from the AS2 message and stores it in a temporary file so
 * it can be delivered using the <i>delivery method</i> configured in the P-Mode.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class SavePayload extends AbstractUserMessageHandler {
    /**
     * The name of the directory used for temporarily storing payloads
     */
    private static final String PAYLOAD_DIR = "plcin";

    @Override
    protected InvocationResponse doProcessing(final IUserMessageEntity userMessage, 
											  final IMessageProcessingContext procCtx, final Logger log) 
													  												throws Exception {
        try {
            // Create a unique filename for temporarily storing the payload
            final File plFile = File.createTempFile("pl-", null, getTempDir());
            log.debug("Saving the payload data from the message to temp file: " + plFile.getAbsolutePath());
            savePayload((MimeBodyPart) procCtx.getProperty(Constants.CTX_MIME_ENVELOPE), plFile);
            log.debug("Saved payload data to file, update message meta-data");
            Payload payloadInfo = new Payload();
            payloadInfo.setContainment(IPayload.Containment.BODY);
            payloadInfo.setContentLocation(plFile.getAbsolutePath());
            final ContentType contentType = (ContentType) procCtx.getProperty(
                                                            org.apache.axis2.Constants.Configuration.CONTENT_TYPE);
            payloadInfo.setMimeType(contentType.getMediaType().toString());
            log.debug("Update message meta-data in database");
            HolodeckB2BCore.getStorageManager().setPayloadInformation(userMessage,
                                                                      Collections.singletonList(payloadInfo));
        } catch (IOException saveFailed) {
            log.error("Could not save the payload data to temp file! Error details: " + saveFailed.getMessage());
            procCtx.addGeneratedError(new OtherContentError("Internal processing error", userMessage.getMessageId()));
            log.debug("Set processing state of message to failed");
            HolodeckB2BCore.getStorageManager().setProcessingState(userMessage, ProcessingState.FAILURE);
        }

        return InvocationResponse.CONTINUE;
    }

    /**
     * Helper method to get the directory where the payload contents can be stored.
     *
     * @todo Consider moving this to util class, as it is a copy of the same method in SaveUserMsgAttachments!
     *
     * @return              {@link File} handler to the directory that must be used for storing payload content
     * @throws IOException   When the specified directory does not exist and can not be created.
     */
    private File getTempDir() throws IOException {
        final String tmpPayloadDirPath = HolodeckB2BCoreInterface.getConfiguration().getTempDirectory() + PAYLOAD_DIR;
        final File tmpPayloadDir = new File(tmpPayloadDirPath);
        if (!tmpPayloadDir.exists() && !tmpPayloadDir.mkdirs())
            throw new IOException("Temp directory for payloads (" + tmpPayloadDirPath + ") could not be created!");
        
        return tmpPayloadDir;
    }

    /**
     * Helper method to save the payload data to a temporary file before delivery to the back-end system.
     *
     * @param payloadPart   The MIME body part containing the payload
     * @param plFile        The temporary file to store the data
     * @throws IOException  When there is an error saving the payload data to the file
     */
    private void savePayload(final MimeBodyPart payloadPart, final File plFile) throws IOException {
        byte[] buffer = new byte[16384]; //16KB buffer
        try (final InputStream is = payloadPart.getInputStream();
             final OutputStream os = new FileOutputStream(plFile)) {
            int bytesRead;
            while ((bytesRead = is.read(buffer, 0, buffer.length)) > -1)
                os.write(buffer, 0, bytesRead);
        } catch (MessagingException ex) {
            throw new IOException("Unable to get access to payload data!", ex);
        }
    }
}

