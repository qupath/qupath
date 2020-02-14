package qupath.lib.classifiers.object;

import java.util.Collection;
import java.util.stream.Collectors;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;

/**
 * Abstract class to help with the creation of object classifiers.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class AbstractObjectClassifier<T> implements ObjectClassifier<T> {
	
	/**
	 * Choose which objects are supported (often detections)
	 */
	private PathObjectFilter filter;
	
	/**
	 * Timestamp representing when the classifier was created/trained
	 */
	private long timestamp = System.currentTimeMillis();
	
	protected AbstractObjectClassifier(final PathObjectFilter filter) {
		this.filter = filter;
	}

	@Override
	public int classifyObjects(ImageData<T> imageData, boolean resetExistingClass) {
		return classifyObjects(imageData, getCompatibleObjects(imageData), resetExistingClass);
	}
	
	@Override
	public Collection<PathObject> getCompatibleObjects(ImageData<T> imageData) {
		var pathObjects = imageData.getHierarchy().getFlattenedObjectList(null);
		if (filter != null)
			pathObjects = pathObjects.stream().filter(filter).collect(Collectors.toList());
		return pathObjects;
	}

}
