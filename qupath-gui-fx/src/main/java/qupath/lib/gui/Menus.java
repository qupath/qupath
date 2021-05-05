/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.controlsfx.control.action.Action;

import javafx.beans.value.ObservableValue;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import qupath.lib.algorithms.IntensityFeaturesPlugin;
import qupath.lib.algorithms.TilerPlugin;
import qupath.lib.gui.ActionTools.ActionAccelerator;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionIcon;
import qupath.lib.gui.ActionTools.ActionMenu;
import qupath.lib.gui.QuPathGUI.DefaultActions;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.MeasurementExportCommand;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.commands.TMACommands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.SlideLabelView;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.objects.DilateAnnotationPlugin;
import qupath.lib.plugins.objects.FillAnnotationHolesPlugin;
import qupath.lib.plugins.objects.FindConvexHullDetectionsPlugin;
import qupath.lib.plugins.objects.RefineAnnotationsPlugin;
import qupath.lib.plugins.objects.SmoothFeaturesPlugin;
import qupath.lib.plugins.objects.SplitAnnotationsPlugin;

class Menus {
	
	private final static String URL_DOCS       = "https://qupath.readthedocs.io";
	private final static String URL_VIDEOS     = "https://www.youtube.com/c/QuPath";
	private final static String URL_CITATION   = "https://qupath.readthedocs.io/en/latest/docs/intro/citing.html";
	private final static String URL_BUGS       = "https://github.com/qupath/qupath/issues";
	private final static String URL_FORUM      = "https://forum.image.sc/tags/qupath";
	private final static String URL_SOURCE     = "https://github.com/qupath/qupath";

	
	private QuPathGUI qupath;
	private DefaultActions actionManager;
	
	private List<?> managers;
	
	Menus(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	public synchronized Collection<Action> getActions() {
		if (managers == null) {
			this.actionManager = qupath.getDefaultActions();
			managers = Arrays.asList(
					new FileMenuManager(),
					new EditMenuManager(),
					new ObjectsMenuManager(),
					new ViewMenuManager(),
					new MeasureMenuManager(),
					new AutomateMenuManager(),
					new AnalyzeMenuManager(),
					new TMAMenuManager(),
					new ClassifyMenuManager(),
					new HelpMenuManager()
					);
		}
		return managers.stream().flatMap(m -> ActionTools.getAnnotatedActions(m).stream()).collect(Collectors.toList());
	}
	
	static Action createAction(Runnable runnable) {
		return new Action(e -> runnable.run());
	}
	
	Action createSelectableCommandAction(final ObservableValue<Boolean> observable) {
		return ActionTools.createSelectableAction(observable, null);
	}
	
	
	
	@ActionMenu("Edit")
	public class EditMenuManager {
		
		@ActionMenu("Undo")
		@ActionAccelerator("shortcut+z")
		@ActionDescription("Undo the last action for the current viewer. " +
				"Note QuPath's undo is limited, and turns itself off (for performance reasons) when many objects are present. " +
				"The limit can be adjusted in the preferences.")
		public final Action UNDO;
		
		@ActionMenu("Redo")
		@ActionAccelerator("shortcut+shift+z")
		@ActionDescription("Redo the last action for the current viewer.")
		public final Action REDO;
		
		public final Action SEP_0 = ActionTools.createSeparator();

		// Copy actions
		@ActionMenu("Copy to clipboard...>Current viewer")
		@ActionDescription("Copy the contents of the current viewer to the clipboard. " + 
				"Note that this creates an RGB image, which does not necessarily contain the original pixel values.")
		public final Action COPY_VIEW = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.VIEWER));
		
