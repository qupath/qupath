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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * A group of properties determining what should be displayed for each viewer.
 * 
 * @author Pete Bankhead
 *
 */
public class ViewerPlusDisplayOptions {
	
	private BooleanProperty showOverview = new SimpleBooleanProperty(true);
	private BooleanProperty showLocation = new SimpleBooleanProperty(true);
	private BooleanProperty showScalebar = new SimpleBooleanProperty(true);
	
	public BooleanProperty showOverviewProperty() {
		return showOverview;
	}
	
	public BooleanProperty showLocationProperty() {
		return showLocation;
	}
	
	public BooleanProperty showScalebarProperty() {
		return showScalebar;
	}
	
	public boolean getShowOverview() {
		return showOverview.get();
	}

	public boolean getShowLocation() {
		return showLocation.get();
	}
	
	public boolean getShowScalebar() {
		return showScalebar.get();
	}

	public void setShowOverview(final boolean show) {
		showOverview.set(show);
	}

	public void setShowLocation(final boolean show) {
		showLocation.set(show);
	}
	
	public void setShowScalebar(final boolean show) {
		showScalebar.set(show);
	}

}
