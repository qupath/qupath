package qupath.lib.lazy.objects;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Calculate the intensity component of an Allred score.
 */
class AllredProportionValue extends AbstractAllredValue {

    private final PathClass[] pathClasses;

    AllredProportionValue(Function<PathObject, DetectionPathClassCounts> countsFunction,
                          Supplier<Double> minPositivePercentageSupplier,
                          PathClass... pathClasses) {
        super(countsFunction, minPositivePercentageSupplier);
        this.pathClasses = pathClasses;
    }

    @Override
    public String getHelpText() {
        var pcString = getParentClassificationsString(pathClasses);
        double minPercentage = getMinPositivePercentage();
        String minRequires = minPercentage == 0 ? "" : "\nSet to 0 if less than " + minPercentage + "% cells positive";
        if (pcString.isEmpty()) {
            return "Allred proportion score calculated from Negative, 1+, 2+ and 3+ classified detections (range 0-5)" + minRequires;
        } else {
            return "Allred proportion score calculated from " + pcString + ": Negative, 1+, 2+ and 3+ classified detections (range 0-5)" + minRequires;
        }
    }


    @Override
    public String getName() {
        double minPercentage = getMinPositivePercentage();
        String name;
        if (minPercentage > 0)
            name = String.format("Allred proportion (min %.1f%%)", minPercentage);
        else
            name = "Allred proportion";
        return getNameForClasses(name, pathClasses);
    }

    @Override
    public Number computeValue(final PathObject pathObject, DetectionPathClassCounts counts) {
        return counts.getAllredProportion(getMinPositivePercentage() / 100.0, pathClasses);
    }

}
