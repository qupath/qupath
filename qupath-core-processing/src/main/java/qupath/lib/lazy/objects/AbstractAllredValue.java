package qupath.lib.lazy.objects;

import qupath.lib.common.GeneralTools;
import qupath.lib.objects.PathObject;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstract base class for calculating an Allred score (the sum of intensity and proportion components).
 */
abstract class AbstractAllredValue extends DetectionCountValue {

    private final Supplier<Double> minPositivePercentageSupplier;

    /**
     * Constructor.
     * @param countsFunction function to get the detection counts
     * @param minPositivePercentageSupplier supplier for the minimum positive percentage; this may be from a constant,
     *                                      or from a user-adjustable preference that will be calculated on demand.
     */
    AbstractAllredValue(Function<PathObject, DetectionPathClassCounts> countsFunction,
                        Supplier<Double> minPositivePercentageSupplier) {
        super(countsFunction);
        this.minPositivePercentageSupplier = minPositivePercentageSupplier;
    }

    /**
     * Get the minimum percentage of positive cells required for a score greater than 0.
     * This is used to avoid a small number of erroneous detections (e.g. due to a tissue fold or other artefact)
     * producing a positive result.
     */
    protected double getMinPositivePercentage() {
        return GeneralTools.clipValue(minPositivePercentageSupplier.get(), 0, 100);
    }

}
