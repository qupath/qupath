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
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.GUIActions;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
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
public class TMAGridRemove implements PathCommand {
	
	public static enum TMARemoveType {ROW, COLUMN};
	
	public static String NAME;
	
	private QuPathGUI qupath;
	private TMARemoveType type;
	
	public TMAGridRemove(final QuPathGUI qupath, final TMARemoveType type) {
		this.qupath = qupath;
		this.type = type;
		switch(type) {
		case ROW:
			NAME = "Remove TMA row";
			return;
		case COLUMN:
			NAME = "Remove TMA row";
			return;
		}
	}

	@Override
	public void run() {
		ImageData<?> imageData = qupath.getImageData();
		if (imageData == null || imageData.getHierarchy().getTMAGrid() == null) {
			DisplayHelpers.showErrorMessage(NAME, "No image with dearrayed TMA cores selected!");
			return;
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
		boolean removeRow = type == TMARemoveType.ROW;
		String typeString = removeRow ? "row" : "column";
		if (row < 0 || col < 0) {
			DisplayHelpers.showErrorMessage(NAME, "Please select a TMA core to indicate which " + typeString + " to remove");
			return;
		}
		
		// Check we have enough rows/columns - if not, this is just a clear operation
		if ((removeRow && grid.getGridHeight() <= 1) || (!removeRow && grid.getGridWidth() <= 1)) {
			if (DisplayHelpers.showConfirmDialog(NAME, "Are you sure you want to delete the entire TMA grid?"))
				hierarchy.setTMAGrid(null);
			return;
		}
		
		// Confirm the removal - add 1 due to 'base 0' probably not being expected by most users...
		int num = removeRow ? row : col;
		if (!DisplayHelpers.showConfirmDialog(NAME, "Are you sure you want to delete " + typeString + " " + (num+1) + " from TMA grid?"))
			return;
		
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
		qupath.getAction(GUIActions.TMA_RELABEL).handle(new ActionEvent());
	}
	

}
