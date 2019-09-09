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

import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.messagemodel.MDNMetadata;
import org.holodeckb2b.as2.packaging.MDNInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.handlers.AbstractBaseHandler;
import org.holodeckb2b.common.messagemodel.Receipt;
import org.holodeckb2b.common.util.MessageIdUtils;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.persistency.entities.IReceiptEntity;

/**
 * Is the <i>in_flow</i> handler that converts the (already parsed) MDN into a Receipt message unit when the MDN is a 
 * positive acknowledgement, i.e. does not contain a disposition modifier or any other failures, errors or warnings.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class ReadReceipt extends AbstractBaseHandler {
	
    @Override
    protected InvocationResponse doProcessing(IMessageProcessingContext procCtx, Logger log) throws Exception {

        MDNInfo mdn = (MDNInfo) procCtx.getProperty(Constants.CTX_AS2_MDN_DATA);
        // Only positive MDNs are converted 
        if (mdn != null && mdn.getModifierSeverity() == null
                        && Utils.isNullOrEmpty(mdn.getFailures()) && Utils.isNullOrEmpty(mdn.getErrors())
                        && Utils.isNullOrEmpty(mdn.getWarnings())) {
            log.debug("Received MDN is a positive acknowledgement, convert into a Receipt");
            final Receipt receipt = new Receipt();
            /* First get the generic meta-data as messageId, timestamp and referenced message. If either the messageId
               or timestamp are not included in the message they will be set now. That these are generated by HB2B will
               be indicated in the MDNMetadataXML object which will automatically set the correct indicators based on
               the availibility of these fields
            */
            receipt.setMessageId(MessageIdUtils.stripBrackets(mdn.getMessageId()));
            receipt.setTimestamp(mdn.getTimestamp());
            // Because HB2B sends the messageId with brackets they should be part of the reference in the MDN, but
            // need to stripped for further processing
            receipt.setRefToMessageId(MessageIdUtils.stripBrackets(mdn.getOrigMessageId()));            
            // Now read the MDN specific fields and convert them to XML representation which can be stored as Receipt's
            // content
            receipt.addElementToContent(new MDNMetadata(mdn).getAsXML());
            log.debug("Converted AS2 MDN into Receipt, storing Receipt in database");
            procCtx.addReceivedReceipt((IReceiptEntity) HolodeckB2BCore.getStorageManager()
                                                                       .storeIncomingMessageUnit(receipt));
            log.info("Positive MDN/Receipt [msgId=" + receipt.getMessageId() + "] received for message with id:"
                     + receipt.getRefToMessageId());
        }

        return InvocationResponse.CONTINUE;
    }
}
