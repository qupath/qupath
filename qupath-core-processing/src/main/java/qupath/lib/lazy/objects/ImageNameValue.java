package qupath.lib.lazy.objects;

import qupath.lib.images.ImageData;
import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;

/**
 * Get the displayed name of the parent of this object.
 */
class ImageNameValue implements LazyStringValue<PathObject> {

    private final ImageData<?> imageData;

    ImageNameValue(final ImageData<?> imageData) {
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
    public String getValue(PathObject pathObject) {
        if (imageData == null)
            return null;
        var hierarchy = imageData.getHierarchy();
        if (PathObjectTools.hierarchyContainsObject(hierarchy, pathObject)) {
            return imageData.getServerMetadata().getName();
        }
        return null;
    }

}
