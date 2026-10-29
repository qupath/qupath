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

/**
 * Record to store a trained model that can be used to build a pixel classifier.
 * @param model the trained model
 * @param featureCalculator the data op used to extract features
 * @param featurePreprocessor the feature preprocessor, applied to the features before they are input to the
 *                           model (optional)
 * @param labels a label map to interpret the output
 * @param countsIndexedByLabels a histogram of the number of training samples, indexed by integer label
 * @param resolution the resolution at which the classifier should be applied
 * @param trainingTime the total time required to train the model
 * @since v0.8.0
 */
public record TrainedPixelClassifierModel(TrainableModel model,
                                          ImageDataOp featureCalculator,
                                          FeaturePreprocessor featurePreprocessor,
                                          Map<PathClass, Integer> labels,
                                          int[] countsIndexedByLabels,
                                          PixelCalibration resolution,
                                          Duration trainingTime) {

    private static final Logger logger = LoggerFactory.getLogger(TrainedPixelClassifierModel.class);

    /**
     * Create a pixel classifier from this model.
     * @param outputType output type, either {@link qupath.lib.images.servers.ImageServerMetadata.ChannelType#CLASSIFICATION} or
     * {@link qupath.lib.images.servers.ImageServerMetadata.ChannelType#PROBABILITY}
     * @return a new pixel classifier, wrapping this model
     */
    public PixelClassifier createPixelClassifier(ImageServerMetadata.ChannelType outputType) {
        return createPixelClassifier(512, 512, outputType);
    }

    /**
     * Create a pixel classifier from this model.
     * @param inputWidth input tile width
     * @param inputHeight input tile height
     * @param outputType output type, either {@link qupath.lib.images.servers.ImageServerMetadata.ChannelType#CLASSIFICATION} or
     * {@link qupath.lib.images.servers.ImageServerMetadata.ChannelType#PROBABILITY}
     * @return a new pixel classifier, wrapping this model
     */
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

    /**
     * Get the names of the features used to train the model, derived from the input image.
     * @param imageData the image used to determine the feature names.
     *                  This is required because some feature names derived from image channel names.
     * @return a list of feature names
     */
    public List<String> getFeatureNames(ImageData<BufferedImage> imageData) {
        return featureCalculator.getChannels(imageData).stream().map(ImageChannel::getName).toList();
    }

}
