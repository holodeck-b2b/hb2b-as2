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

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.axis2.context.MessageContext;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.messagemodel.MDNRequestOptions;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.handlers.AbstractUserMessageHandler;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.core.handlers.MessageProcessingContext;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;

/**
 * Is the <i>in_flow</i> handler responsible for checking if a MDN is requested for the received <i>User Messsage</i>
 * and processing the MDN reply parameters. This is needed because in AS2 the sender of the <i>User Message</i> is
 * responsible for requesting the MDN and providing parameters for its construction like whether the MDN should be
 * returned synchronously or send back async. The sender can also specify whether the MDN should/must be signed and the
 * preferred hashing algorithm to calculate the MIC.
 * <p>As MDN are mapped to <i>Receipt</i> and <i>Error</i> message units in Holodeck B2B which are created in the next
 * handlers in the processing pipeline, the request for a MDN and its paramaters are stored by this handler in the
 * message context.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class CheckMDNRequest extends AbstractUserMessageHandler {

    @Override
    protected InvocationResponse doProcessing(final IUserMessageEntity userMessage, 
    										  final MessageProcessingContext procCtx, final Logger log) 
    												  												throws Exception {    	
        // Get the HTTP headers
        @SuppressWarnings("unchecked")
		final Map<String, String> headers = (Map<String, String>) procCtx.getParentContext()
																		 .getProperty(MessageContext.TRANSPORT_HEADERS);

        log.debug("Check if MDN is requested by checking existance of " + Constants.MDN_REQUEST_HEADER + " header.");
        final boolean mdnRequested = !Utils.isNullOrEmpty(headers.get(Constants.MDN_REQUEST_HEADER));
        if (mdnRequested) {
            MDNRequestOptions mdnOptions = new MDNRequestOptions();
            log.debug("An MDN is request, check if it should be send synchronously or asynchronously");
            mdnOptions.setReplyTo(headers.get(Constants.MDNREQ_REPLY_TO_HEADER));
            log.debug("Check is MDN should be signed");
            final String signedMDNReqHdr = headers.get(Constants.MDNREQ_SIGNING_OPTIONS_HEADER);
            if (Utils.isNullOrEmpty(signedMDNReqHdr)) {
                log.debug("No parameters for signed MDN provided");
                mdnOptions.setSignatureRequest(MDNRequestOptions.SignatureRequest.unsigned);
            } else {
            	try {
	                log.debug("Parse parameters for signed MDN");
	                final Map<String, MDNOption> signingOptions = parseMDNOptions(signedMDNReqHdr);
	                MDNOption o = signingOptions.get(Constants.MDNREQ_SIGNATURE_FORMAT);
	                mdnOptions.setSignatureRequest(o != null && o.required ? MDNRequestOptions.SignatureRequest.required
	                                                                       : MDNRequestOptions.SignatureRequest.optional);
	                mdnOptions.setSignatureFormat(o.value[0]); 
	                o = signingOptions.get(Constants.MDNREQ_MIC_ALGORITHMS);
	                if (o != null)
	                    mdnOptions.setPreferredHashingAlgorithms(Arrays.asList(o.value));
            	} catch (ParseException invalidMDNRequest) {
            		// The MDN request "disposition-notification-options" parameter is invalid
            		// => log as warning and assume nothing on signing of the requested MDN
            		log.warn(invalidMDNRequest.getMessage());
            	}
            }
            log.debug("Parsed the MDN request parameters, indicate in msg ctx that MDN is requested");
            procCtx.setProperty(Constants.CTX_AS2_MDN_REQUEST, mdnOptions);
        }

        return InvocationResponse.CONTINUE;
    }

    /**
     * Helper method to parse the parameters included in the <i>disposition-notification-options</i> header.
     *
     * @param headerValue        The value of the header
     * @return                   A Map containing all parameters included in the header
     * @throws ParseException    If the header value could not be parsed.
     */
    private Map<String, MDNOption> parseMDNOptions(final String headerValue) throws ParseException {
        try {
            final String[] parameters = headerValue.split(";");
            final Map<String, MDNOption> parameterMap = new HashMap<>(parameters.length);
            for (String p : parameters) {
                final int nameEnd = p.indexOf('=');
                final String name = p.substring(0, nameEnd).toLowerCase().trim();
                MDNOption o = new MDNOption();
                final int importanceEnd = p.indexOf(',', nameEnd);
                o.required = "required".equalsIgnoreCase(p.substring(nameEnd+1, importanceEnd).trim());
                // Now split the remaining value part into separate values
                String values[] = p.substring(importanceEnd + 1).split(",");
                o.value = new String[values.length];
                for(int i = 0; i < values.length; i++)
                	o.value[i] = values[i].trim();
                // Add parameter to the map
                parameterMap.put(name, o);
            }
            return parameterMap;
        } catch (Exception ex) {
            throw new ParseException("Could not parse the " + Constants.MDNREQ_SIGNING_OPTIONS_HEADER 
            						+ " header! Value was: " + headerValue, 0);
        }
    }

    /**
     * Represents the value(s) for the MDN parameters included in the "disposition-notification-options" header
     */
    class MDNOption {
        String[]  value;
        boolean   required;
    }	
}

