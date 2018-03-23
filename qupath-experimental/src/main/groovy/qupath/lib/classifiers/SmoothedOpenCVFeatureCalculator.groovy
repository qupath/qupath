package qupath.lib.classifiers

import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_core.Size
import org.bytedeco.javacpp.opencv_imgproc

/**
 * Just apply a Gaussian filter (if even that) to image pixels.
 */
public class SmoothedOpenCVFeatureCalculator implements OpenCVFeatureCalculator {

    private Size size
    private double sigma = 0
    private int padding = 0
    private List<String> featureNames

    public SmoothedOpenCVFeatureCalculator(double sigma=0) {
        this.sigma = sigma
        if (sigma > 0) {
            int s = Math.ceil(sigma * 4) * 2 + 1
            size = new Size(s, s)
            padding = Math.ceil(s * 3) as int
        }
    }

    public Mat calculateFeatures(Mat input) {
        Mat matOutput = new Mat()
        input.convertTo(matOutput, opencv_core.CV_32F)

        int nChannels = matOutput.channels()
		// TODO: Just generate the feature names once!
        def featureNames = []
        for (int c = 1; c <= nChannels; c++) {
            if (sigma > 0)
                featureNames << String.format('Channel %d: Gaussian sigma = %.2f', c, sigma)
            else
                featureNames << String.format('Channel %d', c)
        }

        if (sigma > 0) {
            opencv_imgproc.GaussianBlur(matOutput, matOutput, size, sigma)
        }
		synchronized (this) {
			this.featureNames.clear()
			this.featureNames.addAll(featureNames)
		}
        return matOutput;
    }

    public int requestedPadding() {
        return padding
    }

    public synchronized  List<String> getLastFeatureNames() {
        return featureNames
    }

    @Override
    public String toString() {
        if (sigma == 0)
            return "Original pixel values"
        return String.format("Smoothed original pixel values (sigma = %.2f)", sigma)
    }


}