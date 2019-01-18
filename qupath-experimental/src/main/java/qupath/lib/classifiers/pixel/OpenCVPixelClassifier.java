package qupath.lib.classifiers.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Scalar;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.classifiers.opencv.OpenCVClassifiers.FeaturePreprocessor;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.OpenCVStatModel;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata.OutputType;
import qupath.lib.classifiers.pixel.features.OpenCVFeatureCalculator;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.processing.OpenCVTools;
import qupath.opencv.processing.TypeAdaptersCV;

import org.bytedeco.javacpp.indexer.Indexer;

public class OpenCVPixelClassifier extends AbstractOpenCVPixelClassifier {

	@JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
    private OpenCVStatModel model;
	
	@JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
    private OpenCVFeatureCalculator calculator;
	
    private FeaturePreprocessor preprocessor;
    
    public OpenCVPixelClassifier(OpenCVStatModel statModel, OpenCVFeatureCalculator calculator, FeaturePreprocessor preprocessor, PixelClassifierMetadata metadata) {
    	this(statModel, calculator, preprocessor, metadata, false);
    }

    public OpenCVPixelClassifier(OpenCVStatModel statModel, OpenCVFeatureCalculator calculator, FeaturePreprocessor preprocessor, PixelClassifierMetadata metadata, boolean do8Bit) {
        super(metadata, do8Bit);
        this.model = statModel;
        this.calculator = calculator;
        this.preprocessor = preprocessor;
    }

    void normalizeFeatures(Mat mat, Mat matMean, Mat matStdDev) {
    	double[] means = toDoubleArray(matMean);
        double[] scales = toDoubleArray(matStdDev);
        normalizeFeatures(mat, means, scales);
    }


    public static double[] toDoubleArray(Mat mat) {
        Indexer indexer = mat.createIndexer();
        double[] results = new double[(int)mat.total()];
        boolean colArray = mat.rows() == 1;
        boolean rowArray = mat.cols() == 1;
        if (!colArray && !rowArray)
            throw new IllegalArgumentException("Mat is neither a row nor a column array!");
        for (int i = 0; i < mat.total(); i++) {
            if (rowArray)
                results[i] = indexer.getDouble(i, 0);
            else
                results[i] = indexer.getDouble(0, i);
        }
        indexer.release();
        return results;
    }
    
    
    public static void normalizeFeatures(Mat mat, double[] means, double[] scales) {
        if (means == null && scales == null)
            return;

        MatVector matvec = new MatVector();
        opencv_core.split(mat, matvec);

        for (int c = 0; c < matvec.size(); c++) {
            if (means != null) {
                // TODO: Cache scalars!
                if (means.length == 1)
                    opencv_core.subtractPut(matvec.get(c), Scalar.all(means[0]));
                else
                    opencv_core.subtractPut(matvec.get(c), Scalar.all(means[c]));
            }
            if (scales != null) {
                if (scales.length == 1)
                    opencv_core.dividePut(matvec.get(c), scales[0]);
                else
                    opencv_core.dividePut(matvec.get(c), scales[c]);
            }
        }
        // Might not need to merge...?
        opencv_core.merge(matvec, mat);
    }
    
    
    @Override
    public BufferedImage applyClassification(final ImageServer<BufferedImage> server, final RegionRequest request) throws IOException {
        // Get the pixels into a friendly format
//        Mat matInput = OpenCVTools.imageToMatRGB(img, false);
    	
    	Mat matFeatures = calculator.calculateFeatures(server, request);
    	
//    	PixelClassifierMetadata metadata = getMetadata();
//        normalizeFeatures(matFeatures, metadata.getInputChannelMeans(), metadata.getInputChannelScales());

        int heightFeatures = matFeatures.rows();

        // Get probabilities
        Mat matOutput = new Mat();
        matFeatures = matFeatures.reshape(1, matFeatures.rows()*matFeatures.cols());
        
    	if (preprocessor != null)
    		preprocessor.apply(matFeatures);

        
//        synchronized (model) {
    	var type = getMetadata().getOutputType();
    	if (type == OutputType.Classification) {
        	model.predict(matFeatures, matOutput, null);    		
    	} else {
    		var matTemp = new Mat();
        	model.predict(matFeatures, matTemp, matOutput);
        	matTemp.release();
    	}
//        }
        
        // Reshape output
        Mat matResult = matOutput.reshape(matOutput.cols(), heightFeatures);
        
        // If we have a floating point or multi-channel result, we have probabilities
        ColorModel colorModelLocal;
        if (type == OutputType.Classification) {
        	matResult.convertTo(matResult, opencv_core.CV_8U);
            colorModelLocal = getClassificationsColorModel();
        } else {
        	// Do softmax if needed
            if (doSoftMax())
                applySoftmax(matResult);

            // Convert to 8-bit if needed
            if (do8Bit())
                matResult.convertTo(matResult, opencv_core.CV_8U, 255.0, 0.0);        	
            colorModelLocal = getProbabilityColorModel();
        }

        // Create & return BufferedImage
        BufferedImage imgResult = OpenCVTools.matToBufferedImage(matResult, colorModelLocal);

        // Free matrix
        if (matResult != null)
            matResult.release();

        return imgResult;
    }
    
}