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

package qupath.lib.gui.viewer.overlays;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.ImageObserver;

import qupath.lib.regions.ImageRegion;


/**
 * Interface defining an overlay to paint on top of a viewer.
 * 
 * @author Pete Bankhead
 *
 */
public interface PathOverlay {
	
	/**
	 * Paint the overlay to a graphics object.  The graphics object will have a transform applied to it, so the painting should
	 * make use of coordinates in the original image space.
	 * 
	 * @param g2d Graphics2D object to which drawing should be performed. This should have any transform already applied to it.
	 * @param imageRegion The maximum image region that should be shown.
	 * @param downsampleFactor The downsample factor at which the overlay will be viewed.  There is no need for rescaling according to
	 * 							this value since it has already been applied to the Graphics2D as part of its AffineTransform, however
	 * 							it may optionally be needed within the method e.g. to correct line thicknesses.
	 * @param observer ImageObserver for any drawImage calls; may be null.
	 * @param paintCompletely If true, the method is permitted to return without completely painting everything, for performance reasons.
	 */
	public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor, ImageObserver observer, boolean paintCompletely);

	/**
	 * Check overlay visibility status.  If isVisible() returns {@code false},
	 * then calls to paintOverlay() will not do anything.
	 * @return
	 */
	public boolean isVisible();

	/**
	 * Set whether or not the overlay should paint itself when requested.  If setVisible(false) is called,
	 * then calls to paintOverlay() will not do anything.
	 * @param visible
	 */
	public void setVisible(boolean visible);
	
	/**
	 * Tests both isVisible() and whether opacity &lt;= 0, i.e. will return {@code true} if this overlay could not cause
	 * any change in appearance.
	 * @return
	 */
	public boolean isInvisible();

	/**
	 * Set a preferred overlay color, which the overlay may or may not make use of.
	 * The aim is to provide a means to suggest drawing with a light color on a dark image, 
	 * or a dark color on a light image.
	 * 
	 * @param color
	 */
	public void setPreferredOverlayColor(Color color);

	/**
	 * @see #setPreferredOverlayColor
	 */
	public Color getPreferredOverlayColor();

	/**
	 * Get opacity, between 0 (completely transparent) and 1 (completely opaque).
	 * @return
	 */
	public double getOpacity();

	/**
	 * Set opacity between 0 (completely transparent) and 1 (completely opaque).
	 * @param opacity
	 */
	public void setOpacity(double opacity);
	
}
