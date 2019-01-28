package qupath.lib.classifiers.pixel;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Rect;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.bytedeco.javacpp.opencv_dnn;
import org.bytedeco.javacpp.opencv_dnn.Net;
import org.bytedeco.javacpp.opencv_imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.processing.OpenCVTools;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class OpenCVPixelClassifierDNN extends AbstractOpenCVPixelClassifier {

    private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifier.class);

    private opencv_dnn.Net model;
    private ColorModel colorModel;
    private boolean doSoftMax;
    
    private int inputPadding;
    private int stripOutputPadding;

    private Scalar means;
    private Scalar scales;
    private boolean scalesMatch;
    
    public static PixelClassifier createDNN(Net net, PixelClassifierMetadata metadata, boolean do8Bit, int inputPadding, int stripOutputPadding) {
    	return new OpenCVPixelClassifierDNN(net, metadata, do8Bit, inputPadding, stripOutputPadding);
    }
    
    OpenCVPixelClassifierDNN(Net net, PixelClassifierMetadata metadata, boolean do8Bit) {
    	this(net, metadata, do8Bit, 0, 0);
    }

    OpenCVPixelClassifierDNN(Net net, PixelClassifierMetadata metadata, boolean do8Bit, int inputPadding, int stripOutputPadding) {
        super(metadata, do8Bit);
        
        this.inputPadding = inputPadding;
        this.stripOutputPadding = stripOutputPadding;

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
     * Default padding request
     *
     * @return
     */
    public int requestedPadding() {
        return stripOutputPadding;
    }


    protected Mat doClassification(Mat mat, int pad, boolean doSoftmax) {
//        System.err.println("Mean start: " + opencv_core.mean(mat))
    	
    	// Handle padding, if necessary
        int top = 0, bottom = 0, left = 0, right = 0;
        boolean doPad = false;
        
    	PixelClassifierMetadata metadata = getMetadata();
    	if (metadata.strictInputSize()) {
        	if (mat.cols() > metadata.getInputWidth()) {
        		List<Mat> horizontal = new ArrayList<>();
        		for (int x = 0; x < mat.cols(); x += metadata.getInputWidth()) {
            		Mat matTemp = doClassification(mat.colRange(x, Math.min(x+metadata.getInputWidth(), mat.cols())).clone(), pad, doSoftmax);
            		horizontal.add(matTemp);
        		}
        		Mat matResult = new Mat();
        		opencv_core.hconcat(new MatVector(horizontal.toArray(new Mat[0])), matResult);
        		return matResult;
        	} else if (mat.rows() > metadata.getInputHeight()) {
        		List<Mat> vertical = new ArrayList<>();
        		for (int y = 0; y < mat.rows(); y += metadata.getInputHeight()) {
            		Mat matTemp = doClassification(mat.rowRange(y, Math.min(y+metadata.getInputHeight(), mat.rows())).clone(), pad, doSoftmax);
            		vertical.add(matTemp);
        		}
        		Mat matResult = new Mat();
//        		try {
        			opencv_core.vconcat(new MatVector(vertical.toArray(new Mat[0])), matResult);
//        		} catch (Exception e) {
//        			System.err.println(vertical);
//        			e.printStackTrace();
//        		}
        		return matResult;
        	} else if (mat.cols() < metadata.getInputWidth() || mat.rows() < metadata.getInputHeight()) {
        		top = (metadata.getInputHeight() - mat.rows()) / 2;
        		left = (metadata.getInputWidth() - mat.cols()) / 2;
        		bottom = metadata.getInputHeight() - mat.rows() - top;
        		right = metadata.getInputWidth() - mat.cols() - left;
        		Mat matPadded = new Mat();
        		opencv_core.copyMakeBorder(mat, matPadded, top, bottom, left, right, opencv_core.BORDER_REFLECT);
        		mat = matPadded;
        		doPad = true;
        	}
    	}
    	
//    	opencv_core.extractChannel(mat, mat, 0);
//    	mat.convertTo(mat, opencv_core.CV_32F);
    	
    	opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_RGB2BGR);
    	
//        opencv_imgproc.cvtColor(mat, mat, opencv_imgproc.COLOR_RGB2BGR);
    	mat.convertTo(mat, opencv_core.CV_32F);
//    	System.err.println("Mean: " + opencv_core.mean(mat));
//        mat.convertTo(mat, opencv_core.CV_32F, 1.0/255.0, 0.0);
////        mat.put(opencv_core.subtract(Scalar.ONE, mat))
//		mat.put(opencv_core.subtract(mat, Scalar.ONEHALF));

//        System.err.println("Mean before: " + opencv_core.mean(mat))

    	
    	// TODO: Fix creation of unnecessary objects
        if (metadata.getInputChannelMeans() != null)
            means = toScalar(metadata.getInputChannelMeans());
        else
            means = Scalar.ZERO;
        if (metadata.getInputChannelScales() != null)
            scales = toScalar(metadata.getInputChannelScales());
        else
            scales = Scalar.ONE;
    	
    	
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
        
//    	System.err.println("Mean AFTER: " + opencv_core.mean(mat));

        Mat prob = null;
        synchronized(model) {
        	long startTime = System.currentTimeMillis();
            Mat blob = opencv_dnn.blobFromImage(mat, 1.0, null, null, true, false, opencv_core.CV_32F);
            model.setInput(blob);
            try {
            	prob = model.forward();
            } catch (Exception e2) {
            	logger.error("Error applying classifier", e2);
            }
        	long endTime = System.currentTimeMillis();
//        	System.err.println("Classification time: " + (endTime - startTime) + " ms");
        }
        
        MatVector matvec = new MatVector();
        opencv_dnn.imagesFromBlob(prob, matvec);
//        System.err.println(matvec.get(0).channels());
        if (matvec.size() != 1)
        	throw new IllegalArgumentException("DNN result must be a single image - here, the result is " + matvec.size() + " images");
        Mat matResult = matvec.get(0L);
                
        // Sometimes, rather unfortunately, dimensions can be wrong
        int nChannels = metadata.getChannels().size();
        if (nChannels == matResult.cols() && nChannels != matResult.channels()) {
        	List<Mat> channels = new ArrayList<>();
        	for (int c = 0; c < matResult.cols(); c++) {
        		Mat matChannel = matResult.col(c).reshape(1, matResult.rows());
        		opencv_core.transpose(matChannel, matChannel);
//        		opencv_core.rotate(matChannel, matChannel, opencv_core.ROTATE_180);
        		channels.add(matChannel);
        	}
        	opencv_core.merge(new MatVector(channels.toArray(new Mat[0])), matResult);
        }
                
        // Handle padding
        if (doPad) {
        	Rect rect = new Rect(left, top, metadata.getInputWidth()-right-left, metadata.getInputHeight()-top-bottom);
        	Mat matResult2 = matResult.apply(rect).clone();
        	matResult.release();
        	matResult = matResult2;
        }
        
////        matResult = matResult.reshape(4, 128);
//        MatVector channels = new MatVector();
//        opencv_core.split(matResult, channels);
//        Mat matSum = channels.get(0).clone();
////        System.err.println("Channels: " + channels.size());
////        System.err.println(matSum);
//        for (int c = 1; c < channels.size(); c++)
//        	opencv_core.addPut(matSum, channels.get(0));
////        System.err.println("Mean afterwards: " + opencv_core.mean(matSum));
//        matSum.release();
        
//        int nOutputChannels = metadata.nOutputChannels();
//        List<Mat> matOutput = new ArrayList<>();
//        for (int i = 0; i < nOutputChannels; i++) {
//            Mat plane = opencv_dnn.getPlane(prob, 0, i);
//            matOutput.add(plane);
//        }
//        MatVector matvec = new MatVector(matOutput.toArray(new Mat[0]));
//        Mat matResult = new Mat();
//        opencv_core.merge(matvec, matResult);

        // Remove padding, if necessary
//        pad /= 2;
        if (stripOutputPadding > 0) {
            matResult.put(
            		matResult.apply(
            				new opencv_core.Rect(
            						stripOutputPadding, stripOutputPadding,
            						matResult.cols()-stripOutputPadding*2, matResult.rows()-stripOutputPadding*2)).clone());
        }

        return matResult;
    }


	protected Mat doClassification(Mat mat, int padding) {
		return doClassification(mat, padding, true);
	}

	@Override
    public BufferedImage applyClassification(final ImageServer<BufferedImage> server, final RegionRequest request) throws IOException {
        // Get the pixels into a friendly format
//        Mat matInput = OpenCVTools.imageToMatRGB(img, false);
		
		Mat mat = OpenCVTools.imageToMat(server.readBufferedImage(request));
		Mat matResult = doClassification(mat, inputPadding);
    	        
        // If we have a floating point or multi-channel result, we have probabilities
        ColorModel colorModelLocal;
        if (matResult.channels() > 1) {
        	// Do softmax if needed
            if (doSoftMax)
                applySoftmax(matResult);

            // Convert to 8-bit if needed
            if (do8Bit())
                matResult.convertTo(matResult, opencv_core.CV_8U, 255.0, 0.0);        	
            colorModelLocal = getProbabilityColorModel();
        } else {
            matResult.convertTo(matResult, opencv_core.CV_8U);
            colorModelLocal = getClassificationsColorModel();
        }

        // Create & return BufferedImage
        BufferedImage imgResult = OpenCVTools.matToBufferedImage(matResult, colorModelLocal);

        // Free matrix
        if (matResult != null)
            matResult.release();

        return imgResult;
    }

}