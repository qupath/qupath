package qupath.opencv.ml.pixel.train;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Helper class for computing sample weights.
 * @since v0.8.0
 */
public class SampleWeights {

    private static final SampleWeightCalculator BALANCED = new BalancedSampleWeightCalculator();

    /**
     * Weight each sample according to the frequency of its target label.
     * The weights are {@code n / count}, where @{code n} is the total number of samples and
     * {@code count} gives the number of occurrences of the target label.
     * @returnathe sample weight calculator
     */
    public static SampleWeightCalculator balancedLabels() {
        return BALANCED;
    }

    /**
     * Apply sample weights based upon the target label value.
     * @param weightsMap a mapping between target labels and weights
     * @param defaultWeight the weight to use for any label that is not specified in the map
     * @return the sample weight calculator
     */
    public static SampleWeightCalculator weightedLabels(Map<Integer, Float> weightsMap, Float defaultWeight) {
        return new WeightedLabels(weightsMap, defaultWeight);
    }


    private static class WeightedLabels implements SampleWeightCalculator {

        private final Map<Integer, Float> weightsMap;
        private final float defaultWeight;

        public WeightedLabels(Map<Integer, Float> labelsToWeights, float defaultWeight) {
            this.weightsMap = Map.copyOf(labelsToWeights);
            this.defaultWeight = defaultWeight;
        }

        @Override
        public float[] calculateWeights(List<FeatureVector> samples, TargetVector targets) {
            return applyWeightsMap(targets, weightsMap, defaultWeight);
        }

    }


    private static class BalancedSampleWeightCalculator implements SampleWeightCalculator {

        @Override
        public float[] calculateWeights(List<FeatureVector> samples, TargetVector targets) {
            int n = targets.size();
            CountsPerLabel counts = CountsPerLabel.create(targets);
            Map<Integer, Float> weightsMap = counts.getLabels().stream().collect(
                    Collectors.toMap(
                            l -> l, l -> {
                                int count = counts.getCount(l);
                                return count == 0 ? 0 : (float)n / count;
                            }
                    )
            );
            return applyWeightsMap(targets, weightsMap, 0f);
        }

    }

    // TODO: Check if all the boxing/unboxing is a performance issue; if so, check if labels are positive integers
    //       that can be used as float[] array indices
    private static float[] applyWeightsMap(TargetVector targets, Map<Integer, Float> weightsMap, float defaultWeight) {
        int n = targets.size();
        float[] weights = new float[n];
        for (int i = 0; i < n; i++) {
            int label = targets.get(i);
            weights[i] = weightsMap.getOrDefault(label, defaultWeight);
        }
        return weights;
    }


    private static class CountsPerLabel {

        private final Map<Integer, Integer> labelToCount;
        private final long total;

        private CountsPerLabel(Map<Integer, Integer> labelToCount, long total) {
            this.labelToCount = labelToCount;
            this.total = total;
        }

        public static CountsPerLabel create(TargetVector targets) {
            Map<Integer, Integer> labelToCount = new TreeMap<>();
            for (int i = 0; i < targets.size(); i++) {
                labelToCount.compute(targets.get(i), (k, v) -> v == null ? 1 : v + 1);
            }
            return new CountsPerLabel(Collections.unmodifiableMap(labelToCount), targets.size());
        }

        public int getCount(int label) {
            return labelToCount.getOrDefault(label, 0);
        }

        public double getProportion(int label) {
            return (double)getCount(label) / total;
        }

        public Set<Integer> getLabels() {
            return labelToCount.keySet();
        }

        public int getSize() {
            return labelToCount.size();
        }

        public long getTotalCount() {
            return total;
        }

    }
}
