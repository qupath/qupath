package qupath.process.gui.commands.ml.tasks;

import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.opencv.ml.models.TrainableModel;
import qupath.lib.progress.LogProgressMonitor;
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
public class TrainModelTask extends MonitoredTask<TrainedPixelClassifierModel> {

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
        var monitor = this.and(LogProgressMonitor.debug(LoggerFactory.getLogger(getClass())));
        List<ClassifierTrainingData> allTrainingData = helper.createTrainingData(trainingImages, monitor.rescale(0, 0.9));
        if (allTrainingData.isEmpty()) {
            return null;
        }

        // TODO: Add progress monitoring to training
        updateProgress("Training classifier", 0.9);
        var trainingData = ClassifierTrainingData.merge(allTrainingData);
        var result = trainer.train(model, trainingData);
        monitor.updateProgress("Done!", 1.0);
        return result;
    }
}
