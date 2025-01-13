package qupath.lib.gui.measure;

import qupath.lib.objects.PathObject;
import qupath.opencv.ml.pixel.PixelClassificationMeasurementManager;

class PixelClassifierMeasurementBuilder implements NumericMeasurementBuilder {

    private PixelClassificationMeasurementManager manager;
    private String name;

    PixelClassifierMeasurementBuilder(PixelClassificationMeasurementManager manager, String name) {
        this.manager = manager;
        this.name = name;
    }

    @Override
    public String getName() {
        return "(Live)" + name;
    }

    @Override
    public String getHelpText() {
        return "A temporary measurement made by the current pixel classifier";
    }

    @Override
    public Number getValue(PathObject pathObject) {
        // Return only measurements that can be generated rapidly from cached tiles
        return manager.getCachedMeasurementValue(pathObject, name);
    }

}
