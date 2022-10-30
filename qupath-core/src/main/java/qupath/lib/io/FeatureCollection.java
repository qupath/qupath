/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2020-2022 QuPath developers, The University of Edinburgh
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


package qupath.lib.io;

import java.util.ArrayList;
import java.util.Collection;

import qupath.lib.objects.PathObject;

/**
 * Class to wrap a collection of objects to indicate they should be export as a GeoJSON "FeatureCollection".
 * 
 * @author Pete Bankhead
 */
public class FeatureCollection {
	
	private Collection<? extends PathObject> pathObjects;
	private boolean includeChildren = false;
	
	private FeatureCollection(Collection<? extends PathObject> pathObjects, boolean includeChildren) {
		this.pathObjects = pathObjects;
		this.includeChildren = includeChildren;
	}
	
	/**
	 * If true, include child objects nested within the output.
	 * @return
	 */
	public boolean getIncludeChildren() {
		return includeChildren;
	}
	
	/**
	 * Get the objects being wrapped.
	 * @return
	 */
	public Collection<? extends PathObject> getPathObjects() {
		return pathObjects;
	}

	/**
	 * Wrap a collection of PathObjects as a FeatureCollection, ignoring nested (child) objects.
	 * The purpose of this is to enable exporting a GeoJSON FeatureCollection that may be reused in other software.
	 * @param pathObjects a collection of path objects to store in a feature collection
	 * @return a feature collection that can be used with {@link GsonTools}
	 * @see #wrap(Collection, boolean)
	 */
	public static FeatureCollection wrap(Collection<? extends PathObject> pathObjects) {
		return wrap(pathObjects, false);
	}

	/**
	 * Wrap a collection of PathObjects as a FeatureCollection. The purpose of this is to enable 
	 * exporting a GeoJSON FeatureCollection that may be reused in other software.
	 * @param pathObjects a collection of path objects to store in a feature collection
	 * @param includeChildObjects if true, include child object in the feature collection.
	 * @return a feature collection that can be used with {@link GsonTools}
	 * @implNote Currently no checks are made to avoid duplicate objects. This may change in the future, 
	 *           but for now it's best to be ensure the input does not contain duplicates.
	 *           This is particularly relevant is {@code includeChildObjects} is true, where duplicates 
	 *           may be nested inside other objects.
	 * @implSpec a defensive copy of the collection is created, so that changes to the original collection will not 
	 *           affect the wrapped FeatureCollection - although changes to the underlying objects <i>will</i> make 
	 *           a difference.
	 */
	public static FeatureCollection wrap(Collection<? extends PathObject> pathObjects, boolean includeChildObjects) {
		if (includeChildObjects)
			return new FeatureCollection(new ArrayList<>(pathObjects), includeChildObjects);
		else
			return new FeatureCollection(new ArrayList<>(pathObjects), includeChildObjects);			
	}
	
}