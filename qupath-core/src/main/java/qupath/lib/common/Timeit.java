/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Helper class to time code execution.
 * It is designed to be particularly useful when scripting.
 * The main timing report is implemented as {@code timeit.toString()} so that it can easily be printed.
 * <p>
 * A simple use is to start the timer before running code and print it at the end:
 * <pre>{@code 
 * var timeit = new Timeit().start();
 * // Do something
 * timeit.stop(); // Optional - we can also print at any time
 * System.out.println(timeit);
 * }
 * </pre>
 * 
 * <p>
 * Checkpoints can also be added to output times for individual stages:
 * <pre>{@code 
 * var timeit = new Timeit();
 * timeit.checkpoint("First checkpoint");
 * // Do something
 * timeit.checkpoint("Second checkpoint");
 * // Do something else
 * timeit.stop();
 * System.out.println(timeit);
 * }
 * </pre>
 * 
 * <p>
 * Finally, a Timeit can be used to repeatedly run the same code multiple times, and print the timings at the end.
 * 
 * <pre>{@code 
 * var timeit = new Timeit()
 *   .microseconds()
 *   .checkpointAndRun("Greeting", () -> System.out.println("Hello!"), 10)
 *   .summarizeCheckpoints()
 *   .stop();
 * System.out.println(timeit);
 * }
 * </pre>
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class Timeit {
	
	private static final Logger logger = LoggerFactory.getLogger(Timeit.class);
	
	private static final String DEFAULT_END_NAME = "END";
	
	private TimeUnit unit;
	private int maxDecimals = 3;
	
	private boolean isStarted = false;
	
	private boolean summarizeCheckpoints = false;
	
	private List<Checkpoint> checkpoints = Collections.synchronizedList(new ArrayList<>());
	
	/**
	 * Start the Timeit and create a checkpoint with the default name.
	 * <p>
	 * Note that calling this method is not essential, because the Timeit will automatically 
	 * be started when the first checkpoint is created.
	 * 
	 * @return this instance
	 * @throws UnsupportedOperationException if the Timeit has previously been started
	 * @see #start(String)
	 * @see #checkpoint()
	 */
	public Timeit start() throws UnsupportedOperationException {
		return start(null);
	}
	
	/**
	 * Start the Timeit and create a checkpoint with the specified name.
	 * <p>
	 * Note that calling this method is not essential, because the Timeit will automatically 
	 * be started when the first checkpoint is created.
	 * 
	 * @param name of the checkpoint; if null, a default name will be generated
	 * @return this instance
	 * @throws UnsupportedOperationException if the Timeit has previously been started
	 * @see #start()
	 * @see #checkpoint(String)
	 */
	public Timeit start(String name) throws UnsupportedOperationException {
		if (isStarted)
			throw new UnsupportedOperationException("Timeit has already been started!");
		return checkpoint(name);
	}

	/**
	 * Create a checkpoint with the default name and immediately run the provided runnable.
	 * <p>
	 * Note that no checkpoint is made automatically after completion, 
	 * so you should generally print the output immediately, create a new checkpoint, or call {@link #stop()}.
	 * 
	 * @param runnable the runnable to run
	 * @return this instance
	 * @throws UnsupportedOperationException if the Timeit has already been stopped
	 */
	public Timeit checkpointAndRun(Runnable runnable) throws UnsupportedOperationException {
		return checkpointAndRun(null, runnable, 1);
	}

	/**
	 * Create a checkpoint and immediately run the provided Runnable.
	 * <p>
	 * Note that no checkpoint is made automatically after completion, 
	 * so you should generally print the output immediately, create a new checkpoint, or call {@link #stop()}.
	 * 
	 * @param name the name of the checkpoint to create
	 * @param runnable the runnable to run
	 * @return this instance
	 * @throws UnsupportedOperationException if the Timeit has already been stopped
	 */
	public Timeit checkpointAndRun(String name, Runnable runnable) throws UnsupportedOperationException {
		return checkpointAndRun(name, runnable, 1);
	}

	/**
	 * Create a checkpoint and immediately run the provided Runnable <i>nIterations</i> times.
	 * The purpose of this is to get better insights into the time required to run a self-contained block of code.
	 * <p>
	 * Note that no checkpoint is made automatically after completion of the final iteration, 
	 * so you should generally print the output immediately, create a new checkpoint, or call {@link #stop()}.
	 * 
	 * @param name base name of the checkpoint to create; the iteration number will be appended if <i>nIterations</i> &gt; 1
	 * @param runnable the runnable to run
	 * @param nIterations the number of times to run the runnable
	 * @return this instance
	 * @throws UnsupportedOperationException if the Timeit has already been stopped
	 */
	public Timeit checkpointAndRun(String name, Runnable runnable, int nIterations) throws UnsupportedOperationException {
		if (nIterations <= 0)
			return this;
		for (int i = 0; i < nIterations; i++) {
			String name2 = null;
			if (name != null) {
				name2 = nIterations == 1 ? name : name + " (" + (i+1) + ")";				
			}
			checkpoint(name2);
			runnable.run();
		}
		return this;
	}

	
	/**
	 * Create a new checkpoint with a default name.
	 * @return this instance
	 * @throws UnsupportedOperationException if the Timeit has already been stopped
	 */
	public Timeit checkpoint() throws UnsupportedOperationException {
		return checkpoint(null);
	}
	
	/**
	 * Create a new checkpoint with the specified name.
	 * @param name name of the checkpoint; if null, a default name will be generated
	 * @return this instance
	 * @throws UnsupportedOperationException if the Timeit has already been stopped
	 */
	public Timeit checkpoint(String name) throws UnsupportedOperationException {
		if (!isStarted && !checkpoints.isEmpty()) {
			throw new UnsupportedOperationException("Timeit has already been stopped!");
		}
		if (name == null)
			name = "Checkpoint " + (checkpoints.size() + 1);
		if (!isStarted) {
			isStarted = true;
			logger.debug("Timeit now started with checkpoint {}", name);
		}
		checkpoints.add(new Checkpoint(name));
		if (DEFAULT_END_NAME.equals(name)) {
			isStarted = false;
			logger.debug("Timeit now stopped");
		}
		return this;
	}
	
	/**
	 * Stop the {@link Timeit}.
	 * 
	 * @return this instance
	 * @throws UnsupportedOperationException if the Timeit hasn't been started, or has already been stopped.
	 */
	public Timeit stop() throws UnsupportedOperationException {
		if (!isStarted)
			throw new UnsupportedOperationException("Timeit has already been stopped!");
		return checkpoint(DEFAULT_END_NAME);
	}
	
	private Timeit setUnit(TimeUnit unit) {
		this.unit = unit;
		return this;
	}
	
	public Timeit autoUnits() {
		return setUnit(null);
	}
		
	/**
	 * Report timings in nanoseconds.
	 * @return this instance
	 */
	public Timeit nanoseconds() {
		return setUnit(TimeUnit.NANOSECONDS);
	}
	
	/**
	 * Report timings in milliseconds.
	 * @return this instance
	 */
	public Timeit milliseconds() {
		return setUnit(TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Report timings in microseconds.
	 * @return this instance
	 */
	public Timeit microseconds() {
		return setUnit(TimeUnit.MICROSECONDS);
	}
	
	/**
	 * Report timings in seconds.
	 * @return this instance
	 */
	public Timeit seconds() {
		return setUnit(TimeUnit.SECONDS);
	}

	/**
	 * Report timings in minutes.
	 * @return this instance
	 */
	public Timeit minutes() {
		return setUnit(TimeUnit.MINUTES);
	}
	
	/**
	 * Request that checkpoints are summarized in the {@link #toString()} method.
	 * Currently, this means simply reporting the mean time per checkpoint.
	 * @return this instance
	 * @see #summarizeCheckpoints(boolean)
	 */
	public Timeit summarizeCheckpoints() {
		return summarizeCheckpoints(true);
	}

	/**
	 * Optionally request that checkpoints are summarized in the {@link #toString()} method.
	 * Currently, this means simply reporting the mean time per checkpoint.
	 * @param summarize whether to summarize or not
	 * @return this instance
	 * @see #summarizeCheckpoints(boolean)
	 */
	public Timeit summarizeCheckpoints(boolean summarize) {
		this.summarizeCheckpoints = summarize;
		return this;
	}
	
	private long getStartTime(List<Checkpoint> checkpoints) {
		return checkpoints.get(0).getNanoseconds();
	}
	
	private static Checkpoint getEndCheckpoint(List<Checkpoint> checkpoints) {
		if (checkpoints.isEmpty())
			return null;
		var lastCheckpoint = checkpoints.get(checkpoints.size()-1);
		if (Objects.equals(DEFAULT_END_NAME, lastCheckpoint.getName()))
				return lastCheckpoint;
		return null;
	}
	
	/**
	 * Get the maximum number of decimal places when reporting timings.
	 * @return
	 * @see #maxDecimalPlaces(int)
	 */
	public int getMaxDecimalPlaces() {
		return maxDecimals;
	}
	
	/**
	 * Set the maximum number of decimal places when reporting timings 
	 * using seconds or minutes.
	 * @param maxDP
	 * @return this instance
	 */
	public Timeit maxDecimalPlaces(int maxDP) {
		this.maxDecimals = maxDP;
		return this;
	}
	
	/**
	 * Get an list of all the checkpoints.
	 * @return
	 * @implNote this currently returns a defensive copy of the list; the original contents 
	 *           are unaffected by any changes made.
	 */
	public List<Checkpoint> getCheckpoints() {
		return new ArrayList<>(checkpoints);
	}
	
	/**
	 * Returns a snapshot string representation of the Timeit's status.
	 */
	@Override
	public String toString() {
		if (checkpoints.isEmpty())
			return "No TimeIt information available";
		
		// Duplicate the checkpoints list (in case it is modified elsewhere)
		var checkpoints = new ArrayList<>(this.checkpoints);
		
		long startTime = getStartTime(checkpoints);
		var endCheckpoint = getEndCheckpoint(checkpoints);
		long endTime = endCheckpoint == null ? System.nanoTime() : endCheckpoint.getNanoseconds();
		long totalDuration = endTime - startTime;
		
		var timeUnit = this.unit;
		if (timeUnit == null)
			timeUnit = chooseAutoTimeUnit(totalDuration);
		
		if (checkpoints.size() == 1) {
			return "Time since start\t" + getDurationString(totalDuration, timeUnit, maxDecimals);
		}
		
		var sb = new StringBuilder();
		
		long[] durations = new long[checkpoints.size()-1];
		if (checkpoints.size() > 2 || endCheckpoint == null) {
		
			for (int i = 0; i < checkpoints.size()-1; i++) {
				long start = checkpoints.get(i).nano;
				long end = checkpoints.get(i+1).nano;
				long duration = end - start;
				durations[i] = duration;
				sb.append(checkpoints.get(i).name);
				sb.append("\t");
				sb.append(getDurationString(duration, timeUnit, maxDecimals));
				sb.append("\n");
			}
		}
		
		sb.append("Total duration\t" + getDurationString(totalDuration, timeUnit, maxDecimals));
		if (summarizeCheckpoints) {
			double average = LongStream.of(durations).summaryStatistics().getAverage();
			sb.append("\nAverage per checkpoint: " + getDurationString((long)Math.round(average), timeUnit, maxDecimals));
		}

		
		return sb.toString();
	}
	
	
	private static TimeUnit chooseAutoTimeUnit(long nanos) {
		var t = TimeUnit.NANOSECONDS;
		if (t.toMinutes(nanos) >= 10)
			return TimeUnit.MINUTES;
		if (t.toSeconds(nanos) >= 1)
			return TimeUnit.SECONDS;
		if (t.toMillis(nanos) >= 10)
			return TimeUnit.MILLISECONDS;
		if (t.toMicros(nanos) >= 10)
			return TimeUnit.MICROSECONDS;
		return TimeUnit.NANOSECONDS;
	}
	
	
	private static String getDurationString(long nanos, TimeUnit unit, int maxDecimals) {
		switch (unit) {
		case MICROSECONDS:
			return toMicrosString(nanos, 0);
		case MILLISECONDS:
			return toMillisString(nanos, 0);
		case MINUTES:
			return toMinutesString(nanos, maxDecimals);
		case NANOSECONDS:
			return toNanoString(nanos, 0);
		case SECONDS:
			return toSecondsString(nanos, maxDecimals);
		case DAYS:
		case HOURS:
		default:
			throw new UnsupportedOperationException("Unsupported time unit " + unit);
		}
	}

	private static String toMinutesString(long nanos, int nDecimals) {
		long seconds = TimeUnit.NANOSECONDS.toSeconds(nanos);
		return GeneralTools.formatNumber(seconds/60.0, nDecimals) + " minutes";
	}

	private static String toSecondsString(long nanos, int nDecimals) {
		return GeneralTools.formatNumber(nanos/1e9, nDecimals) + " s";
	}

	private static String toMillisString(long nanos, int nDecimals) {
		return GeneralTools.formatNumber(nanos/1e6, nDecimals) + " ms";
	}

	private static String toMicrosString(long nanos, int nDecimals) {
		return GeneralTools.formatNumber(nanos/1e3, nDecimals) + " µs";
	}

	private static String toNanoString(long nanos, int nDecimals) {
		return nanos + " ns";
	}
	
	
	
	public static void main(String[] args) {
		
		try {
			var timeit = new Timeit()
					.microseconds()
					.checkpointAndRun("Greeting", () -> {
						System.out.println("Hello!");
						try {
							Thread.sleep(10L);
						} catch (Exception e) {}
					}, 10)
					.summarizeCheckpoints()
					.stop();
			
			Thread.sleep(100L);
			
			System.out.println(timeit);

		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	
	/**
	 * Class representing a named checkpoint with a timestamp in nanoseconds.
	 */
	public static class Checkpoint {
		
		private String name;
		private long nano;
		
		private Checkpoint(String name) {
			Objects.requireNonNull(name);
			this.name = name;
			this.nano = System.nanoTime();
		}
		
		/**
		 * Get the checkpoint name.
		 * @return
		 */
		public String getName() {
			return name;
		}
		
		/**
		 * Get the checkpoint timestamp in nanoseconds.
		 * @return
		 */
		public long getNanoseconds() {
			return nano;
		}

		@Override
		public String toString() {
			return "Checkpoint [name=" + name + ", nano=" + nano + "]";
		}
		
	}

}
