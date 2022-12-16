/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

import java.awt.Desktop;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.script.ScriptException;
import javax.swing.SwingUtilities;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
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
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TabPane.TabDragPolicy;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.RotateEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import jfxtras.scene.menu.CirclePopupMenu;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.common.Timeit;
import qupath.lib.common.Version;
import qupath.lib.gui.ActionTools.ActionAccelerator;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionIcon;
import qupath.lib.gui.commands.BrightnessContrastCommand;
import qupath.lib.gui.commands.Commands;
import qupath.lib.gui.commands.CountingPanelCommand;
import qupath.lib.gui.commands.InputDisplayCommand;
import qupath.lib.gui.commands.LogViewerCommand;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.commands.TMACommands;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.GitHubProject.GitHubRepo;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.extensions.UpdateChecker;
import qupath.lib.gui.extensions.UpdateChecker.ReleaseVersion;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.panes.ObjectDescriptionPane;
import qupath.lib.gui.panes.AnnotationPane;
import qupath.lib.gui.panes.ImageDetailsPane;
import qupath.lib.gui.panes.PathObjectHierarchyView;
import qupath.lib.gui.panes.PreferencePane;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.panes.SelectedMeasurementTableView;
import qupath.lib.gui.panes.ServerSelector;
import qupath.lib.gui.panes.WorkflowCommandLogView;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.AutoUpdateType;
import qupath.lib.gui.prefs.PathPrefs.ImageTypeSetting;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.scripting.ScriptEditor;
import qupath.lib.gui.scripting.ScriptEditorControl;
import qupath.lib.gui.scripting.languages.GroovyLanguage;
import qupath.lib.gui.scripting.languages.ScriptLanguageProvider;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.viewer.DragDropImportListener;
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
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.UriImageSupport;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.PathInteractivePlugin;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;
import qupath.lib.scripting.ScriptParameters;
import qupath.lib.scripting.languages.ExecutableLanguage;
import qupath.lib.scripting.languages.ScriptLanguage;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.QPEx;



