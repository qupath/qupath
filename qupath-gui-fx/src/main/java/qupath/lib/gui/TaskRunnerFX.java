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

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.plugins.AbstractTaskRunner;
import qupath.lib.plugins.PathTask;
import qupath.lib.progress.LogProgressMonitor;
import qupath.lib.progress.ProgressMonitor;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

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
			return new PluginProgressMonitorFX(qupath.getStage(), thread::interrupt);
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


	
	
	private static class PluginProgressMonitorFX implements ProgressMonitor {

		private static final Logger logger = LoggerFactory.getLogger(PluginProgressMonitorFX.class);

		private static final String STARTING_MESSAGE = QuPathResources.getString("TaskRunnerFX.starting");
		private static final String RUNNING_MESSAGE = QuPathResources.getString("TaskRunnerFX.running");
		private static final String CANCEL_MESSAGE = QuPathResources.getString("TaskRunnerFX.cancelling");
		private static final String COMPLETED_MESSAGE = QuPathResources.getString("TaskRunnerFX.completed");

		private final Stage owner;

		private final AnimationTimer timer = new AnimationTimer() {
			@Override
			public void handle(long now) {
				handlePulse(now);
			}
		};

		private Dialog<Void> progressDialog;

		private final StringProperty messageText = new SimpleStringProperty();
		private final DoubleProperty progressProperty = new SimpleDoubleProperty(0);

		private final Runnable cancellationTask;

		private final long nanosToDisplay;

		private long startTimeNanos = Long.MIN_VALUE;

		private String currentMessage = null;
		private double currentProgress = 0;

		private final BooleanProperty cancelPressed = new SimpleBooleanProperty(false);

		public PluginProgressMonitorFX(final Stage owner, final Runnable cancellationTask) {
			this(owner, Duration.ofMillis(500), cancellationTask);
		}


		public PluginProgressMonitorFX(final Stage owner, Duration delay, final Runnable cancellationTask) {
			this.owner = owner;
			this.nanosToDisplay = delay.toNanos();
			this.cancellationTask = cancellationTask;
		}


		private void handlePulse(long now) {
			if (currentMessage != null) {
				messageText.set(currentMessage);
			}
			if (isIndeterminate(currentProgress)) {
				progressProperty.set(ProgressBar.INDETERMINATE_PROGRESS);
			} else {
				progressProperty.set(currentProgress);
			}
			if (now - startTimeNanos < nanosToDisplay) {
				return;
			}
			if (isComplete()) {
				stopMonitoring();
			} else if (progressDialog == null) {
				createProgressDialog();
				progressDialog.show();
			}
		}


		void createProgressDialog() {
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(this::createProgressDialog);
				return;
			}

			var progressLabel = new Label();
			progressLabel.textProperty().bind(messageText);
			progressDialog = new Dialog<>();
			progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

			progressDialog.getDialogPane().lookupButton(ButtonType.CANCEL).setVisible(false);
			progressDialog.initOwner(owner);
			// Uses trick from https://www.reddit.com/r/javahelp/comments/39ogiw/cannot_prevent_javafx_dialog_from_closing/
			progressDialog.getDialogPane().getScene().getWindow().setOnCloseRequest(e -> {
				if (!isComplete() && !isCancelled()) {
					e.consume();
				}
			});
            progressDialog.getDialogPane().headerTextProperty().bind(createHeaderTextBinding());

			BorderPane pane = new BorderPane();
			progressDialog.setTitle(QuPathResources.getString("TaskRunnerFX.progress"));


			var progressBar = new ProgressBar();
			progressBar.progressProperty().bind(progressProperty);

			Button btnCancel = new Button(QuPathResources.getString("TaskRunnerFX.cancel"));
			btnCancel.setOnAction(_ -> {
				cancelPressed.set(true);
				if (cancellationTask != null) {
					cancellationTask.run();
				}
			});

			GridPane panel = GridPaneUtils.createRowGridControls(progressLabel, progressBar, btnCancel);

			pane.setCenter(panel);

			progressDialog.initModality(Modality.APPLICATION_MODAL);
			pane.setPadding(new Insets(10, 10, 10, 10));
			progressDialog.getDialogPane().setContent(pane);
		}


		private void stopMonitoring() {
			timer.stop();

			if (progressDialog == null || !progressDialog.isShowing())
				return;

			progressDialog.close();
			long endTimeNanos = System.nanoTime();
			logger.info(String.format("Processing complete in %.2f seconds", (endTimeNanos - startTimeNanos)/1e9));
		}

		private StringBinding createHeaderTextBinding() {
			return Bindings.createStringBinding(() -> {
				if (cancelPressed.get())
					return CANCEL_MESSAGE;
				var progress = progressProperty.get();
				if (progress == 0) {
					return STARTING_MESSAGE;
				} else if (progress == 1) {
					return COMPLETED_MESSAGE;
				} else {
					return RUNNING_MESSAGE;
				}
			}, cancelPressed, progressProperty);
		}

		@Override
		public void updateProgress(String message, double progress) {
			if (progress == 0) {
				if (!hasStarted()) {
					this.startTimeNanos = System.nanoTime();
					timer.start();
				}
			}
			currentMessage = message;
			currentProgress = progress;
		}

		private boolean hasStarted() {
			return startTimeNanos != Long.MIN_VALUE;
		}

		private boolean isComplete() {
			double progress = progressProperty.get();
			return !isIndeterminate(progress) && progress >= 1;
		}

		private boolean isCancelled() {
			return cancelPressed.get();
		}

	}
	
	
}