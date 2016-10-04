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
import java.awt.geom.Rectangle2D;

import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * A collection of static methods useful when working with AWT.
 * 
 * @author Pete Bankhead
 *
 */
public class AwtTools {

	public static Rectangle getBounds(final ImageRegion region) {
		return getBounds(region, new Rectangle());
	}

	public static Rectangle getBounds(final ImageRegion region, Rectangle rect) {
		if (rect == null)
			rect = new Rectangle();
		rect.setFrame(region.getX(), region.getY(), region.getWidth(), region.getHeight());
		return rect;
	}

	public static Rectangle2D getBounds2D(final ROI pathROI) {
		return getBounds2D(pathROI, new Rectangle2D.Double());
	}
	
	public static Rectangle2D getBounds2D(final ROI pathROI, Rectangle2D rect) {
		if (rect == null)
			rect = new Rectangle2D.Double();
		rect.setFrame(pathROI.getBoundsX(), pathROI.getBoundsY(), pathROI.getBoundsWidth(), pathROI.getBoundsHeight());
		return rect;
	}
	
	public static Rectangle getBounds(final ROI pathROI) {
		if (pathROI.isEmpty())
            return new Rectangle();
        int x1 = (int)pathROI.getBoundsX();
        int y1 = (int)pathROI.getBoundsY();
        int x2 = (int)Math.ceil(pathROI.getBoundsX() + pathROI.getBoundsWidth());
        int y2 = (int)Math.ceil(pathROI.getBoundsY() + pathROI.getBoundsHeight());
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
	}
	
	public static ImageRegion getImageRegion(final Rectangle rectangle, final int z, final int t) {
		return ImageRegion.createInstance(rectangle.x, rectangle.y, rectangle.width, rectangle.height, z, t);
	}

}
