package qupath.lib.gui.progress;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.localization.QuPathResources;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Dialog that displays a progress bar and messages from an {@link ObservableProgressMonitor}.
 * <p>
 * Its main benefit is that the progress monitor can be supplied to code that takes a
 * {@link qupath.lib.progress.ProgressMonitor} and may have no JavaFX dependency.
 * <p>
 * By default, the dialog is configured with application modality and is visible
 * only after the progress has been updated and hidden when progress reaches 1.
 *
 * @since v0.8.0
 */
public class ProgressMonitorDialog extends Dialog<Void> {

    private static final Logger logger = LoggerFactory.getLogger(ProgressMonitorDialog.class);

    private static final String STARTING_MESSAGE = QuPathResources.getString("TaskRunnerFX.starting");
    private static final String RUNNING_MESSAGE = QuPathResources.getString("TaskRunnerFX.running");
    private static final String CANCEL_MESSAGE = QuPathResources.getString("TaskRunnerFX.cancelling");
    private static final String COMPLETED_MESSAGE = QuPathResources.getString("TaskRunnerFX.completed");

    private final ObservableProgressMonitor progressMonitor;

    private final Runnable cancellationTask;

    private final long nanosToDisplay;

    private long startTimeNanos = Long.MIN_VALUE;

    private final BooleanProperty cancelPressed = new SimpleBooleanProperty(false);

    private final boolean showOnStart;
    private final boolean hideOnComplete;


    private ProgressMonitorDialog(final Builder builder) {
        super();
        this.nanosToDisplay = builder.delay == null ? 0L : builder.delay.toNanos();
        this.cancellationTask = builder.onCancel;
        this.showOnStart = builder.showOnStart;
        this.hideOnComplete = builder.hideOnComplete;

        this.progressMonitor = builder.progressMonitor == null
                ? ObservableProgressMonitor.createForApplicationThread()
                : builder.progressMonitor;

        this.progressMonitor.isStartedProperty().subscribe(this::handleStarted)
                .and(this.progressMonitor.isCompleteProperty().subscribe(this::handleComplete));

        init(builder);
    }

    /**
     * Create a new builder.
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private void handleStarted(boolean isStarted) {
        if (!showOnStart || !isStarted) {
            return;
        }
        startTimeNanos = System.nanoTime();
        CompletableFuture.delayedExecutor(nanosToDisplay, TimeUnit.NANOSECONDS, Platform::runLater)
                .execute(this::showIfNotComplete);
    }

    private void showIfNotComplete() {
        if (!isComplete()) {
            show();
        }
    }

    private void handleComplete(boolean isComplete) {
        if (!isComplete) {
            return;
        }
        long endTimeNanos = System.nanoTime();
        logger.info(String.format("Processing complete in %.2f seconds", (endTimeNanos - startTimeNanos) / 1e9));
        if (hideOnComplete)
            hide();
    }

    private void init(Builder builder) {

        var progressLabel = new Label();
        progressLabel.textProperty().bind(progressMonitor.messageProperty());

        if (builder.owner != null) {
            initOwner(builder.owner);
        }

        var dialogPane = getDialogPane();

        // Don't permit the dialog
        if (hideOnComplete) {
            // Uses trick from https://www.reddit.com/r/javahelp/comments/39ogiw/cannot_prevent_javafx_dialog_from_closing/
            dialogPane.getScene().getWindow().setOnCloseRequest(e -> {
                if (!isComplete() && !isCancelled()) {
                    e.consume();
                }
            });
        }

        if (builder.showDefaultHeader)
            dialogPane.headerTextProperty().bind(createHeaderTextBinding());

        setTitle(builder.title);

        var progressBar = new ProgressBar();
        progressBar.progressProperty().bind(progressMonitor.progressProperty());

        if (cancellationTask != null) {
            dialogPane.getButtonTypes().add(ButtonType.CANCEL);
            if (getDialogPane().lookupButton(ButtonType.CANCEL) instanceof Button btnCancel) {
                btnCancel.setOnAction(e -> {
                    cancelPressed.set(true);
                    cancellationTask.run();
                    e.consume();
                });
            }
        }

        VBox box = new VBox(progressLabel, progressBar);
        box.setFillWidth(true);
        box.setSpacing(5);
        progressLabel.setMaxWidth(Double.MAX_VALUE);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        box.setPrefWidth(320);
        box.setPadding(new Insets(10, 10, 10, 10));

        initModality(builder.modality);
        dialogPane.setContent(box);
    }


    private StringBinding createHeaderTextBinding() {
        return Bindings.createStringBinding(() -> {
            if (cancelPressed.get())
                return CANCEL_MESSAGE;
            var progress = progressMonitor.getProgress();
            if (progressMonitor.getProgress() <= 0) {
                return STARTING_MESSAGE;
            } else if (progress >= 1) {
                return COMPLETED_MESSAGE;
            } else {
                return RUNNING_MESSAGE;
            }
        }, cancelPressed, progressMonitor.progressProperty());
    }

    private boolean hasStarted() {
        return startTimeNanos != Long.MIN_VALUE;
    }

    private boolean isComplete() {
        return progressMonitor.isComplete();
    }

    private boolean isCancelled() {
        return cancelPressed.get();
    }

    /**
     * Get the progress monitor used by this dialog.
     * @return the progress monitor
     */
    public ObservableProgressMonitor getProgressMonitor() {
        return progressMonitor;
    }

