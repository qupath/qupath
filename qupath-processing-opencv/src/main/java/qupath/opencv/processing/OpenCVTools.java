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

package qupath.opencv.processing;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.indexer.ByteIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Scalar;

/**
 * Collection of static methods to help with using OpenCV from Java.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVTools {
	
	/**
	 * Convert an RGB image to an OpenCV Mat.
	 * 
	 * @param img
	 * @return
	 */
	public static Mat imageToMat(BufferedImage img) {
//		img.getRaster().getDataBuffer()
//		img.getRaster().getDataBuffer().getDataType()
//		byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
		
		ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);        
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(data);
        byte[] dataBytes = byteBuffer.array();
		
//		byte[] dataBytes = new byte[data.length * 4];
//		int j = 0;
//	    for (int x : data) {
//	    	dataBytes[j] = (byte) ((x >>> 0) & 0xff);           
//	    	dataBytes[j] = (byte) ((x >>> 8) & 0xff);
//	    	dataBytes[j] = (byte) ((x >>> 16) & 0xff);
//	    	dataBytes[j] = (byte) ((x >>> 24) & 0xff);
//	        j++;
//	    }
	    
		//		Mat mat = new Mat(img.getHeight(), img.getWidth(), CV_8UC3);
		Mat mat = new Mat(img.getHeight(), img.getWidth(), CV_8UC4);
		ByteBuffer buffer = mat.createBuffer();
		buffer.put(dataBytes);
