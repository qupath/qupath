package qupath.lib.classifiers

import org.bytedeco.javacpp.opencv_core.Mat

import java.awt.image.BufferedImage

public interface OpenCVFeatureCalculator {

    public Mat calculateFeatures(Mat input);

    public int requestedPadding();

}