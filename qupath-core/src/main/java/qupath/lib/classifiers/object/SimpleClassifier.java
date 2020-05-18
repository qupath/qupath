package qupath.lib.classifiers.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

import qupath.lib.classifiers.object.ObjectClassifiers.ClassifyByMeasurementFunction;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;

class SimpleClassifier<T> extends AbstractObjectClassifier<T> {
	
	private Function<PathObject, PathClass> function;
	private Collection<PathClass> pathClasses;
	
	SimpleClassifier(PathObjectFilter filter, Function<PathObject, PathClass> function, Collection<PathClass> pathClasses) {
		super(filter);
		this.function = function;
		this.pathClasses = Collections.unmodifiableList(new ArrayList<>(pathClasses));
	}

	@Override
	public Collection<PathClass> getPathClasses() {
		return pathClasses;
	}

	@Override
	public int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, boolean resetExistingClass) {
		int n = 0;
		for (var pathObject : pathObjects) {
			var previousClass = pathObject.getPathClass();
			if (resetExistingClass)
				pathObject.setPathClass(null);
			
			var pathClass = function.apply(pathObject);
			if (pathClass != null) {
				var currentClass = pathObject.getPathClass();
				if (currentClass == null)
					pathObject.setPathClass(pathClass);
				else
					pathObject.setPathClass(
							PathClassTools.mergeClasses(currentClass, pathClass)
							);
			}
			if (previousClass != pathObject.getPathClass())
				n++;
		}
		return n;
	}
	
	@Override
	public Map<String, Integer> getMissingFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects) {
		if (function instanceof ClassifyByMeasurementFunction) {
			int nMissing = 0;
			var name = ((ClassifyByMeasurementFunction)function).getMeasurement();
			if (name != null) {
				for (var pathObject : pathObjects) {
					if (!pathObject.getMeasurementList().containsNamedMeasurement(name))
						nMissing++;
				}
			}
			if (nMissing > 0)
				return Map.of(name, nMissing);
		}
		return Collections.emptyMap();
	}

}
