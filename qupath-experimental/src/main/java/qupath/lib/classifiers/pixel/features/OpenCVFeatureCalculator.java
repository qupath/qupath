package qupath.lib.classifiers.pixel.features;

import org.bytedeco.javacpp.opencv_core.Mat;

import java.util.List;

public interface OpenCVFeatureCalculator {

    public Mat calculateFeatures(Mat input);

    public int requestedPadding();

    public List<String> getLastFeatureNames();

}