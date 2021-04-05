package qupath.lib.gui.commands;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectIO;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to import object(s) from an output file (GeoJSON or Java serialized).
 * 
 * @author Melvin Gelbard
 */
public final class ImportObjectsCommand {
	
	// Suppress default constructor for non-instantiability
	private ImportObjectsCommand() {
		throw new AssertionError();
	}
	
	/**
	 * Import objects or ROIs and adds them to the current hierarchy.
	 * 
	 * @param qupath
	 * @return success
	 * @throws IOException 
	 */
	public static boolean runObjectImport(QuPathGUI qupath) throws IOException {
		var imageData = qupath.getImageData();
		
		if (imageData == null)
			return false;
		
		var file = Dialogs.promptForFile("Choose file to import", null, "QuPath objects", PathObjectIO.getCompatibleFileExtensions());
		
		// User cancel
		if (file == null)
			return false;
		
		List<PathObject> objs;
		try {
			objs = PathObjectIO.extractObjectsFromFile(file);
		} catch (ClassNotFoundException | IllegalArgumentException ex) {
			Dialogs.showErrorNotification("Error importing objects", ex.getLocalizedMessage());
			return false;
		}
		
		var confirm = Dialogs.showConfirmDialog("Add to hierarchy", String.format("Add %d object(s) to the hierarchy?", objs.size()));
		if (!confirm)
			return false;
		
		var hierarchy = imageData.getHierarchy();
		hierarchy.addPathObjects(objs);
		
		Map<String, String> map = new HashMap<>();
		map.put("path", file.getPath());
		String method = "Import objects";
		String methodString = String.format("%s(%s%s%s)", "importObjectsFromFile", "\"", GeneralTools.escapeFilePath(file.getPath()), "\"");
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(method, map, methodString));
		
		return true;
	}
}