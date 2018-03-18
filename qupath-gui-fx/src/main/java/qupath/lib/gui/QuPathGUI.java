/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui;

import java.awt.Desktop;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.Locale.Category;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.action.ActionUtils.ActionTextBehavior;
import org.controlsfx.glyphfont.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.Task;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.SplitPane.Divider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.RotateEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import jfxtras.scene.menu.CirclePopupMenu;
import qupath.lib.algorithms.CoherenceFeaturePlugin;
import qupath.lib.algorithms.HaralickFeaturesPlugin;
import qupath.lib.algorithms.IntensityFeaturesPlugin;
import qupath.lib.algorithms.LocalBinaryPatternsPlugin;
import qupath.lib.algorithms.TilerPlugin;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.SimpleThreadFactory;
import qupath.lib.common.URLTools;
import qupath.lib.gui.commands.AnnotationCombineCommand;
import qupath.lib.gui.commands.BrightnessContrastCommand;
import qupath.lib.gui.commands.CommandListDisplayCommand;
import qupath.lib.gui.commands.CopyViewToClipboardCommand;
import qupath.lib.gui.commands.CountingPanelCommand;
import qupath.lib.gui.commands.EstimateStainVectorsCommand;
import qupath.lib.gui.commands.LoadClassifierCommand;
import qupath.lib.gui.commands.LogViewerCommand;
import qupath.lib.gui.commands.MeasurementManager;
import qupath.lib.gui.commands.MeasurementMapCommand;
import qupath.lib.gui.commands.MiniViewerCommand;
import qupath.lib.gui.commands.OpenCommand;
import qupath.lib.gui.commands.PreferencesCommand;
import qupath.lib.gui.commands.ProjectCloseCommand;
import qupath.lib.gui.commands.ProjectCreateCommand;
import qupath.lib.gui.commands.ProjectExportImageListCommand;
import qupath.lib.gui.commands.ProjectImportImagesCommand;
import qupath.lib.gui.commands.ProjectMetadataEditorCommand;
import qupath.lib.gui.commands.ProjectOpenCommand;
import qupath.lib.gui.commands.ProjectSaveCommand;
import qupath.lib.gui.commands.QuPathSetupCommand;
import qupath.lib.gui.commands.ResetPreferencesCommand;
import qupath.lib.gui.commands.RevertCommand;
import qupath.lib.gui.commands.RigidObjectEditorCommand;
import qupath.lib.gui.commands.RotateImageCommand;
import qupath.lib.gui.commands.SampleScriptLoader;
import qupath.lib.gui.commands.ExportImageRegionCommand;
import qupath.lib.gui.commands.SaveViewCommand;
import qupath.lib.gui.commands.ScriptInterpreterCommand;
import qupath.lib.gui.commands.SerializeImageDataCommand;
import qupath.lib.gui.commands.SetGridSpacingCommand;
import qupath.lib.gui.commands.OpenWebpageCommand;
import qupath.lib.gui.commands.ShowInstalledExtensionsCommand;
import qupath.lib.gui.commands.ShowLicensesCommand;
import qupath.lib.gui.commands.ShowScriptEditorCommand;
import qupath.lib.gui.commands.ShowSystemInfoCommand;
import qupath.lib.gui.commands.TMAGridView;
import qupath.lib.gui.commands.SingleFeatureClassifierCommand;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.commands.TMAAddNote;
import qupath.lib.gui.commands.TMAViewerCommand;
import qupath.lib.gui.commands.TMAGridAdd;
import qupath.lib.gui.commands.TMAGridAdd.TMAAddType;
import qupath.lib.gui.commands.TMAGridRemove.TMARemoveType;
import qupath.lib.gui.commands.TMAGridReset;
import qupath.lib.gui.commands.TMAGridRemove;
import qupath.lib.gui.commands.TMAExporterCommand;
import qupath.lib.gui.commands.TMAScoreImportCommand;
import qupath.lib.gui.commands.ViewTrackerCommand;
import qupath.lib.gui.commands.ViewerSetDownsampleCommand;
import qupath.lib.gui.commands.WorkflowDisplayCommand;
import qupath.lib.gui.commands.ZoomCommand;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.commands.interfaces.PathSelectableCommand;
import qupath.lib.gui.commands.scriptable.DeleteObjectsCommand;
import qupath.lib.gui.commands.scriptable.DeleteSelectedObjectsCommand;
import qupath.lib.gui.commands.scriptable.DetectionsToPointsCommand;
import qupath.lib.gui.commands.scriptable.DuplicateAnnotationCommand;
import qupath.lib.gui.commands.scriptable.InverseObjectCommand;
import qupath.lib.gui.commands.scriptable.MergeSelectedAnnotationsCommand;
import qupath.lib.gui.commands.scriptable.ResetClassificationsCommand;
import qupath.lib.gui.commands.scriptable.ResetSelectionCommand;
import qupath.lib.gui.commands.scriptable.SelectAllAnnotationCommand;
import qupath.lib.gui.commands.scriptable.SelectObjectsByClassCommand;
import qupath.lib.gui.commands.scriptable.SelectObjectsByMeasurementCommand;
import qupath.lib.gui.commands.scriptable.ShapeSimplifierCommand;
import qupath.lib.gui.commands.scriptable.SpecifyAnnotationCommand;
import qupath.lib.gui.commands.scriptable.TMAGridRelabel;
import qupath.lib.gui.commands.selectable.CellDisplaySelectable;
import qupath.lib.gui.commands.selectable.ToolSelectable;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.helpers.CommandFinderTools;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.DisplayHelpers.DialogButton;
import qupath.lib.gui.helpers.dialogs.DialogHelper;
import qupath.lib.gui.helpers.dialogs.DialogHelperFX;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.icons.PathIconFactory.PathIcons;
import qupath.lib.gui.panels.PathAnnotationPanel;
import qupath.lib.gui.panels.PathImageDetailsPanel;
import qupath.lib.gui.panels.PathObjectHierarchyView;
import qupath.lib.gui.panels.PreferencePanel;
import qupath.lib.gui.panels.ProjectBrowser;
import qupath.lib.gui.panels.SelectedMeasurementTableView;
import qupath.lib.gui.panels.SlideLabelView;
import qupath.lib.gui.panels.WorkflowPanel;
import qupath.lib.gui.panels.classify.RandomTrainingRegionSelector;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.viewer.DragDropFileImportListener;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.gui.viewer.ViewerPlusDisplayOptions;
import qupath.lib.gui.viewer.OverlayOptions.CellDisplayMode;
import qupath.lib.gui.viewer.tools.BrushTool;
import qupath.lib.gui.viewer.tools.EllipseTool;
import qupath.lib.gui.viewer.tools.LineTool;
import qupath.lib.gui.viewer.tools.MoveTool;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.gui.viewer.tools.PointsTool;
import qupath.lib.gui.viewer.tools.PolygonTool;
import qupath.lib.gui.viewer.tools.RectangleTool;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.RotatedImageServer;
import qupath.lib.images.stores.DefaultImageRegionStore;
import qupath.lib.images.stores.ImageRegionStore;
import qupath.lib.images.stores.ImageRegionStoreFactory;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.AbstractPluginRunner;
import qupath.lib.plugins.ParameterDialogWrapper;
import qupath.lib.plugins.PathInteractivePlugin;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.PluginRunnerFX;
import qupath.lib.plugins.objects.DilateAnnotationPlugin;
import qupath.lib.plugins.objects.FindConvexHullDetectionsPlugin;
import qupath.lib.plugins.objects.ShapeFeaturesPlugin;
import qupath.lib.plugins.objects.SmoothFeaturesPlugin;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.TranslatableROI;
import qupath.lib.scripting.DefaultScriptEditor;
import qupath.lib.scripting.QPEx;
import qupath.lib.scripting.ScriptEditor;
import qupath.lib.www.URLHelpers;



/**
 * Main GUI for QuPath, written using JavaFX.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathGUI implements ModeWrapper, ImageDataWrapper<BufferedImage>, ViewerManager<QuPathViewerPlus> {
	
	static Logger logger = LoggerFactory.getLogger(QuPathGUI.class);
	
	private static QuPathGUI instance;
	
	private List<ImageDataChangeListener<BufferedImage>> listeners = Collections.synchronizedList(new ArrayList<>());
	
	private ScriptEditor scriptEditor = null;
	
	private String buildString = null;
	private String versionString = null;
	
	/**
	 * Variable, possibly stored in the manifest, indicating the latest commit tag.
	 * This can be used to give some form of automated versioning.
	 */
	private String latestCommitTag = null;
	
	// For development... don't run update check if running from a directory (rather than a Jar)
	private boolean disableAutoUpdateCheck = new File(qupath.lib.gui.QuPathGUI.class.getProtectionDomain().getCodeSource().getLocation().getFile()).isDirectory();
	
	private static ExtensionClassLoader extensionClassLoader = new ExtensionClassLoader();
	private ServiceLoader<QuPathExtension> extensionLoader = ServiceLoader.load(QuPathExtension.class, extensionClassLoader);
//	private static ServiceLoader<QuPathExtension> extensionLoader = ServiceLoader.load(QuPathExtension.class);
	
	public enum GUIActions { OPEN_IMAGE, OPEN_IMAGE_OR_URL, TMA_EXPORT_DATA, SAVE_DATA, SAVE_DATA_AS,
								COPY_VIEW, COPY_WINDOW, ZOOM_IN, ZOOM_OUT, ZOOM_TO_FIT,
								MOVE_TOOL, RECTANGLE_TOOL, ELLIPSE_TOOL, POLYGON_TOOL, BRUSH_TOOL, LINE_TOOL, POINTS_TOOL, WAND_TOOL,
								BRIGHTNESS_CONTRAST,
								SHOW_OVERVIEW, SHOW_LOCATION, SHOW_SCALEBAR, SHOW_GRID, SHOW_ANALYSIS_PANEL,
								SHOW_ANNOTATIONS, FILL_ANNOTATIONS, SHOW_TMA_GRID, SHOW_TMA_GRID_LABELS, SHOW_OBJECTS, FILL_OBJECTS, 
								SPECIFY_ANNOTATION, ANNOTATION_DUPLICATE, GRID_SPACING,
								COUNTING_PANEL, CONVEX_POINTS, USE_SELECTED_COLOR, DETECTIONS_TO_POINTS,
								ROTATE_IMAGE, MINI_VIEWER,
								RIGID_OBJECT_EDITOR, SHOW_COMMAND_LIST,
								TMA_SCORE_IMPORTER, TMA_ADD_NOTE, COLOR_DECONVOLUTION_REFINE, SHOW_LOG, TMA_RELABEL,
								SHOW_CELL_BOUNDARIES, SHOW_CELL_NUCLEI, SHOW_CELL_BOUNDARIES_AND_NUCLEI,
								SUMMARY_TMA, SUMMARY_ANNOTATIONS, SUMMARY_DETECTIONS,
								VIEW_TRACKER, MEASUREMENT_MAP, WORKFLOW_DISPLAY,
								DELETE_SELECTED_OBJECTS, CLEAR_HIERARCHY, CLEAR_DETECTIONS, CLEAR_TMA_CORES, CLEAR_ANNOTATIONS,
								PROJECT_NEW, PROJECT_OPEN, PROJECT_CLOSE, PROJECT_SAVE, PROJECT_IMPORT_IMAGES, PROJECT_EXPORT_IMAGE_LIST, PROJECT_METADATA,
								PREFERENCES, QUPATH_SETUP,
								TRANSFER_ANNOTATION, SELECT_ALL_ANNOTATION, TOGGLE_SYNCHRONIZE_VIEWERS,
								UNDO, REDO
								};
	
	// Modes for input tools
	public enum Modes { MOVE, RECTANGLE, ELLIPSE, LINE, POLYGON, BRUSH, POINTS, WAND }; //, TMA };
	private Modes mode = Modes.MOVE;
	
	// ExecutorServices for single & multiple threads
	private Map<Object, ExecutorService> mapSingleThreadPools = new HashMap<>();
