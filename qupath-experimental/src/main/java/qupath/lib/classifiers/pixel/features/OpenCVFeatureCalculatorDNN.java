package qupath.lib.classifiers.pixel.features;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_dnn;
import org.bytedeco.javacpp.opencv_dnn.Net;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.opencv_imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.processing.OpenCVTools;

public class OpenCVFeatureCalculatorDNN implements OpenCVFeatureCalculator {
	
	private static Logger logger = LoggerFactory.getLogger(OpenCVFeatureCalculatorDNN.class);

    private Net model;
    
    private PixelClassifierMetadata metadata;
    
    private int inputPadding;
    private int stripOutputPadding;

    private String outputLayerName;
    private double scale;
    private Scalar mean;
    private String name;
    
    public OpenCVFeatureCalculatorDNN(
    		final Net model, 
    		final String name, final String outputLayer,
    		final PixelClassifierMetadata metadata, 
    		final int inputPadding, final int stripOutputPadding) {
    	this.model = model;
    	this.name = name;
    	this.metadata = metadata;
    	this.inputPadding = inputPadding;
    	this.stripOutputPadding = stripOutputPadding;
    	this.outputLayerName = outputLayer;
    	
    	// TODO: Handle differing scales per channel?
    	scale = metadata.getInputChannelScale(0);
    	if (scale == 0)
    		scale = 1.0;
    	double [] means = metadata.getInputChannelMeans();
    	if (means.length == 0)
    		mean = Scalar.ZERO;
    	else if (means.length == 1)
    		mean = Scalar.all(means[0]);
    	else if (means.length == 2)
    		mean = new Scalar(means[0], means[1]);
    	else if (means.length == 3)
    		mean = new Scalar(means[0], means[1], means[2], 0);
    	else if (means.length == 4)
    		mean = new Scalar(means[0], means[1], means[2], means[3]);
    	else
    		throw new IllegalArgumentException("Number of channel means must be <= 4");
    }

//    public OpenCVFeatureCalculatorDNN(
//    		Net model, String name, Scalar mean, double scale, 
//    		String outputLayerName, int nOutputChannels, 
//    		int inputPadding, int stripOutputPadding) {
//        this.model = model;
//        this.name = name;
//
//        this.mean = mean;
//        this.scale = scale;
//        
//        this.inputPadding = inputPadding;
//        this.stripOutputPadding = stripOutputPadding;
//        
//        List<PixelClassifierOutputChannel> channels = new ArrayList<>();
//        int color = ColorTools.makeRGB(255, 255, 255);
//        for (int i = 0; i < nOutputChannels; i++) {
//        	channels.add(new PixelClassifierOutputChannel(name + " " + (i+1), color));
//        }
//        
//        this.metadata = new PixelClassifierMetadata.Builder()
//        		.inputShape(inputWidth, inputHeight)
//        		.channels(channels)
//        		.build();
//		
//        if (outputLayerName == null) {
//	        StringVector layerNames = model.getLayerNames();
//	        List<String> names = new ArrayList<>();
//	        for (int i = 0; i < layerNames.size(); i++)
//	            names.add(layerNames.get(i).getString());
//	        this.outputLayerName = names.get(names.size()-2);
//        } else
//        	this.outputLayerName = outputLayerName;
//        logger.info("DNN output name: {}", this.outputLayerName);
//    }

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
    	
    	int requestedWidth = metadata.getInputWidth() + inputPadding * 2;
    	int requestedHeight = metadata.getInputHeight() + inputPadding * 2;
    	if (requestedWidth != input.cols() || requestedHeight != input.rows()) {
    		logger.warn("Input size {}x{} differs from the preferred size {}x{}", input.cols(), input.rows(), requestedWidth, requestedHeight);
    	}

        Mat blob = opencv_dnn.blobFromImage(input, scale, input.size(), mean, true, false);
        Mat prob;
        Mat matResult = null;
        synchronized (model) {
            model.setInput(blob);
            prob = model.forward(outputLayerName);
            
            MatVector matvec = new MatVector();
            opencv_dnn.imagesFromBlob(prob, matvec);
            if (matvec.size() != 1)
            	throw new IllegalArgumentException("DNN result must be a single image - here, the result is " + matvec.size() + " images");
            matResult = matvec.get(0L);
        }
        
        
//        opencv_imgproc.blur(matResult, matResult, new Size(9, 9));
        
        
        // Remove outer padding if necessary
        if (stripOutputPadding > 0) {
            matResult.put(
            		matResult.apply(
            				new opencv_core.Rect(
            						stripOutputPadding, stripOutputPadding,
            						matResult.cols()-stripOutputPadding*2, matResult.rows()-stripOutputPadding*2)).clone());
        }
        
//        // Find out final shape
//        int[] shape = new int[8];
//        IntPointer dnnShape = opencv_dnn.shape(prob);
//        dnnShape.get(shape);
//        int nPlanes = shape[1];
//
//        // Create output
//        List<Mat> matOutput = new ArrayList<>();
//        List<String> featureNames = new ArrayList<>();
//        for (int p = 0; p < nPlanes; p++) {
//            matOutput.add(opencv_dnn.getPlane(prob, 0, p));
//            featureNames.add("Feature " + p);
//        }
//        Mat matResult = new Mat();
//        opencv_core.merge(new MatVector(matOutput.toArray(new Mat[0])), matResult);

        return matResult;
    }

    @Override
    public String toString() {
        return name;
    }

	@Override
	public Mat calculateFeatures(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		BufferedImage img = BasicMultiscaleOpenCVFeatureCalculator.getPaddedRequest(server, request, inputPadding);
		
		Mat mat = OpenCVTools.imageToMat(img);
		
//		int dx = metadata.getInputWidth() - mat.cols();
//		int dy = metadata.getInputHeight() - mat.rows();
//		if (dx > 0 || dy > 0) {
//			Mat mat2 = new Mat();
//			opencv_core.copyMakeBorder(mat, mat2, 0, dy, 0, dx, opencv_core.BORDER_REPLICATE);
//		}
		
		return calculateFeatures(mat);
	}

	@Override
	public PixelClassifierMetadata getMetadata() {
		return metadata;
	}

}