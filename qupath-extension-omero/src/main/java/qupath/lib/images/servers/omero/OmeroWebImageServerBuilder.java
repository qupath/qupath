package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Scanner;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

/**
 * Builder for ImageServers that make requests from the OMERO web API.
 * <p>
 * See https://docs.openmicroscopy.org/omero/5.4.9/developers/json-api.html
 * 
 * @author Pete Bankhead
 *
 */
public class OmeroWebImageServerBuilder implements ImageServerBuilder<BufferedImage> {

	final private static Logger logger = LoggerFactory.getLogger(OmeroWebImageServerBuilder.class);

	/**
	 * A list of active clients. The user may not necessarily be logged in.
	 */
	final private static Map<String, OmeroWebClient> clients = new HashMap<>();

	/**
	 * A set of potential hosts that don't correspond to valid OMERO web servers.
	 * This is used to avoid trying again.
	 */
	final private static Set<String> failedHosts = new HashSet<>();

	/**
	 * Last username for login
	 */
	private String lastUsername = "";

	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) {

		try {
			String host = uri.getHost();

			if (supportLevel(uri) <= 0) {
				logger.debug("OMERO web server does not support {}", uri);
				return null;
			}
			OmeroWebClient client = clients.get(host);

			if (!client.loggedIn()) {
				if (client.getServers().isEmpty()) {
					logger.warn("Could not find any servers for {}!", host);
					return null;
				}
				if (client.getServers().size() > 1) {
					logger.warn("Found multiple servers for {} - will take the first one", host);
				}
				
				// TODO: Parse args to look for password (or password file - and don't store them!)
				String username = lastUsername;
				char[] password = null;
				List<String> cleanedArgs = new ArrayList<>();
				String authenticationFile = null;
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
						"Please enter your login details for OMERO server", uri.getHost(), username);
				if (authentication == null) {
					logger.warn("Could not log in to {}!", host);
					return null;
				}
				lastUsername = authentication.getUserName();
				String result = client.login(authentication, client.getServers().get(0).id);
				Arrays.fill(authentication.getPassword(), (char)0);
				logger.info(result);
			}

			return new OmeroWebImageServer(uri, client, args);
		} catch (Exception e1) {
			logger.error("Problem connecting to OMERO web server", e1);
		}
		return null;
	}

	@Override
	public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String...args) {
		float supportLevel = supportLevel(uri, args);
		return UriImageSupport.createInstance(this.getClass(), supportLevel, DefaultImageServerBuilder.createInstance(this.getClass(), uri, args));
	}
	
	private static float supportLevel(URI uri, String...args) {

		String host = uri.getHost();

		// If we tried, and failed, to treat this as an OMERO server before, fail early
		if (failedHosts.contains(host))
			return 0;

		OmeroWebClient client = clients.get(host);
		if (client != null)
			return 4;

		String scheme = uri.getScheme();
		if (scheme.startsWith("http")) {
			// Try to connect (but don't log in yet)
			try {
				client = OmeroWebClient.create(host);
			} catch (Exception e) {
				failedHosts.add(host);
				logger.error("Unable to connect to OMERO server", e);
			}
			clients.put(host, client);
			return 4;
		}
		return 0;
	}

	@Override
	public String getName() {
		return "OMERO web";
	}

	@Override
	public String getDescription() {
		return "Image server using the OMERO web API";
	}
	
	@Override
	public Class<BufferedImage> getImageType() {
		return BufferedImage.class;
	}
	
	static class OmeroWebClient {

		private Timer timer;

		private final static String URL_SERVERS = "url:servers";
		private final static String URL_LOGIN = "url:login";
		private final static String URL_TOKEN = "url:token";
		private final static String URL_IMAGES = "url:images";
		private final static String URL_PROJECTS = "url:projects";
		private final static String URL_BASE = "url:base";
		private final static String URL_SAVE = "url:save";

		private Gson gson = new Gson();

		private OmeroAPI supportedVersions;
		private OmeroAPIVersion version;
		private OmeroServer server;
		private List<OmeroServer> servers;

		private String token;
		private String baseURL;
		private String requestURL;

		private Map<String, String> serviceURLs = new HashMap<>();

		OmeroWebClient(final String host) {

			this.baseURL = host;
			if (!this.baseURL.startsWith("http"))
				this.baseURL = "https://" + host;

			timer = new Timer("OMERO " + host, true);

			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					keepAlive();
				}

			}, 60000L);
		}

		synchronized void startTimer() {
			if (timer != null)
				return;
			timer = new Timer("omero-keep-alive", true);
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					keepAlive();
				}
			}, 60000L);
		}

		public boolean loggedIn() {
			return keepAlive() == 200; // Check if response is OK
		}

		private boolean loadURLs() throws JsonSyntaxException, MalformedURLException, IOException {
			supportedVersions = parseJSON(OmeroAPI.class, baseURL, "/api/");
			version = supportedVersions.getLatestVersion();
			if (version == null)
				return false;
			requestURL = version.versionURL;
			serviceURLs = parseJSON(new TypeToken<Map<String, String>>() {
			}.getType(), requestURL);
			servers = parseJSON(OmeroServerList.class, serviceURLs.get(URL_SERVERS)).data;
			return true;
		}

		static OmeroWebClient create(String host) throws JsonSyntaxException, MalformedURLException, IOException {
			OmeroWebClient client = new OmeroWebClient(host);
			client.loadURLs();
			return client;
		}

		String login(final PasswordAuthentication authentication, final int serverID) throws Exception {
			String password = new String(authentication.getPassword());
			String result = login(authentication.getUserName(), password, serverID);
			Arrays.fill(authentication.getPassword(), (char) 0);
			return result;
		}

		String login(final String username, final String password, final int serverID) throws IOException {

			var handler = CookieHandler.getDefault();
			if (handler == null) {
				handler = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
				CookieHandler.setDefault(handler);
			}

			if (this.token == null)
				this.token = getCSRFToken();

			String url = serviceURLs.get(URL_LOGIN);

			HttpsURLConnection connection = (HttpsURLConnection) URI.create(url).toURL().openConnection();
			connection.setRequestProperty("X-CSRFToken", this.token);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Referer", url + ":" + servers.get(0).port);
			connection.setUseCaches(false);

			connection.setDoInput(true);
			connection.setDoOutput(true);
			String s = String.join("&", "server=" + serverID, "username=" + username, "password=" + password);
			try (OutputStream stream = connection.getOutputStream()) {
				stream.write(s.getBytes("UTF-8"));
			}

//	        var client = HttpClient.newBuilder()
//	        		.build();
//	        var request = HttpRequest.newBuilder(URI.create(url))
//	        		.header("X-CSRFToken", this.token)
//	        		.header("Referer", url)
//	        		.header("Cookie", "csrftoken=" + this.token)
//	        		.POST(BodyPublishers.ofString(s)).build();
//	        try {
//				System.err.println(client.send(request, BodyHandlers.ofString()).body());
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}

			try (InputStream input = connection.getInputStream()) {
				return GeneralTools.readInputStreamAsString(input);
			}
		}

		private int keepAlive() {
			try {
				logger.debug("Attempting to keep connection alive...");
				HttpURLConnection connection = (HttpURLConnection) URI.create(serviceURLs.get(URL_PROJECTS)).toURL()
						.openConnection();
				connection.setRequestProperty("Content-Type", "application/json");
				connection.setRequestMethod("GET");
				connection.setDoInput(true);
				int response = connection.getResponseCode();
				connection.disconnect();
				return response;
			} catch (IOException e) {
				logger.warn("Error trying to keep connection alive", e);
				return -1;
			}
		}

		List<OmeroServer> getServers() {
			return Collections.unmodifiableList(servers);
		}

		private String getCSRFToken() throws JsonSyntaxException, MalformedURLException, IOException {
			String url = serviceURLs.get(URL_TOKEN);
			String token = (String) getMapJSON(url).get("data");
			return token;
		}

		private Map getMapJSON(String base, String... query)
				throws JsonSyntaxException, MalformedURLException, IOException {
			return parseJSON(Map.class, base, query);
		}

		private static String getJSONString(String base, String... query) throws MalformedURLException, IOException {

			StringBuilder sb = new StringBuilder(base);
			for (String q : query)
				sb.append(q);

			HttpURLConnection connection = (HttpURLConnection) URI.create(sb.toString()).toURL().openConnection();
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestMethod("GET");
			connection.setDoInput(true);
			try (InputStream stream = connection.getInputStream()) {
				Scanner s = new Scanner(stream).useDelimiter("\\A");
				return s.hasNext() ? s.next() : "";
			}
		}

		private <T> T parseJSON(Class<T> cls, String base, String... query)
				throws JsonSyntaxException, MalformedURLException, IOException {
			return gson.fromJson(getJSONString(base, query), cls);
		}

		@SuppressWarnings("unchecked")
		private <T> T parseJSON(Type type, String base, String... query)
				throws JsonSyntaxException, MalformedURLException, IOException {
			return (T) gson.fromJson(getJSONString(base, query), type);
		}

	}

	private class OmeroAPI {

		private List<OmeroAPIVersion> data;

		OmeroAPIVersion getLatestVersion() {
			if (data == null || data.isEmpty())
				return null;
			return data.get(data.size() - 1);
		}

	}

	private class OmeroAPIVersion {

		private String version;

		@SerializedName("url:base")
		private String versionURL;

	}

	private class OmeroServerList {

		private List<OmeroServer> data;

	}

	private class OmeroServer {

		private String host;
		private String server;
		private int id;
		private int port;

		@Override
		public String toString() {
			return String.format("Host: %s, Server: %s, ID: %d, Port: %d", host, server, id, port);
		}

	}

	static class OmeroAuthenticatorFX extends Authenticator {

		private String lastUsername = "";

		@Override
		protected PasswordAuthentication getPasswordAuthentication() {
			PasswordAuthentication authentication = getPasswordAuthentication(getRequestingPrompt(),
					getRequestingHost(), lastUsername);
			if (authentication == null)
				return null;

			lastUsername = authentication.getUserName();
			return authentication;
		}

		static PasswordAuthentication getPasswordAuthentication(String prompt, String host, String lastUsername) {

			GridPane pane = new GridPane();

			Label labHost = new Label(host);

			Label labUsername = new Label("Username");
			TextField tfUsername = new TextField(lastUsername);
			labUsername.setLabelFor(tfUsername);

			Label labPassword = new Label("Password");
			PasswordField tfPassword = new PasswordField();
			labPassword.setLabelFor(tfPassword);

			int row = 0;
			if (prompt != null && !prompt.isBlank())
				pane.add(new Label(prompt), 0, row++, 2, 1);
			pane.add(labHost, 0, row++, 2, 1);
			pane.add(labUsername, 0, row);
			pane.add(tfUsername, 1, row++);
			pane.add(labPassword, 0, row);
			pane.add(tfPassword, 1, row++);

			pane.setHgap(5);
			pane.setVgap(5);

			if (!DisplayHelpers.showConfirmDialog("Login", pane))
				return null;

			String userName = tfUsername.getText();
			return new PasswordAuthentication(userName, tfPassword.getText().toCharArray());
		}

	}

}