//	private Set<ExecutorService> managedThreadPools = new HashSet<>();
	private ExecutorService poolMultipleThreads = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), new SimpleThreadFactory("qupath-shared-", false));	
	
	private Map<KeyCombination, Action> mapActions = new HashMap<>();
	
	private Map<Modes, Action> modeActions = new HashMap<>();
	private boolean modeSwitchEnabled = true; // Flag whether the mode can be changed or not (e.g. if a command is active that could cause confusion with drawing modes)

	final public static int iconSize = 16;

	private MultiviewManager viewerManager;
	
	private ObjectProperty<Project<BufferedImage>> project = new SimpleObjectProperty<>();
	
	private ProjectBrowser projectBrowser;
	
	/**
	 * Preference panel, which may be used by extensions to add in their on preferences if needed
	 */
	private PreferencePanel prefsPanel;
	
	// Initializing the MenuBar here caused some major trouble (including segfaults) in OSX...
	private MenuBar menuBar;

	private BooleanProperty zoomToFit = new SimpleBooleanProperty(false);
	
	private BorderPane pane; // Main component, to hold toolbar & splitpane
	private Control analysisPanel;
	
	private ViewerPlusDisplayOptions viewerDisplayOptions = new ViewerPlusDisplayOptions();
	private OverlayOptions overlayOptions = new OverlayOptions();
	
	private DefaultImageRegionStore imageRegionStore = ImageRegionStoreFactory.createImageRegionStore(PathPrefs.getTileCacheSizeBytes());

	private ToolBarComponent toolbar; // Top component
	private SplitPane splitPane = new SplitPane(); // Main component

	private ObservableList<PathClass> availablePathClasses = null;

	protected HashMap<GUIActions, Action> actionMap = new HashMap<>();
	
	private Stage stage;
	
	private static DialogHelper standaloneDialogHelper = new DialogHelperFX(); // When there is no parent Window available
	private static Map<Window, DialogHelper> dialogHelpers = new WeakHashMap<>();

	private boolean isStandalone = false;
	private ScriptMenuLoader sharedScriptMenuLoader;
	private ScriptMenuLoader projectScriptMenuLoader;
	
	private DragDropFileImportListener dragAndDrop = new DragDropFileImportListener(this);
	
	private UndoRedoManager undoRedoManager;
	
	public QuPathGUI(final Stage stage) {
		this(stage, null, true);
	}

	
	/**
	 * Create a QuPath instance, optionally initializing it with a path to open.
	 * 
	 * It is also possible to specify that QuPath runs as a standalone application or not.
	 * The practical difference is that, if a standalone application, QuPath may call System.exit(0)
	 * when its window is closed; otherwise, it must not for fear or bringing the host application with it.
	 * 
	 * If QuPath is launched, for example, as an ImageJ plugin then isStandalone should be false.
	 * 
	 * @param path Path of an image, project or data file to open - may be null.
	 * @param isStandalone True if QuPath should be run as a standalone application.
	 */
	public QuPathGUI(final Stage stage, final String path, final boolean isStandalone) {
		super();
		
		updateBuildString();
		
		long startTime = System.currentTimeMillis();
		
		this.stage = stage;
		this.isStandalone = isStandalone;
		
		menuBar = new MenuBar();
		
		// Create preferences panel
		prefsPanel = new PreferencePanel(this);
		
		// Set the number of threads at an early stage...
		AbstractPluginRunner.setNumThreadsRequested(PathPrefs.getNumCommandThreads());
		PathPrefs.numCommandThreadsProperty().addListener(o -> AbstractPluginRunner.setNumThreadsRequested(PathPrefs.getNumCommandThreads()));
		
		// Activate the log at an early stage
		Action actionLog = getAction(GUIActions.SHOW_LOG);
		
		// Turn off the use of ImageIODiskCache (it causes some trouble)
		ImageIO.setUseCache(false);
		
		// Initialize available classes
		initializePathClasses();
		
		logger.trace("Time to tools: {} ms", (System.currentTimeMillis() - startTime));
		
		// Initialize all tools
		initializeTools();

		// Initialize main GUI
//		initializeMainComponent();
		
		// Set this as the current instance
		if (instance == null || instance.getStage() == null || !instance.getStage().isShowing())
			instance = this;
		
		// Ensure the user is notified of any errors from now on
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				DisplayHelpers.showErrorNotification("QuPath exception", e);
				if (actionLog != null)
					actionLog.handle(null);
			}
		});
		
		
		logger.trace("Time to main component: {} ms", (System.currentTimeMillis() - startTime));

		BorderPane pane = new BorderPane();
		pane.setCenter(initializeMainComponent());
		
		logger.trace("Time to menu: {} ms", (System.currentTimeMillis() - startTime));
		
		undoRedoManager = new UndoRedoManager(this);

		initializingMenus.set(true);
		menuBar.setUseSystemMenuBar(true);
		createMenuBar();
		pane.setTop(menuBar);
		
		Scene scene;
		try {
			Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
			scene = new Scene(pane, bounds.getWidth()*0.8, bounds.getHeight()*0.8);
		} catch (Exception e) {
			logger.debug("Unable to set stage size using primary screen {}", Screen.getPrimary());
			scene = new Scene(pane, 1000, 600);
		}
		
		splitPane.setDividerPosition(0, 400/scene.getWidth());
		
		logger.trace("Time to scene: {} ms", (System.currentTimeMillis() - startTime));
		
		stage.setScene(scene);

		// Remove this to only accept drag-and-drop into a viewer
		dragAndDrop.setupTarget(scene);
		
		stage.setOnCloseRequest(e -> {
			
			Set<QuPathViewer> unsavedViewers = new LinkedHashSet<>();
			for (QuPathViewer viewer : viewerManager.getViewers()) {
				if (viewer.getImageData() != null && viewer.getImageData().isChanged())
					unsavedViewers.add(viewer);
			}
			if (!unsavedViewers.isEmpty()) {
				if (unsavedViewers.size() == 1) {
					if (!viewerManager.closeViewer("Quit QuPath", unsavedViewers.iterator().next())) {
						logger.trace("Pressed no to close viewer!");
						e.consume();
						return;
					}
				} else if (!DisplayHelpers.showYesNoDialog("Quit QuPath", "Are you sure you want to quit?\n\nUnsaved changes in " + unsavedViewers.size() + " viewers will be lost.")) {
					logger.trace("Pressed no to quit window!");
					e.consume();
					return;
				}
			}
			// Stop any painter requests
			if (imageRegionStore != null)
				imageRegionStore.close();
			
			// Close any cached file system
			FileSystem fileSystemOld = URLHelpers.getCacheFileSystem();
			if (fileSystemOld != null && fileSystemOld != FileSystems.getDefault()) {
				try {
					fileSystemOld.close();
				} catch (Exception e1) {
					logger.error("Error closing file system", e1);
				}
			}
			
			// Save the PathClasses
			savePathClasses();
			
			// Flush the preferences
			if (!PathPrefs.savePreferences())
				logger.error("Error saving preferences");
			
			// Shut down any pools we know about
			poolMultipleThreads.shutdownNow();
			for (ExecutorService pool : mapSingleThreadPools.values())
				pool.shutdownNow();

			// Shut down all our image servers
			for (QuPathViewer v : getViewers()) {
				if (v.getImageData() != null)
					v.getImageData().getServer().close();
			}

			// Reset the instance
			instance = null;
			
			// Exit if running as a standalone application
			if (isStandalone()) {
				logger.info("Calling Platform.exit();");
				Platform.exit();
				// Something of an extreme option... :/
				// Shouldn't be needed if we shut down everything properly, but here as a backup just in case... 
				// (e.g. if ImageJ is running)
//				logger.info("Calling System.exit(0);");
				System.exit(0);
			}
			
		});
		
		
		logger.debug("Time to display: {} ms", (System.currentTimeMillis() - startTime));
		stage.show();
		logger.trace("Time to finish display: {} ms", (System.currentTimeMillis() - startTime));
		
		// Ensure spacebar presses are noted, irrespective of which component has the focus
		stage.getScene().addEventFilter(KeyEvent.ANY, e -> {
			if (e.getCode() == KeyCode.SPACE) {
				Boolean pressed = null;
				if (e.getEventType() == KeyEvent.KEY_PRESSED)
					pressed = Boolean.TRUE;
				else if (e.getEventType() == KeyEvent.KEY_RELEASED)
					pressed = Boolean.FALSE;
				if (pressed != null) {
					for (QuPathViewer viewer : viewerManager.getOpenViewers()) {
						viewer.setSpaceDown(pressed.booleanValue());
					}
				}
			}
		});
		
		stage.getScene().setOnKeyReleased(e -> {
			// We only seem to need this to mop up shortcuts if the system menu bar is in use (at least on OSX)
			if (e.isConsumed() || e.isShortcutDown() || !(GeneralTools.isMac() && getMenuBar().isUseSystemMenuBar()) || e.getTarget() instanceof TextInputControl)
				return;
			
			for (Entry<KeyCombination, Action> entry : mapActions.entrySet()) {
				if (entry.getKey().match(e)) {
					Action action = entry.getValue();
					if (Boolean.TRUE.equals(action.getProperties().get("Selectable")))
						action.setSelected(!action.isSelected());
					else
						action.handle(new ActionEvent(e.getSource(), e.getTarget()));
					e.consume();
					return;
				}
			}
			
		});
		
		// Install extensions
		refreshExtensions(false);
		
		// Open an image, if required
		if (path != null)
			openImage(path, false, false, false);
		
		// Set the icons
		stage.getIcons().addAll(loadIconList());
		
		
		// Add scripts menu (delayed to here, since it takes a bit longer)
		Menu menuAutomate = getMenu("Automate", false);
		ScriptEditor editor = getScriptEditor();
		sharedScriptMenuLoader = new ScriptMenuLoader("Shared scripts...", PathPrefs.scriptsPathProperty(), (DefaultScriptEditor)editor);
		StringBinding projectScriptsPath = Bindings.createStringBinding(() -> {
			if (project.get() == null)
				return null;
			return getProjectScriptsDirectory(false).getAbsolutePath();
		}, project);
		projectScriptMenuLoader = new ScriptMenuLoader("Project scripts...", projectScriptsPath, (DefaultScriptEditor)editor);
		projectScriptMenuLoader.getMenu().visibleProperty().bind(
				Bindings.isNotNull(project).or(initializingMenus)
				);
		menuAutomate.setOnMenuValidation(e -> {
			sharedScriptMenuLoader.updateMenu();
			projectScriptMenuLoader.updateMenu();
		});
		if (editor instanceof DefaultScriptEditor) {
			addMenuItems(
					menuAutomate,
					null,
					createCommandAction(new SampleScriptLoader(this), "Open sample scripts"),
					projectScriptMenuLoader.getMenu(),
					sharedScriptMenuLoader.getMenu()
					);
		}
		
		// Update action states
		updateProjectActionStates();
		
		// Listen for cache request changes
		PathPrefs.useProjectImageCacheProperty().addListener(v -> updateProjectActionStates());
		
		// Menus should now be complete
		initializingMenus.set(false);
		
		// Update the title
		updateTitle();
		
		// Update display
		// Requesting the style should be enough to make sure it is called...
		logger.info("Selected style: {}", QuPathStyleManager.selectedStyleProperty().get());
				
		long endTime = System.currentTimeMillis();
		logger.debug("Startup time: {} ms", (endTime - startTime));
		
		// Do auto-update check
		if (!disableAutoUpdateCheck)
			checkForUpdate(true);

	}
	
	
	/**
	 * Static method to launch QuPath on the JavaFX Platform thread.
	 * 
	 * This can be used from other applications (e.g. MATLAB).
	 * 
	 * Afterwards, calls to getInstance() will return the QuPath instance.
	 * 
	 * If there is already an instance of QuPath running, this ensures that it is visible - but otherwise does nothing.
	 * 
	 */
	public static void launchQuPath() {
		if (!Platform.isFxApplicationThread()) {
			System.out.println("Requesting QuPath launch in JavaFX thread...");
			logger.info("Requesting QuPath launch in JavaFX thread...");
			new JFXPanel(); // To initialize
			Platform.runLater(() -> launchQuPath());
			logger.info("Request sent");
			System.out.println("Request sent");
			return;
		}
		try {
			if (getInstance() == null){
				System.out.println("Launching new QuPath instance...");
				logger.info("Launching new QuPath instance...");
				Stage stage = new Stage();
				QuPathGUI qupath = new QuPathGUI(stage, (String)null, false);
				qupath.getStage().show();
				System.out.println("Done!");
			} else {
				System.out.println("Trying to show existing QuPath instance...");
				logger.info("Trying to show existing QuPath instance");
				getInstance().getStage().show();
				System.out.println("Done!");
			}
		} catch (Exception e) {
			logger.error("Error lauching QuPath", e);
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Try to launch a browser window for a specified URL.
	 * 
	 * Returns true if this was (as far as we know...) successful, and false otherwise.
	 * 
	 * (Current implementation uses Java AWT, but may change to something more JavaFX-friendly... possibly)
	 * 
	 * @param url
	 * @return
	 */
	public static boolean launchBrowserWindow(final String url) {
		try {
			Desktop.getDesktop().browse(new URI(url));
			return true;
		} catch (Exception e) {
			logger.error("Failed to launch browser window for {}", url, e);
			return false;
		}
	}
	
	
	
//	/**
//	 * Directory containing extensions.
//	 * 
//	 * This can contain any jars - all will be added to the search path when starting QuPath.
//	 * 
//	 * @return
//	 */
//	public File getExtensionDirectory() {
//		// Original version... saved with QuPath, but could lead to permissions woes
//		File currentFile;
//		try {
//			currentFile = new File(QuPathGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI());
//			File dirExtensions = new File(currentFile.getParentFile().getParentFile(), "extensions");
//			return dirExtensions;
//		} catch (URISyntaxException e) {
//			logger.error("Unable to find extensions directory!", e);
//		}
//		return null;
//	}
	
	
	/**
	 * Directory containing extensions.
	 * 
	 * This can contain any jars - all will be added to the search path when starting QuPath.
	 * 
	 * @return
	 */
	public static File getExtensionDirectory() {
		String path = PathPrefs.getExtensionsPath();
		if (path == null || path.trim().length() == 0)
			return null;
		File dir = new File(path);
		if (dir.isDirectory()) {
			return dir;
		}
		return null;
	}
	
	
	/**
	 * Get the default location for extensions.
	 * 
	 * This is platform and user-specific.  It isn't necessarily used (and doesn't necessarily exist).
	 * 
	 * @return
	 */
	private File getDefaultExtensionDirectory() {
		return new File(new File(System.getProperty("user.home"), "QuPath"), "extensions");
	}
	
	
	/**
	 * Get the base directory for the current project, or null if no
	 * project is currently open.
	 * 
	 * @return
	 */
	public File getCurrentProjectDirectory() {
		if (getProject() == null)
			return null;
		return getProject().getBaseDirectory();
	}
	
	
	/**
	 * Get the scripts directory for the current project, or null if no project is open.
	 * 
	 * @param makeDirectory True if the directory should be made (if it doesn't already exist), false otherwise
	 * @return
	 */
	public File getProjectScriptsDirectory(final boolean makeDirectory) {
		return getProjectDirectory("scripts", makeDirectory);
	}
	
	
	/**
	 * Get the classifiers directory for the current project, or null if no project is open.
	 * 
	 * @param makeDirectory True if the directory should be made (if it doesn't already exist), false otherwise
	 * @return
	 */
	public File getProjectClassifierDirectory(final boolean makeDirectory) {
		return getProjectDirectory("classifiers", makeDirectory);
	}
	
	
	/**
	 * Get the data directory for the current project, or null if no project is open.
	 * 
	 * @param makeDirectory True if the directory should be made (if it doesn't already exist), false otherwise
	 * @return
	 */
	public File getProjectDataDirectory(final boolean makeDirectory) {
		return getProjectDirectory("data", makeDirectory);
	}
	
	
	/**
	 * Get the export directory for the current project, or null if no project is open.
	 * 
	 * @param makeDirectory True if the directory should be made (if it doesn't already exist), false otherwise
	 * @return
	 */
	public File getProjectExportDirectory(final boolean makeDirectory) {
		return getProjectDirectory("export", makeDirectory);
	}
	
	
	/**
	 * Get a named directory within the base directory of the current project, or null if no project is open.
	 * 
	 * @param makeDirectory True if the directory should be made (if it doesn't already exist), false otherwise
	 * @return
	 */
	private File getProjectDirectory(final String name, final boolean makeDirectory) {
		File dir = getCurrentProjectDirectory();
		if (dir == null)
			return null;
		dir = new File(dir, name);
		if (makeDirectory && !dir.exists())
			dir.mkdirs();
		return dir;
	}
	
	
	/**
	 * Check if extensions can be installed.
	 * 
	 * Generally, extensions can only be added if running from within a jar.
	 * 
	 * @return
	 */
	public boolean canInstallExtensions() {
		return true;
//		return isRunningJar();
	}
	
//	private boolean isRunningJar() {
//		try {
//			File currentFile = new File(QuPathGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI());
//			return currentFile.getName().toLowerCase().endsWith(".jar");
//		} catch (URISyntaxException e) {
//			logger.error("Error determining whether jar running!", e);
//		}
//		return false;
//	}
	
	
	/**
	 * Check for any updates, showing the new changelog if any updates found.
	 * 
	 * @param isAutoCheck If true, the check will only be performed if the auto-update preferences allow it, 
	 * 					  and the user won't be prompted if no update is available.
	 */
	private void checkForUpdate(final boolean isAutoCheck) {
		
		// Confirm if the user wants us to check for updates
		boolean doAutoUpdateCheck = PathPrefs.doAutoUpdateCheck();
		if (isAutoCheck && !doAutoUpdateCheck)
			return;

		logger.info("Performing update check...");

		// Calculate when we last looked for an update
		long currentTime = System.currentTimeMillis();
		long lastUpdateCheck = PathPrefs.getUserPreferences().getLong("lastUpdateCheck", 0);

		// Don't check run auto-update check again if we already checked within the last hour
		long diffMinutes = (currentTime - lastUpdateCheck) / (60L * 60L * 1000L);
		if (isAutoCheck && diffMinutes < 1)
			return;
		
		// See if we can read the current ChangeLog
		File fileChanges = new File("CHANGELOG.md");
		if (!fileChanges.exists()) {
			logger.warn("No changelog found - will not check for updates");
			if (!isAutoCheck) {
				DisplayHelpers.showErrorMessage("Update check", "Cannot check for updates at this time, sorry");
			}
			return;
		}
		String changeLog = null;
		try {
			changeLog = GeneralTools.readFileAsString(fileChanges.getAbsolutePath());
		} catch (IOException e1) {
			if (!isAutoCheck) {
				DisplayHelpers.showErrorMessage("Update check", "Cannot check for updates at this time, sorry");
			}
			logger.error("Error reading changelog", e1);
			return;
		}
		// Output changelog, if we're tracing...
		logger.trace("Changelog contents:\n{}", changeLog);
		String changeLogCurrent = changeLog;

		// Run the check in a background thread
		createSingleThreadExecutor(this).execute(() -> {
			try {
				// Try to download latest changelog
				URL url = new URL("https://raw.githubusercontent.com/qupath/qupath/master/CHANGELOG.md");
				String changeLogOnline = URLTools.readURLAsString(url, 2000);
				
				// Store last update check time
				PathPrefs.getUserPreferences().putLong("lastUpdateCheck", System.currentTimeMillis());
				
				// Compare the current and online changelogs
				if (compareChangelogHeaders(changeLogCurrent, changeLogOnline)) {
					// If not isAutoCheck, inform user even if there are no updated at this time
					if (!isAutoCheck) {
						Platform.runLater(() -> {
//							Dialog<Void> dialog = new Dialog<>();
//							dialog.setTitle("Update check");
//							dialog.initOwner(getStage());
//							dialog.getDialogPane().setHeaderText("QuPath is up-to-date!");
//							dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
//							dialog.showAndWait();
							DisplayHelpers.showMessageDialog("Update check", "QuPath is up-to-date!");
						});
					}
					return;
				}
				
				// If changelogs are different, notify the user
				showChangelogForUpdate(changeLogOnline);
			} catch (Exception e) {
				// Notify the user if we couldn't read the log
				if (!isAutoCheck) {
					DisplayHelpers.showMessageDialog("Update check", "Unable to check for updates at this time, sorry");
					return;
				}
				logger.debug("Unable to check for updates - {}", e.getLocalizedMessage());
			}
		});
	}
	
	
	/**
	 * Compare two changelogs.
	 * 
	 * In truth, this only checks if they have the same first line.
	 * 
	 * @param changelogOld
	 * @param changelogNew
	 * @return True if the changelogs contain the same first line.
	 */
	private static boolean compareChangelogHeaders(final String changelogOld, final String changelogNew) {
		String[] changesOld = GeneralTools.splitLines(changelogOld.trim());
		String[] changesNew = GeneralTools.splitLines(changelogNew.trim());
		if (changesOld[0].equals(changesNew[0]))
			return true;
		
		// Could try to parse version numbers... but is there any need?
//		Pattern.compile("(\\d+\\.)?(\\d+\\.)?(\\*|\\d+)").matcher(changelogOld);
		
		return false;
	}
	
	
	
	private void showChangelogForUpdate(final String changelog) {
		if (!Platform.isFxApplicationThread()) {
			// Need to be on FX thread
			Platform.runLater(() -> showChangelogForUpdate(changelog));
			return;
		}
		// Show changelog with option to download, or not now
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Update QuPath");
		dialog.initOwner(getStage());
		dialog.setResizable(true);
		ButtonType btDownload = new ButtonType("Download update");
		ButtonType btNotNow = new ButtonType("Not now");
		// Not actually included (for space reasons)
		ButtonType btDoNotRemind = new ButtonType("Do not remind me again");
		
		dialog.getDialogPane().getButtonTypes().addAll(
				btDownload,
				btNotNow
//				btDoNotRemind
				);
		dialog.setHeaderText("A new version of QuPath is available!");
		
		TextArea textArea = new TextArea(changelog);
		textArea.setWrapText(true);
		textArea.setEditable(false);
		
//		BorderPane pane = new BorderPane();
		TitledPane paneChanges = new TitledPane("Changes", textArea);
		paneChanges.setCollapsible(false);
		
		dialog.getDialogPane().setContent(paneChanges);
		Optional<ButtonType> result = dialog.showAndWait();
		if (!result.isPresent())
			return;
		
		if (result.get().equals(btDownload)) {
			String url = "https://github.com/qupath/qupath/releases/latest";
			try {
				DisplayHelpers.browseURI(new URI(url));
			} catch (URISyntaxException e) {
				DisplayHelpers.showErrorNotification("Download", "Unable to open " + url);
			}
		} else if (result.get().equals(btDoNotRemind)) {
			PathPrefs.setDoAutoUpdateCheck(false);
		}
	}
	
	
	
	/**
	 * Keep a record of loaded extensions, both for display and to avoid loading them twice.
	 */
	private static Map<Class<? extends QuPathExtension>, QuPathExtension> loadedExtensions = new HashMap<>();
	
	public Collection<QuPathExtension> getLoadedExtensions() {
		return loadedExtensions.values();
	}
	
	public void refreshExtensions(final boolean showNotification) {
		boolean initializing = initializingMenus.get();
		initializingMenus.set(true);
		
		// Refresh the extensions
		extensionClassLoader.refresh();
		extensionLoader.reload();
		// Sort the extensions by name, to ensure predictable loading order
		// (also, menus are in a better order if ImageJ extension installed before OpenCV extension)
		List<QuPathExtension> extensions = new ArrayList<>();
		extensionLoader.iterator().forEachRemaining(extensions::add);
		Collections.sort(extensions, Comparator.comparing(QuPathExtension::getName));
		for (QuPathExtension extension : extensions) {
			if (!loadedExtensions.containsKey(extension.getClass())) {
				extension.installExtension(this);
				loadedExtensions.put(extension.getClass(), extension);
				if (showNotification)
					DisplayHelpers.showInfoNotification("Extension loaded",  extension.getName());
			}
		}
		// Set the ImageServer to also look on the same search path
		List<ImageServerBuilder<?>> serverBuildersBefore = ImageServerProvider.getInstalledImageServerBuilders();
		ImageServerProvider.setServiceLoader(ServiceLoader.load(ImageServerBuilder.class, extensionClassLoader));
		if (showNotification) {
			// A bit convoluted... but try to show new servers that have been loaded by comparing with the past
			List<String> serverBuilders = serverBuildersBefore.stream().map(s -> s.getName()).collect(Collectors.toList());
			List<String> serverBuildersUpdated = ImageServerProvider.getInstalledImageServerBuilders().stream().map(s -> s.getName()).collect(Collectors.toList());
			serverBuildersUpdated.removeAll(serverBuilders);
			for (String builderName : serverBuildersUpdated) {
				DisplayHelpers.showInfoNotification("Image server loaded",  builderName);
			}
		}
		
		initializingMenus.set(initializing);
	}
	
	/**
	 * Install extensions while QuPath is running.
	 * 
	 * @param files A collection of jar files for installation.
	 */
	public void installExtensions(final Collection<File> files) {
		if (files.isEmpty()) {
			logger.debug("No extensions to install!");
			return;
		}
		if (!canInstallExtensions()) {
			DisplayHelpers.showErrorMessage("Install extension", "Cannot install extensions when not running QuPath from a .jar file (application), sorry!");
			return;
		}
		File dir = getExtensionDirectory();
		if (dir == null) {
			logger.info("No extension directory found!");
			// Prompt to create an extensions directory
			File dirDefault = getDefaultExtensionDirectory();
			String msg;
			if (dirDefault.exists()) {
				msg = "An directory already exists at " + dirDefault.getAbsolutePath() + 
						"\n\nDo you want to use this default, or specify another directory?";
			} else {
				msg = "QuPath can automatically create one at\n" + dirDefault.getAbsolutePath() + 
						"\n\nDo you want to use this default, or specify another directory?";
			}
			
			Dialog<ButtonType> dialog = new Dialog<>();
			dialog.initOwner(getStage());

			ButtonType btUseDefault = new ButtonType("Use default", ButtonData.YES);
			ButtonType btChooseDirectory = new ButtonType("Choose directory", ButtonData.NO);
			ButtonType btCancel = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
			dialog.getDialogPane().getButtonTypes().setAll(btUseDefault, btChooseDirectory, btCancel);

			dialog.setHeaderText(null);
			dialog.setTitle("Choose extensions directory");
			dialog.setContentText("No extensions directory is set.\n\n" + msg);
			Optional<ButtonType> result = dialog.showAndWait();
			if (!result.isPresent() || result.get() == btCancel) {
				logger.info("No extension directory set - extensions not installed");
				return;
			}
			if (result.get() == btUseDefault) {
				if (!dirDefault.exists() && !dirDefault.mkdirs()) {
					DisplayHelpers.showErrorMessage("Extension error", "Unable to create directory at \n" + dirDefault.getAbsolutePath());
					return;
				}
				dir = dirDefault;
				PathPrefs.setExtensionsPath(dir.getAbsolutePath());
			} else {
				dir = getDialogHelper().promptForDirectory(dirDefault);
				if (dir == null) {
					logger.info("No extension directory set - extensions not installed");
					return;
				}
			}
		}
		// Create directory if we need it
		if (!dir.exists())
			dir.mkdir();
		
		// Copy all files into extensions directory
		Path dest = dir.toPath();
		for (File file : files) {
			Path source = file.toPath();
			Path destination = dest.resolve(source.getFileName());
			if (destination.toFile().exists()) {
				// It would be better to check how many files will be overwritten in one go,
				// but this should be a pretty rare occurrence
				if (!DisplayHelpers.showConfirmDialog("Install extension", "Overwrite " + destination.toFile().getName() + "?\n\nYou will have to restart QuPath to see the updates."))
					return;
			}
			try {
				Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				DisplayHelpers.showErrorMessage("Extension error", file + "\ncould not be copied, sorry");
				logger.error("Could not copy file {}", file, e);
				return;
			}
		}
		refreshExtensions(true);
	}
	
	
	/**
	 * Initialize available PathClasses, either from saved list or defaults
	 */
	private void initializePathClasses() {
		availablePathClasses = FXCollections.observableArrayList();
		List<PathClass> pathClasses = new ArrayList<>();		
		try {
			pathClasses.addAll(loadPathClasses());			
		} catch (Exception e) {
			logger.error("Unable to load PathClasses", e);
		}
		if (pathClasses.isEmpty())
			resetAvailablePathClasses();
		else
			availablePathClasses.setAll(pathClasses);
		availablePathClasses.addListener((Change<? extends PathClass> c) -> {
			Project<?> project = getProject();
			if (project != null) {
				// Write the project, if necessary
				if (project.setPathClasses(c.getList()))
					ProjectIO.writeProject(project);
			}
		});
	}
	
	
	/**
	 * Populate the availablePathClasses with a default list.
	 */
	public void resetAvailablePathClasses() {
		List<PathClass> pathClasses = new ArrayList<>(); 
		pathClasses.add(PathClassFactory.getPathClassUnclassified());
		
		pathClasses.add(PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.TUMOR));
		pathClasses.add(PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.STROMA));
		pathClasses.add(PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.IMMUNE_CELLS));
		pathClasses.add(PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.NECROSIS));
		pathClasses.add(PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.OTHER));
		pathClasses.add(PathClassFactory.getDefaultPathClass(PathClassFactory.PathClasses.WHITESPACE));
		
		if (availablePathClasses == null)
			availablePathClasses = FXCollections.observableArrayList(pathClasses);
		else
			availablePathClasses.setAll(pathClasses);
	}
	
	/**
	 * Load PathClasses from preferences.
	 * Note that this also sets the color of any PathClass that is loads,
	 * and is really only intended for use when initializing.
	 * 
	 * @return
	 */
	private List<PathClass> loadPathClasses() {
		byte[] bytes = PathPrefs.getUserPreferences().getByteArray("defaultPathClasses", null);
		if (bytes == null || bytes.length == 0)
			return Collections.emptyList();
		ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		try (ObjectInputStream in = new ObjectInputStream(stream)) {
			List<PathClass> pathClassesOriginal = (List<PathClass>)in.readObject();
			List<PathClass> pathClasses = new ArrayList<>();
			for (PathClass pathClass : pathClassesOriginal) {
				PathClass singleton = PathClassFactory.getSingletonPathClass(pathClass);
				// Ensure the color is set
				if (singleton != null && pathClass.getColor() != null)
					singleton.setColor(pathClass.getColor());
				pathClasses.add(singleton);
			}
			return pathClasses;
		} catch (Exception e) {
			logger.error("Error loading classes", e);
			return Collections.emptyList();
		}
	}

	
	/**
	 * Show a dialog requesting setup parameters
	 * 
	 * @return
	 */
	public boolean showSetupDialog() {
		// Show a setup message
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("QuPath setup");
		dialog.initOwner(getStage());

		// Try to get an image to display
		Image img = loadIcon(128);
		BorderPane pane = new BorderPane();
		if (img != null) {
			StackPane imagePane = new StackPane(new ImageView(img));
			imagePane.setPadding(new Insets(10, 10, 10, 10));
			pane.setLeft(imagePane);
		}

		Map<String, Locale> localeMap = Arrays.stream(Locale.getAvailableLocales()).collect(Collectors.toMap(l -> l.getDisplayName(Locale.US), l -> l));
		localeMap.remove("");
		List<String> localeList = new ArrayList<>(localeMap.keySet());
		Collections.sort(localeList);
		
		long maxMemoryMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
		String maxMemoryString = String.format("Current maximum memory is %.2f GB.", maxMemoryMB/1024.0);
		
		ParameterList paramsSetup = new ParameterList()
				.addTitleParameter("Memory")
				.addEmptyParameter("memoryString", "Set the maximum memory used by QuPath, or -1 to use the default.")
				.addEmptyParameter("memoryString2", maxMemoryString);

		boolean lowMemory = maxMemoryMB < 1024*6;
		if (lowMemory) {
			paramsSetup.addEmptyParameter("memoryStringWarning",
					"It is suggested to increase the memory limit to approximately\nhalf of the RAM available on your computer."
					);
		}

//				.addEmptyParameter("memoryString2", "Current ")
		paramsSetup.addDoubleParameter("maxMemoryGB", "Maximum memory (GB)", Math.ceil(maxMemoryMB/1024.0), null, "Set the maximum memory for QuPath - considering using approximately half the total RAM for the system")
				.addTitleParameter("Region")
				.addEmptyParameter("localeString", "Set the region for QuPath to use for displaying numbers and messages.")
				.addEmptyParameter("localeString2", "Note: It is highly recommended to keep the default (English, US) region settings.")
				.addEmptyParameter("localeString3", "Support for regions that use different number formatting (e.g. commas as decimal marks)\nis still experimental, and may give unexpected results.")
				.addChoiceParameter("localeFormatting", "Numbers & dates", Locale.getDefault(Category.FORMAT).getDisplayName(), localeList, "Choose region settings used to format numbers and dates")
				.addChoiceParameter("localeDisplay", "Messages", Locale.getDefault(Category.DISPLAY).getDisplayName(), localeList, "Choose region settings used for other formatting, e.g. in dialog boxes")
				.addTitleParameter("Updates")
				.addBooleanParameter("checkForUpdates", "Check for updates on startup (recommended)", PathPrefs.doAutoUpdateCheck(), "Specify whether to automatically prompt to download the latest QuPath on startup (required internet connection)")	
				;

		ParameterPanelFX parameterPanel = new ParameterPanelFX(paramsSetup);
		pane.setCenter(parameterPanel.getPane());
		
		Label labelMemory = new Label("You will need to restart QuPath for memory changes to take effect");
		labelMemory.setMaxWidth(Double.MAX_VALUE);
		labelMemory.setAlignment(Pos.CENTER);
		labelMemory.setFont(Font.font("Arial"));
		labelMemory.setStyle("-fx-font-weight: bold;");
		labelMemory.setPadding(new Insets(10, 10, 10, 10));
		pane.setBottom(labelMemory);
		
//		dialog.initStyle(StageStyle.UNDECORATED);
		dialog.getDialogPane().setContent(pane);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);

		Optional<ButtonType> result = dialog.showAndWait();
		if (!result.isPresent() || !ButtonType.APPLY.equals(result.get()))
			return false;
		
		Locale localeFormatting = localeMap.get(paramsSetup.getChoiceParameterValue("localeFormatting"));
		Locale localeDisplay = localeMap.get(paramsSetup.getChoiceParameterValue("localeDisplay"));
		
		PathPrefs.setDefaultLocale(Category.FORMAT, localeFormatting);
		PathPrefs.setDefaultLocale(Category.DISPLAY, localeDisplay);
		
		PathPrefs.setDoAutoUpdateCheck(paramsSetup.getBooleanParameterValue("checkForUpdates"));
		
		if (PathPrefs.hasJavaPreferences()) {
			int maxMemorySpecifiedMB = (int)(paramsSetup.getDoubleParameterValue("maxMemoryGB") * 1024 + 0.5);
			if (maxMemorySpecifiedMB > 512) {
				PathPrefs.maxMemoryMBProperty().set(maxMemorySpecifiedMB);
			} else {
				if (maxMemorySpecifiedMB >= 0)
					DisplayHelpers.showErrorNotification("Max memory setting", "Specified maximum memory setting too low - will reset to default");
				PathPrefs.maxMemoryMBProperty().set(-1);
			}
		} else {
			DisplayHelpers.showWarningNotification("Max memory", "Cannot set maximum memory preferences");
		}
		
		// Try to update display
		if (getStage() != null && getStage().isShowing())
			updateListsAndTables(getStage().getScene().getRoot());
		
		return true;
	}
	
	/**
	 * Make an effort at updating all the trees, tables or lists that we can find.
	 * 
	 * @param parent
	 */
	private static void updateListsAndTables(final Parent parent) {
		if (parent == null)
			return;
		for (Node child : parent.getChildrenUnmodifiable()) {
			if (child instanceof TreeView<?>)
				((TreeView)child).refresh();
			else if (child instanceof ListView<?>)
				((ListView)child).refresh();
			else if (child instanceof TableView<?>)
				((TableView)child).refresh();
			else if (child instanceof TreeTableView<?>)
				((TreeTableView)child).refresh();
			else if (child instanceof Parent)
				updateListsAndTables((Parent)child);
		}
	}
	
	
	/**
	 * Save available PathClasses to preferences.
	 */
	private void savePathClasses() {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(stream)) {
			List<PathClass> pathClasses = new ArrayList<>(availablePathClasses);
			out.writeObject(pathClasses);
			out.flush();
		} catch (IOException e) {
			logger.error("Error saving classes", e);
		}
		byte[] bytes = stream.toByteArray();
		PathPrefs.getUserPreferences().putByteArray("defaultPathClasses", bytes);
	}
	
	
	
	private BorderPane initializeMainComponent() {
		
		// Create a reasonably-sized viewer
		QuPathViewerPlus viewer = new QuPathViewerPlus(null, imageRegionStore, overlayOptions, viewerDisplayOptions);
//		viewer.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		
//		Dimension viewerSize = Toolkit.getDefaultToolkit().getScreenSize();
//		viewerSize.width = (int)Math.max(viewerSize.width * 0.5, viewer.getPreferredSize().width);
//		viewerSize.height = (int)Math.max(viewerSize.height * 0.5, viewer.getPreferredSize().height);
//		viewer.setPreferredSize(viewerSize);

		// Add analysis panel & viewer to split pane
		viewerManager = new MultiviewManager(viewer);
		analysisPanel = createAnalysisPanel();
		analysisPanel.setMinWidth(300);
		analysisPanel.setPrefWidth(400);
		splitPane.setMinWidth(analysisPanel.getMinWidth() + 200);
		splitPane.setPrefWidth(analysisPanel.getPrefWidth() + 200);
		SplitPane.setResizableWithParent(analysisPanel, Boolean.FALSE);
		
//		paneCommands.setRight(cbPin);
		
		Node paneViewer = CommandFinderTools.createCommandFinderPane(this, viewerManager.getNode(), PathPrefs.commandBarDisplayProperty());
//		paneViewer.setTop(tfCommands);
//		paneViewer.setCenter(viewerManager.getNode());
		splitPane.getItems().addAll(analysisPanel, paneViewer);
//		splitPane.getItems().addAll(viewerManager.getComponent());
		SplitPane.setResizableWithParent(viewerManager.getNode(), Boolean.TRUE);
		
		pane = new BorderPane();
		pane.setCenter(splitPane);
		toolbar = new ToolBarComponent(this);
		pane.setTop(toolbar.getComponent());
		
		setInitialLocationAndMagnification(getViewer());

		// Prepare the viewer
		setupViewer(viewerManager.getActiveViewer());

		// Ensure the mode is set
		setMode(getMode());
		
		
		return pane;
	}

	
	/**
	 * Get access to the image region store used for requesting images indirectly.
	 * 
	 * @return
	 */
	public ImageRegionStore<BufferedImage> getImageRegionStore() {
		return imageRegionStore;
	}
	
	
	
	/**
	 * Try to load icons, i.e. images of various sizes that could be sensible icons... here sacrificing elegance in an effort to make it work
	 * 
	 * @return
	 */
	List<Image> loadIconList() {
		try {
			List<Image> icons = new ArrayList<>();
			for (int i : new int[]{16, 32, 48, 64, 128, 256, 512}) {
				Image icon = loadIcon(i);
				if (icon != null)
					icons.add(icon);
			}
			if (!icons.isEmpty())
				return icons;
		} catch (Exception e) {
			logger.warn("Unable to load icons");
		}
		return null;
	}
	
	private Image loadIcon(int size) {
		String path = "icons/QuPath_" + size + ".png";
		try (InputStream stream = getClassLoader().getResourceAsStream(path)) {
			if (stream != null) {
				BufferedImage img = ImageIO.read(stream);
				if (img != null)
					return SwingFXUtils.toFXImage(img, null);
			}
		} catch (IOException e) {
			logger.error("Unable to read icon from " + path);
		}
		return null;
	}
	
	
	/**
	 * Returns true if this is a standalone QuPathGUI instance, as flagged during startup.
	 * 
	 * @return
	 */
	public boolean isStandalone() {
		return isStandalone;
	}
	
	
	@Override
	public List<QuPathViewerPlus> getViewers() {
		if (viewerManager == null)
			return Collections.emptyList();
		return viewerManager.getViewers();
	}
	
	
	@Override
	public QuPathViewerPlus getViewer() {
		return viewerManager == null ? null : viewerManager.getActiveViewer();
	}
	
	
	public static QuPathGUI getInstance() {
		return instance;
	}
	
	
	void activateTools(final QuPathViewerPlus viewer) {
		if (viewer != null)
			viewer.setMode(getMode());		
//		logger.debug("Tools activated for {}", viewer);
	}
	
	
	void deactivateTools(final QuPathViewerPlus viewer) {
		viewer.setMode(null);
//		// Deregister tools
//		for (Entry<Modes, PathTool> entry : tools.entrySet())
//			entry.getValue().deregisterTool(viewer);
//		logger.debug("Tools deactivated for {}", viewer);
	}
	
	
	
	public static ClassLoader getClassLoader() {
		return extensionClassLoader;
	}
	
	
	/**
	 * Get a reference to the default drag & drop listener, so this may be added to additional windows if needed.
	 * 
	 * @return
	 */
	public DragDropFileImportListener getDefaultDragDropListener() {
		return dragAndDrop;
	}
	
	
	
	void setupViewer(final QuPathViewerPlus viewer) {
		
		viewer.setFocusable(true);
		
		// Update active viewer as required
		viewer.getView().focusedProperty().addListener((e, f, nowFocussed) -> {
			if (nowFocussed) {
				viewerManager.setActiveViewer(viewer);
			}
		});
		
		viewer.getView().addEventFilter(MouseEvent.MOUSE_PRESSED, e -> viewer.getView().requestFocus());

		viewer.zoomToFitProperty().bind(zoomToFit);
		
//		viewer.getCanvas().focusedProperty().addListener((e, f, nowFocussed) -> {
//			if (nowFocussed)
//				viewerManager.setActiveViewer(viewer);
//		});
		
		
		// Register tools
		viewer.registerTools(tools);
		
		// Create popup menu
		setViewerPopupMenu(viewer);
		
		
		viewer.getView().widthProperty().addListener((e, f, g) -> {
			if (viewer.getZoomToFit())
				updateMagnificationString();
		});
		viewer.getView().heightProperty().addListener((e, f, g) -> {
			if (viewer.getZoomToFit())
				updateMagnificationString();
		});
		
//		viewer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(new KeyCodeCombination('+'), "zoomIn");
//		viewer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(new KeyCodeCombination('='), "zoomIn");
//		viewer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(new KeyCodeCombination('-'), "zoomOut");
//		viewer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(new KeyCodeCombination('_'), "zoomOut");
//		viewer.getActionMap().put("zoomIn", getAction(GUIActions.ZOOM_IN));
//		viewer.getActionMap().put("zoomOut", getAction(GUIActions.ZOOM_OUT));
		
		// Enable drag and drop
		dragAndDrop.setupTarget(viewer.getView());
		
		
		
		
		// Listen to the scroll wheel
		viewer.getView().setOnScroll(e -> {
			if (viewer == viewerManager.getActiveViewer() || !viewerManager.getSynchronizeViewers()) {
				double scrollUnits = e.getDeltaY() * PathPrefs.getScaledScrollSpeed();
				// Use shift down to adjust opacity
				if (e.isShortcutDown()) {
					OverlayOptions options = viewer.getOverlayOptions();
					options.setOpacity((float)(options.getOpacity() + scrollUnits * 0.001));
					return;
				}
				
				if (PathPrefs.getInvertScrolling())
					scrollUnits = -scrollUnits;
				double newDownsampleFactor = viewer.getDownsampleFactor() * Math.pow(viewer.getDefaultZoomFactor(), scrollUnits);
				newDownsampleFactor = Math.min(viewer.getMaxDownsample(), Math.max(newDownsampleFactor, viewer.getMinDownsample()));
				viewer.setDownsampleFactor(newDownsampleFactor, e.getX(), e.getY());
			}
		});
		
		
		viewer.getView().addEventFilter(RotateEvent.ANY, e -> {
			if (!PathPrefs.getUseRotateGestures())
				return;
//			logger.debug("Rotating: " + e.getAngle());
			viewer.setRotation(viewer.getRotation() + Math.toRadians(e.getAngle()));
			e.consume();
		});

		viewer.getView().addEventFilter(ZoomEvent.ANY, e -> {
			if (!PathPrefs.getUseZoomGestures())
				return;
			double zoomFactor = e.getZoomFactor();
			if (Double.isNaN(zoomFactor))
				return;
			
			logger.debug("Zooming: " + e.getZoomFactor() + " (" + e.getTotalZoomFactor() + ")");
			viewer.setDownsampleFactor(viewer.getDownsampleFactor() / zoomFactor, e.getX(), e.getY());
			e.consume();
		});
		
		viewer.getView().addEventFilter(ScrollEvent.ANY, e -> {
			if (!PathPrefs.getUseScrollGestures() || e.isShiftDown() || e.isShortcutDown())
				return;
			// TODO: Note: When e.isInertia() == TRUE on OSX, the results are quite annoyingly 'choppy', with 0 x,y movements interspersed with 'true' movements
//			logger.debug("Delta: " + e.getDeltaX() + ", " + e.getDeltaY() + " - " + e.isInertia());
			
			double dx = e.getDeltaX() * viewer.getDownsampleFactor();
			double dy = e.getDeltaY() * viewer.getDownsampleFactor();
			
			if (PathPrefs.getInvertScrolling()) {
				dx = -dx;
				dy = -dy;
			}
			
			// Handle rotation
			if (viewer.isRotated()) {
				double cosTheta = Math.cos(-viewer.getRotation());
				double sinTheta = Math.sin(-viewer.getRotation());
				double dx2 = cosTheta*dx - sinTheta*dy;
				double dy2 = sinTheta*dx + cosTheta*dy;
				dx = dx2;
				dy = dy2;
			}
			
			viewer.setCenterPixelLocation(
					viewer.getCenterPixelX() - dx,
					viewer.getCenterPixelY() - dy);
			e.consume();
		});
		
		
		viewer.getView().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (!e.isConsumed()) {
				PathObject pathObject = viewer.getSelectedObject();
				if (pathObject instanceof TMACoreObject) {
					TMACoreObject core = (TMACoreObject)pathObject;
					if (e.getCode() == KeyCode.ENTER) {
						getAction(GUIActions.TMA_ADD_NOTE).handle(new ActionEvent(e.getSource(), e.getTarget()));
						e.consume();
					} else if (e.getCode() == KeyCode.BACK_SPACE) {
						core.setMissing(!core.isMissing());
						viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(core));
						e.consume();
					}
				} else if (pathObject instanceof PathAnnotationObject) {
					if (e.getCode() == KeyCode.ENTER) {
						PathAnnotationPanel.promptToSetActiveAnnotationProperties(viewer.getHierarchy());
						e.consume();
					}
				}
			}
		});
		

	}
	
	
	
	
	
	
	private void setViewerPopupMenu(final QuPathViewerPlus viewer) {
		
		final ContextMenu popup = new ContextMenu();
		
		MenuItem miAddRow = new MenuItem("Add row");
		miAddRow.setOnAction(e -> viewerManager.addRow(viewer));
		MenuItem miAddColumn = new MenuItem("Add column");
		miAddColumn.setOnAction(e -> viewerManager.addColumn(viewer));
		
		MenuItem miRemoveRow = new MenuItem("Remove row");
		miRemoveRow.setOnAction(e -> viewerManager.removeViewerRow(viewer));
		MenuItem miRemoveColumn = new MenuItem("Remove column");
		miRemoveColumn.setOnAction(e -> viewerManager.removeViewerColumn(viewer));

		MenuItem miCloseViewer = new MenuItem("Close viewer");
		miCloseViewer.setOnAction(e -> {
			viewerManager.closeViewer(viewer);
//				viewerManager.removeViewer(viewer);
		});
		MenuItem miResizeGrid = new MenuItem("Reset grid size");
		miResizeGrid.setOnAction(e -> {
				viewerManager.resetGridSize();
		});
		MenuItem miToggleSync = getActionCheckBoxMenuItem(GUIActions.TOGGLE_SYNCHRONIZE_VIEWERS, null);
		Menu menuMultiview = createMenu(
				"Multi-view",
				miToggleSync,
				miCloseViewer,
				null,
				miResizeGrid,
				null,
//				miSplitQuadrants,
//				null,
				miAddRow,
				miAddColumn,
				null,
				miRemoveRow,
				miRemoveColumn
				);
		
		Menu menuView = createMenu(
				"Display",
				getActionCheckBoxMenuItem(GUIActions.SHOW_ANALYSIS_PANEL, null),
				getAction(GUIActions.BRIGHTNESS_CONTRAST),
				null,
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 0.25), "400%"),
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 1), "100%"),
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 2), "50%"),
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 10), "10%"),
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 100), "1%"),
				null,
				getAction(GUIActions.ZOOM_IN),
				getAction(GUIActions.ZOOM_OUT),
				getActionCheckBoxMenuItem(GUIActions.ZOOM_TO_FIT, null)
				);
		
		Menu menuTools = createMenu(
				"Set tool",
				getActionCheckBoxMenuItem(GUIActions.MOVE_TOOL, null),
				getActionCheckBoxMenuItem(GUIActions.RECTANGLE_TOOL, null),
				getActionCheckBoxMenuItem(GUIActions.ELLIPSE_TOOL, null),
				getActionCheckBoxMenuItem(GUIActions.LINE_TOOL, null),
				getActionCheckBoxMenuItem(GUIActions.POLYGON_TOOL, null),
				getActionCheckBoxMenuItem(GUIActions.BRUSH_TOOL, null),
				getActionCheckBoxMenuItem(GUIActions.POINTS_TOOL, null),
				getActionCheckBoxMenuItem(GUIActions.WAND_TOOL, null)
				);

		
		// Add annotation options
		Menu menuCombine = createMenu(
				"Annotations",
				createCommandAction(new AnnotationCombineCommand(viewer, PathROIToolsAwt.CombineOp.ADD), "Merge selected annotations"),
				createCommandAction(new AnnotationCombineCommand(viewer, PathROIToolsAwt.CombineOp.SUBTRACT), "Subtract selected annotations"), // TODO: Make this less ambiguous!
				createCommandAction(new AnnotationCombineCommand(viewer, PathROIToolsAwt.CombineOp.INTERSECT), "Intersect selected annotations")
				);
		
		// Handle awkward 'TMA core missing' option
		CheckMenuItem miTMAValid = new CheckMenuItem("Set core valid");
		miTMAValid.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), false));
		CheckMenuItem miTMAMissing = new CheckMenuItem("Set core missing");
		miTMAMissing.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), true));
		
		Menu menuTMA = new Menu("TMA");
		addMenuItems(
				menuTMA,
				miTMAValid,
				miTMAMissing,
				null,
				getAction(GUIActions.TMA_ADD_NOTE),
				null,
				createMenu(
						"Add",
					createCommandAction(new TMAGridAdd(this, TMAAddType.ROW_BEFORE), "Add TMA row before"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.ROW_AFTER), "Add TMA row after"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.COLUMN_BEFORE), "Add TMA column before"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.COLUMN_AFTER), "Add TMA column after")
					),
				createMenu(
						"Remove",
					createCommandAction(new TMAGridRemove(this, TMARemoveType.ROW), "Remove TMA row"),
					createCommandAction(new TMAGridRemove(this, TMARemoveType.COLUMN), "Remove TMA column")
					),
				createCommandAction(new TMAGridRelabel(this), "Relabel TMA grid")
				);
		
		
		// Create an empty placeholder menu
		Menu menuSetClass = createMenu("Set class");
		
		
		CheckMenuItem miLockAnnotations = new CheckMenuItem("Lock");
		CheckMenuItem miUnlockAnnotations = new CheckMenuItem("Unlock");
		miLockAnnotations.setOnAction(e -> setSelectedAnnotationLock(viewer.getHierarchy(), true));
		miUnlockAnnotations.setOnAction(e -> setSelectedAnnotationLock(viewer.getHierarchy(), false));
		menuCombine.getItems().addAll(0, Arrays.asList(miLockAnnotations, miUnlockAnnotations, new SeparatorMenuItem()));
		
