package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.objects.PathObject;

class PathClassValue implements LazyStringValue<PathObject> {

    @Override
    public String getName() {
        return "Classification";
    }

    @Override
    public String getHelpText() {
        return "The classification of the selected object";
    }

    @Override
    public String getValue(PathObject pathObject) {
        return pathObject.getPathClass() == null ? null : pathObject.getPathClass().toString();
    }

}
