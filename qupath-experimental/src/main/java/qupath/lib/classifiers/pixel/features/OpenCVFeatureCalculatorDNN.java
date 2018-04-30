package qupath.lib.classifiers.pixel.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.bytedeco.javacpp.opencv_core.StringVector;
import org.bytedeco.javacpp.opencv_dnn;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.opencv_dnn.Net;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCVFeatureCalculatorDNN implements OpenCVFeatureCalculator {
	
	private static Logger logger = LoggerFactory.getLogger(OpenCVFeatureCalculatorDNN.class);

    private Net model;
    private int padding = 32; // Default
    private String outputName;
    private List<String> featureNames;
    private double scale;
    private Scalar mean;
    private String name;
    
    public OpenCVFeatureCalculatorDNN(Net model, String name, double mean, double scale) {
    	this(model, name, Scalar.all(mean), scale);
    }

    public OpenCVFeatureCalculatorDNN(Net model, String name, Scalar mean, double scale) {
        this.model = model;
        this.name = name;

        this.mean = mean;
        this.scale = scale;
		
        StringVector layerNames = model.getLayerNames();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < layerNames.size(); i++)
            names.add(layerNames.get(i).getString());
        outputName = names.get(names.size()-2);
        logger.info("DNN output name: {}", outputName);
    }

    @Override
    public Mat calculateFeatures(Mat input) {
//    	Mat mat = new Mat();
//    	opencv_imgproc.cvtColor(input, mat, opencv_imgproc.COLOR_RGB2BGR);
//    	mat.convertTo(mat, opencv_core.CV_32F, 1.0/255.0, 0.0);
////        mat.put(opencv_core.subtract(opencv_core.Scalar.ONE, mat));
//        mat.put(opencv_core.subtract(mat, opencv_core.Scalar.ONEHALF));
//
//        // Handle scales & offsets
//        if (mean != null && !Scalar.ZERO.equals(mean))
//            opencv_core.subtractPut(mat, mean);
//        if (scale != 1) {
//            opencv_core.dividePut(mat, scale);
//        }

        Mat blob = opencv_dnn.blobFromImage(input, scale, input.size(), mean, true, false);
        Mat prob;
        synchronized (model) {
            model.setInput(blob);
            prob = model.forward(outputName);
        }

        // Find out final shape
        int[] shape = new int[8];
        IntPointer dnnShape = opencv_dnn.shape(prob);
        dnnShape.get(shape);
        int nPlanes = shape[1];

        // Create output
        List<Mat> matOutput = new ArrayList<>();
        List<String> featureNames = new ArrayList<>();
        for (int p = 0; p < nPlanes; p++) {
            matOutput.add(opencv_dnn.getPlane(prob, 0, p));
            featureNames.add("Feature " + p);
        }
        Mat matResult = new Mat();
        opencv_core.merge(new MatVector(matOutput.toArray(new Mat[0])), matResult);

        this.featureNames = Collections.unmodifiableList(featureNames);

        return matResult;
    }

    @Override
    public int requestedPadding() {
        return padding;
    }

    @Override
	public List<String> getLastFeatureNames() {
        return featureNames;
    }

    @Override
    public String toString() {
        return name;
    }

}