//		CheckMenuItem miTMAValid = new CheckMenuItem("Set core valid");
//		miTMAValid.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), false));
//		CheckMenuItem miTMAMissing = new CheckMenuItem("Set core missing");
//		miTMAMissing.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), true));
		
		
		Menu menuCells = createMenu(
				"Cells",
				createRadioMenuItem(getAction(GUIActions.SHOW_CELL_BOUNDARIES_AND_NUCLEI), null),
				createRadioMenuItem(getAction(GUIActions.SHOW_CELL_NUCLEI), null),
				createRadioMenuItem(getAction(GUIActions.SHOW_CELL_BOUNDARIES), null)
				);

		
		
		MenuItem miClearSelectedObjects = new MenuItem("Delete object");
		miClearSelectedObjects.setOnAction(e -> {
			PathObjectHierarchy hierarchy = viewer.getHierarchy();
			if (hierarchy == null)
				return;
			if (hierarchy.getSelectionModel().singleSelection()) {
				DisplayHelpers.promptToRemoveSelectedObject(hierarchy.getSelectionModel().getSelectedObject(), hierarchy);
			} else {
				DisplayHelpers.promptToClearAllSelectedObjects(viewer.getImageData());
			}
		});
		
		
		SeparatorMenuItem topSeparator = new SeparatorMenuItem();
		popup.setOnShowing(e -> {
			// Check if we have any cells
			ImageData<?> imageData = viewer.getImageData();
			if (imageData == null)
				menuCells.setVisible(false);
			else
				menuCells.setVisible(!imageData.getHierarchy().getObjects(null, PathCellObject.class).isEmpty());
			
			
			// Check what to show for TMA cores or annotations
			PathObject pathObject = viewer.getSelectedObject();
			menuTMA.setVisible(false);
			if (pathObject instanceof TMACoreObject) {
				boolean isMissing = ((TMACoreObject)pathObject).isMissing();
				miTMAValid.setSelected(!isMissing);
				miTMAMissing.setSelected(isMissing);
				menuTMA.setVisible(true);
			} else if (pathObject instanceof PathAnnotationObject) {
				boolean isLocked = ((PathAnnotationObject)pathObject).isLocked();
				miLockAnnotations.setSelected(isLocked);
				miUnlockAnnotations.setSelected(!isLocked);
			}
			
			// Add clear objects option if we have more than one non-TMA object
			if (imageData == null || imageData.getHierarchy().getSelectionModel().noSelection() || imageData.getHierarchy().getSelectionModel().getSelectedObject() instanceof TMACoreObject)
				miClearSelectedObjects.setVisible(false);
			else {
				if (imageData.getHierarchy().getSelectionModel().singleSelection()) {
					miClearSelectedObjects.setText("Delete object");
					miClearSelectedObjects.setVisible(true);
				} else {
					miClearSelectedObjects.setText("Delete objects");
					miClearSelectedObjects.setVisible(true);					
				}
			}
			
			updateSetAnnotationPathClassMenu(menuSetClass, viewer);
			menuCombine.setVisible(pathObject instanceof PathAnnotationObject);
			topSeparator.setVisible(pathObject instanceof PathAnnotationObject || pathObject instanceof TMACoreObject);
			// Occasionally, the newly-visible top part of a popup menu can have the wrong size?
			popup.setWidth(popup.getPrefWidth());
		});
		
		
//		popup.add(menuClassify);
		popup.getItems().addAll(
				miClearSelectedObjects,
				menuTMA,
				menuSetClass,
				menuCombine,
				topSeparator,
				menuMultiview,
				menuCells,
				menuView,
				menuTools
				);
		
		popup.setAutoHide(true);
		
		// Enable circle pop-up for quick classification on right-click
		CirclePopupMenu circlePopup = new CirclePopupMenu(viewer.getView(), null);
		viewer.getView().addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
			if (e.getButton() == MouseButton.SECONDARY && e.isShiftDown() && !getAvailablePathClasses().isEmpty()) {
				circlePopup.setAnimationDuration(Duration.millis(200));
				updateSetAnnotationPathClassMenu(circlePopup, viewer);
				circlePopup.show(e.getScreenX(), e.getScreenY());
				e.consume();
				return;
			} else if (circlePopup.isShown())
				circlePopup.hide();
				
			if (e.getButton() == MouseButton.SECONDARY) {
				popup.show(viewer.getView().getScene().getWindow(), e.getScreenX(), e.getScreenY());				
				e.consume();
			}
		});
		
