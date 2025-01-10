package qupath.lib.gui.measure;

import qupath.lib.objects.PathObject;

class StringMetadataMeasurementBuilder extends AbstractStringMeasurementBuilder {

    private String name;

    StringMetadataMeasurementBuilder(final String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHelpText() {
        return "A metadata value stored within the selected object";
    }

    @Override
    public String getMeasurementValue(PathObject pathObject) {
        if (pathObject != null) {
            return pathObject.getMetadata().getOrDefault(name, null);
        }
        return null;
    }

}
