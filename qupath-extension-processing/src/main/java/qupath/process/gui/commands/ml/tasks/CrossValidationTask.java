package qupath.process.gui.commands.ml.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.ConfusionMatrix;
import qupath.opencv.ml.models.TrainableModel;
import qupath.opencv.ml.pixel.train.ClassifierTrainingData;
import qupath.opencv.ml.pixel.train.CrossValidation;
import qupath.opencv.ml.pixel.train.PixelClassifierModelTrainer;
import qupath.opencv.ml.pixel.train.PixelClassifierTraining;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * Task to compute confusion matrices by training models with cross validation.
 */
public class CrossValidationTask extends MonitoredTask<List<ConfusionMatrix<PathClass>>> {

    private static final Logger logger = LoggerFactory.getLogger(CrossValidationTask.class);

    private final TrainableModel model;
    private final PixelClassifierTraining helper;
    private final List<ImageData<BufferedImage>> trainingImages;
    private final PixelClassifierModelTrainer pixelClassifierModelTrainer;
    private final Random rng;

    public CrossValidationTask(TrainableModel model,
                               PixelClassifierTraining helper,
                               List<ImageData<BufferedImage>> trainingImages,
                               PixelClassifierModelTrainer pixelClassifierModelTrainer,
                               Random rng) {
        this.model = model;
        this.helper = helper;
        this.trainingImages = List.copyOf(trainingImages);
        this.pixelClassifierModelTrainer = pixelClassifierModelTrainer;
        this.rng = rng;
    }

    @Override
    protected List<ConfusionMatrix<PathClass>> call() throws Exception {
        if (isCancelled())
            return List.of();

        List<ClassifierTrainingData> allTrainingData = helper.createTrainingData(trainingImages, rescale(0, 0.5));
        if (allTrainingData.isEmpty()) {
            logger.warn("Can't compute cross validation, no training data available");
            return List.of();
        }
        if (isCancelled())
            return List.of();

        var result = CrossValidation.computeCrossValidation(
                model,
                pixelClassifierModelTrainer,
                allTrainingData,
                rng,
                rescale(0.5, 1.0)
        );
        if (isCancelled())
            return List.of();
        else
            return result;
    }
}
