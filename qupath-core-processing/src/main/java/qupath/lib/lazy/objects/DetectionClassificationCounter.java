package qupath.lib.lazy.objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Helper class for counting numbers of detection objects.
 */
class DetectionClassificationCounter implements Function<PathObject, DetectionPathClassCounts> {

    private static final Logger logger = LoggerFactory.getLogger(DetectionClassificationCounter.class);
    private final PathObjectHierarchy hierarchy;

    // Map to store cached counts; this should be reset when the hierarchy changes - since v0.6.0 this happens
    // automatically using the hierarchy's last event timestamp
    private final Map<PathObject, DetectionPathClassCounts> map = Collections.synchronizedMap(new WeakHashMap<>());
    private long lastHierarchyEventCount;

    DetectionClassificationCounter(PathObjectHierarchy hierarchy) {
        Objects.requireNonNull(hierarchy, "Hierarchy must not be null!");
        this.hierarchy = hierarchy;
    }

    @Override
    public DetectionPathClassCounts apply(PathObject pathObject) {
        var actualTimestamp = hierarchy.getEventCount();
        if (actualTimestamp > lastHierarchyEventCount) {
            logger.debug("Clearing cached measurements");
            map.clear();
            lastHierarchyEventCount = actualTimestamp;
        }
        return map.computeIfAbsent(pathObject, p -> new DetectionPathClassCounts(hierarchy, p));
    }

}
