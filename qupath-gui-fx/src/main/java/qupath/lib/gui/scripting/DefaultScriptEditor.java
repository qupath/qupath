/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.scripting;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.apache.commons.text.StringEscapeUtils;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.imagej.tools.IJTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.logging.TextAppendable;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.scripting.QP;


/**
 * 
 * Default multilingual script editor.
 * <p>
 * Lacks syntax highlighting and other pleasant features, unfortunately.
 * <p>
 * Warning: This is likely to be replaced by a monolingual editor, supporting Groovy only.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultScriptEditor implements ScriptEditor {

	final static private Logger logger = LoggerFactory.getLogger(DefaultScriptEditor.class);
	
	/**
	 * Enum representing languages supported by this script editor.
	 * <p>
	 * Warning: This is likely to be removed in the future, in favor of stronger support for Groovy only.
	 */
	public enum Language {
		/**
		 * Javascript support (will cease to be part of the JDK)
		 */
		@Deprecated
		JAVASCRIPT("JavaScript", ".js", "//"),
		/**
		 * Jython support
		 */
		@Deprecated
		JYTHON("Jython", ".py", "#"),
		/**
		 * Groovy support (default and preferred scripting language for QuPath)
		 */
		GROOVY("Groovy", ".groovy", "//");
		
		private final String name;
		private final String ext;
		private final String lineComment;
		
		Language(final String name, final String ext, final String lineComment) {
			this.name = name;
			this.ext = ext;
			this.lineComment = lineComment;
		}
		
		/**
		 * Get the file extension for the specified language
		 * @return
		 */
		public String getExtension() {
			return ext;
		}
		
		/**
		 * Get the string used to indicate a line comment, e.g. # for Python or // for Java
		 * @return
		 */
		public String getLineCommentString() {
			return lineComment;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
	}
	
	

//	private static final List<String> SUPPORTED_LANGUAGES = Collections.unmodifiableList(
//			Arrays.asList("JavaScript", "Jython", "Groovy", "Ruby"));
//	final private static Language DEFAULT_LANGUAGE = Language.JAVASCRIPT;
	final private static String NO_LANGUAGE = "None";
	
	final private static String[] SCRIPT_EXTENSIONS = new String[]{"js", "py", "groovy", "rb", "txt"};
	
	private static final List<Language> availableLanguages = new ArrayList<>();
	
	private static int untitledCounter = 0; // For incrementing untitled scripts
	
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
	
	private QuPathGUI qupath;
	private Stage dialog;
	private SplitPane splitMain;
	private ToggleGroup bgLanguages;
	private Font fontMain = Font.font("Courier");
	
	private ObjectProperty<ScriptTab> selectedScript = new SimpleObjectProperty<>();
	
	private StringBinding currentLanguage = javafx.beans.binding.Bindings.createStringBinding(
			() -> {
				if (selectedScript.get() == null || selectedScript.get().getLanguage() == null)
					return null;
				return selectedScript.get().getLanguage().toString();
			},
			selectedScript);
	
	// Binding to indicate it shouldn't be possible to 'Run' any script right now
	private BooleanBinding disableRun = runningTask.isNotNull().or(currentLanguage.isNull());
	
	// Binding to indicate it shouldn't be possible to 'Run' any script right now
	private StringBinding title = Bindings.createStringBinding(() -> {
		if (runningTask.get() == null)
			return "Script Editor";
		else
			return "Script Editor (Running)";
	}, runningTask);
	
	// Accelerators that have been assigned to actions
	private Collection<KeyCombination> accelerators = new HashSet<>();
	
	// Keyboard accelerators
	protected KeyCombination comboPasteEscape = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
//	protected KeyCombination comboPaste = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
//	protected KeyCombination comboCopy = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
	
	// Note: it doesn't seem to work to set the accelerators...
	// this leads to the actions being called twice, due to the built-in behaviour of TextAreas
	protected Action copyAction;
	protected Action cutAction;
	protected Action pasteAction;
	protected Action pasteAndEscapeAction;
	protected Action undoAction;
	protected Action redoAction;
	
	protected Action runScriptAction;
	protected Action runSelectedAction;
	protected Action runProjectScriptAction;
	protected Action runProjectScriptNoSaveAction;

	protected Action insertMuAction;
	protected Action insertQPImportAction;
	protected Action insertQPExImportAction;
	protected Action insertAllDefaultImportAction;
	protected Action insertPixelClassifiersAction;
	protected Action insertObjectClassifiersAction;
	protected Action insertDetectionMeasurementsAction;
	
	protected Action findAction;

	private String tabString = "    "; // String to insert when tab key pressed

	// Add default bindings, i.e. QuPathGUI, Viewer, ImageData... makes scripting much easier
	private BooleanProperty useDefaultBindings = PathPrefs.createPersistentPreference("scriptingUseDefaultBindings", true);
	private BooleanProperty autoRefreshFiles = PathPrefs.createPersistentPreference("scriptingAutoRefreshFiles", true);
	private BooleanProperty sendLogToConsole = PathPrefs.createPersistentPreference("scriptingSendLogToConsole", true);
	private BooleanProperty outputScriptStartTime = PathPrefs.createPersistentPreference("scriptingOutputScriptStartTime", false);
	private BooleanProperty autoClearConsole = PathPrefs.createPersistentPreference("scriptingAutoClearConsole", true);
	private BooleanProperty clearCache = PathPrefs.createPersistentPreference("scriptingClearCache", false);
	

	// Regex pattern used to identify whether a script should be run in the JavaFX Platform thread
	// If so, this line should be included at the top of the script
	private static Pattern patternGuiScript = Pattern.compile("guiscript *?= *?true");
	
	private static ScriptEngineManager manager = createManager();
	
	private ListView<ScriptTab> listScripts = new ListView<>();
	
	/**
	 * Create a map of classes that have changed, and therefore old scripts may use out-of-date import statements.
	 * This allows us to be a bit more helpful in handling the error message.
	 */
	private static Map<String, Class<?>> CONFUSED_CLASSES;
	
	static {	
		CONFUSED_CLASSES = new HashMap<>();
		for (Class<?> cls : QP.getCoreClasses()) {
			CONFUSED_CLASSES.put(cls.getSimpleName(), cls);
		}
		CONFUSED_CLASSES.put("PathRoiToolsAwt", RoiTools.class);
		CONFUSED_CLASSES.put("PathDetectionObject", PathObjects.class);
		CONFUSED_CLASSES.put("PathAnnotationObject", PathObjects.class);
		CONFUSED_CLASSES.put("PathCellObject", PathObjects.class);
		CONFUSED_CLASSES.put("RoiConverterIJ", IJTools.class);
		CONFUSED_CLASSES.put("QP", QP.class);
		CONFUSED_CLASSES.put("QPEx", QPEx.class);
		CONFUSED_CLASSES.put("ShapeSimplifierAwt", ShapeSimplifier.class);
		CONFUSED_CLASSES.put("ImagePlusServerBuilder", IJTools.class);
		CONFUSED_CLASSES.put("DisplayHelpers", Dialogs.class);
		CONFUSED_CLASSES = Collections.unmodifiableMap(CONFUSED_CLASSES);
	}

	/**
	 * Constructor.
	 * @param qupath current QuPath instance.
	 */
	public DefaultScriptEditor(final QuPathGUI qupath) {
		this.qupath = qupath;
		initializeActions();
//		createDialog();
	}
	
	
	private void initializeActions() {
		copyAction = createCopyAction("Copy", null);
		cutAction = createCutAction("Cut", null);
		pasteAction = createPasteAction("Paste", false, null);
		pasteAndEscapeAction = createPasteAction("Paste & escape", true, comboPasteEscape);
		undoAction = createUndoAction("Undo", null);
		redoAction = createRedoAction("Redo", null);
		
		runScriptAction = createRunScriptAction("Run", false);
		runSelectedAction = createRunScriptAction("Run selected", true);
		runProjectScriptAction = createRunProjectScriptAction("Run for project", true);
		runProjectScriptNoSaveAction = createRunProjectScriptAction("Run for project (without save)", false);
		
		insertMuAction = createInsertAction(GeneralTools.SYMBOL_MU + "");
		insertQPImportAction = createInsertAction("QP");
		insertQPExImportAction = createInsertAction("QPEx");
		insertAllDefaultImportAction = createInsertAction("All default");
		insertPixelClassifiersAction = createInsertAction("Pixel classifiers");
		insertObjectClassifiersAction = createInsertAction("Object classifiers");
		insertDetectionMeasurementsAction = createInsertAction("Detection");
		
		qupath.projectProperty().addListener((v, o, n) -> {
			previousImages.clear();
		});
		
		findAction = createFindAction("Find");
	}
	
	/**
	 * Query whether a file represents a supported script.
	 * Currently, this test looks at the file extension only.
	 * @param file the file to test
	 * @return true if the file is likely to contain a supported script, false otherwise
	 */
	public boolean supportsFile(final File file) {
		if (file == null || !file.isFile())
			return false;
		String name = file.getName();
		for (String ext : SCRIPT_EXTENSIONS) {
			if (name.endsWith(ext))
				return true;
		}
		return false;
	}
	


	void maybeRefreshTab(final ScriptTab tab) {
		if (tab != null && autoRefreshFiles.get())
			tab.refreshFileContents();
	}
	
	/**
	 * Get the stage for this script editor.
	 * @return
	 */
	public Stage getStage() {
		return dialog;
	}
	
	/**
	 * Observable value indicating whether a script is currently running or not.
	 * This can be used (for example) to determine whether a user action should be blocked until the script has completed.
	 * @return
	 */
	public ObservableValue<Boolean> scriptRunning() {
		return runningTask.isNotNull();
	}
	
	
	/**
	 * Create a new script in the specified language.
	 * @param script text of the script to add
	 * @param language language of the script
	 * @param doSelect if true, select the script when it is added
	 */
	public void addNewScript(final String script, final Language language, final boolean doSelect) {
		ScriptTab tab = new ScriptTab(script, language);
		listScripts.getItems().add(tab);
		if (doSelect)
			listScripts.getSelectionModel().select(tab);
		updateSelectedScript();
	}
	
	private void addScript(final File file, final boolean doSelect) throws IOException {
		// Try to select an existing tab, if possible
		for (ScriptTab tab : listScripts.getItems()) {
			if (file.equals(tab.getFile())) {
				if (doSelect)
					listScripts.getSelectionModel().select(tab);
				return;
			}
		}
		
		ScriptTab tab = new ScriptTab(file);
		listScripts.getItems().add(tab);
		if (doSelect)
			listScripts.getSelectionModel().select(tab);
	}
	
	
	void updateSelectedScript() {
		ScriptTab tab = listScripts == null ? null : listScripts.getSelectionModel().getSelectedItem();
		if (tab == selectedScript.get())
			return;
		
		Node lastComponent = splitMain.getItems().get(1);
		Node newComponent = tab == null ? null : tab.getComponent();
		if (lastComponent == newComponent)
			return;
		
		double loc = splitMain.getDividers().get(0).getPosition();
		splitMain.getItems().set(1, newComponent);
		if (tab != null) {
			maybeRefreshTab(tab);
			splitMain.setDividerPosition(0, loc);
			// Unfortunately need to wait until divider is present before we can set the divider location
			if (selectedScript.get() == null)
				tab.splitEditor.setDividerPosition(0, 0.75);
			else
				tab.splitEditor.setDividerPosition(0, selectedScript.get().splitEditor.getDividers().get(0).getPosition());
			
			// Update the selected language
			Language language = tab.getLanguage();
			String languageName = language == null ? NO_LANGUAGE : language.toString();
			for (Toggle button : bgLanguages.getToggles()) {
				if (languageName.equals(button.getUserData())) {
					bgLanguages.selectToggle(button);
					break;
				}
			}
		}
		
		selectedScript.set(tab);
	}


	private static ScriptEngineManager createManager() {
		Thread.currentThread().setContextClassLoader(QuPathGUI.getExtensionClassLoader());
		ScriptEngineManager manager = new ScriptEngineManager(QuPathGUI.getExtensionClassLoader());
		//		availableLanguages.add(Language.JAVA);
		for (ScriptEngineFactory factory : manager.getEngineFactories()) {
			for (Language supported : Language.values()) {
				if (factory.getNames().contains(supported.toString().toLowerCase())) {
					availableLanguages.add(supported);
					manager.registerEngineName(supported.toString(), factory);
					//					factories.add(factory);

					logger.trace("-------------------------------");
					logger.trace(factory.getLanguageName());
					logger.trace(factory.getLanguageVersion());
					logger.trace(factory.getEngineName());
					logger.trace(factory.getEngineVersion());
					logger.trace("Names: {}", factory.getNames());
					logger.trace("MIME types: {}", factory.getMimeTypes().toString());
					logger.trace("Extensions: {}", factory.getExtensions().toString());

					logger.trace(factory.getMethodCallSyntax("QuPath", "runPlugin", "imageData", "\"{ key : value }\""));
					logger.trace(factory.getOutputStatement("This is my output"));

					break;
				}
			}
		}
		Collections.sort(availableLanguages);
		return manager;
	}


	Language getSelectedLanguage() {
		return getCurrentScriptTab() == null ? null : getCurrentScriptTab().getLanguage();
	}

	protected String getCurrentLineCommentString() {
		Language language = getSelectedLanguage();
		return language == null ? null : language.getLineCommentString();
	}
	
	protected ScriptEditorControl getNewConsole() {
		TextArea consoleArea = new TextArea();
		consoleArea.setEditable(false);
		return new ScriptEditorTextArea(consoleArea);
	}

	protected ScriptEditorControl getNewEditor() {
		TextArea editor = new CustomTextArea();
		editor.setWrapText(false);
		editor.setFont(fontMain);
		
		ScriptEditorTextArea control = new ScriptEditorTextArea(editor);
		editor.addEventFilter(KeyEvent.KEY_PRESSED, (KeyEvent e) -> {
	        if (e.getCode() == KeyCode.TAB) {
	        	handleTabPress(control, e.isShiftDown());
	        	e.consume();
	        } else if (e.isShortcutDown() && e.getCode() == KeyCode.SLASH) {
	        	handleLineComment(control);
	        	e.consume();
	        } else if (e.getCode() == KeyCode.ENTER && control.getSelectedText().length() == 0) {
				handleNewLine(control);
				e.consume();
			} 
	    });

//		editor.getDocument().addUndoableEditListener(new UndoManager());
//		// Handle tabs
//		editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "tab");
//		editor.getActionMap().put("tab", new TabIndenter(editor, false));
//		editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK), "shiftTab");
//		editor.getActionMap().put("shiftTab", new TabIndenter(editor, true));
		return control;
	}
	
	
	private void createDialog() {
//		if (dialog != null)
//			return;

		dialog = new Stage();
//		dialog.setOnCloseRequest(e -> attemptToQuitScriptEditor());
		if (qupath != null)
			dialog.initOwner(qupath.getStage());
		dialog.titleProperty().bind(title);
		
		MenuBar menubar = new MenuBar();

		// File menu
		Menu menuFile = new Menu("File");
		MenuTools.addMenuItems(
				menuFile,
				createNewAction("New"),
				createOpenAction("Open..."),
				null,
				createSaveAction("Save", false),
				createSaveAction("Save As...", true),
				null,
				createRevertAction("Revert/Refresh"),
				ActionTools.createCheckMenuItem(ActionTools.createSelectableAction(autoRefreshFiles, "Auto refresh files")),
				null,
				createCloseAction("Close script")
//				null,
//				createExitAction("Exit") // Exit actually dramatically quits the entire application...
				);
		
		
		menubar.getMenus().add(menuFile);
		

		// Edit menu
		Menu menuEdit = new Menu("Edit");
		MenuTools.addMenuItems(
				menuEdit,
				undoAction,
				redoAction,
				null,
				cutAction,
				copyAction,
				pasteAction,
				pasteAndEscapeAction,
				null,
				findAction
				);
//		menuEdit.setMnemonic(KeyEvent.VK_E);
//
//		menuEdit.add(undoAction);
//		menuEdit.add(redoAction);
//		menuEdit.addSeparator();
//		
//		menuItem = new MenuItem(cutAction);
//		menuItem.setText("Cut");
//		menuItem.setMnemonic(KeyEvent.VK_T);
//		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, SHORTCUT_MASK));
//		menuEdit.add(menuItem);
//		menuItem = new MenuItem(copyAction);
//		menuItem.setText("Copy");
//		menuItem.setMnemonic(KeyEvent.VK_C);
//		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, SHORTCUT_MASK));
//		menuEdit.add(menuItem);
//		menuItem = new MenuItem(pasteAction);
//		menuItem.setText("Paste");
//		menuItem.setMnemonic(KeyEvent.VK_P);
//		menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, SHORTCUT_MASK));
//		menuEdit.add(menuItem);
		menubar.getMenus().add(menuEdit);

		// Languages menu - ensure each language only gets added once
		Menu menuLanguages = new Menu("Language");
		bgLanguages = new ToggleGroup();
		RadioMenuItem radioMenuItem;
		for (Language language : new LinkedHashSet<>(availableLanguages)) {
			String languageName = language.toString();
			radioMenuItem = new RadioMenuItem(languageName);
			radioMenuItem.setToggleGroup(bgLanguages);
			radioMenuItem.setUserData(languageName);
			menuLanguages.getItems().add(radioMenuItem);
			radioMenuItem.setOnAction(e -> switchLanguage(languageName));
		}
		menuLanguages.getItems().add(new SeparatorMenuItem());
		radioMenuItem = new RadioMenuItem(NO_LANGUAGE);
		radioMenuItem.setToggleGroup(bgLanguages);
		radioMenuItem.setUserData(NO_LANGUAGE);
		bgLanguages.selectToggle(radioMenuItem);
		radioMenuItem.setOnAction(e -> switchLanguage(NO_LANGUAGE));
		menuLanguages.getItems().add(radioMenuItem);
		
		menubar.getMenus().add(menuLanguages);
		
		// Insert menu
		Menu menuInsert = new Menu("Insert");
		Menu subMenuSymbols = new Menu("Symbols");
		Menu subMenuImports = new Menu("Imports");
		Menu subMenuClassifiers = new Menu("Classifiers");
		Menu subMenuMeasurements = new Menu("Measurements");
		MenuTools.addMenuItems(
			menuInsert,
			MenuTools.addMenuItems(
				subMenuSymbols,
				insertMuAction
				),
			MenuTools.addMenuItems(
				subMenuImports,
				insertQPImportAction,
				insertQPExImportAction,
				insertAllDefaultImportAction
				),
			MenuTools.addMenuItems(
				subMenuClassifiers,
				insertPixelClassifiersAction,
				insertObjectClassifiersAction
				),
			MenuTools.addMenuItems(
				subMenuMeasurements,
				insertDetectionMeasurementsAction
				)
		);
		menubar.getMenus().add(menuInsert);

		// Run menu
		Menu menuRun = new Menu("Run");
		MenuTools.addMenuItems(
				menuRun,
				runScriptAction,
				runSelectedAction,
				runProjectScriptAction,
				runProjectScriptNoSaveAction,
				null,
				createKillRunningScriptAction("Kill running script"),
				null,
				ActionTools.createCheckMenuItem(ActionTools.createSelectableAction(useDefaultBindings, "Include default imports")),
				ActionTools.createCheckMenuItem(ActionTools.createSelectableAction(sendLogToConsole, "Show log in console")),
				ActionTools.createCheckMenuItem(ActionTools.createSelectableAction(outputScriptStartTime, "Log script time")),
				ActionTools.createCheckMenuItem(ActionTools.createSelectableAction(autoClearConsole, "Auto clear console")),
				ActionTools.createCheckMenuItem(ActionTools.createSelectableAction(clearCache, "Clear cache (batch processing)"))
				);
		menubar.getMenus().add(menuRun);

		// File list
		BorderPane panelList = new BorderPane();
//		label.setFont(label.getFont().deriveFont(12f));
		TitledPane titledScripts = new TitledPane("Scripts", listScripts);
		titledScripts.prefWidthProperty().bind(panelList.widthProperty());
		titledScripts.prefHeightProperty().bind(panelList.heightProperty());
		titledScripts.setCollapsible(false);
		panelList.setCenter(titledScripts);
		listScripts.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateSelectedScript());
		listScripts.setCellFactory(new Callback<ListView<ScriptTab>, 
	            ListCell<ScriptTab>>() {
	                @Override 
	                public ListCell<ScriptTab> call(ListView<ScriptTab> list) {
	                    return new ScriptTabListCell();
	                }
	            }
	        );
		listScripts.setMinWidth(150);
		runningTask.addListener((v, o, n) -> listScripts.refresh());

		splitMain = new SplitPane();
		splitMain.getItems().addAll(panelList, getCurrentScriptComponent());
		splitMain.setOrientation(Orientation.HORIZONTAL);
		SplitPane.setResizableWithParent(panelList, Boolean.FALSE);
//		splitMain.setResizeWeight(0);
//		splitMain.setOneTouchExpandable(true);
		BorderPane pane = new BorderPane();
		pane.setCenter(splitMain);
		pane.setTop(menubar);
		dialog.setScene(new Scene(pane));

		dialog.setMinWidth(400);
		dialog.setMinHeight(400);
		dialog.setWidth(600);
		dialog.setHeight(400);
		splitMain.setDividerPosition(0, 0.25);
		menubar.useSystemMenuBarProperty().bindBidirectional(PathPrefs.useSystemMenubarProperty());
//		menubar.setUseSystemMenuBar(true);
		updateUndoActionState();
		updateCutCopyActionState();
		
		
		// Support drag & drop
		if (qupath != null && qupath.getDefaultDragDropListener() != null)
			qupath.getDefaultDragDropListener().setupTarget(dialog.getScene());
	}
	
	
	void switchLanguage(final String languageName) {
		ScriptTab tab = getCurrentScriptTab();
		if (tab == null)
			return;
		if (NO_LANGUAGE.equals(languageName))
			tab.setLanguage(null);
		else {
			for (Language l : Language.values()) {
				if (l.toString().equals(languageName)) {
					tab.setLanguage(l);
					break;
				}
			}
		}
	}
	
	
	
	protected Language getCurrentLanguage() {
		ScriptTab tab = getCurrentScriptTab();
		return tab == null ? null : tab.getLanguage();
	}
	
	protected ScriptTab getCurrentScriptTab() {
		return selectedScript.get();
//		return listScripts == null ? null : listScripts.getSelectionModel().getSelectedItem();
	}
	
	protected ScriptEditorControl getCurrentTextComponent() {
		ScriptTab tab = getCurrentScriptTab();
		return tab == null ? null : tab.getEditorComponent();
	}
	
	
	protected ScriptEditorControl getCurrentConsoleComponent() {
		ScriptTab tab = getCurrentScriptTab();
		return tab == null ? null : tab.getConsoleComponent();
	}

	protected Node getCurrentScriptComponent() {
		ScriptTab tab = getCurrentScriptTab();
		return tab == null ? null : tab.getComponent();
	}
	
	protected String getSelectedText() {
		ScriptEditorControl comp = getCurrentTextComponent();
		return comp != null ? comp.getSelectedText() : null;
	}
	
	protected String getCurrentText() {
		ScriptEditorControl comp = getCurrentTextComponent();
		return comp != null ? comp.getText() : "";
	}
	
	void updateCutCopyActionState() {
		String selectedText = getSelectedText();
		copyAction.setDisabled(selectedText == null || selectedText.isEmpty());
		cutAction.setDisabled(selectedText == null || selectedText.isEmpty());
		pasteAction.setDisabled(false);
//		pasteAction.setDisabled(!Clipboard.getSystemClipboard().hasString());
	}
	
	
	void updateUndoActionState() {
		ScriptEditorControl editor = getCurrentTextComponent();
		undoAction.setDisabled(editor == null || !editor.isUndoable());
		redoAction.setDisabled(editor == null || !editor.isRedoable());
	}
	
	
	/**
	 * Execute the script currently shown in the specified ScriptTab.
	 * 
	 * Output will be shown in the console of the ScriptTab.
	 * 
	 * @param tab
	 * @param script
	 * @param imageData
	 */
	private void executeScript(final ScriptTab tab, final String script, final Project<BufferedImage> project, final ImageData<BufferedImage> imageData) {
		ScriptEditorControl console = tab.getConsoleComponent();
		
		ScriptContext context = new SimpleScriptContext();
		context.setAttribute("args", new String[0], ScriptContext.ENGINE_SCOPE);
		var writer = new ScriptConsoleWriter(console, false);
		context.setWriter(writer);
		context.setErrorWriter(new ScriptConsoleWriter(console, true));
		var printWriter = new PrintWriter(writer);
		
		boolean attachToLog = sendLogToConsole.get();
		if (attachToLog)
			LogManager.addTextAppendableFX(console);
		long startTime = System.currentTimeMillis();
		if (outputScriptStartTime.get())
			printWriter.println("Starting script at " + new Date(startTime).toString());
		try {
			Object result = executeScript(tab.getLanguage(), script, project, imageData, useDefaultBindings.get(), context);
			if (result != null) {
				printWriter.println("Result: " + result);
			}
			if (outputScriptStartTime.get())
				printWriter.println(String.format("Script run time: %.2f seconds", (System.currentTimeMillis() - startTime)/1000.0));
		} catch (ScriptException e) {
			// TODO: Consider exception logging here, rather than via the called method
		} finally {
			if (attachToLog)
				Platform.runLater(() -> LogManager.removeTextAppendableFX(console));	
		}
	}

	
	
	private static ScriptContext createDefaultContext() {
		ScriptContext context = new SimpleScriptContext();
		context.setAttribute("args", new String[0], ScriptContext.ENGINE_SCOPE);
		context.setWriter(new LoggerInfoWriter());
		context.setErrorWriter(new LoggerErrorWriter());
		return context;
	}
	
	
	private static abstract class LoggerWriter extends Writer {

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			// Don't need to log newlines
			if (len == 1 && cbuf[off] == '\n')
				return;
			String s = String.valueOf(cbuf, off, len);
			// Skip newlines on Windows too...
			if (s.equals(System.lineSeparator()))
				return;
			log(s);
		}
		
		protected abstract void log(String s);

		@Override
		public void flush() throws IOException {}
		
		@Override
		public void close() throws IOException {
			flush();
		}
	}
	
	private static class LoggerInfoWriter extends LoggerWriter {

		@Override
		protected void log(String s) {
			logger.info(s);
		}
	}
	
	private static class LoggerErrorWriter extends LoggerWriter {

		@Override
		protected void log(String s) {
			logger.error(s);
		}
	}
	
	
	/**
	 * Execute a script using an appropriate ScriptEngine for a specified scripting language.
	 * 
	 * @param language
	 * @param script
	 * @param project 
	 * @param imageData
	 * @param importDefaultMethods
	 * @param context
	 * @return
	 * @throws ScriptException 
	 */
	public static Object executeScript(final Language language, final String script, final Project<BufferedImage> project, final ImageData<BufferedImage> imageData, final boolean importDefaultMethods, final ScriptContext context) throws ScriptException {
		ScriptEngine engine = manager.getEngineByName(language.toString());
		return executeScript(engine, script, project, imageData, importDefaultMethods, context);
	}
	
	
	/**
	 * Execute a script using the specific ScriptEngine.
	 * 
	 * @param engine
	 * @param script
	 * @param project 
	 * @param imageData
	 * @param importDefaultMethods
	 * @param context
	 * @return
	 * @throws ScriptException 
	 */
	public static Object executeScript(final ScriptEngine engine, final String script, final Project<BufferedImage> project, final ImageData<BufferedImage> imageData, final boolean importDefaultMethods, final ScriptContext context) throws ScriptException {
		
		// Set the current ImageData if we can
		QP.setBatchProjectAndImage(project, imageData);
		
		// We'll actually use script2... which may or may not be the same
		String script2 = script;
		
		// Prepare to return a result
		Object result = null;

		// Record if any extra lines are added to the script, to help match line numbers of any exceptions
		int extraLines = 0;

		// Supply default bindings
		if (importDefaultMethods) {
			
			// Class supplying static methods that will be included in the main namespace
			// TODO: Note: Javascript ignores the 'extends', i.e. loses all the QPEx stuff, so most functions don't work.
			// This workaround means that command line script running is used with Javascript, whereas Groovy shows progress dialogs etc.
			String scriptClass = engine.getFactory().getNames().contains("javascript") ? QP.class.getName() : QPEx.class.getName();
			
			// Import whatever else is needed into the namespace for the languages we know about
			if (engine.getFactory().getNames().contains("jython")) {
				script2 = String.format(
						"import qupath\n" +
						"from %s import *\n" +
						"%s\n",
						scriptClass, script);
				extraLines = 2;
			}
			if (engine.getFactory().getNames().contains("groovy")) {
				script2 = QPEx.getDefaultImports(true) + System.lineSeparator() + script;
//				script2 = String.format(
//						"import static %s.*;\n" + 
//						"%s\n",
//						scriptClass, script);
				extraLines = 1; // coreImports.size() + 1;
			}
			if (engine.getFactory().getNames().contains("javascript")) {
				script2 = String.format(
						"var QP = Java.type(\"%s\");\n"
						+ "with (Object.bindProperties({}, QP)) {\n"
						+ "%s\n"
						+ "}\n",
						scriptClass, script);
				extraLines = 2;
			}
			
		}
		
		try {
			result = engine.eval(script2, context == null ? createDefaultContext() : context);
		} catch (ScriptException e) {
			try {
				int line = e.getLineNumber();
				Throwable cause = e;
				// Try to get to the root of the problem
				while (cause.getCause() != null && cause.getCause() != cause)
					cause = cause.getCause();
				
				// Sometimes we can still get the line number for a Groovy exception in this awkward way...
				if (line < 0) {
					for (StackTraceElement element : cause.getStackTrace()) {
						if ("run".equals(element.getMethodName()) && element.getClassName() != null && element.getClassName().startsWith("Script")) {
							line = element.getLineNumber();
							break;
						}
					}
				}
				
				Writer errorWriter = context.getErrorWriter();
				
				StringBuilder sb = new StringBuilder();
				String message = cause.getLocalizedMessage();
				if (message != null && line < 0) {
					var lineMatcher = Pattern.compile("@ line ([\\d]+)").matcher(message);
					if (lineMatcher.find())
						line = Integer.parseInt(lineMatcher.group(1));
				}
				
				// Check if the error was to do with an import statement
				if (message != null && !message.isBlank()) {
					var matcher = Pattern.compile("unable to resolve class ([A-Za-z_.-]+)").matcher(message);
					if (matcher.find()) {
						String missingClass = matcher.group(1).strip();
						sb.append("It looks like you have tried to import a class '" + missingClass + "' that doesn't exist!\n");
						int ind = missingClass.lastIndexOf(".");
						if (ind >= 0)
							missingClass = missingClass.substring(ind+1);
						Class<?> suggestedClass = CONFUSED_CLASSES.get(missingClass);
						if (suggestedClass != null) {
							sb.append("You should probably remove the broken import statement in your script (around line " + line + ").\n");
							sb.append("Then you may want to check 'Run -> Include default imports' is selected, or alternatively add ");
							sb.append("\n    import " + suggestedClass.getName() + "\nat the start of the script. Full error message below.\n");
						}
					}
	
					// Check if the error was to do with a missing property... which can again be thanks to an import statement
					var matcherProperty = Pattern.compile("No such property: ([A-Za-z_.-]+)").matcher(message);
					if (matcherProperty.find()) {
						String missingClass = matcherProperty.group(1).strip();
						sb.append("I cannot find '" + missingClass + "'!\n");
						int ind = missingClass.lastIndexOf(".");
						if (ind >= 0)
							missingClass = missingClass.substring(ind+1);
						Class<?> suggestedClass = CONFUSED_CLASSES.get(missingClass);
						if (suggestedClass != null) {
							if (!suggestedClass.getSimpleName().equals(missingClass)) {
								sb.append("You can try replacing ").append(missingClass).append(" with ").append(suggestedClass.getSimpleName()).append("\n");
							}
							sb.append("You might want to check 'Run -> Include default imports' is selected, or alternatively add ");
							sb.append("\n    import " + suggestedClass.getName() + "\nat the start of the script. Full error message below.\n");
						}
					}
					
					// Check if the error was to do with a special left quote character
					var matcherQuotationMarks = Pattern.compile("Unexpected input: .*([\\x{2018}|\\x{201c}|\\x{2019}|\\x{201D}]+)' @ line (\\d+), column (\\d+).").matcher(message);
					if (matcherQuotationMarks.find()) {
						int nLine = Integer.parseInt(matcherQuotationMarks.group(2));
						String quotationMark = matcherQuotationMarks.group(1);
						String suggestion = quotationMark.equals("‘") || quotationMark.equals("’") ? "'" : "\"";
						sb.append(String.format("At least one invalid quotation mark (%s) was found @ line %s column %s! ", quotationMark, importDefaultMethods ? nLine-1 : nLine, matcherQuotationMarks.group(3)));
						sb.append(String.format("You can try replacing it with a straight quotation mark (%s).%n", suggestion));
					}
				}
				if (sb.length() > 0)
					errorWriter.append(sb.toString());
				
				if (line >= 0) {
					line = line - extraLines;
					if (cause instanceof InterruptedException)
						errorWriter.append("Script interrupted at line " + line + ": " + message + "\n");
					else
						errorWriter.append(cause.getClass().getSimpleName() + " at line " + line + ": " + message + "\n");
				} else {
					if (cause instanceof InterruptedException)
						errorWriter.append("Script interrupted: " + message + "\n");
					else
						errorWriter.append(cause.getClass().getSimpleName() + ": " + message + "\n");
				}
				var stackTrace = Arrays.stream(cause.getStackTrace()).filter(s -> s != null).map(s -> s.toString())
						.collect(Collectors.joining("\n" + "    "));
				if (stackTrace != null)
					stackTrace += "\n";
				errorWriter.append(stackTrace);
//				logger.error("Script error (" + cause.getClass().getSimpleName() + ")", cause);
			} catch (IOException e1) {
				logger.error("Script IO error: {}", e1);
			} catch (Exception e1) {
				logger.error("Script error: {}", e1.getLocalizedMessage(), e1);
//				e1.printStackTrace();
			}
			throw e;
		} finally {
			QP.resetBatchProjectAndImage();
		}
		return result;
	}

	
	
	
	static class ScriptTabListCell extends ListCell<ScriptTab> {
        @Override
        public void updateItem(ScriptTab item, boolean empty) {
            super.updateItem(item, empty);
            if (item == null || empty) {
            	setText(null);
            	setTooltip(null);
             	return;
            }
            var text = item.toString();
            if (item.isRunning) {
            	text = text + " (Running)";
            	setStyle("-fx-font-style: italic;");
            } else
            	setStyle(null);
            setText(text);
            setTooltip(new Tooltip(text));
//            this.setOpacity(0);
        }
    }
	
	
	
	boolean save(final ScriptTab tab, final boolean saveAs) {
		try {
			if (tab.fileExists() && !saveAs)
				tab.saveToFile(tab.getFile());
			else {
				File dir = tab.getFile();
//				if (dir == null) {
//					dir = qupath.getProjectScriptsDirectory(true);
//				}
				// Elaborate attempt to use scripts directory as default
				if (dir == null) {
					try {
						var project = qupath.getProject();
						if (project != null) {
							File dirProject = Projects.getBaseDirectory(project);
							if (dirProject != null && dirProject.isDirectory()) {
								File dirScripts = new File(dirProject, "scripts");
								if (!dirScripts.exists()) {
									try {
										dirScripts.mkdir();
									} catch (Exception e) {
										logger.error("Unable to make script directory: " + e.getLocalizedMessage(), e);
									}
								}
								if (dirScripts.isDirectory())
									dir = dirScripts;
							}
						}
					} catch (Exception e) {
						logger.warn("Problem trying to find project scripts directory: {}", e.getLocalizedMessage());
					}
				}
				File file = Dialogs.getChooser(dialog).promptToSaveFile("Save script file", dir, tab.getName(), "Script file", tab.getRequestedExtension());
				if (file == null)
					return false;
				tab.saveToFile(file);
				listScripts.refresh();
//				listScripts.getItems().set(tab, modelScripts.indexOf(tab)); // Force a model update
//				listScripts.repaint();
				return true;
			}
		} catch (Exception e) {
			logger.error("Error saving file", e);
			e.printStackTrace();
		}
		return false;
	}
	
	
	
	boolean promptToClose(final ScriptTab tab) {
		int ind = listScripts.getItems().indexOf(tab);
		if (ind < 0)
			return false;
		
		// Check if we need to save
		if (tab.isModified() && tab.hasScript()) {
			// TODO: Consider that this previously had a different parent for the dialog... and probably should
			DialogButton option = Dialogs.showYesNoCancelDialog("Close " + tab.getName(), String.format("Save %s before closing?", tab.getName()));
			if (option == DialogButton.CANCEL)
				return false;
			if (option == DialogButton.YES) {
				if (!save(tab, false))
					return false;
			}
		}

		// Update selection, or close window if all scripts have been closed
		listScripts.getItems().remove(ind);
		if (ind >= listScripts.getItems().size())
			ind--;
		if (ind < 0) {
			dialog.close();
			dialog = null;
		}
		else
			listScripts.getSelectionModel().select(ind);
		return true;
	}
	



