package qupath.opencv.ml.pixel.features;

import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;

public interface PixelFeature {
	
	SimpleImage getFeature();
	
	String getName();
	
}


class DefaultPixelFeature<T> implements PixelFeature {
	
	private String name;
	private SimpleImage feature;
	
	DefaultPixelFeature(String name, SimpleImage feature) {
		this.name = name;
		this.feature = feature;
	}
	
	DefaultPixelFeature(String name, float[] values, int width, int height) {
		this(name, SimpleImages.createFloatImage(values, width, height));
	}

	@Override
	public SimpleImage getFeature() {
		return feature;
	}

	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return "Feature: " + getName();
	}
	
}
