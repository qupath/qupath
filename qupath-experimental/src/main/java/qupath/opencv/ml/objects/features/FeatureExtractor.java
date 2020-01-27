package qupath.opencv.ml.objects.features;

import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.List;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

public interface FeatureExtractor<T> {
	
	public List<String> getFeatureNames();
	
	public int nFeatures();

	public void extractFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, FloatBuffer buffer);
	
}