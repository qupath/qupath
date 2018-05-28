package qupath.lib.classifiers.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_core.Scalar;

import qupath.lib.classifiers.pixel.features.OpenCVFeatureCalculator;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.processing.OpenCVTools;

import org.bytedeco.javacpp.opencv_ml;
import org.bytedeco.javacpp.opencv_ml.StatModel;
import org.bytedeco.javacpp.indexer.Indexer;

public class OpenCVPixelClassifier extends AbstractOpenCVPixelClassifier {

    private StatModel model;
    private OpenCVFeatureCalculator calculator;
    
    public OpenCVPixelClassifier(StatModel statModel, OpenCVFeatureCalculator calculator, PixelClassifierMetadata metadata) {
    	this(statModel, calculator, metadata, false);
    }

    public OpenCVPixelClassifier(StatModel statModel, OpenCVFeatureCalculator calculator, PixelClassifierMetadata metadata, boolean do8Bit) {
        super(metadata, do8Bit);
        this.model = statModel;
        this.calculator = calculator;
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
    public BufferedImage applyClassification(final ImageServer<BufferedImage> server, final RegionRequest request) {
        // Get the pixels into a friendly format
//        Mat matInput = OpenCVTools.imageToMatRGB(img, false);
    	
    	Mat matFeatures = calculator.calculateFeatures(server, request);
    	
    	
    	PixelClassifierMetadata metadata = getMetadata();
        normalizeFeatures(matFeatures, metadata.getInputChannelMeans(), metadata.getInputChannelScales());

        int heightFeatures = matFeatures.rows();

        // Get probabilities
        Mat matOutput = new Mat();
        matFeatures = matFeatures.reshape(1, matFeatures.rows()*matFeatures.cols());
        synchronized (model) {
            model.predict(matFeatures, matOutput, 0);
//            model.predict(matFeatures, matOutput, StatModel.RAW_OUTPUT);
        }
        
        // Normalize if we have probabilities
        if (model instanceof opencv_ml.ANN_MLP) {
            Mat matSum = new Mat();
            opencv_core.reduce(matOutput, matSum, 1, opencv_core.REDUCE_SUM);
            for (int c = 0; c < matOutput.cols(); c++) {
                opencv_core.dividePut(matOutput.col(c), matSum);
            }        	
        }

        // Reshape output
        Mat matResult = matOutput.reshape(matOutput.cols(), heightFeatures);
        
        
        // If we have a floating point or multi-channel result, we have probabilities
        ColorModel colorModelLocal;
        if (matResult.channels() > 1) {
        	// Do softmax if needed
            if (doSoftMax)
                applySoftmax(matResult);

            // Convert to 8-bit if needed
            if (do8Bit)
                matResult.convertTo(matResult, opencv_core.CV_8U, 255.0, 0.0);        	
            colorModelLocal = colorModelProbabilities;
        } else {
            matResult.convertTo(matResult, opencv_core.CV_8U);
            colorModelLocal = colorModelClassifications;
        }

        // Create & return BufferedImage
        BufferedImage imgResult = OpenCVTools.matToBufferedImage(matResult, colorModelLocal);

        // Free matrix
        if (matResult != null)
            matResult.release();

        return imgResult;
    }
    
}