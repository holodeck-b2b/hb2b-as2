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
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as2.handlers.in.ProcessGeneratedErrors;
import org.holodeckb2b.as2.packaging.MDNInfo;
import org.holodeckb2b.as2.packaging.MDNTransformationException;
import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.common.handlers.AbstractBaseHandler;
import org.holodeckb2b.commons.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;
import org.holodeckb2b.interfaces.storage.IErrorMessageEntity;

/**
 * Is the <i>out_flow</i> handler responsible for checking if the message contains a <i>Error</i> Signal message
 * unit which should be reported to the sender of the message in error. When the message in error is an User Message
 * the error must be reported using a MDN, for Signals however error reporting should be done using a HTTP error
 * status. If the error is not related to any message unit (because there was an unexpected error in processing) the
 * error is also reported by responding with an HTTP error status code.
 * <p>As the information for the MDN to be created needs to be retrieved from the received message the MDN meta-data
 * is already created by the {@link ProcessGeneratedErrors} <i>in_flow</i> handler.
 * <p>The Error signal to be reported is included in message processing context which contains a collection of Error
 * entity objects for alignment with the other protocols, but since in AS2 there can be only one MDN it must not
 * include more then one signal. If it contains more than one, only the first Error will be included in the message and
 * the others are ignored.
 * <p>Note that this handler does not add the AS2 related HTTP headers to the message as these as generic to any sent
 * AS2 message and therefore added in the {@link AddHeaders} handler.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class PackageErrorSignal extends AbstractBaseHandler {

    @Override
    protected InvocationResponse doProcessing(IMessageProcessingContext procCtx, Logger log) throws Exception {
        // Check if the message includes a error. Although there can be just one Signal in AS2, for alignment
        // with other protocols the context contains collection of Error objects
        Collection<IErrorMessageEntity> errors = procCtx.getSendingErrors();

        if (Utils.isNullOrEmpty(errors))
        	// No Errors to package
        	return InvocationResponse.CONTINUE;
        else if (errors.size() > 1)
        	log.error("Message contains more than 1 Error message unit! Using first, ignoring others");

        final IErrorMessageEntity error = errors.iterator().next();
        // If the Error should be reported using a MDN, it should already contain the MDN meta-data, so we just try
        // to get the MDN data and if it works send the MDN or otherwise use HTTP status codes.
        MDNInfo mdnObject;
        try {
        	mdnObject = new MDNInfo(error);
        } catch (MDNTransformationException noMDNInfo) {
        	mdnObject = null;
        }
        if (mdnObject != null) {
        	log.debug("Error must be packaged as MDN, create the MIME multi-part and add it to message context");
            MimeBodyPart mdnPart = mdnObject.toMimePart();
            procCtx.setProperty(Constants.CTX_MIME_ENVELOPE, mdnPart);
            procCtx.setProperty(org.apache.axis2.Constants.Configuration.CONTENT_TYPE,
                                                 new ContentType(mdnPart.getContentType()).getMediaType().toString());
            // For easy access to MDN options the MDN object is also stored in the msgCtx
            procCtx.setProperty(Constants.CTX_AS2_MDN_DATA, mdnObject);
        } else if (!procCtx.isHB2BInitiated()) {
        	log.debug("Error must be reported using HTTP Error Code");
        	// If the Error has a reference to another message unit, use 403 (Bad Request). If there is no reference,
        	// some error occurred internally => 500 (Server error)
        	if (!Utils.isNullOrEmpty(error.getRefToMessageId()))
        		procCtx.getParentContext().setProperty(org.apache.axis2.Constants.HTTP_RESPONSE_STATE, "403");
        	else
        		procCtx.getParentContext().setProperty(org.apache.axis2.Constants.HTTP_RESPONSE_STATE, "500");
        } else {
        	// Seems we're trying to async an Error without reference to User Message. This is not allowed.
        	log.error("Cannot asynchronously send Error without reference to message unit in error!");
        	HolodeckB2BCore.getStorageManager().setProcessingState(error, ProcessingState.DONE);
        }

        return InvocationResponse.CONTINUE;
    }

}
