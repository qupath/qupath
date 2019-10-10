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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.TextAlignment;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.io.TMAScoreImporter;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;

/**
 * Command for importing TMA maps &amp; additional info (e.g. manual scores).
 * 
 * @author Pete Bankhead
 *
 */
public class TMAScoreImportCommand implements PathCommand {

	final private static Logger logger = LoggerFactory.getLogger(TMAScoreImportCommand.class);
	
	private static String name = "TMA data importer";
	
	private QuPathGUI qupath;
	
	public TMAScoreImportCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
		
		// Support dragging a TMA map onto the image
		qupath.getDefaultDragDropListener().addFileDropHandler((viewer, list) -> {
			if (list.isEmpty())
				return false;
			ImageData<?> imageData = qupath.getImageData();
			if (imageData == null || imageData.getHierarchy().getTMAGrid() == null)
				return false;
			File file = list.get(0);
			if (file.getName().toLowerCase().endsWith(".qpmap")) {
				try {
					CoreInfoGrid grid = new CoreInfoGrid(imageData.getHierarchy().getTMAGrid());
					boolean success = handleImportGrid(grid, GeneralTools.readFileAsString(file.getAbsolutePath()));
					if (success) {
						grid.synchronizeTMAGridToInfo();
						imageData.getHierarchy().fireObjectsChangedEvent(this, imageData.getHierarchy().getTMAGrid().getTMACoreList());
						DisplayHelpers.showInfoNotification("TMA grid import", "TMA grid imported (" + grid.getGridWidth() + "x" + grid.getGridHeight() + ")");
						return true;
					}
				} catch (Exception e) {
					logger.error("Error importing TMA grid", e);
				}
			}
			return false;
		});
	}
		
	@Override
	public void run() {
		ImageData<?> imageData = qupath.getImageData();
		PathObjectHierarchy hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null || hierarchy.getTMAGrid() == null) {
			DisplayHelpers.showErrorMessage(name, "No TMA grid has been set for the selected image!");
			return;
		}
		
		// Show a GUI with the current TMA grid status
		TMAGrid grid = hierarchy.getTMAGrid();
		CoreInfoGrid infoGrid = new CoreInfoGrid(grid);
		
		// Create the table
		TableView<CoreInfoRow> table = new TableView<>();
		table.getItems().setAll(infoGrid.getRows());
		for (int c = 0; c < grid.getGridWidth(); c++) {
			final int col = c;
			TableColumn<CoreInfoRow, TMACoreObject> tableColumn = new TableColumn<>();
			tableColumn.setCellValueFactory(column -> new ReadOnlyObjectWrapper<>(column.getValue().get(col)));
			tableColumn.setCellFactory(column -> new CoreInfoTableCell());
			tableColumn.setResizable(false);
			table.getColumns().add(tableColumn);
		}
		table.widthProperty().addListener(c -> {
			Pane headerRow = (Pane)table.lookup("TableHeaderRow");
			headerRow.setMaxHeight(0);
			headerRow.setMinHeight(0);
			headerRow.setPrefHeight(0);
			headerRow.setVisible(false);			
		});
		table.getSelectionModel().setCellSelectionEnabled(true);
		
		BorderPane pane = new BorderPane();
		pane.setCenter(table);
		
		
		Button btnImportData = new Button("Import data");
		btnImportData.setOnAction(e -> {
			if (handleImportDataFromFile(infoGrid))
				table.refresh();
		});
		Button btnPasteData = new Button("Paste data");
		btnPasteData.setOnAction(e -> {
			if (handleImportDataFromClipboard(infoGrid))
				table.refresh();
		});
		
		
		Button btnPasteGrid = new Button("Paste grid");
		btnPasteGrid.setOnAction(e -> {
			if (handlePasteGrid(infoGrid)) {
				table.refresh();
			}
		});
		Button btnLoadGrid = new Button("Import grid");
		btnLoadGrid.setOnAction(e -> {
			if (handleLoadGridFromFile(infoGrid))
				table.refresh();
		});
		
		GridPane buttonPane = PaneToolsFX.createColumnGridControls(
				btnImportData,
				btnPasteData,
				btnLoadGrid,
				btnPasteGrid
				);
		pane.setBottom(buttonPane);
		
		if (DisplayHelpers.showConfirmDialog(name, pane)) {
			infoGrid.synchronizeTMAGridToInfo();
			hierarchy.fireObjectsChangedEvent(this, new ArrayList<>(grid.getTMACoreList()));
			return;
		}
		
	}
	
	
	private static boolean handleImportDataFromClipboard(final TMAGrid infoGrid) {
		logger.trace("Importing TMA data from clipboard...");
		if (!Clipboard.getSystemClipboard().hasString()) {
			DisplayHelpers.showErrorMessage(name, "No text on clipboard!");
			return false;
		}
		int nScores = TMAScoreImporter.importFromCSV(Clipboard.getSystemClipboard().getString(), createPseudoHierarchy(infoGrid));
		if (nScores == 1)
			DisplayHelpers.showMessageDialog(name, "Updated 1 core");
		else 
			DisplayHelpers.showMessageDialog(name, "Updated " + nScores + " cores");
		return nScores > 0;
	}
	
	
	private static boolean handleImportDataFromFile(final TMAGrid infoGrid) {
		logger.trace("Importing TMA data from file...");
		File file = QuPathGUI.getSharedDialogHelper().promptForFile(null, null, "Text file", new String[]{"csv", "txt"});
		if (file == null)
			return false;
		try {
			int nScores = TMAScoreImporter.importFromCSV(file, createPseudoHierarchy(infoGrid));
			if (nScores == 1)
				DisplayHelpers.showMessageDialog(name, "Updated 1 core");
			else 
				DisplayHelpers.showMessageDialog(name, "Updated " + nScores + " cores");
//			logger.info(String.format("Scores read for %d core(s)", nScores));
			return nScores > 0;
		} catch (IOException e) {
			DisplayHelpers.showErrorMessage(name, e.getLocalizedMessage());
			return false;
		}
	}
	
	
	/**
	 * Creating a pseudo hierarchy makes it possible to make changes without modifying the 'true' underlying hierarchy,
	 * which enables them to be reversible.
	 * 
	 * @param grid
	 * @return
	 */
	private static PathObjectHierarchy createPseudoHierarchy(final TMAGrid grid) {
		PathObjectHierarchy hierarchy = new PathObjectHierarchy();
		hierarchy.setTMAGrid(grid);
		return hierarchy;
	}
	
	
	
	/**
	 * Call <code>handleImportGrid</code> using any text on the System clipboard as input.
	 * 
	 * @param infoGrid
	 * @return
	 */
	private static boolean handlePasteGrid(final TMAGrid infoGrid) {
		logger.trace("Importing TMA grid from clipboard...");
		if (!Clipboard.getSystemClipboard().hasString()) {
			DisplayHelpers.showErrorMessage(name, "No text on clipboard!");
			return false;
		}
		return handleImportGrid(infoGrid, Clipboard.getSystemClipboard().getString());
	}
	
	/**
	 * Call <code>handleImportGrid</code>, prompting the user to select a text file.
	 * 
	 * @param infoGrid
	 * @return
	 */
	private static boolean handleLoadGridFromFile(final TMAGrid infoGrid) {
		logger.trace("Importing TMA grid from file...");
		File file = QuPathGUI.getSharedDialogHelper().promptForFile(null, null, "Text file", new String[]{"csv", "txt"});
		if (file == null)
			return false;
		Scanner scanner;
		try {
			scanner = new Scanner(file);
			scanner.useDelimiter("\\Z");
			String text = scanner.next();
			scanner.close();
			return handleImportGrid(infoGrid, text);
		} catch (FileNotFoundException e) {
			DisplayHelpers.showErrorMessage(name, "File " + file.getName() + " could not be read");
			return false;
		}
	}
	
	/**
	 * Update the CoreInfoGrid with a text containing Unique IDs arranged as a grid with tab or comma delimiters.
	 * 
	 * @param infoGrid
	 * @param text
	 * @return
	 */
	private static boolean handleImportGrid(final TMAGrid infoGrid, final String text) {
		// Try to create a string grid
		List<String[]> rows = new ArrayList<>();
		int nCols = -1;
		for (String row : GeneralTools.splitLines(text)) {
			String[] cols;
			if (row.contains("\t"))
				cols = row.split("\t");
			else if (row.contains(","))
				cols = row.split(",");
			else
				// Stop at the first empty row (lacking delimiters)
				break;
			if (cols.length > nCols)
				nCols = cols.length;
			rows.add(cols);
		}
		if (nCols < 0) {
			DisplayHelpers.showErrorMessage(name, "Could not identify tab or comma delimited columns");
			return false;
		}
		
		// Check if we have a suitable grid size, and whether or not we have column/row headers
		int nRows = rows.size();
		if ((nRows != infoGrid.getGridHeight() || nCols != infoGrid.getGridWidth()) &&
				(nRows != infoGrid.getGridHeight()+1 || nCols != infoGrid.getGridWidth()+1)) {
			DisplayHelpers.showErrorMessage(name, String.format("Grid sizes inconsistent: TMA grid is %d x %d, but text grid is %d x %d", infoGrid.getGridHeight(), infoGrid.getGridWidth(), nRows, nCols));
			return false;
		}
		boolean hasHeaders = nRows == infoGrid.getGridHeight()+1;
		for (int ri = hasHeaders ? 1 : 0; ri < rows.size(); ri++) {
			String[] cols = rows.get(ri);
			int r = hasHeaders ? ri-1 : ri;
			for (int ci = hasHeaders ? 1 : 0; ci < nCols; ci++) {
				int c = hasHeaders ? ci-1 : ci;
				if (ci >= cols.length)
					infoGrid.getTMACore(r, c).setUniqueID(null);
				else {
					String id = cols[ci];
					if (id.trim().length() == 0)
						id = null;
					infoGrid.getTMACore(r, c).setUniqueID(id);
				}
				// Set header, if required
				if (hasHeaders) {
					String header = rows.get(ri)[0] + "-" + rows.get(0)[ci];
					infoGrid.getTMACore(r, c).setName(header);
				}
			}			
		}
		return true;
	}
	
	
	
	static class CoreInfoTableCell extends TableCell<CoreInfoRow, TMACoreObject> {
		
		private Tooltip tooltip = new Tooltip();
		
		@Override
		public void updateItem(TMACoreObject item, boolean empty) {
			super.updateItem(item, empty);
			setWidth(150);
			setHeight(150);
			setMaxWidth(200);
			setMaxHeight(200);
			if (item == null || empty) {
				setText(null);
				setGraphic(null);
				setTooltip(null);
				return;
			}
			if (item.isMissing())
				setTextFill(ColorToolsFX.getCachedColor(PathPrefs.getTMACoreMissingColor()));
			else
				setTextFill(ColorToolsFX.getCachedColor(PathPrefs.getTMACoreColor()));
			
			setAlignment(Pos.CENTER);
			setTextAlignment(TextAlignment.CENTER);
			setContentDisplay(ContentDisplay.CENTER);
			setText(getDisplayString(item));
			tooltip.setText(getExtendedDescription(item));
			setTooltip(tooltip);
		}
		
	}
	
	
	/**
	 * A row of CoreInfo objects - useful for tabular display.
	 */
	static class CoreInfoRow {
		
		private CoreInfo[] list;
		
		CoreInfoRow(final int capacity) {
			list = new CoreInfo[capacity];
		}
		
		public CoreInfo get(int col) {
			return list[col];
		}

		private void set(CoreInfo info, int col) {
			list[col] = info;
		}

	}
	
	
	/**
	 * Specialized TMAGrid to replace TMACoreObjects with CoreInfo objects -
	 * the reason being to intercept modifications, thereby allowing them to 
	 * be either applied to the underlying 'true' TMAGrid or reverted.
	 */
	private static class CoreInfoGrid implements TMAGrid {
		
		private static final long serialVersionUID = 1L;
		
		private TMAGrid grid;
		private Map<TMACoreObject, CoreInfo> coreMap = new LinkedHashMap<>();
		private List<CoreInfoRow> rows = new ArrayList<>();
				
		CoreInfoGrid(final TMAGrid grid) {
			this.grid = grid;
			for (int y = 0; y < getGridHeight(); y++) {
				CoreInfoRow row = new CoreInfoRow(getGridWidth());
				for (int x = 0; x < getGridWidth(); x++) {
					TMACoreObject core = grid.getTMACore(y, x);
					CoreInfo coreInfo = new CoreInfo(core);
					row.set(coreInfo, x);
					coreMap.put(core, coreInfo);
				}
				rows.add(row);
			}
		}
		
		public List<CoreInfoRow> getRows() {
			return Collections.unmodifiableList(rows);
		}
		
		public void synchronizeTMAGridToInfo() {
			coreMap.values().forEach(c -> c.synchronizeCoreToFields());
		}

		@Override
		public int nCores() {
			return grid.nCores();
		}

		@Override
		public int getGridWidth() {
			return grid.getGridWidth();
		}

		@Override
		public int getGridHeight() {
			return grid.getGridHeight();
		}

		@Override
		public TMACoreObject getTMACore(String coreName) {
			return coreMap.get(grid.getTMACore(coreName));
		}

		@Override
		public TMACoreObject getTMACore(int row, int col) {
			return coreMap.get(grid.getTMACore(row, col));
		}

		@Override
		public List<TMACoreObject> getTMACoreList() {
			return new ArrayList<>(coreMap.values());
		}
		
	}
	
	
