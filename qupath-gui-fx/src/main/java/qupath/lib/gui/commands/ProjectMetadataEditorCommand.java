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

package qupath.lib.gui.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.panels.ProjectBrowser;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Command to enable editing of project metadata.
 * 
 * TODO: Support copying and pasting tables, to allow better editing within a spreadsheet application.
 * 
 * TODO: Support adding/removing metadata columns.
 * 
 * @author Pete Bankhead
 *
 */
public class ProjectMetadataEditorCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(ProjectMetadataEditorCommand.class);

	private final static String IMAGE_NAME = "Image name";
	
	private QuPathGUI qupath;
	
	public ProjectMetadataEditorCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		
		Project<?> project = qupath.getProject();
		if (project == null) {
			logger.warn("No project available!");
			return;
		}
		
		Set<String> metadataNameSet = new TreeSet<>();
		List<ImageEntryWrapper> entries = new ArrayList<>();
		for (ProjectImageEntry<?> entry : project.getImageList()) {
			entries.add(new ImageEntryWrapper(entry));
			metadataNameSet.addAll(entry.getMetadataKeys());
		}
		
		TableView<ImageEntryWrapper> table = new TableView<>();
		
		TableColumn<ImageEntryWrapper, String> colName = new TableColumn<>(IMAGE_NAME);
		colName.setCellValueFactory(v -> v.getValue().getNameBinding());
		table.getColumns().add(colName);
		table.setEditable(true);
		
		for (String metadataName : metadataNameSet) {
			TableColumn<ImageEntryWrapper, String> col = new TableColumn<>(metadataName);
			col.setCellFactory(TextFieldTableCell.<ImageEntryWrapper>forTableColumn());
			col.setOnEditCommit(e -> {
				ImageEntryWrapper entry = e.getRowValue();
				String n = e.getNewValue();
				if (n == null || n.isEmpty())
					entry.removeMetadataValue(e.getTableColumn().getText());
				else
					entry.putMetadataValue(e.getTableColumn().getText(), n);
			});
			
			col.setCellValueFactory(v -> v.getValue().getProperty(metadataName));
			col.setEditable(true);
			table.getColumns().add(col);			
		}
		
		table.getItems().setAll(entries);
		table.getSelectionModel().setCellSelectionEnabled(true);
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		
		// Handle deleting entries
		table.addEventHandler(KeyEvent.KEY_RELEASED, e -> {
			if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE) {
				List<TablePosition> positions = table.getSelectionModel().getSelectedCells().stream().filter(
						p -> !IMAGE_NAME.equals(p.getTableColumn().getText())).collect(Collectors.toList());
				if (positions.isEmpty())
					return;
				if (positions.size() == 1) {
					setTextForSelectedCells(positions, null);
				} else {
					if (DisplayHelpers.showConfirmDialog("Project metadata", "Clear metadata for " + positions.size() + " selected cells?")) {
						setTextForSelectedCells(positions, null);
					}
				}
				table.refresh();
			}
		});
		
		BooleanBinding selectedCells = Bindings.createBooleanBinding(
				() -> table.getSelectionModel().selectedItemProperty() == null,
				table.getSelectionModel().selectedItemProperty()
				);
		
		MenuBar menubar = new MenuBar();
		Menu menuEdit = new Menu("Edit");
		MenuItem miCopy = new MenuItem("Copy selected cells");
		miCopy.disableProperty().bind(selectedCells);
		miCopy.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
		miCopy.setOnAction(e -> copySelectedCellsToClipboard(table, true));

		MenuItem miCopyFull = new MenuItem("Copy full table");
		miCopyFull.setOnAction(e -> copyEntireTableToClipboard(table));
		
		MenuItem miPaste = new MenuItem("Paste");
		miPaste.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));
		miPaste.disableProperty().bind(selectedCells);
		miPaste.setOnAction(e -> pasteClipboardContentsToTable(table));

		MenuItem miSet = new MenuItem("Set cell contents");
		miSet.disableProperty().bind(selectedCells);
		miSet.setOnAction(e -> {
			String input = DisplayHelpers.showInputDialog("Set metadata cells", "Metadata text", "");
			if (input == null)
				return;
			setTextForSelectedCells(table.getSelectionModel().getSelectedCells(), input);
			table.refresh();
		});

		menuEdit.getItems().addAll(miCopy, miCopyFull, miPaste, miSet);
		menubar.getMenus().add(menuEdit);
		
		BorderPane pane = new BorderPane();
		pane.setTop(menubar);
		pane.setCenter(table);
		menubar.setUseSystemMenuBar(true);
		
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Project metadata");
		dialog.setHeaderText(null);
		dialog.setResizable(true);
		dialog.getDialogPane().setContent(pane);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.getDialogPane().setPrefWidth(500);
		Optional<ButtonType> result = dialog.showAndWait();
		if (result.isPresent() && result.get().getButtonData() == ButtonData.OK_DONE) {
			// Make the changes
			for (ImageEntryWrapper wrapper : entries) {
				wrapper.commitChanges();
			}
			// Write the project
			ProjectBrowser.syncProject(project);
		}
		
