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

package qupath.lib.gui.viewer.overlays;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;

import qupath.lib.color.ColorToolsAwt;
import qupath.lib.gui.viewer.OverlayOptions;

/**
 * Abstract class to help with implementing PathOverlays.
 * 
 * @author Pete Bankhead
 *
 */
public abstract class AbstractOverlay implements PathOverlay {

	private OverlayOptions overlayOptions = null;
	private Color overlayColor = ColorToolsAwt.TRANSLUCENT_BLACK;
	private double opacity = 1.0;
	private AlphaComposite composite = null;
	
	protected AbstractOverlay(OverlayOptions options) {
		this.overlayOptions = options;
	}

	/**
	 * Get the overlay options, which may influence the display of this overlay.
	 * @return
	 */
	public OverlayOptions getOverlayOptions() {
		return overlayOptions;
	}

	protected AlphaComposite getAlphaComposite() {
		return composite;
	}

	protected void setAlphaComposite(Graphics2D g2d) {
		if (composite != null)
			g2d.setComposite(composite);
	}

	/**
	 * Check overlay visibility status.  If isVisible() returns {@code false},
	 * then calls to paintOverlay() will not do anything.
	 * @return
	 */
	public boolean isVisible() {
		return opacity > 0;
	}

	/**
	 * Set a preferred overlay color, which the overlay may or may not make use of.
	 * The aim is to provide a means to suggest drawing with a light color on a dark image, 
	 * or a dark color on a light image.
	 * 
	 * @param color
	 */
	public void setPreferredOverlayColor(Color color) {
		this.overlayColor = color;
	}

	/**
	 * Return the preferred overlay color.
	 * @return
	 * @see #setPreferredOverlayColor(Color)
	 */
	public Color getPreferredOverlayColor() {
		return overlayColor;
	}

	/**
	 * Get opacity, between 0 (completely transparent) and 1 (completely opaque).
	 * @return
	 */
	public double getOpacity() {
		return opacity;
	}

	/**
	 * Set opacity between 0 (completely transparent) and 1 (completely opaque).
	 * @param opacity
	 */
	public void setOpacity(double opacity) {
		opacity = Math.max(0, Math.min(opacity, 1.0));
		if (this.opacity == opacity)
			return;
		this.opacity = opacity;
		if (opacity == 1.0)
			composite = null;
		else
			composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float)opacity);
	}
	
}