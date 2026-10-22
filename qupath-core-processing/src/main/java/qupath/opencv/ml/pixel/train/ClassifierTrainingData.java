package qupath.opencv.ml.pixel.train;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.classes.PathClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Helper class to store classifier training data, targets and labels.
 */
public class ClassifierTrainingData {

    private static final Logger logger = LoggerFactory.getLogger(ClassifierTrainingData.class);

    private final String name;
    private final List<FeatureVector> features;
    private final TargetVector targets;

    private final Map<PathClass, Integer> labels;

    private ClassifierTrainingData(String name, List<FeatureVector> features, TargetVector targets, Map<PathClass, Integer> labels) {
        Objects.requireNonNull(name, "Name must not be null");
        Objects.requireNonNull(features, "Features must not be null");
        Objects.requireNonNull(targets, "Targets must not be null");
        Objects.requireNonNull(labels, "Labels must not be null");
        this.name = name;
        this.features = List.copyOf(features);
        this.targets = targets;
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        if (features.size() != targets.size())
            throw new IllegalArgumentException("Data has " + features.size() + " samples but " + targets.size() + " targets");
    }

    /**
     * Create a new instance.
     * @param name name to identify the training data.
     * @param features the feature vectors (one per sample)
     * @param targets the target vector (with size equal to the number of samples)
     * @param pathClassesLabels map giving the classification and its corresponding target label
     * @return a new training data instance
     * @throws IllegalArgumentException if any of the parameters are null, or the features and targets have a different number of rows
     */
    public static ClassifierTrainingData create(String name, List<FeatureVector> features, TargetVector targets, Map<PathClass, Integer> pathClassesLabels) {
        return new ClassifierTrainingData(name, features, targets, pathClassesLabels);
    }

    private static Mat featuresToMat(List<FeatureVector> features) {
        if (features.isEmpty())
            return new Mat(0);
        int nSamples = features.size();
        var nFeatures = features.getFirst().size();
        var mat = new Mat(nSamples, nFeatures, opencv_core.CV_32FC1, Scalar.ZERO);
        try (FloatIndexer idx = mat.createIndexer()) {
            for (int i = 0; i < nSamples; i++) {
                var vector = features.get(i);
                for (int j = 0; j < nFeatures; j++) {
                    idx.put(i, j, vector.get(j));
                }
            }
        }
        return mat;
    }

    private static Mat targetsToMat(TargetVector targets) {
        var mat = new Mat(targets.size(), 1, opencv_core.CV_32SC1, Scalar.ZERO);
        try (IntIndexer indexer = mat.createIndexer()) {
            for (int i = 0; i < targets.size(); i++) {
                indexer.put(i, targets.get(i));
            }
        }
        return mat;
    }

    /**
     * Get an unmodifiable map of classifications to labels.
     * @return the labels
     */
    public synchronized Map<PathClass, Integer> getLabelMap() {
        return labels;
    }

    /**
     * Get the training samples. Note that this returns a defensive copy, so should not be called unnecessarily.
     * @return
     */
    public Mat getSamples() {
        return featuresToMat(features);
    }

    /**
     * Get the training targets. Note that this returns a defensive copy, so should not be called unnecessarily.
     * @return
     */
    public Mat getTargets() {
        return targetsToMat(targets);
    }

    /**
     * Total number of training samples.
     *
     * @return
     */
    public int size() {
        return features.size();
    }

    /**
     * Name of the data. This may be derived from an image name.
     *
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Split the data into (roughly) equally-sizes parts.
     *
     * @param nSplits number of splits to create; this must be {@code &leq; size()}
     *                (and in practice should be much less to increase the likelihood of all
     *                labels being represented in each split).
     * @param rng     the random number generator to create the splits; if null, the splits are made
     *                without shuffling the initial data.
     * @return a list of nSplits splits of the original data
     */
    public List<ClassifierTrainingData> split(int nSplits, Random rng) {
        int n = size();
        if (n < nSplits)
            throw new IllegalArgumentException("nSplits (" + nSplits + ") must be less than the data size (" + n + ")");
        var inds = IntStream.range(0, n).boxed().collect(Collectors.toCollection(ArrayList::new));
        if (rng != null)
            Collections.shuffle(inds, rng);
        List<ClassifierTrainingData> data = new ArrayList<>();
        int startInd = 0;
        for (int i = 0; i < nSplits; i++) {
            int endInd = i == nSplits - 1 ? n : (int) Math.floor((double) (n * (i + 1)) / nSplits);
            data.add(extractData(getName() + " (split=" + (i + 1) + ")", inds.subList(startInd, endInd)));
            startInd = endInd + 1;
        }
        return data;
    }

    private ClassifierTrainingData extractData(String name, List<Integer> inds) {
        List<FeatureVector> newFeatures = new ArrayList<>();
        int[] newTargets = new int[inds.size()];
        for (int i = 0; i < inds.size(); i++) {
            int index = inds.get(i);
            newTargets[i] = targets.get(index);
            newFeatures.add(features.get(index));
        }
        return create(name,
                newFeatures,
                TargetVector.create(newTargets),
                getLabelMap());
    }

    private static Mat extractRows(Mat mat, List<Integer> inds) {
        var array = new Mat[inds.size()];
        for (int i = 0; i < inds.size(); i++) {
            array[i] = mat.row(inds.get(i));
        }
        var dest = new Mat();
        opencv_core.vconcat(new MatVector(array), dest);
        return dest;
    }

    /**
     * Merge training data from multiple sources.
     * This assumes that the labels are identical, and the training mats can be concatenated.
     *
     * @param trainingData the collection of training data to use
     * @return the merged training data
     * @throws IllegalArgumentException if no training data is provided, or if it contains incompatible labels
     */
    public static ClassifierTrainingData merge(Collection<? extends ClassifierTrainingData> trainingData) throws IllegalArgumentException {
        List<FeatureVector> train = new ArrayList<>();
        List<TargetVector> target = new ArrayList<>();
        Map<PathClass, Integer> labels = null;
        for (var data : trainingData) {
            if (labels == null)
                labels = data.labels;
            else if (!labels.equals(data.labels))
                throw new IllegalArgumentException("Labels do not match!");
            train.addAll(data.features);
            target.add(data.targets);
        }
        if (labels == null)
            throw new IllegalArgumentException("No training data provided!");
        return create(
                trainingData.stream().map(ClassifierTrainingData::getName).collect(Collectors.joining(", ")),
                train, TargetVector.concat(target), labels
        );
    }

}
