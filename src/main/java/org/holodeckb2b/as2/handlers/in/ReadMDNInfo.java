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

import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.packaging.GenericMessageInfo;
import org.holodeckb2b.as2.packaging.MDNInfo;
import org.holodeckb2b.as2.packaging.MDNTransformationException;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.errors.InvalidHeader;
import org.holodeckb2b.common.handlers.AbstractBaseHandler;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;

/**
 * Is the <i>in_flow</i> handler that checks if the incoming AS2 message is an MDN and if it is parses the MDN
 * MIME part from the message so it can be converted into a Receipt or Error message unit by the next handlers. This
 * handler only checks whether the MDN can be parsed, it does not check whether its contents are valid.  
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class ReadMDNInfo extends AbstractBaseHandler {

    @Override
    protected InvocationResponse doProcessing(IMessageProcessingContext procCtx, Logger log) {
 
    	final BodyPart mainPart = (BodyPart) procCtx.getProperty(Constants.CTX_MAIN_MIME_PART);
    	try {			
			if (!mainPart.isMimeType(Constants.REPORT_MIME_TYPE) 
					&& !mainPart.isMimeType(Constants.MDN_DISPOSITION_MIME_TYPE)) 
				// This is not an MDN
				return InvocationResponse.CONTINUE;
    	} catch (MessagingException contentTypeError) {
			// The message does not contain a valid C-T header, ignore and handle a user message with unkown content
			log.warn("Problem parsing the Content-Type header! Details: " + contentTypeError.getMessage());
			return InvocationResponse.CONTINUE;
    	}    	
	    log.debug("Parsing MDN contained in the message");
	    try {
	        log.debug("Get the general message info of the MDN from msgCtx");
	        GenericMessageInfo generalInfo = (GenericMessageInfo) procCtx.getProperty(Constants.CTX_AS2_GENERAL_DATA);
	        log.debug("Received MDN with msgId [" + generalInfo.getMessageId() + "] from ["
	                   + generalInfo.getFromPartyId() + "] addressed to [" + generalInfo.getToPartyId() + "]");
	        log.debug("Parse the MDN Mime part");
	        MDNInfo mdnObject = new MDNInfo(generalInfo, mainPart);
	        log.debug("Read all data from the MDN, storing it in msgCtx for further processing");
	        procCtx.setProperty(Constants.CTX_AS2_MDN_DATA, mdnObject);
	    } catch (MDNTransformationException invalidMDN) {
	        log.error("Could not parse the MDN! Error details: " + invalidMDN.getMessage());
			// We use the InvalidHeader error to signal this
	        procCtx.addGeneratedError(new InvalidHeader("Invalid MDN! - " + invalidMDN.getMessage()));
	    }		
        return InvocationResponse.CONTINUE;
    }    
}
