/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tma.TMADataIO;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.scripting.QP;

/**
 * Helper class implementing simple 'single-method' commands related to tissue microarrays for easy inclusion in the GUI.
 * 
 * @author Pete Bankhead
 *
 */
public class TMACommands {
	
	private enum TMARemoveType { ROW, COLUMN;
		@Override
		public String toString() {
			switch(this) {
			case COLUMN:
				return "column";
			case ROW:
				return "row";
			default:
				return "unknown";
			}
		}
	};
	
	private final static String NOTE_NAME = "Note";
	
	
	
	private static Map<QuPathGUI, TMAGridView> gridViewMap = new WeakHashMap<>();
	
	/**
	 * Show a TMA core grid view.
	 * <p>
	 * Note that this method may change in future versions to be tied to a specified image data, 
	 * rather than a specific QuPath instance.
	 * @param qupath the QuPath instance for which the grid should be shown
	 */
	public static void showTMAGridView(QuPathGUI qupath) {
		var gridView = gridViewMap.computeIfAbsent(qupath, q -> new TMAGridView(q));
		gridView.run();
	}
	
	
	/**
	 * Prompt to type a node to associate with the selected TMA cores.
	 * @param imageData
	 */
	public static void promptToAddNoteToSelectedCores(ImageData<?> imageData) {
		String title = "Add TMA note";
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return;
		}
		
