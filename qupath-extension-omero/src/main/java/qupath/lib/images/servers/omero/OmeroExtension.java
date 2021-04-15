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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PaneTools;

/**
 * Extension to access images hosted on OMERO.
 */
public class OmeroExtension implements QuPathExtension {
	
	/**
	 * To handle the different stages of browsers (only allow one per OMERO server)
	 */
	private static Map<OmeroWebClient, OmeroWebImageServerBrowserCommand> browsers = new HashMap<>();

	@Override
	public void installExtension(QuPathGUI qupath) {
		var actionClients = ActionTools.createAction(new OmeroWebClientsCommand(qupath), "Manage server connections");
		var actionSendObjects = ActionTools.createAction(new OmeroWritePathObjectsCommand(qupath), "Send annotations to OMERO");
		Menu browseServerMenu = new Menu("Browse server...");
		
//		actionClients.disabledProperty().bind(qupath.projectProperty().isNull());
//		browseServerMenu.disableProperty().bind(qupath.projectProperty().isNull());
		actionSendObjects.disabledProperty().bind(qupath.imageDataProperty().isNull());
		
		MenuTools.addMenuItems(qupath.getMenu("Extensions", false), 
				MenuTools.createMenu("OMERO", 
                		browseServerMenu,
    	                actionClients,
    	                null,
    	                actionSendObjects
    	                )
				);
		createServerListMenu(qupath, browseServerMenu);
	}

	@Override
	public String getName() {
		return "OMERO extension";
	}

	@Override
	public String getDescription() {
		return "Adds the ability to browse OMERO servers and open images hosted on OMERO servers.";
	}
	
	private static Menu createServerListMenu(QuPathGUI qupath, Menu browseServerMenu) {
		EventHandler<Event> validationHandler = e -> {
			browseServerMenu.getItems().clear();
			
			// Get all active servers
			var activeServers = OmeroWebClients.getAllClients();
			
			// Populate the menu with each unique active servers
			for (var client: activeServers) {
				if (client == null)
					continue;
				MenuItem item = new MenuItem(client.getServerURI() + "...");
				item.setOnAction(e2 -> {
					var browser = browsers.get(client);
					if (browser == null || browser.getStage() == null) {
						browser = new OmeroWebImageServerBrowserCommand(qupath, client);
						browsers.put(client, browser);
						browser.run();
					} else
						browser.getStage().requestFocus();
				});
				browseServerMenu.getItems().add(item);
			}
			
			// Create 'New server...' MenuItem
			MenuItem customServerItem = new MenuItem("New server...");
			customServerItem.setOnAction(e2 -> {
				GridPane gp = new GridPane();
				gp.setVgap(5.0);
		        TextField tf = new TextField();
		        tf.setPrefWidth(400);
		        PaneTools.addGridRow(gp, 0, 0, "Enter OMERO URL", new Label("Enter an OMERO server URL to browse (e.g. http://idr.openmicroscopy.org/):"));
		        PaneTools.addGridRow(gp, 1, 0, "Enter OMERO URL", tf, tf);
		        var confirm = Dialogs.showConfirmDialog("Enter OMERO URL", gp);
		        if (!confirm)
		        	return;
		        
		        var path = tf.getText();
				if (path == null || path.isEmpty())
					return;
				try {
					if (!path.startsWith("http:") && !path.startsWith("https:"))
						throw new IOException("The input URL must contain a scheme (e.g. \"https://\")!");

					// Make the path a URI
					URI uri = new URI(path);
					
					// Clean the URI (in case it's a full path)
					URI uriServer = OmeroTools.getServerURI(uri);
					
					if (uriServer == null)
						throw new MalformedURLException("Could not parse server from " + uri.toString());
					
					// Check if client exist and if browser is already opened
					var client = OmeroWebClients.getClientFromServerURI(uriServer);
					if (client == null)
						client = OmeroWebClients.createClientAndLogin(uriServer);
					
					if (client == null)
						throw new IOException("Could not parse server from " + uri.toString());
					
					var browser = browsers.get(client);
					if (browser == null || browser.getStage() == null) {
						// Create new browser
						browser = new OmeroWebImageServerBrowserCommand(qupath, client);
						browsers.put(client, browser);
						browser.run();
					} else	// Request focus for already-existing browser
						browser.getStage().requestFocus();		
					
				} catch (FileNotFoundException ex) {
					Dialogs.showErrorMessage("OMERO web server", "An error occured when trying to reach " + path);
				} catch (IOException | URISyntaxException ex) {
					Dialogs.showErrorMessage("OMERO web server", ex.getLocalizedMessage());
					return;
				}
			});
			MenuTools.addMenuItems(browseServerMenu, null, customServerItem);
		};
		
		// Ensure the menu is populated (every time the parent menu is opened)
		browseServerMenu.getParentMenu().setOnShowing(validationHandler);	
		return browseServerMenu;
	}
	
	/**
	 * Return map of currently opened browsers.
	 * 
	 * @return browsers
	 */
	static Map<OmeroWebClient, OmeroWebImageServerBrowserCommand> getOpenedBrowsers() {
		return browsers;
	}
}
