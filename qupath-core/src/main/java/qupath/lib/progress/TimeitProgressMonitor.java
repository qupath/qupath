package qupath.lib.progress;

import qupath.lib.common.Timeit;

import java.util.Objects;

/**
 * Progress monitor that adds checkpoints to a {@link Timeit} on every progress update.
 * <p>
 * This may be useful for benchmarking any task that takes a progress monitor.
 */
public class TimeitProgressMonitor implements ProgressMonitor {

    private final ProgressMonitor monitor;
    private final Timeit timeit;

    /**
     * Create a basic progress monitor that only logs times.
     */
    public static TimeitProgressMonitor create() {
        return create(ProgressMonitor.NULL);
    }

    /**
     * Create a timeit progress monitor that logs times,
     * then updates another progress monitor.
     * @param monitor the monitor to update
     */
    public static TimeitProgressMonitor create(ProgressMonitor monitor) {
        return new TimeitProgressMonitor(monitor, new Timeit());
    }

    /**
     * Create a timeit progress monitor that logs times,
     * then updates another progress monitor.
     * @param monitor the monitor to update
     * @param timeit the timeit to use (must not be null)
     */
    public static TimeitProgressMonitor create(ProgressMonitor monitor, Timeit timeit) {
        Objects.requireNonNull(monitor);
        Objects.requireNonNull(timeit);
        return new TimeitProgressMonitor(monitor, timeit);
    }


    private TimeitProgressMonitor(ProgressMonitor monitor, Timeit timeit) {
        Objects.requireNonNull(timeit);
        this.monitor = monitor == null ? ProgressMonitor.NULL : monitor;
        this.timeit = timeit;
    }

    @Override
    public void updateProgress(String message, double progress) {
        timeit.checkpoint(message);
        monitor.updateProgress(message, progress);
    }

    /**
     * Get the timeit object that records progress updates.
     * @return the timeit
     */
    public Timeit getTimeit() {
        return timeit;
    }

}
