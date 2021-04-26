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
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) {
		if (canConnectToOmero(uri, args)) {
			try {
				URI serverUri = OmeroTools.getServerURI(uri);
				OmeroWebClient client = OmeroWebClients.getClientFromServerURI(serverUri);
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
			try {
				if (canConnectToOmero(uri, args)) {
					List<URI> uris = OmeroTools.getURIs(uri);
					for (var subURI: uris) {
						try (var server = buildServer(subURI, args)) {
							builders.add(server.getBuilder());
						} catch (Exception e) {
							logger.debug("Unable to create OMERO server", e.getLocalizedMessage());
						}
					}					
				}
				
			} catch (IOException e) {
				Dialogs.showErrorNotification("OMERO web server", e.getLocalizedMessage());
			}
		}

		return UriImageSupport.createInstance(this.getClass(), supportLevel, builders);
	}

	/**
	 * Check whether QuPath can connect to the OMERO server & the OMERO object the given URI represents.
	 * Prompt login if required.
	 * 
	 * @param uri
	 * @param args
	 * @return success
	 */
	static boolean canConnectToOmero(URI uri, String... args) {
		try {
			if (supportLevel(uri) <= 0) {
				logger.debug("OMERO web server does not support {}", uri);
				return false;
			}
			
			var serverUri = OmeroTools.getServerURI(uri);
			if (serverUri == null)
				return false;
			
			var client = OmeroWebClients.getClientFromServerURI(serverUri);
			if (client == null)
				client = OmeroWebClient.create(serverUri, true);
			
			// Check if client can reach the image. If not, prompt login
			boolean isLoggedIn = true;
			if (!OmeroWebClient.canBeAccessed(uri, OmeroTools.parseOmeroObjectType(uri)))
				isLoggedIn = client.logIn(args);
			
			if (!isLoggedIn)
				return false;
			else {
				// Add the client to the list (but not URI yet!)
				OmeroWebClients.addClient(client);

				// Check if client can reach the OMERO object while being logged in
				if (!OmeroWebClient.canBeAccessed(uri, OmeroTools.parseOmeroObjectType(uri)))
					throw new AccessDeniedException(String.format("\"%s\" does not have permission to read %s", client.getUsername(), uri));
			}
			
			return true;
		} catch (AccessDeniedException | ConnectException ex) {		// Catch 'access not permitted'
			Dialogs.showErrorNotification("OMERO web server", ex.getLocalizedMessage());
		} catch (IllegalArgumentException  ex) {					// Catch wrong URI
			Dialogs.showErrorNotification("OMERO web server", ex.getLocalizedMessage());
			OmeroWebClients.addFailedHost(uri);
		} catch (URISyntaxException | IOException ex) {				// Catch errors when creating an OmeroWebClient
			Dialogs.showErrorNotification("OMERO web server", "Could not connect to OMERO web server.\nCheck the following:\n- Valid credentials.\n- Access permission.\n- Correct URL.");			
		}
		return false;
	}
	
	private static float supportLevel(URI uri, String...args) {
		var serverURI = OmeroTools.getServerURI(uri);
		
		// If we tried, and failed, to treat this as an OMERO server before, fail early
		if (OmeroWebClients.hasFailed(serverURI))
			return 0;

		// Check if we already had the same client before
		if (OmeroWebClients.getClientFromServerURI(serverURI) != null)
			return 4;

		String scheme = uri.getScheme();
		if (scheme.startsWith("http")) {
			// Try to connect (but don't log in yet)
			try {
				OmeroWebClient.create(serverURI, false);	// Dummy client
			} catch (Exception e) {
				logger.error("Unable to connect to OMERO server: {}", e.getLocalizedMessage());
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
}