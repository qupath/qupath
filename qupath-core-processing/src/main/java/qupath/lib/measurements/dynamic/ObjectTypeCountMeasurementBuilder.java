package qupath.lib.measurements.dynamic;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;

import java.util.Collection;

class ObjectTypeCountMeasurementBuilder implements NumericMeasurementBuilder {

    private final ImageData<?> imageData;
    private final Class<? extends PathObject> cls;

    public ObjectTypeCountMeasurementBuilder(ImageData<?> imageData, Class<? extends PathObject> cls) {
        this.imageData = imageData;
        this.cls = cls;
    }

    @Override
    public String getHelpText() {
        return "Number of objects with type '" + PathObjectTools.getSuitableName(cls, false) + "'";
    }

    @Override
    public String getName() {
        return "Num " + PathObjectTools.getSuitableName(cls, true);
    }

    @Override
    public Number getValue(final PathObject pathObject) {
        Collection<PathObject> pathObjects;
        var hierarchy = imageData.getHierarchy();
        if (pathObject.isRootObject()) {
            pathObjects = hierarchy.getObjects(null, cls);
        } else {
            pathObjects = hierarchy.getObjectsForROI(cls, pathObject.getROI());
        }
        pathObjects.remove(pathObject);
        return pathObjects.size();
    }

    @Override
    public String toString() {
        return getName();
    }

}
