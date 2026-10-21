package qupath.opencv.ml.pixel.train;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.FeaturePreprocessor;
import qupath.opencv.ml.models.TrainableModel;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ops.ImageDataOp;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record TrainedPixelClassifierModel(TrainableModel model,
                                          ImageDataOp featureCalculator,
                                          FeaturePreprocessor featurePreprocessor,
                                          Map<PathClass, Integer> labels,
                                          int[] countsIndexedByLabels,
                                          PixelCalibration resolution,
                                          Duration trainingTime) {

    private static final Logger logger = LoggerFactory.getLogger(TrainedPixelClassifierModel.class);

    public PixelClassifier createPixelClassifier(ImageServerMetadata.ChannelType outputType) {
        return createPixelClassifier(512, 512, outputType);
    }

    public PixelClassifier createPixelClassifier(int inputWidth, int inputHeight, ImageServerMetadata.ChannelType outputType) {
        if (!model.supportsProbabilities() && !model.supportsProbabilities()) {
            logger.warn("Output type {} not supported, will use {}", outputType, ImageServerMetadata.ChannelType.CLASSIFICATION);
        }

        // Channels are needed for probability output (and work for classification as well)
        var labels2 = new TreeMap<Integer, PathClass>();
        for (var entry : labels.entrySet()) {
            var previous = labels2.put(entry.getValue(), entry.getKey());
            if (previous != null)
                logger.warn("Duplicate label found! {} matches with {} and {}, only the latter be used", entry.getValue(), previous, entry.getKey());
        }
        var channels = ServerTools.classificationLabelsToChannels(labels2, true);

        PixelClassifierMetadata metadata = new PixelClassifierMetadata.Builder()
                .inputResolution(resolution)
                .inputShape(inputWidth, inputHeight)
                .setChannelType(outputType)
                .outputChannels(channels)
                .build();

        return PixelClassifiers.createClassifier(model, featureCalculator, metadata, true);
    }

    public List<String> getFeatureNames(ImageData<BufferedImage> imageData) {
        return featureCalculator.getChannels(imageData).stream().map(ImageChannel::getName).toList();
    }

}
