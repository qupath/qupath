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

package qupath.lib.awt.images;

import java.awt.image.BufferedImage;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * An implementation of PathImage<BufferedImage>.
 * 
 * @author Pete Bankhead
 *
 */
public class PathBufferedImage implements PathImage<BufferedImage> {
	
	private BufferedImage img = null;
	private RegionRequest request;
	private double pixelWidthMicrons, pixelHeightMicrons;
	
	/**
	 * The bounds are the coordinates in the original image space (in pixels) from which this image was extracted.
	 * This is useful for whole slide images, from which this may be a downsampled part.
	 * 
	 * @param path
	 * @param img
	 * @param bounds
	 * @param downsampleFactor
	 * @param pixelWidthMicrons
	 * @param pixelHeightMicrons
	 */
	public PathBufferedImage(ImageServer<BufferedImage> server, RegionRequest request, BufferedImage img) {
		this.img = img;
		this.request = request;
		this.pixelWidthMicrons = server.getPixelWidthMicrons() * request.getDownsample();
		this.pixelHeightMicrons = server.getPixelHeightMicrons() * request.getDownsample();
	}
	
	@Override
	public String getImageTitle() {
		return request.getPath(); // TODO: Consider shortening the path
	}

	@Override
	public BufferedImage getImage() {
		return img;
	}

	@Override
	public int getWidth() {
		return img.getWidth();
	}

	@Override
	public int getHeight() {
		return img.getHeight();
	}

	@Override
	public double getDownsampleFactor() {
		return request.getDownsample();
	}

	@Override
	public boolean hasCachedImage() {
		return img != null;
	}

	@Override
	public BufferedImage getImage(boolean cache) {
		return img;
	}

	@Override
	public boolean validateSquarePixels() {
		return GeneralTools.almostTheSame(getPixelWidthMicrons(), getPixelHeightMicrons(), 0.0001);
	}

	@Override
	public double getPixelWidthMicrons() {
		return pixelWidthMicrons;
	}

	@Override
	public double getPixelHeightMicrons() {
		return pixelHeightMicrons;
	}
	
	@Override
	public boolean hasPixelSizeMicrons() {
		return !Double.isNaN(pixelWidthMicrons + pixelHeightMicrons);
	}

	@Override
	public ImageRegion getImageRegion() {
		return request;
	}
	
}
