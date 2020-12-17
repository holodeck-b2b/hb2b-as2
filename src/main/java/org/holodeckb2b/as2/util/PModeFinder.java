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
package org.holodeckb2b.as2.util;

import java.util.Collection;

import org.holodeckb2b.common.util.MessageUnitUtils;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.general.IPartyId;
import org.holodeckb2b.interfaces.messagemodel.Direction;
import org.holodeckb2b.interfaces.messagemodel.IMessageUnit;
import org.holodeckb2b.interfaces.messagemodel.ISignalMessage;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;
import org.holodeckb2b.interfaces.persistency.PersistenceException;
import org.holodeckb2b.interfaces.persistency.entities.IMessageUnitEntity;
import org.holodeckb2b.interfaces.pmode.ILeg;
import org.holodeckb2b.interfaces.pmode.IPMode;
import org.holodeckb2b.interfaces.pmode.IPModeSet;
import org.holodeckb2b.interfaces.pmode.ITradingPartnerConfiguration;

/**
 * Is a helper class for finding the correct P-Mode for a received AS2 message. Since the meta-data for AS2 messages is
 * limited to the identifiers of the sender and receiver the P-Modes for AS2 message exchanges are much simpler then the
 * "normal" P-Modes: One-Way P-Modes with only information on the <i>Initiator</i> and <i>Responder</i> and no further
 * business meta-data like <i>Service</i> and <i>Action</i>. As a result there can be only one P-Mode for AS2 message
 * exchanges per <i>Sender</i> and <i>Receiver</i>.
 * <p>Finding the P-Mode for an AS2 message therefore is also much simpler since we only need to check on the party
 * identifiers, if these match the P-Mode is found. To differentiate a "AS2 P-Mode" from the "normal P-Mode" a specific
 * value for the MEP-Binding parameter must be used: <i>http://holodeck-b2b.org/pmode/mepBinding/as2</i>.
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 * @see IPMode
 */
public class PModeFinder {

    /**
     * Finds the P-Mode for a received message unit that represents an AS2 message.
     * <p>A P-mode will be considered a match if the identifiers for the sender or receiver of the message or both match
     * to the identifiers of the trading partners configured in the P-Mode. This means that if the P-Mode only contains
     * identifiers for the Responding partner only these identifiers are matched and the others ignored. Matches are
     * therefore weighed meaning a P-Mode where both the Initiator and Responder match to the identifiers from the
     * message will score higher and be returned before a P-Mode where only one partner matches. If there is a mismatch
     * for either the initiator or responder identifiers the P-Mode is considered as a mismatch.
     * <p>If the received message is a Signal Message which does not contain the sender and receiver identifiers the
     * referenced message will be used to find the P-Mode. The referenced message is either the sent message if the
     * Signal is a response or the message referenced in the Signal.  
     * <p>This method will only find one matching P-Mode. This means that when multiple P-Modes with the highest match
     * score are found none is returned.
     *
     * @param as2Message    The message unit to find the P-Mode for, may be both a User and Signal Message
     * @param senderId      Party identifier of the sender of the AS2 message
     * @param receiverId    Party identifier of the receiver of the AS2 message
     * @param procCtx		The message processing context of the received message
     * @return          The P-Mode for the message unit if the message unit can be matched to a <b>single</b> P-Mode,
     *                  <code>null</code> if no P-Mode could be found for this message unit.
     */
    public static IPMode findForReceivedMessage(final IMessageUnit as2Message,
                                                final String senderId, final String receiverId,
                                                final IMessageProcessingContext procCtx) {
        final IPModeSet pmodes = HolodeckB2BCoreInterface.getPModeSet();
        IPMode    hPMode = null;
        int       hValue = 0;
        boolean   multiple = false;
        // Determine which identifier should be used for matching to Initiator and Responder. For the User Messages
        // the sender matches to Initiatior and received to Responder, for signals that are always responses it is
        // reversed, the sender should match to the Responder and the receiver to the Initiator.
        String initiatorId;
        String responderId;
        if (as2Message instanceof IUserMessage) {
            initiatorId = senderId;
            responderId = receiverId;
        } else {
            initiatorId = receiverId;
            responderId = senderId;
        }

        if (pmodes == null)
            return null;

        for (final IPMode p : pmodes.getAll()) {            
            if (!Constants.AS2_MEP_BINDING.equalsIgnoreCase(p.getMepBinding()))
            	// This P-Mode is not for handling AS2
            	continue;            
            else if (as2Message instanceof IUserMessage && p.getLeg(ILeg.Label.REQUEST).getProtocol() != null
                    && !Utils.isNullOrEmpty(p.getLeg(ILeg.Label.REQUEST).getProtocol().getAddress()))
            	// Received message is a user message, so P-Mode shouldn't be configured for sending
                continue;
            else if (as2Message instanceof ISignalMessage && (p.getLeg(ILeg.Label.REQUEST).getProtocol() == null
            		|| Utils.isNullOrEmpty(p.getLeg(ILeg.Label.REQUEST).getProtocol().getAddress())))
            	// Received message is a signal, so P-Mode shouldn't be configured for receiving  
            	continue;            	

            int cValue = 0;
            // Check match for Initiator id
            final Boolean isMatchInitiator = checkPartyId(p.getInitiator(), initiatorId);
            if (isMatchInitiator != null)
                if (isMatchInitiator)
                    cValue++;
                else
                    continue;
            // Check match for Responder id
            final Boolean isMatchResponder = checkPartyId(p.getResponder(), responderId);

            if (isMatchResponder != null)
                if (isMatchResponder)
                    cValue++;
                else
                	continue;

            // Does this P-Mode better match to the message meta data than the current highest match? For AS2 this means
            // that both the initiator and responder identifiers are specified and match
            if (cValue > hValue) {
                // Yes, it does, set it as new best match
                hValue = cValue;
                hPMode = p;
                multiple = false;
            } else if (cValue == hValue)
                // It has the same match as the current highest scoring one
                multiple = true;
        }

        if ((hPMode == null || multiple) && as2Message instanceof ISignalMessage) 
        	return findUsingReference((ISignalMessage) as2Message, procCtx);
        else 
        	// Only return a single P-Mode
        	return !multiple ? hPMode : null;
    }

