package qupath.lib.classifiers.opencv.pixel.features;

import java.awt.image.BufferedImage;

import org.bytedeco.opencv.opencv_core.Mat;

import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(FeatureCalculators.FeatureCalculatorTypeAdapterFactory.class)
public interface OpenCVFeatureCalculator extends FeatureCalculator<BufferedImage, Mat> {

}
