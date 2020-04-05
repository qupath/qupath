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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Optional;
import java.util.Locale.Category;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.SplitPane.Divider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
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
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import jfxtras.scene.menu.CirclePopupMenu;
import qupath.lib.algorithms.IntensityFeaturesPlugin;
import qupath.lib.algorithms.TilerPlugin;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.commands.BrightnessContrastCommand;
import qupath.lib.gui.commands.CommandListDisplayCommand;
import qupath.lib.gui.commands.CopyViewToClipboardCommand;
import qupath.lib.gui.commands.CountingPanelCommand;
import qupath.lib.gui.commands.DetectionCentroidDistancesCommand;
import qupath.lib.gui.commands.DistanceToAnnotationsCommand;
import qupath.lib.gui.commands.EstimateStainVectorsCommand;
import qupath.lib.gui.commands.LoadClassifierCommand;
import qupath.lib.gui.commands.LogViewerCommand;
import qupath.lib.gui.commands.MeasurementExportCommand;
import qupath.lib.gui.commands.MeasurementManager;
import qupath.lib.gui.commands.MeasurementMapCommand;
import qupath.lib.gui.commands.MemoryMonitorCommand;
import qupath.lib.gui.commands.MiniViewerCommand;
import qupath.lib.gui.commands.OpenCommand;
import qupath.lib.gui.commands.PreferencesCommand;
import qupath.lib.gui.commands.ProjectCheckUrisCommand;
import qupath.lib.gui.commands.ProjectCloseCommand;
import qupath.lib.gui.commands.ProjectCreateCommand;
import qupath.lib.gui.commands.ProjectExportImageListCommand;
import qupath.lib.gui.commands.ProjectImportImagesCommand;
import qupath.lib.gui.commands.ProjectMetadataEditorCommand;
import qupath.lib.gui.commands.ProjectOpenCommand;
import qupath.lib.gui.commands.ProjectSaveCommand;
import qupath.lib.gui.commands.QuPathSetupCommand;
import qupath.lib.gui.commands.ResetPreferencesCommand;
import qupath.lib.gui.commands.ResolveHierarchyCommand;
import qupath.lib.gui.commands.ReloadDataCommand;
import qupath.lib.gui.commands.RigidObjectEditorCommand;
import qupath.lib.gui.commands.RotateImageCommand;
import qupath.lib.gui.commands.SampleScriptLoader;
import qupath.lib.gui.commands.ExportImageRegionCommand;
import qupath.lib.gui.commands.HierarchyInsertCommand;
import qupath.lib.gui.commands.SaveViewCommand;
import qupath.lib.gui.commands.ScriptInterpreterCommand;
import qupath.lib.gui.commands.SerializeImageDataCommand;
import qupath.lib.gui.commands.SetGridSpacingCommand;
import qupath.lib.gui.commands.OpenWebpageCommand;
import qupath.lib.gui.commands.ShowInstalledExtensionsCommand;
import qupath.lib.gui.commands.ShowInputDisplayCommand;
import qupath.lib.gui.commands.ShowLicensesCommand;
import qupath.lib.gui.commands.ShowScriptEditorCommand;
import qupath.lib.gui.commands.ShowSystemInfoCommand;
import qupath.lib.gui.commands.TMAGridView;
import qupath.lib.gui.commands.SingleFeatureClassifierCommand;
import qupath.lib.gui.commands.SparseImageServerCommand;
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
import qupath.lib.gui.commands.interfaces.SelectableItemWrapper;
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
import qupath.lib.gui.commands.scriptable.SelectObjectsByClassificationCommand;
import qupath.lib.gui.commands.scriptable.SelectObjectsByMeasurementCommand;
import qupath.lib.gui.commands.scriptable.ShapeSimplifierCommand;
import qupath.lib.gui.commands.scriptable.SpecifyAnnotationCommand;
import qupath.lib.gui.commands.scriptable.TMAGridRelabel;
import qupath.lib.gui.controls.cells.ImageAndNameListCell;
import qupath.lib.gui.dialogs.DialogHelper;
import qupath.lib.gui.dialogs.DialogHelperFX;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.gui.icons.PathIconFactory.PathIcons;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.logging.LoggingAppender;
import qupath.lib.gui.panels.PathAnnotationPanel;
import qupath.lib.gui.panels.PathImageDetailsPanel;
import qupath.lib.gui.panels.PathObjectHierarchyView;
import qupath.lib.gui.panels.PreferencePanel;
import qupath.lib.gui.panels.ProjectBrowser;
import qupath.lib.gui.panels.SelectedMeasurementTableView;
import qupath.lib.gui.panels.SlideLabelView;
import qupath.lib.gui.panels.WorkflowPanel;
import qupath.lib.gui.panels.classify.RandomTrainingRegionSelector;
import qupath.lib.gui.plugins.ParameterDialogWrapper;
import qupath.lib.gui.plugins.PluginRunnerFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.scripting.ScriptEditor;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.viewer.DragDropFileImportListener;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.gui.viewer.ViewerPlusDisplayOptions;
import qupath.lib.gui.viewer.OverlayOptions.DetectionDisplayMode;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.AbstractPluginRunner;
import qupath.lib.plugins.PathInteractivePlugin;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.objects.DilateAnnotationPlugin;
import qupath.lib.plugins.objects.FillAnnotationHolesPlugin;
import qupath.lib.plugins.objects.FindConvexHullDetectionsPlugin;
import qupath.lib.plugins.objects.RefineAnnotationsPlugin;
import qupath.lib.plugins.objects.ShapeFeaturesPlugin;
import qupath.lib.plugins.objects.SmoothFeaturesPlugin;
import qupath.lib.plugins.objects.SplitAnnotationsPlugin;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.gui.scripting.DefaultScriptEditor;



/**
 * Main GUI for QuPath, written using JavaFX.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathGUI {
	
	static Logger logger = LoggerFactory.getLogger(QuPathGUI.class);
	
	private static QuPathGUI instance;
	
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
	
	private ObjectProperty<PathTool> selectedToolProperty = new SimpleObjectProperty<>(PathTools.MOVE);
	private ObservableList<PathTool> tools = FXCollections.observableArrayList(
			PathTools.MOVE, PathTools.RECTANGLE, PathTools.ELLIPSE, PathTools.LINE, PathTools.POLYGON, PathTools.POLYLINE, PathTools.BRUSH, PathTools.POINTS
			);
	
	private BooleanProperty selectedToolLocked = new SimpleBooleanProperty(false);
	
	// ExecutorServices for single & multiple threads
	private Map<Object, ExecutorService> mapSingleThreadPools = new HashMap<>();
//	private Set<ExecutorService> managedThreadPools = new HashSet<>();
	private ExecutorService poolMultipleThreads = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), ThreadTools.createThreadFactory("qupath-shared-", false));	
	
	private Map<PathTool, Action> modeActions = new HashMap<>();
	
	/**
	 * Preferred size for toolbar icons.
	 */
	final public static int TOOLBAR_ICON_SIZE = 16;

	private MultiviewManager viewerManager;
	
	private ObjectProperty<Project<BufferedImage>> project = new SimpleObjectProperty<>();
	
	private ProjectBrowser projectBrowser;
	
	/**
	 * Preference panel, which may be used by extensions to add in their on preferences if needed
	 */
	private PreferencePanel prefsPanel;
	
	/**
	 * The current ImageData in the current QuPathViewer
	 */
	private ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();
	
	// Initializing the MenuBar here caused some major trouble (including segfaults) in OSX...
	private MenuBar menuBar;

	private BooleanProperty zoomToFit = new SimpleBooleanProperty(false);
	
	private BorderPane pane; // Main component, to hold toolbar & splitpane
	private Control analysisPanel;
	
	private ViewerPlusDisplayOptions viewerDisplayOptions = new ViewerPlusDisplayOptions();
	private OverlayOptions overlayOptions = new OverlayOptions();
	
	private DefaultImageRegionStore imageRegionStore;

	private ToolBarComponent toolbar; // Top component
	private SplitPane splitPane = new SplitPane(); // Main component

	private ObservableList<PathClass> availablePathClasses = null;
	
	private Stage stage;
	
	private static DialogHelper standaloneDialogHelper = new DialogHelperFX(null); // When there is no parent Window available
	private static Map<Window, DialogHelper> dialogHelpers = new WeakHashMap<>();

	private boolean isStandalone = false;
	private ScriptMenuLoader sharedScriptMenuLoader;
