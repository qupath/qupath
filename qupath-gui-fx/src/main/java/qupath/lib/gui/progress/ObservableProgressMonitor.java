package qupath.lib.gui.progress;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
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

    // By storing the message and progress in the same record, they are always updated together
    // (even if listeners must be informed sequentially).
    private final ObjectProperty<MessageAndProgress> messageAndProgress;

    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(this, "progress", 0);

    private ObservableProgressMonitor(boolean updateOnApplicationThread, String message, double progress) {
        this.updateOnApplicationThread = updateOnApplicationThread;
        this.messageAndProgress = new SimpleObjectProperty<>(this, "messageAndProgress", new MessageAndProgress(message, progress));
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
    public void updateProgress(String message, double progress) {
        if (!updateOnApplicationThread || Platform.isFxApplicationThread()) {
            this.messageAndProgress.set(new MessageAndProgress(message, progress));
        } else {
            Platform.runLater(() -> this.message.set(message == null ? "" : message));
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

    private record MessageAndProgress(String message, double progress) {}

}
