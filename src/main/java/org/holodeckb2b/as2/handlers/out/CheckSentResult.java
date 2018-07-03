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

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.wsdl.WSDLConstants;

/**
 * Is the <i>out_flow</i> handler responsible for changing the processing state of the message unit that are and has 
 * been sent out in the current message. The basic processing is the same as for the AS4 message exchange, but whether
 * the message exchange was successful is now also depending on the HTTP result code (if Holodeck B2B is initiator).
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class CheckSentResult extends org.holodeckb2b.ebms3.handlers.outflow.CheckSentResult {

	@Override
	protected boolean isSuccessful(final MessageContext msgCtx) {
		if (isInFlow(INITIATOR)) {
			MessageContext inMessageCtx = null;		
			try {
				inMessageCtx = msgCtx.getOperationContext().getMessageContext(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
			} catch (AxisFault noMsgCtx) {
				log.error("No response message context available!");
			}
			return (msgCtx.getFailureReason() == null) && inMessageCtx != null && !inMessageCtx.isProcessingFault(); 
		} else
			return msgCtx.getFailureReason() == null;					
	}
}
