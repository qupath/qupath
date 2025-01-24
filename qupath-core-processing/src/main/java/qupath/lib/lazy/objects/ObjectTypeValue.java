package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;

class ObjectTypeValue implements LazyStringValue<PathObject> {

    @Override
    public String getName() {
        return "Object type";
    }

    @Override
    public String getHelpText() {
        return "The object type (e.g. annotation, detection, cell, tile, TMA core)";
    }

    @Override
    public String getValue(PathObject pathObject) {
        if (pathObject == null)
            return null;
        else if (pathObject.isRootObject())
            return pathObject.getDisplayedName();
        else
            return PathObjectTools.getSuitableName(pathObject.getClass(), false);
    }

}
