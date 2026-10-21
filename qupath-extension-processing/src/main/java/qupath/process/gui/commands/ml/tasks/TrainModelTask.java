package qupath.process.gui.commands.ml.tasks;

import javafx.concurrent.Task;
import qupath.lib.images.ImageData;
import qupath.opencv.ml.models.TrainableModel;
import qupath.opencv.ml.pixel.train.ClassifierTrainingData;
import qupath.opencv.ml.pixel.train.ModelTrainer;
import qupath.opencv.ml.pixel.train.PixelClassifierTraining;
import qupath.opencv.ml.pixel.train.TrainedModel;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * Task to train a pixel classifier model.
 */
public class TrainModelTask extends Task<TrainedModel> {

    private final TrainableModel model;
    private final List<ImageData<BufferedImage>> trainingImages;
    private final PixelClassifierTraining helper;
    private final ModelTrainer trainer;

    public TrainModelTask(TrainableModel model, List<ImageData<BufferedImage>> trainingImages,
                          PixelClassifierTraining helper, ModelTrainer trainer) {
        this.model = model;
        this.trainingImages = List.copyOf(trainingImages);
        this.helper = helper;
        this.trainer = trainer;
    }

    @Override
    protected TrainedModel call() throws IOException {
        long totalWork = 2;
        long workDone = 0;
        updateMessage("Calculating features...");
        updateProgress(workDone++, totalWork);
        List<ClassifierTrainingData> allTrainingData = helper.createTrainingData(trainingImages);
        if (allTrainingData.isEmpty()) {
            return null;
        }

        updateMessage("Training model...");
        updateProgress(workDone++, totalWork);
        try (var trainingData = ClassifierTrainingData.merge(allTrainingData)) {
            return trainer.train(model, trainingData);
        }
    }
}