//		mat.put(0, 0, dataBytes);
		return mat;
	}

	public static void labelImage(Mat matBinary, Mat matLabels, int contourType) {
		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(matBinary, contours, hierarchy, contourType, opencv_imgproc.CHAIN_APPROX_SIMPLE);
		int i = 2;
		int ind = 0;
		Point offset = new Point(0, 0);
		for (int c = 0; c < contours.size(); c++) {
			opencv_imgproc.drawContours(matLabels, contours, c, Scalar.all(i++), -1, 8, hierarchy.col(ind), 2, offset);
//			opencv_imgproc.drawContours(matLabels, temp, 0, new Scalar(i++), -1);
			ind++;
		}
	}
	
	
	/**
	 * Set pixels from a byte array.
	 * 
	 * There is no real error checking; it is assumed that the pixel array is in the appropriate format.
	 * 
	 * @param mat
	 * @param pixels
	 */
	public static void putPixelsUnsigned(Mat mat, byte[] pixels) {
		Indexer indexer = mat.createIndexer();
		if (indexer instanceof ByteIndexer) {
			((ByteIndexer) indexer).put(0, pixels);
		} else
			throw new IllegalArgumentException("Expected a ByteIndexer, but instead got " + indexer.getClass());
	}
	
	/**
	 * Set pixels from a float array.
	 * 
	 * There is no real error checking; it is assumed that the pixel array is in the appropriate format.
	 * 
	 * @param mat
	 * @param pixels
	 */
	public static void putPixelsFloat(Mat mat, float[] pixels) {
		Indexer indexer = mat.createIndexer();
		if (indexer instanceof FloatIndexer) {
			((FloatIndexer) indexer).put(0, pixels);
		} else
			throw new IllegalArgumentException("Expected a FloatIndexer, but instead got " + indexer.getClass());
	}
	

	public static void watershedDistanceTransformSplit(Mat matBinary, int maxFilterRadius) {
			Mat matWatershedSeedsBinary;
			
			// Create a background mask
			Mat matBackground = new Mat();
			compare(matBinary, new Mat(1, 1, CV_32FC1, Scalar.WHITE), matBackground, CMP_NE);
	
			// Separate by shape using the watershed transform
			Mat matDistanceTransform = new Mat();
			opencv_imgproc.distanceTransform(matBinary, matDistanceTransform, opencv_imgproc.CV_DIST_L2, opencv_imgproc.CV_DIST_MASK_PRECISE);
			// Find local maxima
			matWatershedSeedsBinary = new Mat();
			opencv_imgproc.dilate(matDistanceTransform, matWatershedSeedsBinary, OpenCVTools.getCircularStructuringElement(maxFilterRadius));
			compare(matDistanceTransform, matWatershedSeedsBinary, matWatershedSeedsBinary, CMP_EQ);
			matWatershedSeedsBinary.setTo(new Mat(1, 1, matWatershedSeedsBinary.type(), Scalar.ZERO), matBackground);
			// Dilate slightly to merge nearby maxima
			opencv_imgproc.dilate(matWatershedSeedsBinary, matWatershedSeedsBinary, OpenCVTools.getCircularStructuringElement(2));
	
			// Create labels for watershed
			Mat matLabels = new Mat(matDistanceTransform.size(), CV_32F, Scalar.ZERO);
			labelImage(matWatershedSeedsBinary, matLabels, opencv_imgproc.RETR_CCOMP);
	
			// Remove everything outside the thresholded region
			matLabels.setTo(new Mat(1, 1, matLabels.type(), Scalar.ZERO), matBackground);
	
			// Do watershed
			// 8-connectivity is essential for the watershed lines to be preserved - otherwise OpenCV's findContours could not be used
			ProcessingCV.doWatershed(matDistanceTransform, matLabels, 0.1, true);
	
			// Update the binary image to remove the watershed lines
			multiply(matBinary, matLabels, matBinary, 1, matBinary.type());
		}

	public static Mat getCircularStructuringElement(int radius) {
		// TODO: Find out why this doesn't just call a standard request for a strel...
		Mat strel = new Mat(radius*2+1, radius*2+1, CV_8UC1, Scalar.ZERO);
		opencv_imgproc.circle(strel, new Point(radius, radius), radius, Scalar.ONE, -1, LINE_8, 0);
		return strel;
	}

	/*
	 * Invert a binary image.
	 * Technically, set all zero pixels to 255 and all non-zero pixels to 0.
	 */
	public static void invertBinary(Mat matBinary, Mat matDest) {
		compare(matBinary, new Mat(1, 1, CV_32FC1, Scalar.ZERO), matDest, CMP_EQ);
	}
	
	
	/**
	 * Extract pixels as a float[] array.
	 * 
	 * @param mat
	 * @param pixels
	 * @return
	 */
	public static float[] extractPixels(Mat mat, float[] pixels) {
		if (pixels == null)
			pixels = new float[(int)mat.total()];
		Mat mat2 = null;
		if (mat.depth() != CV_32F) {
			mat2 = new Mat();
			mat.convertTo(mat2, CV_32F);
			mat = mat2;
		}
		FloatBuffer buffer = mat.createBuffer();
		buffer.get(pixels);
		if (mat2 != null)
			mat2.release();
		return pixels;
	}
	

	/**
	 * Fill holes in a binary image (1-channel, 8-bit unsigned) with an area <= maxArea.
	 * 
	 * @param matBinary
	 * @param maxArea
	 */
	public static void fillSmallHoles(Mat matBinary, double maxArea) {
		Mat matHoles = new Mat();
		invertBinary(matBinary, matHoles);
		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(matHoles, contours, hierarchy, opencv_imgproc.RETR_CCOMP, opencv_imgproc.CHAIN_APPROX_SIMPLE);
		Scalar color = Scalar.WHITE;
		int ind = 0;
		Point offset = new Point(0, 0);
		Indexer indexerHierearchy = hierarchy.createIndexer();
		for (int c = 0; c < contours.size(); c++) {
			Mat contour = contours.get(c);
			// Only fill the small, inner contours
			// TODO: Check hierarchy indexing after switch to JavaCPP!!
			if (indexerHierearchy.getDouble(0, ind, 3) >= 0 || opencv_imgproc.contourArea(contour) > maxArea) {
				ind++;
				continue;
			}
			opencv_imgproc.drawContours(matBinary, contours, c, color, -1, LINE_8, null, Integer.MAX_VALUE, offset);
			ind++;
		}
	}

	/**
	 * Apply a watershed transform to refine a binary image, guided either by a distance transform or a supplied intensity image.
	 * 
	 * @param matBinary - thresholded, 8-bit unsigned integer binary image
	 * @param matIntensities - optional intensity image for applying watershed transform; if not set, distance transform of binary will be used
	 * @param threshold
	 */
	public static void watershedIntensitySplit(Mat matBinary, Mat matWatershedIntensities, double threshold, int maximaRadius) {
	
		// Separate by intensity using the watershed transform
		// Find local maxima
		Mat matTemp = new Mat();
		
		Mat strel = getCircularStructuringElement(maximaRadius);
		opencv_imgproc.dilate(matWatershedIntensities, matTemp, strel);
		compare(matWatershedIntensities, matTemp, matTemp, CMP_EQ);
		opencv_imgproc.dilate(matTemp, matTemp, getCircularStructuringElement(2));
		Mat matWatershedSeedsBinary = matTemp;
	
		// Remove everything outside the thresholded region
		min(matWatershedSeedsBinary, matBinary, matWatershedSeedsBinary);
	
		// Create labels for watershed
		Mat matLabels = new Mat(matWatershedIntensities.size(), CV_32F, Scalar.ZERO);
		labelImage(matWatershedSeedsBinary, matLabels, opencv_imgproc.RETR_CCOMP);
		
		// Do watershed
		// 8-connectivity is essential for the watershed lines to be preserved - otherwise OpenCV's findContours could not be used
		ProcessingCV.doWatershed(matWatershedIntensities, matLabels, threshold, true);
	
		// Update the binary image to remove the watershed lines
		multiply(matBinary, matLabels, matBinary, 1, matBinary.type());
	}


}
