/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
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

import qupath.lib.images.servers.transforms.BufferedImageNormalizer;
import qupath.lib.io.GsonTools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;

/**
 * An ImageServer implementation used for stain normalization.
 * <p>
 * This assumes that the input and output images have exactly the same type, and therefore there is no requirement
 * to support changes in the image metadata.
 *
 * @since v0.6.0
 */
public class NormalizedImageServer extends AbstractTileableImageServer {

	private final ImageServer<BufferedImage> server;
	private final BufferedImageNormalizer transform;

	protected NormalizedImageServer(ImageServer<BufferedImage> server, BufferedImageNormalizer transform) {
		super();
		this.server = server;
		this.transform = transform;
	}
	
	/**
	 * Get underlying ImageServer, i.e. the one that is being wrapped.
	 * 
	 * @return
	 */
	protected ImageServer<BufferedImage> getWrappedServer() {
		return server;
	}
	
	@Override
	public Collection<URI> getURIs() {
		return getWrappedServer().getURIs();
	}

	@Override
	public String getServerType() {
		return "Normalizing image server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}

	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		var img = getWrappedServer().readRegion(tileRequest.getRegionRequest());
		return img == null ? null : transform.filter(img, img);
	}

	@Override
	protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
		return new ImageServers.NormalizedImageServerBuilder(getMetadata(), getWrappedServer().getBuilder(), transform);
	}

	@Override
	protected String createID() {
		return "Normalized: " + getWrappedServer().getPath() + " " + GsonTools.getInstance(false).toJson(transform);
	}
}
