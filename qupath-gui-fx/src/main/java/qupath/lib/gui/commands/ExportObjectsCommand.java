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
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.PathObjectIO;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to export object(s) as either GeoJSON or Java serialized to an output file.
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
		CheckBox onlyROICheck = new CheckBox("Only export ROI");
		CheckBox compressedCheck = new CheckBox("Compress data");
		CheckBox prettyGsonCheck = new CheckBox("Pretty GeoJSON");
		CheckBox includeMeasurementsCheck = new CheckBox("Include measurements");
		includeMeasurementsCheck.disableProperty().bind(onlyROICheck.selectedProperty());
		includeMeasurementsCheck.setSelected(true);
		combo.getSelectionModel().selectFirst();
		
		var sep = new Separator();
		GridPane grid = new GridPane();
		PaneTools.addGridRow(grid, 0, 0, "Export objects", new Label("Export "), new HBox(5.0), combo);
		PaneTools.addGridRow(grid, 1, 0, null, sep, sep, sep);
		PaneTools.addGridRow(grid, 2, 0, "Only export ROI", onlyROICheck, onlyROICheck, onlyROICheck);
		PaneTools.addGridRow(grid, 3, 0, "Export all measurements along with the objects", includeMeasurementsCheck, includeMeasurementsCheck, includeMeasurementsCheck);
		PaneTools.addGridRow(grid, 4, 0, "Pretty GeoJSON is more human-readable but results in larger file sizes", prettyGsonCheck, prettyGsonCheck, prettyGsonCheck);
		PaneTools.addGridRow(grid, 5, 0, "Compressed files take less memory", compressedCheck, compressedCheck, compressedCheck);
		
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
		toProcess = toProcess.stream().filter(e -> e.getClass() != PathRootObject.class).collect(Collectors.toList());

		// Check if includes ellipse(s), as they will need to be polygonized
		var nEllipses = toProcess.stream().filter(ann -> PathObjectIO.isEllipse(ann)).count();
		if (nEllipses > 0) {
			var response = Dialogs.showYesNoDialog("Ellipse polygonization", String.format("%d ellipse(s) will be polygonized, continue?", nEllipses));
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
		if (onlyROICheck.isSelected()) {
			var objs = toProcess.parallelStream().map(e -> e.getROI()).collect(Collectors.toList());
			PathObjectIO.exportROIsToGeoJson(objs, 
					outFile, 
					prettyGsonCheck.isSelected()
					);
		} else {
			PathObjectIO.exportObjectsToGeoJson(toProcess, 
					outFile, 
					(includeMeasurementsCheck.isSelected() && !includeMeasurementsCheck.isDisabled()), 
					prettyGsonCheck.isSelected()
					);
		}
		
		// Notify user of success
		Dialogs.showInfoNotification("Succesful export", String.format("%d object(s) were successfully exported.", toProcess.size()));
		
		// Get history workflow
		var historyWorkflow = imageData.getHistoryWorkflow();
		
		// args for workflow step
		Map<String, String> map = new HashMap<>();
		map.put("path", outFile.getPath());
		map.put("prettyGson", prettyGsonCheck.isSelected() ? "true" : "false");

		String method;
		String methodTitle;
		String methodString;
		if (onlyROICheck.isSelected()) {
			method = comboChoice.equals("All objects") ? "exportAllROIsToGeoJson" : "exportSelectedROIsToGeoJson";
			methodTitle = comboChoice.equals("All objects") ? "Export all ROIs" : "Export selected ROIs";
			methodString = String.format("%s(%s%s%s, %s)", 
					method, 
					"\"", GeneralTools.escapeFilePath(outFile.getPath()), "\"", 
					String.valueOf(prettyGsonCheck.isSelected()));
		} else {
			method = comboChoice.equals("All objects") ? "exportAllObjectsToGeoJson" : "exportSelectedObjectsToGeoJson";
			methodTitle = comboChoice.equals("All objects") ? "Export all objects" : "Export selected objects";
			map.put("includeMeasurements", includeMeasurementsCheck.isSelected() ? "true" : "false");
			methodString = String.format("%s(%s%s%s, %s, %s)", 
					method, 
					"\"", GeneralTools.escapeFilePath(outFile.getPath()), "\"",
					String.valueOf(includeMeasurementsCheck.isSelected()),
					String.valueOf(prettyGsonCheck.isSelected()));
		}

		historyWorkflow.addStep(new DefaultScriptableWorkflowStep(methodTitle, map, methodString));		
		return true;
	}
	
	
	/**
	 * Run the path object serialized export command.
	 * @param qupath
	 * @return success
	 * @throws IOException 
	 */
	public static boolean runSerializedExport(QuPathGUI qupath) throws IOException {
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
		CheckBox onlyROICheck = new CheckBox("Only export ROI");
		CheckBox compressedCheck = new CheckBox("Compress data");
		CheckBox includeMeasurementsCheck = new CheckBox("Include measurements");
		includeMeasurementsCheck.disableProperty().bind(onlyROICheck.selectedProperty());
		includeMeasurementsCheck.setSelected(true);
		combo.getSelectionModel().selectFirst();
		
		var sep = new Separator();
		GridPane grid = new GridPane();
		PaneTools.addGridRow(grid, 0, 0, "Export objects", new Label("Export "), new HBox(5.0), combo);
		PaneTools.addGridRow(grid, 1, 0, null, sep, sep, sep);
		PaneTools.addGridRow(grid, 2, 0, "Only export ROI", onlyROICheck, onlyROICheck, onlyROICheck);
		PaneTools.addGridRow(grid, 3, 0, "Export all measurements along with the objects", includeMeasurementsCheck, includeMeasurementsCheck, includeMeasurementsCheck);
		PaneTools.addGridRow(grid, 4, 0, "Compressed files take less memory", compressedCheck, compressedCheck, compressedCheck);
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
		toProcess = toProcess.stream().filter(e -> e.getClass() != PathRootObject.class).collect(Collectors.toList());
		
		var project = qupath.getProject();
		File outFile;
		if (!compressedCheck.isSelected())
			outFile = Dialogs.promptToSaveFile("Export to file", project != null && project.getPath() != null ? project.getPath().toFile() : null, "objects.qpdata", "QuPath Serialized Data", ".qpdata");
		else
			outFile = Dialogs.promptToSaveFile("Export to file", project != null && project.getPath() != null ? project.getPath().toFile() : null, "objects.zip", "ZIP archive", ".zip");
		
		// If user cancels
		if (outFile == null)
			return false;
		
		// Export
		if (onlyROICheck.isSelected()) {
			var objs = toProcess.parallelStream().map(e -> e.getROI()).collect(Collectors.toList());
			PathObjectIO.exportROIsAsSerialized(objs, outFile);
		} else
			PathObjectIO.exportObjectsAsSerialized(toProcess, outFile, (includeMeasurementsCheck.isSelected() && !includeMeasurementsCheck.isDisabled()));
		
		// Notify user of success
		Dialogs.showInfoNotification("Succesful export", String.format("%d object(s) were successfully exported.", toProcess.size()));
		
		// args for workflow step
		Map<String, String> map = new HashMap<>();
		map.put("path", outFile.getPath().toString());

		String method;
		String methodTitle;
		String methodString;
		if (onlyROICheck.isSelected()) {
			method = comboChoice.equals("All objects") ? "exportAllROIsAsSerialized" : "exportSelectedROIsAsSerialized";
			methodTitle = comboChoice.equals("All objects") ? "Export all ROIs (serialized)" : "Export selected ROIs (serialized)";
			methodString = String.format("%s(%s%s%s)", method, "\"", GeneralTools.escapeFilePath(outFile.getPath()), "\"");
		} else {
			map.put("includeMeasurements", outFile.getPath().toString());
			method = comboChoice.equals("All objects") ? "exportAllObjectsAsSerialized" : "exportSelectedObjectsAsSerialized";
			methodTitle = comboChoice.equals("All objects") ? "Export all objects (serialized)" : "Export selected objects (serialized)";	
			methodString = String.format("%s(%s%s%s, %s)",
					method, 
					"\"", GeneralTools.escapeFilePath(outFile.getPath()), "\"",
					String.valueOf(includeMeasurementsCheck.isSelected()));			
		}
		
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(methodTitle, map, methodString));
		return true;
	}
}
