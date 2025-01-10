package qupath.lib.gui.measure;

import javafx.beans.binding.IntegerBinding;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

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
        return new ObjectTypeCountMeasurement(imageData.getHierarchy(), pathObject, cls).getValue();
    }

    @Override
    public String toString() {
        return getName();
    }

    static class ObjectTypeCountMeasurement extends IntegerBinding {

        private final PathObjectHierarchy hierarchy;
        private final Class<? extends PathObject> cls;
        private final PathObject pathObject;

        ObjectTypeCountMeasurement(final PathObjectHierarchy hierarchy, final PathObject pathObject,
                                   final Class<? extends PathObject> cls) {
            this.hierarchy = hierarchy;
            this.pathObject = pathObject;
            this.cls = cls;
        }

        @Override
        protected int computeValue() {
            Collection<PathObject> pathObjects;
            if (pathObject.isRootObject()) {
                pathObjects = hierarchy.getObjects(null, cls);
            } else {
                pathObjects = hierarchy.getObjectsForROI(cls, pathObject.getROI());
            }
            pathObjects.remove(pathObject);
            return pathObjects.size();
        }

    }

}
