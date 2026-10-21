package qupath.process.gui.commands.ml.tasks;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.ConfusionMatrix;
import qupath.opencv.ml.models.TrainableModel;
import qupath.process.gui.commands.ml.core.ClassifierTrainingData;
import qupath.process.gui.commands.ml.core.CrossValidation;
import qupath.process.gui.commands.ml.ModelTrainer;
import qupath.process.gui.commands.ml.core.PixelClassifierTraining;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * Task to compute confusion matrices by training models with cross validation.
 */
public class CrossValidationTask extends Task<List<ConfusionMatrix<PathClass>>> {

    private static final Logger logger = LoggerFactory.getLogger(CrossValidationTask.class);

    private final TrainableModel model;
    private final PixelClassifierTraining helper;
    private final List<ImageData<BufferedImage>> trainingImages;
    private final ModelTrainer modelTrainer;
    private final Random rng;

    public CrossValidationTask(TrainableModel model,
                               PixelClassifierTraining helper,
                               List<ImageData<BufferedImage>> trainingImages,
                               ModelTrainer modelTrainer,
                               Random rng) {
        this.model = model;
        this.helper = helper;
        this.trainingImages = List.copyOf(trainingImages);
        this.modelTrainer = modelTrainer;
        this.rng = rng;
    }

    @Override
    protected List<ConfusionMatrix<PathClass>> call() throws Exception {
        if (isCancelled())
            return List.of();
        updateMessage("Calculating features...");
        List<ClassifierTrainingData> allTrainingData = helper.createTrainingData(trainingImages);
        if (allTrainingData.isEmpty()) {
            logger.warn("Can't compute cross validation, no training data available");
            return List.of();
        }
        if (isCancelled())
            return List.of();
        updateMessage("Calculating cross validation...");
        var result = CrossValidation.computeCrossValidation(
                model,
                modelTrainer,
                allTrainingData,
                rng
        );
        if (isCancelled())
            return List.of();
        else
            return result;
    }
}
