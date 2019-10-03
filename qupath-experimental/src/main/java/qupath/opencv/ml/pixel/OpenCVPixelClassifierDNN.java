package qupath.opencv.ml.pixel;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.images.ImageData;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.tools.OpenCVTools;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pixel classifier that uses a pre-trained model that can be read using OpenCV's DNN module.
 * <p>
 * TODO: Test for parallelization. Either reduce OpenCV threads globally or always call prediction from a single thread.
 * 
 * @author Pete Bankhead
 */
class OpenCVPixelClassifierDNN extends AbstractOpenCVPixelClassifier {
	
    private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifierDNN.class);

    private Net model;
    private boolean doSoftMax;
    
    private Scalar means;
    private Scalar scales;
    private boolean scalesMatch;
    
    OpenCVPixelClassifierDNN(Net net, PixelClassifierMetadata metadata, boolean do8Bit) {
        super(metadata, do8Bit);
        
        // TODO: Fix creation of unnecessary objects
//        if (metadata.getInputChannelMeans() != null)
//            means = toScalar(metadata.getInputChannelMeans());
//        else
//            means = Scalar.ZERO;
//        if (metadata.getInputChannelScales() != null)
//            scales = toScalar(metadata.getInputChannelScales());
//        else
//            scales = Scalar.ONE;

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

    @Override
    public boolean supportsImage(ImageData<BufferedImage> imageData) {
    	// TODO: Implement a better check for image compatibility
    	return imageData.getServer().nChannels() == getMetadata().getInputNumChannels();
    }

    protected Mat doClassification(Mat mat, int pad, boolean doSoftmax) {
//        System.err.println("Mean start: " + opencv_core.mean(mat))
    	
    	// Handle padding, if necessary
        int top = 0, bottom = 0, left = 0, right = 0;
        boolean doPad = false;
        
    	PixelClassifierMetadata metadata = getMetadata();
    	// TODO: Check for strict input size
//    	if (metadata.strictInputSize()) {
      	if (true) {
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
        			opencv_core.vconcat(new MatVector(vertical.toArray(Mat[]::new)), matResult);
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
    	
    	mat.convertTo(mat, opencv_core.CV_32F);
    	
        // Handle scales & offsets
        if (means != null);
            opencv_core.subtractPut(mat, means);
        if (scales != null) {
            if (scalesMatch)
                opencv_core.dividePut(mat, scales.get(0L));
            else {
            	MatVector matvec = new MatVector();
                opencv_core.split(mat, matvec);
                for (int i = 0; i < matvec.size(); i++) {
                	if (scales.get(i) == 0)
                		opencv_core.multiplyPut(matvec.get(i), 0.0);
                	else
                		opencv_core.multiplyPut(matvec.get(i), 1.0/scales.get(i));
                }
                opencv_core.merge(matvec, mat);
            }
        }
        
//    	System.err.println("Mean AFTER: " + opencv_core.mean(mat));

        // Net appears not to support multithreading, so we need to synchronize.
        // We also need to extract everything we need at this point, since it appears 
        // that the result of calling model.forward() can become invalid later.
        Mat matResult = null;
        synchronized(model) {
        	long startTime = System.currentTimeMillis();
            Mat blob = null;
            if (mat.channels() == 3)
            	blob = opencv_dnn.blobFromImage(mat, 1.0, null, null, false, false, opencv_core.CV_32F);
            else
            	blob = mat;
            model.setInput(blob);
            try {
            	Mat prob = model.forward();
                MatVector matvec = new MatVector();
                opencv_dnn.imagesFromBlob(prob, matvec);
//                System.err.println(matvec.get(0).channels());
                if (matvec.size() != 1)
                	throw new IllegalArgumentException("DNN result must be a single image - here, the result is " + matvec.size() + " images");
                // Get the first result & clone it - otherwise can have threading woes
                matResult = matvec.get(0L).clone();
                matvec.close();
            } catch (Exception e2) {
            	logger.error("Error applying classifier", e2);
            }
        	long endTime = System.currentTimeMillis();
        	logger.trace("Classification time: {} ms", endTime - startTime);
        }
        
//    	System.err.println("Mean AFTER: " + opencv_core.mean(matResult));

                
        // Sometimes, rather unfortunately, dimensions can be wrong
        int nChannels = metadata.getOutputChannels().size();
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
        	matResult.put(crop(matResult, left, top, metadata.getInputWidth()-right-left, metadata.getInputHeight()-top-bottom));
        }

        // Not sure why exactly we need to clone, but if we don't then catastrophic JVM-destroying errors can occur
        return matResult;//.clone();
    }
    
    
    static Mat crop(Mat mat, int x, int y, int width, int height) {
    	try (Rect rect = new Rect(x, y, width, height)) {
        	var temp = mat.apply(rect);
        	return temp.clone();
    	}
    }


	protected Mat doClassification(Mat mat, int padding) {
		return doClassification(mat, padding, true);
	}

	@Override
    public BufferedImage applyClassification(final ImageData<BufferedImage> imageData, final RegionRequest request) throws IOException {
        // Get the pixels into a friendly format
//        Mat matInput = OpenCVTools.imageToMatRGB(img, false);
		var server = imageData.getServer();
		
		int inputPadding = getMetadata().getInputPadding();
		
//		BufferedImage img = PixelClassifierGUI.getPaddedRequest(server, request, inputPadding);
		BufferedImage img = PixelClassifierTools.getPaddedRequest(server, request, inputPadding);
		Mat mat = OpenCVTools.imageToMat(img);
		
		// Synchronize on the model; does not support multiple threads simultaneously
		Mat matResult = doClassification(mat, 0);
		try {
			if (inputPadding > 0) {
	        	matResult.put(crop(matResult, inputPadding, inputPadding, matResult.cols()-inputPadding*2, matResult.rows()-inputPadding*2));
			}
		} catch (Exception e) {
			logger.error("Error cropping padding (" + Thread.currentThread() + ")", e);
		}
		
    	        
        // If we have a floating point or multi-channel result, we have probabilities
        ColorModel colorModelLocal;
        if (matResult.channels() > 1 || matResult.type() == opencv_core.CV_32F) {
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
        mat.release(); 
        if (matResult != null)
            matResult.release();

        return imgResult;
    }

}