/**
 * Groovy script to demonstrate training a pixel classifier.
 */

import qupath.fx.utils.FXUtils
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay
import qupath.lib.images.servers.ImageServerMetadata
import qupath.opencv.ml.models.TrainableModel
import qupath.opencv.ml.models.statmodel.OpenCVStatModels
import qupath.opencv.ops.ImageDataOp
import qupath.process.gui.commands.ml.core.ClassifierTrainingData
import qupath.process.gui.commands.ml.ModelTrainer
import qupath.process.gui.commands.ml.core.PixelClassifierTraining
import qupath.process.gui.commands.ml.op.MultiscaleImageDataOpBuilder

import static qupath.lib.gui.scripting.QPEx.*

var viewer = getCurrentViewer()

var imageData = viewer == null ? null : viewer.getImageData();
if (viewer == null || imageData == null)
    return;

var server = imageData.getServer();
var resolution = server.getPixelCalibration();

int tileSize = 512;

// TODO: Add Javadocs: why is ImageData needed? Looks like it can be null
ImageDataOp featureCalculator = MultiscaleImageDataOpBuilder.create2D(null)
        .build(imageData, resolution);

TrainableModel model = OpenCVStatModels.Models.ANN.createTrainableModel();

PixelClassifierTraining training = new PixelClassifierTraining(featureCalculator);
ClassifierTrainingData trainingData = training.createTrainingData(imageData);

ModelTrainer trainer = new ModelTrainer(
        resolution,
        featureCalculator
);

var trainedModel = trainer.train(
        model,
        trainingData
);

var pixelClassifier = trainedModel.createPixelClassifier(
        tileSize, tileSize, ImageServerMetadata.ChannelType.CLASSIFICATION
);

var overlay = PixelClassificationOverlay.create(
        viewer.getOverlayOptions(), pixelClassifier
);
overlay.setLivePrediction(true);
FXUtils.runOnApplicationThread(() -> viewer.setCustomPixelLayerOverlay(overlay));
