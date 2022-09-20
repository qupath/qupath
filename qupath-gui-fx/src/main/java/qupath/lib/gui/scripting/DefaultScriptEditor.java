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

package qupath.lib.gui.scripting;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.apache.commons.text.StringEscapeUtils;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.JavadocViewer;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.scripting.languages.GroovyLanguage;
import qupath.lib.gui.scripting.languages.PlainLanguage;
import qupath.lib.gui.scripting.languages.RunnableLanguage;
import qupath.lib.gui.scripting.languages.ScriptLanguage;
import qupath.lib.gui.scripting.languages.ScriptLanguageProvider;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;


/**
 * 
 * Default multilingual script editor.
 * <p>
 * Lacks syntax highlighting and other pleasant features, unfortunately.
 * 
 * @author Pete Bankhead
 */
public class DefaultScriptEditor implements ScriptEditor {

	private static final Logger logger = LoggerFactory.getLogger(DefaultScriptEditor.class);
	
	private static final List<ScriptLanguage> availableLanguages = ScriptLanguageProvider.getAvailableScriptLanguages();
	
	/**
	 * Default classes to import at the start a script, if 'useDefaultBindings' is enabled.
	 */
	private static final Collection<Class<?>> defaultClasses = getDefaultClasses();
	
	/**
	 * Default static classes to import at the start a script, if 'useDefaultBindings' is enabled.
	 */
	private static final Collection<Class<?>> defaultStaticClasses = getDefaultStaticClasses();
	
	private final ScriptEditorDragDropListener dragDropListener;
	
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();

	private QuPathGUI qupath;
	private Stage dialog;
	private SplitPane splitMain;
	private ToggleGroup toggleLanguages = new ToggleGroup();
	private Font fontMain = Font.font("Courier");
	
	private ObjectProperty<ScriptTab> selectedScript = new SimpleObjectProperty<>();
	
	private ObjectProperty<ScriptLanguage> currentLanguage = new SimpleObjectProperty<>();
		
	// Binding to indicate it shouldn't be possible to 'Run' any script right now
	private BooleanBinding disableRun = runningTask.isNotNull().or(Bindings.createBooleanBinding(() -> !(currentLanguage.getValue() instanceof RunnableLanguage), currentLanguage));
	
	// Binding to indicate it shouldn't be possible to 'Run' any script right now
	private StringBinding title = Bindings.createStringBinding(() -> {
		if (runningTask.get() == null)
			return "Script Editor";
		return "Script Editor (Running)";
	}, runningTask);
	
	// Accelerators that have been assigned to actions
	private Collection<KeyCombination> accelerators = new HashSet<>();
	
