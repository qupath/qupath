package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.scene.Scene;
import javafx.stage.Stage;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.omero.OmeroObjects.Dataset;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObject;
import qupath.lib.images.servers.omero.OmeroObjects.Project;
import qupath.lib.images.servers.omero.OmeroObjects.Server;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;


/**
 * Static helper methods related to OMEROWebImageServer.
 * 
 * @author Melvin Gelbard
 *
 */
public class OmeroTools {
	
	final private static Logger logger = LoggerFactory.getLogger(OmeroTools.class);
	
	/**
	 * Patterns for parsing input URIs
	 */
	private final static Pattern patternOldViewer = Pattern.compile("/webgateway/img_detail/(\\d+)");
	private final static Pattern patternNewViewer = Pattern.compile("images=(\\d+)");
	private final static Pattern patternWebViewer= Pattern.compile("/webclient/img_detail/(\\d+)");
	private final static Pattern patternType = Pattern.compile("show=(\\w+-)");
	
	/**
	 * Return the web client used for the specified OMERO server
	 * @param server
	 * @return client
	 */
	public static OmeroWebClient getWebClient(OmeroWebImageServer server) {
		return server.getWebClient();
	};
	
	
	/**
	 * Get all the OMERO objects (inside the parent ID) present in the OMERO server from which 
	 * the specified OmeroWebImageServer was created. 
	 * <p>
	 * If the parent object is an {@code OmeroObjects.Server},
	 * orphaned {@code OmeroObjects.Dataset}s and {@code OmeroObjects.Image}s will also be 
	 * returned.
	 * 
	 * @param server
	 * @param parent 
	 * @return list of OmeroObjects
	 * @throws IOException
	 */
	public static List<OmeroObject> getOmeroObjects(OmeroWebImageServer server, OmeroObject parent) throws IOException {
		String parentId = parent instanceof Server ? "" : parent.getId() + "";
		String objectClass = "projects";
		String parentClass = "";
		parentId = parentId == "" || parentId == null ? "" : "=" + parentId;
		if (parent instanceof Project) {
			objectClass = "datasets";
			parentClass = parentId == "" || parentId == null ? "" : "&project";
		} else if (parent instanceof Dataset) {
			objectClass = "images";
			parentClass = parentId == "" || parentId == null ? "" : "&dataset";
		}

		var query = "?childCount=true" + (parentId != null ? parentClass + parentId : "");
		URL url = new URL(server.getScheme(), server.getHost(), -1, "/api/v0/m/" + objectClass + "/" + query);
		
		var data = readPaginated(url);
		
		List<OmeroObject> list = new ArrayList<>();
		for (var d: data) {
			var gson = new GsonBuilder().registerTypeAdapter(OmeroObject.class, new OmeroObjects.GsonOmeroObjectDeserializer()).setLenient().create();
			try {
				var omeroObj = gson.fromJson(d, OmeroObject.class);
				omeroObj.setParent(parent);
				if (omeroObj != null)
					list.add(omeroObj);
			} catch (Exception e) {
				logger.error("Error parsing OMERO object: " + e.getLocalizedMessage(), e);
			}
		}
		
		// If parent is Server, get orphaned Datasets and Images
		if (parent instanceof Server) {
			var gson = new GsonBuilder().registerTypeAdapter(OmeroObject.class, new OmeroObjects.GsonOmeroObjectDeserializer()).setLenient().create();
			URL urlOrphanedDatasets = new URL(server.getScheme(), server.getHost(), -1, "/api/v0/m/datasets/?childCount=true&orphaned=true");
			URL urlOrphanedImages = new URL(server.getScheme(), server.getHost(), -1, "/webclient/api/images/?orphaned=true");
			
			var orphanedDatasets = readPaginated(urlOrphanedDatasets);
			for (var d: orphanedDatasets) {
				try {
					var omeroObj = gson.fromJson(d, OmeroObject.class);
					omeroObj.setParent(parent);
					if (omeroObj != null)
						list.add(omeroObj);
				} catch (Exception e) {
					logger.error("Error parsing OMERO object: " + e.getLocalizedMessage(), e);
				}
			}
			
			// Requesting orphaned images can time-out the JSON API on OMERO side if too many,
			// so we go through the webclient, whose response comes in a different format
	        HttpURLConnection connection = (HttpURLConnection) urlOrphanedImages.openConnection();
	        if (connection.getResponseCode() == 200) {
	        	InputStreamReader reader = new InputStreamReader(connection.getInputStream());
	        	JsonObject map = GsonTools.getInstance().fromJson(reader, JsonObject.class);
	        	reader.close();
	        	
	        	// Send requests in separate threads, this is not a great design
	        	// TODO: Clean up this code, which now does: 
	        	// 1. Send request for each image in the list of orphaned images in the executor
	        	// 2. Terminate the executor after 5 seconds
	        	// 3. Checks if there are still requests that weren't processed and gives log error if so
	        	// Solution: Give a time-out for the request in readPaginated() :::
	        	//
	        	// URLConnection con = url.openConnection();
	        	// con.setConnectTimeout(connectTimeout);
	        	// con.setReadTimeout(readTimeout);
	        	// InputStream in = con.getInputStream();
	        	//
	    		ExecutorService executorRequests = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("orphaned-image-requests", true));
	    		List<Future<?>> futures = new ArrayList<Future<?>>();
	        	
	    		map.get("images").getAsJsonArray().forEach(e -> {
	    			// To keep track of the completed requests, keep a Future variable
	    			Future<?> future = executorRequests.submit(() -> {
        				try {
	        				URL imageURL = new URL(server.getScheme(), server.getHost(), -1, "/api/v0/m/images/" + e.getAsJsonObject().get("id"));
	        				list.add(readOmeroObject(imageURL));	       
        				} catch (IOException ex) {
    						logger.error("Could not fetch information for image id: " + e.getAsJsonObject().get("id"));
    					}
        			});
	    			futures.add(future);
	        	});
	    		executorRequests.shutdown();
	    		try {
	    			// If more than 10 seconds 
	    			executorRequests.awaitTermination(5L, TimeUnit.SECONDS);
	    			for (Future<?> future : futures){
	    			    if (!future.isDone()) {
	    			    	logger.warn("Too many orphaned images in " + server.getHost() + ". Could not load all of them.");
	    			    	break;
	    			    }
	    			}
	    		} catch (InterruptedException ex) {
	    			logger.warn("An exception occurred while interrupting requests: " + ex);
	    		}
	        	
	        	
	        	
	        	
//	        	map.get("images").getAsJsonArray().forEach(e -> {
//					try {
//						var mel = System.currentTimeMillis();
//						URL imageURL = new URL(server.getScheme(), server.getHost(), -1, "/api/v0/m/images/" + e.getAsJsonObject().get("id"));
//						list.add(readOmeroObject(imageURL));
//						logger.error("Time: " + (System.currentTimeMillis() - mel));
//					} catch (IOException ex) {
//						logger.error("Could not fetch information for image id: " + e.getAsJsonObject().get("id"));
//					}
//	        	});
	        }
				      
						
		}
		return list;
	}	
	
	
	/**
	 * Write PathObject collection to OMERO server. This will not delete the existing 
	 * ROIs present on the OMERO server. Rather, it will simply add the new ones.
	 * @param pathObjects
	 * @param server
	 * @return success
	 * @throws IOException
	 */
	public static boolean writePathObjects(Collection<PathObject> pathObjects, OmeroWebImageServer server) throws IOException {
		// TODO: What to do if token expires?
		// TODO: What if we have more object than the limit accepted by the OMERO API?
		if (pathObjects.isEmpty())
			return true;
		
		String id = server.getId();
		String host = server.getHost();
		String scheme = server.getScheme();
		
		// TODO: probably should do this in one line
		Gson gsonAnnotation = new GsonBuilder().registerTypeAdapter(PathAnnotationObject.class, new OmeroShapes.GsonShapeSerializer()).setLenient().create();
		Gson gsonDetection  = new GsonBuilder().registerTypeAdapter(PathDetectionObject.class, new OmeroShapes.GsonShapeSerializer()).setLenient().create();
		
		// Iterate through PathObjects and get their JSON representation
		List<String> jsonList = new ArrayList<>();
		for (var pathObject: pathObjects) {
			String myJson = "";
			if (pathObject instanceof PathAnnotationObject)
				myJson = gsonAnnotation.toJson(pathObject);
			else
				myJson = gsonDetection.toJson(pathObject);
			
			var gson = GsonTools.getInstance();
			try {
				// See if resulting JSON is a list (e.g. Points/MultiPolygon)
				List<JsonElement> roiList = Arrays.asList(gson.fromJson(myJson, JsonElement[].class));
				roiList.forEach(e -> jsonList.add(e.toString()));
			} catch (Exception e) {
				jsonList.add(myJson);
			}
		}
		
		// Build the JSON String
		StringBuilder json = new StringBuilder("{\"imageId\":" + server.getId() + ",\n" +
				"\"rois\":{\"count\":" + jsonList.size() + ",\n" +
				"\"empty_rois\":{},\n" +
				"\"new_and_deleted\":[],\n" +
				"\"deleted\":{},\n" +
				"\"new\":[");

		// Append PathObjects' JSON, separated by comma
		json.append(String.join(", ", jsonList));

		// End of JSON, no OMERO ROI was modified
		json.append("],\"modified\":[]}}");
		
		// Create request
		URL url = new URL(scheme, host, -1, "/iviewer/persist_rois/");
		var conn = url.openConnection();
		conn.setRequestProperty("Referer", new URL(scheme, host, -1, "/iviewer/?images=" + id).toString());
		conn.setRequestProperty("X-CSRFToken", server.getWebClient().getToken());
		conn.setDoOutput(true);
		
		// Send JSON
		OutputStream stream = conn.getOutputStream();
		stream.write(json.toString().getBytes("UTF-8"));
		stream.close();
		
		
		// Get response
		try (InputStream is = conn.getInputStream()) {
		      
		    InputStreamReader reader = new InputStreamReader(is);

		    //Creating a BufferedReader object
		    BufferedReader br = new BufferedReader(reader);
		    StringBuffer sb = new StringBuffer();
		    String str;
		    while((str = br.readLine())!= null){
		        sb.append(str);
		    }
		    if (sb.toString().toLowerCase().contains("error")) {
		    	logger.error(sb.toString());
		    	return false;
		    }
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		    return false;
		}
		return true;
	}
	
	/**
	 * Return the thumbnail of the Omero object corresponding to the specified id.
	 * @param server
	 * @param id
	 * @param prefSize
	 * @return thumbnail
	 * @throws IOException
	 */
	public static BufferedImage getThumbnail(OmeroWebImageServer server, int id, int prefSize) throws IOException {
		URL url;
		try {
			url = new URL(server.getScheme(), server.getHost(), "/webgateway/render_thumbnail/" + id + "/" + prefSize);
		} catch (MalformedURLException e) {
			logger.warn(e.getLocalizedMessage());
			return null;
		}
		
		return ImageIO.read(url);
	}
	
	
    /**
     * OMERO requests that return a list of items are paginated 
     * (see <a href="https://docs.openmicroscopy.org/omero/5.6.1/developers/json-api.html#pagination">OMERO API docs</a>).
     * Using this helper method ensures that all the requested data is retrieved.
     * @param url
     * @return list of {@code Json Element}s
     * @throws IOException
     */
	// TODO: Consider using parallel/asynchronous requests
    static List<JsonElement> readPaginated(URL url) throws IOException {
    	List<JsonElement> jsonList = new ArrayList<>();
        String symbol = (url.getQuery() != null && !url.getQuery().isEmpty()) ? "&" : "?";

        // Open connection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int response = connection.getResponseCode();
        
        // Catch bad response
        if (response != 200)
        	return jsonList;
        
        InputStreamReader reader = new InputStreamReader(connection.getInputStream());
        JsonObject map = GsonTools.getInstance().fromJson(reader, JsonObject.class);
        map.get("data").getAsJsonArray().forEach(jsonList::add);
        reader.close();

        JsonObject meta = map.getAsJsonObject("meta");
        int offset = 0;
        int totalCount = meta.get("totalCount").getAsInt();
        int limit = meta.get("limit").getAsInt();
        while (offset + limit < totalCount) {
            offset += limit;
            URL nextURL = new URL(url + symbol + "offset=" + offset);
            InputStreamReader newPageReader = new InputStreamReader(nextURL.openStream());
            JsonObject newPageMap = GsonTools.getInstance().fromJson(newPageReader, JsonObject.class);
            newPageMap.get("data").getAsJsonArray().forEach(jsonList::add);
        }
        return jsonList;
    }
    
    /**
     * Helper method to retrieve an {@code OmeroObject} from a given URL.
     * An IOException will be thrown if the connection fails or if the URL 
     * request a list of object instead of a single object.
     * <p>
     * N.B: this method does not set the parent object.
     * @param url
     * @return OmeroObject
     * @throws IOException
     */
    static OmeroObject readOmeroObject(URL url) throws IOException {
        // Open connection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int response = connection.getResponseCode();
        
        // Catch bad response
        if (response != 200)
        	throw new IOException("Connetion to " + url.getHost() + " failed: Error " + response + ".");
        
        // Read input stream
        InputStreamReader reader = new InputStreamReader(connection.getInputStream());
        JsonObject map = GsonTools.getInstance().fromJson(reader, JsonObject.class);
        reader.close();
        
        // Create OmeroObject
        var gson = new GsonBuilder().registerTypeAdapter(OmeroObject.class, new OmeroObjects.GsonOmeroObjectDeserializer()).setLenient().create();
        var omeroObj = gson.fromJson(map.get("data").getAsJsonObject(), OmeroObject.class);
        
        return omeroObj;
    }
    
    
    static void promptBrowsingWindow(OmeroWebImageServer server) {
    	Stage dialog = new Stage();
		dialog.sizeToScene();
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath != null)
			dialog.initOwner(QuPathGUI.getInstance().getStage());
		dialog.setTitle("OMERO web server");
