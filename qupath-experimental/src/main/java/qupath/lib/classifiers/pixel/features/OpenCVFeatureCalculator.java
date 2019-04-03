package qupath.lib.classifiers.pixel.features;

import org.bytedeco.javacpp.opencv_core.Mat;

import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(FeatureCalculators.FeatureCalculatorTypeAdapterFactory.class)
public interface OpenCVFeatureCalculator extends FeatureCalculator<Mat> {

}
