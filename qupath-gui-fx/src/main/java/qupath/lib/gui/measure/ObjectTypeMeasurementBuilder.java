package qupath.lib.gui.measure;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;

class ObjectTypeMeasurementBuilder extends AbstractStringMeasurementBuilder {

    @Override
    public String getName() {
        return "Object type";
    }

    @Override
    public String getHelpText() {
        return "The object type (e.g. annotation, detection, cell, tile, TMA core)";
    }

    @Override
    protected String getMeasurementValue(PathObject pathObject) {
        if (pathObject == null)
            return null;
        else if (pathObject.isRootObject())
            return pathObject.getDisplayedName();
        else
            return PathObjectTools.getSuitableName(pathObject.getClass(), false);
    }

}