    /**
     * Finds the P-Mode for the Signal Message using its reference to the original User Message. This reference should
     * be included in the message (in the <o>Original-MesssgeId</i> MDN field) but in case of a synchronous response
     * may also be derived from the outgoing message unit.  
     * 
     * @param signal	The signal to find the P-Mode for
     * @param procCtx	The message processing context of the signal message
     * @return			
     */
    protected static IPMode findUsingReference(final ISignalMessage signal, final IMessageProcessingContext procCtx) {
        IPMode pmode = null;
        // If the Signal is contained in a response it must be a reference to the sent message unit
        if (procCtx.isHB2BInitiated()) {
            final Collection<IMessageUnitEntity>  reqMUs = procCtx.getSendingMessageUnits();
            if (reqMUs.size() == 1)
                // Request contained one message unit, assuming error applies to it, use its P-Mode
               pmode = HolodeckB2BCore.getPModeSet().get(reqMUs.iterator().next().getPModeId());
            //else:  No or more than one message unit in request => can not be related to specific message unit
        } else
            // Use the message id included in the Signal to get the P-Mode
            pmode = getPModeFromRefdMessage(MessageUnitUtils.getRefToMessageId(signal));
        
        return pmode;
    }

    /**
     * Helper method to get the P-Mode from the referenced message unit.
     *
     * @param refToMsgId    The message id of the referenced message unit
     * @return              The PMode of the referenced message unit if it is found, or<br>
     *                      <code>null</code> if no message unit can be found for the given message id
     */
    private static IPMode getPModeFromRefdMessage(final String refToMsgId)  {
        IPMode pmode = null;
        try {
	        if (!Utils.isNullOrEmpty(refToMsgId)) {
	            Collection<IMessageUnitEntity> refdMsgUnits = HolodeckB2BCore.getQueryManager()
	                                                                         .getMessageUnitsWithId(refToMsgId,
	                                                                        		 				Direction.OUT);
	            if (!Utils.isNullOrEmpty(refdMsgUnits) && refdMsgUnits.size() == 1)
	                pmode = HolodeckB2BCoreInterface.getPModeSet().get(refdMsgUnits.iterator().next().getPModeId());
	        }
        } catch (PersistenceException databaseFailure) {}
        
        return pmode;
    }    	
    
    /**
     * Helper method to check that the given party identifier matches to the party identifier provider in the trading
     * partner configuration.
     *
     * @param partnerCfg    The partner configuration
     * @param expectedId    The expected identifier
     * @return  <code>null</code> if the partner configuration is <code>null</code> or does not contain any party
     *              identifiers,<br>
     *          <code>Boolean.TRUE</code> if the partner configuration contains just one identfier which matches to the
     *              given identifier,<br>
     *          <code>Boolean.FALSE</code> otherwise
     */
    private static Boolean checkPartyId(ITradingPartnerConfiguration partnerCfg, String expectedId) {
        if (partnerCfg != null) {
            final Collection<IPartyId> pmodeIds = partnerCfg.getPartyIds();
            if (!Utils.isNullOrEmpty(pmodeIds))
                return pmodeIds.size() == 1 && pmodeIds.iterator().next().getId().equals(expectedId);
        }
        return null;
    }	
}
