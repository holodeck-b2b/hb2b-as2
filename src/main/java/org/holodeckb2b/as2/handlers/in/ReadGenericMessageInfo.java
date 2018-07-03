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

import java.util.Map;

import org.apache.axis2.context.MessageContext;
import org.holodeckb2b.as2.packaging.GenericMessageInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.handler.BaseHandler;

/**
 * Is the <i>in_flow</i> handler that reads the HTTP headers of the incoming AS2 message to get the general message
 * meta-data common to all AS2 messages. This handler <b>does not check</b> whether the AS2 headers required for
 * processing the message (like AS2-To and AS2-From) are in the message. Such validation is done later in the 
 * processing pipe line.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 * @see GenericMessageInfo
 */
public class ReadGenericMessageInfo extends BaseHandler {

	/**
	 * To handle also cases where the other implementation returns a negative MDN with HTTP 400/500 this handler
	 * also runs in the <i>IN_FAULT_FLOW≤/i>
	 */
    @Override
    protected byte inFlows() {
        return IN_FLOW | IN_FAULT_FLOW;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected InvocationResponse doProcessing(MessageContext mc) throws Exception {
        log.debug("Get http headers for general AS2 message info");
		Map<String, String> httpHeaders = (Map<String, String>) mc.getProperty(MessageContext.TRANSPORT_HEADERS);
        log.debug("Parse the HTTP header and store data in msgCtx for further processing");
        mc.setProperty(Constants.MC_AS2_GENERAL_DATA, new GenericMessageInfo(httpHeaders));

        return InvocationResponse.CONTINUE;
    }
}
