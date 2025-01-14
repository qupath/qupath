package qupath.lib.gui.measure.measurements;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;

class MissingTMACoreMeasurementBuilder implements MeasurementBuilder<Boolean> {

    @Override
    public String getName() {
        return "Missing core";
    }

    @Override
    public Class<Boolean> getMeasurementType() {
        return Boolean.class;
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
