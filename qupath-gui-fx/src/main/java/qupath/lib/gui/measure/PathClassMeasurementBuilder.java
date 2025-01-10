package qupath.lib.gui.measure;

import qupath.lib.objects.PathObject;

class PathClassMeasurementBuilder implements StringMeasurementBuilder {

    @Override
    public String getName() {
        return "Classification";
    }

    @Override
    public String getHelpText() {
        return "The classification of the selected object";
    }

    @Override
    public String getValue(PathObject pathObject) {
        return pathObject.getPathClass() == null ? null : pathObject.getPathClass().toString();
    }

}
