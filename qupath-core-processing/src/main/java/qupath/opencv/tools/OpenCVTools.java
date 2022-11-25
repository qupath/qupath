/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.descriptive.rank.Percentile.EstimationType;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.indexer.ByteIndexer;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.Index;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
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
import org.bytedeco.opencv.opencv_core.Size;

import qupath.lib.analysis.images.ContourTracing;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

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
				typeCV = opencv_core.CV_8UC(nChannels);
				break;
//			case DataBuffer.TYPE_DOUBLE:
//				typeCV = CV_64FC(nChannels); 
//				mat = new Mat(height, width, typeCV, Scalar.ZERO);
//				break;
			case DataBuffer.TYPE_FLOAT:
				typeCV = opencv_core.CV_32FC(nChannels); 
				break;
			case DataBuffer.TYPE_INT:
				typeCV = opencv_core.CV_32SC(nChannels); // Assuming signed int
				break;
			case DataBuffer.TYPE_SHORT:
				typeCV = opencv_core.CV_16SC(nChannels); 
				break;
			case DataBuffer.TYPE_USHORT:
				typeCV = opencv_core.CV_16UC(nChannels); 
				break;
			default:
				typeCV = opencv_core.CV_64FC(nChannels); // Assume 64-bit is as flexible as we can manage
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
	
	
	/**
	 * Get the OpenCV type code corresponding to a {@link PixelType}.
	 * <p>
	 * Note, OpenCV has no matching type for {@link PixelType#UINT32}. In this case a signed 32-bit int 
	 * type will be returned, and a warning logged.
	 * 
	 * @param pixelType the QuPath pixel type
	 * @return the closest OpenCV pixel type
	 * @throws IllegalArgumentException if the {@link PixelType} is unknown
	 */
	public static int getOpenCVPixelType(PixelType pixelType) throws IllegalArgumentException {
		switch (pixelType) {
		case FLOAT32:
			return opencv_core.CV_32F;
		case FLOAT64:
			return opencv_core.CV_64F;
		case INT16:
			return opencv_core.CV_16S;
		case INT32:
			return opencv_core.CV_32S;
		case INT8:
			return opencv_core.CV_8S;
		case UINT16:
			return opencv_core.CV_16U;
		case UINT32:
			logger.warn("OpenCV does not have a uint32 pixel type! Will returned signed type instead as the closest match.");
			return opencv_core.CV_32S;
		case UINT8:
			return opencv_core.CV_8U;
		default:
			throw new IllegalArgumentException("Unknown pixel type " + pixelType);
		}
	}
	
	
	/**
	 * Apply an operation to the pixels of an image.
	 * <p>
	 * No type conversion is applied; it is recommended to use floating point images, or otherwise check 
	 * that clipping, rounding and non-finite values are handled as expected.
	 * 
	 * @param mat image
	 * @param operator operator to apply to pixels of the image, in-place
	 */
	public static void apply(Mat mat, DoubleUnaryOperator operator) {
		Indexer indexer = mat.createIndexer();
		long[] sizes = indexer.sizes();
		long total = 1;
		for (long dim : sizes)
			total *= dim;
		var indexer2 = indexer.reindex(Index.create(total));
		long[] inds = new long[1];
		for (long i = 0; i < total; i++) {
			inds[0] = i;
			double val = indexer2.getDouble(inds);
			val = operator.applyAsDouble(val);
			indexer2.putDouble(inds, val);
		}
		indexer2.close();
		indexer.close();
	}
	
	
	/**
	 * Create a mask by applying a predicate to pixel values.
	 * @param mat the input image
	 * @param predicate the predicate to apply to each pixel
	 * @param trueValue the value to include in the mask for pixels that match the predicate
	 * @param falseValue the value to include in the mask for pixels that do not match the predicate
	 * @return the mask
	 * @see #createBinaryMask(Mat, DoublePredicate)
	 */
	public static Mat createMask(Mat mat, DoublePredicate predicate, double trueValue, double falseValue) {
		var matMask = mat.clone();
		apply(matMask, d -> predicate.test(d) ? trueValue : falseValue);
		return matMask;
	}
	
	/**
	 * Create a binary mask (0, 255 values) by applying a predicate to pixel values.
	 * @param mat the input image
	 * @param predicate the predicate to apply to each pixel
	 * @return the mask
	 * @see #createMask(Mat, DoublePredicate, double, double)
	 */
	public static Mat createBinaryMask(Mat mat, DoublePredicate predicate) {
		var matMask = createMask(mat, predicate, 255, 0);
		matMask.convertTo(matMask, opencv_core.CV_8U);
		return matMask;
	}
	
	/**
	 * Replace a specific value in an array.
	 * <p>
	 * If the value to replace is NaN, use instead {@link #replaceNaNs(Mat, double)}.
	 * 
	 * @param mat array
	 * @param originalValue value to replace
	 * @param newValue value to include in the output
	 * @see #replaceNaNs(Mat, double)
	 * @see #fill(Mat, Mat, double)
	 */
	public static void replaceValues(Mat mat, double originalValue, double newValue) {
		var mask = opencv_core.equals(mat, originalValue).asMat();
		fill(mat, mask, newValue);
		mask.close();
	}
	
	/**
	 * Replace NaNs in a floating point array.
	 * @param mat array
	 * @param newValue replacement value
	 */
	public static void replaceNaNs(Mat mat, double newValue) {
		int depth = mat.depth();
		if (depth == opencv_core.CV_32F)
			// patchNaNs requires 32-bit input
			opencv_core.patchNaNs(mat, newValue);
		else if (depth == opencv_core.CV_64F || depth == opencv_core.CV_16F) {
			var mask = opencv_core.notEquals(mat, mat).asMat();
			fill(mat, mask, newValue);
			mask.close();
		}
	}

	/**
	 * Fill the pixels of an image with a specific value, corresponding to a mask.
	 * @param mat input image
	 * @param mask binary mask
	 * @param value replacement value
	 * @see #fill(Mat, double)
	 */
	public static void fill(Mat mat, Mat mask, double value) {
		var val = scalarMat(value, opencv_core.CV_64F);
		if (mask == null)
			mat.setTo(val);
		else
			mat.setTo(val, mask);
		val.close();
		return;
	}
	
	/**
	 * Fill the pixels of an image with a specific value.
	 * @param mat input image
	 * @param value fill value
	 * @see #fill(Mat, Mat, double)
	 */
	public static void fill(Mat mat, double value) {
		fill(mat, null, value);
	}

	
	/**
	 * Split channels from a {@link Mat}.
	 * May be more convenient than OpenCV's built-in approach.
	 * 
	 * @param mat
	 * @return a list of {@link Mat}, containing each split channel in order
	 */
	public static List<Mat> splitChannels(Mat mat) {
		var list = new ArrayList<Mat>();
		var channels = mat.channels();
		for (int c = 0; c < channels; c++) {
			var temp = new Mat();
			opencv_core.extractChannel(mat, temp, c);
			list.add(temp);
		}
		return list;
		// This code appeared to give the occasional crazy result (perhaps followed by a segfault?)
//		var matvec = new MatVector();
//		opencv_core.split(mat, matvec);
//		return Arrays.asList(matvec.get());
	}
	
	/**
	 * Merge channels from a multichannel {@link Mat}.
	 * May be more convenient than OpenCV's built-in approach.
	 * 
	 * @param channels separate channels
	 * @param dest optional destination (may be null)
	 * @return merged {@link Mat}, which will be the same as dest if provided
	 * @throws IllegalArgumentException if the number of channels in the output would be greater than {@link opencv_core#CV_CN_MAX}
	 */
	@SuppressWarnings("unchecked")
	public static Mat mergeChannels(Collection<? extends Mat> channels, Mat dest) throws IllegalArgumentException {
		if (dest == null)
			dest = new Mat();
				
		boolean firstMat = true;
		boolean allSingleChannel = true;
		int rows = -1;
		int cols = -1;
		int depth = 0;
		int nChannels = 0;
		for (var m : channels) {
			if (firstMat) {
				rows = m.rows();
				cols = m.cols();
				depth = m.depth();
				if (rows < 0 || cols < 0)
					throw new IllegalArgumentException("Rows and columns must be >= 0");
				firstMat = false;
			}
			int nc = m.channels();
			allSingleChannel = allSingleChannel && nc == 1;
			nChannels += nc;
			if (depth != m.depth() || rows != m.rows() || cols != m.cols())
				throw new IllegalArgumentException("mergeChannels() requires all Mats to have the same dimensions and depth!");
		}

		// We can only use OpenCV's merge if all Mats are single-channel
		boolean mixChannels = !allSingleChannel;		
		if (nChannels > opencv_core.CV_CN_MAX)
			throw new IllegalArgumentException("Can't merge more than " + opencv_core.CV_CN_MAX + " channels (you requested " + nChannels + ")");
		
		try (var scope = new PointerScope()) {
			if (mixChannels) {
				dest.create(rows, cols, opencv_core.CV_MAKETYPE(depth, nChannels));
				var vecSource = new MatVector(channels.toArray(Mat[]::new));
				var vecDest = new MatVector(dest);
				int[] inds = new int[nChannels*2];
				for (int i = 0; i < nChannels; i++) {
					inds[i*2] = i;
					inds[i*2+1] = i;
				}
				opencv_core.mixChannels(vecSource, vecDest, new IntPointer(inds));
			} else {
				opencv_core.merge(new MatVector(channels.toArray(Mat[]::new)), dest);
			}
//			scope.deallocate();
		}

		return dest;
	}
	
	/**
	 * Returns true if a Mat is a floating point (rather than int) type.
	 * @param mat
	 * @return
	 */
	public static boolean isFloat(Mat mat) {
		int depth = mat.depth();
		return depth == opencv_core.CV_16F ||
				depth == opencv_core.CV_32F ||
				depth == opencv_core.CV_64F;
	}
	
	private static double round(double v) {
		if (Double.isFinite(v))
			return Math.round(v);
		return v;
	}

	private static double ceil(double v) {
		if (Double.isFinite(v))
			return Math.ceil(v);
		return v;		
	}

	private static double floor(double v) {
		if (Double.isFinite(v))
			return Math.floor(v);
		return v;		
	}
	
	/**
	 * Floor values in a floating point Mat.
	 * Non-floating point Mats are unchanged.
	 * This resembles {@link Math#floor(double)} except that non-finite values are left unchanged.
	 * @param mat
	 */
	public static void floor(Mat mat) {
		if (!isFloat(mat))
			return;
		apply(mat, OpenCVTools::floor);
	}
	
	/**
	 * Round values in a floating point Mat.
	 * Non-floating point Mats are unchanged.
	 * This resembles {@link Math#round(double)} except that non-finite values are left unchanged.
	 * @param mat
	 */
	public static void round(Mat mat) {
		if (!isFloat(mat))
			return;
		apply(mat, OpenCVTools::round);
	}
	
	/**
	 * Ceil values in a floating point Mat.
	 * Non-floating point Mats are unchanged.
	 * This resembles {@link Math#ceil(double)} except that non-finite values are left unchanged.
	 * @param mat
	 */
	public static void ceil(Mat mat) {
		if (!isFloat(mat))
			return;
		apply(mat, OpenCVTools::ceil);
	}
	
	
	/**
	 * Ensure a {@link Mat} is continuous, creating a copy of the data if necessary.
	 * <p>
	 * This can be necessary before calls to {@link Mat#createBuffer()} or {@link Mat#createIndexer()} for 
	 * simpler interpretation of the results.
	 * 
	 * @param mat input Mat, which may or may not be continuous
	 * @param inPlace if true, set {@code mat} to contain the cloned data if required
	 * @return the original mat unchanged if it is already continuous, or cloned data that is continuous if required
	 * @see Mat#isContinuous()
	 */
	public static Mat ensureContinuous(Mat mat, boolean inPlace) {
		if (!mat.isContinuous()) {
			var mat2 = mat.clone();
			if (!inPlace) {
				return mat2;
			}
			mat.put(mat2);
		}
		assert mat.isContinuous();
		return mat;
	}
	
	
	/**
	 * Vertical concatenation for a {@link Mat}.
	 * May be more convenient than OpenCV's built-in approach.
	 * 
	 * @param mats mats to concatenate
	 * @param dest optional destination (may be null)
	 * @return merged {@link Mat}, which will be the same as dest if provided
	 */
	public static Mat vConcat(Collection<? extends Mat> mats, Mat dest) {
		if (dest == null)
			dest = new Mat();
		opencv_core.vconcat(new MatVector(mats.toArray(Mat[]::new)), dest);
		return dest;
	}
	
	/**
	 * Horizontal concatenation for a {@link Mat}.
	 * May be more convenient than OpenCV's built-in approach.
	 * 
	 * @param mats mats to concatenate
	 * @param dest optional destination (may be null)
	 * @return merged {@link Mat}, which will be the same as dest if provided
	 */
	public static Mat hConcat(Collection<? extends Mat> mats, Mat dest) {
		if (dest == null)
			dest = new Mat();
		opencv_core.hconcat(new MatVector(mats.toArray(Mat[]::new)), dest);
		return dest;
	}
	
	
	/**
	 * Apply a method that modifies a {@link Mat} in-place to all 
	 * channels of the {@link Mat}, merging the result and storing the result in-place.
	 * @param input the (possibly-multichannel) mat
	 * @param fun the consumer to apply
	 */
	public static void applyToChannels(Mat input, Consumer<Mat> fun) {
		if (input.channels() == 1) {
			fun.accept(input);
			return;
		}
		var channels = splitChannels(input);
		for (var c : channels) {
			fun.accept(c);
		}
		mergeChannels(channels, input);
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
			case opencv_core.CV_8U:
				type = DataBuffer.TYPE_BYTE;
				bpp = 8;
				break;
			case opencv_core.CV_8S:
				type = DataBuffer.TYPE_SHORT; // Byte is unsigned
				bpp = 16;
				break;
			case opencv_core.CV_16U:
				type = DataBuffer.TYPE_USHORT;
				bpp = 16;
				break;
			case opencv_core.CV_16S:
				type = DataBuffer.TYPE_SHORT;
				bpp = 16;
				break;
			case opencv_core.CV_32S:
				type = DataBuffer.TYPE_INT;
				bpp = 32;
				break;
			case opencv_core.CV_32F:
				type = DataBuffer.TYPE_FLOAT;
				bpp = 32;
				break;
			default:
				logger.warn("Unknown Mat depth {}, will default to CV64F ({})", mat.depth(), opencv_core.CV_64F);
			case opencv_core.CV_64F:
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
		opencv_core.split(mat, matvector);
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
			mat = new Mat(height, width, opencv_core.CV_8UC4);
		else
			mat = new Mat(height, width, opencv_core.CV_8UC3);

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
	 * @deprecated Use {@link #label(Mat, Mat, int)} instead.
	 */
	@Deprecated
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
	 * Add Gaussian noise with specified mean and standard deviation to all channels of a Mat.
	 * This is similar to {@link opencv_core#randn(Mat, Mat, Mat)}, but supports any number of channels.
	 * @param mat image to which noise should be added
	 * @param mean noise mean
	 * @param stdDev noise standard deviation
	 */
	public static void addNoise(Mat mat, double mean, double stdDev) {
		if (!Double.isFinite(mean) || !Double.isFinite(stdDev)) {
			throw new IllegalArgumentException("Noise mean and standard deviation must be finite (specified " + mean + " and " + stdDev + ")");
		}
		if (stdDev < 0) {
			throw new IllegalArgumentException("Noise standard deviation must be >= 0, but specified value is " + stdDev);
		}
		var matMean = new Mat(1, 1, opencv_core.CV_32FC1, Scalar.all(mean));
		var matStdDev = new Mat(1, 1, opencv_core.CV_32FC1, Scalar.all(stdDev));
		int nChannels = mat.channels();
		if (nChannels == 1)
			opencv_core.randn(mat, matMean, matStdDev);
		else
			OpenCVTools.applyToChannels(mat, m -> opencv_core.randn(m, matMean, matStdDev));
		matMean.close();
		matStdDev.close();
	}
	
	/**
	 * Get the median pixel value in a Mat, ignoring NaNs.
	 * This does not distinguish between channels.
	 * @param mat
	 * @return
	 */
	public static double median(Mat mat) {
		return percentiles(mat, 50.0)[0];
	}
	
	/**
	 * Create a {@link Percentile} instance with default configuration.
	 * <p>
	 * This uses {@link EstimationType#R_7} as the estimation type, 
	 * because this corresponds to the default ('linear') method used 
	 * within NumPy.
	 * <p>
	 * See the <a href="https://numpy.org/doc/stable/reference/generated/numpy.percentile.html">NumPy docs</a>  
	 * and also <a href="https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/stat/descriptive/rank/Percentile.EstimationType.html">
	 * Apache Commons Math</a>.
	 * 
	 * @return
	 */
	static Percentile createPercentile() {
		return new Percentile()
				.withEstimationType(EstimationType.R_7)
				.withNaNStrategy(NaNStrategy.REMOVED);
	}
	
	
	/**
	 * Get percentile values for all pixels in a Mat, ignoring NaNs.
	 * <p>
	 * Note that the behavior of this method was changed for v0.4.0 to
	 * match NumPy's 'linear' method to calculate percentiles 
	 * (which is NumPy's current default at the time of writing).
	 * 
	 * @param mat
	 * @param percentiles requested percentiles (must be between 0 and 100)
	 * @return percentile values, in the same order as the input percentiles
	 * 
	 * @implSpec here, providing 0 or 100 should return the minimum or maximum respectively.
	 * @implNote this was rewritten for improved performance in v0.4.0
	 */
	public static double[] percentiles(Mat mat, double... percentiles) {
		boolean doParallel = OpenCVTools.totalPixels(mat) > 1_000_000 && ThreadTools.getParallelism() > 2;
		// Sorting will almost certainly hurt performance unless parallel & we need multiple percentiles
		boolean doSort = doParallel && percentiles.length > 5;
		return percentilesStream(mat, doParallel, doSort, percentiles);
	}
	
	/**
	 * Legacy percentiles method before v0.4.0 (slower)
	 * @param mat
	 * @param percentiles
	 * @return
	 */
	static double[] percentilesSorted(Mat mat, double... percentiles) {
		double[] result = new double[percentiles.length];
		if (result.length == 0)
			return result;
		
		int n = (int)totalPixels(mat);
		
		var percentile = createPercentile();
				
		// Sort, then strip NaNs
		double[] values = OpenCVTools.extractDoubles(mat);
		Arrays.sort(values);
		while (n >= 0) {
			if (Double.isNaN(values[n-1]))
				n--;
			else
				break;
		}
		if (n < values.length)
			values = Arrays.copyOf(values, n);

		double minValue = Double.NaN;
		boolean minSet = false;
		
		// Set data
		// We can't rely on Percentile to strip NaNs (it appears not to)
		percentile.setData(values);
		for (int i = 0; i < percentiles.length; i++) {
			if (percentiles[i] == 0.0) {
				if (!minSet) {
					minValue = Arrays.stream(values).filter(d -> !Double.isNaN(d)).min().orElse(Double.NaN);
					minSet = true;
				}
				result[i] = minValue;
			} else
				result[i] = percentile.evaluate(percentiles[i]);
		}
		return result;
	}
	
	/**
	 * Stream-based percentile method.
	 * @param mat
	 * @param doParallel
	 * @param doSort
	 * @param percentiles
	 * @return
	 * @since v0.4.0
	 */
	static double[] percentilesStream(Mat mat, boolean doParallel, boolean doSort, double... percentiles) {
		double[] result = new double[percentiles.length];
		if (result.length == 0)
			return result;
		
		var percentile = createPercentile();
				
		// Create a customized stream to filter out NaNs, optionally using sorting & parallelization
		double[] values = OpenCVTools.extractDoubles(mat);
		var stream = Arrays.stream(values);
		if (doParallel)
			stream = stream.parallel();
		if (doSort)
			stream = stream.sorted();
		values = stream.filter(d -> !Double.isNaN(d)).toArray();

		double minValue = Double.NaN;
		boolean minSet = false;
		
		// Set data - NaNs should be stripped beforehand, since they aren't here (despite what the docs might suggest)
		percentile.setData(values);
		for (int i = 0; i < percentiles.length; i++) {
			if (percentiles[i] == 0.0) {
				if (!minSet) {
					minValue = Arrays.stream(values).filter(d -> !Double.isNaN(d)).min().orElse(Double.NaN);
					minSet = true;
				}
				result[i] = minValue;
			} else
				result[i] = percentile.evaluate(percentiles[i]);
		}
		return result;
	}
	
	
	/**
	 * Get the mean of an image, across all pixels (regardless of channels), ignoring NaNs.
	 * @param mat
	 * @return the mean of all pixels in the image
	 */
	public static double mean(Mat mat) {
		return reduceMat(mat, opencv_core.REDUCE_AVG);
		// Alternative implementation does not ignore NaNs
//		if (mat.channels() == 1)
//			return opencv_core.mean(mat).get();
//		var temp = mat.reshape(1, mat.rows()*mat.cols());
//		var mean = opencv_core.mean(temp).get();
//		temp.close();
//		return mean;
	}
	
	/**
	 * Get the mean of an image channel, ignoring NaNs.
	 * @param mat
	 * @return an array of channel means; the length equals mat.channels()
	 */
	public static double[] channelMean(Mat mat) {
		return reduceMat(mat, opencv_core.REDUCE_AVG, true);
	}
	
	/**
	 * Get the standard deviation of an image, across all pixels (regardless of channels), ignoring NaNs.
	 * @param mat
	 * @return the standard deviation of all pixels in the image
	 */
	public static double stdDev(Mat mat) {
		Mat temp;
		if (mat.channels() == 1)
			temp = mat;
		else
			temp = mat.reshape(1, mat.rows()*mat.cols());
		var output = channelStdDev(temp);
		assert output.length == 1;
		return output[0];
	}
	
	/**
	 * Get the standard deviation of image channels, ignoring NaNs.
	 * @param mat
	 * @return an array of channel standard deviation; the length equals mat.channels()
	 * @implNote this uses OpenCV's meanStdDev method, which is not corrected for bias; 
	 *           it provides the square root of the population variance.
	 */
	public static double[] channelStdDev(Mat mat) {
		var mean = new Mat();
		var stdDev = new Mat();
		opencv_core.meanStdDev(mat, mean, stdDev);
		double[] output = extractDoubles(stdDev);
		mean.close();
		stdDev.close();
		return output;
	}
	
	/**
	 * Get the sum of an image, across all pixels (regardless of channels), ignoring NaNs.
	 * @param mat
	 * @return the sum of all pixels in the image
	 */
	public static double sum(Mat mat) {
		return reduceMat(mat, opencv_core.REDUCE_SUM);
		// Alternative implementation doesn't ignore NaNs
//		if (mat.channels() == 1)
//			return opencv_core.sumElems(mat).get();
//		var temp = mat.reshape(1, mat.rows()*mat.cols());
//		var sum = opencv_core.sumElems(temp).get();
//		temp.close();
//		return sum;
	}
	
	/**
	 * Get the sum of image channels, ignoring NaNs.
	 * @param mat
	 * @return an array of channel sums; the length equals mat.channels()
	 */
	public static double[] channelSum(Mat mat) {
		return reduceMat(mat, opencv_core.REDUCE_SUM, true);
	}
	
	/**
	 * Get the minimum value in an image, across all pixels (regardless of channels), ignoring NaNs.
	 * @param mat
	 * @return the minimum of all pixels in the image
	 */
	public static double minimum(Mat mat) {
		return reduceMat(mat, opencv_core.REDUCE_MIN, false)[0];
	}
	
	/**
	 * Get the minimum of an image channel, ignoring NaNs.
	 * @param mat
	 * @return an array of channel minima; the length equals mat.channels()
	 */
	public static double[] channelMinimum(Mat mat) {
		return reduceMat(mat, opencv_core.REDUCE_MIN, true);
	}
	
	/**
	 * Get the maximum value in an image, across all pixels (regardless of channels), ignoring NaNs.
	 * @param mat
	 * @return the maximum of all pixels in the image
	 */
	public static double maximum(Mat mat) {
		return reduceMat(mat, opencv_core.REDUCE_MAX, false)[0];
	}
	
	/**
	 * Get the minimum of an image channel, ignoring NaNs.
	 * @param mat
	 * @return an array of channel minima; the length equals mat.channels()
	 */
	public static double[] channelMaximum(Mat mat) {
		return reduceMat(mat, opencv_core.REDUCE_MAX, true);
	}
	
	private static double[] reduceMat(Mat mat, int reduction, boolean byChannel) {
		if (byChannel && mat.channels() > 1)
			return splitChannels(mat).stream().mapToDouble(m -> reduceMat(m, reduction)).toArray();
		else
			return new double[] {reduceMat(mat, reduction)};
	}
	
	private static double reduceMat(Mat mat, int reduction) {
		double[] values = OpenCVTools.extractDoubles(mat);
//		System.err.println("Total: " + mat.total());
//		System.err.println("Size: " + mat.arraySize()/mat.elemSize());
//		System.err.println("Calculated: " + mat.cols() * mat.rows() * mat.channels());
		
		// If using StatUtils, average and sum have different behavior with NaNs
		switch (reduction) {
		case opencv_core.REDUCE_AVG:
			return Arrays.stream(values).filter(d -> !Double.isNaN(d)).average().orElseGet(() -> Double.NaN);
//			return StatUtils.mean(values);
		case opencv_core.REDUCE_MAX:
			return Arrays.stream(values).filter(d -> !Double.isNaN(d)).max().orElseGet(() -> Double.NaN);
//			return StatUtils.max(values);
		case opencv_core.REDUCE_MIN:
			return Arrays.stream(values).filter(d -> !Double.isNaN(d)).min().orElseGet(() -> Double.NaN);
//			return StatUtils.min(values);
		case opencv_core.REDUCE_SUM:
			return Arrays.stream(values).filter(d -> !Double.isNaN(d)).sum();
//			return StatUtils.sum(values);
			default:
				throw new IllegalArgumentException("Unknown reduction type " + reduction);
		}
	}
	
//	/*
//	 * Method using OpenCV's reduce. The problem with this is that it doesn't ignore NaNs.
//	 */
//	private static double reduceMat(Mat mat, int reduction) {
//		Mat temp = mat.reshape(1, mat.rows()*mat.cols()*mat.channels());
//		var matOutput = new Mat();
//		int depth = temp.depth();
//		// Not all depths are supported by reduce (only know for sure that CV_32S isn't...)
//		if (depth == opencv_core.CV_32S || depth == opencv_core.CV_8U || depth == opencv_core.CV_16S || depth == opencv_core.CV_16U || depth == opencv_core.CV_16F)
//			temp.convertTo(temp, opencv_core.CV_32F);
//		opencv_core.reduce(temp, matOutput, 0, reduction);
//		var output = extractDoubles(matOutput);
//		temp.close();
//		matOutput.close();
//		return output[0];
//	}
	
	/**
	 * Determine the number of channels from a specified Mat type (which also encodes depth).
	 * @param type
	 * @return
	 * @see #typeToDepth(int)
	 */
	public static int typeToChannels(int type) {
		return opencv_core.CV_MAT_CN(type);
	}
	
	/**
	 * Determine the depth from a specified Mat type (which may also encode the number of channels).
	 * @param type
	 * @return
	 * @see #typeToChannels(int)
	 */
	public static int typeToDepth(int type) {
		return opencv_core.CV_MAT_DEPTH(type);
	}
	
	
	/**
	 * Create a 1x1 Mat with a specific value, with 1 or more channels.
	 * If necessary, clipping or rounding is applied.
	 * 
	 * @param value the value to include in the Mat
	 * @param type type of the image; this may contain additional channels if required.
	 * @return a Mat with one pixel containing the closest value supported by the type
	 */
	public static Mat scalarMatWithType(double value, int type) {
		if (opencv_core.CV_MAT_CN(type) <= 4)
			return new Mat(1, 1, type, Scalar.all(value));
		var mat = new Mat(1, 1, type);
		fill(mat, value);
		return mat;
	}
	
	/**
	 * Create a 1x1 single-channel Mat with a specific value.
	 * If necessary, clipping or rounding is applied.
	 * 
	 * @param value the value to include in the Mat
	 * @param depth depth of the image; if a type (including channels) is supplied instead, the channel information is removed.
	 * @return a Mat with one pixel containing the closest value supported by the type
	 */
	public static Mat scalarMat(double value, int depth) {
		return new Mat(1, 1, OpenCVTools.typeToDepth(depth), Scalar.all(value));
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
	 * @deprecated {@link #createDisk(int, boolean)} gives more reliable shapes.
	 */
	@Deprecated
	public static Mat getCircularStructuringElement(int radius) {
		// TODO: Find out why this doesn't just call a standard request for a strel...
		Mat strel = new Mat(radius*2+1, radius*2+1, opencv_core.CV_8UC1, Scalar.ZERO);
		opencv_imgproc.circle(strel, new Point(radius, radius), radius, Scalar.ONE, -1, opencv_imgproc.LINE_8, 0);
		return strel;
	}
	
	// Since generating a disk filter can be quite expensive, cache where we can
	private static Map<Integer, Mat> cachedSumDisks = Collections.synchronizedMap(new HashMap<>());
	private static Map<Integer, Mat> cachedMeanDisks = Collections.synchronizedMap(new HashMap<>());

	
	/**
	 * Create a disk filter.
	 * This is a rasterized approximation of a filled circle with the specified radius.
	 * 
	 * @param radius radius of the disk; must be &gt; 0
	 * @param doMean if true, normalize kernel by dividing by the sum of all elements.
	 *               If false, all 'inside' elements are 1 and all 'outside' elements are 0.
	 * @return a Mat of size {@code radius*2+1} that depicts a filled circle
	 * @implNote this uses a distance transform, and tends to get more predictable results than {@link #getCircularStructuringElement(int)}.
	 *           Internally, expensive computations are reduced by caching previously calculated filters and returning only a clone.
	 */
	public static Mat createDisk(int radius, boolean doMean) {
		if (radius <= 0)
			throw new IllegalArgumentException("Radius must be > 0");
		
		Map<Integer, Mat> cache = doMean ? cachedMeanDisks : cachedSumDisks;
		Mat kernel = cache.get(radius);
		if (kernel != null) {
			synchronized (kernel) {
				// This may happen if the kernel was created in a PointerScope and deallocate() was called
				if (!kernel.isNull())
					return kernel.clone();
			}
		}
		kernel = new Mat();
		
		var kernelCenter = new Mat(radius*2+1, radius*2+1, opencv_core.CV_8UC1, Scalar.WHITE);
		try (UByteIndexer idxKernel = kernelCenter.createIndexer()) {
			idxKernel.put(radius, radius, 0);
		}
		opencv_imgproc.distanceTransform(kernelCenter, kernel, opencv_imgproc.DIST_L2, opencv_imgproc.DIST_MASK_PRECISE);
		opencv_imgproc.threshold(kernel, kernel, radius, 1, opencv_imgproc.THRESH_BINARY_INV);
		if (doMean) {
			// Count nonzero pixels
			double sum = opencv_core.sumElems(kernel).get();
			opencv_core.dividePut(kernel, sum);
		}
		// Try to keep reference, even if called within a PointerScope
		kernel.retainReference();
		cache.put(radius, kernel);
		return kernel.clone();
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
		opencv_core.compare(matBinary, new Mat(1, 1, opencv_core.CV_32FC1, Scalar.ZERO), matDest, opencv_core.CMP_EQ);
	}
	
	
	/**
	 * Extract pixels as a float[] array.
	 * <p>
	 * <p>
	 * In QuPath v0.2 this would return only the pixels in the first channel.
	 * In v0.3+ it should return all pixels.
	 * 
	 * @param mat
	 * @param pixels
	 * @return
	 * @implNote in its current form, this is not very efficient.
	 */
	public static float[] extractPixels(Mat mat, float[] pixels) {
		if (pixels == null)
			pixels = new float[(int)totalPixels(mat)];
		Mat mat2 = null;
		if (mat.depth() != opencv_core.CV_32F) {
			mat2 = new Mat();
			mat.convertTo(mat2, opencv_core.CV_32F);
			ensureContinuous(mat2, true);
		} else
			mat2 = ensureContinuous(mat, false);
		
		FloatIndexer idx = mat2.createIndexer();
		idx.get(0L, pixels);
		idx.release();
		
//		FloatBuffer buffer = mat.createBuffer();
//		buffer.get(pixels);
		if (mat2 != mat)
			mat2.close();
		return pixels;
	}
	
	
	/**
	 * Return the total number of pixels in an image, counting each channel separately.
	 * This is similar to Mat.total(), except that Mat.total() ignores multiple channels.
	 * @param mat
	 * @return
	 */
	static long totalPixels(Mat mat) {
		int nChannels = mat.channels();
		if (nChannels > 0)
			return mat.total() * nChannels;
		return mat.total();
	}
	
	
	/**
	 * Extract pixels as a double array.
	 * @param mat
	 * @param pixels
	 * @return
	 */
	public static double[] extractPixels(Mat mat, double[] pixels) {
		if (pixels == null)
			pixels = new double[(int)totalPixels(mat)];
		Mat mat2 = null;
		if (mat.depth() != opencv_core.CV_64F) {
			mat2 = new Mat();
			mat.convertTo(mat2, opencv_core.CV_64F);
			ensureContinuous(mat2, true);
		} else
			mat2 = ensureContinuous(mat, false);
		
		DoubleIndexer idx = mat2.createIndexer();
		idx.get(0L, pixels);
		idx.release();
		
		if (mat2 != mat)
			mat2.close();
		
//		assert mat.total() == pixels.length;
		return pixels;
	}
	
	/**
	 * Extract pixels as a double array.
	 * @param mat
	 * @return
	 */
	public static double[] extractDoubles(Mat mat) {
		return extractPixels(mat, (double[])null);
	}
	
	/**
	 * Extract pixels as a float array.
	 * @param mat
	 * @return
	 */
	public static float[] extractFloats(Mat mat) {
		return extractPixels(mat, (float[])null);
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
		float[] pixels = extractPixels(temp, (float[])null);
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
		Indexer indexerHierarchy = hierarchy.createIndexer();
		for (int c = 0; c < contours.size(); c++) {
			Mat contour = contours.get(c);
			// Only fill the small, inner contours
			// TODO: Check hierarchy indexing after switch to JavaCPP!!
			if (indexerHierarchy.getDouble(0, ind, 3) >= 0 || opencv_imgproc.contourArea(contour) > maxArea) {
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
		opencv_core.compare(matWatershedIntensities, matTemp, matTemp, opencv_core.CMP_EQ);
		opencv_imgproc.dilate(matTemp, matTemp, getCircularStructuringElement(2));
		Mat matWatershedSeedsBinary = matTemp;
	
		// Remove everything outside the thresholded region
		opencv_core.min(matWatershedSeedsBinary, matBinary, matWatershedSeedsBinary);
	
		// Create labels for watershed
		Mat matLabels = new Mat(matWatershedIntensities.size(), opencv_core.CV_32F, Scalar.ZERO);
		labelImage(matWatershedSeedsBinary, matLabels, opencv_imgproc.RETR_CCOMP);
		
		// Do watershed
		// 8-connectivity is essential for the watershed lines to be preserved - otherwise OpenCV's findContours could not be used
		ProcessingCV.doWatershed(matWatershedIntensities, matLabels, threshold, true);
	
		// Update the binary image to remove the watershed lines
		opencv_core.multiply(matBinary, matLabels, matBinary, 1, matBinary.type());
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
	        mat2.close();
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
	 * @return 
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
	}
	
	
	// Alternative weighted sum code that converts to 32-bit
//	static void weightedSum(List<Mat> mats, double[] weights, Mat dest) {
//		boolean isFirst = true;
//		for (int i = 0; i < weights.length; i++) {
//			double w = weights[i];
//			if (w == 0)
//				continue;
//			var temp = mats.get(i);
//			int type = temp.depth();
//			if (type != opencv_core.CV_32F && type != opencv_core.CV_64F) {
//				var temp2 = new Mat();
//				temp.convertTo(temp2, opencv_core.CV_32F);
//				temp = temp2;
//			}
//			if (isFirst) {
//				dest.put(opencv_core.multiply(temp, w));
//				isFirst = false;
//			} else
//				opencv_core.scaleAdd(temp, w, dest, dest);
//			if (mats.get(i) != temp)
//				temp.release();
//		}
//		// TODO: Check this does something sensible!
//		if (isFirst) {
//			dest.create(mats.get(0).size(), mats.get(0).type());
//			dest.put(Scalar.ZERO);
//		}
//	}
	
	/**
	 * Apply a filter along the 'list' dimension for a list of Mats, computing the value 
	 * for a single entry. This is effectively computing a weighted sum of images in the list.
	 * <p>
	 * Note: this method does not change the depth of the input images.
	 * If a floating point output is needed, the Mats should be converted before input.
	 * 
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
	 * @param border 
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
			BufferedImage img = server.readRegion(request2);
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
	
	/**
	 * Crop a region from a Mat by stripping off padding, returning a new image (not a subregion).
	 * @param mat
	 * @param padding 
	 * @return
	 * @since v0.4.0
	 */
	public static Mat crop(Mat mat, Padding padding) {
		return crop(mat, padding.getX1(), padding.getY1(), mat.cols() - padding.getXSum(), mat.rows() - padding.getYSum());
	}


	/**
	 * Apply a function to a {@link Mat} that strictly requires a specific input size.
	 * The output is expected to have the same size as the input, but may have a different number of channels.
	 * <p>
	 * This method can be used to:
	 * <ul>
	 *   <li>Split larger input into tiles of the required size, apply the function and merge the result</li>
	 *   <li>Pad smaller input into tiles of the required size, apply the function and strip padding from the result</li>
	 * </ul>
	 * If the image dimensions are not an exact multiple of the requested tile sizes, both steps may be required.
	 * <p>
	 * <b>Important!</b> If the output (width &amp; height) of the function is smaller than the input, it will resized
	 * to have the same dimensions and a warning will be logged.
	 * 
	 * @param fun the function to apply to the input
	 * @param mat the input Mat
	 * @param tileWidth the strict tile width required by the input
	 * @param tileHeight the strict tile height required by the input
	 * @param padding padding to apply for <i>internal</i> tiling. Note that if the entire image needs to be padded, this should 
	 *                be done before input.
	 * @param borderType an OpenCV border type, in case padding is needed
	 * @return the result of applying fun to mat, having applied any necessary tiling along the way
	 */
	public static Mat applyTiled(Function<Mat, Mat> fun, Mat mat, int tileWidth, int tileHeight, Padding padding, int borderType) {
		
		int top = 0, bottom = 0, left = 0, right = 0;
	    boolean doPad = false;
		Mat matResult = new Mat();
		
		int width = mat.cols();
		int height = mat.rows();
		
		try (var scope = new PointerScope()) {
			if (mat.cols() > tileWidth) {
				// Handle a row
				var paddingY = Padding.getPadding(0, 0, padding.getY1(), padding.getY2());
				List<Mat> horizontal = new ArrayList<>();
				int xStart = 0;
				boolean lastTile = false;
				while (!lastTile) {
					int xEnd = Math.min(xStart+tileWidth, width);
		    		Mat matTemp = applyTiled(fun, mat.colRange(xStart, xEnd).clone(), tileWidth, tileHeight, 
		    				paddingY,
		    				borderType);
		    		// Crop internal padding from x
		    		int pad1 = xStart == 0 ? 0 : padding.getX1();
		    		int pad2 = 0;
		    		if (xEnd >= width) {
		    			lastTile = true;
		    			pad2 = xEnd - width;
		    		} else
		    			pad2 = padding.getX2();
		    		cropX(matTemp, pad1, pad2);
		    		if (matTemp.cols() == 0)
		    			break;
		    		xStart = xEnd - padding.getX1() - pad2;
		    		horizontal.add(matTemp);
				}
				opencv_core.hconcat(new MatVector(horizontal.toArray(Mat[]::new)), matResult);
				return matResult;
			} else if (mat.rows() > tileHeight) {
				// Handle a column
				var paddingX = Padding.getPadding(padding.getX1(), padding.getX2(), 0, 0);
				List<Mat> vertical = new ArrayList<>();
				int yStart = 0;
				boolean lastTile = false;
				while (!lastTile) {
					int yEnd = Math.min(yStart+tileHeight, height);
		    		Mat matTemp = applyTiled(fun, mat.rowRange(yStart, yEnd).clone(), tileWidth, tileHeight, 
		    				paddingX,
		    				borderType);
		    		// Crop internal padding from y
		    		int pad1 = yStart == 0 ? 0 : padding.getY1();
		    		int pad2 = 0;
		    		if (yEnd >= height) {
		    			lastTile = true;
		    			pad2 = yEnd - height;
		    		} else
		    			pad2 = padding.getY2();
		    		cropY(matTemp, pad1, pad2);
		    		yStart = yEnd - padding.getY1() - pad2;
		    		vertical.add(matTemp);
				}
				opencv_core.vconcat(new MatVector(vertical.toArray(Mat[]::new)), matResult);
				return matResult;
			} else if (mat.cols() < tileWidth || mat.rows() < tileHeight) {
		        // If the image is smaller than we can handle, add padding
				top = (tileHeight - mat.rows()) / 2;
				left = (tileWidth - mat.cols()) / 2;
				bottom = tileHeight - mat.rows() - top;
				right = tileWidth - mat.cols() - left;
				Mat matPadded = new Mat();
				opencv_core.copyMakeBorder(mat, matPadded, top, bottom, left, right, borderType);
				mat = matPadded;
				doPad = true;
			}
			
			// Do the actual requested function
			matResult.put(fun.apply(mat));
						
			// Automatic resizing isn't ideal, but otherwise the padding calculations can't be used
			// (resizing is also handy to support an early StarDist implementation)
			int nRows = mat.rows();
			int nCols = mat.cols();
			if (matResult.rows() != nRows || matResult.cols() != nCols) {
				logger.warn("Resizing tiled image from {}x{} to {}x{}", matResult.cols(), matResult.rows(), nRows, nCols);
				opencv_imgproc.resize(matResult, matResult, mat.size());
			}
			
			// Handle padding
			if (doPad) {
	    		matResult.put(crop(matResult,
	    				left,
	    				top,
	    				matResult.cols() - right - left,
	    				matResult.rows( ) - top - bottom));
		    }
			
		}
	    
	    return matResult;
	}
	
	/**
	 * Crop columns, in-place.
	 * @param mat
	 * @param x1 pixels to remove from the left
	 * @param x2 pixels to remove from the right
	 */
	private static void cropX(Mat mat, int x1, int x2) {
		if (x1 == 0 && x2 == 0)
			return;
		if (x1 < 0 || x2 < 0)
			throw new IllegalArgumentException("Cannot crop x by a negative amount (" + x1 + ", " + x2 + ")");
		int width = mat.cols();
		mat.put(mat.colRange(x1, width-x2));
	}
	
	/**
	 * Crop rows, in-place.
	 * @param mat
	 * @param y1 pixels to remove from the top
	 * @param y2 pixels to remove from the bottom
	 */
	private static void cropY(Mat mat, int y1, int y2) {
		if (y1 == 0 && y2 == 0)
			return;
		if (y1 < 0 || y2 < 0)
			throw new IllegalArgumentException("Cannot crop y by a negative amount (" + y1 + ", " + y2 + ")");
		int height = mat.rows();
		mat.put(mat.rowRange(y1, height-y2));
	}
	
	
	private static int DEFAULT_BORDER_TYPE = opencv_core.BORDER_REFLECT;
	
	private static Mat radiusToStrel(int radius) {
		try (var size = new Size(radius*2+1, radius*2+1)) {
			return opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, size);			
		}
	}
	
	/**
	 * Apply a separable filter to an image, with symmetric boundary padding.
	 * @param mat input image
	 * @param kx horizontal kernel
	 * @param ky vertical kernel
	 */
	public static void sepFilter2D(Mat mat, Mat kx, Mat ky) {
		sepFilter2D(mat, kx, ky, DEFAULT_BORDER_TYPE);
	}

	/**
	 * Apply a separable filter to an image.
	 * @param mat input image
	 * @param kx horizontal kernel
	 * @param ky vertical kernel
	 * @param borderType OpenCV border type for boundary padding
	 */
	public static void sepFilter2D(Mat mat, Mat kx, Mat ky, int borderType) {
		opencv_imgproc.sepFilter2D(mat, mat, -1, kx, ky, null, 0, borderType);
	}
	
	/**
	 * Apply a 2D filter to all channels of an image, with symmetric boundary padding.
	 * @param mat input image
	 * @param kernel filter kernel
	 */
	public static void filter2D(Mat mat, Mat kernel) {
		filter2D(mat, kernel, DEFAULT_BORDER_TYPE);
	}

	/**
	 * Apply a 2D filter to all channels of an image.
	 * @param mat input image
	 * @param kernel filter kernel
	 * @param borderType OpenCV border type for boundary padding
	 */
	public static void filter2D(Mat mat, Mat kernel, int borderType) {
		opencv_imgproc.filter2D(mat, mat, -1, kernel, null, 0, borderType);
	}
	
	/**
	 * Apply a circular 2D mean filter to all channels of an image.
	 * @param mat input image
	 * @param radius filter radius
	 * @param borderType OpenCV border type for boundary padding
	 * @see #createDisk(int, boolean)
	 */
	public static void meanFilter(Mat mat, int radius, int borderType) {
		var kernel = createDisk(radius, true);
		filter2D(mat, kernel, borderType);
	}
	
	/**
	 * Apply a circular 2D mean filter to all channels of an image, with symmetric boundary padding.
	 * @param mat input image
	 * @param radius filter radius
	 * @see #createDisk(int, boolean)
	 */
	public static void meanFilter(Mat mat, int radius) {
		meanFilter(mat, radius, DEFAULT_BORDER_TYPE);
	}
	
	/**
	 * Apply a circular 2D sum filter to all channels of an image.
	 * @param mat input image
	 * @param radius filter radius
	 * @param borderType OpenCV border type for boundary padding
	 * @see #createDisk(int, boolean)
	 */
	public static void sumFilter(Mat mat, int radius, int borderType) {
		var kernel = createDisk(radius, false);
		filter2D(mat, kernel, borderType);
	}
	
	/**
	 * Apply a circular 2D sum filter to all channels of an image, with symmetric boundary padding.
	 * @param mat input image
	 * @param radius filter radius
	 * @see #createDisk(int, boolean)
	 */
	public static void sumFilter(Mat mat, int radius) {
		sumFilter(mat, radius, DEFAULT_BORDER_TYPE);
	}
	
	/**
	 * Apply a circular 2D local variance filter to all channels of an image.
	 * @param mat input image
	 * @param radius filter radius
	 * @param borderType OpenCV border type for boundary padding
	 * @see #createDisk(int, boolean)
	 * @see #stdDevFilter(Mat, int, int)
	 */
	public static void varianceFilter(Mat mat, int radius, int borderType) {
		var kernel = createDisk(radius, true);
		var matSquared = mat.mul(mat).asMat();
		opencv_imgproc.filter2D(matSquared, matSquared, -1, kernel, null, 0, borderType);
		opencv_imgproc.filter2D(mat, mat, -1, kernel, null, 0, borderType);
		mat.put(opencv_core.subtract(matSquared, mat.mul(mat)));
		matSquared.close();
	}
	
	/**
	 * Apply a circular 2D local variance filter to all channels of an image, with symmetric boundary padding.
	 * @param mat input image
	 * @param radius filter radius
	 * @see #createDisk(int, boolean)
	 * @see #stdDevFilter(Mat, int)
	 */
	public static void varianceFilter(Mat mat, int radius) {
		varianceFilter(mat, radius, DEFAULT_BORDER_TYPE);
	}
	
	/**
	 * Apply a circular 2D local standard deviation filter to all channels of an image.
	 * @param mat input image
	 * @param radius filter radius
	 * @param borderType OpenCV border type for boundary padding
	 * @see #createDisk(int, boolean)
	 * @see #varianceFilter(Mat, int, int)
	 */	public static void stdDevFilter(Mat mat, int radius, int borderType) {
		varianceFilter(mat, radius, borderType);
		opencv_core.sqrt(mat, mat);
	}
	
	 /**
	 * Apply a circular 2D local standard deviation filter to all channels of an image, with symmetric boundary padding.
	 * @param mat input image
	 * @param radius filter radius
	 * @see #createDisk(int, boolean)
	 * @see #varianceFilter(Mat, int)
	 */
	 public static void stdDevFilter(Mat mat, int radius) {
		stdDevFilter(mat, radius, DEFAULT_BORDER_TYPE);
	}
	
	/**
	 * Apply a 2D maximum filter (dilation) to all channels of an image.
	 * @param mat input image
	 * @param radius radius of the disk structuring element
	 */ 
	public static void maximumFilter(Mat mat, int radius) {
		var strel = radiusToStrel(radius);
		opencv_imgproc.dilate(mat, mat, strel);
	}

	/**
	 * Apply a 2D minimum filter (erosion) to all channels of an image.
	 * @param mat input image
	 * @param radius radius of the disk structuring element
	 */ 
	public static void minimumFilter(Mat mat, int radius) {
		var strel = radiusToStrel(radius);
		opencv_imgproc.erode(mat, mat, strel);
	}
	
	/**
	 * Apply a 2D closing filter (dilation followed by erosion) to all channels of an image.
	 * @param mat input image
	 * @param radius radius of the disk structuring element
	 */ 
	public static void closingFilter(Mat mat, int radius) {
		var strel = radiusToStrel(radius);
		opencv_imgproc.morphologyEx(mat, mat, opencv_imgproc.MORPH_CLOSE, strel);
	}

	/**
	 * Apply a 2D opening filter (erosion followed by dilation) to all channels of an image.
	 * @param mat input image
	 * @param radius radius of the disk structuring element
	 */ 
	public static void openingFilter(Mat mat, int radius) {
		var strel = radiusToStrel(radius);
		opencv_imgproc.morphologyEx(mat, mat, opencv_imgproc.MORPH_OPEN, strel);		
	}

	/**
	 * Apply a 2D Gaussian filter to all channels of an image.
	 * @param mat input image
	 * @param sigma filter sigma value
	 * @param borderType OpenCV border type for boundary padding
	 */
	public static void gaussianFilter(Mat mat, double sigma, int borderType) {
		int s = (int)Math.ceil(sigma * 4) * 2 + 1;
		try (var size = new Size(s, s)) {
			opencv_imgproc.GaussianBlur(mat, mat, size, sigma, sigma, borderType);
		}
	}
	
	/**
	 * Apply a 2D Gaussian filter to all channels of an image, with symmetric boundary padding.
	 * @param mat input image
	 * @param sigma filter sigma value
	 */
	public static void gaussianFilter(Mat mat, double sigma) {
		gaussianFilter(mat, sigma, DEFAULT_BORDER_TYPE);
	}
	
	/**
	 * Label connected components for non-zero pixels in an image.
	 * @param matBinary binary image to label
	 * @param connectivity either 4 or 8
	 * @return an integer labelled image (CV_32S)
	 */
	public static Mat label(Mat matBinary, int connectivity) {
		var matLabels = new Mat();
		label(matBinary, matLabels, connectivity);
		return matLabels;
	}
	
	/**
	 * Label connected components for non-zero pixels in an image.
	 * @param matBinary binary image to label
	 * @param matLabels labelled image to store the output
	 * @param connectivity either 4 or 8
	 * @return number of connected components, equal to the value of the highest integer label
	 */
	public static int label(Mat matBinary, Mat matLabels, int connectivity) {
		// See https://github.com/opencv/opencv/pull/21275/files
		// Error causing segfault introduced in OpenCV 4.5.5, see https://github.com/opencv/opencv/issues/21366
		// Therefore here we try to revert to the previous default algorithms.
		// TODO: For OpenCV > 4.5.5 can likely revert back to connectedComponents again, since the bug has been fixed
		int alg = connectivity == 8 ? opencv_imgproc.CCL_GRANA : opencv_imgproc.CCL_SAUF;
		return opencv_imgproc.connectedComponentsWithAlgorithm(matBinary, matLabels, connectivity, opencv_core.CV_32S, alg) - 1;
//		return opencv_imgproc.connectedComponents(matBinary, matLabels, connectivity, opencv_core.CV_32S) - 1;
	}
	
	/**
	 * Convert integer labels into ROIs.
	 * 
	 * @param matLabels labelled image; each label should be an integer value
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return an ordered map containing all the ROIs that could be found; corresponding labels are keys in the map
//	 * @see #findROIs(Mat, RegionRequest, int, int)
	 * @see ContourTracing#createROIs(SimpleImage, RegionRequest, int, int)
	 */
	public static Map<Number, ROI> createROIs(Mat matLabels, RegionRequest region, int minLabel, int maxLabel) {
		if (matLabels.channels() != 1)
			throw new IllegalArgumentException("Input to createROIs must be a single-channel Mat - current input has " + matLabels.channels() + " channels");
		var image = matToSimpleImage(matLabels, 0);
		return ContourTracing.createROIs(image, region, minLabel, maxLabel);
	}
	
	/**
	 * Create detection objects by tracing contours in a labelled image.
	 * @param matLabels labelled image
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return detection objects generated by tracing contours
	 * @see ContourTracing#createDetections(SimpleImage, RegionRequest, int, int)
	 */
	public static List<PathObject> createDetections(Mat matLabels, RegionRequest region, int minLabel, int maxLabel) {
		var image = matToSimpleImage(matLabels, 0);
		return ContourTracing.createDetections(image, region, minLabel, maxLabel);
	}
	
	/**
	 * Create annotation objects by tracing contours in a labelled image.
	 * @param matLabels labelled image
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return annotation objects generated by tracing contours
	 * @see ContourTracing#createAnnotations(SimpleImage, RegionRequest, int, int)
	 */
	public static List<PathObject> createAnnotations(Mat matLabels, RegionRequest region, int minLabel, int maxLabel) {
		var image = matToSimpleImage(matLabels, 0);
		return ContourTracing.createAnnotations(image, region, minLabel, maxLabel);
	}
	
	/**
	 * Create objects by tracing contours in a labelled image.
	 * @param matLabels labelled image
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @param creator function used to generate objects from ROIs
	 * @return objects generated by tracing contours
	 * @see ContourTracing#createObjects(SimpleImage, RegionRequest, int, int, BiFunction)
	 */
	public static List<PathObject> createObjects(Mat matLabels, RegionRequest region, int minLabel, int maxLabel, BiFunction<ROI, Number, PathObject> creator) {
		var image = matToSimpleImage(matLabels, 0);
		return ContourTracing.createObjects(image, region, minLabel, maxLabel, creator);
	}
	
	/**
	 * Create cell objects by tracing contours in a labelled image.
	 * @param matLabelsNuclei labelled image for the cell nuclei
	 * @param matLabelsCells labelled image for the full cell; labels must correspond to those in matLabelsNuclei
	 * @param region region used to convert coordinates into the full image space (optional)
	 * @param minLabel minimum label; usually 1, but may be 0 if a background ROI should be created
	 * @param maxLabel maximum label; if less than minLabel, the maximum label will be found in the image and used
	 * @return cell objects generated by tracing contours
	 * @see ContourTracing#createCells(SimpleImage, SimpleImage, RegionRequest, int, int)
	 */
	public static List<PathObject> createCells(Mat matLabelsNuclei, Mat matLabelsCells, RegionRequest region, int minLabel, int maxLabel) {
		var imageNuclei = matToSimpleImage(matLabelsNuclei, 0);
		var imageCells = matToSimpleImage(matLabelsCells, 0);
		return ContourTracing.createCells(imageNuclei, imageCells, region, minLabel, maxLabel);
	}
	

	
	private static MaxLabelStat[] updateMaxLabelStats(Mat mat, Mat matLabels, int nLabels, int connectivity) {
		
		int n = nLabels;
		var stats = new MaxLabelStat[n+1];
		for (int i = 0; i <= n; i++)
			stats[i] = new MaxLabelStat(i);
		
		int h = mat.rows();
		int w = mat.cols();
		long[] inds = new long[3];
		
		try (var idx = mat.createIndexer()) {
			try (IntIndexer idxLabels = matLabels.createIndexer()) {
				
				for (int y = 1; y < h-1; y++) {
					for (int x = 1; x < w-1; x++) {
						// Get the current pixel & label
						int lab = idxLabels.get(y, x);
						double val = getValue(idx, y, x, 0, inds);
						
						// Update inside stats
//						if (lab > 0 && lab <= n) {
							stats[lab].inside(val);
//						}
						
						// Update neigbors as well for boundary stats
						for (int y2 = y-1; y2 <= y+1; y2++) {
							for (int x2 = x-1; x2 <= x+1; x2++) {
								if (x == x2 && y == y2)
									continue;
								if (connectivity == 4 && (x != x2 && y != y2))
									continue;
								int lab2 = idxLabels.get(y2, x2);
//								double val2 = getValue(idx, y2, x2, 0, inds);
								// Record if we're on a boundary
								if (lab2 != lab)
									stats[lab2].boundary(val);
							}
						}
					}
				}
			}
		}
		return stats;
	}
	
	
	/**
	 * Get the regional maxima within a Mat, providing the output as a labeled image.
	 * @param mat image containing maxima; must be 2D and single-channel
	 * @return
	 * @implNote the labels are not guaranteed to be consecutive; the behavior of this function may change in a future release.
	 */
	public static Mat findRegionalMaxima(Mat mat) {
		
		checkSingleChannel(mat);
		
		int connectivity = 8;
		
		// Apply 3x3 maximum filter & check equality
		var matMax = new Mat();
		var kernel = Mat.ones(3, 3, opencv_core.CV_32FC1).asMat();
		opencv_imgproc.dilate(mat, matMax, kernel);
		kernel.close();
		
		matMax.put(opencv_core.equals(mat, matMax));
		
		// Find connected components
		var matLabels = label(matMax, connectivity);

		// Calculate stats for labels
		int nLabels = (int)Math.ceil(maximum(matLabels));
		MaxLabelStat[] stats = updateMaxLabelStats(mat, matLabels, nLabels, connectivity);
		
		// If boundaries labels are >= inner labels, they can't correspond to regional max - so remove them
		var nonMaxima = Arrays.stream(stats).filter(s -> s.maxBoundary >= s.maxInside).map(s -> s.label).collect(Collectors.toSet());
		if (!nonMaxima.isEmpty()) {
			// TODO: Make this more efficient...
			for (var nonMax : nonMaxima)
				replaceValues(matLabels, nonMax, 0);
		}
		
		return matLabels;
	}
	
	/**
	 * Get a brief, one-line report on current physical memory use, based on JavaCPP's {@link Pointer} class.
	 * This is suitable for logging if required.
	 * @return
	 * @see #trackedMemory()
	 * @see #memoryReport(CharSequence)
	 */
	public static String physicalMemory() {
		long physicalBytes = Pointer.availablePhysicalBytes();
		
		double physicalMB = physicalBytes / (1024.0 * 1024.0);
		double physicalPercent = physicalBytes / ((double)Pointer.maxPhysicalBytes()) * 100.0;
		
		return "Physical memory: " +
			GeneralTools.formatNumber(physicalMB, 1) + 
			" MB (" + 
			GeneralTools.formatNumber(physicalPercent, 1) + 
			" %)";
		
	}

	/**
	 * Get a brief, one-line report on tracked memory use, based on JavaCPP's {@link Pointer} class.
	 * This is suitable for logging if required.
	 * @return
	 * @see #physicalMemory()
	 * @see #memoryReport(CharSequence)
	 */
	public static String trackedMemory() {
		double trackedMB = Pointer.totalBytes() / (1024.0 * 1024.0);
		long nPointers = Pointer.totalCount();
		return "Tracked memory: " +
			GeneralTools.formatNumber(trackedMB, 1) + 
			" MB (" + 
			nPointers + 
			" pointers)";
	}

	/**
	 * Create a brief report on memory use, based on JavaCPP's {@link Pointer} class.
	 * @param delimiter delimiter to use between elements of the report, e.g. {@code ", "} or {@code "\n"}
	 * @return
	 * @implNote Currently, this concatenates {@link #physicalMemory()} and {@link #trackedMemory()}.
	 * @see #trackedMemory()
	 * @see #physicalMemory()
	 */
	public static String memoryReport(CharSequence delimiter) {
		return String.join(delimiter, physicalMemory(), trackedMemory());
	}
	
	
	
	/**
	 * Find maxima within an image.
	 * @param mat image containing maxima; must be 2D and single-channel
	 * @param mask optional mask
	 * @return
	 */
	public static Collection<IndexedPixel> findMaxima(Mat mat, Mat mask) {
		
		var matMax = findRegionalMaxima(mat);
		var points = labelsToPoints(matMax);
		
		// Update points to have the value of the maximum
		try (var idx = mat.createIndexer()) {
			for (var p : points) {
				p.value = idx.getDouble(p.inds);
			}
		}
		
		// Sort in descending order of value
		Collections.sort(points, Comparator.comparingDouble((IndexedPixel p) -> p.getValue()).reversed());
		
		// Mask if we have to
		if (mask == null)
			return points;
		try (var idx = mask.createIndexer()) {
			return points.stream().filter(p -> idx.getDouble(p.inds) != 0).collect(Collectors.toList());
		}
		
	}
	
	
	private static void checkSingleChannel(Mat mat) {
		if (mat.channels() != 1)
			throw new IllegalArgumentException("Method requires a single-channel mat, but input has " + mat.channels() + " channels");
	}
	
	/**
	 * Extract all the masked pixels within an image.
	 * @param mat input image; must be single-channel
	 * @param mask mask of the same size as mat
	 * @return list of pixels, including their indices and values
	 */
	public static List<IndexedPixel> getMaskedPixels(Mat mat, Mat mask) {
		checkSingleChannel(mat);
		checkSingleChannel(mask);
		
		var pixels = new ArrayList<IndexedPixel>();

		int height = mat.rows();
		int width = mat.cols();
		
		long[] inds = new long[2];
		try (var idx = mat.createIndexer()) {
			try (var idxMask = mask.createIndexer()) {
				for (int y = 0; y < height; y++) {
					inds[0] = y;
					for (int x = 0; x < width; x++) {
						inds[1] = x;
						if (idxMask.getDouble(inds) != 0)
							pixels.add(new IndexedPixel(x, y, idx.getDouble(inds)));
					}					
				}
			}
		}
		return pixels;
	}
	
	
	/**
	 * Class representing the indices of a pixel and its value.
	 * This has considerable overhead, so should be used sparingly.
	 */
	public static class IndexedPixel {
		
		private long[] inds;
		private double value;
		
		IndexedPixel(long[] inds, double value) {
			this.inds = inds.clone();
			this.value = value;
		}
		
		IndexedPixel(int x, int y, double value) {
			this.inds = new long[]{y, x};
			this.value = value;
		}
		
		/**
		 * Get x index.
		 * @return
		 */
		public int getX() {
			return (int)inds[1];
		}
		
		/**
		 * Get y index.
		 * @return
		 */
		public int getY() {
			return (int)inds[0];
		}
		
		/**
		 * Get channel index.
		 * @return
		 */
		public int getC() {
			return inds.length < 3 ? 0 : (int)inds[2];
		}
		
		/**
		 * Get the index array. Note that this returns a clone of the array.
		 * @return
		 * @see #getValue(Indexer)
		 */
		public long[] getInds() {
			return inds.clone();
		}
		
		/**
		 * Get the value stored internally for this pixel.
		 * @return
		 */
		public double getValue() {
			return value;
		}
		
		/**
		 * Get the value from another image via its {@link Indexer}.
		 * This is equivalent to {@code Indexer.getDouble(IndexedPixel.getInds())} but avoids copying the inds array.
		 * @param idx
		 * @return
		 */
		public double getValue(Indexer idx) {
			return idx.getDouble(inds);
		}
		
		/**
		 * Get the euclidean distance to another pixel, based upon the inds array and assuming unit spacing for all dimensions.
		 * @param p2
		 * @return
		 */
		public long distanceSq(IndexedPixel p2) {
			if (inds.length != p2.inds.length)
				throw new IllegalArgumentException("Cannot compute distance between points with different numbers of indices!");
			long sumSq = 0L;
			for (int i = 0; i < inds.length; i++) {
				long d = inds[i] - p2.inds[i];
				sumSq += d*d;
			}
			return sumSq;
		}
		
	}
	
	
	/**
	 * Shrink labels to a single point.
	 * This works by effectively iterating through each label, and retaining only the labeled pixel that is closest to the centroid 
	 * of all pixels with the same label - setting all other pixels within the component to zero.
	 * 
	 * @param mat label mat (must be CV_32S)
	 * @return the labeled image, with only one pixel per label greater than zero
	 */
	public static Mat shrinkLabels(Mat mat) {

		if (mat.channels() != 1)
			throw new IllegalArgumentException("shrinkLabels requires a single-channel mat, but input has " + mat.channels() + " channels");

		var points = labelsToPoints(mat);

		var mat2 = new Mat(mat.rows(), mat.cols(), mat.type(), Scalar.ZERO);
		try (IntIndexer idx2 = mat2.createIndexer()) {
			for (var p : points) {
				idx2.putDouble(p.inds, p.getValue());
			}
		}
		
		return mat2;
	}
	
	
	private static List<IndexedPixel> labelsToPoints(Mat mat) {

		if (mat.channels() != 1)
			throw new IllegalArgumentException("labelsToPoints requires a single-channel mat, but input has " + mat.channels() + " channels");

		int w = mat.cols();
		int h = mat.rows();

		var pixels = new ArrayList<IndexedPixel>();
		try (IntIndexer idx = mat.createIndexer()) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int lab = idx.get(y, x);
					if (lab != 0) {
						pixels.add(new IndexedPixel(x, y, lab));
					}
				}				
			}
		}
		
		// Group by label
		var groups = pixels.stream().collect(Collectors.groupingBy(p -> p.value));
		
		return groups.values().stream().map(l-> closestToCentroid(l)).collect(Collectors.toList());
	}
	
	/**
	 * Get the closest pixel to the centroid of all pixels.
	 * @param pixels
	 * @return
	 */
	private static IndexedPixel closestToCentroid(Collection<IndexedPixel> pixels) {
		// If only 2 pixels, can take either
		if (pixels.size() <= 2)
			return pixels.iterator().next();
		
		double cx = pixels.stream().mapToDouble(p -> p.getX()).average().getAsDouble();
		double cy = pixels.stream().mapToDouble(p -> p.getY()).average().getAsDouble();
		
		IndexedPixel closest = null;
		double minDistanceSq = Double.POSITIVE_INFINITY;
		for (var p : pixels) {
			double dx = cx - p.getX();
			double dy = cy - p.getY();
			double dist = dx*dx + dy*dy;
			if (dist < minDistanceSq) {
				minDistanceSq = dist;
				closest = p;
			}
		}
		return closest;
	}
	
	
	/**
	 * For a labeled image, store the min/max intensity values within the labeled region 
	 * and one pixel outside its boundary. This can be used to determine if the region is a local maximum.
	 */
	private static class MaxLabelStat {
		
		int label;
		double minInside = Double.POSITIVE_INFINITY;
		double maxInside = Double.NEGATIVE_INFINITY;
		
		double minBoundary = Double.POSITIVE_INFINITY;
		double maxBoundary = Double.NEGATIVE_INFINITY;
		
		MaxLabelStat(int label) {
			this.label = label;
		}
		
		public void inside(double value) {
			if (value < minInside)
				minInside = value;
			if (value > maxInside)
				maxInside = value;
		}
		
		public void boundary(double value) {
			if (value < minBoundary)
				minBoundary = value;
			if (value > maxBoundary)
				maxBoundary = value;
		}
		
		
	}
	
	
	private static double getValue(Indexer idx, long i, long j, long k, long[] inds) {
		inds[0] = i;
		inds[1] = j;
		inds[2] = k;
		return idx.getDouble(inds);
	}
	
	
}
