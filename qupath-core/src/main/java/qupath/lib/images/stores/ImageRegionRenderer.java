/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.stores;

import qupath.lib.images.servers.ImageServer;

import java.awt.Graphics;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * Interface for painting regions of an {@link ImageServer} onto a {@link Graphics} object.
 */
public interface ImageRegionRenderer {

	/**
	 * Paint an image region using cached tiles.
	 * This method is intended for on-screen rendering and should return quickly, even if tiles are unavailable.
	 * When this occurs, the tiles may be requested in another thread pool, so that a subsequent call to the
	 * method may be more successful.
	 *
	 * @param server the server representing the image that shown be painted
	 * @param g the graphics object upon which to paint
	 * @param clipShapeVisible the visible shape representing the area of the graphics object that should be filled
	 * @param zPosition the z-stack position
	 * @param tPosition the timepoint position
	 * @param downsampleFactor the downsample factor
	 * @param imgThumbnail a thumbnail image; if not null, this will be used to 'fill the gaps'
	 * @param renderer an {@link ImageRenderer} to convert images to RGB
	 * @return true if the region is painted completely, false if tiles are missing
	 */
	boolean paintRegion(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, BufferedImage imgThumbnail, ImageRenderer renderer);

	/**
	 * Similar to {@link #paintRegion(ImageServer, Graphics, Shape, int, int, double, BufferedImage, ImageRenderer)},
	 * but wait until all the tiles have arrived (or abort if it is taking too long).
	 *
	 * @param server the server representing the image that shown be painted
	 * @param g the graphics object upon which to paint
	 * @param clipShapeVisible the visible shape representing the area of the graphics object that should be filled
	 * @param zPosition the z-stack position
	 * @param tPosition the timepoint position
	 * @param downsampleFactor the downsample factor
	 * @param renderer an {@link ImageRenderer} to convert images to RGB
	 * @param timeoutMilliseconds timeout applied to each tile request; if a tile is taking too long, this method may return
	 * @return true if the region is painted completely, false if tiles are missing (e.g., because of the timeout)
	 */
	boolean paintRegionCompletely(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, ImageRenderer renderer, long timeoutMilliseconds);

}