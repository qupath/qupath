package qupath.lib.gui;

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
import qupath.lib.algorithms.IntensityFeaturesPlugin;
import qupath.lib.algorithms.TilerPlugin;
import qupath.lib.gui.ActionTools.ActionAccelerator;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionIcon;
import qupath.lib.gui.ActionTools.ActionMenu;
import qupath.lib.gui.QuPathGUI.ActionManager;
import qupath.lib.gui.commands.EstimateStainVectorsCommand;
import qupath.lib.gui.commands.ExportImageRegionCommand;
import qupath.lib.gui.commands.LoadClassifierCommand;
import qupath.lib.gui.commands.MeasurementExportCommand;
import qupath.lib.gui.commands.MeasurementManager;
import qupath.lib.gui.commands.MeasurementMapCommand;
import qupath.lib.gui.commands.MemoryMonitorCommand;
import qupath.lib.gui.commands.MiniViewerCommand;
import qupath.lib.gui.commands.PreferencesCommand;
import qupath.lib.gui.commands.ProjectCheckUrisCommand;
import qupath.lib.gui.commands.ProjectExportImageListCommand;
import qupath.lib.gui.commands.ProjectImportImagesCommand;
import qupath.lib.gui.commands.ProjectMetadataEditorCommand;
import qupath.lib.gui.commands.RigidObjectEditorCommand;
import qupath.lib.gui.commands.RotateImageCommand;
import qupath.lib.gui.commands.ScriptInterpreterCommand;
import qupath.lib.gui.commands.SelectAllAnnotationCommand;
import qupath.lib.gui.commands.ShowInputDisplayCommand;
import qupath.lib.gui.commands.ShowInstalledExtensionsCommand;
import qupath.lib.gui.commands.ShowLicensesCommand;
import qupath.lib.gui.commands.ShowSystemInfoCommand;
import qupath.lib.gui.commands.SingleFeatureClassifierCommand;
import qupath.lib.gui.commands.SparseImageServerCommand;
import qupath.lib.gui.commands.SpecifyAnnotationCommand;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.commands.TMAExporterCommand;
import qupath.lib.gui.commands.TMAGridAdd;
import qupath.lib.gui.commands.TMAGridRelabel;
import qupath.lib.gui.commands.TMAGridRemove;
import qupath.lib.gui.commands.TMAGridView;
import qupath.lib.gui.commands.TMAScoreImportCommand;
import qupath.lib.gui.commands.WorkflowDisplayCommand;
import qupath.lib.gui.commands.ZoomCommand;
import qupath.lib.gui.commands.TMAGridAdd.TMAAddType;
import qupath.lib.gui.commands.TMAGridRemove.TMARemoveType;
import qupath.lib.gui.icons.PathIconFactory.PathIcons;
import qupath.lib.gui.panels.classify.RandomTrainingRegionSelector;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.objects.DilateAnnotationPlugin;
import qupath.lib.plugins.objects.FillAnnotationHolesPlugin;
import qupath.lib.plugins.objects.FindConvexHullDetectionsPlugin;
import qupath.lib.plugins.objects.RefineAnnotationsPlugin;
import qupath.lib.plugins.objects.ShapeFeaturesPlugin;
import qupath.lib.plugins.objects.SmoothFeaturesPlugin;
import qupath.lib.plugins.objects.SplitAnnotationsPlugin;

class Menus {
	
	private QuPathGUI qupath;
	private ActionManager actionManager;
	
	private List<?> managers;
	
