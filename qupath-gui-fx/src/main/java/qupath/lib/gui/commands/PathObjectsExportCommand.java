package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
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
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.PathObjectIO;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to export path object(s) as either GeoJSON or Java serialized to an output file.
 * @author Melvin Gelbard
 *
 */
// TODO this could be confusing if someone has a PathClass called "All" (it always selects all classes, not the "All" PathClass)
// TODO make default dir the project one when choosing outFile?
public class PathObjectsExportCommand {

	/**
	 * Run the path object GeoJSON export command.
	 * @param qupath 
	 * @param imageData
	 * @return success
	 * @throws IOException 
	 */
	public static boolean runGeoJSONExport(QuPathGUI qupath, ImageData<BufferedImage> imageData) throws IOException {		
		// Get hierarchy
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Create comboBox for choice
		ComboBox<String> combo = new ComboBox<>();
		combo.setItems(FXCollections.observableArrayList("Objects", "Selected objects"));
		
		// Params
		CheckBox onlyROICheck = new CheckBox("Only export ROI");
		CheckBox compressedCheck = new CheckBox("Compress data");
		CheckBox prettyGsonCheck = new CheckBox("Pretty GeoJSON");
		CheckBox includeMeasurementsCheck = new CheckBox("Include measurements");
		includeMeasurementsCheck.disableProperty().bind(onlyROICheck.selectedProperty());
		includeMeasurementsCheck.setSelected(true);
		combo.getSelectionModel().selectFirst();
		
		GridPane grid = new GridPane();
		PaneTools.addGridRow(grid, 0, 0, "Export path objects", new Label("Export all"), new HBox(5.0), combo);
		PaneTools.addGridRow(grid, 1, 0, null, new Separator(), new Separator(), new Separator());
		PaneTools.addGridRow(grid, 2, 0, "Only export ROI", onlyROICheck, onlyROICheck, onlyROICheck);
		PaneTools.addGridRow(grid, 3, 0, "Compressed files take less memory", compressedCheck, compressedCheck, compressedCheck);
		PaneTools.addGridRow(grid, 4, 0, "Pretty GeoJSON is more human-readable but takes more memory", prettyGsonCheck, prettyGsonCheck, prettyGsonCheck);
		PaneTools.addGridRow(grid, 5, 0, "Export all measurements along with the objects", includeMeasurementsCheck, includeMeasurementsCheck, includeMeasurementsCheck);
		
		grid.setVgap(5.0);
		if (!Dialogs.showConfirmDialog("Export path objects", grid))
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
		toProcess = toProcess.parallelStream().filter(e -> e.getClass() != PathRootObject.class).collect(Collectors.toList());

		// Check if includes ellipse(s), as they will need to be polygonized
		var nEllipses = toProcess.parallelStream().filter(ann -> PathObjectIO.isEllipse(ann)).count();
		if (nEllipses > 0) {
			var response = Dialogs.showYesNoDialog("Ellipse polygonization", String.format("%d ellipse(s) will be polygonized, continue?", nEllipses));
			if (!response)
				return false;
		}

		File outFile;
		var project = qupath.getProject();
		if (!compressedCheck.isSelected())
			outFile = Dialogs.promptToSaveFile("Export to file", project != null && project.getPath() != null ? project.getPath().toFile() : null, "pathObjects.json", "GeoJSON", ".json");
		else
			outFile = Dialogs.promptToSaveFile("Export to file", project != null && project.getPath() != null ? project.getPath().toFile() : null, "pathObjects.zip", "ZIP archive", ".zip");
			
		// If user cancels
		if (outFile == null)
			return false;
		
		// Export
		PathObjectIO.exportToGeoJSON(toProcess, 
				outFile, 
				onlyROICheck.isSelected(), 
				(includeMeasurementsCheck.isSelected() && !includeMeasurementsCheck.isDisabled()), 
				prettyGsonCheck.isSelected(), 
				compressedCheck.isSelected()
				);
		
		// Notify user of success
		Dialogs.showInfoNotification("Succesful export", String.format("%d object(s) were successfully exported.", toProcess.size()));
		
		// Get history workflow
		var historyWorkflow = imageData.getHistoryWorkflow();
		
		// args for workflow step
		String method = comboChoice.equals("Objects") ? "exportAllObjectsToGeoJSON" : "exportSelectedToGeoJSON";
		String methodTitle = comboChoice.equals("Objects") ? "Export all objects" : "Export selected objects";
		Map<String, String> map = new HashMap<>();
		map.put("path", outFile.getPath());
		map.put("onlyROI", onlyROICheck.isSelected() ? "true" : "false");
		map.put("includeMeasurements", includeMeasurementsCheck.isSelected() ? "true" : "false");
		map.put("prettyGson", prettyGsonCheck.isSelected() ? "true" : "false");
		String methodString = String.format("%s(%s%s%s, %s, %s, %s)", 
				method, 
				"\"", GeneralTools.escapeFilePath(outFile.getPath()), "\"", 
				String.valueOf(onlyROICheck.isSelected()), 
				String.valueOf(includeMeasurementsCheck.isSelected()),
				String.valueOf(prettyGsonCheck.isSelected()));
		historyWorkflow.addStep(new DefaultScriptableWorkflowStep(methodTitle, map, methodString));
		
		return true;
	}
	
	
	/**
	 * Run the path object serialized export command.
	 * @param qupath
	 * @param imageData
	 * @return
	 * @throws IOException 
	 */
	public static boolean runSerializedExport(QuPathGUI qupath, ImageData<BufferedImage> imageData) throws IOException {
		// Get hierarchy
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Create comboBox for choice
		ComboBox<String> combo = new ComboBox<>();
		combo.setItems(FXCollections.observableArrayList("Objects", "Selected objects"));
		
		// Params
		CheckBox onlyROICheck = new CheckBox("Only export ROI");
		CheckBox compressedCheck = new CheckBox("Compress data");
		CheckBox includeMeasurementsCheck = new CheckBox("Include measurements");
		includeMeasurementsCheck.disableProperty().bind(onlyROICheck.selectedProperty());
		includeMeasurementsCheck.setSelected(true);
		combo.getSelectionModel().selectFirst();
		
		GridPane grid = new GridPane();
		PaneTools.addGridRow(grid, 0, 0, "Export path objects", new Label("Export all"), new HBox(5.0), combo);
		PaneTools.addGridRow(grid, 1, 0, null, new Separator(), new Separator(), new Separator());
		PaneTools.addGridRow(grid, 2, 0, "Only export ROI", onlyROICheck, onlyROICheck, onlyROICheck);
		PaneTools.addGridRow(grid, 3, 0, "Compressed files take less memory", compressedCheck, compressedCheck, compressedCheck);
		PaneTools.addGridRow(grid, 5, 0, "Export all measurements along with the objects", includeMeasurementsCheck, includeMeasurementsCheck, includeMeasurementsCheck);
		grid.setVgap(5.0);
		if (!Dialogs.showConfirmDialog("Export path objects", grid))
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
		toProcess = toProcess.parallelStream().filter(e -> e.getClass() != PathRootObject.class).collect(Collectors.toList());
		
		var project = qupath.getProject();
		File outFile;
		if (!compressedCheck.isSelected())
			outFile = Dialogs.promptToSaveFile("Export to file", project != null && project.getPath() != null ? project.getPath().toFile() : null, "pathObjects.qpdata", "QuPath Serialized Data", ".qpdata");
		else
			outFile = Dialogs.promptToSaveFile("Export to file", project != null && project.getPath() != null ? project.getPath().toFile() : null, "pathObjects.zip", "ZIP archive", ".zip");
		
		// If user cancels
		if (outFile == null)
			return false;
		
		// Export
		PathObjectIO.exportAsSerialized(toProcess, 
				outFile, 
				onlyROICheck.isSelected(), 
				(includeMeasurementsCheck.isSelected() && !includeMeasurementsCheck.isDisabled()), 
				compressedCheck.isSelected()
				);
		
		// Notify user of success
		Dialogs.showInfoNotification("Succesful export", String.format("%d object(s) were successfully exported.", toProcess.size()));
		
		// args for workflow step
		String method = comboChoice.equals("Objects") ? "exportAllObjectsAsSerialized" : "exportSelectedAsSerialized";
		String methodTitle = comboChoice.equals("Objects") ? "Export all objects (serialized)" : "Export selected objects (serialized)";
		Map<String, String> map = new HashMap<>();
		map.put("path", outFile.getPath().toString());
		map.put("onlyROI", onlyROICheck.isSelected() ? "true" : "false");
		map.put("includeMeasurements", outFile.getPath().toString());
		String methodString = String.format("%s(%s%s%s, %s, %s)",
				method, 
				"\"", GeneralTools.escapeFilePath(outFile.getPath()), "\"",
				String.valueOf(onlyROICheck.isSelected()),
				String.valueOf(includeMeasurementsCheck.isSelected()));
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(methodTitle, map, methodString));
		return true;
	}
}
