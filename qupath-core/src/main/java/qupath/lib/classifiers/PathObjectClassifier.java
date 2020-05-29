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

package qupath.lib.classifiers;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Interface to define a basic object classifier.
 * 
 * @author Pete Bankhead
 *
 */
public interface PathObjectClassifier {

	/**
	 * Get a list of the measurements required by this classifier in order to classify a PathObject.
	 * 
	 * @return
	 */
	public abstract List<String> getRequiredMeasurements();

	/**
	 * Get a collection of the PathClasses that this classifier can produce.
	 * 
	 * @return
	 */
	public abstract Collection<PathClass> getPathClasses();

	/**
	 * Returns true if the classifier has all the information it needs to perform a classification
	 * @return
	 */
	public abstract boolean isValid();

	
	/**
	 * Train the classifier.
	 * @param map
	 * @param measurements
	 * @param normalization
	 * @return True if the classifier has changed as a result of this call, false if it is the same (or failed to create).
	 */
	public boolean updateClassifier(final Map<PathClass, List<PathObject>> map, final List<String> measurements, Normalization normalization);
	
//	/**
//	 * Train the classifier.
//	 * 
//	 * @param imageData
//	 * @param measurements
//	 * @param maxTrainingInstances
//	 */
//	public void updateClassifier(final ImageData<?> imageData, final List<String> measurements, final int maxTrainingInstances);
	
	
	/**
	 * Classify a list of PathObjects, setting their PathClasses accordingly.
	 * @param pathObjects
	 * @return the number of objects classified successfully
	 */
	public abstract int classifyPathObjects(Collection<PathObject> pathObjects);

	/**
	 * A detailed textual description of the classifier (may span multiple lines).
	 * The format of this is unspecified - anything meaningful can be included.
	 * @return
	 */
	public abstract String getDescription();
	
	/**
	 * Identifying name for the classifier.
	 * @return
	 */
	public abstract String getName();
	
	/**
	 * Time (from {@code System.currentTimeMillis()}) at which the classified was built or last modified.
	 * @return 
	 */
	public long getLastModifiedTimestamp();
	
	/**
	 * Provides a hint that the classifier is sufficiently fast and responsive to support being interactively updated.
	 * Slow classifiers should return false, while classifiers using very fast methods may return true.
	 * @return
	 */
	public boolean supportsAutoUpdate();
	

}