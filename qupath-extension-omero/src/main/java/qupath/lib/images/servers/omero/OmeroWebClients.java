package qupath.lib.images.servers.omero;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to keeps track of active OMERO clients.
 * 
 * @author Melvin Gelbard
 */
public class OmeroWebClients {
	
	/**
	 * A list of active/non-active clients. The user may not necessarily be logged in.
	 */
	final private static Map<String, OmeroWebClient> clients = new HashMap<>();
	
	/**
	 * A list of clients and their associated image servers.
	 */
	final private static Map<OmeroWebClient, List<OmeroWebImageServer>> clientImageMap = new HashMap<>();

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
	

	
}
