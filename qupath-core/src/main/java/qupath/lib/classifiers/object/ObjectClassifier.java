package qupath.lib.classifiers.object;

import java.awt.image.BufferedImage;
import java.util.Collection;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

public interface ObjectClassifier<T> {

	Collection<PathClass> getPathClasses();

	int classifyObjects(ImageData<T> imageData);

	int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects);

}