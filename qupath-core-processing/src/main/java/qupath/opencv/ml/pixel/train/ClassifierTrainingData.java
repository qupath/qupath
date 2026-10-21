package qupath.opencv.ml.pixel.train;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.tools.OpenCVTools;

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
public class ClassifierTrainingData implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ClassifierTrainingData.class);

    private final String name;
    private final Mat matTraining;
    private final Mat matTargets;

    private final Map<PathClass, Integer> labels;

    private ClassifierTrainingData(String name, Mat matFeatures, Mat matTargets, Map<PathClass, Integer> labels) {
        Objects.requireNonNull(name, "Name must not be null");
        Objects.requireNonNull(matFeatures, "Features must not be null");
        Objects.requireNonNull(matTargets, "Targets must not be null");
        Objects.requireNonNull(labels, "Labels must not be null");
        this.name = name;
        this.matTraining = matFeatures;
        this.matTargets = matTargets;
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        if (matFeatures.rows() != matTargets.rows())
            throw new IllegalArgumentException("Training data has " + matFeatures.rows() + " rows but " + matTargets.rows() + " targets");
    }

    /**
     * Create a new instance.
     * @param name name to identify the training data.
     * @param matFeatures the features, stored row-wise
     * @param matTargets the targets, stored row-wise
     * @param pathClassesLabels map giving the classification and its corresponding target label
     * @return a new training data instance
     * @throws IllegalArgumentException if any of the parameters are null, or the features and targets have a different number of rows
     */
    public static ClassifierTrainingData create(String name, Mat matFeatures, Mat matTargets, Map<PathClass, Integer> pathClassesLabels) {
        return new ClassifierTrainingData(name, matFeatures, matTargets, pathClassesLabels);
    }

    /**
     * Get an unmodifiable map of classifications to labels.
     * @return the labels
     */
    public synchronized Map<PathClass, Integer> getLabelMap() {
        return labels;
    }

    /**
     * Get training data, containing both features and targets.
     * Note that this creates a defensive copy of the Mats.
     * @return
     */
    public TrainData createTrainData() {
        return TrainData.create(matTraining.clone(), opencv_ml.ROW_SAMPLE, matTargets.clone());
    }

    /**
     * Total number of training samples.
     *
     * @return
     */
    public int size() {
        return matTraining.rows();
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
        var train = extractRows(this.matTraining, inds);
        var targets = extractRows(this.matTargets, inds);
        return create(name, train, targets, getLabelMap());
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

    @Override
    public void close() {
        if (matTraining != null)
            matTraining.close();
        if (matTargets != null)
            matTargets.close();
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
        List<Mat> train = new ArrayList<>();
        List<Mat> target = new ArrayList<>();
        Map<PathClass, Integer> labels = null;
        for (var data : trainingData) {
            if (labels == null)
                labels = data.labels;
            else if (!labels.equals(data.labels))
                throw new IllegalArgumentException("Labels do not match!");
            train.add(data.matTraining);
            target.add(data.matTargets);
        }
        if (labels == null)
            throw new IllegalArgumentException("No training data provided!");
        return create(
                trainingData.stream().map(ClassifierTrainingData::getName).collect(Collectors.joining(", ")),
                OpenCVTools.vConcat(train, new Mat()), OpenCVTools.vConcat(target, new Mat()), labels
        );
    }
}