		var selectedCores = imageData.getHierarchy().getSelectionModel().getSelectedObjects().stream()
				.filter(p -> p.isTMACore())
				.map(c -> (TMACoreObject)c)
				.collect(Collectors.toList());
		if (selectedCores.isEmpty()) {
			Dialogs.showErrorMessage(title, "No TMA cores are selected!  No note will be added.");
			return;
		}
		
		
		String prompt;
		String currentText = null;
		if (selectedCores.size() == 1) {
			var core = selectedCores.get(0);
			prompt = core.getName() == null || core.getName().trim().isEmpty() ? "Core" : core.getName();
			currentText = core.getMetadataMap().get(NOTE_NAME);
		} else {
			prompt = selectedCores.size() + " cores";
		}
		
		
		String inputText = Dialogs.showInputDialog(title, prompt, currentText == null ? "" : null);
		if (inputText != null) {
			for (var core : selectedCores)
				core.putMetadataValue(NOTE_NAME, inputText);
			imageData.getHierarchy().fireObjectsChangedEvent(imageData.getHierarchy(), selectedCores);			
		}
		
//		// It's nice to return focus to the viewer, if possible
//		qupath.getStage().requestFocus();
//		qupath.getViewer().getView().requestFocus();
	}
	
	
	/**
	 * Prompt to export summary TMA data for a specific image to a directory.
	 * @param qupath
	 * @param imageData
	 */
	public static void promptToExportTMAData(QuPathGUI qupath, ImageData<BufferedImage> imageData) {
		String title = "Export TMA data";
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return;
		}
		PathObjectHierarchy hierarchy = imageData == null ? null : imageData.getHierarchy();
		if (hierarchy == null || hierarchy.isEmpty() || hierarchy.getTMAGrid() == null || hierarchy.getTMAGrid().nCores() == 0) {
			Dialogs.showErrorMessage(title, "No TMA data available!");
			return;
		}
		
		var overlayOptions = qupath.getViewers().stream().filter(v -> v.getImageData() == imageData).map(v -> v.getOverlayOptions()).findFirst().orElse(qupath.getOverlayOptions());

		String defaultName = ServerTools.getDisplayableImageName(imageData.getServer());
		File file = Dialogs.promptToSaveFile(null, null, defaultName, "TMA data", ".qptma");
		if (file != null) {
			if (!file.getName().endsWith(".qptma"))
				file = new File(file.getParentFile(), file.getName() + ".qptma");
			double downsample = PathPrefs.tmaExportDownsampleProperty().get();
			TMADataIO.writeTMAData(file, imageData, overlayOptions, downsample);
			WorkflowStep step = new DefaultScriptableWorkflowStep("Export TMA data", "exportTMAData(\"" + GeneralTools.escapeFilePath(file.getParentFile().getAbsolutePath()) + "\", " + downsample + ")");
			imageData.getHistoryWorkflow().addStep(step);
//			PathAwtIO.writeTMAData(file, imageData, viewer.getOverlayOptions(), Double.NaN);
		}
	}
	
	
	/**
	 * Command to install a drag and drop file handler for exported TMA data.
	 * @param qupath QuPath instance to which the handler should be installed
	 */
	public static synchronized void installDragAndDropHandler(QuPathGUI qupath) {
		TMADataImporter.installDragAndDropHandler(qupath);
	}
	
	
	/**
	 * Prompt to import TMA data for the specified image data.
	 * @param imageData the image data for which the TMA data should be imported
	 */
	public static void promptToImportTMAData(ImageData<?> imageData) {
		TMADataImporter.importTMAData(imageData);
	}
	
	
	private static StringProperty rowLabelsProperty = PathPrefs.createPersistentPreference("tmaRowLabels", "A-J");
	private static StringProperty columnLabelsProperty = PathPrefs.createPersistentPreference("tmaColumnLabels", "1-16");
	private static BooleanProperty rowFirstProperty = PathPrefs.createPersistentPreference("tmaLabelRowFirst", true);

	
	/**
	 * Prompt to relabel the core names within a TMA grid.
	 * @param imageData image containing the TMA grid
	 */
	public static void promptToRelabelTMAGrid(ImageData<?> imageData) {
		String title = "Relabel TMA grid";
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return;
		}
		if (imageData.getHierarchy().getTMAGrid() == null) {
			Dialogs.showErrorMessage(title, "No TMA grid selected!");
			return;
		}
		
		ParameterList params = new ParameterList();
		params.addStringParameter("labelsHorizontal", "Column labels", columnLabelsProperty.get(), "Enter column labels.\nThis can be a continuous range of letters or numbers (e.g. 1-10 or A-J),\nor a discontinuous list separated by spaces (e.g. A B C E F G).");
		params.addStringParameter("labelsVertical", "Row labels", rowLabelsProperty.get(), "Enter row labels.\nThis can be a continuous range of letters or numbers (e.g. 1-10 or A-J),\nor a discontinuous list separated by spaces (e.g. A B C E F G).");
		params.addChoiceParameter("labelOrder", "Label order", rowFirstProperty.get() ? "Row first" : "Column first", Arrays.asList("Column first", "Row first"), "Create TMA labels either in the form Row-Column or Column-Row");
		
		if (!Dialogs.showParameterDialog(title, params))
			return;
		
		// Parse the arguments
		String labelsHorizontal = params.getStringParameterValue("labelsHorizontal");
		String labelsVertical = params.getStringParameterValue("labelsVertical");
		boolean rowFirst = "Row first".equals(params.getChoiceParameterValue("labelOrder"));
		
		// Figure out if this will work
		TMAGrid grid = imageData.getHierarchy().getTMAGrid();
		String[] columnLabels = PathObjectTools.parseTMALabelString(labelsHorizontal);
		String[] rowLabels = PathObjectTools.parseTMALabelString(labelsVertical);
		if (columnLabels.length < grid.getGridWidth()) {
			Dialogs.showErrorMessage(title, "Not enough column labels specified!");
			return;			
		}
		if (rowLabels.length < grid.getGridHeight()) {
			Dialogs.showErrorMessage(title, "Not enough row labels specified!");
			return;			
		}
		
		// Apply the labels
		QP.relabelTMAGrid(
				imageData.getHierarchy(),
				labelsHorizontal,
				labelsVertical,
				rowFirst);
		
		// Add to workflow history
		imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Relabel TMA grid",
				String.format("relabelTMAGrid(\"%s\", \"%s\", %s)",
						GeneralTools.escapeFilePath(labelsHorizontal),
						GeneralTools.escapeFilePath(labelsVertical),
						Boolean.toString(rowFirst))));
		
		
		// Store values
		rowLabelsProperty.set(labelsVertical);
		columnLabelsProperty.set(labelsHorizontal);
		rowFirstProperty.set(rowFirst);
	}
	
	/**
	 * Prompt to delete a row from a TMA grid.
	 * The row is identified as being the one that contains the current selected TMA core, 
	 * or the core that contains the selected object.
	 * <p>
	 * After this command is run, the user will be prompted to relabel the TMA grid.
	 * 
	 * @param imageData the image data containing the TMA grid
	 * @return true if the TMA grid was modified, false otherwise (e.g. if the user cancelled)
	 */
	public static boolean promptToDeleteTMAGridRow(ImageData<?> imageData) {
		return promptToDeleteTMAGridRowOrColumn(imageData, TMARemoveType.ROW);
	}

	/**
	 * Prompt to delete a column from a TMA grid.
	 * The column is identified as being the one that contains the current selected TMA core, 
	 * or the core that contains the selected object.
	 * <p>
	 * After this command is run, the user will be prompted to relabel the TMA grid.
	 * 
	 * @param imageData the image data containing the TMA grid
	 * @return true if the TMA grid was modified, false otherwise (e.g. if the user cancelled)
	 */
	public static boolean promptToDeleteTMAGridColumn(ImageData<?> imageData) {
		return promptToDeleteTMAGridRowOrColumn(imageData, TMARemoveType.COLUMN);
	}

	private static boolean promptToDeleteTMAGridRowOrColumn(ImageData<?> imageData, TMARemoveType type) {
		String typeString = type.toString();
		String title = "Delete TMA " + typeString;
		boolean removeRow = type == TMARemoveType.ROW;
		if (imageData == null) {
			Dialogs.showNoImageError(title);
			return false;
		}
		if (imageData.getHierarchy().getTMAGrid() == null) {
			Dialogs.showErrorMessage(title, "No image with dearrayed TMA cores selected!");
			return false;
		}
		
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
		TMACoreObject selectedCore = null;
		if (selected != null)
			selectedCore = PathObjectTools.getAncestorTMACore(selected);
		
		
		// Try to identify the row/column that we want
		TMAGrid grid = hierarchy.getTMAGrid();
		int row = -1;
		int col = -1;
		if (selectedCore != null) {
			for (int y = 0; y < grid.getGridHeight(); y++) {
				for (int x = 0; x < grid.getGridWidth(); x++) {
					if (grid.getTMACore(y, x) == selectedCore) {
						row = y;
						col = x;							
						break;
					}
				}
			}
		}
		
		// We need a selected core to know what to remove
		if (row < 0 || col < 0) {
			Dialogs.showErrorMessage(title, "Please select a TMA core to indicate which " + typeString + " to remove");
			return false;
		}
		
		// Check we have enough rows/columns - if not, this is just a clear operation
		if ((removeRow && grid.getGridHeight() <= 1) || (!removeRow && grid.getGridWidth() <= 1)) {
			if (Dialogs.showConfirmDialog(title, "Are you sure you want to delete the entire TMA grid?"))
				hierarchy.setTMAGrid(null);
			return false;
		}
		
		// Confirm the removal - add 1 due to 'base 0' probably not being expected by most users...
		int num = removeRow ? row : col;
		if (!Dialogs.showConfirmDialog(title, "Are you sure you want to delete " + typeString + " " + (num+1) + " from TMA grid?"))
			return false;
		
		// Create a new grid
		List<TMACoreObject> coresNew = new ArrayList<>();
		for (int r = 0; r < grid.getGridHeight(); r++) {
			if (removeRow && row == r)
				continue;
			for (int c = 0; c < grid.getGridWidth(); c++) {
				if (!removeRow && col == c)
					continue;
				coresNew.add(grid.getTMACore(r, c));
			}
		}
		int newWidth = removeRow ? grid.getGridWidth() : grid.getGridWidth() - 1;
		TMAGrid gridNew = DefaultTMAGrid.create(coresNew, newWidth);
		hierarchy.setTMAGrid(gridNew);
		hierarchy.getSelectionModel().clearSelection();
		
		// Request new labels
		promptToRelabelTMAGrid(imageData);
		
		return true;
	}
	
	
	
