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

package qupath.lib.gui.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.controlsfx.control.HiddenSidesPane;
import org.controlsfx.control.PopOver;
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
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;
import javafx.stage.Stage;
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
	

	
	private static ObjectProperty<CommandBarDisplay> commandBarDisplay;
	
	/**
	 * Property specifying where the command bar should be displayed relative to the main viewer window.
	 * @return
	 */
	public synchronized static ObjectProperty<CommandBarDisplay> commandBarDisplayProperty() {
		if (commandBarDisplay == null) {
			String name = PathPrefs.getUserPreferences().get("commandFinderDisplayMode", CommandBarDisplay.NEVER.name());
			CommandBarDisplay display = CommandBarDisplay.valueOf(name);
			if (display == null)
				display = CommandBarDisplay.HOVER;
			commandBarDisplay = new SimpleObjectProperty<>(display);
			commandBarDisplay.addListener((v, o, n) -> {
				PathPrefs.getUserPreferences().put("commandFinderDisplayMode", n.name());
			});			
		}
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
		MenuManager menuManager = new MenuManager(qupath.getMenuBar());
		
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
		// TODO: Explore updating this with the action list changes
		MenuManager menuManager = new MenuManager(qupath.getMenuBar());
		menuManager.refresh(true);
		
		Stage stage = new Stage();
		stage.initOwner(qupath.getStage());
		stage.setTitle("Command List");
		
		CheckBox cbAutoClose = new CheckBox("Auto close");
		cbAutoClose.selectedProperty().bindBidirectional(autoCloseCommandListProperty);
		cbAutoClose.setPadding(new Insets(2, 2, 2, 2));

		FilteredList<CommandEntry> commands = new FilteredList<>(menuManager.getCommands());
		TableView<CommandEntry> table = createCommandTable(commands);
		TextField textField = createTextField(table, commands, false, stage, cbAutoClose.selectedProperty());

		// Control focus of the text field
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
			if (e.getClickCount() > 1) {
				if (runSelectedCommand(table.getSelectionModel().getSelectedItem())) {
					if (cbAutoClose.isSelected()) {
						stage.hide();
					}
				}
			}
		});

		return stage;
	}
	
	
	
//	public static void menusToMarkdown(final QuPathGUI qupath, Writer writer) throws IOException {
//		MenuManager menuManager = new MenuManager(qupath.getMenuBar());
//		menuManager.refresh(false);
//		PrintWriter printWriter = toPrintWriter(writer);
//		for (var item : menuManager.getCommands()) {
//			toMarkdown(item, printWriter);
//		}
//		printWriter.flush();
//	}
	
	/**
	 * Create a Sphinx representation of the menus for inclusion in the documentation.
	 * @param qupath
	 * @param writer
	 * @throws IOException
	 */
	public static void menusToSphinx(final QuPathGUI qupath, Writer writer) throws IOException {
		MenuManager menuManager = new MenuManager(qupath.getMenuBar());
		menuManager.refresh(false);
		PrintWriter printWriter = toPrintWriter(writer);
		String lastMenu = null;
		for (var item : menuManager.getCommands()) {
			String menuPath = item.getMenuPath();
			if (menuPath != null) {
				String menu = menuPath.split("\u2192")[0].strip();
				if (!Objects.equals(menu, lastMenu)) {
					underlineHeader(menu, "=", printWriter);
					printWriter.println();
					lastMenu = menu;
				}
			}
			toSphinx(item, printWriter);
		}
		printWriter.flush();
	}

	static PrintWriter toPrintWriter(Writer writer) {
		return writer instanceof PrintWriter ? (PrintWriter)writer : new PrintWriter(writer);
	}
	
	
//	static void toMarkdown(CommandEntry entry, PrintWriter writer) {
//		throw new NotImplementedException();
//	}
	
	static void underlineHeader(String text, String character, PrintWriter writer) {
		writer.println(text);
		for (int i = 0; i < text.length(); i++)
			writer.print(character);
		writer.println();
	}
	
	static void toSphinx(CommandEntry entry, PrintWriter writer) {
		
		String title = entry.getText();
		underlineHeader(title, "-", writer);

		String subtitle = entry.getCommandPath();
		writer.print("*" + subtitle + "*");
		String accelerator = entry.getAcceleratorText();
		if (worthwhile(accelerator)) {
			writer.print(String.format("  - :kbd:`%s`", cleanAccelerator(accelerator)));
		}
		writer.println();
		writer.println();
		
		String description = entry.getLongText();
		if (worthwhile(description))
			writer.println("" + description);

		writer.println();		
	}
	
	
	static boolean worthwhile(String s) {
		return s != null && !s.isBlank();
	}
	
	static String cleanAccelerator(String accelerator) {
		return accelerator.replace("shortcut", "Ctrl").replace("shift", "Shift").replace("alt", "Alt");
	}
	
	
	static class TooltipCellFactory<S, T> extends TableCell<S, T> {
		
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
	
	
	static class HelpCellFactory<S> extends TableCell<S, String> implements EventHandler<MouseEvent> {
		
		private Label label = new Label();
		private PopOver popover = new PopOver(label);
		
		public HelpCellFactory() {
			super();
			addEventHandler(MouseEvent.MOUSE_ENTERED, this);
			addEventHandler(MouseEvent.MOUSE_EXITED, this);
			setAlignment(Pos.CENTER);
			label.setWrapText(true);
			label.setMaxWidth(240);
			label.setPadding(new Insets(10.0));
			label.setTextAlignment(TextAlignment.CENTER);
		}
		
		@Override
		public void updateItem(String item, boolean empty) {
			super.updateItem(item, empty);
			setGraphic(null);
			if (!empty && item != null && !item.isEmpty()) {
				setText("?");
				label.setText(item);
			} else {
				setText("");
				label.setText(item);
			}
		}
		
		/**
		 * This popover throws an exception if it animates while a window is closing.
		 */
		void updateAnimated() {
			var window = GuiTools.getWindow(this);
			popover.setAnimated(window != null && window.isShowing());
		}
		
		void maybeShowPopover() {
			var text = label.getText();
			if (text != null && !text.isBlank()) {
				updateAnimated();
				popover.show(this);
			}
		}

		@Override
		public void handle(MouseEvent event) {
			if (event.getEventType() == MouseEvent.MOUSE_EXITED) {
				if (popover.isShowing()) {
					updateAnimated();
					popover.hide();
				}
				event.consume();
			} else if (event.getEventType() == MouseEvent.MOUSE_ENTERED) {
				maybeShowPopover();
				event.consume();				
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
				commands.add(new CommandEntry(item, menuPath));
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
		
		public MenuManager(final MenuBar menubar) {
			this.menubar = menubar;
		}
	
		private void refresh(boolean doSort) {
			// Create sorted command entry list
			List<CommandEntry> commandsTemp = new ArrayList<>();
			for (Menu menu : menubar.getMenus()) {
				addMenuComponents(menu, menu.getText(), commandsTemp);
			}
			if (doSort)
				commandsTemp.sort(Comparator.comparing(CommandEntry::getText));
			commandsBase.setAll(commandsTemp);
		}
				
		public ObservableList<CommandEntry> getCommands() {
			return commandsBase;
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
		
		CommandEntry(final MenuItem item, final String menuPath) {
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
		
	}
	
	
}