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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * A {@link PathObjectClassifier} that is created by applying multiple sub-classifiers in sequence.
 * 
 * @author Pete Bankhead
 *
 */
// TODO: Guarding more against invalid classifiers
class CompositeClassifier implements PathObjectClassifier, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private long timestamp = System.currentTimeMillis();
	private List<PathObjectClassifier> classifiers = new ArrayList<>();

	CompositeClassifier(final Collection<PathObjectClassifier> classifiers) {
		this.classifiers.addAll(classifiers);
		this.timestamp = System.currentTimeMillis();
	}
	
	@Override
	public List<String> getRequiredMeasurements() {
		if (classifiers.isEmpty())
			return Collections.emptyList();
		if (classifiers.size() == 1)
			return classifiers.get(0).getRequiredMeasurements();
		Set<String> measurements = new LinkedHashSet<>();
		for (PathObjectClassifier c : classifiers)
			measurements.addAll(c.getRequiredMeasurements());
		return new ArrayList<>(measurements);
	}

	@Override
	public Collection<PathClass> getPathClasses() {
		if (classifiers.isEmpty())
			return Collections.emptyList();
		if (classifiers.size() == 1)
			return classifiers.get(0).getPathClasses();
		SortedSet<PathClass> pathClasses = new TreeSet<>();
		for (PathObjectClassifier c : classifiers)
			pathClasses.addAll(c.getPathClasses());
		return pathClasses;
	}

	@Override
	public boolean isValid() {
		if (classifiers.isEmpty())
			return false;
		for (PathObjectClassifier c : classifiers) {
			if (!c.isValid())
				return false;
		}
		return true;
	}

//	@Override
//	public void updateClassifier(ImageData<?> imageData, List<String> measurements, int maxTrainingInstances) {
//		for (PathObjectClassifier c : classifiers)
//			c.updateClassifier(imageData, measurements, maxTrainingInstances);
//	}

	/**
	 * Classify a list of PathObjects, setting their {@link PathClass}es accordingly.
	 * @return the number of objects classified successfully by the last classifier.
	 */
	@Override
	public int classifyPathObjects(Collection<PathObject> pathObjects) {
		int n = 0;
		for (PathObjectClassifier c : classifiers)
			n = c.classifyPathObjects(pathObjects);
		return n;
	}

	@Override
	public String getDescription() {
		if (classifiers.isEmpty())
			return "";
		else if (classifiers.size() == 1)
			return classifiers.get(0).getDescription();
		StringBuilder sb = new StringBuilder();
		for (PathObjectClassifier c : classifiers) {
			sb.append(c.getDescription());
			sb.append("\n-----------\n");
		}
		return sb.toString();
	}

	@Override
	public String getName() {
		if (classifiers.isEmpty())
			return "Composite classifier (empty)";
		if (classifiers.size() == 1)
			return classifiers.get(0).getName();
		StringBuilder sb = new StringBuilder();
		sb.append("Composite classifier: ");
		sb.append(classifiers.get(0).getName());
		for (int i = 1; i < classifiers.size(); i++) {
			sb.append(", ");
			sb.append(classifiers.get(i).getName());
		}
		return sb.toString();
	}

	@Override
	public long getLastModifiedTimestamp() {
		return timestamp;
	}

	@Override
	public boolean supportsAutoUpdate() {
		return false;
	}

	@Override
	public boolean updateClassifier(Map<PathClass, List<PathObject>> map, List<String> measurements, Normalization normalization) {
		boolean changed = false;
		for (PathObjectClassifier c : classifiers)
			changed = changed || c.updateClassifier(map, measurements, null);
		return changed;
	}
}