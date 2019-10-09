package qupath.opencv.ml.pixel;

import java.awt.image.BufferedImage;

import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.opencv.ml.OpenCVClassifiers.FeaturePreprocessor;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.ml.OpenCVDNN;
import qupath.opencv.ml.pixel.features.FeatureCalculator;

/**
 * Static methods to help with pixel classification using OpenCV.
 * 
 * @author Pete Bankhead
 */
public class OpenCVPixelClassifiers {
	
	private final static Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifiers.class);

	/**
	 * Create a pixel classifier using an OpenCV DNN Net.
	 * @param net
	 * @param metadata
	 * @param do8Bit if true, convert 32-bit floating point output to 8-bit unsigned int
	 * @return
	 */
	public static PixelClassifier createDNN(OpenCVDNN net, PixelClassifierMetadata metadata, boolean do8Bit) {
		return new OpenCVPixelClassifierDNN(net, metadata, do8Bit);
	}
	

	/**
     * Attempt to read a Net from a single file.
     * Depending on the file extension, this will use the importer for
     * <ul>
     * 	<li>Tensorflow (.pb)</li>
     * 	<li>Caffe (.prototxt)</li>
     * 	<li>Darknet (.cfg)</li>
     * </ul>
     * 
     * @param path Main file from which to load the Net.
     * @return
     */
    public static Net readNet(final String path) {
    	return readNet(path, null);
    }
    
    /**
     * Attempt to read a Net from a single file and optional config file.
     * Depending on the file extension for the first parameter, this will use the importer for
     * <ul>
     * 	<li>Tensorflow (.pb)</li>
     * 	<li>Caffe (.prototxt)</li>
     * 	<li>Darknet (.cfg)</li>
     * </ul>
     * 
     * @param path Main file from which to load the Net.
     * @param config Optional separate file containing weights.
     * @return
     */
    public static Net readNet(final String path, final String config) {
    	if (config == null)
        	logger.info("Reading model from {} (no config file specified)", path);
    	else
    		logger.info("Reading model from {}, with config in {}", path, config);
    	
    	String pathLower = path.toLowerCase();
    	// Try TensorFlow for .pb file
    	if (pathLower.endsWith(".pb")) {
    		if (config == null)
    			return opencv_dnn.readNetFromTensorflow(path);
    		return opencv_dnn.readNetFromTensorflow(path, config);
    	}
    	// Try Caffe for .prototxt file
    	if (pathLower.endsWith(".prototxt")) {
    		if (config == null)
    			return opencv_dnn.readNetFromCaffe(path);
    		return opencv_dnn.readNetFromCaffe(path, config);
    	}
    	// Try Darknet for .cfg file
    	if (pathLower.endsWith(".cfg")) {
    		if (config == null)
    			return opencv_dnn.readNetFromDarknet(path);
    		return opencv_dnn.readNetFromDarknet(path, config);
    	}
    	throw new IllegalArgumentException("Unable to read model from " + path);
    }

    /**
     * Create a PixelClassifier based on an OpenCV StatModel and feature calculator.
     * 
     * @param statModel
     * @param calculator
     * @param preprocessor
     * @param metadata
     * @param do8Bit
     * @return
     */
	public static PixelClassifier createPixelClassifier(OpenCVStatModel statModel, FeatureCalculator<BufferedImage> calculator, FeaturePreprocessor preprocessor, PixelClassifierMetadata metadata, boolean do8Bit) {
		return new OpenCVPixelClassifier(statModel, calculator, preprocessor, metadata, do8Bit);
	}
    
}
