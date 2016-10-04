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

/**
 * 
 * Simple class used for storing information related to GridLines that may be painted over an image.
 * 
 * @author Pete Bankhead
 *
 */
public class GridLines {
	
	private double startX = 0, startY = 0;
	private double spaceX = 250, spaceY = 250;
	private boolean useMicrons = true;
	
	public double getStartX() {
		return startX;
	}

	public double getStartY() {
		return startY;
	}

	public double getSpaceX() {
		return spaceX;
	}

	public double getSpaceY() {
		return spaceY;
	}
	
	public void setSpaceX(double spaceX) {
		this.spaceX = spaceX;
	}

	public void setSpaceY(double spaceY) {
		this.spaceY = spaceY;
	}
	
	public boolean useMicrons() {
		return useMicrons;
	}
	
	public void setUseMicrons(boolean useMicrons) {
		this.useMicrons = useMicrons;
	}

}
