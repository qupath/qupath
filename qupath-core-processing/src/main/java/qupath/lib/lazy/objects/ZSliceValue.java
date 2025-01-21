package qupath.lib.lazy.objects;

import qupath.lib.lazy.interfaces.LazyNumericValue;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

class ZSliceValue implements LazyNumericValue<PathObject> {

    @Override
    public String getName() {
        return "Z-slice";
    }

    @Override
    public String getHelpText() {
        return "Index of z-slice (0-based)";
    }

    @Override
    public Number getValue(final PathObject pathObject) {
        ROI roi = pathObject.getROI();
        if (roi == null)
            return null;
        return roi.getZ();
    }

}
