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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PathROIToolsAwt;
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
		Shape shape = PathROIToolsAwt.getShape(roi);
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
	 * Ensure that an RGB image is the same kind of RGB, so that the int arrays can be treated as 
	 * storing the pixels as packed RGB values.
	 * <p>
	 * Running this command results in byte array variations, or BGR images are converted to have BufferedImage.TYPE_INT_RGB.
	 * <p>
	 * Images that are already RGB, or RGBA are returned unchanged.
	 * 
	 * @param img
	 * @return
	 */
	public static BufferedImage ensureIntRGB(final BufferedImage img) {
		if (img == null)
			return null;
		switch (img.getType()) {
		case BufferedImage.TYPE_3BYTE_BGR:
		case BufferedImage.TYPE_4BYTE_ABGR:
		case BufferedImage.TYPE_4BYTE_ABGR_PRE:
		case BufferedImage.TYPE_INT_BGR:
			BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
//			BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
			Graphics2D g2d = img2.createGraphics();
			g2d.drawImage(img, 0, 0, null);
			g2d.dispose();
			return img2;
		}
		return img;
	}
	


	/**
	 * Resize the image to have the requested width/height, using area averaging and bilinear interpolation.
	 * 
	 * @param img input image to be resized
	 * @param finalWidth target output width
	 * @param finalHeight target output height
	 * @return resized image
	 */
	public static BufferedImage resize(final BufferedImage img, final int finalWidth, final int finalHeight) {

		//		boolean useLegacyResizing = false;
		//		if (useLegacyResizing) {
		//			return resize(img, finalWidth, finalHeight, false);
		//		}

		if (img.getWidth() == finalWidth && img.getHeight() == finalHeight)
			return img;

		logger.trace(String.format("Resizing %d x %d -> %d x %d", img.getWidth(), img.getHeight(), finalWidth, finalHeight));

		double aspectRatio = (double)img.getWidth()/img.getHeight();
		double finalAspectRatio = (double)finalWidth/finalHeight;
		if (!GeneralTools.almostTheSame(aspectRatio, finalAspectRatio, 0.01)) {
			if (!GeneralTools.almostTheSame(aspectRatio, finalAspectRatio, 0.05))
				logger.warn("Substantial difference in aspect ratio for resized image: {}x{} -> {}x{} ({}, {})", img.getWidth(), img.getHeight(), finalWidth, finalHeight, aspectRatio, finalAspectRatio);
			else
				logger.warn("Slight difference in aspect ratio for resized image: {}x{} -> {}x{} ({}, {})", img.getWidth(), img.getHeight(), finalWidth, finalHeight, aspectRatio, finalAspectRatio);
		}

		boolean areaAveraging = true;

		var raster = img.getRaster();
		var raster2 = raster.createCompatibleWritableRaster(finalWidth, finalHeight);

		int w = img.getWidth();
		int h = img.getHeight();

		var fp = new FloatProcessor(w, h);
		fp.setInterpolationMethod(ImageProcessor.BILINEAR);
		for (int b = 0; b < raster.getNumBands(); b++) {
			float[] pixels = (float[])fp.getPixels();
			raster.getSamples(0, 0, w, h, b, pixels);
			var fp2 = fp.resize(finalWidth, finalHeight, areaAveraging);
			raster2.setSamples(0, 0, finalWidth, finalHeight, b, (float[])fp2.getPixels());
		}

		return new BufferedImage(img.getColorModel(), raster2, img.isAlphaPremultiplied(), null);
	}

}
