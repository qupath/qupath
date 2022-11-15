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

package qupath.lib.gui.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.controlsfx.control.HiddenSidesPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.skin.TableColumnHeader;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;


/**
 * 
 * Helper tools for creating components that enable fast access to menu commands via a filtered list.
 * 
 * @author Pete Bankhead
 *
 */
public class CommandFinderTools {
	
	/**
	 * Available modes for displaying the command bar.
	 */
	public enum CommandBarDisplay {
		/**
		 * Always display
		 */
		ALWAYS,
		/**
		 * Never display
		 */
		NEVER,
		/**
		 * Display only when the cursor hovers nearby
		 */
		HOVER;
		
		@Override
		public String toString() {
			switch(this) {
			case ALWAYS:
				return "Always";
			case HOVER:
				return "When cursor near";
			case NEVER:
				return "Never";
			default:
				return super.toString();
			}
		}
		
	};
	
	private static BooleanProperty autoCloseCommandListProperty = PathPrefs.createPersistentPreference("autoCloseCommandList", true); // Return to the pan tool after drawing a ROI
	

	
	private static ObjectProperty<CommandBarDisplay> commandBarDisplay = PathPrefs.createPersistentPreference("commandFinderDisplayMode", CommandBarDisplay.NEVER, CommandBarDisplay.class);
	
	/**
	 * Property specifying where the command bar should be displayed relative to the main viewer window.
	 * @return
	 */
	public static ObjectProperty<CommandBarDisplay> commandBarDisplayProperty() {
		return commandBarDisplay;
	}
	
	
	
	/**
	 * Create a component that contains a {@link TextField} for entering menu commands to run quickly.
	 * 
	 * This component is a container that holds a main {@link Node}, and displays the {@link TextField} only when requested.
	 * 
	 * @param qupath
	 * @param node
	 * @param displayMode
	 * @return
	 */
	public static HiddenSidesPane createCommandFinderPane(final QuPathGUI qupath, final Node node, final ObjectProperty<CommandBarDisplay> displayMode) {
		MenuManager menuManager = MenuManager.getInstance(qupath.getMenuBar());
		
		FilteredList<CommandEntry> commands = new FilteredList<>(menuManager.getCommands());
		TableView<CommandEntry> table = createCommandTable(commands);
		TextField textField = createTextField(table, commands, true, null, null);
		
		BorderPane paneCommands = new BorderPane();
		paneCommands.setPadding(new Insets(5, 10, 5, 10));
		paneCommands.setCenter(textField);
		
		Popup popup = new Popup();
		textField.textProperty().addListener((v, o, n) -> {
			// Ensure the table is up to date if we are just starting
			if (o.isEmpty() && !n.isEmpty())
				menuManager.refresh(true);
			
			if (n.trim().isEmpty())
				popup.hide();
			else {
				Bounds bounds = textField.localToScreen(textField.getBoundsInLocal());
				popup.show(textField, bounds.getMinX(), bounds.getMaxY());			
			}
		});
		textField.focusedProperty().addListener((v, o, n) -> {
			if (!n)
				popup.hide();
		});
		

		table.setOnMouseClicked(e -> {
			if (!e.isConsumed() && e.getClickCount() > 1) {
				if (runSelectedCommand(table.getSelectionModel().getSelectedItem()))
					textField.clear();
			}
		});
		table.prefWidthProperty().bind(textField.widthProperty());

		popup.getContent().add(table);
		
		table.getStylesheets().add(CommandFinderTools.class.getClassLoader().getResource("css/table_without_header.css").toExternalForm());
		
		
		DoubleBinding opacityBinding = Bindings.createDoubleBinding(() -> {
				if (textField.isFocused())
					return 0.9;
				else
					return 0.75;
				},
				textField.focusedProperty());
		textField.opacityProperty().bind(opacityBinding);
		
		
		HiddenSidesPane paneViewer = new HiddenSidesPane();
		paneViewer.pinnedSideProperty().bind(
				Bindings.createObjectBinding(() -> {
					switch (displayMode.get()) {
					case ALWAYS:
						return Side.TOP;
					case NEVER:
						return null;
					default:
						return textField.isFocused() ? Side.TOP : null;
					}
				}, textField.focusedProperty(), displayMode)
			);
		
		displayMode.addListener((v, o, n) -> {
			if (n == CommandBarDisplay.NEVER)
				paneViewer.setTop(null);
			else
				paneViewer.setTop(paneCommands);				
		});

		if (displayMode.get() != CommandBarDisplay.NEVER)
			paneViewer.setTop(paneCommands);
		paneViewer.setContent(node);
		
		commandBarDisplay.addListener((v, o, n) -> {
			var viewers = qupath.getViewers();
			for (var viewer: viewers) {
				viewer.setSlidersPosition(!n.equals(CommandBarDisplay.NEVER));
			}
		});
		
		return paneViewer;
	}
	
	
	
