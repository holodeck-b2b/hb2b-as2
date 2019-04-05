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
import org.apache.axis2.engine.MessageReceiver;
import org.apache.axis2.transport.TransportUtils;
import org.holodeckb2b.common.handler.DefaultMessageReceiver;

/**
 * Implements the Axis2 {@link MessageReceiver} interface for use with the AS2 service. It checks whether a response
 * should be send and if so sets up the outgoing message context and triggers sending of the response. 
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class AS2MessageReceiver extends DefaultMessageReceiver {

	@Override
	protected void prepareOutMessageContext(final MessageContext outMsgContext) throws AxisFault {	    
        // Also add an empty SOAP envelope to the message context to satisfy any handler expecting one
	    outMsgContext.setEnvelope(TransportUtils.createSOAPEnvelope(null));
    }	
}
