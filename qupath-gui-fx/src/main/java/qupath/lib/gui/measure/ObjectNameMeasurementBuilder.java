package qupath.lib.gui.measure;

import qupath.lib.objects.PathObject;

class ObjectNameMeasurementBuilder extends AbstractStringMeasurementBuilder {

    @Override
    public String getName() {
        return "Name";
    }

    @Override
    public String getHelpText() {
        return "Name of the object (may be empty)";
    }

    @Override
    protected String getMeasurementValue(PathObject pathObject) {
        if (pathObject == null)
            return null;
        // v0.5.0 change - previously used pathObject.getDisplayedName(),
        // but this frequently led to confusion for people writing scripts
        return pathObject.getName();
    }

}
