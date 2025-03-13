/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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
import java.awt.desktop.QuitEvent;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.script.ScriptException;
import javax.swing.SwingUtilities;

import ij.IJ;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import javafx.application.Application;
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
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import qupath.ext.extensionmanager.core.ExtensionCatalogManager;
import qupath.ext.extensionmanager.core.savedentities.Registry;
import qupath.ext.extensionmanager.core.savedentities.SavedCatalog;
import qupath.fx.utils.FXUtils;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.LogTools;
import qupath.lib.common.Timeit;
import qupath.lib.common.Version;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.AutomateActions;
import qupath.lib.gui.actions.CommonActions;
import qupath.lib.gui.actions.OverlayActions;
import qupath.lib.gui.actions.ViewerActions;
import qupath.lib.gui.actions.menus.Menus;
import qupath.fx.controls.InputDisplay;
import qupath.lib.gui.commands.LogViewerCommand;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.commands.TMACommands;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.panes.ImageDetailsPane;
import qupath.lib.gui.panes.PreferencePane;
import qupath.lib.gui.panes.ServerSelector;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.ImageTypeSetting;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.prefs.SystemMenuBar;
import qupath.lib.gui.scripting.ScriptEditor;
import qupath.lib.gui.scripting.languages.GroovyLanguage;
import qupath.lib.gui.scripting.languages.ScriptLanguageProvider;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.viewer.DragDropImportListener;
import qupath.lib.gui.viewer.ViewerManager;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.ImageData.ImageType;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.UriImageSupport;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.plugins.PathInteractivePlugin;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
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

	private static final ExtensionCatalogManager extensionCatalogManager = new ExtensionCatalogManager(
			UserDirectoryManager.getInstance().extensionsDirectoryProperty(),
			QuPathGUI.class.getClassLoader(),
			String.format("v%s", BuildInfo.getInstance().getVersion().toString()),
			new Registry(List.of(new SavedCatalog(
					"QuPath catalog",
					"Extensions maintained by the QuPath team",
					URI.create("https://github.com/qupath/qupath-catalog"),
					URI.create("https://raw.githubusercontent.com/qupath/qupath-catalog/refs/heads/main/catalog.json"),
					false
			)))
	);
	private static QuPathGUI instance;
	
	/**
	 * Icon size to use in the main QuPath toolbar
	 */
	public static final int TOOLBAR_ICON_SIZE = 16;

	private Stage stage;
	private HostServices hostServices;
	private MenuBar menuBar;

	private DefaultImageRegionStore imageRegionStore;

	private ToolManager toolManager;
	private SharedThreadPoolManager threadPoolManager;
		
	private PreferencePane prefsPane;
	private LogViewerCommand logViewerCommand;
	private ScriptEditor scriptEditor;
		
	private ViewerManager viewerManager;
	private PathClassManager pathClassManager;
	private UpdateManager updateManager;

	private QuPathMainPaneManager mainPaneManager;
	private UndoRedoManager undoRedoManager;
	private MenuItemVisibilityManager menuVisibilityManager;

	private boolean isStandalone = true;
	
	private DragDropImportListener dragAndDrop;
	
	private ObjectProperty<Project<BufferedImage>> projectProperty = new SimpleObjectProperty<>();
	private ObjectProperty<QuPathViewer> viewerProperty = new SimpleObjectProperty<>();
	private StringBinding titleBinding;

	private BooleanProperty readOnlyProperty = new SimpleBooleanProperty(false);
	private BooleanProperty showAnalysisPane = new SimpleBooleanProperty(true);
		
	private BooleanBinding noProject = projectProperty.isNull();
	private BooleanBinding noViewer = viewerProperty.isNull();
	private BooleanBinding noImageData;
	
	private BooleanProperty pluginRunning = new SimpleBooleanProperty(false);
	private BooleanProperty scriptRunning = new SimpleBooleanProperty(false);
	private BooleanBinding uiBlocked = pluginRunning.or(scriptRunning);
	
	private InputDisplay inputDisplay;
	
	/**
	 * Keystrokes can be lost on macOS... so ensure these are handled
	 */
	private BiMap<KeyCombination, Action> comboMap = HashBiMap.create();

	/**
	 * A list of all actions currently registered for this GUI.
	 */
	private Set<Action> actions = new LinkedHashSet<>();

	private CommonActions commonActions;

	private AutomateActions automateActions;
		
	/**
	 * Flag to record when menus are being modified.
	 * This is used to override menu item visibility settings, since failing to do this 
	 * can result in exceptions (or even segfaults...).
	 */
	private BooleanProperty menusInitializing = new SimpleBooleanProperty(false);
	
	
	/**
	 * Create a new QuPath instance.
	 * 
	 * @param stage a stage to use for the main QuPath window
	 */
	private QuPathGUI(final Stage stage, final HostServices hostServices, boolean showStage) {
		super();
		logger.info("Initializing: {}", System.currentTimeMillis());

		this.stage = stage;
		this.hostServices = hostServices;
		
		var timeit = new Timeit().start("Starting");

		// These could be initialized above, but doing it here helps with finding performance issues.
		// Note that the order is sometimes important.
		toolManager = ToolManager.create();
		threadPoolManager = SharedThreadPoolManager.create();
		imageRegionStore = ImageRegionStoreFactory.createImageRegionStore();
		prefsPane = new PreferencePane();
		viewerManager = ViewerManager.create(this);
		pathClassManager = PathClassManager.create();
		updateManager = UpdateManager.create(this);
		dragAndDrop = new DragDropImportListener(this);
		noImageData = imageDataProperty().isNull();
		titleBinding = createTitleBinding();

		logViewerCommand = new LogViewerCommand(stage);
		initializeLoggingToFile();
		logBuildVersion();

		// Try to ensure that any dialogs are shown with a sensible owner
		Dialogs.setPrimaryWindow(stage);

		// Set this as the current instance
		ensureQuPathInstanceSet();
		
		// Ensure the user is notified of any errors from now on
		setDefaultUncaughtExceptionHandler();
		
		// Set up image cache
		timeit.checkpoint("Creating tile cache");
		initializeImageTileCache();
		
		// Handle changes to the current projects, or properties that affect the current project
		initializeProjectBehavior();
				
		// We can create an undo/redo manager as long as we have a viewer
		undoRedoManager = UndoRedoManager.createForObservableViewer(viewerProperty);
		viewerProperty.bind(viewerManager.activeViewerProperty());
		viewerProperty.addListener((v, o, n) -> activateToolsForViewer(n));

		timeit.checkpoint("Creating menubar");
		createMenubar();
		
		toolManager.getTools().addListener((Change<? extends PathTool> c) -> registerAcceleratorsForAllTools());
		registerAcceleratorsForAllTools();
		setupToolsMenu(getMenu(QuPathResources.getString("Menu.Tools"), true));

		timeit.checkpoint("Creating main component");
		BorderPane pane = new BorderPane();
		pane.setCenter(initializeMainComponent());
		pane.setTop(menuBar);
		
		Scene scene = createAndInitializeMainScene(pane);
		mainPaneManager.setDividerPosition(Math.min(0.5, 400/scene.getWidth()));
		
		initializeStage(scene);
		
		// Add listeners to set default project and image data
		syncDefaultImageDataAndProjectForScripting();
		// We can't install the quit handler during startup, since it can cause a crash on some systems
		// due to its reliance on Desktop - so post that request for later.
		// (This is only done if the stage is shown, since when it is hidden the calls to Desktop can be problematic)
		if (showStage)
			Platform.runLater(this::tryToInstallAppQuitHandler);
		initializeLocaleChangeListeners();
		
		// Refresh style - needs to be applied after showing the stage
		timeit.checkpoint("Refreshing style");
		initializeStyle();

		// Remove this to only accept drag-and-drop into a viewer
		TMACommands.installDragAndDropHandler(this);

		if (showStage) {
			timeit.checkpoint("Showing");
			stage.show();
		}

		// Install extensions
		timeit.checkpoint("Adding extensions");
		new QP(); // Ensure initialized
		ExtensionLoader.loadFromManager(extensionCatalogManager, this);

                // Add scripts menu (delayed to here, since it takes a bit longer)
		timeit.checkpoint("Adding script menus");
		
		// Menus should now be complete - try binding visibility
		timeit.checkpoint("Updating menu item visibility");
		menuVisibilityManager = MenuItemVisibilityManager.createMenubarVisibilityManager(menuBar);
		menuVisibilityManager.ignorePredicateProperty().bind(menusInitializing);
		
		// Populating the scripting menu is slower, so delay it until now
		populateScriptingMenu(getMenu(QuPathResources.getString("Menu.Automate"), false));
		SystemMenuBar.manageMainMenuBar(menuBar);

		stage.setMinWidth(600);
		stage.setMinHeight(400);

		logger.debug("{}", timeit.stop());

		// Run startup script if available, posted later to ensure UI is fully initialized
		Platform.runLater(this::maybeRunStartupScript);
	}


	private void maybeRunStartupScript() {
		String property = System.getProperty("qupath.startup.script", null);
		String path = PathPrefs.startupScriptProperty().get();
		if (property != null) {
			// Block startup script is property is 'false' or 'block'
			if ("false".equalsIgnoreCase(property) || "block".equalsIgnoreCase(property)) {
				logger.debug("Startup script blocked by system property");
				return;
			} else {
				if (path != null && !path.isEmpty())
					logger.warn("Startup script is overridden by system property");
				else
					logger.debug("Startup script specified by system property");
				path = property;
			}
		}
		if (path == null || path.isEmpty()) {
			logger.debug("No startup script found");
			return;
		}
		var file = new File(path);
		if (!file.exists()) {
			logger.warn("Startup script does not exist: {}", path);
			return;
		}
		try {
			logger.info("Running startup script {}", path);
			Dialogs.showInfoNotification(QuPathResources.getString("Startup.scriptTitle"),
					String.format(QuPathResources.getString("Startup.scriptRun"), file.getName()));
			runScript(file, null);
		} catch (ScriptException | IllegalArgumentException e) {
			logger.warn("Exception running startup script: {}", e.getMessage());
			throw new RuntimeException(e);
		}
	}
		
	
	/**
	 * Static method to launch QuPath on the JavaFX Application thread.
	 * <p>
	 * This may be be called in an {@link Application#start(Stage)} method.
	 * If so, then it is preferable to use {@link #createInstance(Stage, HostServices)} to provide 
	 * host services, which can be used for some tasks (e.g. opening a browser window, or 
	 * determining the code location for the application).
	 * However this method can be used if QuPath is being launched some other way.
	 * <p>
	 * Afterwards, calls to {@link #getInstance()} will return the QuPath instance.
	 * 
	 * @param stage the stage to use for the QuPath UI
	 * @return
	 * @throws IllegalStateException if an instance of QuPath is already running (i.e. {@link #getInstance()} is not null)
	 * @see #createInstance(Stage, HostServices)
	 * @see #launchInstanceFromSwing()
	 */
	public static QuPathGUI createInstance(Stage stage) throws IllegalStateException {
		return createInstance(stage, null);
	}



	/**
	 * Static method to launch QuPath from a JavaFX application.
	 * <p>
	 * This is typically expected to be called in an {@link Application#start(Stage)} method, 
	 * although could potentially be called later from another JavaFX application.
	 * <p>
	 * Afterwards, calls to {@link #getInstance()} will return the QuPath instance.
	 * 
	 * @param stage the stage to use for the QuPath UI (usually from {@link Application#start(Stage)})
	 * @param hostServices host services from the JavaFX {@link Application}, if available
	 * @return
	 * @throws IllegalStateException if an instance of QuPath is already running (i.e. {@link #getInstance()} is not null)
	 * @see #createInstance(Stage)
	 * @see #launchInstanceFromSwing()
	 */
	public static QuPathGUI createInstance(Stage stage, HostServices hostServices) throws IllegalStateException {
		QuPathGUI instance = getInstance();
		if (instance != null) {
			throw new IllegalStateException("QuPathGUI already exists, cannot create a new instance!");
		}
		if (stage == null)
			stage = new Stage();
		return new QuPathGUI(stage, hostServices, true);
	}

	/**
	 * Create a new QuPath instance that is not visible (i.e. its stage is not shown).
	 * @return
	 * @throws IllegalStateException
	 */
	public static QuPathGUI createHiddenInstance() throws IllegalStateException {
		var stage = new Stage();
		return new QuPathGUI(stage, null, false);
	}


	/**
	 * Static method to launch QuPath from within a Swing/AWT application.
	 * <p>
	 * This aims to handle several things:
	 * <ul>
	 *   <li>initializing JavaFX in the appropriate thread</li>
	 *   <li>flagging that shutting down QuPath should not terminate the JVM</li>
	 *   <li>showing the QuPath UI window</li>
	 * </ul>
	 * <p>
	 * This can potentially be used from other environments (e.g. MATLAB, Fiji, Python).
	 * Afterwards, calls to {@link #getInstance()} will return the QuPath instance as soon as it is available.
	 * However, note that depending upon the thread from which this method is called, the QuPath instance may <i>not</i> 
	 * be available until some time after the method returns.
	 * <p>
	 * If there is already an instance of QuPath running, this requests that it is made visible - but otherwise does nothing.
	 */
	public static void launchInstanceFromSwing() {
		
		// If we are starting from a Swing application, try to ensure we are on the correct thread
		// (This can be particularly important on macOS)
		if (Platform.isFxApplicationThread() || SwingUtilities.isEventDispatchThread()) {
			launchNonStandaloneInstance();
		} else {
			System.out.println("QuPath launch requested in " + Thread.currentThread());
			SwingUtilities.invokeLater(() -> launchNonStandaloneInstance());
			// Required to be able to restart QuPath... or probably any JavaFX application
			Platform.setImplicitExit(false);
		}		
		
	}



	private static void launchNonStandaloneInstance() {
		
		if (Platform.isFxApplicationThread()) {
			System.out.println("Launching new QuPath instance...");
			logger.info("Launching new QuPath instance...");
			QuPathGUI qupath = createInstance(new Stage());
			qupath.isStandalone = false;
			qupath.getStage().show();
			System.out.println("Done!");			
		} else if (SwingUtilities.isEventDispatchThread()) {
			System.out.println("Initializing with JFXPanel...");
			new JFXPanel(); // To initialize
			Platform.runLater(() -> launchNonStandaloneInstance());
		} else {
			try {
				// This will fail if already started... but unfortunately there is no method to query if this is the case
				System.out.println("Calling Platform.startup()...");
				Platform.startup(() -> launchNonStandaloneInstance());
			} catch (IllegalStateException e) {
				System.err.println("If JavaFX is initialized, be sure to call launchQuPath() on the Application thread!");
				System.out.println("Calling Platform.runLater()...");
				Platform.runLater(() -> launchNonStandaloneInstance());
			}
		}
		
	}



	/**
	 * Request an automated update check in a background thread.
	 * This will use the user preferences to determine whether or how to check for updates 
	 * (i.e. if the preferences have disabled an update check, then it will not be run).
	 * @see #requestFullUpdateCheck()
	 */
	public void requestAutomaticUpdateCheck() {
		updateManager.runAutomaticUpdateCheck();
	}

	/**
	 * Request a manual update check in a background thread.
	 * This should perform the check if possible, regardless of the user preferences.
	 * @see #requestAutomaticUpdateCheck()
	 */
	public void requestFullUpdateCheck() {
		updateManager.runManualUpdateCheck();
	}

	
	private void initializeLoggingToFile() {
		if (PathPrefs.doCreateLogFilesProperty().get()) {
			File fileLogging = tryToStartLogFile();
			if (fileLogging != null) {
				logger.info("Logging to file {}", fileLogging);
			} else {
				logger.warn("No directory set for log files! None will be written.");
			}
		}
	}



	/**
	 * Try to start logging to a file.
	 * This will only work if <code>PathPrefs.getLoggingPath() != null</code>.
	 * 
	 * @return the file that will (attempt to be) used for logging, or <code>null</code> if no file is to be used.
	 */
	private File tryToStartLogFile() {
		var pathLogging = UserDirectoryManager.getInstance().getLogDirectoryPath();
		if (pathLogging != null) {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
			String name = "qupath-" + dateFormat.format(new Date()) + ".log";
			File fileLog = new File(pathLogging.toFile(), name);
			LogManager.logToFile(fileLog);
			return fileLog;
		}
		return null;
	}



	private void logBuildVersion() {
		var buildString = BuildInfo.getInstance().getBuildString();
		var version = BuildInfo.getInstance().getVersion();
		if (buildString != null)
			logger.info("QuPath build: {}", buildString);
		else if (version != null) {
			logger.info("QuPath version: {}", version);			
		} else
			logger.warn("QuPath version unknown!");				
	}



	private void initializeProjectBehavior() {
		setupProjectNameMasking();
		pathClassManager.getAvailablePathClasses().addListener((Change<? extends PathClass> c) -> syncProjectPathClassesToAvailable());
	}
	
	private void syncProjectPathClassesToAvailable() {
		var project = getProject();
		if (project != null) {
			project.setPathClasses(getAvailablePathClasses());
		}
	}
	
	
	private void setupProjectNameMasking() {
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
	}



	private Scene createAndInitializeMainScene(Parent content) {
		Scene scene;
		try {
			Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
			scene = new Scene(content, bounds.getWidth()*0.8, bounds.getHeight()*0.8);
		} catch (Exception e) {
			logger.debug("Unable to set stage size using primary screen {}", Screen.getPrimary());
			scene = new Scene(content, 1000, 600);
		}
		addSceneEventFiltersAndHandlers(scene);
		dragAndDrop.setupTarget(scene);
		return scene;
	}
	
	
	private void initializeStage(Scene scene) {
		stage.setScene(scene);
		stage.setOnCloseRequest(this::handleCloseMainStageRequest);
		stage.getIcons().addAll(loadIconList());
		stage.titleProperty().bind(titleBinding);
	}
	
	
	private void initializeStyle() {
		// Update display
		// Requesting the style should be enough to make sure it is called...
		logger.debug("Setting style to {}", QuPathStyleManager.selectedStyleProperty().get());
		QuPathStyleManager.refresh();
	}
	
	
	private void createMenubar() {
		this.menuBar = createEmptyMenubarMenus();
		populateMenubar();
	}
	
	private MenuBar createEmptyMenubarMenus() {
		return new MenuBar(
				Arrays.asList(
						"Menu.File",
						"Menu.Edit",
						"Menu.Tools",
						"Menu.View",
						"Menu.Objects",
						"Menu.TMA",
						"Menu.Measure",
						"Menu.Automate",
						"Menu.Analyze",
						"Menu.Classify",
						"Menu.Extensions",
						"Menu.Window",
						"Menu.Help")
				.stream()
				.map(QuPathGUI::createMenuFromKey)
				.toArray(Menu[]::new)
				);
	}
	
	private static Menu createMenuFromKey(String key) {
		Menu menu = new Menu();
		QuPathResources.getLocalizedResourceManager().registerProperty(menu.textProperty(), key);
		return menu;
	}
	
	private void populateMenubar() {
		installActions(Menus.createAllMenuActions(this));
		// Insert a recent projects menu
		getMenu(QuPathResources.getString("Menu.File"), true).getItems().add(1, createRecentProjectsMenu());
	}

	
	
	private void populateScriptingMenu(Menu menuScripting) {
		Objects.requireNonNull(menuScripting);
		ScriptEditor editor = getScriptEditor();
		var sharedScriptMenuLoader = new ScriptMenuLoader("Shared scripts...", PathPrefs.scriptsPathProperty(), this::getScriptEditor);
		
		StringBinding projectScriptsPath = Bindings.createStringBinding(() -> {
			var project = getProject();
			File dir = project == null ? null : Projects.getBaseDirectory(project);
			return dir == null ? null : new File(dir, "scripts").getAbsolutePath();
		}, projectProperty);
		var projectScriptMenuLoader = new ScriptMenuLoader("Project scripts...", projectScriptsPath, this::getScriptEditor);
		projectScriptMenuLoader.getMenu().visibleProperty().bind(
				projectProperty.isNotNull().and(menuVisibilityManager.ignorePredicateProperty().not())
				);
		
		var scriptDirectoryProperty = UserDirectoryManager.getInstance().scriptsDirectoryProperty();
		StringBinding userScriptsPath = Bindings.createStringBinding(() -> {
			Path path = scriptDirectoryProperty.get();
			return path == null ? null : path.toString();
		}, scriptDirectoryProperty);
		ScriptMenuLoader userScriptMenuLoader = new ScriptMenuLoader("User scripts...", userScriptsPath, this::getScriptEditor);
	
		MenuTools.addMenuItems(
				menuScripting,
				null,
				sharedScriptMenuLoader.getMenu(),
				userScriptMenuLoader.getMenu(),
				projectScriptMenuLoader.getMenu()
				);
	}



	/**
	 * Set up the tools menu to listen to the available tools.
	 * @param menu
	 */
	private void setupToolsMenu(Menu menu) {
		refreshToolsMenu(toolManager.getTools(), menu);
		toolManager.getTools().addListener((Change<? extends PathTool> c) -> refreshToolsMenu(c.getList(), menu));
	}



	private void refreshToolsMenu(List<? extends PathTool> tools, Menu menu) {
		List<MenuItem> items = new ArrayList<>();
		for (var t : tools) {
			var action = toolManager.getToolAction(t);
			var mi = ActionTools.createCheckMenuItem(action);
			items.add(mi);
		}
		menu.getItems().setAll(items);
		MenuTools.addMenuItems(menu, null, ActionTools.createCheckMenuItem(toolManager.getSelectionModeAction()));
	}



	private void registerAcceleratorsForAllTools() {
		for (var t : toolManager.getTools()) {
			var action = toolManager.getToolAction(t);
			registerAccelerator(action);
		}
		registerAccelerator(toolManager.getSelectionModeAction());
	}



	private void setDefaultUncaughtExceptionHandler() {
		Thread.setDefaultUncaughtExceptionHandler(new QuPathUncaughtExceptionHandler(this));
	}
	
	
	public BooleanProperty showAnalysisPaneProperty() {
		return showAnalysisPane;
	}
	
	
	private Pane initializeMainComponent() {
		
		mainPaneManager = new QuPathMainPaneManager(this);
		
		mainPaneManager.setAnalysisPaneVisible(showAnalysisPane.get());
		showAnalysisPane.addListener((v, o, n) -> mainPaneManager.setAnalysisPaneVisible(n));
		
		// Ensure actions are set appropriately
		toolManager.selectedToolProperty().addListener((v, o, n) -> {
			Action action = toolManager.getToolAction(n);
			if (action != null)
				action.setSelected(true);
			
			activateToolsForViewer(getViewer());
			
			if (n == PathTools.POINTS)
				commonActions.SHOW_POINTS_DIALOG.handle(null);
			
			updateCursorForSelectedTool();			
		});
		
		return mainPaneManager.getMainPane();
	}


	/**
	 * Observable value indicating that the user interface is/should be blocked.
	 * This happens when a plugin or script is running.
	 * @return
	 */
	public ObservableValue<Boolean> uiBlocked() {
		return uiBlocked;
	}


	private void syncDefaultImageDataAndProjectForScripting() {
		imageDataProperty().addListener((v, o, n) -> QP.setDefaultImageData(n));
		projectProperty().addListener((v, o, n) -> QP.setDefaultProject(n));
	}
	
	
	private void initializeImageTileCache() {
		PathPrefs.tileCachePercentageProperty().addListener((v, o, n) -> {
			imageRegionStore.getCache().clear();
		});
		ImageServerProvider.setCache(imageRegionStore.getCache(), BufferedImage.class);
		// Turn off the use of ImageIODiskCache (it causes some trouble)
		ImageIO.setUseCache(false);
	}
	
	
	private void initializeLocaleChangeListeners() {
		// If the Locale changes, we want to try to refresh all list & tables to update number formatting
		ChangeListener<Locale> localeListener = (v, o, n) -> FXUtils.refreshAllListsAndTables();
		PathPrefs.defaultLocaleDisplayProperty().addListener(localeListener);
		PathPrefs.defaultLocaleFormatProperty().addListener(localeListener);
	}
	
	
	private void tryToInstallAppQuitHandler() {
		if (Desktop.isDesktopSupported()) {
			var desktop = Desktop.getDesktop();
			if (desktop.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) {
				desktop.setQuitHandler(new QuPathQuitHandler());
			}
		}
	}
		
	
	/**
	 * Class to handle 'abnormal' requests to quit (e.g. from taskbar or logout)
	 */
	class QuPathQuitHandler implements QuitHandler {
		
		@Override
		public void handleQuitRequestWith(QuitEvent e, QuitResponse response) {
			// Report that we have cancelled - we'll quit anyway if the user confirms it,
			// but we need to handle this on the Application thread
			Platform.runLater(() -> {
				sendQuitRequest();
				response.cancelQuit();
			});
		}
		
	}

	
	
	private void ensureQuPathInstanceSet() {
		synchronized (QuPathGUI.class) { 
			if (instance == null || instance.getStage() == null || !instance.getStage().isShowing())
				instance = this;
		}
	}
	
	
	private void addSceneEventFiltersAndHandlers(Scene scene) {
		scene.addEventFilter(MouseEvent.ANY, new MainSceneMouseEventFilter());
		scene.addEventFilter(KeyEvent.ANY, new MainSceneKeyEventFilter());
		scene.setOnKeyReleased(new MainSceneKeyEventHandler());
	}

	
	class MainSceneKeyEventFilter implements EventHandler<KeyEvent> {

		@Override
		public void handle(KeyEvent e) {
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
						active.setSpaceDown(pressed);
				}
			} else if (e.getCode() == KeyCode.S && e.getEventType() == KeyEvent.KEY_PRESSED) {
				PathPrefs.tempSelectionModeProperty().set(true);
			} else if (e.getEventType() == KeyEvent.KEY_RELEASED) {
				PathPrefs.tempSelectionModeProperty().set(false);
			}
		}
		
	}
	
	
	class MainSceneKeyEventHandler implements EventHandler<KeyEvent> {

		@Override
		public void handle(KeyEvent e) {
			if (e.getEventType() != KeyEvent.KEY_RELEASED)
				return;

			// For detachable viewers, we can have events passed from the other viewer
			// but which should be handled here
			var target = e.getTarget();
			boolean propagatedFromAnotherScene = false;
			if (target instanceof Node node) {
				if (node.getScene() != stage.getScene())
					propagatedFromAnotherScene = true;
			}

			// It seems if using the system menubar on Mac, we can sometimes need to mop up missed keypresses
			if (!propagatedFromAnotherScene) {
				if (e.isConsumed() || e.isShortcutDown() || !(GeneralTools.isMac() && getMenuBar().isUseSystemMenuBar()) || e.getTarget() instanceof TextInputControl) {
					return;
				}
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
				var overlayActions = getOverlayActions();
				var action = overlayActions.SHOW_DETECTIONS;
				action.setSelected(!action.isSelected());
				action = overlayActions.SHOW_PIXEL_CLASSIFICATION;
				action.setSelected(!action.isSelected());
				e.consume();
			}
		}
		
	}	
	
	class MainSceneMouseEventFilter implements EventHandler<MouseEvent> {
		
		private long lastMousePressedWarning = 0L;
		
		@Override
		public void handle(MouseEvent e) {
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
							lastMousePressedWarning = time;
						}
					}
				}
			} else if (e.getButton() == MouseButton.MIDDLE && e.getEventType() == MouseEvent.MOUSE_CLICKED) {
				logger.debug("Middle button pressed {}x {}", e.getClickCount(), System.currentTimeMillis());
				// Here we toggle between the MOVE tool and any previously selected tool
				if (toolManager.getSelectedTool() == PathTools.MOVE)
					toolManager.setSelectedTool(toolManager.getPreviousSelectedTool());
				else
					toolManager.setSelectedTool(PathTools.MOVE);
			}
		}
		
	}
	
	
	/**
	 * Called when there is a close request for the entire QuPath stage
	 * @param e
	 */
	private void handleCloseMainStageRequest(WindowEvent e) {
		// Added to try to resolve macOS issue in which pressing Cmd+Q multiple times 
		// resulted in multiple save prompts appearing - https://github.com/qupath/qupath/issues/941
		// Must be checked on other platforms
		if (Platform.isNestedLoopRunning()) {
			logger.debug("Close request from nested loop - will be discarded");
			e.consume();
			return;
		}

		Collection<? extends QuPathViewer> unsavedViewers = getViewersWithUnsavedChanges();
		if (!unsavedViewers.isEmpty()) {
			if (unsavedViewers.size() == 1) {
				if (!requestToCloseViewer(unsavedViewers.iterator().next(), "Quit QuPath")) {
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

		// should prompt users to save changes if desired.
		if (!scriptEditor.requestClose()) {
			e.consume();
			return;
		}
		
		// Warn if there is a script running
        if (scriptRunning.get()) {
			if (!Dialogs.showYesNoDialog("Quit QuPath", "A script is currently running! Quit anyway?")) {
				logger.trace("Pressed no to quit window with script running!");
				e.consume();
				return;
			}
		}
		
		// Sync changes in the project
		var project = getProject();
		if (project != null) {
			try {
				project.syncChanges();
			} catch (IOException ex) {
				logger.error("Error syncing project: " + ex.getLocalizedMessage(), e);
			}
		}

		// Close the region store to stop any painting requests
		if (imageRegionStore != null)
			imageRegionStore.close();

		// Save the PathClasses
		pathClassManager.savePathClassesToPreferences();

		// Flush the preferences
		if (!PathPrefs.savePreferences())
			logger.error("Error saving preferences");

		// Shut down any thread pools we know about
		threadPoolManager.close();

		// Shut down all our image servers
		closeAllOpenImagesWithoutPrompts();

		// Reset the instance
		instance = null;

		// Exit if running as a standalone application
		if (isStandalone()) {
			logger.info("Calling Platform.exit();");
			Platform.exit();
			// This is required when quitting from Fiji (at least on macOS)
			var ij = IJ.getInstance();
			if (ij != null) {
				logger.debug("Quitting from ImageJ");
				ij.exitWhenQuitting(true);
				ij.quit();
			}
			// Something of an extreme option... :/
			// Shouldn't be needed if we shut down everything properly, but here as a backup just in case 
			// (e.g. if ImageJ is running and this blocks exit)
			System.exit(0);
		}
	}
	
	
	private Collection<? extends QuPathViewer> getViewersWithUnsavedChanges() {
		Set<QuPathViewer> unsavedViewers = new LinkedHashSet<>();
		for (QuPathViewer viewer : viewerManager.getAllViewers()) {
			if (viewer.getImageData() != null && viewer.getImageData().isChanged())
				unsavedViewers.add(viewer);
		}
		return unsavedViewers;
	}
	
	private void closeAllOpenImagesWithoutPrompts() {
		for (QuPathViewer v : getAllViewers()) {
			try {
				if (v.getImageData() != null)
					v.getImageData().getServer().close();
			} catch (Exception e2) {
				logger.warn("Problem closing server", e2);
			}
		}
	}
	
	
	
	/**
	 * Try to launch a browser window for a specified URL.
	 * 
	 * @param url the URL to open in the browser
	 * @return true if this was (as far as we know...) successful, and false otherwise
	 * 
	 * @since v0.5.0 (renamed from {@code launchInBrowserWindow(String}}
	 */
	public static boolean openInBrowser(final String url) {
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
	 * Get the directory containing the QuPath code
	 * @return path object representing the code directory, or null if this cannot be determined
	 */
	Path getCodeDirectory() {
		URI uri = null;
		try {
			if (hostServices != null) {
				String code = hostServices.getCodeBase();
				if (code == null || code.isBlank())
					code = hostServices.getDocumentBase();
				if (code != null && code.isBlank()) {
					uri = GeneralTools.toURI(code);
					return Paths.get(uri);
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
					.toURI()).getParent();
		} catch (Exception e) {
			logger.error("Error identifying code directory: " + e.getLocalizedMessage(), e);
			return null;
		}
	}
	
	
	/**
	 * Get the ToolManager that handles available and selected tools.
	 * @return
	 */
	public ToolManager getToolManager() {
		return toolManager;
	}



	/**
	 * Get a {@link SharedThreadPoolManager} to help with submitting tasks in other threads.
	 * A benefit of using this is that all the thread pools created will be shutdown when 
	 * QuPath is exited.
	 * @return
	 */
	public SharedThreadPoolManager getThreadPoolManager() {
		return threadPoolManager;
	}

	/**
	 * @return the {@link ExtensionCatalogManager} that manage catalogs and extensions of this
	 * QuPath GUI
	 */
	public static ExtensionCatalogManager getExtensionCatalogManager() {
		return extensionCatalogManager;
	}
	
	/**
	 * Get the viewer manager, which gives access to all the viewers available within this QuPath instance.
	 * @return
	 */
	public ViewerManager getViewerManager() {
		return viewerManager;
	}



	/**
	 * Get an observable list of available PathClasses.
	 * @return
	 */
	public ObservableList<PathClass> getAvailablePathClasses() {
		return pathClassManager.getAvailablePathClasses();
	}

	
	/**
	 * Get a reference to the default drag &amp; drop listener, so this may be added to additional windows if needed.
	 * 
	 * @return
	 */
	public DragDropImportListener getDefaultDragDropListener() {
		return dragAndDrop;
	}



	/**
	 * Populate the availablePathClasses with a default list.
	 * 
	 * @return true if changes were mad to the available classes, false otherwise
	 */
	public boolean resetAvailablePathClasses() {
		return pathClassManager.resetAvailablePathClasses();
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
	
	
	
	/**
	 * Access the main tab pane shown in the QuPath window.
	 * This enables extensions to add or remove tabs - but be cautious!
	 * <ul>
	 * <li>Removing tabs can impact other functionality</li>
	 * <li>If adding a tab, it is usually best to apply {@link FXUtils#makeTabUndockable(Tab)}</li>
	 * </ul>
	 * @return
	 */
	public TabPane getAnalysisTabPane() {
		return mainPaneManager == null ? null : mainPaneManager.getAnalysisTabPane().getTabPane();
	}

	
	/**
	 * Load QuPath icons, and various resolutions.
	 * @return
	 */
	private List<Image> loadIconList() {
		try {
			List<Image> icons = new ArrayList<>();
			for (int i : new int[]{16, 32, 48, 64, 128, 256, 512}) {
				try {
					Image icon = loadIcon(i);
					icons.add(icon);
				} catch (IOException e) {
					logger.warn("Unable to load icon for size " + i + ": " + e.getLocalizedMessage(), e);
				}
			}
			if (!icons.isEmpty())
				return icons;
		} catch (Exception e) {
			logger.error("Exception loading icons: " + e.getLocalizedMessage(), e);
		}
		return Collections.emptyList();
	}
	
	private Image loadIcon(int size) throws IOException {
		String path = "icons/QuPath_" + size + ".png";
		try (InputStream stream = QuPathGUI.class.getClassLoader().getResourceAsStream(path)) {
			return new Image(stream);
		}
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
	 * Get an unmodifiable observable list of all viewers.
	 * @return
	 * @see ViewerManager#getAllViewers()
	 */
	public ObservableList<QuPathViewer> getAllViewers() {
		return viewerManager.getAllViewers();
	}
	
	/**
	 * Get the currently active viewer.
	 * @return
	 */
	public QuPathViewer getViewer() {
		return viewerManager == null ? null : viewerManager.getActiveViewer();
	}
	
	/**
	 * Get the static instance of the current QuPath GUI.
	 * @return
	 */
	public static QuPathGUI getInstance() {
		return instance;
	}
	
	
	private void activateToolsForViewer(final QuPathViewer viewer) {
		if (viewer != null)
			viewer.setActiveTool(toolManager.getSelectedTool());		
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
				if (getAllViewers().size() == 1)
					viewer = getViewer();
				else {
					Dialogs.showErrorMessage("Open saved data", "Please specify the viewer where the data should be opened!");
					return false;
				}
			}
			
			// First check to see if the ImageData is already open - if so, just activate the viewer
			for (QuPathViewer v : viewerManager.getAllViewers()) {
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
				logger.warn("Unable to read image server from file: {}", e.getLocalizedMessage());
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
						var newPath = FileChoosers.promptForFilePathOrURI("Set path to missing image", currentPath);
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
						logger.error("Unable to build server {}", serverBuilder, e);
					}
				}
				if (server == null)
					return false;

				// Small optimization... put in a thumbnail request early in a background thread.
				// This way that it will be fetched while the image data is being read -
				// generally leading to improved performance in the viewer's setImageData method
				// (specifically the updating of the ImageDisplay, which needs a thumbnail)
				final ImageServer<BufferedImage> serverTemp = server;
				threadPoolManager.submitShortTask(() -> imageRegionStore.getThumbnail(serverTemp, 0, 0, true));
			}
			
			
			if (promptToSaveChanges && imageData != null && imageData.isChanged()) {
				if (!isReadOnly() && !promptToSaveChangesOrCancel("Save changes", imageData))
					return false;
			}
			
			try {
				ImageData<BufferedImage> imageData2 = PathIO.readImageData(file, server);
				viewer.setImageData(imageData2);
			} catch (IOException e) {
				Dialogs.showErrorMessage("Read image data", "Error reading image data\n" + e.getLocalizedMessage());
				logger.error(e.getMessage(), e);
			}
			
			return true;
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
		
		// Check to see if the ImageData is already open in another viewer - if so, just activate it
		for (QuPathViewer v : viewerManager.getAllViewers()) {
			ImageData<BufferedImage> data = v.getImageData();
			if (data != null && project.getEntry(data) == entry) {
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
			Dialogs.showErrorMessage("Load ImageData", "Error attempting to load image data\n" + e.getLocalizedMessage());
			logger.error(e.getMessage(), e);
			// If this failed
			viewer.resetImageData();
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
	private boolean checkSaveChanges(ImageData<BufferedImage> imageData) {
		if (!imageData.isChanged() || isReadOnly())
			return true;
		ProjectImageEntry<BufferedImage> entry = getProjectImageEntry(imageData);
		String name = entry == null ? ServerTools.getDisplayableImageName(imageData.getServer()) : entry.getImageName();
		var owner = FXUtils.getWindow(getViewer().getView());
		var response = Dialogs.builder()
				.title("Save changes")
				.owner(owner)
				.contentText("Save changes to " + name + "?")
				.buttons(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
				.showAndWait()
				.orElse(ButtonType.CANCEL);
		if (response == ButtonType.CANCEL)
			return false;
		if (response == ButtonType.NO)
			return true;
		
		try {
			if (entry == null) {
				String lastPath = imageData.getLastSavedPath();
				File lastFile = lastPath == null ? null : new File(lastPath);
				File file = FileChoosers.promptToSaveFile("Save data", lastFile,
						FileChoosers.createExtensionFilter("QuPath data files", PathPrefs.getSerializationExtension()));
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
			Dialogs.showErrorMessage("Save ImageData", "Error attempting to save image data\n" + e.getLocalizedMessage());
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
		
	
	
	/**
	 * Show a file chooser to open a new image in the current viewer.
	 * <p>
	 * If this encounters an exception, an error message will be shown.
	 * 
	 * @return true if the image was opened, false otherwise
	 */
	public boolean promptToOpenImageFile() {
		try {
			return openImage(getViewer(), null, true, false);
		} catch (IOException e) {
			Dialogs.showErrorMessage("Open image", "Error opening image\n" + e.getLocalizedMessage());
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	/**
	 * Show a dialog to open a new image in the current viewer, with support 
	 * for entering a URL (rather than requiring a local file only).
	 * <p>
	 * If this encounters an exception, an error message will be shown.
	 * 
	 * @return true if the image was opened, false otherwise
	 */
	public boolean promptToOpenImageFileOrUri() {
		try {
			return openImage(getViewer(), null, true, true);
		} catch (IOException e) {
			Dialogs.showErrorMessage("Open image", "Error opening image\n" + e.getLocalizedMessage());
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	
	public boolean openImage(QuPathViewer viewer, String pathNew) throws IOException {
		return openImage(viewer, pathNew, false, false);
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
			if (getAllViewers().size() == 1)
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
		}
		// Prompt for a path, if required
		File fileNew = null;
		if (pathNew == null) {
			if (includeURLs) {
				pathNew = FileChoosers.promptForFilePathOrURI("Choose path", pathOld);
				if (pathNew == null)
					return false;
				fileNew = new File(pathNew);
			} else {
				fileNew = FileChoosers.buildFileChooser()
						.initialDirectory(fileBase)
						.build()
						.showOpenDialog(Dialogs.getPrimaryWindow());
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
			
			if (builders.isEmpty()) {
				String name = fileNew == null ? pathNew : fileNew.getName();
				String message = "No supported image reader found for " + name + ".\nSee View > Show log for more details";
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
								if (response == ButtonType.CANCEL)
									return false;
								if (response == ButtonType.YES)
									serverNew = serverWrapped;
							}
						}
					}
					imageData = createNewImageData(serverNew);
				}
				
				viewer.setImageData(imageData);

				if (imageData.getImageType() == ImageType.UNSET && PathPrefs.imageTypeSettingProperty().get() == ImageTypeSetting.PROMPT) {
					var type = GuiTools.estimateImageType(serverNew, imageRegionStore.getThumbnail(serverNew, 0, 0, true));
					ImageDetailsPane.promptToSetImageType(imageData, type);
				}

				return true;
			} else {
				// Show an error message if we can't open the file
				Dialogs.showErrorNotification("Open image", "Sorry, I can't open " + pathNew);
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
	private ImageData<BufferedImage> createNewImageData(final ImageServer<BufferedImage> server, final boolean estimateImageType) {
		return new ImageData<>(server, estimateImageType ? GuiTools.estimateImageType(server, imageRegionStore.getThumbnail(server, 0, 0, true)) : ImageData.ImageType.UNSET);
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
				Dialogs.showErrorMessage("Script error", e.getLocalizedMessage());
				logger.error(e.getMessage(), e);
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
				Dialogs.showErrorMessage("Script error", e.getLocalizedMessage());
				logger.error(e.getMessage(), e);
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
			return FXUtils.callOnApplicationThread(() -> installImageDataCommand(menuPath, command));
		}
		Menu menu = parseMenu(menuPath, "Menu.Extensions", true);
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
			return FXUtils.callOnApplicationThread(() -> installCommand(menuPath, runnable));
		}
		Menu menu = parseMenu(menuPath, "Menu.Extensions", true);
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
			return FXUtils.callOnApplicationThread(() -> setAccelerator(item, combo));
		}

		Objects.requireNonNull(item, "Cannot set accelerator for null menu item");
		var action = ActionTools.getActionProperty(item);
		var existingItem = combo == null ? null : lookupAccelerator(combo);
		if (existingItem != null) {
			if (existingItem == item || existingItem == action) {
				logger.info("Accelerator {} already set for {} - no changes needed", combo, item.getText());
				return false;
			} else if (existingItem != null) {
				if (existingItem instanceof MenuItem existingMenuItem) {
					setAccelerator(existingMenuItem, null);
				} else if (existingItem instanceof Action existingAction) {
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
			return FXUtils.callOnApplicationThread(() -> setAccelerator(action, combo));
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
	public void sendQuitRequest() {
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
		var imageData = getImageData();
		if (doInteractive && plugin instanceof PathInteractivePlugin pluginInteractive) {
			ParameterList params = pluginInteractive.getDefaultParameterList(imageData);
			// Update parameter list, if necessary
			if (arg != null) {
				Map<String, String> map = GeneralTools.parseArgStringValues(arg);
				// We use the US locale because we need to ensure decimal points (not commas)
				ParameterList.updateParameterList(params, map, Locale.US);
			}
			var runner = new TaskRunnerFX(this);
			ParameterDialogWrapper<BufferedImage> dialog = new ParameterDialogWrapper<>(pluginInteractive, params, runner);
			dialog.showDialog();
			return !runner.isCancelled();
		}
		else {
			try {
				pluginRunning.set(true);
				var runner = new TaskRunnerFX(this);
				@SuppressWarnings("unused")
				var completed = plugin.runPlugin(runner, imageData, arg);
				return !runner.isCancelled();
			} finally {
				pluginRunning.set(false);
			}
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
		return mainPaneManager == null ? null : mainPaneManager.getToolBar();
	}
	
	
	/**
	 * Get the {@link UndoRedoManager}, which can be useful if needing to clear it in cases where available memory is low.
	 * @return
	 */
	public UndoRedoManager getUndoRedoManager() {
		return undoRedoManager;
	}
	
	/**
	 * Query whether QuPath is in 'read-only' mode. This suppresses dialogs that ask about saving changes.
	 * @return
	 * @apiNote Read only mode is an experimental feature; its behavior is subject to change in future versions.
	 * @see #setReadOnly(boolean)
	 */
	public boolean isReadOnly() {
		return readOnlyProperty.get();
	}
	
	/**
	 * Property indicating whether QuPath is in 'read-only' mode.
	 * @return
	 * @apiNote Read only mode is an experimental feature; its behavior is subject to change in future versions.
	 * @see #isReadOnly()
	 * @see #setReadOnly(boolean)
	 */
	public ReadOnlyBooleanProperty readOnlyProperty() {
		return readOnlyProperty;
	}
	
	/**
	 * Specify whether QuPath should be in 'read-only' mode.
	 * @param readOnly
	 * @apiNote Read only mode is an experimental feature; its behavior is subject to change in future versions.
	 * @see #isReadOnly()
	 */
	public void setReadOnly(boolean readOnly) {
		this.readOnlyProperty.set(readOnly);
	}
	
		
	private void updateCursorForSelectedTool() {
		if (stage == null || stage.getScene() == null)
			return;
		var mode = toolManager.getSelectedTool();
		if (mode == PathTools.MOVE)
			setCursorForAllViewers(Cursor.HAND);
		else
			setCursorForAllViewers(Cursor.DEFAULT);
	}
	
	private void setCursorForAllViewers(final Cursor cursor) {
		for (QuPathViewer viewer : getAllViewers())
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
		if (scriptEditor instanceof DefaultScriptEditor defaultScriptEditor)
			scriptRunning.bind(defaultScriptEditor.scriptRunning());
		else
			scriptRunning.set(false);
	}
	
	
	/**
	 * Return the global {@link OverlayOptions} instance, used to control display within viewers by default.
	 * @return
	 */
	public OverlayOptions getOverlayOptions() {
		return viewerManager == null ? null : viewerManager.getOverlayOptions();
	}

	/**
	 * Return the global {@link DefaultImageRegionStore} instance, used to cache and paint image tiles.
	 * @return
	 */
	public DefaultImageRegionStore getImageRegionStore() {
		return imageRegionStore;
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
		
	/**
	 * Refresh the title bar in the main QuPath window.
	 */
	public void refreshTitle() {
		if (Platform.isFxApplicationThread()) {
			titleBinding.invalidate();
			viewerManager.refreshTitles();
		} else
			Platform.runLater(() -> refreshTitle());
	}
		
	private StringBinding createTitleBinding() {
		return Bindings.createStringBinding(() -> createTitleFromCurrentImage(),
				projectProperty(), imageDataProperty(), PathPrefs.showImageNameInTitleProperty(), PathPrefs.maskImageNamesProperty());
	}
	
	private String createTitleFromCurrentImage() {
		String name = "QuPath";
		var versionString = getVersionString();
		if (versionString != null)
			name = name + " (" + versionString + ")";
		var imageData = imageDataProperty().get();
		if (imageData == null || !PathPrefs.showImageNameInTitleProperty().get())
			return name;
		return name + " - " + getDisplayedImageName(imageData);
	}

	/**
	 * Get the image name to display for a specified image.
	 * This can be used to determine a name to display in the title bar, for example.
	 * @param imageData
	 * @return
	 */
	public String getDisplayedImageName(ImageData<BufferedImage> imageData) {
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
		return viewerManager.imageDataProperty();
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
		for (var viewer : getAllViewers()) {
			if (viewer == null || !viewer.hasServer())
				continue;
			var imageData = viewer.getImageData();
			if (imageData != null) {
				if (!checkSaveChanges(imageData))
					return;
				viewer.resetImageData();
			}
		}
		
		// Confirm the URIs for the new project
		if (project != null) {
			try {
				// Show URI manager dialog if we have any missing URIs
				if (!ProjectCommands.promptToCheckURIs(project, true))
					return;
			} catch (IOException e) {
				Dialogs.showErrorMessage("Update URIs", "Error updating URIs\n" + e.getLocalizedMessage());
				logger.error(e.getMessage(), e);
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
		var projectBrowser = mainPaneManager.getAnalysisTabPane().getProjectBrowser();
		if (projectBrowser != null && !projectBrowser.setProject(project)) {
			this.projectProperty.set(null);
			projectBrowser.setProject(null);
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
		logger.info("Project set to {}", project);
	}
	
	
	/**
	 * Refresh the project, updating the display if required.
	 * This can be called whenever the project has changed (e.g. by adding or removing items).
	 */
	public void refreshProject() {
		if (mainPaneManager != null)
			mainPaneManager.getProjectBrowser().refreshProject();
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
	public ReadOnlyObjectProperty<QuPathViewer> viewerProperty() {
		return viewerManager.activeViewerProperty();
	}
	
	
	/**
	 * Close the image within a viewer, prompting to save changes if necessary.
	 * 
	 * @param viewer
	 * @return True if the viewer no longer contains an open image (either because it never did contain one, or 
	 * the image was successfully closed), false otherwise (e.g. if the user thwarted the close request)
	 */
	public boolean closeViewer(final QuPathViewer viewer) {
		return requestToCloseViewer(viewer, "Save changes");
	}

	/**
	 * Close the image within a viewer, prompting to save changes if necessary.
	 * 
	 * @param dialogTitle Name to use within any displayed dialog box.
	 * @param viewer
	 * @return true if the viewer no longer contains an open image (either because it never did contain one, or
	 * the image was successfully closed), false otherwise (e.g. if the user thwarted the close request)
	 */
	private boolean requestToCloseViewer(final QuPathViewer viewer, final String dialogTitle) {
		ImageData<BufferedImage> imageData = viewer.getImageData();
		if (imageData == null)
			return true;
		if (imageData.isChanged()) {
			if (!isReadOnly() && !promptToSaveChangesOrCancel(dialogTitle, imageData))
				return false;
		}
		viewer.resetImageData();
		return true;
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
		ButtonType response = ButtonType.YES;
		if (imageData.isChanged()) {
			response = Dialogs.showYesNoCancelDialog(dialogTitle, "Save changes to " + ServerTools.getDisplayableImageName(imageData.getServer()) + "?");
		}
		if (response == ButtonType.CANCEL)
			return false;
		if (response == ButtonType.YES) {
			if (filePrevious == null && entry == null) {
				filePrevious = FileChoosers.promptToSaveFile("Save image data",
						new File(ServerTools.getDisplayableImageName(imageData.getServer())),
						FileChoosers.createExtensionFilter("QuPath Serialized Data", PathPrefs.getSerializationExtension()));
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
				Dialogs.showErrorMessage("Save ImageData", "Error saving image data\n" + e.getLocalizedMessage());
				logger.error(e.getMessage(), e);
			}
		}
		return true;
	}
	
	
	/**
	 * Get the log viewer associated with this QuPath instance.
	 * @return
	 * @since v0.5.0
	 */
	public LogViewerCommand getLogViewerCommand() {
		return logViewerCommand;
	}
	
	
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
				GuiTools.showNoImageError("No image");
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
		// Add listener to the inputDisplayDialogProperty to show/hide dialog
		if (inputDisplay == null)
			inputDisplay = new InputDisplay(getStage(), Window.getWindows());
		return inputDisplay.showProperty();
	}
	
	
	/**
	 * Create an {@link Action} that depends upon an {@link QuPathViewer}.
	 * When the action is invoked, it will be passed the current {@link QuPathViewer} as a parameter.
	 * The action will also be disabled if no viewer is present.
	 * @param command the command to run
	 * @return an {@link Action} with appropriate properties set
	 */
	public Action createViewerAction(Consumer<QuPathViewer> command) {
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
	
	/**
	 * Create an {@link Action} that depends upon an {@link ImageData}.
	 * When the action is invoked, it will be passed the current {@link QuPathViewer} as a parameter.
	 * The action will also be disabled if no viewer is present.
	 * @param command the command to run
	 * @param name text of the action
	 * @return an {@link Action} with appropriate properties set
	 */
	public Action createImageDataAction(Consumer<ImageData<BufferedImage>> command, String name) {
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
				GuiTools.showNoProjectError("No project");
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
		installActionsToMenubar(actions);
	}
	
	private void installActionsToMenubar(Collection<? extends Action> actions) {
		
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
	 * Get the common actions associated with this QuPath instance.
	 * @return
	 */
	public synchronized CommonActions getCommonActions() {
		if (commonActions == null) {
			commonActions = new CommonActions(this);
			installActions(ActionTools.getAnnotatedActions(commonActions));
		}
		return commonActions;
	}

	/**
	 * Get the automated actions associated with this QuPath instance.
	 * @return
	 */
	public synchronized AutomateActions getAutomateActions() {
		if (automateActions == null) {
			automateActions = new AutomateActions(this);
			installActions(ActionTools.getAnnotatedActions(automateActions));
		}
		return automateActions;
	}
	
	
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
	
	
	private OverlayActions overlayActions;

	/**
	 * Get the actions associated with the viewer overlay options.
	 * This includes showing/hiding/filling objects, or adjusting opacity.
	 * @return
	 */
	public OverlayActions getOverlayActions() {
		if (overlayActions == null)
			overlayActions = new OverlayActions(getOverlayOptions());
		return overlayActions;
	}
	
	private ViewerActions viewerActions;

	/**
	 * Get the associations associated with QuPath image viewers.
	 * @return
	 */
	public ViewerActions getViewerActions() {
		if (viewerActions == null)
			viewerActions = new ViewerActions(getViewerManager());
		return viewerActions;
	}


	/**
	 * Legacy methods, to be removed in future versions.
	 */


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
	 * @deprecated since v0.5.0; use {@link #getThreadPoolManager()}
	 */
	@Deprecated
	public ExecutorService createSingleThreadExecutor(final Object owner) {
		LogTools.logOnce(logger, "QuPathGUI.createSingleThreadExecutor(Object) is deprecated and will be removed; " +
				"use QuPathGUI.getThreadPoolManager().getSingleThreadExecutor(owner) instead");
		return getThreadPoolManager().getSingleThreadExecutor(owner);
	}

	/**
	 * Create a completion service that uses a shared threadpool for the application.
	 * @param <V>
	 *
	 * @param cls
	 * @return
	 * @deprecated since v0.5.0; use {@link #getThreadPoolManager()}
	 */
	@Deprecated
	public <V> ExecutorCompletionService<V> createSharedPoolCompletionService(Class<V> cls) {
		LogTools.logOnce(logger, "QuPathGUI.createSharedPoolCompletionService(Class) is deprecated and will be removed; " +
				"use QuPathGUI.getThreadPoolManager().createSharedPoolCompletionService(Class) instead");
		return getThreadPoolManager().createSharedPoolCompletionService(cls);
	}

	/**
	 * Submit a short task to a shared thread pool
	 *
	 * @param runnable
	 * @deprecated since v0.5.0; use {@link #getThreadPoolManager()}
	 */
	@Deprecated
	public void submitShortTask(final Runnable runnable) {
		LogTools.logOnce(logger, "QuPathGUI.submitShortTask() is deprecated and will be removed; " +
						"use QuPathGUI.getThreadPoolManager().submitShortTask() instead");
		getThreadPoolManager().submitShortTask(runnable);
	}

}