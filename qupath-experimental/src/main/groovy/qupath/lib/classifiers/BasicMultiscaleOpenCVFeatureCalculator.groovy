package qupath.lib.classifiers

import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_imgproc

public class BasicMultiscaleOpenCVFeatureCalculator implements OpenCVFeatureCalculator {

    private double[] sigmaValues
    private int padding = 0
    private boolean includeEdges

    BasicMultiscaleOpenCVFeatureCalculator(double sigmaStart = 1.0, int nScales = 1, boolean includeEdges = false) {
        def sigmas = []
        for (int i = 0; i < nScales; i++) {
            sigmas << sigmaStart
            sigmaStart *= 2
        }
        sigmaValues = sigmas as double[]
        padding = Math.ceil(sigmaValues[-1] * 3) as int
        this.includeEdges = includeEdges
    }

    public opencv_core.Mat calculateFeatures(opencv_core.Mat input) {
        opencv_core.Mat matDest = new opencv_core.Mat()
        input.convertTo(matDest, opencv_core.CV_32F)

        // Compute smoothing
        def mats = []
        for (double sigma : sigmaValues) {
            int s = Math.ceil(sigma * 3) * 2 + 1
            def size = new opencv_core.Size(s, s)
            def matTemp = new opencv_core.Mat()
            opencv_imgproc.GaussianBlur(matDest, matTemp, size, sigma)
            mats << matTemp

            def matTemp2 = new opencv_core.Mat()
            opencv_imgproc.Laplacian(matTemp, matTemp2, -1)
            mats << matTemp2

            def matTemp3 = new opencv_core.Mat()
            opencv_imgproc.cvtColor(matTemp, matTemp3, opencv_imgproc.COLOR_RGB2HSV)
            mats << matTemp3

            if (includeEdges) {
                def matDx = new opencv_core.Mat()
                def matDy = new opencv_core.Mat()
                opencv_imgproc.Sobel(matTemp, matDx, -1, 1, 0)
                opencv_imgproc.Sobel(matTemp, matDy, -1, 0, 1)
                opencv_core.magnitude(matDx, matDy, matDx)
                mats << matDx
            }

        }
        opencv_core.merge(new opencv_core.MatVector(mats.toArray(new opencv_core.Mat[0])), matDest)
        return matDest
    }

    public int requestedPadding() {
        return padding
    }

    @Override
    public String toString() {
        def edgesOrNot = includeEdges ? ' with edges' : ''
        if (sigmaValues.size() == 1)
            return String.format('Basic texture features%s (sigma = %.2f)', edgesOrNot, sigmaValues[0])
        return String.format('Basic multiscale texture features%s (sigma = %.2f-%.2f)', edgesOrNot, sigmaValues[0], sigmaValues[-1])
    }


}