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
package org.holodeckb2b.as2.axis2.http;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import javax.activation.DataHandler;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.axiom.mime.ContentType;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.dispatchers.RequestURIBasedDispatcher;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.transport.RequestResponseTransport;
import org.apache.axis2.transport.TransportUtils;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.HTTPTransportUtils;
import org.apache.axis2.transport.http.HTTPWorker;
import org.apache.axis2.transport.http.server.AxisHttpRequest;
import org.apache.axis2.transport.http.server.AxisHttpResponse;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.holodeckb2b.as2.handlers.in.AS2MessageReceiver;
import org.holodeckb2b.as2.util.Constants;

/**
 * Extends Axis2 {@link HTTPWorker} to check whether the received message is an AS2 message and if this Holodeck B2B 
 * instance can handle it, i.e. has the AS2 service installed for the targeted URL. This includes a kind of 
 * <i>"dispatch"</i> as the AS2 service is already selected as target.
 * <p>Beside checking whether the received message is an AS2 message, it also reads the data from the input stream
 * into a MIME body part object which is added to the message context so the handlers can easy access the message.
 * 
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class AS2CapableWorker extends HTTPWorker {
	@Override
	public void service(final AxisHttpRequest request, final AxisHttpResponse response, final MessageContext msgContext)
			throws HttpException, IOException {

		if (HTTPConstants.HEADER_POST.equals(request.getMethod()) && isAS2Message(msgContext)) {
			try {
				// Handle possible gzip Transfer-Encoding			
				HTTPTransportUtils.handleGZip(msgContext, request.getInputStream());
				// Convert all http header names to lower-case for easy retrieval
				@SuppressWarnings("unchecked")
				final Map<String, String> httpHeaders = (Map<String, String>) 
															msgContext.getProperty(MessageContext.TRANSPORT_HEADERS);
				final Map<String, String> lcHeaders = new HashMap<>(httpHeaders.size());
				httpHeaders.entrySet().forEach(e -> lcHeaders.put(e.getKey().toLowerCase(), e.getValue()));
				msgContext.setProperty(MessageContext.TRANSPORT_HEADERS, lcHeaders);
				// Create representation of the "MIME envelope", i.e. the root MIME part
				final MimeBodyPart mimeEnvelope = readMimeEnvelope(request);
				// If the message is signed it is a multi-part and the "real" content is in the first part. Therefore
				// we make it easy accessible for handlers 
				BodyPart mainPart = mimeEnvelope;
				if (mimeEnvelope.isMimeType("multipart/signed")) {
				    // This is a signed message, get the MIME type is of the primary part
				    final MimeMultipart multiPart = (MimeMultipart) mimeEnvelope.getContent();
				    mainPart = multiPart.getBodyPart(0);
				}				
				// Prepare message context so Axis can manage processing
				// An empty SOAP envelope is added so handlers relying on the existence don't crash				
				msgContext.setEnvelope(TransportUtils.createSOAPEnvelope(null));
				msgContext.setProperty(MessageContext.TRANSPORT_OUT, response.getOutputStream());
				
				final ContentType contentType = new ContentType(request.getContentType());
				msgContext.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE, contentType);
				msgContext.setProperty(Constants.MC_MIME_ENVELOPE, mimeEnvelope);
				msgContext.setProperty(Constants.MC_MAIN_MIME_PART, mainPart);

				AxisEngine.receive(msgContext);
			} catch (MessagingException | ParseException ctParseFailure) {
				// Failure to parse the provided content-type header or read the multi-part. Consider it as an invalid
				// request
				response.setStatus(HttpStatus.SC_BAD_REQUEST);
				throw new IOException("Message parsing failed");
			}

			// Finalize response
			RequestResponseTransport requestResponseTransportControl = (RequestResponseTransport) msgContext
					.getProperty(RequestResponseTransport.TRANSPORT_CONTROL);

			if (TransportUtils.isResponseWritten(msgContext)
					|| ((requestResponseTransportControl != null) && requestResponseTransportControl.getStatus()
							.equals(RequestResponseTransport.RequestResponseTransportStatus.SIGNALLED))) {
				// The response is written or signaled. The current status is used (probably SC_OK).
			} else {
				// The response may be ack'd, mark the status as accepted.
				response.setStatus(HttpStatus.SC_ACCEPTED);
			}
		} else
			// If it was not an AS2 message, use normal Axis2 processing
			super.service(request, response, msgContext);
	}

	/**
	 * Checks whether the request should be processed as an AS2 message. This is checked by looking if the request URL 
	 * matches to the AS2 service. 
	 *
	 * @param msgContext 	The message context
	 * @return <code>true</code> if the message is targeted at URL handled by the AS2 service,<br>
	 *         <code>false</code> otherwise
	 */
	private static boolean isAS2Message(MessageContext msgContext) {
		try {
			RequestURIBasedDispatcher requestDispatcher = new RequestURIBasedDispatcher();		
			requestDispatcher.invoke(msgContext);
		} catch (AxisFault notFound) {}
		AxisService axisService = msgContext.getAxisService();
		if (axisService != null) {
			// Check it is the AS2 Service
			// => it should have a "Receive" operation which message receiver is the AS2MessageReceiver
			AxisOperation axisOperation = axisService.getOperationBySOAPAction("Receive");
			if (axisOperation != null)
				return AS2MessageReceiver.class == axisOperation.getMessageReceiver().getClass();
			else
				return false;
		} else
			return false;
	}

	/**
	 * Reads the MIME body part contained in the HTTP entity body from the received message's input stream.  
	 *  
	 * @param request	The received request
	 * @return			The MIME body part contained in the message, or<br>
	 * 					<code>null</code> if no MIME body part could be read from the entity body  
	 */
	private MimeBodyPart readMimeEnvelope(AxisHttpRequest request) { 
		try {
			final String contentType = request.getContentType();		
			final ByteArrayDataSource datasource = new ByteArrayDataSource(request.getInputStream(), contentType);
			final MimeBodyPart mimeEnvelope = new MimeBodyPart();
			mimeEnvelope.setDataHandler(new DataHandler(datasource));
			mimeEnvelope.setHeader(HTTPConstants.HEADER_CONTENT_TYPE, contentType);
			return mimeEnvelope;
		} catch (IOException | MessagingException msgReadFailure) {
			return null;
		}
	}

}