//	public static void main(String[] args) {
//		Platform.runLater(() -> {
//			DefaultScriptEditor editor = new DefaultScriptEditor(null);
//			editor.getDialog().show();			
//		});
//	}



	/**
	 * Writer for outputting either to a logger or a styled document.
	 */
	class ScriptConsoleWriter extends Writer {

		private ScriptEditorControl doc;
		private boolean isErrorWriter = false;
//		private int flushCount = 0;
		private StringBuilder sb = new StringBuilder();

		ScriptConsoleWriter(final ScriptEditorControl doc, final boolean isErrorWriter) {
			super();
			this.doc = doc;
			this.isErrorWriter = isErrorWriter;
//			attributes = LoggingAppender.getAttributeSet(isErrorWriter);
		}

		@Override
		public synchronized void write(char[] cbuf, int off, int len) throws IOException {
			// If we aren't showing the log in the console, we need to handle each message
			if (!sendLogToConsole.get()) {
				String s = String.valueOf(cbuf, off, len);
				sb.append(s);
				flush();
			}
			// Don't need to log newlines
			if (len == 1 && cbuf[off] == '\n')
				return;
			String s = String.valueOf(cbuf, off, len);
			// Skip newlines on Windows too...
			if (s.equals(System.lineSeparator()))
				return;
			if (isErrorWriter)
				logger.error(s);
			else
				logger.info(s);
		}

		@Override
		public synchronized void flush() throws IOException {
			// Only update the component when flush is called
			// One reason is that println produces two write statements, but only one flush...
			String s = sb.toString();
			sb.setLength(0);
			if (s.isEmpty())
				return;
			if (Platform.isFxApplicationThread())
				doc.appendText(s);
			else
				Platform.runLater(() -> doc.appendText(s));
//			flushCount++;
//			System.err.println("Flush called: " + flushCount);
		}

		@Override
		public void close() throws IOException {
			flush();
		}

	}

	
	Action createKillRunningScriptAction(final String name) {
		Action action = new Action(name, e -> {
			Future<?> future = runningTask.get();
			if (future == null)
				return;
			if (future.isDone())
				runningTask.set(null);
			else
				future.cancel(true);
		});
		action.disabledProperty().bind(runningTask.isNull());
		return action;
	}
	
	
	/**
	 * Check the first line of this script to see whether it should be run in the JavaFX Platform thread or not
	 * 
	 * @param script
	 * @return
	 */
	private static boolean requestGuiScript(final String script) {
		String[] lines = GeneralTools.splitLines(script);
		if (lines.length > 0) {
			String firstLine = lines[0].toLowerCase();
			return patternGuiScript.matcher(firstLine).find();
		};
		return false;
	}
	
	
	Action createRunScriptAction(final String name, final boolean selectedText) {
		Action action = new Action(name, e -> {
			String script;
			if (selectedText)
				script = getSelectedText();
			else
				script = getCurrentText();

			if (script == null || script.trim().length() == 0) {
				logger.warn("No script selected!");
				return;
			}
			
			Language language = getSelectedLanguage();
			if (language == null)
				return;
//			if (language == Language.JAVA)
//				language = Language.GROOVY; // Replace Java with Groovy for scripting

			ScriptTab tab = getCurrentScriptTab();
			if (autoClearConsole.get() && getCurrentScriptTab() != null) {
				tab.getConsoleComponent().clear();
			}
			
			// It's generally not a good idea to run in the Platform thread... since this will make the GUI unresponsive
			// However, there may be times when it is useful to run a short script in the Platform thread
			boolean runInPlatformThread = requestGuiScript(script);
			
			// Exceute the script
			if (runInPlatformThread) {
				logger.info("Running script in Platform thread...");
				try {
					tab.setRunning(true);
					executeScript(tab, script, qupath.getProject(), qupath.getImageData());
				} finally {
					tab.setRunning(false);
					runningTask.setValue(null);
				}
			} else {
				runningTask.setValue(qupath.createSingleThreadExecutor(this).submit(new Runnable() {
					@Override
					public void run() {
						try {
							tab.setRunning(true);
							executeScript(tab, script, qupath.getProject(), qupath.getImageData());
						} finally {
							tab.setRunning(false);
							Platform.runLater(() -> runningTask.setValue(null));
						}
					}
				}));
			}
		});
		if (selectedText)
			action.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		else
			action.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN));
		
		action.disabledProperty().bind(disableRun);
		
		return action;
	}
	
	
	Action createRunProjectScriptAction(final String name, final boolean doSave) {
		Action action = new Action(name, e -> handleRunProject(doSave));
		action.disabledProperty().bind(disableRun.or(qupath.projectProperty().isNull()));
		return action;
	}
	
	private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	
	/**
	 * Request project image entries to run script for.
	 */
	void handleRunProject(final boolean doSave) {
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			Dialogs.showNoProjectError("Script editor");
			return;
		}
		ScriptTab tab = getCurrentScriptTab();
		if (tab == null || tab.getEditorComponent().getText().trim().length() == 0) {
			Dialogs.showErrorMessage("Script editor", "No script selected!");
			return;
		}
		if (tab.getLanguage() == null) {
			Dialogs.showErrorMessage("Script editor", "Scripting language is unknown!");
			return;			
		}
		
		// Ensure that the previous images remain selected if the project still contains them
