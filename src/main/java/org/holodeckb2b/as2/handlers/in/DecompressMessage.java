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

import java.text.ParseException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.axiom.mime.ContentType;
import org.apache.axis2.context.MessageContext;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;
import org.bouncycastle.mail.smime.SMIMECompressed;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.mail.smime.SMIMEUtil;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.as4.compression.DeCompressionFailure;
import org.holodeckb2b.ebms3.axis2.MessageContextUtils;
import org.holodeckb2b.ebms3.util.AbstractUserMessageHandler;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.module.HolodeckB2BCore;

/**
 * Is the <i>in_flow</i> handler responsible for the decompression of a received AS2 User Message. Because compression
 * can be done both before and after signing of the message this handler will also run twice in the pipeline, once
 * before signature verification and once after. This is possible because the complete MIME part is compressed, so if
 * compression is done after signature, the signature itself is also compressed.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class DecompressMessage extends AbstractUserMessageHandler {

    @Override
    protected byte inFlows() {
        return IN_FLOW | RESPONDER;
    }

    @Override
    protected InvocationResponse doProcessing(MessageContext mc, IUserMessageEntity userMessage) throws Exception {

        // First check if received message does contain a compressed User Message
        if (!isCompressed(mc))
            return InvocationResponse.CONTINUE;

        try {
            log.debug("Received message is compressed, decompress");
            final SMIMECompressed compressedPart = new SMIMECompressed((MimeBodyPart)
                                                                          mc.getProperty(Constants.MC_MIME_ENVELOPE));
            final MimeBodyPart decompressedPart = SMIMEUtil.toMimeBodyPart(compressedPart
                                                                             .getContent(new ZlibExpanderProvider()));
            log.debug("Successfully decompressed the message, replacing compressed data with decompressed version");
            mc.setProperty(Constants.MC_MIME_ENVELOPE, decompressedPart);
            mc.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE,
                                                                  new ContentType(decompressedPart.getContentType()));
        } catch (CMSException | MessagingException | SMIMEException | ParseException compressionFailure) {
            log.error("An error occurred while decompressing the message! Details: " 
            			+ compressionFailure.getMessage());
            MessageContextUtils.addGeneratedError(mc, new DeCompressionFailure(compressionFailure.getMessage(),
                                                                               userMessage.getMessageId()));
            HolodeckB2BCore.getStorageManager().setProcessingState(userMessage, ProcessingState.FAILURE);
        }

        return InvocationResponse.CONTINUE;
    }

    /**
     * Determines whether the received message is compressed by checking the Content-Type header.
     *
     * @param mc  The message context
     * @return    <code>true</code> if the Content-Type indicate a compressed SMIME, <br>
     *            <code>false</code> otherwise
     */
    private boolean isCompressed(MessageContext mc) {
        final ContentType contentType = (ContentType)
                                                mc.getProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE);
        final String sBaseType = contentType.getMediaType().toString();
        final String smimeType = contentType.getParameter("smime-type");

        return sBaseType.equalsIgnoreCase("application/pkcs7-mime") &&
               smimeType != null && smimeType.equalsIgnoreCase("compressed-data");
    }
}
