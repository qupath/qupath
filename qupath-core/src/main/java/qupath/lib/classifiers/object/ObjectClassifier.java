package qupath.lib.classifiers.object;

import java.util.Collection;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Interface defining object classifiers, which assign {@linkplain PathClass PathClasses} to {@linkplain PathObject PathObjects}.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface ObjectClassifier<T> {

	/**
	 * Get the classifications set by this classifier.
	 * @return
	 */
	Collection<PathClass> getPathClasses();

	/**
	 * Classify all compatible objects from an {@link ImageData}.
	 * <p>
	 * Note: this method does <i>not</i> typically fire any hierarchy change/object classification events.
	 * It is up to the caller to fire these events if required.
	 * 
	 * @param imageData data containing the object hierarchy
	 * @param resetExistingClass 
	 * @return the number of objects whose classification was changed.
	 * 
	 * @see #getCompatibleObjects(ImageData)
	 */
	int classifyObjects(ImageData<T> imageData, boolean resetExistingClass);

	/**
	 * Classify the objects in the specified collection.
	 * This provides a means to specify exactly which objects should be classified, avoiding reliance on {@link #getCompatibleObjects(ImageData)}.
	 * <p>
	 * Note: this method does <i>not</i> typically fire any hierarchy change/object classification events.
	 * It is up to the caller to fire these events if required.
	 * 
	 * @param imageData data that may or may not be required for classification depending upon how features are extracted
	 * @param pathObjects the objects to classify
	 * @param resetExistingClass 
	 * @return the number of objects whose classification was changed.
	 * 
	 * @see #classifyObjects(ImageData, boolean)
	 * @see #getCompatibleObjects(ImageData)
	 */
	int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, boolean resetExistingClass);

	/**
	 * Get the objects from an {@link ImageData} that are compatible with this classifier.
	 * @param imageData 
	 * @return a collection of compatible objects, or empty list if no compatible objects are found
	 */
	Collection<PathObject> getCompatibleObjects(ImageData<T> imageData);

}