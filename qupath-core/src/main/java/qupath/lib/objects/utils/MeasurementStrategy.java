package qupath.lib.objects.utils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;

/**
 * A strategy for handling measurements between objects being merged.
 * Usually, it is safe to ignore, but if a pipeline creates objects and adds measurements before handling merges between tiles, then it is useful to consider how the measurements of each object will be passed to the output object.
 * The default implementations assume that the measurement names are identical across all objects; to this end only the names of the measurements of the first object are queried.
 */
@FunctionalInterface
public interface MeasurementStrategy {

    /**
     * Merge the measurements for a set of input objects to a single measurement list.
     * This should operate by adding the calculated measurements to the measurement list passed in as second argument.
     * @param pathObjects the input objects
     * @param outputList the container that output measurements should be stored in
     */
    void mergeMeasurements(List<? extends PathObject> pathObjects, MeasurementList outputList);

    /**
     * The default and previous strategy: ignore and discard.
     */
    MeasurementStrategy IGNORE = (pathObjects, outputList) -> {};

    /**
     * Assign the measurements of the first object to the output object.
     */
    MeasurementStrategy USE_FIRST = (pathObjects, outputList) -> {
        if (pathObjects.isEmpty()) {
            return;
        }
        outputList.putAll(pathObjects.getFirst().getMeasurementList());
    };

    /**
     * Calculate the mean of each measurement and pass these to the output object.
     */
    MeasurementStrategy MEAN = (pathObjects, outputList) -> {
        if (pathObjects.size() <= 1) {
            USE_FIRST.mergeMeasurements(pathObjects, outputList);
            return;
        }
        for (var name: pathObjects.getFirst().getMeasurementList().getNames()) {
            outputList.put(
                    name,
                    pathObjects.stream().mapToDouble(po -> po.getMeasurementList().get(name)).average().orElse(Double.NaN)
            );
        }
    };

    /**
     * Calculate a mean weighted by object area and pass this to the output object.
     */
    MeasurementStrategy WEIGHTED_MEAN = (pathObjects, outputList) -> {
        if (pathObjects.size() <= 1) {
            USE_FIRST.mergeMeasurements(pathObjects, outputList);
            return;
        }
        var weights = computeAreaWeights(pathObjects);
        for (var name: pathObjects.getFirst().getMeasurementList().getNames()) {
            outputList.put(name,
                    pathObjects.stream()
                            .mapToDouble(po ->
                                    po.getMeasurementList().get(name) * weights.get(po)
                            )
                            .sum()
            );
        }
    };

    private static Map<PathObject, Double> computeAreaWeights(List<? extends PathObject> pathObjects) {
        Map<PathObject, Double> output = new HashMap<>();
        double sum = 0;
        for (var po: pathObjects) {
            double area = po.getROI().getArea();
            sum += area;
            output.put(po, area);
        }
        if (sum == 0) {
            Logger logger = LoggerFactory.getLogger(MeasurementStrategy.class);
            logger.warn("No object has non-zero area");
        }
        for (var key: output.keySet()) {
            double finalSum = sum;
            output.computeIfPresent(key, (k, value) -> value / finalSum);
        }
        return output;
    }

    /**
     * Calculate the median of each measurement and pass these to the output object.
     */
    MeasurementStrategy MEDIAN = (pathObjects, outputList) -> {
        if (pathObjects.size() <= 1) {
            USE_FIRST.mergeMeasurements(pathObjects, outputList);
            return;
        }
        for (var name: pathObjects.getFirst().getMeasurementList().getNames()) {
            outputList.put(
                    name,
                    pathObjects.stream()
                            .mapToDouble(po -> po.getMeasurementList().get(name))
                            .sorted()
                            .skip((pathObjects.size() - 1) / 2)
                            .limit(2 - pathObjects.size() % 2)
                            .average()
                            .orElse(Double.NaN)
            );
        }
    };

    /**
     * Choose an object at random and use the measurements from this object.
     */
    MeasurementStrategy RANDOM = (pathObjects, outputList) -> {
        if (pathObjects.size() <= 1) {
            USE_FIRST.mergeMeasurements(pathObjects, outputList);
            return;
        }
        var chosen = pathObjects.stream()
                .skip((int)(pathObjects.size() * Math.random()))
                .findFirst()
                .map(PathObject::getMeasurementList)
                .orElse(null);
        if (chosen == null) {
            return;
        }
        outputList.putAll(chosen);
    };

    /**
     * Use the measurements of the largest object by area, length or number of points.
     */
    MeasurementStrategy USE_BIGGEST = (pathObjects, outputList) -> {
        if (pathObjects.size() <= 1) {
            USE_FIRST.mergeMeasurements(pathObjects, outputList);
            return;
        }
        var chosen = pathObjects.stream()
                .max(Comparator.comparingDouble((PathObject p) -> p.getROI().getArea())
                                .thenComparing(p -> p.getROI().getLength())
                                .thenComparing(p -> p.getROI().getNumPoints())
                )
                .map(PathObject::getMeasurementList)
                .orElse(null);
        if (chosen.isEmpty()) {
            return;
        }
        outputList.putAll(chosen);
    };

    /**
     * Calculate the maximum of each measurement and pass these to the output object.
     */
    MeasurementStrategy MAX = (pathObjects, outputList) -> {
        if (pathObjects.size() <= 1) {
            USE_FIRST.mergeMeasurements(pathObjects, outputList);
            return;
        }
        for (var name: pathObjects.getFirst().getMeasurementList().getNames()) {
            outputList.put(
                    name,
                    pathObjects.stream()
                            .mapToDouble(po -> po.getMeasurementList().get(name))
                            .max()
                            .orElse(Double.NaN)
            );
        }
    };

    /**
     * Calculate the minimum of each measurement and pass these to the output object.
     */
    MeasurementStrategy MIN = (pathObjects, outputList) -> {
        if (pathObjects.size() <= 1) {
            USE_FIRST.mergeMeasurements(pathObjects, outputList);
            return;
        }
        for (var name: pathObjects.getFirst().getMeasurementList().getNames()) {
            outputList.put(
                    name,
                    pathObjects.stream()
                            .mapToDouble(po -> po.getMeasurementList().get(name))
                            .min()
                            .orElse(Double.NaN)
            );
        }
    };
}
