package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.objects.PathObject;

class StringMetadataValue implements LazyStringValue<PathObject> {

    private final String name;

    StringMetadataValue(final String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHelpText() {
        return "A metadata value stored within the selected object";
    }

    @Override
    public String getValue(PathObject pathObject) {
        if (pathObject != null && pathObject.hasMetadata()) {
            return pathObject.getMetadata().getOrDefault(name, null);
        }
        return null;
    }

}
