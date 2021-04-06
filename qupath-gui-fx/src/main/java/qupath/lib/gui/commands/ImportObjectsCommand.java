/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.commands;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to import object(s) from an output file (GeoJSON or Java serialized).
 * 
 * @author Melvin Gelbard
 * @author Pete Bankhead
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
		
		var file = Dialogs.promptForFile("Choose file to import", null, "QuPath objects", PathIO.getObjectFileExtensions().toArray(String[]::new));
		
		// User cancel
		if (file == null)
			return false;
		
		List<PathObject> objs;
		try {
			objs = PathIO.readObjects(file);
		} catch (IOException | IllegalArgumentException ex) {
			Dialogs.showErrorNotification("Error importing objects", ex.getLocalizedMessage());
			return false;
		}
		
		if (objs.isEmpty()) {
			Dialogs.showWarningNotification("Import objects", "No objects found in " + file.getAbsolutePath());
			return false;
		}
		
		int nObjects = objs.stream().mapToInt(p -> 1 + PathObjectTools.countDescendants(p)).sum();
		String message = nObjects == 1 ? "Add 1 object to the hierarchy?" : String.format("Add %d objects to the hierarchy?", nObjects);
		var confirm = Dialogs.showConfirmDialog("Add to hierarchy", message);
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