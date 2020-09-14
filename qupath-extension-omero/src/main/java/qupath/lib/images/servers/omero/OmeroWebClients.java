package qupath.lib.images.servers.omero;

import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.omero.OmeroWebClient.OmeroAuthenticatorFX;

/**
 * Class to keeps track of active OMERO clients.
 * 
 * @author Melvin Gelbard
 */
public class OmeroWebClients {
	
	final private static Logger logger = LoggerFactory.getLogger(OmeroWebClients.class);
	
	/**
	 * Last username for login
	 */
	private static String lastUsername = "";
	
	/**
	 * A list of active/non-active clients. The user may not necessarily be logged in.
	 */
	final private static Map<String, OmeroWebClient> clients = new HashMap<>();
	
	
	/**
	 * A set of potential hosts that don't correspond to valid OMERO web servers.
	 * This is used to avoid trying again.
	 */
	final private static Set<String> failedHosts = new HashSet<>();
	
	
	/**
	 * Return the client associated with the specified host.
	 * @param host
	 * @return
	 */
	static OmeroWebClient getClient(String host) {
		return clients.get(host);
	}
	
	
	/**
	 * Add the specified client to the host-client map.
	 * @param host
	 * @param client
	 */
	static void addClient(String host, OmeroWebClient client) {
		clients.put(host, client);
	}
	
	
	static void removeClient(String host) {
		clients.remove(host);
	}
	
	/**
	 * Return whether the specified host was processed before 
	 * and had failed (to avoid unnecessary processing).
	 * @param host
	 * @return hasFailed
	 */
	static boolean hasFailed(String host) {
		return failedHosts.contains(host);
	}
	
	/**
	 * Add the specified host to the list of failed hosts.
	 * @param host
	 */
	static void addFailedHost(String host) {
		failedHosts.add(host);
	}
	
	/**
	 * Return the map with all hosts and clients.
	 * @return
	 */
	static Map<String, OmeroWebClient> getAllClients() {
		return clients;
	}


	/**
	 * Log in to the client's server with optional args.
	 * 
	 * @param client
	 * @param args
	 * @return success
	 */
	public static boolean logIn(OmeroWebClient client, String...args) {
		try {
			String host = client.getBaseUrl();
			
			if (client.getServers().isEmpty()) {
				logger.warn("Could not find any servers for {}!", host);
				return false;
			}
			if (client.getServers().size() > 1) {
				logger.warn("Found multiple servers for {} - will take the first one", host);
			}
			
			// TODO: Parse args to look for password (or password file - and don't store them!)
			String username = lastUsername;
			char[] password = null;
			List<String> cleanedArgs = new ArrayList<>();
			int i = 0;
			while (i < args.length-1) {
				String name = args[i++];
				if ("--username".equals(name) || "-u".equals(name))
					username = args[i++];
				else if ("--password".equals(name) || "-p".equals(name)) {
					password = args[i++].toCharArray();
				} else
					cleanedArgs.add(name);
			}
			if (cleanedArgs.size() < args.length)
				args = cleanedArgs.toArray(String[]::new);
			
			PasswordAuthentication authentication;
			if (username != null && password != null) {
				logger.info("Username & password parsed from args");
				authentication = new PasswordAuthentication(username, password);
			} else 
				authentication = OmeroAuthenticatorFX.getPasswordAuthentication(
						"Please enter your login details for OMERO server", host, username);
			if (authentication == null) {
				logger.warn("Could not log in to {} - No authentification found!", host);
				return false;
			}
			lastUsername = authentication.getUserName();
			String result = client.login(authentication, client.getServers().get(0).getId());
			Arrays.fill(authentication.getPassword(), (char)0);
			logger.info(result);			
		} catch (Exception e) {
			Dialogs.showErrorMessage("Omero web server", "Could not connect to OMERO web server.\nCheck the following:\n- Valid credentials.\n- Access permission.\n- Correct URL.");
			return false;
		}
		return true;
	}
	
	
	/**
	 * Log the specified client out.
	 * @param client
	 */
	public static void logOut(OmeroWebClient client) {
		client.logOut();
	}
	

	
}
