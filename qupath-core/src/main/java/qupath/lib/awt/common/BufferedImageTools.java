/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.awt.common;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.util.Hashtable;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Static methods for working with BufferedImages and QuPath objects.
 * 
 * @author Pete Bankhead
 *
 */
public final class BufferedImageTools {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractTileableImageServer.class);
	
	// Suppress default constructor for non-instantiability
	private BufferedImageTools() {
		throw new AssertionError();
	}
	
	/**
	 * Create a grayscale BufferedImage representing a mask for a specified ROI.
	 * <p>
	 * Pixels inside the ROI will be 255, pixels outside will be 0.
	 * 
	 * @param width width of the requested mask image
	 * @param height height of the requested mask image
	 * @param roi ROI for mask
	 * @param request region that the mask should correspond to, including the origin (x &amp; y) and downsample factor to use.
	 * @return
	 */
	public static BufferedImage createROIMask(final int width, final int height, final ROI roi, final RegionRequest request) {
		return createROIMask(width, height, roi, request.getX(), request.getY(), request.getDownsample());
	}
	
	/**
	 * Create a ROI mask using the minimal bounding box for the ROI.
	 * @param roi
	 * @param downsample
	 * @return
	 */
	public static BufferedImage createROIMask(final ROI roi, final double downsample) {
		int width = (int)Math.ceil(roi.getBoundsWidth() / downsample);
		int height = (int)Math.ceil(roi.getBoundsHeight() / downsample);
		return createROIMask(width, height, roi, roi.getBoundsX(), roi.getBoundsY(), downsample);
	}

	/**
	 * Create a grayscale BufferedImage representing a mask for a specified ROI.
	 * <p>
	 * Pixels inside the ROI will be 255, pixels outside will be 0.
	 * 
	 * @param width width of the requested mask image
	 * @param height height of the requested mask image
	 * @param roi ROI for mask
	 * @param xOrigin pixel x coordinate of the top left of the region to include in the mask.
	 * @param yOrigin pixel y coordinate of the top left of the region to include in the mask.
	 * @param downsample downsample factor to use when generating the mask, i.e. the amount to scale.
	 * @return
	 */
	public static BufferedImage createROIMask(final int width, final int height, final ROI roi, final double xOrigin, final double yOrigin, final double downsample) {
		Shape shape = RoiTools.getShape(roi);
		return createShapeMask(width, height, shape, xOrigin, yOrigin, downsample);
	}
	
	
	/**
	 * Create a Shape mask using the minimal bounding box for the Shape.
	 * @param shape
	 * @param downsample
	 * @return
	 */
	public static BufferedImage createROIMask(final Shape shape, final double downsample) {
		var bounds = shape.getBounds2D();
		int width = (int)Math.ceil(bounds.getWidth() / downsample);
		int height = (int)Math.ceil(bounds.getHeight() / downsample);
		return createShapeMask(width, height, shape, bounds.getX(), bounds.getY(), downsample);
	}
	
	/**
	 * Create a grayscale BufferedImage representing a mask for a specified ROI.
	 * <p>
	 * Pixels inside the ROI will be 255, pixels outside will be 0.
	 * 
	 * @param width width of the requested mask image
	 * @param height height of the requested mask image
	 * @param shape Shape for mask
	 * @param xOrigin pixel x coordinate of the top left of the region to include in the mask.
	 * @param yOrigin pixel y coordinate of the top left of the region to include in the mask.
	 * @param downsample downsample factor to use when generating the mask, i.e. the amount to scale.
	 * @return
	 */
	public static BufferedImage createShapeMask(final int width, final int height, final Shape shape, final double xOrigin, final double yOrigin, final double downsample) {
		BufferedImage imgMask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g2d = imgMask.createGraphics();
		g2d.scale(1.0/downsample, 1.0/downsample);
		g2d.translate(-xOrigin, -yOrigin);
		g2d.setColor(Color.WHITE);
		g2d.fill(shape);
//		g2d.draw(shape);
		g2d.dispose();
		return imgMask;
	}

	/**
	 * Convert a BufferedImage to have a specified type.
	 * <p>
	 * This makes use of {@code Graphics2D.drawImage}, which imposes limits on supported types 
	 * (i.e. RGB or 8-bit grayscale), and is therefore <b>not</b> suitable for arbitrary type conversion.
	 * <p>
	 * A possible use is to ensure that a BGR/RGB/ARGB image is converted to the same representation, 
	 * for example to allow packed int arrays to be treated in the same way.
	 * <p>
	 * Images that already have the same type are returned unchanged.
	 * 
	 * @param img the input image
	 * @param requestedType the type to which the image should be converted
	 * @return the (possibly-new) output image
	 * @see #is8bitColorType(int)
	 */
	public static BufferedImage ensureBufferedImageType(final BufferedImage img, int requestedType) {
		if (img.getType() != requestedType) {
			BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), requestedType);
			Graphics2D g2d = img2.createGraphics();
			g2d.drawImage(img, 0, 0, null);
			g2d.dispose();
			return img2;
		}
		return img;
	}
	
	/**
	 * Convert an {@link Image} to a {@link BufferedImage} if necessary, or return the original image unchanged 
	 * if it is already a {@link BufferedImage}.
	 * @param image the image to (possible convert)
	 * @return a {@link BufferedImage}
	 */
	public static BufferedImage ensureBufferedImage(Image image) {
		if (image instanceof BufferedImage)
			return (BufferedImage)image;
		var imgBuf = new BufferedImage(image.getWidth(null), image.getWidth(null), BufferedImage.TYPE_INT_ARGB);
		var g2d = imgBuf.createGraphics();
		g2d.drawImage(image, 0, 0, null);
		g2d.dispose();
		return imgBuf;
	}
	
	/**
	 * Duplicate a BufferedImage. This retains the same color model, but copies the raster.
	 * Properties are copied if non-null.
	 * @param img
	 * @return
	 */
	public static BufferedImage duplicate(BufferedImage img) {
		return new BufferedImage(
				img.getColorModel(),
				img.copyData(img.getRaster().createCompatibleWritableRaster()),
				img.isAlphaPremultiplied(),
				extractProperties(img));
	}
	
	/**
	 * Extract a Hashtable of image properties, which can be passed to a constructor for BufferedImage.
	 * @param img
	 * @return
	 */
	static Hashtable<Object, Object> extractProperties(BufferedImage img) {
		String[] names = img.getPropertyNames();
		if (names == null)
			return null;
		Hashtable<Object, Object> properties = null;
		if (names != null && names.length > 0) {
			properties = new Hashtable<>();
			for (String name : names)
				properties.put(name, img.getProperty(name));
		}
		return properties;
	}
	
	/**
	 * Set all values in a DataBuffer.
	 * @param buffer the buffer containing the banks whose values should be set.
	 * @param val the requested value. This will be cast to an int or float if necessary.
	 */
	public static void setValues(DataBuffer buffer, double val) {
		int n = buffer.getSize();
		int nBanks = buffer.getNumBanks();
		
		switch (buffer.getDataType()) {
		case DataBuffer.TYPE_BYTE:
		case DataBuffer.TYPE_INT:
		case DataBuffer.TYPE_USHORT:
		case DataBuffer.TYPE_SHORT:
			int ival = (int)val;
			for (int b = 0; b < nBanks; b++) {
				for (int i = 0; i < n; i++)
					buffer.setElem(b, i, ival);
			}
			break;
		case DataBuffer.TYPE_FLOAT:
			float fval = (float)val;
			for (int b = 0; b < nBanks; b++) {
				for (int i = 0; i < n; i++)
					buffer.setElemFloat(b, i, fval);
			}
			break;
		case DataBuffer.TYPE_DOUBLE:
			for (int b = 0; b < nBanks; b++) {
				for (int i = 0; i < n; i++)
					buffer.setElemDouble(b, i, val);
			}
			break;
		default:
			throw new IllegalArgumentException("Unable to set values for unknown data buffer type " + buffer.getDataType());
		}
	}
	
	
	/**
	 * Returns true if a BufferedImage type represents an 8-bit color image.
	 * The precise representation (BGR, RGB, byte, int, with/without alpha) is not important.
	 * @param type
	 * @return 
	 * @see #ensureBufferedImageType(BufferedImage, int)
	 */
	public static boolean is8bitColorType(int type) {
		switch(type) {
		case BufferedImage.TYPE_3BYTE_BGR:
		case BufferedImage.TYPE_4BYTE_ABGR:
		case BufferedImage.TYPE_4BYTE_ABGR_PRE:
		case BufferedImage.TYPE_INT_ARGB:
		case BufferedImage.TYPE_INT_ARGB_PRE:
		case BufferedImage.TYPE_INT_BGR:
		case BufferedImage.TYPE_INT_RGB:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Resize the image to have the requested width/height, using area averaging and bilinear interpolation.
	 * 
	 * @param img input image to be resized
	 * @param finalWidth target output width
	 * @param finalHeight target output height
	 * @param smoothInterpolate if true, the resize method is permitted to use a smooth interpolation method. If false, nearest-neighbor interpolation is used.
	 * @return resized image
	 */
	public static BufferedImage resize(final BufferedImage img, final int finalWidth, final int finalHeight, boolean smoothInterpolate) {

		if (img.getWidth() == finalWidth && img.getHeight() == finalHeight)
			return img;

		logger.trace(String.format("Resizing %d x %d -> %d x %d", img.getWidth(), img.getHeight(), finalWidth, finalHeight));

		double aspectRatio = (double)img.getWidth()/img.getHeight();
		double finalAspectRatio = (double)finalWidth/finalHeight;
		if (!GeneralTools.almostTheSame(aspectRatio, finalAspectRatio, 0.01)) {
			if (!GeneralTools.almostTheSame(aspectRatio, finalAspectRatio, 0.05))
				logger.debug("Substantial difference in aspect ratio for resized image: {}x{} -> {}x{} ({}, {})", img.getWidth(), img.getHeight(), finalWidth, finalHeight, aspectRatio, finalAspectRatio);
			else
				logger.trace("Slight difference in aspect ratio for resized image: {}x{} -> {}x{} ({}, {})", img.getWidth(), img.getHeight(), finalWidth, finalHeight, aspectRatio, finalAspectRatio);
		}

		WritableRaster raster = img.getRaster();
		WritableRaster raster2 = raster.createCompatibleWritableRaster(finalWidth, finalHeight);

		int w = img.getWidth();
		int h = img.getHeight();
		
		Mat matInput = new Mat(h, w, opencv_core.CV_32FC1);
		Size sizeOutput = new Size(finalWidth, finalHeight);
		Mat matOutput = new Mat(sizeOutput, opencv_core.CV_32FC1);
		FloatIndexer idxInput = matInput.createIndexer(true);
		FloatIndexer idxOutput = matOutput.createIndexer(true);
		float[] pixels = new float[w*h];
		float[] pixelsOut = new float[finalWidth*finalHeight];
		
		int interp = smoothInterpolate ? opencv_imgproc.INTER_AREA : opencv_imgproc.INTER_NEAREST;
		for (int b = 0; b < raster.getNumBands(); b++) {
			raster.getSamples(0, 0, w, h, b, pixels);
			idxInput.put(0L, pixels);
			opencv_imgproc.resize(matInput, matOutput, sizeOutput, 0, 0, interp);
			idxOutput.get(0, pixelsOut);
			raster2.setSamples(0, 0, finalWidth, finalHeight, b, pixelsOut);
		}
		
		idxInput.release();
		idxOutput.release();
		matInput.close();
		matOutput.close();
		sizeOutput.close();
		
//		System.err.println(String.format("Resizing from %d x %d to %d x %d", w, h, finalWidth, finalHeight));

		return new BufferedImage(img.getColorModel(), raster2, img.isAlphaPremultiplied(), null);
	}

	/**
	 * Compute the full histogram for a raster containing 8-bit or 16-bit unsigned integer values.
	 * @param raster the raster containing the data for the histogram; if not TYPE_BYTE or TYPE_USHORT an {@link IllegalArgumentException} will be thrown
	 * @param counts histogram counts; if null, a new array will be created. Its must be sufficient for the data type, i.e. 256 or 65536.
	 * 				 No size checking is performed, therefore if non-null it must be sufficiently large for the data type.
	 * @param rasterMask optional single-channel mask; if not null, corresponding pixels with 0 values in the mask will be skipped
	 * @return
	 */
	public static long[] computeUnsignedIntHistogram(WritableRaster raster, long[] counts, WritableRaster rasterMask) {
		if (counts == null) {
			if (raster.getTransferType() == DataBuffer.TYPE_BYTE)
				counts = new long[256];
			else if (raster.getTransferType() == DataBuffer.TYPE_USHORT)
				counts = new long[65536];
			else
				throw new IllegalArgumentException("TransferType must be DataBuffer.TYPE_BYTE or DataBuffer.TYPE_USHORT!");
		}
		int h = raster.getHeight();
		int w = raster.getWidth();
		int nBands = raster.getNumBands();
		for (int b = 0; b < nBands; b++) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (rasterMask != null && rasterMask.getSample(x, y, 0) == 0)
						continue;
					int ind = raster.getSample(x, y, b);
					counts[ind]++;
				}
			}
		}
		return counts;
	}

	/**
	 * Create a histogram that identifies the channels (bands) of an image with the maximum values according to the argmax criterion.
	 * 
	 * @param raster the multi-band raster containing values to check
	 * @param counts existing histogram if it should be updated, or null if a new histogram should be created. The length should 
	 * 				 match the number of bands in the raster.
	 * @param rasterMask optional single-channel mask; if not null, corresponding pixels with 0 values in the mask will be skipped
	 * @return
	 */
	public static long[] computeArgMaxHistogram(WritableRaster raster, long[] counts, WritableRaster rasterMask) {
		if (counts == null) {
			counts = new long[raster.getNumBands()];
		}
		int h = raster.getHeight();
		int w = raster.getWidth();
		int nBands = raster.getNumBands();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (rasterMask != null && rasterMask.getSample(x, y, 0) == 0)
					continue;
				double maxValue = raster.getSampleDouble(x, y, 0);
				int ind = 0;
				for (int i = 1; i < nBands; i++) {
					double val = raster.getSampleDouble(x, y, i);
					if (val > maxValue) {
						maxValue = val;
						ind = i;
					}
				}
				counts[ind]++;
			}
		}
		return counts;
	}

	/**
	 * Count the number of above-threshold pixels in a specified band of a raster, with optional mask.
	 * 
	 * @param raster the multi-band raster containing values to check
	 * @param band the band (channel) to consider
	 * @param threshold threshold value; pixels with values &gt; threshold this will be counted
	 * @param rasterMask optional single-channel mask; if not null, corresponding pixels with 0 values in the mask will be skipped
	 * @return
	 */
	public static long computeAboveThresholdCounts(WritableRaster raster, int band, double threshold, WritableRaster rasterMask) {
		long count = 0L;
		int h = raster.getHeight();
		int w = raster.getWidth();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (rasterMask != null && rasterMask.getSample(x, y, 0) == 0)
					continue;
				double val = raster.getSampleDouble(x, y, band);
				if (val > threshold)
					count++;
			}
		}
		return count;
	}

