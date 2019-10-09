package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.images.ImageData;
import qupath.lib.io.OpenCVTypeAdapters;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.OpenCVDNN;
import qupath.opencv.tools.OpenCVTools;

/**
 * Use a DNN Net to calculate features.
 * 
 * @author Pete Bankhead
 *
 */
@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
class OpenCVFeatureCalculatorDNN implements FeatureCalculator<BufferedImage> {
	
//	static {
//		FeatureCalculators.FeatureCalculatorTypeAdapterFactory.registerSubtype(OpenCVFeatureCalculatorDNN.class);
//	}
	
	private static Logger logger = LoggerFactory.getLogger(OpenCVFeatureCalculatorDNN.class);

    private OpenCVDNN model;
    private ImmutableDimension inputShape;
    
    // TODO: Consider reading input dimensions from model?
    OpenCVFeatureCalculatorDNN(
    		final OpenCVDNN model, 
    		final int inputWidth, int inputHeight) {
    	
    	this.model = model;
    	this.inputShape = ImmutableDimension.getInstance(inputWidth, inputHeight);
    }
    
    @Override
	public boolean supportsImage(ImageData<BufferedImage> imageData) {
    	// TODO: Check actual input dimensions
    	return imageData.getServer().isRGB();
    }

    private Mat calculateFeatures(Mat input) throws IOException {
    	
    	OpenCVDNN.preprocessMat(input, model);
    	Mat blob = opencv_dnn.blobFromImage(input, 1.0, null, null, model.doSwapRB(), model.doCrop(), opencv_core.CV_32F);

//        Mat blob = opencv_dnn.blobFromImage(input, scale, input.size(), mean, true, false, opencv_core.CV_32F);
        Mat prob;
        Mat matResult = null;
    	Net net = model.getNet();
        synchronized (net) {
        	net.setInput(blob);
            try {
            	String outputLayerName = model.getOutputLayerName();
            	if (outputLayerName == null)
            		prob = net.forward();
            	else
            		prob = net.forward(outputLayerName);
            	
                MatVector matvec = new MatVector();
                opencv_dnn.imagesFromBlob(prob, matvec);
                if (matvec.size() != 1)
                	throw new IllegalArgumentException("DNN result must be a single image - here, the result is " + matvec.size() + " images");
                matResult = matvec.get(0L).clone();
            } catch (Exception e) {
            	throw new IOException(e);
            }
            
            // TODO: Note that there is a maximum number of supported channels; otherwise we need to extract values some other way
//            FloatIndexer indexer = prob.createIndexer();
//            indexer.release();
                        
            if (matResult.rows() != input.rows() || matResult.cols() != input.cols())
            	opencv_imgproc.resize(matResult, matResult, input.size());

        }

        return matResult;
    }

    @Override
    public String toString() {
        return "Feature calculator: " + model.toString();
    }

	@Override
	public List<PixelFeature> calculateFeatures(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
		int padding = 0;//getMetadata().getInputPadding(); // TODO: Check necessity of padding
		BufferedImage img = PixelClassifierTools.getPaddedRequest(imageData.getServer(), request, padding);
		
		Mat mat = OpenCVTools.imageToMat(img);
		
		// Add padding if necessary
		int xPad = 0;
		int yPad = 0;
//		if (metadata.strictInputSize()) {
			xPad = inputShape.width + padding * 2 - mat.cols();
			yPad = inputShape.height + padding * 2 - mat.rows();
//		}
		if (xPad > 0 || yPad > 0) {
			opencv_core.copyMakeBorder(mat, mat, 0, yPad, 0, xPad, opencv_core.BORDER_REFLECT);
		}
		
		Mat matResult = calculateFeatures(mat);
		mat.release();
		
        // Remove outer padding if necessary
        if (padding > 0 || xPad > 0 || yPad > 0) {
        	int width = matResult.cols();
        	int height = matResult.rows();
        	try {
        		Mat matTemp = matResult.rowRange(padding, height-padding-yPad).colRange(padding, width-padding-xPad).clone();
//        		Mat matTemp = matResult.apply(new opencv_core.Rect(padding, padding, width-padding*2-xPad, height-padding*2-yPad)).clone();
	        	matResult.release();
	        	matResult = matTemp;
        	} catch (Exception e) {
        		logger.error(
        				String.format("Error cropped Mat %d x %d with rectangle (%d, %d, %d, %d)",
        						width, height, padding, padding, width-padding*2, height-padding*2),
        				e);
        	} finally {
//        		if (rect != null)
//        			rect.close();        		
        	}
        }
        
        MatVector output = new MatVector();
        opencv_core.split(matResult, output);
        List<PixelFeature> features = new ArrayList<>();
        for (int i = 0; i < output.size(); i++) {
        	var temp = output.get(i);
        	features.add(new DefaultPixelFeature<>(
        			"Feature " + i,
        			OpenCVTools.matToSimpleImage(temp, 0)));
        }
        output.close();

        return features;
	}
	
	@Override
	public ImmutableDimension getInputSize() {
		return inputShape;
	}

}