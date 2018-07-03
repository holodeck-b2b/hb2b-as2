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

import java.util.Collection;

import org.apache.axis2.context.MessageContext;
import org.holodeckb2b.common.handler.BaseHandler;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.axis2.MessageContextUtils;
import org.holodeckb2b.ebms3.constants.MessageContextProperties;
import org.holodeckb2b.interfaces.persistency.entities.IErrorMessageEntity;
import org.holodeckb2b.interfaces.persistency.entities.IReceiptEntity;

/**
 * Is the first handler of the <i>out_flow</i> responsible for preparing a response by checking if the handlers in the
 * <i>in_flow</i> prepared a Signl Message Unit that should be send as response. If they did the Signal's entity object
 * is copied to the out flow message context to make it available to the other handlers in the out flow.<br>
 * Because in AS2 there can be only one "message unit" in the request there can also be only one Signal to be send as
 * response, i.e. either a Receipt or an Error. This handler is therefore simpler then the same AS4 handler that needs
 * to take bundling into account.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class PrepareResponseMessage extends BaseHandler {

	/**
	 * This handler should run in both the normal as well as the fault out flow because it needs to add any errors
	 * to be reported to the message context. 
	 */
    @Override
    protected byte inFlows() {
        return OUT_FLOW | OUT_FAULT_FLOW;
    }

    @Override
    protected InvocationResponse doProcessing(MessageContext mc) throws Exception {
        // Check if there is a receipt signal messages to be included
        log.debug("Check for receipt signal to be included");
        final IReceiptEntity receipt = (IReceiptEntity) MessageContextUtils.getPropertyFromInMsgCtx(mc,
                                                                          MessageContextProperties.RESPONSE_RECEIPT);
        if (receipt != null) {
            log.debug("Response contains a receipt signal");
            // Copy to current context so it gets processed correctly
            MessageContextUtils.addReceiptToSend(mc, receipt);
        } else {
            // There may be an Error that needs to be included
            log.debug("Check for error signal generated during in flow to be included");
            // Although in AS2 it can be just one, the msg context property is a collection to keep processing generic
            @SuppressWarnings("unchecked")
			final Collection<IErrorMessageEntity> errors =
                            (Collection<IErrorMessageEntity>) MessageContextUtils.getPropertyFromInMsgCtx(mc,
                                                                                  MessageContextProperties.OUT_ERRORS);
            if (!Utils.isNullOrEmpty(errors)) {
                log.debug("Response contains Error signal");
                MessageContextUtils.addErrorSignalToSend(mc, errors.iterator().next());
            }
        }
        return InvocationResponse.CONTINUE;
    }
}

