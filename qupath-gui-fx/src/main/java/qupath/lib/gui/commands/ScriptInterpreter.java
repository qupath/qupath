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

package qupath.lib.gui.commands;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;

import org.controlsfx.control.MasterDetailPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Skin;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import jfxtras.scene.layout.GridPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.ObjectTreeBrowser;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.images.ImageData;


/**
 * A simple command window to run scripts, using JSR 223
 * 
 * @author Pete Bankhead
 *
 */
class ScriptInterpreter {

	final private static Logger logger = LoggerFactory.getLogger(ScriptInterpreter.class);

	private QuPathGUI qupath;
	private Stage stage;
	private ScriptEngineManager manager;
	private ScriptContext preferredContext = new SimpleScriptContext(); // A preferred context for interoperability
	private ScriptContext context; // The context currently in use - may be locked to a specific engine
	private ScriptEngine engine;

	/**
	 * Name to use when creating ImageData variable
	 */
	private String defaultImageDataName = "imageData";
	private String defaultHierarchyName = "hierarchy";
	private String defaultSelectionModelName = "selectionModel";
	private String defaultImageServerName = "server";
	private String defaultProjectName = "project";
	private String defaultScriptingHelperName = "QP";

	/**
	 * Font for displaying code
	 */
	private Font font = Font.font("Courier");


	/**
	 * Different types of info that could be displayed for a variable
	 */
	private static enum VariableInfoType {NAME, CLASS, VALUE};

	/**
	 * List of all commands that were run
	 */
	private ObservableList<String> historyList = FXCollections.observableArrayList();

	/**
	 * All the command window text
	 */
	private StringProperty historyText = new SimpleStringProperty("");

	/**
	 * The text currently being entered
	 */
	private StringProperty currentText = new SimpleStringProperty("");

	/**
	 * Table displaying variables
	 */
	private TableView<String> tableVariables = new TableView<>();

	/**
	 * List displaying command history
	 */
	private ListView<String> listHistory = new ListView<>(historyList);

	/**
	 * For inputting new commands.
	 */
	private TextArea textAreaInput = new TextArea();

	ScriptInterpreter(final QuPathGUI qupath, final ClassLoader classLoader) {
		this.qupath = qupath;
		manager = new ScriptEngineManager(classLoader);

		setWriters(preferredContext);

		stage = new Stage();
		stage.setTitle("QuPath Interpreter");

		initialize();
	}


