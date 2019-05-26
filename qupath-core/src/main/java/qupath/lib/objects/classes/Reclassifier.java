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
	
	/**
	 * Helper class to store an object prior to reclassifying it.
	 * 
	 * @param pathObject
	 * @param pathClass
	 * @param retainIntensityClass of we have a single-level or two-level PathClass, with the second element an intensity classification, 
	 * 								optionally retain this and only update the base class.
	 */
	public Reclassifier(final PathObject pathObject, final PathClass pathClass, boolean retainIntensityClass) {
		this(pathObject, pathClass, retainIntensityClass, Double.NaN);
	}
	
	/**
	 * Helper class to store an object prior to reclassifying it, including a classification probability.
	 * 
	 * @param pathObject an object whose classification may be set by a subsequent call to {@link #apply()}
	 * @param pathClass the classification that may be applied to pathObject
	 * @param retainIntensityClass of we have a single-level or two-level PathClass, with the second element an intensity classification, 
	 * 								optionally retain this and only update the base class.
	 * @param probability optional classification probability value to store in the object (may be Double.NaN if this should be ignored).
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
				(PathClassTools.isPositiveOrGradedIntensityClass(previousClass) || PathClassTools.isNegativeClass(previousClass)) && 
				(!previousClass.isDerivedClass() || previousClass.getBaseClass() == previousClass.getParentClass())) {
			pathClass = PathClassFactory.getDerivedPathClass(pathClass, previousClass.getName(), null);
		}
		pathObject.setPathClass(pathClass, probability);
		return previousClass != pathClass;
	}
	
	/**
	 * Get the stored PathObject for which the PathClass may be set.
	 * @return
	 */
	public PathObject getPathObject() {
		return pathObject;
	}
	
}