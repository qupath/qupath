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

import javafx.event.ActionEvent;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.GUIActions;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;

/**
 * Add row or column to an existing TMA grid.
 * 
 * @author Pete Bankhead
 *
 */
public class TMAGridAdd implements PathCommand {
	
	public static enum TMAAddType {ROW_AFTER, ROW_BEFORE, COLUMN_AFTER, COLUMN_BEFORE};
	
	public static String NAME;
	
	private QuPathGUI qupath;
	private TMAAddType type;
	
	public TMAGridAdd(final QuPathGUI qupath, final TMAAddType type) {
		this.qupath = qupath;
		this.type = type;
		String commandName;
		switch(type) {
		case COLUMN_AFTER:
			commandName = "Add TMA column after";
			return;
		case COLUMN_BEFORE:
			commandName = "Add TMA column before";
			return;
		case ROW_AFTER:
			commandName = "Add TMA row after";
			return;
		case ROW_BEFORE:
			commandName = "Add TMA row before";
			return;
		default:
			commandName = null;
		}
		NAME = commandName;
	}

	@Override
	public void run() {
		ImageData<?> imageData = qupath.getImageData();
		if (imageData == null || imageData.getHierarchy().getTMAGrid() == null) {
			DisplayHelpers.showErrorMessage(NAME, "No image with dearrayed TMA cores selected!");
			return;
		}
		try {
			if (addToTMA(imageData, type)) {
				qupath.getAction(GUIActions.TMA_RELABEL).handle(new ActionEvent());
			}
		} catch (Exception e) {
			DisplayHelpers.showErrorMessage(NAME, e.getMessage());
		}
	}
	
	/**
	 * Add a new row or column to a TMA grid.
	 * 
	 * @param imageData
	 * @param type
	 */
	public static boolean addToTMA(final ImageData<?> imageData, final TMAAddType type) {
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
			if (!DisplayHelpers.showConfirmDialog("Add to TMA Grid", "Not enough space within image to store " + outsideCores + " new " + label + " - proceed anyway?"))
				return false;
		}
		
		// If we got this far, update the grid
		hierarchy.setTMAGrid(gridNew);
		return true;
	}
	
	
	/**
	 * Add a new row or column to a TMA grid.
	 * 
	 * @param hierarchy
	 * @param selectedCore
	 * @param type
	 */
	public static TMAGrid createAugmentedTMAGrid(final PathObjectHierarchy hierarchy, final TMACoreObject selectedCore, final TMAAddType type) {
		
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
	
	
	
//	public static void updateTMAGridLabels(final TMAGrid grid) {
//		
//		// Try to figure out where numbers and letters are used
//		Map<Integer, List<String>> rowMap1 = new HashMap<>();
//		Map<Integer, List<String>> rowMap2 = new HashMap<>();
//		Map<Integer, List<String>> colMap1 = new HashMap<>();
//		Map<Integer, List<String>> colMap2 = new HashMap<>();
//		for (int r = 0; r < grid.getGridHeight(); r++) {
//			Set<String> part1 = new HashSet<>();
//			Set<String> part2 = new HashSet<>();
//			for (int c = 0; c < grid.getGridWidth(); c++) {
//				TMACoreObject core = grid.getTMACore(r, c);
//				String name = core.getName();
//				if (name == null)
//					continue;
//				String[] parts = name.split("-");
//				if (parts.length != 2)
//					continue;
//				part1.add(parts[0]);
//				part2.add(parts[1]);
//			}
//			// Can't decide if we don't know anything yet...
//			if (part1.isEmpty() && part2.isEmpty())
//				continue;
//			// 
//		}
//		
//	}
	
	

}
