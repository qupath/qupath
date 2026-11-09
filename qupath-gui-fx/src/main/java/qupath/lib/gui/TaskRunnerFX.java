/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui;

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
import qupath.lib.gui.progress.ObservableProgressMonitor;
import qupath.lib.plugins.AbstractTaskRunner;
import qupath.lib.plugins.PathTask;
import qupath.lib.progress.LogProgressMonitor;
import qupath.lib.progress.ProgressMonitor;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Plugin runner that takes care of showing progress (in the appropriate thread) using JavaFX components.
 */
public class TaskRunnerFX extends AbstractTaskRunner {

	private static final Logger logger = LoggerFactory.getLogger(TaskRunnerFX.class);
	
	// Time to delay QuPath viewer repaints when running plugin tasks
	private final static long repaintDelayMillis = 1000L;

	private final QuPathGUI qupath;

	/**
	 * Constructor.
	 * @param qupath the QuPath instance
	 */
	public TaskRunnerFX(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
	}

	/**
	 * Constructor specifying the number of threads.
	 * @param qupath the QuPath instance
	 * @param nThreads the number of threads to use
	 */
	public TaskRunnerFX(final QuPathGUI qupath, final int nThreads) {
		super(nThreads);
		this.qupath = qupath;
	}

	@Override
	public ProgressMonitor makeProgressMonitor() {
		if (Platform.isFxApplicationThread()) {
			logger.warn("Progress monitor cannot be created from JavaFX application thread - will default to command line monitor");
			return LogProgressMonitor.info(LoggerFactory.getLogger(TaskRunnerFX.class));
		} else {
			var thread = Thread.currentThread();
			return ProgressMonitorDialog.builder()
					.owner(qupath.getStage())
					.delay(Duration.ofMillis(250))
					.onCancel(thread::interrupt)
					.build()
					.getProgressMonitor();
		}
	}
	
	@Override
	public synchronized void runTasks(String message, Collection<? extends Runnable> tasks) {
		var viewer = qupath == null || repaintDelayMillis <= 0 ? null : qupath.getViewer();
		if (viewer != null)
			viewer.setMinimumRepaintSpacingMillis(repaintDelayMillis);
		try {
			super.runTasks(message, tasks);
		} finally {
			if (viewer != null) {
				viewer.resetMinimumRepaintSpacingMillis();
			}
		}
	}


	@Override
	protected void postProcess(final Collection<? extends PathTask> tasks) {
		if (!Platform.isFxApplicationThread()) {
			// When running scripts, it's important to make sure we don't return too early before post-processing is complete
			// Failing to do this leads to issues such as intermittent concurrent modification exceptions, or commands needing
			// to be run twice.
			// This aims to ensure that can't happen.
			FutureTask<Boolean> postProcessTask = new FutureTask<>(() -> super.postProcess(tasks), Boolean.TRUE);
			Platform.runLater(postProcessTask);
			try {
				postProcessTask.get();
			} catch (InterruptedException e) {
				logger.error("Interruption during post-processing", e);
			} catch (ExecutionException e) {
				logger.error("Exception during post-processing", e);
			}
		} else {
			super.postProcess(tasks);
		}
	}


	
	
	private static class ProgressMonitorDialog {

		private static final Logger logger = LoggerFactory.getLogger(ProgressMonitorDialog.class);

		private static final String STARTING_MESSAGE = QuPathResources.getString("TaskRunnerFX.starting");
		private static final String RUNNING_MESSAGE = QuPathResources.getString("TaskRunnerFX.running");
		private static final String CANCEL_MESSAGE = QuPathResources.getString("TaskRunnerFX.cancelling");
		private static final String COMPLETED_MESSAGE = QuPathResources.getString("TaskRunnerFX.completed");

		private final Window owner;

		private Dialog<Void> progressDialog;

		private final ObservableProgressMonitor progressMonitor;

		private final Runnable cancellationTask;

		private final long nanosToDisplay;

		private long startTimeNanos = Long.MIN_VALUE;

		private final BooleanProperty cancelPressed = new SimpleBooleanProperty(false);

