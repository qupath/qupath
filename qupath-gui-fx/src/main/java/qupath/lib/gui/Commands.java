package qupath.lib.gui;

import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.stage.Screen;
import javafx.stage.Stage;
import qupath.lib.analysis.DistanceTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panels.PathClassPane;
import qupath.lib.gui.panels.WorkflowCommandLogView;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tma.TMASummaryViewer;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.GridLines;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriter;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.Projects;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.RoiTools.CombineOp;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

/**
 * Helper class implementing simple 'single-method' commands for easy inclusion in the GUI.
 * 
 * @author Pete Bankhead
 *
 */
public class Commands {
	
	private static Logger logger = LoggerFactory.getLogger(Commands.class);
	
	/**
	 * Insert the selected objects in the hierarchy, resolving positions accordingly.
	 * <p>
	 * This causes smaller 'completely-contained' annotations to be positioned below larger containing annotations, 
	 * and detections to be assigned to other annotations based on centroid location.
	 * @param hierarchy the hierarchy to process
	 */
	public static void insertSelectedObjectsInHierarchy(PathObjectHierarchy hierarchy) {
		if (hierarchy == null)
			return;
		hierarchy.insertPathObjects(hierarchy.getSelectionModel().getSelectedObjects());
	}
	
	/**
	 * Resolve parent-child relationships within the object hierarchy.
	 * This means that objects will be arranged hierarchically, rather than as a flat list.
	 * @param imageData the image data to process
	 */
	public static void promptToResolveHierarchy(ImageData<?> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Resolve hierarchy");
			return;
		}
		var hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null)
			return;
		
		if (!Dialogs.showConfirmDialog("Resolve hierarchy",
				"Are you sure you want to resolve object relationships?\n" +
				"For large object hierarchies this can take a long time.")) {
			return;
		}
		hierarchy.resolveHierarchy();
		
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				"Resolve hierarchy",
				"resolveHierarchy()"));
	}
	
	
	/**
	 * Reset TMA metadata, if available.
	 * @param imageData
	 * @return true if changes were made, false otherwise
	 */
	public static boolean resetTMAMetadata(ImageData<?> imageData) {
		if (imageData == null || imageData.getHierarchy().getTMAGrid() == null) {
			logger.warn("No TMA grid available!");
			return false;
		}
		QP.resetTMAMetadata(imageData.getHierarchy(), true);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Reset TMA metadata", "resetTMAMetadata(true);"));		
		return true;
	}
	
	
	/**
	 * Prompt to save the specified {@link ImageData}.
	 * @param qupath
	 * @param imageData
	 * @param overwriteExisting
	 * @return
	 */
	public static boolean promptToSaveImageData(QuPathGUI qupath, ImageData<BufferedImage> imageData, boolean overwriteExisting) {
		if (imageData == null) {
			Dialogs.showNoImageError("Serialization error");
			return false;
		}
		try {
			var project = qupath.getProject();
			var entry = project == null ? null : project.getEntry(imageData);
			if (entry != null) {
				if (overwriteExisting || Dialogs.showConfirmDialog("Save changes", "Save changes to " + entry.getImageName() + "?")) {
					entry.saveImageData(imageData);
					return true;
				} else
					return false;
			} else {
				String lastSavedPath = imageData.getLastSavedPath();
				File file = null;
				if (lastSavedPath != null) {
					// Use the last path, if required
					if (overwriteExisting)
						file = new File(lastSavedPath);
					if (file == null || !file.isFile()) {
						File fileDefault = new File(lastSavedPath);
						file = qupath.getDialogHelper().promptToSaveFile(null, fileDefault.getParentFile(), fileDefault.getName(), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
					}
				}
				else {
					ImageServer<?> server = imageData.getServer();
					String name = ServerTools.getDisplayableImageName(server);
					if (name.contains(".")) {
						try {
							name = GeneralTools.getNameWithoutExtension(new File(name));
						} catch (Exception e) {}
					}
					file = qupath.getDialogHelper().promptToSaveFile(null, null, name, "QuPath Serialized Data", PathPrefs.getSerializationExtension());
				}
				if (file == null)
					return false;
				PathIO.writeImageData(file, imageData);
				return true;
			}
		} catch (IOException e) {
			Dialogs.showErrorMessage("Save ImageData", e);
			return false;
		}
	}
	
	/**
	 * Save an image snapshot, prompting the user to select the output file.
	 * @param qupath the {@link QuPathGUI} instance to snapshot
	 * @param type the snapshot type
	 * @return true if a snapshot was saved, false otherwise
	 */
	public static boolean saveSnapshot(QuPathGUI qupath, GuiTools.SnapshotType type) {
		BufferedImage img = GuiTools.makeSnapshot(qupath, type);			
		
		String ext = PathPrefs.getDefaultScreenshotExtension();
		List<ImageWriter<BufferedImage>> compatibleWriters = ImageWriterTools.getCompatibleWriters(BufferedImage.class, ext);
		if (compatibleWriters.isEmpty()) {
			logger.error("No compatible image writers found for extension: " + ext);
			return false;
		}
		
		File fileOutput = qupath.getDialogHelper().promptToSaveFile(null, null, null, ext, ext);
		if (fileOutput == null)
			return false;
		
		// Loop through the writers and stop when we are successful
		for (var writer : compatibleWriters) {
			try {
				writer.writeImage(img, fileOutput.getAbsolutePath());
				return true;
			} catch (Exception e) {
				logger.error("Error saving snapshot " + type + " to " + fileOutput.getAbsolutePath(), e);
			}
		}
		return false;
	}
	
//	/**
//	 * Merge the points ROIs of different objects to create a single object containing all points with a specific {@link PathClass}.
//	 * @param imageData the image data containing points to merge
//	 * @param selectedOnly if true, use only classes found within the currently selected objects
//	 */
//	public static void mergePointsForClasses(ImageData<?> imageData, boolean selectedOnly) {
//		var hierarchy = imageData == null ? null : imageData.getHierarchy();
//		if (hierarchy == null) {
//			Dialogs.showNoImageError("Merge points");
//			return;
//		}
//		if (selectedOnly) {
//			PathObjectTools.mergePointsForSelectedObjectClasses(hierarchy);
//			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
//					"Merge points for selected classifications",
//					"mergePointsForSelectedObjectClasses();"
//					));
//		} else {
//			PathObjectTools.mergePointsForAllClasses(hierarchy);
//			imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
//					"Merge points for all classifications",
//					"mergePointsForAllClasses();"
//					));
//		}
//	}
	
	/**
	 * Merge the currently-selected annotations for an image, replacing them with a single new annotation.
	 * @param imageData
	 */
	public static void mergeSelectedAnnotations(ImageData<?> imageData) {
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		logger.debug("Merging selected annotations");
		QP.mergeSelectedAnnotations(hierarchy);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Merge selected annotations",
				"mergeSelectedAnnotations()"));
	}

	
	/**
	 * Duplicate the selected annotations.
	 * @param imageData
	 */
	public static void duplicateSelectedAnnotations(ImageData<?> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Duplicate annotations");
			return;
		}
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		PathObjectTools.duplicateSelectedAnnotations(hierarchy);
		imageData.getHistoryWorkflow().addStep(
				new DefaultScriptableWorkflowStep("Duplicate selected annotations",
						"duplicateSelectedAnnotations()"));
	}


	public static void makeInverseAnnotation(ImageData<?> imageData) {
		if (imageData == null)
			return;
		logger.debug("Make inverse annotation");
		QP.makeInverseAnnotation(imageData);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Invert selected annotation",
				"makeInverseAnnotation()"));
	}
	
	
	
	
