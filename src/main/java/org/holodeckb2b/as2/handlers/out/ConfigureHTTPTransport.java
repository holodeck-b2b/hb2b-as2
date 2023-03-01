/**
 * Copyright (C) 2014 The Holodeck B2B Team, Sander Fieten
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

import org.holodeckb2b.as2.messagemodel.MDNRequestOptions;
import org.holodeckb2b.as2.packaging.MDNInfo;
import org.holodeckb2b.common.handlers.AbstractConfigureHTTPTransport;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.messagemodel.IReceipt;
import org.holodeckb2b.interfaces.messagemodel.ISignalMessage;
import org.holodeckb2b.interfaces.persistency.entities.IMessageUnitEntity;
import org.holodeckb2b.interfaces.pmode.ILeg;

/**
 * Is the <i>out flow</i> handler that configures the actual message transport over the HTTP protocol and sets the
 * target URL where the message should be send.
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 * @see AbstractConfigureHTTPTransport
 */
public class ConfigureHTTPTransport extends AbstractConfigureHTTPTransport {

	/**
	 * Gets the destination URL for the given message unit. In case of a Signal Message the URL can be given by the
	 * Sender of the User Message the Signal applies to in the MDN request options.
	 *
	 * @param msgToSend		The message unit being send
	 * @param leg			The P-Mode configuration parameters for this leg
	 * @param mc			The message processing context
	 * @return				The destination URL, <code>null</code> if URL cannot be determined
	 */
	@Override
	protected String getDestinationURL(IMessageUnitEntity msgToSend, ILeg leg, IMessageProcessingContext procCtx) {
		String destURL = null;

		if (msgToSend instanceof ISignalMessage) {
			final MDNInfo mdn = (MDNInfo) procCtx.getProperty(org.holodeckb2b.as2.util.Constants.CTX_AS2_MDN_DATA);
			MDNRequestOptions mdnRequest = mdn.getMDNRequestOptions();
			destURL = mdnRequest != null ? mdnRequest.getReplyTo() : null;
			if (Utils.isNullOrEmpty(destURL))
	            try {
	                if (msgToSend instanceof IReceipt)
	                     destURL = leg.getReceiptConfiguration().getTo();
	                 else
	                     destURL = leg.getUserMessageFlow().getErrorHandlingConfiguration().getReceiverErrorsTo();
	            } catch (NullPointerException npe) {}
		} else
			destURL = leg.getProtocol() != null ? leg.getProtocol().getAddress() : null;

        return destURL;
	}

}