	Menus(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	public synchronized Collection<Action> getActions() {
		if (managers == null) {
			this.actionManager = qupath.getActionManager();
			managers = Arrays.asList(
					new FileMenuManager(),
					new EditMenuManager(),
					new ObjectsMenuManager(),
					new ToolsMenuManager(),
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
	
	
	@ActionMenu("Tools")
	public class ToolsMenuManager {
		
		public final Action MOVE_TOOL = actionManager.MOVE_TOOL;
		public final Action RECTANGLE_TOOL = actionManager.RECTANGLE_TOOL;
		public final Action ELLIPSE_TOOL = actionManager.ELLIPSE_TOOL;
		public final Action LINE_TOOL = actionManager.LINE_TOOL;
		public final Action POLYGON_TOOL = actionManager.POLYGON_TOOL;
		public final Action POLYLINE_TOOL = actionManager.POLYLINE_TOOL;
		public final Action BRUSH_TOOL = actionManager.BRUSH_TOOL;
		public final Action POINTS_TOOL = actionManager.POINTS_TOOL;
		
		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionMenu("Multi-touch gestures>Turn on all gestures")
		public final Action GESTURES_ALL = createAction(() -> {
			PathPrefs.setUseScrollGestures(true);
			PathPrefs.setUseZoomGestures(true);
			PathPrefs.setUseRotateGestures(true);
		});
		
		@ActionMenu("Multi-touch gestures>Turn off all gestures")
		public final Action GESTURES_NONE = createAction(() -> {
			PathPrefs.setUseScrollGestures(false);
			PathPrefs.setUseZoomGestures(false);
			PathPrefs.setUseRotateGestures(false);
		});

		@ActionMenu("Multi-touch gestures>")
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionMenu("Multi-touch gestures>Use scroll gestures")
		public final Action GESTURES_SCROLL = createSelectableCommandAction(PathPrefs.useScrollGesturesProperty());
		@ActionMenu("Multi-touch gestures>Use zoom gestures")
		public final Action GESTURES_ZOOM = createSelectableCommandAction(PathPrefs.useZoomGesturesProperty());
		@ActionMenu("Multi-touch gestures>Use rotate gestures")
		public final Action GESTURES_ROTATE = createSelectableCommandAction(PathPrefs.useRotateGesturesProperty());

	}
	
	
	@ActionMenu("Edit")
	public class EditMenuManager {
		
		@ActionMenu("Undo")
		@ActionAccelerator("shortcut+z")
		public final Action UNDO;
		
		@ActionMenu("Redo")
		@ActionAccelerator("shortcut+shift+z")
		public final Action REDO;
		
		public final Action SEP_0 = ActionTools.createSeparator();
		
		// Copy actions
		@ActionMenu("Copy to clipboard...>Current viewer")
		@ActionAccelerator("shortcut+c")
		public final Action COPY_VIEW = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.VIEWER));
		
		@ActionMenu("Copy to clipboard...>Main window content")
		public final Action COPY_WINDOW = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.MAIN_SCENE));
		
		@ActionMenu("Copy to clipboard...>Main window screenshot")
		public final Action COPY_WINDOW_SCREENSHOT = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT));			
		
		@ActionMenu("Copy to clipboard...>Full screenshot")
		public final Action COPY_FULL_SCREENSHOT = createAction(() -> copyViewToClipboard(qupath, GuiTools.SnapshotType.FULL_SCREENSHOT));

		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionMenu("Preferences...")
		@ActionIcon(PathIcons.COG)
		@ActionAccelerator("shortcut+,")
		@ActionDescription("Set preferences to customize QuPath's appearance and behavior")
		public final Action PREFERENCES;
		
		@ActionMenu("Reset preferences")
		@ActionDescription("Reset preferences to their default values - this can be useful if you are experiencing any newly-developed persistent problems with QuPath")
		public final Action RESET_PREFERENCES = createAction(() -> Commands.promptToResetPreferences());

		
		public EditMenuManager() {
			var undoRedo = qupath.getUndoRedoManager();
			UNDO = createUndoAction(undoRedo);
			REDO = createRedoAction(undoRedo);
			PREFERENCES = createAction(new PreferencesCommand(qupath, qupath.getPreferencePanel()));
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
		
		@ActionMenu("Show script editor")
		@ActionAccelerator("shortcut+[")
		public final Action SCRIPT_EDITOR = createAction(() -> Commands.showScriptEditor(qupath));

		@ActionMenu("Script interpreter")
		public final Action SCRIPT_INTERPRETER = createAction(new ScriptInterpreterCommand(qupath));
		
		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionMenu("Show workflow command history")
		@ActionAccelerator("shortcut+shift+w")
		public final Action HISTORY_SHOW = createAction(new WorkflowDisplayCommand(qupath));

		@ActionMenu("Create command history script")
		public final Action HISTORY_SCRIPT = qupath.createImageDataAction(imageData -> Commands.showWorkflowScript(qupath, imageData));

	}
	
	@ActionMenu("Analyze")
	public class AnalyzeMenuManager {
		
		@ActionMenu("Preprocessing>Estimate stain vectors")
		public final Action COLOR_DECONVOLUTION_REFINE = createAction(new EstimateStainVectorsCommand(qupath));
		
		@ActionMenu("Region identification>Tiles & superpixels>Create tiles")
		public final Action CREATE_TILES = qupath.createPluginAction("Create tiles", TilerPlugin.class, qupath, null);

		@ActionMenu("Cell detection>")
		public final Action SEP_0 = ActionTools.createSeparator();

		@ActionMenu("Calculate features>Add smoothed features")
		public final Action SMOOTHED_FEATURES = qupath.createPluginAction("Add Smoothed features", SmoothFeaturesPlugin.class, qupath, null);
		@ActionMenu("Calculate features>Add intensity features")
		public final Action INTENSITY_FEATURES = qupath.createPluginAction("Add intensity features", IntensityFeaturesPlugin.class, qupath, null);
		@ActionMenu("Calculate features>Add shape features (deprecated)")
		public final Action SHAPE_FEATURES = qupath.createPluginAction("Add shape features", ShapeFeaturesPlugin.class, qupath, null);

		@ActionMenu("Spatial analysis>Distance to annotations 2D")
		public final Action DISTANCE_TO_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.distanceToAnnotations2D(imageData));
		@ActionMenu("Spatial analysis>Detect centroid distances 2D")
		public final Action DISTANCE_CENTROIDS = qupath.createImageDataAction(imageData -> Commands.detectionCentroidDistances2D(imageData));

	}
	
	@ActionMenu("Classify")
	public class ClassifyMenuManager {
		
		
		@ActionMenu("Object classification>Legacy>Load detection classifier (legacy)")
		public final Action LEGACY_DETECTION = createAction(new LoadClassifierCommand(qupath));
		
		@ActionMenu("Object classification>")
		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionMenu("Object classification>Legacy>Choose random training samples (legacy)")
		public final Action LEGACY_RANDOM_TRAINING = createAction(new RandomTrainingRegionSelector(qupath, qupath.getAvailablePathClasses()));

		@ActionMenu("Object classification>Legacy>Classify by specific feature (legacy)")
		public final Action LEGACY_FEATURE = createAction(new SingleFeatureClassifierCommand(qupath, PathDetectionObject.class));

		@ActionMenu("Object classification>")
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionMenu("Object classification>Reset detection classifications")
		public final Action RESET_DETECTION_CLASSIFICATIONS = qupath.createImageDataAction(imageData -> Commands.resetClassifications(imageData, PathDetectionObject.class));

		@ActionMenu("Pixel classification>")
		public final Action SEP_3 = ActionTools.createSeparator();

		public final Action SEP_4 = ActionTools.createSeparator();

		@ActionMenu("Extras>Create combined training image")
		public final Action TRAINING_IMAGE = createAction(new SparseImageServerCommand(qupath));

	}
	
	@ActionMenu("File")
	public class FileMenuManager {
		
		@ActionMenu("Project...>Create project")
		public final Action PROJECT_NEW = createAction(() -> Commands.promptToCreateProject(qupath));
		
		@ActionMenu("Project...>Open project")
		public final Action PROJECT_OPEN = createAction(() -> Commands.promptToOpenProject(qupath));
		
		@ActionMenu("Project...>Close project")
		public final Action PROJECT_CLOSE = qupath.createProjectAction(project -> Commands.closeProject(qupath));
		
		@ActionMenu("Project...>")
		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionMenu("Project...>Add images")
		public final Action IMPORT_IMAGES = createAction(new ProjectImportImagesCommand(qupath));
		@ActionMenu("Project...>Export image list")
		public final Action EXPORT_IMAGE_LIST = createAction(new ProjectExportImageListCommand(qupath));	
		
		@ActionMenu("Project...>")
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionMenu("Project...>Edit project metadata")
		public final Action METADATA = createAction(new ProjectMetadataEditorCommand(qupath));
		
		@ActionMenu("Project...>Check project URIs")
		public final Action CHECK_URIS = createAction(new ProjectCheckUrisCommand(qupath));

		public final Action SEP_3 = ActionTools.createSeparator();

		// TODO: ADD RECENT PROJECTS
		@ActionMenu("Open...")
		@ActionAccelerator("shortcut+o")
		public final Action OPEN_IMAGE = createAction(() -> qupath.openImage(null, true, false));
		@ActionMenu("Open URI...")
		@ActionAccelerator("shortcut+shift+o")
		public final Action OPEN_IMAGE_OR_URL = createAction(() -> qupath.openImage(null, true, true));
		
		@ActionMenu("Reload data")
		@ActionAccelerator("shortcut+r")
		public final Action RELOAD_DATA = qupath.createImageDataAction(imageData -> Commands.reloadImageData(qupath, imageData));

		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionMenu("Save As")
		@ActionAccelerator("shortcut+s")
		public final Action SAVE_DATA_AS = qupath.createImageDataAction(imageData -> Commands.promptToSaveImageData(qupath, imageData, false));
		@ActionMenu("Save")
		@ActionAccelerator("shortcut+shift+s")
		public final Action SAVE_DATA = qupath.createImageDataAction(imageData -> Commands.promptToSaveImageData(qupath, imageData, true));
		
		public final Action SEP_5 = ActionTools.createSeparator();
		
		@ActionMenu("Export images...>Original pixels")
		public final Action EXPORT_ORIGINAL = createAction(new ExportImageRegionCommand(qupath, false));
		@ActionMenu("Export images...>Rendered RGB (with overlays)")
		public final Action EXPORT_RENDERED = createAction(new ExportImageRegionCommand(qupath, false));
		
		@ActionMenu("Export snapshot...>Main window screenshot")
		public final Action SNAPSHOT_WINDOW = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT));
		@ActionMenu("Export snapshot...>Main window content")
		public final Action SNAPSHOT_WINDOW_CONTENT = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.MAIN_SCENE));
		@ActionMenu("Export snapshot...>Current viewer content")
		public final Action SNAPSHOT_VIEWER_CONTENT = createAction(() -> Commands.saveSnapshot(qupath, GuiTools.SnapshotType.VIEWER));
		
		public final Action SEP_6 = ActionTools.createSeparator();

		@ActionMenu("TMA data...>Import TMA map")
		public final Action TMA_IMPORT = createAction(new TMAScoreImportCommand(qupath));
		@ActionMenu("TMA data...>Launch Export TMA data")
		public final Action TMA_EXPORT = createAction(new TMAExporterCommand(qupath.viewerProperty()));
		@ActionMenu("TMA data...>Launch TMA data viewer")
		public final Action TMA_VIEWER = createAction(() -> Commands.launchTMADataViewer(qupath));

		public final Action SEP_7 = ActionTools.createSeparator();

		@ActionMenu("Quit")
		public final Action QUIT = new Action("Quit", e -> qupath.tryToQuit());

	}
	
	
	@ActionMenu("Objects")
	public class ObjectsMenuManager {
		
		@ActionMenu("Delete...>Delete selected objects")
		public final Action DELETE_SELECTED_OBJECTS = qupath.createImageDataAction(imageData -> GuiTools.promptToClearAllSelectedObjects(imageData));
		
		@ActionMenu("Delete...>")
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionMenu("Delete...>Delete all objects")
		public final Action CLEAR_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, null));
		@ActionMenu("Delete...>Delete all annotations")
		public final Action CLEAR_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, PathAnnotationObject.class));
		@ActionMenu("Delete...>Delete all detects")
		public final Action CLEAR_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, PathDetectionObject.class));

		@ActionMenu("Select...>Reset selection")
		@ActionAccelerator("shortcut+alt+r")
		public final Action RESET_SELECTION = qupath.createImageDataAction(imageData -> Commands.resetSelection(imageData));

		@ActionMenu("Select...>")
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionMenu("Select...>Select TMA cores")
		@ActionAccelerator("shortcut+alt+t")
		public final Action SELECT_TMA_CORES = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, TMACoreObject.class));
		@ActionMenu("Select...>Select annotations")
		@ActionAccelerator("shortcut+alt+a")
		public final Action SELECT_ANNOTATIONS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathAnnotationObject.class));

		@ActionMenu("Select...>Select detections...>Select all detections")
		@ActionAccelerator("shortcut+alt+d")
		public final Action SELECT_ALL_DETECTIONS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathDetectionObject.class));
		@ActionMenu("Select...>Select detections...>Select cells")
		@ActionAccelerator("shortcut+alt+c")
		public final Action SELECT_CELLS = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathCellObject.class));
		@ActionMenu("Select...>Select detections...>Select tiles")
		public final Action SELECT_TILES = qupath.createImageDataAction(imageData -> Commands.selectObjectsByClass(imageData, PathTileObject.class));

		@ActionMenu("Select...>")
		public final Action SEP_3 = ActionTools.createSeparator();

		@ActionMenu("Select...>Select objects by classification")
		public final Action SELECT_BY_CLASSIFICATION = qupath.createImageDataAction(imageData -> Commands.promptToSelectObjectsByClassification(qupath, imageData));
		
		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionMenu("Annotations...>Specify annotation")
		public final Action SPECIFY_ANNOTATION = createAction(new SpecifyAnnotationCommand(qupath));
		@ActionMenu("Annotations...>Create full image annotation")
		@ActionAccelerator("shortcut+shift+a")
		public final Action SELECT_ALL_ANNOTATION = createAction(new SelectAllAnnotationCommand(qupath.viewerProperty()));

		@ActionMenu("Annotations...>")
		public final Action SEP_5 = ActionTools.createSeparator();
		
		@ActionMenu("Annotations...>Insert into hierarchy")
		@ActionAccelerator("shortcut+shift+i")
		public final Action INSERT_INTO_HIERARCHY = qupath.createHierarchyAction(h -> Commands.insertSelectedObjectsInHierarchy(h));
		@ActionMenu("Annotations...>Resolve hierarchy")
		@ActionAccelerator("shortcut+shift+r")
		public final Action RESOLVE_HIERARCHY = qupath.createImageDataAction(imageData -> Commands.promptToResolveHierarchy(imageData));
		

		@ActionMenu("Annotations...>")
		public final Action SEP_6 = ActionTools.createSeparator();

		@ActionMenu("Annotations...>Rotate annotation")
		@ActionAccelerator("shortcut+shift+alt+r")
		public final Action RIGID_OBJECT_EDITOR = createAction(new RigidObjectEditorCommand(qupath));
		@ActionMenu("Annotations...>Duplicate annotations")
		@ActionAccelerator("shift+d")
		public final Action ANNOTATION_DUPLICATE = qupath.createImageDataAction(imageData -> Commands.duplicateSelectedAnnotations(imageData));
		@ActionMenu("Annotations...>Transfer last annotation")
		@ActionAccelerator("shift+e")
		public final Action TRANSFER_ANNOTATION = createAction(() -> qupath.viewerManager.applyLastAnnotationToActiveViewer());

		@ActionMenu("Annotations...>")
		public final Action SEP_7 = ActionTools.createSeparator();

		@ActionMenu("Annotations...>Expand annotations")
		public final Action EXPAND_ANNOTATIONS = qupath.createPluginAction("Expand annotations", DilateAnnotationPlugin.class, null);
		@ActionMenu("Annotations...>Split annotations")
		public final Action SPLIT_ANNOTATIONS = qupath.createPluginAction("Split annotations", SplitAnnotationsPlugin.class, null);
		@ActionMenu("Annotations...>Remove fragments")
		public final Action REMOVE_FRAGMENTS = qupath.createPluginAction("Remove annotations", RefineAnnotationsPlugin.class, null);
		@ActionMenu("Annotations...>Fill holes")
		public final Action FILL_HOLES = qupath.createPluginAction("Fill holes", FillAnnotationHolesPlugin.class, null);

		@ActionMenu("Annotations...>")
		public final Action SEP_8 = ActionTools.createSeparator();
		
		@ActionMenu("Annotations...>Make inverse")
		public final Action MAKE_INVERSE = qupath.createImageDataAction(imageData -> Commands.makeInverseAnnotation(imageData));
		@ActionMenu("Annotations...>Merge selected")
		public final Action MERGE_SELECTED = qupath.createImageDataAction(imageData -> Commands.mergeSelectedAnnotations(imageData));
		@ActionMenu("Annotations...>Simplify shape")
		public final Action SIMPLIFY_SHAPE = qupath.createImageDataAction(imageData -> Commands.promptToSimplifyShape(imageData, 1.0));

	}
	
	
	@ActionMenu("TMA")
	public class TMAMenuManager {
		
		@ActionMenu("Add...>Add TMA row before")
		public final Action ADD_ROW_BEFORE = createAction(new TMAGridAdd(qupath, TMAAddType.ROW_BEFORE));
		@ActionMenu("Add...>Add TMA row after")
		public final Action ADD_ROW_AFTER = createAction(new TMAGridAdd(qupath, TMAAddType.ROW_AFTER));
		@ActionMenu("Add...>Add TMA column before")
		public final Action ADD_COLUMN_BEFORE = createAction(new TMAGridAdd(qupath, TMAAddType.COLUMN_BEFORE));
		@ActionMenu("Add...>Add TMA column after")
		public final Action ADD_COLUMN_AFTER = createAction(new TMAGridAdd(qupath, TMAAddType.COLUMN_AFTER));
		
		@ActionMenu("Remove...>Remove TMA row")
		public final Action REMOVE_ROW = createAction(new TMAGridRemove(qupath, TMARemoveType.ROW));
		@ActionMenu("Remove...>Remove TMA column")
		public final Action REMOVE_COLUMN = createAction(new TMAGridRemove(qupath, TMARemoveType.COLUMN));

		@ActionMenu("Relabel TMA grid")
		public final Action RELABEL = qupath.createImageDataAction(imageData -> TMAGridRelabel.promptToRelabelTMAGrid(imageData));
		@ActionMenu("Reset TMA metadata")
		public final Action RESET_METADATA = qupath.createImageDataAction(imageData -> Commands.resetTMAMetadata(imageData));
		@ActionMenu("TMA grid summary view")
		public final Action CLEAR_CORES = qupath.createImageDataAction(imageData -> Commands.promptToDeleteObjects(imageData, TMACoreObject.class));
		@ActionMenu("Delete TMA grid")
		public final Action SUMMARY_GRID = createAction(new TMAGridView(qupath));

		public final Action SEP_0 = ActionTools.createSeparator();
		
		@ActionMenu("Find convex hull detections (TMA)")
		public final Action CONVEX_HULL = qupath.createPluginAction("Find convex hull detections (TMA)", FindConvexHullDetectionsPlugin.class, qupath, null);

	}
	
	@ActionMenu("View")
	public class ViewMenuManager {
		
		public final Action SHOW_ANALYSIS_PANEL = actionManager.SHOW_ANALYSIS_PANEL;
		public final Action COMMAND_LIST = actionManager.SHOW_COMMAND_LIST;
		public final Action SEP_0 = ActionTools.createSeparator();
		public final Action BRIGHTNESS_CONTRAST = actionManager.BRIGHTNESS_CONTRAST;
		public final Action SEP_1 = ActionTools.createSeparator();
		public final Action TOGGLE_SYNCHRONIZE_VIEWERS = actionManager.TOGGLE_SYNCHRONIZE_VIEWERS;
		public final Action MATCH_VIEWER_RESOLUTIONS = actionManager.MATCH_VIEWER_RESOLUTIONS;
		
		@ActionMenu("Mini viewers...>Show channel viewer")
		public final Action CHANNEL_VIEWER = createAction(new MiniViewerCommand(qupath, true));
		@ActionMenu("Mini viewers...>Show mini viewer")
		public final Action MINI_VIEWER = createAction(new MiniViewerCommand(qupath, false));
		
		public final Action SEP_2 = ActionTools.createSeparator();
		
		@ActionMenu("Zoom...>400%")
		public final Action ZOOM_400 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 0.25));
		@ActionMenu("Zoom...>100%")
		public final Action ZOOM_100 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 1));
		@ActionMenu("Zoom...>10%")
		public final Action ZOOM_10 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 10));
		@ActionMenu("Zoom...>1%")
		public final Action ZOOM_1 = qupath.createViewerAction(viewer -> Commands.setViewerDownsample(viewer, 100));
		
		public final Action SEP_3 = ActionTools.createSeparator();
		@ActionMenu("Zoom...>Zoom in")
		@ActionIcon(PathIcons.ZOOM_IN)
		@ActionAccelerator("+")
		public final Action ZOOM_IN = createAction(ZoomCommand.createZoomInCommand(qupath.viewerProperty()));
		@ActionMenu("Zoom...>Zoom out")
		@ActionIcon(PathIcons.ZOOM_OUT)
		@ActionAccelerator("-")
		public final Action ZOOM_OUT = createAction(ZoomCommand.createZoomOutCommand(qupath.viewerProperty()));
		@ActionMenu("Zoom...>Zoom to fit")
		public final Action ZOOM_TO_FIT = actionManager.ZOOM_TO_FIT;
				
		@ActionMenu("Rotate image")
		public final Action ROTATE_IMAGE = createAction(new RotateImageCommand(qupath));

		public final Action SEP_4 = ActionTools.createSeparator();
		
		@ActionMenu("Cell display>")
		public final Action SHOW_CELL_BOUNDARIES = actionManager.SHOW_CELL_BOUNDARIES;
		public final Action SHOW_CELL_NUCLEI = actionManager.SHOW_CELL_NUCLEI;
		public final Action SHOW_CELL_BOUNDARIES_AND_NUCLEI = actionManager.SHOW_CELL_BOUNDARIES_AND_NUCLEI;
		public final Action SHOW_CELL_CENTROIDS = actionManager.SHOW_CELL_CENTROIDS;
		
		public final Action SHOW_ANNOTATIONS = actionManager.SHOW_ANNOTATIONS;
		public final Action FILL_ANNOTATIONS = actionManager.FILL_ANNOTATIONS;
		public final Action SHOW_NAMES = actionManager.SHOW_NAMES;
		public final Action SHOW_TMA_GRID = actionManager.SHOW_TMA_GRID;
		public final Action SHOW_TMA_GRID_LABELS = actionManager.SHOW_TMA_GRID_LABELS;
		public final Action SHOW_DETECTIONS = actionManager.SHOW_DETECTIONS;
		public final Action FILL_DETECTIONS = actionManager.FILL_DETECTIONS;

		@ActionMenu("Show object connections")
		public final Action SHOW_CONNECTIONS = createSelectableCommandAction(qupath.getOverlayOptions().showConnectionsProperty());

		public final Action SHOW_PIXEL_CLASSIFICATION = actionManager.SHOW_PIXEL_CLASSIFICATION;
		
		public final Action SEP_5 = ActionTools.createSeparator();
		
		public final Action SHOW_OVERVIEW = actionManager.SHOW_OVERVIEW;
		public final Action SHOW_LOCATION = actionManager.SHOW_LOCATION;
		public final Action SHOW_SCALEBAR = actionManager.SHOW_SCALEBAR;
		public final Action SHOW_GRID = actionManager.SHOW_GRID;
		public final Action GRID_SPACING = actionManager.GRID_SPACING;
		
		public final Action SEP_6 = ActionTools.createSeparator();
		
		public final Action VIEW_TRACKER = actionManager.VIEW_TRACKER;
		public final Action SLIDE_LABEL = createSelectableCommandAction(qupath.slideLabelView.showingProperty());

		public final Action SEP_7 = ActionTools.createSeparator();
		
		@ActionMenu("Show input display")
		public final Action INPUT_DISPLAY = createAction(new ShowInputDisplayCommand(qupath));

		@ActionMenu("Show memory monitor")
		public final Action MEMORY_MONITORY = createAction(new MemoryMonitorCommand(qupath));
		
		@ActionMenu("Show log")
		public final Action SHOW_LOG = actionManager.SHOW_LOG;
		
	}
	
	
	@ActionMenu("Measure")
	public class MeasureMenuManager {
		
		@ActionMenu("Show measurement maps")
		@ActionAccelerator("shortcut+shift+m")
		@ActionDescription("View detection measurements in context using interactive, color-coded maps")
		public final Action MAPS = createAction(new MeasurementMapCommand(qupath));
		
		@ActionMenu("Show measurement manager")
		@ActionDescription("View and optionally delete detection measurements")
		public final Action MANAGER = createAction(new MeasurementManager(qupath));
		
		@ActionMenu("")
		public final Action SEP_1 = ActionTools.createSeparator();
		
		@ActionMenu("Show TMA measurements")		
		@ActionDescription("Show summary measurements for tissue microarray (TMA) cores")
		public final Action TMA = createAction(new SummaryMeasurementTableCommand(qupath, TMACoreObject.class));
		
		@ActionMenu("Show annotation measurements")		
		@ActionDescription("Show summary measurements for annotation objects")
		public final Action ANNOTATIONS = createAction(new SummaryMeasurementTableCommand(qupath, PathAnnotationObject.class));
		
		@ActionMenu("Show detection measurements")		
		@ActionDescription("Show summary measurements for detection objects")
		public final Action DETECTIONS = createAction(new SummaryMeasurementTableCommand(qupath, PathDetectionObject.class));
		
		public final Action SEP_2 = ActionTools.createSeparator();

		@ActionMenu("Export measurements")		
		@ActionDescription("Export summary measurements for multiple images within a project")
		public final Action EXPORT = createAction(new MeasurementExportCommand(qupath));
		
	}
	
	
	@ActionMenu("Help")
	public class HelpMenuManager {

		@ActionMenu("Show setup options")
		public final Action QUPATH_SETUP = createAction(() -> qupath.showSetupDialog());

		@ActionMenu("")
		public final Action SEP_1 = ActionTools.createSeparator();

		@ActionMenu("Documentation (web)")
		public final Action DOCS = createAction(() -> QuPathGUI.launchBrowserWindow(QuPathGUI.URL_DOCS));
		
		@ActionMenu("Demo videos (web)")
		public final Action DEMOS = createAction(() -> QuPathGUI.launchBrowserWindow(QuPathGUI.URL_VIDEOS));

		@ActionMenu("Check for updates (web)")
		public final Action UPDATE = createAction(() -> qupath.checkForUpdate(false));

		public final Action SEP_2 = ActionTools.createSeparator();
		
		@ActionMenu("Cite QuPath (web)")
		public final Action CITE = createAction(() -> QuPathGUI.launchBrowserWindow(QuPathGUI.URL_CITATION));
		
		@ActionMenu("Report bug (web)")
		public final Action BUGS = createAction(() -> QuPathGUI.launchBrowserWindow(QuPathGUI.URL_BUGS));
		
		@ActionMenu("View user forum (web)")
		public final Action FORUM = createAction(() -> QuPathGUI.launchBrowserWindow(QuPathGUI.URL_FORUM));
		
		@ActionMenu("View source code (web)")
		public final Action SOURCE = createAction(() -> QuPathGUI.launchBrowserWindow(QuPathGUI.URL_SOURCE));

		public final Action SEP_3 = ActionTools.createSeparator();

		@ActionMenu("License")
		public final Action LICENSE = createAction(new ShowLicensesCommand(qupath));
		
		@ActionMenu("System info")
		public final Action INFO = createAction(new ShowSystemInfoCommand(qupath));
		
		@ActionMenu("Installed extensions")
		public final Action EXTENSIONS = createAction(new ShowInstalledExtensionsCommand(qupath));
		
	}

}
