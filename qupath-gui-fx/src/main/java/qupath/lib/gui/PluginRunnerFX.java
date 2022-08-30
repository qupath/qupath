/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.util.Duration;
import qupath.lib.gui.tools.PaneTools;
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
class PluginRunnerFX extends AbstractPluginRunner<BufferedImage> {

	private static final Logger logger = LoggerFactory.getLogger(PluginRunnerFX.class);
	
	// Time to delay QuPath viewer repaints when running plugin tasks
	private static long repaintDelayMillis = 1000;

	private QuPathGUI qupath;
	//		private ImageData<BufferedImage> imageData; // Consider reinstating - at least as an option

	/**
	 * Constructor.
	 * @param qupath the QuPath instance
	 */
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
		var viewer = qupath == null || repaintDelayMillis <= 0 ? null : qupath.getViewer();
		if (viewer != null)
			viewer.setMinimumRepaintSpacingMillis(repaintDelayMillis);
		try {
			super.runTasks(tasks, updateHierarchy);
		} catch (Exception e) {
			throw(e);
		} finally {
			if (viewer != null)
				viewer.resetMinimumRepaintSpacingMillis();
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

		private static final Logger logger = LoggerFactory.getLogger(PluginProgressMonitorFX.class);

		private static String STARTING_MESSAGE = "Starting...";
		private static String RUNNING_MESSAGE = "Running...";
		private static String CANCEL_MESSAGE = "Cancelling...";
		private static String COMPLETED_MESSAGE = "Completed!";

		private Stage owner;
		
		private Timeline timeline;

		private Dialog<Void> progressDialog;
		private Label progressLabel;
		private ProgressBar progressBar;
		
		private String lastMessage;

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
				panel = PaneTools.createRowGridControls(progressLabel, progressBar);
			} else {
				progressLabel.setTextAlignment(TextAlignment.CENTER);
				panel = PaneTools.createRowGridControls(progressLabel);
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

			// Show dialog after a delay
			Duration duration = millisToDisplay > 0 ? Duration.millis(millisToDisplay) : Duration.millis(500);
			if (timeline == null)
				timeline = new Timeline(new KeyFrame(
						duration,
						e -> updateDialog()));
			if (millisToDisplay > 0)
				timeline.setDelay(duration);
			timeline.setCycleCount(Timeline.INDEFINITE);
			if (!taskComplete)
				timeline.playFromStart();
		}

		@Override
		public boolean cancelled() {
			return cancelPressed;
		}


		@Override
		public void updateProgress(final int progressIncrement, final String message, final ImageRegion region) {
			progress.addAndGet(progressIncrement);
			this.lastMessage = message;
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
			
			if (timeline != null)
				timeline.stop();

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
		
		
		private void updateDialog() {
			if (!progressDialog.isShowing() && !taskComplete)
				progressDialog.show();

			int progressValue = progress.get();
			int progressPercent = (int)Math.round((double)progressValue / maxProgress * 100.0);
			// Update the display
			// Don't update the label if cancel was pressed, since this is probably already giving a more informative message
			if (!cancelPressed)
				progressDialog.getDialogPane().setHeaderText(RUNNING_MESSAGE);

			if (lastMessage == null)
				progressLabel.setText("");
			else
				progressLabel.setText(lastMessage + " (" + progressPercent + "%)");
			if (progressValue >= maxProgress) {
				stopMonitoring(COMPLETED_MESSAGE);
			} else if (progressBar != null)
				progressBar.setProgress((double)progressValue/maxProgress);
		}
		


		private void doDialogClose() {
			permitClose = true;
			if (progressDialog != null)
				progressDialog.close();
			if (timeline != null)
				timeline.stop();
		}

	}
	
	
}