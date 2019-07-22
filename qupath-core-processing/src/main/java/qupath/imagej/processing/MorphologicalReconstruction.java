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

import ij.gui.Roi;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Rectangle;
import java.util.Arrays;


/**
 * Implementation of morphological reconstruction for ImageJ.
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
	 * Overall, this appears to be about 20% faster than MorphologicalReconstruction using Point objects (rather than a primitive int array).
	 * 
	 */
	
	
	
	/**
	 * Alternative morphological reconstruction (based on ImageJ's FloodFiller) suitable for binary images only.
	 * @param bpMarker
	 * @param bpMask
	 * @param permitMaskChanges
	 * @return
	 */
	public static ByteProcessor binaryReconstruction(ByteProcessor bpMarker, ByteProcessor bpMask, boolean permitMaskChanges) {
		if (!permitMaskChanges) {
			bpMask = (ByteProcessor)bpMask.duplicate();
			bpMarker = (ByteProcessor)bpMarker.duplicate();
		}
		FloodFiller ff = new FloodFiller(bpMask);
		bpMask.setValue(127);
		int width = bpMarker.getWidth();
		byte[] pxMarker = (byte[])bpMarker.getPixels();
		byte[] pxMask = (byte[])bpMask.getPixels();
		for (int i = 0; i < pxMarker.length; i++) {
			if (pxMarker[i] == (byte)255 && pxMask[i] != (byte)127)
				ff.fill(i % width, i / width);
		}
		Arrays.fill(pxMarker, (byte)0);
		for (int i = 0; i < pxMask.length; i++) {
			if (pxMask[i] == (byte)127)
				pxMarker[i] = (byte)255;
		}
		return bpMarker;
	}
	
	
	
	private static int dilateAndCompare(final ImageProcessor ipMarker, final ImageProcessor ipMask, final boolean reverse, final IntDequeue queue) {
		int inc, hStart, vStart, hEnd, vEnd;
		Rectangle rect = ipMarker.getRoi();
		if (reverse) {
			inc = -1;
			hStart = rect.x + rect.width - 1;
			vStart = rect.y + rect.height - 1;
			hEnd = rect.x - 1;
			vEnd = rect.y - 1;
		} else {
			inc = 1;
			hStart = rect.x;
			vStart = rect.y;
			hEnd = rect.x + rect.width;
			vEnd = rect.y + rect.height;
		}
		final boolean populateQueue = queue != null;
		final int width = ipMarker.getWidth();
		
		// Apply horizontal propagating dilation
		int changes = 0;
		// valPrevious is the last processed value, valP1-3 are those from the previous row
		float valPrevious, valP1, valP2, valP3, valCurrent;
		boolean firstRow = true;
		for (int y = vStart; y != vEnd; y += inc) {
			// Initialize previous 4 neighbours
			valPrevious = ipMarker.getf(hStart, y);
			if (firstRow) {
				valP1 = valPrevious;
				valP2 = valPrevious;
				valP3 = valPrevious;
			} else {
				valP2 = ipMarker.getf(hStart, y-inc);
				valP1 = valP2;
			}

			boolean firstColumn = true;

			for (int x = hStart; x != hEnd; x += inc) {
				// Get the current value from the marker image
				valCurrent = ipMarker.getf(x, y);
				
				if (firstRow) {
					valP1 = valCurrent;
					valP2 = valCurrent;
					valP3 = valCurrent;
				} else if (x+inc != hEnd)
					valP3 = ipMarker.getf(x+inc, y-inc);
				else
					valP3 = valCurrent;
				
				// Get the neighbourhood maximum
//				float valNeighbourMax = Math.max(Math.max(valP1, valP2), Math.max(valP3, valPrevious));
				float valNeighbourMax = (valP1 >= valP2) ? valP1 : valP2;
				valNeighbourMax = (valNeighbourMax >= valP3) ? valNeighbourMax : valP3;
				valNeighbourMax = (valNeighbourMax >= valPrevious) ? valNeighbourMax : valPrevious;
				
				// If the current value is less than the neighbourhood maximum, try to update it while remaining under the mask
				if (valCurrent < valNeighbourMax) {
					float valMask = ipMask.getf(x, y);
//					float valNew = Math.min(valMask, valNeighbourMax);
					// Get the minimum
					float valNew = (valNeighbourMax >= valMask) ? valMask : valNeighbourMax;
					if (valNew > valCurrent) {
						valCurrent = valNew;
						ipMarker.setf(x, y, valCurrent);
						changes++;
					}
				}
				
				// If there are neighbours with lower values than we have just updated, and we have a queue,
				// test whether to add the neighbours to the queue
				if (populateQueue) {
					boolean addToQueue = false;
					if (valPrevious < valCurrent && valPrevious < ipMask.getf(x-inc, y)) {
						addToQueue = true;
					}
					else if (!firstRow) {
						if (!firstColumn && valP1 < valCurrent && valP1 < ipMask.getf(x-inc, y-inc)) {
							addToQueue = true;
						}
						else if (valP2 < valCurrent && valP2 < ipMask.getf(x, y-inc)) {
							addToQueue = true;
						}
						else if (valP3 < valCurrent && valP3 < ipMask.getf(x+inc, y-inc)) {
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
	
	private static void processPoint(final ImageProcessor ipMarker, final ImageProcessor ipMask, final int ind, final float val, final IntDequeue queue) {
		final float valTempMarker = ipMarker.getf(ind);
		if (valTempMarker < val) {
			final float valTempMask = ipMask.getf(ind);
			if (valTempMarker < valTempMask) {
//				ipMarker.setf(x, y, Math.min(val, valTempMask));
				ipMarker.setf(ind, valTempMask <= val ? valTempMask : val);
				queue.add(ind);
			}
		}
	}
	
	
	private static boolean processQueue(final ImageProcessor ipMarker, final ImageProcessor ipMask, final IntDequeue queue) {
		final Rectangle rect = ipMarker.getRoi();
		final int x1 = rect.x;
		final int y1 = rect.y;
		final int x2 = x1 + rect.width;
		final int y2 = y1 + rect.height;
		
		long counter = 0;
		final int width = ipMarker.getWidth();
		
		final Thread currentThread = Thread.currentThread();
		
		while (!queue.isEmpty()) {
			counter++;

			// If we were interrupted, stop
			if (counter % 2500 == 0 && currentThread.isInterrupted())
				return false;
			
			final int ind = queue.remove();
			final int x = ind % width;
			final int y = ind / width;
			
			// Test 8-neighbours
			final float val = ipMarker.getf(ind);
			
//			for (int yy = Math.max(y-1, y1); yy < Math.min(y+2, y2); yy++) {
//				for (int xx = Math.max(x-1, x1); xx < Math.min(x+2, x2); xx++) {
//					if (xx != x || yy != y)
//						processPoint(ipMarker, ipMask, yy*width+xx, val, queue);
//				}
//			}
			
			if (x > x1) {
				processPoint(ipMarker, ipMask, ind-1, val, queue);
				if (y > y1)
					processPoint(ipMarker, ipMask, ind-width-1, val, queue);
				if (y < y2-1)
					processPoint(ipMarker, ipMask, ind+width-1, val, queue);
			}
			if (x < x2-1) {
				processPoint(ipMarker, ipMask, ind+1, val, queue);
				if (y > y1)
					processPoint(ipMarker, ipMask, ind-width+1, val, queue);
				if (y < y2-1)
					processPoint(ipMarker, ipMask, ind+width+1, val, queue);
			}
			if (y > y1)
				processPoint(ipMarker, ipMask, ind-width, val, queue);
			if (y < y2-1)
				processPoint(ipMarker, ipMask, ind+width, val, queue);
		}
		
		return true;
	}
	
	/**
	 * Apply morphological operation using marker and mask images. The marker image is changed.
	 * @param ipMarker
	 * @param ipMask
	 * @return
	 */
	public static boolean morphologicalReconstruction(final ImageProcessor ipMarker, final ImageProcessor ipMask) {
		// Really we just need one round of forward propagation, followed by one round of backward
		// propagation filling in the queue... but working with the queue is slow, so it is better to
		// repeat propagation steps so long as they are changing a sufficiently high proportion of the pixels
		// at each step (here, 10%)
		
//		long startTime = System.currentTimeMillis();
		final int nPixels = ipMarker.getWidth() * ipMarker.getHeight();
		// Apply forward propagation, counting the number of pixels that changed
		int nChanges = dilateAndCompare(ipMarker, ipMask, false, null);
		// For as long as > ?% pixels change, continue propagations
//		int counter = 0;
		while (nChanges/(double)nPixels > 0.1) {
//			System.out.println(String.format("Changes as propagation iteration %d: %.2f%%", counter++, 100.*nChanges/nPixels));
			// Apply backwards propagation
			dilateAndCompare(ipMarker, ipMask, true, null);
			// Apply forward propagation again
			nChanges = dilateAndCompare(ipMarker, ipMask, false, null);			
		}
//		System.out.println(String.format("Changes as propagation iteration %d: %.2f%%", counter++, 100.*nChanges/nPixels));
		// Apply backwards propagation, filling the queue
		final IntDequeue queue = new IntDequeue(nPixels/4);
		dilateAndCompare(ipMarker, ipMask, true, queue);
//		long endTime = System.currentTimeMillis();
//		System.out.println("Queue setup time: " + (endTime - startTime)/1000.);
		
//		startTime = System.currentTimeMillis();
		processQueue(ipMarker, ipMask, queue);
//		endTime = System.currentTimeMillis();
//		System.out.println("Queue process time: " + (endTime - startTime)/1000.);
		// Process pixels in the queue (FIFO)
		return processQueue(ipMarker, ipMask, queue);
	}
	
//	// More conventional approach...
//	public static boolean morphologicalReconstruction(ImageProcessor ipMarker, ImageProcessor ipMask) {
//		// Apply forward propagation
//		dilateAndCompare(ipMarker, ipMask, false, null);
//		// Apply backwards propagation, filling the queue
//		IntDequeue queue = new IntDequeue(ipMarker.getWidth()*ipMarker.getHeight()/4);
//		dilateAndCompare(ipMarker, ipMask, true, queue);
//		// Process pixels in the queue (FIFO)
//		return processQueue(ipMarker, ipMask, queue);
//	}
	
	
	/**
	 * Check that marker and mask images have the same size, and ensure that marker pixels are &lt;= mask pixels, 
	 * making this if necessary.
	 * @param ipMarker
	 * @param ipMask
	 * @return
	 */
	public static boolean validateMarkerMask(ImageProcessor ipMarker, ImageProcessor ipMask) {
		if (ipMarker.getWidth() != ipMask.getWidth() || ipMarker.getHeight() != ipMask.getHeight())
			return false;
		ipMarker.copyBits(ipMask, 0, 0, Blitter.MIN);
		return true;
	}
	
	/**
	 * Apply opening by reconstruction, with the specified minimum filter radius.
	 * @param ip
	 * @param radius
	 * @return
	 */
	public static ImageProcessor openingByReconstruction(final ImageProcessor ip, final double radius) {
		// Apply (initial) morphological opening
		final RankFilters rf = new RankFilters();
		final ImageProcessor ipReconstructed = ip.duplicate();
		ipReconstructed.setRoi(ip.getRoi());
		rf.rank(ipReconstructed, radius, RankFilters.MIN);
//		if (ip.getRoi() == null)
//			rf.rank(ipReconstructed, radius, RankFilters.MAX);
		// Dilate opened image constrained by original image as a mask
		if (morphologicalReconstruction(ipReconstructed, ip))
			return ipReconstructed;
		return null;
	}
	
//	public static boolean morphologicalReconstruction(ImageProcessor ipMarker, ImageProcessor ipMask) {
//		// Apply forward propagation
//		long t1 = System.currentTimeMillis();
//		dilateAndCompare(ipMarker, ipMask, false, null);
//		long t2 = System.currentTimeMillis();
//		// Apply backwards propagation, filling the queue
//		Queue<Point> queue = new ArrayDeque<>();
//		dilateAndCompare(ipMarker, ipMask, true, queue);
//		long t3 = System.currentTimeMillis();
//		// Process pixels in the queue (FIFO)
//		boolean result = processQueue(ipMarker, ipMask, queue);
//		long t4 = System.currentTimeMillis();
//		IJ.log("ORIGINAL");
//		IJ.log("Time 1: " + (t2-t1));
//		IJ.log("Time 2: " + (t3-t2));
//		IJ.log("Time queue: " + (t4-t3));
//		return result;
//	}
	
	/**
	 * Somewhat awkwardly, two ip.invert()'s in ImageJ do not necessarily 
	 * @param ip
	 */
//	private static void invertReproducibly(ImageProcessor ip) {
//		if (ip == null)
//			return;
//		if (ip instanceof FloatProcessor)
//			ip.multiply(-1.0);
//		else if (ip instanceof ByteProcessor)
//			ip.invert();
//		else if (ip instanceof ShortProcessor) {
//			int max = (int)Math.pow(2, 16)-1;
//			for (int i = 0; i < ip.getWidth() * ip.getHeight(); i++)
//				ip.setf(i, max - ip.getf(i));
//		}
//	}
	
	public static ImageProcessor closingByReconstruction(final ImageProcessor ip, final double radius) {
		final ImageProcessor ipDuplicate = ip.duplicate();
//		invertReproducibly(ipDuplicate);
		ipDuplicate.invert();
		final ImageProcessor ipReconstructed = openingByReconstruction(ipDuplicate, radius);
		if (ipReconstructed != null)
			ipReconstructed.invert();
//		invertReproducibly(ipReconstructed);
		return ipReconstructed;
	}
	
	/**
	 * Ensure that a FloatProcessor only has region minima within a specified Roi, using morphological reconstruction.
	 * 
	 * @param fp
	 * @param roi
	 */
	public static void imposeMinima(final FloatProcessor fp, final Roi roi) {
		final ImageProcessor fpOrig = fp.duplicate();

//		ImageStatistics stats = fp.getStatistics();
		
		fp.setValue(Float.NEGATIVE_INFINITY);
		fp.fill(roi);
		RoiLabeling.fillOutside(fp, roi, Float.POSITIVE_INFINITY);

		fpOrig.copyBits(fp, 0, 0, Blitter.MIN);

		fpOrig.multiply(-1);
		fp.multiply(-1);
		morphologicalReconstruction(fp, fpOrig);
		fp.multiply(-1);
	}
	
	/**
	 * Ensure that a FloatProcessor only has region maxima within a specified Roi, using morphological reconstruction.
	 * 
	 * @param fp
	 * @param roi
	 */
	public static void imposeMaxima(FloatProcessor fp, Roi roi) {
		final ImageProcessor fpOrig = fp.duplicate();
		final ImageStatistics stats = fp.getStatistics();
		
		fp.setValue(Float.POSITIVE_INFINITY);
		fp.fill(roi);
		RoiLabeling.fillOutside(fp, roi, Float.NEGATIVE_INFINITY);

		fpOrig.copyBits(fp, 0, 0, Blitter.MAX);

		morphologicalReconstruction(fp, fpOrig);
		
		fp.setValue(stats.max);
		fp.fill(roi);
	}
	
	/**
	 * Ensure that a FloatProcessor only has region maxima within a specified mask, using morphological reconstruction.
	 * 
	 * @param fp
	 * @param ipMask
	 */
	public static void imposeMaxima(final FloatProcessor fp, final ImageProcessor ipMask) {
		final ImageProcessor fpOrig = fp.duplicate();
		final ImageStatistics stats = fp.getStatistics();
		
		final int w = fp.getWidth();
		final int h = fp.getHeight();
		for (int i = 0; i < w * h; i++) {
			if (ipMask.getf(i) == 0)
				fp.setf(i, Float.NEGATIVE_INFINITY);
			else
				fp.setf(i, Float.POSITIVE_INFINITY);
		}
		fpOrig.copyBits(fp, 0, 0, Blitter.MAX);

		morphologicalReconstruction(fp, fpOrig);

		for (int i = 0; i < w * h; i++) {
			if (ipMask.getf(i) != 0)
				fp.setf(i, (float)stats.max);
		}
	}
	
	
//	public static void imposeMinima(FloatProcessor fp, Roi roi) {
//		ImageProcessor fpOrig = fp.duplicate();
//
//		ImageStatistics stats = fp.getStatistics();
//		
//		fp.setValue(Float.NEGATIVE_INFINITY);
//		fp.fill(roi);
//		ROILabeling.fillOutside(fp, roi, Float.POSITIVE_INFINITY);
//
//		ImageProcessor fp1 = fpOrig.duplicate();
//		fp1.add(0.001 * (stats.max - stats.min));
//		fp1.copyBits(fp, 0, 0, Blitter.MIN);
//
//		fp1.multiply(-1);
//		fp.multiply(-1);
//		morphologicalReconstruction(fp, fp1);
//		fp.multiply(-1);
//	}
//	
//	public static void imposeMaxima(FloatProcessor fp, Roi roi) {
//		ImageProcessor fpOrig = fp.duplicate();
//
//		ImageStatistics stats = fp.getStatistics();
//		
//		fp.setValue(Float.POSITIVE_INFINITY);
//		fp.fill(roi);
//		ROILabeling.fillOutside(fp, roi, Float.NEGATIVE_INFINITY);
//
//		ImageProcessor fp1 = fpOrig.duplicate();
//		fp1.subtract(0.001 * (stats.max - stats.min));
//		fp1.copyBits(fp, 0, 0, Blitter.MAX);
//
//		morphologicalReconstruction(fp, fp1);
//		
////		fp.setValue(stats.max);
////		fp.fill(roi);
//	}

	
	
	private static class IntDequeue {
		
		final private static int MAX_EXPANSION = 1024*10;
		
		private int[] array;
		private int head = 0; // Points to location of first element in queue
		private int tail = 0; // Points to location of *next* insert
		
		private IntDequeue(int capacity) {
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
		private int remove() {
//			return array[head++];
			head++;
			return array[head-1];
		}
		
		private void add(int val) {
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
//			System.out.println("Expanding IntDeque to length " + array.length);
		}
		
	}


	/**
	 * Replace all potential local maxima - as determined by effectively comparing the image with itself after
	 * applying a 3x3 maximum filter - with the lowest possible value via {@code setf(x, y, Float.NEGATIVE_INFINITY)}.
	 * <p>
	 * These can then be filled in by morphological reconstruction on the way to finding 'true' maxima.
	 * 
	 * @param ip
	 * @param threshold
	 * @param x1
	 * @param x2
	 * @param y1
	 * @param y2
	 * @return
	 */
	static ImageProcessor getMaximaLabels(ImageProcessor ip, float threshold, int x1, int x2, int y1, int y2) {
		float minVal = (ip instanceof FloatProcessor) ? Float.NEGATIVE_INFINITY : 0;
		ImageProcessor ip2 = ip.duplicate();
		for (int y = y1+1; y < y2-1; y++) {
			float val = ip.getf(x1, y);
			float nextVal = ip.getf(x1+1, y);
			for (int x = x1+1; x < x2-1; x++) {
				float lastVal = val;
				val = nextVal;
				nextVal = ip.getf(x+1, y);
				if (val < threshold || val < lastVal || val < nextVal)
					continue;
				// We have a value >= its horizontal neighbours... now test the verticals
				if (val >= ip.getf(x-1, y-1) && val >= ip.getf(x, y-1) && val >= ip.getf(x+1, y-1) && 
						val >= ip.getf(x-1, y+1) && val >= ip.getf(x, y+1) && val >= ip.getf(x+1, y+1))
					ip2.setf(x, y, minVal);
			}
		}
		ip2.setRoi(ip.getRoi());
		return ip2;
	}



	/**
		 * Find regional maxima using morphological reconstruction.
		 * @param ip input image
		 * @param threshold the extent to which a maximum must be greater than its surroundings
		 * @param outputBinary if true, the output is a binary image
		 * @return
		 */
		public static ImageProcessor findRegionalMaxima(ImageProcessor ip, float threshold, boolean outputBinary) {
	//		float minVal = (ip instanceof FloatProcessor) ? Float.NEGATIVE_INFINITY : 0;
	
			Rectangle bounds = ip.getRoi();
			int x1, x2, y1, y2;
			if (bounds == null) {
				x1 = 0;
				x2 = ip.getWidth();
				y1 = 0;
				y2 = ip.getHeight();
			} else {
				x1 = bounds.x;
				x2 = bounds.x + bounds.width;
				y1 = bounds.y;
				y2 = bounds.y + bounds.height;
			}
			
			
	//		long startTime = System.currentTimeMillis();
			ImageProcessor ip2 = getMaximaLabels(ip, threshold, x1, x2, y1, y2);
			
	
			morphologicalReconstruction(ip2, ip);
			
			
			// Determine the height of the maxima
			ImageProcessor ipOutput;
			if (outputBinary)
				ipOutput = SimpleThresholding.greaterThan(ip, ip2);
			else {
				ip2.copyBits(ip, 0, 0, Blitter.DIFFERENCE);
				ipOutput = ip2;
			}
			
	//		// Apply a mask, if there is one
	//		byte[] mask = ip.getMaskArray();
	//		if (mask != null) {
	//			for (int i = 0; i < mask.length; i++)
	//				if (mask[i] == 0)
	//					ipOutput.set(i, 0);
	//		}
			return ipOutput;
		}

}
