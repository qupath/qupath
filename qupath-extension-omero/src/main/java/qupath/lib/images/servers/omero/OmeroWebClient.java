package qupath.lib.images.servers.omero;

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
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
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
import qupath.lib.gui.dialogs.Dialogs;

/**
 * Class representing an Omero Web Client. This class will take care of 
 * logging in, keeping the connection alive and logging out.
 * 
 * @author Melvin Gelbard
 */
public class OmeroWebClient {
	
	final private static Logger logger = LoggerFactory.getLogger(OmeroWebClient.class);

	private Timer timer;

	private final static String URL_SERVERS = "url:servers";
	private final static String URL_LOGIN = "url:login";
	private final static String URL_TOKEN = "url:token";
	private final static String URL_IMAGES = "url:images";
	private final static String URL_PROJECTS = "url:projects";
	private final static String URL_BASE = "url:base";
	private final static String URL_SAVE = "url:save";

	private Gson gson = new Gson();
	
	private List<OmeroWebImageServer> imageServers = new ArrayList<>();
	
	private URI uri;
	private String username;

	private OmeroAPI supportedVersions;
	private OmeroAPIVersion version;
	private List<OmeroServer> servers;

	private String token;
	private String baseURL;
	private String requestURL;

	private Map<String, String> serviceURLs = new HashMap<>();

	OmeroWebClient(final URI uri) {
		this.uri = uri;
		this.username = "";
		this.baseURL = uri.getHost();
		if (!this.baseURL.startsWith("http"))
			this.baseURL = "https://" + this.baseURL;
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
		}, 60000L, 60000L);
	}

	private boolean loadURLs() throws JsonSyntaxException, MalformedURLException, IOException {
		supportedVersions = parseJSON(OmeroAPI.class, baseURL, "/api/");
		version = supportedVersions.getLatestVersion();
		if (version == null)
			return false;
		requestURL = version.versionURL;
		serviceURLs = parseJSON(new TypeToken<Map<String, String>>() {}.getType(), requestURL);
		servers = parseJSON(OmeroServerList.class, serviceURLs.get(URL_SERVERS)).data;
		return true;
	}

	static OmeroWebClient create(URI uri, boolean startTimer) throws JsonSyntaxException, MalformedURLException, IOException {
		OmeroWebClient client = new OmeroWebClient(uri);
		client.loadURLs();
		if (startTimer)
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
			username = authentication.getUserName();
			// To avoid storing the password in a String: create ByteBuffers and concatenate them, then convert to byte[]
			String s = String.join("&", "server=" + serverID, "username=" + username, "password=");
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
			var pingURL = URI.create(baseURL + "/webclient/keepalive_ping/?_=" + System.currentTimeMillis()).toURL();
			HttpURLConnection connection = (HttpURLConnection) pingURL.openConnection();
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
	
	boolean loggedIn() {
		try {
			logger.debug("Attempting to log in...");
			String imageId = OmeroTools.getOmeroObjectId(uri);
			if (imageId == null)
				return false;
			
			URL imgDataURL = new URL(uri.getScheme(), uri.getHost(), -1, "/webgateway/imgData/" + imageId);
			HttpURLConnection connection = (HttpURLConnection) imgDataURL.openConnection();
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestMethod("GET");
			connection.setDoInput(true);
			int response = connection.getResponseCode();
			connection.disconnect();
			return response == 200;
		} catch (IOException e) {
			logger.warn("Error trying to log in", e);
			return false;
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
	
	String getToken() {
		return token;
	}
	
	void logOut() {
		try {
			String url = baseURL + "/webclient/logout/";
			HttpURLConnection connection;
			connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
			connection.setRequestProperty("X-CSRFToken", token);
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Referer", url + ":" + servers.get(0).port);
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setDoInput(true);
			int response = connection.getResponseCode();
			connection.disconnect();
			
			if (response != 200)
				throw new IOException("Server returned " + response);	
			
		} catch (IOException e) {
			logger.error("Could not logout.", e.getLocalizedMessage());
		}

		timer.cancel();
	}
	
	String getUsername() {
		return username;
	}
	
	void addImageServer(OmeroWebImageServer server) {
		if (!imageServers.contains(server))
			imageServers.add(server);
	}
	
	List<OmeroWebImageServer> getImageServers() {
		return imageServers;
	}
	
	URI getURI() {
		return uri;
	}
	
	String getBaseUrl() {
		return baseURL;
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

	class OmeroServer {

		private String host;
		private String server;
		private int id;
		private int port;

		@Override
		public String toString() {
			return String.format("Host: %s, Server: %s, ID: %d, Port: %d", host, server, id, port);
		}

		public int getId() {
			return id;
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
