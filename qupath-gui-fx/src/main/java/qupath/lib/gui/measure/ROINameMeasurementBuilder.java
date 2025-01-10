package qupath.lib.gui.measure;

import qupath.lib.objects.PathObject;

class ROINameMeasurementBuilder implements StringMeasurementBuilder {

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
