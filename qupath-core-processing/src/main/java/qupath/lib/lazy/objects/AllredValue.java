package qupath.lib.lazy.objects;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Calculate an Allred score, between 0 and 8.
 * This is the sum of an intensity and a proportion component.
 */
class AllredValue extends AbstractAllredValue {

    private final PathClass[] pathClasses;

    AllredValue(Function<PathObject, DetectionPathClassCounts> countsFunction,
                Supplier<Double> minPositivePercentageSupplier,
                PathClass... pathClasses) {
        super(countsFunction, minPositivePercentageSupplier);
        this.pathClasses = pathClasses;
    }

    @Override
    public String getHelpText() {
        return "Sum of Allred proportion and intensity scores (range 0-8)";
    }

    @Override
    public String getName() {
        double minPercentage = getMinPositivePercentage();
        String name;
        if (minPercentage > 0)
            name = String.format("Allred score (min %.1f%%)", minPercentage);
        else
            name = "Allred score";
        return getNameForClasses(name, pathClasses);
    }

    @Override
    public Number computeValue(final PathObject pathObject, DetectionPathClassCounts counts) {
        return counts.getAllredScore(getMinPositivePercentage() / 100.0, pathClasses);
    }

}
