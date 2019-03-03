package qupath.lib.objects.classes;

import qupath.lib.objects.PathObject;

/**
 * Helper class when classifying PathObjects.
 * <p>
 * When applying a classifier to make objects, it's desirable to make updating the classification an atomic 
 * operation, applied to all objects in one go.  Consequently, it the classifier is aborted early 
 * (e.g. the thread interrupted), then the objects are not partially reclassified.
 */
public class Reclassifier {
	
	private PathObject pathObject;
	private PathClass pathClass;
	private double probability = Double.NaN;
	private boolean retainIntensityClass;
	
	public Reclassifier(final PathObject pathObject, final PathClass pathClass, boolean retainIntensityClass) {
		this(pathObject, pathClass, retainIntensityClass, Double.NaN);
	}
	
	/**
	 * Helper class to store an object prior to reclassifying it.
	 * 
	 * @param pathObject
	 * @param pathClass
	 * @param retainIntensityClass If we have a single-level or two-level PathClass, with the second element an intensity classification, 
	 * 								optionally retain this and only update the base class.
	 * @param probability
	 */
	public Reclassifier(final PathObject pathObject, final PathClass pathClass, boolean retainIntensityClass, final double probability) {
		this.pathObject = pathObject;
		this.pathClass = pathClass;
		this.retainIntensityClass = retainIntensityClass;
		this.probability = probability;
	}
	
	/**
	 * Apply the stored classification.
	 * @return true if the classification for the object changed, false otherwise.
	 */
	public boolean apply() {
		var previousClass = pathObject.getPathClass();
		var pathClass = this.pathClass;
		if (retainIntensityClass && previousClass != null &&
				(PathClassFactory.isPositiveOrPositiveIntensityClass(previousClass) || PathClassFactory.isNegativeClass(previousClass)) && 
				(!previousClass.isDerivedClass() || previousClass.getBaseClass() == previousClass.getParentClass())) {
			pathClass = PathClassFactory.getDerivedPathClass(pathClass, previousClass.getName(), null);
		}
		pathObject.setPathClass(pathClass, probability);
		return previousClass != pathClass;
	}
	
	public PathObject getPathObject() {
		return pathObject;
	}
	
}