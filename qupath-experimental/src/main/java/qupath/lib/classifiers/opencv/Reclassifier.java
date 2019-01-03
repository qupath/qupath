package qupath.lib.classifiers.opencv;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Helper class when classifying PathObjects.
 * <p>
 * When applying a classifier to make objects, it's desirable to make updating the classification an atomic 
 * operation, applied to all objects in one go.  Consequently, it the classifier is aborted early 
 * (e.g. the thread interrupted), then the objects are not partially reclassified.
 */
class Reclassifier {
	
	private PathObject pathObject;
	private PathClass pathClass;
	private double probability = Double.NaN;
	
	public Reclassifier(final PathObject pathObject, final PathClass pathClass) {
		this(pathObject, pathClass, Double.NaN);
	}
	
	public Reclassifier(final PathObject pathObject, final PathClass pathClass, final double probability) {
		this.pathObject = pathObject;
		this.pathClass = pathClass;
		this.probability = probability;
	}
	
	/**
	 * Apply the stored classification.
	 * @return true if the classification for the object changed, false otherwise.
	 */
	public boolean apply() {
		var previousClass = pathObject.getPathClass();
		pathObject.setPathClass(pathClass, probability);
		return previousClass != pathClass;
	}
	
}