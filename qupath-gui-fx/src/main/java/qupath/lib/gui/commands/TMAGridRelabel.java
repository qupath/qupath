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

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Relabel the cores of a TMA grid.
 * 
 * This is usually necessary after adding/removing rows or columns.
 * 
 * @author Pete Bankhead
 *
 */
public class TMAGridRelabel implements PathCommand {
	
	public static String NAME = "Relabel TMA cores";
	
	private QuPathGUI qupath;
	
	public TMAGridRelabel(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		ImageData<?> imageData = qupath.getImageData();
		if (imageData == null || imageData.getHierarchy().getTMAGrid() == null) {
			DisplayHelpers.showErrorMessage(NAME, "No TMA grid selected!");
			return;
		}
		
		ParameterList params = new ParameterList();
		params.addStringParameter("labelsHorizontal", "Column labels", "1-16", "Enter column labels.\nThis can be a continuous range of letters or numbers (e.g. 1-10 or A-J),\nor a discontinuous list separated by spaces (e.g. A B C E F G).");
		params.addStringParameter("labelsVertical", "Row labels", "A-J", "Enter row labels.\nThis can be a continuous range of letters or numbers (e.g. 1-10 or A-J),\nor a discontinuous list separated by spaces (e.g. A B C E F G).");
		params.addChoiceParameter("labelOrder", "Label order", "Row first", new String[]{"Column first", "Row first"}, "Create TMA labels either in the form Row-Column or Column-Row");
		
		if (!DisplayHelpers.showParameterDialog(NAME, params))
			return;
		
		// Apply the labels
		String[] columnLabels = PathObjectTools.parseTMALabelString(params.getStringParameterValue("labelsHorizontal"));
		String[] rowLabels = PathObjectTools.parseTMALabelString(params.getStringParameterValue("labelsVertical"));
		TMAGrid grid = imageData.getHierarchy().getTMAGrid();
		if (columnLabels.length < grid.getGridWidth()) {
			DisplayHelpers.showErrorMessage(NAME, "Not enough column labels specified!");
			return;			
		}
		if (rowLabels.length < grid.getGridHeight()) {
			DisplayHelpers.showErrorMessage(NAME, "Not enough row labels specified!");
			return;			
		}
		
		boolean rowFirst = "Row first".equals(params.getChoiceParameterValue("labelOrder"));
		for (int r = 0; r < grid.getGridHeight(); r++) {
			for (int c = 0; c < grid.getGridWidth(); c++) {
				String name;
				if (rowFirst)
					name = rowLabels[r] + "-" + columnLabels[c];
				else
					name = columnLabels[r] + "-" + rowLabels[c];
				grid.getTMACore(r, c).setName(name);
			}			
		}
		imageData.getHierarchy().fireObjectsChangedEvent(this, new ArrayList<>(grid.getTMACoreList()));
	}
	
}
