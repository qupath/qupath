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

package qupath.lib.analysis.algorithms;

import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleModifiableImage;

/**
 * Implementation of 2D morphological reconstruction, using 8-connectivity &amp; a hybrid method.
 * <p>
 * TODO: Implement any further optimizations added to the ImageJ version
 * 
 * @author Pete Bankhead
 *
 */
public class MorphologicalReconstruction {
	/*
	 * Morphological reconstruction using 8-connectivity & a hybrid method.
	 * 
	 * For algorithm details, see:
	 *   Vincent, L. (1993).
	 *   	Morphological Grayscale Reconstruction in Image Analysis: Applications and Efficient Algorithms.
	 * 	    IEEE Transactions on Image Processing
	 * 
	 * Overall, this appears to be about 20% faster than MorphologicalReconstruction using Point objects (by instead using a primitive int array).
	 * 
	 */
	
	
	private static int dilateAndCompare(SimpleModifiableImage ipMarker, SimpleImage ipMask, boolean reverse, IntDequeue queue) {
		int inc, hStart, vStart, hEnd, vEnd;
		if (reverse) {
			inc = -1;
			hStart = ipMarker.getWidth() - 1;
			vStart = ipMarker.getHeight() - 1;
			hEnd = -1;
			vEnd = -1;
		} else {
			inc = 1;
			hStart = 0;
			vStart = 0;
			hEnd = ipMarker.getWidth();
			vEnd = ipMarker.getHeight();
		}
		boolean populateQueue = queue != null;
		int width = ipMarker.getWidth();
		
		// Apply horizontal propagating dilation
		int changes = 0;
		// valPrevious is the last processed value, valP1-3 are those from the previous row
		float valPrevious, valP1, valP2, valP3, valCurrent;
		boolean firstRow = true;
		for (int y = vStart; y != vEnd; y += inc) {
			// Initialize previous 4 neighbours
			valPrevious = ipMarker.getValue(hStart, y);
			if (firstRow) {
				valP1 = valPrevious;
				valP2 = valPrevious;
				valP3 = valPrevious;
			} else {
				valP2 = ipMarker.getValue(hStart, y-inc);
				valP1 = valP2;
			}

			boolean firstColumn = true;

			for (int x = hStart; x != hEnd; x += inc) {
				// Get the current value from the marker image
				valCurrent = ipMarker.getValue(x, y);
				
				if (firstRow) {
					valP1 = valCurrent;
					valP2 = valCurrent;
					valP3 = valCurrent;
				} else if (x+inc != hEnd)
					valP3 = ipMarker.getValue(x+inc, y-inc);
				else
					valP3 = valCurrent;
				
				// Get the neighbourhood maximum
//				float valNeighbourMax = Math.max(Math.max(valP1, valP2), Math.max(valP3, valPrevious));
				float valNeighbourMax = (valP1 >= valP2) ? valP1 : valP2;
				valNeighbourMax = (valNeighbourMax >= valP3) ? valNeighbourMax : valP3;
				valNeighbourMax = (valNeighbourMax >= valPrevious) ? valNeighbourMax : valPrevious;
				
				// If the current value is less than the neighbourhood maximum, try to update it while remaining under the mask
				if (valCurrent < valNeighbourMax) {
					float valMask = ipMask.getValue(x, y);
//					float valNew = Math.min(valMask, valNeighbourMax);
					// Get the minimum
					float valNew = (valNeighbourMax >= valMask) ? valMask : valNeighbourMax;
					if (valNew > valCurrent) {
						valCurrent = valNew;
						ipMarker.setValue(x, y, valCurrent);
						changes++;
					}
				}
				
				// If there are neighbours with lower values than we have just updated, and we have a queue,
				// test whether to add the neighbours to the queue
				if (populateQueue) {
					boolean addToQueue = false;
					if (valPrevious < valCurrent && valPrevious < ipMask.getValue(x-inc, y)) {
						addToQueue = true;
					}
					else if (!firstRow) {
						if (!firstColumn && valP1 < valCurrent && valP1 < ipMask.getValue(x-inc, y-inc)) {
							addToQueue = true;
						}
						else if (valP2 < valCurrent && valP2 < ipMask.getValue(x, y-inc)) {
							addToQueue = true;
						}
						else if (valP3 < valCurrent && valP3 < ipMask.getValue(x+inc, y-inc)) {
							addToQueue = true;
						}
					}
					if (addToQueue) {
						queue.add(y*width + x);
					}
				}
				
				// Update previous neighbours
				valPrevious = valCurrent;
				valP1 = valP2;
				valP2 = valP3;
				firstColumn = false;
			}
			firstRow = false;
		}
		return changes;
	}
	
