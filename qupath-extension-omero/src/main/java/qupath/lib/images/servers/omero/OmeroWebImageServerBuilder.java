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
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.dialogs.Dialogs;
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
	
	private static OmeroWebClient client = null;

	
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
				OmeroWebClient client = OmeroWebClients.getClient(uri.getHost());
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
			
			if (!client.loggedIn())
				OmeroWebClients.logIn(client, args);
			
			OmeroWebClients.addClient(host, client);
			return true;
		} catch (Exception e) {
			Dialogs.showErrorMessage("Omero web server", "Could not connect to OMERO web server.\nCheck the following:\n- Valid credentials.\n- Access permission.\n- Correct URL.");
		}
		return false;
	}
	
	private static float supportLevel(URI uri, String...args) {

		String host = uri.getHost();

		// If we tried, and failed, to treat this as an OMERO server before, fail early
		if (OmeroWebClients.hasFailed(host))
			return 0;

		client = OmeroWebClients.getClient(host);
		if (client != null)
			return 4;

		String scheme = uri.getScheme();
		if (scheme.startsWith("http")) {
			// Try to connect (but don't log in yet)
			try {
				client = OmeroWebClient.create(host);
			} catch (Exception e) {
				OmeroWebClients.addFailedHost(host);
				logger.error("Unable to connect to OMERO server", e.getLocalizedMessage());
				return 0;
			}
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
	
	private URI getStandardURI(URI uri, String... args) throws IOException {
		if (!canConnectToOmero(uri, args))
			throw new IOException("Problem connecting to OMERO web server");
		
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
}