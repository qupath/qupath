package qupath.lib.gui.measure;

import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.lazy.objects.PathObjectLazyValues;
import qupath.lib.objects.PathObject;
import qupath.opencv.ml.pixel.PixelClassificationMeasurementManager;

import java.util.ArrayList;
import java.util.List;

class LivePixelClassificationValueFactory implements PathObjectValueFactory {


    @Override
    public List<LazyValue<PathObject, ?>> createValues(PathObjectListWrapper wrapper) {

        List<LazyValue<PathObject, ?>> measurements = new ArrayList<>();

        if (wrapper.containsAnnotations() || wrapper.containsTMACores() || wrapper.containsRoot()) {
            var pixelClassifier = ObservableMeasurementTableData.getPixelLayer(wrapper.getImageData());
            if (pixelClassifier != null) {
                if (pixelClassifier.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.CLASSIFICATION || pixelClassifier.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.PROBABILITY) {
                    var pixelManager = new PixelClassificationMeasurementManager(pixelClassifier);
                    for (String name : pixelManager.getMeasurementNames()) {
                        measurements.add(PathObjectLazyValues.createLivePixelClassificationMeasurement(pixelManager, name));
                    }
                }
            }
        }

        return measurements;
    }
}