//		// It's necessary to make the Window the owner, since otherwise the context menu does not disappear when clicking elsewhere on the viewer
//		viewer.getView().setOnContextMenuRequested(e -> {
//			popup.show(viewer.getView().getScene().getWindow(), e.getScreenX(), e.getScreenY());
////			popup.show(viewer.getView(), e.getScreenX(), e.getScreenY());
//		});
			
	}
	
	
	/**
	 * Set selected TMA cores to have the specified 'missing' status.
	 * 
	 * @param hierarchy
	 * @param setToMissing
	 */
	private static void setTMACoreMissing(final PathObjectHierarchy hierarchy, final boolean setToMissing) {
		if (hierarchy == null)
			return;
		PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
		List<PathObject> changed = new ArrayList<>();
		if (pathObject instanceof TMACoreObject) {
			TMACoreObject core = (TMACoreObject)pathObject;
			core.setMissing(setToMissing);
			changed.add(core);
			// Update any other selected cores to have the same status
			for (PathObject pathObject2 : hierarchy.getSelectionModel().getSelectedObjects()) {
				if (pathObject2 instanceof TMACoreObject) {
					core = (TMACoreObject)pathObject2;
					if (core.isMissing() != setToMissing) {
						core.setMissing(setToMissing);
						changed.add(core);
					}
				}
			}
		}
		if (!changed.isEmpty())
			hierarchy.fireObjectsChangedEvent(getInstance(), changed);
	}
	
	
	/**
	 * Set selected TMA cores to have the specified 'locked' status.
	 * 
	 * @param hierarchy
	 * @param setToMissing
	 */
	private static void setSelectedAnnotationLock(final PathObjectHierarchy hierarchy, final boolean setToLocked) {
		if (hierarchy == null)
			return;
		PathObject pathObject = hierarchy.getSelectionModel().getSelectedObject();
		List<PathObject> changed = new ArrayList<>();
		if (pathObject instanceof PathAnnotationObject) {
			PathAnnotationObject annotation = (PathAnnotationObject)pathObject;
			annotation.setLocked(setToLocked);
			changed.add(annotation);
			// Update any other selected cores to have the same status
			for (PathObject pathObject2 : hierarchy.getSelectionModel().getSelectedObjects()) {
				if (pathObject2 instanceof PathAnnotationObject) {
					annotation = (PathAnnotationObject)pathObject2;
					if (annotation.isLocked() != setToLocked) {
						annotation.setLocked(setToLocked);
						changed.add(annotation);
					}
				}
			}
		}
		if (!changed.isEmpty())
			hierarchy.fireObjectsChangedEvent(getInstance(), changed);
	}
	
	
	
	/**
	 * Update a 'set annotation class' menu for a viewer immediately prior to display
	 * 
	 * @param menuSet
	 * @param viewer
	 * @return
	 */
	void updateSetAnnotationPathClassMenu(final Menu menuSetClass, final QuPathViewer viewer) {
		updateSetAnnotationPathClassMenu(menuSetClass.getItems(), viewer, false);
		menuSetClass.setVisible(!menuSetClass.getItems().isEmpty());
	}
	
	
	void updateSetAnnotationPathClassMenu(final CirclePopupMenu menuSetClass, final QuPathViewer viewer) {
		updateSetAnnotationPathClassMenu(menuSetClass.getItems(), viewer, true);
	}

		
	void updateSetAnnotationPathClassMenu(final ObservableList<MenuItem> menuSetClassItems, final QuPathViewer viewer, final boolean useFancyIcons) {
		// We need a viewer and an annotation, as well as some PathClasses, otherwise we just need to ensure the menu isn't visible
		if (viewer == null || !(viewer.getSelectedObject() instanceof PathAnnotationObject) || availablePathClasses.isEmpty()) {
			menuSetClassItems.clear();
			return;
		}
		
		PathObject mainPathObject = viewer.getSelectedObject();
		PathClass currentClass = mainPathObject.getPathClass();
		
		ToggleGroup group = new ToggleGroup();
		List<MenuItem> itemList = new ArrayList<>();
		RadioMenuItem selected = null;
		for (PathClass pathClass : availablePathClasses) {
			PathClass pathClassToSet = pathClass.getName() == null ? null : pathClass;
			String name = pathClass.getName() == null ? "None" : pathClass.getName();
			Action actionSetClass = new Action(name, e -> {
				List<PathObject> changed = new ArrayList<>();
				for (PathObject pathObject : viewer.getAllSelectedObjects()) {
					if (!pathObject.isAnnotation() || pathObject.getPathClass() == pathClassToSet)
						continue;
					pathObject.setPathClass(pathClassToSet);
					changed.add(pathObject);
				}
				if (!changed.isEmpty())
					viewer.getHierarchy().fireObjectClassificationsChangedEvent(this, changed);				
			});
			Node shape;
			if (useFancyIcons) {
				Ellipse r = new Ellipse(iconSize/2.0, iconSize/2.0, iconSize, iconSize);
				if ("None".equals(name)) {
					r.setFill(Color.rgb(255, 255, 255, 0.75));
					
				}
				else
					r.setFill(ColorToolsFX.getCachedColor(pathClass.getColor()));
				r.setOpacity(0.8);
				DropShadow effect = new DropShadow(6, -3, 3, Color.GRAY);
				r.setEffect(effect);
				shape = r;
			} else {
				Rectangle r = new Rectangle(0, 0, 8, 8);
				r.setFill("None".equals(name) ? Color.TRANSPARENT : ColorToolsFX.getCachedColor(pathClass.getColor()));
				shape = r;
			}
//			actionSetClass.setGraphic(r);
			RadioMenuItem item = ActionUtils.createRadioMenuItem(actionSetClass);
			item.graphicProperty().unbind();
			item.setGraphic(shape);
			item.setToggleGroup(group);
			itemList.add(item);
			if (pathClassToSet == currentClass)
				selected = item;
		}
		group.selectToggle(selected);
		menuSetClassItems.setAll(itemList);
	}
	
	
	/**
	 * Opan the image represented by the specified ProjectImageEntry.
	 * 
	 * If an image is currently open, this command will prompt to save any changes.
	 * 
	 * @param entry
	 */
	public void openImageEntry(ProjectImageEntry<BufferedImage> entry) {
		if (entry == null)
			return;
		// Check if we're changing ImageData
		ImageData<BufferedImage> imageData = getImageData();
		if (imageData != null && imageData.getServerPath().equals(entry.getServerPath()))
			return;
		// If the current ImageData belongs to the current project, and there have been any changes, serialize these
		Project<BufferedImage> project = getProject();
		if (imageData != null && project != null) {
			ProjectImageEntry<BufferedImage> entryPrevious = project.getImageEntry(imageData.getServerPath());
			File filePrevious = getImageDataFile(project, entryPrevious);
			if (filePrevious != null) {
				// Write if the ImageData has changed, of if it has not previously been written
				if (imageData.isChanged()) {
					DialogButton response = DisplayHelpers.showYesNoCancelDialog("Save changes", "Save changes to " + entryPrevious.getImageName() + "?");
					if (response == DialogButton.YES)
						PathIO.writeImageData(filePrevious, imageData);
					else if (response == DialogButton.CANCEL)
						return;
				}
			}
		}
		File fileData = getImageDataFile(project, entry);

		//		boolean rotate180 = true;
		// Check if we need to rotate the image
		String value = entry.getMetadataValue("rotate180");
		boolean rotate180 = value != null && value.toLowerCase().equals("true");

		if (fileData != null && fileData.isFile()) {
			// Open the image, and then the data if possible
			if (openImage(entry.getServerPath(), false, false, rotate180))
				openSavedData(getViewer(), fileData, true);
			else
				DisplayHelpers.showErrorMessage("Image open", "Unable to open image for path\n" + entry.getServerPath());
		} else
			openImage(entry.getServerPath(), false, false, rotate180);
	}
	
	/**
	 * Get the ImageData file for a specific entry of a project.
	 * 
	 * This file does not necessarily exist, but it is the file that ought to be used for loading/saving 
	 * within a project.
	 * 
	 * @param project
	 * @param entry
	 * @return
	 */
	public static File getImageDataFile(final Project<?> project, final ProjectImageEntry<?> entry) {
		if (project == null || entry == null)
			return null;
		File dirBase = project.getBaseDirectory();
		if (dirBase == null || !dirBase.isDirectory())
			return null;

		File dirData = new File(dirBase, "data");
		if (!dirData.exists())
			dirData.mkdir();
		return new File(dirData, entry.getImageName() + "." + PathPrefs.getSerializationExtension());
	}
	
	
	
	
	/**
	 * Open a new whole slide image server, or ImageData.
	 * If the path is the same as a currently-open server, do nothing.
	 * 
	 * @param prompt - if true, give the user the opportunity to cancel opening if a whole slide server is already set
	 * @return true if the server was set for this GUI, false otherwise
	 */
	public boolean openImage(String pathNew, boolean prompt, boolean includeURLs, boolean rotate180) {
		return openImage(getViewer(), pathNew, prompt, includeURLs, rotate180);
	}

	/**
	 * Open a new whole slide image server, or ImageData.
	 * If the path is the same as a currently-open server, do nothing.
	 * 
	 * @param prompt - if true, give the user the opportunity to cancel opening if a whole slide server is already set
	 * @return true if the server was set for this GUI, false otherwise
	 */
	public boolean openImage(QuPathViewer viewer, String pathNew, boolean prompt, boolean includeURLs, boolean rotate180) {
		
		if (viewer == null) {
			if (getViewers().size() == 1)
				viewer = getViewer();
			else {
				DisplayHelpers.showErrorMessage("Open image", "Please specify the viewer where the image should be opened!");
				return false;
			}
		}
		
		ImageServer<BufferedImage> server = viewer.getServer();
		String pathOld = null;
		File fileBase = null;
		if (server != null) {
			pathOld = server.getPath();
			try {
				fileBase = new File(pathOld).getParentFile();
			} catch (Exception e) {};
		}
		// Prompt for a path, if required
		File fileNew = null;
		if (pathNew == null) {
			if (includeURLs) {
				pathNew = getDialogHelper().promptForFilePathOrURL("Choose path", pathOld, fileBase, null, null);
				if (pathNew == null)
					return false;
				fileNew = new File(pathNew);
			} else {
				fileNew = getDialogHelper().promptForFile(null, fileBase, null, null);
				if (fileNew == null)
					return false;
				pathNew = fileNew.getAbsolutePath();
			}
//			if (includeURLs)
//				pathNew = PathPrefs.getDialogHelper().promptForFilePathOrURL(pathOld, fileBase, "Image files", PathPrefs.getKnownImageExtensions());
//			else {
//				File file = PathPrefs.getDialogHelper().promptForFile(null, fileBase, "Image files", PathPrefs.getKnownImageExtensions());
//				if (file != null)
//					pathNew = file.getAbsolutePath();
//			}
		} else
			fileNew = new File(pathNew);
		
		// If we have a file, check if it is a data file - if so, handle differently
		if (fileNew.isFile() && GeneralTools.checkExtensions(pathNew, PathPrefs.getSerializationExtension()))
			return openSavedData(viewer, fileNew, false);

		// Check for project file
		if (fileNew.isFile() && GeneralTools.checkExtensions(pathNew, ProjectIO.getProjectExtension())) {
				logger.info("Trying to load project {}", fileNew.getAbsolutePath());
				try {
					Project<BufferedImage> project = ProjectIO.loadProject(fileNew, BufferedImage.class);
					if (project != null) {
						setProject(project);
						return true;
					}
				} catch (Exception e) {
					DisplayHelpers.showErrorMessage("Open project", "Could not open " + fileNew.getName() + " as a QuPath project");
					return false;
				}
		}

		
		// Try opening an image, unless it's the same as the image currently open
		if (!pathNew.equals(pathOld)) {
			ImageServer<BufferedImage> serverNew = ImageServerProvider.buildServer(pathNew, BufferedImage.class);
			if (serverNew != null) {
				if (pathOld != null && prompt && !viewer.getHierarchy().isEmpty()) {
					if (!DisplayHelpers.showYesNoDialog("Replace open image", "Close " + server.getShortServerName() + "?"))
						return false;
				}
				if (rotate180)
					serverNew = new RotatedImageServer(serverNew);
				ImageData<BufferedImage> imageData = serverNew == null ? null : createNewImageData(serverNew); // TODO: DEAL WITH PATHOBJECT HIERARCHIES!
				
				viewer.setImageData(imageData);
				setInitialLocationAndMagnification(viewer);
				
//				// Reset the object hierarchy to clear any ROIs etc.
//				hierarchy.clearAll();
//				hierarchy.getSelectionModel().resetSelection();
				
				return true;
			}
			else {
				// Show an error message if we can't open the file
				DisplayHelpers.showErrorNotification("Open image", "Sorry, I can't open " + pathNew);
//				logger.error("Unable to build whole slide server for path '{}'", pathNew);
			}
		}
		return false;
	}
	
	
	public ImageData<BufferedImage> createNewImageData(final ImageServer<BufferedImage> server) {
		return createNewImageData(server, PathPrefs.getAutoEstimateImageType());
	}
	
	/**
	 * Create a new ImageData, optionally estimating the image type.
	 * 
	 * @param server
	 * @param estimateImageType
	 * @return
	 */
	public ImageData<BufferedImage> createNewImageData(final ImageServer<BufferedImage> server, final boolean estimateImageType) {
		return new ImageData<BufferedImage>(server, estimateImageType ? DisplayHelpers.estimateImageType(server, imageRegionStore.getThumbnail(server, 0, 0, true)) : ImageData.ImageType.UNSET);
	}
	
		
	/**
	 * Attempt to update the build string, providing some basic version info.
	 * 
	 * This only works when running from a Jar.
	 * 
	 * @return
	 */
	public boolean updateBuildString() {
		try {
			for (URL url : Collections.list(getClass().getClassLoader().getResources("META-INF/MANIFEST.MF"))) {
				if (url == null)
					continue;
				try (InputStream stream = url.openStream()) {
					Manifest manifest = new Manifest(url.openStream());
					Attributes attributes = manifest.getMainAttributes();
					String version = attributes.getValue("Implementation-Version");
					String buildTime = attributes.getValue("QuPath-build-time");
					String latestCommit = attributes.getValue("QuPath-latest-commit");
					if (latestCommit != null)
						latestCommitTag = latestCommit;
					if (version == null || buildTime == null)
						continue;
					buildString = "Version: " + version + "\n" + "Build time: " + buildTime;
					if (latestCommitTag != null)
						buildString += "\n" + "Latest commit tag: " + latestCommitTag;
					versionString = version;
					return true;
				} catch (IOException e) {
					logger.error("Error reading manifest", e);
				}
			}
		} catch (IOException e) {
			logger.error("Error searching for build string", e);
		}
		return false;
	}
	
	
	
	
	/**
	 * Open a saved data file within a particular viewer, optionally keeping the same ImageServer as is currently open.
	 * The purpose of this is to make it possible for a project (for example) to open the correct server prior to
	 * opening the data file, enabling it to make use of relative path names and not have to rely on the absolute path
	 * encoded within the ImageData.
	 * 
	 * @param viewer
	 * @param file
	 * @param keepExistingServer If true and the viewer already has an ImageServer, then any ImageServer path recorded within the data file will be ignored
	 * @return
	 */
	public boolean openSavedData(QuPathViewer viewer, final File file, final boolean keepExistingServer) {
		
		if (viewer == null) {
			if (getViewers().size() == 1)
				viewer = getViewer();
			else {
				DisplayHelpers.showErrorMessage("Open saved data", "Please specify the viewer where the data should be opened!");
				return false;
			}
		}
		
		// First check to see if the ImageData is already open - if so, just activate the viewer
		for (QuPathViewerPlus v : viewerManager.getViewers()) {
			ImageData<?> data = v.getImageData();
			if (data != null && data.getLastSavedPath() != null && new File(data.getLastSavedPath()).equals(file)) {
				viewerManager.setActiveViewer(v);
				return true;
			}
		}
		
		String serverPath = null;
		ImageData<BufferedImage> imageData = viewer.getImageData();
		
		// If we are loading data related to the same image server, load into that - otherwise open a new image if we can find it
		serverPath = PathIO.readSerializedServerPath(file);
		boolean sameServer = serverPath == null || (imageData != null && imageData.getServerPath().equals(serverPath));			
		
		
		// If we don't have the same server, try to check the path is valid.
		// If it isn't, then prompt to enter a new path.
		// Currently, URLs are always assumed to be valid, but files may have moved.
		// TODO: Make it possible to recover data if a stored URL ceases to be valid.
		ImageServer<BufferedImage> server = null;
		if (sameServer || (imageData != null && keepExistingServer))
			server = imageData.getServer();
		else {
			server = ImageServerProvider.buildServer(serverPath, BufferedImage.class);
			if (server == null) {
//				boolean pathValid = new File(serverPath).isFile() || URLHelpers.checkURL(serverPath);
//				if (!pathValid) {
					serverPath = getDialogHelper().promptForFilePathOrURL("Set path to missing file", serverPath, new File(serverPath).getParentFile(), null, null);
//					fileImage = getDialogHelper().promptForFile("Set image location (" + fileImage.getName() + ")",fileImage.getParentFile(), null, null);
					if (serverPath == null)
						return false;
					server = ImageServerProvider.buildServer(serverPath, BufferedImage.class);
					if (server == null)
						return false;
//				}
			}
			
			// Small optimization... put in a thumbnail request early in a background thread.
			// This way that it will be fetched while the image data is being read -
			// generally leading to improved performance in the viewer's setImageData method
			// (specifically the updating of the ImageDisplay, which needs a thumbnail)
			final ImageServer<BufferedImage> serverTemp = server;
			poolMultipleThreads.submit(() -> {
					imageRegionStore.getThumbnail(serverTemp, 0, 0, true);
			});
		}
		
		
		if (imageData != null && imageData.isChanged()) {
			if (!DisplayHelpers.showYesNoDialog("Discard objects", "Discard changes for " + imageData.getServer().getShortServerName() + " without saving?"))
				return false;			
			
		} else if (imageData != null && imageData.isChanged()) {
			if (!DisplayHelpers.showYesNoDialog("Replace open image", "Close " + imageData.getServer().getShortServerName() + " without saving?"))
				return false;			
		}
		
//		ImageDataLoader task = new ImageDataLoader(file, imageData, server);
//		ProgressDialog dialog = new ProgressDialog(task);
//		dialog.initOwner(getStage());
////		dialog.show
//		createSingleThreadExecutor(this).submit(task);
//		dialog.showAndWait();
//		
//		ImageData<BufferedImage> imageData2 = task.getImageData();
		
		ImageData<BufferedImage> imageData2 = PathIO.readImageData(file, imageData, server, BufferedImage.class);
		// Check it worked...
		if (imageData2 == null)
			return false;
		
		if (imageData2 != imageData) {
			viewer.setImageData(imageData2);
			// If we just have a single viewer, no harm in centering this
			if (viewerManager.getViewers().size() == 1 || !viewerManager.synchronizeViewersProperty().get())
				setInitialLocationAndMagnification(viewer);
		}
		// Make sure that the color channels are loaded
		if (viewer.getImageDisplay().loadChannelColorProperties())
			viewer.repaintEntireImage();
		
		return true;
	}
	
	
	
	static class ImageDataLoader extends Task<ImageData<BufferedImage>> {
		
		private File file;
		private ImageData<BufferedImage> imageData;
		private ImageServer<BufferedImage> server;
		
		ImageDataLoader(final File file, final ImageData<BufferedImage> imageData, final ImageServer<BufferedImage> server) {
			this.file = file;
			this.imageData = imageData;
			this.server = server;
		}

		@Override
		protected ImageData<BufferedImage> call() throws Exception {
			imageData = PathIO.readImageData(file, imageData, server, BufferedImage.class);
			return imageData;
		}
		
		ImageData<BufferedImage> getImageData() {
			return imageData;
		}
		
	}
	
	
	
//	public static void addMenuItem(final JMenuBar menuBar, final Action action, final String... menuPath) {
//		
//		// Find (or create) the required menu
//		for (int i = 0; i < menuBar.getMenuCount(); i++) {
//			JMenu menu = menuBar.getMenu(i);
//			if (name.equals(menu.getText()))
//				return menu;
//		}
//		if (createMenu) {
//			JMenu menu = new JMenu(name);
//			menuBar.add(menu);
//			return menu;
//		}
//		return null;
//		
//	}
	
	
	/**
	 * Get a reference to the PreferencePanel.
	 * 
	 * This can be useful for extensions to be able to add in their own preferences.
	 * 
	 * @return
	 */
	public PreferencePanel getPreferencePanel() {
		return prefsPanel;
	}
	
	
	/**
	 * Add menus to a MenuBar.
	 * 
	 * @param menuBar
	 * @param menus
	 * @return
	 */
	static MenuBar addToMenuBar(final MenuBar menuBar, final Menu... menus) {
		menuBar.getMenus().addAll(menus);
		return menuBar;
	}
	
	/**
	 * Create a menu, and add new menu items.
	 * 
	 * If null is passed as an object, a separated is added.
	 * 
	 * @param menu
	 * @param objects
	 * @return new menu
	 */
	public static Menu createMenu(final String name, final Object... items) {
		return addMenuItems(createMenu(name), items);
	}
	
	/**
	 * Add menu items to an existing menu.
	 * 
	 * If null is passed as an object, a separated is added.
	 * 
	 * @param menu
	 * @param objects
	 * @return menu, so that this method can be nested inside other calls.
	 */
	public static Menu addMenuItems(final Menu menu, final Object... items) {
		// Check if the last item was a separator -
		// we don't want two adjacent separators, since this looks a bit weird
		boolean lastIsSeparator = menu.getItems().isEmpty() ? false : menu.getItems().get(menu.getItems().size()-1) instanceof SeparatorMenuItem;
		
		List<MenuItem> newItems = new ArrayList<>();
		for (Object item : items) {
			if (item == null) {
				if (!lastIsSeparator)
					newItems.add(new SeparatorMenuItem());
				lastIsSeparator = true;
			}
			else if (item instanceof MenuItem) {
				newItems.add((MenuItem)item);
				lastIsSeparator = false;
			}
			else if (item instanceof Action) {
				newItems.add(createMenuItem((Action)item));
				lastIsSeparator = false;
			} else
				logger.warn("Could not add menu item {}", item);
		}
		if (!newItems.isEmpty()) {
			boolean initializing = initializingMenus.get();
			initializingMenus.set(true);
//			System.err.println("MENU STUFF: " + initializingMenus.get() + " - " + menu.getText());
			menu.getItems().addAll(newItems);
			initializingMenus.set(initializing);
		}
		return menu;
	}
	
	
	/**
	 * Create an executor using a single thread.
	 * 
	 * Optionally specify an owner, in which case the same Executor will be returned for the owner 
	 * for so long as the Executor has not been shut down; if it has been shut down, a new Executor will be returned.
	 * 
	 * Specifying an owner is a good idea if there is a chance that any submitted tasks could block,
	 * since the same Executor will be returned for all requests that give a null owner.
	 * 
	 * The advantage of using this over creating an ExecutorService some other way is that
	 * shutdown will be called on any pools created this way whenever QuPath is quit.
	 * 
	 * @param owner
	 * 
	 */
	public ExecutorService createSingleThreadExecutor(final Object owner) {
		ExecutorService pool = mapSingleThreadPools.get(owner);
		if (pool == null || pool.isShutdown()) {
			pool = Executors.newSingleThreadExecutor(new SimpleThreadFactory(owner.getClass().getSimpleName().toLowerCase() + "-", false));
			mapSingleThreadPools.put(owner, pool);
		}
		return pool;
	}
	
	/**
	 * Create a completion service that uses a shared threadpool for the application.
	 * 
	 * @param cls
	 */
	public <V> ExecutorCompletionService<V> createSharedPoolCompletionService(Class<V> cls) {
		return new ExecutorCompletionService<V>(poolMultipleThreads);
	}
	
	/**
	 * Submit a short task to a shared thread pool
	 * 
	 * @param runnable
	 */
	public void submitShortTask(final Runnable runnable) {
		poolMultipleThreads.submit(runnable);
	}
	
	
	