		@ActionDescription("Copy the contents of the main QuPath window to the clipboard. " + 
				"This ignores any additional overlapping windows and dialog boxes.")
		@ActionMenu("Copy to clipboard...>Main window content")
		public final Action COPY_WINDOW = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.MAIN_SCENE));
		
		@ActionDescription("Copy the area of the screen corresponding to the main QuPath window to the clipboard. " + 
				"This includes any additional overlapping windows and dialog boxes.")
		@ActionMenu("Copy to clipboard...>Main window screenshot")
		public final Action COPY_WINDOW_SCREENSHOT = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT));			
		
		@ActionDescription("Make a screenshot and copy it to the clipboard.")
		@ActionMenu("Copy to clipboard...>Full screenshot")
		public final Action COPY_FULL_SCREENSHOT = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.FULL_SCREENSHOT));

		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionMenu("Preferences...")
		@ActionIcon(PathIcons.COG)
		@ActionAccelerator("shortcut+,")
		@ActionDescription("Set preferences to customize QuPath's appearance and behavior.")
		public final Action PREFERENCES = Commands.createSingleStageAction(() -> Commands.createPreferencesDialog(qupath));
		
		@ActionMenu("Reset preferences")
		@ActionDescription("Reset preferences to their default values - this can be useful if you are experiencing any newly-developed persistent problems with QuPath.")
		public final Action RESET_PREFERENCES = createAction(() -> Commands.promptToResetPreferences());

		
		public EditMenuManager() {
			var undoRedo = qupath.getUndoRedoManager();
			UNDO = createUndoAction(undoRedo);
			REDO = createRedoAction(undoRedo);
		}
		
		private Action createUndoAction(UndoRedoManager undoRedoManager) {
			Action actionUndo = new Action("Undo", e -> undoRedoManager.undoOnce());
			actionUndo.disabledProperty().bind(undoRedoManager.canUndo().not());
			return actionUndo;
		}
		
		private Action createRedoAction(UndoRedoManager undoRedoManager) {
			Action actionRedo = new Action("Redo", e -> undoRedoManager.redoOnce());
			actionRedo.disabledProperty().bind(undoRedoManager.canRedo().not());
			return actionRedo;
		}
		
		private void copyViewToClipboard(final QuPathGUI qupath, final GuiTools.SnapshotType type) {
			Image img = GuiTools.makeSnapshotFX(qupath, qupath.getViewer(), type);
			Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.IMAGE, img));
		}
	}
	

	@ActionMenu("Automate")
	public class AutomateMenuManager {
		
		@ActionDescription("Open the script editor.")
		@ActionMenu("Show script editor")
		@ActionAccelerator("shortcut+[")
		public final Action SCRIPT_EDITOR = createAction(() -> Commands.showScriptEditor(qupath));

		@ActionDescription("Open a script interpreter. " +
				"This makes it possible to run scripts interactively, line by line. " +
				"However, in general the Script Editor is more useful.")
		@ActionMenu("Script interpreter")
		public final Action SCRIPT_INTERPRETER = createAction(() -> Commands.showScriptInterpreter(qupath));
		
		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionDescription("Show a history of the commands applied to the current image. " +
				"Note that this is not fully exhaustive, because not all commands can be recorded. " +
				"However, the command history is useful to help automatically generate batch-processing scripts.")
		@ActionMenu("Show workflow command history")
		@ActionAccelerator("shortcut+shift+w")
		public final Action HISTORY_SHOW = Commands.createSingleStageAction(() -> Commands.createWorkflowDisplayDialog(qupath));

		@ActionDescription("Create a script based upon the actions recorded in the command history.")
		@ActionMenu("Create command history script")
		public final Action HISTORY_SCRIPT = qupath.createImageDataAction(imageData -> Commands.showWorkflowScript(qupath, imageData));

	}
	
	@ActionMenu("Analyze")
	public class AnalyzeMenuManager {
		
		@ActionDescription("Estimate stain vectors for color deconvolution in brightfield images. " + 
				"This can be used when there are precisely 2 stains (e.g. hematoxylin and eosin, hematoxylin and DAB) " +
				"to improve stain separation.")
		@ActionMenu("Preprocessing>Estimate stain vectors")
		public final Action COLOR_DECONVOLUTION_REFINE = qupath.createImageDataAction(imageData -> Commands.promptToEstimateStainVectors(imageData));
		
		@ActionDescription("Create tiles. These can be useful as part of a larger workflow, for example " + 
				"by adding intensity measurements to the tiles, training a classifier and then merging classified tiles to identify larger regions.")
		@ActionMenu("Tiles & superpixels>Create tiles")
		public final Action CREATE_TILES = qupath.createPluginAction("Create tiles", TilerPlugin.class, null);

		@ActionMenu("Cell detection>")
		public final Action SEP_0 = ActionTools.createSeparator();

		@ActionDescription("Supplement the measurements for detection objects by calculating a weighted sum of the corresponding measurements from neighboring objects.")
		@ActionMenu("Calculate features>Add smoothed features")
		public final Action SMOOTHED_FEATURES = qupath.createPluginAction("Add Smoothed features", SmoothFeaturesPlugin.class, null);
		@ActionDescription("Add new intensity-based features to objects.")
		@ActionMenu("Calculate features>Add intensity features")
		public final Action INTENSITY_FEATURES = qupath.createPluginAction("Add intensity features", IntensityFeaturesPlugin.class, null);
		@ActionDescription("Add new shape-based features to objects.")
		@ActionMenu("Calculate features>Add shape features")
		public final Action SHAPE_FEATURES = qupath.createImageDataAction(imageData -> Commands.promptToAddShapeFeatures(qupath));

//		@Deprecated
//		public final Action SHAPE_FEATURES = qupath.createPluginAction("Add shape features", ShapeFeaturesPlugin.class, null);

		@ActionDescription("Calculate distances between detection centroids and the closest annotation for each classification. " +
				"For example, this may be used to identify the distance of every cell from 'bigger' region that has been annotated (e.g. an area of tumor, a blood vessel).")
		@ActionMenu("Spatial analysis>Distance to annotations 2D")
		public final Action DISTANCE_TO_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData));
		@ActionDescription("Calculate distances between detection centroids for each classification. " +
				"For example, this may be used to identify the closest cell of a specified type.")
		@ActionMenu("Spatial analysis>Detect centroid distances 2D")
		public final Action DISTANCE_CENTROIDS = qupath.createImageDataAction(imageData -> Commands.detectionCentroidDistances2D(imageData));

	}
	
	@ActionMenu("Classify")
	public class ClassifyMenuManager {
				
		@ActionMenu("Object classification>")
		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionDescription("Reset the classifications of all detections.")
		@ActionMenu("Object classification>Reset detection classifications")
		public final Action RESET_DETECTION_CLASSIFICATIONS = qupath.createImageDataAction(imageData -> Commands.resetClassifications(imageData, PathDetectionObject.class));

		@ActionMenu("Pixel classification>")
		public final Action SEP_3 = ActionTools.createSeparator();

		public final Action SEP_4 = ActionTools.createSeparator();


	}
	
	@ActionMenu("File")
	public class FileMenuManager {
		
		@ActionDescription("Create a new project. " + 
				"Usually it's easier just to drag an empty folder onto QuPath to create a project, rather than navigate these menus.")
		@ActionMenu("Project...>Create project")
		public final Action PROJECT_NEW = createAction(() -> Commands.promptToCreateProject(qupath));
		
		@ActionDescription("Open an existing project. " +
				"Usually it's easier just to drag a project folder onto QuPath to open it, rather than bother with this command.")
		@ActionMenu("Project...>Open project")
		public final Action PROJECT_OPEN = createAction(() -> Commands.promptToOpenProject(qupath));
		
		@ActionDescription("Close the current project, including any images that are open.")
		@ActionMenu("Project...>Close project")
		public final Action PROJECT_CLOSE = qupath.createProjectAction(project -> Commands.closeProject(qupath));
		
		@ActionMenu("Project...>")
		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionDescription("Add images to the current project. " +
				"You can also add images by dragging files onto the main QuPath window.")
		@ActionMenu("Project...>Add images")
		public final Action IMPORT_IMAGES = qupath.createProjectAction(project -> ProjectCommands.promptToImportImages(qupath));

		@ActionDescription("Export a list of the image paths for images in the current project.")
		@ActionMenu("Project...>Export image list")
		public final Action EXPORT_IMAGE_LIST = qupath.createProjectAction(project -> ProjectCommands.promptToExportImageList(project));	
		
		@ActionMenu("Project...>")
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionDescription("Edit the metadata for the current project. " + 
				"By adding key-value properties to images, they can be sorted and queried more easily.")
		@ActionMenu("Project...>Edit project metadata")
		public final Action METADATA = qupath.createProjectAction(project -> ProjectCommands.showProjectMetadataEditor(project));
		
		@ActionDescription("Check the 'Uniform Resource Identifiers' for images in the current project. " +
				"This basically helps fix things whenever files have moved and images can no longer be found.")
		@ActionMenu("Project...>Check project URIs")
		public final Action CHECK_URIS = qupath.createProjectAction(project -> {
			try {
				ProjectCommands.promptToCheckURIs(project, false);
			} catch (IOException e) {
				Dialogs.showErrorMessage("Check project URIs", e);
			}
		});
		
		@ActionMenu("Project...>")
		public final Action SEP_22= ActionTools.createSeparator();
		
		@ActionDescription("Import images from a legacy project (QuPath v0.1.2 or earlier)." +
				"\n\nNote that it is generally a bad idea to mix versions of QuPath for analysis, "
				+ "but this can be helpful to recover old data and annotations."
				+ "\n\nThe original images will need to be available, with the paths set correctly in the project file.")
		@ActionMenu("Project...>Import images from v0.1.2")
		public final Action IMPORT_IMAGES_LEGACY = qupath.createProjectAction(project -> ProjectCommands.promptToImportLegacyProject(qupath));


		public final Action SEP_3 = ActionTools.createSeparator();

		// TODO: ADD RECENT PROJECTS
		@ActionDescription("Open an image in the current viewer, using a file chooser. " +
				"You can also just drag the file on top of the viewer.")
		@ActionMenu("Open...")
		@ActionAccelerator("shortcut+o")
		public final Action OPEN_IMAGE = createAction(() -> qupath.openImage(null, true, false));
		
		@ActionDescription("Open an image in the current viewer, by entering the path to the image. " +
				"This can be used to add images that are not represented by local files (e.g. hosted by OMERO), " + 
				"but beware that a compatible image reader needs to be available to interpret them.")
		@ActionMenu("Open URI...")
		@ActionAccelerator("shortcut+shift+o")
		public final Action OPEN_IMAGE_OR_URL = createAction(() -> qupath.openImage(null, true, true));
		
		@ActionDescription("Reload any previously-saved data for the current image. " +
				"This provides a more dramatic form of 'undo' (albeit without any 'redo' option).")
		@ActionMenu("Reload data")
		@ActionAccelerator("shortcut+r")
		public final Action RELOAD_DATA = qupath.createImageDataAction(imageData -> Commands.reloadImageData(qupath, imageData));

		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionDescription("Save a .qpdata file for this image, specifying the file path. " +
				"Warning! It is usually much better to use projects instead, and allow QuPath to decide where to store your data files.")
		@ActionMenu("Save As")
		@ActionAccelerator("shortcut+shift+s")
		public final Action SAVE_DATA_AS = qupath.createImageDataAction(imageData -> Commands.promptToSaveImageData(qupath, imageData, false));

		@ActionDescription("Save a .qpdata file for this image. This command is best used within projects, where QuPath will choose the location to save the file.")
		@ActionMenu("Save")
		@ActionAccelerator("shortcut+s")
		public final Action SAVE_DATA = qupath.createImageDataAction(imageData -> Commands.promptToSaveImageData(qupath, imageData, true));
		
		public final Action SEP_5 = ActionTools.createSeparator();
		
		@ActionDescription("Export an image region, by extracting the pixels from the original image.")
		@ActionMenu("Export images...>Original pixels")
		public final Action EXPORT_ORIGINAL = qupath.createImageDataAction(imageData -> Commands.promptToExportImageRegion(qupath.getViewer(), false));
		@ActionDescription("Export an image region, as an RGB image matching how it is displayed in the viewer.")
		@ActionMenu("Export images...>Rendered RGB (with overlays)")
		public final Action EXPORT_RENDERED = qupath.createImageDataAction(imageData -> Commands.promptToExportImageRegion(qupath.getViewer(), true));
		
		@ActionDescription("Export the area of the screen corresponding to the main QuPath window to the clipboard. " + 
				"This includes any additional overlapping windows and dialog boxes.")
		@ActionMenu("Export snapshot...>Main window screenshot")
		public final Action SNAPSHOT_WINDOW = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT));
		
		@ActionDescription("Export the contents of the main QuPath window to the clipboard. " + 
				"This ignores any additional overlapping windows and dialog boxes.")
		@ActionMenu("Export snapshot...>Main window content")
		public final Action SNAPSHOT_WINDOW_CONTENT = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.MAIN_SCENE));

		@ActionDescription("Export the contents of the current viewer to the clipboard. " + 
				"Note that this creates an RGB image, which does not necessarily contain the original pixel values.")
		@ActionMenu("Export snapshot...>Current viewer content")
		public final Action SNAPSHOT_VIEWER_CONTENT = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.VIEWER));
		
		public final Action SEP_6 = ActionTools.createSeparator();

		@ActionDescription("Import objects from GeoJSON or .qpdata files.")
		@ActionMenu("Object data...>Import objects")
		public final Action OBJECT_IMPORT= qupath.createImageDataAction(imageData -> Commands.runObjectImport(qupath, imageData));

		@ActionDescription("Export objects in GeoJSON format to file.")
		@ActionMenu("Object data...>Export as GeoJSON")
		public final Action EXPORT_GEOJSON = qupath.createImageDataAction(imageData -> Commands.runGeoJsonObjectExport(qupath, imageData));
		
		public final Action SEP_7 = ActionTools.createSeparator();

		@ActionDescription("Import a TMA map, e.g. a grid containing 'Unique ID' values for each core.")
		@ActionMenu("TMA data...>Import TMA map")
		public final Action TMA_IMPORT = qupath.createImageDataAction(imageData -> TMACommands.promptToImportTMAData(imageData));

		@ActionDescription("Export TMA data for the current image, in a format compatible with the 'TMA data viewer'.")
		@ActionMenu("TMA data...>Export TMA data")
		public final Action TMA_EXPORT = qupath.createImageDataAction(imageData -> TMACommands.promptToExportTMAData(qupath, imageData));
		
		@ActionDescription("Launch the 'TMA data viewer' to visualize TMA core data that was previously exported.")
		@ActionMenu("TMA data...>Launch TMA data viewer")
		public final Action TMA_VIEWER = createAction(() -> Commands.launchTMADataViewer(qupath));

		public final Action SEP_8 = ActionTools.createSeparator();

		@ActionDescription("Quit QuPath.")
		@ActionMenu("Quit")
		public final Action QUIT = new Action("Quit", e -> qupath.tryToQuit());

	}
	
	
	@ActionMenu("Objects")
	public class ObjectsMenuManager {
		
		@ActionDescription("Delete the currently selected objects.")
		@ActionMenu("Delete...>Delete selected objects")
		public final Action DELETE_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> GuiTools.promptToClearAllSelectedObjects(imageData));
		
		@ActionMenu("Delete...>")
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionDescription("Delete all objects for the current image.")
		@ActionMenu("Delete...>Delete all objects")
		public final Action CLEAR_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, null));

		@ActionDescription("Delete all annotation objects for the current image.")
		@ActionMenu("Delete...>Delete all annotations")
		public final Action CLEAR_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, PathAnnotationObject.class));
		
		@ActionDescription("Delete all detection objects for the current image.")
		@ActionMenu("Delete...>Delete all detections")
		public final Action CLEAR_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, PathDetectionObject.class));

		@ActionDescription("Reset the selected objects for the current image.")
		@ActionMenu("Select...>Reset selection")
		@ActionAccelerator("shortcut+alt+r")
		public final Action RESET_SELECTION = qupath.createImageDataAction(imageData -> Commands.resetSelection(imageData));

		@ActionMenu("Select...>")
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionDescription("Select all TMA cores for the current image.")
		@ActionMenu("Select...>Select TMA cores")
		@ActionAccelerator("shortcut+alt+t")
		public final Action SELECT_TMA_CORES = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, TMACoreObject.class));

		@ActionDescription("Select all annotation objects for the current image.")
		@ActionMenu("Select...>Select annotations")
		@ActionAccelerator("shortcut+alt+a")
		public final Action SELECT_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathAnnotationObject.class));

		@ActionDescription("Select all detection objects for the current image (this includes cells and tiles).")
		@ActionMenu("Select...>Select detections...>Select all detections")
		@ActionAccelerator("shortcut+alt+d")
		public final Action SELECT_ALL_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathDetectionObject.class));

		@ActionDescription("Select all cell objects for the current image.")
		@ActionMenu("Select...>Select detections...>Select cells")
		@ActionAccelerator("shortcut+alt+c")
		public final Action SELECT_CELLS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathCellObject.class));
		
		@ActionDescription("Select all tile objects for the current image.")
		@ActionMenu("Select...>Select detections...>Select tiles")
		public final Action SELECT_TILES = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathTileObject.class));

		@ActionMenu("Select...>")
		public final Action SEP_3 = ActionTools.createSeparator();

		@ActionDescription("Select objects based upon their classification.")
		@ActionMenu("Select...>Select objects by classification")
		public final Action SELECT_BY_CLASSIFICATION = qupath.createImageDataAction(imageData -> Commands.promptToSelectObjectsByClassification(qupath, imageData));
		
		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionDescription("Create a rectangle or ellipse annotation with the specified properties.")
		@ActionMenu("Annotations...>Specify annotation")
		public final Action SPECIFY_ANNOTATION = Commands.createSingleStageAction(() -> Commands.createSpecifyAnnotationDialog(qupath));
		
		@ActionDescription("Create an annotation representing the full width and height of the current image.")
		@ActionMenu("Annotations...>Create full image annotation")
		@ActionAccelerator("shortcut+shift+a")
		public final Action SELECT_ALL_ANNOTATION = qupath.createImageDataAction(imageData -> Commands.createFullImageAnnotation(qupath.getViewer()));

		@ActionMenu("Annotations...>")
		public final Action SEP_5 = ActionTools.createSeparator();
		
		@ActionDescription("Insert the selected objects in the object hierarchy. " + 
				"This involves resolving parent/child relationships based upon regions of interest.")
		@ActionMenu("Annotations...>Insert into hierarchy")
		@ActionAccelerator("shortcut+shift+i")
		public final Action INSERT_INTO_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.insertSelectedObjectsInHierarchy(imageData));
		
		@ActionDescription("Resolve the object hierarchy by setting parent/child relationships "
				+ "between objects based upon regions of interest.")
		@ActionMenu("Annotations...>Resolve hierarchy")
		@ActionAccelerator("shortcut+shift+r")
		public final Action RESOLVE_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.promptToResolveHierarchy(imageData));
		

		@ActionMenu("Annotations...>")
		public final Action SEP_6 = ActionTools.createSeparator();

		@ActionDescription("Interactively rotate the current selected annotation.")
		@ActionMenu("Annotations...>Rotate annotation")
		@ActionAccelerator("shortcut+shift+alt+r")
		public final Action RIGID_OBJECT_EDITOR = qupath.createImageDataAction(imageData -> Commands.editSelectedAnnotation(qupath));
		
		@ActionDescription("Duplicate the selected annotations.")
		@ActionMenu("Annotations...>Duplicate annotations")
		@ActionAccelerator("shift+d")
		public final Action ANNOTATION_DUPLICATE = qupath.createImageDataAction(imageData -> Commands.duplicateSelectedAnnotations(imageData));
		
		@ActionDescription("Transfer the last annotation to the current image. "
				+ "This can be used to bring annotations from one viewer to another, or to recover "
				+ "an annotation that has just been deleted.")
		@ActionMenu("Annotations...>Transfer last annotation")
		@ActionAccelerator("shift+e")
		public final Action TRANSFER_ANNOTATION = qupath.createImageDataAction(imageData -> qupath.viewerManager.applyLastAnnotationToActiveViewer());

		@ActionMenu("Annotations...>")
		public final Action SEP_7 = ActionTools.createSeparator();

		@ActionDescription("Expand (or contract) the selected annotations, "
				+ "optionally removing the interior.")
		@ActionMenu("Annotations...>Expand annotations")
		public final Action EXPAND_ANNOTATIONS = qupath.createPluginAction("Expand annotations", DilateAnnotationPlugin.class, null);
		
		@ActionDescription("Split complex annotations that contain disconnected pieces into separate annotations.")
		@ActionMenu("Annotations...>Split annotations")
		public final Action SPLIT_ANNOTATIONS = qupath.createPluginAction("Split annotations", SplitAnnotationsPlugin.class, null);
		
		@ActionDescription("Remove small fragments of annotations that contain disconnected pieces.")
		@ActionMenu("Annotations...>Remove fragments & holes")
		public final Action REMOVE_FRAGMENTS = qupath.createPluginAction("Remove fragments & holes", RefineAnnotationsPlugin.class, null);
		
		@ActionDescription("Fill holes occurring inside annotations.")
		@ActionMenu("Annotations...>Fill holes")
		public final Action FILL_HOLES = qupath.createPluginAction("Fill holes", FillAnnotationHolesPlugin.class, null);

		@ActionMenu("Annotations...>")
		public final Action SEP_8 = ActionTools.createSeparator();
		
		@ActionDescription("Make annotations corresponding to the 'inverse' of the selected annotation. "
				+ "The inverse annotation contains 'everything else' outside the current annotation, constrained by its parent.")
		@ActionMenu("Annotations...>Make inverse")
		public final Action MAKE_INVERSE = qupath.createImageDataAction(imageData -> Commands.makeInverseAnnotation(imageData));
		
		@ActionDescription("Merge the selected annotations to become one, single annotation.")
		@ActionMenu("Annotations...>Merge selected")
		public final Action MERGE_SELECTED = qupath.createImageDataAction(imageData -> Commands.mergeSelectedAnnotations(imageData));
		
		@ActionDescription("Simplify the shapes of the current selected annotations. "
				+ "This removes vertices that are considered unnecessary, using a specified amplitude tolerance.")
		@ActionMenu("Annotations...>Simplify shape")
		public final Action SIMPLIFY_SHAPE = qupath.createImageDataAction(imageData -> Commands.promptToSimplifySelectedAnnotations(imageData, 1.0));

	}
	
	
	@ActionMenu("TMA")
	public class TMAMenuManager {
		
		@ActionDescription("Add a row to the TMA grid before (above) the row containing the current selected object.")
		@ActionMenu("Add...>Add TMA row before")
		public final Action ADD_ROW_BEFORE = qupath.createImageDataAction(imageData -> TMACommands.promptToAddRowBeforeSelected(imageData));
		
		@ActionDescription("Add a row to the TMA grid after (below) the row containing the current selected object.")
		@ActionMenu("Add...>Add TMA row after")
		public final Action ADD_ROW_AFTER = qupath.createImageDataAction(imageData -> TMACommands.promptToAddRowAfterSelected(imageData));
		
		@ActionDescription("Add a column to the TMA grid before (to the left of) the column containing the current selected object.")
		@ActionMenu("Add...>Add TMA column before")
		public final Action ADD_COLUMN_BEFORE = qupath.createImageDataAction(imageData -> TMACommands.promptToAddColumnBeforeSelected(imageData));
		
		@ActionDescription("Add a column to the TMA grid after (to the right of) the column containing the current selected object.")
		@ActionMenu("Add...>Add TMA column after")
		public final Action ADD_COLUMN_AFTER = qupath.createImageDataAction(imageData -> TMACommands.promptToAddColumnAfterSelected(imageData));
		
		@ActionDescription("Remove the row containing the current selected object from the TMA grid.")
		@ActionMenu("Remove...>Remove TMA row")
		public final Action REMOVE_ROW = qupath.createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridRow(imageData));
		
		@ActionDescription("Remove the column containing the current selected object from the TMA grid.")
		@ActionMenu("Remove...>Remove TMA column")
		public final Action REMOVE_COLUMN = qupath.createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridColumn(imageData));

		@ActionDescription("Relabel the cores of a TMA grid. This is often needed after adding or deleting rows or columns.")
		@ActionMenu("Relabel TMA grid")
		public final Action RELABEL = qupath.createImageDataAction(imageData -> TMACommands.promptToRelabelTMAGrid(imageData));
		
		@ActionDescription("Remove all the metadata for the TMA grid in the current image.")
		@ActionMenu("Reset TMA metadata")
		public final Action RESET_METADATA = qupath.createImageDataAction(imageData -> Commands.resetTMAMetadata(imageData));
		
		@ActionDescription("Delete the TMA grid for the current image.")
		@ActionMenu("Delete TMA grid")
		public final Action CLEAR_CORES = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, TMACoreObject.class));
		
		@ActionDescription("Show an interactive summary view of all the TMA cores in the current image.")
		@ActionMenu("TMA grid summary view")
		public final Action SUMMARY_GRID = qupath.createImageDataAction(imageData -> TMACommands.showTMAGridView(qupath));

		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionDescription("Find all detections occurring on the convex hull of the detections within a TMA core. "
				+ "This can be used to find cells occurring towards the edge of the core, which can then be deleted if necessary. "
				+ "Often these cells may yield less reliable measurements because of artifacts.")
		@ActionMenu("Find convex hull detections (TMA)")
		public final Action CONVEX_HULL = qupath.createPluginAction("Find convex hull detections (TMA)", FindConvexHullDetectionsPlugin.class, null);

	}
	
	@ActionMenu("View")
	public class ViewMenuManager {
		
		@ActionDescription("Show/hide the analysis pane (the one on the left).")
		@ActionMenu("Show analysis pane")
		@ActionAccelerator("shift+a")
		public final Action SHOW_ANALYSIS_PANEL = actionManager.SHOW_ANALYSIS_PANE;
		
		@ActionDescription("Show the command list (much easier than navigating menus...).")
		@ActionMenu("Show command list")
		@ActionAccelerator("shortcut+l")
		public final Action COMMAND_LIST = Commands.createSingleStageAction(() -> CommandFinderTools.createCommandFinderDialog(qupath));

		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionDescription("Show the brightness/contrast dialog. "
				+ "This enables changing how the image is displayed, but not the image data itself.")
		public final Action BRIGHTNESS_CONTRAST = actionManager.BRIGHTNESS_CONTRAST;
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionDescription("Synchronize panning and zooming when working with images open in multiple viewers.")
		public final Action TOGGLE_SYNCHRONIZE_VIEWERS = actionManager.TOGGLE_SYNCHRONIZE_VIEWERS;
		
		@ActionDescription("Adjust zoom factors to match the resolutions of images open in multiple viewers.")
		public final Action MATCH_VIEWER_RESOLUTIONS = actionManager.MATCH_VIEWER_RESOLUTIONS;
		
		@ActionDescription("Open a viewer window that shows individual channels of an image size by side.")
		@ActionMenu("Mini viewers...>Show channel viewer")
		public final Action CHANNEL_VIEWER = qupath.createViewerAction(viewer -> Commands.showChannelViewer(viewer));

		@ActionDescription("Open a viewer window that shows a view of the pixel under the cursor.")
		@ActionMenu("Mini viewers...>Show mini viewer")
		public final Action MINI_VIEWER = qupath.createViewerAction(viewer -> Commands.showMiniViewer(viewer));
		
		public final Action SEP_2 = ActionTools.createSeparator();
		
		@ActionDescription("Set the zoom factor to 400% (downsample = 0.25).")
		@ActionMenu("Zoom...>400%")
		public final Action ZOOM_400 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 0.25));
		@ActionDescription("Set the zoom factor to 100% (downsample = 1).")
		@ActionMenu("Zoom...>100%")
		public final Action ZOOM_100 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 1));
		@ActionDescription("Set the zoom factor to 10% (downsample = 10).")
		@ActionMenu("Zoom...>10%")
		public final Action ZOOM_10 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 10));
		@ActionDescription("Set the zoom factor to 1% (downsample = 100).")
		@ActionMenu("Zoom...>1%")
		public final Action ZOOM_1 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 100));
		
		public final Action SEP_3 = ActionTools.createSeparator();
		@ActionDescription("Zoom in for the current viewer.")
		@ActionMenu("Zoom...>Zoom in")
		@ActionIcon(PathIcons.ZOOM_IN)
