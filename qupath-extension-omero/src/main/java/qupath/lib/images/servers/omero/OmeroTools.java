/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.scene.Node;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.images.servers.omero.OmeroAnnotations.OmeroAnnotationType;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObject;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObjectType;
import qupath.lib.images.servers.omero.OmeroObjects.OrphanedFolder;
import qupath.lib.images.servers.omero.OmeroObjects.Server;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;


/**
 * Static helper methods related to OMEROWebImageServer.
 * 
 * @author Melvin Gelbard
 *
 */
public final class OmeroTools {
	
	private final static Logger logger = LoggerFactory.getLogger(OmeroTools.class);
	
	/**
	 * Patterns to parse image URIs (for IDs)
	 */
	private final static Pattern patternOldViewer = Pattern.compile("/webgateway/img_detail/(\\d+)");
	private final static Pattern patternNewViewer = Pattern.compile("images=(\\d+)");
	private final static Pattern patternWebViewer= Pattern.compile("/webclient/img_detail/(\\d+)");
	private final static Pattern patternLinkImage = Pattern.compile("show=image-(\\d+)");
	private final static Pattern patternImgDetail = Pattern.compile("img_detail/(\\d+)");
	private final static Pattern[] imagePatterns = new Pattern[] {patternOldViewer, patternNewViewer, patternWebViewer, patternImgDetail, patternLinkImage};
	
	/**
	 * Pattern to recognize the OMERO type of an URI (i.e. Project, Dataset, Image, ..)
	 */
	private final static Pattern patternType = Pattern.compile("show=(\\w+-)");
	
	/**
	 * Patterns to parse Projects and Datasets ID ('link URI').
	 */
	private final static Pattern patternLinkProject = Pattern.compile("show=project-(\\d+)");
	private final static Pattern patternLinkDataset = Pattern.compile("show=dataset-(\\d+)");
	
	/**
	 * Suppress default constructor for non-instantiability
	 */
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
	 * Get all the OMERO objects (inside the parent Id) present in the OMERO server with the specified 
	 * URI.
	 * <p>
	 * No orphaned {@code OmeroObject} will be fetched.
	 * 
	 * @param uri
	 * @param parent
	 * @return list of OmeroObjects
	 * @throws IOException
	 */
	public static List<OmeroObject> readOmeroObjects(URI uri, OmeroObject parent) throws IOException {
		List<OmeroObject> list = new ArrayList<>();
		if (parent == null)
			return list;
		
		OmeroObjectType type = OmeroObjectType.PROJECT;
		if (parent.getType() == OmeroObjectType.PROJECT)
			type = OmeroObjectType.DATASET;
		else if (parent.getType() == OmeroObjectType.DATASET)
			type = OmeroObjectType.IMAGE;

		var gson = new GsonBuilder().registerTypeAdapter(OmeroObject.class, new OmeroObjects.GsonOmeroObjectDeserializer()).setLenient().create();
		List<JsonElement> data = OmeroRequests.requestObjectList(uri.getScheme(), uri.getHost(), type, parent);
		for (var d: data) {
			gson = new GsonBuilder().registerTypeAdapter(OmeroObject.class, new OmeroObjects.GsonOmeroObjectDeserializer()).setLenient().create();
			try {
				var omeroObj = gson.fromJson(d, OmeroObject.class);
				omeroObj.setParent(parent);
				if (omeroObj != null)
					list.add(omeroObj);
			} catch (Exception e) {
				logger.error("Error parsing OMERO object: " + e.getLocalizedMessage(), e);
			}
		}
		
		return list;
	}
	
//	/**
//	 * Get all the orphaned images in the given server.
//	 * 
//	 * @param uri
//	 * @return list of orphaned images
//	 * @see #populateOrphanedImageList(URI, OrphanedFolder)
//	 */
//	public static List<OmeroObject> readOrphanedImages(URI uri) {
//		List<OmeroObject> list = new ArrayList<>();
//
//		// Requesting orphaned images can time-out the JSON API on OMERO side if too many,
//		// so we go through the webclient, whose response comes in a different format.
//		try {
//			var map = OmeroRequests.requestWebClientObjectList(uri.getScheme(), uri.getHost(), OmeroObjectType.IMAGE);
//        	
//        	// Send requests in separate threads, this is not a great design
//        	// TODO: Clean up this code, which now does: 
//        	// 1. Send request for each image in the list of orphaned images in the executor
//        	// 2. Terminate the executor after 5 seconds
//        	// 3. Checks if there are still requests that weren't processed and gives log error if so
//        	// Solution: Give a time-out for the request in readPaginated() :::
//        	//
//        	// URLConnection con = url.openConnection();
//        	// con.setConnectTimeout(connectTimeout);
//        	// con.setReadTimeout(readTimeout);
//        	// InputStream in = con.getInputStream();
//        	//
//    		ExecutorService executorRequests = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("orphaned-image-requests", true));
//    		List<Future<?>> futures = new ArrayList<Future<?>>();
//        	
//    		map.get("images").getAsJsonArray().forEach(e -> {
//    			// To keep track of the completed requests, keep a Future variable
//    			Future<?> future = executorRequests.submit(() -> {
//    				try {
//    					var id = Integer.parseInt(e.getAsJsonObject().get("id").toString());
//        				var omeroObj = readOmeroObject(uri.getScheme(), uri.getHost(), id, OmeroObjectType.IMAGE);
//        				list.add(omeroObj);	       
//    				} catch (IOException ex) {
//						logger.error("Could not fetch information for image id: " + e.getAsJsonObject().get("id"));
//					}
//    			});
//    			futures.add(future);
//        	});
//    		executorRequests.shutdown();
//    		try {
//    			// Wait 10 seconds to terminate tasks
//    			executorRequests.awaitTermination(10L, TimeUnit.SECONDS);
//    			long nPending = futures.parallelStream().filter(e -> !e.isDone()).count();
//    			if (nPending > 0)
//    				logger.warn("Too many orphaned images in " + uri.getHost() + ". Ignored " + nPending + " orphaned image(s).");
//    		} catch (InterruptedException ex) {
//    			logger.debug("InterrupedException occurred while interrupting requests.");
//    			Thread.currentThread().interrupt();
//    		}
//        } catch (IOException ex) {		
//        	logger.error(ex.getLocalizedMessage());
//        }
//		return list;
//	}
	
