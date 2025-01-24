package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyBooleanValue;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;

class MissingTMACoreValue implements LazyBooleanValue<PathObject> {

    @Override
    public String getName() {
        return "Missing core";
    }

    @Override
    public String getHelpText() {
        return "True if the selected object is a TMA core marked as 'missing', False if it is not missing";
    }

    @Override
    public Boolean getValue(PathObject pathObject) {
        if (pathObject instanceof TMACoreObject core)
            return core.isMissing();
        return null;
    }




}
