package qupath.opencv.ml;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;import java.util.concurrent.atomic.AtomicInteger;

/**
 * Confusion matrix to help assess classifier performance.
 *
 * @param <T> generic parameter for the labels (usually {@link Integer}, {@link String} or {@link qupath.lib.objects.classes.PathClass}).
 */
public class ConfusionMatrix<T> {

    private final Map<T, Map<T, AtomicInteger>> matrix;
    private final Set<T> labels = new LinkedHashSet<>();
    private final String name;
    private int count;

    /**
     * Create a confusion matrix without specifying labels.
     * @param name a name to identify this confusion matrix (must not be null)
     */
    public ConfusionMatrix(String name) {
        this(name, List.of());
    }

    /**
     * Create a confusion matrix with the specified labels.
     * Using this constructor makes it easy to define the order of the labels.
     * @param name a name to identify the confusion matrix (must not be null)
     * @param labels the labels for target and predicted values (must not be null)
     */
    public ConfusionMatrix(String name, List<T> labels) {
        Objects.requireNonNull(name, "Name must not be null");
        Objects.requireNonNull(labels, "Labels must not be null");
        this.name = name;
        this.matrix = new LinkedHashMap<>();
        this.labels.addAll(labels);
        for (var row : labels) {
            for (var col : labels) {
                int _ = getCount(row, col);
            }
        }
    }

    /**
     * Get the name property of this confusion matrix.
     * @return the name, if available
     */
    public String getName() {
        return name;
    }

    /**
     * Add the contents of multiple confusion matrices.
     * @param name name to identify the new confusion matrix (must not be null)
     * @param matrices the matrices to add
     * @return a new confusion matrix, where each element is the sum of elements from the input
     * @param <T> the generic parameter for labels; this should match for all confusion matrices
     */
    public static <T> ConfusionMatrix<T> sum(String name, Collection<? extends ConfusionMatrix<T>> matrices) {
        var sum = new ConfusionMatrix<T>(name);
        for (var mat : matrices) {
            var labels = mat.getLabels();
            for (var row : labels) {
                for (var col : labels) {
                    sum.accumulate(row, col, mat.getCount(row, col));
                }
            }
        }
        return sum;
    }

    /**
     * Update the value of an entry by adding 1.
     * @param row actual label
     * @param col predicted label
     */
    public synchronized void accumulate(T row, T col) {
        accumulate(row, col, 1);
    }

    /**
     * Update the value of an entry by adding the specified value.
     * @param row actual label
     * @param col predicted label
     * @param toAdd the value to add to the count
     */
    public synchronized void accumulate(T row, T col, int toAdd) {
        labels.add(row);
        labels.add(col);
        var counter = matrix.computeIfAbsent(row, _ -> new LinkedHashMap<>())
                .computeIfAbsent(col, _ -> new AtomicInteger(0));
        counter.addAndGet(toAdd);
        count += toAdd;
    }

    /**
     * Get a list of all labels within the confusion matrix.
     * @return
     */
    public synchronized List<T> getLabels() {
        return List.copyOf(labels);
    }

    /**
     * Get the sum of all confusion matrix counts.
     * @return
     */
    public synchronized int getTotal() {
        return count;
    }

    /**
     * Get the maximum entry within the matrix.
     * @return
     */
    public synchronized int getMax() {
         int max = 0;
         for (var row : getLabels()) {
             for (var col : getLabels()) {
                 var val = getCount(row, col);
                 if (val > max)
                     max = val;
             }
         }
         return max;
    }


//    /**
//     * Get the normalized count for a specified row and column.
//     * The normalization is calculated by division by the row sum.
//     * @param row the row label (true)
//     * @param col the column label (target)
//     * @return
//     */
//    public synchronized double getNormalizedCount(T row, T col) {
//        return (double)getCount(row, col) / getRowSum(row);
//    }

    /**
     * Get the sum of confusion matrix entries for a full row.
     * @param row the row label
     * @return
     */
    public synchronized int getRowSum(T row) {
        int sum = 0;
        for (var col : getLabels()) {
            sum += getCount(row, col);
        }
        return sum;
    }

    /**
     * Get the confusion matrix count for the specified row and column.
     * @param row the row label (true)
     * @param col the column label (prediction)
     * @return
     */
    public synchronized int getCount(T row, T col) {
        if (!matrix.containsKey(row))
            return 0;
        return matrix.get(row).computeIfAbsent(col, _ -> new AtomicInteger(0)).get();
    }

    /**
     * Get the true positives for a specific label.
     * @param label the label
     * @return
     */
    public synchronized int getTruePositives(T label) {
        return getCount(label, label);
    }

    /**
     * Get the total number of true positives, across all labels.
     * @return
     */
    public synchronized int getTruePositives() {
        return getLabels().stream().mapToInt(this::getTruePositives).sum();
    }

    /**
     * Get the false positives for a specific label.
     * @param col the label
     * @return
     */
    public synchronized int getFalsePositives(T col) {
        int sum = 0;
        for (var row : getLabels()) {
            if (!Objects.equals(col, row)) {
                sum += getCount(row, col);
            }
        }
        return sum;
    }

    /**
     * Get the false negatives for a specific label.
     * @param row the label
     * @return
     */
    public synchronized int getFalseNegatives(T row) {
        int sum = 0;
        for (var col : getLabels()) {
            if (!Objects.equals(row, col)) {
                sum += getCount(row, col);
            }
        }
        return sum;
    }


    /**
     * Get the precision for a specified label.
     * @param i the label
     * @return
     */
    public synchronized double getPrecision(T i) {
        double truePos = getTruePositives(i);
        double falsePos = getFalsePositives(i);
        return truePos / (truePos + falsePos);
    }

    /**
     * Get the overall precision, using macro averaging for multi-class.
     * @return
     */
    public synchronized double getPrecision() {
        return getLabels()
                .stream()
                .mapToDouble(this::getPrecision)
                .average()
                .orElse(Double.NaN);
    }

    /**
     * Get the recall for a specified label.
     * @param i the label
     * @return
     */
    public synchronized double getRecall(T i) {
        double truePos = getTruePositives(i);
        double falseNeg = getFalseNegatives(i);
        return truePos / (truePos + falseNeg);
    }

    /**
     * Get the overall recall, using macro averaging for multi-class.
     * @return
     */
    public synchronized double getRecall() {
        return getLabels()
                .stream()
                .mapToDouble(this::getRecall)
                .average()
                .orElse(Double.NaN);
    }

    /**
     * Get the F1 score for a specified label.
     * @param i the label
     * @return
     */
    public synchronized double getF1(T i) {
        double precision = getPrecision(i);
        double recall = getRecall(i);
        return 2 * (precision * recall) / (precision + recall);
    }

    /**
     * Get the overall F1 score, using macro averaging for multi-class.
     * @return
     */
    public synchronized double getF1() {
        return getLabels()
                .stream()
                .mapToDouble(this::getF1)
                .average()
                .orElse(Double.NaN);
    }

    /**
     * Get the overall accuracy, i.e. the number of true positives divided
     * by the sum of all entries.
     * @return
     */
    public synchronized double getAccuracy() {
        double nTruePositives = getTruePositives();
        return nTruePositives / getTotal();
    }

}
