package qupath.lib.classifiers.opencv;


public interface PredictionModel<S, T> {
	
	public T predict(S input);
	
}