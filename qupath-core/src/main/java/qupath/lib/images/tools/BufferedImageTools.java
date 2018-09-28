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

package qupath.lib.images.tools;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;

import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.interfaces.ROI;

/**
 * Some helpful static methods for working with BufferedImages and QuPath objects.
 * 
 * @author Pete Bankhead
 *
 */
public class BufferedImageTools {
	
	/**
	 * Create a grayscale BufferedImage representing a mask for a specified ROI.
	 * 
	 * Pixels inside the ROI will be 255, pixels outside will be 0.
	 * 
	 * @param width Width of the requested mask image
	 * @param height Height of the requested mask image
	 * @param roi ROI for mask
	 * @param request Region that the mask should correspond to, including the origin (x &amp; y) and downsample factor to use.
	 * @return
	 */
	public static BufferedImage createROIMask(final int width, final int height, final ROI roi, final RegionRequest request) {
		return createROIMask(width, height, roi, request.getX(), request.getY(), request.getDownsample());
	}

	/**
	 * Create a grayscale BufferedImage representing a mask for a specified ROI.
	 * 
	 * Pixels inside the ROI will be 255, pixels outside will be 0.
	 * 
	 * @param width Width of the requested mask image
	 * @param height Height of the requested mask image
	 * @param roi ROI for mask
	 * @param xOrigin Pixel x coordinate of the top left of the region to include in the mask.
	 * @param yOrigin Pixel y coordinate of the top left of the region to include in the mask.
	 * @param downsample Downsample factor to use when generating the mask, i.e. the amoutn to scale.
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

}