		private final boolean showOnStart;
		private final boolean hideOnComplete;


		private ProgressMonitorDialog(final Builder builder) {
			this.owner = builder.owner;
			this.nanosToDisplay = builder.delay == null ? 0L : builder.delay.toNanos();
			this.cancellationTask = builder.onCancel;
			this.showOnStart = builder.showOnStart;
			this.hideOnComplete = builder.hideOnComplete;

			this.progressMonitor = builder.progressMonitor == null
					? ObservableProgressMonitor.createForApplicationThread()
					: builder.progressMonitor;

			this.progressMonitor.isStartedProperty().subscribe(this::handleStarted)
					.and(this.progressMonitor.isCompleteProperty().subscribe(this::handleComplete));
		}

		public static Builder builder() {
			return new Builder();
		}

		private void handleStarted(boolean isStarted) {
			if (!showOnStart || !isStarted) {
				return;
			}
			startTimeNanos = System.nanoTime();
			CompletableFuture.delayedExecutor(nanosToDisplay, TimeUnit.NANOSECONDS, Platform::runLater)
					.execute(this::showIfNeeded);
		}

		private void showIfNeeded() {
			if (!isComplete()) {
				createProgressDialog();
				progressDialog.show();
			}
		}

		private void handleComplete(boolean isComplete) {
			if (!isComplete) {
				return;
			}
			long endTimeNanos = System.nanoTime();
			logger.info(String.format("Processing complete in %.2f seconds", (endTimeNanos - startTimeNanos)/1e9));
			if (hideOnComplete && progressDialog != null)
				progressDialog.hide();
		}

		void createProgressDialog() {
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(this::createProgressDialog);
				return;
			}

			var progressLabel = new Label();
			progressLabel.textProperty().bind(progressMonitor.messageProperty());
			progressDialog = new Dialog<>();
			progressDialog.initOwner(owner);
			// Uses trick from https://www.reddit.com/r/javahelp/comments/39ogiw/cannot_prevent_javafx_dialog_from_closing/
			progressDialog.getDialogPane().getScene().getWindow().setOnCloseRequest(e -> {
				if (!isComplete() && !isCancelled()) {
					e.consume();
				}
			});
            progressDialog.getDialogPane().headerTextProperty().bind(createHeaderTextBinding());

			progressDialog.setTitle(QuPathResources.getString("TaskRunnerFX.progress"));


			var progressBar = new ProgressBar();
			progressBar.progressProperty().bind(progressMonitor.progressProperty());

			if (cancellationTask != null) {
				progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
				if (progressDialog.getDialogPane().lookupButton(ButtonType.CANCEL) instanceof Button btnCancel) {
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

			progressDialog.initModality(Modality.APPLICATION_MODAL);
			progressDialog.getDialogPane().setContent(box);
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

		ProgressMonitor getProgressMonitor() {
			return progressMonitor;
		}


		public static class Builder {

			private Window owner;
			private Duration delay = Duration.ofNanos(0L);
			private Runnable onCancel;
			private ObservableProgressMonitor progressMonitor;

			private boolean showOnStart = true;
			private boolean hideOnComplete = true;

			public Builder owner(Window owner) {
				this.owner = owner;
				return this;
			}

			public Builder onCancel(Runnable onCancel) {
				this.onCancel = onCancel;
				return this;
			}

			public Builder delay(Duration delay) {
				this.delay = delay;
				return this;
			}

			public Builder delayMillis(long milliseconds) {
				return delay(Duration.ofMillis(milliseconds));
			}

			public Builder progressMonitor(ObservableProgressMonitor progressMonitor) {
				this.progressMonitor = progressMonitor;
				return this;
			}

			public Builder showOnStart(boolean showOnStart) {
				this.showOnStart = showOnStart;
				return this;
			}

			public Builder hideOnComplete(boolean hideOnComplete) {
				this.hideOnComplete = hideOnComplete;
				return this;
			}

			public ProgressMonitorDialog build() {
				return new ProgressMonitorDialog(this);
			}


		}


	}
	
	
}