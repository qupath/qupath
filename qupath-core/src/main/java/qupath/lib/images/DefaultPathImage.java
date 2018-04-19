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

package qupath.lib.images;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * An implementation of {@code PathImage<BufferedImage>}.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultPathImage<T> implements PathImage<T> {
	
	private T img = null;
	private RegionRequest request;
	private double pixelWidthMicrons, pixelHeightMicrons;
	
	/**
	 * The bounds are the coordinates in the original image space (in pixels) from which this image was extracted.
	 * This is useful for whole slide images, from which this may be a downsampled part.
	 * 
	 * @param server
	 * @param request
	 * @param img
	 */
	public DefaultPathImage(ImageServer<T> server, RegionRequest request, T img) {
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
	public T getImage() {
		return img;
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
	public T getImage(boolean cache) {
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
