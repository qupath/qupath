package qupath.lib.images.servers.omero;

import java.io.IOException;
import java.net.URI;

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
			Dialogs.showErrorMessage("Not an OMERO server", "The currently opened image is not an OMERO web server.");
			return;
		}
		
		// Check if at least one object was selected
		var selectedObjects = viewer.getAllSelectedObjects();
		if (selectedObjects.isEmpty()) {
			Dialogs.showErrorMessage("No objects", "You must first select objects!");
			return;
		}
		
		// Confirm
		var omeroServer = (OmeroWebImageServer) server;
		URI uri = server.getURIs().iterator().next();
		var confirm = Dialogs.showConfirmDialog("Send objects", String.format("%d object(s) will be sent to:%s%s", 
				selectedObjects.size(), 
				System.lineSeparator(), 
				uri));
		
		if (!confirm)
			return;
		
		// Write path object(s)
		try {
			OmeroTools.writePathObjects(selectedObjects, omeroServer);
			Dialogs.showInfoNotification("Objects written successfully", selectedObjects.size() + " object(s) were successfully written to OMERO server");
		} catch (IOException ex) {
			Dialogs.showErrorNotification("Could not send objects", ex.getLocalizedMessage());
		}
	}
}
