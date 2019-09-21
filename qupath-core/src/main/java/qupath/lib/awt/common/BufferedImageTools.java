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

package qupath.lib.awt.common;

import java.awt.Color;
import java.awt.Graphics2D;
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
public class BufferedImageTools {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractTileableImageServer.class);
	
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
	 * Create a grayscale BufferedImage representing a mask for a specified ROI.
	 * <p>
	 * Pixels inside the ROI will be 255, pixels outside will be 0.
	 * 
	 * @param width width of the requested mask image
	 * @param height height of the requested mask image
	 * @param roi ROI for mask
	 * @param xOrigin pixel x coordinate of the top left of the region to include in the mask.
	 * @param yOrigin pixel y coordinate of the top left of the region to include in the mask.
	 * @param downsample downsample factor to use when generating the mask, i.e. the amoutn to scale.
	 * @return
	 */
	public static BufferedImage createROIMask(final int width, final int height, final ROI roi, final double xOrigin, final double yOrigin, final double downsample) {
		BufferedImage imgMask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		Shape shape = RoiTools.getShape(roi);
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
	 * @param img
	 * @return
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
		case DataBuffer.TYPE_FLOAT:
			float fval = (float)val;
			for (int b = 0; b < nBanks; b++) {
				for (int i = 0; i < n; i++)
					buffer.setElemFloat(b, i, fval);
			}
		case DataBuffer.TYPE_DOUBLE:
			for (int b = 0; b < nBanks; b++) {
				for (int i = 0; i < n; i++)
					buffer.setElemDouble(b, i, val);
			}
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
