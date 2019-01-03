package qupath.lib.classifiers.opencv;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import qupath.lib.objects.PathObject;

class FeatureExtractor {
	
	private List<String> measurements = new ArrayList<>();
	private Normalizer normalizer;
	private double missingValue;
	
	FeatureExtractor(final List<String> measurements, final Normalizer normalizer, final double missingValue) {
		this.measurements.addAll(measurements);
		this.normalizer = normalizer;
		this.missingValue = missingValue;
	}
	
	public void extractFeatures(final Collection<PathObject> pathObjects, FloatBuffer buffer) {
		for (var pathObject : pathObjects)
			extractFeatures(pathObject, buffer);
	}
	
	public List<String> getFeatureNames() {
		return Collections.unmodifiableList(measurements);
	}
	
	public int nFeatures() {
		return measurements.size();
	}
	
	public void extractFeatures(final PathObject pathObject, FloatBuffer buffer) {
		var measurementList = pathObject.getMeasurementList();
		int idx = 0;
		for (var m : measurements) {
			double value = measurementList.getMeasurementValue(m);
			if (normalizer != null)
				value = normalizer.normalizeFeature(idx, value);
			if (!Double.isFinite(value))
				buffer.put((float)missingValue);
			else
				buffer.put((float)value);
			idx++;
		}
	}
	
}