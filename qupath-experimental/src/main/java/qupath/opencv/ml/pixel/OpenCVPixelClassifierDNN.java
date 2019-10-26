package qupath.opencv.ml.pixel;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.global.opencv_dnn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.images.ImageData;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.OpenCVDNN;
import qupath.opencv.tools.OpenCVTools;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pixel classifier that uses a pre-trained model that can be read using OpenCV's DNN module.
 * 
 * @author Pete Bankhead
 */
class OpenCVPixelClassifierDNN extends AbstractOpenCVPixelClassifier {
	
    private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifierDNN.class);

    private OpenCVDNN model;
    private boolean doSoftMax;
    
    OpenCVPixelClassifierDNN(OpenCVDNN model, PixelClassifierMetadata metadata, boolean do8Bit) {
        super(metadata, do8Bit);
        this.model = model;
    }

    @Override
    public boolean supportsImage(ImageData<BufferedImage> imageData) {
    	// TODO: Implement a better check for image compatibility
    	return imageData.getServer().nChannels() == getMetadata().getInputNumChannels();
    }

    private Mat doClassification(Mat mat) throws IOException {
    	// Handle padding, if necessary
        int top = 0, bottom = 0, left = 0, right = 0;
        boolean doPad = false;
        
    	PixelClassifierMetadata metadata = getMetadata();
        // If the image is larger than we can handle, convert it into tiles
    	if (mat.cols() > metadata.getInputWidth()) {
    		List<Mat> horizontal = new ArrayList<>();
    		for (int x = 0; x < mat.cols(); x += metadata.getInputWidth()) {
        		Mat matTemp = doClassification(mat.colRange(x, Math.min(x+metadata.getInputWidth(), mat.cols())).clone());
        		horizontal.add(matTemp);
    		}
    		Mat matResult = new Mat();
    		opencv_core.hconcat(new MatVector(horizontal.toArray(new Mat[0])), matResult);
    		return matResult;
    	} else if (mat.rows() > metadata.getInputHeight()) {
    		List<Mat> vertical = new ArrayList<>();
    		for (int y = 0; y < mat.rows(); y += metadata.getInputHeight()) {
        		Mat matTemp = doClassification(mat.rowRange(y, Math.min(y+metadata.getInputHeight(), mat.rows())).clone());
        		vertical.add(matTemp);
    		}
    		Mat matResult = new Mat();
    		opencv_core.vconcat(new MatVector(vertical.toArray(Mat[]::new)), matResult);
    		return matResult;
    	} else if (mat.cols() < metadata.getInputWidth() || mat.rows() < metadata.getInputHeight()) {
            // If the image is smaller than we can handle, add padding
    		top = (metadata.getInputHeight() - mat.rows()) / 2;
    		left = (metadata.getInputWidth() - mat.cols()) / 2;
    		bottom = metadata.getInputHeight() - mat.rows() - top;
    		right = metadata.getInputWidth() - mat.cols() - left;
    		Mat matPadded = new Mat();
    		opencv_core.copyMakeBorder(mat, matPadded, top, bottom, left, right, opencv_core.BORDER_REFLECT);
    		mat = matPadded;
    		doPad = true;
    	}
    	
    	// Currently we require 32-bit input
    	mat.convertTo(mat, opencv_core.CV_32F);
    	
        // Handle scales & offsets
    	OpenCVDNN.preprocessMat(mat, model);
        
        // Net appears not to support multithreading, so we need to synchronize.
        // We also need to extract the results we need at this point while still within the synchronized block,
    	// since it appears that the result of calling model.forward() can become invalid later.
        Mat matResult = null;
        var net = model.getNet();
        synchronized(net) {
        	long startTime = System.currentTimeMillis();
            Mat blob = null;
            if (mat.channels() == 3)
            	// TODO: Consider creating a multidimensional Mat directly (since this may be limited to 3 channels?)
            	blob = opencv_dnn.blobFromImage(mat, 1.0, null, null, model.doSwapRB(), model.doCrop(), opencv_core.CV_32F);
            else
            	blob = mat;
            net.setInput(blob);
            try {
            	Mat prob = net.forward();
                MatVector matvec = new MatVector();
                opencv_dnn.imagesFromBlob(prob, matvec);
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
        
        // Sometimes, rather unfortunately, dimensions can be wrong (I think... possibly no longer, but check anyway)
        int nChannels = metadata.getClassificationLabels().size();
        if (nChannels == matResult.cols() && nChannels != matResult.channels()) {
        	List<Mat> channels = new ArrayList<>();
        	for (int c = 0; c < matResult.cols(); c++) {
        		Mat matChannel = matResult.col(c).reshape(1, matResult.rows());
        		opencv_core.transpose(matChannel, matChannel);
        		channels.add(matChannel);
        	}
        	opencv_core.merge(new MatVector(channels.toArray(new Mat[0])), matResult);
        }
                
        // Handle padding
        if (doPad) {
        	matResult.put(OpenCVTools.crop(matResult, left, top, metadata.getInputWidth()-right-left, metadata.getInputHeight()-top-bottom));
        }
        return matResult;
    }
    
    
    @Override
    public BufferedImage applyClassification(final ImageData<BufferedImage> imageData, final RegionRequest request) throws IOException {
        // Get the pixels into a friendly format
		var server = imageData.getServer();

		int inputPadding = getMetadata().getInputPadding();
		
		BufferedImage img = PixelClassifierTools.getPaddedRequest(server, request, inputPadding);
		Mat mat = OpenCVTools.imageToMat(img);
		
		// Do the classification
		Mat matResult = doClassification(mat);
		if (inputPadding > 0) {
        	matResult.put(OpenCVTools.crop(matResult, inputPadding, inputPadding, matResult.cols()-inputPadding*2, matResult.rows()-inputPadding*2));
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