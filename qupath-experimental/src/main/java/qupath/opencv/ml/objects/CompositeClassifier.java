package qupath.opencv.ml.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Apply a collection of classifiers sequentially.
 * This may be used as an alternative approach of creating a multi-class classifier.
 * 
 * @author Pete Bankhead
 */
class CompositeClassifier<T> implements ObjectClassifier<T> {

	private List<ObjectClassifier<T>> classifiers;
	
	private transient Collection<PathClass> pathClasses;
	
	CompositeClassifier(Collection<ObjectClassifier<T>> classifiers) {
		this.classifiers = new ArrayList<>(classifiers);
	}

	@Override
	public synchronized Collection<PathClass> getPathClasses() {
		if (pathClasses == null) {
			pathClasses = new LinkedHashSet<>();
			for (var c : classifiers)
				pathClasses.addAll(c.getPathClasses());
			pathClasses = Collections.unmodifiableCollection(pathClasses);
		}
		return pathClasses;
	}

	@Override
	public int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects) {
		var beforeMap = createMap(pathObjects);
//		pathObjects.stream().forEach(p -> p.setPathClass(null)); // Reset classifications
		for (var c : classifiers) {
			c.classifyObjects(imageData, pathObjects);
			if (Thread.currentThread().isInterrupted()) {
				resetClassifications(pathObjects, beforeMap);
				return 0;
			}
		}
		var afterMap = createMap(pathObjects);
		int n = 0;
		for (var pathObject : pathObjects) {
			if (!Objects.equals(beforeMap.get(pathObject), afterMap.get(pathObject)))
				n++;
		}
		return n;
	}

	@Override
	public int classifyObjects(ImageData<T> imageData) {
		var allObjects = imageData.getHierarchy().getFlattenedObjectList(null);
		var beforeMap = createMap(allObjects);
//		allObjects.stream().forEach(p -> p.setPathClass(null)); // Reset classifications
		for (var c : classifiers) {
			c.classifyObjects(imageData);
			if (Thread.currentThread().isInterrupted()) {
				resetClassifications(allObjects, beforeMap);
				return 0;
			}
		}
		var afterMap = createMap(allObjects);
		int n = 0;
		for (var pathObject : allObjects) {
			if (!Objects.equals(beforeMap.get(pathObject), afterMap.get(pathObject)))
				n++;
		}
		return n;
	}
	
	Map<PathObject, PathClass> createMap(Collection<? extends PathObject> pathObjects) {
		var map = new HashMap<PathObject, PathClass>();
		for (var pathObject : pathObjects)
			map.put(pathObject, pathObject.getPathClass());
		return map;
//		return pathObjects.stream().collect(Collectors.toMap(p -> p, p -> p.getPathClass()));
	}
	
	void resetClassifications(Collection<? extends PathObject> pathObjects, Map<PathObject, PathClass> map) {
		pathObjects.stream().forEach(p -> p.setPathClass(map.get(p)));
	}
	

}
