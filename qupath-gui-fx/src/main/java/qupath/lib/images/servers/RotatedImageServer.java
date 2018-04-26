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

package qupath.lib.images.servers;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

import qupath.lib.images.DefaultPathImage;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer that wraps another ImageServer, but intercepts region requests to 
 * effectively rotate the image by 180 degrees.
 * 
 * @author Pete Bankhead
 *
 */
public class RotatedImageServer extends WrappedImageServer<BufferedImage> {
	
	public RotatedImageServer(final ImageServer<BufferedImage> server) {
		super(server);
	}

	@Override
	public PathImage<BufferedImage> readRegion(RegionRequest request) {
		request = rotateRequest(request);
		return new DefaultPathImage<>(this, request, readRotatedBufferedImage(request));
	}

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) {
		request = rotateRequest(request);
		return readRotatedBufferedImage(request);
	}

	RegionRequest rotateRequest(RegionRequest request) {
		return RegionRequest.createInstance(request.getPath() + " (before rotation)", request.getDownsample(), 
				getWidth()-request.getX()-request.getWidth(),
				getHeight() - request.getY() - request.getHeight(),
				request.getWidth(), request.getHeight(), request.getZ(), request.getT());
	}

	
	BufferedImage readRotatedBufferedImage(RegionRequest rotatedRequest) {
		BufferedImage img = getWrappedServer().readBufferedImage(rotatedRequest);
		
		if (img == null) {
			return img;
		}
		
		// TODO: Improve efficiency of this...
		AffineTransform transform = AffineTransform.getScaleInstance(-1, -1);
		transform.translate(-img.getWidth(), -img.getHeight());
	    AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
	    img = op.filter(img, null);
		
		return img;
	}
	
	@Override
	public String getPath() {
		return getWrappedServer().getPath() + " (rotated)";
	}

	@Override
	public String getServerType() {
		return getWrappedServer().getServerType() + " (rotated 180)";
	}

}
