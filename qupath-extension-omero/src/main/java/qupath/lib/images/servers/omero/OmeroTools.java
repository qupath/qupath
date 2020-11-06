package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import qupath.lib.common.ThreadTools;
import qupath.lib.images.servers.omero.OmeroAnnotations.OmeroAnnotationType;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObject;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObjectType;
import qupath.lib.images.servers.omero.OmeroObjects.Server;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;


/**
 * Static helper methods related to OMEROWebImageServer.
 * 
 * @author Melvin Gelbard
 *
 */
public class OmeroTools {
	
	private final static Logger logger = LoggerFactory.getLogger(OmeroTools.class);
	
	/**
	 * Patterns for parsing input URIs
	 */
	private final static Pattern patternOldViewer = Pattern.compile("/webgateway/img_detail/(\\d+)");
	private final static Pattern patternNewViewer = Pattern.compile("images=(\\d+)");
	private final static Pattern patternWebViewer= Pattern.compile("/webclient/img_detail/(\\d+)");
	private final static Pattern patternLink = Pattern.compile("show=image-(\\d+)");
	private final static Pattern patternImgDetail = Pattern.compile("img_detail/(\\d+)");
	private final static Pattern patternType = Pattern.compile("show=(\\w+-)");
	
	
	// Suppress default constructor for non-instantiability
	private OmeroTools() {
		throw new AssertionError();
	}
	
