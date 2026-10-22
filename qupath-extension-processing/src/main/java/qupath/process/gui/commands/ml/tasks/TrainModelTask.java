package qupath.process.gui.commands.ml.tasks;

import javafx.concurrent.Task;
import qupath.lib.images.ImageData;
import qupath.opencv.ml.models.TrainableModel;
import qupath.opencv.ml.pixel.train.ClassifierTrainingData;
import qupath.opencv.ml.pixel.train.PixelClassifierModelTrainer;
import qupath.opencv.ml.pixel.train.PixelClassifierTraining;
import qupath.opencv.ml.pixel.train.TrainedPixelClassifierModel;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * Task to train a pixel classifier model.
 */
public class TrainModelTask extends Task<TrainedPixelClassifierModel> {

    private final TrainableModel model;
    private final List<ImageData<BufferedImage>> trainingImages;
    private final PixelClassifierTraining helper;
    private final PixelClassifierModelTrainer trainer;

    public TrainModelTask(TrainableModel model, List<ImageData<BufferedImage>> trainingImages,
                          PixelClassifierTraining helper, PixelClassifierModelTrainer trainer) {
        this.model = model;
        this.trainingImages = List.copyOf(trainingImages);
        this.helper = helper;
        this.trainer = trainer;
    }

    @Override
    protected TrainedPixelClassifierModel call() throws IOException {
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
        var trainingData = ClassifierTrainingData.merge(allTrainingData);
        return trainer.train(model, trainingData);
    }
}
