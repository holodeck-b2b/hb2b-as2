/*
 * Copyright (C) 2024 The Holodeck B2B Team, Sander Fieten
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;

import org.holodeckb2b.interfaces.messagemodel.IPayload;

/**
 * Is a {@link DataSource} implementation that wraps an {@link IPayload}.
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 * @since  4.0.0
 */
public class PayloadDataSource implements DataSource {
	/**
	 * The payload that contains the data for this data source
	 */
	private final IPayload	payload;

	public PayloadDataSource(IPayload pl) {
		this.payload = pl;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return payload.getContent();
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getContentType() {
		return payload.getMimeType();
	}

	@Override
	public String getName() {
		return payload.getPayloadURI();
	}

}
