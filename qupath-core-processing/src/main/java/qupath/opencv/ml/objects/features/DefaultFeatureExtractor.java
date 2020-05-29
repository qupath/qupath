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

package qupath.opencv.ml.objects.features;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import qupath.lib.images.ImageData;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;

/**
 * {@link FeatureExtractor} that takes features from the existing {@link MeasurementList} of each object.
 * 
 * @author Pete Bankhead
 *
 */
class DefaultFeatureExtractor<T> implements FeatureExtractor<T> {
	
	private List<String> measurements = new ArrayList<>();
	
	DefaultFeatureExtractor(final Collection<String> measurements) {
		this.measurements.addAll(measurements);
	}
	
	@Override
	public void extractFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, FloatBuffer buffer) {
		for (var pathObject : pathObjects)
			extractFeatures(pathObject, buffer);
	}
	
	@Override
	public List<String> getFeatureNames() {
		return Collections.unmodifiableList(measurements);
	}
	
	@Override
	public int nFeatures() {
		return measurements.size();
	}
	
	private void extractFeatures(final PathObject pathObject, FloatBuffer buffer) {
		var measurementList = pathObject.getMeasurementList();
		for (var m : measurements) {
			double value = measurementList.getMeasurementValue(m);
			buffer.put((float)value);
		}
	}
	
	@Override
	public Collection<String> getMissingFeatures(ImageData<T> imageData, PathObject pathObject) {
		List<String> missing = null;
		var ml = pathObject.getMeasurementList();
		for (var name : measurements) {
			if (!ml.containsNamedMeasurement(name)) {
				if (missing == null)
					missing = new ArrayList<>();
				missing.add(name);
			}
		}
		return missing == null ? Collections.emptyList() : missing;
	}
	
}