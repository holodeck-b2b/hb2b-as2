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
package org.holodeckb2b.as2.axis2;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import javax.activation.DataHandler;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.namespace.QName;

import org.apache.axiom.mime.ContentType;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants.Configuration;
import org.apache.axis2.client.OperationClient;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.async.AxisCallback;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.ClientUtils;
import org.apache.axis2.description.OutInAxisOperation;
import org.apache.axis2.description.WSDL2Constants;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.i18n.Messages;
import org.apache.axis2.transport.TransportUtils;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.util.Utils;


/**
 * Is a special Axis2 {@link AxisOperation} implementation that can handle AS2, i.e. MIME messages and supports the 
 * Out In MEP but allowing for an empty response. When there is a response it prepares the response message context so
 * the AS2 message can be handled correctly. 
 * 
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class OutOptInAS2Operation extends OutInAxisOperation {

    /**
     * Create a new instance
     */
    public OutOptInAS2Operation(final QName name) {
        super(name);
        setMessageExchangePattern(WSDL2Constants.MEP_URI_OUT_IN);
    }

    /**
     * Returns the MEP client for an Out-IN operation capable of handling AS2 messages and that accepts both an empty 
     * response as well as one containing a MDN. 
     *
     * {@inheritDoc}
     */
    @Override
    public OperationClient createClient(final ServiceContext sc, final Options options) {
        return new OutOptInAS2OperationClient(this, sc, options);
    }

    /**
     * The client to handle the MEP. This is a copy of <code>OutInAxisOperationClient<code> inner class of {@link
     * OutInAxisOperation} with an adjusted {@link #handleResponse(MessageContext)} method.
     * 
     * TODO: Consider extracting this class to HB2B common as it can be reused for multiple protocols
     */
    class OutOptInAS2OperationClient extends OperationClient {

        private final Log log = LogFactory.getLog(OutOptInAS2OperationClient.class);

        OutOptInAS2OperationClient(final OutInAxisOperation axisOp, final ServiceContext sc, final Options options) {
            super(axisOp, sc, options);
        }

        /**
         * Adds message context to operation context, so that it will handle the logic correctly if the OperationContext
         * is null then new one will be created, and Operation Context will become null when some one calls reset().
         *
         * @param msgContext the MessageContext to add
         * @throws AxisFault
         */
        @Override
        public void addMessageContext(final MessageContext msgContext) throws AxisFault {
            msgContext.setServiceContext(sc);
            if (msgContext.getMessageID() == null) {
                setMessageID(msgContext);
            }
            axisOp.registerOperationContext(msgContext, oc);
        }

        /**
         * Returns the message context for a given message label.
         *
         * @param messageLabel : label of the message and that can be either "Out" or "In" and nothing else
         * @return Returns MessageContext.
         * @throws AxisFault
         */
        @Override
        public MessageContext getMessageContext(final String messageLabel)
                throws AxisFault {
            return oc.getMessageContext(messageLabel);
        }

        /**
         * Executes the MEP. What this does depends on the specific MEP client. The basic idea is to have the MEP client
         * execute and do something with the messages that have been added to it so far. For example, if its an Out-In
         * MEP, then if the Out message has been set, then executing the client asks it to send the message and get the
         * In message, possibly using a different thread.
         *
         * @param block Indicates whether execution should block or return ASAP. What block means is of course a
         * function of the specific MEP client. IGNORED BY THIS MEP CLIENT.
         * @throws AxisFault if something goes wrong during the execution of the MEP.
         */
        @Override
        public void executeImpl(final boolean block) throws AxisFault {
            if (log.isDebugEnabled()) {
                log.debug("Entry: OutOptInAxisOperationClient::execute, " + block);
            }
            if (completed) {
                throw new AxisFault(Messages.getMessage("mepiscomplted"));
            }
            final ConfigurationContext cc = sc.getConfigurationContext();

            // copy interesting info from options to message context.
            final MessageContext mc = oc.getMessageContext(WSDLConstants.MESSAGE_LABEL_OUT_VALUE);
            if (mc == null) {
                throw new AxisFault(Messages.getMessage("outmsgctxnull"));
            }
            prepareMessageContext(cc, mc);

            if (options.getTransportIn() == null && mc.getTransportIn() == null) {
                mc.setTransportIn(ClientUtils.inferInTransport(cc
                        .getAxisConfiguration(), options, mc));
            } else if (mc.getTransportIn() == null) {
                mc.setTransportIn(options.getTransportIn());
            }

            if (block) {
                // Send the SOAP Message and receive a response
                send(mc);
                completed = true;
            } else {
                sc.getConfigurationContext().getThreadPool().execute(
                        new OutOptInAS2OperationClient.NonBlockingInvocationWorker(mc, axisCallback));
            }
        }

        /**
         * When synchronous send() gets back a response MessageContext, this is the workhorse method which processes it.
         *
         * @param responseMessageContext the active response MessageContext
         * @throws AxisFault if something went wrong
         */
        protected void handleResponse(final MessageContext responseMessageContext) throws AxisFault {
        	
        	// If the Content-Type header is set on the response, there should be a response message available
        	// The default Axis http sender does not set the content type in the msg ctx, must be retrievd from http 
        	// headers
        	ContentType contentType = null;
        	try {
        		@SuppressWarnings("unchecked")
				Map<String, String> httpHeaders = (Map<String, String>) 
        										 responseMessageContext.getProperty(MessageContext.TRANSPORT_HEADERS);
        		if (!Utils.isNullOrEmpty(httpHeaders))
        			contentType = new ContentType(httpHeaders.get(HTTPConstants.CONTENT_TYPE.toLowerCase()));
        	} catch (ParseException invalidContentType) {
        		log.warn("Could not parse the Content-Type header of the response");	
        	} catch (NullPointerException notAvailable) {}
        	
        	if (contentType != null) {        	
        		InputStream is = (InputStream) responseMessageContext.getProperty(MessageContext.TRANSPORT_IN);
    			try {
    				final ByteArrayDataSource datasource = new ByteArrayDataSource(is, contentType.toString());
    				final MimeBodyPart mimeEnvelope = new MimeBodyPart();
    				mimeEnvelope.setDataHandler(new DataHandler(datasource));
    				mimeEnvelope.setHeader(HTTPConstants.HEADER_CONTENT_TYPE, contentType.toString());
    				responseMessageContext.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE, 
    												   contentType);
    				responseMessageContext.setProperty(Constants.MC_MIME_ENVELOPE, mimeEnvelope);
    				// If the message is signed it is a multi-part and the "real" content is in the first part. 
    				// Therefore we make it easy accessible for handlers 
    				BodyPart mainPart = mimeEnvelope;
    				if (mimeEnvelope.isMimeType("multipart/signed")) {
    				    // This is a signed message, get the MIME type is of the primary part
    				    final MimeMultipart multiPart = (MimeMultipart) mimeEnvelope.getContent();
    				    mainPart = multiPart.getBodyPart(0);
    				}				
    				responseMessageContext.setProperty(Constants.MC_MAIN_MIME_PART, mainPart);    				
    				// An empty SOAP envelope is added so handlers relying on the existence don't crash				
    				responseMessageContext.setEnvelope(TransportUtils.createSOAPEnvelope(null));
    				
    				AxisEngine.receive(responseMessageContext);
    			} catch (IOException | MessagingException msgReadFailure) {
    				// Although a C-T header was set, the content was not a valid AS2 message...
    				log.error("Could not read AS2 message from the HTTP response!");
    			}  
        	}
        }

        /**
         * Synchronously send the request and receive a response. This relies on the transport correctly connecting the
         * response InputStream!
         *
         * @param msgContext the request MessageContext to send.
         * @return Returns MessageContext.
         * @throws AxisFault Sends the message using a two way transport and waits for a response
         */
        protected MessageContext send(final MessageContext msgContext) throws AxisFault {

        // create the responseMessageContext
            final MessageContext responseMessageContext
                    = msgContext.getConfigurationContext().createMessageContext();

            responseMessageContext.setServerSide(false);
            responseMessageContext.setOperationContext(msgContext.getOperationContext());
            responseMessageContext.setOptions(new Options(options));
            responseMessageContext.setMessageID(msgContext.getMessageID());
            addMessageContext(responseMessageContext);
            responseMessageContext.setServiceContext(msgContext.getServiceContext());
            responseMessageContext.setAxisMessage(
                    axisOp.getMessage(WSDLConstants.MESSAGE_LABEL_IN_VALUE));

            //sending the message
            AxisEngine.send(msgContext);

            responseMessageContext.setDoingREST(msgContext.isDoingREST());

            // Copy RESPONSE properties which the transport set onto the request message context when it processed
            // the incoming response recieved in reply to an outgoing request.
            // We convert the http headers to lowercase for unambigious processing
            @SuppressWarnings("unchecked")
			final Map<String, String> httpHeaders = (Map<String, String>) 
            												msgContext.getProperty(MessageContext.TRANSPORT_HEADERS);
            if (!Utils.isNullOrEmpty(httpHeaders)) {
            		final Map<String, String> lcHeaders = new HashMap<>(httpHeaders.size());
            		httpHeaders.entrySet().forEach(e -> lcHeaders.put(e.getKey().toLowerCase(), e.getValue()));
            		responseMessageContext.setProperty(MessageContext.TRANSPORT_HEADERS, lcHeaders);
            }
            responseMessageContext.setProperty(HTTPConstants.MC_HTTP_STATUS_CODE,
                    msgContext.getProperty(HTTPConstants.MC_HTTP_STATUS_CODE));
            responseMessageContext.setProperty(Configuration.CONTENT_TYPE, 
            													msgContext.getProperty(Configuration.CONTENT_TYPE));
            responseMessageContext.setProperty(MessageContext.TRANSPORT_IN, msgContext
                    .getProperty(MessageContext.TRANSPORT_IN));
            responseMessageContext.setTransportIn(msgContext.getTransportIn());
            responseMessageContext.setTransportOut(msgContext.getTransportOut());
            handleResponse(responseMessageContext);
            return responseMessageContext;
        }

    /**
     * This class is the workhorse for a non-blocking invocation that uses a two
     * way transport.
     */
    private class NonBlockingInvocationWorker implements Runnable {
        private MessageContext msgctx;
        private AxisCallback axisCallback;

        public NonBlockingInvocationWorker(MessageContext msgctx ,
                                           AxisCallback axisCallback) {
            this.msgctx = msgctx;
            this.axisCallback =axisCallback;
        }

        @Override
		public void run() {
            try {
                // send the request and wait for response
                MessageContext response = send(msgctx);
                // call the callback
                if (response != null) {
                    SOAPEnvelope resenvelope = response.getEnvelope();

                    if (resenvelope.hasFault()) {
                        SOAPBody body = resenvelope.getBody();
                        // If a fault was found, create an AxisFault with a MessageContext so that
                        // other programming models can deserialize the fault to an alternative form.
                        AxisFault fault = new AxisFault(body.getFault(), response);
                        if (axisCallback != null) {
                            if (options.isExceptionToBeThrownOnSOAPFault()) {
                                axisCallback.onError(fault);
                            } else {
                                axisCallback.onFault(response);
                            }
                        }

                    } else {
                        if (axisCallback != null) {
                            axisCallback.onMessage(response);
                        }

                    }
                }

            } catch (Exception e) {
                if (axisCallback != null) {
                    axisCallback.onError(e);
                }

            } finally {
                if (axisCallback != null) {
                    axisCallback.onComplete();
                }
            }
        }
    }
    }
}

