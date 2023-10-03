/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.workflow.SimplePluginWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;

/**
 * General abstract plugin implementation, which defines some methods to facilitate 
 * creating plugins that do parallel processing.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class AbstractPlugin<T> implements PathPlugin<T> {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractPlugin.class);
	
	
	/**
	 * Get a collection of tasks to perform.
	 * 
	 * This will be called from {@link #runPlugin(TaskRunner, ImageData, String)} <b>after</b> a call to {@link #parseArgument(ImageData, String)}.
	 *
	 * The default implementation simply calls {@link #getParentObjects(ImageData)}, then {@link #addRunnableTasks(ImageData, PathObject, List)}
	 * for every parent object that was returned.
	 * 
	 * @param imageData
	 * @return
	 */
	protected Collection<Runnable> getTasks(final ImageData<T> imageData) {
		Collection<? extends PathObject> parentObjects = getParentObjects(imageData);
		if (parentObjects == null || parentObjects.isEmpty())
			return Collections.emptyList();
		
		List<Runnable> tasks = new ArrayList<>(parentObjects.size());
		
		long startTime = System.currentTimeMillis();
		for (PathObject pathObject : parentObjects) {
			addRunnableTasks(imageData, pathObject, tasks);
		}
		long endTime = System.currentTimeMillis();
		logger.debug("Time to add plugin tasks: {} ms", endTime - startTime);
		return tasks;
	}
	
	/**
	 * Optionally request a hierarchy update after the tasks have run.
	 * Default implementation returns true.
	 * @return
	 */
	protected boolean requestHierarchyUpdate() {
		return true;
	}
	
	/**
	 * Parse the input argument, returning 'true' if the argument is valid and it's possible to run the plugin.
	 * <p>
	 * This is called from within runPlugin.
	 * If it returns 'true', getTasks will be called and then runTasks will submit these to the plugin runner to run.
	 * If it returns 'false', runPlugin will immediately abort and return false as well.
	 * 
	 * Since this could result in some internal variables changed (e.g. a ParameterList), implementing classes can't
	 * be assumed to be thread-safe; plugins should be created and called from a single thread, although they may use
	 * multiple threads (via a PluginRunner) to complete their tasks.
	 * 
	 * 
	 * @param imageData
	 * @param arg
	 * @return
	 */
	protected abstract boolean parseArgument(ImageData<T> imageData, String arg);
	
	
	/**
	 * Get a collection of objects to process, based on the contents of the PluginRunner.
	 * 
	 * This could (for example) return the selected object, the root object, all detection objects... depending upon what the plugin does.
	 * 
	 * Each object this returns will be passed to addRunnableTasks to create a task to run.
	 * 
	 * In practice, this method can be overridden to return anything/nothing if getTasks is overridden instead.
	 * 
	 * @param imageData
	 * @return
	 */
	protected abstract Collection<? extends PathObject> getParentObjects(final ImageData<T> imageData);

	
	/**
	 * For a specified parent object, generate a task to run.
	 * 
	 * In practice, this method can be overridden to return anything/nothing if getTasks is overridden instead.
	 * 
	 * @param imageData
	 * @param parentObject
	 * @param tasks
	 */
	protected abstract void addRunnableTasks(final ImageData<T> imageData, final PathObject parentObject, List<Runnable> tasks);		

	
	
	@Override
	public boolean runPlugin(final TaskRunner taskRunner, ImageData<T> imageData, final String arg) {

		if (!parseArgument(imageData, arg))
			return false;

		preprocess(taskRunner, imageData);

		Collection<Runnable> tasks = getTasks(imageData);
		if (tasks.isEmpty())
			return false;

		taskRunner.runTasks(tasks);

		if (requestHierarchyUpdate())
			imageData.getHierarchy().fireHierarchyChangedEvent(this);

		postprocess(taskRunner, imageData);
		
		if (taskRunner.isCancelled())
			return false;

		// Only add a workflow step if plugin was not cancelled
		addWorkflowStep(imageData, arg);
		
		return true;
	}
	
	
	/**
	 * Called after parsing the argument String, and immediately before creating &amp; running any generated tasks.
	 * 
	 * Does nothing by default.
	 * @param taskRunner
	 * @param imageData
	 */
	protected void preprocess(final TaskRunner taskRunner, final ImageData<T> imageData) {};

	/**
	 * Called immediately after running any generated tasks.
	 * 
	 * Does nothing by default.
	 * @param taskRunner
	 * @param imageData
	 */
	protected void postprocess(final TaskRunner taskRunner, final ImageData<T> imageData) {};

	
	/**
	 * Add a workflow step to the ImageData indicating the argument that this plugin was run with.
	 * 
	 * Subclasses may override this if a better workflow step should be logged.
	 * 
	 * A subclass may also override this to avoid adding a workflow step at all.
	 * 
	 * @param imageData
	 * @param arg
	 */
	protected void addWorkflowStep(final ImageData<T> imageData, final String arg) {
		@SuppressWarnings("unchecked")
		WorkflowStep step = new SimplePluginWorkflowStep(getName(), (Class<? extends PathPlugin<T>>)getClass(), arg);
		imageData.getHistoryWorkflow().addStep(step);
		logger.debug("Adding workflow step: {}", step);
	}
	
	
	/**
	 * Test method for rearranging a collection so that entries are interleaved with a regularity given by stride.
	 * <p>
	 * It can be used to rearrange tasks to try to make better use of cached image regions, by helping to ensure that
	 * all available processors are operating on distinct parts of the image - rather than all in the same region,
	 * where image tile requests could become a bottleneck.
	 * 
	 * Intended use would be something like the following:
	 * <pre>
	 *   int n = tasks.size();
	 *	 Runnable[] tasks2 = new Runnable[n];
 	 *   if (rearrangeByStride(tasks, tasks2, Runtime.getRuntime().availableProcessors()))
	 *     tasks = Arrays.asList(tasks2);
	 * </pre>
	 * 
	 * @param input
	 * @param output
	 * @param stride
	 * @return
	 */
	protected static <T> boolean rearrangeByStride(final Collection<T> input, final T[] output, final int stride) {
		int n = input.size();
		int ind = 0;
		int startInd = 0;
		int indNew = 0;
		for (T t : input) {
			if (output[indNew] != null) {
				// This shouldn't happen if I've done this properly... but in case I haven't, log an error and
				// default to original list
				logger.error("Not null for " + indNew + " (" + ind + "/" + n + ")");
				return false;
//				return tasks;
			}
			output[indNew] = t;
			ind++;
			indNew += stride;
			if (indNew >= n) {
				startInd++;
				indNew = startInd;
			}
		}
		return true;
	}

}
