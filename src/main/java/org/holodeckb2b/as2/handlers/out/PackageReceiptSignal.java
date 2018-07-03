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

import javax.mail.internet.MimeBodyPart;

import org.apache.axiom.mime.ContentType;
import org.apache.axis2.context.MessageContext;
import org.holodeckb2b.as2.packaging.MDNInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.handler.BaseHandler;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.constants.MessageContextProperties;
import org.holodeckb2b.interfaces.persistency.entities.IReceiptEntity;

/**
 * Is the <i>out_flow</i> handler responsible for checking if the message contains a <i>Receipt</i> Signal message 
 * unit and therefore a MDN needs to be created. The Receipt signal to be sent is included in the {@link
 * MessageContextProperties#OUT_RECEIPTS} message context property. It contains a collection of Receipt entity objects 
 * for alignment with the other protocols, but since in AS2 there can be only one MDN it must not include more then 
 * one signal. If it contains more than one, only the first Receipt will be included in the message and the others are 
 * ignored.
 * <p>Note that this handler does not add the AS2 related HTTP headers to the message as these as generic to any sent 
 * AS2 message and therefore added in the {@link AddHeaders} handler.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class PackageReceiptSignal extends BaseHandler {

    @Override
    protected byte inFlows() {
        return OUT_FLOW;
    }

    @Override
    protected InvocationResponse doProcessing(MessageContext mc) throws Exception {
        // Check if the message includes a receipt. Although there can be just one Signal in AS2, for alignment
        // with other protocols the msg context property contains collection of Receipts objects 
        @SuppressWarnings("unchecked")
		Collection<IReceiptEntity> receipts = (Collection<IReceiptEntity>) mc.getProperty(
                                                                                 MessageContextProperties.OUT_RECEIPTS);

        if (Utils.isNullOrEmpty(receipts))
        	// No Receipts to package
        	return InvocationResponse.CONTINUE;
        else if (receipts.size() > 1)
        	log.error("Message contains more than 1 Receipt message unit! Using first, ignoring others");
        
        final MDNInfo mdnObject = new MDNInfo(receipts.iterator().next());
        log.debug("Create the MDN MIME multi-part and add it to message context");
        MimeBodyPart mdnPart = mdnObject.toMimePart();
        mc.setProperty(Constants.MC_MIME_ENVELOPE, mdnPart);
        mc.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE,
                                              new ContentType(mdnPart.getContentType()).getMediaType().toString());
        // For easy access to MDN options the MDN object is also stored in the msgCtx
        mc.setProperty(Constants.MC_AS2_MDN_DATA, mdnObject);

        return InvocationResponse.CONTINUE;
    }

}