//	private ScriptMenuLoader projectScriptMenuLoader;
	
	private ImageServer<BufferedImage> selectedSeries = null;
	
	private DragDropFileImportListener dragAndDrop = new DragDropFileImportListener(this);
	
	private UndoRedoManager undoRedoManager;
	
	private HostServices hostServices;
	
	/**
	 * Keystrokes can be lost on macOS... so ensure these are handled
	 */
	private Map<KeyCombination, Action> comboMap = new HashMap<>();
	
	
	
	static class FileCommands {
		
		private QuPathGUI qupath;
		
		FileCommands(QuPathGUI qupath) {
			
		}
		
		
	}
	
	
	
	public class ActionManager {
	
		// File actions
		private Action OPEN_IMAGE = createCommandAction(new OpenCommand(QuPathGUI.this), "Open...", null, new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
		private Action OPEN_IMAGE_OR_URL = createCommandAction(new OpenCommand(QuPathGUI.this, true), "Open URI...", null, new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		private Action TMA_EXPORT_DATA = createCommandAction(new TMAExporterCommand(QuPathGUI.this.viewerProperty()), "Export TMA data");
		private Action SAVE_DATA = createCommandAction(new SerializeImageDataCommand(QuPathGUI.this, true), "Save", null, new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
		private Action SAVE_DATA_AS = createCommandAction(new SerializeImageDataCommand(QuPathGUI.this, false), "Save As", null, new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));			
		
		// Copy actions
		private Action COPY_VIEW = createCommandAction(new CopyViewToClipboardCommand(QuPathGUI.this, GuiTools.SnapshotType.VIEWER), "Current viewer", null, new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
		private Action COPY_FULL_SCREENSHOT = createCommandAction(new CopyViewToClipboardCommand(QuPathGUI.this, GuiTools.SnapshotType.FULL_SCREENSHOT), "Full screenshot");
		private Action COPY_WINDOW_SCREENSHOT = createCommandAction(new CopyViewToClipboardCommand(QuPathGUI.this, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT), "Main window screenshot");			
		private Action COPY_WINDOW = createCommandAction(new CopyViewToClipboardCommand(QuPathGUI.this, GuiTools.SnapshotType.MAIN_SCENE), "Main window content");
		
		// Zoom actions
		private Action ZOOM_IN = createCommandAction(ZoomCommand.createZoomInCommand(QuPathGUI.this.viewerProperty()), "Zoom in", PathIconFactory.createNode(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE, PathIconFactory.PathIcons.ZOOM_IN), new KeyCodeCombination(KeyCode.PLUS));
		private Action ZOOM_OUT = createCommandAction(ZoomCommand.createZoomOutCommand(QuPathGUI.this.viewerProperty()), "Zoom out", PathIconFactory.createNode(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE, PathIconFactory.PathIcons.ZOOM_OUT), new KeyCodeCombination(KeyCode.MINUS));
		Action ZOOM_TO_FIT = createSelectableCommandAction(zoomToFit, "Zoom to fit", PathIconFactory.PathIcons.ZOOM_TO_FIT, null);
		
		// Tool actions
		public Action MOVE_TOOL = createToolAction(PathTools.MOVE, new KeyCodeCombination(KeyCode.M));
		Action RECTANGLE_TOOL = createToolAction(PathTools.RECTANGLE, new KeyCodeCombination(KeyCode.R));
		Action ELLIPSE_TOOL = createToolAction(PathTools.ELLIPSE, new KeyCodeCombination(KeyCode.O));
		Action POLYGON_TOOL = createToolAction(PathTools.POLYGON, new KeyCodeCombination(KeyCode.P));
		Action POLYLINE_TOOL = createToolAction(PathTools.POLYLINE, new KeyCodeCombination(KeyCode.V));
		Action BRUSH_TOOL = createToolAction(PathTools.BRUSH, new KeyCodeCombination(KeyCode.B));
		Action LINE_TOOL = createToolAction(PathTools.LINE, new KeyCodeCombination(KeyCode.L));
		public Action POINTS_TOOL = createToolAction(PathTools.POINTS, new KeyCodeCombination(KeyCode.PERIOD));
//		Action WAND_TOOL = createToolAction(PathTools.WAND, new KeyCodeCombination(KeyCode.W));
		Action SELECTION_MODE = createSelectableCommandAction(PathPrefs.selectionModeProperty(), "Selection mode", PathIconFactory.PathIcons.SELECTION_MODE, new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
		
		// Toolbar actions
		Action BRIGHTNESS_CONTRAST = createCommandAction(new BrightnessContrastCommand(QuPathGUI.this), "Brightness/Contrast", PathIconFactory.createNode(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE, PathIconFactory.PathIcons.CONTRAST), new KeyCodeCombination(KeyCode.C, KeyCombination.SHIFT_DOWN));
		Action SHOW_OVERVIEW = createSelectableCommandAction(viewerDisplayOptions.showOverviewProperty(), "Show slide overview", PathIconFactory.PathIcons.OVERVIEW, null);
		Action SHOW_LOCATION = createSelectableCommandAction(viewerDisplayOptions.showLocationProperty(), "Show cursor location", PathIconFactory.PathIcons.LOCATION, null);
		Action SHOW_SCALEBAR = createSelectableCommandAction(viewerDisplayOptions.showScalebarProperty(), "Show scalebar", PathIconFactory.PathIcons.SHOW_SCALEBAR, null);
		public Action SHOW_GRID = createSelectableCommandAction(overlayOptions.showGridProperty(), "Show grid", PathIconFactory.PathIcons.GRID, new KeyCodeCombination(KeyCode.G, KeyCombination.SHIFT_DOWN));

		Action SHOW_PIXEL_CLASSIFICATION = createSelectableCommandAction(overlayOptions.showPixelClassificationProperty(), "Show pixel classification", PathIconFactory.PathIcons.PIXEL_CLASSIFICATION, new KeyCodeCombination(KeyCode.C));
		private Action GRID_SPACING = createCommandAction(new SetGridSpacingCommand(overlayOptions), "Set grid spacing");
		private Action COUNTING_PANEL = createCommandAction(new CountingPanelCommand(QuPathGUI.this), "Counting tool", PathTools.POINTS.getIcon(), null);
			
		// TMA actions
		private Action TMA_SCORE_IMPORTER = createCommandAction(new TMAScoreImportCommand(QuPathGUI.this), "Import TMA map");
		private Action TMA_ADD_NOTE = createCommandAction(new TMAAddNote(QuPathGUI.this), "Add TMA note");
		public Action TMA_RELABEL = createCommandAction(new TMAGridRelabel(QuPathGUI.this), "Relabel TMA grid");
		
		// Overlay options actions
		private Action SHOW_CELL_BOUNDARIES = createSelectableCommandAction(new SelectableItemWrapper<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.BOUNDARIES_ONLY), "Cell boundaries only", PathIconFactory.createNode(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE, PathIcons.CELL_ONLY), null);
		private Action SHOW_CELL_NUCLEI = createSelectableCommandAction(new SelectableItemWrapper<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.NUCLEI_ONLY), "Nuclei only", PathIconFactory.createNode(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE, PathIcons.CELL_NULCEI_BOTH), null);
		private Action SHOW_CELL_BOUNDARIES_AND_NUCLEI = createSelectableCommandAction(new SelectableItemWrapper<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.NUCLEI_AND_BOUNDARIES), "Nuclei & cell boundaries", PathIconFactory.createNode(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE, PathIcons.NUCLEI_ONLY), null);
		private Action SHOW_CELL_CENTROIDS = createSelectableCommandAction(new SelectableItemWrapper<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.CENTROIDS), "Centroids only", PathIconFactory.createNode(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE, PathIcons.CENTROIDS_ONLY), null);
		private Action MEASUREMENT_MAP = createCommandAction(new MeasurementMapCommand(QuPathGUI.this), "Show measurement maps", null, new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		public Action SHOW_ANNOTATIONS = createSelectableCommandAction(overlayOptions.showAnnotationsProperty(), "Show annotations", PathIconFactory.PathIcons.ANNOTATIONS, new KeyCodeCombination(KeyCode.A));
		private Action SHOW_NAMES = createSelectableCommandAction(overlayOptions.showNamesProperty(), "Show labels", (Node)null, new KeyCodeCombination(KeyCode.N));
		private Action FILL_ANNOTATIONS = createSelectableCommandAction(overlayOptions.fillAnnotationsProperty(), "Fill annotations", PathIconFactory.PathIcons.ANNOTATIONS_FILL, new KeyCodeCombination(KeyCode.F, KeyCombination.SHIFT_DOWN));	
		Action SHOW_TMA_GRID = createSelectableCommandAction(overlayOptions.showTMAGridProperty(), "Show TMA grid", PathIconFactory.PathIcons.TMA_GRID, new KeyCodeCombination(KeyCode.G));
		private Action SHOW_TMA_GRID_LABELS = createSelectableCommandAction(overlayOptions.showTMACoreLabelsProperty(), "Show TMA grid labels");
		Action SHOW_DETECTIONS = createSelectableCommandAction(overlayOptions.showDetectionsProperty(), "Show detections", PathIconFactory.PathIcons.DETECTIONS, new KeyCodeCombination(KeyCode.D));
		public Action FILL_DETECTIONS = createSelectableCommandAction(overlayOptions.fillDetectionsProperty(), "Fill detections", PathIconFactory.PathIcons.DETECTIONS_FILL, new KeyCodeCombination(KeyCode.F));	
		public Action CONVEX_POINTS = createSelectableCommandAction(PathPrefs.showPointHullsProperty(), "Show point convex hull");
		
		// Measurement table actions
		Action SUMMARY_TMA = createCommandAction(new SummaryMeasurementTableCommand(QuPathGUI.this, TMACoreObject.class), "Show TMA measurements");
		Action SUMMARY_ANNOTATIONS = createCommandAction(new SummaryMeasurementTableCommand(QuPathGUI.this, PathAnnotationObject.class), "Show annotation measurements");
		Action SUMMARY_DETECTIONS = createCommandAction(new SummaryMeasurementTableCommand(QuPathGUI.this, PathDetectionObject.class), "Show detection measurements");
		Action EXPORT_MEASUREMENTS = createCommandAction(new MeasurementExportCommand(QuPathGUI.this), "Export measurements");
		
		// Object delete actions
		private Action DELETE_SELECTED_OBJECTS = createCommandAction(new DeleteSelectedObjectsCommand<>(QuPathGUI.this.imageDataProperty()), "Delete selected objects");
		private Action CLEAR_HIERARCHY = createCommandAction(new DeleteObjectsCommand<>(QuPathGUI.this.imageDataProperty(), null), "Delete all objects");
		private Action CLEAR_DETECTIONS = createCommandAction(new DeleteObjectsCommand<>(QuPathGUI.this.imageDataProperty(), PathDetectionObject.class), "Delete all detections");
		private Action CLEAR_TMA_CORES = createCommandAction(new DeleteObjectsCommand<>(QuPathGUI.this.imageDataProperty(), TMACoreObject.class), "Delete TMA grid");
		private Action CLEAR_ANNOTATIONS = createCommandAction(new DeleteObjectsCommand<>(QuPathGUI.this.imageDataProperty(), PathAnnotationObject.class), "Delete all annotations");

	
		// Project actions
		public Action PROJECT_NEW = createCommandAction(new ProjectCreateCommand(QuPathGUI.this), "Create project");
		public Action PROJECT_OPEN = createCommandAction(new ProjectOpenCommand(QuPathGUI.this), "Open project");
		private Action PROJECT_CLOSE = createCommandAction(new ProjectCloseCommand(QuPathGUI.this), "Close project");
		private Action PROJECT_SAVE = createCommandAction(new ProjectSaveCommand(QuPathGUI.this), "Save project");
		public Action PROJECT_IMPORT_IMAGES = createCommandAction(new ProjectImportImagesCommand(QuPathGUI.this), "Add images");
		private Action PROJECT_EXPORT_IMAGE_LIST = createCommandAction(new ProjectExportImageListCommand(QuPathGUI.this), "Export image list");		
		private Action PROJECT_METADATA = createCommandAction(new ProjectMetadataEditorCommand(QuPathGUI.this), "Edit project metadata");
		
		// Object actions
		private Action TRANSFER_ANNOTATION = createCommandAction(new TransferAnnotationCommand(QuPathGUI.this), "Transfer last annotation", null, new KeyCodeCombination(KeyCode.E, KeyCombination.SHIFT_DOWN));
		private Action SELECT_ALL_ANNOTATION = createCommandAction(new SelectAllAnnotationCommand(QuPathGUI.this.viewerProperty()), "Create full image annotation", null, new KeyCodeCombination(KeyCode.A, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN));
		private Action SPECIFY_ANNOTATION = createCommandAction(new SpecifyAnnotationCommand(QuPathGUI.this), "Specify annotation");
		private Action ANNOTATION_DUPLICATE = createCommandAction(new DuplicateAnnotationCommand<>(QuPathGUI.this.imageDataProperty()), "Duplicate annotation", null, new KeyCodeCombination(KeyCode.D, KeyCombination.SHIFT_DOWN));

		public Action DETECTIONS_TO_POINTS = createCommandAction(new DetectionsToPointsCommand<>(QuPathGUI.this.imageDataProperty()), "Convert detections to points");
		
		// Viewer actions
		private Action VIEW_TRACKER = createCommandAction(new ViewTrackerCommand(QuPathGUI.this), "Show tracking panel", null, new KeyCodeCombination(KeyCode.T, KeyCombination.SHIFT_DOWN)); // TODO: Note: this only works with the original viewer
		private Action TOGGLE_SYNCHRONIZE_VIEWERS = createSelectableCommandAction(viewerManager.synchronizeViewersProperty(), "Synchronize viewers", (Node)null, new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.ALT_DOWN, KeyCombination.SHORTCUT_DOWN));
		private Action MATCH_VIEWER_RESOLUTIONS = new Action("Match viewer resolutions", e -> viewerManager.matchResolutions());
		private Action ROTATE_IMAGE = createCommandAction(new RotateImageCommand(QuPathGUI.this), "Rotate image");
		private Action MINI_VIEWER = createCommandAction(new MiniViewerCommand(QuPathGUI.this, false), "Show mini viewer");
		private Action CHANNEL_VIEWER = createCommandAction(new MiniViewerCommand(QuPathGUI.this, true), "Show channel viewer");
		private Action RIGID_OBJECT_EDITOR = createCommandAction(new RigidObjectEditorCommand(QuPathGUI.this), "Rotate annotation", null, new KeyCodeCombination(KeyCode.R, KeyCombination.SHIFT_DOWN, KeyCombination.ALT_DOWN, KeyCombination.SHORTCUT_DOWN));
		
		// General GUI actions
		private Action SHOW_LOG = createCommandAction(new LogViewerCommand(QuPathGUI.this), "Show log", null, new KeyCodeCombination(KeyCode.L, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN));

		private Action QUPATH_SETUP = createCommandAction(new QuPathSetupCommand(QuPathGUI.this), "Show setup options");
		Action PREFERENCES = createCommandAction(new PreferencesCommand(QuPathGUI.this, prefsPanel), "Preferences...", PathIconFactory.createNode(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE, PathIcons.COG), new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));
		Action SHOW_ANALYSIS_PANEL = createShowAnalysisPaneAction();
		private Action WORKFLOW_DISPLAY = createCommandAction(new WorkflowDisplayCommand(QuPathGUI.this), "Show workflow command history", null, new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		private Action UNDO = createUndoAction();
		private Action REDO = createRedoAction();
		public Action USE_SELECTED_COLOR = createSelectableCommandAction(PathPrefs.useSelectedColorProperty(), "Highlight selected objects by color");
		private Action SHOW_COMMAND_LIST = createCommandAction(new CommandListDisplayCommand(QuPathGUI.this), "Show command list", null, new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN));
		private Action COLOR_DECONVOLUTION_REFINE = createCommandAction(new EstimateStainVectorsCommand(QuPathGUI.this), "Estimate stain vectors");
		
	}
	
	private ActionManager actionManager;
	
	public synchronized ActionManager getActionManager() {
		if (actionManager == null)
			actionManager = new ActionManager();
		return actionManager;
	}
	
	
	private Action createUndoAction() {
		Action actionUndo = new Action("Undo", e -> undoRedoManager.undoOnce());
		actionUndo.disabledProperty().bind(undoRedoManager.canUndo().not());
		actionUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCodeCombination.SHORTCUT_DOWN));
		return actionUndo;
	}
	
	private Action createRedoAction() {
		Action actionRedo = new Action("Redo", e -> undoRedoManager.redoOnce());
		actionRedo.disabledProperty().bind(undoRedoManager.canRedo().not());
		actionRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_DOWN));
		return actionRedo;
	}
	
	private Action createShowAnalysisPaneAction() {
		ShowAnalysisPaneSelectable temp = new ShowAnalysisPaneSelectable(pane, splitPane, analysisPanel, viewerManager, true);
		var action = createSelectableCommandAction(temp.showPanelProperty(), "Show analysis panel", PathIconFactory.PathIcons.MEASURE, new KeyCodeCombination(KeyCode.A, KeyCombination.SHIFT_DOWN));
		action.selectedProperty().addListener((e, f, g) -> temp.setAnalysisPanelVisible(g));
		return action;
	}
	
	
	/**
	 * Create a QuPath instance, optionally initializing it with a path to open.
	 * <p>
	 * It is also possible to specify that QuPath runs as a standalone application or not.
	 * The practical difference is that, if a standalone application, QuPath may call System.exit(0)
	 * when its window is closed; otherwise, it must not for fear or bringing the host application with it.
	 * <p>
	 * If QuPath is launched, for example, from a running Fiji instance then isStandalone should be false.
	 * 
	 * @param services host services available during startup; may be null, but required for some functionality (e.g. opening a webpage in the host browser)
	 * @param stage a stage to use for the main QuPath window (may be null)
	 * @param path path of an image, project or data file to open (may be null)
	 * @param isStandalone true if QuPath should be run as a standalone application
	 */
	QuPathGUI(final HostServices services, final Stage stage, final String path, final boolean isStandalone) {
		super();
		
		this.hostServices = services;
		
		if (PathPrefs.doCreateLogFilesProperty().get()) {
			File fileLogging = tryToStartLogFile();
			if (fileLogging != null) {
				logger.info("Logging to file {}", fileLogging);
			} else {
				logger.warn("No directory set for log files! None will be written.");
			}
		}
		
		updateBuildString();
		logger.info("QuPath build: {}", buildString);
		
		long startTime = System.currentTimeMillis();
		
		// Set up cache
		imageRegionStore = ImageRegionStoreFactory.createImageRegionStore(PathPrefs.getTileCacheSizeBytes());
		
		PathPrefs.tileCacheProportionProperty().addListener((v, o, n) -> {
			imageRegionStore.getCache().clear();
		});
		
		ImageServerProvider.setCache(imageRegionStore.getCache(), BufferedImage.class);
		
		this.stage = stage;
		this.isStandalone = isStandalone;
		
		menuBar = new MenuBar();
		
		// Prepare for image name masking
		project.addListener((v, o, n) -> {
			if (n != null)
				n.setMaskImageNames(PathPrefs.getMaskImageNames());
			updateTitle();
		});
		PathPrefs.maskImageNamesProperty().addListener(((v, o, n) -> {
			var currentProject = getProject();
			if (currentProject != null) {
				currentProject.setMaskImageNames(n);
			}
		}));
		
		// Create preferences panel
		prefsPanel = new PreferencePanel(this);
		
		// Set the number of threads at an early stage...
		AbstractPluginRunner.setNumThreadsRequested(PathPrefs.getNumCommandThreads());
		PathPrefs.numCommandThreadsProperty().addListener(o -> AbstractPluginRunner.setNumThreadsRequested(PathPrefs.getNumCommandThreads()));
		
		// Activate the log at an early stage
		// TODO: NEED TO TURN ON LOG!
//		Action actionLog = createAction(GUIActions.SHOW_LOG);
		
		// Turn off the use of ImageIODiskCache (it causes some trouble)
		ImageIO.setUseCache(false);
		
		// Initialize available classes
		initializePathClasses();
		
		logger.trace("Time to tools: {} ms", (System.currentTimeMillis() - startTime));
		
		// Initialize all tools
//		initializeTools();

		// Initialize main GUI
//		initializeMainComponent();
		
		// Set this as the current instance
		if (instance == null || instance.getStage() == null || !instance.getStage().isShowing())
			instance = this;
		
		// Ensure the user is notified of any errors from now on
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				Dialogs.showErrorNotification("QuPath exception", e);
				if (actionManager.SHOW_LOG != null)
					actionManager.SHOW_LOG.handle(null);
				// Try to reclaim any memory we can
				if (e instanceof OutOfMemoryError) {
					getViewer().getImageRegionStore().clearCache(false, false);
				}
			}
		});
		
		
		logger.trace("Time to main component: {} ms", (System.currentTimeMillis() - startTime));

		BorderPane pane = new BorderPane();
		pane.setCenter(initializeMainComponent());
		
		logger.trace("Time to menu: {} ms", (System.currentTimeMillis() - startTime));
		
		initializingMenus.set(true);
		menuBar.useSystemMenuBarProperty().bindBidirectional(PathPrefs.useSystemMenubarProperty());
		createMenuBar();
		pane.setTop(menuBar);
		
//		CommandAnnotations.installAnnotatedCommands(this, CommandAnnotations.class);
		
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
				} else if (!Dialogs.showYesNoDialog("Quit QuPath", "Are you sure you want to quit?\n\nUnsaved changes in " + unsavedViewers.size() + " viewers will be lost.")) {
					logger.trace("Pressed no to quit window!");
					e.consume();
					return;
				}
			}
			// Stop any painter requests
			if (imageRegionStore != null)
				imageRegionStore.close();
			
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
				try {
					if (v.getImageData() != null)
						v.getImageData().getServer().close();
				} catch (Exception e2) {
					logger.warn("Problem closing server", e2);
				}
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
					// Set spacebar for only the active viewer (since it results in registering 
					// tools, and we don't want tools to be registered to inactive viewers...)
					var active = viewerManager.getActiveViewer();
					if (active != null)
						active.setSpaceDown(pressed.booleanValue());