	/**
	 * Return the web client used for the specified OMERO server.
	 * 
	 * @param server
	 * @return client
	 */
	public static OmeroWebClient getWebclient(OmeroWebImageServer server) {
		return server.getWebclient();
	};
	
	
	/**
	 * Get all the OMERO objects (inside the parent Id) present in the OMERO server from which 
	 * the specified OmeroWebImageServer was created. 
	 * <p>
	 * If the parent object is an {@code OmeroObjects.Server}, orphaned {@code OmeroObjects.Dataset}s 
	 * and {@code OmeroObjects.Image}s will also be returned.
	 * 
	 * @param server
	 * @param parent 
	 * @return list of OmeroObjects
	 * @throws IOException
	 */
	public static List<OmeroObject> getOmeroObjects(OmeroWebImageServer server, OmeroObject parent) throws IOException {
		List<OmeroObject> list = new ArrayList<>();
		if (parent == null)
			return list;
		
		OmeroObjectType type = OmeroObjectType.PROJECT;
		if (parent.getType() == OmeroObjectType.PROJECT)
			type = OmeroObjectType.DATASET;
		else if (parent.getType() == OmeroObjectType.DATASET)
			type = OmeroObjectType.IMAGE;

		List<JsonElement> data = OmeroRequests.requestObjectList(server.getScheme(), server.getHost(), type, parent);
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
			
			var orphanedDatasets = OmeroRequests.requestObjectList(server.getScheme(), server.getHost(), OmeroObjectType.DATASET, true);
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
			// so we go through the webclient, whose response comes in a different format.
			try {
				var map = OmeroRequests.requestWebClientObjectList(server.getScheme(), server.getHost(), OmeroObjectType.IMAGE);
	        	
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
        					var id = Integer.parseInt(e.getAsJsonObject().get("id").toString());
	        				var omeroObj = getOmeroObject(server.getScheme(), server.getHost(), id, OmeroObjectType.IMAGE);
	        				omeroObj.setParent(parent);
	        				list.add(omeroObj);	       
        				} catch (IOException ex) {
    						logger.error("Could not fetch information for image id: " + e.getAsJsonObject().get("id"));
    					}
        			});
	    			futures.add(future);
	        	});
	    		executorRequests.shutdown();
	    		try {
	    			// Wait 10 seconds to terminate tasks
	    			executorRequests.awaitTermination(10L, TimeUnit.SECONDS);
	    			long nPending = futures.parallelStream().filter(e -> !e.isDone()).count();
	    			if (nPending > 0)
	    				logger.warn("Too many orphaned images in " + server.getHost() + ". Ignored " + nPending + " orphaned image(s).");
	    		} catch (InterruptedException ex) {
	    			logger.warn("An exception occurred while interrupting requests: " + ex);
	    		}
	        } catch (IOException ex) {		
	        	logger.error(ex.getLocalizedMessage());
	        }
		}
		return list;
	}
	
    /**
     * Helper method to retrieve an {@code OmeroObject} of type {@code OmeroObjectType} with {@code id} from 
     * a given server details. An IOException will be thrown if the connection fails.
     * <p>
     * N.B: this method does not set the parent object.
     * 
     * @param scheme
	 * @param host
	 * @param id
	 * @param type
	 * @return OmeroObject
	 * @throws IOException
     */
    public static OmeroObject getOmeroObject(String scheme, String host, int id, OmeroObjectType type) throws IOException {
    	// Request Json
    	var map = OmeroRequests.requestObjectInfo(scheme, host, id, type);
        
        // Create OmeroObject
        var gson = new GsonBuilder().registerTypeAdapter(OmeroObject.class, new OmeroObjects.GsonOmeroObjectDeserializer()).setLenient().create();
        return gson.fromJson(map.get("data").getAsJsonObject(), OmeroObject.class);
    }
    
    /**
	 * Return the Id associated with the {@code URI} provided.
	 * If multiple Ids are present, only the first one will be retrieved.
	 * If no Id could be found, return -1.
	 * 
	 * @param uri
	 * @return Id
	 */
	public static int getOmeroObjectId(URI uri) {
		String uriString = uri.toString().replace("show%3Dimage-", "show=image-");
		uriString = uriString.replace("/?images%3D", "/?images=");
		Pattern[] similarPatterns = new Pattern[] {patternLink, patternImgDetail, patternNewViewer, patternWebViewer};
        for (int i = 0; i < similarPatterns.length; i++) {
        	var matcher = similarPatterns[i].matcher(uriString);
        	if (matcher.find())
        		return Integer.parseInt(matcher.group(1));
        }
        return -1;
	}
	
	
	/**
	 * Request the {@code OmeroAnnotations} object of type {@code category} associated with 
	 * the {@code OmeroObject} specified.
	 * 
	 * @param server
	 * @param obj
	 * @param category
	 * @return omeroAnnotations object
	 */
	public static OmeroAnnotations getOmeroAnnotations(OmeroWebImageServer server, OmeroObject obj, OmeroAnnotationType category) {
		try {
			var json = OmeroRequests.requestOMEROAnnotations(server.getScheme(), server.getHost(), obj.getId(), obj.getType(), category);
			return OmeroAnnotations.getOmeroAnnotations(json.getAsJsonObject());
		} catch (Exception e) {
			logger.warn("Could not fetch {} information: {}", category, e.getLocalizedMessage());
			return null;
		}
	}
	
	
	/**
	 * Write PathObject collection to OMERO server. This will not delete the existing 
	 * ROIs present on the OMERO server. Rather, it will simply add the new ones.
	 * 
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
		Gson gsonDetection  = new GsonBuilder().registerTypeAdapter(PathDetectionObject.class, new OmeroShapes.GsonShapeSerializer()).serializeSpecialFloatingPointValues().setLenient().create();
		
		// Iterate through PathObjects and get their JSON representation
		List<String> jsonList = new ArrayList<>();
		for (var pathObject: pathObjects) {
			String myJson = "";
			if (pathObject instanceof PathAnnotationObject)
				myJson = gsonAnnotation.toJson(pathObject);
			else {
				// TODO: ugly design, should improve this
				if (pathObject instanceof PathCellObject) {
					var detTemp = PathObjects.createDetectionObject(pathObject.getROI());
					detTemp.setPathClass(pathObject.getPathClass());
					detTemp.setColorRGB(pathObject.getColorRGB());
					detTemp.setName(pathObject.getName());
					pathObject = detTemp;
				}
				myJson = gsonDetection.toJson(pathObject);				
			}
			
			var gson = GsonTools.getInstance();
			try {
				// See if resulting JSON is a list (e.g. Points/MultiPolygon)
				List<JsonElement> roiList = Arrays.asList(gson.fromJson(myJson, JsonElement[].class));
				roiList.forEach(e -> jsonList.add(e.toString()));
			} catch (Exception e) {
				jsonList.add(myJson);
			}
		}
		
		return OmeroRequests.requestWriteROIs(scheme, host, Integer.parseInt(id), server.getWebclient().getToken(), jsonList);
	}
	
	/**
	 * Return the thumbnail of the OMERO image corresponding to the specified {@code id}.
	 * 
	 * @param server
	 * @param id
	 * @param prefSize
	 * @return thumbnail
	 */
	public static BufferedImage getThumbnail(OmeroWebImageServer server, int id, int prefSize) {
		try {
			return OmeroRequests.requestThumbnail(server.getScheme(), server.getHost(), id, prefSize);			
		} catch (IOException e) {
			logger.warn("Error requesting the thumbnail: {}", e.getLocalizedMessage());
			return null;
		}
	}
	
	
    /**
     * OMERO requests that return a list of items are paginated 
     * (see <a href="https://docs.openmicroscopy.org/omero/5.6.1/developers/json-api.html#pagination">OMERO API docs</a>).
     * Using this helper method ensures that all the requested data is retrieved.
     * 
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
		OmeroObjectType type;
        String query = uri.getQuery() != null ? uri.getQuery() : "";
        
        // Because of encoding, the equal sign might not be recognized when loading .qpproj file
        query = query.replace("%3D", "=");
        
        // Match 
        var matcherType = patternType.matcher(query);
        if (matcherType.find())
            type = OmeroObjectType.fromString(matcherType.group(1).replace("-", ""));
        else
            throw new IOException("URI not recognized: " + uri.toString());
        
        var patternId = Pattern.compile(type.toString().toLowerCase() + "-(\\d+)");
        var matcherId = patternId.matcher(query);
        while (matcherId.find()) {
        	ids.add(matcherId.group(1));
        }
		
        // Cascading the types to get all ('leaf') images
        StringBuilder sb = new StringBuilder(uri.getScheme() + "://" + uri.getHost() + uri.getPath() + "?show=image-");
        List<String> tempIds = new ArrayList<String>();
        // TODO: Support screen and plates
        switch (type) {
        case SCREEN:
        	break;
        case PLATE:
        	break;
        case WELL:
        	break;
        case PROJECT:
        	for (String id: ids) {
        		var data = OmeroRequests.requestObjectList(uri.getScheme(), uri.getHost(), OmeroObjectType.DATASET, Integer.parseInt(id));
    			for (int i = 0; i < data.size(); i++) {
        			tempIds.add(data.get(i).getAsJsonObject().get("@id").getAsString());
        		}
        	}
        	ids =  new ArrayList<>(tempIds);
        	tempIds.clear();
        	type = OmeroObjectType.DATASET;
        	
        case DATASET:
        	for (String id: ids) {
        		var data = OmeroRequests.requestObjectList(uri.getScheme(), uri.getHost(), OmeroObjectType.IMAGE, Integer.parseInt(id));
    			for (int i = 0; i < data.size(); i++) {
    				tempIds.add(data.get(i).getAsJsonObject().get("@id").getAsString());
    			}	
        	}
        	ids = new ArrayList<>(tempIds);
        	tempIds.clear();
        	type = OmeroObjectType.IMAGE;
        	
        case IMAGE:
        	if (ids.isEmpty())
        		logger.info("No image found in URI: " + uri.toString());
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
}