private static enum TMAAddType {ROW_AFTER, ROW_BEFORE, COLUMN_AFTER, COLUMN_BEFORE;
		
		private String commandName() {
			switch(this) {
			case COLUMN_AFTER:
				return "Add TMA column after";
			case COLUMN_BEFORE:
				return "Add TMA column before";
			case ROW_AFTER:
				return "Add TMA row after";
			case ROW_BEFORE:
				return "Add TMA row before";
			default:
				throw new IllegalArgumentException("Unknown enum value: " + this);
			}
		}
	
	};
	
	
	/**
	 * Prompt to add a row to a TMA grid after the row containing the currently-selected object.
	 * <p>
	 * After this command is run, the user will be prompted to relabel the TMA grid.
	 * 
	 * @param imageData the image data containing the TMA grid
	 * @return true if the TMA grid was modified, false otherwise (e.g. if the user cancelled)
	 */
	public static boolean promptToAddRowAfterSelected(final ImageData<?> imageData) {
		return promptToAddTMARowOrColumn(imageData, TMAAddType.ROW_AFTER);
	}
	
	/**
	 * Prompt to add a row to a TMA grid before the row containing the currently-selected object.
	 * <p>
	 * After this command is run, the user will be prompted to relabel the TMA grid.
	 * 
	 * @param imageData the image data containing the TMA grid
	 * @return true if the TMA grid was modified, false otherwise (e.g. if the user cancelled)
	 */
	public static boolean promptToAddRowBeforeSelected(final ImageData<?> imageData) {
		return promptToAddTMARowOrColumn(imageData, TMAAddType.ROW_BEFORE);
	}
	
	/**
	 * Prompt to add a column to a TMA grid after the column containing the currently-selected object.
	 * <p>
	 * After this command is run, the user will be prompted to relabel the TMA grid.
	 * 
	 * @param imageData the image data containing the TMA grid
	 * @return true if the TMA grid was modified, false otherwise (e.g. if the user cancelled)
	 */
	public static boolean promptToAddColumnAfterSelected(final ImageData<?> imageData) {
		return promptToAddTMARowOrColumn(imageData, TMAAddType.COLUMN_AFTER);
	}
	
	/**
	 * Prompt to add a column to a TMA grid before the column containing the currently-selected object.
	 * <p>
	 * After this command is run, the user will be prompted to relabel the TMA grid.
	 * 
	 * @param imageData the image data containing the TMA grid
	 * @return true if the TMA grid was modified, false otherwise (e.g. if the user cancelled)
	 */
	public static boolean promptToAddColumnBeforeSelected(final ImageData<?> imageData) {
		return promptToAddTMARowOrColumn(imageData, TMAAddType.COLUMN_BEFORE);
	}
	
	
	/**
	 * Add a new row or column to a TMA grid.
	 * 
	 * @param imageData
	 * @param type
	 * @return 
	 */
	private static boolean promptToAddTMARowOrColumn(final ImageData<?> imageData, final TMAAddType type) {
		String NAME = type.commandName();
		
		if (imageData == null) {
			Dialogs.showNoImageError(NAME);
			return false;
		}
		if (imageData.getHierarchy().getTMAGrid() == null) {
			Dialogs.showErrorMessage(NAME, "No image with dearrayed TMA cores selected!");
			return false;
		}
		
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
		TMACoreObject selectedCore = null;
		if (selected != null)
			selectedCore = PathObjectTools.getAncestorTMACore(selected);
		
		TMAGrid gridNew = createAugmentedTMAGrid(hierarchy, selectedCore, type);
		double w = imageData.getServer().getWidth();
		double h = imageData.getServer().getHeight();
		
		// Check if the core centroids all fall within the image or not
		int outsideCores = 0;
		for (TMACoreObject core : gridNew.getTMACoreList()) {
			// Shouldn't happen...
			if (!core.hasROI())
				continue;
			
			// Check if the centroid for any *new* core falls within the image
			// (don't fail if an existing core is outside)
			double x = core.getROI().getCentroidX();
			double y = core.getROI().getCentroidY();
			if (x < 0 || x >= w || y < 0 || y >= h) {
				if (!hierarchy.getTMAGrid().getTMACoreList().contains(core)) {
					outsideCores++;
				}
//				throw new IllegalArgumentException("Cannot update TMA grid - not enough space within image");
			}
		}
		if (outsideCores > 0) {
			String label = outsideCores == 1 ? "core" : "cores";
			if (!Dialogs.showConfirmDialog("Add to TMA Grid", "Not enough space within image to store " + outsideCores + " new " + label + " - proceed anyway?"))
				return false;
		}
		
		// If we got this far, update the grid
		hierarchy.setTMAGrid(gridNew);
		
		// Prompt for relabelling
		TMACommands.promptToRelabelTMAGrid(imageData);
		return true;
	}
	
	
	/**
	 * Add a new row or column to a TMA grid.
	 * 
	 * @param hierarchy
	 * @param selectedCore
	 * @param type
	 * @return 
	 */
	private static TMAGrid createAugmentedTMAGrid(final PathObjectHierarchy hierarchy, final TMACoreObject selectedCore, final TMAAddType type) {
		
		TMAGrid grid = hierarchy.getTMAGrid();
		
		// Convert to easier form
		boolean addAfter = type == TMAAddType.COLUMN_AFTER || type == TMAAddType.ROW_AFTER;
		boolean addColumn = type == TMAAddType.COLUMN_AFTER || type == TMAAddType.COLUMN_BEFORE;
		
		// Try to identify the row/column that we want
		int row = -1;
		int col = -1;
		if (selectedCore != null) {
			for (int y = 0; y < grid.getGridHeight(); y++) {
				for (int x = 0; x < grid.getGridWidth(); x++) {
					if (grid.getTMACore(y, x) == selectedCore) {
						if (addAfter) {
							row = y+1;
							col = x+1;
						} else {
							row = y;
							col = x;							
						}
						break;
					}
				}
			}
		}
		
		// If we don't have a row or column, choose based on the add type
		if (row < 0) {
			switch (type) {
			case COLUMN_AFTER:
				col = grid.getGridWidth();
				break;
			case COLUMN_BEFORE:
				col = 0;
				break;
			case ROW_AFTER:
				row = grid.getGridHeight();
				break;
			case ROW_BEFORE:
				row = 0;
				break;
			default:
				break;
			}
		}
		
		// Compute the width of the new grid
		int newWidth = addColumn ? grid.getGridWidth() + 1 : grid.getGridWidth();
		int newHeight = addColumn ? grid.getGridHeight() : grid.getGridHeight() + 1;
		
		// Loop through cores, getting mean widths, heights & displacements
		RunningStatistics statsWidth = new RunningStatistics();
		RunningStatistics statsHeight = new RunningStatistics();
		RunningStatistics statsDx = new RunningStatistics();
		RunningStatistics statsDy = new RunningStatistics();
		for (int r = 0; r < grid.getGridHeight(); r++) {
			TMACoreObject coreColBefore = null;
			for (int c = 0; c < grid.getGridWidth(); c++) {
				TMACoreObject core = grid.getTMACore(r, c);
				if (!core.hasROI())
					continue;
				statsWidth.addValue(core.getROI().getBoundsWidth());
				statsHeight.addValue(core.getROI().getBoundsHeight());
				if (coreColBefore != null && coreColBefore.hasROI()) {
					statsDx.addValue(core.getROI().getCentroidX() - coreColBefore.getROI().getCentroidX());
				}
				if (r > 0) {
					TMACoreObject coreRowBefore = grid.getTMACore(r-1, c);
					if (coreRowBefore != null && coreRowBefore.hasROI()) {
						statsDy.addValue(core.getROI().getCentroidY() - coreRowBefore.getROI().getCentroidY());
					}					
				}
				coreColBefore = core;
			}
		}
		
		double meanWidth = statsWidth.getMean();
		double meanHeight = statsHeight.getMean();
		double meanDx = statsDx.getMean();
		double meanDy = statsDy.getMean();
		double diameter = (meanWidth + meanHeight) / 2;
		
		// Create a new list of cores, adding where necessary
		List<TMACoreObject> coresNew = new ArrayList<>();
		for (int r = 0; r < newHeight; r++) {
			for (int c = 0; c < newWidth; c++) {
				// Copy existing rows & columns, or create new cores if required
				if (addColumn) {
					if (c < col) {
						coresNew.add(grid.getTMACore(r, c));
					} else if (c > col) {
						coresNew.add(grid.getTMACore(r, c-1));						
					} else if (c == col) {
						// Try to get average x & y coordinates between surrounding columns
						double x1, x2, y;
						if (col == 0) {
							x2 = grid.getTMACore(r, c).getROI().getCentroidX();
							x1 = x2 - meanDx*2;
							y = grid.getTMACore(r, c).getROI().getCentroidY();
						} else if (col == grid.getGridWidth()) {
							x1 = grid.getTMACore(r, c-1).getROI().getCentroidX();
							x2 = x1 + meanDx*2;							
							y = grid.getTMACore(r, c-1).getROI().getCentroidY();
						} else {
							x1 = grid.getTMACore(r, c-1).getROI().getCentroidX();
							x2 = grid.getTMACore(r, c).getROI().getCentroidX();
							y = (grid.getTMACore(r, c-1).getROI().getCentroidY() + grid.getTMACore(r, c).getROI().getCentroidY())/2;
						}
						TMACoreObject coreNew = PathObjects.createTMACoreObject((x1+x2)/2, y, diameter, true);
						coresNew.add(coreNew);
					}
				} else {
					if (r < row) {
						coresNew.add(grid.getTMACore(r, c));
					} else if (r > row) {
						coresNew.add(grid.getTMACore(r-1, c));
					} else if (r == row) {
						// Try to get average x & y coordinates between surrounding columns
						double x, y1, y2;
						if (row == 0) {
							y2 = grid.getTMACore(r, c).getROI().getCentroidY();
							y1 = y2 - meanDy*2;
							x = grid.getTMACore(r, c).getROI().getCentroidX();
						} else if (row == grid.getGridHeight()) {
							y1 = grid.getTMACore(r-1, c).getROI().getCentroidY();
							y2 = y1 + meanDy*2;							
							x = grid.getTMACore(r-1, c).getROI().getCentroidX();
						} else {
							y1 = grid.getTMACore(r-1, c).getROI().getCentroidY();
							y2 = grid.getTMACore(r, c).getROI().getCentroidY();
							x = (grid.getTMACore(r-1, c).getROI().getCentroidX() + grid.getTMACore(r, c).getROI().getCentroidX())/2;
						}
						TMACoreObject coreNew = PathObjects.createTMACoreObject(x, (y1+y2)/2, diameter, true);
						coresNew.add(coreNew);
					}
				}
			}
		}
		
		// Update with a new TMAGrid
		return DefaultTMAGrid.create(coresNew, newWidth);
	}
	

}