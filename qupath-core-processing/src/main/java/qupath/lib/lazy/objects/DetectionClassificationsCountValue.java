package qupath.lib.lazy.objects;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.function.Function;

class DetectionClassificationsCountValue extends DetectionCountValue {

    private final PathClass pathClass;
    private final boolean baseClassification;

    /**
     * Count objects with specific classifications.
     *
     * @param countsFunction     a function to create counts for the objects; this can return cached values for performance
     * @param pathClass          the classification of detections to count
     * @param baseClassification if {@code true}, also count objects with classifications derived from the specified classification,
     *                           if {@code false} count objects with <i>only</i> the exact classification given.
     */
    DetectionClassificationsCountValue(Function<PathObject, DetectionPathClassCounts> countsFunction,
                                       PathClass pathClass, boolean baseClassification) {
        super(countsFunction);
        this.pathClass = pathClass;
        this.baseClassification = baseClassification;
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
    public Number computeValue(final PathObject pathObject, DetectionPathClassCounts counts) {
        if (baseClassification)
            return counts.getCountForAncestor(pathClass);
        else
            return counts.getDirectCount(pathClass);
    }

}
