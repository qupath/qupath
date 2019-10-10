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

package qupath.opencv.tools;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.bytedeco.opencv.global.opencv_core.*;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.javacpp.indexer.ByteIndexer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.javacpp.indexer.ShortIndexer;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.indexer.UShortIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;

import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

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
	 * <p>
	 * An effort will be made to do a sensible conversion based on the BufferedImage type, 
	 * returning a Mat with a suitable type.
	 * <p>
	 * BGR and RGB images will remain with the same channel order, and an alpha channel 
	 * (if present) will be included at the end (i.e. to give BGRA or RGBA).
	 * <p>
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
				typeCV = CV_16UC(nChannels); 
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
	 * <p>
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
	 * <p>
	 * If no ColorModel is specified, a grayscale model will be used for single-channel 8-bit 
	 * images and RGB/ARGB for 3/4 channel 8-bit images.
	 * <p>
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
			var sampleModel = new BandedSampleModel(type, width, height, channels);
			raster = WritableRaster.createWritableRaster(sampleModel, null);
			// We do need a ColorModel or some description
			colorModel = ColorModelFactory.getDummyColorModel(bpp * channels);
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
	 * Extract 8-bit unsigned pixels from a BufferedImage as a multichannel RGB(A) Mat.
	 * 
	 * @param img input image
	 * @param includeAlpha if true, return any available alpha data as a 4th channel.
	 * @return
	 */
	public static Mat imageToMatRGB(final BufferedImage img, final boolean includeAlpha) {
		return imageToMatRGBorBGR(img, false, includeAlpha);
	}
	
	/**
	 * Extract 8-bit unsigned pixels from a BufferedImage as a multichannel BGR(A) Mat.
	 * 
	 * @param img input image
	 * @param includeAlpha if true, return any available alpha data as a 4th channel.
	 * @return
	 */
	public static Mat imageToMatBGR(final BufferedImage img, final boolean includeAlpha) {
		return imageToMatRGBorBGR(img, true, includeAlpha);
	}
	
	/**
	 * Extract 8-bit unsigned pixels from a BufferedImage, either as RGB(A) (default) 
	 * or BGR(A) (OpenCV's preferred format).
	 * 
	 * @param img input image
	 * @param doBGR if true, request BGR rather than RGB
	 * @param includeAlpha if true, return any available alpha data as a 4th channel.
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
	
	/**
	 * Create a labelled image from a binary image using findContours and drawContours.
	 * @param matBinary
	 * @param matLabels
	 * @param contourRetrievalMode defined within OpenCV findContours
	 */
	public static void labelImage(Mat matBinary, Mat matLabels, int contourRetrievalMode) {
		MatVector contours = new MatVector();
		Mat hierarchy = new Mat();
		opencv_imgproc.findContours(matBinary, contours, hierarchy, contourRetrievalMode, opencv_imgproc.CHAIN_APPROX_SIMPLE);
		Point offset = new Point(0, 0);
		for (int c = 0; c < contours.size(); c++) {
			opencv_imgproc.drawContours(matLabels, contours, c, Scalar.all(c+1), -1, 8, hierarchy, 2, offset);
		}
		hierarchy.close();
		contours.close();
	}
	
	
	/**
	 * Set pixels from a byte array.
	 * <p>
	 * There is no real error checking; it is assumed that the pixel array is in the appropriate format.
	 * 
	 * @param mat
	 * @param pixels
	 */
	public static void putPixelsUnsigned(Mat mat, byte[] pixels) {
		Indexer indexer = mat.createIndexer();
		if (indexer instanceof ByteIndexer) {
			((ByteIndexer) indexer).put(0, pixels);
		} else if (indexer instanceof UByteIndexer) {
			int n = pixels.length;
			for (int i = 0; i < n; i++) {
				((UByteIndexer) indexer).put(0, pixels[i] & 0xFF);
			}
		} else
			throw new IllegalArgumentException("Expected a ByteIndexer, but instead got " + indexer.getClass());
	}
	
	/**
	 * Set pixels from a float array.
	 * <p>
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

	/**
	 * Create a Mat depicting a circle of the specified radius.
	 * <p>
	 * Pixels within the circle have the value 1, pixels outside are 0.
	 * 
	 * @param radius
	 * @return
	 */
	public static Mat getCircularStructuringElement(int radius) {
		// TODO: Find out why this doesn't just call a standard request for a strel...
		Mat strel = new Mat(radius*2+1, radius*2+1, CV_8UC1, Scalar.ZERO);
		opencv_imgproc.circle(strel, new Point(radius, radius), radius, Scalar.ONE, -1, opencv_imgproc.LINE_8, 0);
		return strel;
	}

	/**
	 * Invert a binary image.
	 * <p>
	 * Specifically, sets all zero pixels to 255 and all non-zero pixels to 0.
	 * 
	 * @param matBinary
	 * @param matDest
	 */
	public static void invertBinary(Mat matBinary, Mat matDest) {
		compare(matBinary, new Mat(1, 1, CV_32FC1, Scalar.ZERO), matDest, CMP_EQ);
	}
	
	
	/**
	 * Extract pixels as a float[] array.
	 * <p>
	 * Implementation note: In its current form, this is not terribly efficient. 
	 * Also be wary if the Mat is not continuous.
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
		FloatIndexer idx = mat.createIndexer();
		idx.get(0L, pixels);
		idx.release();
		
//		FloatBuffer buffer = mat.createBuffer();
//		buffer.get(pixels);
		if (mat2 != null)
			mat2.release();
		return pixels;
	}
	
	/**
	 * Convert a Mat to a {@link SimpleImage}.
	 * @param mat
	 * @param channel
	 * @return
	 */
	public static SimpleImage matToSimpleImage(Mat mat, int channel) {
		Mat temp = mat;
		if (mat.channels() > 1) {
			temp = new Mat();
			opencv_core.extractChannel(mat, temp, channel);
		}
		float[] pixels = extractPixels(temp, null);
		return SimpleImages.createFloatImage(pixels, mat.cols(), mat.rows());
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
			opencv_imgproc.drawContours(matBinary, contours, c, color, -1, opencv_imgproc.LINE_8, null, Integer.MAX_VALUE, offset);
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
	 * Convert a single-channel OpenCV {@code Mat} into an ImageJ {@code ImageProcessor}.
	 * 
	 * @param mat
	 * @return
	 */
	public static ImageProcessor matToImageProcessor(Mat mat) {
		if (mat.channels() != 1)
			throw new IllegalArgumentException("Only a single-channel Mat can be converted to an ImageProcessor! Specified Mat has " + mat.channels() + " channels");
	    int w = mat.cols();
	    int h = mat.rows();
	    if (mat.depth() == opencv_core.CV_32F) {
	        FloatIndexer indexer = mat.createIndexer();
	        float[] pixels = new float[w*h];
	        indexer.get(0L, pixels);
	        return new FloatProcessor(w, h, pixels);
	    } else if (mat.depth() == opencv_core.CV_8U) {
	        UByteIndexer indexer = mat.createIndexer();
	        int[] pixels = new int[w*h];
	        indexer.get(0L, pixels);
	        ByteProcessor bp = new ByteProcessor(w, h);
	        for (int i = 0; i < pixels.length; i++)
	        	bp.set(i, pixels[i]);
	        return bp;
	    } else if (mat.depth() == opencv_core.CV_16U) {
	        UShortIndexer indexer = mat.createIndexer();
	        int[] pixels = new int[w*h];
	        indexer.get(0L, pixels);
	        short[] shortPixels = new short[pixels.length];
	        for (int i = 0; i < pixels.length; i++)
	        	shortPixels[i] = (short)pixels[i];
	        return new ShortProcessor(w, h, shortPixels, null); // TODO: Test!
	    } else {
	    	Mat mat2 = new Mat();
	        mat.convertTo(mat2, opencv_core.CV_32F);
	        ImageProcessor ip = matToImageProcessor(mat2);
	        mat2.release();
	        return ip;
	    }
	}


	/**
	 * Convert an OpenCV {@code Mat} into an ImageJ {@code ImagePlus}.
	 * 
	 * @param mat
	 * @param title
	 * @return
	 */
	public static ImagePlus matToImagePlus(Mat mat, String title) {
	    if (mat.channels() == 1) {
	        return new ImagePlus(title, matToImageProcessor(mat));
	    }
	    return matToImagePlus(title, mat);
//	    MatVector matvec = new MatVector();
//	    opencv_core.split(mat, matvec);
//	    return matToImagePlus(matvec, title);
	};
	
	/**
	 * Convert an OpenCV {@code MatVector} into an ImageJ {@code ImagePlus}.
	 * 
	 * @param title
	 * @param mats
	 */
	public static ImagePlus matToImagePlus(String title, Mat... mats) {
		ImageStack stack = null;
		int nChannels = 1;
	    for (Mat mat : mats) {
	    	if (stack == null) {
	    		stack = new ImageStack(mat.cols(), mat.rows());
	    	} else if (mat.channels() != nChannels) {
	    		throw new IllegalArgumentException("Number of channels must be the same for all Mats!");
	    	}
	    	
	    	if (mat.channels() == 1) {
		    	ImageProcessor ip = matToImageProcessor(mat);
		        stack.addSlice(ip);	    		
	    	} else {
	    		nChannels = mat.channels();
	    		MatVector split = new MatVector();
	    		opencv_core.split(mat, split);
	    		for (int c = 0; c < split.size(); c++)
	    			stack.addSlice(matToImageProcessor(split.get(c)));	
	    	}
	    }
	    ImagePlus imp = new ImagePlus(title, stack);
	    imp.setDimensions(nChannels, mats.length, 1);
	    return nChannels == 1 ? imp : new CompositeImage(imp);
	}


	/**
	 * Get filter coefficients for a 1D Gaussian (derivative) kernel.
	 * 
	 * @param sigma Gaussian sigma
	 * @param order order of the derivative: 0, ('standard' Gaussian filter), 1 (first derivative) or 2 (second derivative)
	 * @param length number of coefficients in the kernel; in general, this should be an odd number
	 * @return
	 */
	public static double[] getGaussianDeriv(double sigma, int order, int length) {
		int n = length / 2;
	    double[] kernel = new double[length];
	    double denom2 = 2 * sigma * sigma;
	    double denom = sigma * Math.sqrt(2 * Math.PI);
	    switch (order) {
	        case 0:
	            for (int x = -n; x < length-n; x++) {
	                double val = Math.exp(-(double)(x * x)/denom2);
	                kernel[x + n] = (float)(val / denom);
	            }
	            return kernel;
	        case 1:
	            denom *= sigma * sigma;
	            for (int x = -n; x < length-n; x++) {
	                double val = - x * Math.exp(-(double)(x * x)/denom2);
	                kernel[x + n] = (float)(val / denom);
	            }
	            return kernel;
	        case 2:
	            denom *= sigma * sigma * sigma * sigma;
	            for (int x = -n; x < length-n; x++) {
	                double val = - (sigma*sigma - x*x) * Math.exp(-(double)(x * x)/denom2);
	                kernel[x + n] = (float)(val / denom);
	            }
	            return kernel;
	        default:
	            throw new IllegalArgumentException("Order must be <= 2");
	    }
	}


	/**
	 * Get filter coefficients for a 1D Gaussian (derivative) kernel.
	 * 
	 * @param sigma Gaussian sigma
	 * @param order order of the derivative: 0, ('standard' Gaussian filter), 1 (first derivative) or 2 (second derivative)
	 * @param doColumn if true, return coefficients as a column vector rather than a row vector (default)
	 * @return
	 */
	public static Mat getGaussianDerivKernel(double sigma, int order, boolean doColumn) {
	    int n = (int)(sigma * 4);
		int len = n * 2 + 1;
		double[] kernel = getGaussianDeriv(sigma, order, len);
		Mat mat;
		if (doColumn)
			mat = new Mat(1, kernel.length, opencv_core.CV_64F);
		else
			mat = new Mat(kernel.length, 1, opencv_core.CV_64F);
		
		DoubleIndexer indexer = mat.createIndexer();
		indexer.put(0L, kernel);
		indexer.release();
		
		return mat;
	}


	static int ensureInRange(int ind, int max, int border) {
		if (ind < 0) {
			switch(border) {
			case opencv_core.BORDER_REFLECT:
				return ensureInRange(-(ind + 1), max, border);
			case opencv_core.BORDER_REFLECT_101:
				return ensureInRange(-ind, max, border);
			case opencv_core.BORDER_REPLICATE:
			default:
				return 0;
			}
		}
		
		if (ind >= max) {
			switch(border) {
			case opencv_core.BORDER_REFLECT:
				return ensureInRange(2*max - ind - 1, max, border);
			case opencv_core.BORDER_REFLECT_101:
				return ensureInRange(2*max - ind - 2, max, border);
			case opencv_core.BORDER_REPLICATE:
			default:
				return max-1;
			}
		}
		return ind;
	}
	
	
	static void weightedSum(List<Mat> mats, double[] weights, Mat dest) {
		boolean isFirst = true;
		for (int i = 0; i < weights.length; i++) {
			double w = weights[i];
			if (w == 0)
				continue;
			if (isFirst) {
				dest.put(opencv_core.multiply(mats.get(i), w));
				isFirst = false;
			} else
				opencv_core.scaleAdd(mats.get(i), w, dest, dest);
		}
		// TODO: Check this does something sensible!
		if (isFirst) {
			dest.create(mats.get(0).size(), mats.get(0).type());
			dest.put(Scalar.ZERO);
		}
		
//		MatExpr expr = null;
//		for (int i = 0; i < weights.length; i++) {
//			double w = weights[i];
//			if (w == 0)
//				continue;
//			if (expr == null)
//				expr = opencv_core.multiply(mats.get(i), w);
//			else {
//				MatExpr expr2 = opencv_core.add(expr, opencv_core.multiply(mats.get(i), w));
//				expr.close();
//				expr = expr2;
//			}
//		}
//		dest.put(expr);
	}
	
	/**
	 * Apply a filter along the 'list' dimension for a list of Mats, computing the value 
	 * for a single entry. This is effectively computing a weighted sum of images in the list.
	 * @param mats
	 * @param kernel
	 * @param ind3D
	 * @param border
	 * @return
	 */
	public static Mat filterSingleZ(List<Mat> mats, double[] kernel, int ind3D, int border) {
		// Calculate weights for each image
		int n = mats.size();
		int halfSize = kernel.length / 2;
		int startInd = ind3D - halfSize;
		int endInd = startInd + kernel.length;
		double[] weights = new double[mats.size()];
		int k = 0;
		for (int i = startInd; i < endInd; i++) {
			int ind = ensureInRange(i, n, border);
			weights[ind] += kernel[k];
			k++;
		}
		Mat result = new Mat();
		weightedSum(mats, weights, result);
		return result;
	}
	
	
	/**
	 * Filter filter along entries in the input list.
	 * <p>
	 * If each Mat in the list can be considered a consecutive 2D image plane from a z-stack, 
	 * this can be considered filtering along the z-dimension.
	 * 
	 * @param mats
	 * @param kernelZ
	 * @param ind3D if -1, return filtered results for all mats, otherwise only return results for the mat at the specified ind3D
	 * @return
	 */
	public static List<Mat> filterZ(List<Mat> mats, Mat kernelZ, int ind3D, int border) {
		
		/*
		 * We can avoid the rigmarole of applying the full filtering 
		 * by instead simply calculating the weighted sum corresponding to the convolution 
		 * around the z-slice of interest only.
		 */
		
		boolean doWeightedSums = true;//ind3D >= 0;

		if (doWeightedSums) {
			// Extract kernel values
			int ks = (int)kernelZ.total();
			double[] kernelArray = new double[ks];
			DoubleIndexer idx = kernelZ.createIndexer();
			idx.get(0L, kernelArray);
			idx.release();
	
			if (ind3D >= 0) {
				// Calculate weights for each image
				Mat result = filterSingleZ(mats, kernelArray, ind3D, border);
				return Arrays.asList(result);
			} else {
				List<Mat> output = new ArrayList<>();
				for (int i = 0; i < mats.size(); i++) {
					Mat result = filterSingleZ(mats, kernelArray, i, border);
					output.add(result);
				}
				return output;
			}
		}		
		
		// Create a an array of images reshaped as column vectors
		Mat[] columns = new Mat[mats.size()];
		int nRows = 0;
		for (int i = 0; i < mats.size(); i++) {
			Mat mat = mats.get(i);
			nRows = mat.rows();
			columns[i] = mat.reshape(mat.channels(), mat.rows()*mat.cols());
		}
		
		// Concatenate columns, effectively meaning z dimension now along rows
		Mat matConcatZ = new Mat();
		opencv_core.hconcat(new MatVector(columns), matConcatZ);
		
		// Apply z filtering along rows
		if (kernelZ.rows() > 1)
			kernelZ = kernelZ.t().asMat();
		
//		Mat empty = new Mat(1, 1, opencv_core.CV_64FC1, Scalar.ONE);
//		opencv_imgproc.sepFilter2D(matConcatZ, matConcatZ, opencv_core.CV_32F, kernelZ, empty, null, 0.0, border);
		opencv_imgproc.filter2D(matConcatZ, matConcatZ, opencv_core.CV_32F, kernelZ, null, 0.0, border);
		
		int start = 0;
		int end = mats.size();
		if (ind3D >= 0) {
			start = ind3D;
			end = ind3D+1;
		}
		
		// Reshape to create output list
		List<Mat> output = new ArrayList<>();
		for (int i = start; i < end; i++) {
			output.add(matConcatZ.col(i).clone().reshape(matConcatZ.channels(), nRows));
		}
		
		return output;
	}


	/**
	 * Extract a list of Mats, where each Mat corresponds to a z-slice.
	 * 
	 * @param server
	 * @param request
	 * @param zMin first z slice, inclusive
	 * @param zMax last z slice, exclusive
	 * @return
	 * @throws IOException
	 */
	public static List<Mat> extractZStack(ImageServer<BufferedImage> server, RegionRequest request, int zMin, int zMax) throws IOException {
		List<Mat> list = new ArrayList<>();
		for (int z = zMin; z < zMax; z++) {
			RegionRequest request2 = RegionRequest.createInstance(server.getPath(), request.getDownsample(),
					request.getX(), request.getY(), request.getWidth(), request.getHeight(), z, request.getT());
			BufferedImage img = server.readBufferedImage(request2);
			list.add(imageToMat(img));
		}
		return list;
	}


	/**
	 * Extract a list of Mats, where each Mat corresponds to a z-slice, for all available z-slices of a region.
	 * 
	 * @param server
	 * @param request
	 * @return
	 * @throws IOException
	 * @see #extractZStack(ImageServer, RegionRequest, int, int)
	 */
	public static List<Mat> extractZStack(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		return extractZStack(server, request, 0, server.nZSlices());
	}


	/**
	 * Crop a region from a Mat based on its bounding box, returning a new image (not a subregion).
	 * @param mat
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @return
	 */
	public static Mat crop(Mat mat, int x, int y, int width, int height) {
		try (Rect rect = new Rect(x, y, width, height)) {
	    	var temp = mat.apply(rect);
	    	return temp.clone();
		}
	}
	

}