//		FilteredList<ProjectImageEntry<?>> sourceList = new FilteredList<>(FXCollections.observableArrayList(project.getImageList()));
		
		String sameImageWarning = doSave ? "A selected image is open in the viewer!\nUse 'File>Reload data' to see changes." : null;
		var listSelectionView = ProjectDialogs.createImageChoicePane(qupath, project.getImageList(), previousImages, sameImageWarning);
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Select project images");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
		dialog.getDialogPane().setContent(listSelectionView);
		dialog.setResizable(true);
		dialog.getDialogPane().setPrefWidth(600);
		dialog.initModality(Modality.APPLICATION_MODAL);
		Optional<ButtonType> result = dialog.showAndWait();
		if (!result.isPresent() || result.get() != ButtonType.OK)
			return;
		
		previousImages.clear();
//		previousImages.addAll(listSelectionView.getTargetItems());

		previousImages.addAll(ProjectDialogs.getTargetItems(listSelectionView));

		if (previousImages.isEmpty())
			return;
		
		List<ProjectImageEntry<BufferedImage>> imagesToProcess = new ArrayList<>(previousImages);

		ProjectTask worker = new ProjectTask(project, imagesToProcess, tab, doSave);
		
		
		ProgressDialog progress = new ProgressDialog(worker);
		progress.initOwner(qupath.getStage());
		progress.setTitle("Batch script");
		progress.getDialogPane().setHeaderText("Batch processing...");
		progress.getDialogPane().setGraphic(null);
		progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
			if (Dialogs.showYesNoDialog("Cancel batch script", "Are you sure you want to stop the running script after the current image?")) {
				worker.quietCancel();
				progress.setHeaderText("Cancelling...");
//				worker.cancel(false);
				progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
			}
			e.consume();
		});
		
		// Clear console if necessary
		if (autoClearConsole.get() && getCurrentScriptTab() != null) {
			tab.getConsoleComponent().clear();
		}
		
		// Create & run task
		runningTask.set(qupath.createSingleThreadExecutor(this).submit(worker));
		progress.show();
	}
	
	class ProjectTask extends Task<Void> {
		
		private Project<BufferedImage> project;
		private Collection<ProjectImageEntry<BufferedImage>> imagesToProcess;
		private ScriptTab tab;
		private boolean quietCancel = false;
		private boolean doSave = false;
		
		ProjectTask(final Project<BufferedImage> project, final Collection<ProjectImageEntry<BufferedImage>> imagesToProcess, final ScriptTab tab, final boolean doSave) {
			this.project = project;
			this.imagesToProcess = imagesToProcess;
			this.tab = tab;
			this.doSave = doSave;
		}
		
		public void quietCancel() {
			this.quietCancel = true;
		}

		public boolean isQuietlyCancelled() {
			return quietCancel;
		}

		@Override
		public Void call() {
			
			long startTime = System.currentTimeMillis();
			
			tab.setRunning(true);
			
			int counter = 0;
			for (ProjectImageEntry<BufferedImage> entry : imagesToProcess) {
				try {
					// Stop
					if (isQuietlyCancelled() || isCancelled()) {
						logger.warn("Script cancelled with " + (imagesToProcess.size() - counter) + " image(s) remaining");
						break;
					}
					
					updateProgress(counter, imagesToProcess.size());
					counter++;
					updateMessage(entry.getImageName() + " (" + counter + "/" + imagesToProcess.size() + ")");
					
					// Create a new region store if we need one
					System.gc();

					// Open saved data if there is any, or else the image itself
					ImageData<BufferedImage> imageData = entry.readImageData();
					if (imageData == null) {
						logger.warn("Unable to open {} - will be skipped", entry.getImageName());
						continue;
					}
//					QPEx.setBatchImageData(imageData);
					executeScript(tab, tab.getEditorComponent().getText(), project, imageData);
					if (doSave)
						entry.saveImageData(imageData);
					imageData.getServer().close();
					
					if (clearCache.get()) {
						try {
							var store = qupath == null ? null : qupath.getImageRegionStore();
							if (store != null)
								store.clearCache();
							System.gc();
						} catch (Exception e) {
							
						}
					}
				} catch (Exception e) {
					logger.error("Error running batch script: {}", e);
				}
			}
			updateProgress(imagesToProcess.size(), imagesToProcess.size());
			
			long endTime = System.currentTimeMillis();
			
			long timeMillis = endTime - startTime;
			String time = null;
			if (timeMillis > 1000*60)
				time = String.format("Total processing time: %.2f minutes", timeMillis/(1000.0 * 60.0));
			else if (timeMillis > 1000)
				time = String.format("Total processing time: %.2f seconds", timeMillis/(1000.0));
			else
				time = String.format("Total processing time: %d milliseconds", timeMillis);
			logger.info("Processed {} images", imagesToProcess.size());
			logger.info(time);
			
			return null;
		}
		
		
		@Override
		protected void done() {
			super.done();
			tab.setRunning(false);
			// Make sure we reset the running task
			Platform.runLater(() -> runningTask.setValue(null));
		}
		
	};
	
	
	protected static String getClipboardText(boolean escapeCharacters) {
		var clipboard = Clipboard.getSystemClipboard();
		var files = clipboard.getFiles();
		String text = clipboard.getString();
		if (files != null && !files.isEmpty()) {
			String s;
			if (files.size() == 1)
				s = files.get(0).getAbsolutePath();
			else {
				s = "[" + files.stream().map(f -> "\"" + f.getAbsolutePath() + "\"").collect(Collectors.joining("," + System.lineSeparator())) + "]";
			}
			if ("\\".equals(File.separator)) {
				s = s.replace("\\", "/");
			}
			text = s;
		}
		if (text != null && escapeCharacters)
			text = StringEscapeUtils.escapeJava(text);
		return text;
	}
	
	protected static boolean pasteFromClipboard(ScriptEditorControl control, boolean escapeCharacters) {
		// Intercept clipboard if we have files, to create a suitable string representation as well
		var text = getClipboardText(escapeCharacters);
		if (text == null)
			return false;
		control.paste(text);
		return true;
	}
	
	
	
	Action createCopyAction(final String name, final KeyCombination accelerator) {
		Action action = new Action(name, e -> {
			if (e.isConsumed())
				return;
			ScriptEditorControl editor = getCurrentTextComponent();
			if (editor != null) {
				editor.copy();
			}
			e.consume();
		});
		if (accelerator != null) {
			action.setAccelerator(accelerator);
			accelerators.add(accelerator);
		}
		return action;
	}
	
	
	Action createCutAction(final String name, final KeyCombination accelerator) {
		Action action = new Action(name, e -> {
			if (e.isConsumed())
				return;
			ScriptEditorControl editor = getCurrentTextComponent();
			if (editor != null) {
				editor.cut();
			}
			e.consume();
		});
		if (accelerator != null) {
			action.setAccelerator(accelerator);
			accelerators.add(accelerator);
		}
		return action;
	}
		
	Action createPasteAction(final String name, final boolean doEscape, final KeyCombination accelerator) {
		Action action = new Action(name, e -> {
			if (e.isConsumed())
				return;
			ScriptEditorControl editor = getCurrentTextComponent();
			if (editor != null)
				pasteFromClipboard(editor, doEscape);
			e.consume();
		});
		if (accelerator != null) {
			action.setAccelerator(accelerator);
			accelerators.add(accelerator);
		}
		return action;
	}
	
	
	Action createUndoAction(final String name, final KeyCombination accelerator) {
		Action action = new Action(name, e -> {
			ScriptEditorControl editor = getCurrentTextComponent();
			if (editor != null && editor.isUndoable())
				editor.undo();
			e.consume();
		});
		if (accelerator != null) {
			action.setAccelerator(accelerator);
			accelerators.add(accelerator);
		}
		return action;
	}
	
	Action createRedoAction(final String name, final KeyCombination accelerator) {
		Action action = new Action(name, e -> {
			ScriptEditorControl editor = getCurrentTextComponent();
			if (editor != null && editor.isRedoable())
				editor.redo();
			e.consume();
		});
		if (accelerator != null) {
			action.setAccelerator(accelerator);
			accelerators.add(accelerator);
		}
		return action;
	}

	
	/**
	 * Handle the press of the tab key, with/without shift.
	 * This either inserts getTabString() at the current caret position (if no text is selected, 
	 * or either indents or removes the indentation from all selected rows if a selection is made.
	 * 
	 * @param textArea
	 * @param shiftDown
	 */
	protected void handleTabPress(final ScriptEditorControl textArea, final boolean shiftDown) {
		String selected = textArea.getSelectedText();
		int pos = textArea.getCaretPosition();
		if (selected == null || selected.length() == 0) {
			textArea.insertText(pos, tabString);
			return;
		}

		String text = textArea.getText();
		IndexRange range = textArea.getSelection();
		int startRowPos = getRowStartPosition(text, range.getStart());
		int endRowPos = getRowEndPosition(text, range.getEnd());
		String textBetween = text.substring(startRowPos, endRowPos);
		String replaceText;
		if (shiftDown) {
			// Remove tabs at start of selected rows
			replaceText = textBetween.replace("\n"+tabString, "\n");
			if (replaceText.startsWith(tabString))
				replaceText = replaceText.substring(tabString.length());
		} else {
			replaceText = tabString + textBetween.replace("\n", "\n"+tabString);
		}
		
		textArea.selectRange(startRowPos, endRowPos);
		textArea.paste(replaceText);
		textArea.selectRange(startRowPos, startRowPos + replaceText.length());
	}
	
	
	/**
	 * Handle adding a new line, by checking current line for appropriate indentation.
	 * Note: this method should be called <em>instead</em> of simply accepting the newline character,
	 * i.e. the method itself will add the newline as required.
	 * 
	 * @param textArea
	 */
	protected void handleNewLine(final ScriptEditorControl textArea) {
		int caretPos = textArea.getCaretPosition();
		String text = textArea.getText();
		int startRowPos = getRowStartPosition(text, caretPos);
		String subString = text.substring(startRowPos, caretPos);
		String trimmedSubString = subString.trim();
		int ind = trimmedSubString.length() == 0 ? subString.length() : subString.indexOf(trimmedSubString);
		String insertText = ind == 0 ? "\n" : "\n" + subString.substring(0, ind);
		textArea.insertText(caretPos, insertText);
		int newPos = caretPos + insertText.length();
		textArea.selectRange(newPos, newPos);
	}
	
	
	/**
	 * Handle the press of the / key, with/without shift.
	 * This either inserts comments or uncomments the selected lines, if possible.
	 * 
	 * @param textArea
	 */
	protected void handleLineComment(final ScriptEditorControl textArea) {
		String commentString = getCurrentLineCommentString();
		if (commentString == null)
			return;

		String text = textArea.getText();
		IndexRange range = textArea.getSelection();
		boolean hasSelection = range.getLength() > 0;
		int startRowPos = getRowStartPosition(text, range.getStart());
		int endRowPos = getRowEndPosition(text, range.getEnd());
		String textBetween = text.substring(startRowPos, endRowPos);
		// Check if every new row starts with a comment string - if so we want to remove these, if not we want to add comments
		
		int nNewLines = textBetween.length() - textBetween.replace("\n", "").length();
		int nCommentLines = (textBetween.length() - textBetween.replace("\n" + commentString, commentString).length());
		boolean allComments = textBetween.startsWith(commentString) && nNewLines == nCommentLines;
		
		String replaceText;
		if (allComments) {
			// Remove tabs at start of selected rows
			replaceText = textBetween.replace("\n"+commentString, "\n");
			if (replaceText.startsWith(commentString))
				replaceText = replaceText.substring(commentString.length());
		} else {
			replaceText = commentString + textBetween.replace("\n", "\n"+commentString);
		}
		
		textArea.selectRange(startRowPos, endRowPos);
		textArea.paste(replaceText);
		if (hasSelection)
			textArea.selectRange(startRowPos, startRowPos + replaceText.length());
	}
	
	
	
	static int getRowStartPosition(final String text, final int pos) {
		return text.substring(0, pos).lastIndexOf("\n") + 1;
	}

	static int getRowEndPosition(final String text, final int pos) {
		int pos2 = text.substring(pos).indexOf("\n");
		if (pos2 < 0)
			return text.length();
		return pos + pos2;
	}

	
	
