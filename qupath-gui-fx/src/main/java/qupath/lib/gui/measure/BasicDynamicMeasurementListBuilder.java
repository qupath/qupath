package qupath.lib.gui.measure;

import qupath.lib.measurements.dynamic.DefaultMeasurements;
import qupath.lib.measurements.dynamic.MeasurementBuilder;

import java.util.ArrayList;
import java.util.List;

public class BasicDynamicMeasurementListBuilder implements ObjectMeasurementListBuilder {

    @Override
    public List<MeasurementBuilder<?>> getMeasurements(PathObjectListWrapper wrapper) {

        List<MeasurementBuilder<?>> measurements = new ArrayList<>();

        // Check if we have any annotations / TMA cores

        var imageData = wrapper.getImageData();

        // Include object ID if we have anything other than root objects
        if (!wrapper.containsRootOnly())
            measurements.add(DefaultMeasurements.OBJECT_ID);

        // Include the object type
        measurements.add(DefaultMeasurements.OBJECT_TYPE);

        // Include the object displayed name
        measurements.add(DefaultMeasurements.OBJECT_NAME);

        // Include the classification
        if (!wrapper.containsRootOnly()) {
            measurements.add(DefaultMeasurements.CLASSIFICATION);
            // Get the name of the containing TMA core if we have anything other than cores
            if (imageData != null && imageData.getHierarchy().getTMAGrid() != null) {
                measurements.add(DefaultMeasurements.TMA_CORE_NAME);
            }
            // Get the name of the first parent object
            measurements.add(DefaultMeasurements.PARENT_DISPLAYED_NAME);
        }

        // Include the TMA missing status, if appropriate
        if (wrapper.containsTMACores()) {
            measurements.add(DefaultMeasurements.TMA_CORE_MISSING);
        }

        if (wrapper.containsAnnotationsOrDetections()) {
            measurements.add(DefaultMeasurements.ROI_TYPE);
        }

        // Add centroids
        if (!wrapper.containsRootOnly()) {
            measurements.add(DefaultMeasurements.createROICentroidX(imageData));
            measurements.add(DefaultMeasurements.createROICentroidY(imageData));
        }

        // New v0.4.0: include z and time indices
        var serverMetadata = imageData == null ? null : imageData.getServerMetadata();
        if (wrapper.containsMultiZ() || (wrapper.containsROIs() && serverMetadata != null && serverMetadata.getSizeZ() > 1)) {
            measurements.add(DefaultMeasurements.ROI_Z_SLICE);
        }

        if (wrapper.containsMultiT() || (wrapper.containsROIs() && serverMetadata != null && serverMetadata.getSizeT() > 1)) {
            measurements.add(DefaultMeasurements.ROI_TIMEPOINT);
        }
        return measurements;
    }

}
