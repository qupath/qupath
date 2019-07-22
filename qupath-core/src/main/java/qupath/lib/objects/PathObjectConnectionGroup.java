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

package qupath.lib.objects;

import java.util.Collection;
import java.util.List;

import qupath.lib.objects.PathObject;

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

}
