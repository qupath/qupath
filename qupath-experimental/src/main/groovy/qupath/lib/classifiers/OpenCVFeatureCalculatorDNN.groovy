package qupath.lib.classifiers

import org.bytedeco.javacpp.opencv_core
import org.bytedeco.javacpp.opencv_dnn
import org.bytedeco.javacpp.opencv_dnn.Net

class OpenCVFeatureCalculatorDNN implements OpenCVFeatureCalculator {

    private Net model
    private int padding = 32 // Default
    private String outputName
    private List<String> featureNames
    private double mean, scale
    private String name

    //TODO: Remove hard-coded mean/scale defaults!
    OpenCVFeatureCalculatorDNN(Net model, String name, double mean=0.134906, double scale=0.127217) {
        this.model = model
        this.name = name

        this.mean = mean
        this.scale = scale

        def layerNames = model.getLayerNames()
        def names = []
        for (int i = 0; i < layerNames.size(); i++)
            names << layerNames.get(i).getString()
        outputName = names[-2]
        print 'Output name: ' + outputName
    }

    @Override
    opencv_core.Mat calculateFeatures(opencv_core.Mat input) {
        def mat = new opencv_core.Mat()
        input.convertTo(mat, opencv_core.CV_32F, 1.0/255.0, 0.0)
        mat.put(opencv_core.subtract(opencv_core.Scalar.ONE, mat))

    // Handle scales & offsets
        if (mean != 0)
            opencv_core.subtractPut(mat, opencv_core.Scalar.all(mean))
        if (scale != 1) {
            opencv_core.dividePut(mat, scale)
        }

        def blob = opencv_dnn.blobFromImage(input)
        def prob
        synchronized (model) {
            model.setInput(blob)
            prob = model.forward(outputName)
        }

        // Find out final shape
        def shape = new int[8]
        def dnnShape = opencv_dnn.shape(prob)
        dnnShape.get(shape)
        int nPlanes = shape[1]

        // Create output
        def matOutput = []
        def featureNames =  []
        for (int p = 0; p < nPlanes; p++) {
            matOutput << opencv_dnn.getPlane(prob, 0, p)
            featureNames << 'Feature ' + p
        }
        def matResult = new opencv_core.Mat()
        opencv_core.merge(new opencv_core.MatVector(matOutput as opencv_core.Mat[]), matResult)

        this.featureNames = Collections.unmodifiableList(featureNames)

        return matResult
    }

    @Override
    int requestedPadding() {
        return padding
    }

    @Override
    List<String> getLastFeatureNames() {
        return featureNames
    }

    @Override
    public String toString() {
        return name
    }

}
