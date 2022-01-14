/**
 * Copyright (C) 2019 The Holodeck B2B Team, Sander Fieten
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
package org.holodeckb2b.as2.axis2;

import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.activation.DataHandler;
import javax.mail.BodyPart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.axiom.mime.ContentType;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.axis2.builder.Builder;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.transport.TransportUtils;
import org.apache.axis2.transport.http.HTTPConstants;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.core.MessageProcessingContext;

/**
 * Is a {@link Builder} implementation for parsing the main MIME part of a received AS2 message. It makes the parsed
 * MIME parts available in the Holodeck B2B <i>message processing context</i> so the message can be processed by the 
 * handlers of the AS2 Module.
 * 
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class AS2MessageBuilder implements Builder {

	@Override
	public OMElement processDocument(InputStream inputStream, String contentType, MessageContext messageContext)
			throws AxisFault {
		
		// If the received message has no Content-Type header we cannot build it.
		if (Utils.isNullOrEmpty(contentType))
			return null;
        			
		try {
			// Create representation of the "MIME envelope", i.e. the root MIME part		 
			final ByteArrayDataSource datasource = new ByteArrayDataSource(inputStream, contentType);
			final MimeBodyPart mimeEnvelope = new MimeBodyPart();
			mimeEnvelope.setDataHandler(new DataHandler(datasource));
			mimeEnvelope.setHeader(HTTPConstants.HEADER_CONTENT_TYPE, contentType);
			
			// If the message is signed it is a multi-part and the "real" content is in the first part. Therefore
			// we make it easy accessible for handlers 
			BodyPart mainPart = mimeEnvelope;
			if (mimeEnvelope.isMimeType("multipart/signed")) {
			    // This is a signed message, get the MIME type is of the primary part
			    final MimeMultipart multiPart = (MimeMultipart) mimeEnvelope.getContent();
			    mainPart = multiPart.getBodyPart(0);
			}				
			
			MessageProcessingContext procCtx = MessageProcessingContext.getFromMessageContext(messageContext);
			
			procCtx.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE, new ContentType(contentType));
			procCtx.setProperty(Constants.CTX_MIME_ENVELOPE, mimeEnvelope);
			procCtx.setProperty(Constants.CTX_MAIN_MIME_PART, mainPart);
			
	        @SuppressWarnings("unchecked")
			Map<String, String> httpHeaders = (Map<String, String>) messageContext
																		   .getProperty(MessageContext.TRANSPORT_HEADERS);
	        if (httpHeaders != null) 
	        	messageContext.setProperty(MessageContext.TRANSPORT_HEADERS, httpHeaders.entrySet().parallelStream()
											.collect(Collectors.toMap(h -> h.getKey().toLowerCase(), Entry::getValue)));
	
			return TransportUtils.createSOAPEnvelope(null);
		} catch (Exception msgBuildError) {
			throw new AxisFault("Error in building AS2 message", msgBuildError);
		}
	}

}
