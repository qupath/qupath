/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferFloat;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ColorTransforms.ExtractChannel;
import qupath.lib.images.servers.ColorTransforms.ExtractChannelByName;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.regions.RegionRequest;

/**
 * A {@link TransformingImageServer} that applies color transforms to generate channels.
 * 
 * @author Pete Bankhead
 */
public class ChannelTransformFeatureServer extends TransformingImageServer<BufferedImage> {
	
	private final static Logger logger = LoggerFactory.getLogger(ChannelTransformFeatureServer.class);
	
	private List<ColorTransforms.ColorTransform> transforms;
	private ImageServerMetadata metadata;
	private ColorModel colorModel;

	ChannelTransformFeatureServer(ImageServer<BufferedImage> server, List<ColorTransforms.ColorTransform> transforms) {
		super(server);
		
		logger.trace("Creating server for {} and color transforms {}", server, transforms);
		
		this.transforms = new ArrayList<>(transforms);
		
		List<ImageChannel> channels = new ArrayList<>();
		List<ImageChannel> wrappedChannels = server.getMetadata().getChannels();
		int k = 0;
		for (var t : transforms) {
			int ind = -1;
			if (t instanceof ExtractChannel) {
				ind = ((ExtractChannel)t).getChannelNumber();
			} else if (t instanceof ExtractChannelByName) {
				String name = ((ExtractChannelByName)t).getChannelName();
				int i = 0;
				for (var c : wrappedChannels) {
					if (name.equals(c.getName())) {
						ind = i;
						break;
					}
					i++;
				}
			}
			if (ind >= 0 && ind < wrappedChannels.size())
				channels.add(wrappedChannels.get(ind));
			else
				channels.add(ImageChannel.getInstance(t.getName(), ImageChannel.getDefaultChannelColor(k)));
			k++;
		}
		
		String name;
		if (transforms.size() > 0 && transforms.size() <= 4) {
			String names = transforms.stream().map(t -> t.getName()).collect(Collectors.joining(","));
			name = String.format("%s (%s)", server.getMetadata().getName(), names);
		} else
			name = String.format("%s (%d channels)", server.getMetadata().getName(), transforms.size());
		
		metadata = new ImageServerMetadata.Builder(server.getMetadata())
//				.path(String.format("%s, %s (%s)", server.getPath(), stains.toString(), sb.toString()))
				.pixelType(PixelType.FLOAT32)
				.rgb(false)
				.channels(channels)
				.name(name)
				.build();
	}
	
	protected ColorModel getColorModel() {
		if (colorModel == null) {
			synchronized(this) {
				colorModel = ColorModelFactory.createColorModel(getMetadata().getPixelType(), getMetadata().getChannels());
			}
		}
		return colorModel;
	}
	
	/**
	 * Returns null (does not support ServerBuilders).
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return new ImageServers.ChannelTransformFeatureServerBuilder(
				getMetadata(),
				getWrappedServer().getBuilder(),
				transforms
				);
	}
	
	/**
	 * Returns a UUID.
	 */
	@Override
	protected String createID() {
		return UUID.randomUUID().toString();
	}
	
	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
		BufferedImage img = getWrappedServer().readBufferedImage(request);
		if (img == null)
			return null;
		
		var server = getWrappedServer();
		WritableRaster raster = transformRaster(server, img, transforms);
		
		return new BufferedImage(getColorModel(), raster, false, null);
	}
	
	
	static WritableRaster transformRaster(ImageServer<BufferedImage> server, BufferedImage img, List<ColorTransforms.ColorTransform> transforms) {
		
		int w = img.getWidth();
		int h = img.getHeight();
		int nChannels = transforms.size();
		
		SampleModel model = new BandedSampleModel(DataBuffer.TYPE_FLOAT, w, h, nChannels);
		float[][] bytes = new float[nChannels][w*h];
		DataBufferFloat buffer = new DataBufferFloat(bytes, w*h);
		WritableRaster raster = Raster.createWritableRaster(model, buffer, null);
		
		for (int b = 0; b < transforms.size(); b++) {
			transforms.get(b).extractChannel(server, img, bytes[b]);
		}
		
		return raster;
	}
	

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	
	@Override
	public String getServerType() {
		return "Channel transform server";
	}

}