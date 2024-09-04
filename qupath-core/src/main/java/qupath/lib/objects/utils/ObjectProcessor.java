package qupath.lib.objects.utils;

import qupath.lib.objects.PathObject;

import java.util.Collection;
import java.util.List;

/**
 * Minimal interface for processing one or more objects.
 * <p>
 * This is intended for tasks such as merging, splitting, filtering, etc.
 */
public interface ObjectProcessor {

    /**
     * Process a collection of objects and return the result.
     * @param input the input objects
     * @return the output objects; this should always be a new collection (even if it contains the same objects)
     */
    List<PathObject> process(Collection<? extends PathObject> input);

    /**
     * Create a new ObjectProcessor that applies (at least) two processors sequentially.
     * @param after the processor to apply next
     * @return a processor that applies this processor first, and then the given processor
     */
    default ObjectProcessor andThen(ObjectProcessor after) {
        return (input) -> after.process(process(input));
    }

}
