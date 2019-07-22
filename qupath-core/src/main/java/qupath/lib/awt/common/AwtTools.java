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

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * A collection of static methods useful when working with AWT shapes and {@link ImageRegion ImageRegions}.
 * 
 * @author Pete Bankhead
 *
 */
public class AwtTools {

	/**
	 * Create a {@link Rectangle} corresponding to the x,y,width,height of an {@link ImageRegion}.
	 * @param region
	 * @return
	 */
	public static Rectangle getBounds(final ImageRegion region) {
		return getBounds(region, new Rectangle());
	}


	/**
	 * Set the bounds of an existing {@link Rectangle} to the x,y,width,height of an {@link ImageRegion}.
	 * <p>
	 * If no {@code Rectangle} is provided, a new one will be created.
	 * @param region
	 * @param rect
	 * @return
	 */
	public static Rectangle getBounds(final ImageRegion region, Rectangle rect) {
		if (rect == null)
			rect = new Rectangle();
		rect.setFrame(region.getX(), region.getY(), region.getWidth(), region.getHeight());
		return rect;
	}

	/**
	 * Create a {@link Rectangle2D} corresponding to bounding box of a {@link ROI}.
	 * @param roi
	 * @return
	 */
	public static Rectangle2D getBounds2D(final ROI roi) {
		return getBounds2D(roi, new Rectangle2D.Double());
	}
	
	/**
	 * Set the bounds of an existing {@link Rectangle2D} to the x,y,width,height of a {@link ROI}.
	 * <p>
	 * If no {@code Rectangle2D} is provided, a new one will be created.
	 * @param roi
	 * @param rect
	 * @return
	 */
	public static Rectangle2D getBounds2D(final ROI roi, Rectangle2D rect) {
		if (rect == null)
			rect = new Rectangle2D.Double();
		rect.setFrame(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
		return rect;
	}
	
	/**
	 * Create a {@link Rectangle} corresponding to bounding box of a {@link ROI}.
	 * <p>
	 * This differs from {@link #getBounds2D(ROI)} in that the bounding box must consist of integer values.
	 * @param roi
	 * @return
	 */
	public static Rectangle getBounds(final ROI roi) {
		if (roi.isEmpty())
            return new Rectangle();
        int x1 = (int)roi.getBoundsX();
        int y1 = (int)roi.getBoundsY();
        int x2 = (int)Math.ceil(roi.getBoundsX() + roi.getBoundsWidth());
        int y2 = (int)Math.ceil(roi.getBoundsY() + roi.getBoundsHeight());
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
	}
	
	/**
	 * Create an {@link ImageRegion} corresponding to a specified {@link Rectangle} bounding box.
	 * @param rectangle
	 * @param z the z position of the region
	 * @param t the t position of the region
	 * @return
	 */
	public static ImageRegion getImageRegion(final Rectangle rectangle, final int z, final int t) {
		return ImageRegion.createInstance(rectangle.x, rectangle.y, rectangle.width, rectangle.height, z, t);
	}
	
	/**
	 * Create an {@link ImageRegion} corresponding to a the bounding box of a {@link Shape}.
	 * @param shape
	 * @param z the z position of the region
	 * @param t the t position of the region
	 * @return
	 */
	public static ImageRegion getImageRegion(final Shape shape, final int z, final int t) {
		if (shape instanceof Rectangle)
			return getImageRegion((Rectangle)shape, z, t);
		return getImageRegion(shape.getBounds(), z, t);
	}

}
