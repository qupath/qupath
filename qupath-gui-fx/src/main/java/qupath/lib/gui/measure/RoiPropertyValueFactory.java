package qupath.lib.gui.measure;

import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.lazy.objects.PathObjectLazyValues;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class RoiPropertyValueFactory implements PathObjectValueFactory {

    @Override
    public List<LazyValue<PathObject, ?>> createValues(PathObjectListWrapper wrapper) {

        List<LazyValue<PathObject, ?>> measurements = new ArrayList<>();

        var imageData = wrapper.getImageData();

        if (wrapper.containsAnnotationsOrDetections()) {
            measurements.add(PathObjectLazyValues.ROI_TYPE);
        }

        // Add centroids
        if (!wrapper.containsRootOnly()) {
            measurements.add(PathObjectLazyValues.createROICentroidX(imageData));
            measurements.add(PathObjectLazyValues.createROICentroidY(imageData));
        }

        // New v0.4.0: include z and time indices
        var serverMetadata = imageData == null ? null : imageData.getServerMetadata();
        if (wrapper.containsMultiZ() || (wrapper.containsROIs() && serverMetadata != null && serverMetadata.getSizeZ() > 1)) {
            measurements.add(PathObjectLazyValues.ROI_Z_SLICE);
        }

        if (wrapper.containsMultiT() || (wrapper.containsROIs() && serverMetadata != null && serverMetadata.getSizeT() > 1)) {
            measurements.add(PathObjectLazyValues.ROI_TIMEPOINT);
        }

        // If we have an annotation, add shape features
        if (wrapper.containsAnnotations()) {
            // Find all non-null annotation measurements
            var annotationRois = wrapper.getPathObjects().stream()
                    .filter(PathObject::isAnnotation)
                    .map(PathObject::getROI)
                    .filter(Objects::nonNull)
                    .toList();
            // Add point count, if we have any points
            if (annotationRois.stream().anyMatch(ROI::isPoint)) {
                measurements.add(PathObjectLazyValues.ROI_NUM_POINTS);
            }
            // Add area & perimeter measurements, if we have any areas
            if (annotationRois.stream().anyMatch(ROI::isArea)) {
                measurements.add(PathObjectLazyValues.createROIAreaMeasurement(wrapper.getImageData()));
                measurements.add(PathObjectLazyValues.createROIPerimeterMeasurement(wrapper.getImageData()));
            }
            // Add line length measurements, if we have any lines
            if (annotationRois.stream().anyMatch(ROI::isLine)) {
                measurements.add(PathObjectLazyValues.createROILengthMeasurement(wrapper.getImageData()));
            }
        }

        return measurements;
    }

}
