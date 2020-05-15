package qupath.lib.classifiers.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.function.IntFunction;

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
	public int classifyObjects(ImageData<T> imageData, boolean resetExistingClass) {
		return classifyObjects(imageData, getCompatibleObjects(imageData), resetExistingClass);
	}
	
	@Override
	public int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, boolean resetExistingClass) {
		var beforeMap = createMap(pathObjects);
//		pathObjects.stream().forEach(p -> p.setPathClass(null)); // Reset classifications
		if (resetExistingClass)
			pathObjects.stream().forEach(p -> p.setPathClass(null));
		for (var c : classifiers) {
			c.classifyObjects(imageData, pathObjects, false);
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

	@Override
	public Collection<PathObject> getCompatibleObjects(ImageData<T> imageData) {
		Set<PathObject> set = new LinkedHashSet<>();
		for (var classifier : classifiers)
			set.addAll(classifier.getCompatibleObjects(imageData));
		return set;
	}

	@Override
	public Map<String, Integer> getMissingFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects) {
		if (pathObjects == null)
			pathObjects = getCompatibleObjects(imageData);
		Map<String, Integer> missingFeatures = new LinkedHashMap<>();
		for (var classifier: classifiers) {
			var newMissing = classifier.getMissingFeatures(imageData, pathObjects);
			if (newMissing.isEmpty())
				continue;
			if (missingFeatures.isEmpty())
				missingFeatures.putAll(newMissing);
			else {
				// Note that conceivably this might not be correct if different classifiers using the same feature names 
				// to refer to different features... but that seems rather unlikely in practice
				for (var entry : newMissing.entrySet())
					missingFeatures.merge(entry.getKey(), entry.getValue(), (i, i2) -> Math.max(i, i2));
			}
		}
		return missingFeatures;
	}
	

}
