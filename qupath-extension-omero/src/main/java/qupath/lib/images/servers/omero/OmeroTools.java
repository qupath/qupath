package qupath.lib.images.servers.omero;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.scene.Scene;
import javafx.stage.Stage;
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
			URL urlOrphanedDatasets = new URL(server.getScheme(), server.getHost(), -1, "/api/v0/m/datasets/?childCount=true&orphaned=true");
			URL urlOrphanedImages = new URL(server.getScheme(), server.getHost(), -1, "/api/v0/m/images/?childCount=true&orphaned=true");
			
			var orphanedData = readPaginated(urlOrphanedDatasets);
			orphanedData.addAll(readPaginated(urlOrphanedImages));
			
			for (var d: orphanedData) {
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
     * OMERO requests that return a list of items are paginated 
     * (see <a href="https://docs.openmicroscopy.org/omero/5.6.1/developers/json-api.html#pagination">OMERO API docs</a>).
     * Using this helper method ensures that all the requested data is retrieved.
     * @param url
     * @return list of {@code Json Element}s
     * @throws IOException
     */
	// TODO: Consider using parallel/asynchronous requests
    static List<JsonElement> readPaginated(URL url) throws IOException {
        String symbol = (url.getQuery() != null && !url.getQuery().isEmpty()) ? "&" : "?";

        InputStreamReader reader = new InputStreamReader(url.openStream());
        JsonObject map = GsonTools.getInstance().fromJson(reader, JsonObject.class);
        List<JsonElement> jsonList = new ArrayList<>();
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
		
		dialog.showAndWait();
    }
}
