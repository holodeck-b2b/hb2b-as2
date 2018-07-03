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

import static org.apache.axis2.Constants.Configuration.MESSAGE_FORMATTER;
import static org.apache.axis2.addressing.AddressingConstants.DISABLE_ADDRESSING_FOR_OUT_MESSAGES;

import org.apache.axis2.context.MessageContext;
import org.holodeckb2b.as2.packaging.AS2MessageFormatter;
import org.holodeckb2b.common.handler.BaseHandler;

/**
 * Is the <i>out_flow</i> handler that ensure that the AS2 message will be correctly formatted when send by Axis.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class SetMessageFormatter extends BaseHandler {

	/**
	 * Errors can be reported both in the normal as well in the fault flow
	 */
    @Override
    protected byte inFlows() {
        return OUT_FLOW | OUT_FAULT_FLOW;
    }

    @Override
    protected InvocationResponse doProcessing(MessageContext mc) throws Exception {
        mc.setProperty(MESSAGE_FORMATTER, new AS2MessageFormatter());
        mc.setDoingREST(true);
        mc.setProperty(DISABLE_ADDRESSING_FOR_OUT_MESSAGES, Boolean.TRUE);
        return InvocationResponse.CONTINUE;
    }

}
