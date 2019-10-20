/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.plugins;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.AbstractPluginRunner;
import qupath.lib.plugins.SimpleProgressMonitor;
import qupath.lib.regions.ImageRegion;

/**
 * Plugin runner that takes care of showing progress (in the appropriate thread) using JavaFX components.
 * 
 * @author Pete Bankhead
 *
 */
public class PluginRunnerFX extends AbstractPluginRunner<BufferedImage> {

	final private static Logger logger = LoggerFactory.getLogger(PluginRunnerFX.class);
	
	// Time to delay QuPath viewer repaints when running plugin tasks
	private static long repaintDelayMillis = 2000;

	private QuPathGUI qupath;
	//		private ImageData<BufferedImage> imageData; // Consider reinstating - at least as an option

	public PluginRunnerFX(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
		//			this.imageData = qupath.getImageData();
	}

	@Override
	public SimpleProgressMonitor makeProgressMonitor() {
		if (Platform.isFxApplicationThread()) {
			logger.warn("Progress monitor cannot be created from JavaFX application thread - will default to command line monitor");
			return new CommandLinePluginRunner.CommandLineProgressMonitor();
		} else
			return new PluginProgressMonitorFX(qupath.getStage());
	}
	
	@Override
	public synchronized void runTasks(Collection<Runnable> tasks, boolean updateHierarchy) {
		boolean delayRepaints = qupath != null && qupath.getViewer() != null && repaintDelayMillis > 0;
		if (delayRepaints)
			qupath.getViewer().setMinimumRepaintSpacingMillis(repaintDelayMillis);
		try {
			super.runTasks(tasks, updateHierarchy);
		} catch (Exception e) {
			throw(e);
		} finally {
			if (delayRepaints)
				qupath.getViewer().resetMinimumRepaintSpacingMillis();
		}
	}

	@Override
	public ImageData<BufferedImage> getImageData() {
		//			return imageData;
		return qupath.getImageData();
	}


	@Override
	protected void postProcess(final Collection<PathTask> tasks) {
		if (!Platform.isFxApplicationThread()) {
			// When running scripts, it's important to make sure we don't return too early before post-processing is complete
			// Failing to do this leads to issues such as intermittent concurrent modification exceptions, or commands needing
			// to be run twice
			// This aims to ensure that can't happen
			FutureTask<Boolean> postProcessTask = new FutureTask<>(() -> {
					super.postProcess(tasks);
			}, Boolean.TRUE);
			Platform.runLater(postProcessTask);
			try {
				postProcessTask.get();
			} catch (InterruptedException e) {
				logger.error("Interruption during post-processing", e);
			} catch (ExecutionException e) {
				logger.error("Exception during post-processing", e);
			}
//			Platform.runLater(() -> postProcess(runnable));
			return;
		}
		super.postProcess(tasks);
	}


	
	
	static class PluginProgressMonitorFX implements SimpleProgressMonitor {

		final private static Logger logger = LoggerFactory.getLogger(PluginProgressMonitorFX.class);

		private static String STARTING_MESSAGE = "Starting...";
		private static String RUNNING_MESSAGE = "Running...";
		private static String CANCEL_MESSAGE = "Cancelling...";
		private static String COMPLETED_MESSAGE = "Completed!";

		private Stage owner;

		private Dialog<Void> progressDialog;
		private Label progressLabel;
		private ProgressBar progressBar;

		private AtomicLong lastUpdateTimestamp = new AtomicLong(0);
		private AtomicInteger progress = new AtomicInteger(0);
		private int maxProgress;
		private int millisToDisplay;
		private boolean taskComplete = false;

		private boolean permitClose = false;

		private long startTimeMS = 0;

		private boolean cancelPressed = false;

		public PluginProgressMonitorFX(final Stage owner) {
			this(owner, 500);
		}


		public PluginProgressMonitorFX(final Stage owner, int millisToDisplay) {
			this.owner = owner;
			this.millisToDisplay = millisToDisplay;
		}


		@Override
		public void startMonitoring(final String message, final int maxProgress, final boolean mayCancel) {

			if (progressDialog != null)
				throw new UnsupportedOperationException("Unsupported attempt to reuse a plugin progress monitor!");

			this.startTimeMS = System.currentTimeMillis();
			this.maxProgress = maxProgress;
			this.progress.set(0);

			createProgressDialog(message, mayCancel);
		}


		void createProgressDialog(final String message, final boolean mayCancel) {
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(() -> createProgressDialog(message, mayCancel));
				return;
			}

			taskComplete = false;

			if (maxProgress > 1)
				progressLabel = new Label("Starting " + maxProgress + " tasks...");
			else
				progressLabel = new Label("");
			progressDialog = new Dialog<>();
			progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

