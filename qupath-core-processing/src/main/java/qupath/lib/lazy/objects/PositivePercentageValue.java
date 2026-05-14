package qupath.lib.lazy.objects;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.function.Function;

class PositivePercentageValue extends DetectionCountValue {

    private PathClass[] parentClasses;

    PositivePercentageValue(Function<PathObject, DetectionPathClassCounts> countsFunction,
                            PathClass... parentClasses) {
        super(countsFunction);
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
    public Number computeValue(final PathObject pathObject, DetectionPathClassCounts counts) {
        return counts.getPositivePercentage(parentClasses);
    }

}
