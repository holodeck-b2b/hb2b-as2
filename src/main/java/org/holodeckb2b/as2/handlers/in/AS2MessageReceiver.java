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

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.engine.MessageReceiver;
import org.apache.axis2.transport.TransportUtils;
import org.apache.axis2.util.MessageContextBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.holodeckb2b.ebms3.constants.MessageContextProperties;

/**
 * Implements the Axis2 {@link MessageReceiver} interface for use with the AS2 service. It checks whether a response
 * should be send and if so sets up the outgoing message context and triggers sending of the response. 
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class AS2MessageReceiver implements MessageReceiver {
    private Log     log = LogFactory.getLog("org.holodeckb2b.msgproc.AS2.CheckForResponse");
	
    @Override
    public void receive(MessageContext messageCtx) throws AxisFault {
        log.debug("Check if a response must be sent");
        final Boolean responseRequired = (Boolean) messageCtx.getProperty(MessageContextProperties.RESPONSE_REQUIRED);
        if (responseRequired != null && responseRequired.booleanValue()) {
            log.debug("There is a response to be sent, prepare message context and start send process");
            final MessageContext outMsgContext = MessageContextBuilder.createOutMessageContext(messageCtx);
            // Also add an empty SOAP envelope to the message context to satisfy any handler expecting one
            outMsgContext.setEnvelope(TransportUtils.createSOAPEnvelope(null));
            // Start the outgoing flow
            AxisEngine.send(outMsgContext);
        } else {
            log.debug("No response required, done processing");
        }    	
    }	
}