//	Action createTabIndenterAction(final TextArea textArea, final boolean shiftDown) {
//		return new Action(e -> handleTabPress(textArea, shiftDown));
//	}
	
	
	
	/**
	 * Given a file name, determine the associated language - or null if no suitable (supported) language can be found.
	 * 
	 * @param name
	 * @return
	 */
	public static Language getLanguageFromName(String name) {
		name = name.toLowerCase();
		for (Language l : Language.values()) {
			if (name.endsWith(l.getExtension()))
				return l;
		}
		return null;
	}
	
	
	
	class ScriptTab {
		
		private File file = null;
		private long lastModified = -1L;
		private String lastSavedContents = null;
		
		private Language language = null;
		
//		private BooleanProperty isModified = new SimpleBooleanProperty();
		private boolean isModified = false;
		
		private SplitPane splitEditor;
		private String name;
		
		private ScriptEditorControl console;
		private ScriptEditorControl editor;
		
		private boolean isRunning = false;
		
		
		public ScriptTab(final String script, final Language language) {
			initialize();
			if (script != null)
				editor.setText(script);
			untitledCounter++;
			name = "Untitled " + untitledCounter;
			setLanguage(language);
		}
		
		public ScriptTab(final File file) throws IOException {
			initialize();
			readFile(file);
		}
		
		
		protected void readFile(final File file) throws IOException {
			logger.info("Loading script file {}", file.getAbsolutePath());
			Scanner scanner = new Scanner(file);
			String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
//			String content = scanner.useDelimiter("\\Z").next();
			editor.setText(content);
			name = file.getName();
			this.file = file;
			lastModified = file.lastModified();
			lastSavedContents = content;
			setLanguage(getLanguageFromName(name));
			scanner.close();
			updateIsModified();
		}
		
		
		protected void refreshFileContents() {
			try {
				if (file != null && file.lastModified() > lastModified) {
					logger.debug("Calling refresh!");
					readFile(file);
					updateIsModified();
				}
			} catch (IOException e) {
				logger.error("Cannot refresh script file", e);
			}
		}
		
		
		private void initialize() {
			BorderPane panelMainEditor = new BorderPane();
			editor = getNewEditor();
			editor.textProperty().addListener((v, o, n) -> {
				updateUndoActionState();
				updateIsModified();
			});
			editor.selectedTextProperty().addListener((v, o, n) -> updateCutCopyActionState());
			editor.focusedProperty().addListener((v, o, n) -> maybeRefreshTab(this));
			
			panelMainEditor.setCenter(editor.getControl());


			console = getNewConsole();
			ContextMenu popup = new ContextMenu();
			popup.getItems().add(ActionUtils.createMenuItem(new Action("Clear console", e -> console.setText(""))));
			console.setPopup(popup);
			
			splitEditor = new SplitPane();
			splitEditor.setOrientation(Orientation.VERTICAL);
			splitEditor.getItems().addAll(
					panelMainEditor,
					console.getControl());
			SplitPane.setResizableWithParent(console.getControl(), Boolean.FALSE);
			splitEditor.setDividerPosition(0, 0.75);
			
			updateIsModified();
		}
		
		public Node getComponent() {
			return splitEditor;
		}
		
		
		public ScriptEditorControl getEditorComponent() {
			return editor;
		}
		
		public boolean hasScript() {
			return editor.getText().length() > 0;
		}

		public ScriptEditorControl getConsoleComponent() {
			return console;
		}

		public File getFile() {
			return file;
		}
		
		public boolean fileExists() {
			return file != null && file.exists();
		}
		
		boolean isRunning() {
			return isRunning;
		}
		
		void setRunning(boolean running) {
			this.isRunning = running;
		}
		
//		public ReadOnlyBooleanProperty isModifiedProperty() {
//			return isModified;
//		}
		
		/**
		 * Return true if the script is modified, i.e. it isn't the same as the last saved version
		 * 
		 * @return
		 */
		public boolean isModified() {
			return isModified;
		}

		private void updateIsModified() {
			boolean newState = !fileExists() || !editor.getText().equals(lastSavedContents); // TODO: Consider checking disk contents / timestamp
			if (isModified == newState)
				return;
			isModified = newState;
			// Update the display of the list
			if (listScripts != null)
				listScripts.refresh();
		}
		
		public void saveToFile(final File file) throws IOException {
			String text = getCurrentText();
			Files.writeString(file.toPath(), text);
			this.file = file;
			this.name = file.getName();
			this.lastSavedContents = text;
			this.lastModified = file.lastModified();
			updateIsModified();
		}
		
		public Language getLanguage() {
			return language;
		}
		
		public void setLanguage(final Language language) {
			this.language = language;
		}
		
		public String getRequestedExtension() {
			if (language == null)
				return ".txt";
			return language.getExtension();
		}
		
		public String getName() {
			return name;
		}
		
		
		@Override
		public String toString() {
			return isModified ? "*" + name : name;
		}

		
	}
	
	
	
	Action createOpenAction(final String name) {
		Action action = new Action(name, e -> {
			
			String dirPath = PathPrefs.scriptsPathProperty().get();
			File dir = null;
			if (dirPath != null)
				dir = new File(dirPath);
//			File file = Dialogs.promptForFile("Choose script file", dir, "Known script files", SCRIPT_EXTENSIONS);
			File file = Dialogs.promptForFile("Choose script file", dir, "Groovy script", ".groovy");
			if (file == null)
				return;
			try {
				addScript(file, true);
				PathPrefs.scriptsPathProperty().set(file.getParent());
			} catch (Exception ex) {
				logger.error("Unable to open script file: {}", ex);
				ex.printStackTrace();
			}
		});
		action.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
		return action;
	}
	
	
	Action createNewAction(final String name) {
		Action action = new Action(name, e -> addNewScript("", getDefaultLanguage(), true));
		action.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
		return action;
	}
	
	
	Action createCloseAction(final String name) {
		Action action = new Action(name, e -> {
			ScriptTab tab = getCurrentScriptTab();
			if (tab == null)
				return;
			promptToClose(tab);
		});
		action.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
		return action;
	}
	
	
	Action createSaveAction(final String name, final boolean saveAs) {
		Action action = new Action(name, e -> {
			ScriptTab tab = getCurrentScriptTab();
			if (tab == null)
				return;
			save(tab, saveAs);
		});
		if (saveAs)
			action.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		else
			action.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
		return action;
	}
	
	
	Action createRevertAction(final String name) {
		Action action = new Action(name, e -> {
			ScriptTab tab = getCurrentScriptTab();
			if (tab != null)
				tab.refreshFileContents();
		});
		return action;
	}
	
	Action createFindAction(final String name) {
		ScriptFindCommand findCommand = new ScriptFindCommand();
		Action action = new Action(name, e -> {
			findCommand.run();
		});
		action.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
		return action;
	}
	
	Action createExitAction(final String name) {
		Action action = new Action(name, e -> {
			attemptToQuitScriptEditor();
			e.consume();
		});
		action.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
		return action;
	}
	
	Action createInsertAction(final String name) {
		Action action = new Action(name, e -> {
			var control = getCurrentTextComponent();

			if (name.toLowerCase().equals("pixel classifiers")) {
				try {
					String classifiers = qupath.getProject().getPixelClassifiers().getNames().stream()
							.map(classifierName -> "\"" + classifierName + "\"")
							.collect(Collectors.joining(", "));
					control.paste("[" + classifiers + "]");
				} catch (IOException ex) {
					logger.error("Could not fetch classifiers", ex.getLocalizedMessage());
				}
			} else if (name.toLowerCase().equals("object classifiers")) {
				try {
					String classifiers = qupath.getProject().getObjectClassifiers().getNames().stream()
							.map(classifierName -> "\"" + classifierName + "\"")
							.collect(Collectors.joining(", "));
					control.paste("[" + classifiers + "]");
				} catch (IOException ex) {
					logger.error("Could not fetch classifiers", ex.getLocalizedMessage());
				}
			} else if (name.toLowerCase().equals("detection")) {
				ObservableMeasurementTableData model = new ObservableMeasurementTableData();
				model.setImageData(qupath.getImageData(), qupath.getImageData().getHierarchy().getObjects(null, PathDetectionObject.class));
				List<String> data = SummaryMeasurementTableCommand.getTableModelStrings(model, "\", \"", Arrays.asList());
				control.paste("[\"" + data.get(0) + "\"]");
			} else if (name.toLowerCase().equals(GeneralTools.SYMBOL_MU + ""))
				control.paste(GeneralTools.SYMBOL_MU + "");
			else {	
				// Imports (end with a new line)
				if (name.toLowerCase().equals("qpex"))
					control.insertText(0, "import static qupath.lib.gui.scripting.QPEx.*");
				else if (name.toLowerCase().equals("qp"))
					control.insertText(0, "import static qupath.lib.gui.scripting.QP.*");
				else if (name.toLowerCase().equals("all default"))
					control.insertText(0, QPEx.getDefaultImports(false));
				handleNewLine(control);
			}
			e.consume();
		});
		
		if (name.equals(GeneralTools.SYMBOL_MU + ""))
			action.setAccelerator(new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		else if (name.toLowerCase().equals("pixel classifiers") || name.toLowerCase().equals("object classifiers"))
			action.disabledProperty().bind(qupath.projectProperty().isNull());
		else if (name.toLowerCase().equals("detection"))
			action.disabledProperty().bind(qupath.imageDataProperty().isNull());
			
		return action;
	}
	
	
	void attemptToQuitScriptEditor() {
		if (listScripts.getItems().isEmpty())
			dialog.close();
		while (promptToClose(getCurrentScriptTab()))
			continue;
	}
	


	@Override
	public void showEditor() {
		if (dialog == null || !dialog.isShowing())
			createDialog();
		// Create a new script if we need one
		if (listScripts.getItems().isEmpty())
			showScript(null, null);
		if (!dialog.isShowing())
			dialog.show();
	}


	@Override
	public void showScript(String name, String script) {
		if (dialog == null || !dialog.isShowing())
			createDialog();
		addNewScript(script, getDefaultLanguage(), true);
		if (!dialog.isShowing())
			dialog.show();
	}
	
	
	protected Language getDefaultLanguage() {
		if (availableLanguages.contains(Language.GROOVY))
				return Language.GROOVY;
		return Language.JAVASCRIPT;
	}


	@Override
	public void showScript(File file) {
		try {
			if (dialog == null || !dialog.isShowing())
				createDialog();
			addScript(file, true);
			if (!dialog.isShowing()) {
				dialog.show();
			}
		} catch (Exception e) {
			logger.error("Could not load script from {}", file);
			logger.error("", e);
		}
	}
	
	
	/**
	 * Basic script editor control.
	 * The reason for its existence is to enable custom script editors to be implemented that provide additional functionality 
	 * (e.g. syntax highlighting), but do not rely upon subclassing any specific JavaFX control.
	 * <p>
	 * Note: This is rather cumbersome, and may be removed in the future if the script editor design is revised.
	 */
	public static interface ScriptEditorControl extends TextAppendable {
		
		/**
		 * Text currently in the editor control.
		 * @return
		 */
		public StringProperty textProperty();
		
		/**
		 * Set all the text in the editor.
		 * @param text
		 */
		public void setText(final String text);

		/**
		 * Get all the text in the editor;
		 * @return
		 */
		public String getText();
		
		/**
		 * Deselect any currently-selected text.
		 */
		public void deselect();
		
		/**
		 * Get the range of the currently-selected text.
		 * @return
		 */
		public IndexRange getSelection();

		/**
		 * Set the range of the selected text.
		 * @param anchor
		 * @param caretPosition
		 */
		public void selectRange(int anchor, int caretPosition);

		/**
		 * Text currently selected in the editor control.
		 * @return
		 */
		public ObservableValue<String> selectedTextProperty();
		
		/**
		 * Get the value of {@link #selectedTextProperty()}.
		 * @return
		 */
		public String getSelectedText();
		
		/**
		 * Returns true if 'undo' can be applied to the control.
		 * @return
		 */
		public boolean isUndoable();
		
		/**
		 * Returns true if 'redo' can be applied to the control.
		 * @return
		 */
		public boolean isRedoable();
		
		/**
		 * Get the region representing this control, so it may be added to a scene.
		 * @return
		 */
		public Region getControl();
		
		/**
		 * Set the popup menu for this control.
		 * @param menu
		 */
		public void setPopup(ContextMenu menu);
		
		/**
		 * Request undo.
		 */
		public void undo();
		
		/**
		 * Request redo.
		 */
		public void redo();
		
		/**
		 * Request copy the current selection.
		 */
		public void copy();
		
		/**
		 * Request cut the current selection.
		 */
		public void cut();
		
		/**
		 * Request paste the specified text.
		 * @param text 
		 */
		public void paste(String text);
		
		@Override
		public void appendText(final String text);
		
		/**
		 * Request clear the contents of the control.
		 */
		public void clear();
		
		/**
		 * Get the current caret position.
		 * @return
		 */
		public int getCaretPosition();
		
		/**
		 * Request inserting the specified text.
		 * @param pos position to insert the text
		 * @param text the text to insert
		 */
		public void insertText(int pos, String text);

		/**
		 * Request deleting the text within the specified range.
		 * @param startIdx
		 * @param endIdx
		 */
		public void deleteText(int startIdx, int endIdx);

		/**
		 * Focused property of the control.
		 * @return
		 */
		public ReadOnlyBooleanProperty focusedProperty();

	}
	
	
	static class ScriptEditorTextArea implements ScriptEditorControl {
		
		private TextArea textArea;
		
		ScriptEditorTextArea(final TextArea textArea) {
			this.textArea = textArea;
		}

		@Override
		public StringProperty textProperty() {
			return textArea.textProperty();
		}

		@Override
		public void setText(String text) {
			textArea.setText(text);
		}

		@Override
		public String getText() {
			return textArea.getText();
		}

		@Override
		public ObservableValue<String> selectedTextProperty() {
			return textArea.selectedTextProperty();
		}

		@Override
		public String getSelectedText() {
			return textArea.getSelectedText();
		}

		@Override
		public Control getControl() {
			return textArea;
		}

		@Override
		public boolean isUndoable() {
			return textArea.isUndoable();
		}

		@Override
		public boolean isRedoable() {
			return textArea.isRedoable();
		}

		@Override
		public void undo() {
			textArea.undo();
		}

		@Override
		public void redo() {
			textArea.redo();
		}

		@Override
		public void copy() {
			textArea.copy();
		}

		@Override
		public void cut() {
			textArea.cut();
		}

		@Override
		public void paste(String text) {
			if (text != null)
				textArea.replaceSelection(text);
//			textArea.paste();
		}

		@Override
		public void clear() {
			textArea.clear();
		}

		@Override
		public void appendText(final String text) {
			textArea.appendText(text);
		}

		@Override
		public ReadOnlyBooleanProperty focusedProperty() {
			return textArea.focusedProperty();
		}
		
		@Override
		public int getCaretPosition() {
			return textArea.getCaretPosition();
		}
		
		@Override
		public void insertText(int pos, String text) {
			textArea.insertText(pos, text);
		}
		
		@Override
		public void deleteText(int startIdx, int endIdx) {
			textArea.deleteText(startIdx, endIdx);
		}

		@Override
		public void deselect() {
			textArea.deselect();
		}

		@Override
		public IndexRange getSelection() {
			return textArea.getSelection();
		}

		@Override
		public void selectRange(int anchor, int caretPosition) {
			textArea.selectRange(anchor, caretPosition);
		}

		@Override
		public void setPopup(ContextMenu menu) {
			textArea.setContextMenu(menu);
		}
		
	}
	
	
	
	class ScriptFindCommand implements Runnable {
		
		private Dialog<Void> dialog;
		private TextField tfFind = new TextField();
		
		@Override
		public void run() {
			if (dialog == null)
				createFindDialog();
			dialog.show();
			tfFind.requestFocus();
		}
		
		private void createFindDialog() {
			dialog = new Dialog<>();
			dialog.setTitle("Find text");
			dialog.initOwner(DefaultScriptEditor.this.dialog);
			dialog.initModality(Modality.NONE);
			
			ButtonType btNext = new ButtonType("Next");
			ButtonType btPrevious = new ButtonType("Previous");
			ButtonType btClose = new ButtonType("Close", ButtonData.CANCEL_CLOSE);
			dialog.getDialogPane().getButtonTypes().setAll(btPrevious, btNext, btClose);
//			dialog.getDialogPane().lookupButton(btClose).setVisible(false);
			
			GridPane pane = new GridPane();
			pane.add(new Label("Search text: "), 0, 0);
			tfFind.setTooltip(new Tooltip("Enter the search text"));
			tfFind.setPrefColumnCount(32);
			pane.add(tfFind, 1, 0);
			CheckBox cbIgnoreCase = new CheckBox("Ignore case");
			pane.add(cbIgnoreCase, 0, 1, 2, 1);
			pane.setVgap(10);
			
			var actionNext = new Action("Next", e -> {
				findNext(getCurrentTextComponent(), tfFind.getText(), cbIgnoreCase.isSelected());
				e.consume();
			});
//			actionNext.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
			var actionPrevious = new Action("Previous", e -> {
				findNext(getCurrentTextComponent(), tfFind.getText(), cbIgnoreCase.isSelected());
				e.consume();
			});
//			actionNext.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN));
			tfFind.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
				if (e.getCode() == KeyCode.ENTER) {
					actionNext.handle(new ActionEvent());
					e.consume();
				}
			});

			((Button)dialog.getDialogPane().lookupButton(btNext)).addEventFilter(ActionEvent.ACTION, e -> actionNext.handle(e));
			((Button)dialog.getDialogPane().lookupButton(btPrevious)).addEventFilter(ActionEvent.ACTION, e -> actionPrevious.handle(e));
			
//			((Button)dialog.getDialogPane().lookupButton(btClose)).addEventFilter(ActionEvent.ACTION, e -> {
//				findPrevious(getCurrentTextComponent(), tfFind.getText());
//				e.consume();
//			});

			dialog.getDialogPane().setHeader(null);
			dialog.getDialogPane().setContent(pane);
			
		}
		
		
		void findNext(final ScriptEditorControl control, final String findText, final boolean ignoreCase) {
			if (control == null || findText == null || findText.isEmpty())
				return;
			
			String text = control.getText();
			String toFind = null;
			if (ignoreCase) {
				toFind = findText.toLowerCase();
				text = text.toLowerCase();
			} else
				toFind = findText;
			if (!text.contains(findText))
				return;
			int pos = control.getSelection().getEnd();
			int ind = text.substring(pos).indexOf(toFind);
			// If not found, loop around
			if (ind < 0)
				ind = text.indexOf(toFind);
			else
				ind = ind + pos;
			control.selectRange(ind, ind + toFind.length());
		}
		
		void findPrevious(final ScriptEditorControl control, final String findText, final boolean ignoreCase) {
			if (control == null || findText == null || findText.isEmpty())
				return;
			
			String text = control.getText();
			String toFind = null;
			if (ignoreCase) {
				toFind = findText.toLowerCase();
				text = text.toLowerCase();
			} else
				toFind = findText;
			if (!text.contains(toFind))
				return;
			
			int pos = control.getSelection().getStart();
			int ind = pos == 0 ? text.lastIndexOf(toFind) : text.substring(0, pos).lastIndexOf(toFind);
			// If not found, loop around
			if (ind < 0)
				ind = text.lastIndexOf(toFind);
			control.selectRange(ind, ind + toFind.length());
		}

	}
	
	static class CustomTextArea extends TextArea {
		
		CustomTextArea() {
			super();
			setStyle("-fx-font-family: monospaced;");
		}
		
		/**
		 * We need to override the default Paste command to handle escaping
		 */
		@Override
		public void paste() {
			var text = getClipboardText(false);
			if (text != null)
				replaceSelection(text);
		}
		
	}
}
