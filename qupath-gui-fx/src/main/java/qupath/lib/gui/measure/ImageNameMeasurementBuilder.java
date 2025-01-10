package qupath.lib.gui.measure;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;

/**
 * Get the displayed name of the parent of this object.
 */
class ImageNameMeasurementBuilder extends AbstractStringMeasurementBuilder {

    private final ImageData<?> imageData;

    ImageNameMeasurementBuilder(final ImageData<?> imageData) {
        this.imageData = imageData;
    }

    @Override
    public String getName() {
        return "Image";
    }

    @Override
    public String getHelpText() {
        return "Name for the current image";
    }

    @Override
    public String getMeasurementValue(PathObject pathObject) {
        if (imageData == null)
            return null;
        var hierarchy = imageData.getHierarchy();
        if (PathObjectTools.hierarchyContainsObject(hierarchy, pathObject)) {
            return imageData.getServerMetadata().getName();
        }
        return null;
    }

}