/**
 * Main GUI for QuPath, written using JavaFX.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPathGUI {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPathGUI.class);
	
	private static QuPathGUI instance;
	
	private ScriptEditor scriptEditor = null;
	
	// For development... don't run update check if running from a directory (rather than a Jar)
	private boolean disableAutoUpdateCheck = new File(qupath.lib.gui.QuPathGUI.class.getProtectionDomain().getCodeSource().getLocation().getFile()).isDirectory();
	
	private static ExtensionClassLoader extensionClassLoader = new ExtensionClassLoader();
	private ServiceLoader<QuPathExtension> extensionLoader = ServiceLoader.load(QuPathExtension.class, extensionClassLoader);
	
	private ObjectProperty<PathTool> selectedToolProperty = new SimpleObjectProperty<>(PathTools.MOVE);
	private ObservableList<PathTool> tools = FXCollections.observableArrayList(
			PathTools.MOVE, PathTools.RECTANGLE, PathTools.ELLIPSE, PathTools.LINE, PathTools.POLYGON, PathTools.POLYLINE, PathTools.BRUSH, PathTools.POINTS
			);
	
	private BooleanProperty selectedToolLocked = new SimpleBooleanProperty(false);

	private BooleanProperty readOnly = new SimpleBooleanProperty(false);
	
	// ExecutorServices for single & multiple threads
	private Map<Object, ExecutorService> mapSingleThreadPools = new HashMap<>();
	private ExecutorService poolMultipleThreads = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), ThreadTools.createThreadFactory("qupath-shared-", false));	
	
	private Map<PathTool, Action> toolActions = new HashMap<>();
	
	/**
	 * Preferred size for toolbar icons.
	 */
	public static final int TOOLBAR_ICON_SIZE = 16;

	MultiviewManager viewerManager;
	
	private ObjectProperty<Project<BufferedImage>> projectProperty = new SimpleObjectProperty<>();
	
	private ProjectBrowser projectBrowser;
	
	/**
	 * Preference panel, which may be used by extensions to add in their on preferences if needed
	 */
	private PreferencePane prefsPane;
	
	/**
	 * The current ImageData in the current QuPathViewer
	 */
	private ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();
	
	// Initializing the MenuBar here caused some major trouble (including segfaults) in OSX...
	private MenuBar menuBar;

	private BooleanProperty zoomToFit = new SimpleBooleanProperty(false);
	
	private BorderPane pane; // Main component, to hold toolbar & splitpane
	private TabPane analysisTabPane = new TabPane();
	private Region mainViewerPane;
	
	private ViewerPlusDisplayOptions viewerDisplayOptions = new ViewerPlusDisplayOptions();
	
	/**
	 * Default options used for viewers
	 */
	private OverlayOptions overlayOptions = new OverlayOptions();
	
	/**
	 * Default region store used by viewers for tile caching and repainting
	 */
	private DefaultImageRegionStore imageRegionStore;

	private ToolBarComponent toolbar; // Top component
	private SplitPane splitPane = new SplitPane(); // Main component

	private ObservableList<PathClass> availablePathClasses = null;
	
	private Stage stage;
	
	private boolean isStandalone = false;
	
	private DragDropImportListener dragAndDrop = new DragDropImportListener(this);
	
	private UndoRedoManager undoRedoManager;
	
	private HostServices hostServices;
	
	/**
	 * Keystrokes can be lost on macOS... so ensure these are handled
	 */
	private BiMap<KeyCombination, Action> comboMap = HashBiMap.create();
	
	private ObjectProperty<QuPathViewer> viewerProperty = new SimpleObjectProperty<>();
	
	private BooleanBinding noProject = projectProperty.isNull();
	private BooleanBinding noViewer = viewerProperty.isNull();
	private BooleanBinding noImageData = imageDataProperty.isNull();
	
	private BooleanProperty pluginRunning = new SimpleBooleanProperty(false);
	private BooleanProperty scriptRunning = new SimpleBooleanProperty(false);
	private BooleanBinding uiBlocked = pluginRunning.or(scriptRunning);
	
	private SimpleBooleanProperty showInputDisplayProperty = new SimpleBooleanProperty(false);

	private PathTool previousTool = PathTools.MOVE;
	
	private LogViewerCommand logViewerCommand = new LogViewerCommand(QuPathGUI.this);
	
	/**
	 * Create an {@link Action} that depends upon an {@link ImageData}.
	 * When the action is invoked, it will be passed the current {@link ImageData} as a parameter.
	 * The action will also be disabled if no image data is present.
	 * @param command the command to run
	 * @return an {@link Action} with appropriate properties set
	 */
	public Action createImageDataAction(Consumer<ImageData<BufferedImage>> command) {
		var action = new Action(e -> {
			var imageData = getImageData();
			if (imageData == null)
				Dialogs.showNoImageError("No image");
			else
				command.accept(imageData);
		});
		action.disabledProperty().bind(noImageData);
		return action;
	}
	
	
	/**
	 * Property to indicate whether a plugin is running or not.
	 * This is used to plugin the UI if needed.
	 * @return
	 */
	BooleanProperty pluginRunningProperty() {
		return pluginRunning;
	}
	
	/**
	 * Property to indicate whether the input display is currently showing
	 * @return input display property
	 */
	public BooleanProperty showInputDisplayProperty() {
		return showInputDisplayProperty;
	}
	
	
	/**
	 * Create an {@link Action} that depends upon an {@link QuPathViewer}.
	 * When the action is invoked, it will be passed the current {@link QuPathViewer} as a parameter.
	 * The action will also be disabled if no viewer is present.
	 * @param command the command to run
	 * @return an {@link Action} with appropriate properties set
	 */
	Action createViewerAction(Consumer<QuPathViewer> command) {
		var action = new Action(e -> {
			var viewer = getViewer();
			if (viewer == null)
				Dialogs.showErrorMessage("No viewer", "This command required an active viewer!");
			else
				command.accept(viewer);
		});
		action.disabledProperty().bind(noViewer);
		return action;
	}
	
	// TODO: Remove this command whenever annotations can be applied more easily
	private Action createImageDataAction(Consumer<ImageData<BufferedImage>> command, String name) {
		var action = createImageDataAction(command);
		action.setText(name);
		return action;
	}
	
	/**
	 * Create an {@link Action} that depends upon a {@link Project}.
	 * When the action is invoked, it will be passed the current {@link Project} as a parameter.
	 * The action will also be disabled if no image data is present.
	 * @param command the command to run
	 * @return an {@link Action} with appropriate properties set
	 */
	public Action createProjectAction(Consumer<Project<BufferedImage>> command) {
		var action = new Action(e -> {
			var project = getProject();
			if (project == null)
				Dialogs.showNoProjectError("No project");
			else
				command.accept(project);
		});
		action.disabledProperty().bind(noProject);
		return action;
	}
	
	/**
	 * Install the specified actions. It is assumed that these have been configured via {@link ActionTools}, 
	 * and therefore have sufficient information associated with them (including a menu path).
	 * @param actions
	 */
	public void installActions(Collection<? extends Action> actions) {
		this.actions.addAll(actions);
		installActionsImpl(actions);
	}
	
	private void installActionsImpl(Collection<? extends Action> actions) {
		
		var menus = getMenuBar().getMenus();
		
		var menuMap = new HashMap<String, Menu>();
		
		for (var action : actions) {
			var menuString = action.getProperties().get("MENU");
			if (menuString instanceof String) {
				var menu = menuMap.computeIfAbsent((String)menuString, s -> MenuTools.getMenu(menus, s, true));
				var items = menu.getItems();
				var name = action.getText();
				var newItem = ActionTools.createMenuItem(action);
				if (!(newItem instanceof SeparatorMenuItem)) {
					var existing = items.stream().filter(m -> m.getText() != null && m.getText().equals(name)).findFirst().orElse(null);
					if (existing != null) {
						logger.warn("Existing menu item found with name '{}' - this will be replaced", name);
						items.set(items.indexOf(existing), newItem);
						continue;
					}
				} else if (items.isEmpty()) {
					// Don't add a separator if there is nothing to separate
					continue;
				}
				items.add(newItem);
				registerAccelerator(action);
			} else {
				// Not really a problem, but ideally we'd rearrange things so it doesn't happen
				// (Currently it does a lot when adding actions to the toolbar)
				logger.trace("Found command without associated menu: {}", action.getText());
			}
		}
	}
	
	
	/**
	 * Default actions associated with a specific QuPath instance.
	 * These are useful for generating toolbars and context menus, ensuring that the same actions are used consistently.
	 */
	public class DefaultActions {
		
		// Zoom actions
		/**
		 * Apply 'zoom-to-fit' setting to all viewers
		 */
		@ActionIcon(PathIcons.ZOOM_TO_FIT)
		public final Action ZOOM_TO_FIT = ActionTools.createSelectableAction(zoomToFit, "Zoom to fit");
		
		// Tool actions
		/**
		 * Move tool action
		 */
		@ActionAccelerator("m")
		@ActionDescription("Move tool, both for moving around the viewer (panning) and moving objects (translation).")
		public final Action MOVE_TOOL = getToolAction(PathTools.MOVE);
		/**
		 * Rectangle tool action
		 */
		@ActionAccelerator("r")
		@ActionDescription("Click and drag to draw a rectangle annotation. Hold down 'Shift' to constrain shape to be a square.")
		public final Action RECTANGLE_TOOL = getToolAction(PathTools.RECTANGLE);
		/**
		 * Ellipse tool action
		 */
		@ActionAccelerator("o")
		@ActionDescription("Click and drag to draw an ellipse annotation. Hold down 'Shift' to constrain shape to be a circle.")
		public final Action ELLIPSE_TOOL = getToolAction(PathTools.ELLIPSE);
		/**
		 * Polygon tool action
		 */
		@ActionAccelerator("p")
		@ActionDescription("Create a closed polygon annotation, either by clicking individual points (with double-click to end) or clicking and dragging.")
		public final Action POLYGON_TOOL = getToolAction(PathTools.POLYGON);
		/**
		 * Polyline tool action
		 */
		@ActionAccelerator("v")
		@ActionDescription("Create a polyline annotation, either by clicking individual points (with double-click to end) or clicking and dragging.")
		public final Action POLYLINE_TOOL = getToolAction(PathTools.POLYLINE);
		/**
		 * Brush tool action
		 */
		@ActionAccelerator("b")
		@ActionDescription("Click and drag to paint with a brush. "
				+ "By default, the size of the region being drawn depends upon the zoom level in the viewer.")
		public final Action BRUSH_TOOL = getToolAction(PathTools.BRUSH);
		/**
		 * Line tool action
		 */
		@ActionAccelerator("l")
		@ActionDescription("Click and drag to draw a line annotation.")
		public final Action LINE_TOOL = getToolAction(PathTools.LINE);
		/**
		 * Points/counting tool action
		 */
		@ActionAccelerator(".")
		@ActionDescription("Click to add points to an annotation.")
		public final Action POINTS_TOOL = getToolAction(PathTools.POINTS);
		
		/**
		 * Toggle 'selection mode' on/off for all drawing tools.
		 */
		@ActionAccelerator("shift+s")
		@ActionIcon(PathIcons.SELECTION_MODE)
		@ActionDescription("Turn on/off selection mode - this converts drawing tools into selection tools")
		public final Action SELECTION_MODE = ActionTools.createSelectableAction(PathPrefs.selectionModeProperty(), "Selection mode");
		
		// Toolbar actions
		/**
		 * Show the brightness/contrast dialog.
		 */
		@ActionIcon(PathIcons.CONTRAST)
		@ActionAccelerator("shift+c")
		@ActionDescription("Open brightness & contrast dialog - also used to adjust channels and colors")
		public final Action BRIGHTNESS_CONTRAST = ActionTools.createAction(new BrightnessContrastCommand(QuPathGUI.this), "Brightness/Contrast");
		
		/**
		 * Toggle the image overview display on the viewers.
		 */
		@ActionIcon(PathIcons.OVERVIEW)
		@ActionDescription("Show/hide overview image (top right)")
		public final Action SHOW_OVERVIEW = ActionTools.createSelectableAction(viewerDisplayOptions.showOverviewProperty(), "Show slide overview");
		/**
		 * Toggle the cursor location display on the viewers.
		 */
		@ActionIcon(PathIcons.LOCATION)
		@ActionDescription("Show/hide location text (bottom right)")
		public final Action SHOW_LOCATION = ActionTools.createSelectableAction(viewerDisplayOptions.showLocationProperty(), "Show cursor location");
		/**
		 * Toggle the scalebar display on the viewers.
		 */
		@ActionIcon(PathIcons.SHOW_SCALEBAR)
		@ActionDescription("Show/hide scalebar (bottom left)")
		public final Action SHOW_SCALEBAR = ActionTools.createSelectableAction(viewerDisplayOptions.showScalebarProperty(), "Show scalebar");
		/**
		 * Toggle the counting grid display on the viewers.
		 */
		@ActionIcon(PathIcons.GRID)
		@ActionAccelerator("shift+g")
		@ActionDescription("Show/hide counting grid overlay")
		public final Action SHOW_GRID = ActionTools.createSelectableAction(overlayOptions.showGridProperty(), "Show grid");
		/**
		 * Prompt to set the spacing for the counting grid.
		 */
		public final Action GRID_SPACING = ActionTools.createAction(() -> Commands.promptToSetGridLineSpacing(overlayOptions), "Set grid spacing");
		
		/**
		 * Show the counting tool dialog. By default, this is connected to setting the points tool to active.
		 */
		public final Action COUNTING_PANEL = ActionTools.createAction(new CountingPanelCommand(QuPathGUI.this), "Counting tool", PathTools.POINTS.getIcon(), null);

		/**
		 * Toggle the pixel classification overlay visibility on the viewers.
		 */
		@ActionIcon(PathIcons.PIXEL_CLASSIFICATION)
		@ActionAccelerator("c")
		@ActionDescription("Show/hide pixel classification overlay (when available)")
		public final Action SHOW_PIXEL_CLASSIFICATION = ActionTools.createSelectableAction(overlayOptions.showPixelClassificationProperty(), "Show pixel classification");
			
		// TMA actions
		/**
		 * Add a note to any selected TMA core.
		 */
		private final Action TMA_ADD_NOTE = createImageDataAction(imageData -> TMACommands.promptToAddNoteToSelectedCores(imageData), "Add TMA note");
		
		// Overlay options actions
		/**
		 * Request that cells are displayed using their boundary ROI only.
		 */
		@ActionIcon(PathIcons.CELL_ONLY)
		public final Action SHOW_CELL_BOUNDARIES = createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.BOUNDARIES_ONLY), "Cell boundaries only");
		/**
		 * Request that cells are displayed using their boundary ROI only.
		 */
		@ActionIcon(PathIcons.NUCLEI_ONLY)
		public final Action SHOW_CELL_NUCLEI = createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.NUCLEI_ONLY), "Nuclei only");
		/**
		 * Request that cells are displayed using both cell and nucleus ROIs.
		 */
		@ActionIcon(PathIcons.CELL_NUCLEI_BOTH)
		public final Action SHOW_CELL_BOUNDARIES_AND_NUCLEI = createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.NUCLEI_AND_BOUNDARIES), "Nuclei & cell boundaries");
		/**
		 * Request that cells are displayed using their centroids only.
		 */
		@ActionIcon(PathIcons.CENTROIDS_ONLY)
		public final Action SHOW_CELL_CENTROIDS = createSelectableCommandAction(new SelectableItem<>(overlayOptions.detectionDisplayModeProperty(), DetectionDisplayMode.CENTROIDS), "Centroids only");

		/**
		 * Toggle the display of annotations.
		 */
		@ActionIcon(PathIcons.ANNOTATIONS)
		@ActionAccelerator("a")
		@ActionDescription("Show/hide annotation objects")
		public final Action SHOW_ANNOTATIONS = ActionTools.createSelectableAction(overlayOptions.showAnnotationsProperty(), "Show annotations");
		
		/**
		 * Toggle the display of annotation names.
		 */
		@ActionIcon(PathIcons.SHOW_NAMES)
		@ActionAccelerator("n")
		@ActionDescription("Show/hide annotation names (where available)")
		public final Action SHOW_NAMES = ActionTools.createSelectableAction(overlayOptions.showNamesProperty(), "Show names");
		
		/**
		 * Display annotations filled in.
		 */
		@ActionIcon(PathIcons.ANNOTATIONS_FILL)
		@ActionAccelerator("shift+f")
		@ActionDescription("Full/unfill annotation objects")
		public final Action FILL_ANNOTATIONS = ActionTools.createSelectableAction(overlayOptions.fillAnnotationsProperty(), "Fill annotations");	
		
		/**
		 * Toggle the display of TMA cores.
		 */
		@ActionIcon(PathIcons.TMA_GRID)
		@ActionAccelerator("g")
		@ActionDescription("Show/hide TMA grid")
		public final Action SHOW_TMA_GRID = ActionTools.createSelectableAction(overlayOptions.showTMAGridProperty(), "Show TMA grid");
		/**
		 * Toggle the display of TMA grid labels.
		 */
		public final Action SHOW_TMA_GRID_LABELS = ActionTools.createSelectableAction(overlayOptions.showTMACoreLabelsProperty(), "Show TMA grid labels");
		
		/**
		 * Toggle the display of detections.
		 */
		@ActionIcon(PathIcons.DETECTIONS)
		@ActionAccelerator("d")
		@ActionDescription("Show/hide detection objects")
		public final Action SHOW_DETECTIONS = ActionTools.createSelectableAction(overlayOptions.showDetectionsProperty(), "Show detections");
		
		/**
		 * Display detections filled in.
		 */
		@ActionIcon(PathIcons.DETECTIONS_FILL)
		@ActionAccelerator("f")
		@ActionDescription("Fill/unfill detection objects")
		public final Action FILL_DETECTIONS = ActionTools.createSelectableAction(overlayOptions.fillDetectionsProperty(), "Fill detections");	
		/**
		 * Display the convex hull of point ROIs.
		 */
		public final Action CONVEX_POINTS = ActionTools.createSelectableAction(PathPrefs.showPointHullsProperty(), "Show point convex hull");
		
		// Viewer actions
		/**
		 * Toggle the synchronization of multiple viewers.
		 */
		@ActionAccelerator("shortcut+alt+s")
		@ActionDescription("Synchronize viewers, so that pan, zoom and rotate in one viewer also impacts the other viewers")
		public final Action TOGGLE_SYNCHRONIZE_VIEWERS = ActionTools.createSelectableAction(viewerManager.synchronizeViewersProperty(), "Synchronize viewers");
		/**
		 * Match the resolution of all open viewers.
		 */
		public final Action MATCH_VIEWER_RESOLUTIONS = new Action("Match viewer resolutions", e -> viewerManager.matchResolutions());
		
		/**
		 * Show the main log window.
		 */
		@ActionAccelerator("shortcut+shift+l")
		public final Action SHOW_LOG = ActionTools.createAction(logViewerCommand, "Show log");

		/**
		 * Toggle the visibility of the 'Analysis pane' in the main viewer.
		 */
		@ActionIcon(PathIcons.MEASURE)
		@ActionAccelerator("shift+a")
		public final Action SHOW_ANALYSIS_PANE = createShowAnalysisPaneAction();
		
		/**
		 * Show descriptions for the selected object
		 */
		public final Action SHOW_OBJECT_DESCRIPTIONS = Commands.createSingleStageAction(() -> Commands.createObjectDescriptionsDialog(QuPathGUI.this));

		
		/**
		 * Show summary measurement table for TMA cores.
		 */
		@ActionDescription("Show summary measurements for tissue microarray (TMA) cores")
		public final Action MEASURE_TMA = createImageDataAction(imageData -> Commands.showTMAMeasurementTable(QuPathGUI.this, imageData), "Show TMA measurements");
		
		/**
		 * Show summary measurement table for annotations.
		 */
		@ActionDescription("Show summary measurements for annotation objects")
		public final Action MEASURE_ANNOTATIONS = createImageDataAction(imageData -> Commands.showAnnotationMeasurementTable(QuPathGUI.this, imageData), "Show annotation measurements");
		
		/**
		 * Show summary measurement table for detections.
		 */
		@ActionDescription("Show summary measurements for detection objects")
		public final Action MEASURE_DETECTIONS = createImageDataAction(imageData -> Commands.showDetectionMeasurementTable(QuPathGUI.this, imageData), "Show detection measurements");
		
		/**
		 * Show grid view for annotation measurements.
		 */
		@ActionDescription("Show grid view annotation objects")
		public final Action MEASURE_GRID_ANNOTATIONS = createImageDataAction(imageData -> Commands.showAnnotationGridView(QuPathGUI.this), "Show annotation grid view");

		/**
		 * Show grid view for TMA core measurements.
		 */
		@ActionDescription("Show grid view TMA cores")
		public final Action MEASURE_GRID_TMA_CORES = createImageDataAction(imageData -> Commands.showAnnotationGridView(QuPathGUI.this), "Show TMA core grid view");

		private DefaultActions() {
			// This has the effect of applying the annotations
			ActionTools.getAnnotatedActions(this);
		}
		
	}
	
	private DefaultActions defaultActions;
	
	
	/**
	 * Get the default actions associated with this QuPath instance.
	 * @return
	 */
	public synchronized DefaultActions getDefaultActions() {
		if (defaultActions == null) {
			defaultActions = new DefaultActions();
			installActions(ActionTools.getAnnotatedActions(defaultActions));
		}
		return defaultActions;
	}
	
	
	private Action createShowAnalysisPaneAction() {
		return ActionTools.createSelectableAction(showAnalysisPane, "Show analysis panel");
	}
	
	/**
	 * A list of all actions currently registered for this GUI.
	 */
	private Set<Action> actions = new LinkedHashSet<>();
	
	/**
	 * Search for an action based upon its text (name) property.
	 * @param text the text to search for
	 * @return the action, if found, or null otherwise
	 */
	public Action lookupActionByText(String text) {
		var found = actions.stream().filter(p -> text.equals(p.getText())).findFirst().orElse(null);
		if (found == null) {
			logger.warn("No action called '{}' could be found!", text);
		}
		return found;
	}
	
	/**
	 * Search for a menu item based upon its path.
	 * @param menuPath path to the menu item, in the form {@code "Main menu>Submenu>Name}
	 * @return the menu item corresponding to this path, or null if no menu item is found
	 */
	public MenuItem lookupMenuItem(String menuPath) {
		var menu = parseMenu(menuPath, "", false);
		if (menu == null)
			return null;
		var name = parseName(menuPath);
		if (name.isEmpty())
			return menu;
		return menu.getItems().stream().filter(m -> name.equals(m.getText())).findFirst().orElse(null);
	}
	
	
	private long lastMousePressedWarning = 0L;
	
	
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
	 * @param startupQuietly true if QuPath should start up without showing any messages or dialogs
	 */
	QuPathGUI(final HostServices services, final Stage stage, final String path, final boolean isStandalone, final boolean startupQuietly) {
		super();
		
		var timeit = new Timeit().start("Starting");
		
		this.hostServices = services;
		
		if (PathPrefs.doCreateLogFilesProperty().get()) {
			File fileLogging = tryToStartLogFile();
			if (fileLogging != null) {
				logger.info("Logging to file {}", fileLogging);
			} else {
				logger.warn("No directory set for log files! None will be written.");
			}
		}
		
		var buildString = BuildInfo.getInstance().getBuildString();
		var version = BuildInfo.getInstance().getVersion();
		if (buildString != null)
			logger.info("QuPath build: {}", buildString);
		else if (version != null) {
			logger.info("QuPath version: {}", version);			
		} else
			logger.warn("QuPath version unknown!");						
		
		long startTime = System.currentTimeMillis();
		
		// Set up cache
		timeit.checkpoint("Creating tile cache");
		imageRegionStore = ImageRegionStoreFactory.createImageRegionStore(QuPathGUI.getTileCacheSizeBytes());
		
		PathPrefs.tileCachePercentageProperty().addListener((v, o, n) -> {
			imageRegionStore.getCache().clear();
		});
		
		ImageServerProvider.setCache(imageRegionStore.getCache(), BufferedImage.class);
		
		this.stage = stage;
		this.isStandalone = isStandalone;
		
		timeit.checkpoint("Creating menus");

		menuBar = new MenuBar(
				Arrays.asList("File", "Edit", "Tools", "View", "Objects", "TMA", "Measure", "Automate", "Analyze", "Classify", "Extensions", "Help")
				.stream().map(Menu::new).toArray(Menu[]::new)
				);
		
		setupToolsMenu(getMenu("Tools", true));
		
		// Prepare for image name masking
		projectProperty.addListener((v, o, n) -> {
			if (n != null)
				n.setMaskImageNames(PathPrefs.maskImageNamesProperty().get());
			refreshTitle();
		});
		PathPrefs.maskImageNamesProperty().addListener(((v, o, n) -> {
			var currentProject = getProject();
			if (currentProject != null) {
				currentProject.setMaskImageNames(n);
			}
		}));
		
		// Create preferences panel
		timeit.checkpoint("Creating preferences");
		prefsPane = new PreferencePane();
		
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
		Thread.setDefaultUncaughtExceptionHandler(new QuPathUncaughtExceptionHandler());
		
		
		logger.trace("Time to main component: {} ms", (System.currentTimeMillis() - startTime));

		timeit.checkpoint("Creating main component");
		BorderPane pane = new BorderPane();
		pane.setCenter(initializeMainComponent());
		
		logger.trace("Time to menu: {} ms", (System.currentTimeMillis() - startTime));
		
		initializingMenus.set(true);
		menuBar.useSystemMenuBarProperty().bindBidirectional(PathPrefs.useSystemMenubarProperty());
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
		TMACommands.installDragAndDropHandler(this);
		
		
		// Add listener to the inputDisplayDialogProperty to show/hide dialog
		showInputDisplayProperty.addListener((v, o, n) -> {
			var dialogInstance = InputDisplayCommand.getInstance(getStage(), showInputDisplayProperty);
			if (n)
				dialogInstance.show();
			else
				dialogInstance.requestClose();
		});
		
		stage.setOnCloseRequest(this::handleCloseRequest);
		
		timeit.checkpoint("Showing");
		logger.debug("Time to display: {} ms", (System.currentTimeMillis() - startTime));
		stage.show();
		logger.trace("Time to finish display: {} ms", (System.currentTimeMillis() - startTime));

		timeit.checkpoint("Adding event filters");

		stage.getScene().addEventFilter(MouseEvent.ANY, this::sceneMouseEventFilter);
		
		// Ensure spacebar presses are noted, irrespective of which component has the focus
		stage.getScene().addEventFilter(KeyEvent.ANY, this::sceneKeyEventFilter);
		
		stage.getScene().setOnKeyReleased(this::sceneKeyReleasedHandler);
		
		// Install extensions
		timeit.checkpoint("Refreshing extensions");
		refreshExtensions(false);
		
		// Open an image, if required
		if (path != null) {
			timeit.checkpoint("Opening image");
			openImage(path, false, false);
		}
		
		// Set the icons
		timeit.checkpoint("Setting icons");
		stage.getIcons().addAll(loadIconList());
		
		// TODO: Make the script menus also available within the script editor itself
		
		// Add scripts menu (delayed to here, since it takes a bit longer)
		timeit.checkpoint("Adding script menus");
		Menu menuAutomate = getMenu("Automate", false);
		ScriptEditor editor = getScriptEditor();
		var sharedScriptMenuLoader = new ScriptMenuLoader("Shared scripts...", PathPrefs.scriptsPathProperty(), editor);
		
		StringBinding projectScriptsPath = Bindings.createStringBinding(() -> {
			var project = getProject();
			File dir = project == null ? null : Projects.getBaseDirectory(project);
			return dir == null ? null : new File(dir, "scripts").getAbsolutePath();
		}, projectProperty);
		var projectScriptMenuLoader = new ScriptMenuLoader("Project scripts...", projectScriptsPath, editor);
		projectScriptMenuLoader.getMenu().visibleProperty().bind(
				projectProperty.isNotNull().and(initializingMenus.not())
				);
		
		StringBinding userScriptsPath = Bindings.createStringBinding(() -> {
			String userPath = PathPrefs.getUserPath();
			File dirScripts = userPath == null ? null : new File(userPath, "scripts");
			if (dirScripts == null || !dirScripts.isDirectory())
				return null;
			return dirScripts.getAbsolutePath();
		}, PathPrefs.userPathProperty());
		ScriptMenuLoader userScriptMenuLoader = new ScriptMenuLoader("User scripts...", userScriptsPath, editor);


		MenuTools.addMenuItems(
				menuAutomate,
				null,
				sharedScriptMenuLoader.getMenu(),
				userScriptMenuLoader.getMenu(),
				projectScriptMenuLoader.getMenu()
				);
		
		// Menus should now be complete - try binding visibility
		timeit.checkpoint("Updating menu item visibility");
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
		
		// Register all the accelerators
		
		// Update display
		// Requesting the style should be enough to make sure it is called...
		logger.debug("Selected style: {}", QuPathStyleManager.selectedStyleProperty().get());
				
		long endTime = System.currentTimeMillis();
		logger.debug("Startup time: {} ms", (endTime - startTime));
				
		// Show a startup message, if we have one
		if (!startupQuietly)
			showStartupMessage();
		
		// Add listeners to set default project and image data
		imageDataProperty.addListener((v, o, n) -> QP.setDefaultImageData(n));
		projectProperty.addListener((v, o, n) -> QP.setDefaultProject(n));
		
		// Run startup script, if we can
		try {
			timeit.checkpoint("Running startup script");
			runStartupScript();			
		} catch (Exception e) {
			logger.error("Error running startup script", e);
		}
		
		
		if (Desktop.isDesktopSupported()) {
			var desktop = Desktop.getDesktop();
			if (desktop.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) {
				desktop.setQuitHandler((e, r) -> {
					Platform.runLater(() -> {
						tryToQuit();
						// Report that we have cancelled - we'll quit anyway if the user confirms it,
						// but we need to handle this on the Application thread
						r.cancelQuit();
					});
				});
			}
		}
		
		// Refresh what we can if the locale changes
		ChangeListener<Locale> localeListener = (v, o, n) -> updateListsAndTables();
//		PathPrefs.defaultLocaleProperty() // Handled by update to other two
		PathPrefs.defaultLocaleDisplayProperty().addListener(localeListener);
		PathPrefs.defaultLocaleFormatProperty().addListener(localeListener);
		
		timeit.checkpoint("Refreshing style");
		QuPathStyleManager.refresh();
		
		logger.debug("{}", timeit.stop());
		
		
		// Show setup if required
		if (startupQuietly)
			return;
		
		// If showing the welcome stage, don't check for updates until it is closed 
		// since the user may change the preference at this point
		if (PathPrefs.showStartupMessageProperty().get()) {
			Platform.runLater(() -> {
				var welcomeStage = WelcomeStage.getInstance(this);
				if (!disableAutoUpdateCheck)
					welcomeStage.setOnHidden(e -> {
						checkForUpdate(true);
						welcomeStage.setOnHidden(null);
					});
				welcomeStage.show();
			});
		} else {
			// Do auto-update check
			if (!disableAutoUpdateCheck)
				checkForUpdate(true);
		}
	}
	
	/**
	 * Filter key events on the scene
	 * @param e
	 */
	private void sceneKeyEventFilter(KeyEvent e) {
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
//				for (QuPathViewer viewer : viewerManager.getOpenViewers()) {
//					viewer.setSpaceDown(pressed.booleanValue());
//				}
			}
		}
	}
	
	/**
	 * Handle key released events on the scene
	 * @param e
	 */
	private void sceneKeyReleasedHandler(KeyEvent e) {
		if (e.getEventType() != KeyEvent.KEY_RELEASED)
			return;
		
		// It seems if using the system menubar on Mac, we can sometimes need to mop up missed keypresses
		if (e.isConsumed() || e.isShortcutDown() || !(GeneralTools.isMac() && getMenuBar().isUseSystemMenuBar()) || e.getTarget() instanceof TextInputControl) {
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
			var action = defaultActions.SHOW_DETECTIONS;
			action.setSelected(!action.isSelected());
			action = defaultActions.SHOW_PIXEL_CLASSIFICATION;
			action.setSelected(!action.isSelected());
			e.consume();
		}
	}
	
	/**
	 * Filter mouse events on the scene
	 * @param e
	 */
	private void sceneMouseEventFilter(MouseEvent e) {
		
		// Don't bother with move/enter/exit events
		if (e.getEventType() == MouseEvent.MOUSE_MOVED ||
				e.getEventType() == MouseEvent.MOUSE_ENTERED ||
				e.getEventType() == MouseEvent.MOUSE_EXITED ||
				e.getEventType() == MouseEvent.MOUSE_ENTERED_TARGET ||
				e.getEventType() == MouseEvent.MOUSE_EXITED_TARGET
				)
			return;
			
		
		if (uiBlocked.get()) {
			e.consume();
			// Show a warning if clicking (but not *too* often)
			if (e.getEventType() == MouseEvent.MOUSE_PRESSED) {
				long time = System.currentTimeMillis();
				if (time - lastMousePressedWarning > 5000L) {
					if (scriptRunning.get()) {
						Dialogs.showWarningNotification("Script running", "Please wait until the current script has finished!");
						lastMousePressedWarning = time;
					} else if (pluginRunning.get()) {
						logger.warn("Please wait until the current command is finished!");
//							Dialogs.showWarningNotification("Command running", "Please wait until the current command has finished!");
						lastMousePressedWarning = time;
					}
				}
			}
		} else if (e.getButton() == MouseButton.MIDDLE && e.getEventType() == MouseEvent.MOUSE_CLICKED) {
			logger.debug("Middle button pressed {}x {}", e.getClickCount(), System.currentTimeMillis());

			// Here we toggle between the MOVE tool and any previously selected tool
			if (getSelectedTool() == PathTools.MOVE)
				setSelectedTool(previousTool);
			else
				setSelectedTool(PathTools.MOVE);
		}
	}
	
	
	/**
	 * Called when there is a close request for the entire QuPath stage
	 * @param e
	 */
	private void handleCloseRequest(WindowEvent e) {
		// Added to try to resolve macOS issue in which pressing Cmd+Q multiple times 
		// resulted in multiple save prompts appearing - https://github.com/qupath/qupath/issues/941
		// Must be checked on other platforms
		if (Platform.isNestedLoopRunning()) {
			logger.debug("Close request from nested loop - will be discarded");
			e.consume();
			return;
		}

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
		// Could uncomment this to sync the project - but we should be careful to avoid excessive synchronization
		// (and it may already be synchronied when saving the image data)
		var project = getProject();
		if (project != null) {
			try {
				project.syncChanges();
			} catch (IOException ex) {
				logger.error("Error syncing project: " + ex.getLocalizedMessage(), e);
			}
		}

		// Check if there is a script running
		if (scriptRunning.get()) {
			if (!Dialogs.showYesNoDialog("Quit QuPath", "A script is currently running! Quit anyway?")) {
				logger.trace("Pressed no to quit window with script running!");
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
			//						logger.info("Calling System.exit(0);");
			System.exit(0);
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
			LogManager.logToFile(fileLog);
			return fileLog;
		}
		return null;
	}
	
	
	/**
	 * Set up the tools menu to listen to the available tools.
	 * @param menu
	 */
	private void setupToolsMenu(Menu menu) {
		tools.addListener((Change<? extends PathTool> c) -> refreshToolsMenu(tools, menu));
	}
	
	private void refreshToolsMenu(List<PathTool> tools, Menu menu) {
		menu.getItems().setAll(tools.stream().map(t -> ActionTools.createCheckMenuItem(getToolAction(t))).collect(Collectors.toList()));
		MenuTools.addMenuItems(menu, null, ActionTools.createCheckMenuItem(defaultActions.SELECTION_MODE));
	}
	
	
	private void showStartupMessage() {
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
	 * This can potentially be used from other environments (e.g. MATLAB, Fiji, Python).
	 * It is assumed that it is being launched from a JavaFX (without {@link HostServices} available) or AWT/Swing application; 
	 * if this is not the case, use {@link #launchQuPath(HostServices, boolean)} instead.
	 * <p>
	 * Afterwards, calls to {@link #getInstance()} will return the QuPath instance as soon as it is available.
	 * However, note that depending upon the thread from which this method is called, the QuPath instance may <i>not</i> 
	 * be available until some time after the method returns.
	 * <p>
	 * If there is already an instance of QuPath running, this ensures that it is visible - but otherwise does nothing.
	 */
	public static void launchQuPath() {
		launchQuPath(null, true);
	}
	
	/**
	 * Launch QuPath from an AWT/Swing application.
	 * Equivalent to calling {@code #launchQuPath(hostServices, true)}
	 * @param hostServices
	 * @deprecated as of v0.2.3 in favor of {@link #launchQuPath(HostServices, boolean)}
	 */
	@Deprecated
	public static void launchQuPath(HostServices hostServices) {
		launchQuPath(hostServices, true);
	}
	
	/**
	 * Static method to launch QuPath on the JavaFX Application thread.
	 * <p>
	 * This can potentially be used from other environments (e.g. MATLAB, Fiji, Python).
	 * Afterwards, calls to {@link #getInstance()} will return the QuPath instance as soon as it is available.
	 * However, note that depending upon the thread from which this method is called, the QuPath instance may <i>not</i> 
	 * be available until some time after the method returns.
	 * <p>
	 * If there is already an instance of QuPath running, this requests that it is made visible - but otherwise does nothing.
	 * 
	 * @param hostServices JavaFX HostServices if available, otherwise null
	 * @param isSwing if true, it is assumed that the launch is being requested from another Swing-based Java application.
	 *                This results in an alternative method of starting the JavaFX runtime, which may be more reliable. 
	 *                However, when called from a non-swing app in some cases the thread may freeze.
	 */
	public static void launchQuPath(HostServices hostServices, boolean isSwing) {
		
		QuPathGUI instance = getInstance();
		if (instance != null) {
			logger.info("Request to launch QuPath - will try to show existing instance instead");
			if (Platform.isFxApplicationThread())
				instance.getStage().show();
			else {
				Platform.runLater(() -> instance.getStage().show());
			}
			return;
		}
		
		if (Platform.isFxApplicationThread()) {
			System.out.println("Launching new QuPath instance...");
			logger.info("Launching new QuPath instance...");
			Stage stage = new Stage();
			QuPathGUI qupath = new QuPathGUI(hostServices, stage, (String)null, false, false);
			qupath.getStage().show();
			System.out.println("Done!");
			return;
		}
		
		System.out.println("QuPath launch requested in " + Thread.currentThread());
		
		if (isSwing) {
			// If we are starting from a Swing application, try to ensure we are on the correct thread
			// (This can be particularly important on macOS)
			if (SwingUtilities.isEventDispatchThread()) {
				System.out.println("Initializing with JFXPanel...");
				new JFXPanel(); // To initialize
				Platform.runLater(() -> launchQuPath(hostServices, true));
				return;
			} else {
				SwingUtilities.invokeLater(() -> launchQuPath(hostServices, true));
				// Required to be able to restart QuPath... or probably any JavaFX application
				Platform.setImplicitExit(false);
			}
		} else {
			try {
				// This will fail if already started... but unfortunately there is no method to query if this is the case
				System.out.println("Calling Platform.startup()...");
				Platform.startup(() -> launchQuPath(hostServices, false));
			} catch (Exception e) {
				System.err.println("If JavaFX is initialized, be sure to call launchQuPath() on the Application thread!");
				System.out.println("Calling Platform.runLater()...");
				Platform.runLater(() -> launchQuPath(hostServices, false));
			}
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
		Version version = getVersion();
		if (version != null)
			return Paths.get(System.getProperty("user.home"), "QuPath", String.format("v%d.%d", version.getMajor(), version.getMinor())).toFile();
		else
			return Paths.get(System.getProperty("user.home"), "QuPath").toFile();
	}
	
	/**
	 * Get the directory containing the QuPath code
	 * @return {@link File} object representing the code directory, or null if this cannot be determined
	 */
	File getCodeDirectory() {
		URI uri = null;
		try {
			if (hostServices != null) {
				String code = hostServices.getCodeBase();
				if (code == null || code.isBlank())
					code = hostServices.getDocumentBase();
				if (code != null && code.isBlank()) {
					uri = GeneralTools.toURI(code);
					return Paths.get(uri).toFile();
				}
			}
		} catch (URISyntaxException e) {
			logger.debug("Exception converting to URI: " + e.getLocalizedMessage(), e);
		}
		try {
			return Paths.get(
					QuPathGUI.class
					.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI()).getParent().toFile();
		} catch (Exception e) {
			logger.error("Error identifying code directory: " + e.getLocalizedMessage(), e);
			return null;
		}
	}
	
	/**
	 * Do an update check.
	 * @param isAutoCheck if true, avoid prompting the user unless an update is available. If false, the update has been explicitly 
	 *                    requested and so the user should be notified of the outcome, regardless of whether an update is found.
	 * @param updateCheckType
	 */
	private synchronized void doUpdateCheck(AutoUpdateType updateCheckType, boolean isAutoCheck) {

		String title = "Update check";

		// Get a map of all the projects we can potentially check
		Map<GitHubRepo, Version> projects = new LinkedHashMap<>();
		Map<GitHubRepo, ReleaseVersion> projectUpdates = new LinkedHashMap<>();
		
		// Start with the main app
		var qupathVersion = getVersion();
		if (qupathVersion != null && qupathVersion != Version.UNKNOWN) {
			if (updateCheckType == AutoUpdateType.QUPATH_ONLY || updateCheckType == AutoUpdateType.QUPATH_AND_EXTENSIONS)
				projects.put(GitHubRepo.create("QuPath", "qupath", "qupath"), qupathVersion);
		}
		
		// Work through extensions
		if (updateCheckType == AutoUpdateType.QUPATH_AND_EXTENSIONS || updateCheckType == AutoUpdateType.EXTENSIONS_ONLY) {
			for (var ext : getLoadedExtensions()) {
				var v = ext.getVersion();
				if (!(ext instanceof GitHubProject)) {
					// This also applies to built-in QuPath extensions
					logger.debug("Can't check for updates for {} (not a project with its own GitHub repo)", ext.getName());
				} else if (v != null && v != Version.UNKNOWN) {
					var project = (GitHubProject)ext;
					projects.put(project.getRepository(), v);
				} else {
					logger.warn("Can't check for updates for {} - unknown version", ext.getName());
				}
			}
		}

		// Report if there is nothing to update
		if (projects.isEmpty()) {
			if (isAutoCheck) {
				logger.warn("Cannot check for updates for this installation");
			} else {
				Dialogs.showMessageDialog(title, "Sorry, no update check is available for this installation");
			}
			return;
		}
		
		// Check for any updates
		for (var entry : projects.entrySet()) {
			try {
				var project = entry.getKey();
				logger.info("Update check for {}", project.getUrlString());
				var release = UpdateChecker.checkForUpdate(entry.getKey());
				if (release != null && release.getVersion() != Version.UNKNOWN && entry.getValue().compareTo(release.getVersion()) < 0) {
					logger.info("Found newer release for {} ({} -> {})", project.getName(), entry.getValue(), release.getVersion());
					projectUpdates.put(project, release);
				} else if (release != null) {
					logger.info("No newer release for {} ({} is newer than {})", project.getName(), entry.getValue(), release.getVersion());
				}
			} catch (Exception e) {
				logger.error("Update check failed for {}", entry.getKey());
				logger.debug(e.getLocalizedMessage(), e);
			}
		}
		PathPrefs.getUserPreferences().putLong("lastUpdateCheck", System.currentTimeMillis());
		
		// If we couldn't determine the version, tell the user only if this isn't the automatic check
		if (projectUpdates.isEmpty()) {
			if (!isAutoCheck)
				Dialogs.showMessageDialog(title, "No updates found!");
			return;
		}
		
		// Create a table showing the updates available
		var table = new TableView<GitHubRepo>();
		table.getItems().setAll(projectUpdates.keySet());
		
		var colRepo = new TableColumn<GitHubRepo, String>("Name");
		colRepo.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().getName()));
		
		var colCurrent = new TableColumn<GitHubRepo, String>("Current version");
		colCurrent.setCellValueFactory(r -> new SimpleStringProperty(projects.get(r.getValue()).toString()));

		var colNew = new TableColumn<GitHubRepo, String>("New version");
		colNew.setCellValueFactory(r -> new SimpleStringProperty(projectUpdates.get(r.getValue()).getVersion().toString()));
		
		table.setRowFactory(r -> {
			var row = new TableRow<GitHubRepo>();
			row.itemProperty().addListener((v, o, n) -> {
				if (n == null) {
					row.setTooltip(null);
					row.setOnMouseClicked(null);
				} else {
					var release = projectUpdates.get(n);
					var uri = release.getUri();
					if (uri == null) {
						row.setTooltip(new Tooltip("No URL available, sorry!"));
						row.setOnMouseClicked(null);
					} else {
						row.setTooltip(new Tooltip(uri.toString()));
						row.setOnMouseClicked(e -> {
							if (e.getClickCount() > 1) {
								launchBrowserWindow(uri.toString());
							}
						});
					}
				}
			});
			return row;
		});
		
		table.getColumns().setAll(Arrays.asList(colRepo, colCurrent, colNew));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		table.setPrefHeight(200);
		table.setPrefWidth(500);
		
		
		var comboUpdates = new ComboBox<AutoUpdateType>();
		comboUpdates.getItems().setAll(AutoUpdateType.values());
		comboUpdates.getSelectionModel().select(PathPrefs.autoUpdateCheckProperty().get());
		var labelUpdates = new Label("Check for updates on startup:");
		labelUpdates.setLabelFor(comboUpdates);
		labelUpdates.setAlignment(Pos.CENTER_RIGHT);
		
		var paneUpdates = new GridPane();
		paneUpdates.add(labelUpdates, 0, 0);
		paneUpdates.add(comboUpdates, 1, 0);
		paneUpdates.setHgap(5);
		PaneTools.setToExpandGridPaneWidth(comboUpdates);
		paneUpdates.setPadding(new Insets(5, 0, 0, 0));
		
		var pane = new BorderPane(table);
		pane.setBottom(paneUpdates);
		
		var result = new Dialogs.Builder()
				.buttons(ButtonType.OK)
				.title(title)
				.headerText("Updates are available!\nDouble-click an entry to open the webpage, if available.")
				.content(pane)
				.resizable()
				.showAndWait()
				.orElse(ButtonType.CANCEL) == ButtonType.OK;
		
		if (result) {
			PathPrefs.autoUpdateCheckProperty().set(comboUpdates.getSelectionModel().getSelectedItem());
		}
	}
	
	
	
	
	
	
	
	/**
	 * Check for any updates.
	 * 
	 * @param isAutoCheck if true, the check will only be performed if the auto-update preferences allow it, 
	 * 					  and the user won't be prompted if no update is available.
	 */
	void checkForUpdate(final boolean isAutoCheck) {
		AutoUpdateType checkType;
		if (isAutoCheck) {
			// For automated checks, respect the user preferences for QuPath, extensions or neither
			checkType = PathPrefs.autoUpdateCheckProperty().get();
			boolean doAutoUpdateCheck = checkType != null && checkType != AutoUpdateType.NONE;
			if (!doAutoUpdateCheck) {
				logger.debug("No update check because of user preference ({})", checkType);
				return;
			}

			// Don't run auto-update check again if we already checked within the last hour
			long currentTime = System.currentTimeMillis();
			long lastUpdateCheck = PathPrefs.getUserPreferences().getLong("lastUpdateCheck", 0);
			double diffHours = (double)(currentTime - lastUpdateCheck) / (60L * 60L * 1000L);
			if (diffHours < 12) {
				logger.debug("Skipping update check (I already checked recently)");
				return;
			}
		} else {
			// Check everything when explicitly requested
			logger.debug("Manually requested update check - will search for QuPath and extensions");
			checkType = AutoUpdateType.QUPATH_AND_EXTENSIONS;
		}
		
		// Run the check in a background thread
		createSingleThreadExecutor(this).execute(() -> doUpdateCheck(checkType, isAutoCheck));
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
		Iterator<QuPathExtension> iterator = extensionLoader.iterator();
		while (iterator.hasNext()) {
			try {
				extensions.add(iterator.next());
			} catch (Throwable e) {
				if (getStage() != null && getStage().isShowing()) {
					Dialogs.showErrorMessage("Extension error", "Error loading extension - check 'View -> Show log' for details.");
				}
				logger.error(e.getLocalizedMessage(), e);
			}
		}
		Collections.sort(extensions, Comparator.comparing(QuPathExtension::getName));
		Version qupathVersion = getVersion();
		for (QuPathExtension extension : extensions) {
			if (!loadedExtensions.containsKey(extension.getClass())) {
				Version version = extension.getVersion();
				try {
					long startTime = System.currentTimeMillis();
					extension.installExtension(this);
					long endTime = System.currentTimeMillis();
					logger.info("Loaded extension {} ({} ms)", extension.getName(), endTime - startTime);
					if (version != null)
						logger.debug("{} was written for QuPath {}", extension.getName(), version);
					else
						logger.debug("{} does not report a compatible QuPath version", extension.getName());						
					loadedExtensions.put(extension.getClass(), extension);
					if (showNotification)
						Dialogs.showInfoNotification("Extension loaded",  extension.getName());
				} catch (Exception | LinkageError e) {
					String message = "Unable to load " + extension.getName();
					if (showNotification)
						Dialogs.showErrorNotification("Extension error", message);
					logger.error("Error loading extension " + extension + ": " + e.getLocalizedMessage(), e);
					if (!Objects.equals(qupathVersion, version)) {
						if (version == null)
							logger.warn("QuPath version for which the '{}' was written is unknown!", extension.getName());
						else if (version.equals(qupathVersion))
							logger.warn("'{}' reports that it is compatible with the current QuPath version {}", extension.getName(), qupathVersion);
						else
							logger.warn("'{}' was written for QuPath {} but current version is {}", extension.getName(), version, qupathVersion);
					}
					try {
						logger.error("It is recommended that you delete {} and restart QuPath",
								URLDecoder.decode(
										extension.getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm(),
										StandardCharsets.UTF_8));
					} catch (Exception e2) {
						logger.debug("Error finding code source " + e2.getLocalizedMessage(), e2);
					}
					defaultActions.SHOW_LOG.handle(null);
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

		File dir = getExtensionDirectory();
		if (dir == null || !dir.isDirectory()) {
			logger.info("No extension directory found!");
			var dirUser = requestUserDirectory(true);
			if (dirUser == null)
				return;
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
     * Handle installing CSS files (which can be used to style QuPath).
     * @param list list of css files
     * @return
     */
	public boolean installStyles(final Collection<File> list) {
		var dir = requestUserDirectory(true);
		if (dir == null)
			return false;
		
		var pathCssString = PathPrefs.getCssStylesPath();
		
		int nInstalled = 0;
		try {
			// If we have a user directory, add a CSS subdirectory if needed
			var pathCss = Paths.get(pathCssString);
			if (!Files.exists(pathCss)) {
				if (Files.isDirectory(pathCss.getParent()))
					Files.createDirectory(pathCss);
			}
			// If we still don't have a css directory, return
			if (!Files.isDirectory(pathCss))
				return false;
			
			// Copy over the files
			Boolean overwriteExisting = null;
			for (var file : list) {
				if (!file.getName().toLowerCase().endsWith(".css")) {
					logger.warn("Cannot install style for {} - not a .css file!", file);
					continue;
				}
				var source = file.toPath();
				var target = pathCss.resolve(file.getName());
				if (Objects.equals(source, target)) {
					logger.warn("Can't copy CSS - source and target files are the same!");
					continue;
				}
				if (Files.exists(target)) {
					// Check if we want to overwrite - if so, retain the response so we don't 
					// have to prompt multiple times if there are multiple files
					if (overwriteExisting == null) {
						var response = Dialogs.showYesNoCancelDialog("Install CSS", "Do you want to overwrite existing CSS files?");
						if (response == DialogButton.YES)
							overwriteExisting = Boolean.TRUE;
						else if (response == DialogButton.NO)
							overwriteExisting = Boolean.FALSE;
						else // cancelled
							return false;
					}
					// Skip
					if (!overwriteExisting)
						continue;
				}
				logger.info("Copying {} -> {}", source, target);
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);	
				nInstalled++;
			}
		} catch (IOException e) {
			logger.error("Exception installing CSS files: " + e.getLocalizedMessage(), e);
			return false;
		}
		if (nInstalled > 0)
			QuPathStyleManager.updateAvailableStyles();
		return true;
	}
	
	
	/**
	 * Request the current user directory, optionally prompting the user to request a director if none is available.
	 * @param promptIfMissing 
	 * @return
	 */
	public File requestUserDirectory(boolean promptIfMissing) {
		
		var pathUser = PathPrefs.getUserPath();
		var dir = pathUser == null ? null : new File(pathUser);
		if (dir != null && dir.isDirectory())
			return dir;
		
		if (!promptIfMissing)
			return null;
		
		// Prompt to create an extensions directory
		File dirDefault = getDefaultQuPathUserDirectory();
		String msg;
		if (dirDefault.exists()) {
			msg = dirDefault.getAbsolutePath() + " already exists.\n" +
					"Do you want to use this default, or specify another directory?";
		} else {
			msg = String.format("Do you want to create a new user directory at\n %s?",
					dirDefault.getAbsolutePath());
		}
		
		ButtonType btUseDefault = new ButtonType("Use default", ButtonData.YES);
		ButtonType btChooseDirectory = new ButtonType("Choose directory", ButtonData.NO);
		ButtonType btCancel = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
		
		var result = Dialogs.builder()
			.title("Choose user directory")
			.headerText("No user directory set")
			.contentText(msg)
			.buttons(btUseDefault, btChooseDirectory, btCancel)
			.showAndWait()
			.orElse(btCancel);
			
		if (result == btCancel) {
			logger.info("Dialog cancelled - no user directory set");
			return null;
		}
		if (result == btUseDefault) {
			if (!dirDefault.exists() && !dirDefault.mkdirs()) {
				Dialogs.showErrorMessage("Extension error", "Unable to create directory at \n" + dirDefault.getAbsolutePath());
				return null;
			}
			dir = dirDefault;
		} else {
			File dirUser = Dialogs.promptForDirectory("Set user directory", dirDefault);
			if (dirUser == null) {
				logger.info("No QuPath user directory set!");
				return null;
			}
			dir = dirUser;
		}
		PathPrefs.userPathProperty().set(dir.getAbsolutePath());
		return dir;
	}
	
	
	
	/**
	 * Initialize available PathClasses, either from saved list or defaults
	 */
	private void initializePathClasses() {
		availablePathClasses = FXCollections.observableArrayList();
		Set<PathClass> pathClasses = new LinkedHashSet<>();		
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
			// We need a list for UI components (e.g. ListViews), but we want it to behave like a set
			// Therefore if we find some non-unique nor null elements, correct the list as soon as possible
			var list = c.getList();
			var set = new LinkedHashSet<PathClass>();
			set.add(PathClass.NULL_CLASS);
			set.addAll(list);
			set.remove(null);
			if (!(set.size() == list.size() && set.containsAll(list))) {
				logger.info("Invalid PathClass list modification: {} will be corrected to {}", list, set);
				Platform.runLater(() -> availablePathClasses.setAll(set));
				return;
			}
			Project<?> project = getProject();
			if (project != null) {
				// Write the project, if necessary
				project.setPathClasses(set);
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
		List<PathClass> pathClasses = Arrays.asList(
				PathClass.NULL_CLASS,
				PathClass.StandardPathClasses.TUMOR,
				PathClass.StandardPathClasses.STROMA,
				PathClass.StandardPathClasses.IMMUNE_CELLS,
				PathClass.StandardPathClasses.NECROSIS,
				PathClass.StandardPathClasses.OTHER,
				PathClass.StandardPathClasses.REGION,
				PathClass.StandardPathClasses.IGNORE,
				PathClass.StandardPathClasses.POSITIVE,
				PathClass.StandardPathClasses.NEGATIVE
				);
		
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
		try (ObjectInputStream in = PathIO.createObjectInputStream(stream)) {
			List<PathClass> pathClassesOriginal = (List<PathClass>)in.readObject();
			List<PathClass> pathClasses = new ArrayList<>();
			for (PathClass pathClass : pathClassesOriginal) {
				PathClass singleton = PathClass.getSingleton(pathClass);
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
	 * Make an effort at updating all the trees, tables or lists that we can find.
	 * This is useful after a locale change.
	 */
	private static void updateListsAndTables() {
		for (var window : Window.getWindows()) {
			if (!window.isShowing())
				continue;
			var scene = window.getScene();
			if (scene != null)
				updateListsAndTables(scene.getRoot());
		}
	}
	
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
		if (bytes.length < 0.75*Preferences.MAX_VALUE_LENGTH)
			PathPrefs.getUserPreferences().putByteArray("defaultPathClasses", bytes);
		else
			logger.error("Classification list too long ({} bytes) - cannot save it to the preferences.", bytes.length);
	}
	
	
	
	
	private Menu createRecentProjectsMenu() {
		
		// Create a recent projects list in the File menu
		ObservableList<URI> recentProjects = PathPrefs.getRecentProjectList();
		Menu menuRecent = GuiTools.createRecentItemsMenu("Recent projects...", recentProjects, uri -> {
			Project<BufferedImage> project;
			try {
				project = ProjectIO.loadProject(uri, BufferedImage.class);
				setProject(project);
			} catch (Exception e1) {
				Dialogs.showErrorMessage("Project error", "Cannot find project " + uri);
				logger.error("Error loading project", e1);
			}
		}, Project::getNameFromURI);
		
		return menuRecent;
	}
	
	
	
	private BorderPane initializeMainComponent() {
		
		pane = new BorderPane();
		
		// Create a reasonably-sized viewer
		QuPathViewerPlus viewer = new QuPathViewerPlus(null, imageRegionStore, overlayOptions, viewerDisplayOptions);
		
		

		// Add analysis panel & viewer to split pane
		viewerManager = new MultiviewManager(viewer);
		
		viewerProperty.bind(viewerManager.activeViewerProperty());
		
		// Now that we have a viewer, we can create an undo/redo manager
		undoRedoManager = new UndoRedoManager(this);

		
		// TODO: MOVE INITIALIZING MANAGERS ELSEWHERE
		installActions(new Menus(this).getActions());
		
		// Add a recent projects menu
		getMenu("File", true).getItems().add(1, createRecentProjectsMenu());

//		analysisPanel = createAnalysisPanel();
		initializeAnalysisPanel();
		analysisTabPane.setMinWidth(300);
		analysisTabPane.setPrefWidth(400);
		splitPane.setMinWidth(analysisTabPane.getMinWidth() + 200);
		splitPane.setPrefWidth(analysisTabPane.getPrefWidth() + 200);
		SplitPane.setResizableWithParent(analysisTabPane, Boolean.FALSE);		

		
//		paneCommands.setRight(cbPin);
		
		mainViewerPane = CommandFinderTools.createCommandFinderPane(this, viewerManager.getNode(), CommandFinderTools.commandBarDisplayProperty());
//		paneViewer.setTop(tfCommands);
//		paneViewer.setCenter(viewerManager.getNode());
		splitPane.getItems().addAll(analysisTabPane, mainViewerPane);
//		splitPane.getItems().addAll(viewerManager.getComponent());
		SplitPane.setResizableWithParent(viewerManager.getNode(), Boolean.TRUE);
		
		pane.setCenter(splitPane);
		toolbar = new ToolBarComponent(this);
		pane.setTop(toolbar.getToolBar());
		
		setAnalysisPaneVisible(showAnalysisPane.get());
		showAnalysisPane.addListener((v, o, n) -> setAnalysisPaneVisible(n));
		
//		setInitialLocationAndMagnification(getViewer());

		// Prepare the viewer
		setupViewer(viewerManager.getActiveViewer());

		// Ensure the mode is set

		// Ensure actions are set appropriately
		selectedToolProperty.addListener((v, o, n) -> {
			Action action = toolActions.get(n);
			if (action != null)
				action.setSelected(true);
			
			activateTools(getViewer());
			
			if (n == PathTools.POINTS)
				defaultActions.COUNTING_PANEL.handle(null);
			
			updateCursor();			
		});
		setSelectedTool(getSelectedTool());
		
		return pane;
	}
	
	
	/**
	 * Access the main tab pane shown in the QuPath window.
	 * This enables extensions to add or remove tabs - but be cautious!
	 * <ul>
	 * <li>Removing tabs can impact other functionality</li>
	 * <li>If adding a tab, it is usually best to apply {@link GuiTools#makeTabUndockable(Tab)}</li>
	 * </ul>
	 * @return
	 */
	public TabPane getAnalysisTabPane() {
		return analysisTabPane;
	}

	
	/**
	 * Try to load icons, i.e. images of various sizes that could be sensible icons... here sacrificing elegance in an effort to make it work
	 * 
	 * @return
	 */
	private List<Image> loadIconList() {
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
	
	/**
	 * Get an unmodifiable list of all viewers.
	 * @return
	 */
	public List<QuPathViewerPlus> getViewers() {
		if (viewerManager == null)
			return Collections.emptyList();
		return viewerManager.getViewers();
	}
	
	/**
	 * Get the currently active viewer.
	 * @return
	 */
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
			viewer.setActiveTool(getSelectedTool());		
//		logger.debug("Tools activated for {}", viewer);
	}
	
	
	private void deactivateTools(final QuPathViewerPlus viewer) {
		viewer.setActiveTool(null);
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
	public DragDropImportListener getDefaultDragDropListener() {
		return dragAndDrop;
	}
	
	
	
	void setupViewer(final QuPathViewerPlus viewer) {
		
		viewer.getView().setFocusTraversable(true);
		
		// Update active viewer as required
		viewer.getView().focusedProperty().addListener((e, f, nowFocussed) -> {
			if (nowFocussed) {
				viewerManager.setActiveViewer(viewer);
			}
		});
		
		viewer.getView().addEventFilter(MouseEvent.MOUSE_PRESSED, e -> viewer.getView().requestFocus());

		viewer.zoomToFitProperty().bind(zoomToFit);
		
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
				
				if (PathPrefs.invertScrollingProperty().get())
					scrollUnits = -scrollUnits;
				double newDownsampleFactor = viewer.getDownsampleFactor() * Math.pow(viewer.getDefaultZoomFactor(), scrollUnits);
				newDownsampleFactor = Math.min(viewer.getMaxDownsample(), Math.max(newDownsampleFactor, viewer.getMinDownsample()));
				viewer.setDownsampleFactor(newDownsampleFactor, e.getX(), e.getY());
			}
		});
		
		
		viewer.getView().addEventFilter(RotateEvent.ANY, e -> {
			if (!PathPrefs.useRotateGesturesProperty().get())
				return;
//			logger.debug("Rotating: " + e.getAngle());
			viewer.setRotation(viewer.getRotation() + Math.toRadians(e.getAngle()));
			e.consume();
		});

		viewer.getView().addEventFilter(ZoomEvent.ANY, e -> {
			if (!PathPrefs.useZoomGesturesProperty().get())
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
						defaultActions.TMA_ADD_NOTE.handle(new ActionEvent(e.getSource(), e.getTarget()));
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
			boolean wouldRatherDoSomethingElse = e.getTouchCount() == 0 && (!PathPrefs.useScrollGesturesProperty().get() || e.isShiftDown() || e.isShortcutDown());
			if (wouldRatherDoSomethingElse) {
				return;
			}
			
			if (viewer.getZoomToFit())
				return;
			
			// Don't pan with inertia events (use the 'mover' instead)
			if (e.isInertia()) {
				e.consume();
				return;
			}
			
			// Return if we aren't using a touchscreen, and we don't want to handle scroll gestures - 
			// but don't consume the event so that it can be handled elsewhere
			lastTouchEvent = e.getTouchCount() != 0;
			if (!lastTouchEvent && !PathPrefs.useScrollGesturesProperty().get() || e.isShiftDown() || e.isShortcutDown()) {
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
			if (PathPrefs.invertScrollingProperty().get()) {
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
		miRemoveRow.setOnAction(e -> viewerManager.removeRow(viewer));
		MenuItem miRemoveColumn = new MenuItem("Remove column");
		miRemoveColumn.setOnAction(e -> viewerManager.removeColumn(viewer));

		MenuItem miCloseViewer = new MenuItem("Close viewer");
		miCloseViewer.setOnAction(e -> {
			viewerManager.closeViewer(viewer);
//				viewerManager.removeViewer(viewer);
		});
		MenuItem miResizeGrid = new MenuItem("Reset grid size");
		miResizeGrid.setOnAction(e -> {
				viewerManager.resetGridSize();
		});
		MenuItem miToggleSync = ActionTools.createCheckMenuItem(defaultActions.TOGGLE_SYNCHRONIZE_VIEWERS, null);
		MenuItem miMatchResolutions = ActionTools.createMenuItem(defaultActions.MATCH_VIEWER_RESOLUTIONS);
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
				ActionTools.createCheckMenuItem(defaultActions.SHOW_ANALYSIS_PANE, null),
				defaultActions.BRIGHTNESS_CONTRAST,
				null,
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 0.25), "400%"),
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 1), "100%"),
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 2), "50%"),
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 10), "10%"),
				ActionTools.createAction(() -> Commands.setViewerDownsample(viewer, 100), "1%")
				);
		
		ToggleGroup groupTools = new ToggleGroup();
		Menu menuTools = MenuTools.createMenu(
				"Set tool",
				ActionTools.createCheckMenuItem(defaultActions.MOVE_TOOL, groupTools),
				ActionTools.createCheckMenuItem(defaultActions.RECTANGLE_TOOL, groupTools),
				ActionTools.createCheckMenuItem(defaultActions.ELLIPSE_TOOL, groupTools),
				ActionTools.createCheckMenuItem(defaultActions.LINE_TOOL, groupTools),
				ActionTools.createCheckMenuItem(defaultActions.POLYGON_TOOL, groupTools),
				ActionTools.createCheckMenuItem(defaultActions.POLYLINE_TOOL, groupTools),
				ActionTools.createCheckMenuItem(defaultActions.BRUSH_TOOL, groupTools),
				ActionTools.createCheckMenuItem(defaultActions.POINTS_TOOL, groupTools),
				null,
				ActionTools.createCheckMenuItem(defaultActions.SELECTION_MODE)
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
				defaultActions.TMA_ADD_NOTE,
				null,
				MenuTools.createMenu(
						"Add",
					createImageDataAction(imageData -> TMACommands.promptToAddRowBeforeSelected(imageData), "Add TMA row before"),
					createImageDataAction(imageData -> TMACommands.promptToAddRowAfterSelected(imageData), "Add TMA row after"),
					createImageDataAction(imageData -> TMACommands.promptToAddColumnBeforeSelected(imageData), "Add TMA column before"),
					createImageDataAction(imageData -> TMACommands.promptToAddColumnAfterSelected(imageData), "Add TMA column after")
					),
				MenuTools.createMenu(
						"Remove",
					createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridRow(imageData), "Remove TMA row"),
					createImageDataAction(imageData -> TMACommands.promptToDeleteTMAGridColumn(imageData), "column")
					)
				);
		
		
		// Create an empty placeholder menu
		Menu menuSetClass = MenuTools.createMenu("Set class");
		
		
//		CheckMenuItem miTMAValid = new CheckMenuItem("Set core valid");
//		miTMAValid.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), false));
//		CheckMenuItem miTMAMissing = new CheckMenuItem("Set core missing");
//		miTMAMissing.setOnAction(e -> setTMACoreMissing(viewer.getHierarchy(), true));
		
		
		Menu menuCells = MenuTools.createMenu(
				"Cells",
				ActionTools.createCheckMenuItem(defaultActions.SHOW_CELL_BOUNDARIES_AND_NUCLEI, null),
				ActionTools.createCheckMenuItem(defaultActions.SHOW_CELL_NUCLEI, null),
				ActionTools.createCheckMenuItem(defaultActions.SHOW_CELL_BOUNDARIES, null),
				ActionTools.createCheckMenuItem(defaultActions.SHOW_CELL_CENTROIDS, null)
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
				var setType = PathPrefs.imageTypeSettingProperty().get();
				if (setType == ImageTypeSetting.AUTO_ESTIMATE || setType == ImageTypeSetting.PROMPT) {
					var type = GuiTools.estimateImageType(imageData.getServer(), imageRegionStore.getThumbnail(imageData.getServer(), 0, 0, true));
					logger.info("Image type estimated to be {}", type);
					if (setType == ImageTypeSetting.PROMPT) {
						ImageDetailsPane.promptToSetImageType(imageData, type);
					} else {
						imageData.setImageType(type);
						imageData.setChanged(false); // Don't want to retain this as a change resulting in a prompt to save the data
					}
				}
			}
			return true;
		} catch (Exception e) {
			Dialogs.showErrorMessage("Load ImageData", e);
			return false;
		}
	}
	
	
	ProjectImageEntry<BufferedImage> getProjectImageEntry(ImageData<BufferedImage> imageData) {
		if (imageData == null)
			return null;
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
		if (!imageData.isChanged() || isReadOnly())
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
				File file = Dialogs.promptToSaveFile("Save data", dirBase, defaultName, "QuPath data files", PathPrefs.getSerializationExtension());
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
				pathNew = Dialogs.promptForFilePathOrURL("Choose path", pathOld, fileBase, null);
				if (pathNew == null)
					return false;
				fileNew = new File(pathNew);
			} else {
				fileNew = Dialogs.promptForFile(null, fileBase, null);
				if (fileNew == null)
					return false;
				pathNew = fileNew.getAbsolutePath();
			}
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
				List<ProjectImageEntry<BufferedImage>> entries = ProjectCommands.promptToImportImages(this, pathNew);
				if (entries.isEmpty())
					return false;
				return openImageEntry(entries.get(0));
			}
			ImageServer<BufferedImage> serverNew = null;

			UriImageSupport<BufferedImage> support = ImageServerProvider.getPreferredUriImageSupport(BufferedImage.class, pathNew);
			List<ServerBuilder<BufferedImage>> builders = support == null ? Collections.emptyList() : support.getBuilders();
