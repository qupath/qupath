package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.PathObjectIO;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to export path object(s) as either GeoJSON or Java serialized to an output file.
 * @author Melvin Gelbard
 *
 */
// TODO this could be confusing if someone has a PathClass called "All" (it always selects all classes, not the "All" PathClass)
// TODO make default dir the project one when choosing outFile?
public class PathObjectsExportCommand {
	
	private static final Logger logger = LoggerFactory.getLogger(PathObjectsExportCommand.class);

	/**
	 * Keys for parameterList
	 */
	private static final String KEY_OBJECT_TYPE = "processObjectType";
	private static final String KEY_PATH_CLASS = "processClass";
	
	private static final LinkedHashMap<String, Class<? extends PathObject>> objectsToProcess = new LinkedHashMap<>();

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
		
		// Get all objects
		Collection<PathObject> allObjects = hierarchy.getObjects(null,  null);
		
		// Add all path object types
		objectsToProcess.put("Objects", PathObject.class);
		objectsToProcess.put("Selected objects", PathObject.class);
		objectsToProcess.put(PathObjectTools.getSuitableName(PathAnnotationObject.class, true), PathAnnotationObject.class);
		objectsToProcess.put(PathObjectTools.getSuitableName(PathCellObject.class, true), PathCellObject.class);
		objectsToProcess.put(PathObjectTools.getSuitableName(PathDetectionObject.class, true), PathDetectionObject.class);
		objectsToProcess.put(PathObjectTools.getSuitableName(PathTileObject.class, true), PathTileObject.class);

		List<String> pathObjectList = new ArrayList<>(objectsToProcess.keySet());
		List<String> pathClassList = allObjects.parallelStream()
				.filter(e -> e.getClass() != PathRootObject.class)
				.map(e -> e.getPathClass())
				.distinct()
				.map(e -> e == null || e.getName() == null ? "Unclassified" : e.getName())
				.collect(Collectors.toList());
		pathClassList.add(0, "All");
		
		// Params to prompt
		ParameterList paramsObjectTypes = new ParameterList();
		paramsObjectTypes.addChoiceParameter(KEY_OBJECT_TYPE, "Export all", pathObjectList.get(0), pathObjectList);
		paramsObjectTypes.addChoiceParameter(KEY_PATH_CLASS, "Only class", pathClassList.get(0), pathClassList);
		CheckBox onlyROICheck = new CheckBox("Only export ROI");
		CheckBox compressedCheck = new CheckBox("Compress data");
		CheckBox prettyGsonCheck = new CheckBox("Pretty GeoJSON");
		CheckBox includeMeasurementsCheck = new CheckBox("Include measurements");
		includeMeasurementsCheck.disableProperty().bind(onlyROICheck.selectedProperty());
		includeMeasurementsCheck.setSelected(true);
		
		GridPane grid = new GridPane();
		PaneTools.addGridRow(grid, 0, 0, "Export path objects", new ParameterPanelFX(paramsObjectTypes).getPane());
		PaneTools.addGridRow(grid, 1, 0, null, new Separator());
		PaneTools.addGridRow(grid, 2, 0, "Only export ROI", onlyROICheck);
		PaneTools.addGridRow(grid, 3, 0, "Compressed files take less memory", compressedCheck);
		PaneTools.addGridRow(grid, 4, 0, "Pretty GeoJSON is more human-readable but takes more memory", prettyGsonCheck);
		PaneTools.addGridRow(grid, 5, 0, "Export all measurements along with the objects", includeMeasurementsCheck);
		
		grid.setVgap(5.0);
		if (!Dialogs.showConfirmDialog("Export path objects", grid))
			return false;
		
		String pathObjectChosen = (String)paramsObjectTypes.getChoiceParameterValue(KEY_OBJECT_TYPE);
		String pathClassChosen = (String)paramsObjectTypes.getChoiceParameterValue(KEY_PATH_CLASS);
		
		Collection<PathObject> toProcess;
		if ("Selected objects".equals(pathObjectChosen)) {
			if (hierarchy.getSelectionModel().noSelection()) {
				Dialogs.showErrorMessage("No selection", "No selection detected!");
				return false;
			}
			toProcess = hierarchy.getSelectionModel().getSelectedObjects();
		} else
			toProcess = hierarchy.getObjects(null, objectsToProcess.get(pathObjectChosen));
		
		// Remove PathRootObject
		toProcess = toProcess.parallelStream().filter(e -> e.getClass() != PathRootObject.class).collect(Collectors.toList());

		// Filter by PathClass if needed
		if (!pathClassChosen.equals("All"))
			toProcess = toProcess.parallelStream()
				.filter(e -> { 
					if (pathClassChosen.equals("Unclassified"))
						return e.getPathClass() == null;
					else
						return e.getPathClass() != null && e.getPathClass().equals(PathClassFactory.getPathClass(pathClassChosen));
				})
				.collect(Collectors.toList());

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
		
