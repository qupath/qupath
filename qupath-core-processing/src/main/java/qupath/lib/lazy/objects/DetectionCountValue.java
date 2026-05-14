package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyNumericValue;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Abstract base class for measurements that can be derived from a {@link DetectionPathClassCounts} object.
 */
abstract class DetectionCountValue implements LazyNumericValue<PathObject> {

    private final Function<PathObject, DetectionPathClassCounts> countsFunction;

    DetectionCountValue(Function<PathObject, DetectionPathClassCounts> countsFunction) {
        this.countsFunction = countsFunction;
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
    protected String getParentClassificationsString(PathClass... pathClasses) {
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
    protected String getNameForClasses(final String measurementName, final PathClass... parentClasses) {
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

    public Number getValue(final PathObject pathObject) {
        var counts = countsFunction.apply(pathObject);
        if (counts == null)
            return null;
        else
            return computeValue(pathObject, counts);
    }

    /**
     * Compute the measurement value.
     *
     * @param pathObject the input object for which the calculation should be made
     * @param counts     counts based on the input object; this should never be null (if it is, the method isn't called)
     * @return
     */
    protected abstract Number computeValue(PathObject pathObject, DetectionPathClassCounts counts);

    @Override
    public String toString() {
        return getName();
    }

}
