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

package qupath.lib.gui.commands.selectable;

import qupath.lib.gui.commands.interfaces.PathSelectableCommand;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.OverlayOptions.DetectionDisplayMode;

/**
 * Selectable command to specify a cell display mode (i.e. cell border and/or nucleus border).
 * 
 * @author Pete Bankhead
 *
 */
public class CellDisplaySelectable implements PathSelectableCommand {
	
	private OverlayOptions overlayOptions;
	private DetectionDisplayMode mode;
	
	public CellDisplaySelectable(final OverlayOptions overlayOptions, final DetectionDisplayMode mode) {
		this.overlayOptions = overlayOptions;
		this.mode = mode;
	}

	@Override
	public boolean isSelected() {
		return mode == overlayOptions.getCellDisplayMode();
	}

	@Override
	public void setSelected(boolean selected) {
		if (selected)
			overlayOptions.setCellDisplayMode(mode);
	}
	
//	private static final long serialVersionUID = 1L;
//	
//	private OverlayOptions overlayOptions;
//	private CellDisplayMode mode;
//	
//	public CellDisplayAction(final OverlayOptions overlayOptions, final CellDisplayMode mode) {
//		super(getTitle(mode), null);
//		this.overlayOptions = overlayOptions;
//		this.mode = mode;
//		putValue(Action.SELECTED_KEY, mode == overlayOptions.getCellDisplayMode());
//		putValue(SHORT_DESCRIPTION, "Show " + getTitle(mode));
//	}
//	
//	private static String getTitle(CellDisplayMode mode) {
//		switch (mode) {
//		case BOUNDARIES_ONLY:
//			return "Cell boundaries only";
//		case NUCLEI_AND_BOUNDARIES:
//			return "Nuclei & cell boundaries";
//		case NUCLEI_ONLY:
//			return "Nuclei only";
//		default:
//			break;
//		}
//		return null;
//	}
//		
//	@Override
//	public void actionPerformed(ActionEvent e) {
//		if ((Boolean)getValue(Action.SELECTED_KEY))
//			overlayOptions.setCellDisplayMode(mode);
//	}
	
}