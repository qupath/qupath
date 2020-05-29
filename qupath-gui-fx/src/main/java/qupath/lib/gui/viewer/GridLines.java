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

package qupath.lib.gui.viewer;

import qupath.lib.gui.prefs.PathPrefs;

/**
 * 
 * Simple class used for storing information related to GridLines that may be painted over an image.
 * <p>
 * This implementation simply wraps around the properties in {@link PathPrefs}.
 * 
 * @author Pete Bankhead
 *
 */
public class GridLines {
	
	/**
	 * Starting x coordinate for any counting grid (usually 0).
	 * @return
	 * @see PathPrefs#gridStartXProperty()
	 */
	public double getStartX() {
		return PathPrefs.gridStartXProperty().get();
	}

	/**
	 * Starting y coordinate for any counting grid (usually 0).
	 * @return
	 * @see PathPrefs#gridStartYProperty()
	 */
	public double getStartY() {
		return PathPrefs.gridStartYProperty().get();
	}

	/**
	 * Horizontal spacing between lines for any counting grid.
	 * @return
	 * @see PathPrefs#gridSpacingXProperty()
	 */
	public double getSpaceX() {
		return PathPrefs.gridSpacingXProperty().get();
	}
	
	/**
	 * Vertical spacing between lines for any counting grid.
	 * @return
	 * @see PathPrefs#gridSpacingYProperty()
	 */
	public double getSpaceY() {
		return PathPrefs.gridSpacingYProperty().get();
	}
	
	/**
	 * Set the horizontal spacing between lines for any counting grid.
	 * @param spaceX
	 * @see PathPrefs#gridSpacingXProperty()
	 */
	public void setSpaceX(double spaceX) {
		PathPrefs.gridSpacingXProperty().set(spaceX);
	}

	/**
	 * Set the vertical spacing between lines for any counting grid.
	 * @param spaceY
	 * @see PathPrefs#gridSpacingYProperty()
	 */
	public void setSpaceY(double spaceY) {
		PathPrefs.gridSpacingYProperty().set(spaceY);
	}
	
	/**
	 * Query whether to use calibrated units when calculating coordinates for any counting grid.
	 * @return
	 * @see PathPrefs#gridScaleMicronsProperty()
	 */
	public boolean useMicrons() {
		return PathPrefs.gridScaleMicronsProperty().get();
	}
	
	/**
	 * Specify whether to use calibrated units when calculating coordinates for any counting grid.
	 * @param useMicrons
	 * @see PathPrefs#gridScaleMicronsProperty()
	 */
	public void setUseMicrons(boolean useMicrons) {
		PathPrefs.gridScaleMicronsProperty().set(useMicrons);
	}

}
