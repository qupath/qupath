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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
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
	
	private Class<T> imageClass;
	
	private URI uri;
	
	protected AbstractImageServer(URI uri, Class<T> imageClass) {
		this.uri = uri;
		this.imageClass = imageClass;
		this.cache = ImageServerProvider.getCache(imageClass);
	}

	@Override
	public URI getURI() {
		return uri;
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
	
	@Override
	public ImageServerMetadata.OutputType getOutputType() {
		return getMetadata().getOutputType();
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

	@Override
	public boolean hasPixelSizeMicrons() {
		return !Double.isNaN(getPixelWidthMicrons() + getPixelHeightMicrons());
	}
	
	@Override
	public double getAveragedPixelSizeMicrons() {
		return 0.5 * (getPixelWidthMicrons() + getPixelHeightMicrons());
	}
	
	protected int getPreferredResolutionLevel(double requestedDownsample) {
		var metadata = getMetadata();
		double downsampleFactor = Math.max(requestedDownsample, metadata.getDownsampleForLevel(0));
		int n = metadata.nLevels();
		int bestDownsampleSeries = -1;
		double bestDownsampleDiff = Double.POSITIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			double d = metadata.getDownsampleForLevel(i);
			double downsampleDiff = downsampleFactor - d;
			if (!Double.isNaN(downsampleDiff) && (downsampleDiff >= 0 || GeneralTools.almostTheSame(downsampleFactor, d, 0.01)) && downsampleDiff < bestDownsampleDiff) {
				bestDownsampleSeries = i;
				bestDownsampleDiff = Math.abs(downsampleDiff);
			}
		}
		return bestDownsampleSeries;
	}
	
	@Override
	public double getPreferredDownsampleFactor(double requestedDownsample) {
		int level = getPreferredResolutionLevel(requestedDownsample);
		return getDownsampleForResolution(level);
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
	public int getBitsPerPixel() {
		return getMetadata().getBitDepth();
	}
	
	/**
	 * Get the image width for a specified resolution level.
	 * 
	 * @param level
	 * @return
	 */
	public int getLevelWidth(int level) {
		return getMetadata().getLevel(level).getWidth();
	}
	
	/**
	 * Get the image height for a specified resolution level.
	 * 
	 * @param level
	 * @return
	 */
	public int getLevelHeight(int level) {
		return getMetadata().getLevel(level).getHeight();
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
		return getServerType() + ": " + getPath() + " (" + getDisplayedImageName() + ")";
	}	
	
	
	@Override
	public String getShortServerName() {
		return ServerTools.getDefaultShortServerName(getPath());
	}
	
//	public String getShortServerName(final String path) {
//		return getDefaultShortServerName(path);
//	}
	
	@Override
	public T getCachedTile(TileRequest tile) {
		var cache = getCache();
		return cache == null ? null : cache.getOrDefault(tile.getRegionRequest(), null);
	}
	
	@Override
	public ImageServer<T> openSubImage(String imageName) throws IOException {
		throw new UnsupportedOperationException("Cannot construct sub-image with name " + imageName + " for " + getClass().getSimpleName());
	}
	
	@Override
	public String getPath() {
		return getMetadata().getPath();
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
	public double getPixelWidthMicrons() {
		return getMetadata().getPixelWidthMicrons();
	}

	@Override
	public double getPixelHeightMicrons() {
		return getMetadata().getPixelHeightMicrons();
	}
	
	@Override
	public int nZSlices() {
		return getMetadata().getSizeZ();
	}

	@Override
	public double getZSpacingMicrons() {
		return getMetadata().getZSpacingMicrons();
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
		if (metadata == getMetadata())
			return;
		
		if (!getOriginalMetadata().isCompatibleMetadata(metadata))
			throw new IllegalArgumentException("Specified metadata is incompatible with original metadata for " + this);
		
		// Reset the tile requests if the resolution levels differ
		if (!getMetadata().getLevels().equals(metadata.getLevels()))
			tileRequestManager = null;
		
		userMetadata = metadata;
	}
	
	@Override
	public List<String> getSubImageList() {
		return Collections.emptyList();
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
	public String getDisplayedImageName() {
		String name = getMetadata().getName();
		if (name == null)
			return getShortServerName();
		else
			return name;
	}
	
	@Override
	public ImageChannel getChannel(int channel) {
		return getMetadata().getChannel(channel);
	}
	
	@Override
	public List<ImageChannel> getChannels() {
		return getMetadata().getChannels();
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
		
	
	
	
	
	private TileRequestManager tileRequestManager;
	
	protected synchronized TileRequestManager getTileRequestManager() {
		if (tileRequestManager == null || tileRequestManager.currentMetadata != getMetadata()) {
			tileRequestManager = new TileRequestManager(TileRequest.getAllTileRequests(this));
		}
		return tileRequestManager;
	}
	
	@Override
	public Collection<TileRequest> getAllTileRequests() {
		return getTileRequestManager().getAllTiles();
	}
	
	@Override
	public TileRequest getTile(int level, int x, int y, int z, int t) {
		return getTileRequestManager().getTile(level, x, y, z, t);
	}
	
	@Override
	public Collection<TileRequest> getTiles(final RegionRequest request) {
		return getTileRequestManager().getTiles(request);
	}
	
	
	
	private class TileRequestManager {
		
		private Collection<TileRequest> allTiles;
		private Map<String, SpatialIndex> tiles = new LinkedHashMap<>();
		private ImageServerMetadata currentMetadata;
		
		private String getKey(TileRequest tile) {
			return getKey(tile.getLevel(), tile.getZ(), tile.getT());
		}
		
		private String getKey(int level, int z, int t) {
			return level + ":" + z + ":" + t;
		}
		
		TileRequestManager(Collection<TileRequest> tiles) {
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
		
		public Collection<TileRequest> getAllTiles() {
			return allTiles;
		}
		
		public TileRequest getTile(int level, int x, int y, int z, int t) {
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
		
		public List<TileRequest> getTiles(RegionRequest request) {
			int level = getPreferredResolutionLevel(request.getDownsample());
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
		
	}
	
	
}