//					for (QuPathViewer viewer : viewerManager.getOpenViewers()) {
//						viewer.setSpaceDown(pressed.booleanValue());
//					}
				}
			}
		});
		
		stage.getScene().setOnKeyReleased(e -> {
			// Previously we needed to mop up some shortcuts when using the System menubar on macOS, but apparently not any more (?_
			if (e.isConsumed() || e.getTarget() instanceof TextInputControl) {
				return;
			}
			
			for (var entry : comboMap.entrySet()) {
				if (entry.getKey().match(e)) {
					var action = entry.getValue();
					if (ActionTools.isSelectable(action))
						action.setSelected(!action.isSelected());
					else
						action.handle(new ActionEvent(e.getSource(), e.getTarget()));
					e.consume();
					return;
				}
			}
			
			// Generic 'hiding'
			if (new KeyCodeCombination(KeyCode.H).match(e)) {
				var action = actionManager.SHOW_DETECTIONS;
				action.setSelected(!action.isSelected());
				action = actionManager.SHOW_PIXEL_CLASSIFICATION;
				action.setSelected(!action.isSelected());
			}
			
		});
		
		// Install extensions
		refreshExtensions(false);
		
		// Open an image, if required
		if (path != null)
			openImage(path, false, false);
		
		// Set the icons
		stage.getIcons().addAll(loadIconList());
		
		
		// Add scripts menu (delayed to here, since it takes a bit longer)
		Menu menuAutomate = getMenu("Automate", false);
		ScriptEditor editor = getScriptEditor();
		sharedScriptMenuLoader = new ScriptMenuLoader("Shared scripts...", PathPrefs.scriptsPathProperty(), (DefaultScriptEditor)editor);
		
		// TODO: Reintroduce project scripts
		StringBinding projectScriptsPath = Bindings.createStringBinding(() -> {
			var project = getProject();
			if (project == null)
				return null;
			File dir = Projects.getBaseDirectory(project);
			if (dir == null)
				return null;
			return new File(dir, "scripts").getAbsolutePath();
//			return getProjectScriptsDirectory(false).getAbsolutePath();
		}, project);
		var projectScriptMenuLoader = new ScriptMenuLoader("Project scripts...", projectScriptsPath, (DefaultScriptEditor)editor);
		projectScriptMenuLoader.getMenu().visibleProperty().bind(
				project.isNotNull().and(initializingMenus.not())
				);
		
		StringBinding userScriptsPath = Bindings.createStringBinding(() -> {
			String userPath = PathPrefs.getUserPath();
			File dirScripts = userPath == null ? null : new File(userPath, "scripts");
			if (dirScripts == null || !dirScripts.isDirectory())
				return null;
			return dirScripts.getAbsolutePath();
		}, PathPrefs.userPathProperty());
		ScriptMenuLoader userScriptMenuLoader = new ScriptMenuLoader("User scripts...", userScriptsPath, (DefaultScriptEditor)editor);

		menuAutomate.setOnMenuValidation(e -> {
			sharedScriptMenuLoader.updateMenu();
			projectScriptMenuLoader.updateMenu();
			userScriptMenuLoader.updateMenu();
		});

		if (editor instanceof DefaultScriptEditor) {
			MenuTools.addMenuItems(
					menuAutomate,
					null,
					createCommandAction(new SampleScriptLoader(this), "Open sample scripts"),
					sharedScriptMenuLoader.getMenu(),
					userScriptMenuLoader.getMenu(),
					projectScriptMenuLoader.getMenu()
					);
		}
		
		// Update action states
		updateProjectActionStates();
		
		// Listen for cache request changes
		PathPrefs.useProjectImageCacheProperty().addListener(v -> updateProjectActionStates());
		
		// Menus should now be complete - try binding visibility
		initializingMenus.set(false);
		try {
			for (var item : MenuTools.getFlattenedMenuItems(menuBar.getMenus(), false)) {
				if (!item.visibleProperty().isBound())
					bindVisibilityForExperimental(item);
			}
		} catch (Exception e) {
			logger.warn("Error binding menu visibility: {}", e.getLocalizedMessage());
			logger.warn("", e);
		}
		
		// Update the title
		stage.titleProperty().bind(titleBinding);
		
		// Update display
		// Requesting the style should be enough to make sure it is called...
		logger.info("Selected style: {}", QuPathStyleManager.selectedStyleProperty().get());
				
		long endTime = System.currentTimeMillis();
		logger.debug("Startup time: {} ms", (endTime - startTime));
		
		// Do auto-update check
		if (!disableAutoUpdateCheck)
			checkForUpdate(true);
		
		// Show a startup message, if we have one
		showStarupMesssage();
		
		// Run startup script, if we can
		try {
			runStartupScript();			
		} catch (Exception e) {
			logger.error("Error running startup script", e);
		}
	}
	
	
	/**
	 * Try to start logging to a file.
	 * This will only work if <code>PathPrefs.getLoggingPath() != null</code>.
	 * 
	 * @return the file that will (attempt to be) used for logging, or <code>null</code> if no file is to be used.
	 */
	private static File tryToStartLogFile() {
		String pathLogging = PathPrefs.getLoggingPath();
		if (pathLogging != null) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
			String name = "qupath-" + dateFormat.format(new Date()) + ".log";
			File fileLog = new File(pathLogging, name);
			LoggingAppender.getInstance().addFileAppender(fileLog);
			return fileLog;
		}
		return null;
	}
	
	
	void showStarupMesssage() {
		File fileStartup = new File("STARTUP.md");
		if (!fileStartup.exists()) {
			fileStartup = new File("app", fileStartup.getName());
			if (!fileStartup.exists()) {
				logger.trace("No startup file found in {}", fileStartup.getAbsolutePath());
				return;
			}
		}
		try {
			TextArea textArea = new TextArea();
			String text = GeneralTools.readFileAsString(fileStartup.getAbsolutePath());
			textArea.setText(text);
			textArea.setWrapText(true);
			textArea.setEditable(false);
			Platform.runLater(() -> {
				Stage stage = new Stage();
				stage.setTitle("QuPath");
				stage.initOwner(getStage());
				Scene scene = new Scene(textArea);
				textArea.setPrefHeight(500);
				stage.setScene(scene);
				textArea.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
					if (e.getClickCount() == 2)
						stage.hide();
				});
				stage.showAndWait();
//				DisplayHelpers.showMessageDialog(
//						"QuPath",
//						textArea);
			});
		} catch (Exception e) {
			logger.error("Error reading " + fileStartup.getAbsolutePath(), e);
		}
	}
	
	/**
	 * Static method to launch QuPath on the JavaFX Application thread.
	 * <p>
	 * This can be used from other applications (e.g. MATLAB).
	 * Afterwards, calls to getInstance() will return the QuPath instance.
	 * <p>
	 * If there is already an instance of QuPath running, this ensures that it is visible - but otherwise does nothing.
	 * <p>
	 * If {@link HostServices} are available, {@link #launchQuPath(HostServices)} should be used instead.
	 * This method exists to make it easier to call using reflection whenever {@link HostServices} are not present.
	 */
	public static void launchQuPath() {
		launchQuPath(null);
	}
	
	
	/**
	 * Static method to launch QuPath on the JavaFX Application thread.
	 * <p>
	 * This can be used from other applications (e.g. MATLAB).
	 * Afterwards, calls to getInstance() will return the QuPath instance.
	 * <p>
	 * If there is already an instance of QuPath running, this ensures that it is visible - but otherwise does nothing.
	 * 
	 * @param hostServices JavaFX HostServices if available, otherwise null
	 */
	public static void launchQuPath(HostServices hostServices) {
		if (!Platform.isFxApplicationThread()) {
			System.out.println("Requesting QuPath launch in JavaFX thread...");
			logger.info("Requesting QuPath launch in JavaFX thread...");
			new JFXPanel(); // To initialize
			Platform.runLater(() -> launchQuPath(hostServices));
			logger.info("Request sent");
			System.out.println("Request sent");
			return;
		}
		try {
			if (getInstance() == null){
				System.out.println("Launching new QuPath instance...");
				logger.info("Launching new QuPath instance...");
				Stage stage = new Stage();
				QuPathGUI qupath = new QuPathGUI(hostServices, stage, (String)null, false);
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
	 * @param url the URL to open in the browser
	 * @return true if this was (as far as we know...) successful, and false otherwise
	 */
	public static boolean launchBrowserWindow(final String url) {
		var instance = getInstance();
		if (instance != null && instance.hostServices != null) {
			logger.debug("Showing URL with host services: {}", url);
			instance.hostServices.showDocument(url);
			return true;
		}
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
			try {
				Desktop.getDesktop().browse(new URI(url));
				return true;
			} catch (Exception e) {
				logger.error("Failed to launch browser window for {}", url, e);
				return false;
			}
		} else {
			Dialogs.showErrorMessage("Show URL", "Sorry, unable to launch a browser to open \n" + url);
			return false;
		}
	}
	
	
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
		return dir;
	}
	
	
	/**
	 * Get the default location for extensions.
	 * 
	 * This is platform and user-specific.  It isn't necessarily used (and doesn't necessarily exist).
	 * 
	 * @return
	 */
	private static File getDefaultQuPathUserDirectory() {
		return new File(System.getProperty("user.home"), "QuPath");
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
	}
		
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
		double diffHours = (double)(currentTime - lastUpdateCheck) / (60L * 60L * 1000L);
		if (isAutoCheck && diffHours < 1)
			return;
		
		// See if we can read the current ChangeLog
		File fileChanges = new File("CHANGELOG.md");
		if (!fileChanges.exists()) {
			logger.warn("No changelog found - will not check for updates");
			if (!isAutoCheck) {
				Dialogs.showErrorMessage("Update check", "Cannot check for updates at this time, sorry");
			}
			return;
		}
		String changeLog = null;
		try {
			changeLog = GeneralTools.readFileAsString(fileChanges.getAbsolutePath());
		} catch (IOException e1) {
			if (!isAutoCheck) {
				Dialogs.showErrorMessage("Update check", "Cannot check for updates at this time, sorry");
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
				String changeLogOnline = GeneralTools.readURLAsString(url, 2000);
				
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
							Dialogs.showMessageDialog("Update check", "QuPath is up-to-date!");
						});
					}
					return;
				}
				
				// If changelogs are different, notify the user
				showChangelogForUpdate(changeLogOnline);
			} catch (Exception e) {
				// Notify the user if we couldn't read the log
				if (!isAutoCheck) {
					Dialogs.showMessageDialog("Update check", "Unable to check for updates at this time, sorry");
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
			String url = "https://qupath.github.io";
			try {
				GuiTools.browseURI(new URI(url));
			} catch (URISyntaxException e) {
				Dialogs.showErrorNotification("Download", "Unable to open " + url);
			}
		} else if (result.get().equals(btDoNotRemind)) {
			PathPrefs.setDoAutoUpdateCheck(false);
		}
	}
	
	
	
	/**
	 * Keep a record of loaded extensions, both for display and to avoid loading them twice.
	 */
	private static Map<Class<? extends QuPathExtension>, QuPathExtension> loadedExtensions = new HashMap<>();
	
	/**
	 * @return a collection of extensions that are currently loaded
	 */
	public Collection<QuPathExtension> getLoadedExtensions() {
		return loadedExtensions.values();
	}
	
	/**
	 * Check the extensions directory, loading any new extensions found there.
	 * @param showNotification if true, display a notification if a new extension has been loaded
	 */
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
				try {
					long startTime = System.currentTimeMillis();
					extension.installExtension(this);
					long endTime = System.currentTimeMillis();
					logger.info("Loaded extension {} ({} ms)", extension.getName(), endTime - startTime);
					loadedExtensions.put(extension.getClass(), extension);
					if (showNotification)
						Dialogs.showInfoNotification("Extension loaded",  extension.getName());
				} catch (Exception e) {
					logger.error("Error loading extension " + extension, e);
				}
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
				Dialogs.showInfoNotification("Image server loaded",  builderName);
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
			Dialogs.showErrorMessage("Install extension", "Cannot install extensions when not running QuPath from a .jar file (application), sorry!");
			return;
		}
		File dir = getExtensionDirectory();
		if (dir == null || !dir.isDirectory()) {
			logger.info("No extension directory found!");
			// Prompt to create an extensions directory
			File dirDefault = getDefaultQuPathUserDirectory();
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
					Dialogs.showErrorMessage("Extension error", "Unable to create directory at \n" + dirDefault.getAbsolutePath());
					return;
				}
				PathPrefs.setUserPath(dirDefault.getAbsolutePath());
			} else {
				File dirUser = getDialogHelper().promptForDirectory(dirDefault);
				if (dirUser == null) {
					logger.info("No QuPath user directory set - extensions not installed");
					return;
				}
				PathPrefs.setUserPath(dirUser.getAbsolutePath());
			}
			// Now get the extensions directory (within the user directory)
			dir = getExtensionDirectory();
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
				if (!Dialogs.showConfirmDialog("Install extension", "Overwrite " + destination.toFile().getName() + "?\n\nYou will have to restart QuPath to see the updates."))
					return;
			}
			try {
				Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				Dialogs.showErrorMessage("Extension error", file + "\ncould not be copied, sorry");
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
				project.setPathClasses(c.getList());
//				if (project.setPathClasses(c.getList())
//					ProjectBrowser.syncProject(project);
			}
		});
	}
	
	
	/**
	 * Populate the availablePathClasses with a default list.
	 * 
	 * @return true if changes were mad to the available classes, false otherwise
	 */
	public boolean resetAvailablePathClasses() {
		List<PathClass> pathClasses = new ArrayList<>(); 
		pathClasses.add(PathClassFactory.getPathClassUnclassified());
		
		pathClasses.add(PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.TUMOR));
		pathClasses.add(PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.STROMA));
		pathClasses.add(PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IMMUNE_CELLS));
		pathClasses.add(PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.NECROSIS));
		pathClasses.add(PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.OTHER));
		pathClasses.add(PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.REGION));
		pathClasses.add(PathClassFactory.getPathClass(PathClassFactory.StandardPathClasses.IGNORE));
		
		if (availablePathClasses == null) {
			availablePathClasses = FXCollections.observableArrayList(pathClasses);
			return true;
		} else
			return availablePathClasses.setAll(pathClasses);
	}
	
	/**
	 * Load PathClasses from preferences.
	 * Note that this also sets the color of any PathClass that is loads,
	 * and is really only intended for use when initializing.
	 * 
	 * @return
	 */
	private static List<PathClass> loadPathClasses() {
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
				.addEmptyParameter("Set the maximum memory used by QuPath, or -1 to use the default.")
				.addEmptyParameter(maxMemoryString);

		boolean lowMemory = maxMemoryMB < 1024*6;
		if (lowMemory) {
			paramsSetup.addEmptyParameter(
					"It is suggested to increase the memory limit to approximately\nhalf of the RAM available on your computer."
					);
		}

//				.addEmptyParameter("memoryString2", "Current ")
		paramsSetup.addDoubleParameter("maxMemoryGB", "Maximum memory (GB)", Math.ceil(maxMemoryMB/1024.0), null, "Set the maximum memory for QuPath - considering using approximately half the total RAM for the system")
				.addTitleParameter("Region")
				.addEmptyParameter("Set the region for QuPath to use for displaying numbers and messages.")
				.addEmptyParameter("Note: It is highly recommended to keep the default (English, US) region settings.")
				.addEmptyParameter("Support for regions that use different number formatting (e.g. commas as decimal marks)\nis still experimental, and may give unexpected results.")
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
			int maxMemorySpecifiedMB = (int)(Math.round(paramsSetup.getDoubleParameterValue("maxMemoryGB") * 1024));
			if (maxMemorySpecifiedMB > 512) {
				PathPrefs.maxMemoryMBProperty().set(maxMemorySpecifiedMB);
			} else {
				if (maxMemorySpecifiedMB >= 0)
					Dialogs.showErrorNotification("Max memory setting", "Specified maximum memory setting too low - will ignore");
//				PathPrefs.maxMemoryMBProperty().set(-1);
			}
		} else {
			Dialogs.showWarningNotification("Max memory", "Cannot set maximum memory preferences");
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
				((TreeView<?>)child).refresh();
			else if (child instanceof ListView<?>)
				((ListView<?>)child).refresh();
			else if (child instanceof TableView<?>)
				((TableView<?>)child).refresh();
			else if (child instanceof TreeTableView<?>)
				((TreeTableView<?>)child).refresh();
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
		
		pane = new BorderPane();
		
		// Create a reasonably-sized viewer
		QuPathViewerPlus viewer = new QuPathViewerPlus(null, imageRegionStore, overlayOptions, viewerDisplayOptions);

		// Add analysis panel & viewer to split pane
		viewerManager = new MultiviewManager(viewer);
		
		// Now that we have a viewer, we can create an undo/redo manager
		undoRedoManager = new UndoRedoManager(this);

		
//		analysisPanel = createAnalysisPanel();
		createAnalysisPanel();
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
		
		pane.setCenter(splitPane);
		toolbar = new ToolBarComponent(this);
		pane.setTop(toolbar.getToolBar());
		
//		setInitialLocationAndMagnification(getViewer());

		// Prepare the viewer
		setupViewer(viewerManager.getActiveViewer());

		// Ensure the mode is set

		// Ensure actions are set appropriately
		selectedToolProperty.addListener((v, o, n) -> {
			Action action = modeActions.get(n);
			if (action != null)
				action.setSelected(true);
			
			activateTools(getViewer());
			
			if (n == PathTools.POINTS)
				actionManager.COUNTING_PANEL.handle(null);
			
			updateCursor();			
		});
		setMode(getSelectedTool());
		
		return pane;
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
		try (InputStream stream = getExtensionClassLoader().getResourceAsStream(path)) {
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
	 * Query whether this is a standalone QuPathGUI instance, as flagged during startup.
	 * It can be important to know this so as to avoid calling System.exit(0) or similar, 
	 * and bringing down some other application entirely.
	 * 
	 * @return true if this is a standalone QuPathGUI instance, false otherwise
	 */
	public boolean isStandalone() {
		return isStandalone;
	}
	
	
	public List<QuPathViewerPlus> getViewers() {
		if (viewerManager == null)
			return Collections.emptyList();
		return viewerManager.getViewers();
	}
	
	
	public QuPathViewerPlus getViewer() {
		return viewerManager == null ? null : viewerManager.getActiveViewer();
	}
	
	/**
	 * Get the static instance of the current QuPath GUI.
	 * @return
	 */
	public static QuPathGUI getInstance() {
		return instance;
	}
	
	
	void activateTools(final QuPathViewerPlus viewer) {
		if (viewer != null)
			viewer.setMode(getSelectedTool());		
//		logger.debug("Tools activated for {}", viewer);
	}
	
	
	void deactivateTools(final QuPathViewerPlus viewer) {
		viewer.setMode(null);
//		// Deregister tools
//		for (Entry<Modes, PathTool> entry : tools.entrySet())
//			entry.getValue().deregisterTool(viewer);
//		logger.debug("Tools deactivated for {}", viewer);
	}
	
	
	/**
	 * Get the {@link ClassLoader} used to load extensions.
	 * @return
	 */
	public static ClassLoader getExtensionClassLoader() {
		return extensionClassLoader;
	}
	
	
	/**
	 * Get a reference to the default drag &amp; drop listener, so this may be added to additional windows if needed.
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
				
				// Avoid zooming at the end of a gesture when using touchscreens
				if (e.isInertia())
					return;
				
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
		
		viewer.getView().addEventFilter(ScrollEvent.ANY, new ScrollEventPanningFilter(viewer));
		
		
		viewer.getView().addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			PathObject pathObject = viewer.getSelectedObject();
			if (!e.isConsumed() && pathObject != null) {
				if (pathObject.isTMACore()) {
					TMACoreObject core = (TMACoreObject)pathObject;
					if (e.getCode() == KeyCode.ENTER) {
						actionManager.TMA_ADD_NOTE.handle(new ActionEvent(e.getSource(), e.getTarget()));
						e.consume();
					} else if (e.getCode() == KeyCode.BACK_SPACE) {
						core.setMissing(!core.isMissing());
						viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(core));
						e.consume();
					}
				} else if (pathObject.isAnnotation()) {
					if (e.getCode() == KeyCode.ENTER) {
						GuiTools.promptToSetActiveAnnotationProperties(viewer.getHierarchy());
						e.consume();
					}
				}
			}
		});
		

	}
	
	
	
	
	static class ScrollEventPanningFilter implements EventHandler<ScrollEvent> {
		
		private QuPathViewer viewer;
		private boolean lastTouchEvent = false;
		private double deltaX = 0;
		private double deltaY = 0;
		private long lastTimestamp = 0L;
		
		ScrollEventPanningFilter(final QuPathViewer viewer) {
			this.viewer = viewer;
		}

		@Override
		public void handle(ScrollEvent e) {
			// Check if we'd rather be using scroll to do something else (e.g. zoom, adjust opacity)
			boolean wouldRatherDoSomethingElse = e.getTouchCount() == 0 && (!PathPrefs.getUseScrollGestures() || e.isShiftDown() || e.isShortcutDown());
			if (wouldRatherDoSomethingElse) {
				return;
			}
			
			// Don't pan with inertia events (use the 'mover' instead)
			if (e.isInertia()) {
				e.consume();
				return;
			}
			
			// Return if we aren't using a touchscreen, and we don't want to handle scroll gestures - 
			// but don't consume the event so that it can be handled elsewhere
			lastTouchEvent = e.getTouchCount() != 0;
			if (!lastTouchEvent && !PathPrefs.getUseScrollGestures() || e.isShiftDown() || e.isShortcutDown()) {
				return;
			}
			// Swallow the event if we're using a touch screen without the move tool selected - we want to draw instead
			if (lastTouchEvent && viewer.getActiveTool() != PathTools.MOVE) {
				e.consume();
				return;
			}
			
//			// If this is a SCROLL_FINISHED event, continue moving with the last starting velocity - but ignore inertia
			if (!lastTouchEvent && e.getEventType() == ScrollEvent.SCROLL_FINISHED) {
				if (System.currentTimeMillis() - lastTimestamp < 100L) {
					viewer.requestStartMoving(deltaX, deltaY);
					viewer.requestDecelerate();					
				}
				deltaX = 0;
				deltaY = 0;
				e.consume();
				return;
			}
//			viewer.requestStopMoving();
			
			// Use downsample since shift will be defined in full-resolution pixel coordinates
			double dx = e.getDeltaX() * viewer.getDownsampleFactor();
			double dy = e.getDeltaY() * viewer.getDownsampleFactor();
			
			// When e.isInertia() == TRUE on OSX, the results are quite annoyingly 'choppy' - x,y values are often passed separately
//			System.err.println(String.format("dx=%.1f, dy=%.1f %s", e.getDeltaX(), e.getDeltaY(), (e.isInertia() ? "-Inertia" : "")));
			
			// Flip scrolling direction if necessary
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

			// Shift the viewer
			viewer.setCenterPixelLocation(
					viewer.getCenterPixelX() - dx,
					viewer.getCenterPixelY() - dy);
			
			// Retain deltas in case we need to decelerate later
			deltaX = dx;
			deltaY = dy;
			lastTimestamp = System.currentTimeMillis();
			
//			if (deltaX == 0 && deltaY == 0) {
//				viewer.requestStopMoving();
//			} else {
//				viewer.requestStartMoving(deltaX, deltaY);
//				viewer.requestDecelerate();				
//			}
			e.consume();
		}
		
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
		MenuItem miToggleSync = ActionTools.getActionCheckBoxMenuItem(actionManager.TOGGLE_SYNCHRONIZE_VIEWERS, null);
		MenuItem miMatchResolutions = ActionTools.getActionMenuItem(actionManager.MATCH_VIEWER_RESOLUTIONS);
		Menu menuMultiview = MenuTools.createMenu(
				"Multi-view",
				miToggleSync,
				miMatchResolutions,
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
		
		Menu menuView = MenuTools.createMenu(
				"Display",
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_ANALYSIS_PANEL, null),
				actionManager.BRIGHTNESS_CONTRAST,
				null,
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 0.25), "400%"),
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 1), "100%"),
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 2), "50%"),
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 10), "10%"),
				createCommandAction(new ViewerSetDownsampleCommand(viewer, 100), "1%"),
				null,
				actionManager.ZOOM_IN,
				actionManager.ZOOM_OUT,
				ActionTools.getActionCheckBoxMenuItem(actionManager.ZOOM_TO_FIT, null)
				);
		
		ToggleGroup groupTools = new ToggleGroup();
		Menu menuTools = MenuTools.createMenu(
				"Set tool",
				ActionTools.getActionCheckBoxMenuItem(actionManager.MOVE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.RECTANGLE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.ELLIPSE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.LINE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.POLYGON_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.POLYLINE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.BRUSH_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.POINTS_TOOL, groupTools)
//				ActionTools.getActionCheckBoxMenuItem(actionManager.WAND_TOOL, groupTools)
				);

		
		// Handle awkward 'TMA core missing' option
		CheckMenuItem miTMAValid = new CheckMenuItem("Set core valid");
		miTMAValid.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), false));
		CheckMenuItem miTMAMissing = new CheckMenuItem("Set core missing");
		miTMAMissing.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), true));
		
		Menu menuTMA = new Menu("TMA");
		MenuTools.addMenuItems(
				menuTMA,
				miTMAValid,
				miTMAMissing,
				null,
				actionManager.TMA_ADD_NOTE,
				null,
				MenuTools.createMenu(
						"Add",
					createCommandAction(new TMAGridAdd(this, TMAAddType.ROW_BEFORE), "Add TMA row before"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.ROW_AFTER), "Add TMA row after"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.COLUMN_BEFORE), "Add TMA column before"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.COLUMN_AFTER), "Add TMA column after")
					),
				MenuTools.createMenu(
						"Remove",
					createCommandAction(new TMAGridRemove(this, TMARemoveType.ROW), "Remove TMA row"),
					createCommandAction(new TMAGridRemove(this, TMARemoveType.COLUMN), "Remove TMA column")
					),
				createCommandAction(new TMAGridRelabel(this), "Relabel TMA grid")
				);
		
		
		// Create an empty placeholder menu
		Menu menuSetClass = MenuTools.createMenu("Set class");
		
		
//		CheckMenuItem miTMAValid = new CheckMenuItem("Set core valid");
//		miTMAValid.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), false));
//		CheckMenuItem miTMAMissing = new CheckMenuItem("Set core missing");
//		miTMAMissing.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), true));
		
		
		Menu menuCells = MenuTools.createMenu(
				"Cells",
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_CELL_BOUNDARIES_AND_NUCLEI, null),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_CELL_NUCLEI, null),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_CELL_BOUNDARIES, null),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_CELL_CENTROIDS, null)
				);

		
		
		MenuItem miClearSelectedObjects = new MenuItem("Delete object");
		miClearSelectedObjects.setOnAction(e -> {
			PathObjectHierarchy hierarchy = viewer.getHierarchy();
			if (hierarchy == null)
				return;
			if (hierarchy.getSelectionModel().singleSelection()) {
				GuiTools.promptToRemoveSelectedObject(hierarchy.getSelectionModel().getSelectedObject(), hierarchy);
			} else {
				GuiTools.promptToClearAllSelectedObjects(viewer.getImageData());
			}
		});
		
		// Create a standard annotations menu
		Menu menuAnnotations = GuiTools.populateAnnotationsMenu(this, new Menu("Annotations"));
		
		SeparatorMenuItem topSeparator = new SeparatorMenuItem();
		popup.setOnShowing(e -> {
			// Check if we have any cells
			ImageData<?> imageData = viewer.getImageData();
			if (imageData == null)
				menuCells.setVisible(false);
			else
				menuCells.setVisible(!imageData.getHierarchy().getDetectionObjects().isEmpty());
			
			
			// Check what to show for TMA cores or annotations
			Collection<PathObject> selectedObjects = viewer.getAllSelectedObjects();
			PathObject pathObject = viewer.getSelectedObject();
			menuTMA.setVisible(false);
			if (pathObject instanceof TMACoreObject) {
				boolean isMissing = ((TMACoreObject)pathObject).isMissing();
				miTMAValid.setSelected(!isMissing);
				miTMAMissing.setSelected(isMissing);
				menuTMA.setVisible(true);
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
			
			boolean hasAnnotations = pathObject instanceof PathAnnotationObject || (!selectedObjects.isEmpty() && selectedObjects.stream().allMatch(p -> p.isAnnotation()));
			
			updateSetAnnotationPathClassMenu(menuSetClass, viewer);
			menuAnnotations.setVisible(hasAnnotations);
			topSeparator.setVisible(hasAnnotations || pathObject instanceof TMACoreObject);
			// Occasionally, the newly-visible top part of a popup menu can have the wrong size?
			popup.setWidth(popup.getPrefWidth());
		});
		
		
//		popup.add(menuClassify);
		popup.getItems().addAll(
				miClearSelectedObjects,
				menuTMA,
				menuSetClass,
				menuAnnotations,
				topSeparator,
				menuMultiview,
				menuCells,
				menuView,
				menuTools
				);
		
		popup.setAutoHide(true);
		
		// Enable circle pop-up for quick classification on right-click
		CirclePopupMenu circlePopup = new CirclePopupMenu(viewer.getView(), null);
		viewer.getView().addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
			if ((e.isPopupTrigger() || e.isSecondaryButtonDown()) && e.isShiftDown() && !getAvailablePathClasses().isEmpty()) {
				circlePopup.setAnimationDuration(Duration.millis(200));
				updateSetAnnotationPathClassMenu(circlePopup, viewer);
				circlePopup.show(e.getScreenX(), e.getScreenY());
				e.consume();
				return;
			} else if (circlePopup.isShown())
				circlePopup.hide();
				
			if (e.isPopupTrigger() || e.isSecondaryButtonDown()) {
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
	 * Update a 'set annotation class' menu for a viewer immediately prior to display
	 * 
	 * @param menuSetClass
	 * @param viewer
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
			String name = pathClass.getName() == null ? "None" : pathClass.toString();
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
				Ellipse r = new Ellipse(TOOLBAR_ICON_SIZE/2.0, TOOLBAR_ICON_SIZE/2.0, TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE);
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
	 * Open the image represented by the specified ProjectImageEntry.
	 * <p>
	 * If an image is currently open, this command will prompt to save any changes.
	 * 
	 * @param entry
	 * @return 
	 */
	public boolean openImageEntry(ProjectImageEntry<BufferedImage> entry) {
		Project<BufferedImage> project = getProject();
		if (entry == null || project == null)
			return false;
		
		// Check if we're changing ImageData at all
		var viewer = getViewer();
		ImageData<BufferedImage> imageData = viewer.getImageData();
		if (imageData != null && project.getEntry(imageData) == entry) {
			return false;
		}
//		if (imageData != null && imageData.getServerPath().equals(entry.getServerPath()))
//			return false;
		
		// Check to see if the ImageData is already open in another viewer - if so, just activate it
//		String path = entry.getServerPath();
		for (QuPathViewerPlus v : viewerManager.getViewers()) {
			ImageData<BufferedImage> data = v.getImageData();
			if (data != null && project.getEntry(data) == entry) {
//			if (data != null && data.getServer().getPath().equals(path)) {
				viewerManager.setActiveViewer(v);
				return true;
			}
		}
		
		// If the current ImageData belongs to the current project, check if there are changes to save
		if (imageData != null && project != null) {
			if (!checkSaveChanges(imageData))
				return false;
		}

		// Check if we need to rotate the image
		try {
			imageData = entry.readImageData();
			viewer.setImageData(imageData);
//			setInitialLocationAndMagnification(viewer);
			if (imageData != null && (imageData.getImageType() == null || imageData.getImageType() == ImageType.UNSET)) {
				if (PathPrefs.getAutoEstimateImageType()) {
					var type = GuiTools.estimateImageType(imageData.getServer(), imageRegionStore.getThumbnail(imageData.getServer(), 0, 0, true));
					logger.info("Image type estimated to be {}", type);
					imageData.setImageType(type);
					imageData.setChanged(false); // Don't want to retain this as a change resulting in a prompt to save the data
				} else if (PathPrefs.getPromptForImageType()) {
					PathImageDetailsPanel.promptToSetImageType(imageData);
				}
			}
			return true;
		} catch (Exception e) {
			Dialogs.showErrorMessage("Load ImageData", e);
			return false;
		}
	}
	
	
	ProjectImageEntry<BufferedImage> getProjectImageEntry(ImageData<BufferedImage> imageData) {
		var project = getProject();
		return project == null ? null : project.getEntry(imageData);
	}
	
	/**
	 * Check if changes need to be saved for an ImageData, prompting the user if necessary.
	 * <p>
	 * This will return true if the matter is adequately dealt with (no changes needed, 
	 * user saves changes, user declines to save changes) and false otherwise (i.e. user cancelled).
	 * 
	 * @param imageData
	 * @return
	 */
	boolean checkSaveChanges(ImageData<BufferedImage> imageData) {
		if (!imageData.isChanged())
			return true;
		ProjectImageEntry<BufferedImage> entry = getProjectImageEntry(imageData);
		String name = entry == null ? ServerTools.getDisplayableImageName(imageData.getServer()) : entry.getImageName();
		var response = Dialogs.showYesNoCancelDialog("Save changes", "Save changes to " + name + "?");
		if (response == DialogButton.CANCEL)
			return false;
		if (response == DialogButton.NO)
			return true;
		
		try {
			if (entry == null) {
				String lastPath = imageData.getLastSavedPath();
				File lastFile = lastPath == null ? null : new File(lastPath);
				File dirBase = lastFile == null ? null : lastFile.getParentFile();
				String defaultName = lastFile == null ? null : lastFile.getName();
				File file = getDialogHelper().promptToSaveFile("Save data", dirBase, defaultName, "QuPath data files", PathPrefs.getSerializationExtension());
				if (file == null)
					return false;
				PathIO.writeImageData(file, imageData);
			} else {
				entry.saveImageData(imageData);
				var project = getProject();
				if (project != null)
					project.syncChanges();
			}
			return true;
		} catch (IOException e) {
			Dialogs.showErrorMessage("Save ImageData", e);
			return false;
		}
	}
	
		
	
	
	/**
	 * Open a new whole slide image server or ImageData.
	 * If the path is the same as a currently-open server, do nothing.
	 * <p>
	 * If this encounters an exception, an error message will be shown.
	 * @param pathNew 
	 * @param prompt if true, give the user the opportunity to cancel opening if a whole slide server is already set
	 * @param includeURLs 
	 * @return true if the server was set for this GUI, false otherwise
	 */
	public boolean openImage(String pathNew, boolean prompt, boolean includeURLs) {
		try {
			return openImage(getViewer(), pathNew, prompt, includeURLs);
		} catch (IOException e) {
			Dialogs.showErrorMessage("Open image", e);
			return false;
		}
	}

	/**
	 * Open a new whole slide image server, or ImageData.
	 * If the path is the same as a currently-open server, do nothing.
	 * 
	 * @param viewer the viewer into which the image should be opened
	 * @param pathNew 
	 * @param prompt if true, give the user the opportunity to cancel opening if a whole slide server is already set
	 * @param includeURLs if true, any prompt should support URL input and not only a file chooser
	 * @return true if the server was set for this GUI, false otherwise
	 * @throws IOException 
	 */
	public boolean openImage(QuPathViewer viewer, String pathNew, boolean prompt, boolean includeURLs) throws IOException {
		
		if (viewer == null) {
			if (getViewers().size() == 1)
				viewer = getViewer();
			else {
				Dialogs.showErrorMessage("Open image", "Please specify the viewer where the image should be opened!");
				return false;
			}
		}
		
		ImageServer<BufferedImage> server = viewer.getServer();
		String pathOld = null;
		File fileBase = null;
		if (server != null) {
			var uris = server.getURIs();
			if (uris.size() == 1) {
				var uri = uris.iterator().next();
				pathOld = uri.toString();
				try {
					var path = GeneralTools.toPath(uri);
					if (path != null)
						fileBase = path.toFile().getParentFile();
				} catch (Exception e) {};
			}
//			pathOld = server.getPath();
//			try {
//				fileBase = new File(pathOld).getParentFile();
//			} catch (Exception e) {};
		}
		// Prompt for a path, if required
		File fileNew = null;
		if (pathNew == null) {
			if (includeURLs) {
				pathNew = getDialogHelper().promptForFilePathOrURL("Choose path", pathOld, fileBase, null);
				if (pathNew == null)
					return false;
				fileNew = new File(pathNew);
			} else {
				fileNew = getDialogHelper().promptForFile(null, fileBase, null);
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
			return openSavedData(viewer, fileNew, false, true);

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
					Dialogs.showErrorMessage("Open project", e);
					logger.error("Error opening project " + fileNew.getAbsolutePath(), e);
					return false;
				}
		}

		
		// Try opening an image, unless it's the same as the image currently open
		if (!pathNew.equals(pathOld)) {
			// If we have a project, show the import dialog
			if (getProject() != null) {
				List<ProjectImageEntry<BufferedImage>> entries = ProjectImportImagesCommand.promptToImportImages(this, pathNew);
				if (entries.isEmpty())
					return false;
				return openImageEntry(entries.get(0));
			}
			ImageServer<BufferedImage> serverNew = null;

			List<ImageServer<BufferedImage>> serverList = ImageServerProvider.getServerList(pathNew, BufferedImage.class);
			
			if (serverList.size() == 0) {
				String message = "Unable to build ImageServer for " + pathNew + ".\nSee View > Show log for more details";
				Dialogs.showErrorMessage("Unable to build server", message);
				return false;
			}
			else if (serverList.size() == 1) {
				serverNew = serverList.get(0);
			} else {
				ObservableList<ImageServer<BufferedImage>> serverObservableList = FXCollections.observableArrayList();
				for (ImageServer<BufferedImage> imageServer: serverList)
					serverObservableList.add(imageServer);
				List<ImageServer<BufferedImage>> serverImagesToOpen = promptSeriesSelector(this, serverObservableList);
				
				// Only allows one image to be opened
				if (!serverImagesToOpen.isEmpty()) serverNew = serverImagesToOpen.get(0);
				else return false;
			}

			if (serverNew != null) {
				if (pathOld != null && prompt && !viewer.getHierarchy().isEmpty()) {
					if (!Dialogs.showYesNoDialog("Replace open image", "Close " + ServerTools.getDisplayableImageName(server) + "?"))
						return false;
				}
				ImageData<BufferedImage> imageData = null;
				if (serverNew != null) {
					int minSize = PathPrefs.getMinPyramidDimension();
					if (serverNew.nResolutions() == 1 && Math.max(serverNew.getWidth(), serverNew.getHeight()) > minSize) {
						// Check if we have any hope at all with the current settings
						long estimatedBytes = (long)serverNew.getWidth() * (long)serverNew.getHeight() * (long)serverNew.nChannels() * (long)serverNew.getPixelType().getBytesPerPixel();
						double requiredBytes = estimatedBytes * (4.0/3.0);
						if (prompt && imageRegionStore != null && requiredBytes >= imageRegionStore.getTileCacheSize()) {
							logger.warn("Selected image is {} x {} x {} pixels ({})", serverNew.getWidth(), serverNew.getHeight(), serverNew.nChannels(), serverNew.getPixelType());
							Dialogs.showErrorMessage("Image too large",
									"Non-pyramidal image is too large for the available tile cache!\n" +
									"Try converting the image to a pyramidal file format, or increasing the memory available to QuPath.");
							return false;
						}
						// Offer to pyramidalize
						var serverWrapped = ImageServers.pyramidalize(serverNew);
						if (serverWrapped.nResolutions() > 1) {
							if (prompt) {
								var response = Dialogs.showYesNoCancelDialog("Auto pyramidalize",
										"QuPath works best with large images saved in a pyramidal format.\n\n" +
										"Do you want to generate a pyramid dynamically from " + ServerTools.getDisplayableImageName(serverNew) + "?" +
										"\n(This requires more memory, but is usually worth it)");
								if (response == DialogButton.CANCEL)
									return false;
								if (response == DialogButton.YES)
									serverNew = serverWrapped;
							}
						}
					}
					imageData = createNewImageData(serverNew);
				}
				
				viewer.setImageData(imageData);
//				setInitialLocationAndMagnification(viewer);

				if (imageData.getImageType() == ImageType.UNSET && PathPrefs.getPromptForImageType())
					PathImageDetailsPanel.promptToSetImageType(imageData);

//				// Reset the object hierarchy to clear any ROIs etc.
//				hierarchy.clearAll();
//				hierarchy.getSelectionModel().resetSelection();
				
				return true;
			} else {
				// Show an error message if we can't open the file
				Dialogs.showErrorNotification("Open image", "Sorry, I can't open " + pathNew);
//				logger.error("Unable to build whole slide server for path '{}'", pathNew);
			}
		}
		return false;
	}
	
	private ObservableValue<String> getSeriesQuickInfo(ImageServer<BufferedImage> imageServer, int index) {
		String filePath = imageServer.getURIs().iterator().next().toString();
		String serverType = imageServer.getServerType();
		String width = "" + imageServer.getWidth() + " px";
		String height = "" + imageServer.getHeight() + " px";
		double pixelWidthTemp = imageServer.getPixelCalibration().getPixelWidth().doubleValue();
		String pixelWidth = GeneralTools.formatNumber(pixelWidthTemp, 4) + " " + imageServer.getPixelCalibration().getPixelWidthUnit();
		double pixelHeightTemp = imageServer.getPixelCalibration().getPixelHeight().doubleValue();
		String pixelHeight = GeneralTools.formatNumber(pixelHeightTemp, 4) + " " + imageServer.getPixelCalibration().getPixelHeightUnit();
		String pixelType = imageServer.getPixelType().toString();
		String nChannels = String.valueOf(imageServer.nChannels());
		String nResolutions = String.valueOf(imageServer.nResolutions());
		String[] outString = new String[] {filePath, serverType, width, height, pixelWidth, pixelHeight, pixelType, nChannels, nResolutions};
		ObservableValue<String> out = new ReadOnlyObjectWrapper<String>(outString[index]);
		return out;
	}

	
	@SuppressWarnings("unchecked")
	public List<ImageServer<BufferedImage>> promptSeriesSelector(QuPathGUI qupath, ObservableList<ImageServer<BufferedImage>> serverList) {			
		// Get thumbnails in separate thread
		ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("thumbnail-loader", true));

		ListView<ImageServer<BufferedImage>> listSeries = new ListView<>();
		listSeries.setPrefWidth(480);
		listSeries.setMinHeight(100);
		
		// thumbnailBank is the map for storing thumbnails
		Map<String, BufferedImage> thumbnailBank = new HashMap<String, BufferedImage>();
		for (ImageServer<BufferedImage> server: serverList) {
			executor.submit(() -> {
				try {
					thumbnailBank.put(server.getMetadata().getName(), ProjectImportImagesCommand.getThumbnailRGB(server, null));
					Platform.runLater( () -> listSeries.refresh());
				} catch (IOException e) {
					logger.warn("Error loading thumbnail: " + e.getLocalizedMessage(), e);
				}
			});
		};
		
		double thumbnailSize = 80;
		listSeries.setCellFactory(v -> new ImageAndNameListCell(thumbnailBank, thumbnailSize, thumbnailSize));
		listSeries.getItems().setAll(serverList);

		
		// Info table - Changes according to selected series
		String[] attributes = new String[] {"Full Path", "Server Type", "Width", "Height", "Pixel Width", "Pixel Height", "Pixel Type", "Number of Channels", "Number of Resolutions"};
		Integer[] indices = new Integer[9];
		for (int index = 0; index < 9; index++) indices[index] = index;
		ObservableList<Integer> indexList = FXCollections.observableArrayList(indices);
		
		TableView<Integer> tableInfo = new TableView<>();
		tableInfo.setMinHeight(200);
		tableInfo.setMinWidth(500);
		
		// First column (attribute names)
		TableColumn<Integer, String> attributeCol = new TableColumn<Integer, String>("Attribute");
		attributeCol.setMinWidth(242);
		attributeCol.setResizable(false);
		attributeCol.setCellValueFactory(cellData -> {
			return new ReadOnlyObjectWrapper<String>(attributes[cellData.getValue()]);
		});
		
		// Second column (attribute values)
		TableColumn<Integer, String> valueCol = new TableColumn<Integer, String>("Value");
		valueCol.setMinWidth(242);
		valueCol.setResizable(false);
		valueCol.setCellValueFactory(cellData -> {
			if (selectedSeries != null) return getSeriesQuickInfo(selectedSeries, cellData.getValue());
			else return null;
		});
		
		
		// Adding the values on hover over the info table
		tableInfo.setRowFactory(tableView -> {
            final TableRow<Integer> row = new TableRow<>();
            row.hoverProperty().addListener((observable) -> {
                final var element = row.getItem();
                if (row.isHover() && selectedSeries != null) {
                	ObservableValue<String> value = getSeriesQuickInfo(selectedSeries, element);
                	Tooltip tooltip = new Tooltip(value.getValue());
                	Tooltip.install(row, tooltip);
                }
            });
            return row;
		});
		
		// Set items to info table
		tableInfo.setItems(indexList);
		tableInfo.getColumns().addAll(attributeCol, valueCol);
		

		// Pane structure
		BorderPane paneSelector = new BorderPane();
		BorderPane paneSeries = new BorderPane(listSeries);
		BorderPane paneInfo = new BorderPane(tableInfo);
		paneInfo.setMaxHeight(100);
		paneSelector.setCenter(paneSeries);
		paneSelector.setBottom(paneInfo);

		BorderPane pane = new BorderPane();
		pane.setCenter(paneSelector);
		
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Open image");
		ButtonType typeImport = new ButtonType("Open", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(typeImport, ButtonType.CANCEL);
		dialog.getDialogPane().setContent(pane);
		
		listSeries.getSelectionModel().selectedItemProperty().addListener((obs, previousSelectedRow, selectedRow) -> {
		    if (selectedRow != null) {
		    	selectedSeries = selectedRow;
		    	indexList.removeAll(indexList);
		    	indexList.addAll(indices);
		    }
		});
		
		listSeries.setOnMouseClicked(new EventHandler<MouseEvent>() {

		    @Override
		    public void handle(MouseEvent click) {
		    	ImageServer<BufferedImage> selectedItem = listSeries.getSelectionModel().getSelectedItem();

		        if (click.getClickCount() == 2 && selectedItem != null) {
		        	Button okButton = (Button) dialog.getDialogPane().lookupButton(typeImport);
		        	okButton.fire();
		        }
		    }
		});
		
		Optional<ButtonType> result = dialog.showAndWait();
		
		try {
			executor.shutdownNow();
		} catch (Exception e) {
			logger.warn(e.getLocalizedMessage(), e);
		} finally {
			selectedSeries = null;
			try {
				for (ImageServer<BufferedImage> server: serverList)
					server.close();
			} catch (Exception e) {
				logger.debug(e.getLocalizedMessage(), e);
			}
		}		
		
		if (!result.isPresent() || result.get() != typeImport || result.get() == ButtonType.CANCEL)
			return Collections.emptyList();
		
		return listSeries.getSelectionModel().getSelectedItems();
	}

	/**
	 * Create a new {@link ImageData} from the specified server.
	 * @param server
	 * @return
	 */
	private ImageData<BufferedImage> createNewImageData(final ImageServer<BufferedImage> server) {
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
		return new ImageData<BufferedImage>(server, estimateImageType ? GuiTools.estimateImageType(server, imageRegionStore.getThumbnail(server, 0, 0, true)) : ImageData.ImageType.UNSET);
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
	 * Check the user directory, and run a Groovy script called "startup.groovy" - if it exists.
	 * @throws ScriptException 
	 * @throws FileNotFoundException 
	 */
	private void runStartupScript() throws FileNotFoundException, ScriptException {
		String pathUsers = PathPrefs.getUserPath();
		File fileScript = pathUsers == null ? null : new File(pathUsers, "startup.groovy");
		if (fileScript != null && fileScript.exists()) {
			ScriptEngine engine = new ScriptEngineManager(getExtensionClassLoader()).getEngineByName("groovy");
			engine.getContext().setWriter(new Writer() {
				
				@Override
				public void write(char[] cbuf, int off, int len) throws IOException {
					logger.info(String.valueOf(cbuf, off, len));
				}
				
				@Override
				public void flush() throws IOException {}
				
				@Override
				public void close() throws IOException {}
			});
			engine.getContext().setErrorWriter(new Writer() {
				
				@Override
				public void write(char[] cbuf, int off, int len) throws IOException {
					logger.error(String.valueOf(cbuf, off, len));
				}
				
				@Override
				public void flush() throws IOException {}
				
				@Override
				public void close() throws IOException {}
			});
			engine.eval(new FileReader(fileScript));
		} else {
			logger.debug("No startup script found");
		}
	}
	
	
	
	/**
	 * Open a saved data file within a particular viewer, optionally keeping the same ImageServer as is currently open.
	 * The purpose of this is to make it possible for a project (for example) to open the correct server prior to
	 * opening the data file, enabling it to make use of relative path names and not have to rely on the absolute path
	 * encoded within the ImageData.
	 * 
	 * @param viewer
	 * @param file
	 * @param keepExistingServer if true and the viewer already has an ImageServer, then any ImageServer path recorded within the data file will be ignored
	 * @param promptToSaveChanges if true, the user will be prompted to ask whether to save changes or not
	 * @return
	 * @throws IOException 
	 */
	public boolean openSavedData(QuPathViewer viewer, final File file, final boolean keepExistingServer, boolean promptToSaveChanges) throws IOException {
		
		if (viewer == null) {
			if (getViewers().size() == 1)
				viewer = getViewer();
			else {
				Dialogs.showErrorMessage("Open saved data", "Please specify the viewer where the data should be opened!");
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
		try {
			serverPath = PathIO.readSerializedServerPath(file);
		} catch (Exception e) {
			logger.warn("Unable to read server path from file: {}", e.getLocalizedMessage());
		}
		boolean sameServer = serverPath == null || (imageData != null && imageData.getServerPath().equals(serverPath));			
		
		
		// If we don't have the same server, try to check the path is valid.
		// If it isn't, then prompt to enter a new path.
		// Currently, URLs are always assumed to be valid, but files may have moved.
		// TODO: Make it possible to recover data if a stored URL ceases to be valid.
		ImageServer<BufferedImage> server = null;
		if (sameServer || (imageData != null && keepExistingServer))
			server = imageData.getServer();
		else {
			try {
				server = ImageServerProvider.buildServer(serverPath, BufferedImage.class);
			} catch (IOException e) {
				logger.error("Unable to open server path " + serverPath, e);
			}
			if (server == null) {
//				boolean pathValid = new File(serverPath).isFile() || URLHelpers.checkURL(serverPath);
//				if (!pathValid) {
					serverPath = getDialogHelper().promptForFilePathOrURL("Set path to missing file", serverPath, new File(serverPath).getParentFile(), null);
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
		
		
		if (promptToSaveChanges && imageData != null && imageData.isChanged()) {
			if (!promptToSaveChangesOrCancel("Save changes", imageData))
				return false;
//			if (!DisplayHelpers.showYesNoDialog("Discard changes", "Discard changes for " + imageData.getServer().getShortServerName() + " without saving?"))
//				return false;			
//		} else if (imageData != null && imageData.isChanged()) {
//			if (!DisplayHelpers.showYesNoDialog("Replace open image", "Close " + imageData.getServer().getShortServerName() + " without saving?"))
//				return false;			
		}
		
//		ImageDataLoader task = new ImageDataLoader(file, imageData, server);
//		ProgressDialog dialog = new ProgressDialog(task);
//		dialog.initOwner(getStage());
////		dialog.show
//		createSingleThreadExecutor(this).submit(task);
//		dialog.showAndWait();
//		
//		ImageData<BufferedImage> imageData2 = task.getImageData();
		
		try {
			ImageData<BufferedImage> imageData2 = PathIO.readImageData(file, imageData, server, BufferedImage.class);
			if (imageData2 != imageData) {
				viewer.setImageData(imageData2);
//				// If we just have a single viewer, no harm in centering this
//				if (viewerManager.getViewers().size() == 1 || !viewerManager.synchronizeViewersProperty().get())
//					setInitialLocationAndMagnification(viewer);
			}
		} catch (IOException e) {
			Dialogs.showErrorMessage("Read image data", e);
		}
		
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
	 * Get a reference to the {@link PreferencePanel}.
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
	 * Create an executor using a single thread.
	 * <p>
	 * Optionally specify an owner, in which case the same Executor will be returned for the owner 
	 * for so long as the Executor has not been shut down; if it has been shut down, a new Executor will be returned.
	 * <p>
	 * Specifying an owner is a good idea if there is a chance that any submitted tasks could block,
	 * since the same Executor will be returned for all requests that give a null owner.
	 * <p>
	 * The advantage of using this over creating an ExecutorService some other way is that
	 * shutdown will be called on any pools created this way whenever QuPath is quit.
	 * 
	 * @param owner
	 * @return 
	 * 
	 */
	public ExecutorService createSingleThreadExecutor(final Object owner) {
		ExecutorService pool = mapSingleThreadPools.get(owner);
		if (pool == null || pool.isShutdown()) {
			pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory(owner.getClass().getSimpleName().toLowerCase() + "-", false));
			mapSingleThreadPools.put(owner, pool);
		}
		return pool;
	}
	
	/**
	 * Create a completion service that uses a shared threadpool for the application.
	 * @param <V> 
	 * 
	 * @param cls
	 * @return 
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
	
	
	private final static String URL_DOCS       = "https://qupath.readthedocs.io";
	private final static String URL_VIDEOS     = "https://www.youtube.com/c/QuPath";
	private final static String URL_CITATION   = "https://qupath.readthedocs.io/en/latest/docs/intro/citing.html";
//	private final static String URL_EXTENSIONS = "https://github.com/qupath/qupath/wiki/Extensions";
	private final static String URL_BUGS       = "https://github.com/qupath/qupath/issues";
	private final static String URL_FORUM      = "https://forum.image.sc/tags/qupath";
	private final static String URL_SOURCE     = "https://github.com/qupath/qupath";

	
	
	private MenuBar createMenuBar() {
		
		// Create a recent projects list
		ObservableList<URI> recentProjects = PathPrefs.getRecentProjectList();
		Menu menuRecent = MenuTools.createMenu("Recent projects...");
		
		
		// Create a File menu
		Action actionQuit = new Action("Quit", e -> tryToQuit());
//		actionQuit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCodeCombination.SHORTCUT_DOWN));
		Menu menuFile = MenuTools.createMenu(
				"File",
				MenuTools.createMenu(
						"Project...",
						ActionTools.getActionMenuItem(actionManager.PROJECT_NEW),
						ActionTools.getActionMenuItem(actionManager.PROJECT_OPEN),
						ActionTools.getActionMenuItem(actionManager.PROJECT_CLOSE),
						null,
						ActionTools.getActionMenuItem(actionManager.PROJECT_IMPORT_IMAGES),
						ActionTools.getActionMenuItem(actionManager.PROJECT_EXPORT_IMAGE_LIST),
						null,
						ActionTools.getActionMenuItem(actionManager.PROJECT_METADATA),
						createCommandAction(new ProjectCheckUrisCommand(this), "Check project URIs")
						),
				menuRecent,
				null,
				ActionTools.getActionMenuItem(actionManager.OPEN_IMAGE),
				ActionTools.getActionMenuItem(actionManager.OPEN_IMAGE_OR_URL),
				createCommandAction(new ReloadDataCommand(this), "Reload data", null, new KeyCodeCombination(KeyCode.R, KeyCodeCombination.SHORTCUT_DOWN)),
				null,
				ActionTools.getActionMenuItem(actionManager.SAVE_DATA_AS),
				ActionTools.getActionMenuItem(actionManager.SAVE_DATA),
				null,
				MenuTools.createMenu(
						"Export images...",
						createCommandAction(new ExportImageRegionCommand(this, false), "Original pixels"),
						createCommandAction(new ExportImageRegionCommand(this, true), "Rendered RGB (with overlays)")
						),
				MenuTools.createMenu(
						"Export snapshot...",
						createCommandAction(new SaveViewCommand(this, GuiTools.SnapshotType.MAIN_WINDOW_SCREENSHOT), "Main window screenshot"),
						createCommandAction(new SaveViewCommand(this, GuiTools.SnapshotType.MAIN_SCENE), "Main window content"),
						createCommandAction(new SaveViewCommand(this, GuiTools.SnapshotType.VIEWER), "Current viewer content")
						),
				null,
				MenuTools.createMenu(
						"TMA data...",
						ActionTools.getActionMenuItem(actionManager.TMA_SCORE_IMPORTER),
						ActionTools.getActionMenuItem(actionManager.TMA_EXPORT_DATA),
						createCommandAction(new TMAViewerCommand(), "Launch TMA data viewer")
						),
				null,
				actionQuit
				);
		
		
		menuFile.setOnMenuValidation(e -> {
			menuRecent.getItems().clear();
			for (URI uri : recentProjects) {
				if (uri == null)
					continue;
//				String name = fileProject.getAbsolutePath();
//				int maxLength = 40;
//				if (name.length() > maxLength)
//					name = "..." + name.substring(name.length() - maxLength);
				String name = Project.getNameFromURI(uri);
				name = ".../" + name;
				MenuItem item = new MenuItem(name);
				item.setOnAction(e2 -> {
					Project<BufferedImage> project;
					try {
						project = ProjectIO.loadProject(uri, BufferedImage.class);
						setProject(project);
					} catch (Exception e1) {
						Dialogs.showErrorMessage("Project error", "Cannot find project " + uri);
						logger.error("Error loading project", e1);
					}
				});
				menuRecent.getItems().add(item);
			}
		});
		
		
		// Create Edit menu
		Menu menuEdit = MenuTools.createMenu(
				"Edit",
				actionManager.UNDO,
				actionManager.REDO,
				null,
				MenuTools.createMenu("Copy to clipboard...",
					actionManager.COPY_VIEW,
					actionManager.COPY_WINDOW,
					actionManager.COPY_WINDOW_SCREENSHOT,
					actionManager.COPY_FULL_SCREENSHOT
					),
				null,
				ActionTools.getActionMenuItem(actionManager.PREFERENCES),
				createCommandAction(new ResetPreferencesCommand(), "Reset preferences")
				);

		// Create Tools menu
		ToggleGroup groupTools = new ToggleGroup();
		Menu menuTools = MenuTools.createMenu(
				"Tools",
				ActionTools.getActionCheckBoxMenuItem(actionManager.MOVE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.RECTANGLE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.ELLIPSE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.LINE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.POLYGON_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.POLYLINE_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.BRUSH_TOOL, groupTools),
//				ActionTools.getActionCheckBoxMenuItem(actionManager.WAND_TOOL, groupTools),
				ActionTools.getActionCheckBoxMenuItem(actionManager.POINTS_TOOL, groupTools)
				);
		
		Menu menuGestures = MenuTools.createMenu("Multi-touch gestures");
		MenuTools.addMenuItems(
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
		MenuTools.addMenuItems(
				menuTools,
				null,
				menuGestures);
		
		// Create View menu
		SlideLabelView slideLabelView = new SlideLabelView(this);
		ToggleGroup groupCellDisplay = new ToggleGroup();
		Menu menuView = MenuTools.createMenu(
				"View",
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_ANALYSIS_PANEL),
				ActionTools.getActionMenuItem(actionManager.SHOW_COMMAND_LIST),
//				createSelectableCommandAction(pinCommandList, "Pin command list", (Node)null, new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN)),
				null,
				ActionTools.getActionMenuItem(actionManager.BRIGHTNESS_CONTRAST),
				null,
				ActionTools.getActionCheckBoxMenuItem(actionManager.TOGGLE_SYNCHRONIZE_VIEWERS),
				ActionTools.getActionCheckBoxMenuItem(actionManager.MATCH_VIEWER_RESOLUTIONS),
				MenuTools.createMenu(
						"Mini viewers",
						ActionTools.getActionMenuItem(actionManager.CHANNEL_VIEWER),
						ActionTools.getActionMenuItem(actionManager.MINI_VIEWER)
						),
				null,
				MenuTools.createMenu(
						"Zoom",
						createCommandAction(new ViewerSetDownsampleCommand(this.viewerProperty(), 0.25), "400%"),
						createCommandAction(new ViewerSetDownsampleCommand(this.viewerProperty(), 1), "100%"),
						createCommandAction(new ViewerSetDownsampleCommand(this.viewerProperty(), 2), "50%"),
						createCommandAction(new ViewerSetDownsampleCommand(this.viewerProperty(), 10), "10%"),
						createCommandAction(new ViewerSetDownsampleCommand(this.viewerProperty(), 100), "1%"),
						null,
						ActionTools.getActionMenuItem(actionManager.ZOOM_IN),
						ActionTools.getActionMenuItem(actionManager.ZOOM_OUT),
						ActionTools.getActionCheckBoxMenuItem(actionManager.ZOOM_TO_FIT)
						),
				ActionTools.getActionMenuItem(actionManager.ROTATE_IMAGE),
				null,
				MenuTools.createMenu(
						"Cell display",
						ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_CELL_BOUNDARIES, groupCellDisplay),
						ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_CELL_NUCLEI, groupCellDisplay),
						ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_CELL_BOUNDARIES_AND_NUCLEI, groupCellDisplay),
						ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_CELL_CENTROIDS, groupCellDisplay)
						),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_ANNOTATIONS),
				ActionTools.getActionCheckBoxMenuItem(actionManager.FILL_ANNOTATIONS),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_NAMES),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_TMA_GRID),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_TMA_GRID_LABELS),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_DETECTIONS),
				ActionTools.getActionCheckBoxMenuItem(actionManager.FILL_DETECTIONS),
				ActionTools.getActionCheckBoxMenuItem(createSelectableCommandAction(overlayOptions.showConnectionsProperty(), "Show object connections")),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_PIXEL_CLASSIFICATION),
				null,
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_OVERVIEW),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_LOCATION),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_SCALEBAR),
				ActionTools.getActionCheckBoxMenuItem(actionManager.SHOW_GRID),
				ActionTools.getActionMenuItem(actionManager.GRID_SPACING),
				null,
				ActionTools.getActionMenuItem(actionManager.VIEW_TRACKER),
				ActionTools.getActionCheckBoxMenuItem(createSelectableCommandAction(slideLabelView.showingProperty(), "Show slide label")),				
				null,
				createCommandAction(new ShowInputDisplayCommand(this), "Show input on screen"),
				createCommandAction(new MemoryMonitorCommand(this), "Show memory monitor"),
				ActionTools.getActionMenuItem(actionManager.SHOW_LOG)
			);
		
		
		Menu menuObjects = MenuTools.createMenu(
				"Objects",
				MenuTools.createMenu(
						"Delete...",
						ActionTools.getActionMenuItem(actionManager.DELETE_SELECTED_OBJECTS),
						null,
						ActionTools.getActionMenuItem(actionManager.CLEAR_HIERARCHY),
						ActionTools.getActionMenuItem(actionManager.CLEAR_ANNOTATIONS),
						ActionTools.getActionMenuItem(actionManager.CLEAR_DETECTIONS)
						),
				MenuTools.createMenu(
						"Select...",
						createCommandAction(new ResetSelectionCommand<>(this.imageDataProperty()), "Reset selection", null, new KeyCodeCombination(KeyCode.R, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.ALT_DOWN)),
						null,
						createCommandAction(new SelectObjectsByClassCommand<>(this.imageDataProperty(), TMACoreObject.class), "Select TMA cores", null, new KeyCodeCombination(KeyCode.T, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.ALT_DOWN)),
						createCommandAction(new SelectObjectsByClassCommand<>(this.imageDataProperty(), PathAnnotationObject.class), "Select annotations", null, new KeyCodeCombination(KeyCode.A, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.ALT_DOWN)),
						MenuTools.createMenu(
								"Select detections...",
							createCommandAction(new SelectObjectsByClassCommand<>(this.imageDataProperty(), PathDetectionObject.class), "Select all detections", null, new KeyCodeCombination(KeyCode.D, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.ALT_DOWN)),
							createCommandAction(new SelectObjectsByClassCommand<>(this.imageDataProperty(), PathCellObject.class), "Select cells", null, new KeyCodeCombination(KeyCode.C, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.ALT_DOWN)),
							createCommandAction(new SelectObjectsByClassCommand<>(this.imageDataProperty(), PathTileObject.class), "Select tiles", null, null)
							),
						null,
						createCommandAction(new SelectObjectsByClassificationCommand(this), "Select objects by classification"),
						createCommandAction(new SelectObjectsByMeasurementCommand(this), "Select objects by measurements (legacy)")
						),
				null,
				MenuTools.createMenu("Annotations...",
					ActionTools.getActionMenuItem(actionManager.SPECIFY_ANNOTATION),
					ActionTools.getActionMenuItem(actionManager.SELECT_ALL_ANNOTATION),
					null,					
					createCommandAction(new HierarchyInsertCommand(this), "Insert into hierarchy", null, new KeyCodeCombination(KeyCode.I, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_DOWN)),
					createCommandAction(new ResolveHierarchyCommand(this), "Resolve hierarchy", null, new KeyCodeCombination(KeyCode.R, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_DOWN)),
					null,					
					ActionTools.getActionMenuItem(actionManager.RIGID_OBJECT_EDITOR),
					ActionTools.getActionMenuItem(actionManager.ANNOTATION_DUPLICATE),
					ActionTools.getActionMenuItem(actionManager.TRANSFER_ANNOTATION),
					null,
					createPluginAction("Expand annotations", DilateAnnotationPlugin.class, null),
					createPluginAction("Split annotations", SplitAnnotationsPlugin.class, null),
					createPluginAction("Remove fragments", RefineAnnotationsPlugin.class, null),
					createPluginAction("Fill holes", FillAnnotationHolesPlugin.class, null),
					null,
					createCommandAction(new InverseObjectCommand<>(this.imageDataProperty()), "Make inverse"),
					createCommandAction(new MergeSelectedAnnotationsCommand<>(this.imageDataProperty()), "Merge selected"),
					createCommandAction(new ShapeSimplifierCommand<>(this.imageDataProperty()), "Simplify shape")
				)
				);

		
		Menu menuTMA = MenuTools.createMenu(
				"TMA",
				MenuTools.createMenu(
					"Add...",
					createCommandAction(new TMAGridAdd(this, TMAAddType.ROW_BEFORE), "Add TMA row before"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.ROW_AFTER), "Add TMA row after"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.COLUMN_BEFORE), "Add TMA column before"),
					createCommandAction(new TMAGridAdd(this, TMAAddType.COLUMN_AFTER), "Add TMA column after")
					),
				MenuTools.createMenu(
						"Remove...",
						createCommandAction(new TMAGridRemove(this, TMARemoveType.ROW), "Remove TMA row"),
						createCommandAction(new TMAGridRemove(this, TMARemoveType.COLUMN), "Remove TMA column")
						),
				ActionTools.getActionMenuItem(actionManager.TMA_RELABEL),
				createCommandAction(new TMAGridReset(this), "Reset TMA metadata"),
				ActionTools.getActionMenuItem(actionManager.CLEAR_TMA_CORES),
				createCommandAction(new TMAGridView(this), "TMA grid summary view"),
//				createCommandAction(new TMAExplorer(this), "TMA explorer (experimental)"),
				null,
				createPluginAction("Find convex hull detections (TMA)", FindConvexHullDetectionsPlugin.class, this, null)
				);
		
		
		Menu menuMeasure = MenuTools.createMenu(
				"Measure",
				ActionTools.getActionMenuItem(actionManager.MEASUREMENT_MAP),
				createCommandAction(new MeasurementManager(this), "Show measurement manager"),
				null,
				ActionTools.getActionMenuItem(actionManager.SUMMARY_TMA),
				ActionTools.getActionMenuItem(actionManager.SUMMARY_ANNOTATIONS),
				ActionTools.getActionMenuItem(actionManager.SUMMARY_DETECTIONS),
				null,
				ActionTools.getActionMenuItem(actionManager.EXPORT_MEASUREMENTS)
//				null,
//				createCommandAction(new TMADescendantsMeasurementCommand(this), "Add core summary measurement (TMA)"),
//				null,
//				getActionMenuItem(actionManager.KAPLAN_MEIER_TMA)
				);
		
		
		// Try to load a script editor
		Action actionScriptEditor = createCommandAction(new ShowScriptEditorCommand(this, false), "Show script editor");
		actionScriptEditor.setAccelerator(new KeyCodeCombination(KeyCode.OPEN_BRACKET, KeyCodeCombination.SHORTCUT_DOWN, KeyCodeCombination.SHIFT_ANY));
		Menu menuAutomate = MenuTools.createMenu(
				"Automate",
				actionScriptEditor,
				createCommandAction(new ScriptInterpreterCommand(this), "Script interpreter"),
				null,
				ActionTools.getActionMenuItem(actionManager.WORKFLOW_DISPLAY),
				createCommandAction(new ShowScriptEditorCommand(this, true), "Create command history script")
				);
		
		// Add some plugins
		Menu menuAnalysis = MenuTools.createMenu(
				"Analyze",
				MenuTools.createMenu(
						"Preprocessing",
						ActionTools.getActionMenuItem(actionManager.COLOR_DECONVOLUTION_REFINE)
						),
				MenuTools.createMenu(
						"Region identification",
						MenuTools.createMenu(
								"Tiles & superpixels",
								createPluginAction("Create tiles", TilerPlugin.class, this, null)
								)
						),
				MenuTools.createMenu(
						"Cell detection"
						),
				MenuTools.createMenu(
						"Calculate features",
//						new PathPluginAction("Create tiles", TilerPlugin.class, this),
						createPluginAction("Add Smoothed features", SmoothFeaturesPlugin.class, this, null),
						createPluginAction("Add Shape features (experimental)", ShapeFeaturesPlugin.class, this, null),
						createPluginAction("Add Intensity features (experimental)", IntensityFeaturesPlugin.class, this, null)
//						createPluginAction("Add Haralick texture features (legacy)", HaralickFeaturesPlugin.class, this, null),
//						createPluginAction("Add Haralick texture features (feature test version)", HaralickFeaturesPluginTesting.class, this, imageRegionStore, null),
//						createPluginAction("Add Coherence texture feature (experimental)", CoherenceFeaturePlugin.class, this, null),
//						null,
//						createPluginAction("Add Local Binary Pattern features (experimental)", LocalBinaryPatternsPlugin.class, this, null)
						),
				MenuTools.createMenu(
						"Spatial analysis",
						createCommandAction(new DistanceToAnnotationsCommand(this), "Distance to annotations 2D (experimental)"),
						createCommandAction(new DetectionCentroidDistancesCommand(this), "Detection centroid distances 2D (experimental)")
						)
				);

		// Try to load classifiers
		Menu menuClassifiers = MenuTools.createMenu("Classify");

		Menu menuObjectClassifiers = MenuTools.createMenu("Object classification");
		Menu menuPixelClassifiers = MenuTools.createMenu("Pixel classification");
		MenuTools.addMenuItems(menuClassifiers, menuObjectClassifiers, menuPixelClassifiers);
		
		MenuTools.addMenuItems(
				menuObjectClassifiers,
				MenuTools.createMenu("Older classifiers", 
					createCommandAction(new LoadClassifierCommand(this), "Load detection classifier"),
					null,
					createCommandAction(new RandomTrainingRegionSelector(this, getAvailablePathClasses()), "Choose random training samples"),
					createCommandAction(new SingleFeatureClassifierCommand(this, PathDetectionObject.class), "Classify by specific feature")
					),
				null,
				createCommandAction(new ResetClassificationsCommand<>(this.imageDataProperty(), PathDetectionObject.class), "Reset detection classifications")
				);
				
		MenuTools.addMenuItems(
				menuClassifiers,
				null,
				MenuTools.createMenu("Extras",
						createCommandAction(new SparseImageServerCommand(this), "Create combined training image")
						)
			);
		
		Action actionUpdateCheck = new Action("Check for updates (web)", e -> {
			checkForUpdate(false);
		});
		
		Menu menuHelp = MenuTools.createMenu(
				"Help",
				actionManager.QUPATH_SETUP,
				null,
				createCommandAction(new OpenWebpageCommand(this, URL_DOCS), "Documentation (web)"),
				createCommandAction(new OpenWebpageCommand(this, URL_VIDEOS), "Demo videos (web)"),
				actionUpdateCheck,
				null,
				createCommandAction(new OpenWebpageCommand(this, URL_CITATION), "Cite QuPath (web)"),
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
	
	
	public Action createPluginAction(final String name, final Class<? extends PathPlugin> pluginClass, final String arg) {
		return createPluginAction(name, pluginClass, this, arg);
	}

	
	/**
	 * Refresh the project, updating the display if required.
	 * This can be called whenever the project has changed (e.g. by adding or removing items).
	 */
	public void refreshProject() {
		projectBrowser.refreshProject();
	}
	
	
	public Action createPluginAction(final String name, final PathPlugin<BufferedImage> plugin, final String arg) {
		return new Action(name, event -> {
			try {
				if (plugin instanceof PathInteractivePlugin) {
					PathInteractivePlugin<BufferedImage> pluginInteractive = (PathInteractivePlugin<BufferedImage>)plugin;
					ParameterDialogWrapper<BufferedImage> dialog = new ParameterDialogWrapper<>(pluginInteractive, pluginInteractive.getDefaultParameterList(getImageData()), new PluginRunnerFX(this));
					dialog.showDialog();
//					((PathInteractivePlugin<BufferedImage>)plugin).runInteractive(new PluginRunnerFX(this, false), arg);
				}
				else
					((PathPlugin<BufferedImage>)plugin).runPlugin(new PluginRunnerFX(this), arg);

			} catch (Exception e) {
				Dialogs.showErrorMessage("Error", "Error running " + plugin.getName());
			}
		});
	}
	
	
	/**
	 * Request to quit QuPath.
	 */
	void tryToQuit() {
		var stage = getStage();
		if (stage == null || !stage.isShowing())
			return;
		stage.fireEvent(
				new WindowEvent(
				        stage,
				        WindowEvent.WINDOW_CLOSE_REQUEST
				    )
				);
	}
	
	
	/**
	 * Create an Action to construct and run a plugin interactively.
	 * 
	 * @param name
	 * @param pluginClass
	 * @param qupath
	 * @param arg
	 * @return
	 */
	public static Action createPluginAction(final String name, final Class<? extends PathPlugin> pluginClass, final QuPathGUI qupath, final String arg) {
		try {
			return new Action(name, event -> {
				PathPlugin<BufferedImage> plugin = qupath.createPlugin(pluginClass);
				qupath.runPlugin(plugin, arg, true);
			});
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
				ParameterDialogWrapper<BufferedImage> dialog = new ParameterDialogWrapper<>(pluginInteractive, params, new PluginRunnerFX(this));
				dialog.showDialog();
			}
			else
				plugin.runPlugin(new PluginRunnerFX(this), arg);

		} catch (Exception e) {
			logger.error("Unable to run plugin " + plugin, e);
		}
	}
	
	/**
	 * Create a plugin from a specified class.
	 * 
	 * @param pluginClass
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public PathPlugin<BufferedImage> createPlugin(final Class<? extends PathPlugin> pluginClass) {
		PathPlugin<BufferedImage> plugin = null;
		try {
			plugin = pluginClass.getConstructor().newInstance();
		} catch (Exception e1) {
			logger.error("Unable to construct plugin {}", pluginClass, e1);
		}
		return plugin;
	}
	

	public Action createCommandAction(final PathCommand command, final String name, final Node icon, final KeyCombination accelerator) {
		var action = ActionTools.actionBuilder(name, e -> command.run())
				.accelerator(accelerator)
				.graphic(icon)
				.build();
		return registerAccelerator(action);
	}
	
	public Action createCommandAction(final PathCommand command, final String name) {
		return createCommandAction(command, name, (Node)null, null);
	}
	
	public Action createSelectableCommandAction(final ObservableValue<Boolean> property, final String name) {
		return createSelectableCommandAction(property, name, (Node)null, null);
	}

	public Action createSelectableCommandAction(final ObservableValue<Boolean> property, final String name, final Node icon, final KeyCombination accelerator) {
		var action = ActionTools.actionBuilder()
			.text(name)
			.selected(property)
			.selectable(true)
			.accelerator(accelerator)
			.graphic(icon)
			.build();
		return registerAccelerator(action);
	}
	
	private Action createSelectableCommandAction(final ObservableValue<Boolean> property, final String name, final PathIconFactory.PathIcons icon, final KeyCombination accelerator) {
		return createSelectableCommandAction(property, name, PathIconFactory.createNode(TOOLBAR_ICON_SIZE, TOOLBAR_ICON_SIZE, icon), accelerator);
	}
	
	private Action createToolAction(final PathTool tool, final KeyCombination accelerator) {
		  var action = createSelectableCommandAction(new SelectableItemWrapper<>(selectedToolProperty, tool), tool.getName(), tool.getIcon(), accelerator);
		  action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.contains(tool) || selectedToolLocked.get(), selectedToolLocked, tools));
		  return action;
	}

	private <T> Action createSelectableCommandAction(final SelectableItemWrapper<T> command, final String name, final Node icon, final KeyCombination accelerator) {
		var action = ActionTools.actionBuilder(e -> command.setSelected(true))
				.text(name)
				.accelerator(accelerator)
				.selectable(true)
				.selected(command.isSelected())
				.graphic(icon)
				.build();
		action.selectedProperty().addListener(e -> {
			command.setSelected(action.isSelected());
		});
//		action.setSelected(command.isSelected());
		return registerAccelerator(action);
	}

	/**
	 * Register the accelerator so we can still trigger the event if it is not otherwise called (e.g. if it doesn't require a 
	 * shortcut key, and macOS drops it).
	 * @param action the action to register; nothing will be done if it has no accelerator set
	 * @return the same action as provided as a parameter
	 */
	private Action registerAccelerator(Action action) {
		var accelerator = action.getAccelerator();
		if (accelerator == null)
			return action;
		System.err.println(action.getText());
		var previous = comboMap.put(accelerator, action);
		if (previous != null && previous != action)
			logger.warn("Multiple actions registered for {}, will keep {} and drop {}", accelerator, action.getText(), previous.getText());
		return action;
	}
	
	/**
	 * Get the main toolbar.
	 * @return
	 */
	public ToolBar getToolBar() {
		return toolbar.getToolBar();
	}
	
	
	/**
	 * Get the {@link UndoRedoManager}, which can be useful if needing to clear it in cases where available memory is low.
	 * @return
	 */
	public UndoRedoManager getUndoRedoManager() {
		return undoRedoManager;
	}
	
	public void setModeSwitchingEnabled(final boolean enabled) {
		selectedToolLocked.set(!enabled);
//		for (Action action : modeActions.values())
//			action.setDisabled(!enabled);
	}
	
	public boolean isModeSwitchingEnabled() {
		return !selectedToolLocked.get();
	}
	
	
	/**
	 * Get a read-only list of the current available tools.
	 * @return
	 */
	public List<PathTool> getAvailableTools() {
		return Collections.unmodifiableList(tools);
	}
	
	public boolean installTool(PathTool tool) {
		if (tool == null || tools.contains(tool))
			return false;
		// Keep the points tool last (since it is kind
		int ind = tools.indexOf(PathTools.POINTS);
		if (ind < 0)
			tools.add(tool);
		else
			tools.add(ind, tool);
		return true;
	}
	
	
	public void setMode(PathTool mode) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> setMode(mode));
			return;
		}
		if (!isModeSwitchingEnabled()) {
			logger.warn("Mode switching currently disabled - cannot change to {}", mode);
			return;
		}
		this.selectedToolProperty.set(mode);
	}
	
	
	/**
	 * Request that a specified Jar file be added to the extension classpath.
	 * <p>
	 * Note: This is really intended for dependencies that should remain where they are 
	 * on disk (e.g. because they are included in other applications).
	 * <p>
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
	 */
	protected void updateCursor() {
		if (stage == null || stage.getScene() == null)
			return;
		var mode = getSelectedTool();
		if (mode == PathTools.MOVE)
			updateCursor(Cursor.HAND);
		else
			updateCursor(Cursor.DEFAULT);
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
	

	public PathTool getSelectedTool() {
		return selectedToolProperty.get();
	}
	
	public ObjectProperty<PathTool> selectedToolProperty() {
		return selectedToolProperty;
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
		String text = menuItem.getText();
		if (text == null)
			return menuItem;
		text = text.toLowerCase().trim();
		if (text.equals("experimental") || text.endsWith("experimental)"))
			menuItem.visibleProperty().bind(showExperimentalOptions);
		else if (text.equals("tma") || text.endsWith("tma)"))
			menuItem.visibleProperty().bind(showTMAOptions);
		else if (text.equals("legacy") || text.endsWith("legacy)"))
			menuItem.visibleProperty().bind(showLegacyOptions);
		return menuItem;
	}
	
	/**
	 * Return the global {@link OverlayOptions} instance, used to control display within viewers by default.
	 * @return
	 */
	public OverlayOptions getOverlayOptions() {
		return overlayOptions;
	}

	
	private void createAnalysisPanel() {
		TabPane tabbedPanel = new TabPane();
		analysisPanel = tabbedPanel;
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
		
//		return tabbedPanel;
	}
	
	
	
	
	
	/**
	 * Create a default list model for storing available PathClasses.
	 * 
	 * @return
	 */
	public ObservableList<PathClass> getAvailablePathClasses() {
		return availablePathClasses;
	}
	
	
//	protected void setInitialLocationAndMagnification(final QuPathViewer viewer) {
//		if (viewer == null || viewer.getServer() == null)
//			return;
//		// Set to the highest magnification that contains the full image to start
//		int serverWidth = viewer.getServer().getWidth();
//		int serverHeight = viewer.getServer().getHeight();
//		int w = viewer.getWidth() - 20;
//		int h = viewer.getHeight() - 20;
//		double xScale = (double)serverWidth / w;
//		double yScale = (double)serverHeight / h;
//		viewer.setDownsampleFactor(Math.max(1, Math.max(xScale, yScale)));
//		viewer.centerImage();
//	}
	
	/**
	 * Get the menubar for the main QuPath application.
	 * @return
	 */
	public MenuBar getMenuBar() {
		return menuBar;
	}

	/**
	 * Get a reference to an existing menu from the main QuPath menubar, optionally creating a new menu if it is not present.
	 * 
	 * @param name
	 * @param createMenu
	 * @return
	 */
	public Menu getMenu(final String name, final boolean createMenu) {
		var menubar = getMenuBar();
		if (menubar == null)
			return null;
		return MenuTools.getMenu(menuBar.getMenus(), name, createMenu);
	}

	/**
	 * Get the main QuPath stage.
	 * @return
	 */
	public Stage getStage() {
		return stage;
	}
	
	private void updateMagnificationString() {
		if (toolbar == null)
			return;
		toolbar.updateMagnificationDisplay(getViewer());
	}
	
	
	private String getDisplayedImageName(ImageData<BufferedImage> imageData) {
		if (imageData == null)
			return null;
		var project = getProject();
		var entry = project == null ? null : project.getEntry(imageData);
		if (entry == null) {
			if (PathPrefs.getMaskImageNames())
				return "(Name masked)";
			return ServerTools.getDisplayableImageName(imageData.getServer());
		} else {
			// Make sure that the status of name masking has been set in the project (in case it hasn't been triggered yet...)
			project.setMaskImageNames(PathPrefs.getMaskImageNames());
			return entry.getImageName();
		}
	}
	
	public void updateTitle() {
		if (Platform.isFxApplicationThread())
			titleBinding.invalidate();
		else
			Platform.runLater(() -> updateTitle());
	}
	
	
	private StringBinding titleBinding = Bindings.createStringBinding(
				() -> {
					String name = "QuPath";
					if (versionString != null)
						name = name + " (" + versionString + ")";
					var imageData = imageDataProperty.get();
					if (imageData == null || !PathPrefs.showImageNameInTitle())
						return name;
					return name + " - " + getDisplayedImageName(imageData);
				},
				project, imageDataProperty, PathPrefs.showImageNameInTitleProperty(), PathPrefs.maskImageNamesProperty());
	
	
	/**
	 * Get a String representing the QuPath version &amp; build time.
	 * 
	 * @return
	 */
	public String getBuildString() {
		return buildString;
	}
	
	
	public ReadOnlyObjectProperty<ImageData<BufferedImage>> imageDataProperty() {
		return imageDataProperty;
	}
	
	public ObservableValue<PathObjectHierarchy> hierarchyProperty() {
		return Bindings.createObjectBinding(() -> {
			var imageData = imageDataProperty.getValue();
			return imageData == null ? null : imageData.getHierarchy();
		}, imageDataProperty());
	}
	
	private void fireImageDataChangedEvent(final ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {		
		
		imageDataProperty.set(imageDataNew);
		
		// A bit awkward, this... but make sure the extended scripting helper static class knows what's happened
		QPEx.setBatchImageData(imageDataNew);
		
	}
	
	
	/**
	 * Set the active project, triggering any necessary GUI updates.
	 * 
	 * @param project
	 */
	public void setProject(final Project<BufferedImage> project) {
		var currentProject = this.project.get();
		if (currentProject == project)
			return;
		
		// Ensure we save the current project
		if (currentProject != null) {
			try {
				currentProject.syncChanges();
			} catch (IOException e) {
				logger.error("Error syncing project", e);
				if (!Dialogs.showYesNoDialog("Project error", "A problem occurred while saving the last project - do you want to continue?"))
					return;
			}
		}
		
		// Check if we want to save the current image; we could still veto the project change at this point
		for (var viewer : getViewers()) {
			if (viewer == null || !viewer.hasServer())
				continue;
			var imageData = viewer.getImageData();
			if (imageData != null) {
//				ProjectImageEntry<BufferedImage> entry = getProjectImageEntry(imageData);
	//			if (entry != null) {
					if (!checkSaveChanges(imageData))
						return;
					viewer.setImageData(null);
	//			} else
	//				ProjectImportImagesCommand.addSingleImageToProject(project, imageData.getServer(), null);
			}
		}
		
		// Confirm the URIs for the new project
		if (project != null) {
			try {
				// Show URI manager dialog if we have any missing URIs
				if (!ProjectCheckUrisCommand.checkURIs(project, true))
					return;
			} catch (IOException e) {
				Dialogs.showErrorMessage("Update URIs", e);
				return;
			}
		}
		
		// Store in recent list, if needed
		URI uri = project == null ? null : project.getURI();
		if (uri != null) {
			ObservableList<URI> list = PathPrefs.getRecentProjectList();			
			if (list.contains(uri)) {
				if (!uri.equals(list.get(0))) {
					list.remove(uri);
					list.add(0, uri);
				}
			} else
				list.add(0, uri);
		}
		
		this.project.set(project);
		if (!this.projectBrowser.setProject(project)) {
			this.project.set(null);
			this.projectBrowser.setProject(null);
		}
		
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
				if (!pathClasses.contains(PathClassFactory.getPathClassUnclassified())) {
					pathClasses = new ArrayList<>(pathClasses);
					pathClasses.add(0, PathClassFactory.getPathClassUnclassified());
				}
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
		actionManager.PROJECT_CLOSE.setDisabled(project == null);
		actionManager.PROJECT_IMPORT_IMAGES.setDisabled(project == null);
		actionManager.PROJECT_EXPORT_IMAGE_LIST.setDisabled(project == null);
		actionManager.PROJECT_METADATA.setDisabled(project == null);
		
		// Ensure the URLHelpers status is appropriately set
		File dirBase = Projects.getBaseDirectory(project);
		if (dirBase != null && PathPrefs.useProjectImageCache()) {
			File cache = new File(dirBase, "cache");
			if (!cache.exists())
				cache.mkdirs();
			try {
				// Works for zip files - but these aren't flushed until closing the cache, so result in memory leak (and horribly shutdown performance)
//				cache = new File(cache, "QuPath image cache.zip");
//				URI cachePath = URI.create("jar:" + new File(cache, "/!/tiles").toURI().toString());
//				fileSystem = FileSystems.newFileSystem(cachePath, Collections.singletonMap("create", "true"));
//				fileSystemRoot = "";
				FileSystem fileSystem = FileSystems.getDefault();
				String fileSystemRoot = cache.getAbsolutePath();
				logger.debug("File system: {}", fileSystem);
				logger.debug("File system root: {}", fileSystemRoot);
			} catch (Exception e) {
				logger.error("Error creating file system", e);
			}
		}
	}
	
	
	public ReadOnlyObjectProperty<Project<BufferedImage>> projectProperty() {
		return project;
	}
	
	public Project<BufferedImage> getProject() {
		return project.get();
	}

	/**
	 * Get a shared {@link DialogHelper}.
	 * 
	 * Generally it's better to use getDialogHelper where a {@link QuPathGUI} instance is available, since it will have
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
	 * Get a DialogHelper with a specified {@link Window} as a parent.
	 * 
	 * This will return a different {@link DialogHelper} for each different Window parent,
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
	 * so there isn't a need to get a reference to its parent {@link Window} manually.
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
	
	
	
	static class ShowAnalysisPaneSelectable {
		
		private BorderPane parent;
		private SplitPane splitPane;
		private Control analysisPanel;
		private MultiviewManager manager;
		protected double lastDividerLocation;
		
		private BooleanProperty showPanel = new SimpleBooleanProperty();
		
		ShowAnalysisPaneSelectable(final BorderPane parent, final SplitPane splitPane, final Control analysisPanel, final MultiviewManager manager, final boolean defaultVisible) {
			this.parent = parent;
			this.splitPane = splitPane;
			this.analysisPanel = analysisPanel;
			this.manager = manager;
			
			showPanel.setValue(parent.getCenter() == splitPane);
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
		
		public BooleanProperty showPanelProperty() {
			return showPanel;
		}
		
		
	}



//	public ImageData<BufferedImage> getImageData() {
//		return getViewer() == null ? null : getViewer().getImageData();
//	}
	
	public ImageData<BufferedImage> getImageData() {
		return imageDataProperty.get();
	}
	
	
	/**
	 * Property representing the viewer currently active.
	 * 
	 * @return
	 */
	public ReadOnlyObjectProperty<QuPathViewerPlus> viewerProperty() {
		return viewerManager.activeViewerProperty();
	}
	
	
	
	/**
	 * Show a prompt to save changes for an ImageData. 
	 * <p>
	 * Note the return value indicates whether the user cancelled or not, rather than whether the data 
	 * was saved or not.
	 * 
	 * @param dialogTitle
	 * @param imageData
	 * @return true if the prompt 'succeeded' (i.e. user chose 'Yes' or 'No'), false if it was cancelled.
	 */
	private boolean promptToSaveChangesOrCancel(String dialogTitle, ImageData<BufferedImage> imageData) {
		var project = getProject();
		var entry = project == null ? null : project.getEntry(imageData);
		File filePrevious = null;
		if (entry == null) {
			String lastPath = imageData.getLastSavedPath();
			filePrevious = lastPath == null ? null : new File(lastPath);
		}
		DialogButton response = DialogButton.YES;
		if (imageData.isChanged()) {
			response = Dialogs.showYesNoCancelDialog(dialogTitle, "Save changes to " + ServerTools.getDisplayableImageName(imageData.getServer()) + "?");
		}
		if (response == DialogButton.CANCEL)
			return false;
		if (response == DialogButton.YES) {
			if (filePrevious == null && entry == null) {
				filePrevious = getDialogHelper().promptToSaveFile("Save image data", filePrevious, ServerTools.getDisplayableImageName(imageData.getServer()), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
				if (filePrevious == null)
					return false;
			}
			try {
				if (entry != null) {
					entry.saveImageData(imageData);
					project.syncChanges(); // Should make sure we save the project in case metadata has changed as well
				} else
					PathIO.writeImageData(filePrevious, imageData);
			} catch (IOException e) {
				Dialogs.showErrorMessage("Save ImageData", e);
			}
		}
		return true;
	}
	
	
	
	class MultiviewManager implements QuPathViewerListener {
		
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
		
		/**
		 * Match the display resolutions (downsamples) of all viewers to match the current viewer.
		 * This uses calibrated pixel size information if available.
		 */
		public void matchResolutions() {
			var viewer = getViewer();
			var activeViewers = getViewers().stream().filter(v -> v.hasServer()).collect(Collectors.toList());
			if (activeViewers.size() <= 1 || !viewer.hasServer())
				return;
			var cal = viewer.getServer().getPixelCalibration();
			double pixelSize = cal.getAveragedPixelSize().doubleValue();
			double downsample = viewer.getDownsampleFactor();
			for (var temp : activeViewers) {
				if (temp == viewer)
					continue;
				var cal2 = temp.getServer().getPixelCalibration();
				double newDownsample;
				double tempPixelSize = cal2.getAveragedPixelSize().doubleValue();
				if (Double.isFinite(tempPixelSize) && Double.isFinite(pixelSize) && cal2.getPixelWidthUnit().equals(cal.getPixelWidthUnit()) && cal2.getPixelHeightUnit().equals(cal.getPixelHeightUnit())) {
					newDownsample = (pixelSize / tempPixelSize) * downsample;
				} else {
					newDownsample = downsample;
				}
				temp.setDownsampleFactor(newDownsample);
			}
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
				Dialogs.showErrorMessage("Multiview error", "Cannot find " + viewer + " in the grid!");
				return;
			}
			int nOpen = splitPaneGrid.countOpenViewersForRow(row);
			if (nOpen > 0) {
				Dialogs.showErrorMessage("Close row error", "Please close all open viewers in selected row, then try again");
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
				if (!promptToSaveChangesOrCancel(dialogTitle, imageData))
					return false;
			}
			viewer.setImageData(null);
			return true;
		}
		
		
		
		public void removeViewerColumn(final QuPathViewerPlus viewer) {
			int col = splitPaneGrid.getColumn(viewer.getView());
			if (col < 0) {
				// Shouldn't occur...
				Dialogs.showErrorMessage("Multiview error", "Cannot find " + viewer + " in the grid!");
				return;
			}
			int nOpen = splitPaneGrid.countOpenViewersForColumn(col);
			if (nOpen > 0) {
				Dialogs.showErrorMessage("Close column error", "Please close all open viewers in selected column, then try again");
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
		 * @return 
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
			if (viewer != getActiveViewer() || viewer.isImageDataChanging() || zoomToFit.get()) {
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
			if (hierarchy.getTMAGrid() != null) {
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
							roi = roi.translate(dx, dy);
							// TODO: Deal with rotations... it's a bit tricky...
//						} else {
						// TODO: Check how best to handle transferring ROIs with rotation involved
						if (rotation != 0) {
							AffineTransform transform = new AffineTransform();
							transform.rotate(-rotation, coreNewParent.getROI().getCentroidX(), coreNewParent.getROI().getCentroidY());
							logger.info("ROTATING: " + transform);
							Area area = RoiTools.getArea(roi);
							area.transform(transform);
							roi = RoiTools.getShapeROI(area, roi.getImagePlane());
						}
					}
				}
			}
			
			
			PathObject annotation = PathObjects.createAnnotationObject(roi, lastAnnotationObject.getPathClass());
//			hierarchy.addPathObject(annotation, false);
			
//			// Make sure any core parent is set
			hierarchy.addPathObjectBelowParent(coreNewParent, annotation, true);
			
			activeViewer.setSelectedObject(annotation);
			return true;
		}

		@Override
		public void viewerClosed(QuPathViewer viewer) {
			removeViewer(viewer); // May be avoidable...?
		}

		public QuPathViewerPlus getViewer() {
			return getActiveViewer();
		}
		
		
		
		
		
		
		
		class SplitPaneGrid {
			
			private SplitPane splitPaneMain = new SplitPane();
			private List<SplitPane> splitPaneRows = new ArrayList<>();
			
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
	
}