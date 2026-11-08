package qupath.lib.gui.progress;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import qupath.lib.progress.ProgressMonitor;

/**
 * Progress monitor that stores the most recent message and progress values in JavaFX properties,
 * which can optionally be updated only on the JavaFX application thread.
 */
public class ObservableProgressMonitor implements ProgressMonitor {

    private final boolean updateOnApplicationThread;

    private volatile boolean updateRequested = false;
    private volatile MessageAndProgress lastMessageAndProgress;

    // By storing the message and progress in the same record, they are always updated together
    // (even if listeners must be informed sequentially).
    private final ObjectProperty<MessageAndProgress> messageAndProgress;

    private final ReadOnlyBooleanWrapper isStarted = new ReadOnlyBooleanWrapper(this, "isStarted", false);
    private final ReadOnlyBooleanWrapper isComplete = new ReadOnlyBooleanWrapper(this, "isComplete", false);
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(this, "progress", 0);

    private ObservableProgressMonitor(boolean updateOnApplicationThread, String message, double progress) {
        this.updateOnApplicationThread = updateOnApplicationThread;
        this.messageAndProgress = new SimpleObjectProperty<>(this, "messageAndProgress", new MessageAndProgress(message, progress, false, false));
        this.isStarted.bind(messageAndProgress.map(MessageAndProgress::isStarted));
        this.isComplete.bind(messageAndProgress.map(MessageAndProgress::isComplete));
        this.message.bind(messageAndProgress.map(MessageAndProgress::message));
        this.progress.bind(messageAndProgress.map(MessageAndProgress::progress));
    }

    /**
     * Create an observable progress monitor that updates properties only on the JavaFX application thread.
     * The initial message is an empty string, and progress is 0.
     * @return the progress monitor
     */
    public static ObservableProgressMonitor createForApplicationThread() {
        return createForApplicationThread("", 0);
    }

    /**
     * Create an observable progress monitor that updates properties only on the JavaFX application thread.
     * @param message the initial message
     * @param progress the initial progress value
     * @return the progress monitor
     */
    public static ObservableProgressMonitor createForApplicationThread(String message, double progress) {
        return new ObservableProgressMonitor(true, message, progress);
    }

    /**
     * Create an observable progress monitor that updates properties immediately, irrespective of the thread
     * used to make the update. Listeners may be attached to the monitor's properties, but they should not be
     * bound to the UI.
     * The initial message is an empty string, and progress is 0.
     * @return the progress monitor
     */
    public static ObservableProgressMonitor createForAnyThread() {
        return createForAnyThread("", 0);
    }

    /**
     * Create an observable progress monitor that updates properties immediately, irrespective of the thread
     * used to make the update. Listeners may be attached to the monitor's properties, but they should not be
     * bound to the UI.
     * @param message the initial message
     * @param progress the initial progress value
     * @return the progress monitor
     */
    public static ObservableProgressMonitor createForAnyThread(String message, double progress) {
        return new ObservableProgressMonitor(false, message, progress);
    }

    @Override
    public synchronized void updateProgress(String message, double progress) {
        // We want to avoid flooding Platform.runLater with runnables in the event that many small tasks are making
        // progress updates from different threads before the next application thread pulse.
        // That's why we set a local variable first, then request that the update is handled.
        this.lastMessageAndProgress = new MessageAndProgress(message, progress, true, progress >= 1);
        requestUpdate();
    }

    private void requestUpdate() {
        if (!updateOnApplicationThread || Platform.isFxApplicationThread()) {
            this.messageAndProgress.set(lastMessageAndProgress);
            updateRequested = false;
        } else if (!updateRequested) {
            updateRequested = true;
            Platform.runLater(this::requestUpdate);
        }
    }

    /**
     * String property storing the most recently set message.
     */
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    public String getMessage() {
        return messageProperty().get();
    }

    /**
     * Double property storing the most recently set progress value.
     */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    public double getProgress() {
        return progressProperty().get();
    }

    /**
     * Boolean property that is set to true after the first call to {@link #updateProgress(String, double)};
     */
    public ReadOnlyBooleanProperty isStartedProperty() {
        return isStarted.getReadOnlyProperty();
    }

    public boolean isStarted() {
        return isStartedProperty().get();
    }

    /**
     * Boolean property that is set to true when the progress value &geq; 1.
     */
    public ReadOnlyBooleanProperty isCompleteProperty() {
        return isComplete.getReadOnlyProperty();
    }

    public boolean isComplete() {
        return isCompleteProperty().get();
    }

    private record MessageAndProgress(String message, double progress, boolean isStarted, boolean isComplete) {}

}
