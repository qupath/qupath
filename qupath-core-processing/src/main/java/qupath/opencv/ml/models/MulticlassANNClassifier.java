package qupath.opencv.ml.models;

/**
 * A multiclass version of ANN.
 */
class MulticlassANNClassifier extends ANNClassifier {

    @Override
    public boolean supportsMulticlass() {
        return true;
    }

    @Override
    public String getName() {
        return "ANN MLP (Multiclass)";
    }

}