	/**
	 * Create a dialog showing a filtered list of menu commands, for fast selection.
	 * 
	 * @param qupath
	 * @return
	 */
	public static Stage createCommandFinderDialog(final QuPathGUI qupath) {
		return createCommandFinderDialog(qupath.getMenuBar(), qupath.getStage());
	}
	
	/**
	 * Create a dialog showing a filtered list of recently-used commands, for fast selection.
	 * 
	 * @param qupath
	 * @return
	 */
	public static Stage createRecentCommandsDialog(final QuPathGUI qupath) {
		return createRecentCommandDialog(qupath.getMenuBar(), qupath.getStage());
	}
	
	private static Stage createRecentCommandDialog(final MenuBar menubar, final Window owner) {
		// TODO: Explore updating this with the action list changes
		MenuManager menuManager = MenuManager.getInstance(menubar);
		menuManager.refresh(true);

		Stage stage = new Stage();
		stage.initOwner(owner);
		stage.setTitle("Recent Commands");

		FilteredList<CommandEntry> commands = new FilteredList<>(menuManager.getRecentCommands());
		TableView<CommandEntry> table = createCommandTable(commands);
		// Don't make recent command sortable
		for (var col : table.getColumns())
			col.setSortable(false);
		
		TextField textField = createTextField(table, commands, false, stage, null);
		textField.setPromptText("Search recent commands");

		// Control focus of the text field
		stage.focusedProperty().addListener((v, o, n) -> {
			if (n) {
				table.requestFocus();
				if (!table.getItems().isEmpty() && textField.textProperty().isEmpty().get())
					table.getSelectionModel().selectLast();
			}
		});
		
		commands.addListener((Change<? extends CommandEntry> c) -> {
			if (table.isVisible() && !table.getItems().isEmpty() && textField.textProperty().isEmpty().get()) {
				table.scrollTo(table.getItems().size()-1);
				table.getSelectionModel().selectLast();
			}
		});

		// Make it possible to type immediately (alternative would be to reset text when hiding)
		stage.setOnShown(e -> textField.selectAll());

		BorderPane pane = new BorderPane();
		pane.setCenter(table);
		pane.setBottom(textField);

		stage.setScene(new Scene(pane, 600, 400));

		stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				stage.hide();
				e.consume();
			}
		});

		textField.textProperty().addListener((v, o, n) -> {
			// Ensure the table is up to date if we are just starting
			if (o.isEmpty() && !n.isEmpty())
				menuManager.refresh(true);
		});

		table.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1 && !(e.getTarget() instanceof TableColumnHeader)) {
				runSelectedCommand(table.getSelectionModel().getSelectedItem());
			}
		});

		return stage;
	}

	
	private static Stage createCommandFinderDialog(final MenuBar menubar, final Window owner) {
		// TODO: Explore updating this with the action list changes
		MenuManager menuManager = MenuManager.getInstance(menubar);
		menuManager.refresh(true);
		
		Stage stage = new Stage();
		stage.initOwner(owner);
		stage.setTitle("Command List");
		
		CheckBox cbAutoClose = new CheckBox("Auto close");
		cbAutoClose.selectedProperty().bindBidirectional(autoCloseCommandListProperty);
		cbAutoClose.setPadding(new Insets(2, 2, 2, 2));

		FilteredList<CommandEntry> commands = new FilteredList<>(menuManager.getCommands());			
		TableView<CommandEntry> table = createCommandTable(commands);
		TextField textField = createTextField(table, commands, false, stage, cbAutoClose.selectedProperty());
		textField.setPromptText("Search all commands");
		
		// Control focus of the text field
		// This actually controls the table itself
		stage.focusedProperty().addListener((v, o, n) -> {
			if (n)
				textField.requestFocus();
		});
		
		// Make it possible to type immediately (alternative would be to reset text when hiding)
		stage.setOnShown(e -> textField.selectAll());

		BorderPane panelSearch = new BorderPane();
		panelSearch.setCenter(textField);
		panelSearch.setRight(cbAutoClose);		
		
		BorderPane pane = new BorderPane();
		pane.setCenter(table);
		pane.setBottom(panelSearch);
		
		stage.setScene(new Scene(pane, 600, 400));
		
		stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
	        if (e.getCode() == KeyCode.ESCAPE) {
	        	stage.hide();
	        	e.consume();
	        }
		});
		
		textField.textProperty().addListener((v, o, n) -> {
			// Ensure the table is up to date if we are just starting
			if (o.isEmpty() && !n.isEmpty())
				menuManager.refresh(true);
		});

		table.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1 && !(e.getTarget() instanceof TableColumnHeader)) {
				if (runSelectedCommand(table.getSelectionModel().getSelectedItem())) {
					if (cbAutoClose.isSelected()) {
						stage.hide();
					}
				}
			}
		});

		return stage;
	}
	
	
	/**
	 * Create a markdown representation of the menus for inclusion in the documentation, 
	 * using the current QuPath instance.
	 * @return 
	 * @throws IOException
	 */
	public static String menusToMarkdown() throws IOException {
		var qupath = QuPathGUI.getInstance();
		Objects.requireNonNull(qupath, "No QuPath instance!");
		var writer = new StringWriter();
		menusToMarkdown(qupath, writer);
		return writer.toString();
	}
	
	/**
	 * Write a markdown representation of the menus for inclusion in the documentation.
	 * @param qupath
	 * @param writer
	 * @throws IOException
	 */
	public static void menusToMarkdown(final QuPathGUI qupath, Writer writer) throws IOException {
		MenuManager menuManager = MenuManager.getInstance(qupath.getMenuBar());
		menuManager.refresh(false);
		PrintWriter printWriter = toPrintWriter(writer);
		String lastMenu = null;
		for (var item : menuManager.getCommands()) {
			String menuPath = item.getMenuPath();
			if (menuPath != null) {
				String menu = menuPath.split("\u2192")[0].strip();
				if (!Objects.equals(menu, lastMenu)) {
					printWriter.println("## " + menu);
					lastMenu = menu;
				}
			}
			toMarkdown(item, printWriter);
		}
		printWriter.flush();
	}

	
	private static PrintWriter toPrintWriter(Writer writer) {
		return writer instanceof PrintWriter ? (PrintWriter)writer : new PrintWriter(writer);
	}
	
	private static void toMarkdown(CommandEntry entry, PrintWriter writer) {
		
		String title = "### " + entry.getText();
		writer.println(title);

		String subtitle = entry.getCommandPath();
		writer.print("{menuselection}`" + subtitle.replaceAll("\u2192", "-->") + "`");
		String accelerator = entry.getAcceleratorText();
		if (worthwhile(accelerator)) {
			String a = entry.getMenuItem().getAccelerator().toString();
			writer.print(String.format("  - {kbd}`%s`", cleanAccelerator(a)));
		}
		writer.println();
		writer.println();
		
		String description = entry.getLongText();
		if (worthwhile(description))
			writer.println("" + description);

		writer.println();		
	}
	
	
	private static boolean worthwhile(String s) {
		return s != null && !s.isBlank();
	}
	
	private static String cleanAccelerator(String accelerator) {
		return accelerator.replace("shortcut", "Ctrl")
				.replace("Shortcut", "Ctrl")
				.replace("shift", "Shift")
				.replace("alt", "Alt");
	}
	
	
	private static class TooltipCellFactory<S, T> extends TableCell<S, T> {
		
		private Function<S, String> funTip;
		private Tooltip tooltip = new Tooltip();
		
		public TooltipCellFactory(Function<S, String> funTip) {
			super();
			this.funTip = funTip;
		}
		
		@Override
		public void updateItem(T item, boolean empty) {
			super.updateItem(item, empty);
			setGraphic(null);
			if (item == null || empty || funTip == null) {
				setText(null);
				setTooltip(null);
				return;
			}
			setText(item.toString());
			var row = getTableRow();
			String text = row == null ? null : funTip.apply(row.getItem());
			if (text == null || text.isEmpty()) {
				setTooltip(null);
			} else {
				tooltip.setText(text);
				setTooltip(tooltip);
			}
		}
	
	}
	
	
	static class HelpCellFactory<S> extends TableCell<S, String> {
		
		/*
		 * Tooltip to show help message.
		 * Popover was used prior to v0.4.0 - but the behavior was a bit unreliable.
		 * See https://github.com/qupath/qupath/issues/1132
		 */
		private Tooltip tooltip = new Tooltip();
		
		public HelpCellFactory() {
			super();
			setAlignment(Pos.CENTER);
			tooltip.setWrapText(true);
			tooltip.setMaxWidth(240);
			tooltip.setTextAlignment(TextAlignment.CENTER);
			tooltip.setShowDelay(Duration.millis(200));
			tooltip.setShowDuration(Duration.INDEFINITE);
		}
		
		@Override
		public void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			setGraphic(null);
			if (!empty && item != null && !item.isEmpty()) {
				setText("?");
				tooltip.setText(item);
				setTooltip(tooltip);
			} else {
				setText("");
				tooltip.setText(item);
				setTooltip(null);
			}
		}
	
	}

	
	
	
	private static TextField createTextField(final TableView<CommandEntry> table, final FilteredList<CommandEntry> commands, final boolean clearTextOnRun, final Stage dialog, final ObservableBooleanValue hideDialogOnRun) {
		TextField textField = new TextField();
		
		textField.setTooltip(new Tooltip("Start typing to search through available commands, then select any you want to run"));
		
		textField.textProperty().addListener((v, o, n) -> {
			updateTableFilter(n.toLowerCase(), commands);
		});
		
		textField.setOnKeyReleased(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				if (!runSelectedCommand(table.getSelectionModel().getSelectedItem()))
					return;
				
				if (clearTextOnRun)
					textField.clear();
				if (hideDialogOnRun != null && hideDialogOnRun.get() && dialog != null)
					dialog.hide();
				e.consume();
			} else if (e.getCode() == KeyCode.DOWN) {
				if (table.getItems().size() == 1)
					table.getSelectionModel().select(0);
				else {
					int row = table.getSelectionModel().getSelectedIndex() + 1;
					if (row < table.getItems().size())
						table.getSelectionModel().select(row);
				}
				e.consume();
			} else if (e.getCode() == KeyCode.UP) {
				if (table.getItems().size() == 1)
					table.getSelectionModel().select(0);
				else {
					int row = table.getSelectionModel().getSelectedIndex() - 1;
					if (row >= 0)
						table.getSelectionModel().select(row);
				}
				e.consume();
			}
		});
		
		return textField;
	}
	
	
	static void addMenuComponents(final Menu menu, final String menuPath, final List<CommandEntry> commands) {
		for (MenuItem item : menu.getItems()) {
			if (!item.isVisible())
				continue;
			if (item instanceof Menu)
				addMenuComponents((Menu)item, menuPath + " \u2192 " + ((Menu)item).getText(), commands);
			else if (item instanceof SeparatorMenuItem)
				continue;
			else if (item.getText() != null)
				commands.add(CommandEntry.getInstance(item, menuPath));
		}
	}
	
	
	static TableView<CommandEntry> createCommandTable(final ObservableList<CommandEntry> commands) {
		TableView<CommandEntry> table = new TableView<>();
		SortedList<CommandEntry> items = new SortedList<>(commands);
		items.comparatorProperty().bind(table.comparatorProperty());
		table.setItems(items);
		
		TableColumn<CommandEntry, String> col1 = new TableColumn<>("Command");
		col1.setCellValueFactory(new PropertyValueFactory<>("text"));
		TableColumn<CommandEntry, String> col2 = new TableColumn<>("Menu Path");
		col2.setCellValueFactory(new PropertyValueFactory<>("menuPath"));
		TableColumn<CommandEntry, String> col3 = new TableColumn<>("Keys");
		col3.setCellValueFactory(new PropertyValueFactory<>("acceleratorText"));
		TableColumn<CommandEntry, String> col4 = new TableColumn<>("Help");
		col4.setCellValueFactory(new PropertyValueFactory<>("longText"));
		
		Function<CommandEntry, String> tipExtractor = entry -> entry == null ? null : entry.getLongText();
		col1.setCellFactory(v -> new TooltipCellFactory<CommandEntry, String>(tipExtractor));
		col2.setCellFactory(v -> new TooltipCellFactory<CommandEntry, String>(tipExtractor));
		col3.setCellFactory(v -> new TooltipCellFactory<CommandEntry, String>(tipExtractor));
		col4.setCellFactory(v -> new HelpCellFactory<CommandEntry>());
		
		// Indicate if an item is enabled or not
		table.setRowFactory(e -> {
			return new TableRow<>() {
				@Override
				public void updateItem(CommandEntry entry, boolean empty) {
					super.updateItem(entry, empty);
					if (entry == null || empty || !entry.item.get().isDisable()) {
						setStyle("-fx-opacity: 1.0;");
					} else
						setStyle("-fx-opacity: 0.5;");
				}
			};
		});
		
		
		table.setOnKeyPressed(e -> {
			if (e.isConsumed() || e.getCode() != KeyCode.ENTER)
				return;
			var selected = table.getSelectionModel().getSelectedItem();
			if (selected != null)
				runSelectedCommand(selected);
		});
		
		
		col1.prefWidthProperty().bind(table.widthProperty().multiply(0.4).subtract(6));
		col2.prefWidthProperty().bind(table.widthProperty().multiply(0.4).subtract(6));
		col3.prefWidthProperty().bind(table.widthProperty().multiply(0.1).subtract(6));
		col4.prefWidthProperty().bind(table.widthProperty().multiply(0.1).subtract(6));
		
		table.getColumns().add(col1);
		table.getColumns().add(col2);
		table.getColumns().add(col3);
		table.getColumns().add(col4);
		
		table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
		table.setFocusTraversable(false);
		return table;
	}
	
	
	static void updateTableFilter(final String text, final FilteredList<CommandEntry> commands) {
		commands.setPredicate(new Predicate<CommandEntry>() {
			@Override
			public boolean test(CommandEntry entry) {
				return entry.getCommandPath().toLowerCase().contains(text);
			}
		});
		
	}
	
	
	
	static boolean runSelectedCommand(final CommandEntry entry) {
		if (entry != null) {
			MenuItem item = entry.getMenuItem();
			return MenuManager.fireMenuItem(item);
		}
		return false;
	}
	
	
	private static class MenuManager {
		
		private static Logger logger = LoggerFactory.getLogger(MenuManager.class);
		
		private MenuBar menubar;
		private ObservableList<CommandEntry> commandsBase = FXCollections.observableArrayList();
		
		private ObservableList<CommandEntry> recentCommands = FXCollections.observableArrayList();
		
		private static Map<MenuBar, MenuManager> managerMap = new WeakHashMap<>();
		
		/**
		 * Get MenuManager for the specified menubar.
		 * Returns a cached instance if possible, creates a new one if necessary.
		 * @param menubar
		 * @return
		 */
		public static MenuManager getInstance(MenuBar menubar) {
			return managerMap.computeIfAbsent(menubar, m -> new MenuManager(m));
		}

		private MenuManager(final MenuBar menubar) {
			this.menubar = menubar;
		}
	
		private void refresh(boolean doSort) {
			// Stop recording anything for previous entries
			for (var c : commandsBase)
				c.setRecentCommandList(null);
			
			// Create sorted command entry list
			List<CommandEntry> commandsTemp = new ArrayList<>();
			for (Menu menu : menubar.getMenus()) {
				addMenuComponents(menu, menu.getText(), commandsTemp);
			}
			if (doSort)
				commandsTemp.sort(Comparator.comparing(CommandEntry::getText));
			commandsBase.setAll(commandsTemp);

			// Start recording for new entries
			for (var c : commandsBase)
				c.setRecentCommandList(recentCommands);
		}
				
		public ObservableList<CommandEntry> getCommands() {
			return commandsBase;
		}
		
		public ObservableList<CommandEntry> getRecentCommands() {
			return recentCommands;
		}
		
		static boolean fireMenuItem(final MenuItem menuItem) {
			if (menuItem.isDisable()) {
				logger.error("'{}' command is not currently available!", menuItem.getText());
				return false;
			}
			if (menuItem instanceof CheckMenuItem)
				fireMenuItem((CheckMenuItem)menuItem);
			else if (menuItem instanceof RadioMenuItem)
				fireMenuItem((RadioMenuItem)menuItem);
			else
				menuItem.fire();
			return true;
		}
		
		static void fireMenuItem(final CheckMenuItem menuItem) {
			menuItem.setSelected(!menuItem.isSelected());
		}
	
		static void fireMenuItem(final RadioMenuItem menuItem) {
			menuItem.setSelected(!menuItem.isSelected());
		}
		
	}

	
	/**
	 * Helper class to wrap summary data for a command to display in the list.
	 */
	public static class CommandEntry {
		
		private StringProperty menuPath = new SimpleStringProperty();
		private ObjectProperty<MenuItem> item = new SimpleObjectProperty<>();
		private StringProperty text = new SimpleStringProperty();
		private StringProperty acceleratorText = new SimpleStringProperty();
		private StringProperty longText = new SimpleStringProperty();
		
		private CommandListener listener = new CommandListener();
		
		private ObservableList<CommandEntry> recent;
		
		private static Map<MenuItem, CommandEntry> map = new WeakHashMap<>();
		
		static synchronized CommandEntry getInstance(final MenuItem item, final String menuPath) {
			var cached = map.getOrDefault(item, null);
			if (cached == null || !Objects.equals(cached.getMenuPath(), menuPath)) {
				cached = new CommandEntry(item, menuPath);
				map.put(item, cached);
			}
			return cached;
		}
		
		private CommandEntry(final MenuItem item, final String menuPath) {
			this.item.set(item);
			this.menuPath.set(menuPath);
			this.text.bind(Bindings.createStringBinding(() -> {
				var temp = this.item.get();
				return temp == null ? "" : temp.getText();
			}, this.item));
			ObservableObjectValue<KeyCombination> accelerator = Bindings.createObjectBinding(() -> {
				var temp = this.item.get();
				return temp == null ? null : temp.getAccelerator();
			}, this.item);
			
			var action = item == null ? null : ActionTools.getActionProperty(item);
			if (action != null) {
				longText = action.longTextProperty();
			}

			this.acceleratorText.bind(Bindings.createStringBinding(() -> {
				var temp = accelerator.get();
				return temp == null ? null : temp.getDisplayText();
			}, accelerator));
		}
		
		/**
		 * Add to head of recent command list *only* if not already there
		 */
		private void addToRecent() {
			if (recent == null)
				return;
			if (recent.isEmpty() || recent.get(recent.size()-1).getMenuItem() != this.getMenuItem())
				recent.add(this);
		}
		
		/**
		 * Get the menu item corresponding to this command.
		 * @return
		 */
		public MenuItem getMenuItem() {
			return item.get();
		}
		
		/**
		 * Get a string representation of the menu path for this command, including the menu path and the text.
		 * @return
		 */
		public String getCommandPath() {
			return getMenuPath() + " \u2192 " + getText();
		}
		
		/**
		 * Get the name of the command.
		 * @return
		 */
		public String getText() {
			return text.get();
		}
		
		/**
		 * Get the long text (description) for the command, if available.
		 * This assumes an action property has been set, see {@link ActionTools#putActionProperty(MenuItem, org.controlsfx.control.action.Action)}
		 * @return the long text for the command, or null if no such text is available
		 */
		public String getLongText() {
			return longTextProperty().get();
		}
		
		/**
		 * Get a String representation of any accelerator for the command
		 * @return
		 */
		public String getAcceleratorText() {
			return acceleratorTextProperty().get();
		}
		
		/**
		 * Get a String representation of the menu containing this command.
		 * @return
		 */
		public String getMenuPath() {
			return menuPath.get();
		}
		
		/**
		 * Property corresponding to {@link #getAcceleratorText()}
		 * @return
		 */
		public ReadOnlyStringProperty acceleratorTextProperty() {
			return acceleratorText;
		}
		
		/**
		 * Property corresponding to {@link #getText()}
		 * @return
		 */
		public ReadOnlyStringProperty textProperty() {
			return text;
		}
		
		/**
		 * Property corresponding to {@link #getLongText()}
		 * @return
		 */
		public ReadOnlyStringProperty longTextProperty() {
			return longText;
		}
		
		/**
		 * Property corresponding to {@link #getMenuPath()}
		 * @return
		 */
		public ReadOnlyStringProperty menuPathProperty() {
			return menuPath;
		}
		
		/**
		 * Set an observable list where we register this command each time it's fired
		 * @param recent
		 */
		private void setRecentCommandList(ObservableList<CommandEntry> recent) {
			if (Objects.equals(recent, this.recent))
				return;
			this.recent = recent;
			
			// Add or remove listeners, as needed
			var item = getMenuItem();
			if (recent == null) {
				if (item instanceof CheckMenuItem) {
					((CheckMenuItem) item).selectedProperty().removeListener(listener);
				} else if (item instanceof RadioMenuItem) {
					((RadioMenuItem) item).selectedProperty().removeListener(listener);
				} else {
					item.removeEventHandler(ActionEvent.ACTION, listener);
				}
			} else {
				if (item instanceof CheckMenuItem) {
					((CheckMenuItem) item).selectedProperty().addListener(listener);
				} else if (item instanceof RadioMenuItem) {
					((RadioMenuItem) item).selectedProperty().addListener(listener);
				} else {
					item.addEventHandler(ActionEvent.ACTION, listener);
				}
			}
		}
		
		
		class CommandListener implements ChangeListener<Boolean>, EventHandler<ActionEvent> {

			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				addToRecent();
			}

			@Override
			public void handle(ActionEvent event) {
				addToRecent();
			}
			
		}
		
		
	}
	
	
}