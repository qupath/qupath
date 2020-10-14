package qupath.lib.images.servers.omero;

import java.io.IOException;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
/**
 * Command to write path objects back to the OMERO server where the 
 * current image is hosted.
 * 
 * @author Melvin Gelbard
 *
 */
public class OmeroWritePathObjectsCommand implements Runnable {
	
	QuPathGUI qupath;
	
	OmeroWritePathObjectsCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var viewer = qupath.getViewer();
		var server = viewer.getServer();
		
		// Check if OMERO server
		if (!(server instanceof OmeroWebImageServer)) {
			Dialogs.showErrorMessage("Not an OMERO server", "The currently opened image is not an OMERO web server.");
			return;
		}
		
		// Check if at least one object was selected
		var selectedObjects = viewer.getAllSelectedObjects();
		if (selectedObjects.isEmpty()) {
			Dialogs.showErrorMessage("No objects", "You must first select objects!");
			return;
		}
		
		// Write path object(s)
		var omeroServer = (OmeroWebImageServer) server;
		try {
			OmeroTools.writePathObjects(selectedObjects, omeroServer);
			Dialogs.showInfoNotification("Object(s) written successfully", "Object(s) were successfully written to OMERO server");
		} catch (IOException ex) {
			Dialogs.showErrorNotification("Could not send path objects", ex.getLocalizedMessage());
		}
	}
}
