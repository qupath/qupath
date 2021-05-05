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

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.objects.PathObject;

/**
 * Command to write path objects back to the OMERO server where the 
 * current image is hosted.
 * 
 * @author Melvin Gelbard
 *
 */
public class OmeroWritePathObjectsCommand implements Runnable {
	
	private final String title = "Send objects to OMERO";
	
	private QuPathGUI qupath;
	
	OmeroWritePathObjectsCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var viewer = qupath.getViewer();
		var server = viewer.getServer();
		
		// Check if OMERO server
		if (!(server instanceof OmeroWebImageServer)) {
			Dialogs.showErrorMessage(title, "The current image is not from OMERO!");
			return;
		}
		
		// Check if at least one object was selected (and type)
		var selectedObjects = viewer.getAllSelectedObjects();
		Collection<PathObject> objs;
		if (selectedObjects.size() == 0) {
			// If no selection, get all annotation objects
			objs = viewer.getHierarchy().getAnnotationObjects();
			if (objs.size() == 0) {
				Dialogs.showErrorMessage(title, "No annotations to send!");
				return;
			}
			
			// Ask user if he/she wants to send all annotations instead
			var confirm = Dialogs.showConfirmDialog("Send annotations", String.format("No annotations are selected. Send all annotations instead? (%d %s)", 
					objs.size(),
					(objs.size() == 1 ? "object" : "objects")));
			
			if (!confirm)
				return;
		} else {
			objs = selectedObjects;
			
			// Get detections amongst selection
			var detections = objs.stream().filter(e -> e.isDetection()).collect(Collectors.toList());
			
			// Give warning and filter out detection objects
			if (detections.size() > 0) {
				Dialogs.showWarningNotification(title, String.format("Sending detection objects is not supported (%d %s)", 
						detections.size(),
						(detections.size() == 1 ? "object" : "objects")));
				
				objs = objs.stream().filter(e -> !e.isDetection()).collect(Collectors.toList());				
			}
			
			// Output message if no annotation object was found
			if (objs.size() == 0) {
				Dialogs.showErrorMessage(title, "No annotation objects to send!");
				return;
			}
		}
		
		// Confirm
		var omeroServer = (OmeroWebImageServer) server;
		URI uri = server.getURIs().iterator().next();
		String objectString = "object" + (objs.size() == 1 ? "" : "s");
		GridPane pane = new GridPane();
		PaneTools.addGridRow(pane, 0, 0, null, new Label(String.format("%d %s will be sent to:", objs.size(), objectString)));
		PaneTools.addGridRow(pane, 1, 0, null, new Label(uri.toString()));
		var confirm = Dialogs.showConfirmDialog("Send " + (selectedObjects.size() == 0 ? "all " : "") + objectString, pane);
		if (!confirm)
			return;
		
		// Write path object(s)
		try {
			OmeroTools.writePathObjects(objs, omeroServer);
			Dialogs.showInfoNotification(StringUtils.capitalize(objectString) + " written successfully", String.format("%d %s %s successfully written to OMERO server", 
					objs.size(), 
					objectString, 
					(objs.size() == 1 ? "was" : "were")));
		} catch (IOException ex) {
			Dialogs.showErrorNotification("Could not send " + objectString, ex.getLocalizedMessage());
		}
	}
}
