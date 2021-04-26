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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.List;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.omero.OmeroAnnotations.OmeroAnnotationType;
import qupath.lib.images.servers.omero.OmeroObjects.Group;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObject;
import qupath.lib.images.servers.omero.OmeroObjects.OmeroObjectType;
import qupath.lib.images.servers.omero.OmeroObjects.Owner;
import qupath.lib.io.GsonTools;

/**
 * Class for handling OMERO requests.
 * @author Melvin Gelbard
 *
 */
public final class OmeroRequests {
	
	private static final String WEBCLIENT_READ_ANNOTATION = "/webclient/api/annotations/?type=%s&%s=%d&limit=10000&_=&%d";
	
	private static final String WEBGATEWAY_DATA = "/webgateway/imgData/%d";
	private static final String WEBGATEWAY_THUMBNAIL = "/webgateway/render_thumbnail/%d/%d";	// '/webgateway/render_thumbnail/101/256'
	private static final String WEBGATEWAY_ICON = "/static/webgateway/img/%s";
	private static final String WEBGATEWAY_IMAGE_ICON = "/static/webclient/image/%s";
	
	private static final String JSON_API_INFO = "/api/v0/m/%s/%d";					// '/api/v0/m/{images}/{101}'
	private static final String JSON_API_LIST = "/api/v0/m/%s/?%s";					// '/api/v0/m/{images}/?{childCount=true}'
	private static final String JSON_API_FILTERED_LIST = "/api/v0/m/%s/%d/%s/?%s";	// '/api/v0/m/{datasets}/{103}/{images}/?{childCount=true}'
	private static final String JSON_API_ROIS = "/api/v0/m/rois/?image=%s";
	
	/**
	 * Suppress default constructor for non-instantiability
	 */
	private OmeroRequests() {
		throw new AssertionError();
	}
	
	/**
	 * Request the metadata of OMERO image with {@code id}.
	 * @param scheme
	 * @param host
	 * @param id
	 * @return metadata json
	 * @throws IOException
	 */
	public static JsonObject requestMetadata(String scheme, String host, int id) throws IOException {
		URL url = new URL(scheme, host, String.format(WEBGATEWAY_DATA, id));
		InputStreamReader reader = new InputStreamReader(url.openStream());
		JsonObject map = new Gson().fromJson(reader, JsonObject.class);
		reader.close();
		return map;
	}
	
	/**
	 * Request information about an OMERO object with id ({@code id}) and OMERO type ({@code type}). 
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param id object's OMERO id
	 * @param type object's type
	 * @return json response
	 * @throws IOException
	 */
	public static JsonObject requestObjectInfo(String scheme, String host, int id, OmeroObjectType type) throws IOException {
		return requestObjectInfo(scheme, host, id, type, null);
	}
	
	/**
	 * Request information about an OMERO object with id ({@code id}) and OMERO type ({@code type}).
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param id object's OMERO id
	 * @param type object's type
	 * @param args
	 * @return json response
	 * @throws IOException
	 */
	public static JsonObject requestObjectInfo(String scheme, String host, int id, OmeroObjectType type, String args) throws IOException {
		// Create URL
		URL url = new URL(scheme, host, String.format(JSON_API_INFO, type.toURLString(), id) + (args == null ? "" : args));
		
		// Open connection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int response = connection.getResponseCode();
        
        // Catch bad response
        if (response != 200)
        	throw new IOException(String.format("Connection to %s failed: Error %d.", url.getHost(), response));
        
        // Read input stream
        InputStreamReader reader = new InputStreamReader(connection.getInputStream());
        JsonObject json = GsonTools.getInstance().fromJson(reader, JsonObject.class);
        reader.close();

        // Return json
		return json;
	}
	
	/**
	 * Request a list of all {@code OmeroObject}s with type {@code objectType} from the server. 
	 * This will <b>not</b> fetch orphaned objects.
	 * <p>
	 * A list of {@code JsonElement}s is returned as the OMERO API response is paginated.
	 * <p>
	 * N.B. There has been some issues when using this method to request very large quantities of 
	 * objects, resulting in a time-out error from OMERO.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param objectType object's type
	 * @return list of json responses
	 * @throws IOException
	 * @see #requestWebClientObjectList
	 */
	public static List<JsonElement> requestObjectList(String scheme, String host, OmeroObjectType objectType) throws IOException {
			return requestObjectList(scheme, host, objectType, null, -1);
	}
	
