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
 * Strategies for handling measurements between objects being merged.
 * Usually, it is safe to ignore, but if a pipeline creates objects and adds measurements before handling merges between tiles, then it is useful to consider how the measurements of each object will be passed to the output object.
 */
@FunctionalInterface
public interface MeasurementStrategy {

    /**
     * Merge the measurements for a set of input objects to a single measurement list.
     * @param pathObjects the input objects
     * @param outputList the container that output measurements should be stored in
     */
    void mergeMeasurements(List<? extends PathObject> pathObjects, MeasurementList outputList);

    /**
     * The default and previous strategy: ignore and discard.
     */
    MeasurementStrategy IGNORE = ((pathObjects, outputList) -> {});

    /**
     * Assign the measurements of the first object to the output object.
     */
    MeasurementStrategy USE_FIRST = ((pathObjects, outputList) -> {
        outputList.putAll(pathObjects.getFirst().getMeasurementList());
    });

    /**
     * Calculate the mean of each measurement and pass these to the output object.
     */
    MeasurementStrategy MEAN = ((pathObjects, outputList) -> {
        for (var name: pathObjects.getFirst().getMeasurementList().getNames()) {
            outputList.put(
                    name,
                    pathObjects.stream().mapToDouble(po -> po.getMeasurementList().get(name)).average().orElse(Double.NaN)
            );
        }
    });


    /**
     * Calculate a mean weighted by object area and pass this to the output object.
     */
    MeasurementStrategy WEIGHTED_MEAN = ((pathObjects, outputList) -> {
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
    });

    private static Map<PathObject, Double> computeAreaWeights(List<? extends PathObject> pathObjects) {
        Logger logger = LoggerFactory.getLogger(MeasurementStrategy.class);
        Map<PathObject, Double> output = new HashMap<>();
        double sum = 0;
        for (var po: pathObjects) {
            double area = po.getROI().getArea();
            sum += area;
            output.put(po, area);
        }
        if (sum == 0) {
            logger.warn("Zero areas");
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
    MeasurementStrategy MEDIAN = ((pathObjects, outputList) -> {
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
    });

    /**
     * Choose an object at random and use the measurements from this object.
     */
    MeasurementStrategy RANDOM = ((pathObjects, outputList) -> {
        var chosen = pathObjects.stream()
                .skip((int)(pathObjects.size() * Math.random()))
                .findFirst()
                .map(PathObject::getMeasurementList)
                .orElse(null);
        if (chosen == null) {
            return;
        }
        outputList.putAll(chosen);
    });

    /**
     * Use the measurements of the largest object by area, length or number of points.
     */
    MeasurementStrategy USE_BIGGEST = ((pathObjects, outputList) -> {
        var chosen = pathObjects.stream()
                .max(
                        Comparator.comparingDouble(
                        pathObject -> {
                            if (pathObject.getROI().getArea() > 0) {
                                return pathObject.getROI().getArea();
                            }
                            if (pathObject.getROI().getLength() > 0) {
                                return pathObject.getROI().getLength();
                            }
                            return pathObject.getROI().getNumPoints();
                        })
                )
                .map(PathObject::getMeasurementList)
                .orElse(null);
        if (chosen.isEmpty()) {
            return;
        }
        outputList.putAll(chosen);
    });

}
