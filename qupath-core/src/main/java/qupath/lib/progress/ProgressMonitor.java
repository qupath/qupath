package qupath.lib.progress;

import java.util.function.DoubleUnaryOperator;

/**
 * Simple progress monitor for use within long-running methods.
 * <p>
 * This enables different ways to displaying progress, for example through log messages or a graphical interface.
 *
 * @since v0.8.0
 */
@FunctionalInterface
public interface ProgressMonitor {

    /**
     * Default progress monitor that ignores all updates.
     */
    ProgressMonitor NULL = (m, p) -> {
    };

    /**
     * Handle a progress update.
     *
     * @param message  message to display
     * @param progress progress value between 0 (start) and 1 (complete).
     *                 Values &lt; 0 or non-finite values indicate that the progress is indeterminate
     *                 (for example, when a task is initializing).
     */
    void updateProgress(String message, double progress);

    /**
     * Update the progress as a proportion of some maximum value.
     * <p>
     * This is a convenience that simply calls {@code updateProgresss(message, progress/maxValue}.
     * @param message the message
     * @param progress the current progress
     * @param maxValue the maximum value, corresponding to the progress being complete
     */
    default void updateProgress(String message, double progress, double maxValue) {
        updateProgress(message, progress / maxValue);
    }

    /**
     * Update the progress monitor message, but with indeterminate progress.
     * @param message the new message
     */
    default void updateProgress(String message) {
        updateProgress(message, Double.NaN);
    }

    /**
     * Check whether a progress value is indeterminate, i.e. not a finite value &geq; 0.
     *
     * @param progress the progress to check
     * @return true if the progress is indeterminate, false otherwise
     */
    default boolean isIndeterminate(double progress) {
        return progress < 0 || !Double.isFinite(progress);
    }

    /**
     * Combine two progress monitors, applying update messages sequentially.
     *
     * @param other the second progress monitor
     * @return a new progress monitor that updates both this and another
     */
    default ProgressMonitor and(ProgressMonitor other) {
        return (String message, double progress) -> {
            updateProgress(message, progress);
            other.updateProgress(message, progress);
        };
    }

    /**
     * Clamp progress values within the specified range.
     * @param minValue the minimum value (between 0 and 1)
     * @param maxValue the maximum value (between 0 and 1)
     * @return the new progress monitor, which clamps input progress values between the specified range
     */
    default ProgressMonitor clamp(double minValue, double maxValue) {
        return transformDeterminate(d -> Math.clamp(d, minValue, maxValue));
    }

    /**
     * Linearly rescale progress values to fall within the specified range.
     * <p>
     * This is useful if a progress monitor should be passed to different methods,
     * since it allows (for example) the first half of the process to be managed by one method and
     * the second half by the next method.
     * @param minValue the minimum value, corresponding to an input progress of 0
     * @param maxValue the maximum value, corresponding to an input progress of 1
     * @return a progress monitor that remaps progress values to the specified range
     * @throws IllegalArgumentException if minValue or maxValue are outside the range 0-1, or either is non-finite
     */
    default ProgressMonitor rescale(double minValue, double maxValue) throws IllegalArgumentException{
        if (minValue < 0 || maxValue < 0 || minValue > 1 || maxValue > 1 || !Double.isFinite(minValue) || !Double.isFinite(maxValue)) {
            throw new IllegalArgumentException("Invalid min/max values: " + minValue + "-" + maxValue);
        }
        double diff = maxValue - minValue;
        return transformDeterminate(d -> d * diff + minValue);
    }

    /**
     * Create a progress monitor that transforms the progress value before it is passed to update progress.
     * This is applied to all progress values, even if {@link #isIndeterminate(double)} returns {@code true}.
     * @param operator the operator
     * @return the transformed progress monitor
     */
    default ProgressMonitor transform(DoubleUnaryOperator operator) {
        return (String message, double progress) -> updateProgress(message, operator.applyAsDouble(progress));
    }

    /**
     * Create a progress monitor that transforms the progress value before it is passed to update progress.
     * This is applied to only progress values where {@link #isIndeterminate(double)} returns {@code false}.
     * @param operator the operator
     * @return the transformed progress monitor
     */
    default ProgressMonitor transformDeterminate(DoubleUnaryOperator operator) {
        return (String message, double progress) -> {
            if (isIndeterminate(progress))
                updateProgress(message, progress);
            else
                updateProgress(message, operator.applyAsDouble(progress));
        };
    }

}
