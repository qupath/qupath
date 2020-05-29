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

package qupath.lib.classifiers.object;

import java.util.Collection;
import java.util.stream.Collectors;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;

/**
 * Abstract class to help with the creation of object classifiers.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class AbstractObjectClassifier<T> implements ObjectClassifier<T> {
	
	/**
	 * Choose which objects are supported (often detections)
	 */
	private PathObjectFilter filter;
	
	/**
	 * Timestamp representing when the classifier was created/trained
	 */
	private long timestamp = System.currentTimeMillis();
	
	protected AbstractObjectClassifier(final PathObjectFilter filter) {
		this.filter = filter;
	}

	@Override
	public int classifyObjects(ImageData<T> imageData, boolean resetExistingClass) {
		return classifyObjects(imageData, getCompatibleObjects(imageData), resetExistingClass);
	}
	
	@Override
	public Collection<PathObject> getCompatibleObjects(ImageData<T> imageData) {
		var pathObjects = imageData.getHierarchy().getFlattenedObjectList(null);
		if (filter != null)
			pathObjects = pathObjects.stream().filter(filter).collect(Collectors.toList());
		return pathObjects;
	}

}