	/**
	 * Request a list of all {@code OmeroObject}s with type {@code objectType} from the server.
	 * Depending on whether the {@code orphaned} flag is active, the method will return 
	 * <b>only</b> orphaned objects or <b>only</b> non-orphaned objects.
	 * 
	 * <p>
	 * N.B. There has been some issues when using this method to request very large quantities of 
	 * objects, resulting in a time-out error from OMERO.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param objectType object's type
	 * @param onlyOrphaned type of objects to request
	 * @return list of json responses
	 * @throws IOException
	 * @see #requestWebClientObjectList
	 */
	public static List<JsonElement> requestObjectList(String scheme, String host, OmeroObjectType objectType, boolean onlyOrphaned) throws IOException {
		return requestObjectList(scheme, host, objectType, onlyOrphaned ? OmeroObjectType.SERVER : objectType, -1);
	}
	
	/**
	 * Request a list of {@code OmeroObject}s with type ({@code objectType}) and parent ({@code parent}) from the server.
	 * A list of {@code JsonElement}s is returned as the OMERO API response is paginated.
	 * <p>
	 * Note: if {@code parent}'s type is {@code SERVER} and {@code objectType} is different than {@code PROJECT}, 
	 * orphaned {@code OmeroObject}s will be returned.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param objectType object's type
	 * @param parent object's parent type
	 * @return list of json responses
	 * @throws IOException
	 * @see #requestWebClientObjectList
	 */
	public static List<JsonElement> requestObjectList(String scheme, String host, OmeroObjectType objectType, OmeroObject parent) throws IOException {
		return requestObjectList(scheme, host, objectType, parent.getType(), parent.getId());
	}
	
	/**
	 * Request a list of {@code OmeroObject}s with type {@code objectType} and parent's id {@code parentId} from the server.
	 * A list of {@code JsonElement}s is returned as the OMERO API response is paginated.
	 * <p>
	 * Note: if {@code parentId == -1}, all {@code OmeroObject}s of type {@code objectType} in the server will be 
	 * returned (without parent restriction).
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param objectType object's type
	 * @param parentId object's parent id
	 * @return list of json responses
	 * @throws IOException
	 * @see #requestWebClientObjectList
	 */
	public static List<JsonElement> requestObjectList(String scheme, String host, OmeroObjectType objectType, int parentId) throws IOException {
		if (objectType == OmeroObjectType.SERVER)
			throw new InvalidParameterException("objectType parameter cannot be OmeroObjectType.SERVER.");
		
		if (objectType == OmeroObjectType.PROJECT || objectType == OmeroObjectType.SCREEN)
			return requestObjectList(scheme, host, objectType, OmeroObjectType.SERVER, parentId);
		if (objectType == OmeroObjectType.DATASET)
			return requestObjectList(scheme, host, objectType, OmeroObjectType.PROJECT, parentId);
		else if (objectType == OmeroObjectType.IMAGE)
			return requestObjectList(scheme, host, objectType, OmeroObjectType.DATASET, parentId);
		else if (objectType == OmeroObjectType.PLATE)
			return requestObjectList(scheme, host, objectType, OmeroObjectType.SCREEN, parentId);
		else if (objectType == OmeroObjectType.WELL)			
			return requestObjectList(scheme, host, objectType, OmeroObjectType.PLATE, parentId);
		else
			throw new IOException(String.format("Could not recognized OMERO object: %s", objectType.toString()));
	}
	
