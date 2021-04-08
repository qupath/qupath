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

package qupath.lib.gui.images.servers;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.SingleChannelDisplayInfo;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TransformingImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer that applies a color transform to an image. This can either be a single RGB transform, or one or more single-channel (float) transforms.
 * <p>
 * Note: This class may move or be removed in a later version.
 * 
 * @author Pete Bankhead
 */
public class ChannelDisplayTransformServer extends TransformingImageServer<BufferedImage> implements PathObjectReader {
	
	private static Logger logger = LoggerFactory.getLogger(ChannelDisplayTransformServer.class);
	
	private List<ChannelDisplayInfo> channels;
	private ImageServerMetadata metadata;
	
	private ColorModel colorModel;
	
	/**
	 * Create an {@link ImageServer} for which the channels are created dynamically from a list of {@linkplain ChannelDisplayInfo ChannelDisplayInfos}.
	 * @param server the server providing the underlying data
	 * @param channels {@link ChannelDisplayInfo} objects defining how the pixels from the wrapped server should be converted to channels in the new server
	 * @return
	 */
	public static ImageServer<BufferedImage> createColorTransformServer(ImageServer<BufferedImage> server, List<ChannelDisplayInfo> channels) {
		return new ChannelDisplayTransformServer(server, channels);
	}
	
	/**
	 * Returns null (does not support ServerBuilders).
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return null;
	}

	private ChannelDisplayTransformServer(ImageServer<BufferedImage> server, List<ChannelDisplayInfo> channels) {
		super(server);
		this.channels = channels;
				
		this.metadata = new ImageServerMetadata.Builder(server.getMetadata())
				.channels(channels.stream().map(c -> ImageChannel.getInstance(c.getName(), c.getColor())).collect(Collectors.toList()))
				.build();
	}
	
	@Override
	protected String createID() {
		return getClass().getName() + ": + " + getWrappedServer().getPath() + " " + GsonTools.getInstance().toJson(channels);
	}
	
	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
		
		// Transform the pixels, if required
		BufferedImage img = getWrappedServer().readBufferedImage(request);
		int width = img.getWidth();
		int height = img.getHeight();
		
		WritableRaster raster = null;
		
		float[] pxFloat = null;
		int b = 0;
		for (ChannelDisplayInfo channel : channels) {
			if (channel instanceof SingleChannelDisplayInfo) {
				if (raster == null) {
					SampleModel model = new BandedSampleModel(DataBuffer.TYPE_FLOAT, width, height, nChannels());
					raster = Raster.createWritableRaster(model, null);
				}
				if (raster.getTransferType() == DataBuffer.TYPE_FLOAT) {
					pxFloat = ((SingleChannelDisplayInfo)channel).getValues(img, 0, 0, width, height, pxFloat);
					raster.setSamples(0, 0, width, height, b, pxFloat);
					b++;
				} else {
					logger.error("Unable to apply color transform " + channel.getName() + " - incompatible with previously-applied transforms");
				}
			} else if (channels.size() == 1) {
				int[] rgb = channel.getRGB(img, null, false);
				img.setRGB(0, 0, width, height, rgb, 0, width);
				return img;
			} else {
				logger.error("Cannot apply requested color transforms! Must either be a single RGB transform, or one or more single-channel transforms");
			}
		}
		if (colorModel == null)
			colorModel = ColorModelFactory.createColorModel(PixelType.FLOAT32, channels.size(), false, channels.stream().mapToInt(c -> {
				Integer color = c.getColor();
				if (color == null)
					color = ColorTools.packRGB(255, 255, 255);
				return color;
			}).toArray());
		
		return new BufferedImage(colorModel, raster, false, null);
	}
	
	@Override
	public String getServerType() {
		String name = String.join(", ", channels.stream().map(c -> c.getName()).collect(Collectors.toList()));
		return super.getWrappedServer().getServerType() + " (" + name + ")";
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}

	@Override
	public Collection<PathObject> readPathObjects() throws IOException {
		var server = getWrappedServer();
		if (server instanceof PathObjectReader)
			return ((PathObjectReader)server).readPathObjects();
		return Collections.emptyList();
	}
	

}