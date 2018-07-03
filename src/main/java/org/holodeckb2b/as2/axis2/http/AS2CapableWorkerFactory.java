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
package org.holodeckb2b.as2.axis2.http;

import org.apache.axis2.transport.http.server.Worker;
import org.apache.axis2.transport.http.server.WorkerFactory;

/**
 * Is the {@link WorkerFactory} implementation that creates the {@link Worker}s that are capable of also processing
 * AS2 messages.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 *
 */
public class AS2CapableWorkerFactory implements WorkerFactory {

	/* 
	 * @see org.apache.axis2.transport.http.server.WorkerFactory#newWorker()
	 */
	@Override
	public Worker newWorker() {
		return new AS2CapableWorker();
	}

}
