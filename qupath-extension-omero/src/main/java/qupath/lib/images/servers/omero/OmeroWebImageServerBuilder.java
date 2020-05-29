/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Scanner;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.io.GsonTools;

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
	
	/**
	 * Patterns for parsing input URIs
	 */
	private final static Pattern patternOldViewer = Pattern.compile("/webgateway/img_detail/(\\d+)");
	private final static Pattern patternNewViewer = Pattern.compile("images=(\\d+)");
	private final static Pattern patternWebViewer= Pattern.compile("/webclient/img_detail/(\\d+)");
	private final static Pattern patternType = Pattern.compile("show=(\\w+-)");

	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) {
		if (canConnectToOmero(uri, args)) {
			try {
				OmeroWebClient client = clients.get(uri.getHost());
				return new OmeroWebImageServer(uri, client, args);
			} catch (IOException e) {
				Dialogs.showErrorNotification("OMERO web server", uri.toString() + " - " + e.getLocalizedMessage());
			}
		}
		return null;
	}


	@Override
	public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String...args) {
		float supportLevel = supportLevel(uri, args);
		Collection<ServerBuilder<BufferedImage>> builders = new ArrayList<>();
		
		if (supportLevel > 0f) {
			List<URI> uris = new ArrayList<>();
			try {
				uris = getURIs(uri, args);
			} catch (IOException e) {
				Dialogs.showErrorNotification("OMERO web server", e.getLocalizedMessage());
			}
			
			for (var subURI: uris) {
				try (var server = buildServer(subURI, args)) {
					builders.add(server.getBuilder());
				} catch (Exception e) {
					logger.debug("Unable to create OMERO server", e.getLocalizedMessage());
				}
			}
		}

		return UriImageSupport.createInstance(this.getClass(), supportLevel, builders);
	}
	
	private boolean canConnectToOmero(URI uri, String... args) {
		try {
			String host = uri.getHost();
			
			if (supportLevel(uri) <= 0) {
				logger.debug("OMERO web server does not support {}", uri);
				return false;
			}
			OmeroWebClient client = clients.get(host);
			
			if (!client.loggedIn()) {
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
							"Please enter your login details for OMERO server", uri.getHost(), username);
				if (authentication == null) {
					logger.warn("Could not log in to {} - No authentification found!", host);
					return false;
				}
				lastUsername = authentication.getUserName();
				String result = client.login(authentication, client.getServers().get(0).id);
				Arrays.fill(authentication.getPassword(), (char)0);
				logger.info(result);
			}
			return true;
		} catch (Exception e) {
			Dialogs.showErrorMessage("Omero web server", "Could not connect to OMERO web server.\nCheck the following:\n- Valid credentials.\n- Access permission.\n- Correct URL.");
		}
		return false;
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
				logger.error("Unable to connect to OMERO server", e.getLocalizedMessage());
				return 0;
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
	
	/**
	 * Return a list of valid URIs from the given URI. If no valid URI can be parsed 
	 * from it, an IOException is thrown.
	 * 
	 * <p>
	 * E.g. "{@code /host/webclient/?show=image=4|image=5}" returns a list containing: 
	 * "{@code /host/webclient/?show=image=4}" and "{@code /host/webclient/?show=image=5}".
	 * 
	 * @param uri
	 * @param args
	 * @return list
	 * @throws IOException
	 */
	private List<URI> getURIs(URI uri, String... args) throws IOException {
		List<URI> list = new ArrayList<>();
        String elemId = "image-";
        String query = uri.getQuery() != null ? uri.getQuery() : "";
        String shortPath = uri.getPath() + query;
        String equalSign = "%3D";
        Pattern[] similarPatterns = new Pattern[] {patternOldViewer, patternNewViewer, patternWebViewer};

        // Check for simpler patterns first
        for (int i = 0; i < similarPatterns.length; i++) {
        	var matcher = similarPatterns[i].matcher(shortPath);
            if (matcher.find()) {
                elemId += matcher.group(1);
                list.add(URI.create(uri.getScheme() + "://" + uri.getHost() + "/webclient/?show" + equalSign + elemId));
                return list;
            }
        }

        // If no simple pattern was matched, check for the last possible one: /webclient/?show=
        if (shortPath.startsWith("/webclient/show=")) {
        	URI newURI = getStandardURI(uri);
            var patternElem = Pattern.compile("image-(\\d+)");
            var matcherElem = patternElem.matcher(newURI.toString());
            while (matcherElem.find()) {
                list.add(URI.create(uri.getScheme() + "://" + uri.getHost() + uri.getPath() + "?show" + equalSign + "image-" + matcherElem.group(1)));
            }
        	return list;
        }
        
        // At this point, no valid URI pattern was found
        throw new IOException("URI not recognized: " + uri.toString());
	}
	
	private URI getStandardURI(URI uri, String... args) throws IOException {
		if (!canConnectToOmero(uri, args))
			throw new IOException("Problem connecting to OMERO web server");
		List<String> ids = new ArrayList<String>();
		String vertBarSign = "%7C";
		// Identify the type of element shown (e.g. dataset)
        var type = "";
        String query = uri.getQuery() != null ? uri.getQuery() : "";
        var matcherType = patternType.matcher(query);
        if (matcherType.find())
            type = matcherType.group(1);
        else
            throw new IOException("URI not recognized: " + uri.toString());
        
        var patternId= Pattern.compile(type + "(\\d+)");
        var matcherId = patternId.matcher(query);
        while (matcherId.find()) {
        	ids.add(matcherId.group(1));
        }
		
        // Cascading the types to get all ('leaf') images
        StringBuilder sb = new StringBuilder(uri.getScheme() + "://" + uri.getHost() + uri.getPath() + "?show=image-");
        List<String> tempIds = new ArrayList<String>();
        // TODO: Support screen and plates
        switch (type) {
        case "screen-":
        	type = "plate-";
        case "plate-":
        	break;
        case "project-":
        	for (String id: ids) {
        		URL request = new URL(uri.getScheme(), uri.getHost(), -1, "/api/v0/m/projects/" + id + "/datasets/");
        		InputStreamReader reader = new InputStreamReader(request.openStream());
        		JsonObject map = GsonTools.getInstance().fromJson(reader, JsonObject.class);
        		reader.close();
        		
        		JsonArray data = map.getAsJsonArray("data");
        		for (int i = 0; i < data.size(); i++) {
        			tempIds.add(data.get(i).getAsJsonObject().get("@id").getAsString());
        		}
        	}
        	ids =  new ArrayList<>(tempIds);
        	tempIds.clear();
        	type = "dataset-";
        	
        case "dataset-":
        	for (String id: ids) {
        		URL request = new URL(uri.getScheme(), uri.getHost(), -1, "/api/v0/m/datasets/" + id + "/images/");
        		InputStreamReader reader = new InputStreamReader(request.openStream());
        		JsonObject map = GsonTools.getInstance().fromJson(reader, JsonObject.class);
        		reader.close();
        		
        		JsonArray data = map.getAsJsonArray("data");
        		for (int i = 0; i < data.size(); i++) {
        			tempIds.add(data.get(i).getAsJsonObject().get("@id").getAsString());
        		}
        	}
        	ids = new ArrayList<>(tempIds);
        	tempIds.clear();
        	type="image-";
        	
        case "image-":
        	if (ids.isEmpty())
        		throw new IOException("No image found in URI: " + uri.toString());
        	for (int i = 0; i < ids.size(); i++) {
        		String imgId = (i == ids.size()-1) ? ids.get(i) : ids.get(i) + vertBarSign + "image-";
        		sb.append(imgId);        		
        	}
        	break;
        default:
        	throw new IOException("No image found in URI: " + uri.toString());
        }
        
		return URI.create(sb.toString());
	}
	
	
	@SuppressWarnings("unused")
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
			client.startTimer();
			return client;
		}

		String login(final PasswordAuthentication authentication, final int serverID) throws Exception {
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
			
			
			var charset = StandardCharsets.UTF_8;
			try (OutputStream stream = connection.getOutputStream()) {
				// To avoid storing the password in a String: create ByteBuffers and concatenate them, then convert to byte[]
				String s = String.join("&", "server=" + serverID, "username=" + authentication.getUserName(), "password=");
				CharBuffer charBuffer = CharBuffer.wrap(authentication.getPassword());
				byte[] sBytes = s.getBytes(charset);
				byte[] out = new byte[sBytes.length + charBuffer.length() * 4];
				ByteBuffer byteBuffer = ByteBuffer.wrap(out);
				byteBuffer.put(sBytes);
				var encoder = charset.newEncoder();
				encoder.encode(CharBuffer.wrap(charBuffer), byteBuffer, true);
				stream.write(out, 0, byteBuffer.position());
				
				// Fill the traces of password with '0'
				Arrays.fill(authentication.getPassword(), (char) 0);
				Arrays.fill(out, (byte)0);
				Arrays.fill(charBuffer.array(), (char)0);
				Arrays.fill(byteBuffer.array(), (byte)0);
				encoder.reset();
				System.gc();
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

		private Map<?, ?> getMapJSON(String base, String... query)
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
				try (var s = new Scanner(stream).useDelimiter("\\A")) {
					return s.hasNext() ? s.next() : "";
				}
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

		@SuppressWarnings("unused")
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

			if (!Dialogs.showConfirmDialog("Login", pane))
				return null;

			String userName = tfUsername.getText();
			int passLength = tfPassword.getCharacters().length();
			char[] password = new char[passLength];
			for (int i = 0; i < passLength; i++) {
				password[i] = tfPassword.getCharacters().charAt(i);
			}

			return new PasswordAuthentication(userName, password);
		}

	}

}