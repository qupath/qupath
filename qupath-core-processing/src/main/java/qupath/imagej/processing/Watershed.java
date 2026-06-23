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

package qupath.imagej.processing;

import ij.IJ;
import ij.plugin.filter.EDM;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Implementation of 2D watershed transform for ImageJ.
 *
 * @author Pete Bankhead
 *
 */
public class Watershed {

	/**
	 * Expand non-zero regions in a labeled image up to a maximum distance, using a watershed transform to prevent region merging.
	 * The expansion is performed in-place.
	 *
	 * @param ipLabels labeled image, where values &le; 0 represent the background
	 * @param maxDistance maximum expansion distance, in pixels
	 * @param conn8 if true, use 8-connectivity
	 */
	public static void watershedExpandLabels(final ImageProcessor ipLabels, final double maxDistance, final boolean conn8) {
		var bp = SimpleThresholding.thresholdAbove(ipLabels, 0.0);
		FloatProcessor fpEDM = new EDM().makeFloatEDM(bp, (byte)255, false);
		fpEDM.multiply(-1);
		doWatershed(fpEDM, ipLabels, -maxDistance, conn8);
	}


	/**
	 * Apply a watershed transform.
	 * @param ip intensity image
	 * @param ipLabels starting locations
	 * @param conn8 if true, use 8-connectivity rather than 4-connectivity
	 */
	public static void doWatershed(final ImageProcessor ip, final ImageProcessor ipLabels, final boolean conn8) {
		doWatershed(ip, ipLabels, Double.NEGATIVE_INFINITY, conn8);
	}

	/**
	 * Apply an intensity-constrained watershed transform, preventing regions from expanding to pixels below a specified minimum threshold
	 * @param ip intensity image
	 * @param ipLabels starting locations
	 * @param minThreshold minimum threshold
	 * @param conn8 if true, use 8-connectivity rather than 4-connectivity
	 */
	public static void doWatershed(final ImageProcessor ip, final ImageProcessor ipLabels, final double minThreshold, final boolean conn8) {

		final long startTime = System.currentTimeMillis();

		final int width = ip.getWidth();
		final int height = ip.getHeight();

		// Create & initialize a priority queue
		final WatershedQueueWrapper queue = new WatershedQueueWrapper(ip, ipLabels, minThreshold);

		// Process the queue
		while (!queue.isEmpty()) {
			final PixelWithValue pwv = queue.poll();
			final int x = pwv.x;
			final int y = pwv.y;

			float lastLabel;
			if (conn8)
				lastLabel = getNeighborLabels8(ipLabels, x, y, width, height);
			else
				lastLabel = getNeighborLabels4(ipLabels, x, y, width, height);
			if (Float.isNaN(lastLabel))
				continue;
			ipLabels.setf(x, y, lastLabel);
			if (conn8)
				addNeighboursToQueue8(queue, x, y, width, height);
			else
				addNeighboursToQueue4(queue, x, y, width, height);

		}

		final long endTime = System.currentTimeMillis();
		if (IJ.debugMode)
			IJ.log(String.format("Watershed time taken: %.2fs", (endTime - startTime)/1000.0));
	}


	private static float getNeighborLabels4(final ImageProcessor ipLabels, final int x, final int y, final int w, final int h) {
		float lastLabel = Float.NaN;
		if (x > 0) {
			float label = ipLabels.getf(x-1, y);
			if (label != 0) {
				if (Float.isNaN(lastLabel))
					lastLabel = label;
				else if (lastLabel != label)
					return Float.NaN;
			}
		}
		if (x < w-1) {
			float label = ipLabels.getf(x+1, y);
			if (label != 0) {
				if (Float.isNaN(lastLabel))
					lastLabel = label;
				else if (lastLabel != label)
					return Float.NaN;
			}
		}
		if (y > 0) {
			float label = ipLabels.getf(x, y-1);
			if (label != 0) {
				if (Float.isNaN(lastLabel))
					lastLabel = label;
				else if (lastLabel != label)
					return Float.NaN;
			}
		}
		if (y < h-1) {
			float label = ipLabels.getf(x, y+1);
			if (label != 0) {
				if (Float.isNaN(lastLabel))
					lastLabel = label;
				else if (lastLabel != label)
					return Float.NaN;
			}
		}
		return lastLabel;
	}


