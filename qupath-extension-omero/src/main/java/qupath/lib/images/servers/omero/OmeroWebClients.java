package qupath.lib.images.servers.omero;

import java.net.PasswordAuthentication;
import java.net.URI;
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
 * Class to keep track of active OMERO clients.
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
	final private static Map<String, List<OmeroWebClient>> clients = new HashMap<>();
	
	
	/**
	 * A set of potential hosts that don't correspond to valid OMERO web servers.
	 * This is used to avoid trying again.
	 */
	final private static Set<URI> failedHosts = new HashSet<>();
	
	
	/**
	 * Return the client associated with the specified uri.
	 * @param host
	 * @return
	 */
	static OmeroWebClient getClient(URI uri) {
		for (var list: clients.values()) {
			for (var client: list) {
				if (client.getURI() == uri)
					return client;
			}
		}
		return null;
	}
	
	
	/**
	 * Add the specified client to the host-clients map.
	 * @param host
	 * @param client
	 */
	static void addClient(String host, OmeroWebClient client) {
		var list = clients.get(host);
		if (list != null && !list.contains(client))
			list.add(client);
		else
			clients.put(host, new ArrayList<OmeroWebClient>(Arrays.asList(client)));
	}
	
	/**
	 * Remove the client from the host-clients map
	 * @param uri
	 */
	static void removeClient(OmeroWebClient client) {
		var list = clients.get(client.getURI().getHost());
		if (list != null)
			list.remove(client);
	}
	
	/**
	 * Return whether the specified host was processed before 
	 * and had failed (to avoid unnecessary processing).
	 * @param host
	 * @return hasFailed
	 */
	static boolean hasFailed(URI uri) {
		return failedHosts.contains(uri);
	}
	
	/**
	 * Add the specified host to the list of failed hosts.
	 * @param host
	 */
	static void addFailedHost(URI uri) {
		failedHosts.add(uri);
	}
	
	/**
	 * Return the map with all hosts and clients.
	 * @return
	 */
	static Map<String, List<OmeroWebClient>> getAllClients() {
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
