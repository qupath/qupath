package qupath.opencv.ml.pixel.train;

import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Objects;

/**
 * Lightweight wrapper around a int array, which does not permit its contents to be modified.
 * Its intended use is to provide the target labels when training a classifier.
 */
public class TargetVector {

    private static final TargetVector EMPTY = new TargetVector(new int[0]);

    private final int[] targets;

    private TargetVector(int[] targets) {
        this.targets = targets;
    }

    /**
     * Create a target vector that wraps a duplicate of the provided int array.
     * <p>
     * Note that this makes a defensive copy of the provided array to ensure its contents cannot change.
     * @param targets the targets
     * @return a new targets vector
     */
    public static TargetVector create(int[] targets) {
        Objects.requireNonNull(targets);
        return new TargetVector(targets.clone());
    }

    /**
     * Get the feature at the specified index.
     * @param index the index
     * @return the feature
     */
    public int get(int index) {
        return targets[index];
    }

    /**
     * The number of features in the feature vector.
     * @return the feature vector size
     */
    public int size() {
        return targets.length;
    }

    /**
     * Get a read-only int buffer containing the contents of this target vector.
     * @return the int buffer
     */
    public IntBuffer asIntBuffer() {
        return IntBuffer.wrap(targets).asReadOnlyBuffer();
    }

    /**
     * Concatenate an array of target vectors.
     * @param targets the target vectors to concatenate
     * @return a new target vector containing values from all the input vectors
     */
    public static TargetVector concat(TargetVector... targets) {
        if (targets.length == 0) {
            return EMPTY;
        } else if (targets.length == 1) {
            return targets[0];
        } else {
            int n = 0;
            for (TargetVector v : targets) {
                n += v.size();
            }
            int[] result = new int[n];
            int i = 0;
            for (TargetVector v : targets) {
                System.arraycopy(v.targets, 0, result, i, v.size());
                i += v.size();
            }
            return new TargetVector(result);
        }
    }

    /**
     * Concatenate a collection of target vectors.
     * @param targets the target vectors to concatenate
     * @return a new target vector containing values from all the input vectors
     */
    public static TargetVector concat(Collection<? extends TargetVector> targets) {
        return concat(targets.toArray(TargetVector[]::new));
    }

}
