/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import qupath.lib.common.ThreadTools;


/**
 * Manager to simplify submitting short tasks in background threads using a shared {@link ExecutorService}.
 * <p>
 * This can also create a reusable single-thread {@link ExecutorService} using an object as a key.
 * 
 * @author Pete Bankhead
 * @since v0.5.0 (replacing functionality previously in {@link QuPathGUI}
 */
public class SharedThreadPoolManager implements AutoCloseable {
	
	// ExecutorServices for single & multiple threads
	private Map<Object, ExecutorService> mapSingleThreadPools = new HashMap<>();
	private ExecutorService poolMultipleThreads = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), ThreadTools.createThreadFactory("qupath-shared-", false));	

	private SharedThreadPoolManager() {}
	
	/**
	 * Create a new instance
	 * @return
	 */
	public static SharedThreadPoolManager create() {
		return new SharedThreadPoolManager();
	}
	
	/**
	 * Get a reusable executor using a single thread, creating a new executor if needed.
	 * <p>
	 * An owner can be specified, in which case the same Executor will be returned for the owner 
	 * for so long as the Executor has not been shut down; if it has been shut down, a new Executor will be returned.
	 * <p>
	 * Specifying an owner is a good idea if there is a chance that any submitted tasks could block,
	 * since the same Executor will be returned for all requests that give a null owner.
	 * <p>
	 * The advantage of using this over creating an ExecutorService some other way is that
	 * shutdown will be called on any pools created this way whenever QuPath is quit.
	 * 
	 * @param owner
	 * @return 
	 * 
	 */
	public ExecutorService getSingleThreadExecutor(final Object owner) {
		ExecutorService pool = mapSingleThreadPools.get(owner);
		if (pool == null || pool.isShutdown()) {
			pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory(owner.getClass().getSimpleName().toLowerCase() + "-", false));
			mapSingleThreadPools.put(owner, pool);
		}
		return pool;
	}
	
	/**
	 * Create a completion service that uses a shared threadpool for the application.
	 * @param <V> 
	 * 
	 * @param cls
	 * @return 
	 */
	public <V> ExecutorCompletionService<V> createSharedPoolCompletionService(Class<V> cls) {
		return new ExecutorCompletionService<>(poolMultipleThreads);
	}
	
	/**
	 * Submit a short task to a shared thread pool
	 * 
	 * @param runnable
	 */
	public void submitShortTask(final Runnable runnable) {
		poolMultipleThreads.submit(runnable);
	}


	/**
	 * Shutdown any threadpools created by this manager.
	 */
	@Override
	public void close() {
		// Shut down any pools we know about
		poolMultipleThreads.shutdownNow();
		for (ExecutorService pool : mapSingleThreadPools.values())
			pool.shutdownNow();
	}

}
