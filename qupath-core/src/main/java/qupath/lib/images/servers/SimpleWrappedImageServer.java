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
import java.util.Collections;
import java.util.List;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;
import qupath.lib.regions.RegionRequest;

/**
 * An ImageServer that simply wraps around an existing ImageServer.
 * <p>
 * This may have no use...
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
@Deprecated
class SimpleWrappedImageServer<T> implements ImageServer<T>, PathObjectReader {
	
	@JsonAdapter(ImageServers.ImageServerTypeAdapter.class)
	private ImageServer<T> server;
	
	protected SimpleWrappedImageServer(ImageServer<T> server) {
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
		return server.getURIs();
	}

	@Override
	public T readBufferedImage(RegionRequest request) throws IOException {
		return server.readBufferedImage(request);
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}

	@Override
	public void close() throws Exception {
		server.close();
	}

	@Override
	public String getPath() {
		return server.getPath();
	}

	@Override
	public double[] getPreferredDownsamples() {
		return server.getPreferredDownsamples();
	}

	@Override
	public int nResolutions() {
		return server.nResolutions();
	}

	@Override
	public double getDownsampleForResolution(int level) {
		return server.getDownsampleForResolution(level);
	}

	@Override
	public int getWidth() {
		return server.getWidth();
	}

	@Override
	public int getHeight() {
		return server.getHeight();
	}

	@Override
	public int nChannels() {
		return server.nChannels();
	}

	@Override
	public boolean isRGB() {
		return server.isRGB();
	}

	@Override
	public int nZSlices() {
		return server.nZSlices();
	}

	@Override
	public int nTimepoints() {
		return server.nTimepoints();
	}

	@Override
	public T getCachedTile(TileRequest tile) {
		return server.getCachedTile(tile);
	}

	@Override
	public String getServerType() {
		return server.getServerType();
	}

	@Override
	public List<String> getAssociatedImageList() {
		return server.getAssociatedImageList();
	}

	@Override
	public T getAssociatedImage(String name) {
		return server.getAssociatedImage(name);
	}

	@Override
	public boolean isEmptyRegion(RegionRequest request) {
		return server.isEmptyRegion(request);
	}

	@Override
	public PixelType getPixelType() {
		return server.getPixelType();
	}

	@Override
	public ImageChannel getChannel(int channel) {
		return server.getChannel(channel);
	}

	@Override
	public ImageServerMetadata getMetadata() {
		return server.getMetadata();
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) throws IllegalArgumentException {
		server.setMetadata(metadata);
	}

	@Override
	public T getDefaultThumbnail(int z, int t) throws IOException {
		return server.getDefaultThumbnail(z, t);
	}

	@Override
	public TileRequestManager getTileRequestManager() {
		return server.getTileRequestManager();
	}

	@Override
	public Class<T> getImageClass() {
		return server.getImageClass();
	}

	@Override
	public Collection<PathObject> readPathObjects() throws IOException {
		if (server instanceof PathObjectReader)
			return ((PathObjectReader)server).readPathObjects();
		return Collections.emptyList();
	}

}