//			List<ImageServer<BufferedImage>> serverList = ImageServerProvider.getServerList(pathNew, BufferedImage.class);
			
			if (builders.isEmpty()) {
				String message = "Unable to build ImageServer for " + pathNew + ".\nSee View > Show log for more details";
				Dialogs.showErrorMessage("Unable to build server", message);
				return false;
			}
			else if (builders.size() == 1) {
				try {
					serverNew = builders.get(0).build();
				} catch (Exception e) {	
					logger.error("Error building server: " + e.getLocalizedMessage(), e);
				}
			} else {
				var selector = ServerSelector.createFromBuilders(builders);
				serverNew = selector.promptToSelectImage("Open", false);
				if (serverNew == null)
					return false;
			}

			if (serverNew != null) {
				if (pathOld != null && prompt && !viewer.getHierarchy().isEmpty()) {
					if (!Dialogs.showYesNoDialog("Replace open image", "Close " + ServerTools.getDisplayableImageName(server) + "?"))
						return false;
				}
				ImageData<BufferedImage> imageData = null;
				if (serverNew != null) {
					int minSize = PathPrefs.minPyramidDimensionProperty().get();
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

				if (imageData.getImageType() == ImageType.UNSET && PathPrefs.imageTypeSettingProperty().get() == ImageTypeSetting.PROMPT) {
					var type = GuiTools.estimateImageType(serverNew, imageRegionStore.getThumbnail(serverNew, 0, 0, true));
					ImageDetailsPane.promptToSetImageType(imageData, type);
				}

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
	

	
	/**
	 * Create a new {@link ImageData} from the specified server.
	 * @param server
	 * @return
	 */
	private ImageData<BufferedImage> createNewImageData(final ImageServer<BufferedImage> server) {
		return createNewImageData(server, PathPrefs.imageTypeSettingProperty().get() == ImageTypeSetting.AUTO_ESTIMATE);
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
	 * Check the user directory, and run a Groovy script called "startup.groovy" - if it exists.
	 * @throws ScriptException 
	 * @throws FileNotFoundException 
	 */
	private void runStartupScript() throws FileNotFoundException, ScriptException {
		String pathUsers = PathPrefs.getUserPath();
		File fileScript = pathUsers == null ? null : new File(pathUsers, "startup.groovy");
		if (fileScript != null && fileScript.exists()) {
			logger.info("Startup script found at {}", fileScript.getAbsolutePath());
			if (PathPrefs.runStartupScriptProperty().get()) {
				logger.info("Running startup script (you can turn this setting off in the preferences panel)");
				try {
					runScript(fileScript, null);
				} catch (Exception e) {
					logger.error("Error running startup.groovy: " + e.getLocalizedMessage(), e);
				}
			} else {
				logger.warn("You need to enable the startup script in the Preferences if you want to run it");
			}
		} else {
			logger.debug("No startup script found");
		}
	}
	
	
	/**
	 * Install a Groovy script as a new command in QuPath.
	 * @param menuPath menu where the command should be installed; see {@link #lookupMenuItem(String)} for the specification.
	 *                 If only a name is provided, the command will be added to the "Extensions" menu.
	 *                 If a menu item already exists for the given path, it will be removed.
	 * @param file the Groovy script to run; note that this will be reloaded each time it is required
	 * @return the {@link MenuItem} for the command
	 * @see #installGroovyCommand(String, String)
	 */
	public MenuItem installGroovyCommand(String menuPath, final File file) {
		return installCommand(menuPath, () -> {
			try {
				runScript(file, null);
			} catch (ScriptException e) {
				Dialogs.showErrorMessage("Script error", e);
			}
		});
	}
	
	/**
	 * Install a Groovy script as a new command in QuPath.
	 * @param menuPath menu where the command should be installed; see {@link #lookupMenuItem(String)} for the specification.
	 *                 If only a name is provided, the command will be added to the "Extensions" menu.
	 *                 If a menu item already exists for the given path, it will be removed.
	 * @param script the Groovy script to run
	 * @return the {@link MenuItem} for the command
	 * @see #installGroovyCommand(String, File)
	 */
	public MenuItem installGroovyCommand(String menuPath, final String script) {
		return installCommand(menuPath, () -> {
			try {
				runScript(null, script);
			} catch (ScriptException e) {
				Dialogs.showErrorMessage("Script error", e);
			}
		});
	}
	
	/**
	 * Install a new command in QuPath that takes the current {@link ImageData} as input.
	 * The command will only be enabled when an image is available.
	 * @param menuPath menu where the command should be installed; see {@link #lookupMenuItem(String)} for the specification.
	 *                 If only a name is provided, the command will be added to the "Extensions" menu.
	 *                 If a menu item already exists for the given path, it will be removed.
	 * @param command the command to run
	 * @return the {@link MenuItem} for the command
	 * @see #installCommand(String, Runnable)
	 */
	public MenuItem installImageDataCommand(String menuPath, final Consumer<ImageData<BufferedImage>> command) {
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> installImageDataCommand(menuPath, command));
		}
		Menu menu = parseMenu(menuPath, "Extensions", true);
		String name = parseName(menuPath);
		var action = createImageDataAction(command, name);
		var item = ActionTools.createMenuItem(action);
		addOrReplaceItem(menu.getItems(), item);
		return item;
	}
	
	/**
	 * Install a new command in QuPath.
	 * @param menuPath menu where the command should be installed; see {@link #lookupMenuItem(String)} for the specification.
	 *                 If only a name is provided, the command will be added to the "Extensions" menu.
	 *                 If a menu item already exists for the given path, it will be removed.
	 * @param runnable the command to run
	 * @return the {@link MenuItem} for the command. This can be further customized if needed.
	 */
	public MenuItem installCommand(String menuPath, Runnable runnable) {
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> installCommand(menuPath, runnable));
		}
		Menu menu = parseMenu(menuPath, "Extensions", true);
		String name = parseName(menuPath);
		var action = ActionTools.createAction(runnable, name);
		var item = ActionTools.createMenuItem(action);
		addOrReplaceItem(menu.getItems(), item);
		return item;
	}
	
	private void addOrReplaceItem(List<MenuItem> items, MenuItem item) {
		String name = item.getText();
		if (name != null) {
			for (int i = 0; i < items.size(); i++) {
				if (name.equals(items.get(i).getText())) {
					items.set(i, item);
					return;
				}
			}
		}
		items.add(item);
	}
	
	/**
	 * Identify a menu by parsing a menu path.
	 * @param menuPath the path to the menu, separated by {@code >}
	 * @param defaultMenu the default menu to use, if no other menu can be found
	 * @param create if true, create the menu if it does not already exist.
	 * @return
	 */
	private Menu parseMenu(String menuPath, String defaultMenu, boolean create) {
		int separator = menuPath.lastIndexOf(">");
		if (separator < 0) {
			return getMenu(defaultMenu, create);
		} else {
			return getMenu(menuPath.substring(0, separator), create);
		}
	}
	
	private String parseName(String menuPath) {
		int separator = menuPath.lastIndexOf(">");
		if (separator < 0) {
			return menuPath;
		} else {
			return menuPath.substring(separator+1);
		}
	}
		
	
	/**
	 * Convenience method to execute a script.
	 * Either a script file or the text of the script must be provided, or both.
	 * <p>
	 * If only the script text is given, the language is assumed to be Groovy.
	 * 
	 * @param file the file containing the script to run
	 * @param script the script to run
	 * @return result of the script execution
	 * @throws ScriptException 
	 * @throws IllegalArgumentException if both file and script are null
	 */
	public Object runScript(final File file, final String script) throws ScriptException, IllegalArgumentException {
		var params = ScriptParameters.builder()
						.setProject(getProject())
						.setImageData(getImageData())
						.setDefaultImports(QPEx.getCoreClasses())
						.setDefaultStaticImports(Collections.singletonList(QPEx.class))
						.setFile(file)
						.setScript(script)
						.setBatchSaveResult(false)
						.useLogWriters()
						.build();
		ScriptLanguage language = null;
		if (file != null) {
			language = ScriptLanguageProvider.fromString(file.getName());
		}
		if (!(language instanceof ExecutableLanguage))
			language = GroovyLanguage.getInstance();
		return ((ExecutableLanguage)language).execute(params);
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
		
		ServerBuilder<BufferedImage> serverBuilder = null;
		ImageData<BufferedImage> imageData = viewer.getImageData();
		
		// If we are loading data related to the same image server, load into that - otherwise open a new image if we can find it
		try {
			serverBuilder = PathIO.extractServerBuilder(file.toPath());
		} catch (Exception e) {
			logger.warn("Unable to read server path from file: {}", e.getLocalizedMessage());
		}
		var existingBuilder = imageData == null || imageData.getServer() == null ? null : imageData.getServer().getBuilder();
		boolean sameServer = Objects.equals(existingBuilder, serverBuilder);			
		
		
		// If we don't have the same server, try to check the path is valid.
		// If it isn't, then prompt to enter a new path.
		// Currently, URLs are always assumed to be valid, but files may have moved.
		// TODO: Make it possible to recover data if a stored URL ceases to be valid.
		ImageServer<BufferedImage> server = null;
		if (sameServer || (imageData != null && keepExistingServer))
			server = imageData.getServer();
		else {
			try {
				server = serverBuilder.build();
			} catch (Exception e) {
				logger.error("Unable to build server " + serverBuilder, e);
			}
			// TODO: Ideally we would use an interface like ProjectCheckUris instead
			if (server == null && serverBuilder != null) {
				var uris = serverBuilder.getURIs();
				var urisUpdated = new HashMap<URI, URI>();
				for (var uri : uris) {
					var pathUri = GeneralTools.toPath(uri);
					if (pathUri != null && Files.exists(pathUri)) {
						urisUpdated.put(uri, uri);
						continue;
					}
					String currentPath = pathUri == null ? uri.toString() : pathUri.toString();
					var newPath = Dialogs.promptForFilePathOrURL("Set path to missing image", currentPath, file.getParentFile(), null);
					if (newPath == null)
						return false;
					try {
						urisUpdated.put(uri, GeneralTools.toURI(newPath));
					} catch (URISyntaxException e) {
						throw new IOException(e);
					}
				}
				serverBuilder = serverBuilder.updateURIs(urisUpdated);
				try {
					server = serverBuilder.build();
				} catch (Exception e) {
					logger.error("Unable to build server " + serverBuilder, e);
				}
			}
			if (server == null)
				return false;
//			
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
			if (!isReadOnly() && !promptToSaveChangesOrCancel("Save changes", imageData))
				return false;
		}
		
		try {
			ImageData<BufferedImage> imageData2 = PathIO.readImageData(file, imageData, server, BufferedImage.class);
			if (imageData2 != imageData) {
				viewer.setImageData(imageData2);
			}
		} catch (IOException e) {
			Dialogs.showErrorMessage("Read image data", e);
		}
		
		return true;
	}
	
	
	/**
	 * Get a reference to the {@link PreferencePane}.
	 * 
	 * This can be used by extensions to add in their own preferences.
	 * 
	 * @return
	 */
	public PreferencePane getPreferencePane() {
		return prefsPane;
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
	 * Get the action or menu item associated with an accelerator.
	 * This is particularly useful to check whether a key combination is in use before using it 
	 * for a new command.
	 * 
	 * @param combo
	 * @return an {@link Action} or {@link MenuItem} associated with the accelerator, or null
	 * @since v0.4.0
	 */
	public Object lookupAccelerator(String combo) {
		return lookupAccelerator(KeyCombination.valueOf(combo));
	}
	
	/**
	 * Get the action or menu item associated with an key combination.
	 * This is particularly useful to check whether a key combination is in use before using it 
	 * for a new command.
	 * 
	 * @param combo
	 * @return an {@link Action} or {@link MenuItem} associated with the accelerator, or null
	 * @since v0.4.0
	 */
	public Object lookupAccelerator(KeyCombination combo) {
		for (var action : actions) {
			var accelerator = action.getAccelerator();
			if (accelerator != null && Objects.equals(accelerator, combo))
				return action;
		}
		
		var menuItems = MenuTools.getFlattenedMenuItems(menuBar.getMenus(), false);
		for (var mi : menuItems) {
			var accelerator = mi.getAccelerator();
			if (accelerator != null && Objects.equals(accelerator, combo)) {
				var action = ActionTools.getActionProperty(mi);
				return action == null ? mi : action;
			}
		}
		
		return null;
	}
	
	/**
	 * Set an accelerator for the specified menu command.
	 * The command is defined as described in {@link #lookupMenuItem(String)}, 
	 * and the accelerator is the the format used by {@link KeyCombination#valueOf(String)}.
	 * An example:
	 * <pre>
	 * <code> 
	 * setAccelerator("File&gt;Open...", "shift+o");
	 * </code></pre>
	 * Where possible, the accelerator for an action associated with a menu item will be changed.
	 * If the combo is null, any existing accelerator will be removed.
	 * Additionally, if the accelerator is already assigned to another item then it will be 
	 * removed from that item.
	 * 
	 * @param menuCommand
	 * @param combo
	 * @return true if a change was made, false otherwise
	 * @see #lookupMenuItem(String)
	 * @see #setAccelerator(Action, KeyCombination)
	 */
	public boolean setAccelerator(String menuCommand, String combo) {
		Objects.requireNonNull(menuCommand, "Cannot set accelerator for null menu item");
		var item = lookupMenuItem(menuCommand);
		if (item == null) {
			logger.warn("Could not find command for {}", menuCommand);
			return false;
		}
		setAccelerator(item, combo == null ? null : KeyCombination.valueOf(combo));
		return true;
	}
	
	/**
	 * Set the accelerator for the specified menu item.
	 * Where possible, the accelerator will be set via any action that controls 
	 * the menu item - so that it is applied consistently for other related buttons 
	 * or menu items.
	 * <p>
     * If the combo is null, any existing accelerator will be removed.
	 * Additionally, if the accelerator is already assigned to another item then it will be 
	 * removed from that item.
	 * 
	 * @param item
	 * @param combo
	 * @return true if changes were made, false otherwise
	 * @see #setAccelerator(Action, KeyCombination)
	 */
	public boolean setAccelerator(MenuItem item, KeyCombination combo) {
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> setAccelerator(item, combo));
		}

		Objects.requireNonNull(item, "Cannot set accelerator for null menu item");
		var action = ActionTools.getActionProperty(item);
		var existingItem = combo == null ? null : lookupAccelerator(combo);
		if (existingItem != null) {
			if (existingItem == item || existingItem == action) {
				logger.info("Accelerator {} already set for {} - no changes needed", combo, item.getText());
				return false;
			} else if (existingItem != null) {
				if (existingItem instanceof MenuItem) {
					var mi = (MenuItem)existingItem;
					setAccelerator(mi, null);
				} else if (existingItem instanceof Action) {
					var existingAction = (Action)existingItem;
					setAccelerator(existingAction, null);
				} else {
					// Shouldn't happen
					logger.warn("Can't identify {} to remove accelerator", existingItem);
				}
			}
		}
		if (action != null) {
			// Prefer setting the accelerator on the action, rather than the menu item
			if (!setAccelerator(action, combo))
				return false;
		} else {
			// Set accelerator on the menu item if there is no action
			item.acceleratorProperty().unbind();
			if (item.getAccelerator() != null) {
				if (combo == null)
					logger.warn("Accelerator {} for {} will be removed", item.getAccelerator(), item.getText());
				else
					logger.warn("Accelerator for {} will be changed from {} to {}", item.getText(), item.getAccelerator(), combo);
			}
			item.setAccelerator(combo);
		}
		 // Seems necessary to re-add the menu item on Windows to get it working
        var menu = item.getParentMenu();
        var items = menu.getItems();
        int ind = items.indexOf(item);
        items.remove(item);
        items.add(ind, item);
        return true;
	}

	/**
	 * Set the accelerator for the specified action.
     * If the combo is null, any existing accelerator will be removed.
	 * Additionally, if the accelerator is already assigned to another action then it will be 
	 * removed from that item.
	 * 
	 * @param action
	 * @param combo
	 * @return true if changes were made, false otherwise
	 * @see #setAccelerator(String, String)
	 * @see #setAccelerator(MenuItem, KeyCombination)
	 */
	public boolean setAccelerator(Action action, KeyCombination combo) {
		if (!Platform.isFxApplicationThread()) {
			return GuiTools.callOnApplicationThread(() -> setAccelerator(action, combo));
		}
		Objects.requireNonNull(action, "Cannot set accelerator for null action");
		if (Objects.equals(action.getAccelerator(), combo)) {
			logger.info("Accelerator {} already set for {} - no changes needed", combo, action.getText());
			return false;
		}
		action.acceleratorProperty().unbind();
		if (action.getAccelerator() != null) {
			if (combo == null)
				logger.warn("Accelerator {} for {} will be removed", action.getAccelerator(), action.getText());
			else
				logger.info("Accelerator for {} will be changed from {} to {}", action.getText(), action.getAccelerator(), combo);
		}
		action.setAccelerator(combo);
		registerAccelerator(action);
		return true;
	}

	
