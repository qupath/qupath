package qupath.lib.gui.measure;

import javafx.beans.binding.Binding;
import javafx.beans.binding.Bindings;
import qupath.lib.objects.PathObject;
import qupath.opencv.ml.pixel.PixelClassificationMeasurementManager;

class PixelClassifierMeasurementBuilder extends AbstractNumericMeasurementBuilder {

    private PixelClassificationMeasurementManager manager;
    private String name;

    PixelClassifierMeasurementBuilder(PixelClassificationMeasurementManager manager, String name) {
        this.manager = manager;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHelpText() {
        return "A temporary measurement made by the current pixel classifier";
    }

    @Override
    public Binding<Number> createMeasurement(PathObject pathObject) {
        // Return only measurements that can be generated rapidly from cached tiles
        return Bindings.createObjectBinding(() -> manager.getMeasurementValue(pathObject, name, true));
    }

}
