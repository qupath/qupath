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
import java.util.Map;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Interface defining object classifiers, which assign {@linkplain PathClass PathClasses} to {@linkplain PathObject PathObjects}.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface ObjectClassifier<T> {

	/**
	 * Get the classifications set by this classifier.
	 * @return
	 */
	Collection<PathClass> getPathClasses();

	/**
	 * Classify all compatible objects from an {@link ImageData}.
	 * <p>
	 * Note: this method does <i>not</i> typically fire any hierarchy change/object classification events.
	 * It is up to the caller to fire these events if required.
	 * 
	 * @param imageData data containing the object hierarchy
	 * @param resetExistingClass 
	 * @return the number of objects whose classification was changed.
	 * 
	 * @see #getCompatibleObjects(ImageData)
	 */
	int classifyObjects(ImageData<T> imageData, boolean resetExistingClass);

	/**
	 * Classify the objects in the specified collection.
	 * This provides a means to specify exactly which objects should be classified, avoiding reliance on {@link #getCompatibleObjects(ImageData)}.
	 * <p>
	 * Note: this method does <i>not</i> typically fire any hierarchy change/object classification events.
	 * It is up to the caller to fire these events if required.
	 * 
	 * @param imageData data that may or may not be required for classification depending upon how features are extracted
	 * @param pathObjects the objects to classify
	 * @param resetExistingClass 
	 * @return the number of objects whose classification was changed.
	 * 
	 * @see #classifyObjects(ImageData, boolean)
	 * @see #getCompatibleObjects(ImageData)
	 */
	int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, boolean resetExistingClass);

	/**
	 * Get the objects from an {@link ImageData} that are compatible with this classifier.
	 * @param imageData 
	 * @return a collection of compatible objects, or empty list if no compatible objects are found
	 */
	Collection<PathObject> getCompatibleObjects(ImageData<T> imageData);
	
	/**
	 * Check for missing features, returning the names and number of input objects missing the specified features.
	 * This is useful as a warning that the input for the classifier may not be valid.
	 * Default implementation returns an empty map; however, implementations should attempt to provide a meaningful 
	 * output if possible. Features that are not missing should not be included in the output.
	 * @param imageData image containing the objects to test
	 * @param pathObjects objects to test for missing features; if not available, {@link #getCompatibleObjects(ImageData)} will be called.
	 * @return a map of feature names and the number of objects missing the corresponding features.
	 */
	Map<String, Integer> getMissingFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects);
//	default Collection<String> getMissingFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects) {
//		return Collections.emptyList();
//	}

}