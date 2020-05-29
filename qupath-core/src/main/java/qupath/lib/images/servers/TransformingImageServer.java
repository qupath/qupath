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

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import qupath.lib.regions.RegionRequest;

/**
 * An ImageServer implementation used to apply transforms to another ImageServer.
 * This might be a spatial or pixel intensity transformation, for example.
 * <p>
 * Subclasses may only implement the methods necessary to apply the required transform, 
 * such as {@link #readBufferedImage(RegionRequest)} since much of the remaining functionality 
 * is left up to the {@link AbstractImageServer} implementation.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class TransformingImageServer<T> extends AbstractImageServer<T> {
	
	private ImageServer<T> server;
	
	protected TransformingImageServer(ImageServer<T> server) {
		super(server.getImageClass());
		this.server = server;
	}
	
	/**
	 * Get underlying ImageServer, i.e. the one that is being wrapped.
	 * 
	 * @return
	 */
	protected ImageServer<T> getWrappedServer() {
		return server;
	}
	
	@Override
	public Collection<URI> getURIs() {
		return getWrappedServer().getURIs();
	}

	@Override
	public T readBufferedImage(RegionRequest request) throws IOException {
		return server.readBufferedImage(request);
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}

}