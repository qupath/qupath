package qupath.lib.classifiers.pixel.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Size;
import org.bytedeco.javacpp.opencv_imgproc;

public class BasicMultiscaleOpenCVFeatureCalculator implements OpenCVFeatureCalculator {

    private double[] sigmaValues;
    private int padding = 0;
    private boolean includeEdges;
    private List<String> featureNames;
    
    public BasicMultiscaleOpenCVFeatureCalculator(boolean includeEdges) {
    	this(1, 1, includeEdges);
    }

    public BasicMultiscaleOpenCVFeatureCalculator(double sigmaStart, int nScales, boolean includeEdges) {
        sigmaValues = new double[nScales];
        for (int i = 0; i < nScales; i++) {
        	sigmaValues[i] = sigmaStart;
            sigmaStart *= 2;
        }
        padding = (int)Math.ceil(sigmaValues[sigmaValues.length-1] * 3);
        this.includeEdges = includeEdges;
    }

    public opencv_core.Mat calculateFeatures(opencv_core.Mat input) {
        opencv_core.Mat matDest = new Mat();
        input.convertTo(matDest, opencv_core.CV_32F);

        int nChannels = input.channels();

        List<String> featureNames = new ArrayList<>();

        // Compute smoothing
        List<Mat> mats = new ArrayList<>();
        for (double sigma : sigmaValues) {
            int s = (int)Math.ceil(sigma * 3) * 2 + 1;
            Size size = new Size(s, s);
            Mat matTemp = new Mat();
            opencv_imgproc.GaussianBlur(matDest, matTemp, size, sigma);
            mats.add(matTemp);

            for (int c = 1; c <= nChannels; c++)
                featureNames.add(String.format("Channel %d: Gaussian sigma = %.2f", c, sigma));

            Mat matTemp2 = new Mat();
            opencv_imgproc.Laplacian(matTemp, matTemp2, -1);
            mats.add(matTemp2);

            for (int c = 1; c <= nChannels; c++)
                featureNames.add(String.format("Channel %d: Laplacian sigma = %.2f", c, sigma));
            
//            // Must be 8-bit for median filter...?
//            Mat matTemp3 = new Mat();
//            int window = (int)Math.round(sigma)*2+1;
//            opencv_imgproc.medianBlur(matDest, matTemp3, window);
//            mats.add(matTemp3);
//            for (int c = 1; c <= nChannels; c++)
//                featureNames.add(String.format("Channel %d: Median window = %d", c, window));

//            def matTemp3 = new opencv_core.Mat()
//            opencv_imgproc.cvtColor(matTemp, matTemp3, opencv_imgproc.COLOR_RGB2HSV)
//            mats << matTemp3

            if (includeEdges) {
            	Mat matDx = new Mat();
                Mat matDy = new Mat();
                opencv_imgproc.Sobel(matTemp, matDx, -1, 1, 0);
                opencv_imgproc.Sobel(matTemp, matDy, -1, 0, 1);
                opencv_core.magnitude(matDx, matDy, matDx);
                mats.add(matDx);

                for (int c = 1; c <= nChannels; c++)
                    featureNames.add(String.format("Channel %d: Gradient mag sigma = %.2f", c, sigma));
            }

        }
        opencv_core.merge(new opencv_core.MatVector(mats.toArray(new Mat[0])), matDest);

        this.featureNames = Collections.unmodifiableList(featureNames);
        return matDest;
    }

    public int requestedPadding() {
        return padding;
    }

    public List<String> getLastFeatureNames() {
        return featureNames;
    }

    @Override
    public String toString() {
        String edgesOrNot = includeEdges ? " with edges" : "";
        if (sigmaValues.length == 1)
            return String.format("Basic texture features%s (sigma = %.2f)", edgesOrNot, sigmaValues[0]);
        return String.format("Basic multiscale texture features%s (sigma = %.2f-%.2f)", edgesOrNot, sigmaValues[0], sigmaValues[sigmaValues.length-1]);
    }


}