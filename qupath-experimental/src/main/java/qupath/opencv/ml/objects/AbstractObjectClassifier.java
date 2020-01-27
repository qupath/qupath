package qupath.opencv.ml.objects;

import java.awt.image.BufferedImage;
import java.util.stream.Collectors;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObjectFilter;

public abstract class AbstractObjectClassifier implements ObjectClassifier<BufferedImage> {
	
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
	public int classifyObjects(ImageData<BufferedImage> imageData) {
		var pathObjects = imageData.getHierarchy().getFlattenedObjectList(null);
		if (filter != null)
			pathObjects = pathObjects.stream().filter(filter).collect(Collectors.toList());
		return classifyObjects(imageData, pathObjects);
	}

}