		// args for workflow step
		Map<String, String> map = new HashMap<>();
		map.put("type", pathObjectChosen);
		map.put("path", outFile.getPath().toString());
		map.put("compress", compressedCheck.isSelected() ? "true" : "false");
		map.put("prettyGson", prettyGsonCheck.isSelected() ? "true" : "false");
		map.put("onlyROI", onlyROICheck.isSelected() ? "true" : "false");
		String methodString = String.format("exportToGeoJSON(%s, %s%s%s, %s, %s, %s, %s)", 
				"objs", 
				"\"", outFile.getPath(), "\"", 
				String.valueOf(onlyROICheck.isSelected()), 
				String.valueOf(includeMeasurementsCheck.isSelected()),
				String.valueOf(prettyGsonCheck.isSelected()),
				String.valueOf(compressedCheck.isSelected()));
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Export path objects",	map, methodString));
		
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
		
		// Get all objects
		Collection<PathObject> allObjects = hierarchy.getObjects(null,  null);
		
		// Add all path object types
		objectsToProcess.put("Objects", PathObject.class);
		objectsToProcess.put("Selected objects", PathObject.class);
		objectsToProcess.put(PathObjectTools.getSuitableName(PathAnnotationObject.class, true), PathAnnotationObject.class);
		objectsToProcess.put(PathObjectTools.getSuitableName(PathCellObject.class, true), PathCellObject.class);
		objectsToProcess.put(PathObjectTools.getSuitableName(PathDetectionObject.class, true), PathDetectionObject.class);
		objectsToProcess.put(PathObjectTools.getSuitableName(PathTileObject.class, true), PathTileObject.class);

		List<String> pathObjectList = new ArrayList<>(objectsToProcess.keySet());
		List<String> pathClassList = allObjects.parallelStream()
				.filter(e -> e.getClass() != PathRootObject.class)
				.map(e -> e.getPathClass())
				.distinct()
				.map(e -> e == null || e.getName() == null ? "Unclassified" : e.getName())
				.collect(Collectors.toList());
		pathClassList.add(0, "All");
		
		// Params to prompt
		ParameterList paramsObjectTypes = new ParameterList();
		paramsObjectTypes.addChoiceParameter(KEY_OBJECT_TYPE, "Export all", pathObjectList.get(0), pathObjectList);
		paramsObjectTypes.addChoiceParameter(KEY_PATH_CLASS, "Only class", pathClassList.get(0), pathClassList);
		Pane paramPane = new ParameterPanelFX(paramsObjectTypes).getPane();
		CheckBox onlyROICheck = new CheckBox("Only export ROI");
		CheckBox compressedCheck = new CheckBox("Compress data");
		CheckBox includeMeasurementsCheck = new CheckBox("Include measurements");
		includeMeasurementsCheck.disableProperty().bind(onlyROICheck.selectedProperty());
		includeMeasurementsCheck.setSelected(true);
		
		GridPane grid = new GridPane();
		PaneTools.addGridRow(grid, 0, 0, "Export path objects", paramPane);
		PaneTools.addGridRow(grid, 1, 0, null, new Separator());
		PaneTools.addGridRow(grid, 2, 0, "Only export ROI", onlyROICheck);
		PaneTools.addGridRow(grid, 3, 0, "Compressed files take less memory", compressedCheck);
		PaneTools.addGridRow(grid, 4, 0, "Export all measurements along with the objects", includeMeasurementsCheck);
		grid.setVgap(5.0);
		if (!Dialogs.showConfirmDialog("Export path objects", grid))
			return false;
		
		String pathObjectChosen = (String)paramsObjectTypes.getChoiceParameterValue(KEY_OBJECT_TYPE);
		String pathClassChosen = (String)paramsObjectTypes.getChoiceParameterValue(KEY_PATH_CLASS);
		
		Collection<PathObject> toProcess;
		if ("Selected objects".equals(pathObjectChosen)) {
			if (hierarchy.getSelectionModel().noSelection()) {
				Dialogs.showErrorMessage("No selection", "No selection detected!");
				return false;
			}
			toProcess = hierarchy.getSelectionModel().getSelectedObjects();
		} else
			toProcess = hierarchy.getObjects(null, objectsToProcess.get(pathObjectChosen));
		
		// Remove PathRootObject
		toProcess = toProcess.parallelStream().filter(e -> e.getClass() != PathRootObject.class).collect(Collectors.toList());

		// Filter by PathClass if needed
		if (!pathClassChosen.equals("All")) {
			toProcess = toProcess.parallelStream()
				.filter(e -> {
					if (pathClassChosen.equals("Unclassified"))
						return e.getPathClass() == null;
					else
						return e.getPathClass() != null && e.getPathClass().equals(PathClassFactory.getPathClass(pathClassChosen));
				})
				.collect(Collectors.toList());
		}
		
		var project = qupath.getProject();
		var outFile = Dialogs.promptToSaveFile("Export to file", project != null && project.getPath() != null ? project.getPath().toFile() : null, "pathObjects.qpdata", "QuPath Serialized Data", ".qpdata");
		
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
		Map<String, String> map = new HashMap<>();
		map.put("type", pathObjectChosen);
		map.put("path", outFile.getPath().toString());
		String methodString = String.format("exportAsSerialized(%s, %s%s%s, %s, %s, %s)",
				"objs", "\"", outFile.getPath(), "\"",
				String.valueOf(onlyROICheck.isSelected()),
				String.valueOf(includeMeasurementsCheck.isSelected()),
				String.valueOf(compressedCheck.isSelected()));
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Export path objects",	map, methodString));	
		return true;
	}
	
	
}
