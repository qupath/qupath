package qupath.lib.gui.measure;

import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.lazy.objects.PathObjectLazyValues;
import qupath.lib.objects.PathObject;

import java.util.ArrayList;
import java.util.List;

class ObjectPropertyValueFactory implements PathObjectValueFactory {

    @Override
    public List<LazyValue<PathObject, ?>> createValues(PathObjectListWrapper wrapper) {

        List<LazyValue<PathObject, ?>> measurements = new ArrayList<>();

        var imageData = wrapper.getImageData();

        // Include object ID if we have anything other than root objects
        if (!wrapper.containsRootOnly())
            measurements.add(PathObjectLazyValues.OBJECT_ID);

        // Include the object type
        measurements.add(PathObjectLazyValues.OBJECT_TYPE);

        // Include the object displayed name
        measurements.add(PathObjectLazyValues.OBJECT_NAME);

        // Include the classification
        if (!wrapper.containsRootOnly()) {
            measurements.add(PathObjectLazyValues.CLASSIFICATION);
            // Get the name of the containing TMA core if we have anything other than cores
            if (imageData != null && imageData.getHierarchy().getTMAGrid() != null) {
                measurements.add(PathObjectLazyValues.TMA_CORE_NAME);
            }
            // Get the name of the first parent object
            measurements.add(PathObjectLazyValues.PARENT_DISPLAYED_NAME);
        }

        // Include the TMA missing status, if appropriate
        if (wrapper.containsTMACores()) {
            measurements.add(PathObjectLazyValues.TMA_CORE_MISSING);
        }

        return measurements;
    }

}
