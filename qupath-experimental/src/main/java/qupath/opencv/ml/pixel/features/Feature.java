package qupath.opencv.ml.pixel.features;

public interface Feature<T> {
	
	T getFeature();
	
	String getName();
	
}


class DefaultFeature<T> implements Feature<T> {
	
	private String name;
	private T feature;
	
	DefaultFeature(String name, T feature) {
		this.name = name;
		this.feature = feature;
	}

	@Override
	public T getFeature() {
		return feature;
	}

	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return "Default feature: " + getName();
	}
	
}
