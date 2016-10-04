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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import qupath.lib.gui.viewer.GridLines;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;


/**
 * An overlay used to show a (counting) grid on top of an image.
 * 
 * @author Pete Bankhead
 *
 */
public class GridOverlay extends AbstractImageDataOverlay {

	
	public GridOverlay(final OverlayOptions overlayOptions, final ImageData<BufferedImage> imageData) {
		super(overlayOptions, imageData);
	}
	
	@Override
	public boolean isInvisible() {
		return super.isInvisible() || !getOverlayOptions().getShowGrid();
	}

	@Override
	public void paintOverlay(final Graphics2D g, final ImageRegion imageRegion, final double downsampleFactor, final ImageObserver observer, final boolean paintCompletely) {
		if (isInvisible())
			return;

		
		Graphics2D g2d = (Graphics2D)g.create();
		// Set alpha composite if needed
		setAlphaComposite(g2d);
		
		// Ensure antialias is on...?
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		// Draw grid lines
		g2d.setStroke(new BasicStroke((float)(downsampleFactor*1.5)));
		drawGrid(getOverlayOptions().getGridLines(), g2d, getServer(), imageRegion, getPreferredOverlayColor());
		
		g2d.dispose();
	}


	@Override
	public boolean supportsImageDataChange() {
		return true;
	}

	public static void drawGrid(final GridLines gridLines, final Graphics g, final ImageServer<?> server, final ImageRegion imageRegion, final Color color) {
			// Get the image server, and check if we know the pixel size in microns
			if (server == null || (gridLines.useMicrons() && !server.hasPixelSizeMicrons()))
				return;
			
			Graphics2D g2d = (Graphics2D)g.create();
			
			// Convert microns to pixels if needed
			double startXpx = gridLines.getStartX();
			double startYpx = gridLines.getStartY();
			double spaceXpx = gridLines.getSpaceX();
			double spaceYpx = gridLines.getSpaceY();
			if (gridLines.useMicrons()) {
				startXpx /= server.getPixelWidthMicrons();
				startYpx /= server.getPixelHeightMicrons();
				spaceXpx /= server.getPixelWidthMicrons();
				spaceYpx /= server.getPixelHeightMicrons();
			}
			
			// Do the painting
			if (color != null)
				g2d.setColor(color);
			
			// Compute image coordinate boundaries according to what is actually visible within the image
			int minImageX = imageRegion.getX();
			int maxImageX = imageRegion.getX() + imageRegion.getWidth();
			int minImageY = imageRegion.getY();
			int maxImageY = imageRegion.getY() + imageRegion.getHeight();
			
	//		// Draw vertical lines
			for (double x = startXpx; x < server.getWidth(); x += spaceXpx) {
				// Check if we are within range
				if (x < minImageX || x > maxImageX)
					continue;
				g2d.drawLine((int)(x + .5), minImageY, (int)(x + .5), maxImageY);
			}
			for (double y = startYpx; y < server.getHeight(); y += spaceYpx) {
				// Check if we are within range
				if (y < minImageY || y > maxImageY)
					continue;
				g2d.drawLine(minImageX, (int)(y + .5), maxImageX, (int)(y + .5));
			}
			
					
			g2d.dispose();
	
		}


}
