/**
 * Groovy script to demonstrate training a pixel classifier.
 */


import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_ml.ANN_MLP
import qupath.fx.utils.FXUtils
import qupath.lib.gui.viewer.overlays.PixelClassificationOverlay
import qupath.lib.images.servers.ImageServerMetadata
import qupath.lib.images.servers.PixelType
import qupath.opencv.ml.models.TrainableModel
import qupath.opencv.ml.models.statmodel.OpenCVStatModels
import qupath.opencv.ops.ImageDataOp
import qupath.opencv.ml.pixel.train.ClassifierTrainingData
import qupath.opencv.ml.pixel.train.PixelClassifierModelTrainer
import qupath.opencv.ml.pixel.train.PixelClassifierTraining
import qupath.opencv.ops.ImageOps
import qupath.opencv.tools.MultiscaleFeatures

import static qupath.lib.gui.scripting.QPEx.*

var viewer = getCurrentViewer()

var imageData = viewer == null ? null : viewer.getImageData();
if (viewer == null || imageData == null)
    return;

var server = imageData.getServer();
var resolution = server.getPixelCalibration()

int tileSize = 512

// Define the feature extractor
var features = [MultiscaleFeatures.MultiscaleFeature.GAUSSIAN,
                MultiscaleFeatures.MultiscaleFeature.LAPLACIAN,
                MultiscaleFeatures.MultiscaleFeature.HESSIAN_EIGENVALUE_MIN,
                MultiscaleFeatures.MultiscaleFeature.HESSIAN_EIGENVALUE_MAX]

ImageDataOp featureCalculator = ImageOps.buildImageDataOp()
    .appendOps(
            ImageOps.Core.ensureType(PixelType.FLOAT32),
            ImageOps.Core.splitMerge(
                    ImageOps.Filters.features(features, 1.0, 1.0),
                    ImageOps.Filters.features(features, 2.0, 2.0),
                    ImageOps.Filters.features(features, 4.0, 4.0),
            )
    )

// Create the model to train
var modelCV = ANN_MLP.create()
modelCV.setLayerSizes(new Mat(16))
TrainableModel model = OpenCVStatModels.wrapStatModel(modelCV)

// Extract the training data from the current image
PixelClassifierTraining training = new PixelClassifierTraining(featureCalculator)
ClassifierTrainingData trainingData = training.createTrainingData(imageData)

// Create a trainer
PixelClassifierModelTrainer trainer = PixelClassifierModelTrainer.builder(featureCalculator)
            .resolution(resolution)
            .build()

// Train the model
var trainedModel = trainer.train(
        model,
        trainingData
)

// Create the pixel classifier
var pixelClassifier = trainedModel.createPixelClassifier(
        tileSize, tileSize, ImageServerMetadata.ChannelType.CLASSIFICATION
)

// Show as an overlay
var overlay = PixelClassificationOverlay.create(
        viewer.getOverlayOptions(), pixelClassifier
)
overlay.setLivePrediction(true)
FXUtils.runOnApplicationThread(() -> viewer.setCustomPixelLayerOverlay(overlay))

