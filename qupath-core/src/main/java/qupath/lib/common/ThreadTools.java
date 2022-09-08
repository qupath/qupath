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

package qupath.lib.common;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a thread factory that supports adding a prefix to the name and setting daemon status.
 * <p>
 * This helps with debugging, e.g. using visualvm
 * 
 * @author Pete Bankhead
 *
 */
public class ThreadTools {
	
	private static final Logger logger = LoggerFactory.getLogger(ThreadTools.class);
	
	private static int requestedThreads = ForkJoinPool.getCommonPoolParallelism();
	
	/**
	 * Create a named thread factory with a specified priority.
	 * 
	 * @param prefix
	 * @param daemon
	 * @param priority
	 * @return
	 */
	public static ThreadFactory createThreadFactory(String prefix, boolean daemon, int priority) {
		return new SimpleThreadFactory(prefix, daemon, priority);
	}
	
	/**
	 * Create a named thread factory with {@code Thread.NORM_PRIORITY}.
	 * 
	 * @param prefix
	 * @param daemon
	 * @return
	 */
	public static ThreadFactory createThreadFactory(String prefix, boolean daemon) {
		return createThreadFactory(prefix, daemon, Thread.NORM_PRIORITY);
	}
	
	/**
	 * Set the requested level of parallelism.
	 * Note that for interactive use this is usually set through the user interface and shouldn't be modified 
	 * elsewhere to maintain consistency.
	 * @param nThreads
	 */
	public static void setParallelism(int nThreads) {
		if (nThreads <= 0)
			throw new IllegalArgumentException("Number of threads must be >= 1, but requested number is " + nThreads);
		logger.info("Setting parallelism to {}", nThreads);
		requestedThreads = nThreads;
	}
	
	/**
	 * Get the requested level of parallelism. 
	 * Other classes that make use of thread pools can use this to help balance multithreading with memory use.
	 * The default value is {@link ForkJoinPool#getCommonPoolParallelism()}.
	 * @return
	 */
	public static int getParallelism() {
		return requestedThreads;
	}
	
	
	
	static class SimpleThreadFactory implements ThreadFactory {
		
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private String prefix;
		private boolean daemon;
		private int priority;
	
		SimpleThreadFactory(final String prefix, final boolean daemon, final int priority) {
			this.group = Thread.currentThread().getThreadGroup();
			this.prefix = prefix;
			this.daemon = daemon;
			this.priority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority));
					
		}
	
		@Override
		public Thread newThread(Runnable r) {
			String name = prefix + threadNumber.getAndIncrement();
			Thread t = new Thread(group, r, name, 0);
			t.setDaemon(daemon);
			if (t.getPriority() != priority)
				t.setPriority(priority);
			return t;
		}
		
	}
	
}