//	private final static String URL_DOCS       = "http://go.qub.ac.uk/qupath-docs");
//	private final static String URL_VIDEOS     = "http://go.qub.ac.uk/qupath-videos";
//	private final static String URL_CITATION   = "http://go.qub.ac.uk/qupath-citation";
//	private final static String URL_EXTENSIONS = "http://go.qub.ac.uk/qupath-extensions";
//	private final static String URL_BUGS       = "http://go.qub.ac.uk/qupath-bugs";
//	private final static String URL_FORUM      = "http://go.qub.ac.uk/qupath-forum";
//	private final static String URL_SOURCE     = "http://go.qub.ac.uk/qupath-source";
	
	private final static String URL_DOCS       = "https://github.com/qupath/qupath/wiki";
	private final static String URL_VIDEOS     = "https://www.youtube.com/channel/UCk5fn7cjMZFsQKKdy-YWOFQ";
	private final static String URL_CITATION   = "https://github.com/qupath/qupath/wiki/Citing-QuPath";
	private final static String URL_EXTENSIONS = "https://github.com/qupath/qupath/wiki/Extensions";
	private final static String URL_BUGS       = "https://github.com/qupath/qupath/issues";
	private final static String URL_FORUM      = "https://groups.google.com/forum/#!forum/qupath-users";
	private final static String URL_SOURCE     = "https://github.com/qupath/qupath";

	
	
	protected MenuBar createMenuBar() {
		
		// Create a recent projects list
		ObservableList<File> recentProjects = PathPrefs.getRecentProjectList();
		Menu menuRecent = createMenu("Recent projects...");
		
		
		// Create a File menu
		Menu menuFile = createMenu(
				"File",
				createMenu(
						"Project...",
						getActionMenuItem(GUIActions.PROJECT_NEW),
						getActionMenuItem(GUIActions.PROJECT_OPEN),
						getActionMenuItem(GUIActions.PROJECT_CLOSE),
						null,
						getActionMenuItem(GUIActions.PROJECT_IMPORT_IMAGES),
						getActionMenuItem(GUIActions.PROJECT_EXPORT_IMAGE_LIST),
						null,
						getActionMenuItem(GUIActions.PROJECT_METADATA)
						),
				menuRecent,
				null,
				getActionMenuItem(GUIActions.OPEN_IMAGE),
				getActionMenuItem(GUIActions.OPEN_IMAGE_OR_URL),
				createCommandAction(new RevertCommand(this), "Revert", null, new KeyCodeCombination(KeyCode.R, KeyCodeCombination.SHORTCUT_DOWN)),
				null,
				getActionMenuItem(GUIActions.SAVE_DATA_AS),
				getActionMenuItem(GUIActions.SAVE_DATA),
				null,
				createMenu(
						"Export snapshot...",
						createCommandAction(new SaveViewCommand(this, true), "Window snapshot"),
						createCommandAction(new SaveViewCommand(this, false), "Viewer snapshot")
						),
				createCommandAction(new ExportImageRegionCommand(this), "Export image region"),
				null,
				getActionMenuItem(GUIActions.TMA_SCORE_IMPORTER),
				getActionMenuItem(GUIActions.TMA_EXPORT_DATA),
				createCommandAction(new TMAViewerCommand(), "Launch TMA data viewer")
				);
		
		
		menuFile.setOnMenuValidation(e -> {
			menuRecent.getItems().clear();
			for (File fileProject : recentProjects) {
				if (fileProject == null)
					continue;
//				String name = fileProject.getAbsolutePath();
//				int maxLength = 40;
//				if (name.length() > maxLength)
//					name = "..." + name.substring(name.length() - maxLength);
				String name = fileProject.getParentFile() != null ? fileProject.getParentFile().getName() + "/" + fileProject.getName() : fileProject.getName();
				name = ".../" + name;
				MenuItem item = new MenuItem(name);
				item.setOnAction(e2 -> {
					Project<BufferedImage> project;
					try {
						project = ProjectIO.loadProject(fileProject, BufferedImage.class);
						setProject(project);
					} catch (Exception e1) {
						DisplayHelpers.showErrorMessage("Project error", "Cannot find project " + fileProject.getName());
					}
				});
				menuRecent.getItems().add(item);
			}
		});
		
		
		// Create Edit menu
		Menu menuEdit = createMenu(
				"Edit",
				getActionMenuItem(GUIActions.UNDO),
				getActionMenuItem(GUIActions.REDO),
				null,
				getActionMenuItem(GUIActions.COPY_VIEW),
				getActionMenuItem(GUIActions.COPY_WINDOW),
				null,
				getActionMenuItem(GUIActions.PREFERENCES),
				createCommandAction(new ResetPreferencesCommand(), "Reset preferences")
				);

		// Create Tools menu
		ToggleGroup groupTools = new ToggleGroup();
		Menu menuTools = createMenu(
				"Tools",
				getActionCheckBoxMenuItem(GUIActions.MOVE_TOOL, groupTools),
				getActionCheckBoxMenuItem(GUIActions.RECTANGLE_TOOL, groupTools),
				getActionCheckBoxMenuItem(GUIActions.ELLIPSE_TOOL, groupTools),
				getActionCheckBoxMenuItem(GUIActions.LINE_TOOL, groupTools),
				getActionCheckBoxMenuItem(GUIActions.POLYGON_TOOL, groupTools),
				getActionCheckBoxMenuItem(GUIActions.BRUSH_TOOL, groupTools),
				getActionCheckBoxMenuItem(GUIActions.WAND_TOOL, groupTools),
				getActionCheckBoxMenuItem(GUIActions.POINTS_TOOL, groupTools)
				);
		
		Menu menuGestures = createMenu("Multi-touch gestures");
		addMenuItems(
				menuGestures,
				ActionUtils.createMenuItem(new Action("Turn on all gestures", e -> {
					PathPrefs.setUseScrollGestures(true);
					PathPrefs.setUseZoomGestures(true);
					PathPrefs.setUseRotateGestures(true);
				})),
				ActionUtils.createMenuItem(new Action("Turn off all gestures", e -> {
					PathPrefs.setUseScrollGestures(false);
					PathPrefs.setUseZoomGestures(false);
					PathPrefs.setUseRotateGestures(false);
				})),
				null,
				ActionUtils.createCheckMenuItem(createSelectableCommandAction(PathPrefs.useScrollGesturesProperty(), "Use scroll gestures")),
				ActionUtils.createCheckMenuItem(createSelectableCommandAction(PathPrefs.useZoomGesturesProperty(), "Use zoom gestures")),
				ActionUtils.createCheckMenuItem(createSelectableCommandAction(PathPrefs.useRotateGesturesProperty(), "Use rotate gestures"))
				);
		addMenuItems(
				menuTools,
				null,
				menuGestures);
		
//		if (PathPrefs.getRequestAdvancedControllers()) {
//			try {
//				// If we have an advanced input controller, try turning it on.
//				// Previously, we had a menu item... but here, we assume that if a controller is plugged in, then it's wanted.
//				// However, note that it doesn't like it if a controller is unplugged... in which case it won't work, even if it's plugged back in.
//				Class<?> cAdvancedController = getClassLoader().loadClass("qupath.lib.gui.input.AdvancedControllerActionFactory");
//				Method method = cAdvancedController.getMethod("tryToTurnOnAdvancedController", QuPathGUI.class);
//				if (Boolean.TRUE.equals(method.invoke(null, this))) {
//					logger.info("Advanced controllers turned ON");
//				} else
//					logger.debug("No advanced controllers found");
//			} catch (Exception e) {
//				logger.error("Unable to load advanced controller support");
//				logger.debug("{}", e);
//			}
//		}
		
		// Create View menu
		SlideLabelView slideLabelView = new SlideLabelView(this);
		ToggleGroup groupCellDisplay = new ToggleGroup();
		Menu menuView = createMenu(
				"View",
				getActionCheckBoxMenuItem(GUIActions.SHOW_ANALYSIS_PANEL),
				getActionMenuItem(GUIActions.SHOW_COMMAND_LIST),
//				createSelectableCommandAction(pinCommandList, "Pin command list", (Node)null, new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN)),
				null,
				getActionMenuItem(GUIActions.BRIGHTNESS_CONTRAST),
				null,
				getActionCheckBoxMenuItem(GUIActions.TOGGLE_SYNCHRONIZE_VIEWERS),
				null,
				createMenu(
						"Zoom",
						createCommandAction(new ViewerSetDownsampleCommand(this, 0.25), "400%"),
						createCommandAction(new ViewerSetDownsampleCommand(this, 1), "100%"),
						createCommandAction(new ViewerSetDownsampleCommand(this, 2), "50%"),
						createCommandAction(new ViewerSetDownsampleCommand(this, 10), "10%"),
						createCommandAction(new ViewerSetDownsampleCommand(this, 100), "1%"),
						null,
						getActionMenuItem(GUIActions.ZOOM_IN),
						getActionMenuItem(GUIActions.ZOOM_OUT),
						createCheckMenuItem(getAction(GUIActions.ZOOM_TO_FIT))
						),
				getActionMenuItem(GUIActions.ROTATE_IMAGE),
				null,
				createMenu(
						"Cell display",
						getActionCheckBoxMenuItem(GUIActions.SHOW_CELL_BOUNDARIES, groupCellDisplay),
						getActionCheckBoxMenuItem(GUIActions.SHOW_CELL_NUCLEI, groupCellDisplay),
						getActionCheckBoxMenuItem(GUIActions.SHOW_CELL_BOUNDARIES_AND_NUCLEI, groupCellDisplay)
						),
				getActionCheckBoxMenuItem(GUIActions.SHOW_ANNOTATIONS),
				getActionCheckBoxMenuItem(GUIActions.FILL_ANNOTATIONS),
				getActionCheckBoxMenuItem(GUIActions.SHOW_TMA_GRID),
				getActionCheckBoxMenuItem(GUIActions.SHOW_TMA_GRID_LABELS),
				getActionCheckBoxMenuItem(GUIActions.SHOW_OBJECTS),
				getActionCheckBoxMenuItem(GUIActions.FILL_OBJECTS),
				createCheckMenuItem(createSelectableCommandAction(overlayOptions.showConnectionsProperty(), "Show object connections")),
				null,
				getActionCheckBoxMenuItem(GUIActions.SHOW_OVERVIEW),
				getActionCheckBoxMenuItem(GUIActions.SHOW_LOCATION),
				getActionCheckBoxMenuItem(GUIActions.SHOW_SCALEBAR),
				getActionCheckBoxMenuItem(GUIActions.SHOW_GRID),
				getActionMenuItem(GUIActions.GRID_SPACING),
				null,
				getActionMenuItem(GUIActions.VIEW_TRACKER),
				getActionMenuItem(GUIActions.MINI_VIEWER),
				createCheckMenuItem(createSelectableCommandAction(slideLabelView.showingProperty(), "Show slide label")),				
				null,
				getActionMenuItem(GUIActions.SHOW_LOG)
			);
		
		
		Menu menuObjects = createMenu(
				"Objects",
				createMenu(
						"Delete...",
						getActionMenuItem(GUIActions.DELETE_SELECTED_OBJECTS),
						null,
						getActionMenuItem(GUIActions.CLEAR_HIERARCHY),
						getActionMenuItem(GUIActions.CLEAR_ANNOTATIONS),
						getActionMenuItem(GUIActions.CLEAR_DETECTIONS)
						),
				createMenu(
						"Select...",
						createCommandAction(new ResetSelectionCommand(this), "Reset selection", null, new KeyCodeCombination(KeyCode.R, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_DOWN)),
						null,
						createCommandAction(new SelectObjectsByClassCommand(this, TMACoreObject.class), "Select TMA cores"),
						createCommandAction(new SelectObjectsByClassCommand(this, PathAnnotationObject.class), "Select annotations"),
						createCommandAction(new SelectObjectsByClassCommand(this, PathDetectionObject.class), "Select detections"),
						createCommandAction(new SelectObjectsByClassCommand(this, PathCellObject.class), "Select cells"),
						null,
						createCommandAction(new SelectObjectsByMeasurementCommand(this), "Select by measurements (experimental)")
						),
				null,
				getActionMenuItem(GUIActions.RIGID_OBJECT_EDITOR),
				getActionMenuItem(GUIActions.SPECIFY_ANNOTATION),
				createPluginAction("Expand annotations", DilateAnnotationPlugin.class, null, false),
				getActionMenuItem(GUIActions.SELECT_ALL_ANNOTATION),
				getActionMenuItem(GUIActions.ANNOTATION_DUPLICATE),
				getActionMenuItem(GUIActions.TRANSFER_ANNOTATION),
				null,
				createCommandAction(new InverseObjectCommand(this), "Make inverse annotation"),
				createCommandAction(new MergeSelectedAnnotationsCommand(this), "Merge selected annotations"),
				createCommandAction(new ShapeSimplifierCommand(this), "Simplify annotation shape")
				);

		
		Menu menuTMA = createMenu(
				"TMA",
				createMenu(
					"Add...",
					createCommandAction(new TMAGridAdd(this, TMAAddType.ROW_BEFORE), "Add TMA row before"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.ROW_AFTER), "Add TMA row after"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.COLUMN_BEFORE), "Add TMA column before"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.COLUMN_AFTER), "Add TMA column after")
					),
				createMenu(
						"Remove...",
						createCommandAction(new TMAGridRemove(this, TMARemoveType.ROW), "Remove TMA row"),
						createCommandAction(new TMAGridRemove(this, TMARemoveType.COLUMN), "Remove TMA column")
						),
				getActionMenuItem(GUIActions.TMA_RELABEL),
				createCommandAction(new TMAGridReset(this), "Reset TMA metadata"),
				getActionMenuItem(GUIActions.CLEAR_TMA_CORES),
				createCommandAction(new TMAGridView(this), "TMA grid summary view"),
//				createCommandAction(new TMAExplorer(this), "TMA explorer (experimental)"),
				null,
				createPluginAction("Find convex hull detections (TMA)", FindConvexHullDetectionsPlugin.class, this, false, null)
				);
		
		
		Menu menuMeasure = createMenu(
				"Measure",
				getActionMenuItem(GUIActions.MEASUREMENT_MAP),
				createCommandAction(new MeasurementManager(this), "Show measurement manager"),
				null,
				getActionMenuItem(GUIActions.SUMMARY_TMA),
				getActionMenuItem(GUIActions.SUMMARY_ANNOTATIONS),
				getActionMenuItem(GUIActions.SUMMARY_DETECTIONS)
//				null,
//				createCommandAction(new TMADescendantsMeasurementCommand(this), "Add core summary measurement (TMA)"),
//				null,
//				getActionMenuItem(GUIActions.KAPLAN_MEIER_TMA)
				);
		
		
		// Try to load a script editor
		Action actionScriptEditor = createCommandAction(new ShowScriptEditorCommand(this, false), "Show script editor");
		actionScriptEditor.setAccelerator(new KeyCodeCombination(KeyCode.BRACELEFT, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_ANY));	
		Menu menuAutomate = createMenu(
				"Automate",
				actionScriptEditor,
				createCommandAction(new ScriptInterpreterCommand(this), "Script interpreter"),
				null,
				getActionMenuItem(GUIActions.WORKFLOW_DISPLAY),
				createCommandAction(new ShowScriptEditorCommand(this, true), "Create command history script")
				);
		
		// Add some plugins
		Menu menuAnalysis = createMenu(
				"Analyze",
				createMenu(
						"Preprocessing",
						getActionMenuItem(GUIActions.COLOR_DECONVOLUTION_REFINE)
						),
				createMenu(
						"Region identification",
						createMenu(
								"Tiles & superpixels",
								createPluginAction("Create tiles", TilerPlugin.class, this, false, null)
								)
						),
				createMenu(
						"Calculate features",
//						new PathPluginAction("Create tiles", TilerPlugin.class, this),
						createPluginAction("Add Intensity features (experimental)", IntensityFeaturesPlugin.class, this, true, null),
						createPluginAction("Add Haralick texture features (legacy)", HaralickFeaturesPlugin.class, this, true, null),
//						createPluginAction("Add Haralick texture features (feature test version)", HaralickFeaturesPluginTesting.class, this, imageRegionStore, null),
						createPluginAction("Add Coherence texture feature (experimental)", CoherenceFeaturePlugin.class, this, true, null),
						createPluginAction("Add Smoothed features", SmoothFeaturesPlugin.class, this, false, null),
						createPluginAction("Add Shape features (experimental)", ShapeFeaturesPlugin.class, this, false, null),
						null,
						createPluginAction("Add Local Binary Pattern features (experimental)", LocalBinaryPatternsPlugin.class, this, true, null)
						)
				);

		// Try to load classifiers
		Menu menuClassifiers = createMenu(
				"Classify",
				createCommandAction(new LoadClassifierCommand(this), "Load classifier"),
				null);

		addMenuItems(
				menuClassifiers,
				null,
				createCommandAction(new ResetClassificationsCommand(this, PathDetectionObject.class), "Reset detection classifications"),
				null,
				createCommandAction(new RandomTrainingRegionSelector(this, getAvailablePathClasses()), "Choose random training samples"),
				createCommandAction(new SingleFeatureClassifierCommand(this, PathDetectionObject.class), "Classify by specific feature")
			);
		
		Action actionUpdateCheck = new Action("Check for updates (web)", e -> {
			checkForUpdate(false);
		});
		
		Menu menuHelp = createMenu(
				"Help",
//				createCommandAction(new HelpCommand(this), "Documentation"),
				getAction(GUIActions.QUPATH_SETUP),
				null,
				createCommandAction(new OpenWebpageCommand(this, URL_DOCS), "Documentation (web)"),
				createCommandAction(new OpenWebpageCommand(this, URL_VIDEOS), "Demo videos (web)"),
//				createCommandAction(new OpenWebpageCommand(this, "http://go.qub.ac.uk/qupath-latest"), "Get latest version (web)"),
				actionUpdateCheck,
				null,
				createCommandAction(new OpenWebpageCommand(this, URL_CITATION), "Cite QuPath (web)"),
				createCommandAction(new OpenWebpageCommand(this, URL_EXTENSIONS), "Add extensions (web)"),
				createCommandAction(new OpenWebpageCommand(this, URL_BUGS), "Report bug (web)"),
				createCommandAction(new OpenWebpageCommand(this, URL_FORUM), "View user forum (web)"),
				createCommandAction(new OpenWebpageCommand(this, URL_SOURCE), "View source code (web)"),
				null,
				createCommandAction(new ShowLicensesCommand(this), "License"),
				createCommandAction(new ShowSystemInfoCommand(this), "System info"),
				createCommandAction(new ShowInstalledExtensionsCommand(this), "Installed extensions")
				);
		
		// Add all to menubar
		return addToMenuBar(
				getMenuBar(),
				menuFile,
				menuEdit,
				menuTools,
				menuView,
				menuObjects,
				menuTMA,
				menuMeasure,
				menuAutomate,
				menuAnalysis,
				menuClassifiers,
				menuHelp
				);
	}
	
	
	public Action createPluginAction(final String name, final Class<? extends PathPlugin> pluginClass, final String arg, final boolean includeRegionStore) {
		return createPluginAction(name, pluginClass, this, includeRegionStore, arg);
	}

	
	
	public Action createPluginAction(final String name, final String pluginClassName, final boolean includeRegionStore, final String arg) throws ClassNotFoundException {
		Class<PathPlugin> cls = (Class<PathPlugin>)getClassLoader().loadClass(pluginClassName);
		return createPluginAction(name, cls, this, includeRegionStore, arg);
	}
	
	
	/**
	 * Update project display.
	 */
	public void refreshProject() {
		projectBrowser.refreshProject();
	}
	
	
	public Action createPluginAction(final String name, final PathPlugin<BufferedImage> plugin, final String arg) {
		Action action = new Action(name, event -> {
			try {
				if (plugin instanceof PathInteractivePlugin) {
					PathInteractivePlugin<BufferedImage> pluginInteractive = (PathInteractivePlugin<BufferedImage>)plugin;
					ParameterDialogWrapper<BufferedImage> dialog = new ParameterDialogWrapper<>(pluginInteractive, pluginInteractive.getDefaultParameterList(getImageData()), new PluginRunnerFX(this, false));
					dialog.showDialog();
//					((PathInteractivePlugin<BufferedImage>)plugin).runInteractive(new PluginRunnerFX(this, false), arg);
				}
				else
					((PathPlugin<BufferedImage>)plugin).runPlugin(new PluginRunnerFX(this, false), arg);

			} catch (Exception e) {
				DisplayHelpers.showErrorMessage("Error", "Error running " + plugin.getName());
			}
		});
		return action;
	}
	
	/**
	 * Create an Action to construct and run a plugin interactively.
	 * 
	 * @param name
	 * @param pluginClass
	 * @param qupath
	 * @param includeRegionStore
	 * @param arg
	 * @return
	 */
	public static Action createPluginAction(final String name, final Class<? extends PathPlugin> pluginClass, final QuPathGUI qupath, final boolean includeRegionStore, final String arg) {
		try {
			Action action = new Action(name, event -> {
				PathPlugin<BufferedImage> plugin = qupath.createPlugin(pluginClass, includeRegionStore);
				qupath.runPlugin(plugin, arg, true);
			});
			return action;
		} catch (Exception e) {
			logger.error("Unable to initialize class " + pluginClass, e);
		}

		return null;
	}
	
	
	/**
	 * Run a plugin, interactively (i.e. launching a dialog) if necessary.
	 * 
	 * @param plugin
	 * @param arg
	 * @param doInteractive
	 */
	public void runPlugin(final PathPlugin<BufferedImage> plugin, final String arg, final boolean doInteractive) {
		try {
			// TODO: Check safety...
			if (doInteractive && plugin instanceof PathInteractivePlugin) {
				PathInteractivePlugin<BufferedImage> pluginInteractive = (PathInteractivePlugin<BufferedImage>)plugin;
				ParameterList params = pluginInteractive.getDefaultParameterList(getImageData());
				// Update parameter list, if necessary
				if (arg != null) {
					Map<String, String> map = GeneralTools.parseArgStringValues(arg);
					// We use the US locale because we need to ensure decimal points (not commas)
					ParameterList.updateParameterList(params, map, Locale.US);
				}
				ParameterDialogWrapper<BufferedImage> dialog = new ParameterDialogWrapper<>(pluginInteractive, params, new PluginRunnerFX(this, false));
				dialog.showDialog();
			}
			else
				plugin.runPlugin(new PluginRunnerFX(this, false), arg);

		} catch (Exception e) {
			logger.error("Unable to run plugin " + plugin, e);
		}
	}
	
	/**
	 * Create a plugin from a specified class.
	 * 
	 * @param pluginClass
	 * @param includeRegionStore
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public PathPlugin<BufferedImage> createPlugin(final Class<? extends PathPlugin> pluginClass, final boolean includeRegionStore) {
		PathPlugin<BufferedImage> plugin = null;
		try {
			if (includeRegionStore) {
				try {
					Constructor<? extends PathPlugin> constructor = pluginClass.getConstructor(ImageRegionStore.class);
					plugin = constructor.newInstance(getImageRegionStore());
				} catch (NoSuchMethodException e) {
					// Ideally would check properly, instead of relying on this...
				}
			}
			if (plugin == null)
				plugin = pluginClass.getConstructor().newInstance();
		} catch (Exception e1) {
			logger.error("Unable to construct plugin {}", pluginClass, e1);
		}
		return plugin;
	}
	
	
	
//	public static Action createPluginAction(final String name, final Class<? extends PathPlugin> pluginClass, final QuPathGUI qupath, final DefaultImageRegionStore regionStore, final String arg) {
//		Action action = new Action(name, event -> {
//		
//			QuPathViewer viewer = qupath.getViewer();
//			ImageServer<BufferedImage> server = viewer.getServer();
//			if (server == null) {
//				// TODO: Display an error message to the user!
//				logger.error("No whole slide image server could be found!");
//				return;
//			}
//			try {
//				PathPlugin plugin;
//				if (regionStore != null)
//					plugin = pluginClass.getConstructor(ImageRegionStore.class).newInstance(regionStore);
//				else
//					plugin = pluginClass.getConstructor().newInstance();
//
//				// TODO: Check safety...
//				if (plugin instanceof PathInteractivePlugin)
//					((PathInteractivePlugin<BufferedImage>)plugin).runInteractive(PluginRunnerFactory.makePluginRunnerFX(qupath, false), arg);
//				else
//					((PathPlugin<BufferedImage>)plugin).runPlugin(PluginRunnerFactory.makePluginRunnerFX(qupath, false), arg);
//							
//			} catch (Exception e) {
//				logger.error("Unable to initialize class " + pluginClass, e);
//			}
//			
//		});
//		
//		return action;
//	}
	
	
	
	public static Action createCommandAction(final PathCommand command, final String name, final Node icon, final KeyCombination accelerator) {
		Action action = new Action(name, e -> command.run());
		action.setAccelerator(accelerator);
		action.setGraphic(icon);
		return action;
	}
	
//	Action createCommandAction(final PathCommand command, final String name, final PathIconFactory.PathIcons icon, final KeyCombination accelerator) {
//		return createCommandAction(command, name, PathIconFactory.createNode(iconSize, iconSize, icon), accelerator);
//	}

	public Action createCommandAction(final String className, final String name, final Object... arguments) {
		try {
			Class<? extends PathCommand> cls = (Class<PathCommand>)getClassLoader().loadClass(className);
			Class<?>[] classes = new Class<?>[arguments.length];
			for (int i = 0; i < arguments.length; i++)
				classes[i] = arguments[i].getClass();
			Constructor<? extends PathCommand> constructor = cls.getConstructor(classes);
			PathCommand pathCommand = constructor.newInstance(arguments);
			return createCommandAction(pathCommand, name);
		} catch (Exception e) {
			logger.error("Unable to construct command: {}", e);
		}
		return null;
	}
	
	public static Action createCommandAction(final PathCommand command, final String name) {
		return createCommandAction(command, name, (Node)null, null);
	}
	
	public static Action createSelectableCommandAction(final ObservableValue<Boolean> property, final String name) {
		return createSelectableCommandAction(property, name, (Node)null, null);
	}

	public static Action createSelectableCommandAction(final ObservableValue<Boolean> property, final String name, final Node icon, final KeyCombination accelerator) {
//		Action action = new Action(name, e -> property.set(!property.get()));
		Action action = new Action(name);
		if (property instanceof Property)
			action.selectedProperty().bindBidirectional((Property<Boolean>)property);			
		else
			action.selectedProperty().bind(property);
		action.getProperties().put("Selectable", Boolean.TRUE);
		action.setAccelerator(accelerator);
		action.setGraphic(icon);
		return action;
//		return new PathSelectableAction(command, name, node, accelerator);
	}
	
	private Action createSelectableCommandAction(final ObservableValue<Boolean> property, final String name, final PathIconFactory.PathIcons icon, final KeyCombination accelerator) {
		return createSelectableCommandAction(property, name, PathIconFactory.createNode(iconSize, iconSize, icon), accelerator);
	}

	private Action createSelectableCommandAction(final PathSelectableCommand command, final String name, final Node icon, final KeyCombination accelerator) {
		Action action = new Action(name, e -> command.setSelected(!command.isSelected()));
		action.selectedProperty().addListener(e -> {
			command.setSelected(action.isSelected());
		});
		action.getProperties().put("Selectable", Boolean.TRUE);
		action.setAccelerator(accelerator);
		action.setGraphic(icon);
		return action;
//		return new PathSelectableAction(command, name, node, accelerator);
	}

	private Action createSelectableCommandAction(final PathSelectableCommand command, final String name, final Modes mode, final KeyCombination accelerator) {
		Action action = createSelectableCommandAction(command, name, PathIconFactory.createNode(iconSize, iconSize, mode), accelerator);
		// Register in the map
		if (mode != null)
			modeActions.put(mode, action);
		return action;
	}
	
	
	
	public void addToolbarSeparator() {
		toolbar.toolbar.getItems().add(new Separator(Orientation.VERTICAL));
	}
	
	public void addToolbarCommand(final String name, final PathCommand command, final Node icon) {
		toolbar.toolbar.getItems().add(getActionButton(createCommandAction(command, name, icon, null), icon != null));
	}
	
	public void addToolbarButton(final Button button) {
		toolbar.toolbar.getItems().add(button);
	}
	
//	public void addToolbarCommand(final String name, final PathCommand command, final Icon icon) {
//		toolbar.toolbar.getItems().add(getActionButton(createCommandAction(command, name, PathIconFactory.createNode(icon), null), icon != null));
//	}
	

	
	
	protected Action createAction(GUIActions actionType) {
//		QuPathViewerPlus viewer = viewerManager.getActiveViewer();
		Action action;
		switch (actionType) {
		case BRIGHTNESS_CONTRAST:
			return createCommandAction(new BrightnessContrastCommand(this), "Brightness/Contrast", PathIconFactory.createNode(iconSize, iconSize, PathIconFactory.PathIcons.CONTRAST), new KeyCodeCombination(KeyCode.C, KeyCombination.SHIFT_DOWN));
		case LINE_TOOL:
			action = createSelectableCommandAction(new ToolSelectable(this, Modes.LINE), "Line tool", Modes.LINE, new KeyCodeCombination(KeyCode.L));
			action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.containsKey(Modes.LINE), tools));
			return action;
		case ELLIPSE_TOOL:
			action = createSelectableCommandAction(new ToolSelectable(this, Modes.ELLIPSE), "Ellipse tool", Modes.ELLIPSE, new KeyCodeCombination(KeyCode.O));
			action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.containsKey(Modes.ELLIPSE), tools));
			return action;
		case MOVE_TOOL:
			action = createSelectableCommandAction(new ToolSelectable(this, Modes.MOVE), "Move tool", Modes.MOVE, new KeyCodeCombination(KeyCode.M));
			action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.containsKey(Modes.MOVE), tools));
			return action;
		case POINTS_TOOL:
			action = createSelectableCommandAction(new ToolSelectable(this, Modes.POINTS), "Points tool", Modes.POINTS, new KeyCodeCombination(KeyCode.PERIOD));
			action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.containsKey(Modes.POINTS), tools));
			return action;
		case POLYGON_TOOL:
			action = createSelectableCommandAction(new ToolSelectable(this, Modes.POLYGON), "Polygon tool", Modes.POLYGON, new KeyCodeCombination(KeyCode.P));
			action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.containsKey(Modes.POLYGON), tools));
			return action;
		case BRUSH_TOOL:
			action = createSelectableCommandAction(new ToolSelectable(this, Modes.BRUSH), "Brush tool", Modes.BRUSH, new KeyCodeCombination(KeyCode.B));
			action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.containsKey(Modes.BRUSH), tools));
			return action;
		case RECTANGLE_TOOL:
			action = createSelectableCommandAction(new ToolSelectable(this, Modes.RECTANGLE), "Rectangle tool", Modes.RECTANGLE, new KeyCodeCombination(KeyCode.R));
			action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.containsKey(Modes.RECTANGLE), tools));
			return action;
		case WAND_TOOL:
			action = createSelectableCommandAction(new ToolSelectable(this, Modes.WAND), "Wand tool", Modes.WAND, new KeyCodeCombination(KeyCode.W));
			action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.containsKey(Modes.WAND), tools));
			return action;
		case SHOW_GRID:
			return createSelectableCommandAction(overlayOptions.showGridProperty(), "Show grid", PathIconFactory.PathIcons.GRID, new KeyCodeCombination(KeyCode.G, KeyCombination.SHIFT_DOWN));
		case SHOW_LOCATION:
			return createSelectableCommandAction(viewerDisplayOptions.showLocationProperty(), "Show cursor location", PathIconFactory.PathIcons.LOCATION, null);
		case SHOW_OVERVIEW:
			return createSelectableCommandAction(viewerDisplayOptions.showOverviewProperty(), "Show slide overview", PathIconFactory.PathIcons.OVERVIEW, null);
		case SHOW_SCALEBAR:
			return createSelectableCommandAction(viewerDisplayOptions.showScalebarProperty(), "Show scalebar", PathIconFactory.PathIcons.SHOW_SCALEBAR, null);
		case SHOW_ANALYSIS_PANEL:
			// I don't understand why registering a listener within the ShowAnalysisPanelSelectable constructor didn't work... but it didn't
			ShowAnalysisPanelSelectable temp = new ShowAnalysisPanelSelectable(pane, splitPane, analysisPanel, viewerManager, true);
			action = createSelectableCommandAction(temp.showPanelProperty(), "Show analysis panel", PathIconFactory.PathIcons.MEASURE, new KeyCodeCombination(KeyCode.A, KeyCombination.SHIFT_DOWN));
			action.selectedProperty().addListener((e, f, g) -> temp.setAnalysisPanelVisible(g));
			return action;
		case ZOOM_TO_FIT:
			return createSelectableCommandAction(zoomToFit, "Zoom to fit", PathIconFactory.PathIcons.ZOOM_TO_FIT, null);
		case ZOOM_IN:
			return createCommandAction(new ZoomCommand.ZoomIn(this), "Zoom in", PathIconFactory.createNode(iconSize, iconSize, PathIconFactory.PathIcons.ZOOM_IN), new KeyCodeCombination(KeyCode.PLUS));
		case ZOOM_OUT:
			return createCommandAction(new ZoomCommand.ZoomOut(this), "Zoom out", PathIconFactory.createNode(iconSize, iconSize, PathIconFactory.PathIcons.ZOOM_OUT), new KeyCodeCombination(KeyCode.MINUS));
		case COPY_VIEW:
			return createCommandAction(new CopyViewToClipboardCommand(this, false), "Copy view to clipboard", null, new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
		case COPY_WINDOW:
			return createCommandAction(new CopyViewToClipboardCommand(this, true), "Copy window to clipboard");
		case OPEN_IMAGE:
			return createCommandAction(new OpenCommand(this), "Open...", null, new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
		case OPEN_IMAGE_OR_URL:
			return createCommandAction(new OpenCommand(this, true), "Open URL...", null, new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		case SAVE_DATA_AS:
			return createCommandAction(new SerializeImageDataCommand(this, false), "Save As", null, new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));			
		case SAVE_DATA:
			return createCommandAction(new SerializeImageDataCommand(this, true), "Save", null, new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
		case SHOW_ANNOTATIONS:
			return createSelectableCommandAction(overlayOptions.showAnnotationsProperty(), "Show annotations", PathIconFactory.PathIcons.ANNOTATIONS, new KeyCodeCombination(KeyCode.A));
		case FILL_ANNOTATIONS:
			return createSelectableCommandAction(overlayOptions.fillAnnotationsProperty(), "Fill annotations", PathIconFactory.PathIcons.ANNOTATIONS_FILL, new KeyCodeCombination(KeyCode.F, KeyCombination.SHIFT_DOWN));	
		case SHOW_TMA_GRID:
			return createSelectableCommandAction(overlayOptions.showTMAGridProperty(), "Show TMA grid", PathIconFactory.PathIcons.TMA_GRID, new KeyCodeCombination(KeyCode.G));
		case SHOW_TMA_GRID_LABELS:
			return createSelectableCommandAction(overlayOptions.showTMACoreLabelsProperty(), "Show TMA grid labels");
		case SHOW_OBJECTS:
			return createSelectableCommandAction(overlayOptions.showObjectsProperty(), "Show detections", PathIconFactory.PathIcons.DETECTIONS, new KeyCodeCombination(KeyCode.H));
		case FILL_OBJECTS:
			return createSelectableCommandAction(overlayOptions.fillObjectsProperty(), "Fill detections", PathIconFactory.PathIcons.DETECTIONS_FILL, new KeyCodeCombination(KeyCode.F));	
		case SPECIFY_ANNOTATION:
			return createCommandAction(new SpecifyAnnotationCommand(this), "Specify annotation");
		case ANNOTATION_DUPLICATE:
			return createCommandAction(new DuplicateAnnotationCommand(this), "Duplicate annotation", null, new KeyCodeCombination(KeyCode.D, KeyCombination.SHIFT_DOWN));
		case GRID_SPACING:
			return createCommandAction(new SetGridSpacingCommand(overlayOptions), "Set grid spacing");
		case COUNTING_PANEL:
			return createCommandAction(new CountingPanelCommand(this), "Counting tool", PathIconFactory.createNode(iconSize, iconSize, Modes.POINTS), null);
		case CONVEX_POINTS:
			PathPrefs.showPointHullsProperty().addListener(e -> {
				for (QuPathViewer v : getViewers())
					v.repaint();
			});
			return createSelectableCommandAction(PathPrefs.showPointHullsProperty(), "Show point convex hull");
		case USE_SELECTED_COLOR:
			PathPrefs.useSelectedColorProperty().addListener(e -> {
				for (QuPathViewer v : getViewers())
					v.repaint();
			});
			return createSelectableCommandAction(PathPrefs.useSelectedColorProperty(), "Use selected color for points");
		case DETECTIONS_TO_POINTS:
			return createCommandAction(new DetectionsToPointsCommand(this), "Convert detections to points");
		case ROTATE_IMAGE:
			return createCommandAction(new RotateImageCommand(this), "Rotate image");
		case MINI_VIEWER:
			return createCommandAction(new MiniViewerCommand(this), "Show mini viewer");
		case TMA_SCORE_IMPORTER:
			return createCommandAction(new TMAScoreImportCommand(this), "Import TMA map");
		case TMA_RELABEL:
			return createCommandAction(new TMAGridRelabel(this), "Relabel TMA grid");
//		case OVERLAY_OPACITY:
//			return new OpacityAction(this, overlayOptions);
		case COLOR_DECONVOLUTION_REFINE:
			return createCommandAction(new EstimateStainVectorsCommand(this), "Estimate stain vectors");
//			return createCommandAction(new ColorDeconvolutionRefineAction(this), "Refine color deconvolution stains");
		
		case SHOW_COMMAND_LIST:
			return createCommandAction(new CommandListDisplayCommand(this), "Show command list", null, new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN));

		case SHOW_CELL_BOUNDARIES:
			return createSelectableCommandAction(new CellDisplaySelectable(overlayOptions, CellDisplayMode.BOUNDARIES_ONLY), "Cell boundaries only", PathIconFactory.createNode(iconSize, iconSize, PathIcons.CELL_ONLY), null);
		case SHOW_CELL_NUCLEI:
			return createSelectableCommandAction(new CellDisplaySelectable(overlayOptions, CellDisplayMode.NUCLEI_ONLY), "Nuclei only", PathIconFactory.createNode(iconSize, iconSize, PathIcons.CELL_NULCEI_BOTH), null);
		case SHOW_CELL_BOUNDARIES_AND_NUCLEI:
			return createSelectableCommandAction(new CellDisplaySelectable(overlayOptions, CellDisplayMode.NUCLEI_AND_BOUNDARIES), "Nuclei & cell boundaries", PathIconFactory.createNode(iconSize, iconSize, PathIcons.NUCLEI_ONLY), null);
		
		case RIGID_OBJECT_EDITOR:
			return createCommandAction(new RigidObjectEditorCommand(this), "Rotate annotation", null, new KeyCodeCombination(KeyCode.R, KeyCombination.SHIFT_DOWN, KeyCombination.ALT_DOWN, KeyCombination.SHORTCUT_DOWN));
			
		case SUMMARY_TMA:
			return createCommandAction(new SummaryMeasurementTableCommand(this, TMACoreObject.class), "Show TMA measurements");
//			return createCommandAction(new SummaryTableCommand(this, TMACoreObject.class), "Show TMA core measurements");
		case SUMMARY_DETECTIONS:
			return createCommandAction(new SummaryMeasurementTableCommand(this, PathDetectionObject.class), "Show detection measurements");
//			return createCommandAction(new SummaryTableCommand(this, PathDetectionObject.class), "Show detection measurements");
		case SUMMARY_ANNOTATIONS:
			return createCommandAction(new SummaryMeasurementTableCommand(this, PathAnnotationObject.class), "Show annotation measurements");
		
		case VIEW_TRACKER:
			return createCommandAction(new ViewTrackerCommand(this), "Show tracking panel", null, new KeyCodeCombination(KeyCode.T, KeyCombination.SHIFT_DOWN)); // TODO: Note: this only works with the original viewer
		case MEASUREMENT_MAP:
			return createCommandAction(new MeasurementMapCommand(this), "Show measurement maps", null, new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		case WORKFLOW_DISPLAY:
			return createCommandAction(new WorkflowDisplayCommand(this), "Show workflow command history", null, new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		case TMA_EXPORT_DATA:
			return createCommandAction(new TMAExporterCommand(this), "Export TMA data");
			
		case SHOW_LOG:
			return createCommandAction(new LogViewerCommand(this), "Show log", null, new KeyCodeCombination(KeyCode.L, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN));
			
		
		case DELETE_SELECTED_OBJECTS:
			return createCommandAction(new DeleteSelectedObjectsCommand(this), "Delete selected objects");
		case CLEAR_HIERARCHY:
			return createCommandAction(new DeleteObjectsCommand(this, null), "Delete all objects");
		case CLEAR_DETECTIONS:
			return createCommandAction(new DeleteObjectsCommand(this, PathDetectionObject.class), "Delete all detections");
		case CLEAR_TMA_CORES:
			return createCommandAction(new DeleteObjectsCommand(this, TMACoreObject.class), "Delete TMA grid");
		case CLEAR_ANNOTATIONS:
			return createCommandAction(new DeleteObjectsCommand(this, PathAnnotationObject.class), "Delete all annotations");
			
			
		case PROJECT_NEW:
			return createCommandAction(new ProjectCreateCommand(this), "Create project");
		case PROJECT_OPEN:
			return createCommandAction(new ProjectOpenCommand(this), "Open project");
		case PROJECT_CLOSE:
			return createCommandAction(new ProjectCloseCommand(this), "Close project");
		case PROJECT_SAVE:
			return createCommandAction(new ProjectSaveCommand(this), "Save project");
		case PROJECT_IMPORT_IMAGES:
			return createCommandAction(new ProjectImportImagesCommand(this), "Add images");
		case PROJECT_EXPORT_IMAGE_LIST:
			return createCommandAction(new ProjectExportImageListCommand(this), "Export image list");			
		case PROJECT_METADATA:
			return createCommandAction(new ProjectMetadataEditorCommand(this), "Edit project metadata");
			
		case PREFERENCES:
			return createCommandAction(new PreferencesCommand(this, prefsPanel), "Preferences...", PathIconFactory.createNode(iconSize, iconSize, PathIcons.COG), new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));
		
		case QUPATH_SETUP:
			return createCommandAction(new QuPathSetupCommand(this), "Show setup options");
			
		case TMA_ADD_NOTE:
			return createCommandAction(new TMAAddNote(this), "Add TMA note");
			
		case TRANSFER_ANNOTATION:
			return createCommandAction(new TransferAnnotationCommand(this), "Transfer last annotation", null, new KeyCodeCombination(KeyCode.E, KeyCombination.SHIFT_DOWN));
		case SELECT_ALL_ANNOTATION:
			return createCommandAction(new SelectAllAnnotationCommand(this), "Create full image annotation", null, new KeyCodeCombination(KeyCode.A, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN));
		
		case TOGGLE_SYNCHRONIZE_VIEWERS:
			return createSelectableCommandAction(viewerManager.synchronizeViewersProperty(), "Synchronize viewers", (Node)null, new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.ALT_DOWN, KeyCombination.SHORTCUT_DOWN));
			
		case UNDO:
			Action actionUndo = new Action("Undo", e -> undoRedoManager.undoOnce());
			actionUndo.disabledProperty().bind(undoRedoManager.canUndo().not());
			actionUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCodeCombination.SHORTCUT_DOWN));
			return actionUndo;
		case REDO:
			Action actionRedo = new Action("Redo", e -> undoRedoManager.redoOnce());
			actionRedo.disabledProperty().bind(undoRedoManager.canRedo().not());
			actionRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_DOWN));
			return actionRedo;
			
		default:
			return null;
		}
	}
	
	
	public void setModeSwitchingEnabled(final boolean enabled) {
		modeSwitchEnabled = enabled;
		for (Action action : modeActions.values())
			action.setDisabled(!enabled);
	}
	
	public boolean isModeSwitchingEnabled() {
		return modeSwitchEnabled;
	}

	
	private void initializeTools() {
		// Create tools
		putToolForMode(Modes.MOVE, new MoveTool(this));
		putToolForMode(Modes.RECTANGLE, new RectangleTool(this));
		putToolForMode(Modes.ELLIPSE, new EllipseTool(this));
		putToolForMode(Modes.LINE, new LineTool(this));
		putToolForMode(Modes.POINTS, new PointsTool(this));
		putToolForMode(Modes.POLYGON, new PolygonTool(this));
		putToolForMode(Modes.BRUSH, new BrushTool(this));
	}
	
	
	/**
	 * Set a PathTool for use with a specified Mode.
	 * 
	 * This will replace the default tool for that mode.  The purpose of this method is to allow 
	 * 'better' implementations of the tools to be implemented elsewhere (e.g. in extensions that 
	 * require optional dependencies).
	 * 
	 * @param mode
	 * @param tool
	 */
	public void putToolForMode(final Modes mode, final PathTool tool) {
		tools.put(mode, tool);
	}
	
	
	@Override
	public void setMode(Modes mode) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> setMode(mode));
			return;
		}
		if (!modeSwitchEnabled) {
			logger.warn("Mode switching currently disabled - cannot change to {}", mode);
			return;
		}
		this.mode = mode;
		// Ensure actions are set appropriately
		Action action = modeActions.get(mode);
		if (action != null)
			action.setSelected(true);
		
		activateTools(getViewer());
		
		if (mode == Modes.POINTS)
			getAction(GUIActions.COUNTING_PANEL).handle(null);
		
