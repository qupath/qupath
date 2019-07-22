/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.imagej.processing;

import java.util.ArrayDeque;
import java.util.PriorityQueue;

import ij.IJ;
import ij.plugin.filter.EDM;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * Implementation of 2D watershed transform for ImageJ.
 * 
 * @author Pete Bankhead
 *
 */
public class Watershed {
	
	/**
	 * Expand non-zero regions in a binary image up to a maximum distance, using a watershed transform to prevent region merging.
	 * @param bp
	 * @param maxDistance
	 * @param conn8
	 * @return
	 */
	public static ImageProcessor watershedExpand(final ByteProcessor bp, final double maxDistance, final boolean conn8) {
		FloatProcessor fpEDM = new EDM().makeFloatEDM(bp, (byte)255, false);
		fpEDM.multiply(-1);
		ImageProcessor ipLabels = RoiLabeling.labelImage(bp, 0.5f, conn8);
		doWatershed(fpEDM, ipLabels, -maxDistance, conn8);
		return ipLabels;
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
			queue.discard(pwv);
			
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
	
	
	
	
	
	static class WatershedQueueWrapper {

		final private PriorityQueue<PixelWithValue> queue = new PriorityQueue<>();
		final private boolean[] queued;
		final private int width, height;
		final private ImageProcessor ip;
		
		private long counter = 0;//Long.MIN_VALUE;
		
		// Keep a pool of objects so they can be reused... not normally worth the effort, but we are likely to have *a lot*
		final private ArrayDeque<PixelWithValue> dequePool;
		
		
		public WatershedQueueWrapper(final ImageProcessor ip, final ImageProcessor ipLabels, final double minThreshold) {
			this.ip = ip;
			this.width = ip.getWidth();
			this.height = ip.getHeight();
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
					}
					else if (ipLabels.getPixelValue(x+1, y) != 0f || ipLabels.getPixelValue(x-1, y) != 0f || ipLabels.getPixelValue(x, y-1) != 0f || ipLabels.getPixelValue(x, y+1) != 0f) {
						queued[y * width + x] = true;
						queue.add(new PixelWithValue(x, y, val, ++counter));
					}
				}			
			}
			
			// Create a deque
			dequePool = new ArrayDeque<>(ip.getWidth() * ip.getHeight());
		}
		
		public final void add(final int x, final int y) {
			// Don't add to the queue twice
			if (!mayAddToQueue(x, y))
				return;
			addWithoutCheck(x, y, ip.getf(x, y));
		}
		
		protected final void addWithoutCheck(final int x, final int y, final float val) {
			PixelWithValue pwv = dequePool.poll();
			if (pwv == null)
				pwv = new PixelWithValue(x, y, val, ++counter);
			else {
				pwv.x = x;
				pwv.y = y;
				pwv.value = val;
				pwv.count = ++counter;
			}
			// Add, while storing a count variable, effectively turning the PriorityQueue into a FIFO queue whenever values are equal
			// This is necessary to produce reasonable-looking watershed results where there are plateaus (i.e. pixels with the same value)
			queue.add(pwv);
			
//			queue.add(new PixelWithValue(x, y, val, ++counter));

			// Keep track of the fact this has been queued - won't need it again
			queued[y * width + x] = true;
		}
		
//		public final boolean mayAddToQueue(int x, int y) {
//			return !queued[y * width + x];
//		}

		public final boolean mayAddToQueue(final int x, final int y) {
			return x >= 0 && x < width && y >= 0 && y < height && !queued[y * width + x];
		}

		public final PixelWithValue poll() {
			return queue.poll();
		}
		
		public final boolean isEmpty() {
			return queue.isEmpty();
		}
		
		/**
		 * Inform the queue it is free to reuse a PixelWithValue object if required
		 * @param pwv
		 */
		public final void discard(PixelWithValue pwv) {
			dequePool.add(pwv);
		}
		
	}


	static class PixelWithValue implements Comparable<PixelWithValue> {
		
		public int x, y;
		public float value;
		public long count;
		
//		final public int x, y;
//		final public float value;
//		final public long count;
		
		public PixelWithValue(final int x, final int y, final float value, final long count) {
			this.x = x;
			this.y = y;
			this.value = value;
			this.count = count;
			
//			System.out.println("My count: " + count);
		}

		@Override
		public int compareTo(final PixelWithValue pwv) {
//			// Profiling indicates that the many comparisons are the slowest part of the algorithm...
//			if (value == pwv.value) 
//				return count > pwv.count ? 1 : -1;
//			else
//				return value > pwv.value ? -1 : 1;
					
			// Profiling indicates that the many comparisons are the slowest part of the algorithm...
			if (value < pwv.value) {
				return 1;
			}
			else if (value > pwv.value) {
				return -1;
			}
			return count > pwv.count ? 1 : -1;
			
//			// Profiling indicates that the many comparisons are the slowest part of the algorithm...
//			if (value > pwv.value)
//				return 1;
//			else if (value < pwv.value)
//				return -1;
//			return count > pwv.count ? 1 : -1;
		}
		
	}

}