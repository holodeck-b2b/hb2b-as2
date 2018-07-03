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

import org.apache.axis2.context.MessageContext;
import org.holodeckb2b.as2.packaging.GenericMessageInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.as2.util.PModeFinder;
import org.holodeckb2b.common.handler.BaseHandler;
import org.holodeckb2b.common.messagemodel.util.MessageUnitUtils;
import org.holodeckb2b.ebms3.axis2.MessageContextUtils;
import org.holodeckb2b.ebms3.errors.ProcessingModeMismatch;
import org.holodeckb2b.interfaces.persistency.entities.IMessageUnitEntity;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.module.HolodeckB2BCore;

/**
 * Is the <i>in_flow</i> handler that determines which P-Mode governs the processing of the received AS2 message. The
 * actual finding of the P-Mode is done by the {@link PModeFinder}. If the P-Mode cannot be determined a
 * <i>ProcessingModeMismatch</i> error will be generated. How this error should be reported is determined in ...
 * @todo Add ref to error processor handler
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class FindPMode extends BaseHandler {

	/**
	 * To handle also cases where the other implementation returns a negative MDN with HTTP 400/500 this handler
	 * also runs in the <i>IN_FAULT_FLOW≤/i>
	 */
    @Override
    protected byte inFlows() {
        return IN_FLOW | IN_FAULT_FLOW;
    }

    @Override
    protected InvocationResponse doProcessing(MessageContext mc) throws Exception {

        // First get the message unit and general message info from msg context
        GenericMessageInfo msgInfo = (GenericMessageInfo) mc.getProperty(Constants.MC_AS2_GENERAL_DATA);
        IMessageUnitEntity msgUnit = MessageContextUtils.getPrimaryMessageUnit(mc);
        // If this info is misisng something has probably already gone wrong and we don't need to find a P-Mode
        if (msgInfo == null || msgUnit == null)
            return InvocationResponse.CONTINUE;

        log.debug("Finding P-Mode for " + MessageUnitUtils.getMessageUnitName(msgUnit)
                  + "[msgId=" +  msgUnit.getMessageId() + ",sender=" + msgInfo.getFromPartyId()
                  + ",receiver=" + msgInfo.getToPartyId() + "]");
        IPMode pmode = PModeFinder.findForReceivedMessage(msgUnit, 
        												  msgInfo.getFromPartyId(), msgInfo.getToPartyId(),
        												  mc);
                
        if (pmode == null) {
            // No matching P-Mode could be found for this message, return error
            log.error("No P-Mode found for message [" + msgUnit.getMessageId() + "], unable to process it!");
            final ProcessingModeMismatch   noPmodeIdError = new ProcessingModeMismatch();
            noPmodeIdError.setRefToMessageInError(msgInfo.getMessageId());
            noPmodeIdError.setErrorDetail("Can not process message [msgId=" + msgUnit.getMessageId()
                                        + "] because no processing configuration was found for the message!");
            MessageContextUtils.addGeneratedError(mc, noPmodeIdError);
            log.debug("Set the processing state of this message to failure");
            HolodeckB2BCore.getStorageManager().setProcessingState(msgUnit, ProcessingState.FAILURE);
        } else {
            log.debug("Found P-Mode [" + pmode.getId() + "] for message [" + msgUnit.getMessageId() + "]");
            HolodeckB2BCore.getStorageManager().setPModeId(msgUnit, pmode.getId());
        }

        return InvocationResponse.CONTINUE;
    }
}
