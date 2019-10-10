package qupath.opencv.ml.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.List;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.OpenCVClassifiers.FeaturePreprocessor;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.ml.pixel.features.FeatureCalculator;
import qupath.opencv.ml.pixel.features.PixelFeature;
import qupath.opencv.tools.OpenCVTools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OpenCVPixelClassifier extends AbstractOpenCVPixelClassifier {
	
	private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifier.class);

    private OpenCVStatModel model;
	
    private FeatureCalculator<BufferedImage> calculator;
    
    private FeaturePreprocessor preprocessor;
    
    private OpenCVPixelClassifier() {
    	super(null, false);
    }
    
//    @Override
//	public String toString() {
//		return String.format("Pixel classifier ", model.toString());
//	}
    
//    public OpenCVStatModel getModel() {
//    	return model;
//    }
    
//    public OpenCVPixelClassifier(OpenCVStatModel statModel, OpenCVFeatureCalculator calculator, FeaturePreprocessor preprocessor, PixelClassifierMetadata metadata) {
//    	this(statModel, calculator, preprocessor, metadata, false);
//    }

    OpenCVPixelClassifier(OpenCVStatModel statModel, FeatureCalculator<BufferedImage> calculator, FeaturePreprocessor preprocessor, PixelClassifierMetadata metadata, boolean do8Bit) {
        super(metadata, do8Bit);
        this.model = statModel;
        this.calculator = calculator;
        this.preprocessor = preprocessor;
    }
    
    @Override
    public boolean supportsImage(ImageData<BufferedImage> imageData) {
    	return calculator.supportsImage(imageData);
    }
    
    /**
     * Rescale the rows of matResult so that they sum to maxValue.
     * <p>
     * If matProbabilities has an integer type, then maxValue should normally reflect the largest supported value 
     * (e.g. 255 for CV_8U).  In this case it is not guaranteed that values will sum exactly to the desired maxValue 
     * due to rounding (e.g. consider a row with values [255.0/2, 255.0/2] or [255.0/3, 255.0/3, 255.0/3].
     * 
     * @param matRawInput input values; each row corresponds to a sample and each column the raw estimate for a particular class
     * @param matProbabilities output mat; may be the same as matRawInput and <i>must be preallocated<i>.
     * @param maxValue the maximum value; this would normally be 1.0 for floating point output, or 255.0 for 8-bit output
     * @param doSoftmax if true, {@code Math.exp(value)} will be calculated for each value in matRawInput.
     */
    static void rescaleToEstimatedProbabilities(Mat matRawInput, Mat matProbabilities, double maxValue, boolean doSoftmax) {
    	
    	if (matRawInput != matProbabilities && matRawInput.rows() != matProbabilities.rows() && matRawInput.cols() != matProbabilities.cols()) {
    		if (matProbabilities.empty())
    			matProbabilities.create(matRawInput.rows(), matRawInput.cols(), matRawInput.type());    		
    		else
    			matProbabilities.create(matRawInput.rows(), matRawInput.cols(), matProbabilities.type());    		
    	}
    	
    	int warnNegativeValues = 0;
        var idxInput = matRawInput.createIndexer();
        var idxOutput = matProbabilities.createIndexer();
        long[] inds = new long[2];
        long rows = idxInput.rows();
        long cols = idxOutput.cols();
        double[] vals = new double[(int)cols];
        for (long r = 0; r < rows; r++) {
        	inds[0] = r;
        	double sum = 0;
        	for (int k = 0; k < cols; k++) {
            	inds[1] = k;
            	double val = idxInput.getDouble(inds);
            	if (doSoftmax) {
            		val = Math.exp(val);
            	} else if (val < 0) {
            		val = 0;
            		warnNegativeValues++;
            	}
            	vals[k] = val;
            	sum += val;
        	}
        	
        	for (int k = 0; k < cols; k++) {
        		inds[1] = k;
        		idxOutput.putDouble(inds, vals[k] * (maxValue / sum));
        	}
        	// Consider if the output should be integer, could set the highest probability to be 1 - the maximum
        	// The aim is to avoid rounding errors to result in the sum not adding up to what is expected 
        	// (e.g. 255/3 + 255/3 + 255/3).
        	// But as this example shows, it can result in a different interpretation of the results...
        }
        
        if (warnNegativeValues > 0) {
        	long total = rows * cols;
        	logger.warn(
        			String.format("Negative raw 'probability' values detected (%d/%d, %.1f%%) - " +
        					" - these will be clipped to 0.  Should softmax be being used...?", warnNegativeValues, total, warnNegativeValues*(100.0/total)));
        }
    }

    
