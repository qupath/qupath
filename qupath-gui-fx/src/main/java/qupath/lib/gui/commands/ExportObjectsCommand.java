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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.io.PathIO.GeoJsonExportOptions;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.scripting.QP;

/**
 * Command to export object(s) in GeoJSON format to an output file.
 * 
 * @author Melvin Gelbard
 * @author Pete Bankhead
 */
// TODO make default dir the project one when choosing outFile?
public final class ExportObjectsCommand {
	
	// Suppress default constructor for non-instantiability
	private ExportObjectsCommand() {
		throw new AssertionError();
	}
	
	/**
	 * Run the path object GeoJSON export command.
	 * @param qupath 
	 * @return success
	 * @throws IOException 
	 */
	public static boolean runGeoJsonExport(QuPathGUI qupath) throws IOException {
		// Get ImageData
		var imageData = qupath.getImageData();
		if (imageData == null)
			return false;
		
		// Get hierarchy
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		String allObjects = "All objects";
		String selectedObjects = "Selected objects";
		String defaultObjects = hierarchy.getSelectionModel().noSelection() ? allObjects : selectedObjects;
		
		// Params
		var parameterList = new ParameterList()
				.addChoiceParameter("exportOptions", "Export ", defaultObjects, Arrays.asList(allObjects, selectedObjects), "Choose which objects to export - run a 'Select annotations/detections' command first if needed")
				.addBooleanParameter("excludeMeasurements", "Exclude measurements", false, "Exclude object measurements during export - for large numbers of detections this can help reduce the file size")
				.addBooleanParameter("doPretty", "Pretty JSON", false, "Pretty GeoJSON is more human-readable but results in larger file sizes")
				.addBooleanParameter("doFeatureCollection", "Export as FeatureCollection", true, "Export as a 'FeatureCollection', which is a standard GeoJSON way to represent multiple objects; if not, a regular JSON object/array will be export")
				.addBooleanParameter("doZip", "Compress data (zip)", false, "Compressed files take less memory");
		
		if (!Dialogs.showParameterDialog("Export objects", parameterList))
			return false;
		
		Collection<PathObject> toProcess;
		var comboChoice = parameterList.getChoiceParameterValue("exportOptions");
		if (comboChoice.equals("Selected objects")) {
			if (hierarchy.getSelectionModel().noSelection()) {
				Dialogs.showErrorMessage("No selection", "No selection detected!");
				return false;
			}
			toProcess = hierarchy.getSelectionModel().getSelectedObjects();
		} else
			toProcess = hierarchy.getObjects(null, null);
		
		// Remove PathRootObject
		toProcess = toProcess.stream().filter(e -> !e.isRootObject()).collect(Collectors.toList());

		// Check if includes ellipse(s), as they will need to be polygonized
		var nEllipses = toProcess.stream().filter(ann -> isEllipse(ann)).count();
		if (nEllipses > 0) {
			String message = nEllipses == 1 ? "1 ellipse will be polygonized, continue?" : String.format("%d ellipses will be polygonized, continue?", nEllipses);
			var response = Dialogs.showYesNoDialog("Ellipse polygonization", message);
			if (!response)
				return false;
		}

		File outFile;
		// Get default name & output directory
		var project = qupath.getProject();
		String defaultName = imageData.getServer().getMetadata().getName();
		if (project != null) {
			var entry = project.getEntry(imageData);
			if (entry != null)
				defaultName = entry.getImageName();
		}
		defaultName = GeneralTools.getNameWithoutExtension(defaultName);
		File defaultDirectory = project == null || project.getPath() == null ? null : project.getPath().toFile();
		while (defaultDirectory != null && !defaultDirectory.isDirectory())
			defaultDirectory = defaultDirectory.getParentFile();
		if (parameterList.getBooleanParameterValue("doZip"))
			outFile = Dialogs.promptToSaveFile("Export to file", defaultDirectory, defaultName + ".zip", "ZIP archive", ".zip");
		else
			outFile = Dialogs.promptToSaveFile("Export to file", defaultDirectory, defaultName + ".geojson", "GeoJSON", ".geojson");
			
		// If user cancels
		if (outFile == null)
			return false;
		
		List<GeoJsonExportOptions> options = new ArrayList<>();
		if (parameterList.getBooleanParameterValue("excludeMeasurements"))
			options.add(GeoJsonExportOptions.EXCLUDE_MEASUREMENTS);
		if (parameterList.getBooleanParameterValue("doPretty"))
			options.add(GeoJsonExportOptions.PRETTY_JSON);
		if (parameterList.getBooleanParameterValue("doFeatureCollection"))
			options.add(GeoJsonExportOptions.FEATURE_COLLECTION);
		
		
		// Export
		QP.exportObjectsToGeoJson(toProcess, 
				outFile.getAbsolutePath(), 
				options.toArray(GeoJsonExportOptions[]::new)
				);
		
		// Notify user of success
		int nObjects = toProcess.size();
		String message = nObjects == 1 ? "1 object was exported to " + outFile.getAbsolutePath() : 
			String.format("%d objects were exported to %s", nObjects, outFile.getAbsolutePath());
		Dialogs.showInfoNotification("Succesful export", message);
		
		// Get history workflow
		var historyWorkflow = imageData.getHistoryWorkflow();
		
		// args for workflow step
		Map<String, String> map = new LinkedHashMap<>();
		map.put("path", outFile.getPath());

		String method = comboChoice.equals(allObjects) ? "exportAllObjectsToGeoJson" : "exportSelectedObjectsToGeoJson";
		String methodTitle = comboChoice.equals(allObjects) ? "Export all objects" : "Export selected objects";
		String optionsString = options.stream().map(o -> "\"" + o.name() + "\"").collect(Collectors.joining(", "));
		map.put("options", optionsString);
		if (!optionsString.isEmpty())
			optionsString = ", " + optionsString;
		String methodString = String.format("%s(%s%s)", 
				method, 
				"\"" + GeneralTools.escapeFilePath(outFile.getPath()) + "\"",
				optionsString);

		historyWorkflow.addStep(new DefaultScriptableWorkflowStep(methodTitle, map, methodString));		
		return true;
	}
	
	/**
	 * Return whether the {@code PathObject} is an ellipse.
	 * 
	 * @param ann
	 * @return isEllipse
	 */
	private static boolean isEllipse(PathObject ann) {
		return ann.getROI() != null && ann.getROI().getRoiName().equals("Ellipse");
	}
	
}