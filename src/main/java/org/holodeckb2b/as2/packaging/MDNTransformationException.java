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
package org.holodeckb2b.as2.packaging;

import org.holodeckb2b.interfaces.messagemodel.ISignalMessage;

/**
 * Indicates that an error occurred during the transformation/parsing of a received AS2 message or {@link 
 * ISignalMessage} object into a {@link MDNInfo}.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class MDNTransformationException extends Exception {

	/**
     * Create a new <code>ParseException</code> with the given error message.
     *
     * @param message The description of the parsing error
     */
    public MDNTransformationException(final String message) {
        super(message);
    }

    /**
     * Create a new <code>ParseException</code> with the given error message and the throwable that caused the parsing
     * issue.
     *
     * @param message   The description of the parsing error
     * @param cause     The cause of the parsing error
     */
    public MDNTransformationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

