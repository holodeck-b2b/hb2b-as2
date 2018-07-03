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
package org.holodeckb2b.as2.axis2;

import static org.apache.axis2.client.ServiceClient.ANON_OUT_IN_OP;

import java.util.UUID;

import org.apache.axis2.AxisFault;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.holodeckb2b.as2.AS2Module;

/**
 * Is a specialized version of an {@link AxisService} preconfigured for sending the AS2 message. It ensures that the
 * Holodeck B2B AS2 module will be engaged and sets a specific {@link AxisOperation} that can handle both empty 
 * responses and ones that contain a message. 
 *  
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class AS2SendService extends AxisService {

	public AS2SendService() throws AxisFault {
		super("AS2-SND-SVC-" + UUID.randomUUID());
        final OutOptInAS2Operation outInOperation = new OutOptInAS2Operation(ANON_OUT_IN_OP);
        addOperation(outInOperation);
        addModuleref(AS2Module.AXIS_MODULE_NAME);
        addParameter(org.apache.axis2.Constants.Configuration.DISABLE_REST, Boolean.FALSE);
	}
}