    /**
     * Builder class to customize a progress monitor dialog.
     */
    public static class Builder {

        private Window owner;
        private String title = QuPathResources.getString("TaskRunnerFX.progress");

        private Duration delay = Duration.ofNanos(0L);
        private Runnable onCancel;
        private ObservableProgressMonitor progressMonitor;

        private boolean showDefaultHeader = false;
        private boolean showOnStart = true;
        private boolean hideOnComplete = true;

        private Modality modality = Modality.APPLICATION_MODAL;

        /**
         * Set the dialog owner.
         * @param owner the owner window
         * @return this builder
         */
        public Builder owner(Window owner) {
            this.owner = owner;
            return this;
        }

        /**
         * Set the dialog title.
         * @param title the title
         * @return this builder
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Set the dialog behavior for when the 'cancel' button is pressed.
         * If this is not set, no 'cancel' button is shown by default.
         * <p>
         * A typical example would be to interrupt a calling thread.
         * @param onCancel the runnable to run when cancel is pressed
         * @return this builder
         */
        public Builder onCancel(Runnable onCancel) {
            this.onCancel = onCancel;
            return this;
        }

        /**
         * Optionally specify a delay before which the dialog appears.
         * This takes effect if {@link #showOnStart(boolean)} is set to {@code true}.
         * @param delay the delay to wait before showing the dialog,
         *              after the first progress update is received.
         * @return this builder
         * @see #delayMillis(long)
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Optionally specify a delay in milliseconds before which the dialog appears.
         * This takes effect if {@link #showOnStart(boolean)} is set to {@code true}.
         * @param milliseconds the delay to wait before showing the dialog,
         *              after the first progress update is received.
         * @return this builder
         * @see #delay(Duration)
         */
        public Builder delayMillis(long milliseconds) {
            return delay(Duration.ofMillis(milliseconds));
        }

        /**
         * Optionally set the progress monitor for the dialog to use.
         * If not set, a new progress monitor will be created.
         * @param progressMonitor the progress monitor
         * @return this builder
         */
        public Builder progressMonitor(ObservableProgressMonitor progressMonitor) {
            this.progressMonitor = progressMonitor;
            return this;
        }

        /**
         * Optionally show the dialog automatically after the first progress update,
         * possibly after a delay.
         * Default is {@code true}.
         * @param showOnStart true if the dialog should be shown after the first update,
         *                    false if its visibility will be controlled some other way.
         * @return this builder
         * @see #delay(Duration)
         */
        public Builder showOnStart(boolean showOnStart) {
            this.showOnStart = showOnStart;
            return this;
        }

        /**
         * Optionally hide the dialog automatically after the progress reaches 1.
         * Default is {@code true}.
         * @param hideOnComplete true if the dialog should be hidden when the progress
         *                       is &geq; 1.
         * @return this builder
         */
        public Builder hideOnComplete(boolean hideOnComplete) {
            this.hideOnComplete = hideOnComplete;
            return this;
        }

        /**
         * Set the dialog modality. Default is APPLICATION_MODAL.
         * @param modality the modality
         * @return this builder
         */
        public Builder modality(Modality modality) {
            this.modality = modality;
            return this;
        }

        /**
         * Show the default header text in the dialog pane to indicate whether a task is
         * starting, running, cancelled or completed.
         * @param showDefaultHeader true if the default text should be shown, false otherwise
         * @return this builder
         */
        public Builder showDefaultHeader(boolean showDefaultHeader) {
            this.showDefaultHeader = showDefaultHeader;
            return this;
        }

        /**
         * Build the dialog. This must be called on the JavaFX application thread.
         * @return the dialog
         * @see #buildAsync()
         */
        public ProgressMonitorDialog build() {
            return new ProgressMonitorDialog(this);
        }

        /**
         * Build the dialog, using {@code Platform.runLater} if required.
         * @return a completed future if this is called from the JavaFX application thread,
         *         otherwise a completable future that will contain the result of the
         *         build when available.
         * @see #build()
         */
        public CompletableFuture<ProgressMonitorDialog> buildAsync() {
            if (Platform.isFxApplicationThread()) {
                return CompletableFuture.completedFuture(build());
            } else {
                return CompletableFuture.supplyAsync(this::build, Platform::runLater);
            }
        }


    }

}
