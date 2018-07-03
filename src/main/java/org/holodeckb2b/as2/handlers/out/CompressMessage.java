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
package org.holodeckb2b.as2.handlers.out;

import java.text.ParseException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.axiom.mime.ContentType;
import org.apache.axis2.context.MessageContext;
import org.bouncycastle.cms.jcajce.ZlibCompressor;
import org.bouncycastle.mail.smime.SMIMECompressedGenerator;
import org.bouncycastle.mail.smime.SMIMEException;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.util.AbstractUserMessageHandler;
import org.holodeckb2b.interfaces.as4.pmode.IAS4PayloadProfile;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.pmode.ILeg;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.IUserMessageFlow;

/**
 * Is the <i>out_flow</i> handler responsible for the compression of the AS2 message. It creates the S/MIME package with
 * the compressed content. As the AS2 extension is intended for migration purposed the P-Mode parameter for the AS4
 * Compression Feature is reused to indicate whether compression should be applied to the message. If the parameter
 * exists and contains a non-empty string compression will be applied.
 * <p>As described in <a href="https://tools.ietf.org/html/rfc5402">RFC5402</a> compression may be applied both before
 * and after signing of the message's content. Holodeck B2B will compress the message after the content has been signed
 * so the signature's digest applies to the original content.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class CompressMessage extends AbstractUserMessageHandler {

    @Override
    protected byte inFlows() {
        return OUT_FLOW;
    }

    @Override
    protected InvocationResponse doProcessing(MessageContext mc, IUserMessageEntity userMessage) throws Exception {
    	// First check that there is content that can be compressed
    	final MimeBodyPart  msgToCompress = (MimeBodyPart) mc.getProperty(Constants.MC_MIME_ENVELOPE);
        if (msgToCompress == null)
        	return InvocationResponse.CONTINUE;

        // Check if message needs to be compressed
        if (!shouldCompress(userMessage)) {
            log.debug("Message does not need to be compressed");
            return InvocationResponse.CONTINUE;
        }

        try {
            log.debug("Message should be compressed, create SMIME generator");
            final SMIMECompressedGenerator smimeGenerator = new SMIMECompressedGenerator();
            final MimeBodyPart compressedMsg = smimeGenerator.generate(msgToCompress, new ZlibCompressor());
            log.debug("Message MIME part successfully compressed, set as new MIME Envelope");
            // Create the MIME body part to include in message context
            mc.setProperty(Constants.MC_MIME_ENVELOPE, compressedMsg);
            final ContentType contentType = new ContentType(compressedMsg.getContentType());
            mc.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE, contentType);

            log.debug("Completed message compression succesfully");
            return InvocationResponse.CONTINUE;
        } catch (ParseException | MessagingException | SMIMEException compressFailure) {
            log.error("An error occurred while compressing the message. Error details: "
                     + Utils.getExceptionTrace(compressFailure));
            throw compressFailure;
        }
    }

    /**
     * Determines whether compression should be applied to the message by checking if the P-Mode parameter for AS4
     * Compression has been set.
     *
     * @param um     The User Message that is send
     * @return       <code>true</code> if the P-Mode parameter for AS4 Compression exists and contains a non-empty
     *               value,<br><code>false</code>
     */
    private boolean shouldCompress(final IUserMessage um) {
        boolean compress = false;
        final IPMode pmode = HolodeckB2BCoreInterface.getPModeSet().get(um.getPModeId());

        if (pmode != null) {
            final ILeg leg = pmode.getLeg(ILeg.Label.REQUEST);
            if (leg != null) {
                final IUserMessageFlow umFlow = leg.getUserMessageFlow();
                if (umFlow != null) {
                    try {
                        final IAS4PayloadProfile payloadProfile = (IAS4PayloadProfile) umFlow.getPayloadProfile();
                        compress = payloadProfile != null && !Utils.isNullOrEmpty(payloadProfile.getCompressionType());
                    } catch (ClassCastException cce) {}
                }
            }
        }
        return compress;
    }
}

