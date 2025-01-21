package qupath.lib.gui.measure;

import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.lazy.objects.PathObjectLazyValues;
import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

class ObjectCountValueFactory implements PathObjectValueFactory {

    // TODO: Switch to fixed double value
    private final Supplier<Double> allredMinPercentage;

    ObjectCountValueFactory(Supplier<Double> allredMinPercentagePositive) {
        this.allredMinPercentage = allredMinPercentagePositive;
    }

    ObjectCountValueFactory() {
        this(PathPrefs.allredMinPercentagePositiveProperty()::get);
    }

    @Override
    public List<LazyValue<PathObject, ?>> createValues(PathObjectListWrapper wrapper) {
        List<LazyValue<PathObject, ?>> measurements = new ArrayList<>();

        // Add derived measurements if we don't have only detections
        if (wrapper.containsRoot() || wrapper.containsAnnotationsTmaCores()) {
            var imageData = wrapper.getImageData();
            boolean detectionsAnywhere = imageData == null ? wrapper.containsDetections() : !imageData.getHierarchy().getDetectionObjects().isEmpty();
            if (detectionsAnywhere) {
                var builder = PathObjectLazyValues.createDetectionCountMeasurement(wrapper.getImageData());
                measurements.add(builder);
            }

            measurements.addAll(getDetectionCountsMeasurements(wrapper));
        }

        return measurements;
    }


    private List<LazyValue<PathObject, ?>> getDetectionCountsMeasurements(PathObjectListWrapper wrapper) {
        var imageData = wrapper.getImageData();
        List<LazyValue<PathObject, ?>> builders = new ArrayList<>();
        if (imageData == null || imageData.getHierarchy() == null)
            return builders;

        Set<PathClass> pathClasses = PathObjectTools.getRepresentedPathClasses(imageData.getHierarchy(), PathDetectionObject.class);

        pathClasses.remove(null);
        pathClasses.remove(PathClass.NULL_CLASS);

        Set<PathClass> parentIntensityClasses = new LinkedHashSet<>();
        Set<PathClass> parentPositiveNegativeClasses = new LinkedHashSet<>();
        for (PathClass pathClass : pathClasses) {
            if (PathClassTools.isGradedIntensityClass(pathClass)) {
                parentIntensityClasses.add(pathClass.getParentClass());
                parentPositiveNegativeClasses.add(pathClass.getParentClass());
            } else if (PathClassTools.isPositiveClass(pathClass) || PathClassTools.isNegativeClass(pathClass))
                parentPositiveNegativeClasses.add(pathClass.getParentClass());
        }

        // Store intensity parent classes, if required
        if (!parentPositiveNegativeClasses.isEmpty()) {
            List<PathClass> pathClassList = new ArrayList<>(parentPositiveNegativeClasses);
            pathClassList.remove(null);
            pathClassList.remove(PathClass.NULL_CLASS);
            Collections.sort(pathClassList);
            for (PathClass pathClass : pathClassList) {
                builders.add(PathObjectLazyValues.createBaseClassCountsMeasurement(imageData, pathClass));
            }
        }

        // We can compute counts for any PathClass that is represented
        List<PathClass> pathClassList = new ArrayList<>(pathClasses);
        Collections.sort(pathClassList);
        for (PathClass pathClass : pathClassList) {
            builders.add(PathObjectLazyValues.createExactClassCountsMeasurement(imageData, pathClass));
        }

        // We can compute positive percentages if we have anything in ParentPositiveNegativeClasses
        for (PathClass pathClass : parentPositiveNegativeClasses) {
            builders.add(PathObjectLazyValues.createPositivePercentageMeasurement(imageData, pathClass));
        }
        if (parentPositiveNegativeClasses.size() > 1)
            builders.add(PathObjectLazyValues.createPositivePercentageMeasurement(imageData, parentPositiveNegativeClasses.toArray(new PathClass[0])));

        // We can compute H-scores and Allred scores if we have anything in ParentIntensityClasses
        for (PathClass pathClass : parentIntensityClasses) {
            builders.add(PathObjectLazyValues.createHScoreMeasurement(imageData, pathClass));
            builders.add(PathObjectLazyValues.createAllredProportionMeasurement(imageData, allredMinPercentage, pathClass));
            builders.add(PathObjectLazyValues.createAllredIntensityMeasurement(imageData, allredMinPercentage, pathClass));
            builders.add(PathObjectLazyValues.createAllredMeasurement(imageData, allredMinPercentage, pathClass));
        }
        if (parentIntensityClasses.size() > 1) {
            PathClass[] parentIntensityClassesArray = parentIntensityClasses.toArray(PathClass[]::new);
            builders.add(PathObjectLazyValues.createHScoreMeasurement(imageData, parentIntensityClassesArray));
            builders.add(PathObjectLazyValues.createAllredProportionMeasurement(imageData,allredMinPercentage,  parentIntensityClassesArray));
            builders.add(PathObjectLazyValues.createAllredIntensityMeasurement(imageData, allredMinPercentage, parentIntensityClassesArray));
            builders.add(PathObjectLazyValues.createAllredMeasurement(imageData, allredMinPercentage, parentIntensityClassesArray));
        }

        // Add density measurements
        // These are only added if we have a (non-derived) positive class
        // Additionally, these are only non-NaN if we have an annotation, or a TMA core containing a single annotation
        boolean includeDensityMeasurements = wrapper.containsAnnotations() || wrapper.containsTMACores();
        if (includeDensityMeasurements) {
            for (PathClass pathClass : pathClassList) {
                if (PathClassTools.isPositiveClass(pathClass) && pathClass.getBaseClass() == pathClass) {
                    builders.add(PathObjectLazyValues.createDetectionClassDensityMeasurement(imageData, pathClass));
                }
            }
        }
        return builders;
    }

}