//		dialog.setAlwaysOnTop(true);
		
		OmeroWebImageServerBrowser browser = new OmeroWebImageServerBrowser(qupath, server);
		
		dialog.setScene(new Scene(browser.getPane()));
		
		dialog.setOnCloseRequest(e -> browser.shutdownPool());
		
		dialog.showAndWait();
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
	static List<URI> getURIs(URI uri, String... args) throws IOException {
		List<URI> list = new ArrayList<>();
        String elemId = "image-";
        String query = uri.getQuery() != null ? uri.getQuery() : "";
        String shortPath = uri.getPath() + query;
        Pattern[] similarPatterns = new Pattern[] {patternOldViewer, patternNewViewer, patternWebViewer};

        // Check for simpler patterns first
        for (int i = 0; i < similarPatterns.length; i++) {
        	var matcher = similarPatterns[i].matcher(shortPath);
            if (matcher.find()) {
                elemId += matcher.group(1);
                list.add(URI.create(uri.getScheme() + "://" + uri.getHost() + "/webclient/?show=" + elemId));
                return list;
            }
        }

        // If no simple pattern was matched, check for the last possible one: /webclient/?show=
        if (shortPath.startsWith("/webclient/show")) {
        	URI newURI = getStandardURI(uri);
            var patternElem = Pattern.compile("image-(\\d+)");
            var matcherElem = patternElem.matcher(newURI.toString());
            while (matcherElem.find()) {
                list.add(URI.create(uri.getScheme() + "://" + uri.getHost() + uri.getPath() + "?show=" + "image-" + matcherElem.group(1)));
            }
        	return list;
        }
        
        // At this point, no valid URI pattern was found
        throw new IOException("URI not recognized: " + uri.toString());
	}
	
	static URI getStandardURI(URI uri, String... args) throws IOException {
		List<String> ids = new ArrayList<String>();
		String vertBarSign = "%7C";
		
		// Identify the type of element shown (e.g. dataset)
        var type = "";
        String query = uri.getQuery() != null ? uri.getQuery() : "";
        
        // Because of encoding, the equal sign might not be recognized when loading .qpproj file
        query = query.replace("%3D", "=");
        
        // Match 
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
        		var data = OmeroTools.readPaginated(request);
        		
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
        		var data = OmeroTools.readPaginated(request);
        		
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
	
	/**
	 * Return the Id associated with the URI provided ().
	 * If multiple Ids are present, only the first one will be retrieved.
	 * If no no Id could be found, return null.
	 * @param uri
	 * @return Id
	 */
	public static String getOmeroObjectId(URI uri) {
		Pattern patternLink = Pattern.compile("show=image-(\\d+)");
		Pattern[] similarPatterns = new Pattern[] {patternLink, patternNewViewer, patternWebViewer};
        for (int i = 0; i < similarPatterns.length; i++) {
        	var matcher = similarPatterns[i].matcher(uri.getQuery());
        	if (matcher.find())
        		return matcher.group(1);
        }
        return null;
	}
}
