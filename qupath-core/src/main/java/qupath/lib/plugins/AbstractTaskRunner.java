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

package qupath.lib.plugins;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ThreadTools;


/**
 * Abstract PluginRunner to help with the creation of plugin runners for specific circumstances,
 * e.g. running through a GUI, or from a command line only.
 */
public abstract class AbstractTaskRunner implements TaskRunner {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractTaskRunner.class);

	private static int counter = 0;

	private ExecutorService pool;
	private ExecutorCompletionService<Runnable> service;

	private Map<Future<Runnable>, Runnable> pendingTasks = new ConcurrentHashMap<>();
	
	private SimpleProgressMonitor monitor;
	
	private boolean tasksCancelled = false;

	private int numThreads;

	/**
	 * Constructor for a PluginRunner that uses the default number of threads, read from
	 * {@link ThreadTools#getParallelism()}.
	 */
	protected AbstractTaskRunner() {
		this(-1);
	}

	/**
	 * Constructor for a PluginRunner that optionally uses a fixed number of threads.
	 * @param numThreads the number of threads to use, or -1 to use the default number of threads defined by
	 *                   {@link ThreadTools#getParallelism()}.
	 */
	protected AbstractTaskRunner(int numThreads) {
		super();
		this.numThreads = numThreads;
	}

	/**
	 * Create a progress monitor to update the user on what is happening.
	 * @return
	 */
	protected abstract SimpleProgressMonitor makeProgressMonitor();
	
	@Override
	public synchronized void runTasks(String message, Collection<? extends Runnable> tasks) {
		
		if (tasks.isEmpty())
			return;
		
		// Reset cancelled status
		tasksCancelled = false;
		
		// Ensure we have a pool
		if (pool == null || pool.isShutdown()) {
			int n = numThreads <= 0 ? ThreadTools.getParallelism() : numThreads;
			pool = Executors.newFixedThreadPool(n, ThreadTools.createThreadFactory("task-runner-"+(++counter)+"-", false));
			logger.debug("New threadpool created with {} threads", n);
			service = new ExecutorCompletionService<>(pool);
		} else if (service == null)
			service = new ExecutorCompletionService<>(pool);
		
		monitor = makeProgressMonitor();
		monitor.startMonitoring(message, tasks.size(), true);
		for (Runnable task : tasks) {
			// If a task if null, then skip it - otherwise the monitor can get stuck
			if (task == null) {
				logger.warn("Skipping null task");
				continue;
			}
			Future<Runnable> future = service.submit(task, task);
			pendingTasks.put(future, task);
		}
		// TODO: See if this needs to be shutdown here, or there's a better way..?
		// In any case, it was inhibiting application shutdown just letting it be...
		pool.shutdown();
		awaitCompletion();
		
		// Post-process any PathTasks
		postProcess(tasks.stream().filter(t -> t instanceof PathTask).map(t -> (PathTask)t).toList());
	}

	
	/**
	 * Await the completion of currently-running tasks, notifying any listener if necessary.
	 */
	protected void awaitCompletion() {
		try {
			while (!pendingTasks.isEmpty()) {
				Future<Runnable> future = null;
				// Check if the monitor has been cancelled; if so, do any post processing if a task is available and otherwise cancel remaining ones
				if (!tasksCancelled && monitor != null && monitor.cancelled() && (future = service.poll()) == null) {
					// Cancel all enqueued tasks
					for (Future<?> entry : pendingTasks.keySet().toArray(new Future<?>[0])) {
						if (entry.cancel(true)) {
							pendingTasks.remove(entry);
							monitor.updateProgress(1, "", null);
						} else
							logger.debug("Cancel returned false for {}", entry);
					}
					tasksCancelled = true;
				}
				future = future == null ? service.take() : future;
//				logger.warn("Future: {}", future);
				// If the task finished without being cancelled, run post-processing if required & update the progress monitor
				if (!future.isCancelled()) {
					Runnable runnable = future.get();
					PathTask task = runnable instanceof PathTask ? (PathTask)runnable : null;
					updateMonitor(task);
				}
				pendingTasks.remove(future);
			}
			if (monitor != null)
				monitor.pluginCompleted("");
		} catch (InterruptedException e) {
			logger.error("Plugin interrupted: {}", e.getMessage(), e);
			monitor.pluginCompleted("Completed with error " + e.getMessage());
		} catch (ExecutionException e) {
			logger.error("Error running plugin: {}", e.getMessage(), e);
			if (pool != null)
				pool.shutdownNow();
			monitor.pluginCompleted("Completed with error " + e.getMessage());
		} catch (Exception e) {
			logger.error("Error running plugin: {}", e.getMessage(), e);
			if (pool != null)
				pool.shutdownNow();
			monitor.pluginCompleted("Completed with error " + e.getMessage());
		} finally {
			pendingTasks.clear();
		}
	}

	
	/**
	 * Perform post-processing after a task has complete.
	 * 
	 * This is necessary to call the taskComplete method (if the Runnable is an instance of PathTask),
	 * and also to update any progress monitor.
	 * 
	 * Note: Subclasses may choose to override this method so that it is called on a particular thread
	 * (e.g. with Platform.runLater() or SwingUtilities.invokeLater).
	 * 
	 * @param tasks
	 */
	protected void postProcess(final Collection<? extends PathTask> tasks) {
		boolean wasCancelled = tasksCancelled || monitor.cancelled();
		for (var task : tasks)
			task.taskComplete(wasCancelled);

		// Removed v0.5.0 - TODO: Check if should be reinstated
//		var imageData = getImageData();
//		if (imageData != null)
//			imageData.getHierarchy().fireHierarchyChangedEvent(this);
	}
	
	private void updateMonitor(final PathTask task) {
		if (monitor == null)
			return;
		String text = task == null ? "Completed" : task.getLastResultsDescription();
		monitor.updateProgress(1, text, null);
	}
	
	@Override
	public boolean isCancelled() {
		return tasksCancelled;
	}
	

}