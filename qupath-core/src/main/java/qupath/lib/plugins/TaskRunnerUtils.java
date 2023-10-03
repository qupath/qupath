/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

import qupath.lib.common.ThreadTools;

import java.util.function.IntFunction;

/**
 * A utility class to help with the creation of {@link TaskRunner} instances.
 * <p>
 * An application can use {@link #setCreateFunction(IntFunction)} and #setCreateHeadlessFunction(IntFunction)} to
 * control the creation of {@link TaskRunner} instances.
 * @since v0.5.0
 */
public class TaskRunnerUtils {

    private static IntFunction<TaskRunner> DEFAULT_HEADLESS_FUNCTION = (int nThreads) -> new CommandLineTaskRunner(nThreads);

    private IntFunction<TaskRunner> headlessFunction = DEFAULT_HEADLESS_FUNCTION;

    private IntFunction<TaskRunner> function = DEFAULT_HEADLESS_FUNCTION;

    private static TaskRunnerUtils INSTANCE = new TaskRunnerUtils();

    private TaskRunnerUtils() {}

    /**
     * Get the default instance. This is a singleton, shared across an application.
     * @return
     */
    public static TaskRunnerUtils getDefaultInstance() {
        return INSTANCE;
    }

    /**
     * Create a new instance. This may be used if part of an application requires its {@link TaskRunner} instances
     * to differ from those used elsewhere.
     * @return
     */
    public static TaskRunnerUtils newInstance() {
        return new TaskRunnerUtils();
    }

    /**
     * Get the default function used to create {@link TaskRunner} instances.
     * This is suitable for use in a headless environment.
     * @return
     */
    public static IntFunction<TaskRunner> getDefaultCreateFunction() {
        return DEFAULT_HEADLESS_FUNCTION;
    }

    /**
     * Set the function used to generate new {@link TaskRunner} instances.
     * @param function a creator function that takes a requested number of threads as input
     * @return this instance
     */
    public TaskRunnerUtils setCreateHeadlessFunction(IntFunction<TaskRunner> function) {
        this.headlessFunction = function;
        return this;
    }

    /**
     * Set the function used to generate new headless {@link TaskRunner} instances.
     * @param function a creator function that takes a requested number of threads as input
     * @return this instance
     */
    public TaskRunnerUtils setCreateFunction(IntFunction<TaskRunner> function) {
        this.function = function;
        return this;
    }

    /**
     * Create a new {@link TaskRunner} instance, using the default number of threads from
     * {@link ThreadTools#getParallelism()}.
     * The task runner may support headless use, but does not have to.
     * @return
     */
    public TaskRunner createTaskRunner() {
        return createTaskRunner(ThreadTools.getParallelism());
    }

    /**
     * Create a new {@link TaskRunner} instance with the specified number of threads.
     * The task runner may support headless use, but does not have to.
     * @param nThreads
     * @return
     */
    public TaskRunner createTaskRunner(int nThreads) {
        return function.apply(nThreads);
    }

    /**
     * Create a new headless {@link TaskRunner} instance, using the default number of threads from
     * {@link ThreadTools#getParallelism()}.
     * @return
     */
    public TaskRunner createHeadlessTaskRunner() {
        return createHeadlessTaskRunner(ThreadTools.getParallelism());
    }

    /**
     * Create a new headless {@link TaskRunner} instance with the specified number of threads.
     * @param nThreads
     * @return
     */
    public TaskRunner createHeadlessTaskRunner(int nThreads) {
        return headlessFunction.apply(nThreads);
    }

}
