package qupath.lib.lazy.objects;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Cache to store the number of descendant detection objects with a particular PathClass.
 * <p>
 * (The parent is included in any count if it's a detection object... but it's expected not to be.
 * Rather, this is intended for counting the descendants of annotations or TMA cores.)
 */
class DetectionPathClassCounts {

    private final Map<PathClass, Integer> counts = new HashMap<>();

    /**
     * Create a structure to count detections inside a specified parent.
     *
     * @param hierarchy
     * @param parentObject the parent object.
     */
    DetectionPathClassCounts(final PathObjectHierarchy hierarchy, final PathObject parentObject) {
        Collection<PathObject> pathObjects;
        if (parentObject.isRootObject())
            pathObjects = hierarchy.getDetectionObjects();
        else
            pathObjects = hierarchy.getAllDetectionsForROI(parentObject.getROI());

        for (PathObject child : pathObjects) {
            if (child == parentObject || !child.isDetection())
                continue;
            PathClass pathClass = child.getPathClass();
            counts.merge(pathClass, 1, Integer::sum);
        }
    }

    public int getDirectCount(final PathClass pathClass) {
        return counts.getOrDefault(pathClass, 0);
    }

    public int getCountForAncestor(final Predicate<PathClass> predicate, final PathClass ancestor) {
        int count = 0;
        for (Map.Entry<PathClass, Integer> entry : counts.entrySet()) {
            if (ancestor == null) {
                if (predicate.test(entry.getKey()) && entry.getKey().getParentClass() == null)
                    count += entry.getValue();
            } else if (ancestor.isAncestorOf(entry.getKey()) && predicate.test(entry.getKey()))
                count += entry.getValue();
        }
        return count;
    }

    public int getCountForAncestor(final Predicate<PathClass> predicate, final PathClass... ancestors) {
        int count = 0;
        for (PathClass ancestor : ancestors)
            count += getCountForAncestor(predicate, ancestor);
        return count;
    }

    public int getCountForAncestor(final PathClass... ancestors) {
        return getCountForAncestor(pathClass -> true, ancestors);
    }

    public int getOnePlus(final PathClass... ancestors) {
        return getCountForAncestor(PathClassTools::isOnePlus, ancestors);
    }

    public int getTwoPlus(final PathClass... ancestors) {
        return getCountForAncestor(PathClassTools::isTwoPlus, ancestors);
    }

    public int getThreePlus(final PathClass... ancestors) {
        return getCountForAncestor(PathClassTools::isThreePlus, ancestors);
    }

    public int getNegative(final PathClass... ancestors) {
        return getCountForAncestor(PathClassTools::isNegativeClass, ancestors);
    }

    public int getPositive(final PathClass... ancestors) {
        return getCountForAncestor(PathClassTools::isPositiveOrGradedIntensityClass, ancestors);
    }

    public double getHScore(final PathClass... ancestors) {
        double plus1 = 0;
        double plus2 = 0;
        double plus3 = 0;
        double negative = 0;
        for (PathClass ancestor : ancestors) {
            plus1 += getOnePlus(ancestor);
            plus2 += getTwoPlus(ancestor);
            plus3 += getThreePlus(ancestor);
            negative += getNegative(ancestor);
        }
        return (plus1 * 1 + plus2 * 2 + plus3 * 3) / (plus1 + plus2 + plus3 + negative) * 100;
    }

    public int getAllredIntensity(final double minProportion, final PathClass... ancestors) {
        int proportionScore = getAllredProportion(minProportion, ancestors);
        int intensityScore = 0;
        if (proportionScore > 0) {
            int nPositive = getPositive(ancestors);
            double meanIntensity = (getOnePlus(ancestors) + getTwoPlus(ancestors) * 2. + getThreePlus(ancestors) * 3.) / nPositive;
            if (meanIntensity > 7. / 3.)
                intensityScore = 3;
            else if (meanIntensity > 5. / 3.)
                intensityScore = 2;
            else
                intensityScore = 1;
        }
        return intensityScore;
    }

    public int getAllredProportion(final double minProportion, final PathClass... ancestors) {
        // Compute Allred score
        double proportion = getPositivePercentage(ancestors) / 100.0;
        if (proportion < minProportion)
            return 0;
        int proportionScore;
        if (proportion >= 2. / 3.)
            proportionScore = 5;
        else if (proportion >= 1. / 3.)
            proportionScore = 4;
        else if (proportion >= 0.1)
            proportionScore = 3;
        else if (proportion >= 0.01)
            proportionScore = 2;
        else if (proportion > 0) // 'Strict' Allred scores accepts anything above 0 as positive... but minProportion may already have kicked in
            proportionScore = 1;
        else
            proportionScore = 0;
        return proportionScore;
    }

    public int getAllredScore(final double minProportion, final PathClass... ancestors) {
        return getAllredIntensity(minProportion, ancestors) + getAllredProportion(minProportion, ancestors);
    }

    /**
     * Get the percentage of positive detections, considering only descendants of one or more
     * specified classes.
     *
     * @param ancestors
     * @return
     */
    public double getPositivePercentage(final PathClass... ancestors) {
        double positive = 0;
        double negative = 0;
        for (PathClass ancestor : ancestors) {
            positive += getPositive(ancestor);
            negative += getNegative(ancestor);
        }
        return positive / (positive + negative) * 100;
    }

}
