/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.images.servers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;


/**
 * Abstract implementation of ImageServer providing some common functionality.
 * 
 * @author Pete Bankhead
 *
 */
public abstract class AbstractImageServer<T> implements ImageServer<T> {
	
	final private static Logger logger = LoggerFactory.getLogger(AbstractImageServer.class);
	
	/**
	 * User-defined metadata (e.g. if pixel sizes needed to be set explicitly).
	 */
	private ImageServerMetadata userMetadata;
	
	/**
	 * Cache to use for storing & retrieving tiles.
	 */
	private transient Map<RegionRequest, T> cache;
	
	/**
	 * Cached ServerBuilder, so this doesn't need to be constructed on every request
	 */
	private transient ServerBuilder<T> builder;
	
	/**
	 * Unique ID for this server
	 */
	private String id;
	
	private Class<T> imageClass;
	
	protected AbstractImageServer(Class<T> imageClass) {
		this.imageClass = imageClass;
		this.cache = ImageServerProvider.getCache(imageClass);
	}
	
	@Override
	public Class<T> getImageClass() {
		return imageClass;
	}
	
	/**
	 * Get the internal cache. This may be useful to check for the existence of a cached tile any time 
	 * when speed is of the essence, and if no cached tile is available a request will not be made.
	 * 
	 * @return
	 */
	protected Map<RegionRequest, T> getCache() {
		return cache;
	}
	
	protected double getThumbnailDownsampleFactor(int maxWidth, int maxHeight) {
		if (maxWidth <= 0) {
			if (maxHeight <= 0) {
				maxWidth = 1024;
				maxHeight = 1024;
			} else {
				maxWidth = Integer.MAX_VALUE;
			}
		} else {
			if (maxHeight <= 0) {
				maxHeight = Integer.MAX_VALUE;
			}			
		}

		double xDownsample = (double)getWidth() / maxWidth;
		double yDownsample = (double)getHeight() / maxHeight;
		double downsample = Math.max(xDownsample, yDownsample);
		if (downsample < 1)
			downsample = 1;
		return downsample;
	}
	
	/**
	 * Create a ServerBuilder, which can be used to construct an identical ImageServer.
	 * This should also include the current metadata.
	 * It is permissible to return null for an ImageServer that cannot be recreated 
	 * via a {@link ServerBuilder}.
	 * 
	 * @return
	 */
	protected abstract ServerBuilder<T> createServerBuilder();
	
	@Override
	public ServerBuilder<T> getBuilder() {
		if (builder == null)
			builder = createServerBuilder();
		return builder;
	}
	
	@Override
	public double getDownsampleForResolution(int level) {
		return getMetadata().getDownsampleForLevel(level);
	}
	
	@Override
	public void close() throws Exception {
		logger.trace("Server " + this + " being closed now...");		
	}
	
	@Override
	public int nResolutions() {
		return getMetadata().nLevels();
	}
	
	/**
	 * Request the preferred downsamples from the image metadata.
	 * <p>
	 * Note that this makes a defensive copy of the array, so it is generally preferable to use 
	 * {@code #getDownsampleForResolution(int)} where possible.
	 * 
	 * @see #getDownsampleForResolution(int)
	 */
	@Override
	public double[] getPreferredDownsamples() {
		return getMetadata().getPreferredDownsamplesArray();
	}
	
	@Override
	public boolean isRGB() {
		return getMetadata().isRGB();
	}
	
	@Override
	public PixelType getPixelType() {
		return getMetadata().getPixelType();
	}
	
	
	/**
	 * Attempt to close the server.  While not at all a good idea to rely on this, it may help clean up after some forgotten servers.
	 */
	@Override
	protected void finalize() throws Throwable {
		// Ensure we close...
		try{
			close();
		} catch(Throwable t){
			throw t;
		} finally{
			super.finalize();
		}
	}
	
	
	/**
	 * Always returns false.
	 */
	@Override
	public boolean isEmptyRegion(RegionRequest request) {
		return false;
	}
	
	
	@Override
	public String toString() {
		return getServerType() + ": " + getPath() + " (" + ServerTools.getDisplayableImageName(this) + ")";
	}	
	
	
	@Override
	public T getCachedTile(TileRequest tile) {
		var cache = getCache();
		return cache == null ? null : cache.getOrDefault(tile.getRegionRequest(), null);
	}
	
	/**
	 * Create a unique ID for the server, which can be returned as the default value of {@link #getPath()}.
	 * A suggested implementation is
	 * <p>
	 * <pre>
	 *  getClass().getName() + ": " + URI + parameters
	 * </pre>
	 * This will be called on demand whenever {@link #getPath()} is first required. 
	 * 
	 * @return
	 */
	protected abstract String createID();
	
	/**
	 * Default implementation lazily calls {@link #createID()} on demand.
	 * 
	 * @see #createID()
	 */
	@Override
	public String getPath() {
		if (id == null) {
			synchronized(this) {
				String myID = createID();
				if (id == null)
					id = myID;
			}
		}
		return id;
	}

	@Override
	public int getWidth() {
		return getMetadata().getWidth();
	}