//		Stage stage = new Stage();
//		stage.initOwner(qupath.getStage());
//		stage.setTitle("Project metadata");
//		stage.setScene(new Scene(pane, 500, 400));
//		stage.showAndWait();
		
	}
	
	
	/**
	 * Copy the text content of selected table cells to the clipboard.
	 * 
	 * Note #1: This only works for continuous selections, i.e. single selected 
	 * cells, or multiple cells arranged in a full rectangular grid.
	 * 
	 * Note #2: This method assumes that TableSelectionModel.getSelectedCells() returns
	 * table rows and columns returned with valid indices >= 0.
	 * No provision is made for indices of -1 used to indicate the selection of 
	 * an entire row or column.
	 * 
	 * @param table
	 * @param warnIfDiscontinuous If true, a warning is shown if a discontinous selection is made.
	 */
	private static void copySelectedCellsToClipboard(final TableView<?> table, final boolean warnIfDiscontinuous) {
		List<TablePosition> positions = table.getSelectionModel().getSelectedCells();
		if (positions.isEmpty())
			return;
		
		int[] rows = positions.stream().mapToInt(tp -> tp.getRow()).sorted().toArray();
		int[] cols = positions.stream().mapToInt(tp -> tp.getColumn()).sorted().toArray();
		boolean isContinuous = (rows[rows.length-1] - rows[0] + 1) * (cols[cols.length-1] - cols[0] + 1) == positions.size();
		if (!isContinuous) {
			if (warnIfDiscontinuous)
				DisplayHelpers.showWarningNotification("Copy table selection", "Cannot copy discontinous selection, sorry");
			return;
		}
		
		copyToClipboard(positions);
	}
	
	
	
	/**
	 * Copy the contents of an entire table to the clipboard.
	 * 
	 * This should adapt to sorted rows as required.
	 * 
	 * @param table
	 */
	private static void copyEntireTableToClipboard(final TableView<?> table) {
		List<TablePosition> positions = new ArrayList<>();
		for (TableColumn<?, ?> column : table.getColumns()) {
			for (int row = 0; row < table.getItems().size(); row++) {
				positions.add(new TablePosition(table, row, column));
			}
		}
		copyToClipboard(positions);
	}
	
	
	private static void copyToClipboard(List<TablePosition> positions) {
		// Ensure positions are sorted
		positions = new ArrayList<>(positions);
		positions.sort((p1, p2) -> {
			int row = Integer.compare(p1.getRow(), p2.getRow());
			if (row == 0)
				return Integer.compare(p1.getColumn(), p2.getColumn());
			return row;
		});

		// Create tab-delimited string representation
		StringBuilder sb = new StringBuilder();
		int lastRow = -1;
		for (TablePosition<?,?> tp : positions) {
			int row = tp.getRow();
			Object data = tp.getTableColumn().getCellData(row);
			String dataString = data == null ? "" : data.toString();
			if (row == lastRow)
				sb.append("\t");
			else if (lastRow >= 0)
				sb.append("\n");
			sb.append(dataString);
			lastRow = row;
		}

		ClipboardContent content = new ClipboardContent();
		content.putString(sb.toString());
		Clipboard.getSystemClipboard().setContent(content);
	}
	
	
	private static void pasteClipboardContentsToTable(TableView<?> table) {
		String s = Clipboard.getSystemClipboard().getString();
		if (s == null) {
			logger.warn("No text on clipboard");
			return;
		}
		
		List<TablePosition> positions = table.getSelectionModel().getSelectedCells();
		if (positions.isEmpty()) {
			logger.warn("No table cells selected");
			return;
		}
		
		if (s.contains("\n") || s.contains("\t")) {
			DisplayHelpers.showWarningNotification("Paste contents", "Cannot paste clipboard contents - only simple, single-cell text supported");
			return;
		}
		
		setTextForSelectedCells(positions, s);
		table.refresh();
	}
	
	private static void setTextForSelectedCells(final List<TablePosition> positions, final String text) {
		boolean containsImageNameColumns = false;
		for (TablePosition<?,?> tp : positions) {
			boolean isImageNameColumn = IMAGE_NAME.equals(tp.getTableColumn().getText());
			if (isImageNameColumn) {
				containsImageNameColumns = true;
				continue;
			}
			ImageEntryWrapper wrapper = (ImageEntryWrapper)tp.getTableView().getItems().get(tp.getRow());
			String key = tp.getTableColumn().getText();
			if (text == null)
				wrapper.removeMetadataValue(key);
			else
				wrapper.putMetadataValue(key, text);
		}
		if (containsImageNameColumns) {
			DisplayHelpers.showWarningNotification("Project metadata table", "The image name cannot be changed");
		}
	}
	
	
	
	static class ImageEntryWrapper {
		
		private ProjectImageEntry<?> entry;
		/*
		 * Internal metadata map - used to store temporary edits, which the user may yet cancel.
		 */
		private Map<String, String> metadataMap = new TreeMap<>();
		
		ImageEntryWrapper(final ProjectImageEntry<?> entry) {
			this.entry = entry;
			this.metadataMap.putAll(entry.getMetadataMap());
		}
		
		public ObservableStringValue getNameBinding() {
			return Bindings.createStringBinding(() -> entry.getImageName());
		}
		
		/**
		 * Update the underlying ProjectImageEntry to have the same metadata map.
		 */
		public void commitChanges() {
			if (metadataMap.equals(entry.getMetadataMap()))
				return;
			entry.clearMetadata();
			for (Entry<String, String> mapEntry : metadataMap.entrySet()) {
				entry.putMetadataValue(mapEntry.getKey(), mapEntry.getValue());
			}
		}
		
		public ObservableStringValue getProperty(final String columnName) {
			return Bindings.createStringBinding(() -> {
				String value = metadataMap.get(columnName);
				if (value == null)
					return "";
				return
						value;
			});
		}
		
		public String getMetadataValue(Object key) {
			return metadataMap.get(key);
		}
		
		public void putMetadataValue(String key, String value) {
			metadataMap.put(key, value);
		}

		public void removeMetadataValue(String key) {
			metadataMap.remove(key);
		}

	}
	
	
}
