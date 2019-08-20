package qupath.lib.objects.classes;

/**
 * Static methods for use with {@link PathClass} objects.
 * 
 * @author Pete Bankhead
 */
public class PathClassTools {

	/**
	 * Returns true if the PathClass represents a built-in intensity class.
	 * Here, this means its name is equal to "1+", "2+" or "3+".
	 * 
	 * @param pathClass
	 * @return
	 */
	public static boolean isGradedIntensityClass(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.intensityClassNames.contains(pathClass.getName());
	}
	
	/**
	 * Returns true if the PathClass should be ignored from some operations.
	 * In practice, this checks if the name ends with an asterisk.
	 * It is useful to avoid generating objects for certain classes (e.g. Ignore*, Artefact*, Background*) 
	 * where these would not be meaningful.
	 * @param pathClass
	 * @return
	 */
	public static boolean isIgnoredClass(final PathClass pathClass) {
		return pathClass.getName().endsWith("*");
	}
	
	/**
	 * Returns true if the name of the class is "1+", indicating a weakly-positive staining.
	 * @param pathClass
	 * @return
	 */
	public static boolean isOnePlus(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.ONE_PLUS.equals(pathClass.getName());
	}

	/**
	 * Returns true if the name of the class is "2+", indicating a moderately-positive staining.
	 * @param pathClass
	 * @return
	 */
	public static boolean isTwoPlus(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.TWO_PLUS.equals(pathClass.getName());
	}

	/**
	 * Returns true if the name of the class is "3+", indicating a weakly-positive staining.
	 * @param pathClass
	 * @return
	 */
	public static boolean isThreePlus(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.THREE_PLUS.equals(pathClass.getName());
	}

	/**
	 * Returns {@code true} if the PathClass has the name "Positive".
	 * 
	 * @param pathClass
	 * @return
	 */
	public static boolean isPositiveClass(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.POSITIVE.equals(pathClass.getName());
	}
	
	/**
	 * Returns true if the name of the class is "Positive", "1+", "2+" or "3+", indicating positive staining.
	 * @param pathClass
	 * @return
	 */
	public static boolean isPositiveOrGradedIntensityClass(final PathClass pathClass) {
		return pathClass != null && (isPositiveClass(pathClass) || isOnePlus(pathClass) || isTwoPlus(pathClass) || isThreePlus(pathClass));
	}
	
	/**
	 * Returns true if the PathClass has the name "Negative".
	 * 
	 * @param pathClass
	 * @return
	 */
	public static boolean isNegativeClass(final PathClass pathClass) {
		return pathClass != null && PathClassFactory.NEGATIVE.equals(pathClass.getName());
	}

	/**
	 * Get the first ancestor class that is not an intensity class (i.e. not negative, positive, 1+, 2+ or 3+).
	 * <p>
	 * This will return null if pathClass is null, or if no non-intensity classes are found.
	 * 
	 * @param pathClass
	 * @return
	 */
	public static PathClass getNonIntensityAncestorClass(PathClass pathClass) {
		while (pathClass != null && (isPositiveOrGradedIntensityClass(pathClass) || isNegativeClass(pathClass)))
			pathClass = pathClass.getParentClass();
		return pathClass;
	}

}
