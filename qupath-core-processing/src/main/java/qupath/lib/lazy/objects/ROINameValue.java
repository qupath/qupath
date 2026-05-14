package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyStringValue;
import qupath.lib.objects.PathObject;

class ROINameValue implements LazyStringValue<PathObject> {

    @Override
    public String getName() {
        return "ROI";
    }

    @Override
    public String getHelpText() {
        return "The name of the region of interest (ROI) type";
    }

    @Override
    public String getValue(PathObject pathObject) {
        return pathObject.hasROI() ? pathObject.getROI().getRoiName() : null;
    }

}
