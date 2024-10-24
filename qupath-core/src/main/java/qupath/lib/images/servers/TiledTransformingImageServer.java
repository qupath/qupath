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
import java.net.URI;
import java.util.Collection;

/**
 * An ImageServer implementation used to apply transforms to another ImageServer.
 * This may implement a spatial or pixel intensity transformation, for example.
 * <p>
 * Subclasses may only implement the methods necessary to apply the required transform, 
 * such as {@link #readTile(TileRequest)}.
 * <p>
 * This class should be used in preference to {@link TransformingImageServer} when internal tile caching
 * is desirable.
 * 
 * @author Pete Bankhead
 *
 * @since v0.6.0
 */
public abstract class TiledTransformingImageServer extends AbstractTileableImageServer {

	private ImageServer<BufferedImage> server;

	protected TiledTransformingImageServer(ImageServer<BufferedImage> server) {
		super();
		this.server = server;
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
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}

}
