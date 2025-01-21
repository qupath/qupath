package qupath.lib.gui.measure;

import qupath.lib.measurements.dynamic.MeasurementBuilder;

import java.util.List;

public interface ObjectMeasurementListBuilder {

    List<MeasurementBuilder<?>> getMeasurements(PathObjectListWrapper wrapper);

}