	private void initialize() {
		MenuBar menuBar = new MenuBar();
		Menu menuLanguages = new Menu("Language");
		menuBar.getMenus().add(menuLanguages);
		ToggleGroup group = new ToggleGroup();

		// Create menu items for each scripting language available
		// Attempt to 'sanitize' the names
		for (ScriptEngineFactory factory : manager.getEngineFactories()) {
			//				logger.info(factory.getEngineName() + ", " + factory.getEngineVersion());
			//				logger.info(factory.getLanguageName() + ", " + factory.getLanguageVersion());
			logger.info("{}", factory.getNames());

			String name;
			List<String> allNames = factory.getNames();
			if (allNames.contains("groovy"))
				name = "Groovy";
			else if (allNames.contains("jython"))
				name = "Jython";
			else if (allNames.contains("python"))
				name = "Python";
			else if (allNames.contains("r"))
				name = "R";
			else if (allNames.contains("ruby"))
				name = "Ruby";
			else if (allNames.contains("javascript"))
				name = "JavaScript";
			else
				name = factory.getLanguageName();

			RadioMenuItem menuItem = new RadioMenuItem(name);
			menuItem.setToggleGroup(group);
			menuItem.selectedProperty().addListener((o, v, n) -> {
				if (n) {
					engine = factory.getScriptEngine();
					try {
						engine.setContext(preferredContext);
						context = preferredContext;
					} catch (Exception e) {
						logger.warn("Could not set preferred script context for {}: {}", engine, e.getLocalizedMessage());
						context = engine.getContext();
						setWriters(context);
					}
					updateVariableTable();
				}
			});
			if (group.getSelectedToggle() == null || "Groovy".equals(name)) // Prefer Groovy if we can get it
				group.selectToggle(menuItem);
			menuLanguages.getItems().add(menuItem);
		}
		FXCollections.sort(menuLanguages.getItems(), (m1, m2) -> m1.getText().compareTo(m2.getText()));


		// Script menu
		Menu menuScript = new Menu("Script");
		MenuItem miGenerateScript = new MenuItem("Generate script");
		miGenerateScript.setOnAction(e -> {
			String script = String.join("\n", historyList);
			qupath.getScriptEditor().showScript("From interpreter", script);
		});
		menuScript.getItems().add(miGenerateScript);
		menuBar.getMenus().add(menuScript);


		/*
		 * Create the main area
		 */
		// Simple TextArea-based command log (no syntax coloring)
		//			TextArea textArea = new TextArea();
		//			textArea.setPrefColumnCount(50);
		//			textArea.setPrefRowCount(40);
		//			textArea.textProperty().bindBidirectional(historyText);
		//			textArea.setWrapText(true);
		//			textArea.setFont(font);
		//			textArea.setEditable(false);
		//			textArea.textProperty().addListener((v, o, n) -> {
		////				if (textArea.getSelection().getLength() == 0) {
		////					textArea.setScrollTop(Double.MAX_VALUE);
		////				}
		//				Platform.runLater(() -> textArea.appendText(""));
		//			});

		// Command log with some color coding
		WebView textArea = new WebView();
		historyText.addListener((v, o, n) -> {
			String styleAll = "* {\n  " + 
					"font-family: \"Courier New\", Courier, monospace;\n" + 
					"font-size: 0.95em;\n " + 
					"}";
			String styleError = ".error {\n  " + 
					"color: red;\n" + 
					"}";
			String styleWarning = ".warning {\n  " + 
					"color: orange;\n" + 
					"}";
			String styleCommand = ".command {\n  " + 
					"color: black;\n" + 
					"}";
			String styleOther = ".other {\n  " + 
					"color: gray;\n" + 
					"}";
			String styleVariable = ".variable {\n  " + 
					"color: purple;\n" + 
					"}";

			StringBuilder sb = new StringBuilder();
			sb.append("<html>").append("\n");
			sb.append("<head>").append("\n");
			sb.append("<script language=\"javascript\" type=\"text/javascript\">").append("\n");
			sb.append("function scrollToEnd(){").append("\n");
			sb.append("window.scrollTo(0, document.body.scrollHeight);").append("\n");
			sb.append("}").append("\n");
			sb.append("</script>").append("\n");
			sb.append("<style>").append("\n");
			sb.append(styleAll).append("\n");
			sb.append(styleError).append("\n");
			sb.append(styleWarning).append("\n");
			sb.append(styleCommand).append("\n");
			sb.append(styleOther).append("\n");
			sb.append(styleVariable).append("\n");
			sb.append("</style>").append("\n");
			sb.append("</head>").append("\n");
			sb.append("<body onload='scrollToEnd()'>").append("\n");
			sb.append(n.replace("\n", "<br/>"));
			sb.append("</body>");

			//				String content = String.format("<html><head><style>%s</style></head><body>%s</body></html>", style, n.replace("\n", "<br/>"));

			textArea.getEngine().loadContent(sb.toString());
		});

		//			TextArea textArea = new TextArea();
		//			textArea.setPrefColumnCount(50);
		//			textArea.setPrefRowCount(40);
		//			textArea.textProperty().bindBidirectional(historyText);
		//			textArea.setWrapText(true);
		//			textArea.setFont(font);
		//			textArea.setEditable(false);
		//			textArea.textProperty().addListener((v, o, n) -> {
		////				if (textArea.getSelection().getLength() == 0) {
		////					textArea.setScrollTop(Double.MAX_VALUE);
		////				}
		//				Platform.runLater(() -> textArea.appendText(""));
		//			});



		// Input
		textAreaInput = new TextArea();
		textAreaInput.setPrefRowCount(4);
		textAreaInput.textProperty().bindBidirectional(currentText);
		textAreaInput.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (e.getCode() == KeyCode.ENTER) {
				runLine();
				e.consume();
			}
		});
		textAreaInput.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
			if (e.getCode() == KeyCode.ENTER) {
				return; // Already handled with key press
			} else if (e.getCode() == KeyCode.UP || (e.getCode() == KeyCode.DOWN)) {
				// Don't do anything if we're using autocomplete
				if (menuAutocomplete.isShowing())
					return;
				if (e.getCode() == KeyCode.UP) {
					if (!decrementHistoryPointer()) {
						e.consume();
						return;
					}
				}
				else {
					if (!incrementHistoryPointer()) {
						e.consume();
						return;
					}
				}
				//					textAreaInput.setText(listHistory.getSelectionModel().getSelectedItem());
				//					textAreaInput.appendText(""); // To move caret
				e.consume();
				return;
			}
			//				resetHistoryPointer();
			if (menuAutocomplete.isShowing() || (e.isControlDown() && e.getCode() == KeyCode.SPACE)) {
				updateAutocompleteMenu();
				// Using reflection for compatibility with Java 8 and Java 9
				Skin<?> skin = textAreaInput.getSkin();
				Bounds b = null;
				try {
					b = (Bounds)skin.getClass().getMethod("getCaretBounds").invoke(skin);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
						| NoSuchMethodException | SecurityException e1) {
					logger.error("Error requesting caret bounds - cannot display autocomplete menu", e1);
				}
				//					TextAreaSkin skin = (TextAreaSkin)textAreaInput.getSkin();
				//					Bounds b = skin.getCaretBounds();
				// If there's only one open, and we aren't already showing, just use it
				if (!menuAutocomplete.isShowing() && menuAutocomplete.getItems().size() == 1) {
					menuAutocomplete.getItems().get(0).fire();
					e.consume();
					return;
				}

				if (b != null && !menuAutocomplete.getItems().isEmpty()) {
					menuAutocomplete.show(textAreaInput,
							Side.TOP,
							b.getMaxX()+5,
							b.getMaxY()+5);
				}
			} else if (menuAutocomplete.isShowing() && e.getCode() == KeyCode.TAB) {
				menuAutocomplete.hide();
				e.consume();
			}
		});
		textAreaInput.addEventHandler(KeyEvent.KEY_RELEASED, e -> {
			if (!e.isConsumed())
				updateLastHistoryListEntry();
		});
		historyList.add("");
		//			currentText.addListener((v, o, n) -> {
		//				historyList.set(historyList.size()-1, n);
		//			}); // Keep last entry updated
		textAreaInput.setFont(font);

		// Create the variable table
		TableColumn<String, String> colKeys = new TableColumn<>("Name");
		colKeys.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()));
		colKeys.setCellFactory(c -> new VariableTableCell(VariableInfoType.NAME));

		TableColumn<String, String> colClasses = new TableColumn<>("Class");
		colClasses.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()));
		colClasses.setCellFactory(c -> new VariableTableCell(VariableInfoType.CLASS));

		TableColumn<String, String> colValues = new TableColumn<>("Value");
		colValues.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()));
		colValues.setCellFactory(c -> new VariableTableCell(VariableInfoType.VALUE));

		tableVariables.setPlaceholder(new Label("No variables set"));
		tableVariables.getColumns().add(colKeys);
		tableVariables.getColumns().add(colClasses);
		tableVariables.getColumns().add(colValues);
		tableVariables.setTableMenuButtonVisible(true);
		tableVariables.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		Button btnClear = new Button("Clear variables");
		btnClear.setOnAction(e -> {
			Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
			List<String> variables = getSelectedVariableNames();
			if (variables.isEmpty())
				variables = getCurrentVariableNames();
			if (variables.isEmpty())
				return;
			if (variables.size() == 1) {
				if (!Dialogs.showConfirmDialog("Clear variables", "Clear variable '" + variables.get(0) + "'?"))
					return;
			} else {
				if (!Dialogs.showConfirmDialog("Clear variables", "Clear " + variables.size() + " variables?"))
					return;
			}
			for (String v : variables)
				bindings.remove(v);
			logger.info("Removed variables from interpreter workspace: {}", variables);
			updateVariableTable();
		});
		btnClear.setMaxWidth(Double.MAX_VALUE);
		btnClear.disableProperty().bind(
				javafx.beans.binding.Bindings.createBooleanBinding(() -> tableVariables.getItems().isEmpty(), tableVariables.getItems())
				);

		Button btnAdd = new Button("Add...");
		ContextMenu menuAdd = new ContextMenu();
		MenuItem miAddImageData = new MenuItem("Image data");
		miAddImageData.setOnAction(e -> {
			ImageData<?> imageData = qupath.getImageData();
			engine.put(defaultImageDataName, imageData);
			updateVariableTable();
		});
		MenuItem miAddHierarchy = new MenuItem("Hierarchy");
		miAddHierarchy.setOnAction(e -> {
			ImageData<?> imageData = qupath.getImageData();
			engine.put(defaultHierarchyName, imageData == null ? null : imageData.getHierarchy());
			updateVariableTable();
		});
		MenuItem miAddSelectionModel = new MenuItem("Selection model");
		miAddSelectionModel.setOnAction(e -> {
			ImageData<?> imageData = qupath.getImageData();
			engine.put(defaultSelectionModelName, imageData == null ? null : imageData.getHierarchy().getSelectionModel());
			updateVariableTable();
		});
		MenuItem miAddServer = new MenuItem("Image server");
		miAddServer.setOnAction(e -> {
			ImageData<?> imageData = qupath.getImageData();
			engine.put(defaultImageServerName, imageData == null ? null : imageData.getServer());
			updateVariableTable();
		});
		MenuItem miAddProject = new MenuItem("Project");
		miAddProject.setOnAction(e -> {
			engine.put(defaultProjectName, qupath.getProject());
			updateVariableTable();
		});
		MenuItem miAddScriptingHelpers = new MenuItem("Scripting helpers");
		miAddScriptingHelpers.setOnAction(e -> {
			engine.put(defaultScriptingHelperName, new QPEx());
			updateVariableTable();
		});
		menuAdd.getItems().addAll(miAddImageData, miAddHierarchy, miAddSelectionModel, miAddServer, miAddProject, miAddScriptingHelpers);
		btnAdd.setOnMouseClicked(e -> {
			menuAdd.show(btnAdd, e.getScreenX(), e.getScreenY());
		});
		btnAdd.setMaxWidth(Double.MAX_VALUE);

		GridPane paneTable = new GridPane();
		paneTable.add(tableVariables, 0, 0, 2, 1);
		paneTable.add(btnAdd, 0, 1, 1, 1);
		paneTable.add(btnClear, 1, 1, 1, 1);
		GridPane.setVgrow(tableVariables, Priority.ALWAYS);
		GridPane.setHgrow(tableVariables, Priority.ALWAYS);
		GridPane.setHgrow(btnAdd, Priority.ALWAYS);
		GridPane.setHgrow(btnClear, Priority.ALWAYS);
		ColumnConstraints col1 = new ColumnConstraints();
		col1.setPercentWidth(50);
		ColumnConstraints col2 = new ColumnConstraints();
		col2.setPercentWidth(50);
		paneTable.getColumnConstraints().addAll(col1, col2);


		// Handle list
		listHistory.setEditable(false);
		listHistory.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		listHistory.setStyle("-fx-font-family: \"Courier New\", Courier, monospace;");
		listHistory.getSelectionModel().selectedIndexProperty().addListener((v, o, n) -> {
			if (o.intValue() < 0 || n.intValue() < 0)
				return;
			textAreaInput.setText(listHistory.getSelectionModel().getSelectedItem());
			textAreaInput.appendText(""); // To move caret
			//				e.consume();
			//				return;
		});


		// Create tabbed pane
		TabPane tabPane = new TabPane();
		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		tabPane.getTabs().add(new Tab("Variables", paneTable));
		tabPane.getTabs().add(new Tab("History", listHistory));


		// Set the stage
		BorderPane pane = new BorderPane();
		MasterDetailPane paneMasterDetail = new MasterDetailPane(Side.LEFT);
		BorderPane paneInner = new BorderPane();
		paneInner.setCenter(textArea);
		paneInner.setBottom(textAreaInput);
		paneMasterDetail.setMasterNode(paneInner);
		paneMasterDetail.setDetailNode(tabPane);
		pane.setTop(menuBar);
		pane.setCenter(paneMasterDetail);
		//			menuBar.setUseSystemMenuBar(true);
		menuBar.useSystemMenuBarProperty().bindBidirectional(PathPrefs.useSystemMenubarProperty());

		stage.setScene(new Scene(pane, 800, 600));
		textAreaInput.requestFocus();
		//			paneMasterDetail.setDividerPosition(350);

	}


	private void setWriters(final ScriptContext context) {
		context.setWriter(new Writer() {

			@Override
			public void write(char[] cbuf, int off, int len) throws IOException {
				addToCommandWindow(String.copyValueOf(cbuf, off, len));
			}

			@Override
			public void flush() throws IOException {}

			@Override
			public void close() throws IOException {}

		});
		context.setErrorWriter(new Writer() {

			@Override
			public void write(char[] cbuf, int off, int len) throws IOException {
				addToCommandWindow("Error: " + String.copyValueOf(cbuf, off, len));
			}

			@Override
			public void flush() throws IOException {}

			@Override
			public void close() throws IOException {}

		});
	}


	//		private void addToCommandWindow(final String newText) {
	//			historyText.set(historyText.get() + "\n" + newText);
	//		}


	private void addToCommandWindow(final String newText) {
		String lower = newText.toLowerCase();
		if (lower.startsWith("error:"))
			historyText.set(historyText.get() + "<pre class=\"error\">" + newText + "</pre>");
		else if (lower.startsWith("warn"))
			historyText.set(historyText.get() + "<pre class=\"warning\">" + newText + "</pre>");
		else if (lower.startsWith("-------"))
			historyText.set(historyText.get() + "<pre class=\"variable\">" + newText + "</pre>");
		else if (lower.startsWith("> "))
			historyText.set(historyText.get() + "<pre class=\"command\">" + newText + "</pre>");
		else
			historyText.set(historyText.get() + "<pre class=\"other\">" + newText + "</pre>");

		//			if (lower.startsWith("error:"))
		//				historyText.set(historyText.get() + "<pre style=\"color:red\">" + newText + "</pre>");
		//			else if (lower.startsWith("warn"))
		//				historyText.set(historyText.get() + "<pre style=\"color:orange\">" + newText + "</pre>");
		//			else if (lower.startsWith("-------"))
		//				historyText.set(historyText.get() + "<pre style=\"color:purple\">" + newText + "</pre>");
		//			else if (!lower.startsWith("> "))
		//				historyText.set(historyText.get() + "<pre style=\"color:grey\">" + newText + "</pre>");
		//			else
		//				historyText.set(historyText.get() + "<pre>" + newText + "</pre>");
	}



	/**
	 * Synchronize the last entry in the history list to the current text in the command entry box
	 */
	private void updateLastHistoryListEntry() {
		historyList.set(historyList.size()-1, textAreaInput.getText()); // Keep last entry updated
	}

	/**
	 * Get names of variables currently selected in the variable table
	 * @return
	 */
	private ObservableList<String> getSelectedVariableNames() {
		return tableVariables.getSelectionModel().getSelectedItems();
	}

	/**
	 * Get names of all available variables from the variable table (call updateVariableTable() first if necessary)
	 * @return
	 */
	private ObservableList<String> getCurrentVariableNames() {
		return tableVariables.getItems();
	}

	private void updateVariableTable() {
		Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		List<String> variableNames = bindings.keySet().stream().filter(v -> !v.equals("__builtins__")).collect(Collectors.toList());

		// Globals
		try {
			bindings = engine.getBindings(ScriptContext.GLOBAL_SCOPE);
			if (bindings != null)
				variableNames.addAll(bindings.keySet());
		} catch (Exception e) {
			logger.debug("Unable to obtain global variables for {}", engine);
		}

		Collections.sort(variableNames);
		tableVariables.getItems().setAll(variableNames);
	}


	private Object getVariable(final String name) {
		if (name == null || name.isEmpty())
			return null;
		return engine == null ? null : engine.get(name);
		//			Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		//			if (bindings == null)
		//				bindings = engine.getBindings(ScriptContext.GLOBAL_SCOPE);
		//        	return bindings == null ? null : bindings.get(name);
	}


	private ContextMenu menuAutocomplete = new ContextMenu();

	private void updateAutocompleteMenu() {

		menuAutocomplete.setStyle("-fx-font-size: 0.8em");
		menuAutocomplete.setOpacity(0.9);
		menuAutocomplete.setMaxHeight(200);

		menuAutocomplete.hide();
		menuAutocomplete.getItems().clear();
		String text = currentText.get();
		if (text.trim().isEmpty()) {
			return;
		}

		boolean breakAtDot = false;
		int ind = text.length()-1;
		String delimiters = " \t;:(){}";
		while (ind >= 0) {
			char c = text.charAt(ind);
			if (c == '.') {
				breakAtDot = true;
				break;
			} else if (delimiters.indexOf(c) >= 0)
				break;
			ind--;
		}

		// Set the starting index for any completion
		final int startInd = ind+1;
		List<MenuItem> items = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		if (breakAtDot) {
			String current = text.substring(startInd);
			if (ind == 0)
				return;
			// Try to get a variable name
			ind--;
			while (ind >= 0) {
				char c = text.charAt(ind);
				if (delimiters.indexOf(c) >= 0)
					break;
				ind--;
			}
			String lastName = text.substring(ind+1, startInd-1);
			Object o = getVariable(lastName);
			if (o != null) {
				// Autocomplete using methods/fields
				for (Field f : o.getClass().getFields()) {
					String fieldName = f.getName();
					if (fieldName.startsWith(current)) {
						items.add(getCompletionMenuItem(fieldName, text, startInd, fieldName, null));
					}
				}

				for (Method m : o.getClass().getMethods()) {
					String methodName = m.getName();
					if (methodName.startsWith(current)) {
						sb.setLength(0);
						sb.append(methodName).append("(");
						int count = 0;
						int n = m.getParameterCount();
						for (Parameter p : m.getParameters()) {
							count++;
							sb.append(p.getType().getSimpleName());
							sb.append(" ");
							sb.append(p.getName());
							if (count < n)
								sb.append(", ");
						}
						sb.append(")");
						String completion;
						if (n == 0)
							completion = methodName + "()";
						else
							completion = methodName + "(";

						items.add(getCompletionMenuItem(sb.toString(), text, startInd, completion, new Text(m.getReturnType().getSimpleName())));
					}
				}

			}
		} else {
			// Try to autocomplete using an existing variable name
			String current = text.substring(startInd);
			for (String v : getCurrentVariableNames()) {
				if (v.startsWith(current)) {
					items.add(getCompletionMenuItem(v, text, startInd, v, null));
				}
			}				
		}
		if (items.isEmpty()) {
			return;
		}
		menuAutocomplete.getItems().setAll(items);
	}


	private MenuItem getCompletionMenuItem(final String name, final String oldText, final int startInd, final String completion, final Node graphic) {
		Label label = new Label(name);
		label.setMaxWidth(Double.POSITIVE_INFINITY);
		CustomMenuItem item = new CustomMenuItem(label);
		item.setOnAction(e -> {
			currentText.set(oldText.substring(0, startInd) + completion);
			textAreaInput.appendText("");
			updateLastHistoryListEntry();
		});
		if (graphic != null) {
			label.setContentDisplay(ContentDisplay.RIGHT);
			label.setGraphic(graphic);
		}
		return item;
	}



	private boolean incrementHistoryPointer() {
		int historyPointer = listHistory.getSelectionModel().getSelectedIndex();
		if (historyPointer < historyList.size() - 1) {
			listHistory.getSelectionModel().select(historyPointer + 1);
			return true;
		}
		return false;
	}

	private boolean decrementHistoryPointer() {
		int historyPointer = listHistory.getSelectionModel().getSelectedIndex();
		if (historyPointer > 0) {
			listHistory.getSelectionModel().select(historyPointer - 1);
			return true;
		}
		return false;
	}

	private void resetHistoryPointer() {
		listHistory.getSelectionModel().select(historyList.size()-1);
	}


	private void runLine() {
		String text = currentText.get();
		if (text != null && text.length() > 0) {
			addToCommandWindow("> " + text);
			String lastHistory = historyList.get(historyList.size()-1);
			if (lastHistory != null && !lastHistory.isEmpty())
				historyList.add(""); // Create a new history entry
			resetHistoryPointer();
			try {
				Object result = engine.eval(text, context);
				if (result == null)
					addToCommandWindow("");
				else
					addToCommandWindow("" + result + "\n");
			} catch (Exception e) {
				logger.error("Script error", e);
				addToCommandWindow("Error: " + e.getLocalizedMessage() + "\n");
			} finally {
				currentText.set("");
				updateVariableTable();
			}
		}
	}

	public Stage getStage() {
		return stage;
	}


	class VariableTableCell extends TableCell<String, String> {

		private VariableInfoType type;
		private Tooltip tooltip = new Tooltip();
		private StringBuilder sb = new StringBuilder();

		VariableTableCell(final VariableInfoType type) {
			this.type = type;
		}

		@Override
		public void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			if (empty) {
				setText(null);
				setTooltip(null);
			} else {
				Object value = getVariable(item);
				switch (type) {
				case CLASS:
					setText(value == null ? "-" : value.getClass().getSimpleName());
					break;
				case NAME:
					setText(item);
					break;
				case VALUE:
					setText(String.valueOf(value));
					break;
				default:
					setText(item);
				}

				String text = item;
				if (value != null) {
					sb.setLength(0);
					sb.append("Name:\t").append(item).append("\n");
					sb.append("Class:\t").append(value.getClass().getName()).append("\n");
					sb.append("Value:\t").append(value);
					//			        	sb.append("Value:\t").append(value).append("\n");

					//			        	for (Method m : value.getClass().getMethods()) {
					//			        		sb.append("\n");
					//			        		sb.append(m.getReturnType()).append(" ");
					//			        		sb.append(m.getName()).append("(");
					//			        		int count = 0;
					//			        		int n = m.getParameterCount();
					//			        		for (Class<?> c : m.getParameterTypes()) {
					//			        			count++;
					//				        		sb.append(c.getSimpleName());
					//				        		if (count < n)
					//					        		sb.append(", ");
					//			        		}
					//			        		sb.append(")");
					//			        	}
					text = sb.toString();

					setOnMouseClicked(e -> {
						if (e.getClickCount() == 2) {


							var treeView = ObjectTreeBrowser.createObjectTreeBrowser(item, value);
							Stage stage = new Stage();
							stage.setTitle("Object Inspector: " + item);
							stage.initOwner(qupath.getStage());
							stage.setScene(new Scene(new BorderPane(treeView), 400, 400));
							stage.show();


							sb.setLength(0);
							sb.append("---------------------------------------------\n");
							sb.append("NAME: \t").append(item);
							sb.append("\n");
							sb.append("VALUE: \t").append(String.valueOf(value));
							sb.append("\n");
							sb.append("CLASS: \t").append(value.getClass().getName());
							sb.append("\n");
							//			        			sb.append("---------------------------------------------\n");
							Field[] fields = value.getClass().getFields();
							if (fields.length > 0) {
								sb.append("FIELDS:\n");
								for (Field f : value.getClass().getFields()) {
									try {
										Object innerValue = f.get(value);
										sb.append("  ");
										sb.append(f.getName());
										sb.append(": ");
										sb.append("\t");
										sb.append(String.valueOf(innerValue));
										sb.append("\n");
									} catch (Exception e1) {
										logger.trace("Could not find value for field {}", f.getName());
									}
								}
							}
							sb.append("METHODS:\n");
							for (Method m : value.getClass().getMethods()) {
								sb.append("  ");
								sb.append(m.getReturnType().getSimpleName());
								sb.append("  ");
								sb.append(m.getName());
								sb.append("(");
								int count = 0;
								int n = m.getParameterCount();
								for (Parameter p : m.getParameters()) {
									count++;
									sb.append(p.getType().getSimpleName());
									sb.append(" ");
									sb.append(p.getName());
									if (count < n)
										sb.append(", ");
								}
								sb.append(")\n");
							}
							sb.append("---------------------------------------------\n");

							addToCommandWindow(sb.toString());
						}
					});

				} else
					setOnMouseClicked(null);
				tooltip.setText(text);
				setTooltip(tooltip);
			}
		}

	}

}