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
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.nio.FloatBuffer;
import static org.bytedeco.javacpp.opencv_core.*;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.indexer.ByteIndexer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.javacpp.indexer.ShortIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.indexer.UShortIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Scalar;

import qupath.lib.common.ColorTools;

/**
 * Collection of static methods to help with using OpenCV from Java.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVTools {
	
	private static Logger logger = LoggerFactory.getLogger(OpenCVTools.class);
	
	/**
	 * Convert a BufferedImage to an OpenCV Mat.
	 * 
	 * An effort will be made to do a sensible conversion based on the BufferedImage type, 
	 * returning a Mat with a suitable type.
	 * 
	 * BGR and RGB images will remain with the same channel order, and an alpha channel 
	 * (if present) will be included at the end (i.e. to give BGRA or RGBA).
	 * 
	 * Note: the behavior of this method has changed; in QuPath &lt;= 0.1.2 only
	 * RGB images were really supported, and an RGB conversion was *always* made.
	 * 
	 * @see #imageToMatRGB
	 * @see #imageToMatBGR
	 * 
	 * @param img
	 * @return
	 */
	public static Mat imageToMat(BufferedImage img) {
		switch (img.getType()) {
			case BufferedImage.TYPE_INT_BGR:
			case BufferedImage.TYPE_3BYTE_BGR:
				return imageToMatBGR(img, false);
			case BufferedImage.TYPE_4BYTE_ABGR:
			case BufferedImage.TYPE_4BYTE_ABGR_PRE:
				return imageToMatBGR(img, true);
			case BufferedImage.TYPE_INT_ARGB:
			case BufferedImage.TYPE_INT_ARGB_PRE:
				return imageToMatRGB(img, true);
			case BufferedImage.TYPE_USHORT_555_RGB:
			case BufferedImage.TYPE_USHORT_565_RGB:
			case BufferedImage.TYPE_INT_RGB:
				return imageToMatRGB(img, false);
			case BufferedImage.TYPE_USHORT_GRAY:
		}
		
		int width = img.getWidth();
		int height = img.getHeight();
		WritableRaster raster = img.getRaster();
		
		DataBuffer buffer = raster.getDataBuffer();
		int nChannels = raster.getNumBands();
		int typeCV;
		switch (buffer.getDataType()) {
			case DataBuffer.TYPE_BYTE:
				typeCV = CV_8UC(nChannels);
				break;
//			case DataBuffer.TYPE_DOUBLE:
//				typeCV = CV_64FC(nChannels); 
//				mat = new Mat(height, width, typeCV, Scalar.ZERO);
//				break;
			case DataBuffer.TYPE_FLOAT:
				typeCV = CV_32FC(nChannels); 
				break;
			case DataBuffer.TYPE_INT:
				typeCV = CV_32SC(nChannels); // Assuming signed int
				break;
			case DataBuffer.TYPE_SHORT:
				typeCV = CV_16SC(nChannels); 
				break;
			case DataBuffer.TYPE_USHORT:
				typeCV = CV_16SC(nChannels); 
				break;
			default:
				typeCV = CV_64FC(nChannels); // Assume 64-bit is as flexible as we can manage
		}
		
		// Create a new Mat & put the pixels
		Mat mat = new Mat(height, width, typeCV, Scalar.ZERO);
		putPixels(raster, mat);
		return mat;
	}
	
	
	private static void putPixels(WritableRaster raster, UByteIndexer indexer) {
		int[] pixels = null;
		int width = raster.getWidth();
		int height = raster.getHeight();
		for (int b = 0; b < raster.getNumBands(); b++) {
			pixels = raster.getSamples(0, 0, width, height, b, pixels);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					indexer.put(y, x, b, pixels[y*width + x]);
				}
			}
		}
	}
	
	private static void putPixels(WritableRaster raster, UShortIndexer indexer) {
		int[] pixels = null;
		int width = raster.getWidth();
		int height = raster.getHeight();
		for (int b = 0; b < raster.getNumBands(); b++) {
			pixels = raster.getSamples(0, 0, width, height, b, pixels);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					indexer.put(y, x, b, pixels[y*width + x]);
				}
			}
		}
	}
	
	private static void putPixels(WritableRaster raster, ShortIndexer indexer) {
		int[] pixels = null;
		int width = raster.getWidth();
		int height = raster.getHeight();
		for (int b = 0; b < raster.getNumBands(); b++) {
			pixels = raster.getSamples(0, 0, width, height, b, pixels);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					indexer.put(y, x, b, (short)pixels[y*width + x]);
				}
			}
		}
	}
	
	private static void putPixels(WritableRaster raster, FloatIndexer indexer) {
		float[] pixels = null;
		int width = raster.getWidth();
		int height = raster.getHeight();
		for (int b = 0; b < raster.getNumBands(); b++) {
			pixels = raster.getSamples(0, 0, width, height, b, pixels);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					indexer.put(y, x, b, pixels[y*width + x]);
				}
			}
		}
	}
	
	/**
	 * Put the pixels for the specified raster into a preallocated Mat.
	 * 
	 * @param raster
	 * @param mat
	 */
	private static void putPixels(WritableRaster raster, Mat mat) {
		Indexer indexer = mat.createIndexer();
		if (indexer instanceof UByteIndexer)
			putPixels(raster, (UByteIndexer)indexer);
		else if (indexer instanceof ShortIndexer)
			putPixels(raster, (ShortIndexer)indexer);
		else if (indexer instanceof UShortIndexer)
			putPixels(raster, (UShortIndexer)indexer);
		else if (indexer instanceof FloatIndexer)
			putPixels(raster, (FloatIndexer)indexer);
		else {
			double[] pixels = null;
			int width = raster.getWidth();
			int height = raster.getHeight();
			long[] indices = new long[3];
			for (int b = 0; b < raster.getNumBands(); b++) {
				pixels = raster.getSamples(0, 0, width, height, b, pixels);
				indices[2] = b;
				for (int y = 0; y < height; y++) {
					indices[0] = y;
					for (int x = 0; x < width; x++) {
						indices[1] = x;
						indexer.putDouble(indices, pixels[y*width + x]);
					}
				}
			}
		}
		indexer.release();
	}
	
	
	/**
	 * Convert a Mat to a BufferedImage.
	 * 
	 * This is equivalent to matToBufferedImage(mat, null);
	 * As such, the ColorModel may or may not end up being something useful.
	 * 
	 * @see #matToBufferedImage
	 * 
	 * @param mat
	 * @return
	 */
	public static BufferedImage matToBufferedImage(final Mat mat) {
		return matToBufferedImage(mat, null);
	}

	
	/**
	 * Convert a Mat to a BufferedImage.
	 * 
	 * If no ColorModel is specified, a grayscale model will be used for single-channel 8-bit 
	 * images and RGB/ARGB for 3/4 channel 8-bit images.
	 * 
	 * For all other cases a ColorModel should be specified for meaningful display.
	 * 
	 * @param mat
	 * @param colorModel
	 * @return
	 */
	public static BufferedImage matToBufferedImage(final Mat mat, ColorModel colorModel) {
		
		int type;
		int bpp = 0;
		switch (mat.depth()) {
			case CV_8U:
				type = DataBuffer.TYPE_BYTE;
				bpp = 8;
				break;
			case CV_8S:
				type = DataBuffer.TYPE_SHORT; // Byte is unsigned
				bpp = 16;
				break;
			case CV_16U:
				type = DataBuffer.TYPE_USHORT;
				bpp = 16;
				break;
			case CV_16S:
				type = DataBuffer.TYPE_SHORT;
				bpp = 16;
				break;
			case CV_32S:
				type = DataBuffer.TYPE_INT;
				bpp = 32;
				break;
			case CV_32F:
				type = DataBuffer.TYPE_FLOAT;
				bpp = 32;
				break;
			default:
				logger.warn("Unknown Mat depth {}, will default to CV64F ({})", mat.depth(), CV_64F);
			case CV_64F:
				type = DataBuffer.TYPE_DOUBLE;
				bpp = 64;
		}
		
		// Create a suitable raster
		int width = mat.cols();
		int height = mat.rows();
		int channels = mat.channels();
	
		// We might generate an image for a special case
		BufferedImage img = null;
		
		// Handle some special cases
		if (colorModel == null) {
			if (type == DataBuffer.TYPE_BYTE) {
				if (channels == 1) {
					img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
					// TODO: Set the bytes
				} else if (channels == 3) {
					img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				} else if (channels == 4) {
					img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				}
			}
		} else if (colorModel instanceof IndexColorModel) {
			img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, (IndexColorModel)colorModel);
		}
		
		// Create the image
		WritableRaster raster;
		if (img != null) {
			raster = img.getRaster();
		} else if (colorModel != null) {
			raster = colorModel.createCompatibleWritableRaster(width, height);
			img = new BufferedImage(colorModel, raster, false, null);
		} else {
			// Create some kind of raster we can use
			raster = WritableRaster.createBandedRaster(type, width, height, channels, null);
			// We do need a ColorModel or some description
			colorModel = new DummyColorModel(bpp * channels);
			img = new BufferedImage(colorModel, raster, false, null);
		}
		MatVector matvector = new MatVector();
		split(mat, matvector);
		// We don't know which of the 3 supported array types will be needed yet...
		int[] pixelsInt = null;
		float[] pixelsFloat = null;
		double[] pixelsDouble = null;
		for (int b = 0; b < channels; b++) {
			// Extract pixels for the current channel
			Mat matChannel = matvector.get(b);
			Indexer indexer = matChannel.createIndexer();
			if (indexer instanceof UByteIndexer) {
				if (pixelsInt == null)
					pixelsInt = new int[width*height];
				((UByteIndexer) indexer).get(0L, pixelsInt);
			} else if (indexer instanceof UShortIndexer) {
				if (pixelsInt == null)
					pixelsInt = new int[width*height];
				((UShortIndexer) indexer).get(0L, pixelsInt);
			} else if (indexer instanceof FloatIndexer) {
				if (pixelsFloat == null)
					pixelsFloat = new float[width*height];
				((FloatIndexer) indexer).get(0L, pixelsFloat);				
			} else if (indexer instanceof DoubleIndexer) {
				if (pixelsDouble == null)
					pixelsDouble = new double[width*height];
				((DoubleIndexer) indexer).get(0L, pixelsDouble);				
			} else {
				if (pixelsDouble == null)
					pixelsDouble = new double[width*height];
				// This is inefficient, but unlikely to occur too often
				pixelsDouble = new double[width * height];
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++){
						pixelsDouble[y * width + x] = indexer.getDouble(y, x, b);
					}
				}
			}
			// Set the samples
			if (pixelsInt != null)
				raster.setSamples(0, 0, width, height, b, pixelsInt);
			else if (pixelsFloat != null)
				raster.setSamples(0, 0, width, height, b, pixelsFloat);
			else if (pixelsDouble != null)
				raster.setSamples(0, 0, width, height, b, pixelsDouble);
		}
		return img;
	}
		
	

	/**
	 * Extract 8-bit unsigned pixels from a BufferedImage as a multichannel RGB Mat.
	 * 
	 * If Alpha is requested, it will be returned as a 4th channel.
	 * 
	 * @param img
	 * @param includeAlpha
	 * @return
	 */
	public static Mat imageToMatRGB(final BufferedImage img, final boolean includeAlpha) {
		return imageToMatRGBorBGR(img, false, includeAlpha);
	}
	
	/**
	 * Extract 8-bit unsigned pixels from a BufferedImage as a multichannel BGR Mat.
	 * 
	 * If Alpha is requested, it will be returned as a 4th channel.
	 * 
	 * @param img
	 * @param includeAlpha
	 * @return
	 */
	public static Mat imageToMatBGR(final BufferedImage img, final boolean includeAlpha) {
		return imageToMatRGBorBGR(img, true, includeAlpha);
	}
	
	/**
	 * Extract 8-bit unsigned pixels from a BufferedImage, either as RGB (default) 
	 * or BGR (OpenCV's preferred format).
	 * 
	 * If Alpha is requested, it will be returned as the final channel.
	 * 
	 * @param img
	 * @param doBGR
	 * @param includeAlpha
	 * @return
	 */
	private static Mat imageToMatRGBorBGR(final BufferedImage img, final boolean doBGR, final boolean includeAlpha) {
		// We can request the RGB values directly
		int width = img.getWidth();
		int height = img.getHeight();
		int[] data = img.getRGB(0, 0, width, height, null, 0, img.getWidth());
		
		Mat mat;
		if (includeAlpha)
			mat = new Mat(height, width, CV_8UC4);
		else
			mat = new Mat(height, width, CV_8UC3);

		UByteIndexer indexer = mat.createIndexer();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int val = data[y*width + x];
				int r = ColorTools.red(val);
				int g = ColorTools.green(val);
				int b = ColorTools.blue(val);
				if (doBGR) {
					indexer.put(y, x, 0, b);
					indexer.put(y, x, 1, g);
					indexer.put(y, x, 2, r);
				} else {
					indexer.put(y, x, 0, r);
					indexer.put(y, x, 1, g);
					indexer.put(y, x, 2, b);
				}
				if (includeAlpha) {
					int a = ColorTools.alpha(val);
					indexer.put(y, x, 3, a);
				}
			}
		}
		indexer.release();
		
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
	 * Fill holes in a binary image (1-channel, 8-bit unsigned) with an area &lt;= maxArea.
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
	 * @param matBinary thresholded, 8-bit unsigned integer binary image
	 * @param matWatershedIntensities optional intensity image for applying watershed transform; if not set, distance transform of binary will be used
	 * @param threshold
	 * @param maximaRadius
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
	
	
	
	
	
	
	/**
	 * An extremely tolerant ColorModel that assumes everything should be shown in black.
	 * Assumes QuPath takes care of display elsewhere, so this is just needed to avoid any trouble with null pointer exceptions.
	 */
	static class DummyColorModel extends ColorModel {
		
		DummyColorModel(final int nBits) {
			super(nBits);
		}

		@Override
		public int getRed(int pixel) {
			return 0;
		}

		@Override
		public int getGreen(int pixel) {
			return 0;
		}

		@Override
		public int getBlue(int pixel) {
			return 0;
		}

		@Override
		public int getAlpha(int pixel) {
			return 0;
		}
		
		@Override
		public boolean isCompatibleRaster(Raster raster) {
			// We accept everything...
			return true;
		}
		
		@Override
		public ColorModel coerceData(WritableRaster raster, boolean isAlphaPremultiplied) {
			// Don't do anything
			return null;
		}
		
		
	};
	

}
