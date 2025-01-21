package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyNumericValue;
import qupath.lib.objects.PathObject;

/**
 * Value extracted from an object's measurement list.
 */
public class MeasurementListValue implements LazyNumericValue {

    private final String name;

    MeasurementListValue(final String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHelpText() {
        return "A value stored within the selected object's measurement list";
    }

    @Override
    public Number getValue(PathObject pathObject) {
        if (pathObject != null) {
            var ml = pathObject.getMeasurementList();
            var val = ml.get(name);
            if (Double.isNaN(val) && !ml.containsKey(name))
                return null;
            return val;
        } else {
            return null;
        }
    }
}
