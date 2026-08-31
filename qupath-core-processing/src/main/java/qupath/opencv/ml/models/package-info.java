/**
 * Classes and interfaces used to define low-level models that are trained and used
 * within pixel and object classifiers.
 * <p>
 * This was introduced in QuPath v0.8.0 to enable more kinds of model to be supported.
 * Previously, trainable pixel and object classifiers always used OpenCV's {@code StatModel}.
 * <p>
 * Although we continue to use OpenCV {@code Mat} and {@code TrainData} as inputs
 * (which is why the classes are located in the {@code qupath.opencv} package), the changes introduced in v0.8.0
 * allow different model implementations to be used.
 */
package qupath.opencv.ml.models;