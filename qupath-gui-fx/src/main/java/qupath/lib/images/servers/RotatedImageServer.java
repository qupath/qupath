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
import java.io.File;
import java.util.List;

import qupath.lib.awt.images.PathBufferedImage;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer that wraps another ImageServer, but intercepts region requests to 
 * effectively rotate the image by 180 degrees.
 * 
 * @author Pete Bankhead
 *
 */
public class RotatedImageServer extends AbstractImageServer<BufferedImage> {
	
	private ImageServer<BufferedImage> server;
	
	public RotatedImageServer(final ImageServer<BufferedImage> server) {
		super();
		this.server = server;
	}

	@Override
	public double[] getPreferredDownsamples() {
		return server.getPreferredDownsamples();
	}

	@Override
	public boolean isRGB() {
		return server.isRGB();
	}

	@Override
	public double getTimePoint(int ind) {
		return server.getTimePoint(ind);
	}
	
	

	@Override
	public PathImage<BufferedImage> readRegion(RegionRequest request) {
		request = rotateRequest(request);
		return new PathBufferedImage(this, request, readRotatedBufferedImage(request));
	}

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) {
		request = rotateRequest(request);
		return readRotatedBufferedImage(request);
	}

	RegionRequest rotateRequest(RegionRequest request) {
		return RegionRequest.createInstance(request.getPath(), request.getDownsample(), 
				getWidth()-request.getX()-request.getWidth(),
				getHeight() - request.getY() - request.getHeight(),
				request.getWidth(), request.getHeight(), request.getZ(), request.getT());
	}

	
	BufferedImage readRotatedBufferedImage(RegionRequest rotatedRequest) {
		BufferedImage img = server.readBufferedImage(rotatedRequest);
		
		if (img == null)
			return img;
		
		// TODO: Improve efficiency of this...
		AffineTransform transform = AffineTransform.getScaleInstance(-1, -1);
		transform.translate(-img.getWidth(null), -img.getHeight(null));
	    AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
	    img = op.filter(img, null);
		
		return img;
	}
	

	@Override
	public String getServerType() {
		return server.getServerType() + " (rotated 180)";
	}

	@Override
	public List<String> getSubImageList() {
		return server.getSubImageList();
	}

	@Override
	public List<String> getAssociatedImageList() {
		return server.getAssociatedImageList();
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		return server.getAssociatedImage(name);
	}

	@Override
	public String getDisplayedImageName() {
		return server.getDisplayedImageName();
	}

	@Override
	public boolean containsSubImages() {
		return server.containsSubImages();
	}

	@Override
	public boolean usesBaseServer(ImageServer<?> server) {
		return server.usesBaseServer(server);
	}

	@Override
	public File getFile() {
		return server.getFile();
	}

	@Override
	public int getBitsPerPixel() {
		return server.getBitsPerPixel();
	}

	@Override
	public Integer getDefaultChannelColor(int channel) {
		return server.getDefaultChannelColor(channel);
	}

	@Override
	public ImageServerMetadata getMetadata() {
		return server.getMetadata();
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		server.setMetadata(metadata);
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}

}
