package qupath.lib.gui.measure.measurements;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        for (PathClass pathClass : parentIntensityClasses) {
            builders.add(new HScoreMeasurementBuilder(countsFunction, pathClass));
            builders.add(new AllredProportionMeasurementBuilder(countsFunction, pathClass));
            builders.add(new AllredIntensityMeasurementBuilder(countsFunction, pathClass));
            builders.add(new AllredMeasurementBuilder(countsFunction, pathClass));
        }
        if (parentIntensityClasses.size() > 1) {
            PathClass[] parentIntensityClassesArray = parentIntensityClasses.toArray(PathClass[]::new);
            builders.add(new HScoreMeasurementBuilder(countsFunction, parentIntensityClassesArray));
            builders.add(new AllredProportionMeasurementBuilder(countsFunction, parentIntensityClassesArray));
            builders.add(new AllredIntensityMeasurementBuilder(countsFunction, parentIntensityClassesArray));
            builders.add(new AllredMeasurementBuilder(countsFunction, parentIntensityClassesArray));
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



    private static double computeDensityPerMM(final ImageData<?> imageData, final DetectionPathClassCounts counts,
                                              final PathObject pathObject, final PathClass pathClass) {
        // If we have a TMA core, look for a single annotation inside
        // If we don't have that, we can't return counts since it's ambiguous where the
        // area should be coming from
        PathObject pathObjectTemp = pathObject;
        if (pathObject instanceof TMACoreObject) {
            var children = pathObject.getChildObjectsAsArray();
            if (children.length != 1)
                return Double.NaN;
            pathObjectTemp = children[0];
        }
        // We need an annotation to get a meaningful area
        if (pathObjectTemp == null || !(pathObjectTemp.isAnnotation() || pathObjectTemp.isRootObject()))
            return Double.NaN;

        int n = counts.getCountForAncestor(pathClass);
        ROI roi = pathObjectTemp.getROI();
        // For the root, we can measure density only for 2D images of a single time-point
        var serverMetadata = imageData.getServerMetadata();
        if (pathObjectTemp.isRootObject() && serverMetadata.getSizeZ() == 1 && serverMetadata.getSizeT() == 1)
            roi = ROIs.createRectangleROI(0, 0, serverMetadata.getWidth(), serverMetadata.getHeight(), ImagePlane.getDefaultPlane());

        if (roi != null && roi.isArea()) {
            double pixelWidth = 1;
            double pixelHeight = 1;
            PixelCalibration cal = serverMetadata == null ? null : serverMetadata.getPixelCalibration();
            if (cal != null && cal.hasPixelSizeMicrons()) {
                pixelWidth = cal.getPixelWidthMicrons() / 1000;
                pixelHeight = cal.getPixelHeightMicrons() / 1000;
            }
            return n / roi.getScaledArea(pixelWidth, pixelHeight);
        }
        return Double.NaN;
    }



    private static double computeAllredIntensity(DetectionPathClassCounts counts, double minPositivePercentage, PathClass... pathClasses) {
        return counts.getAllredIntensity(minPositivePercentage / 100, pathClasses);
    }

    private static  double computeAllredProportion(DetectionPathClassCounts counts, double minPositivePercentage, PathClass... pathClasses) {
        return counts.getAllredProportion(minPositivePercentage / 100, pathClasses);
    }

    private static double computeAllredScore(DetectionPathClassCounts counts, double minPositivePercentage, PathClass... pathClasses) {
        return counts.getAllredScore(minPositivePercentage / 100, pathClasses);
    }


    /**
     * Get a string representation that can be used to refer to zero or more parent (base) classifications.
     * <ul>
     * <li>If no classifications are provided, or only the null classification, then an empty string is returned.</li>
     * <li>If one non-null classification is provided, its {@code toString()} representation is returned.</li>
     * <li>Otherwise, a string representing the multiple classifications is returned, e.g. {@code "(Tumor|Stroma|Other)"}.</li>*
     * </ul>
     *
     * @param pathClasses
     * @return
     */
    private static String getParentClassificationsString(PathClass... pathClasses) {
        if (pathClasses.length == 0)
            return "";
        if (pathClasses.length == 1 && (pathClasses[0] == null || pathClasses[0] == PathClass.NULL_CLASS))
            return "";
        if (pathClasses.length == 1) {
            return pathClasses[0].toString();
        } else {
            return "(" + Arrays.stream(pathClasses)
                    .map(p -> p == null ? "<Unclassified>" : p.toString())
                    .collect(Collectors.joining("|")) + ")";
        }
    }

    /**
     * Get a suitable name for a measurement that reflects the parent PathClasses used in its calculation, e.g.
     * to get the positive % measurement name for both tumor & stroma classes, the input would be
     * getNameForClasses("Positive %", tumorClass, stromaClass);
     * and the output would be "Stroma + Tumor: Positive %"
     *
     * @param measurementName
     * @param parentClasses
     * @return
     */
    private static String getNameForClasses(final String measurementName, final PathClass... parentClasses) {
        if (parentClasses == null || parentClasses.length == 0)
            return measurementName;
        if (parentClasses.length == 1) {
            PathClass parent = parentClasses[0];
            if (parent == null)
                return measurementName;
            else
                return parent.getBaseClass().toString() + ": " + measurementName;
        }
        String[] names = new String[parentClasses.length];
        for (int i = 0; i < names.length; i++) {
            PathClass parent = parentClasses[i];
            names[i] = parent == null ? "" : parent.getName();
        }
        Arrays.sort(names);
        return String.join(" + ", names) + ": " + measurementName;
    }


    private static class ClassCountMeasurementBuilder implements NumericMeasurementBuilder {

        private final Function<PathObject, DetectionPathClassCounts> countsFunction;
        private final PathClass pathClass;
        private final boolean baseClassification;

        /**
         * Count objects with specific classifications.
         *
         * @param countsFunction a function to create counts for the objects; this can return cached values for performance
         * @param pathClass the classification of detections to count
         * @param baseClassification if {@code true}, also count objects with classifications derived from the specified classification,
         *                           if {@code false} count objects with <i>only</i> the exact classification given.
         */
        ClassCountMeasurementBuilder(Function<PathObject, DetectionPathClassCounts> countsFunction,
                                     PathClass pathClass, boolean baseClassification) {
            this.pathClass = pathClass;
            this.baseClassification = baseClassification;
            this.countsFunction = countsFunction;
        }

        @Override
        public String getHelpText() {
            if (baseClassification) {
                if (pathClass == null || pathClass == PathClass.NULL_CLASS)
                    return "Number of detection objects with no base classification";
                else
                    return "Number of detection objects with the base classification '" + pathClass + "' (including all sub-classifications)";
            } else {
                if (pathClass == null || pathClass == PathClass.NULL_CLASS)
                    return "Number of detection objects with no classification";
                else
                    return "Number of detection objects with the exact classification '" + pathClass + "'";
            }
        }

        @Override
        public String getName() {
            if (baseClassification)
                return "Num " + pathClass.toString() + " (base)";
            else
                return "Num " + pathClass.toString();
        }

        @Override
        public Number getValue(final PathObject pathObject) {
            DetectionPathClassCounts counts = countsFunction.apply(pathObject);
            if (baseClassification)
                return counts.getCountForAncestor(pathClass);
            else
                return counts.getDirectCount(pathClass);
        }

        @Override
        public String toString() {
            return getName();
        }

    }


    private static class ClassDensityMeasurementBuilder implements NumericMeasurementBuilder {

        private final Function<PathObject, DetectionPathClassCounts> countsFunction;
        private final ImageData<?> imageData;
        private final PathClass pathClass;

        ClassDensityMeasurementBuilder(Function<PathObject, DetectionPathClassCounts> countsFunction,
                                       ImageData<?> imageData, PathClass pathClass) {
            this.countsFunction = countsFunction;
            this.imageData = imageData;
            this.pathClass = pathClass;
        }

        @Override
        public String getHelpText() {
            return "Density of detections with classification '" + pathClass + "' inside the selected objects";
        }

        @Override
        public String getName() {
            if (imageData != null && imageData.getServerMetadata().getPixelCalibration().hasPixelSizeMicrons())
                return String.format("Num %s per mm^2", pathClass.toString());
            else
                return String.format("Num %s per px^2", pathClass.toString());
        }

        @Override
        public Number getValue(final PathObject pathObject) {
            // Only return density measurements for annotations
            if (pathObject.isAnnotation() || (pathObject.isTMACore() && pathObject.nChildObjects() == 1)) {
                var counts = countsFunction.apply(pathObject);
                return computeDensityPerMM(imageData, counts, pathObject, pathClass);
            }
            return Double.NaN;
        }

        @Override
        public String toString() {
            return getName();
        }

    }


    private static class PositivePercentageMeasurementBuilder implements NumericMeasurementBuilder {

        private final Function<PathObject, DetectionPathClassCounts> countsFunction;
        private PathClass[] parentClasses;

        PositivePercentageMeasurementBuilder(Function<PathObject, DetectionPathClassCounts> countsFunction,
                                             PathClass... parentClasses) {
            this.countsFunction = countsFunction;
            this.parentClasses = parentClasses;
        }

        @Override
        public String getHelpText() {
            var pcString = getParentClassificationsString(parentClasses);
            if (pcString.isEmpty()) {
                return "Number of detection classified as 'Positive' / ('Positive' + 'Negative') * 100%";
            } else {
                return "Number of detection classified as '" + pcString + ": Positive' / ('"
                        + pcString + ": Positive' + '" + pcString + ": Negative') * 100%";
            }
        }

        @Override
        public String getName() {
            return getNameForClasses("Positive %", parentClasses);
        }

        @Override
        public Number getValue(final PathObject pathObject) {
            DetectionPathClassCounts counts = countsFunction.apply(pathObject);
            return counts.getPositivePercentage(parentClasses);
        }

    }


    private static class HScoreMeasurementBuilder implements NumericMeasurementBuilder {

        private final Function<PathObject, DetectionPathClassCounts> countsFunction;
        private final PathClass[] pathClasses;

        HScoreMeasurementBuilder(Function<PathObject, DetectionPathClassCounts> countsFunction, PathClass... pathClasses) {
            this.countsFunction = countsFunction;
            this.pathClasses = pathClasses;
        }

        @Override
        public String getHelpText() {
            var pcString = getParentClassificationsString(pathClasses);
            if (pcString.isEmpty()) {
                return "H-score calculated from Negative, 1+, 2+ and 3+ classified detections (range 0-300)";
            } else {
                return "H-score calculated from " + pcString + ": Negative, 1+, 2+ and 3+ classified detections (range 0-300)";
            }
        }

        @Override
        public String getName() {
            return getNameForClasses("H-score", pathClasses);
        }

        @Override
        public Number getValue(final PathObject pathObject) {
            var counts = countsFunction.apply(pathObject);
            return counts.getHScore(pathClasses);
        }

    }


    private static class AllredIntensityMeasurementBuilder implements NumericMeasurementBuilder {

        private final Function<PathObject, DetectionPathClassCounts> countsFunction;
        private final PathClass[] pathClasses;

        AllredIntensityMeasurementBuilder(Function<PathObject, DetectionPathClassCounts> countsFunction,
                                          PathClass... pathClasses) {
            this.countsFunction = countsFunction;
            this.pathClasses = pathClasses;
        }

        @Override
        public String getHelpText() {
            var pcString = getParentClassificationsString(pathClasses);
            double minPercentage = PathPrefs.allredMinPercentagePositiveProperty().get();
            String minRequires = minPercentage == 0 ? "" : "\nSet to 0 if less than " + minPercentage + "% cells positive";
            if (pcString.isEmpty()) {
                return "Allred intensity score calculated from Negative, 1+, 2+ and 3+ classified detections (range 0-3)" + minRequires;
            } else {
                return "Allred intensity score calculated from " + pcString + ": Negative, 1+, 2+ and 3+ classified detections (range 0-3)" + minRequires;
            }
        }

        @Override
        public String getName() {
            double minPercentage = PathPrefs.allredMinPercentagePositiveProperty().get();
            String name;
            if (minPercentage > 0)
                name = String.format("Allred intensity (min %.1f%%)", minPercentage);
            else
                name = "Allred intensity";
            return getNameForClasses(name, pathClasses);
        }

        @Override
        public Number getValue(final PathObject pathObject) {
            var counts = countsFunction.apply(pathObject);
            return computeAllredIntensity(counts, PathPrefs.allredMinPercentagePositiveProperty().getValue(), pathClasses);
        }

    }

    private static class AllredProportionMeasurementBuilder implements NumericMeasurementBuilder {

        private final Function<PathObject, DetectionPathClassCounts> countsFunction;
        private final PathClass[] pathClasses;

        AllredProportionMeasurementBuilder(Function<PathObject, DetectionPathClassCounts> countsFunction,
                                           PathClass... pathClasses) {
            this.countsFunction = countsFunction;
            this.pathClasses = pathClasses;
        }

        @Override
        public String getHelpText() {
            var pcString = getParentClassificationsString(pathClasses);
            double minPercentage = PathPrefs.allredMinPercentagePositiveProperty().get();
            String minRequires = minPercentage == 0 ? "" : "\nSet to 0 if less than " + minPercentage + "% cells positive";
            if (pcString.isEmpty()) {
                return "Allred proportion score calculated from Negative, 1+, 2+ and 3+ classified detections (range 0-5)" + minRequires;
            } else {
                return "Allred proportion score calculated from " + pcString + ": Negative, 1+, 2+ and 3+ classified detections (range 0-5)" + minRequires;
            }
        }


        @Override
        public String getName() {
            double minPercentage = PathPrefs.allredMinPercentagePositiveProperty().get();
            String name;
            if (minPercentage > 0)
                name = String.format("Allred proportion (min %.1f%%)", minPercentage);
            else
                name = "Allred proportion";
            return getNameForClasses(name, pathClasses);
        }

        @Override
        public Number getValue(final PathObject pathObject) {
            var counts = countsFunction.apply(pathObject);
            return computeAllredProportion(counts, PathPrefs.allredMinPercentagePositiveProperty().getValue(), pathClasses);
        }

    }

    private static class AllredMeasurementBuilder implements NumericMeasurementBuilder {

        private final Function<PathObject, DetectionPathClassCounts> countsFunction;
        private final PathClass[] pathClasses;

        AllredMeasurementBuilder(Function<PathObject, DetectionPathClassCounts> countsFunction,
                                 PathClass... pathClasses) {
            this.countsFunction = countsFunction;
            this.pathClasses = pathClasses;
        }

        @Override
        public String getHelpText() {
            return "Sum of Allred proportion and intensity scores (range 0-8)";
        }

        @Override
        public String getName() {
            double minPercentage = PathPrefs.allredMinPercentagePositiveProperty().get();
            String name;
            if (minPercentage > 0)
                name = String.format("Allred score (min %.1f%%)", minPercentage);
            else
                name = "Allred score";
            return getNameForClasses(name, pathClasses);
        }

        @Override
        public Number getValue(final PathObject pathObject) {
            var counts = countsFunction.apply(pathObject);
            return computeAllredScore(counts, PathPrefs.allredMinPercentagePositiveProperty().getValue(), pathClasses);
        }

    }

}