	/**
	 * Populate the specified {@code orphanedFolder}'s image list with all orphaned images in the server.
	 * <p>
	 * As soon as all the objects have been loaded in the list, the {@code isLoading} property of the 
	 * {@code OrphanedFodler} is modified accordingly.
	 * 
	 * @param uri
	 * @param orphanedFolder 
	 */
	public static synchronized void populateOrphanedImageList(URI uri, OrphanedFolder orphanedFolder) {
		var list = orphanedFolder.getImageList();
		orphanedFolder.setLoading(true);
		list.clear();
		
		try {
			var map = OmeroRequests.requestWebClientObjectList(uri.getScheme(), uri.getHost(), OmeroObjectType.IMAGE);
    		ExecutorService executorRequests = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("orphaned-image-requests", true));
    		
    		// Get the total amount of orphaned images to load
    		int max = map.get("images").getAsJsonArray().size();
    		if (max == 0)
    			orphanedFolder.setLoading(false);
    		
    		orphanedFolder.setTotalChildCount(max);
    		map.get("images").getAsJsonArray().forEach(e -> {
    			executorRequests.submit(() -> {
    				try {
    					var id = Integer.parseInt(e.getAsJsonObject().get("id").toString());
    					OmeroObject omeroObj = readOmeroObject(uri.getScheme(), uri.getHost(), id, OmeroObjectType.IMAGE);
        				Platform.runLater(() -> {
        					list.add(omeroObj);
        					
        					// Check if all orphaned images were loaded
        					if (orphanedFolder.incrementAndGetLoadedCount() >= max)
        						orphanedFolder.setLoading(false);
        				});
    				} catch (IOException ex) {
						logger.error("Could not fetch information for image id: " + e.getAsJsonObject().get("id"));
					}
    			});
        	});
    		executorRequests.shutdown();
        } catch (IOException ex) {
        	logger.error(ex.getLocalizedMessage());
        }
	}
	
	/**
	 * Get all the orphaned {@code OmeroObject}s of type {@code type} from the server.
	 * 
	 * @param uri
	 * @param server the parent {@code Server} object
	 * @return list of orphaned datasets
	 * @throws IOException 
	 */
	public static List<OmeroObject> readOrphanedDatasets(URI uri, Server server) throws IOException {
		List<OmeroObject> list = new ArrayList<>();
		var gson = new GsonBuilder().registerTypeAdapter(OmeroObject.class, new OmeroObjects.GsonOmeroObjectDeserializer()).setLenient().create();
	
		var orphanedDatasets = OmeroRequests.requestObjectList(uri.getScheme(), uri.getHost(), OmeroObjectType.DATASET, true);
		for (var d: orphanedDatasets) {
			try {
				OmeroObject omeroObj = gson.fromJson(d, OmeroObject.class);
				omeroObj.setParent(server);
				if (omeroObj != null)
					list.add(omeroObj);
			} catch (Exception e) {
				logger.error("Error parsing OMERO object: {}", e.getLocalizedMessage());
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
    public static OmeroObject readOmeroObject(String scheme, String host, int id, OmeroObjectType type) throws IOException {
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
     * @param type 
	 * @return Id
	 */
	public static int parseOmeroObjectId(URI uri, OmeroObjectType type) {
		String cleanUri = uri.toString().replace("%3D", "=");
		Matcher m;
		switch (type) {
			case SERVER:
				logger.error("Cannot parse an ID from OMERO server.");
				break;
			case PROJECT:
				m = patternLinkProject.matcher(cleanUri);
				if (m.find()) return Integer.parseInt(m.group(1));
				break;
			case DATASET:
				m = patternLinkDataset.matcher(cleanUri);
				if (m.find()) return Integer.parseInt(m.group(1));
				break;
			case IMAGE:
				for (var p: imagePatterns) {
					m = p.matcher(cleanUri);
					if (m.find()) return Integer.parseInt(m.group(1));
				}
				break;
			default:
				throw new UnsupportedOperationException("Type (" + type + ") not supported");
		}
		return -1;
	}
	
    /**
	 * Return the type associated with the {@code URI} provided.
	 * If multiple types are present, only the first one will be retrieved.
	 * If no type is found, return UNKNOWN.
	 * <p>
	 * Accepts the same formats as the {@code OmeroWebImageServer} constructor.
	 * <br>
	 * E.g., https://{server}/webclient/?show=dataset-{datasetId}
	 * 
	 * @param uri
	 * @return omeroObjectType
	 */
	public static OmeroObjectType parseOmeroObjectType(URI uri) {
			var uriString = uri.toString().replace("%3D", "=");
	        if (patternLinkProject.matcher(uriString).find())
	        	return OmeroObjectType.PROJECT;
	        else if (patternLinkDataset.matcher(uriString).find())
	        	return OmeroObjectType.DATASET;
	        else {
	        	for (var p: imagePatterns) {
	        		if (p.matcher(uriString).find())
	        			return OmeroObjectType.IMAGE;
	        	}
	        }
	        return OmeroObjectType.UNKNOWN;
	}
	
	
	/**
	 * Request the {@code OmeroAnnotations} object of type {@code category} associated with 
	 * the {@code OmeroObject} specified.
	 * 
	 * @param uri
	 * @param obj
	 * @param category
	 * @return omeroAnnotations object
	 */
	public static OmeroAnnotations readOmeroAnnotations(URI uri, OmeroObject obj, OmeroAnnotationType category) {
		try {
			var json = OmeroRequests.requestOMEROAnnotations(uri.getScheme(), uri.getHost(), obj.getId(), obj.getType(), category);
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
		Gson gsonTMAs  = new GsonBuilder().registerTypeAdapter(TMACoreObject.class, new OmeroShapes.GsonShapeSerializer()).serializeSpecialFloatingPointValues().setLenient().create();
		Gson gsonAnnotation = new GsonBuilder().registerTypeAdapter(PathAnnotationObject.class, new OmeroShapes.GsonShapeSerializer()).setLenient().create();
		Gson gsonDetection  = new GsonBuilder().registerTypeAdapter(PathDetectionObject.class, new OmeroShapes.GsonShapeSerializer()).serializeSpecialFloatingPointValues().setLenient().create();
		
		// Iterate through PathObjects and get their JSON representation
		List<String> jsonList = new ArrayList<>();
		for (var pathObject: pathObjects) {
			String myJson = "";
			if (pathObject.isTMACore())
				myJson = gsonTMAs.toJson(pathObject);
			else if (pathObject.isAnnotation())
				myJson = gsonAnnotation.toJson(pathObject);
			else if (pathObject.isDetection()) {
				// TODO: ugly design, should improve this
				if (pathObject instanceof PathCellObject) {
					var detTemp = PathObjects.createDetectionObject(pathObject.getROI());
					detTemp.setPathClass(pathObject.getPathClass());
					detTemp.setColorRGB(pathObject.getColorRGB());
					detTemp.setName(pathObject.getName());
					pathObject = detTemp;
				}
				myJson = gsonDetection.toJson(pathObject);				
			} else
				throw new IOException(String.format("Type not supported: %s", pathObject.getClass()));
			
			try {
				// See if resulting JSON is a list (e.g. Points/MultiPolygon)
				List<JsonElement> roiList = Arrays.asList(GsonTools.getInstance().fromJson(myJson, JsonElement[].class));
				roiList.forEach(e -> jsonList.add(e.toString()));
			} catch (Exception e) {
				jsonList.add(myJson);
			}
		}
		
		return OmeroRequests.requestWriteROIs(scheme, host, Integer.parseInt(id), server.getWebclient().getToken(), jsonList);
	}
	
	/**
	 * Return the thumbnail of the OMERO image corresponding to the specified {@code imageId}.
	 * 
	 * @param server
	 * @param imageId
	 * @param prefSize
	 * @return thumbnail
	 */
	public static BufferedImage getThumbnail(OmeroWebImageServer server, int imageId, int prefSize) {
		try {
			return OmeroRequests.requestThumbnail(server.getScheme(), server.getHost(), imageId, prefSize);			
		} catch (IOException e) {
			logger.warn("Error requesting the thumbnail: {}", e.getLocalizedMessage());
			return null;
		}
	}
	
	/**
	 * Return the thumbnail of the OMERO image corresponding to the specified {@code id}.
	 * 
	 * @param uri
	 * @param id
	 * @param prefSize
	 * @return thumbnail
	 */
	public static BufferedImage getThumbnail(URI uri, int id, int prefSize) {
		try {
			return OmeroRequests.requestThumbnail(uri.getScheme(), uri.getHost(), id, prefSize);
		} catch (IOException e) {
			logger.warn("Error requesting the thumbnail: {}", e.getLocalizedMessage());
			return null;
		}
	}
	
	
//	/**
//	 * Return a list of all {@code OmeroWebClient}s that are using the specified URI (based on their {@code host}).
//	 * @param uri
//	 * @return
//	 */
//	public static OmeroWebClient getWebClients(URI uri) {
//		var webclients = OmeroWebClients.getAllClients();
//		return webclients.values().parallelStream().flatMap(List::stream).filter(e -> e.getURI().getHost().equals(uri.getHost())).collect(Collectors.toList());
//	}
	
	
	/**
	 * Return a clean URI of the server from which the given URI is specified. This method relies on 
	 * the specified {@code uri} to be formed properly (with a scheme and a host). 
	 * <p>
	 * A few notes:
	 * <li> If the URI does not contain a host (but does a path), it will be returned without modification. </li>
	 * <li> If no host <b> and </b> no path is found, {@code null} is returned. </li>
	 * <li> If the specified {@code uri} does not contain a scheme, {@code https://} will be used. </li>
	 * <p>
	 * E.g. {@code https://www.my-server.com/show=image-462} returns {@code https://www.my-server.com/}
	 * 
	 * @param uri
	 * @return clean uri
	 */
	public static URI getServerURI(URI uri) {
		if (uri == null)
			return null;
		
		try {
			var host = uri.getHost();
			var path = uri.getPath();
			if (host == null || host.isEmpty())
				return (path == null || path.isEmpty()) ? null : uri;

			var scheme = uri.getScheme();
			if (scheme == null || scheme.isEmpty())
				scheme = "https://";
			return new URL(scheme, host, "").toURI();
		} catch (MalformedURLException | URISyntaxException ex) {
			logger.error("Could not parse server from {}", uri.toString());
		}
		return null;
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
	 * <p>
	 * E.g. "{@code /host/webclient/?show=image=4|image=5}" returns a list containing: 
	 * "{@code /host/webclient/?show=image=4}" and "{@code /host/webclient/?show=image=5}".
	 * 
	 * @param uri
	 * @return list
	 * @throws IOException
	 */
    static List<URI> getURIs(URI uri) throws IOException {
		List<URI> list = new ArrayList<>();
		uri = URI.create(uri.toString().replace("show%3Dimage-", "show=image-"));
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
	
    static URI getStandardURI(URI uri) throws IOException {
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
    
	static Node createStateNode(boolean loggedIn) {
		var state = loggedIn ? IconFactory.PathIcons.ACTIVE_SERVER : IconFactory.PathIcons.INACTIVE_SERVER;
		return IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, state);
	}
}