//		for (QuPathViewerPlus viewer : viewerManager.getViewers())
//			viewer.setMode(mode);
//		getViewer().setMode(mode);
		updateCursor();
	}
	
	
	/**
	 * Request that a specified Jar file be added to the extension classpath.
	 * 
	 * Note: This is really intended for dependencies that should remain where they are 
	 * on disk (e.g. because they are included in other applications).
	 * 
	 * Jars containing QuPath extensions should be copied directly into the extensions 
	 * directory instead.
	 * 
	 * @param file
	 * @return
	 */
	public boolean addExtensionJar(final File file) {
		return extensionClassLoader.addJAR(file);
	}
	
	
	
	/**
	 * Set the cursor for all the viewers.
	 * 
	 * @param cursor
	 */
	protected void updateCursor() {
		if (stage == null || stage.getScene() == null)
			return;
		switch(getMode()) {
		case MOVE:
			updateCursor(Cursor.HAND);
			break;
		default:
			updateCursor(Cursor.DEFAULT);
		}		
	}
	
	/**
	 * Set the cursor for all the viewers.
	 * 
	 * @param cursor
	 */
	protected void updateCursor(final Cursor cursor) {
		for (QuPathViewer viewer : getViewers())
			viewer.getView().setCursor(cursor);
	}

	
	/**
	 * Get a reference to the current ScriptEditor (which may or may not be open at the moment).
	 * 
	 * @return
	 */
	public ScriptEditor getScriptEditor() {
		if (scriptEditor == null) {
			scriptEditor = new DefaultScriptEditor(this);
		}
		return scriptEditor;
	}
	
	
	/**
	 * Set a new ScriptEditor, which will be used from now on.
	 * 
	 * @param scriptEditor
	 */
	public void setScriptEditor(final ScriptEditor scriptEditor) {
		this.scriptEditor = scriptEditor;
	}
	
	
	
	/**
	 * Repaint the viewer.  In the future, if multiple viewers are registered with the GUI 
	 * (not yet possible) then this may result in all being repainted.
	 */
	public void repaintViewers() {
		viewerManager.repaintViewers();
	}
	

	@Override
	public Modes getMode() {
		return mode;
	}
	
	public MenuItem getActionMenuItem(GUIActions actionType) {
		Action action = getAction(actionType);
		return createMenuItem(action);
	}
	
	public static CheckBox createCheckBox(final Action action) {
		return ActionUtils.createCheckBox(action);
	}
	
	
	public static MenuItem createMenuItem(final Action action) {
		return bindVisibilityForExperimental(ActionUtils.createMenuItem(action));
	}
	
	public static CheckMenuItem createCheckMenuItem(final Action action) {
		return bindVisibilityForExperimental(ActionUtils.createCheckMenuItem(action));
	}
	
	/**
	 * Flag to indicate that menus are being initialized.
	 * All menu items should be 'visible' during this time, and the system menubar shouldn't be used.
	 * This is an attempt to workaround a Java issue (at least on OS X) where IndexOutOfBoundsExceptions occur 
	 * when messing around with the system menubar with visible/invisible items.
	 */
	private static BooleanProperty initializingMenus = new SimpleBooleanProperty(false);
	
	private static BooleanBinding showExperimentalOptions = PathPrefs.showExperimentalOptionsProperty().or(initializingMenus);
	private static BooleanBinding showTMAOptions = PathPrefs.showTMAOptionsProperty().or(initializingMenus);
	private static BooleanBinding showLegacyOptions = PathPrefs.showLegacyOptionsProperty().or(initializingMenus);
	
	private static <T extends MenuItem> T bindVisibilityForExperimental(final T menuItem) {
		String text = menuItem.getText().toLowerCase().trim();
		if (text.equals("experimental") || text.endsWith("experimental)"))
			menuItem.visibleProperty().bind(showExperimentalOptions);
		else if (text.equals("tma") || text.endsWith("tma)"))
			menuItem.visibleProperty().bind(showTMAOptions);
		else if (text.equals("legacy") || text.endsWith("legacy)"))
			menuItem.visibleProperty().bind(showLegacyOptions);
		return menuItem;
	}
	
	
	private static RadioMenuItem createRadioMenuItem(final Action action, final ToggleGroup group) {
		RadioMenuItem menuItem = ActionUtils.createRadioMenuItem(action);
		menuItem.setToggleGroup(group);
		return bindVisibilityForExperimental(menuItem);
	}
	

	public MenuItem getActionCheckBoxMenuItem(GUIActions actionType, ToggleGroup group) {
		Action action = getAction(actionType);
		if (group != null)
			return createRadioMenuItem(action, group);
		else
			return createCheckMenuItem(action);
	}
	
	public MenuItem getActionCheckBoxMenuItem(GUIActions actionType) {
		return getActionCheckBoxMenuItem(actionType, null);
	}
	
	public CheckBox getActionCheckBox(GUIActions actionType, boolean hideActionText) {
		// Not sure why we have to bind?
		Action action = getAction(actionType);
		CheckBox button = createCheckBox(action);
		button.selectedProperty().bindBidirectional(action.selectedProperty());
		if (hideActionText) {
			button.setTooltip(new Tooltip(button.getText()));
			button.setText("");
		}
		return button;
	}
	
	public ToggleButton getActionToggleButton(GUIActions actionType, boolean hideActionText, ToggleGroup group) {
		Action action = getAction(actionType);
		ToggleButton button = ActionUtils.createToggleButton(action, hideActionText ? ActionTextBehavior.HIDE : ActionTextBehavior.SHOW);
		if (hideActionText && action.getText() != null) {
			Tooltip.install(button, new Tooltip(action.getText()));
//			button.setTooltip(new Tooltip(action.getText()));
////			button.setText(null);
		}
		
		// Internally, ControlsFX duplicates graphics (or gives up) because Nodes can't appear multiple times the scene graph
		// Consequently, we need to bind changes to the text fill here so that they filter through
		if (button.getGraphic() instanceof Glyph) {
			((Glyph)button.getGraphic()).textFillProperty().bind(((Glyph)action.getGraphic()).textFillProperty());
		}
		
		if (group != null)
			button.setToggleGroup(group);
		return button;
	}

	public ToggleButton getActionToggleButton(GUIActions actionType, boolean hideActionText) {
		return getActionToggleButton(actionType, hideActionText, null);
	}
	
	public ToggleButton getActionToggleButton(GUIActions actionType, boolean hideActionText, ToggleGroup group, boolean isSelected) {
		ToggleButton button = getActionToggleButton(actionType, hideActionText, group);
		return button;
	}

	public ToggleButton getActionToggleButton(GUIActions actionType, boolean hideActionText, boolean isSelected) {
		return getActionToggleButton(actionType, hideActionText, null, isSelected);
	}
	
	public Button getActionButton(GUIActions actionType, boolean hideActionText) {
		return getActionButton(getAction(actionType), hideActionText);
	}
	
	public Button getActionButton(Action action, boolean hideActionText) {
		Button button = ActionUtils.createButton(action, hideActionText ? ActionTextBehavior.HIDE : ActionTextBehavior.SHOW);
		if (hideActionText && action.getText() != null) {
			Tooltip.install(button, new Tooltip(action.getText()));
		}
		return button;
	}
	
	/**
	 * Get an Action associated by this GUI.
	 * It can be useful if associated buttons etc. or an alternative layout are being created elsewhere.
	 * 
	 * @param actionType
	 * @return
	 */
	public Action getAction(GUIActions actionType) {
		Action action = actionMap.get(actionType);
		if (action == null) {
			action = createAction(actionType);
			
			if (action.getAccelerator() != null)
				mapActions.put(action.getAccelerator(), action);
			
			actionMap.put(actionType, action);
		}
		return action;
	}

	
	private ObservableMap<Modes, PathTool> tools = FXCollections.observableMap(new HashMap<>());
	
	
	private Control createAnalysisPanel() {
		TabPane tabbedPanel = new TabPane();
		tabbedPanel.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		projectBrowser = new ProjectBrowser(this);

		tabbedPanel.getTabs().add(new Tab("Project", projectBrowser.getPane()));
		PathImageDetailsPanel pathImageDetailsPanel = new PathImageDetailsPanel(this);
		tabbedPanel.getTabs().add(new Tab("Image", pathImageDetailsPanel.getContainer()));

		final PathAnnotationPanel panelAnnotations = new PathAnnotationPanel(this);
		SplitPane splitAnnotations = new SplitPane();
		splitAnnotations.setOrientation(Orientation.VERTICAL);
		splitAnnotations.getItems().addAll(
				panelAnnotations.getPane(),
				new SelectedMeasurementTableView(this).getTable());
		tabbedPanel.getTabs().add(new Tab("Annotations", splitAnnotations));

		final PathObjectHierarchyView paneHierarchy = new PathObjectHierarchyView(this);
		SplitPane splitHierarchy = new SplitPane();
		splitHierarchy.setOrientation(Orientation.VERTICAL);
		splitHierarchy.getItems().addAll(
				paneHierarchy.getPane(),
				new SelectedMeasurementTableView(this).getTable());
		tabbedPanel.getTabs().add(new Tab("Hierarchy", splitHierarchy));
		
		// Bind the split pane dividers to create a more consistent appearance
		splitAnnotations.getDividers().get(0).positionProperty().bindBidirectional(
				splitHierarchy.getDividers().get(0).positionProperty()
				);

		WorkflowPanel workflowPanel = new WorkflowPanel(this);
		tabbedPanel.getTabs().add(new Tab("Workflow", workflowPanel.getPane()));
		
//		PathObjectHierarchyPanel pathObjectHierarchyPanel = new PathObjectHierarchyPanel(this);
//		tabbedPanel.getTabs().add(new Tab("Hierarchy", pathObjectHierarchyPanel.getPane()));
		
		return tabbedPanel;
	}
	
	
	
	
	
	/**
	 * Create a default list model for storing available PathClasses.
	 * 
	 * @return
	 */
	public ObservableList<PathClass> getAvailablePathClasses() {
		return availablePathClasses;
	}
	
	
	protected void setInitialLocationAndMagnification(final QuPathViewer viewer) {
		if (viewer == null || viewer.getServer() == null)
			return;
		// Set to the highest magnification that contains the full image to start
		int serverWidth = viewer.getServer().getWidth();
		int serverHeight = viewer.getServer().getHeight();
		int w = viewer.getWidth() - 20;
		int h = viewer.getHeight() - 20;
		double xScale = (double)serverWidth / w;
		double yScale = (double)serverHeight / h;
		viewer.setDownsampleFactor(Math.max(1, Math.max(xScale, yScale)));
		viewer.centerImage();
	}
	
	
	public MenuBar getMenuBar() {
		return menuBar;
	}

	/**
	 * Get a reference to an existing menu, optionally creating a new menu if it is not present.
	 * 
	 * @param name
	 * @param createMenu
	 * @return
	 */
	public Menu getMenu(final String name, final boolean createMenu) {
		MenuBar menuBar = getMenuBar();
		if (menuBar == null)
			return null;
		
		Menu menuCurrent = null;
		for (String n : name.split(">")) {
			if (menuCurrent == null) {
				for (Menu menu : menuBar.getMenus()) {
					if (n.equals(menu.getText())) {
						menuCurrent = menu;
						break;
					}
				}
				if (menuCurrent == null) {
					if (createMenu) {
						menuCurrent = new Menu(n.trim());
						// Make sure we don't replace the 'Help' menu at the end
						List<Menu> menus = menuBar.getMenus();
						if (!menus.isEmpty() && "Help".equals(menus.get(menus.size()-1).getText()))
							menus.add(menus.size()-1, menuCurrent);
						else
							menus.add(menuCurrent);
					} else
						return null;
				}
			} else {
				List<MenuItem> searchItems = menuCurrent.getItems();
				menuCurrent = null;
				for (MenuItem menuItem : searchItems) {
					if (menuItem instanceof Menu && (menuItem.getText().equals(n) || menuItem.getText().equals(n.trim()))) {
						menuCurrent = (Menu)menuItem;
						break;
					}
				}
				if (menuCurrent == null) {
					if (createMenu) {
						menuCurrent = new Menu(n.trim());
						searchItems.add(menuCurrent);
					} else
						return null;
				}				
			}
		}
		return menuCurrent;
	}
	
	
	public MenuItem getMenuItem(String itemName) {
		Collection<MenuItem> menuItems;
		int ind = itemName.lastIndexOf(">");
		if (ind >= 0) {
			Menu menu = getMenu(itemName.substring(0, ind), false);
			if (menu == null) {
				logger.warn("No menu found for {}", itemName);
				return null;
			}
			menuItems = menu.getItems();
			itemName = itemName.substring(ind+1);
		} else {
			menuItems = new HashSet<>();
			for (Menu menu : getMenuBar().getMenus())
				menuItems.addAll(menu.getItems());
		}
		for (MenuItem menuItem : menuItems) {
			if (itemName.equals(menuItem.getText()))
				return menuItem;
		}
		logger.warn("No menu item found for {}", itemName);
		return null;
	}
	
	
	static Menu createMenu(final String name) {
		return bindVisibilityForExperimental(new Menu(name));
	}

	
	public Stage getStage() {
		return stage;
	}
	
	static class ToolBarComponent {
		
		private double lastMagnification = Double.NaN;
		
		private Label labelMag = new Label("1x");
		private Tooltip tooltipMag = new Tooltip("Current magnification - double-click to set");
		private ToolBar toolbar = new ToolBar();
		
		ToolBarComponent(final QuPathGUI qupath) {
			
			labelMag.setTooltip(tooltipMag);
			labelMag.setPrefWidth(60);
			labelMag.setMinWidth(60);
			labelMag.setMaxWidth(60);
			labelMag.setTextAlignment(TextAlignment.CENTER);
			
			labelMag.setOnMouseClicked(e -> {

					QuPathViewer viewer = qupath.getViewer();
					if (viewer == null || e.getClickCount() != 2 || !viewer.hasServer())
						return;
					double fullMagnification = viewer.getServer().getMagnification();
					boolean hasMagnification = !Double.isNaN(fullMagnification);
					ParameterList params = new ParameterList();
					if (hasMagnification) {
						double defaultValue = Math.rint(viewer.getMagnification() * 1000) / 1000;
						params.addDoubleParameter("magnification", "Enter magnification", defaultValue);
					} else {
						double defaultValue = Math.rint(viewer.getDownsampleFactor() * 1000) / 1000;
						params.addDoubleParameter("downsample", "Enter downsample factor", defaultValue);			
					}
//					ParameterPanelFX panel = new ParameterPanelFX(params);
//					panel.getPane().setPadding(new Insets(10, 10, 10, 10));
					
					if (!DisplayHelpers.showParameterDialog("Set magnification", params))
						return;
					
					if (hasMagnification) {
						double mag = params.getDoubleParameterValue("magnification");
						if (!Double.isNaN(mag))
							viewer.setMagnification(mag);
					} else {
						double downsample = params.getDoubleParameterValue("downsample");
						if (!Double.isNaN(downsample))
							viewer.setDownsampleFactor(downsample);
					}
				
			});
			
			// Show analysis panel
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_ANALYSIS_PANEL, true, null, true));
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
//			GlyphFont fontAwesome = GlyphFontRegistry.font("FontAwesome");
//			toolbar.getItems().add(new Button("", fontAwesome.create(FontAwesome.Glyph.ARROWS).color(Color.GRAY)));
//			toolbar.getItems().add(new Button("", fontAwesome.create(FontAwesome.Glyph.MAGIC).color(Color.GRAY)));
//			toolbar.getItems().add(new Button("", fontAwesome.create(FontAwesome.Glyph.ANGLE_DOUBLE_LEFT).color(Color.GRAY)));
			
			ToggleGroup groupTools = new ToggleGroup();
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.MOVE_TOOL, true, groupTools, true));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.RECTANGLE_TOOL, true, groupTools, false));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.ELLIPSE_TOOL, true, groupTools, false));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.LINE_TOOL, true, groupTools, false));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.POLYGON_TOOL, true, groupTools, false));
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			ToggleButton btnBrush = qupath.getActionToggleButton(GUIActions.BRUSH_TOOL, true, groupTools, false);
			toolbar.getItems().add(btnBrush);
			btnBrush.setOnMouseClicked(e -> {
				if (e.isPopupTrigger() || e.getClickCount() < 2)
					return;

				final ParameterList params = new ParameterList()
						.addDoubleParameter("brushSize", "Brush diameter", PathPrefs.getBrushDiameter(), "pixels")
						.addBooleanParameter("brushScaleMag", "Scale brush size by magnification", PathPrefs.getBrushScaleByMag())
						.addBooleanParameter("brushCreateNew", "Create new objects when painting", PathPrefs.getBrushCreateNewObjects());
				final ParameterPanelFX panel = new ParameterPanelFX(params);
				panel.addParameterChangeListener(new ParameterChangeListener() {

					@Override
					public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
						if ("brushSize".equals(key)) {
							double radius = params.getDoubleParameterValue("brushSize");
							if (!Double.isNaN(radius)) {
								PathPrefs.setBrushDiameter((int)Math.round(Math.max(1, radius)));
							}
						} else if ("brushCreateNew".equals(key))
							PathPrefs.setBrushCreateNewObjects(params.getBooleanParameterValue("brushCreateNew"));
						else if ("brushScaleMag".equals(key))
							PathPrefs.setBrushScaleByMag(params.getBooleanParameterValue("brushScaleMag"));
					}

				});
				
				DisplayHelpers.showConfirmDialog("Brush tool options", panel.getPane());
				
//				dialog = new JDialog(qupath.getFrame(), "Brush tool options");
//				dialog.add(panel);
//				panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
//				dialog.pack();
//				dialog.setLocationRelativeTo(null);
//				dialog.setAlwaysOnTop(true);
//				dialog.setModal(false);
//				dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
//				dialog.setVisible(true);
			});
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			ToggleButton toggleWand = qupath.getActionToggleButton(GUIActions.WAND_TOOL, true, groupTools, false);
//			toggleWand.visibleProperty().bind(Bindings.not(qupath.getAction(GUIActions.WAND_TOOL).disabledProperty()));
			toolbar.getItems().add(toggleWand);
