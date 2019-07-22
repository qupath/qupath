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

package qupath.lib.gui.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.controlsfx.control.HiddenSidesPane;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
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
	
	public enum CommandBarDisplay {ALWAYS, NEVER, HOVER;
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

	/**
	 * Create a component that contains a TextField for entering menu commands to run quickly.
	 * 
	 * This component is a container that holds a main Node, and displays the TextField only when requested.
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
				menuManager.refresh();
			
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
			if (e.getClickCount() > 1) {
				runSelectedCommand(table.getSelectionModel().getSelectedItem());
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
		
		return paneViewer;
	}
	
	
	/**
	 * Create a dialog showing a filtered list of menu commands, for fast selection.
	 * 
	 * @param qupath
	 * @return
	 */
	public static Stage createCommandFinderDialog(final QuPathGUI qupath) {
		MenuManager menuManager = new MenuManager(qupath.getMenuBar());
		menuManager.refresh();
		
		Stage stage = new Stage();
		stage.initOwner(qupath.getStage());
		stage.setTitle("Command List");
		
		CheckBox cbAutoClose = new CheckBox("Auto close");
		cbAutoClose.selectedProperty().bindBidirectional(PathPrefs.autoCloseCommandListProperty());
		cbAutoClose.setPadding(new Insets(2, 2, 2, 2));

		FilteredList<CommandEntry> commands = new FilteredList<>(menuManager.getCommands());
		TableView<CommandEntry> table = createCommandTable(commands);
		TextField textField = createTextField(table, commands, false, stage, cbAutoClose.selectedProperty());
		
		BorderPane panelSearch = new BorderPane();
		panelSearch.setCenter(textField);
		panelSearch.setRight(cbAutoClose);		
		
		BorderPane pane = new BorderPane();
		pane.setCenter(table);
		pane.setBottom(panelSearch);
		
		stage.setScene(new Scene(pane, 600, 400));
		
		textField.textProperty().addListener((v, o, n) -> {
			// Ensure the table is up to date if we are just starting
			if (o.isEmpty() && !n.isEmpty())
				menuManager.refresh();
		});

		table.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				runSelectedCommand(table.getSelectionModel().getSelectedItem());
				if (cbAutoClose.isSelected()) {
					stage.hide();
				}
			}
		});

		return stage;
	}
	
	
	
	
	private static TextField createTextField(final TableView<CommandEntry> table, final FilteredList<CommandEntry> commands, final boolean clearTextOnRun, final Stage dialog, final ObservableBooleanValue hideDialogOnRun) {
		TextField textField = new TextField();
		
		textField.setTooltip(new Tooltip("Start typing to search through available commands, then select any you want to run"));
		
		textField.textProperty().addListener((v, o, n) -> {
			updateTableFilter(n.toLowerCase(), commands);
		});
		
		textField.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				runSelectedCommand(table.getSelectionModel().getSelectedItem());
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
				addMenuComponents((Menu)item, menuPath + ((Menu)item).getText() + ">", commands);
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
		table.getColumns().add(col1);
		table.getColumns().add(col2);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
	
	
	
	static void runSelectedCommand(final CommandEntry entry) {
		if (entry != null) {
			MenuItem item = entry.getMenuItem();
			MenuManager.fireMenuItem(item);
		}
	}
	
	
	public static class MenuManager {
		
		private MenuBar menubar;
		private ObservableList<CommandEntry> commandsBase = FXCollections.observableArrayList();
		
		public MenuManager(final MenuBar menubar) {
			this.menubar = menubar;
		}
	
		private void refresh() {
			// Create sorted command entry list
			List<CommandEntry> commandsTemp = new ArrayList<>();
			for (Menu menu : menubar.getMenus()) {
				addMenuComponents(menu, menu.getText() + ">", commandsTemp);
			}
			Collections.sort(commandsTemp, new Comparator<CommandEntry>() {
				@Override
				public int compare(CommandEntry o1, CommandEntry o2) {
					return o1.getText().compareTo(o2.getText());
				}
			});
			commandsBase.setAll(commandsTemp);
		}
		
		public ObservableList<CommandEntry> getCommands() {
			return commandsBase;
		}
		
		static void fireMenuItem(final MenuItem menuItem) {
			if (menuItem instanceof CheckMenuItem)
				fireMenuItem((CheckMenuItem)menuItem);
			else if (menuItem instanceof RadioMenuItem)
				fireMenuItem((RadioMenuItem)menuItem);
			else
				menuItem.fire();
		}
		
		static void fireMenuItem(final CheckMenuItem menuItem) {
			menuItem.setSelected(!menuItem.isSelected());
		}
	
		static void fireMenuItem(final RadioMenuItem menuItem) {
			menuItem.setSelected(!menuItem.isSelected());
		}
		
	}

	
	
	public static class CommandEntry {
		
		private StringProperty menuPath = new SimpleStringProperty();
		private ObjectProperty<MenuItem> item = new SimpleObjectProperty<>();
		private StringProperty text = new SimpleStringProperty(); // If I knew what I was doing, I might create a binding...
		
		CommandEntry(final MenuItem item, final String menuPath) {
			this.item.set(item);
			this.menuPath.set(menuPath);
			this.text.set(item.getText());
		}
		
		public MenuItem getMenuItem() {
			return item.get();
		}
		
		public String getCommandPath() {
			return menuPath + getText();
		}
		
		public String getText() {
			return text.get();
		}
		
		public String getMenuPath() {
			return menuPath.get();
		}
		
		public ReadOnlyStringProperty textProperty() {
			return text;
		}
		
		public ReadOnlyStringProperty menuPathProperty() {
			return menuPath;
		}
		
	}
	
	
}