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

import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Security;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisDescription;
import org.apache.axis2.description.AxisModule;
import org.apache.axis2.modules.Module;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.neethi.Assertion;
import org.apache.neethi.Policy;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Is the implementation of the Axis2 {@link Module} interface and is responsible for the correct initialization of 
 * the Holodeck B2B AS2 Module. This is limited to loading the BouncyCastle security provider.
 *  
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class AS2Module implements Module {
	/**
	 * The name of the AS2 module
	 */
	public static final String AXIS_MODULE_NAME = "holodeckb2b-as2";

	/**
	 * Logger
	 */
	private static Logger log = LogManager.getLogger(AS2Module.class);

	/**
	 * Initializes the AS2 Axis2 module by loading and registering the BouncyCastle security provider.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void init(final ConfigurationContext cc, final AxisModule am) throws AxisFault {
		log.info("Starting Holodeck B2B AS2 module...");

		// Check if module name in module.xml is equal to constant use in code
		if (!am.getName().equals(AXIS_MODULE_NAME)) {
			// Name is not equal! This is a fatal configuration error, stop loading this
			// module and alert operator
			log.fatal("Invalid Holodeck B2B AS2 module configuration found! Name in configuration is: " + am.getName()
					+ ", expected was: " + AXIS_MODULE_NAME);
			throw new AxisFault("Invalid configuration found for module: " + am.getName());
		}

		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null)
			Security.addProvider(new BouncyCastleProvider());

		final MailcapCommandMap dhMapping = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
		dhMapping.addMailcap("application/pkcs7-signature;; x-java-content-handler="
				+ org.bouncycastle.mail.smime.handlers.pkcs7_signature.class.getName());
		dhMapping.addMailcap("application/pkcs7-mime;; x-java-content-handler="
				+ org.bouncycastle.mail.smime.handlers.pkcs7_mime.class.getName());
		dhMapping.addMailcap("application/x-pkcs7-signature;; x-java-content-handler="
				+ org.bouncycastle.mail.smime.handlers.x_pkcs7_signature.class.getName());
		dhMapping.addMailcap("application/x-pkcs7-mime;; x-java-content-handler="
				+ org.bouncycastle.mail.smime.handlers.x_pkcs7_mime.class.getName());
		dhMapping.addMailcap("multipart/signed;; x-java-content-handler="
				+ org.bouncycastle.mail.smime.handlers.multipart_signed.class.getName());
		try {
			AccessController.doPrivileged((PrivilegedAction) () -> { CommandMap.setDefaultCommandMap(dhMapping);
																	 return null;
															   	   });
		} catch (AccessControlException notAllowed) {
			log.error("Could not set the content to data handler mapping");
		}

		log.info("Started Holodeck B2B AS2 Module");
	}

	/*
	 * Not used
	 * 
	 * @see
	 * org.apache.axis2.modules.Module#engageNotify(org.apache.axis2.description.
	 * AxisDescription)
	 */
	@Override
	public void engageNotify(AxisDescription axisDescription) throws AxisFault {
	}

	/*
	 * Not used
	 * 
	 * @see org.apache.axis2.modules.Module#canSupportAssertion(org.apache.neethi.
	 * Assertion)
	 */
	@Override
	public boolean canSupportAssertion(Assertion assertion) {
		// TODO Auto-generated method stub
		return false;
	}

	/*
	 * Not used
	 * 
	 * @see org.apache.axis2.modules.Module#applyPolicy(org.apache.neethi.Policy,
	 * org.apache.axis2.description.AxisDescription)
	 */
	@Override
	public void applyPolicy(Policy policy, AxisDescription axisDescription) throws AxisFault {
	}

	/*
	 * Not used
	 * 
	 * @see org.apache.axis2.modules.Module#shutdown(org.apache.axis2.context.
	 * ConfigurationContext)
	 */
	@Override
	public void shutdown(ConfigurationContext configurationContext) throws AxisFault {
		// TODO Auto-generated method stub

	}

}
