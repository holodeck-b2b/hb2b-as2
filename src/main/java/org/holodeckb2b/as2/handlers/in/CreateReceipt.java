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

import java.util.Collections;

import org.apache.axis2.context.MessageContext;
import org.holodeckb2b.as2.messagemodel.MDNMetadataFactory;
import org.holodeckb2b.as2.messagemodel.MDNRequestOptions;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.messagemodel.Receipt;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.constants.MessageContextProperties;
import org.holodeckb2b.ebms3.util.AbstractUserMessageHandler;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.general.ReplyPattern;
import org.holodeckb2b.interfaces.persistency.entities.IReceiptEntity;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.pmode.ILeg;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.IReceiptConfiguration;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.module.HolodeckB2BCore;

/**
 * Is the <i>in_flow</i> handler responsible for creation of a <i>Receipt</i> (=AS2 MDN) for the received <i>User
 * Message</i> if such is needed. Whether a <i>Receipt</i> should be created depends on the P-Mode configuration or a
 * explicit request by the Sender. The same applies to the Receipt options, like signing and whether it should be send
 * as response or asynchronously. These are also determined based on the P-Mode or the Sender's request. The Sender's
 * request for a Receipt/MDN is retrieved from the message by the {@link CheckMDNRequest} handler.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class CreateReceipt extends AbstractUserMessageHandler {

    @Override
    protected byte inFlows() {
        return IN_FLOW | RESPONDER;
    }

    @Override
    protected InvocationResponse doProcessing(MessageContext mc, IUserMessageEntity userMessage) throws Exception {
        IReceiptConfiguration rcptConfig = null;
        MDNRequestOptions mdnRequest = null;

        log.debug("Check P-Mode if Receipt should be created for message [msgId=" + userMessage.getMessageId() + "]");
        final IPMode pmode = HolodeckB2BCoreInterface.getPModeSet().get(userMessage.getPModeId());
        if (pmode == null)
            // The P-Mode configurations has changed and does not include this P-Mode anymore!
            log.error("P-Mode " + userMessage.getPModeId() + " not found in current P-Mode set!");
        else
            // AS2 is always a one-way MEP
            rcptConfig = pmode.getLeg(ILeg.Label.REQUEST).getReceiptConfiguration();

        // If P-Mode doesn't configure receipt, check if sender requested a MDN
        if (rcptConfig == null) {
            log.debug("No Receipt configuration in P-Mode, checking message if sender requested one");
            mdnRequest = (MDNRequestOptions) mc.getProperty(Constants.MC_AS2_MDN_REQUEST);
            log.debug((mdnRequest == null ? "No " : "") + "MDN requested by sender of message");
        } else
            log.debug("Receipt is configured in P-Mode");

        if (rcptConfig != null || mdnRequest != null) {
            log.debug("Receipt requested for this message exchange, create new Receipt signal");
            final Receipt rcptData = new Receipt();
            // Copy some meta-data to receipt
            rcptData.setRefToMessageId(userMessage.getMessageId());
            rcptData.setPModeId(userMessage.getPModeId());            
            rcptData.setContent(Collections.singletonList(MDNMetadataFactory.createMDN(pmode, mdnRequest, mc)
            																.getAsXML()));
            log.debug("Store the Receipt for sending");
            IReceiptEntity receipt = (IReceiptEntity) HolodeckB2BCore.getStorageManager()
                                                                                .storeOutGoingMessageUnit(rcptData);
            log.debug("Saved Receipt [msgId=" + receipt.getMessageId() + "] for received message [msgId="
                        + userMessage.getMessageId() + "]");

            // Now check if the Receipt should be send sync or async, this can be configured both in P-Mode and by
            // sender's request. The latter takes precedence
            if ((rcptConfig != null && rcptConfig.getPattern() == ReplyPattern.CALLBACK)
               || (mdnRequest != null && !Utils.isNullOrEmpty(mdnRequest.getReplyTo()))) {
                log.debug("The Receipt should be send back to sender asynchronously");
                HolodeckB2BCore.getStorageManager().setProcessingState(receipt, ProcessingState.READY_TO_PUSH);
            } else {
                log.debug("The Receipt should be send back to sender synchronously");
                mc.setProperty(MessageContextProperties.RESPONSE_RECEIPT, receipt);
                mc.setProperty(MessageContextProperties.RESPONSE_REQUIRED, true);
            }
        }

        return InvocationResponse.CONTINUE;
    }
}
