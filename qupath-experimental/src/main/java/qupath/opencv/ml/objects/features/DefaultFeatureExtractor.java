package qupath.opencv.ml.objects.features;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;

/**
 * {@link FeatureExtractor} that takes features from the existing {@link MeasurementList} of each object.
 * 
 * @author Pete Bankhead
 *
 */
class DefaultFeatureExtractor implements FeatureExtractor {
	
	private List<String> measurements = new ArrayList<>();
	
	DefaultFeatureExtractor(final List<String> measurements) {
		this.measurements.addAll(measurements);
	}
	
	@Override
	public void extractFeatures(final Collection<PathObject> pathObjects, FloatBuffer buffer) {
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
	
}