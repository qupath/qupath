package qupath.process.gui.commands.ml.tasks;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.opencv.ml.models.FeatureImportance;
import qupath.opencv.ml.pixel.train.FeatureImportanceCalculator;
import qupath.opencv.ml.pixel.train.PixelClassifierTraining;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;

/**
 * Task to compute feature importance.
 */
public class FeatureImportanceTask extends MonitoredTask<List<FeatureImportance>> {

    private final FeatureImportanceCalculator calculator;
    private final PixelClassifierTraining training;
    private final List<ImageData<BufferedImage>> images;

    public FeatureImportanceTask(FeatureImportanceCalculator calculator,
                                 PixelClassifierTraining training,
                                 List<ImageData<BufferedImage>> images) {
        Objects.requireNonNull(calculator);
        Objects.requireNonNull(training);
        Objects.requireNonNull(images);
        if (images.isEmpty()) {
            throw new IllegalArgumentException("Images must not be empty");
        }
        this.calculator = calculator;
        this.training = training;
        this.images = List.copyOf(images);
    }

    @Override
    protected List<FeatureImportance> call() throws Exception {
        var trainingData = training.createTrainingData(images, rescale(0, 0.5));
        return this.calculator.calculateFeatureImportance(
                trainingData,
                training.getFeatureOp().getChannels(images.getFirst()).stream().map(ImageChannel::getName).toList(),
                rescale(0.5, 1.0)
        );
    }

}
