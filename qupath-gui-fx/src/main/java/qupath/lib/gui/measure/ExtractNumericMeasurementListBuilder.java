package qupath.lib.gui.measure;

import qupath.lib.measurements.dynamic.DefaultMeasurements;
import qupath.lib.measurements.dynamic.MeasurementBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Measurements that only extract metadata from objects.
 * @return
 */
public class ExtractNumericMeasurementListBuilder implements ObjectMeasurementListBuilder {

    @Override
    public List<MeasurementBuilder<?>> getMeasurements(PathObjectListWrapper wrapper) {
        var list = new ArrayList<MeasurementBuilder<?>>();
        wrapper.getFeatureNames()
                .stream()
                .map(DefaultMeasurements::createMeasurementListMeasurement)
                .forEach(list::add);
        return list;
    }

}