//	/**
//	 * Resize the image to have the requested width/height, using area averaging and bilinear interpolation.
//	 * 
//	 * @param img input image to be resized
//	 * @param finalWidth target output width
//	 * @param finalHeight target output height
//	 * @return resized image
//	 */
//	public static BufferedImage resize(final BufferedImage img, final int finalWidth, final int finalHeight) {
//
//		if (img.getWidth() == finalWidth && img.getHeight() == finalHeight)
//			return img;
//
//		logger.trace(String.format("Resizing %d x %d -> %d x %d", img.getWidth(), img.getHeight(), finalWidth, finalHeight));
//
//		double aspectRatio = (double)img.getWidth()/img.getHeight();
//		double finalAspectRatio = (double)finalWidth/finalHeight;
//		if (!GeneralTools.almostTheSame(aspectRatio, finalAspectRatio, 0.01)) {
//			if (!GeneralTools.almostTheSame(aspectRatio, finalAspectRatio, 0.05))
//				logger.warn("Substantial difference in aspect ratio for resized image: {}x{} -> {}x{} ({}, {})", img.getWidth(), img.getHeight(), finalWidth, finalHeight, aspectRatio, finalAspectRatio);
//			else
//				logger.warn("Slight difference in aspect ratio for resized image: {}x{} -> {}x{} ({}, {})", img.getWidth(), img.getHeight(), finalWidth, finalHeight, aspectRatio, finalAspectRatio);
//		}
//
//		boolean areaAveraging = true;
//
//		var raster = img.getRaster();
//		var raster2 = raster.createCompatibleWritableRaster(finalWidth, finalHeight);
//
//		int w = img.getWidth();
//		int h = img.getHeight();
//
//		var fp = new FloatProcessor(w, h);
//		fp.setInterpolationMethod(ImageProcessor.BILINEAR);
//		for (int b = 0; b < raster.getNumBands(); b++) {
//			float[] pixels = (float[])fp.getPixels();
//			raster.getSamples(0, 0, w, h, b, pixels);
//			var fp2 = fp.resize(finalWidth, finalHeight, areaAveraging);
//			raster2.setSamples(0, 0, finalWidth, finalHeight, b, (float[])fp2.getPixels());
//		}
//
//		return new BufferedImage(img.getColorModel(), raster2, img.isAlphaPremultiplied(), null);
//	}
}