package qupath.lib.classifiers.pixel;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.bytedeco.javacpp.opencv_dnn;
import org.bytedeco.javacpp.opencv_dnn.Net;
import org.bytedeco.javacpp.opencv_imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.List;

class OpenCVPixelClassifierDNN extends AbstractOpenCVPixelClassifier {

    private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifier.class);

    private opencv_dnn.Net model;
    private ColorModel colorModel;
    private boolean doSoftMax;

    private Scalar means;
    private Scalar scales;
    private boolean scalesMatch;

    OpenCVPixelClassifierDNN(Net net, PixelClassifierMetadata metadata, boolean do8Bit) {
        super(metadata, do8Bit);

        // TODO: Fix creation of unnecessary objects
        if (metadata.getInputChannelMeans() != null)
            means = toScalar(metadata.getInputChannelMeans());
        else
            means = Scalar.ZERO;
        if (metadata.getInputChannelScales() != null)
            scales = toScalar(metadata.getInputChannelScales());
        else
            scales = Scalar.ONE;

        scalesMatch = true;
        double firstScale = scales.get(0L);
        for (int i = 1; i < metadata.getInputNumChannels(); i++) {
            if (firstScale != scales.get(i)) {
                scalesMatch = false;
                break;
            }
        }

        this.model = net;
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
     * Attempt to read a Net from a single file & optional config file.
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
    	if (config != null)
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
     * Default padding request
     *
     * @return
     */
    public int requestedPadding() {
        return 32;
    }


    protected Mat doClassification(Mat mat, int pad, boolean doSoftmax) {
//        System.err.println("Mean start: " + opencv_core.mean(mat))
        opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_RGB2BGR);
        mat.convertTo(mat, opencv_core.CV_32F, 1.0/255.0, 0.0);
//        mat.put(opencv_core.subtract(Scalar.ONE, mat))
		mat.put(opencv_core.subtract(mat, Scalar.ONEHALF));

//        System.err.println("Mean before: " + opencv_core.mean(mat))

        // Handle scales & offsets
        if (means != null);
            opencv_core.subtractPut(mat, means);
        if (scales != null) {
            if (scalesMatch)
                opencv_core.dividePut(mat, scales.get(0L));
            else {
            	MatVector matvec = new MatVector();
                opencv_core.split(mat, matvec);
                for (int i = 0; i < matvec.size(); i++)
                    opencv_core.multiplyPut(matvec.get(i), scales.get(i));
                opencv_core.merge(matvec, mat);
            }
        }

        Mat prob;
        synchronized(model) {
            Mat blob = opencv_dnn.blobFromImage(mat);
            model.setInput(blob);
            prob = model.forward();
        }
        int nOutputChannels = getMetadata().nOutputChannels();
        List<Mat> matOutput = new ArrayList<>();
        for (int i = 0; i < nOutputChannels; i++) {
            Mat plane = opencv_dnn.getPlane(prob, 0, i);
            matOutput.add(plane);
        }
        MatVector matvec = new MatVector(matOutput.toArray(new Mat[0]));
        Mat matResult = new Mat();
        opencv_core.merge(matvec, matResult);

        // Remove padding, if necessary
//        pad /= 2;
        if (pad > 0) {
            matResult.put(matResult.apply(new opencv_core.Rect(pad, pad, matResult.cols()-pad*2, matResult.rows()-pad*2)).clone());
        }

        return matResult;
    }


	@Override
	protected Mat doClassification(Mat mat, int padding) {
		return doClassification(mat, padding, true);
	}

}