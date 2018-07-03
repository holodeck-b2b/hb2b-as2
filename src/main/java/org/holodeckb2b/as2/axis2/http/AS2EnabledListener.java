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

import java.io.IOException;

import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.SessionContext;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.transport.TransportListener;
import org.apache.axis2.transport.http.HTTPTransportUtils;
import org.apache.axis2.transport.http.HTTPWorkerFactory;
import org.apache.axis2.transport.http.server.HttpFactory;
import org.apache.axis2.transport.http.server.SimpleHttpServer;

/**
 * Is an implementation of an Axis2 {@link TransportListener} that will create a stand alone HTTP listener that
 * is capable of handling AS2 messages. It uses the HTTP sever built into Axis2 but initializes the {@link 
 * HTTPWorkerFactory} to create {@link } workers that can also process AS2 messages. 
 * <p>As this transport listener replaces the default Axis2 listener it uses the same parameters, the main one being
 * the port parameter where the message should be received. If that parameter is not provided the default port 8080
 * is used. 
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 *
 */
public class AS2EnabledListener implements TransportListener {
	/**
	 * The Axis2 http server
	 */
	private SimpleHttpServer embedded = null;
	/**
	 * The Axis2 configuration of this instance
	 */
    private ConfigurationContext configurationContext;
    /**
     * The transport configuration for this listener
     */
    private TransportInDescription	transportConfig;
    /**
     * The factory for HTTP workers
     */
    private HttpFactory httpFactory;
    /**
     * The default port used if nothing is specified in the configuration
     */
    private static final int DEFAULT_PORT = 8080;

	@Override
	public void init(ConfigurationContext axisConf, TransportInDescription transprtIn) throws AxisFault {
        this.configurationContext = axisConf;
        this.transportConfig = transprtIn;
        int port = DEFAULT_PORT;
        Parameter portParameter = transprtIn.getParameter(PARAM_PORT);
        if (portParameter != null)
        	port = Integer.parseInt((String) portParameter.getValue());

        if (httpFactory == null)
            httpFactory = new HttpFactory(configurationContext, port, new AS2CapableWorkerFactory());        
	}

	@Override
	public void start() throws AxisFault {
        try {
            embedded = new SimpleHttpServer(httpFactory, httpFactory.getPort());
            embedded.init();
            embedded.start();
        } catch (IOException e) {
            throw AxisFault.makeFault(e);
        }		
    }

	@Override
	public void stop() throws AxisFault {
        if (embedded != null) {
            try {
                embedded.destroy();
            } catch (Exception e) {
            }
        }
	}

	@Override
	public EndpointReference[] getEPRsForService(String serviceName, String ip) throws AxisFault {
        if (embedded == null) {
            throw new AxisFault("Unable to generate EPR for the transport : http");
        }
        return HTTPTransportUtils.getEPRsForService(configurationContext, transportConfig, 
        											serviceName, ip, embedded.getPort());
	}

	@Override
	public SessionContext getSessionContext(MessageContext messageContext) {
		// Session support isn't needed for Holodeck B2B 
		return null;
	}

	@Override
	public void destroy() {
		this.configurationContext = null;
	}

}
