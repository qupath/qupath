package qupath.opencv.ml.objects.features;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.List;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.opencv.ml.Normalizer;

class NormalizedFeatureExtractor implements FeatureExtractor {
	
	private FeatureExtractor featureExtractor;
	private Normalizer normalizer;
	
	NormalizedFeatureExtractor(FeatureExtractor featureExtractor, Normalizer normalizer) {
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
	public void extractFeatures(ImageData<BufferedImage> imageData, Collection<PathObject> pathObjects, FloatBuffer buffer) {
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

}