//		@ActionAccelerator("ignore shift+plus")
		public final Action ZOOM_IN = Commands.createZoomCommand(qupath, 10);
		@ActionDescription("Zoom out for the current viewer.")
		@ActionMenu("Zoom...>Zoom out")
		@ActionIcon(PathIcons.ZOOM_OUT)
		@ActionAccelerator("-")
		public final Action ZOOM_OUT = Commands.createZoomCommand(qupath, -10);
		
		@ActionDescription("Adjust zoom for all images to fit the entire image in the viewer.")
		@ActionMenu("Zoom...>Zoom to fit")
		public final Action ZOOM_TO_FIT = actionManager.ZOOM_TO_FIT;
				
		@ActionDescription("Rotate the image visually (this is only for display - the coordinate system remains unchanged).")
		@ActionMenu("Rotate image")
		public final Action ROTATE_IMAGE = qupath.createImageDataAction(imageData -> Commands.createRotateImageDialog(qupath));

		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionMenu("Cell display>")
		@ActionDescription("Show cells by drawing the outer boundary ROI only.")
		public final Action SHOW_CELL_BOUNDARIES = actionManager.SHOW_CELL_BOUNDARIES;
		@ActionDescription("Show cells by drawing the nucleus ROI only (if available).")
		public final Action SHOW_CELL_NUCLEI = actionManager.SHOW_CELL_NUCLEI;
		@ActionDescription("Show cells by drawing both the outer boundary and nucleus ROIs (if available).")
		public final Action SHOW_CELL_BOUNDARIES_AND_NUCLEI = actionManager.SHOW_CELL_BOUNDARIES_AND_NUCLEI;
		@ActionDescription("Show cells by drawing the centroids only.")
		public final Action SHOW_CELL_CENTROIDS = actionManager.SHOW_CELL_CENTROIDS;
		
		@ActionDescription("Toggle showing all annotations in the viewer.")
		public final Action SHOW_ANNOTATIONS = actionManager.SHOW_ANNOTATIONS;
		@ActionDescription("Toggle showing annotation ROIs as filled shapes in the viewer.")
		public final Action FILL_ANNOTATIONS = actionManager.FILL_ANNOTATIONS;
		@ActionDescription("Toggle showing all annotation names in the viewer.")
		public final Action SHOW_NAMES = actionManager.SHOW_NAMES;
		@ActionDescription("Toggle showing any TMA grid in the viewer.")
		public final Action SHOW_TMA_GRID = actionManager.SHOW_TMA_GRID;
		@ActionDescription("Toggle showing any TMA core labels in the viewer.")
		public final Action SHOW_TMA_GRID_LABELS = actionManager.SHOW_TMA_GRID_LABELS;
		@ActionDescription("Toggle showing all detections in the viewer.")
		public final Action SHOW_DETECTIONS = actionManager.SHOW_DETECTIONS;
		@ActionDescription("Toggle showing detection ROIs as filled shapes in the viewer.")
		public final Action FILL_DETECTIONS = actionManager.FILL_DETECTIONS;

		@ActionMenu("Show object connections")
		@ActionDescription("Show connections between objects, if available. "
				+ "This can be used alongside some spatial commands, such as to display a Delaunay triangulation as an overlay.")
		public final Action SHOW_CONNECTIONS = createSelectableCommandAction(qupath.getOverlayOptions().showConnectionsProperty());

		@ActionDescription("Toggle pixel classification overlays in the viewer. "
				+ "This only has an effect if there is actually a pixel classification available.")
		public final Action SHOW_PIXEL_CLASSIFICATION = actionManager.SHOW_PIXEL_CLASSIFICATION;
		
		public final Action SEP_5 = ActionTools.createSeparator();
		
		@ActionDescription("Toggle showing the image overview in the viewer. This is a clickable thumbnail used for navigation.")
		public final Action SHOW_OVERVIEW = actionManager.SHOW_OVERVIEW;
		@ActionDescription("Toggle showing the cursor location in the viewer.")
		public final Action SHOW_LOCATION = actionManager.SHOW_LOCATION;
		@ActionDescription("Toggle showing the scalebar in the viewer.")
		public final Action SHOW_SCALEBAR = actionManager.SHOW_SCALEBAR;
		@ActionDescription("Toggle showing the counting grid in the viewer.")
		public final Action SHOW_GRID = actionManager.SHOW_GRID;
		@ActionDescription("Adjust the counting grid spacing for the viewers.")
		public final Action GRID_SPACING = actionManager.GRID_SPACING;
		
		public final Action SEP_6 = ActionTools.createSeparator();
		
		@ActionDescription("Record zoom and panning movements within a viewer for later playback.")
		@ActionMenu("Show view recorder")
		public final Action VIEW_TRACKER = qupath.createImageDataAction(imageData -> Commands.showViewTracker(qupath));

		@ActionDescription("Show the slide label associated with the image in the active viewer (if available).")
		@ActionMenu("Show slide label")
		public final Action SLIDE_LABEL = createSelectableCommandAction(new SlideLabelView(qupath).showingProperty());

		public final Action SEP_7 = ActionTools.createSeparator();
		
		@ActionDescription("Show mouse clicks and keypresses on screen. "
				+ "This is particularly useful for demos and tutorials.")
		@ActionMenu("Show input display")
		public final Action INPUT_DISPLAY = createAction(() -> Commands.showInputDisplay(qupath));

		@ActionDescription("Show a dialog to track memory usage within QuPath, and clear the cache if required.")
		@ActionMenu("Show memory monitor")
		public final Action MEMORY_MONITORY = Commands.createSingleStageAction(() -> Commands.createMemoryMonitorDialog(qupath));
		
		@ActionDescription("Show the log. This is very helpful for identifying and debugging errors. "
				+ "\n\nIf you wish to report a problem using QuPath, please check the log for relevant information to provide.")
		@ActionMenu("Show log")
		public final Action SHOW_LOG = actionManager.SHOW_LOG;
		
		
		public final Action SEP_8 = ActionTools.createSeparator();

		@ActionDescription("Turn on all multi-touch gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Turn on all gestures")
		public final Action GESTURES_ALL = createAction(() -> {
			PathPrefs.useScrollGesturesProperty().set(true);
			PathPrefs.useZoomGesturesProperty().set(true);
			PathPrefs.useRotateGesturesProperty().set(true);
		});
		
		@ActionDescription("Turn off all multi-touch gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Turn off all gestures")
		public final Action GESTURES_NONE = createAction(() -> {
			PathPrefs.useScrollGesturesProperty().set(false);
			PathPrefs.useZoomGesturesProperty().set(false);
			PathPrefs.useRotateGesturesProperty().set(false);
		});
		
		@ActionMenu("Multi-touch gestures>")
		public final Action SEP_9 = ActionTools.createSeparator();
		
		@ActionDescription("Toggle scroll gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Use scroll gestures")
		public final Action GESTURES_SCROLL = createSelectableCommandAction(PathPrefs.useScrollGesturesProperty());
		
		@ActionDescription("Toggle zoom gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Use zoom gestures")
		public final Action GESTURES_ZOOM = createSelectableCommandAction(PathPrefs.useZoomGesturesProperty());

		@ActionDescription("Toggle rotate gestures for touchscreens and trackpads.")
		@ActionMenu("Multi-touch gestures>Use rotate gestures")
		public final Action GESTURES_ROTATE = createSelectableCommandAction(PathPrefs.useRotateGesturesProperty());

		ViewMenuManager() {
			// This accelerator should work even if the character requires a shift key to be pressed
			// Oddly, on an English keyboard Shift+= must be pressed at first (since this indicates the + key), 
			// but afterwards = alone will suffice (i.e. Shift is truly ignored again).
			ZOOM_IN.setAccelerator(new KeyCharacterCombination("+", KeyCombination.SHIFT_ANY));
			// Match on whatever would type +
			var combo = new KeyCombination(KeyCombination.SHIFT_ANY, KeyCombination.SHORTCUT_ANY) {
				@Override
				public boolean match(final KeyEvent event) {
					return 
						event.getCode() != KeyCode.UNDEFINED &&
						"+".equals(event.getText()) &&
						super.match(event);
				}
			};
			ZOOM_IN.setAccelerator(combo);
		}
		
	}
	
	
	@ActionMenu("Measure")
	public class MeasureMenuManager {
		
		@ActionMenu("Show measurement maps")
		@ActionAccelerator("shortcut+shift+m")
		@ActionDescription("View detection measurements in context using interactive, color-coded maps.")
		public final Action MAPS = Commands.createSingleStageAction(() -> Commands.createMeasurementMapDialog(qupath));
		
		@ActionMenu("Show measurement manager")
		@ActionDescription("View and optionally delete detection measurements.")
		public final Action MANAGER = qupath.createImageDataAction(imageData -> Commands.showDetectionMeasurementManager(qupath, imageData));
		
		@ActionMenu("")
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionDescription("Show a measurement table for tissue microarray cores.")
		public final Action TMA = qupath.getDefaultActions().MEASURE_TMA;
		
		@ActionDescription("Show a measurement table for annotation objects.")
		public final Action ANNOTATIONS = qupath.getDefaultActions().MEASURE_ANNOTATIONS;
		
		@ActionDescription("Show a measurement table for detection objects.")
		public final Action DETECTIONS = qupath.getDefaultActions().MEASURE_DETECTIONS;
		
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionMenu("Export measurements")		
		@ActionDescription("Export summary measurements for multiple images within a project.")
		public final Action EXPORT;
		
		private MeasureMenuManager() {
			var measureCommand = new MeasurementExportCommand(qupath);
			EXPORT = qupath.createProjectAction(project -> measureCommand.run());
		}
		
	}
	
	
	@ActionMenu("Help")
	public class HelpMenuManager {

		@ActionDescription("Show the setup options that appear when QuPath is first started, " +
				"to set the maximum memory and locale.")
		@ActionMenu("Show setup options")
		public final Action QUPATH_SETUP = createAction(() -> qupath.showSetupDialog());

		@ActionMenu("")
		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionDescription("Open the main QuPath documentation website.")
		@ActionMenu("Documentation (web)")
		public final Action DOCS = createAction(() -> QuPathGUI.launchBrowserWindow(URL_DOCS));
		
		@ActionDescription("Open the QuPath demo videos and tutorials.")
		@ActionMenu("YouTube channel (web)")
		public final Action DEMOS = createAction(() -> QuPathGUI.launchBrowserWindow(URL_VIDEOS));

		@ActionDescription("Check online for an updated QuPath release.")
		@ActionMenu("Check for updates (web)")
		public final Action UPDATE = createAction(() -> qupath.checkForUpdate(false));

		public final Action SEP_2 = ActionTools.createSeparator();
		
		@ActionDescription("Please cite the QuPath publication if you use the software! " +
				"\nThis command opens a web page to show how.")
		@ActionMenu("Cite QuPath (web)")
		public final Action CITE = createAction(() -> QuPathGUI.launchBrowserWindow(URL_CITATION));
		
		@ActionDescription("Report a bug. Please follow the template and do not use this for general questions!")
		@ActionMenu("Report bug (web)")
		public final Action BUGS = createAction(() -> QuPathGUI.launchBrowserWindow(URL_BUGS));
		
		@ActionDescription("Visit the user forum. This is the place to ask questions (and give answers).")
		@ActionMenu("View user forum (web)")
		public final Action FORUM = createAction(() -> QuPathGUI.launchBrowserWindow(URL_FORUM));
		
		@ActionDescription("View the QuPath source code online.")
		@ActionMenu("View source code (web)")
		public final Action SOURCE = createAction(() -> QuPathGUI.launchBrowserWindow(URL_SOURCE));

		public final Action SEP_3 = ActionTools.createSeparator();

		@ActionDescription("View license information for QuPath and its third-party dependencies.")
		@ActionMenu("License")
		public final Action LICENSE = Commands.createSingleStageAction(() -> Commands.createLicensesWindow(qupath));
		
		@ActionDescription("View system information.")
		@ActionMenu("System info")
		public final Action INFO = Commands.createSingleStageAction(() -> Commands.createShowSystemInfoDialog(qupath));
		
		@ActionDescription("View a list of installed QuPath extensions.")
		@ActionMenu("Installed extensions")
		public final Action EXTENSIONS = createAction(() -> Commands.showInstalledExtensions(qupath));
		
	}

}