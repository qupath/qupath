package qupath.lib.gui.measure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.PathObject;

class ObjectIdMeasurementBuilder implements StringMeasurementBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ObjectIdMeasurementBuilder.class);

    @Override
    public String getName() {
        return ObservableMeasurementTableData.NAME_OBJECT_ID;
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
