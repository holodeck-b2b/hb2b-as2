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
import java.io.InputStream;
import java.io.OutputStream;

import javax.mail.internet.MimeBodyPart;

import org.apache.axiom.mime.ContentType;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.errors.OtherContentError;
import org.holodeckb2b.common.handlers.AbstractUserMessageHandler;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.interfaces.storage.IPayloadContent;
import org.holodeckb2b.interfaces.storage.IPayloadEntity;
import org.holodeckb2b.interfaces.storage.IUserMessageEntity;

/**
 * Is the <i>in_flow</i> handler that reads the payload data from the AS2 message and saves it to storage using the
 * Holodeck B2B <i>Payload Storage Provider</i>.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class SavePayload extends AbstractUserMessageHandler {

    @Override
    protected InvocationResponse doProcessing(final IUserMessageEntity userMessage,
											  final IMessageProcessingContext procCtx, final Logger log)
													  												throws Exception {
        try {
        	IPMode pmode = HolodeckB2BCoreInterface.getPModeSet().get(userMessage.getPModeId());
        	IPayloadEntity payload = userMessage.getPayloads().iterator().next();
        	log.trace("Get the storage for payload data");
        	IPayloadContent storage = HolodeckB2BCore.getStorageManager().createStorageReceivedPayload(payload, pmode);
        	if (storage.getContent() != null) {
            	log.debug("Content of payload has already been saved");
				return InvocationResponse.CONTINUE;
        	}
        	log.debug("Save payload data");
        	MimeBodyPart payloadPart = (MimeBodyPart) procCtx.getProperty(Constants.CTX_MIME_ENVELOPE);
        	try (InputStream is = payloadPart.getInputStream(); OutputStream os = storage.openStorage()) {
	        	Utils.copyStream(is, os);
        	}
            final ContentType contentType = (ContentType) procCtx.getProperty(
                                                            org.apache.axis2.Constants.Configuration.CONTENT_TYPE);
            payload.setMimeType(contentType.getMediaType().toString());
            log.debug("Update message meta-data in database");
            HolodeckB2BCore.getStorageManager().updatePayloadInformation(payload);
        } catch (IOException saveFailed) {
            log.error("Could not save the payload data to temp file! Error details: " + saveFailed.getMessage());
            procCtx.addGeneratedError(new OtherContentError("Internal processing error", userMessage.getMessageId()));
            log.debug("Set processing state of message to failed");
            HolodeckB2BCore.getStorageManager().setProcessingState(userMessage, ProcessingState.FAILURE);
        }

        return InvocationResponse.CONTINUE;
    }
}

