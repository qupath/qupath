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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.io.PathObjectIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to export object(s) in GeoJSON format to an output file.
 * 
 * @author Melvin Gelbard
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
		
		// Create comboBox for choice
		ComboBox<String> combo = new ComboBox<>();
		combo.setItems(FXCollections.observableArrayList("All objects", "Selected objects"));
		
		// Params
		CheckBox compressedCheck = new CheckBox("Compress data");
		CheckBox prettyGsonCheck = new CheckBox("Pretty GeoJSON");
		CheckBox includeMeasurementsCheck = new CheckBox("Include measurements");
		includeMeasurementsCheck.setSelected(true);
		combo.getSelectionModel().selectFirst();
		
		var sep = new Separator();
		GridPane grid = new GridPane();
		int row = 0;
		PaneTools.addGridRow(grid, row++, 0, "Export objects", new Label("Export "), new HBox(5.0), combo);
		PaneTools.addGridRow(grid, row++, 0, null, sep, sep, sep);
		PaneTools.addGridRow(grid, row++, 0, "Export all measurements along with the objects", includeMeasurementsCheck, includeMeasurementsCheck, includeMeasurementsCheck);
		PaneTools.addGridRow(grid, row++, 0, "Pretty GeoJSON is more human-readable but results in larger file sizes", prettyGsonCheck, prettyGsonCheck, prettyGsonCheck);
		PaneTools.addGridRow(grid, row++, 0, "Compressed files take less memory", compressedCheck, compressedCheck, compressedCheck);
		
		grid.setVgap(5.0);
		if (!Dialogs.showConfirmDialog("Export objects", grid))
			return false;
		
		Collection<PathObject> toProcess;
		var comboChoice = combo.getSelectionModel().getSelectedItem();
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
		var project = qupath.getProject();
		if (!compressedCheck.isSelected())
			outFile = Dialogs.promptToSaveFile("Export to file", project != null && project.getPath() != null ? project.getPath().toFile() : null, "objects.geojson", "GeoJSON", ".geojson");
		else
			outFile = Dialogs.promptToSaveFile("Export to file", project != null && project.getPath() != null ? project.getPath().toFile() : null, "objects.zip", "ZIP archive", ".zip");
			
		// If user cancels
		if (outFile == null)
			return false;
		
		// Export
		PathObjectIO.exportObjectsToGeoJson(toProcess, 
				outFile, 
				(includeMeasurementsCheck.isSelected() && !includeMeasurementsCheck.isDisabled()), 
				prettyGsonCheck.isSelected()
				);
		
		// Notify user of success
		int nObjects = toProcess.size();
		String message = nObjects == 1 ? "1 object was sucessfully exported" : String.format("%d objects were successfully exported.", nObjects);
		Dialogs.showInfoNotification("Succesful export", message);
		
		// Get history workflow
		var historyWorkflow = imageData.getHistoryWorkflow();
		
		// args for workflow step
		Map<String, String> map = new HashMap<>();
		map.put("path", outFile.getPath());
		map.put("prettyGson", prettyGsonCheck.isSelected() ? "true" : "false");

		String method = comboChoice.equals("All objects") ? "exportAllObjectsToGeoJson" : "exportSelectedObjectsToGeoJson";
		String methodTitle = comboChoice.equals("All objects") ? "Export all objects" : "Export selected objects";
		map.put("includeMeasurements", includeMeasurementsCheck.isSelected() ? "true" : "false");
		String methodString = String.format("%s(%s%s%s, %s, %s)", 
				method, 
				"\"", GeneralTools.escapeFilePath(outFile.getPath()), "\"",
				String.valueOf(includeMeasurementsCheck.isSelected()),
				String.valueOf(prettyGsonCheck.isSelected()));

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