package qupath.opencv.ml.objects;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import qupath.lib.objects.PathObject;

public class FeatureExtractor {
	
	private List<String> measurements = new ArrayList<>();
	
	protected FeatureExtractor(final List<String> measurements) {
		this.measurements.addAll(measurements);
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
		for (var m : measurements) {
			double value = measurementList.getMeasurementValue(m);
			buffer.put((float)value);
		}
	}
	
}