	/**
	 * Request a list of {@code OmeroObject}s with type {@code objectType} and parent's id {@code parentId} from the server. 
	 * A list of {@code JsonElement}s is returned as the OMERO API response is paginated.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param objectType object's type
	 * @param parentType type of object's parent
	 * @param parentId object's parent id
	 * @return list of json responses
	 * @throws IOException
	 * @see #requestWebClientObjectList
	 */
	public static List<JsonElement> requestObjectList(String scheme, String host, OmeroObjectType objectType, OmeroObjectType parentType, int parentId) throws IOException {
		URL url;
		String query = "childCount=true";
		if (parentType == OmeroObjectType.SERVER)	// Orphaned
			url = new URL(scheme, host, String.format(JSON_API_LIST, objectType.toURLString(), query) + "&orphaned=true");
		else if (parentId == -1)					// All OmeroObjects of type 'objectType'
			url = new URL(scheme, host, String.format(JSON_API_LIST, objectType.toURLString(), query));
		else										// All OmeroObjects of type 'objectType' with parent
			url = new URL(scheme, host, String.format(JSON_API_FILTERED_LIST, parentType.toURLString(), parentId, objectType.toURLString(), query));

		// Return json
		return OmeroTools.readPaginated(url);
		
	}
	
	/**
	 * Request a list of all {@code OmeroObject}s with type {@code objectType} from the server via the Webclient.
	 * <p>
	 * This can be an alternative to {@link #requestObjectList(String, String, OmeroObjectType)} if the OMERO API times out.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param objectType object's type
	 * @return JsonObject
	 * @throws IOException
	 * @see #requestObjectList
	 */
	public static JsonObject requestWebClientObjectList(String scheme, String host, OmeroObjectType objectType) throws IOException {
		URL urlOrphanedImages = new URL(scheme, host, String.format("/webclient/api/%s/?orphaned=true", objectType.toURLString()));
		HttpURLConnection connection = (HttpURLConnection) urlOrphanedImages.openConnection();
        if (connection.getResponseCode() == 200) {
        	InputStreamReader reader = new InputStreamReader(connection.getInputStream());
        	JsonObject map = GsonTools.getInstance().fromJson(reader, JsonObject.class);
        	reader.close();
        	return map;
        } else
        	throw new IOException(String.format("Error %d while connecting to OMERO Webclient: %s", connection.getResponseCode(), connection.getResponseMessage()));
	}
	
	/**
	 * Request all OMERO 'metadata' annotations (<b>not</b> QuPath annotations) of type {@code annType} 
	 * from the {@code OmeroObject} with the specified {@code id}.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param id object's id
	 * @param objType object's type
	 * @param annType annotation's type
	 * @return JsonElement
	 * @throws IOException
	 */
	public static JsonElement requestOMEROAnnotations(String scheme, String host, int id, OmeroObjectType objType, OmeroAnnotationType annType) throws IOException {
		URL url = new URL(scheme, host, String.format(WEBCLIENT_READ_ANNOTATION, annType.toURLString(), objType.toString().toLowerCase(), id, System.currentTimeMillis()));
		InputStreamReader reader = new InputStreamReader(url.openStream());
		JsonElement json = GsonTools.getInstance().fromJson(reader, JsonElement.class);
		reader.close();
		return json;
	}

	/**
	 * Request all the (OMERO) ROIs from the OMERO image with the specified {@code id}.
	 * A list of {@code JsonElement}s is returned as the OMERO API response is paginated.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param id object's id
	 * @return list of json responses
	 * @throws IOException
	 */
	public static List<JsonElement> requestROIs(String scheme, String host, String id) throws IOException {
		URL url = new URL(scheme, host, String.format(JSON_API_ROIS, id));
		return OmeroTools.readPaginated(url);
	}
	
	/**
	 * Request to write QuPath's annotations (in Json form) to the OMERO image with the specified {@code id}.
	 * It is recommended to use methods from {@link OmeroTools} directly with {@code PathObject}s instead of this method.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param id object's id
	 * @param token webclient's token
	 * @param roiJsonList list of Jsons
	 * @return success
	 * @throws IOException
	 * @see OmeroTools
	 */
	public static boolean requestWriteROIs(String scheme, String host, int id, String token, List<String> roiJsonList) throws IOException {
		String request = String.format("{\"imageId\":%d,\n" +
				"\"rois\":{\"count\":%d,\n" +
				"\"empty_rois\":{},\n" +
				"\"new_and_deleted\":[],\n" +
				"\"deleted\":{},\n" +
				"\"new\":[%s],\"modified\":[]}}", id, roiJsonList.size(), String.join(", ", roiJsonList));
		
		
		// Create request
		URL url = new URL(scheme, host, -1, "/iviewer/persist_rois/");
		var conn = url.openConnection();
		conn.setRequestProperty("Referer", new URL(scheme, host, "/iviewer/?images=" + id).toString());
		conn.setRequestProperty("X-CSRFToken", token);
		conn.setDoOutput(true);
		
		// Send JSON
		OutputStream stream = conn.getOutputStream();
		stream.write(request.getBytes("UTF-8"));
		stream.close();
		
		
		// Get response
		String response = GeneralTools.readInputStreamAsString(conn.getInputStream());
	    if (response.toLowerCase().contains("error"))
	    	throw new IOException(response);
		
		return true;
	}

