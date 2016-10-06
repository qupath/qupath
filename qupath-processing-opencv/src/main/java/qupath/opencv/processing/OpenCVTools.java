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
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Collection of static methods to help with using OpenCV from Java.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVTools {
	
	
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
	    
		//		Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC3);
		Mat mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC4);
		mat.put(0, 0, dataBytes);
		return mat;
	}

		public static void labelImage(Mat matBinary, Mat matLabels, int contourType) {
			List<MatOfPoint> contours = new ArrayList<>();
			Mat hierarchy = new Mat();
			Imgproc.findContours(matBinary, contours, hierarchy, contourType, Imgproc.CHAIN_APPROX_SIMPLE);
			// It's convoluted, but drawing contours this way is *much* faster than passing the full list (which is copied by the OpenCV 2.4.9 Java code)
			List<MatOfPoint> temp = new ArrayList<>(1);
			int i = 2;
			int ind = 0;
			for (MatOfPoint contour : contours) {
				temp.clear();
				temp.add(contour);
				Imgproc.drawContours(matLabels, temp, 0, new Scalar(i++), -1, 8, hierarchy.col(ind), 2, new Point(0, 0));
	//			Imgproc.drawContours(matLabels, temp, 0, new Scalar(i++), -1);
				ind++;
			}
		}

	public static void watershedDistanceTransformSplit(Mat matBinary, int maxFilterRadius) {
			Mat matWatershedSeedsBinary;
			
			// Create a background mask
			Mat matBackground = new Mat();
			Core.compare(matBinary, new Scalar(255), matBackground, Core.CMP_NE);
	
			// Separate by shape using the watershed transform
			Mat matDistanceTransform = new Mat();
			Imgproc.distanceTransform(matBinary, matDistanceTransform, Imgproc.CV_DIST_L2, Imgproc.CV_DIST_MASK_PRECISE);
			// Find local maxima
			matWatershedSeedsBinary = new Mat();
			Imgproc.dilate(matDistanceTransform, matWatershedSeedsBinary, OpenCVTools.getCircularStructuringElement(maxFilterRadius));
			Core.compare(matDistanceTransform, matWatershedSeedsBinary, matWatershedSeedsBinary, Core.CMP_EQ);
			matWatershedSeedsBinary.setTo(new Scalar(0), matBackground);
			// Dilate slightly to merge nearby maxima
			Imgproc.dilate(matWatershedSeedsBinary, matWatershedSeedsBinary, OpenCVTools.getCircularStructuringElement(2));
	
			// Create labels for watershed
			Mat matLabels = new Mat(matDistanceTransform.size(), CvType.CV_32F, new Scalar(0));
			labelImage(matWatershedSeedsBinary, matLabels, Imgproc.RETR_CCOMP);
	
			// Remove everything outside the thresholded region
			matLabels.setTo(new Scalar(0), matBackground);
	
			// Do watershed
			// 8-connectivity is essential for the watershed lines to be preserved - otherwise OpenCV's findContours could not be used
			ProcessingCV.doWatershed(matDistanceTransform, matLabels, 0.1, true);
	
			// Update the binary image to remove the watershed lines
			Core.multiply(matBinary, matLabels, matBinary, 1, matBinary.type());
		}

	public static Mat getCircularStructuringElement(int radius) {
			Mat strel = new Mat(radius*2+1, radius*2+1, CvType.CV_8UC1, new Scalar(0));
			Imgproc.circle(strel, new Point(radius, radius), radius, new Scalar(1), -1);
			return strel;
		}

	/*
	 * Invert a binary image.
	 * Technically, set all zero pixels to 255 and all non-zero pixels to 0.
	 */
	public static void invertBinary(Mat matBinary, Mat matDest) {
		Core.compare(matBinary, new Scalar(0), matDest, Core.CMP_EQ);
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
		List<MatOfPoint> contours = new ArrayList<>();
		Mat hierarchy = new Mat();
		Imgproc.findContours(matHoles, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
		List<MatOfPoint> contoursTemp = new ArrayList<>(1);
		Scalar color = new Scalar(255);
		int ind = 0;
		for (MatOfPoint contour : contours) {
			// Only fill the small, inner contours
			if (hierarchy.get(0, ind)[3] >= 0 || Imgproc.contourArea(contour) > maxArea) {
				ind++;
				continue;
			}
			contoursTemp.clear();
			contoursTemp.add(contour);
			Imgproc.drawContours(matBinary, contoursTemp, 0, color, -1);
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
		Imgproc.dilate(matWatershedIntensities, matTemp, strel);
		Core.compare(matWatershedIntensities, matTemp, matTemp, Core.CMP_EQ);
		Imgproc.dilate(matTemp, matTemp, getCircularStructuringElement(2));
		Mat matWatershedSeedsBinary = matTemp;
	
		// Remove everything outside the thresholded region
		Core.min(matWatershedSeedsBinary, matBinary, matWatershedSeedsBinary);
	
		// Create labels for watershed
		Mat matLabels = new Mat(matWatershedIntensities.size(), CvType.CV_32F, new Scalar(0));
		labelImage(matWatershedSeedsBinary, matLabels, Imgproc.RETR_CCOMP);
		
		// Do watershed
		// 8-connectivity is essential for the watershed lines to be preserved - otherwise OpenCV's findContours could not be used
		ProcessingCV.doWatershed(matWatershedIntensities, matLabels, threshold, true);
	
		// Update the binary image to remove the watershed lines
		Core.multiply(matBinary, matLabels, matBinary, 1, matBinary.type());
	}


}