	// Keyboard accelerators
	protected KeyCombination comboPasteEscape = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
	protected final KeyCodeCombination completionCodeCombination = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);

	protected Action beautifySourceAction = ActionTools.createAction(this::beautifySource, "Beautify source");
	protected Action compressSourceAction = ActionTools.createAction(this::compressSource, "Compress source");
	
	private BooleanBinding canBeautifyBinding = Bindings.createBooleanBinding(() -> {
		var language = getCurrentLanguage();
		var syntax = language == null ? null : language.getSyntax();
		return syntax == null || !syntax.canBeautify();
	}, currentLanguageProperty());
	
	private BooleanBinding canCompressBinding = Bindings.createBooleanBinding(() -> {
		var language = getCurrentLanguage();
		var syntax = language == null ? null : language.getSyntax();
		return syntax == null || !syntax.canCompress();
	}, currentLanguageProperty());
	
	private void beautifySource() {
		var tab = getCurrentScriptObject();
		var editor = tab == null ? null : tab.getEditorComponent();
		var language = tab == null ? null : tab.getLanguage();
		var syntax = language == null ? null : language.getSyntax();
		if (editor == null || syntax == null || !syntax.canBeautify())
			return;
		editor.setText(syntax.beautify(editor.getText()));
	}

	private void compressSource() {
		var tab = getCurrentScriptObject();
		var editor = tab == null ? null : tab.getEditorComponent();
		var language = tab == null ? null : tab.getLanguage();
		var syntax = language == null ? null : language.getSyntax();
		if (editor == null || syntax == null || !syntax.canCompress())
			return;
		editor.setText(syntax.compress(editor.getText()));		
	}

	
	// Note: it doesn't seem to work to set the accelerators...
	// this leads to the actions being called twice, due to the built-in behaviour of TextAreas
	protected Action copyAction;
	protected Action cutAction;
	protected Action pasteAction;
	protected Action pasteAndEscapeAction;
	protected Action undoAction;
	protected Action redoAction;
	
	private Action zapGremlinsAction = createReplaceTextAction("Zap gremlins", GeneralTools::zapGremlins, true);
	private Action replaceQuotesAction = createReplaceTextAction("Replace curly quotes", GeneralTools::replaceCurlyQuotes, true);
	
	private Action showJavadocsAction = ActionTools.createAction(() -> JavadocViewer.getInstance().getStage().show(), "Show Javadocs");
	
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

	protected Action smartEditingAction;

	// Add default bindings, i.e. QuPathGUI, Viewer, ImageData... makes scripting much easier
	private BooleanProperty useDefaultBindings = PathPrefs.createPersistentPreference("scriptingUseDefaultBindings", true);
	private BooleanProperty autoRefreshFiles = PathPrefs.createPersistentPreference("scriptingAutoRefreshFiles", true);
	private BooleanProperty sendLogToConsole = PathPrefs.createPersistentPreference("scriptingSendLogToConsole", true);
	private BooleanProperty outputScriptStartTime = PathPrefs.createPersistentPreference("scriptingOutputScriptStartTime", false);
	private BooleanProperty autoClearConsole = PathPrefs.createPersistentPreference("scriptingAutoClearConsole", true);
	private BooleanProperty clearCache = PathPrefs.createPersistentPreference("scriptingClearCache", false);
	protected BooleanProperty smartEditing = PathPrefs.createPersistentPreference("scriptingSmartEditing", true);
	private BooleanProperty wrapTextProperty = PathPrefs.createPersistentPreference("scriptingWrapText", false);
	

	// Regex pattern used to identify whether a script should be run in the JavaFX Platform thread
	// If so, this line should be included at the top of the script
	private static Pattern patternGuiScript = Pattern.compile("guiscript *?= *?true");
	
	private ListView<ScriptTab> listScripts = new ListView<>();

	/**
	 * Constructor.
	 * @param qupath current QuPath instance.
	 */
	public DefaultScriptEditor(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.dragDropListener = new ScriptEditorDragDropListener(qupath);
		initializeActions();
		
		currentLanguage.bind(Bindings.createObjectBinding(() -> {
			var language = toggleLanguages.getSelectedToggle();
			return language == null ? null : ScriptLanguageProvider.fromString((String)language.getUserData());
		}, toggleLanguages.selectedToggleProperty()));
		
		selectedScript.addListener((v, o, n) -> {
			if (n == null || n.getLanguage() == null)
				return;
			setToggle(n.getLanguage());
		});
	}
	
	private void setToggle(ScriptLanguage language) {
		
		var currentSelected = toggleLanguages.getSelectedToggle() == null ? null : toggleLanguages.getSelectedToggle().getUserData();
		var languageName = language == null ? null : language.toString();
		if (Objects.equals(languageName, currentSelected))
			return;
		
		for (Toggle button : toggleLanguages.getToggles()) {
			if (language.toString().equals(button.getUserData())) {
				button.setSelected(true);
				break;
			}
		}
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
		
		beautifySourceAction.setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN));
		beautifySourceAction.disabledProperty().bind(canBeautifyBinding);
		compressSourceAction.disabledProperty().bind(canCompressBinding);
		
		qupath.projectProperty().addListener((v, o, n) -> {
			previousImages.clear();
		});
		
		findAction = createFindAction("Find");
		
		smartEditingAction = ActionTools.createSelectableAction(smartEditing, "Enable smart editing");
	}
	
	
	Action createReplaceTextAction(String name, Function<String, String> fun, boolean limitToSelected) {
		var action = new Action(name, e -> replaceCurrentEditorText(fun, limitToSelected));
		action.disabledProperty().bind(selectedScript.isNull());
		return action;
	}
	
	void replaceCurrentEditorText(Function<String, String> fun, boolean limitToSelected) {
		var editor = getCurrentTextComponent();
		if (editor == null)
			return;
		var selected = editor.getSelectedText();
		if (limitToSelected && !selected.isEmpty()) {
			var updated = fun.apply(selected);
			if (!Objects.equals(selected, updated)) {
				editor.paste(updated);
			}
		} else
			editor.setText(fun.apply(editor.getText()));
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
		for (ScriptLanguage l: availableLanguages) {
			for (String ext: l.getExtensions()) {
				if (name.endsWith(ext))
					return true;
			}
		}
		return false;
	}
	


	void maybeRefreshTab(final ScriptTab tab, boolean updateLanguage) {
		if (tab != null && autoRefreshFiles.get()) {
			tab.refreshFileContents();
			if (updateLanguage)
				setToggle(tab.getLanguage());
		}
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
	public void addNewScript(final String script, final ScriptLanguage language, final boolean doSelect) {
		ScriptEditorControl editor = getNewEditor();
		editor.wrapTextProperty().bindBidirectional(wrapTextProperty);
		ScriptTab tab = new ScriptTab(editor, getNewConsole(), script, language);
		
		// Attach all listeners
		editor.textProperty().addListener((v, o, n) -> {
			updateUndoActionState();
			tab.updateIsModified();
		});
		editor.selectedTextProperty().addListener((v, o, n) -> updateCutCopyActionState());
		editor.focusedProperty().addListener((v, o, n) -> {
			if (n)
				maybeRefreshTab(tab, false);
		});
		tab.isModifiedProperty().addListener((v, o, n) ->{
			if (listScripts != null)
				listScripts.refresh();
		});
		
		// Update relevant field in script editor
//		setCurrentTabLanguage(language);
//		currentLanguage.set(language);
		listScripts.getItems().add(tab);
		if (doSelect)
			listScripts.getSelectionModel().select(tab);
		updateSelectedScript(true);
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
		
		ScriptEditorControl editor = getNewEditor();
		editor.wrapTextProperty().bindBidirectional(wrapTextProperty);
		
		ScriptTab tab = new ScriptTab(editor, getNewConsole(), file);
		
		// Attach all listeners
		editor.textProperty().addListener((v, o, n) -> {
			updateUndoActionState();
			tab.updateIsModified();
		});
		editor.selectedTextProperty().addListener((v, o, n) -> updateCutCopyActionState());
		editor.focusedProperty().addListener((v, o, n) -> {
			if (n)
				maybeRefreshTab(tab, false);
		});
		tab.isModifiedProperty().addListener((v, o, n) -> {
			// Update the display of the list
			if (listScripts != null)
				listScripts.refresh();
		});
		setToggle(tab.getLanguage());
		listScripts.getItems().add(tab);
		if (doSelect)
			listScripts.getSelectionModel().select(tab);
	}
	
	
	void updateSelectedScript(boolean updateLanguage) {
		ScriptTab tab = listScripts == null ? null : listScripts.getSelectionModel().getSelectedItem();
		if (tab == selectedScript.get())
			return;
		
		Node lastComponent = splitMain.getItems().get(1);
		Node newComponent = tab == null ? null : tab.getSplitEditor();
		if (lastComponent == newComponent)
			return;
		
		double loc = splitMain.getDividers().get(0).getPosition();
		if (tab != null) {
			splitMain.getItems().set(1, newComponent);
			maybeRefreshTab(tab, false);
			splitMain.setDividerPosition(0, loc);
			// Unfortunately need to wait until divider is present before we can set the divider location
			if (selectedScript.get() == null)
				tab.getSplitEditor().setDividerPosition(0, 0.75);
			else
				tab.getSplitEditor().setDividerPosition(0, selectedScript.get().getSplitEditor().getDividers().get(0).getPosition());
			
			// Update the selected language
			selectedScript.set(tab);
			setToggle(tab.getLanguage());
		} else
			selectedScript.set(tab);
//		else
//			splitMain.getItems().set(1, newComponent);
	}

	ScriptLanguage getSelectedLanguage() {
		return getCurrentScriptObject() == null ? null : getCurrentScriptObject().getLanguage();
	}
	
	protected ScriptEditorControl getNewConsole() {
		TextArea consoleArea = new TextArea();
		consoleArea.setEditable(false);
		return new TextAreaControl(consoleArea);
	}

	protected ScriptEditorControl getNewEditor() {
		TextArea editor = new CustomTextArea();
		editor.setFont(fontMain);
		
		TextAreaControl control = new TextAreaControl(editor);
		editor.addEventFilter(KeyEvent.KEY_PRESSED, (KeyEvent e) -> {
			var language = currentLanguage.getValue();
			if (language == null)
				return;
	        if (e.getCode() == KeyCode.TAB) {
	        	language.getSyntax().handleTabPress(control, e.isShiftDown());
	        	e.consume();
	        } else if (e.isShortcutDown() && e.getCode() == KeyCode.SLASH) {
	        	language.getSyntax().handleLineComment(control);
	        	e.consume();
	        } else if (e.getCode() == KeyCode.ENTER && control.getSelectedText().length() == 0) {
	        	language.getSyntax().handleNewLine(control, smartEditing.get());
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
		
		var miWrapLines = new CheckMenuItem("Wrap lines");
		miWrapLines.selectedProperty().bindBidirectional(wrapTextProperty);

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
				findAction,
				null,
				beautifySourceAction,
				compressSourceAction,
				zapGremlinsAction,
				replaceQuotesAction,
				null,
				smartEditingAction,
				miWrapLines
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
		List<RadioMenuItem> nonRunnableLanguages = new ArrayList<>();
		for (ScriptLanguage language : new LinkedHashSet<>(availableLanguages)) {
			String languageName = language.toString();
			RadioMenuItem item = new RadioMenuItem(languageName);
			item.setToggleGroup(toggleLanguages);
			item.setUserData(languageName);
			if (language instanceof RunnableLanguage)
				menuLanguages.getItems().add(item);
			else
				nonRunnableLanguages.add(item);

			item.selectedProperty().addListener((v, o, n) -> {
				if (n)
					setCurrentTabLanguage(language);
			});
		}
		
		if (!nonRunnableLanguages.isEmpty()) {
			menuLanguages.getItems().add(new SeparatorMenuItem());
			for (RadioMenuItem item: nonRunnableLanguages) {
				menuLanguages.getItems().add(item);
			}
		}
		
		// Setting the default language (Groovy in this case), or if not present, the first one available
		var defaultLanguage = toggleLanguages.getToggles()
				.stream()
				.filter(t -> t.getUserData().equals(GroovyLanguage.getInstance()))
				.findFirst()
				.orElseGet(() -> toggleLanguages.getToggles().get(0));
		defaultLanguage.setSelected(true);
//		toggleLanguages.selectToggle(defaultLanguage);
		
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
		
		// Help menu
		Menu menuHelp = new Menu("Help");
		MenuTools.addMenuItems(menuHelp,
				showJavadocsAction
				);
		menubar.getMenus().add(menuHelp);

		// File list
		BorderPane panelList = new BorderPane();
//		label.setFont(label.getFont().deriveFont(12f));
		TitledPane titledScripts = new TitledPane("Scripts", listScripts);
		titledScripts.prefWidthProperty().bind(panelList.widthProperty());
		titledScripts.prefHeightProperty().bind(panelList.heightProperty());
		titledScripts.setCollapsible(false);
		panelList.setCenter(titledScripts);
		listScripts.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateSelectedScript(true));
		listScripts.setCellFactory(new Callback<ListView<ScriptTab>, 
	            ListCell<ScriptTab>>() {
	                @Override 
	                public ListCell<ScriptTab> call(ListView<ScriptTab> list) {
	                    return new ScriptObjectListCell();
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
		
		// Accept Drag and Drop
		dialog.getScene().setOnDragOver(event -> {
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
		dialog.getScene().setOnDragDropped(dragDropListener);
		
		splitMain.setDividerPosition(0, 0.25);
		menubar.useSystemMenuBarProperty().bindBidirectional(PathPrefs.useSystemMenubarProperty());
//		menubar.setUseSystemMenuBar(true);
		updateUndoActionState();
		updateCutCopyActionState();
	}
	
	
	void setCurrentTabLanguage(final ScriptLanguage language) {
		ScriptTab tab = getCurrentScriptObject();
		if (tab == null)
			return;
		for (ScriptLanguage l : availableLanguages) {
			if (l == language) {
				tab.setLanguage(l);
				break;
			}
		}
	}
	
	protected ReadOnlyObjectProperty<ScriptLanguage> currentLanguageProperty() {
		return currentLanguage;
	}
	
	protected ScriptLanguage getCurrentLanguage() {
		return currentLanguage.get();
	}
	
	protected ScriptTab getCurrentScriptObject() {
		return selectedScript.get();
//		return listScripts == null ? null : listScripts.getSelectionModel().getSelectedItem();
	}
	
	protected ScriptEditorControl getCurrentTextComponent() {
		ScriptTab tab = getCurrentScriptObject();
		return tab == null ? null : tab.getEditorComponent();
	}
	
	
	protected ScriptEditorControl getCurrentConsoleComponent() {
		ScriptTab tab = getCurrentScriptObject();
		return tab == null ? null : tab.getConsoleComponent();
	}

	protected Node getCurrentScriptComponent() {
		ScriptTab tab = getCurrentScriptObject();
		return tab == null ? null : tab.getSplitEditor();
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
	 * Execute the script currently shown in the specified ScriptObject.
	 * 
	 * Output will be shown in the console of the ScriptObject.
	 * 
	 * @param tab
	 * @param script
	 * @param project 
	 * @param imageData
	 */
	private void executeScript(final ScriptTab tab, final String script, final Project<BufferedImage> project, final ImageData<BufferedImage> imageData) {
		if (!(tab.getLanguage() instanceof RunnableLanguage))
			return;
	
		RunnableLanguage runnableLanguage = (RunnableLanguage)tab.getLanguage();
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
			Object result = executeScript(runnableLanguage, script, project, imageData, useDefaultBindings.get(), context);
			if (result != null) {
				printWriter.println("Result: " + result);
			}
			if (outputScriptStartTime.get())
				printWriter.println(String.format("Script run time: %.2f seconds", (System.currentTimeMillis() - startTime)/1000.0));
		} catch (ScriptException e) {
			// TODO: Consider exception logging here, rather than via the called method
		} catch (Throwable t) {
			// This can happen when something goes very wrong - like attempting to load a missing native library
			// We need to somehow let the user know, rather than swallowing the problem silently
			logger.error(t.getLocalizedMessage(), t);
		} finally {
			if (attachToLog)
				Platform.runLater(() -> LogManager.removeTextAppendableFX(console));	
		}
	}

	
	/**
	 * Create default ScriptContext to run scripts.
	 * @return default script context
	 */
	public static ScriptContext createDefaultContext() {
		ScriptContext context = new SimpleScriptContext();
		context.setAttribute("args", new String[0], ScriptContext.ENGINE_SCOPE);
		context.setWriter(new LoggerInfoWriter());
		context.setErrorWriter(new LoggerErrorWriter());
		return context;
	}
	
	
	private abstract static class LoggerWriter extends Writer {

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
	public static Object executeScript(final RunnableLanguage language, final String script, final Project<BufferedImage> project, final ImageData<BufferedImage> imageData, final boolean importDefaultMethods, final ScriptContext context) throws ScriptException {
		if (importDefaultMethods)
			return language.executeScript(script, project, imageData, defaultClasses, defaultStaticClasses, context);
		return language.executeScript(script, project, imageData, new ArrayList<>(), new ArrayList<>(), context);					
	}
	
	static class ScriptObjectListCell extends ListCell<ScriptTab> {
        @Override
        public void updateItem(ScriptTab item, boolean empty) {
            super.updateItem(item, empty);
            if (item == null || empty) {
            	setText(null);
            	setTooltip(null);
             	return;
            }
            var text = item.toString();
            if (item.isRunning()) {
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
				tab.saveToFile(getCurrentText(), tab.getFile());
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
				// TODO: Allow multiple extensions to be used?
				File file = Dialogs.getChooser(dialog).promptToSaveFile("Save script file", dir, tab.getName(), currentLanguage.getValue().getName() + " file", tab.getRequestedExtensions()[0]);
				if (file == null)
					return false;
				tab.saveToFile(getCurrentText(), file);
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
			
			ScriptLanguage language = getSelectedLanguage();
			if (language == null)
				return;
//			if (language == Language.JAVA)
//				language = Language.GROOVY; // Replace Java with Groovy for scripting

			ScriptTab tab = getCurrentScriptObject();
			if (autoClearConsole.get() && getCurrentScriptObject() != null) {
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
	 * @param doSave 
	 */
	void handleRunProject(final boolean doSave) {
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			Dialogs.showNoProjectError("Script editor");
			return;
		}
		ScriptTab tab = getCurrentScriptObject();
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
		if (autoClearConsole.get() && getCurrentScriptObject() != null) {
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

	
	


	
	
//	Action createTabIndenterAction(final TextArea textArea, final boolean shiftDown) {
//		return new Action(e -> handleTabPress(textArea, shiftDown));
//	}
	
	
	
	/**
	 * Given a file name, determine the associated language - or null if no suitable (supported) language can be found.
	 * 
	 * @param name
	 * @return
	 */
	public static ScriptLanguage getLanguageFromName(String name) {
		for (ScriptLanguage l : availableLanguages) {
			for (String possibleExt: l.getExtensions()) {
				if (name.toLowerCase().endsWith(possibleExt))
					return l;
			}
		}
		return PlainLanguage.getInstance();
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
		Action action = new Action(name, e -> addNewScript("", getDefaultLanguage(null), true));
		action.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
		return action;
	}
	
	
	Action createCloseAction(final String name) {
		Action action = new Action(name, e -> {
			ScriptTab tab = getCurrentScriptObject();
			if (tab == null)
				return;
			promptToClose(tab);
		});
		action.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
		return action;
	}
	
	
	Action createSaveAction(final String name, final boolean saveAs) {
		Action action = new Action(name, e -> {
			ScriptTab tab = getCurrentScriptObject();
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
			ScriptTab tab = getCurrentScriptObject();
			if (tab != null) {
				tab.refreshFileContents();
				setToggle(tab.getLanguage());
			}
		});
		return action;
	}
	
	Action createFindAction(final String name) {
		ScriptFindCommand findCommand = new ScriptFindCommand(this);
		Action action = new Action(name, e -> {
			findCommand.run();
			e.consume();
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

			String join = "," + System.lineSeparator() + "  ";
			String listFormat = "[" + System.lineSeparator() + "  %s" + System.lineSeparator() + "]";
			if (name.toLowerCase().equals("pixel classifiers")) {
				try {
					String classifiers = qupath.getProject().getPixelClassifiers().getNames().stream()
							.map(classifierName -> "\"" + classifierName + "\"")
							.collect(Collectors.joining(join));
					String s = classifiers.isEmpty() ? "[]" : String.format(listFormat, classifiers);
					control.paste(s);
				} catch (IOException ex) {
					logger.error("Could not fetch classifiers", ex.getLocalizedMessage());
				}
			} else if (name.toLowerCase().equals("object classifiers")) {
				try {
					String classifiers = qupath.getProject().getObjectClassifiers().getNames().stream()
							.map(classifierName -> "\"" + classifierName + "\"")
							.collect(Collectors.joining(join));
					String s = classifiers.isEmpty() ? "[]" : String.format(listFormat, classifiers);
					control.paste(s);
				} catch (IOException ex) {
					logger.error("Could not fetch classifiers", ex.getLocalizedMessage());
				}
			} else if (name.toLowerCase().equals("detection")) {
				var imageData = qupath.getImageData();
				String measurements = "";
				if (imageData != null) {
					measurements = imageData.getHierarchy()
							.getDetectionObjects()
							.stream()
							.flatMap(d -> d.getMeasurementList().getMeasurementNames().stream())
							.distinct()
							.map(m -> "\"" + m + "\"")
							.collect(Collectors.joining(join))
							;
				}
				String s = measurements.isEmpty() ? "[]" : String.format(listFormat, measurements);
				control.paste(s);
			} else if (name.toLowerCase().equals(GeneralTools.SYMBOL_MU + ""))
				control.paste(GeneralTools.SYMBOL_MU + "");
			else {	
				// TODO: fix
//				// Imports (end with a new line)
//				if (name.toLowerCase().equals("qpex"))
//					control.insertText(0, "import static qupath.lib.gui.scripting.QPEx.*");
//				else if (name.toLowerCase().equals("qp"))
//					control.insertText(0, "import static qupath.lib.gui.scripting.QP.*");
//				else if (name.toLowerCase().equals("all default"))
//					control.insertText(0, QPEx.getDefaultImports(false)); 
//				currentLanguage.get().getSyntax().handleNewLine(control, smartEditing.get());
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
		while (promptToClose(getCurrentScriptObject()))
			continue;
	}
	
	boolean promptToClose(final ScriptTab tab) {
		int ind = listScripts.getItems().indexOf(tab);
		if (ind < 0)
			return false;
		
		// Check if we need to save
		if (tab.isModifiedProperty().get() && tab.hasScript()) {
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
//			dialog = null;
		}
		else
			listScripts.getSelectionModel().select(ind);
		return true;
	}

	@Override
	public void showEditor() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::showEditor);
			return;
		}

		if (dialog == null)
			createDialog();
		else {
			// Set explicitly, to avoid repositioning
			// If this isn't called, the dialog will be centered on screen
			dialog.setX(dialog.getX());
			dialog.setY(dialog.getY());
		}
		// Create a new script if we need one
		if (listScripts.getItems().isEmpty())
			showScript(null, null);
		if (!dialog.isShowing())
			dialog.show();
	}


	@Override
	public void showScript(String name, String script) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showScript(name, script));
			return;
		}
		if (dialog == null)
			createDialog();
		addNewScript(script, getDefaultLanguage(name), true);
		if (!dialog.isShowing())
			dialog.show();
	}
	
	
	private ScriptLanguage getDefaultLanguage(String fileName) {
		var ext = fileName == null ? null : GeneralTools.getExtension(fileName).orElse(null);
		if (ext == null) {
			if (availableLanguages.contains(GroovyLanguage.getInstance()))
					return GroovyLanguage.getInstance();
			return PlainLanguage.getInstance();
		}
		ext = ext.toLowerCase();
		for (var language : availableLanguages) {
			for (var ext2 : language.getExtensions()) {
				if (Objects.equals(ext, ext2.toLowerCase()))
					return language;
			}
		}
		return PlainLanguage.getInstance();
	}

	@Override
	public void showScript(File file) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showScript(file));
			return;
		}
		try {
			if (dialog == null)
				createDialog();
			addScript(file, true);
			if (!dialog.isShowing())
				dialog.show();
			
		} catch (Exception e) {
			logger.error("Could not load script from {}", file);
			logger.error("", e);
		}
	}
	

	/**
	 * Get the collection of classes to import at the start of a script, if desired.
	 * @return collection of default classes
	 */
	public static Collection<Class<?>> getDefaultClasses() {
		var out = QPEx.getCoreClasses();
		out.add(QPEx.class);	// Add itself
		return out;
	}
	
	/**
	 * Get the collection of static classes to import at the start of a script, if desired.
	 * @return collection of default static classes
	 */
	public static Collection<Class<?>> getDefaultStaticClasses() {
		List<Class<?>> out = new ArrayList<>();
		out.add(QPEx.class);
		return out;
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