//	public Map<KeyCombination, String> getAccelerators() {
//		
//		var map = new HashMap<KeyCombination, String>();
//		var menuItems = MenuTools.getFlattenedMenuItems(menuBar.getMenus(), true);
//		for (var mi : menuItems) {
//			var accelerator = mi.getAccelerator();
//			if (accelerator != null)
//				map.put(accelerator, mi.getText());
//		}
//		
//		for (var action : actions) {
//			var accelerator = action.getAccelerator();
//			if (accelerator != null) {
//				var text = action.getText();
//				var existingText = map.put(accelerator, text);
//				if (existingText != null && !Objects.equals(text, existingText) && 
//						!existingText.contains(text)) {
//					map.put(accelerator, text + ", " + existingText);
//				}
//			}
//		}
//		
//		return map;
//	}
	
	
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
	
	
	
	
	/**
	 * Create an action for a plugin to be run through this QuPath instance.
	 * @param name name of the plugin
	 * @param pluginClass class of the plugin
	 * @param arg optional argument (may be required by some plugins)
	 * @return
	 */
	public Action createPluginAction(final String name, final Class<? extends PathPlugin> pluginClass, final String arg) {
		return createPluginAction(name, pluginClass, this, arg);
	}

	
	/**
	 * Create an Action to call the specified plugin for the current image.
	 * @param name plugin name
	 * @param plugin the plugin to call
	 * @param arg any argument required by the plugin
	 * @return an action that may be called to run the plugin
	 */
	public Action createPluginAction(final String name, final PathPlugin<BufferedImage> plugin, final String arg) {
		var action = new Action(name, event -> {
			try {
				runPlugin(plugin, arg, true);
			} catch (Exception e) {
				logger.error("Error running " + plugin.getName() + ": " + e.getLocalizedMessage(), e);
			}
		});
		// We assume that plugins require image data
		action.disabledProperty().bind(noImageData);
		return action;
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
	private static Action createPluginAction(final String name, final Class<? extends PathPlugin> pluginClass, final QuPathGUI qupath, final String arg) {
		try {
			var action = new Action(name, event -> {
				try {
					PathPlugin<BufferedImage> plugin = qupath.createPlugin(pluginClass);
					qupath.runPlugin(plugin, arg, true);
				} catch (Exception e) {
					logger.error("Error running " + name + ": " + e.getLocalizedMessage(), e);
				}
			});
			// We assume that plugins require image data
			action.disabledProperty().bind(qupath.noImageData);
			ActionTools.parseAnnotations(action, pluginClass);
			return action;
		} catch (Exception e) {
			logger.error("Unable to initialize plugin " + pluginClass + ": " + e.getLocalizedMessage(), e);
		}
		return null;
	}
	
	
	/**
	 * Run a plugin, interactively (i.e. launching a dialog) if necessary.
	 * <p>
	 * Note that this does not in itself perform any exception handling.
	 * 
	 * @param plugin the plugin to run
	 * @param arg optional string argument (usually JSON)
	 * @param doInteractive if true, show an interactive dialog if the plugin is an instance of {@link PathInteractivePlugin}
	 * @return true if running the plugin was successful and was not cancelled.
	 *              Note that if {@code doInteractive == true} and the dialog was launched 
	 *              but not run, this will also return true.
	 * @throws Exception
	 */
	public boolean runPlugin(final PathPlugin<BufferedImage> plugin, final String arg, final boolean doInteractive) throws Exception {
//		try {
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
				var runner = new PluginRunnerFX(this);
				ParameterDialogWrapper<BufferedImage> dialog = new ParameterDialogWrapper<>(pluginInteractive, params, runner);
				dialog.showDialog();
				return !runner.isCancelled();
			}
			else {
				try {
					pluginRunning.set(true);
					var runner = new PluginRunnerFX(this);
					@SuppressWarnings("unused")
					var completed = plugin.runPlugin(runner, arg);
					return !runner.isCancelled();
				} finally {
					pluginRunning.set(false);
				}
			}
//		} catch (Exception e) {
//			logger.error("Unable to run plugin " + plugin, e);
//			return false;
//		}
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
	

	/**
	 * Get the action that corresponds to a specific {@link PathTool}, creating a new action if one does not already exist.
	 * @param tool
	 * @return
	 */
	public Action getToolAction(PathTool tool) {
		var action = toolActions.get(tool);
		if (action == null) {
			action = createToolAction(tool);
			toolActions.put(tool, action);
		}
		// Make sure the accelerator is registered
		registerAccelerator(action);
		return action;
	}
	
	private Action createToolAction(final PathTool tool) {
		  var action = createSelectableCommandAction(new SelectableItem<>(selectedToolProperty, tool), tool.getName(), tool.getIcon(), null);
		  action.disabledProperty().bind(Bindings.createBooleanBinding(() -> !tools.contains(tool) || selectedToolLocked.get(), selectedToolLocked, tools));
		  registerAccelerator(action);
		  return action;
	}

	private static <T> Action createSelectableCommandAction(final SelectableItem<T> command, final String name, final Node icon, final KeyCombination accelerator) {
		var action = ActionTools.actionBuilder(e -> command.setSelected(true))
				.text(name)
				.accelerator(accelerator)
				.selectable(true)
				.selected(command.selectedProperty())
				.graphic(icon)
				.build();
		return action;
	}
	
	private static <T> Action createSelectableCommandAction(final SelectableItem<T> command, final String name) {
		return createSelectableCommandAction(command, name, null, null);
	}

	/**
	 * Register the accelerator so we can still trigger the event if it is not otherwise called (e.g. if it doesn't require a 
	 * shortcut key, and macOS drops it).
	 * @param action the action to register; nothing will be done if it has no accelerator set
	 * @return the same action as provided as a parameter
	 */
	private Action registerAccelerator(Action action) {
		var accelerator = action.getAccelerator();
		// Check if nothing needs to be done
		if (accelerator != null && comboMap.get(accelerator) == action)
			return action;
		// Remove the action if we already had it
		comboMap.inverse().remove(action);
		if (accelerator == null) {
			return action;
		}
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
	
	/**
	 * Toggle whether the user is permitted to switch to a new active {@link PathTool}.
	 * This can be used to lock a tool temporarily.
	 * @param enabled
	 */
	public void setToolSwitchingEnabled(final boolean enabled) {
		selectedToolLocked.set(!enabled);
	}
	
	/**
	 * Returns true if the user is able to activate another {@link PathTool}, false otherwise.
	 * @return
	 */
	public boolean isToolSwitchingEnabled() {
		return !selectedToolLocked.get();
	}
	
	/**
	 * Query whether QuPath is in 'read-only' mode. This suppresses dialogs that ask about saving changes.
	 * @return
	 * @apiNote Read only mode is an experimental feature; its behavior is subject to change in future versions.
	 * @see #setReadOnly(boolean)
	 */
	public boolean isReadOnly() {
		return readOnly.get();
	}
	
	/**
	 * Property indicating whether QuPath is in 'read-only' mode.
	 * @return
	 * @apiNote Read only mode is an experimental feature; its behavior is subject to change in future versions.
	 * @see #isReadOnly()
	 * @see #setReadOnly(boolean)
	 */
	public ReadOnlyBooleanProperty readOnlyProperty() {
		return readOnly;
	}
	
	/**
	 * Specify whether QuPath should be in 'read-only' mode.
	 * @param readOnly
	 * @apiNote Read only mode is an experimental feature; its behavior is subject to change in future versions.
	 * @see #isReadOnly()
	 */
	public void setReadOnly(boolean readOnly) {
		this.readOnly.set(readOnly);
	}
	
	/**
	 * Get a list of the current available tools.
	 * Tools may be removed from this list, but {@link #installTool(PathTool, KeyCodeCombination)} is the preferred way 
	 * to add a new tool, to ensure that any accelerator is properly managed.
	 * @return
	 */
	public ObservableList<PathTool> getAvailableTools() {
		return tools;
	}
	
	/**
	 * Install a new tool for interacting with viewers.
	 * @param tool the tool to add
	 * @param accelerator an optional accelerator (may be null)
	 * @return true if the tool was added, false otherwise (e.g. if the tool had already been added)
	 */
	public boolean installTool(PathTool tool, KeyCodeCombination accelerator) {
		if (tool == null || tools.contains(tool))
			return false;
		// Keep the points tool last
		if (accelerator != null) {
			var action = getToolAction(tool);
			if (accelerator != null) {
				action.setAccelerator(accelerator);
				registerAccelerator(action);
			}
		}
		int ind = tools.indexOf(PathTools.POINTS);
		if (ind < 0)
			tools.add(tool);
		else
			tools.add(ind, tool);
		return true;
	}
	
	/**
	 * Programmatically select the active {@link PathTool}.
	 * This may fail if {@link #isToolSwitchingEnabled()} returns false.
	 * @param tool
	 */
	public void setSelectedTool(PathTool tool) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> setSelectedTool(tool));
			return;
		}
		if (!isToolSwitchingEnabled()) {
			logger.warn("Mode switching currently disabled - cannot change to {}", tool);
			return;
		}
		// If the current tool is not move, record before switching to newly selected
		if (getSelectedTool() != PathTools.MOVE)
			previousTool = getSelectedTool();
		this.selectedToolProperty.set(tool);
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
	void updateCursor() {
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
	void updateCursor(final Cursor cursor) {
		for (QuPathViewer viewer : getViewers())
			viewer.getView().setCursor(cursor);
	}

	
	/**
	 * Get a reference to the current {@link ScriptEditor} (which may or may not be open at the moment).
	 * 
	 * @return
	 */
	public ScriptEditor getScriptEditor() {
		if (scriptEditor == null) {
			setScriptEditor(new DefaultScriptEditor(this));
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
		// Try to bind to whether a script is running or not
		scriptRunning.unbind();
		if (scriptEditor instanceof DefaultScriptEditor)
			scriptRunning.bind(((DefaultScriptEditor)scriptEditor).scriptRunning());
		else
			scriptRunning.set(false);
	}
	
	
	/**
	 * Set the control used to display log messages.
	 * @param control
	 */
	public void setLogControl(final ScriptEditorControl<?> control) {
		logViewerCommand.setLogControl(control);
	}
	
	
	/**
	 * Repaint the viewer.  In the future, if multiple viewers are registered with the GUI 
	 * (not yet possible) then this may result in all being repainted.
	 */
	public void repaintViewers() {
		viewerManager.repaintViewers();
	}
	
	/**
	 * Get the value of {@link #selectedToolProperty()}.
	 * @return
	 */
	public PathTool getSelectedTool() {
		return selectedToolProperty().get();
	}
	
	/**
	 * Property containing the currently-selected {@link PathTool}.
	 * @return
	 */
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

	/**
	 * Return the global {@link DefaultImageRegionStore} instance, used to cache and paint image tiles.
	 * @return
	 */
	public DefaultImageRegionStore getImageRegionStore() {
		return imageRegionStore;
	}
	
	
	
	
	private void initializeAnalysisPanel() {
		analysisTabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		analysisTabPane.setTabDragPolicy(TabDragPolicy.REORDER);
		projectBrowser = new ProjectBrowser(this);

		analysisTabPane.getTabs().add(new Tab("Project", projectBrowser.getPane()));
		ImageDetailsPane pathImageDetailsPanel = new ImageDetailsPane(this);
		analysisTabPane.getTabs().add(new Tab("Image", pathImageDetailsPanel.getPane()));
		
		/*
		 * Create tabs.
		 * Note that we don't want ImageData/hierarchy events to be triggered for tabs that aren't visible,
		 * since these can be quite expensive.
		 * For that reason, we create new bindings.
		 * 
		 * TODO: Handle analysis pane being entirely hidden.
		 */
		
		// Create a tab for annotations
		var tabAnnotations = new Tab("Annotations");
		SplitPane splitAnnotations = new SplitPane();
		splitAnnotations.setOrientation(Orientation.VERTICAL);
		var annotationPane = new AnnotationPane(this, imageDataProperty());
		// Don't make updates if the tab isn't visible
		var annotationTabVisible = Bindings.createBooleanBinding(() -> {
			return tabAnnotations.getTabPane() == null || tabAnnotations.isSelected();
		}, tabAnnotations.tabPaneProperty(), tabAnnotations.selectedProperty());
		annotationPane.disableUpdatesProperty().bind(annotationTabVisible.not());
		var tabAnnotationsMeasurements = createMeasurementsAndDescriptionsPane(annotationTabVisible);
		splitAnnotations.getItems().addAll(annotationPane.getPane(), tabAnnotationsMeasurements);
		tabAnnotations.setContent(splitAnnotations);
		analysisTabPane.getTabs().add(tabAnnotations);		
		
		// Create a tab for the full hierarchy
		var tabHierarchy = new Tab("Hierarchy");
		final PathObjectHierarchyView paneHierarchy = new PathObjectHierarchyView(this, imageDataProperty());
		var hierarchyTabVisible = Bindings.createBooleanBinding(() -> {
			return tabHierarchy.getTabPane() == null || tabHierarchy.isSelected();
		}, tabHierarchy.tabPaneProperty(), tabHierarchy.selectedProperty());
		paneHierarchy.disableUpdatesProperty().bind(hierarchyTabVisible.not());
		var tabHierarchyMeasurements = createMeasurementsAndDescriptionsPane(hierarchyTabVisible);
		SplitPane splitHierarchy = new SplitPane();
		splitHierarchy.setOrientation(Orientation.VERTICAL);
		splitHierarchy.getItems().addAll(paneHierarchy.getPane(), tabHierarchyMeasurements);
		tabHierarchy.setContent(splitHierarchy);
		analysisTabPane.getTabs().add(tabHierarchy);
		
		analysisTabPane.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			// Update split locations if both tabs are in the tab pane
			if (tabAnnotations.getTabPane() != null && tabHierarchy.getTabPane() != null) {
				if (o == tabHierarchy) {
					splitHierarchy.setDividerPosition(0, splitAnnotations.getDividerPositions()[0]);
				} else if (o == tabAnnotations) {
					splitAnnotations.setDividerPosition(0, splitHierarchy.getDividerPositions()[0]);				
				}
			}
		});
		var commandLogView = new WorkflowCommandLogView(this);
		TitledPane titledLog = new TitledPane("Command history", commandLogView.getPane());
		titledLog.setCollapsible(false);
		titledLog.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		var pane = new BorderPane(titledLog);
		analysisTabPane.getTabs().add(new Tab("Workflow", pane));
		
		// Make the tabs undockable
		for (var tab : analysisTabPane.getTabs()) {
			GuiTools.makeTabUndockable(tab);
		}
	}
	
	/**
	 * Make a tab pane to show either measurements or descriptions for the selected object.
	 * Optionally provide a bindable value for visibility, since this can reduce expensive updates.
	 * @param visible
	 * @return
	 */
	private TabPane createMeasurementsAndDescriptionsPane(ObservableBooleanValue visible) {
		var tabpaneObjectsShared = new TabPane();
		var objectMeasurementsTable = new SelectedMeasurementTableView(imageDataProperty());
		tabpaneObjectsShared.setSide(Side.BOTTOM);
		var tabSharedTable = new Tab("Measurements", objectMeasurementsTable.getTable());
		tabpaneObjectsShared.getTabs().add(tabSharedTable);
		var descriptionPane = ObjectDescriptionPane.createPane(imageDataProperty(), true);
		var tabSharedDescription = new Tab("Description", descriptionPane);
		tabpaneObjectsShared.getTabs().add(tabSharedDescription);
		tabpaneObjectsShared.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		
		if (visible != null) {
			objectMeasurementsTable.getTable().visibleProperty().bind(visible);
			descriptionPane.visibleProperty().bind(visible);
		}
		return tabpaneObjectsShared;
	}
	
	
	
	/**
	 * Create a default list model for storing available PathClasses.
	 * 
	 * @return
	 */
	public ObservableList<PathClass> getAvailablePathClasses() {
		return availablePathClasses;
	}
	
	
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
			if (PathPrefs.maskImageNamesProperty().get())
				return "(Name masked)";
			return ServerTools.getDisplayableImageName(imageData.getServer());
		} else {
			// Make sure that the status of name masking has been set in the project (in case it hasn't been triggered yet...)
			project.setMaskImageNames(PathPrefs.maskImageNamesProperty().get());
			return entry.getImageName();
		}
	}
	
	/**
	 * Refresh the title bar in the main QuPath window.
	 */
	public void refreshTitle() {
		if (Platform.isFxApplicationThread())
			titleBinding.invalidate();
		else
			Platform.runLater(() -> refreshTitle());
	}
	
	
	private StringBinding titleBinding = Bindings.createStringBinding(
				() -> {
					String name = "QuPath";
					var versionString = getVersionString();
					if (versionString != null)
						name = name + " (" + versionString + ")";
					var imageData = imageDataProperty.get();
					if (imageData == null || !PathPrefs.showImageNameInTitleProperty().get())
						return name;
					return name + " - " + getDisplayedImageName(imageData);
				},
				projectProperty, imageDataProperty, PathPrefs.showImageNameInTitleProperty(), PathPrefs.maskImageNamesProperty());
	
	
	/**
	 * Get a String representing the QuPath version &amp; build time.
	 * 
	 * @return
	 */
	public static String getBuildString() {
		return BuildInfo.getInstance().getBuildString();
	}
	
	private static String getVersionString() {
		return BuildInfo.getInstance().getVersionString();
	}
	
	/**
	 * Get the current QuPath version.
	 * @return
	 */
	public static Version getVersion() {
		return BuildInfo.getInstance().getVersion();
	}
	
	/**
	 * Read-only property containing the image open within the currently-active viewer.
	 * To change the open image data, you should do so directly within the viewer.
	 * @return
	 */
	public ReadOnlyObjectProperty<ImageData<BufferedImage>> imageDataProperty() {
		return imageDataProperty;
	}
	
	
	private void fireImageDataChangedEvent(final ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {		
		
		imageDataProperty.set(imageDataNew);
		
//		// A bit awkward, this... but make sure the extended scripting helper static class knows what's happened
//		QPEx.setBatchImageData(imageDataNew);
		
	}
	
	
	/**
	 * Set the active project, triggering any necessary GUI updates.
	 * 
	 * @param project
	 */
	public void setProject(final Project<BufferedImage> project) {
		var currentProject = this.projectProperty.get();
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
				if (!ProjectCommands.promptToCheckURIs(project, true))
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
		
		this.projectProperty.set(project);
		if (!this.projectBrowser.setProject(project)) {
			this.projectProperty.set(null);
			this.projectBrowser.setProject(null);
		}
		
		// Update the PathClass list, if necessary
		if (project != null) {
			List<PathClass> pathClasses = project.getPathClasses();
			if (pathClasses.isEmpty()) {
				// Update the project according to the specified PathClasses
				project.setPathClasses(getAvailablePathClasses());
			} else {
				// Update the available classes
				if (!pathClasses.contains(PathClass.NULL_CLASS)) {
					pathClasses = new ArrayList<>(pathClasses);
					pathClasses.add(0, PathClass.NULL_CLASS);
				}
				getAvailablePathClasses().setAll(pathClasses);
			}
		}
		
		// Ensure we have the required directories
//		getProjectClassifierDirectory(true);
//		getProjectScriptsDirectory(true);
		
		logger.info("Project set to {}", project);
	}
	
	
	/**
	 * Refresh the project, updating the display if required.
	 * This can be called whenever the project has changed (e.g. by adding or removing items).
	 */
	public void refreshProject() {
		projectBrowser.refreshProject();
	}
	
	/**
	 * Read-only property representing the currently-open project.
	 * @return
	 */
	public ReadOnlyObjectProperty<Project<BufferedImage>> projectProperty() {
		return projectProperty;
	}
	
	/**
	 * Get the value of {@link #projectProperty()}.
	 * @return
	 */
	public Project<BufferedImage> getProject() {
		return projectProperty.get();
	}
	
	
	private BooleanProperty showAnalysisPane = new SimpleBooleanProperty(true);
	protected double lastDividerLocation;
	
	private void setAnalysisPaneVisible(boolean visible) {
		if (visible) {
			if (analysisPanelVisible())
				return;
			splitPane.getItems().setAll(analysisTabPane, mainViewerPane);
			splitPane.setDividerPosition(0, lastDividerLocation);
			pane.setCenter(splitPane);
		} else {
			if (!analysisPanelVisible())
				return;
			lastDividerLocation = splitPane.getDividers().get(0).getPosition();
			pane.setCenter(mainViewerPane);				
		}
	}
	
	private boolean analysisPanelVisible() {
		return pane.getCenter() == splitPane;
	}
	
	

	/**
	 * Get the value of {@link #imageDataProperty()}.
	 * @return
	 */
	public ImageData<BufferedImage> getImageData() {
		return imageDataProperty().get();
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
				filePrevious = Dialogs.promptToSaveFile("Save image data", filePrevious, ServerTools.getDisplayableImageName(imageData.getServer()), "QuPath Serialized Data", PathPrefs.getSerializationExtension());
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
	
	
	
	/**
	 * Calculate the appropriate tile cache size based upon the user preferences.
	 * @return tile cache size in bytes
	 */
	private static long getTileCacheSizeBytes() {
		// Try to compute a sensible value...
		Runtime rt = Runtime.getRuntime();
		long maxAvailable = rt.maxMemory(); // Max available memory
		if (maxAvailable == Long.MAX_VALUE) {
			logger.warn("No inherent maximum memory set - for caching purposes, will assume 64 GB");
			maxAvailable = 64L * 1024L * 1024L * 1024L;
		}
		double percentage = PathPrefs.tileCachePercentageProperty().get();
		if (percentage < 10) {
			logger.warn("At least 10% of available memory needs to be used for tile caching (you requested {}%)", percentage);
			percentage = 10;
		} else if (percentage > 90) {
			logger.warn("No more than 90% of available memory can be used for tile caching (you requested {}%)", percentage);
			percentage = 90;			
		}
		long tileCacheSize = Math.round(maxAvailable * (percentage / 100.0));
		logger.info(String.format("Setting tile cache size to %.2f MB (%.1f%% max memory)", tileCacheSize/(1024.*1024.), percentage));
		return tileCacheSize;
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
		
		/**
		 * Try to remove the row containing the specified viewer, notifying the user if this isn't possible.
		 * @param viewer
		 * @return true if the row was removed, false otherwise
		 */
		public boolean removeRow(final QuPathViewer viewer) {
//			if (viewer.getServer() != null)
//				System.err.println(viewer.getServer().getShortServerName());
			// Note: These are the internal row numbers... these don't necessarily match with the displayed row (?)
			int row = splitPaneGrid.getRow(viewer.getView());
 			if (row < 0) {
				// Shouldn't occur...
				Dialogs.showErrorMessage("Multiview error", "Cannot find " + viewer + " in the grid!");
				return false;
			}
 			if (splitPaneGrid.nRows() == 1) {
				Dialogs.showErrorMessage("Close row error", "The last row can't be removed!");
				return false;
 			}
 			
			int nOpen = splitPaneGrid.countOpenViewersForRow(row);
			if (nOpen > 0) {
				Dialogs.showErrorMessage("Close row error", "Please close all open viewers in selected row, then try again");
//				DisplayHelpers.showErrorMessage("Close row error", "Please close all open viewers in row " + row + ", then try again");
				return false;
			}
			splitPaneGrid.removeRow(row);
			splitPaneGrid.resetGridSize();
			// Make sure the viewer list is up-to-date
			refreshViewerList();
			return true;
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
				if (!isReadOnly() && !promptToSaveChangesOrCancel(dialogTitle, imageData))
					return false;
			}
			viewer.setImageData(null);
			return true;
		}
		
		
		/**
		 * Try to remove the column containing the specified viewer, notifying the user if this isn't possible.
		 * @param viewer
		 * @return true if the column was removed, false otherwise
		 */
		public boolean removeColumn(final QuPathViewer viewer) {
			int col = splitPaneGrid.getColumn(viewer.getView());
			if (col < 0) {
				// Shouldn't occur...
				Dialogs.showErrorMessage("Multiview error", "Cannot find " + viewer + " in the grid!");
				return false;
			}
			
 			if (splitPaneGrid.nCols() == 1) {
				Dialogs.showErrorMessage("Close row error", "The last row can't be removed!");
				return false;
 			}
			
			int nOpen = splitPaneGrid.countOpenViewersForColumn(col);
			if (nOpen > 0) {
				Dialogs.showErrorMessage("Close column error", "Please close all open viewers in selected column, then try again");
//				DisplayHelpers.showErrorMessage("Close column error", "Please close all open viewers in column " + col + ", then try again");
				return false;
			}
			splitPaneGrid.removeColumn(col);
			splitPaneGrid.resetGridSize();
			// Make sure the viewer list is up-to-date
			refreshViewerList();
			return true;
		}
		
		
		public void addRow(final QuPathViewer viewer) {
			splitViewer(viewer, false);
			splitPaneGrid.resetGridSize();
		}

		public void addColumn(final QuPathViewer viewer) {
			splitViewer(viewer, true);
			splitPaneGrid.resetGridSize();
		}

		
		public void splitViewer(final QuPathViewer viewer, final boolean splitVertical) {
			if (!viewers.contains(viewer))
				return;
			
			if (splitVertical) {
				splitPaneGrid.addColumn(splitPaneGrid.getColumn(viewer.getView()));
			} else {
				splitPaneGrid.addRow(splitPaneGrid.getRow(viewer.getView()));
			}
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
					
					// Shift as required - correcting for rotation
					double downsampleRatio = v.getDownsampleFactor() / downsample;
					if (!Double.isNaN(dx) && !Double.isNaN(downsampleRatio)) {
						
						double rot = rotation - v.getRotation();
						double sin = Math.sin(rot);
						double cos = Math.cos(rot);
						
						double dx2 = dx * downsampleRatio;
						double dy2 = dy * downsampleRatio;

						double dx3 = cos * dx2 - sin * dy2;
						double dy3 = sin * dx2 + cos * dy2;
						
						v.setCenterPixelLocation(v.getCenterPixelX() + dx3, v.getCenterPixelY() + dy3);
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
			hierarchy.addObjectBelowParent(coreNewParent, annotation, true);
			
			activeViewer.setSelectedObject(annotation);
			return true;
		}

		@Override
		public void viewerClosed(QuPathViewer viewer) {}

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
			
		}
		
		
	}
	
	
	class QuPathUncaughtExceptionHandler implements UncaughtExceptionHandler {
		
		private long lastExceptionTimestamp = 0L;
		private String lastExceptionMessage = null;
		
		private long sameExceptionCount = 0;
		private long minDelay = 1000;
		
		@Override
		public void uncaughtException(Thread t, Throwable e) {
			// Avoid showing the same message repeatedly
			String msg = e.getLocalizedMessage();
			long timestamp = System.currentTimeMillis();
			try {
				if (timestamp - lastExceptionTimestamp < minDelay && 
						Objects.equals(msg, lastExceptionMessage)) {
					sameExceptionCount++;
					// Don't continually log the full stack trace
					if (sameExceptionCount > 3)
						logger.error("{} (see full stack trace above, or use 'debug' log level)", e.getLocalizedMessage());
					else
						logger.debug(e.getLocalizedMessage(), e);
					return;
				} else
					sameExceptionCount = 0;
	
				if (e instanceof OutOfMemoryError) {
					// Try to reclaim any memory we can
					getViewer().getImageRegionStore().clearCache(true, true);
					Dialogs.showErrorNotification("Out of memory error",
							"Out of memory! You may need to decrease the 'Number of parallel threads' in the preferences, "
							+ "then restart QuPath.");
					logger.error(e.getLocalizedMessage(), e);
				} else {
					Dialogs.showErrorNotification("QuPath exception", e);
					if (defaultActions.SHOW_LOG != null)
						defaultActions.SHOW_LOG.handle(null);
				}
			} finally {
				lastExceptionMessage = msg;
				lastExceptionTimestamp = timestamp;				
			}
		}
		
	}

	
}