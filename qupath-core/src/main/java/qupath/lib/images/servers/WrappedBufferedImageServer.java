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
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;

/**
 * Implementation of an {@link ImageServer} that simply wraps an existing {@link BufferedImage}.
 * <p>
 * This may help whenever requiring a server, but only having a {@link BufferedImage}.
 * 
 * @author Pete Bankhead
 *
 */
public class WrappedBufferedImageServer extends AbstractTileableImageServer {
	
	private ImageServerMetadata originalMetadata;

	private BufferedImage img;
	
	private final int[] rgbTypes = new int[] {
			BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE, BufferedImage.TYPE_INT_RGB
	};
	
	/**
	 * Create an {@code ImageServer<BufferedImage>} using an image that has been provided directly.
	 * 
	 * @param imageName a name to display (may be null)
	 * @param img the BufferedImage to wrap
	 */
	public WrappedBufferedImageServer(final String imageName, final BufferedImage img) {
		this(imageName, img, null);
	}
	
	/**
	 * Create an {@code ImageServer<BufferedImage>} using an image that has been provided directly.
	 * 
	 * @param imageName a name to display (may be null)
	 * @param img the BufferedImage to wrap
	 * @param channels the ImageChannels, required in case the preferred channel colors cannot be obtained from the image itself
	 */
	public WrappedBufferedImageServer(final String imageName, final BufferedImage img, List<ImageChannel> channels) {
		super();
		this.img = BufferedImageTools.duplicate(img);

		// Create metadata objects
		PixelType pixelType;
		switch (img.getRaster().getSampleModel().getTransferType()) {
		case DataBuffer.TYPE_BYTE:
			pixelType = PixelType.UINT8;
			break;
		case DataBuffer.TYPE_USHORT:
			pixelType = PixelType.UINT16;
			break;
		case DataBuffer.TYPE_FLOAT:
			pixelType = PixelType.FLOAT32;
			break;
		case DataBuffer.TYPE_DOUBLE:
			pixelType = PixelType.FLOAT64;
			break;
		case DataBuffer.TYPE_SHORT:
			pixelType = PixelType.INT16;
			break;
		case DataBuffer.TYPE_INT:
			pixelType = PixelType.INT32;
			break;
		default:
			throw new IllegalArgumentException("Unsupported image data type " + img.getRaster().getDataBuffer().getDataType());
		}
		int nChannels = img.getSampleModel().getNumBands();
		boolean isRGB = false;
		for (int type : rgbTypes) {
			isRGB = isRGB | type == img.getType();
			pixelType = PixelType.UINT8;
		}
		// Warning! This method of obtaining channels risks resulting in different colors from the original image
		if (channels == null) {
			if (isRGB)
				channels = ImageChannel.getDefaultRGBChannels();
			else
				channels = ImageChannel.getDefaultChannelList(nChannels);
		}
		originalMetadata = new ImageServerMetadata.Builder()
				.width(img.getWidth())
				.height(img.getHeight())
				.name(imageName)
				.preferredTileSize(img.getWidth(), img.getHeight())
				.levelsFromDownsamples(1.0)
				.rgb(isRGB)
				.pixelType(pixelType)
				.channels(channels)
				.build();
	}
	
	@Override
	public Collection<URI> getURIs() {
		return Collections.emptyList();
	}
	
	/**
	 * Returns a UUID.
	 */
	@Override
	protected String createID() {
		return UUID.randomUUID().toString();
	}

	@Override
	public String getServerType() {
		return "BufferedImage wrapper";
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}
	
	/**
	 * Returns null (does not support ServerBuilders).
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return null;
	}

	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		return img;
	}

}