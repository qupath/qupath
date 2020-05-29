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
import java.util.Collection;
import java.util.List;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

class NormalizedFeatureExtractor<T> implements FeatureExtractor<T> {
	
	private FeatureExtractor<T> featureExtractor;
	private Normalizer normalizer;
	
	NormalizedFeatureExtractor(FeatureExtractor<T> featureExtractor, Normalizer normalizer) {
		this.featureExtractor = featureExtractor;
		this.normalizer = normalizer;
	}

	@Override
	public List<String> getFeatureNames() {
		return featureExtractor.getFeatureNames();
	}

	@Override
	public int nFeatures() {
		return featureExtractor.nFeatures();
	}

	@Override
	public void extractFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, FloatBuffer buffer) {
		int pos = buffer.position();
		featureExtractor.extractFeatures(imageData, pathObjects, buffer);
		int n = nFeatures();
		assert (buffer.position() - pos) == pathObjects.size() * n;
		int ind = pos;
		for (int i = 0; i < pathObjects.size(); i++) {
			for (int j = 0; j < n; j++) {
				double val = buffer.get(ind);
				val = normalizer.normalizeFeature(j, val);				
				buffer.put(ind, (float)val);
				ind++;
			}
		}
	}
	
	@Override
	public Collection<String> getMissingFeatures(ImageData<T> imageData, PathObject pathObject) {
		return featureExtractor.getMissingFeatures(imageData, pathObject);
	}

}