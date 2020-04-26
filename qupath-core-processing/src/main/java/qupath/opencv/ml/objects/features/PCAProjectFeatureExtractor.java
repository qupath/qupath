package qupath.opencv.ml.objects.features;

import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.opencv.ml.objects.features.Preprocessing.PCAProjector;

class PCAProjectFeatureExtractor<T> implements FeatureExtractor<T> {
	
	private FeatureExtractor<T> featureExtractor;
	private PCAProjector pca;
	
	PCAProjectFeatureExtractor(FeatureExtractor<T> featureExtractor, PCAProjector pca) {
		this.featureExtractor = featureExtractor;
		this.pca = pca;
	}

	@Override
	public List<String> getFeatureNames() {
		return IntStream.rangeClosed(1, pca.nComponents()+1)
				.mapToObj(i -> "PCA " + i)
				.collect(Collectors.toList());
	}

	@Override
	public int nFeatures() {
		return pca.nComponents();
	}

	@Override
	public void extractFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, FloatBuffer buffer) {
		Mat mat = new Mat(pathObjects.size(), featureExtractor.nFeatures(), opencv_core.CV_32FC1);
		FloatBuffer temp = mat.createBuffer();
		featureExtractor.extractFeatures(imageData, pathObjects, temp);
		pca.project(mat, mat);
		buffer.put(mat.createBuffer());
		mat.close();
	}

}
