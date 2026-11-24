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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.progress.ObservableProgressMonitor;
import qupath.lib.gui.progress.ProgressMonitorDialog;
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
		var monitor = ObservableProgressMonitor.createForApplicationThread();
		ProgressMonitorDialog.builder()
					.owner(qupath.getStage())
					.delay(Duration.ofMillis(250))
					.onCancel(Thread.currentThread()::interrupt)
					.progressMonitor(monitor)
					.showWhenRunning()
					.showDefaultHeader(true)
					.buildAsync(); // We only need to build it, we don't need the dialog
		return monitor;
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


}