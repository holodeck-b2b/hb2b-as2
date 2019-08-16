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
package org.holodeckb2b.as2;

import org.holodeckb2b.as2.util.Constants;
import org.holodeckb2b.ebms3.pmode.BasicPModeValidator;
import org.holodeckb2b.interfaces.pmode.validation.IPModeValidator;

/**
 * Extends Holodeck B2B's default P-Mode validator {@link BasicPModeValidator} to support "AS2 P-Modes". These can be
 * recognized by a specific value for the <b>PMode.MEPBinding</p> parameter. This value however is not accepted by the
 * default validator and therefore added in this validator. Although AS2 uses somes parameters differently, or not at
 * all, the validation itself has not been changed. This could however be added in a later stage if decided that AS2
 * should have long term support (apart from migration support).  
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 * @see IPModeValidator
 */
public class PModeValidator extends BasicPModeValidator {

	/**
	 * Add the AS2 specific value for the MEPBinding parameter to list of allowed values.  
	 */
    static {
        VALID_MEP_BINDINGS.add(Constants.AS2_MEP_BINDING);
    }
}

