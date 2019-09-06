package qupath.opencv.ml.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.List;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;

import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.classifiers.pixel.PixelClassifiers;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.OpenCVClassifiers.FeaturePreprocessor;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.ml.pixel.features.Feature;
import qupath.opencv.ml.pixel.features.OpenCVFeatureCalculator;
import qupath.opencv.tools.OpenCVTools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(PixelClassifiers.PixelClassifierTypeAdapterFactory.class)
class OpenCVPixelClassifier extends AbstractOpenCVPixelClassifier {
	
	private static final Logger logger = LoggerFactory.getLogger(OpenCVPixelClassifier.class);

    private OpenCVStatModel model;
	
    private OpenCVFeatureCalculator calculator;
	
    private FeaturePreprocessor preprocessor;
    
    private OpenCVPixelClassifier() {
    	super(null, false);
    }
    
//    public OpenCVStatModel getModel() {
//    	return model;
//    }
    
//    public OpenCVPixelClassifier(OpenCVStatModel statModel, OpenCVFeatureCalculator calculator, FeaturePreprocessor preprocessor, PixelClassifierMetadata metadata) {
//    	this(statModel, calculator, preprocessor, metadata, false);
//    }

    OpenCVPixelClassifier(OpenCVStatModel statModel, OpenCVFeatureCalculator calculator, FeaturePreprocessor preprocessor, PixelClassifierMetadata metadata, boolean do8Bit) {
        super(metadata, do8Bit);
        this.model = statModel;
        this.calculator = calculator;
        this.preprocessor = preprocessor;
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

    
    
    
    
    @Override
    public BufferedImage applyClassification(final ImageData<BufferedImage> imageData, final RegionRequest request) throws IOException {
        // Get the pixels into a friendly format
//        Mat matInput = OpenCVTools.imageToMatRGB(img, false);
    	
    	List<Feature<Mat>> features = calculator.calculateFeatures(imageData, request);
    	
//    	PixelClassifierMetadata metadata = getMetadata();
//        normalizeFeatures(matFeatures, metadata.getInputChannelMeans(), metadata.getInputChannelScales());

    	Mat firstFeature = features.get(0).getFeature();
        int heightFeatures = firstFeature.rows();

        // Get probabilities
        Mat matOutput = new Mat();
        Mat[] columns = new Mat[features.size()];
        int i = 0;
        for (Feature<Mat> feature : features) {
        	Mat temp = feature.getFeature();
        	columns[i++] = temp.reshape(1, (int)temp.total());
        }
        Mat matFeatures = new Mat();
    	opencv_core.hconcat(new MatVector(columns), matFeatures);
    	if (matFeatures.channels() > 1)
    		matFeatures.put(matFeatures.reshape(1, matFeatures.rows()*matFeatures.channels()));
        
    	if (preprocessor != null)
    		preprocessor.apply(matFeatures);

        
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