	private static void addNeighboursToQueue4(final WatershedQueueWrapper queue, final int x, final int y, final int w, final int h) {
		queue.add(x, y-1);
		queue.add(x-1, y);
		queue.add(x+1, y);
		queue.add(x, y+1);
	}


	private static float getNeighborLabels8(final ImageProcessor ipLabels, final int x, final int y, final int w, final int h) {
		float lastLabel = Float.NaN;
		for (int yy = Math.max(y-1, 0); yy <= Math.min(h-1, y+1); yy++) {
			for (int xx = Math.max(x-1, 0); xx <= Math.min(w-1, x+1); xx++) {
				if (xx == x && yy == y)
					continue;
				float label = ipLabels.getf(xx, yy);
				// TODO: CONSIDER USE OF -1 BOUNDARIES
				if (label <= 0)
					continue;
				if (Float.isNaN(lastLabel))
					lastLabel = label;
				else if (lastLabel != label)
					return Float.NaN;
			}
		}
		return lastLabel;
	}


	private static void addNeighboursToQueue8(final WatershedQueueWrapper queue, final int x, final int y, final int w, final int h) {
		queue.add(x-1, y-1);
		queue.add(x, y-1);
		queue.add(x+1, y-1);

		queue.add(x-1, y);
		queue.add(x+1, y);

		queue.add(x-1, y+1);
		queue.add(x, y+1);
		queue.add(x+1, y+1);
	}





	private static final class WatershedQueueWrapper {

		private final PriorityQueue<PixelWithValue> queue;
		private final boolean[] queued;
		private final int width, height;
		private final ImageProcessor ip;

		private long counter = 0;

		public WatershedQueueWrapper(final ImageProcessor ip, final ImageProcessor ipLabels, final double minThreshold) {
			this.ip = ip;
			this.width = ip.getWidth();
			this.height = ip.getHeight();

			List<PixelWithValue> toQueue = new ArrayList<>();

			// Keep a record of already-queued pixels
			queued = new boolean[width * height];

			// Loop through and populate the queue sensibly; background assumed to be zero
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					float val = ip.getf(x, y);
					// Mark below-threshold pixels as queued (even if they aren't...) to indicate they shouldn't be added later
					if (val <= minThreshold) {
						queued[y * width + x] = true;
						continue;
					}
					// Mark already-labeled pixels as queued as well,
					// and add pixels immediately adjacent to a labeled pixel to the queue
					if (ipLabels.getf(x, y) != 0) {
						queued[y * width + x] = true;
					} else if (ipLabels.getPixelValue(x+1, y) != 0f ||
							ipLabels.getPixelValue(x-1, y) != 0f ||
							ipLabels.getPixelValue(x, y-1) != 0f ||
							ipLabels.getPixelValue(x, y+1) != 0f) {
						queued[y * width + x] = true;
						toQueue.add(new PixelWithValue(x, y, val, ++counter));
					}
				}
			}

			// It's faster to pass the pixels to the constructor
			this.queue = new PriorityQueue<>(toQueue);
		}

		public void add(final int x, final int y) {
			// Don't add to the queue twice
			if (!mayAddToQueue(x, y))
				return;

			// Add, while storing a count variable, effectively turning the PriorityQueue into a FIFO queue whenever values are equal
			// This is necessary to produce reasonable-looking watershed results where there are plateaus (i.e. pixels with the same value)
			queue.add(createPixelWithValue(x, y));

			// Keep track of the fact this has been queued - won't need it again
			queued[y * width + x] = true;
		}

		private PixelWithValue createPixelWithValue(int x, int y) {
			return new PixelWithValue(x, y, ip.getf(x, y), ++counter);
		}

		public boolean mayAddToQueue(final int x, final int y) {
			return x >= 0 && x < width && y >= 0 && y < height && !queued[y * width + x];
		}

		public PixelWithValue poll() {
			return queue.poll();
		}

		public boolean isEmpty() {
			return queue.isEmpty();
		}

	}


	private record PixelWithValue(int x, int y, float value, long count) implements Comparable<PixelWithValue> {

		@Override
		public int compareTo(final PixelWithValue pwv) {
			// Profiling indicates that the many comparisons are the slowest part of the algorithm...
			if (value < pwv.value) {
				return 1;
			}
			else if (value > pwv.value) {
				return -1;
			}
			// Counts should never be equal
			return count > pwv.count ? 1 : -1;
		}

	}

}