//			if (qupath.tools.containsKey(Modes.WAND))
//				toolbar.getItems().add(toggleWand);
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.POINTS_TOOL, true, groupTools, false));
//			toolbar.getItems().add(getActionToggleButton(GUIActions.POINTS_TOOL, true, groupTools, false));
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));

//			toolbar.getItems().add(getActionToggleButton(GUIActions.USE_COLOR_LUT, true, false));
			toolbar.getItems().add(qupath.getActionButton(GUIActions.BRIGHTNESS_CONTRAST, true));
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
//			toolbar.getItems().add(getActionButton(new SetDownsampleAction(viewer, "1%", 100), false, false));
//			toolbar.getItems().add(getActionButton(new SetDownsampleAction(viewer, "10%", 10), false, false));
//			toolbar.getItems().add(getActionButton(new SetDownsampleAction(viewer, "50%", 2), false, false));
//			toolbar.getItems().add(getActionButton(new SetDownsampleAction(viewer, "100%", 1), false, false));
//			toolbar.getItems().add(getActionButton(new SetDownsampleAction(viewer, "400%", 0.25), false, false));
	// //		toolbar.getItems().add(sliderMag);
			toolbar.getItems().add(labelMag);
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.ZOOM_TO_FIT, true, false));

			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			OverlayOptions overlayOptions = qupath.overlayOptions;
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_ANNOTATIONS, true, overlayOptions.getShowAnnotations()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_TMA_GRID, true, overlayOptions.getShowTMAGrid()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_OBJECTS, true, overlayOptions.getShowObjects()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.FILL_OBJECTS, true, overlayOptions.getFillObjects()));

			final Slider sliderOpacity = new Slider(0, 1, 1);
			sliderOpacity.valueProperty().bindBidirectional(overlayOptions.opacityProperty());
			sliderOpacity.setTooltip(new Tooltip("Overlay opacity"));
			toolbar.getItems().add(sliderOpacity);
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			
			Button btnMeasure = new Button();
			btnMeasure.setGraphic(PathIconFactory.createNode(iconSize, iconSize, PathIcons.TABLE));
			btnMeasure.setTooltip(new Tooltip("Show measurements table"));
			ContextMenu popupMeasurements = new ContextMenu();
			popupMeasurements.getItems().addAll(
					qupath.getActionMenuItem(GUIActions.SUMMARY_TMA),
					qupath.getActionMenuItem(GUIActions.SUMMARY_ANNOTATIONS),
					qupath.getActionMenuItem(GUIActions.SUMMARY_DETECTIONS)
					);
			btnMeasure.setOnMouseClicked(e -> {
				popupMeasurements.show(btnMeasure, e.getScreenX(), e.getScreenY());
			});
			
			toolbar.getItems().add(btnMeasure);
			
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			
			// TODO: Check if viewer really needed...
			QuPathViewerPlus viewer = qupath.getViewer();
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_OVERVIEW, true, viewer.isOverviewVisible()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_LOCATION, true, viewer.isLocationVisible()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_SCALEBAR, true, viewer.isScalebarVisible()));
			toolbar.getItems().add(qupath.getActionToggleButton(GUIActions.SHOW_GRID, true, overlayOptions.getShowGrid()));
			
			// Add preferences button
			toolbar.getItems().add(new Separator(Orientation.VERTICAL));
			toolbar.getItems().add(qupath.getActionButton(GUIActions.PREFERENCES, true));
		}
		
		
		public void updateMagnificationDisplay(final QuPathViewer viewer) {
			if (viewer == null || labelMag == null)
				return;
			// Update magnification info
			double mag = viewer.getMagnification();
			if (Math.abs(mag - lastMagnification) / mag < 0.0001)
				return;
			lastMagnification = mag;
			Platform.runLater(() -> {
				labelMag.setText(DisplayHelpers.getMagnificationString(viewer));
//				labelMag.setTextAlignment(TextAlignment.CENTER);
			});
		}
		
		public Node getComponent() {
			return toolbar;
		}
		
		
	}
	
	
		
	
	
	private void updateMagnificationString() {
		if (toolbar == null)
			return;
		toolbar.updateMagnificationDisplay(getViewer());
	}
	
	
	/**
	 * Set zoom to fit for a specified viewer.
	 * 
	 * If the viewer is null, the active viewer will be used instead.
	 * 
	 * @param viewer
	 * @param zoomToFit
	 */
	public void setZoomToFit(final QuPathViewer viewer, final boolean zoomToFit) {
		// If we are turning off zoom to fit, make sure the slider is updated suitable
		QuPathViewer viewer2 = viewer == null ? getViewer() : viewer;
		if (viewer2 == null)
			return;
		viewer2.setZoomToFit(zoomToFit);
		if (zoomToFit && viewer2 == getViewer())
			updateMagnificationString();
	}
	
	
	/**
	 * Trigger an update to the title of the Stage.
	 */
	public void updateTitle() {
		if (stage == null)
			return;
		String name = "QuPath";
		if (versionString != null)
			name = name + " (" + versionString + ")";
		ImageData<?> imageData = getImageData();
		if (imageData == null || imageData.getServer() == null)
			stage.setTitle(name);
		else {
			// Try to set name based on project entry
			if (project.get() != null) {
				String path = imageData.getServerPath();
				ProjectImageEntry<?> entry = project.get().getImageEntry(path);
				if (entry != null) {
					stage.setTitle(name + " - " + entry.getImageName());
					return;
				}
			}			
			// Set name based on server instead
			stage.setTitle(name + " - " + imageData.getServer().getShortServerName());
		}
	}
	
	/**
	 * Get a String representing the QuPath version & build time.
	 * 
	 * @return
	 */
	public String getBuildString() {
		return buildString;
	}
	
	
	private void fireImageDataChangedEvent(final ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {		
		// Ensure we have the right tooltip for magnification
		if (toolbar != null && toolbar.tooltipMag != null) {
			if (imageDataNew == null)
				toolbar.tooltipMag.setText("Magnification");
			else if (!Double.isNaN(imageDataNew.getServer().getMagnification()))
				toolbar.tooltipMag.setText("Current magnification - double-click to edit");
			else
				toolbar.tooltipMag.setText("Current downsample value - double-click to edit");
		}

		// A bit awkward, this... but make sure the extended scripting helper static class knows what's happened
		QPEx.setBatchImageData(imageDataNew);
		
		// Notify listeners
		for (ImageDataChangeListener<BufferedImage> listener : listeners) {
			listener.imageDataChanged(this, imageDataOld, imageDataNew);
		}
		
		// Update title, if required
		if (stage != null) {
			updateTitle();		
		}
	}
	
	
	/**
	 * Set the active project, triggering any necessary GUI updates.
	 * 
	 * @param project
	 */
	public void setProject(final Project<BufferedImage> project) {
		if (this.project.get() == project)
			return;
		
		// Store in recent list, if needed
		File file = project == null ? null : project.getFile();
		if (file != null) {
			ObservableList<File> list = PathPrefs.getRecentProjectList();			
			if (list.contains(file)) {
				if (!file.equals(list.get(0))) {
					list.remove(file);
					list.add(0, file);
				}
			} else
				list.add(0, file);
		}
		
		this.project.set(project);
		this.projectBrowser.setProject(project);
		
		// Enable disable actions
		updateProjectActionStates();
		
		// Update the PathClass list, if necessary
		if (project != null) {
			List<PathClass> pathClasses = project.getPathClasses();
			if (pathClasses.isEmpty()) {
				// Update the project according to the specified PathClasses
				project.setPathClasses(getAvailablePathClasses());
			} else {
				// Update the available classes
				getAvailablePathClasses().setAll(pathClasses);
			}
		}
		
		// Ensure we have the required directories
//		getProjectClassifierDirectory(true);
//		getProjectScriptsDirectory(true);
		
		logger.info("Project set to {}", project);
	}
	
	
	private void updateProjectActionStates() {
		Project<?> project = getProject();
		getAction(GUIActions.PROJECT_CLOSE).setDisabled(project == null);
		getAction(GUIActions.PROJECT_IMPORT_IMAGES).setDisabled(project == null);
		getAction(GUIActions.PROJECT_EXPORT_IMAGE_LIST).setDisabled(project == null);
		getAction(GUIActions.PROJECT_METADATA).setDisabled(project == null);
		
		// Ensure the URLHelpers status is appropriately set
		FileSystem fileSystem = null;
		String fileSystemRoot = null;
		if (project != null && PathPrefs.useProjectImageCache()) {
			File cache = new File(project.getBaseDirectory(), "cache");
			if (!cache.exists())
				cache.mkdirs();
			try {
				// Works for zip files - but these aren't flushed until closing the cache, so result in memory leak (and horribly shutdown performance)
//				cache = new File(cache, "QuPath image cache.zip");
//				URI cachePath = URI.create("jar:" + new File(cache, "/!/tiles").toURI().toString());
//				fileSystem = FileSystems.newFileSystem(cachePath, Collections.singletonMap("create", "true"));
//				fileSystemRoot = "";
				fileSystem = FileSystems.getDefault();
				fileSystemRoot = cache.getAbsolutePath();
			} catch (Exception e) {
				logger.error("Error creating file system", e);
			}
		}
		FileSystem fileSystemOld = URLHelpers.getCacheFileSystem();
		if (fileSystemOld == fileSystem)
			return;
		if (fileSystemOld != null && fileSystemOld != FileSystems.getDefault()) {
			try {
				fileSystemOld.close();
			} catch (IOException e) {
				logger.error("Error closing file system", e);
			}
		}
		URLHelpers.setCacheFileSystem(fileSystem, fileSystemRoot);
	}
	
	
	public ReadOnlyObjectProperty<Project<BufferedImage>> projectProperty() {
		return project;
	}
	
	public Project<BufferedImage> getProject() {
		return project.get();
	}

	/**
	 * Get a shared DialogHelper.
	 * 
	 * Generally it's better to use getDialogHelper where a QuPathGUI instance is available, since it will have
	 * its parent window set.
	 * 
	 * @return
	 */
	public static DialogHelper getSharedDialogHelper() {
		if (getInstance() == null)
			return getDialogHelper(null);
		else
			return getDialogHelper(getInstance().getStage());
	}
	
	/**
	 * Get a DialogHelper with a specified Window as a parent.
	 * 
	 * This will return a different DialogHelper for each different Window parent,
	 * but the same DialogHelper for the same Windows.
	 * 
	 * @param parent
	 * @return
	 */
	public static DialogHelper getDialogHelper(final Window parent) {
		if (parent == null)
			return standaloneDialogHelper;
		DialogHelper helper = dialogHelpers.get(parent);
		if (helper == null) {
			helper = new DialogHelperFX(parent);
			dialogHelpers.put(parent, helper);
		}
		return helper;
	}
	
	/**
	 * Call getDialogHelper for the Window containing a specified Node.
	 * 
	 * This is a convenience method when a dialog is related to a Node,
	 * so there isn't a need to get a reference to its parent Window manually.
	 * 
	 * @param node
	 * @return
	 */
	public static DialogHelper getDialogHelperForParent(final Node node) {
		Window window = null;
		if (node != null && node.getScene() != null)
			window = node.getScene().getWindow();
		return getDialogHelper(window);
	}
	
	public DialogHelper getDialogHelper() {
		return getDialogHelper(getStage());
	}

	static double getProportion(final double val, final double min, final double max) {
		return (val - min) / (max - min);
	}
	
	
	
	
	
	static class ShowAnalysisPanelSelectable {
		
		private BorderPane parent;
		private SplitPane splitPane;
		private Control analysisPanel;
		private MultiviewManager manager;
		protected double lastDividerLocation;
		
		private BooleanProperty showPanel = new SimpleBooleanProperty();
		
		public ShowAnalysisPanelSelectable(final BorderPane parent, final SplitPane splitPane, final Control analysisPanel, final MultiviewManager manager, final boolean defaultVisible) {
			this.parent = parent;
			this.splitPane = splitPane;
			this.analysisPanel = analysisPanel;
			this.manager = manager;
			
			showPanel.setValue(parent.getCenter() == splitPane);
			
//			// This didn't get fired when it was used...
//			showPanel.addListener((v, o, n) -> {
//				System.err.println("Property changes");
//				setAnalysisPanelVisible(n);
//			});
		}
		
		void setAnalysisPanelVisible(boolean visible) {
			if (visible) {
				if (analysisPanelVisible())
					return;
				splitPane.getItems().setAll(analysisPanel, manager.getNode());
				splitPane.setDividerPosition(0, lastDividerLocation);
				parent.setCenter(splitPane);
			} else {
				if (!analysisPanelVisible())
					return;
				lastDividerLocation = splitPane.getDividers().get(0).getPosition();
				parent.setCenter(manager.getNode());				
			}
		}
			
		public boolean analysisPanelVisible() {
			return parent.getCenter() == splitPane;
		}

//		public void setShowPanel(boolean selected) {
//			System.err.println("Property changes instead");
//			this.showPanel.setValue(selected);
//		}
//		
//		public boolean getShowPanel() {
//			System.err.println("Property value requested");
//			return showPanel.get();
//		}
		
		public BooleanProperty showPanelProperty() {
			return showPanel;
		}
		
		
	}



	@Override
	public ImageData<BufferedImage> getImageData() {
		return getViewer() == null ? null : getViewer().getImageData();
	}
	
	
	/**
	 * Property representing the viewer currently active.
	 * 
	 * @return
	 */
	public ReadOnlyObjectProperty<QuPathViewerPlus> viewerProperty() {
		return viewerManager.activeViewerProperty();
	}
	
	
	
	class MultiviewManager implements QuPathViewerListener, ViewerManager<QuPathViewerPlus> {
		
		private List<QuPathViewerPlus> viewers = new ArrayList<>();
		private SimpleObjectProperty<QuPathViewerPlus> activeViewerProperty = new SimpleObjectProperty<>();
		
		private SplitPaneGrid splitPaneGrid;
		
		private PathObject lastAnnotationObject = null;
//		private ROI lastROI = null;
		
		final int borderWidth = 4;
		final Color colorTransparent = Color.TRANSPARENT;
		final Color colorBorder = Color.rgb(180, 0, 0, 0.5);
		
		final Border borderTransparent = new Border(new BorderStroke(colorTransparent, null, null, null));
		final Border borderSelected = new Border(new BorderStroke(colorBorder, BorderStrokeStyle.SOLID, null, null));
		
//		private boolean aligningCores = false;
		private BooleanProperty synchronizeViewers = new SimpleBooleanProperty(true);
		private double lastX = Double.NaN;
		private double lastY = Double.NaN;
		private double lastDownsample = Double.NaN;
		private double lastRotation = Double.NaN;
		
		public MultiviewManager(final QuPathViewerPlus defaultViewer) {
			this.viewers.add(defaultViewer);
			if (defaultViewer != null)
				defaultViewer.addViewerListener(this);
			setActiveViewer(defaultViewer);
			splitPaneGrid = new SplitPaneGrid(defaultViewer.getView());
		}
		
		@Override
		public List<QuPathViewerPlus> getViewers() {
			return Collections.unmodifiableList(viewers);
		}
		
		/**
		 * Return a list of viewers which currently have an ImageData object set
		 * @return
		 */
		public List<QuPathViewerPlus> getOpenViewers() {
			List<QuPathViewerPlus> openViewers = new ArrayList<>();
			for (QuPathViewerPlus v : viewers) {
				if (v.getImageData() != null)
					openViewers.add(v);
			}
			return openViewers;
		}
		
		
		private void setActiveViewer(final QuPathViewerPlus viewer) {
			QuPathViewerPlus previousActiveViewer = getActiveViewer();
			if (previousActiveViewer == viewer)
				return;
			
			ImageData<BufferedImage> imageDataOld = getImageData();
			ImageData<BufferedImage> imageDataNew = viewer == null ? null : viewer.getImageData();
			if (previousActiveViewer != null) {
				previousActiveViewer.setBorderColor(null);
//				activeViewer.setBorder(BorderFactory.createLineBorder(colorTransparent, borderWidth));
//				activeViewer.setBorder(null);
				deactivateTools(previousActiveViewer);
				
				// Grab reference to the current annotation, if there is one
				PathObject pathObjectSelected = previousActiveViewer.getSelectedObject();
				if (pathObjectSelected instanceof PathAnnotationObject) {
					lastAnnotationObject = pathObjectSelected;					
				}
			}
			this.activeViewerProperty.set(viewer);
			lastX = Double.NaN;
			lastY = Double.NaN;
			lastDownsample = Double.NaN;
			lastRotation = Double.NaN;
			if (viewer != null) {
//				activeViewer.getView().setBorder(null);
				viewer.setBorderColor(colorBorder);
//				activeViewer.setBorder(BorderFactory.createLineBorder(colorBorder, borderWidth));
				activateTools(viewer);
//				QuPathGUI qupath = QuPathGUI.this; // New to me... http://stackoverflow.com/questions/1816458/getting-hold-of-the-outer-class-object-from-the-inner-class-object
//				if (qupath != null)
//					qupath.imageDataChanged(null, imageDataOld, imageDataNew);
				
				if (viewer.getServer() != null) {
					lastX = viewer.getCenterPixelX();
					lastY = viewer.getCenterPixelY();
					lastDownsample = viewer.getDownsampleFactor();
					lastRotation = viewer.getRotation();
				}
				
				updateMagnificationString();

			}
			logger.debug("Active viewer set to {}", viewer);
			fireImageDataChangedEvent(imageDataOld, imageDataNew);
		}
		
		public QuPathViewerPlus getActiveViewer() {
			return activeViewerProperty.get();
		}
		
		public ReadOnlyObjectProperty<QuPathViewerPlus> activeViewerProperty() {
			return activeViewerProperty;
		}
		
		public Node getNode() {
			return splitPaneGrid.getMainSplitPane();
		}
		
		
		
		/**
		 * Create a viewer, adding it to the stored array but not adding it to any component (which is left up to the calling code to handle)
		 * @return
		 */
		protected QuPathViewerPlus createViewer() {
			QuPathViewerPlus viewerNew = new QuPathViewerPlus(null, imageRegionStore, overlayOptions, viewerDisplayOptions);
			setupViewer(viewerNew);
			viewerNew.addViewerListener(this);
			viewers.add(viewerNew);
			return viewerNew;
		}
		
		
		SplitPane getAncestorSplitPane(Node node) {
			while (node != null && !(node instanceof SplitPane))
				node = node.getParent();
			return (SplitPane)node;
		}
		
		
		public void removeViewerRow(final QuPathViewerPlus viewer) {
//			if (viewer.getServer() != null)
//				System.err.println(viewer.getServer().getShortServerName());
			// Note: These are the internal row numbers... these don't necessarily match with the displayed row (?)
			int row = splitPaneGrid.getRow(viewer.getView());
 			if (row < 0) {
				// Shouldn't occur...
				DisplayHelpers.showErrorMessage("Multiview error", "Cannot find " + viewer + " in the grid!");
				return;
			}
			int nOpen = splitPaneGrid.countOpenViewersForRow(row);
			if (nOpen > 0) {
				DisplayHelpers.showErrorMessage("Close row error", "Please close all open viewers in selected row, then try again");
//				DisplayHelpers.showErrorMessage("Close row error", "Please close all open viewers in row " + row + ", then try again");
				return;
			}
			splitPaneGrid.removeRow(row);
			splitPaneGrid.resetGridSize();
			// Make sure the viewer list is up-to-date
			refreshViewerList();
		}
		
		
		/**
		 * Check all viewers to see if they are associated with a scene, and remove them from the list if not.
		 */
		private void refreshViewerList() {
			// Remove viewers from the list if they aren't associated with anything
			// Easiest way is to check for a scene
			Iterator<? extends QuPathViewer> iter = viewers.iterator();
			while (iter.hasNext()) {
				if (iter.next().getView().getScene() == null)
					iter.remove();
			}
		}
		
		
		/**
		 * Close the image within a viewer, prompting to save changes if necessary.
		 * 
		 * @param viewer
		 * @return True if the viewer no longer contains an open image (either because it never did contain one, or 
		 * the image was successfully closed), false otherwise (e.g. if the user thwarted the close request)
		 */
		public boolean closeViewer(final QuPathViewer viewer) {
			return closeViewer("Save changes", viewer);
		}
		
		/**
		 * Close the image within a viewer, prompting to save changes if necessary.
		 * 
		 * @param dialogTitle Name to use within any displayed dialog box.
		 * @param viewer
		 * @return True if the viewer no longer contains an open image (either because it never did contain one, or 
		 * the image was successfully closed), false otherwise (e.g. if the user thwarted the close request)
		 */
		public boolean closeViewer(final String dialogTitle, final QuPathViewer viewer) {
			ImageData<BufferedImage> imageData = viewer.getImageData();
			if (imageData == null)
				return true;
			// Deal with saving, if necessary
			if (imageData.isChanged()) {
				String lastPath = imageData.getLastSavedPath();
				File filePrevious = lastPath == null ? null : new File(lastPath);
				if ((filePrevious == null || !filePrevious.exists()) && project.get() != null) {
					ProjectImageEntry<BufferedImage> entryPrevious = project.get().getImageEntry(imageData.getServerPath());
					filePrevious = getImageDataFile(project.get(), entryPrevious);
				}
				DialogButton response = DialogButton.YES;
				if (imageData.isChanged()) {
					response = DisplayHelpers.showYesNoCancelDialog(dialogTitle, "Save changes to " + imageData.getServer().getShortServerName() + "?");
				}
				if (response == DialogButton.CANCEL)
					return false;
				if (response == DialogButton.YES) {
					if (filePrevious == null) {
						filePrevious = getDialogHelper().promptToSaveFile("Save image data", filePrevious, imageData.getServer().getShortServerName(), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
						if (filePrevious == null)
							return false;
					}
					PathIO.writeImageData(filePrevious, imageData);
				}
			}
			viewer.setImageData(null);
			return true;
		}
		
		
		public void removeViewerColumn(final QuPathViewerPlus viewer) {
			int col = splitPaneGrid.getColumn(viewer.getView());
			if (col < 0) {
				// Shouldn't occur...
				DisplayHelpers.showErrorMessage("Multiview error", "Cannot find " + viewer + " in the grid!");
				return;
			}
			int nOpen = splitPaneGrid.countOpenViewersForColumn(col);
			if (nOpen > 0) {
				DisplayHelpers.showErrorMessage("Close column error", "Please close all open viewers in selected column, then try again");
//				DisplayHelpers.showErrorMessage("Close column error", "Please close all open viewers in column " + col + ", then try again");
				return;
			}
			splitPaneGrid.removeColumn(col);
			splitPaneGrid.resetGridSize();
			// Make sure the viewer list is up-to-date
			refreshViewerList();
		}
		
		
		public void addRow(final QuPathViewerPlus viewer) {
			splitViewer(viewer, false);
			splitPaneGrid.resetGridSize();
		}

		public void addColumn(final QuPathViewerPlus viewer) {
			splitViewer(viewer, true);
			splitPaneGrid.resetGridSize();
		}

		
		public void splitViewer(final QuPathViewerPlus viewer, final boolean splitVertical) {
			if (!viewers.contains(viewer))
				return;
			
			if (splitVertical) {
				splitPaneGrid.addColumn(splitPaneGrid.getColumn(viewer.getView()));
			} else {
				splitPaneGrid.addRow(splitPaneGrid.getRow(viewer.getView()));
			}
		}
		
		/**
		 * Remove viewer from display
		 * @param viewer
		 */
		public boolean removeViewer(QuPathViewer viewer) {
			if (viewers.size() == 1) {
				logger.error("Cannot remove last viewer!");
				return false;
			}
			return true;
		}
		
		
		public void resetGridSize() {
			splitPaneGrid.resetGridSize();
		}
		
		
		public void repaintViewers() {
			for (QuPathViewer v : viewers)
				v.repaint();
		}

		@Override
		public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
			if (viewer != null && viewer == getActiveViewer()) {
				if (viewer.getServer() != null) {
					// Setting these to NaN prevents unexpected jumping when a new image is opened
					lastX = Double.NaN;
					lastY = Double.NaN;
					lastDownsample = Double.NaN;
					lastRotation = Double.NaN;
				}
				fireImageDataChangedEvent(imageDataOld, viewer.getImageData());
			}
		}

		@Override
		public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
			if (viewer == null)
				return;
			if (viewer != getActiveViewer() || viewer.isImageDataChanging()) {
//				// Only change downsamples for non-active viewer
//				double downsample = viewer.getDownsampleFactor();
//				if (synchronizeViewers) {
//					for (QuPathViewer v : viewers) {
//						double oldDownsample = v.getDownsampleFactor();
//						if (!GeneralTools.almostTheSame(downsample, oldDownsample, 0.0001)) {
//							v.setDownsampleFactor(downsample);
//						}
//					}
//				}
				return;
			} else {
				// Update magnification info
				updateMagnificationString();
			}
			
			QuPathViewerPlus activeViewer = getActiveViewer();
			double x = activeViewer.getCenterPixelX();
			double y = activeViewer.getCenterPixelY();
			double rotation = activeViewer.getRotation();
			double dx = Double.NaN, dy = Double.NaN, dr = Double.NaN;
			
			double downsample = viewer.getDownsampleFactor();
			double relativeDownsample = viewer.getDownsampleFactor() / lastDownsample;
			
			// Shift as required, assuming we aren't aligning cores
//			if (!aligningCores) {
//			synchronizeViewers = true;
			if (synchronizeViewers.get()) {
				if (!Double.isNaN(lastX + lastY)) {
					dx = x - lastX;
					dy = y - lastY;
					dr = rotation - lastRotation;
				}
				
				for (QuPathViewer v : viewers) {
					if (v == viewer)
						continue;
					if (!Double.isNaN(relativeDownsample))
						v.setDownsampleFactor(v.getDownsampleFactor() * relativeDownsample, -1, -1, false);
					if (!Double.isNaN(dr) && dr != 0)
						v.setRotation(v.getRotation() + dr);
					
					// Shift as required
					double downsampleRatio = v.getDownsampleFactor() / downsample;
					if (!Double.isNaN(dx) && !Double.isNaN(downsampleRatio)) {
						v.setCenterPixelLocation(v.getCenterPixelX() + dx*downsampleRatio, v.getCenterPixelY() + dy*downsampleRatio);
					}
				}
			}
			
			lastX = x;
			lastY = y;
			lastDownsample = downsample;
			lastRotation = rotation;
		}
		
		
		public boolean getSynchronizeViewers() {
			return synchronizeViewers.get();
		}
		
		public void setSynchronizeViewers(final boolean synchronizeViewers) {
			this.synchronizeViewers.set(synchronizeViewers);
		}
		
		public ReadOnlyBooleanProperty synchronizeViewersProperty() {
			return synchronizeViewers;
		}
		
		
		/**
		 * Convert the viewers to quadrants, using new split panes appropriately.
		 * Warning: this will throw a RuntimeException if > viewers are open.
		 */
		public void setupQuadrants() {
			// TODO: REINSTATE QUADRANTS!!!!
//			if (viewers.size() > 4)
//				throw new RuntimeException("Cannot set up quadrants while > 4 viewers are open");
//			
//			JSplitPane splitTop = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
//			splitTop.setResizeWeight(0.5);
//			splitTop.setOneTouchExpandable(true);
//
//			JSplitPane splitBottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
//			splitBottom.setResizeWeight(0.5);
//			splitBottom.setOneTouchExpandable(true);
//			
//			// Add the available viewers, and any extra ones that are needed
//			if (viewers.size() > 0)
//				splitTop.setLeftComponent(viewers.get(0));
//			else
//				splitTop.setLeftComponent(viewerManager.createViewer());
//			if (viewers.size() > 1)
//				splitTop.setRightComponent(viewers.get(1));
//			else
//				splitTop.setRightComponent(viewerManager.createViewer());
//			if (viewers.size() > 2)
//				splitBottom.setLeftComponent(viewers.get(2));
//			else
//				splitBottom.setLeftComponent(viewerManager.createViewer());
//			if (viewers.size() > 3)
//				splitBottom.setRightComponent(viewers.get(3));
//			else
//				splitBottom.setRightComponent(viewerManager.createViewer());
//			
//			JSplitPane splitMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, splitTop, splitBottom);
//			splitMain.setResizeWeight(0.5);
//			splitMain.setOneTouchExpandable(true);
//			
//			panel.removeAll();
//			panel.add(splitMain, BorderLayout.CENTER);
//			
//			// Need synchronous validation for the divider locations to work (i.e. not revalidate())
//			panel.invalidate();
//			panel.validate();
//			splitMain.setDividerLocation(0.5);
//			splitTop.setDividerLocation(0.5);
//			splitBottom.setDividerLocation(0.5);
		}
		

		@Override
		public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {
			// Store any annotation ROIs, which might need to be transferred
			if (pathObjectSelected instanceof PathAnnotationObject) {
				lastAnnotationObject = pathObjectSelected;
				return;
			}
			
			// Don't handle unselected viewers
			if (viewer != getActiveViewer()) {
				return;
			}
			
//			logger.info("SELECTED OBJECT CHANGED");

			
			// Synchronize TMA cores
			if (!(pathObjectSelected instanceof TMACoreObject))
				return;
			
			// Thwart the upcoming region shift
			lastX = Double.NaN;
			lastY = Double.NaN;
			lastDownsample = Double.NaN;
			lastRotation = Double.NaN;
			
//			aligningCores = true;
			String coreName = ((TMACoreObject)pathObjectSelected).getName();
			for (QuPathViewer v : viewers) {
				if (v == viewer)
					continue;
				PathObjectHierarchy hierarchy = v.getHierarchy();
				if (hierarchy == null || hierarchy.getTMAGrid() == null)
					continue;
				
				TMAGrid tmaGrid = hierarchy.getTMAGrid();
				TMACoreObject core = tmaGrid.getTMACore(coreName);
				if (core != null) {
					v.setSelectedObject(core);
					double cx = core.getROI().getCentroidX();
					double cy = core.getROI().getCentroidY();
					v.setCenterPixelLocation(cx, cy);
				}
			}
		}
		
		
		
		public boolean applyLastAnnotationToActiveViewer() {
			if (lastAnnotationObject == null) {
				logger.info("No annotation object to copy");
				return false;
			}
			
			QuPathViewerPlus activeViewer = getActiveViewer();
			if (activeViewer == null || activeViewer.getHierarchy() == null) {
				logger.info("No active viewer available");
				return false;
			}
			
			PathObjectHierarchy hierarchy = activeViewer.getHierarchy();
			if (PathObjectTools.hierarchyContainsObject(hierarchy, lastAnnotationObject)) {
				logger.info("Hierarchy already contains annotation object!");
				return false;
			}
			
			ROI roi = lastAnnotationObject.getROI().duplicate();
			
			// If we are within a TMA core, try to apply any required translations
			TMACoreObject coreNewParent = null;
			if (roi instanceof TranslatableROI && hierarchy.getTMAGrid() != null) {
				TMACoreObject coreParent = null;
				PathObject parent = lastAnnotationObject.getParent();
				while (parent != null) {
					if (parent instanceof TMACoreObject) {
						coreParent = (TMACoreObject)parent;
						break;
					} else
						parent = parent.getParent();
				}
				if (coreParent != null) {
					coreNewParent = hierarchy.getTMAGrid().getTMACore(coreParent.getName());
					if (coreNewParent != null) {
						double rotation = activeViewer.getRotation();
//						if (rotation == 0) {
							double dx = coreNewParent.getROI().getCentroidX() - coreParent.getROI().getCentroidX();
							double dy = coreNewParent.getROI().getCentroidY() - coreParent.getROI().getCentroidY();
							TranslatableROI roiTranslatable = (TranslatableROI)roi;
							roi = roiTranslatable.translate(dx, dy);
							// TODO: Deal with rotations... it's a bit tricky...
//						} else {
						// TODO: Check how best to handle transferring ROIs with rotation involved
						if (rotation != 0) {
							AffineTransform transform = new AffineTransform();
							transform.rotate(-rotation, coreNewParent.getROI().getCentroidX(), coreNewParent.getROI().getCentroidY());
							logger.info("ROTATING: " + transform);
							Area area = PathROIToolsAwt.getArea(roi);
							area.transform(transform);
							roi = PathROIToolsAwt.getShapeROI(area, roi.getC(), roi.getZ(), roi.getT());
						}
					}
				}
			}
			
			
			PathObject annotation = new PathAnnotationObject(roi, lastAnnotationObject.getPathClass());
//			hierarchy.addPathObject(annotation, false);
			
//			// Make sure any core parent is set
			hierarchy.addPathObjectBelowParent(coreNewParent, annotation, false, true);
			
			activeViewer.setSelectedObject(annotation);
			return true;
		}

		@Override
		public void viewerClosed(QuPathViewer viewer) {
			removeViewer(viewer); // May be avoidable...?
		}

		@Override
		public QuPathViewerPlus getViewer() {
			return getActiveViewer();
		}
		
		
		
		
		
		
		
		class SplitPaneGrid {
			
			private SplitPane splitPaneMain = new SplitPane();
			private List<SplitPane> splitPaneRows = new ArrayList<>();
			
//			private Map<Node, QuPathViewer> viewerMap = new WeakHashMap<>();
			
			SplitPaneGrid(final Node node) {
				splitPaneMain.setOrientation(Orientation.VERTICAL);
				SplitPane splitRow = new SplitPane();
				splitRow.setOrientation(Orientation.HORIZONTAL);
				splitRow.getItems().add(node);
				splitPaneRows.add(splitRow);
				splitPaneMain.getItems().add(splitRow);
			}
			
			SplitPane getMainSplitPane() {
				return splitPaneMain;
			}
			
			
			void addRow(final int position) {
				SplitPane splitRow = new SplitPane();
				splitRow.setOrientation(Orientation.HORIZONTAL);
				
				// For now, we create a row with the same number of columns in every row
				// Create viewers & bind dividers
				splitRow.getItems().clear();
				SplitPane firstRow = splitPaneRows.get(0);
				splitRow.getItems().add(createViewer().getView());
				for (int i = 0; i < firstRow.getDividers().size(); i++) {
					splitRow.getItems().add(createViewer().getView());
//					splitRow.getDividers().get(i).positionProperty().bindBidirectional(firstRow.getDividers().get(i).positionProperty());
				}
				
				// Ensure the new divider takes up half the space
				double lastDividerPosition = position == 0 ? 0 : splitPaneMain.getDividers().get(position-1).getPosition();
				double nextDividerPosition = position >= splitPaneRows.size()-1 ? 1 : splitPaneMain.getDividers().get(position).getPosition();
				splitPaneRows.add(position, splitRow);
				splitPaneMain.getItems().add(position+1, splitRow);
				splitPaneMain.setDividerPosition(position, (lastDividerPosition + nextDividerPosition)/2);
				
				refreshDividerBindings();
			}
			
			
			
			boolean removeRow(final int row) {
				if (row < 0 || row >= splitPaneRows.size() || splitPaneRows.size() == 1) {
					logger.error("Cannot remove row {} from grid with {} rows", row, splitPaneRows.size());
					return false;
				}
				SplitPane splitPane = splitPaneRows.remove(row);
//				// WeakHashMap should take care of this... but check anyway
//				for (Node node : splitPane.getItems())
//					viewerMap.remove(node);
				splitPaneMain.getItems().remove(splitPane);
				refreshDividerBindings();
				return true;
			}
			
			
			/**
			 * Restore all grid panels to be the same size
			 */
			public void resetGridSize() {
				resetDividers(splitPaneRows.get(0)); // Because of property binding, this should be enough
				resetDividers(splitPaneMain);
			}
			
			
			void resetDividers(final SplitPane splitPane) {
				int n = splitPane.getItems().size();
				if (n <= 1)
					return;
				if (n == 2) {
					splitPane.setDividerPosition(0, 0.5);
					return;
				}
				double[] positions = new double[n-1];
				for (int i = 0; i < positions.length; i++)
					positions[i] = (i + 1.0) / (double)n;
				splitPane.setDividerPositions(positions);
			}

			
			boolean removeColumn(final int col) {
				if (col < 0 || col >= nCols() || nCols() == 1) {
					logger.error("Cannot remove column {} from grid with {} columns", col, nCols());
					return false;
				}
				for (SplitPane splitRow : splitPaneRows) {
					splitRow.getItems().remove(col);
				}
				refreshDividerBindings();
				return true;
			}
			
			
			int countOpenViewersForRow(final int row) {
				int count = 0;
				for (QuPathViewer viewer : getViewers()) {
					if (row == getRow(viewer.getView()) && viewer.hasServer())
						count++;
				}
				return count;
			}
			
			
			int countOpenViewersForColumn(final int col) {
				int count = 0;
				for (QuPathViewer viewer : getViewers()) {
					if (col == getColumn(viewer.getView()) && viewer.hasServer())
						count++;
				}
				return count;				
			}

			
			/**
			 * Update all divider bindings so they match the first row
			 */
			void refreshDividerBindings() {
				SplitPane firstRow = splitPaneRows.get(0);
				for (int r = 1; r < splitPaneRows.size(); r++) {
					SplitPane splitRow = splitPaneRows.get(r);
					for (int c = 0; c < splitRow.getDividers().size(); c++) {
						splitRow.getDividers().get(c).positionProperty().bindBidirectional(firstRow.getDividers().get(c).positionProperty());
					}
				}
			}
			
			
			void addColumn(final int position) {
				SplitPane firstRow = splitPaneRows.get(0);
				double lastDividerPosition = position == 0 ? 0 : firstRow.getDividers().get(position-1).getPosition();
				double nextDividerPosition = position >= firstRow.getItems().size()-1 ? 1 : firstRow.getDividers().get(position).getPosition();
				
				firstRow.getItems().add(position+1, createViewer().getView());
				Divider firstDivider = firstRow.getDividers().get(position);
				firstDivider.setPosition((lastDividerPosition + nextDividerPosition)/2);
				for (int i = 1; i < splitPaneRows.size(); i++) {
					SplitPane splitRow = splitPaneRows.get(i);
					splitRow.getItems().add(position+1, createViewer().getView());
				}
				
				refreshDividerBindings();
			}
			
			
			public int getRow(final Node node) {
				int count = 0;
				for (SplitPane row : splitPaneRows) {
					int ind = row.getItems().indexOf(node);
					if (ind >= 0)
						return count;
					count++;
				}
				return -1;
			}

			public int getColumn(final Node node) {
				for (SplitPane row : splitPaneRows) {
					int ind = row.getItems().indexOf(node);
					if (ind >= 0)
						return ind;
				}
				return -1;
			}

			public int nRows() {
				return splitPaneRows.size();
			}

			public int nCols() {
				return splitPaneRows.get(0).getDividers().size() + 1;
			}
			
//			public int nCols(final int row) {
//				return splitPaneRows.get(row).getDividers().size();
//			}

		}
		
		
	}
	
	
	
	
	
	static class TransferAnnotationCommand implements PathCommand {
		
		final private QuPathGUI qupath;
		
		public TransferAnnotationCommand(final QuPathGUI qupath) {
			super();
			this.qupath = qupath;
		}

		@Override
		public void run() {
			qupath.viewerManager.applyLastAnnotationToActiveViewer();
		}
		
	}
	
	
	@Override
	public void addImageDataChangeListener(ImageDataChangeListener<BufferedImage> listener) {
		listeners.add(listener);
	}


	@Override
	public void removeImageDataChangeListener(ImageDataChangeListener<BufferedImage> listener) {
		listeners.remove(listener);
	}
	
	
	public static class ExtensionClassLoader extends URLClassLoader {

		public ExtensionClassLoader() {
			super(new URL[0], QuPathGUI.class.getClassLoader());
//			refresh();
		}
		
		/**
		 * Request that a specified JAR file be added to the classpath.
		 * 
		 * @param file
		 * @return
		 */
		private boolean addJAR(final File file) {
			try (JarFile jar = new JarFile(file)) {
				if (jar.entries().hasMoreElements()) {
					addURL(file.toURI().toURL());
					return true;
				}
			} catch (IOException e) {
				logger.error("Unable to add file to classpath", e);
			}
			return false;
		}
		
		/**
		 * Ensure all Jars in the extensions directory (and one subdirectory down) are available
		 */
		public void refresh() {
			File dirExtensions = getExtensionDirectory();
			if (dirExtensions == null)
				return;
			refreshExtensions(dirExtensions);
			for (File dir : dirExtensions.listFiles()) {
				if (!dir.isHidden() && dir.isDirectory()) {
					Path dirPath = dir.toPath();
					if (Files.isSymbolicLink(dirPath))
						try {
							dir = Files.readSymbolicLink(dirPath).toFile();
						} catch (IOException e) {
							logger.error("Error refreshing extensions", e);
						}
					refreshExtensions(dir);
				}
			}
		}
		
		/**
		 * Ensure all Jars from the specified directory are available.
		 * 
		 * @param dirExtensions
		 */
		private void refreshExtensions(final File dirExtensions) {
			if (dirExtensions == null) {
				logger.debug("No extensions directory specified");				
				return;
			} else if (!dirExtensions.isDirectory()) {
				logger.warn("Cannot load extensions from " + dirExtensions + " - not a valid directory");	
				return;
			}
			logger.info("Refreshing extensions in " + dirExtensions);				
			for (File file : dirExtensions.listFiles()) {
				if (file.getName().toLowerCase().endsWith(".jar")) {
					try {
						addURL(file.toURI().toURL());
						logger.info("Added extension: " + file.getAbsolutePath());
					} catch (MalformedURLException e) {
						logger.debug("Error adding {} to classpath", file, e);
					}
				}
			}
		}
		
	}
	
}