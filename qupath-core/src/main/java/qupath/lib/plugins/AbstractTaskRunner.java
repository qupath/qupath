/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ThreadTools;
import qupath.lib.progress.ProgressMonitor;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Abstract TaskRunner to help with the creation of runners for specific circumstances,
 * e.g. running through a GUI, or from a command line only.
 */
public abstract class AbstractTaskRunner implements TaskRunner {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractTaskRunner.class);
	private static final AtomicLong runnerCounter = new AtomicLong(0L);

	private boolean tasksCancelled = false;

	private final int numThreads;

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
	protected abstract ProgressMonitor makeProgressMonitor();
	
	@Override
	public synchronized void runTasks(String message, Collection<? extends Runnable> tasks) {
		
		if (tasks.isEmpty())
			return;

		// Reset the task counter, so that we can use this again
		var taskCounter = new AtomicLong(0);

		// Reset cancelled status
		tasksCancelled = false;

		List<PathTask> tasksToPostprocess = tasks.stream()
				.filter(t -> t instanceof PathTask)
				.map(t -> (PathTask)t)
				.toList();

		// If we need to postprocess, treat that as one extra task for the purposes of progress monitoring
		int totalTasks = tasksToPostprocess.isEmpty() ? tasks.size() :  tasks.size() + 1;

		// Ensure we have a pool
		ExecutorService pool = createExecutor(numThreads);
		ExecutorCompletionService<Runnable> service = new ExecutorCompletionService<>(pool);

		Set<Future<Runnable>> pendingTasks = new HashSet<>();

		ProgressMonitor monitor = makeProgressMonitor();
		monitor.updateProgress(message, 0);
		for (Runnable task : tasks) {
			// If a task if null, then skip it - otherwise the monitor can get stuck
			if (task == null) {
				logger.warn("Skipping null task");
				continue;
			}
			Future<Runnable> future = service.submit(task, task);
			pendingTasks.add(future);
		}
		// TODO: See if this needs to be shutdown here, or there's a better way..?
		// In any case, it was inhibiting application shutdown just letting it be...
		Exception exception = null;
		try {
			pool.shutdown();
			awaitCompletion(service, monitor, taskCounter, totalTasks, pendingTasks);
		} catch (InterruptedException e) {
			logger.info("Tasks interrupted: {}", e.getMessage(), e);
			tasksCancelled = true;
			exception = e;
		} catch (Exception e) {
			logger.error("Error running task: {}", e.getMessage(), e);
			pool.shutdownNow();
			exception = e;
		} finally {
			pendingTasks.clear();
			try {
				// Post-process any PathTasks
				postProcess(tasksToPostprocess);
			} catch (Exception e) {
				logger.error("Error during postprocessing: {}", e.getMessage(), e);
				exception = e;
			} finally {
				if (exception != null) {
					monitor.complete("Completed with error " + exception.getMessage());
				} else {
					monitor.complete("");
				}
			}
		}
	}


	private static ExecutorService createExecutor(int numThreads) {
		int n = numThreads <= 0 ? ThreadTools.getParallelism() : numThreads;
		var pool = Executors.newFixedThreadPool(n, ThreadTools.createThreadFactory("task-runner-"+runnerCounter.incrementAndGet()+"-", false));
		logger.debug("New threadpool created with {} threads", n);
		return pool;
	}

	
	/**
	 * Await the completion of currently-running tasks, notifying any listener if necessary.
	 */
	private void awaitCompletion(ExecutorCompletionService<Runnable> service, ProgressMonitor monitor, AtomicLong taskCounter, long totalTasks,
								 Collection<Future<Runnable>> pendingTasks) throws InterruptedException, ExecutionException {
		while (!pendingTasks.isEmpty()) {
			// Cancellation has just been detected: cancel all the pending tasks that we can
			if (!tasksCancelled && Thread.interrupted()) {
				pendingTasks.forEach(t -> t.cancel(true));
				tasksCancelled = true;
			}
			// Continue to handle any tasks that have already started
			var future = service.take();
			// If the task finished without being cancelled, update the progress monitor
			if (!future.isCancelled()) {
				Runnable runnable = future.get();
				String message = null;
				if (runnable instanceof PathTask task) {
					message = task.getLastResultsDescription();
				}
				updateMonitor(monitor, message == null ? "Completed" : message, taskCounter, totalTasks);
			} else {
				updateMonitor(monitor, "Task cancelled", taskCounter, totalTasks);
			}
			pendingTasks.remove(future);
		}
	}

	
	/**
	 * Perform post-processing after a task has complete.
	 * <p>
	 * This is necessary to call the taskComplete method (if the Runnable is an instance of PathTask),
	 * and also to update any progress monitor.
	 * <p>
	 * Note: Subclasses may choose to override this method so that it is called on a particular thread
	 * (e.g. with Platform.runLater() or SwingUtilities.invokeLater).
	 * 
	 * @param tasks
	 */
	protected void postProcess(final Collection<? extends PathTask> tasks) {
		boolean wasCancelled = isCancelled();
		for (var task : tasks)
			task.taskComplete(wasCancelled);
	}

	private void updateMonitor(ProgressMonitor monitor, String text, AtomicLong taskCounter, long totalTasks) {
		if (monitor == null)
			return;
		monitor.updateProgress(text, taskCounter.incrementAndGet(), totalTasks);
	}
	
	@Override
	public boolean isCancelled() {
		return tasksCancelled;
	}

}