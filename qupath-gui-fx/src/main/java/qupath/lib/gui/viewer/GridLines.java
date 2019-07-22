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
	
	public double getStartX() {
		return PathPrefs.gridStartXProperty().get();
	}

	public double getStartY() {
		return PathPrefs.gridStartYProperty().get();
	}

	public double getSpaceX() {
		return PathPrefs.gridSpacingXProperty().get();
	}

	public double getSpaceY() {
		return PathPrefs.gridSpacingYProperty().get();
	}
	
	public void setSpaceX(double spaceX) {
		PathPrefs.gridSpacingXProperty().set(spaceX);
	}

	public void setSpaceY(double spaceY) {
		PathPrefs.gridSpacingYProperty().set(spaceY);
	}
	
	public boolean useMicrons() {
		return PathPrefs.gridScaleMicrons().get();
	}
	
	public void setUseMicrons(boolean useMicrons) {
		PathPrefs.gridScaleMicrons().set(useMicrons);
	}

}
