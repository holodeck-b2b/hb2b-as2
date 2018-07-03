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

import javax.mail.BodyPart;
import javax.mail.MessagingException;

import org.apache.axis2.context.MessageContext;
import org.holodeckb2b.as2.packaging.GenericMessageInfo;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.handler.BaseHandler;
import org.holodeckb2b.common.messagemodel.PartyId;
import org.holodeckb2b.common.messagemodel.Property;
import org.holodeckb2b.common.messagemodel.TradingPartner;
import org.holodeckb2b.common.messagemodel.UserMessage;
import org.holodeckb2b.common.util.MessageIdUtils;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.axis2.MessageContextUtils;
import org.holodeckb2b.ebms3.constants.MessageContextProperties;
import org.holodeckb2b.ebms3.errors.InvalidHeader;
import org.holodeckb2b.module.HolodeckB2BCore;

/**
 * Is the <i>in_flow</i> handler that checks whether the message contains a business document and if it does creates
 * a new <i>User Message</i> message unit for it. It is assumed that any message that is not an MDN contains a business
 * document.
 * <p>The only required information to accept the message as a User Message is the existence of the <i>AS2-From</i>
 * and <i>AS2-To</i> headers. Note that although the <i>Message-id</i> header should be provided, it is not required
 * by this handler. If not supplied a message-id will be generated.       
 * <p>Because the message may be encrypted and/or compressed the actual payload may not be accessible yet. Therefore
 * only the general meta-data is saved in the new message unit. Information on the payload is read and added to message
 * unit later by the {@link SavePayload} handler.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class ReadUserMessage extends BaseHandler {

	/**
	 * User Message can only be contained in messages that are received in a request, so this handler only needs
	 * to run in the RESPONDER flow.
	 */
    @Override
    protected byte inFlows() {
        return IN_FLOW | RESPONDER;
    }
    
    @Override
    protected InvocationResponse doProcessing(MessageContext mc) throws Exception {

    	final BodyPart mainPart = (BodyPart) mc.getProperty(Constants.MC_MAIN_MIME_PART);
    	try {			
			if (mainPart.isMimeType(Constants.REPORT_MIME_TYPE) 
				|| mainPart.isMimeType(Constants.MDN_DISPOSITION_MIME_TYPE))
				// This is an MDN and can be ignored
				return InvocationResponse.CONTINUE;
    	} catch (MessagingException ctParseError) {
    		log.warn("Could not determine the MIME type of the main body part!");
    	}

        log.debug("Get the general message info of the User Message from msgCtx");
        GenericMessageInfo generalInfo = (GenericMessageInfo) mc.getProperty(Constants.MC_AS2_GENERAL_DATA);
        
        // Check that at least the party ids of the sender and receiver are included in the message
        final String fromId = generalInfo.getFromPartyId();
        final String toId = generalInfo.getToPartyId();        
        if (Utils.isNullOrEmpty(fromId) || Utils.isNullOrEmpty(toId)) {
        	log.error("Received message does not contain AS2-To and/or AS2-From header(s)!");
			// We use the InvalidHeader error to signal this
			MessageContextUtils.addGeneratedError(mc, 
											new InvalidHeader("Missing required AS2-To and/or AS2-From header(s)!"));
			return InvocationResponse.CONTINUE;
        }
        // Create a UserMessage to save in message database
        final UserMessage as2UserMessage = new UserMessage();
        // Brackets should be stripped from the messageId       
        as2UserMessage.setMessageId(MessageIdUtils.stripBrackets(generalInfo.getMessageId()));
        as2UserMessage.setTimestamp(generalInfo.getTimestamp());
        final TradingPartner sender = new TradingPartner();
        sender.addPartyId(new PartyId(fromId, null));
        as2UserMessage.setSender(sender);
        final TradingPartner receiver = new TradingPartner();
        receiver.addPartyId(new PartyId(toId, null));
        as2UserMessage.setReceiver(receiver);
        final String subject = generalInfo.getSubject();
        if (!Utils.isNullOrEmpty(subject))
            as2UserMessage.addMessageProperty(new Property("Subject", subject));

        log.info("Received User Message with msgId [" + as2UserMessage.getMessageId() + "] from [" + fromId 
        		+ "] addressed to [" + toId + "]");
        log.debug("Saving user message meta data to database and message context");
        mc.setProperty(MessageContextProperties.IN_USER_MESSAGE,
                       HolodeckB2BCore.getStorageManager().storeIncomingMessageUnit(as2UserMessage));
        log.debug("Saved user message meta data to database");
        return InvocationResponse.CONTINUE;
    }    
}