	/**
	 * Request a thumbnail for the OMERO image of size {@code prefSize} with the specified {@code id}.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param id object's id
	 * @param prefSize thumbnail's size
	 * @return thumbnail
	 * @throws IOException
	 */
	public static BufferedImage requestThumbnail(String scheme, String host, int id, int prefSize) throws IOException {
		URL url = new URL(scheme, host, String.format(WEBGATEWAY_THUMBNAIL, id, prefSize));			
		return ImageIO.read(url);
		
	}

	/**
	 * Request OMERO icon with the specified {@code iconFilename} from the provided server.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param iconFilename icon's filename
	 * @return icon
	 * @throws IOException
	 */
	public static BufferedImage requestIcon(String scheme, String host, String iconFilename) throws IOException {
		URL url = new URL(scheme, host, String.format(WEBGATEWAY_ICON, iconFilename));
		return ImageIO.read(url);
	}
	
	/**
	 * Request OMERO icon with the specified {@code iconFilename} from the provided server. 
	 * This is a separate method from {@link #requestIcon(String, String, String)} because 
	 * OMERO Image icons are hosted on a separate path.
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param iconFilename icon's filename
	 * @return icon
	 * @throws IOException
	 */
	public static BufferedImage requestImageIcon(String scheme, String host, String iconFilename) throws IOException {
		URL url = new URL(scheme, host, String.format(WEBGATEWAY_IMAGE_ICON, iconFilename));
		return ImageIO.read(url);
	}

	/**
	 * Request advanced search with specified {@code query}. A list of fields (e.g. 'name', 'description') to look 
	 * into can be specified as well as a list of datatypes (e.g. 'image', 'plate') to narrow the search. Additionally, 
	 * a specific Group/Owner can be provided to restrict the search to them. 
	 * 
	 * @param scheme server's scheme
	 * @param host server's host
	 * @param query query to search
	 * @param fields fields to query
	 * @param datatypes datatypes to query
	 * @param group group to restrict search to
	 * @param owner owner to restrict search to
	 * @return response
	 * @throws IOException
	 */
	public static String requestAdvancedSearch(String scheme, String host, String query, String[] fields, String[] datatypes,
			Group group, Owner owner) throws IOException {
		
		// Throw NPE to avoid unexpected behavior due to wrong OMERO URL syntax
		if (group == null || owner == null)
			throw new NullPointerException();
		
		String urlQuery = "/webclient/load_searching/form/?query=%s&%s&%s"
				+ "&searchGroup=%s"
				+ "&ownedBy=%s"
				+ "&useAcquisitionDate=false"
				+ "&startdateinput="
				+ "&enddateinput=&_=%d";
		
		URL url = new URL(
				scheme, 					// Scheme
				host, 						// Host
				String.format(urlQuery, 	// Long url query
						query, 
						String.join("&", fields), 
						String.join("&", datatypes), 
						group.getId(), 
						owner.getId(),
						System.currentTimeMillis()
				)
		);
		
		InputStreamReader reader = new InputStreamReader(url.openStream());
		String response = "";
		var temp = reader.read();
		while (temp != -1) {
			response += (char)temp;
			temp = reader.read();
		}
		
		return response;
	}

	/**
	 * Checks whether this QuPath instance is logged in to the specified server (<b>not</b> necessarily with access to its image).
	 * @param uri 
	 * @return isLoggedIn
	 */
	public static boolean isLoggedIn(URI uri) {
		try {
			var url = new URL(uri.getScheme(), uri.getHost(), "/api/v0/m/" + OmeroObjectType.PROJECT.toURLString());
			var conn = (HttpURLConnection) url.openConnection();
			return conn.getResponseCode() == 200;
		} catch (IOException e) {
			return false;
		}
	}
}
