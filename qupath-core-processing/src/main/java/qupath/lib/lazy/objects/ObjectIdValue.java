package qupath.lib.lazy.objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.objects.PathObject;

class ObjectIdValue implements LazyStringValue<PathObject> {

    private static final Logger logger = LoggerFactory.getLogger(ObjectIdValue.class);

    @Override
    public String getName() {
        return "Object ID";
    }

    @Override
    public String getHelpText() {
        return "Universal Unique Identifier for the selected object";
    }

    @Override
    public String getValue(PathObject pathObject) {
        var id = pathObject.getID(); // Shouldn't be null!
        if (id == null) {
            logger.warn("ID null for {}", pathObject);
            return null;
        }
        return id.toString();
    }

}