//	/**
//	 * Specialized TMAGrid to replace TMACoreObjects with CoreInfo objects -
//	 * the reason being to intercept modifications, thereby allowing them to 
//	 * be either applied to the underlying 'true' TMAGrid or reverted.
//	 */
//	private static class CoreInfoGrid extends DefaultTMAGrid {
//		
//		private static final long serialVersionUID = 1L;
//		
//		private List<CoreInfoRow> rows = new ArrayList<>();
//				
//		CoreInfoGrid(final TMAGrid grid) {
//			super(grid.getTMACoreList().stream().map(c -> new CoreInfo(c)).collect(Collectors.toList()), grid.getGridWidth());
//			for (int y = 0; y < getGridHeight(); y++) {
//				CoreInfoRow row = new CoreInfoRow(getGridWidth());
//				for (int x = 0; x < getGridWidth(); x++)
//					row.set((CoreInfo)getTMACore(y, x), x);
//				rows.add(row);
//			}
//		}
//		
//		public List<CoreInfoRow> getRows() {
//			return Collections.unmodifiableList(rows);
//		}
//		
//		public void synchronizeTMAGridToInfo() {
//			for (int row = 0; row < getGridHeight(); row++) {
//				for (int col = 0; col < getGridWidth(); col++) {
//					((CoreInfo)getTMACore(row, col)).synchronizeCoreToFields();
//				}
//			}
//		}
//		
//	}
	
	
	/**
	 * Wrapper for a TMACoreObject that can intercept any changes.
	 * synchronizeFieldsToCore() should be called to modify the wrapped object.
	 */
	static class CoreInfo extends TMACoreObject {
		
		private TMACoreObject core;
		
		public CoreInfo(final TMACoreObject core) {
			super();
			this.core = core;
			synchronizeFieldsToCore();
		}
		
		/**
		 * Set the TMA core object's properties based on the current fields
		 */
		private void synchronizeCoreToFields() {
			core.setMissing(isMissing());
			core.setName(getName());
			core.setUniqueID(getUniqueID());
			for (String name : getMeasurementList().getMeasurementNames()) {
				core.getMeasurementList().putMeasurement(name, getMeasurementList().getMeasurementValue(name));
			}
			core.getMeasurementList().close();
			for (Entry<String, String> entry : getMetadataMap().entrySet()) {
				core.putMetadataValue(entry.getKey(), (String)entry.getValue());
			}
		}
		
		/**
		 * Set the fields based on the TMA core object
		 */
		private void synchronizeFieldsToCore() {
			setMissing(core.isMissing());
			setName(core.getName());
			setUniqueID(core.getUniqueID());
		}
		
	}
	
	
	static String getDisplayString(final TMACoreObject core) {
		StringBuilder sb = new StringBuilder();
		sb.append(core.getName()).append("\n");
//		if (isMissing())
//			sb.append("(missing)");
//		sb.append("\n");
		if (core.getUniqueID() != null)
			sb.append(core.getUniqueID());
		sb.append("\n");
		sb.append("\n");
		return sb.toString();
	}
	
	
	static String getExtendedDescription(final TMACoreObject core) {
		StringBuilder sb = new StringBuilder();
		sb.append("Name:\t");
		sb.append(core.getName());
		if (core.isMissing())
			sb.append(" (missing)\n");
		sb.append("\n");
//		sb.append("ID:\t");
//		if (getUniqueID() != null)
//			sb.append(getUniqueID());
//		else
//			sb.append("-");
		sb.append("\n");
		for (Entry<String, String> entry : core.getMetadataMap().entrySet()) {
			sb.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
		}
		for (String name : core.getMeasurementList().getMeasurementNames()) {
			sb.append(name).append("\t").append(core.getMeasurementList().getMeasurementValue(name)).append("\n");
		}
		return sb.toString();
	}
	
	
}