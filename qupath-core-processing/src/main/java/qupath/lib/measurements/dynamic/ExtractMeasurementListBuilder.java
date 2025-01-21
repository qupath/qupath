package qupath.lib.measurements.dynamic;

import qupath.lib.objects.PathObject;

public class ExtractMeasurementListBuilder implements NumericMeasurementBuilder {

    private final String name;

    ExtractMeasurementListBuilder(final String name) {
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
