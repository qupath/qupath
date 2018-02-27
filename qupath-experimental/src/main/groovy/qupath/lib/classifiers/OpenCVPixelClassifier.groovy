package qupath.lib.classifiers

import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_core.Mat
import org.bytedeco.javacpp.opencv_imgproc
import org.bytedeco.javacpp.opencv_ml

class OpenCVPixelClassifier extends AbstractOpenCVPixelClassifier {

    private opencv_ml.StatModel model;
    private OpenCVFeatureCalculator calculator;

    OpenCVPixelClassifier(opencv_ml.StatModel statModel, OpenCVFeatureCalculator calculator, PixelClassifierMetadata metadata) {
        super(metadata)
        this.model = statModel;
        this.calculator = calculator
    }

    void normalizeFeatures(Mat mat, Mat matMean, Mat matStdDev) {
        def means = toDoubleArray(matMean)
        def scales = toDoubleArray(matStdDev)
        normalizeFeatures(means, scales)
    }

    public int requestedPadding() {
        return calculator.requestedPadding()
    }

    public static double[] toDoubleArray(Mat mat) {
        def indexer = mat.createIndexer()
        def results = new double[(int)mat.total()]
        boolean colArray = mat.rows() == 1
        boolean rowArray = mat.cols() == 1
        if (!colArray && !rowArray)
            throw new IllegalArgumentException("Mat is neither a row nor a column array!")
        for (int i = 0; i < mat.total(); i++) {
            if (rowArray)
                results[i] = indexer.getDouble(i, 0)
            else
                results[i] = indexer.getDouble(0, i)
        }
        indexer.release()
        return results
    }


    public static void normalizeFeatures(Mat mat, double[] means, double[] scales) {
        if (means == null && scales == null)
            return;

        def matvec = new opencv_core.MatVector()
        opencv_core.split(mat, matvec)

        for (int c = 0; c < matvec.size(); c++) {
            if (means != null) {
                // TODO: Cache scalars!
                if (means.size() == 1)
                    opencv_core.subtractPut(matvec.get(c), opencv_core.Scalar.all(means[0]));
                else
                    opencv_core.subtractPut(matvec.get(c), opencv_core.Scalar.all(means[c]));
            }
            if (scales != null) {
                if (scales.size() == 1)
                    opencv_core.dividePut(matvec.get(c), scales[0]);
                else
                    opencv_core.dividePut(matvec.get(c), scales[c]);
            }
        }
        // Might not need to merge...?
        opencv_core.merge(matvec, mat)
    }

    protected Mat doClassification(Mat mat, int pad=0) {
        Mat matFeatures = calculator.calculateFeatures(mat);
        normalizeFeatures(matFeatures, metadata.inputChannelMeans, metadata.inputChannelScales)

        // Remove padding now, if we want - this means less to classify
        // Remove padding, if necessary
        if (pad > 0) {
            opencv_imgproc.resize(matFeatures, matFeatures, mat.size())
            int padX = pad
            int padY = pad
//            double scaleDownX = mat.rows() / matFeatures.rows()
//            double scaleDownY = mat.rows() / matFeatures.rows()
//            int padX = (pad / scaleDownX) / 2 as int
//            int padY = (pad / scaleDownY) / 2 as int
//            matFeatures.put(matFeatures.apply(new opencv_core.Rect(padX, padY, mat.cols()-padX*2, mat.rows()-padY*2)).clone())
            matFeatures.put(matFeatures.apply(new opencv_core.Rect(padX, padY, mat.cols()-padX*2, mat.rows()-padY*2)).clone())
        }
        int heightFeatures = matFeatures.rows()

        // Get probabilities
        Mat matOutput = new Mat();
        matFeatures = matFeatures.reshape(1, matFeatures.rows()*matFeatures.cols())
        synchronized (model) {
            model.predict(matFeatures, matOutput, opencv_ml.StatModel.RAW_OUTPUT);
        }

        // Normalize
        def matSum = new Mat()
        opencv_core.reduce(matOutput, matSum, 1, opencv_core.REDUCE_SUM)
        for (int c = 0; c < matOutput.cols(); c++) {
            opencv_core.dividePut(matOutput.col(c), matSum)
        }

        // Reshape output
        Mat matResult = matOutput.reshape(matOutput.cols(), heightFeatures);

        return matResult
    }

}
