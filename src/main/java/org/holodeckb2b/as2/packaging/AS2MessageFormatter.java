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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.axiom.om.OMOutputFormat;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.MessageFormatter;
import org.holodeckb2b.as2.util.Constants;

/**
 * Is an Axis2 {@link MessageFormatter} to write the MIME body part containing the AS2 message to the HTTP entity body
 * of the outgoing message.   
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class AS2MessageFormatter implements MessageFormatter {

	@Override
	public byte[] getBytes(MessageContext messageContext, OMOutputFormat format) throws AxisFault {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			writeTo(messageContext, format, baos, true);
			return baos.toByteArray();
		} catch (IOException ex) {
			throw AxisFault.makeFault(ex);
		}

	}

	@Override
	public void writeTo(MessageContext messageContext, OMOutputFormat format, OutputStream outputStream,
						boolean preserve) throws AxisFault {
		MimeBodyPart mimeEnvelope = (MimeBodyPart) messageContext.getProperty(Constants.MC_MIME_ENVELOPE);
		if (mimeEnvelope == null)
			return;
		
		try (InputStream mimeContentIS = mimeEnvelope.getInputStream()) {
			byte[] buffer = new byte[1024];
			int bytesRead = 0;
			while ((bytesRead = mimeContentIS.read(buffer)) > 0)
				outputStream.write(buffer, 0, bytesRead);
		} catch (MessagingException | IOException msgWriteFailure) {
			throw AxisFault.makeFault(msgWriteFailure);
		}
	}

	@Override
	public String getContentType(MessageContext messageContext, OMOutputFormat format, String soapAction) {
		try {
			return ((MimeBodyPart) messageContext.getProperty(Constants.MC_MIME_ENVELOPE)).getContentType();
		} catch (Exception me) {
			return null;
		}
	}

	@Override
	public URL getTargetAddress(MessageContext messageContext, OMOutputFormat format, URL targetURL) throws AxisFault {
		return targetURL;
	}

	@Override
	public String formatSOAPAction(MessageContext messageContext, OMOutputFormat format, String soapAction) {
		return null;
	}

}