//    synchronized ImageServer<BufferedImage> getFeatureServer() {
//    	if (featureServer == null) {
//    		featureServer = new FeatureImageServer(imageData, calculator, resolution)
//    	}
//    	return featureServer;
//    }
    
    
    @Override
    public BufferedImage applyClassification(final ImageData<BufferedImage> imageData, final RegionRequest request) throws IOException {
        // Get the pixels into a friendly format
//        Mat matInput = OpenCVTools.imageToMatRGB(img, false);
//    	BufferedImage imgFeatures = calculator.readBufferedImage(request);
    	List<PixelFeature> features = calculator.calculateFeatures(imageData, request);
    	
//    	PixelClassifierMetadata metadata = getMetadata();
//        normalizeFeatures(matFeatures, metadata.getInputChannelMeans(), metadata.getInputChannelScales());

    	if (features.isEmpty())
    		return null;
    	
        int widthFeatures = features.get(0).getFeature().getWidth();
        int heightFeatures = features.get(0).getFeature().getHeight();
        int n = widthFeatures * heightFeatures;

        // Extract features into a suitable format
//        long startTime = System.currentTimeMillis();
        
        // It's faster to put in row-wise and then transpose
        int nBands = features.size();
        Mat matFeatures = new Mat(nBands, n, opencv_core.CV_32FC1);
        FloatIndexer idx = matFeatures.createIndexer();
        int col = 0;
        for (int b = 0; b < nBands; b++) {
        	var feature = features.get(b);
        	float[] temp = SimpleImages.getPixels(feature.getFeature(), true);
        	idx.put(col, 0, temp);
        	col++;
        }
        idx.release();
        matFeatures.put(matFeatures.t());
        
//        int nBands = imgFeatures.getSampleModel().getNumBands();
//        Mat matFeatures = new Mat(widthFeatures*heightFeatures, nBands, opencv_core.CV_32FC1);
//        FloatIndexer idx = matFeatures.createIndexer();
//        float[] temp = null;
//        int col = 0;
//        for (int b = 0; b < nBands; b++) {
//        	temp = imgFeatures.getRaster().getSamples(0, 0, widthFeatures, heightFeatures, b, temp);
//        	for (int row = 0; row < n; row++)
//        		idx.put(row, col, temp[row]);
//        	col++;
//        }
//        idx.release();

//        long endTime = System.currentTimeMillis();
//        System.err.println("Transfer: " + (endTime - startTime));
        
        
        // Do preprocessing (e.g. PCA, normalization)
    	if (preprocessor != null)
    		preprocessor.apply(matFeatures);
    	

    	// Calculate predictions/probabilities
    	var matOutput = new Mat();
    	var type = getMetadata().getOutputType();
    	if (type == ImageServerMetadata.ChannelType.CLASSIFICATION) {
        	model.predict(matFeatures, matOutput, null);    		
    	} else {
    		var matTemp = new Mat();
        	model.predict(matFeatures, matTemp, matOutput);
        	matTemp.release();
    	}
    	
    	
    	ColorModel colorModelLocal = null;
    	
    	if (type == ImageServerMetadata.ChannelType.PROBABILITY) {
    		var matProbabilities = matOutput;
    		double maxValue = 1.0;
    		if (do8Bit()) {
    			matProbabilities = new Mat(matOutput.rows(), matOutput.cols(), opencv_core.CV_8UC(matOutput.channels()));
    			maxValue = 255.0;
    		}
    		rescaleToEstimatedProbabilities(matOutput, matProbabilities, maxValue, doSoftmax());
    		if (do8Bit()) {
    			matOutput.release();
    			matOutput = matProbabilities;
    		}
    		colorModelLocal = getProbabilityColorModel();
    	} else if (type == ImageServerMetadata.ChannelType.CLASSIFICATION) {
    		matOutput.convertTo(matOutput, opencv_core.CV_8U);
    		colorModelLocal = getClassificationsColorModel();
    	}
    	
    	
        // Reshape output
        Mat matResult = matOutput.reshape(matOutput.cols(), heightFeatures);
        matOutput.release();

        // Create & return BufferedImage
        BufferedImage imgResult = OpenCVTools.matToBufferedImage(matResult, colorModelLocal);

        // Free matrix
        if (matResult != null)
            matResult.release();

        return imgResult;
    }
    
}