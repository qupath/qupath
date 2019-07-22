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

	protected OverlayOptions overlayOptions = null;
	private Color overlayColor = ColorToolsAwt.TRANSLUCENT_BLACK;
	private double opacity = 1.0;
	private AlphaComposite composite = null;
	private boolean visible = true;

	public AbstractOverlay() {
		super();
	}

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

	@Override
	public boolean isVisible() {
		return visible;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	/**
	 * Tests both isVisible() and whether opacity &lt;= 0, i.e. will return true if this overlay could not cause
	 * any change in appearance.
	 * @return
	 */
	@Override
	public boolean isInvisible() {
		return !isVisible() || opacity <= 0;
	}

	@Override
	public void setPreferredOverlayColor(Color color) {
		this.overlayColor = color;
	}

	@Override
	public Color getPreferredOverlayColor() {
		return overlayColor;
	}

	@Override
	public double getOpacity() {
		return opacity;
	}

	@Override
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