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
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServers.ConcatChannelsImageServerBuilder;
import qupath.lib.regions.RegionRequest;

/**
 * Concatenate ImageServers along the channel dimension.
 * 
 * @author Pete Bankhead
 *
 */
class ConcatChannelsImageServer extends TransformingImageServer<BufferedImage> {
	
	private ImageServerMetadata originalMetadata;
	private List<ImageServer<BufferedImage>> allServers = new ArrayList<>();

	/**
	 * Define a main ImageServer and a collection of others to append along the channel dimension.
	 * <p>
	 * The order of entries in the collection determines the order in which the channels will be appended.
	 * <p>
	 * The main server is used to determine the metadata. If the main server is also inside the 
	 * collection, then it will be inserted at the corresponding location in the collection.
	 * Otherwise, its pixels may not be shown.
	 * <p>
	 * (This is a change from earlier QuPath v0.2.0 milestones, where the server would be inserted 
	 * at the first position - but this was problematic since it required equality tests to work).
	 * 
	 * @param server
	 * @param imageServers
	 */
	ConcatChannelsImageServer(ImageServer<BufferedImage> server, Collection<ImageServer<BufferedImage>> imageServers) {
		super(server);
		//
//		if (!imageServers.contains(server))
//			allServers.add(0, server);
		allServers.addAll(imageServers);
		
		var channels = new ArrayList<ImageChannel>();
		for (var s : allServers)
			channels.addAll(s.getMetadata().getChannels());
		
		originalMetadata = new ImageServerMetadata.Builder(server.getMetadata())
//				.path("Merged channels ["+String.join(", ", allServers.stream().map(s -> s.getPath()).collect(Collectors.toList())) + "]")
				.rgb(server.getMetadata().isRGB() && allServers.size() == 1)
				.channels(channels)
				.build();
	}

	@Override
	protected String createID() {
		StringBuilder sb = new StringBuilder();
		for (var server : allServers) {
			if (sb.length() == 0)
				sb.append(", ");
			sb.append(server.getPath());
		}
		return getClass().getName() + ": [" + sb + "]";
	}
	
	@Override
	public String getServerType() {
		return "Channel concat image server";
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}
	
	/**
	 * Get an unmodifiable list of all ImageServers being concatenated.
	 * @return
	 */
	public List<ImageServer<BufferedImage>> getAllServers() {
		return Collections.unmodifiableList(allServers);
	}
	
	@Override
	public BufferedImage readBufferedImage(RegionRequest request) throws IOException {
		
		List<WritableRaster> rasters = new ArrayList<>();
		boolean premultiplied = false;
		int nBands = 0;
		for (var server : allServers) {
			var img = server.readBufferedImage(request);
			if (img == null) {
				rasters.add(null);
				nBands += server.nChannels();
			} else {
				premultiplied = img.isAlphaPremultiplied();
				nBands += img.getRaster().getNumBands();
				rasters.add(img.getRaster());
			}
		}
		var first = rasters.stream().filter(r -> r != null).findFirst().orElse(null);
		if (first == null)
			return null;
		int width = first.getWidth();
		int height = first.getHeight();
		
		WritableRaster raster;
		if (first.getDataBuffer().getDataType() != DataBuffer.TYPE_BYTE && first.getDataBuffer().getDataType() != DataBuffer.TYPE_USHORT) {
			BandedSampleModel sampleModel = new BandedSampleModel(DataBuffer.TYPE_FLOAT, width, height, nBands);
			raster = WritableRaster.createWritableRaster(sampleModel, null);
		} else
			raster = WritableRaster.createInterleavedRaster(first.getDataBuffer().getDataType(), width, height, nBands, null);
		
//		var raster = WritableRaster.createBandedRaster(first.getDataBuffer().getDataType(), width, height, nBands, null);
		float[] samples = new float[width * height];
		int currentBand = 0;
		for (var temp : rasters) {
			if (temp == null)
				continue;
			int w = Math.min(width, temp.getWidth());
			int h = Math.min(height, temp.getHeight());
			for (int b = 0; b < temp.getNumBands(); b++) {
				samples = temp.getSamples(0, 0, w, h, b, samples);
				raster.setSamples(0, 0, w, h, currentBand, samples);
				currentBand++;
			}
		}
		
		return new BufferedImage(
				ColorModelFactory.getDummyColorModel(getPixelType().getBitsPerPixel()),
				raster, premultiplied, null);
	}
	
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return new ConcatChannelsImageServerBuilder(
				getMetadata(),
				getWrappedServer().getBuilder(),
				allServers.stream().map(s -> s.getBuilder()).collect(Collectors.toList())
				);
	}
	
}