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

package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.io.IOException;

import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServers.RotatedImageServerBuilder;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer that wraps another ImageServer, but intercepts region requests to 
 * effectively rotate the image by 90, 180 or 270 degrees.
 * 
 * @author Pete Bankhead
 *
 */
public class RotatedImageServer extends TransformingImageServer<BufferedImage> {
	
	/**
	 * Enum for rotations in increments of 90 degrees.
	 */
	public static enum Rotation{
		
		/**
		 * No rotation.
		 */
		ROTATE_NONE,
		
		/**
		 * Rotate 90 degrees clockwise.
		 */
		ROTATE_90,
		
		/**
		 * Rotate 180 degrees.
		 */
		ROTATE_180,
		
		/**
		 * Rotate 270 degrees clockwise.
		 */
		ROTATE_270;
		
		@Override
		public String toString() {
			switch(this) {
			case ROTATE_180:
				return "Rotate 180";
			case ROTATE_270:
				return "Rotate 270";
			case ROTATE_90:
				return "Rotate 90";
			case ROTATE_NONE:
				return "No rotation";
			default:
				return "Unknown rotation";
			}
		}
		
	}
	
	private ImageServerMetadata metadata;
	private Rotation rotation;
	
	/**
	 * Create an image server that rotates pixel requests for a second server by a specified increment of 90 degrees.
	 * @param server
	 * @param rotation
	 */
	public RotatedImageServer(final ImageServer<BufferedImage> server, final Rotation rotation) {
		super(server);
		this.rotation = rotation;
		
		switch (rotation) {
		case ROTATE_270:
		case ROTATE_90:
			metadata = getQuarterRotatedMetadata(server.getOriginalMetadata());
			break;
		case ROTATE_180:
			metadata = new ImageServerMetadata.Builder(server.getOriginalMetadata())
//						.path(getPath())
						.build();
			break;
		case ROTATE_NONE:
		default:
			metadata = server.getOriginalMetadata().duplicate();
		}
	}
	