//	/**
//	 * Combine the selected annotations for the image open in the specified viewer.
//	 * @param viewer viewer containing the image data
//	 * @param op the {@link CombineOp} operation to apply
//	 * @return true if changes were made, false otherwise
//	 */
//	public static boolean combineSelectedAnnotations(QuPathViewer viewer, RoiTools.CombineOp op) {
//		var hierarchy = viewer == null ? null : viewer.getImageData();
//		return combineSelectedAnnotations(hierarchy, op);
//	}
	
	/**
	 * Combine the selected annotations for the specified hierarchy.
	 * @param imageData the image data to process
	 * @param op the {@link CombineOp} operation to apply
	 * @return true if changes were made, false otherwise
	 */
	public static boolean combineSelectedAnnotations(ImageData<?> imageData, RoiTools.CombineOp op) {
		// TODO: CONSIDER MAKING THIS SCRIPTABLE!
		if (imageData == null) {
			Dialogs.showNoImageError("Combine annotations");
			return false;
		}
		var hierarchy = imageData.getHierarchy();
		// Ensure the main selected object is first in the list, if possible
		var selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		var mainObject = hierarchy.getSelectionModel().getSelectedObject();
		if (mainObject != null && !selected.isEmpty() && !selected.get(0).equals(mainObject)) {
			selected.remove(mainObject);
			selected.add(0, mainObject);
		}
		return combineAnnotations(hierarchy, selected, op);
	}
	
	
	/**
	 * Combine all the annotations that overlap with a selected object.
	 * <p>
	 * The selected object should itself be an annotation.
	 * 
	 * @param hierarchy
	 * @param pathObjects
	 * @param op
	 * @return true if any changes were made, false otherwise
	 */
	static boolean combineAnnotations(PathObjectHierarchy hierarchy, List<PathObject> pathObjects, RoiTools.CombineOp op) {
		if (hierarchy == null || hierarchy.isEmpty() || pathObjects.isEmpty()) {
			logger.warn("Combine annotations: Cannot combine - no annotations found");
			return false;
		}
		
		pathObjects = new ArrayList<>(pathObjects);
		PathObject pathObject = pathObjects.get(0);
		if (!pathObject.isAnnotation()) { // || !RoiTools.isShapeROI(pathObject.getROI())) {
			logger.warn("Combine annotations: No annotation with ROI selected");				
			return false;
		}
		var plane = pathObject.getROI().getImagePlane();
//		pathObjects.removeIf(p -> !RoiTools.isShapeROI(p.getROI())); // Remove any null or point ROIs, TODO: Consider supporting points
		pathObjects.removeIf(p -> !p.hasROI() || !p.getROI().getImagePlane().equals(plane)); // Remove any null or point ROIs, TODO: Consider supporting points
		if (pathObjects.isEmpty()) {
			logger.warn("Cannot combint annotations - only one suitable annotation found");
			return false;
		}
		
		var allROIs = pathObjects.stream().map(p -> p.getROI()).collect(Collectors.toCollection(() -> new ArrayList<>()));
		ROI newROI;
		
		switch (op) {
		case ADD:
			newROI = RoiTools.union(allROIs);
			break;
		case INTERSECT:
			newROI = RoiTools.intersection(allROIs);
			break;
		case SUBTRACT:
			var first = allROIs.remove(0);
			newROI = RoiTools.combineROIs(first, RoiTools.union(allROIs), op);
			break;
		default:
			throw new IllegalArgumentException("Unknown combine op " + op);
		}
	
		if (newROI == null) {
			logger.debug("No changes were made");
			return false;
		}
		
		PathObject newObject = null;
		if (!newROI.isEmpty()) {
			newObject = PathObjects.createAnnotationObject(newROI, pathObject.getPathClass());
			newObject.setName(pathObject.getName());
			newObject.setColorRGB(pathObject.getColorRGB());
		}

		// Remove previous objects
		hierarchy.removeObjects(pathObjects, true);
		if (newObject != null)
			hierarchy.addPathObject(newObject);
		return true;
	}
	
	/**
	 * Prompt to select objects according to their classifications.
	 * @param qupath
	 * @param imageData
	 */
	public static void promptToSelectObjectsByClassification(QuPathGUI qupath, ImageData<?> imageData) {
		if (imageData == null)
			return;
		var pathClass = Dialogs.showChoiceDialog("Select objects", "", qupath.getAvailablePathClasses(), null);
		if (pathClass == null)
			return;
		PathClassPane.selectObjectsByClassification(imageData, pathClass);
	}

	
	/**
	 * Prompt to delete objects of a specified type, or all objects.
	 * @param imageData
	 * @param cls
	 */
	public static void promptToDeleteObjects(ImageData<?> imageData, Class<? extends PathObject> cls) {
		if (imageData == null)
			return;
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Handle no specified class - indicates all objects of all types should be cleared
		if (cls == null) {
			int n = hierarchy.nObjects();
			if (n == 0)
				return;
			String message;
			if (n == 1)
				message = "Delete object?";
			else
				message = "Delete all " + n + " objects?";
			if (Dialogs.showYesNoDialog("Delete objects", message)) {
				hierarchy.clearAll();
				hierarchy.getSelectionModel().setSelectedObject(null);
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear all objects", "clearAllObjects();"));
			}
			return;
		}
		
		// Handle clearing TMA grid
		if (TMACoreObject.class.equals(cls)) {
			if (hierarchy.getTMAGrid() != null) {
				if (Dialogs.showYesNoDialog("Delete objects", "Clear TMA grid?")) {
					hierarchy.clearAll();
					
					PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
					if (selected instanceof TMACoreObject)
						hierarchy.getSelectionModel().setSelectedObject(null);

					imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear TMA Grid", "clearTMAGrid();"));
				}
				return;
			}
		}
		
		
		// Handle clearing objects of another specified type
		Collection<PathObject> pathObjects = hierarchy.getObjects(null, cls);
		if (pathObjects.isEmpty())
			return;
		int n = pathObjects.size();
		String message = n == 1 ? "Delete 1 object?" : "Delete " + n + " objects?";
		if (Dialogs.showYesNoDialog("Delete objects", message)) {
			hierarchy.removeObjects(pathObjects, true);
			
			PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
			if (selected != null && selected.getClass().isAssignableFrom(cls))
				hierarchy.getSelectionModel().setSelectedObject(null);
			
			if (selected != null && selected.getClass().isAssignableFrom(cls))
				hierarchy.getSelectionModel().setSelectedObject(null);
			
			if (cls == PathDetectionObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear detections", "clearDetections();"));
			else if (cls == PathAnnotationObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear annotations", "clearAnnotations();"));
			else if (cls == TMACoreObject.class)
				imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear TMA grid", "clearTMAGrid();"));
			else
				logger.warn("Cannot clear all objects for class {}", cls);
		}
	}
	
	
	/**
	 * Reset QuPath's preferences, after confirming with the user.
	 * QuPath needs to be restarted for this to take effect.
	 * @return true if the preferences were reset, false otherwise
	 */
	public static boolean promptToResetPreferences() {
		if (Dialogs.showConfirmDialog("Reset Preferences", "Do you want to reset all custom preferences?\n\nYou may have to restart QuPath to see all changes.")) {
			PathPrefs.resetPreferences();
			return true;
		}
		else
			logger.info("Reset preferences command skipped!");
		return false;
	}

	
	
	/**
	 * Set the downsample factor for the specified viewer.
	 * @param viewer
	 * @param downsample
	 */
	public static void setViewerDownsample(QuPathViewer viewer, double downsample) {
		if (viewer != null)
			viewer.setDownsampleFactor(downsample);
	}
	
	
	/**
	 * Close the current project open in the {@link QuPathGUI}.
	 * @param qupath
	 */
	public static void closeProject(QuPathGUI qupath) {
		qupath.setProject(null);
	}
	
	
	/**
	 * Prompt the user to select an empty directory, and use this to create a new project and set it as active.
	 * @param qupath the {@link QuPathGUI} instance for which the project should be created.
	 * @return true if a project was created, false otherwise (e.g. the user cancelled).
	 */
	public static boolean promptToCreateProject(QuPathGUI qupath) {
		File dir = qupath.getDialogHelper().promptForDirectory(null);
		if (dir == null)
			return false;
		if (!dir.isDirectory()) {
			logger.error(dir + " is not a valid project directory!");
		}
		for (File f : dir.listFiles()) {
			if (!f.isHidden()) {
				logger.error("Cannot create project for non-empty directory {}", dir);
				Dialogs.showErrorMessage("Project creator", "Project directory must be empty!");
				return false;
			}
		}
		qupath.setProject(Projects.createProject(dir, BufferedImage.class));
		return true;
	}
	
	
	/**
	 * Prompt the user to open an existing project and set it as active.
	 * @param qupath the {@link QuPathGUI} instance for which the project should be opened.
	 * @return true if a project was opened, false otherwise (e.g. the user cancelled).
	 */

	public static boolean promptToOpenProject(QuPathGUI qupath) {
		File fileProject = qupath.getDialogHelper().promptForFile("Choose project file", null, "QuPath projects", new String[]{ProjectIO.getProjectExtension()});
		if (fileProject != null) {
			try {
				Project<BufferedImage> project = ProjectIO.loadProject(fileProject, BufferedImage.class);
				qupath.setProject(project);
				return true;
			} catch (Exception e) {
				Dialogs.showErrorMessage("Load project", "Could not read project from " + fileProject.getName());
			}
		}
		return false;
	}
	
	
	
	/**
	 * Open new window with the TMA data viewer.
	 * @param qupath current {@link QuPathGUI} instance (may be null).
	 */
	public static void launchTMADataViewer(QuPathGUI qupath) {
		Stage stage = new Stage();
		if (qupath != null)
			stage.initOwner(qupath.getStage());
		TMASummaryViewer tmaViewer = new TMASummaryViewer(stage);
		
		ImageData<BufferedImage> imageData = qupath.getImageData();
		if (imageData != null && imageData.getHierarchy().getTMAGrid() != null)
			tmaViewer.setTMAEntriesFromImageData(imageData);
		
		try {
			Screen screen = Screen.getPrimary();
			stage.setWidth(screen.getBounds().getWidth()*0.75);
			stage.setHeight(screen.getBounds().getHeight()*0.75);
		} catch (Exception e) {
			logger.error("Exception setting stage size", e);
		}
		
		stage.show();
	}
	
	/**
	 * Compute the distance between all detections and the closest annotation, for all annotation classifications.
	 * @param imageData the image data to process
	 */
	public static void distanceToAnnotations2D(ImageData<?> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Distance to annotations");
			return;
		}
		
		if (imageData.getServer().nZSlices() > 1) {
			logger.debug("Warning user that measurements will be 2D...");
			if (!Dialogs.showConfirmDialog("Distance to annotations 2D", 
					"Distance to annotations command works only in 2D - distances will not be calculated for objects on different z-slices or time-points")) {
				logger.debug("Command cancelled");
				return;
			}
		}
		
		DistanceTools.detectionToAnnotationDistances(imageData);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				"Distance to annotations 2D",
				"detectionToAnnotationDistances()"));
	}
	
	/**
	 * Compute the distance between the centroids of all detections, for all available classifications.
	 * @param imageData the image data to process
	 */
	public static void detectionCentroidDistances2D(ImageData<?> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Detection centroid distances");
			return;
		}
		
		if (imageData.getServer().nZSlices() > 1) {
			logger.debug("Warning user that measurements will be 2D...");
			if (!Dialogs.showConfirmDialog("Detection centroid distances 2D", 
					"Detection centroid distances command works only in 2D - distances will not be calculated for objects on different z-slices or time-points")) {
				logger.debug("Command cancelled");
				return;
			}
		}
		
		DistanceTools.detectionCentroidDistances(imageData);
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep(
				"Detection centroid distances 2D",
				"detectionCentroidDistances()"));
	}
	
	
	/**
	 * Prompt to input the spacing for the grid lines optionally displayed on viewers.
	 * @param options the {@link OverlayOptions} that manage the grid lines.
	 */
	public static void promptToSetGridLineSpacing(OverlayOptions options) {
		GridLines gridLines = options.getGridLines();
		
		ParameterList params = new ParameterList()
				.addDoubleParameter("hSpacing", "Horizontal spacing", gridLines.getSpaceX())
				.addDoubleParameter("vSpacing", "Vertical spacing", gridLines.getSpaceY())
				.addBooleanParameter("useMicrons", "Use microns", gridLines.useMicrons());
		
		if (!Dialogs.showParameterDialog("Set grid spacing", params))
			return;
		
		gridLines = new GridLines();
		gridLines.setSpaceX(params.getDoubleParameterValue("hSpacing"));
		gridLines.setSpaceY(params.getDoubleParameterValue("vSpacing"));
		gridLines.setUseMicrons(params.getBooleanParameterValue("useMicrons"));
		
		options.gridLinesProperty().set(gridLines);
	}
	
	
	/**
	 * Reload the specified image data from a previously saved version,if available.
	 * @param qupath
	 * @param imageData
	 */
	public static void reloadImageData(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Reload data");
			return;
		}
		// TODO: Support loading from a project as well
		
		var viewer = qupath.getViewers().stream().filter(v -> v.getImageData() == imageData).findFirst().orElse(null);
		if (viewer == null) {
			Dialogs.showErrorMessage("Reload data", "Specified image data not found open in any viewer!");
			return;
		}

		// Check if we have a saved file
		File savedFile = imageData.getLastSavedPath() == null ? null : new File(imageData.getLastSavedPath());
		if (savedFile == null || !savedFile.isFile()) {
			Dialogs.showErrorMessage("Reload", "No previously saved data found!");
			return;
		}
		
		if (Dialogs.showConfirmDialog("Reload", "Revert to last saved version?  All changes will be lost.")) {
			try {
				logger.info("Reverting to last saved version: {}", savedFile.getAbsolutePath());
				ImageData<BufferedImage> imageDataNew = PathIO.readImageData(savedFile, null, imageData.getServer(), BufferedImage.class);
				viewer.setImageData(imageDataNew);
			} catch (Exception e) {
				Dialogs.showErrorMessage("Reload", "Error reverting to previously saved file\n\n" + e.getLocalizedMessage());
			}
		}

	}

	
	/**
	 * Convert detection objects to point annotations based upon their ROI centroids.
	 * @param imageData the image data to process
	 * @param preferNucleus if true, use a nucleus ROI for cell objects (if available
	 */
	public static void convertDetectionsToPoints(ImageData<?> imageData, boolean preferNucleus) {
		if (imageData == null) {
			Dialogs.showNoImageError("Convert detections to points");
			return;
		}
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Collection<PathObject> pathObjects = hierarchy.getDetectionObjects();
		if (pathObjects.isEmpty()) {
			Dialogs.showErrorMessage("Detections to points", "No detections found!");
			return;
		}
		
		// Remove any detections that don't have a ROI - can't do much with them
		Iterator<PathObject> iter = pathObjects.iterator();
		while (iter.hasNext()) {
			if (!iter.next().hasROI())
				iter.remove();
		}
		
		if (pathObjects.isEmpty()) {
			logger.warn("No detections found with ROIs!");
			return;
		}
		
		// Check if existing objects should be deleted
		String message = pathObjects.size() == 1 ? "Delete detection after converting to a point?" :
			String.format("Delete %d detections after converting to points?", pathObjects.size());
		var button = Dialogs.showYesNoCancelDialog("Detections to points", message);
		if (button == Dialogs.DialogButton.CANCEL)
			return;
		
		boolean	deleteDetections = button == Dialogs.DialogButton.YES;		
		PathObjectTools.convertToPoints(hierarchy, pathObjects, preferNucleus, deleteDetections);
	}


	public static void promptToSimplifyShape(ImageData<?> imageData, double altitudeThreshold) {
			PathObjectHierarchy hierarchy = imageData.getHierarchy();
			PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
			if (!(pathObject instanceof PathAnnotationObject) || pathObject.hasChildren() || !RoiTools.isShapeROI(pathObject.getROI())) {
				logger.error("Only annotations without child objects can be simplified");
				return;
			}
	
			String input = Dialogs.showInputDialog("Simplify shape", 
					"Set altitude threshold in pixels (> 0; higher values give simpler shapes)", 
					Double.toString(altitudeThreshold));
			if (input == null || !(input instanceof String) || ((String)input).trim().length() == 0)
				return;
			try {
				altitudeThreshold = Double.parseDouble(((String)input).trim());
			} catch (NumberFormatException e) {
				logger.error("Could not parse altitude threshold from {}", input);
				return;
			}
			
			long startTime = System.currentTimeMillis();
			ROI pathROI = pathObject.getROI();
			PathObject pathObjectNew = null;
			if (pathROI instanceof PolygonROI) {
				PolygonROI polygonROI = (PolygonROI)pathROI;
				polygonROI = ShapeSimplifier.simplifyPolygon(polygonROI, altitudeThreshold);
				pathObjectNew = PathObjects.createAnnotationObject(polygonROI, pathObject.getPathClass(), pathObject.getMeasurementList());
			} else {
				pathROI = ShapeSimplifier.simplifyShape(pathROI, altitudeThreshold);
				pathObjectNew = PathObjects.createAnnotationObject(pathROI, pathObject.getPathClass(), pathObject.getMeasurementList());			
			}
			long endTime = System.currentTimeMillis();
	//		logger.debug("Polygon simplified in " + (endTime - startTime)/1000. + " seconds");
			logger.debug("Shape simplified in " + (endTime - startTime) + " ms");
			hierarchy.removeObject(pathObject, true);
			hierarchy.addPathObject(pathObjectNew);
			hierarchy.getSelectionModel().setSelectedObject(pathObjectNew);
	//		viewer.setSelectedObject(pathObjectNew);
		}



	/**
	 * Select objects that are instances of a specified class, logging an appropriate method in the workflow.
	 * 
	 * @param imageData
	 * @param cls
	 */
	public static void selectObjectsByClass(final ImageData<?> imageData, final Class<? extends PathObject> cls) {
		if (cls == TMACoreObject.class)
			QP.selectTMACores(imageData.getHierarchy());
		else
			QP.selectObjectsByClass(imageData.getHierarchy(), cls);
		
		Map<String, String> params = Collections.singletonMap("Type", PathObjectTools.getSuitableName(cls, false));
		String method;
		if (cls == PathAnnotationObject.class)
			method = "selectAnnotations();";
		else if (cls == PathDetectionObject.class)
			method = "selectDetections();";
		else if (cls == TMACoreObject.class)
			method = "selectTMACores();";
		else if (cls == PathCellObject.class)
			method = "selectCells();";
		else if (cls == PathTileObject.class)
			method = "selectTiles();";
		else
			// TODO: Get a suitable name to disguise Java classes
			method = "selectObjectsByClass(" + cls.getName() + ");";
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep("Select objects by class", params, method);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}



	/**
	 * Reset the selection for an image.
	 * @param imageData
	 */
	public static void resetSelection(final ImageData<?> imageData) {
		if (imageData == null) {
			logger.warn("No image available!");
			return;
		}
		
		// Do the action reset
		imageData.getHierarchy().getSelectionModel().clearSelection();
		
		// Log the appropriate command
		String method = "resetSelection();";
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep("Reset selection", method);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}



	/**
	 * Select objects that are instances of a specified class, logging an appropriate method in the workflow.
	 * 
	 * @param imageData
	 * @param cls
	 */
	public static void resetClassifications(final ImageData<?> imageData, final Class<? extends PathObject> cls) {
		if (imageData == null) {
			logger.warn("No classifications to reset!");
			return;
		}
		// Do the reset
		QP.resetClassifications(imageData.getHierarchy(), cls);
		
		// Log the appropriate command
		Map<String, String> params = Collections.singletonMap("Type", PathObjectTools.getSuitableName(cls, false));
		String method;
		if (cls == PathDetectionObject.class)
			method = "resetDetectionClassifications();";
		else // TODO: Get a suitable name to disguise Java classes
			method = "resetClassifications(" + cls.getName() + ");";
		
		WorkflowStep newStep = new DefaultScriptableWorkflowStep("Reset classifications", params, method);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		if (newStep.equals(lastStep))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);
	}
	
	
	
	/**
	 * Show the QuPath script editor with a script corresponding to the command history of a specified image.
	 * @param qupath
	 * @param imageData
	 */
	public static void showWorkflowScript(QuPathGUI qupath, ImageData<?> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Show workflow script");
			return;
		}
		WorkflowCommandLogView.showScript(qupath.getScriptEditor(), imageData.getHistoryWorkflow());
	}
	
	/**
	 * Show the script editor, or bring the window to the front if it is already open.
	 * @param qupath
	 */
	public static void showScriptEditor(QuPathGUI qupath) {
		var scriptEditor = qupath.getScriptEditor();
		if (scriptEditor == null) {
			Dialogs.showErrorMessage("Script editor", "No script editor found!");
			return;
		}
		// Show script editor with a new script
		if ((scriptEditor instanceof Window) && ((Window)scriptEditor).isShowing())
			((Window)scriptEditor).toFront();
		else
			scriptEditor.showEditor();
	}
	
	
}
