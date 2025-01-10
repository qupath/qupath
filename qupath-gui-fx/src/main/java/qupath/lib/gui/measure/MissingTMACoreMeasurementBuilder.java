package qupath.lib.gui.measure;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;

class MissingTMACoreMeasurementBuilder extends AbstractStringMeasurementBuilder {

    @Override
    public String getName() {
        return "Missing core";
    }

    @Override
    public String getHelpText() {
        return "True if the selected object is a TMA core marked as 'missing', False if it is not missing";
    }

    @Override
    protected String getMeasurementValue(PathObject pathObject) {
        if (pathObject instanceof TMACoreObject)
            return ((TMACoreObject) pathObject).isMissing() ? "True" : "False";
        return null;
    }

}
