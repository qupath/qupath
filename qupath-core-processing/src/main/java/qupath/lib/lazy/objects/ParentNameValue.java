package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.objects.PathObject;

/**
 * Get the displayed name of the parent of this object.
 */
class ParentNameValue implements LazyStringValue<PathObject> {

    @Override
    public String getName() {
        return "Parent";
    }

    @Override
    public String getHelpText() {
        return "Displayed name the parent of the selected object in the object hierarchy";
    }

    @Override
    public String getValue(PathObject pathObject) {
        PathObject parent = pathObject == null ? null : pathObject.getParent();
        if (parent == null)
            return null;
        return parent.getDisplayedName();
    }

}