	@Override
	public int getHeight() {
		return getMetadata().getHeight();
	}

	@Override
	public int nChannels() {
		return getMetadata().getSizeC(); // Only RGB
	}
	
	@Override
	public int nZSlices() {
		return getMetadata().getSizeZ();
	}
	
	@Override
	public int nTimepoints() {
		return getMetadata().getSizeT();
	}
	
	@Override
	public synchronized ImageServerMetadata getMetadata() {
		return userMetadata == null ? getOriginalMetadata() : userMetadata;
	}

	@Override
	public synchronized void setMetadata(ImageServerMetadata metadata) {
		if (Objects.equals(metadata, getMetadata()))
			return;
		
		ImageServerMetadata originalMetadata = getOriginalMetadata();
		if (!originalMetadata.isCompatibleMetadata(metadata))
			throw new IllegalArgumentException("Specified metadata is incompatible with original metadata for " + this);
		
		// Reset the tile requests if the resolution levels differ
		if (!getMetadata().getLevels().equals(metadata.getLevels()))
			tileRequestManager = null;
		
		userMetadata = metadata;
		builder = null; // Reset the builder so it will be regenerated as required
	}

	@Override
	public List<String> getAssociatedImageList() {
		return Collections.emptyList();
	}

	@Override
	public T getAssociatedImage(String name) {
		throw new IllegalArgumentException("No associated image with name '" + name + "' for " + getPath());
	}
	
	@Override
	public ImageChannel getChannel(int channel) {
		return getMetadata().getChannel(channel);
	}

	
	@Override
	public T getDefaultThumbnail(int z, int t) throws IOException {
		int ind = nResolutions() - 1;
		double targetDownsample = Math.sqrt(getWidth() / 1024.0 * getHeight() / 1024.0);
		double[] downsamples = getPreferredDownsamples();
		while (ind > 0 && downsamples[ind-1] >= targetDownsample)
			ind--;
		double downsample = downsamples[ind];
		RegionRequest request = RegionRequest.createInstance(getPath(), downsample, 0, 0, getWidth(), getHeight(), z, t);
		return readBufferedImage(request);
	}
		
	
	
	
	
	private DefaultTileRequestManager tileRequestManager;
	
	@Override
	public synchronized TileRequestManager getTileRequestManager() {
		if (tileRequestManager == null || tileRequestManager.currentMetadata != getMetadata()) {
			tileRequestManager = new DefaultTileRequestManager(TileRequest.getAllTileRequests(this));
		}
		return tileRequestManager;
	}
	
	
	private class DefaultTileRequestManager implements TileRequestManager {
		
		private Collection<TileRequest> allTiles;
		private Map<String, SpatialIndex> tiles = new LinkedHashMap<>();
		private ImageServerMetadata currentMetadata;
		
		private String getKey(TileRequest tile) {
			return getKey(tile.getLevel(), tile.getZ(), tile.getT());
		}
		
		private String getKey(int level, int z, int t) {
			return level + ":" + z + ":" + t;
		}
		
		DefaultTileRequestManager(Collection<TileRequest> tiles) {
			currentMetadata = getMetadata();
			allTiles = Collections.unmodifiableList(new ArrayList<>(tiles));
			for (var tile : allTiles) {
				var key = getKey(tile);
				var set = this.tiles.get(key);
				if (set == null) {
					set = new Quadtree();
					this.tiles.put(key, set);
				}
				set.insert(getEnvelope(tile.getRegionRequest()), tile);
//				set.add(tile);
			}
		}
		
		@Override
		public Collection<TileRequest> getAllTileRequests() {
			return allTiles;
		}
		
		@Override
		public TileRequest getTileRequest(int level, int x, int y, int z, int t) {
			var key = getKey(level, z, t);
			var set = tiles.get(key);
			if (set != null) {
				for (var obj : set.query(new Envelope(x, x, y, y))) {
					TileRequest tile = (TileRequest)obj;
					if (tile.getLevel() == level && tile.getRegionRequest().contains(x, y, z, t))
						return tile;
				}				
			}
			return null;
		}
		
		private Envelope getEnvelope(ImageRegion region) {
			return new Envelope(
					region.getX(),
					region.getX() + region.getWidth(),
					region.getY(),
					region.getY() + region.getHeight());
		}
		
		@Override
		public List<TileRequest> getTileRequests(RegionRequest request) {
			int level = ServerTools.getPreferredResolutionLevel(AbstractImageServer.this, request.getDownsample());
			var key = getKey(level, request.getZ(), request.getT());
			var set = tiles.get(key);
			var list = new ArrayList<TileRequest>();
			if (set != null) {
				for (var obj : set.query(getEnvelope(request))) {
					TileRequest tile = (TileRequest)obj;
					if (request.intersects(tile.getRegionRequest()))
						list.add(tile);
				}	
			}
			return list;
		}

		@Override
		public Collection<TileRequest> getTileRequestsForLevel(int level) {
			return getAllTileRequests().stream().filter(t -> t.getLevel() == level).collect(Collectors.toList());
		}
		
	}
	
	
}