	private static void processPoint(final SimpleModifiableImage ipMarker, final SimpleImage ipMask, final int x, final int y, final float val, final IntDequeue queue, int width) {
		float valTempMarker = ipMarker.getValue(x, y);
		if (valTempMarker < val) {
			float valTempMask = ipMask.getValue(x, y);
			if (valTempMarker < valTempMask) {
//				ipMarker.setf(x, y, Math.min(val, valTempMask));
				ipMarker.setValue(x, y, valTempMask <= val ? valTempMask : val);
				queue.add(y * width + x);
			}
		}
	}
	
	
	private static boolean processQueue(final SimpleModifiableImage ipMarker, final SimpleImage ipMask, final IntDequeue queue) {
		int x1 = 0;
		int y1 = 0;
		int x2 = ipMarker.getWidth();
		int y2 = ipMarker.getHeight();
		
		int counter = 0;
		int width = ipMarker.getWidth();
		
		Thread currentThread = Thread.currentThread();
		
		while (!queue.isEmpty()) {
			counter++;
			
//			System.out.println("Counter " + counter);

			// If we were interrupted, stop
			if (counter % 2500 == 0 && currentThread.isInterrupted())
				return false;
			
			int ind = queue.remove();
			int x = ind % width;
			int y = ind / width;
			
			// Test 8-neighbours
			float val = ipMarker.getValue(x, y);
			if (x > x1) {
				processPoint(ipMarker, ipMask, x-1, y, val, queue, width);
				if (y > y1)
					processPoint(ipMarker, ipMask, x-1, y-1, val, queue, width);
				if (y < y2-1)
					processPoint(ipMarker, ipMask, x-1, y+1, val, queue, width);
			}
			if (x < x2-1) {
				processPoint(ipMarker, ipMask, x+1, y, val, queue, width);
				if (y > y1)
					processPoint(ipMarker, ipMask, x+1, y-1, val, queue, width);
				if (y < y2-1)
					processPoint(ipMarker, ipMask, x+1, y+1, val, queue, width);
			}
			if (y > y1)
				processPoint(ipMarker, ipMask, x, y-1, val, queue, width);
			if (y < y2-1)
				processPoint(ipMarker, ipMask, x, y+1, val, queue, width);
		}
		
		return true;
	}
	
	/**
	 * Apply morphological reconstruction with the specified marker and mask images.
	 * 
	 * @param imMarker
	 * @param imMask
	 * @return true if the reconstruction terminated successfully, false if it stopped early (e.g. due to an interruption).
	 */
	public static boolean morphologicalReconstruction(SimpleModifiableImage imMarker, SimpleImage imMask) {
		// Apply forward propagation
		dilateAndCompare(imMarker, imMask, false, null);
		// Apply backwards propagation, filling the queue
		IntDequeue queue = new IntDequeue(1024*1024);
		dilateAndCompare(imMarker, imMask, true, queue);
		// Process pixels in the queue (FIFO)
		return processQueue(imMarker, imMask, queue);
	}

	
	static class IntDequeue {
		
		private int[] array;
		private int head = 0; // Points to location of first element in queue
		private int tail = 0; // Points to location of *next* insert
		private static int MAX_EXPANSION = 1024*10;
		
		public IntDequeue(int capacity) {
//			IJ.log("Using INT DEQUE!");
			array = new int[capacity];
			head = 0;
			tail = 0;
		}
		
		public boolean isEmpty() {
			return tail == head;
		}
		
		/**
		 * Performs no check that the output will be valid (caller should use isEmpty first to check this)
		 * @return
		 */
		public int remove() {
			head++;
			return array[head-1];
		}
		
		public void add(int val) {
			// Do a normal add if we can
			if (tail < array.length) {
				array[tail] = val;
				tail++;
				return;
			}
			// Shift everything back if that's an option
			if (head != 0) {
//				IJ.log("Shifting with head at " + head);
				if (tail > head)
					System.arraycopy(array, head, array, 0, tail-head);
				tail -= head;
				head = 0;
				array[tail] = val;
				tail++;
				return;
			}
			// We need to expand the array
			int[] array2 = new int[Math.max(array.length*2, MAX_EXPANSION)];
			System.arraycopy(array, 0, array2, 0, array.length);
			array = array2;
			array[tail] = val;
			tail++;
//			IJ.log("Expanding IntDeque to length " + array.length);
		}
		
	}
	
}

