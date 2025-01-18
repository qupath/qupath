package qupath.lib.gui.measure.measurements;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Helper class to handle dynamic measurements that are based on counts of classified objects.
 * This includes calculations of positive percentage and H-score.
 */
class DerivedMeasurementManager {

    private static final Logger logger = LoggerFactory.getLogger(DerivedMeasurementManager.class);


    static List<MeasurementBuilder<?>> createMeasurements(ImageData<?> imageData, boolean includeDensityMeasurements) {
        List<MeasurementBuilder<?>> builders = new ArrayList<>();
        if (imageData == null || imageData.getHierarchy() == null)
            return builders;

        Set<PathClass> pathClasses = PathObjectTools.getRepresentedPathClasses(imageData.getHierarchy(), PathDetectionObject.class);

        Function<PathObject, DetectionPathClassCounts> countsFunction = new DetectionClassificationCounter(imageData.getHierarchy());

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
                builders.add(new ClassCountMeasurementBuilder(countsFunction, pathClass, true));
            }
        }

        // We can compute counts for any PathClass that is represented
        List<PathClass> pathClassList = new ArrayList<>(pathClasses);
        Collections.sort(pathClassList);
        for (PathClass pathClass : pathClassList) {
            builders.add(new ClassCountMeasurementBuilder(countsFunction, pathClass, false));
        }

        // We can compute positive percentages if we have anything in ParentPositiveNegativeClasses
        for (PathClass pathClass : parentPositiveNegativeClasses) {
            builders.add(new PositivePercentageMeasurementBuilder(countsFunction, pathClass));
        }
        if (parentPositiveNegativeClasses.size() > 1)
            builders.add(new PositivePercentageMeasurementBuilder(countsFunction, parentPositiveNegativeClasses.toArray(new PathClass[0])));

        // We can compute H-scores and Allred scores if we have anything in ParentIntensityClasses
        Supplier<Double> allredMinPercentage = PathPrefs.allredMinPercentagePositiveProperty()::get;
        for (PathClass pathClass : parentIntensityClasses) {
            builders.add(new HScoreMeasurementBuilder(countsFunction, pathClass));
            builders.add(new AllredProportionMeasurementBuilder(countsFunction, allredMinPercentage, pathClass));
            builders.add(new AllredIntensityMeasurementBuilder(countsFunction, allredMinPercentage, pathClass));
            builders.add(new AllredMeasurementBuilder(countsFunction, allredMinPercentage, pathClass));
        }
        if (parentIntensityClasses.size() > 1) {
            PathClass[] parentIntensityClassesArray = parentIntensityClasses.toArray(PathClass[]::new);
            builders.add(new HScoreMeasurementBuilder(countsFunction, parentIntensityClassesArray));
            builders.add(new AllredProportionMeasurementBuilder(countsFunction,allredMinPercentage,  parentIntensityClassesArray));
            builders.add(new AllredIntensityMeasurementBuilder(countsFunction, allredMinPercentage, parentIntensityClassesArray));
            builders.add(new AllredMeasurementBuilder(countsFunction, allredMinPercentage, parentIntensityClassesArray));
        }

        // Add density measurements
        // These are only added if we have a (non-derived) positive class
        // Additionally, these are only non-NaN if we have an annotation, or a TMA core containing a single annotation
        if (includeDensityMeasurements) {
            for (PathClass pathClass : pathClassList) {
                if (PathClassTools.isPositiveClass(pathClass) && pathClass.getBaseClass() == pathClass) {
                    builders.add(new ClassDensityMeasurementBuilder(countsFunction, imageData, pathClass));
                }
            }
        }
        return builders;
    }



    private static class DetectionClassificationCounter implements Function<PathObject, DetectionPathClassCounts> {

        private final PathObjectHierarchy hierarchy;

        // Map to store cached counts; this should be reset when the hierarchy changes - since v0.6.0 this happens
        // automatically using the hierarchy's last event timestamp
        private final Map<PathObject, DetectionPathClassCounts> map = Collections.synchronizedMap(new WeakHashMap<>());
        private long lastHierarchyEventCount;

        private DetectionClassificationCounter(PathObjectHierarchy hierarchy) {
            Objects.requireNonNull(hierarchy, "Hierarchy must not be null!");
            this.hierarchy = hierarchy;
        }

        @Override
        public DetectionPathClassCounts apply(PathObject pathObject) {
            var actualTimestamp = hierarchy.getEventCount();
            if (actualTimestamp > lastHierarchyEventCount) {
                logger.debug("Clearing cached measurements");
                map.clear();
                lastHierarchyEventCount = actualTimestamp;
            }
            return map.computeIfAbsent(pathObject, p -> new DetectionPathClassCounts(hierarchy, p));
        }

    }

}
