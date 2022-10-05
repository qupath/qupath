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

package qupath.lib.objects;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import qupath.lib.regions.ImageRegion;

/**
 * Interface defining a basic structure to represent relationships between PathObjects that do not fit with  
 * the parent-child idea of the PathObjectHierarchy.
 * <p>
 * Example applications would be Delaunay triangulation.
 * <p>
 * Such connections can be represented on an overlay by drawing lines between object centroids.
 * 
 * @author Pete Bankhead
 *
 */
public interface PathObjectConnectionGroup {
	
	/**
	 * Returns true if the specified PathObject is contained within this group.
	 * 
	 * @param pathObject
	 * @return
	 */
	public boolean containsObject(final PathObject pathObject);
	
	/**
	 * Get an unmodifiable collection containing all the PathObjects contained within this group.
	 * 
	 * @return
	 */
	public Collection<PathObject> getPathObjects();
	
	/**
	 * Get all the connections to a specified PathObject stored in this group.
	 * <p>
	 * If containsObject(pathObject) returns null, this will return an empty list (and not null).
	 * 
	 * @param pathObject
	 * @return
	 */
	public List<PathObject> getConnectedObjects(final PathObject pathObject);
	
	
	/**
	 * Get all the objects with connections that <i>may</i> intersect the specified region.
	 * @param region
	 * @return 
	 * @implNote the default implementation simply checks the z and t values of the region, and ensures they 
	 *           match with the ROI of an object being returned. Subclasses should provide more optimized implementations.
	 */
	public default Collection<PathObject> getPathObjectsForRegion(ImageRegion region) {
		return getPathObjects()
				.stream()
				.filter(p -> p.getROI().getZ() == region.getZ() && p.getROI().getT() == region.getT())
				.collect(Collectors.toList());
	}

}
