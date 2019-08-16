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


import java.util.Map;

import org.apache.axis2.engine.Handler.InvocationResponse;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.holodeckb2b.as2.packaging.GenericMessageInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.handlers.AbstractBaseHandler;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.core.handlers.MessageProcessingContext;
import org.holodeckb2b.interfaces.messagemodel.IMessageUnit;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;

/**
 * Is the <i>out_flow</i> handler responsible for adding the AS2 HTTP headers to the message. The headers explicitly
 * defined in the specification are "AS2-Version", "AS2-To" and "AS2-From" (see <a href=
 * "https://tools.ietf.org/html/rfc4130#section-6.1">section 6.1</a>). However the specification also mentioned the use
 * of the "Message-id", "Subject", "Date", "Original-Recipient" and "Final-Recipient" headers. These are therefore also
 * added to the message.
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class AddHeaders extends AbstractBaseHandler {

    @Override
    protected InvocationResponse doProcessing(MessageProcessingContext procCtx, Log log) throws Exception {

    	log.debug("Getting already set HTTP headers");
        @SuppressWarnings("unchecked")
		Map<String, String> httpHeaders = (Map<String, String>) procCtx.getParentContext()
																	   .getProperty(HTTPConstants.HTTP_HEADERS);

        // Check whether this message contains a User or Signal Message
        final IMessageUnit primaryMsg = procCtx.getPrimaryMessageUnit();
        GenericMessageInfo msgInfo = null;
        if (primaryMsg instanceof IUserMessage)
            msgInfo = new GenericMessageInfo((IUserMessage) primaryMsg);
        else
            msgInfo = (GenericMessageInfo) procCtx.getProperty(Constants.CTX_AS2_MDN_DATA);

        if (msgInfo != null) {
	        log.debug("Adding the generic AS2 HTTP headers");
	        if (!Utils.isNullOrEmpty(httpHeaders))
	        	httpHeaders.putAll(msgInfo.getAsHTTPHeaders());
	        else
	        	procCtx.getParentContext().setProperty(HTTPConstants.HTTP_HEADERS, msgInfo.getAsHTTPHeaders());
        } else 
        	log.debug("No AS2 headers to be set");
        
        return InvocationResponse.CONTINUE;
    }

}

