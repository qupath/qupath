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


package qupath.lib.analysis.heatmaps;

import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.regions.RegionRequest;

/**
 * Class to represent density maps, used to visualize and annotate hotspots within images.
 * <p>
 * Note that density maps are always expected to be 'small' (i.e. fitting easily into RAM), since they can summarize 
 * the content of a large image. This makes them much easier to generate and use.
 * 
 * @author Pete Bankhead
 * 
 */
public interface DensityMap {
	
	/**
	 * Get the region to which the density map corresponds.
	 * @return
	 */
	public RegionRequest getRegion();
	
	/**
	 * Get the main density values. The interpretation of these varies according to the type of the map.
	 * @return
	 */
	public SimpleImage getValues();
	
	/**
	 * Get optional alpha values that can control how the densities are displayed.
	 * <p>
	 * For example, for a density map that gives the percentage of positive cells the alpha values could 
	 * provide the total cell counts - thereby enabling regions with low and high cell counts to be displayed 
	 * differently.
	 * 
	 * @return an alpha image if available, or else null
	 */
	public default SimpleImage getAlpha() {
		return null;
	}
	
	/**
	 * Get text corresponding to a pixel location, defined in the full image space.
	 * @param x
	 * @param y
	 * @return
	 */
	public default String getText(double x, double y) {
		return null;
	}
	

}
