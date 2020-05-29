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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;
import qupath.lib.regions.RegionRequest;

/**
 * Helper class intended to make a single-resolution image act more like an image pyramid.
 * This does not avoid the fact that pixels must still be requested at the resolution of the original server, 
 * but enables tile caching at other resolutions - which may substantially improve performance in some cases.
 * 
 * @author Pete Bankhead
 */
class PyramidGeneratingImageServer extends AbstractTileableImageServer implements PathObjectReader {
	
	private ImageServer<BufferedImage> server;
	private ImageServerMetadata metadata;
	
	PyramidGeneratingImageServer(ImageServer<BufferedImage> server, int tileWidth, int tileHeight, double... downsamples) {
		this(server, createDefaultMetadata(server, tileWidth, tileHeight, downsamples));
	}
	
	static ImageServerMetadata createDefaultMetadata(ImageServer<BufferedImage> server, int tileWidth, int tileHeight, double... downsamples) {
		return new ImageServerMetadata.Builder(server.getMetadata())
				.preferredTileSize(tileWidth, tileHeight)
				.levelsFromDownsamples(downsamples)
				.build();
	}
	
	PyramidGeneratingImageServer(ImageServer<BufferedImage> server, ImageServerMetadata metadata) {
		this.server = server;
		this.metadata = metadata;
	}

	@Override
	public Collection<URI> getURIs() {
		return server.getURIs();
	}
	
	@Override
	public String getServerType() {
		return "Generated pyramid (" + server.getServerType() + ")";
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		// Request directly from the original server if that's most efficient
		RegionRequest request = tileRequest.getRegionRequest();
		double downsample = request.getDownsample();
		int level = ServerTools.getPreferredResolutionLevel(this, downsample);
		double closestOriginalDownsample = ServerTools.getPreferredDownsampleFactor(server, downsample);
		if (level == 0 || closestOriginalDownsample >= getDownsampleForResolution(level - 1))
			return server.readBufferedImage(request.updatePath(server.getPath()));
		
		// Read image from the 'previous' resolution
		RegionRequest request2 = request.updateDownsample(getDownsampleForResolution(level - 1));
		
		// If we have an empty tile, we should also return an empty tile
		BufferedImage img = readBufferedImage(request2);
//		if (img == null)
//			return null;
		if (img == null || isEmptyTile(img))
			return getEmptyTile(tileRequest.getTileWidth(), tileRequest.getTileHeight());
		
//		if (img == null || isEmptyTile(img))
//			return getEmptyTile(tileRequest.getTileWidth(), tileRequest.getTileHeight());
		
		// Resize to the required size
		return BufferedImageTools.resize(img, tileRequest.getTileWidth(), tileRequest.getTileHeight(), allowSmoothInterpolation());
	}
	
	/**
	 * Override the default method to request the value from the wrapped server, if possible
	 */
	@Override
	protected boolean allowSmoothInterpolation() {
		if (server instanceof AbstractTileableImageServer)
			return ((AbstractTileableImageServer)server).allowSmoothInterpolation();
		else
			return super.allowSmoothInterpolation();
	}
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		var builder = server.getBuilder();
		if (builder == null)
			return null;
		return new ImageServers.PyramidGeneratingServerBuilder(getMetadata(), builder);
	}
	
	@Override
	protected String createID() {
		return getClass().getSimpleName() + ":" + server.getPath();
	}

	@Override
	public Collection<PathObject> readPathObjects() throws IOException {
		if (server instanceof PathObjectReader)
			return ((PathObjectReader)server).readPathObjects();
		return Collections.emptyList();
	}

}