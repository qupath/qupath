package qupath.lib.lazy.objects;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.function.Function;

/**
 * Calculate H-score between 0 and 300.
 */
class HScoreValue extends DetectionCountValue {

    private final PathClass[] pathClasses;

    HScoreValue(Function<PathObject, DetectionPathClassCounts> countsFunction, PathClass... pathClasses) {
        super(countsFunction);
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
    public Number computeValue(final PathObject pathObject, DetectionPathClassCounts counts) {
        return counts.getHScore(pathClasses);
    }

}
