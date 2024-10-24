/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.List;

/**
 * A group of properties determining what should be displayed for each viewer.
 * 
 * @author Pete Bankhead
 *
 */
public class ViewerPlusDisplayOptions {
	
	private BooleanProperty showOverview = new SimpleBooleanProperty(null, "showOverview", true);
	private BooleanProperty showLocation = new SimpleBooleanProperty(null, "showLocation", true);
	private BooleanProperty showScalebar = new SimpleBooleanProperty(null, "showScalebar", true);

	private static ViewerPlusDisplayOptions SHARED_INSTANCE = createSharedInstance();

	private static ViewerPlusDisplayOptions createSharedInstance() {
		ViewerPlusDisplayOptions options = new ViewerPlusDisplayOptions();
		for (var prop : List.of(options.showOverview, options.showLocation, options.showScalebar)) {
			prop.bindBidirectional(PathPrefs.createPersistentPreference("viewerOptions_" + prop.getName(), prop.get()));
		}
		return options;
	}

	/**
	 * Get a shared instance with persistence properties.
	 * @return
	 */
	public static ViewerPlusDisplayOptions getSharedInstance() {
		return SHARED_INSTANCE;
	}

	/**
	 * Show the overview image.
	 * @return
	 */
	public BooleanProperty showOverviewProperty() {
		return showOverview;
	}
	
	/**
	 * Show the cursor location.
	 * @return
	 */
	public BooleanProperty showLocationProperty() {
		return showLocation;
	}
	
	/**
	 * Show the scalebar.
	 * @return
	 */
	public BooleanProperty showScalebarProperty() {
		return showScalebar;
	}
	
	/**
	 * Get the value of {@link #showOverviewProperty()}.
	 * @return
	 */
	public boolean getShowOverview() {
		return showOverview.get();
	}

	/**
	 * Get the value of {@link #showLocationProperty()}.
	 * @return
	 */
	public boolean getShowLocation() {
		return showLocation.get();
	}
	
	/**
	 * Get the value of {@link #showScalebarProperty()}.
	 * @return
	 */
	public boolean getShowScalebar() {
		return showScalebar.get();
	}

	/**
	 * Set the value of {@link #showOverviewProperty()}.
	 * @param show
	 */
	public void setShowOverview(final boolean show) {
		showOverview.set(show);
	}

	/**
	 * Set the value of {@link #showLocationProperty()}.
	 * @param show
	 */
	public void setShowLocation(final boolean show) {
		showLocation.set(show);
	}
	
	/**
	 * Set the value of {@link #showScalebarProperty()}.
	 * @param show
	 */
	public void setShowScalebar(final boolean show) {
		showScalebar.set(show);
	}

}