			((Button)progressDialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setVisible(false);
			//			((Button)progressDialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setOnAction(e -> {
			//				progressDialog.getDialogPane().setHeaderText(CANCEL_MESSAGE);
			//				progressLabel.setText("Completing tasks currently in progress...");
			//				cancelPressed = true; //!plugin.cancelPlugin();
			//			});
			progressDialog.initOwner(owner);
			// Uses trick from https://www.reddit.com/r/javahelp/comments/39ogiw/cannot_prevent_javafx_dialog_from_closing/
			progressDialog.getDialogPane().getScene().getWindow().setOnCloseRequest(e -> {
				if (!permitClose)
					e.consume();
			});
			//			progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
			if (message == null)
				progressDialog.getDialogPane().setHeaderText(STARTING_MESSAGE);
			else
				progressDialog.getDialogPane().setHeaderText(message);

			BorderPane pane = new BorderPane();
			progressDialog.setTitle("Progress");

			int nRows = 2;
			if (maxProgress > 1)
				nRows++;
			if (mayCancel)
				nRows++;		

			GridPane panel;
			if (maxProgress > 1) {
				progressBar = new ProgressBar();
				panel = PaneToolsFX.createRowGridControls(progressLabel, progressBar);
			} else {
				progressLabel.setTextAlignment(TextAlignment.CENTER);
				panel = PaneToolsFX.createRowGridControls(progressLabel);
			}
			if (mayCancel) {
				Button btnCancel = new Button("Cancel");
				panel.add(btnCancel, 0, nRows-1);
				btnCancel.prefWidthProperty().bind(panel.widthProperty());
				btnCancel.setOnAction(e -> {
					progressDialog.getDialogPane().setHeaderText(CANCEL_MESSAGE);
					//						progressLabel.setText("Completing tasks currently in progress...");
					cancelPressed = true; //!plugin.cancelPlugin();
				});
			}
			pane.setCenter(panel);

			progressDialog.initModality(Modality.APPLICATION_MODAL);
			pane.setPadding(new Insets(10, 10, 10, 10));
			progressDialog.getDialogPane().setContent(pane);
			//			progressDialog.setScene(new Scene(dialogPane));
			//			progressDialog.initStyle(StageStyle.UNDECORATED);
			//			progressDialog.setOnCloseRequest(e -> {
			//				System.err.println("Source: " + e.getSource());
			//				e.consume();
			//			}); // Thwart closing requests
			//			progressDialog.setMinWidth(Math.max(progressDialog.getMinWidth(), 450));

			// Show dialog after a delay
			if (millisToDisplay > 0) {

				java.util.Timer timer = new java.util.Timer("Plugin-progress-timer", true);
				timer.schedule(new TimerTask() {
					@Override
					public void run() {
						if (!taskComplete) {
							Platform.runLater(() -> {
								//								long startTime = System.currentTimeMillis();
								if (!taskComplete) {
									progressDialog.show();
									logger.trace("Starting progress monitor...");
								}
								//								long endTime = System.currentTimeMillis();
								//								logger.trace("Progress monitor visible time " + (endTime-startTime) + " ms");
							});
						}
					}},
						millisToDisplay);
			} else
				progressDialog.show();
		}

		@Override
		public boolean cancelled() {
			return cancelPressed;
		}


		@Override
		public void updateProgress(final int progressIncrement, final String message, final ImageRegion region) {
			int currentProgress = progress.addAndGet(progressIncrement);
			if (Platform.isFxApplicationThread())
				updateProgressDisplay(0, message, region);
			else {
				// TODO: Defer updates if unfinished for at least ~250 ms
				// Avoiding too many calls isn't so critical that it requires synchronization overhead
				//				synchronized(lastUpdateTimestamp) {
				long currentTime = System.currentTimeMillis();
				if (currentProgress >= maxProgress || currentTime - lastUpdateTimestamp.get() > 250) {
					lastUpdateTimestamp.set(currentTime);
					Platform.runLater(() ->	updateProgressDisplay(0, message, region));
				}
			}
		}

		@Override
		public void pluginCompleted(final String message) {
			stopMonitoring(message);
		}


		void stopMonitoring(final String message) {
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(() -> stopMonitoring(message));
				return;
			}

			if (taskComplete && (progressDialog == null || !progressDialog.isShowing()))
				return;

			taskComplete = true;
			if (progressDialog != null) {
				// We need to enable responding to close requests again
				doDialogClose();
			}
			long endTime = System.currentTimeMillis();
			logger.info(String.format("Processing complete in %.2f seconds", (endTime - startTimeMS)/1000.));
			if (message != null && message.trim().length() > 0)
				logger.info(message);
		}


		// This should only be called from the Event Dispatch Thread
		// TODO: MAKE USE OF IMAGEREGION WHEN UPDATING THE PROGRESS DISPLAY!
		void updateProgressDisplay(final int progressIncrement, final String message, final ImageRegion region) {
			int progressValue;
			if (progressIncrement > 0)
				progressValue = progress.addAndGet(progressIncrement);
			else
				progressValue = progress.get();
			int progressPercent = (int)((double)progressValue / maxProgress * 100 + .5);
			// Update the display
			if (progressDialog != null) {

				// Don't update the label if cancel was pressed, since this is probably already giving a more informative message
				if (!cancelPressed)
					progressDialog.getDialogPane().setHeaderText(RUNNING_MESSAGE);

				progressLabel.setText(message + " (" + progressPercent + "%)");
				if (progressValue >= maxProgress) {
					stopMonitoring(COMPLETED_MESSAGE);
				} else if (progressBar != null)
					progressBar.setProgress((double)progressValue/maxProgress);
			}
		}


		private void doDialogClose() {
			permitClose = true;
			if (progressDialog != null)
				progressDialog.close();
		}

	}
	
	
}