	/**
	 * Get the rotation applied by this server.
	 * @return
	 */
	public Rotation getRotation() {
		return rotation;
	}
	
	
	/**
	 * Need to rotate pixel & image dimensions if rotating by 90 or 270 degrees.
	 * 
	 * @param metadata
	 * @return
	 */
	private ImageServerMetadata getQuarterRotatedMetadata(ImageServerMetadata metadata) {
		
		var levelBuilder = new ImageServerMetadata.ImageResolutionLevel.Builder(metadata.getHeight(), metadata.getWidth());
		for (int i = 0; i < metadata.nLevels(); i++) {
			var level = metadata.getLevel(i);
			levelBuilder.addLevel(level.getDownsample(), level.getHeight(), level.getWidth());
		}
		
		var builder = new ImageServerMetadata.Builder(metadata)
//				.path(getPath())
				.width(metadata.getHeight())
				.height(metadata.getWidth())
				.preferredTileSize(metadata.getPreferredTileHeight(), metadata.getPreferredTileWidth())
				.levels(levelBuilder.build())
				;
		
		if (metadata.pixelSizeCalibrated())
			builder.pixelSizeMicrons(metadata.getPixelHeightMicrons(), metadata.getPixelWidthMicrons());
		
		return builder.build();
	}

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) throws IOException {
		switch (rotation) {
		case ROTATE_180:
			return rotate180(request);
		case ROTATE_270:
			return rotate270(request);
		case ROTATE_90:
			return rotate90(request);
		case ROTATE_NONE:
		default:
			// Don't apply annotation rotation
			return getWrappedServer().readBufferedImage(request);
		}
	}
	
	private BufferedImage rotate90(RegionRequest request) throws IOException {
		var request2 = rotateRequest(request);
		
		var img = getWrappedServer().readBufferedImage(request2);
		if (img == null)
			throw new IOException("Unable to read image for " + request2);
		var raster = img.getRaster();
		int w = raster.getWidth();
		int h = raster.getHeight();
		var raster2 = raster.createCompatibleWritableRaster(h, w);
		float[] samples = new float[w * h];
		float[] samples2 = new float[w * h];
		for (int b = 0; b < raster.getNumBands(); b++) {
			samples = raster.getSamples(0, 0, w, h, b, samples);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int ind1 = y*w + x;
					int ind2 = x*h + (h - y - 1);
					float temp = samples[ind1];
					samples[ind1] = samples[ind2];							
					samples2[ind2] = temp;							
				}				
			}
			raster2.setSamples(0, 0, h, w, b, samples2);
		}
		return new BufferedImage(img.getColorModel(), raster2, img.isAlphaPremultiplied(), null);
	}

	private BufferedImage rotate180(RegionRequest request) throws IOException {
		var request2 = rotateRequest(request);
		
		var img = getWrappedServer().readBufferedImage(request2);
		var raster = img.getRaster();
		int w = raster.getWidth();
		int h = raster.getHeight();
		float[] samples = new float[w * h];
		for (int b = 0; b < raster.getNumBands(); b++) {
			samples = raster.getSamples(0, 0, w, h, b, samples);
			for (int ind1 = 0; ind1 < w*h/2; ind1++) {
//				for (int x = 0; x < w/2; x++) {
//					int ind1 = y*w + x;
					int ind2 = w*h - ind1 - 1;
					float temp = samples[ind1];
					samples[ind1] = samples[ind2];							
					samples[ind2] = temp;							
//				}				
			}
			raster.setSamples(0, 0, w, h, b, samples);
		}
		return img;
	}

	private BufferedImage rotate270(RegionRequest request) throws IOException {
		var request2 = rotateRequest(request);
		
		var img = getWrappedServer().readBufferedImage(request2);
		var raster = img.getRaster();
		int w = raster.getWidth();
		int h = raster.getHeight();
		var raster2 = raster.createCompatibleWritableRaster(h, w);
		float[] samples = new float[w * h];
		float[] samples2 = new float[w * h];
		for (int b = 0; b < raster.getNumBands(); b++) {
			samples = raster.getSamples(0, 0, w, h, b, samples);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int ind1 = y*w + x;
					int ind2 = (w - x - 1)*h + y;
					float temp = samples[ind1];
					samples[ind1] = samples[ind2];							
					samples2[ind2] = temp;							
				}				
			}
			raster2.setSamples(0, 0, h, w, b, samples2);
		}
		return new BufferedImage(img.getColorModel(), raster2, img.isAlphaPremultiplied(), null);
	}

	private RegionRequest rotateRequest(RegionRequest request) {
		String path = getWrappedServer().getPath();
		switch (rotation) {
		case ROTATE_180:
			return RegionRequest.createInstance(path, request.getDownsample(), 
					getWidth()-request.getX()-request.getWidth(),
					getHeight() - request.getY() - request.getHeight(),
					request.getWidth(), request.getHeight(), request.getZ(), request.getT());
		case ROTATE_270:
			return RegionRequest.createInstance(path,
					request.getDownsample(),
					getHeight() - request.getY() - request.getHeight(),
					request.getX(),
					request.getHeight(), request.getWidth(), request.getZ(), request.getT());
		case ROTATE_90:
			return RegionRequest.createInstance(path,
					request.getDownsample(), 
					request.getY(),
					getWidth()-request.getX()-request.getWidth(),
					request.getHeight(), request.getWidth(), request.getZ(), request.getT());
		case ROTATE_NONE:
		default:
			return request;
		}
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	@Override
	protected String createID() {
		int rot = 0;
		switch (rotation) {
		case ROTATE_180:
			rot = 180;
			break;
		case ROTATE_270:
			rot = 270;
			break;
		case ROTATE_90:
			rot = 90;
			break;
		case ROTATE_NONE:
		default:
			rot = 0;
			break;
		}
		return getClass().getName() + ": " + getWrappedServer().getPath() + " (Rotate=" + rot + ")";
	}

	@Override
	public String getServerType() {
		return getWrappedServer().getServerType() + " (" + rotation + ")";
	}
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return new RotatedImageServerBuilder(getMetadata(), getWrappedServer().getBuilder(), getRotation());
	}
	
	/**
	 * Get a ServerBuilder that applies a rotation to another server.
	 * @param builder
	 * @param rotation
	 * @return
	 */
	public static ServerBuilder<BufferedImage> getRotatedBuilder(ServerBuilder<BufferedImage> builder, Rotation rotation) {
		return new RotatedImageServerBuilder(null, builder, rotation);		
	}

}
