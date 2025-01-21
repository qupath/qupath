package qupath.lib.gui.measure;

import qupath.lib.measurements.dynamic.DefaultMeasurements;
import qupath.lib.measurements.dynamic.MeasurementBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Measurements that only extract metadata from objects.
 * @return
 */
public class ExtractMetadataMeasurementListBuilder implements ObjectMeasurementListBuilder {

    @Override
    public List<MeasurementBuilder<?>> getMeasurements(PathObjectListWrapper wrapper) {
        var list = new ArrayList<MeasurementBuilder<?>>();
        wrapper.getMetadataNames()
                .stream()
                .map(DefaultMeasurements::createMetadataMeasurement)
                .forEach(list::add);
        return list;
    }

}
