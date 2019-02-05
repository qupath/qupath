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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.PathImage;
import qupath.lib.images.DefaultPathImage;
import qupath.lib.regions.RegionRequest;


/**
 * Abstract implementation of ImageServer providing some common functionality.
 * 
 * @author Pete Bankhead
 *
 */
public abstract class AbstractImageServer<T> implements ImageServer<T> {
	
	final private static Logger logger = LoggerFactory.getLogger(AbstractImageServer.class);
	
	private ImageServerMetadata userMetadata;
	
	private transient Long timestamp = null;
	
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
	public T getBufferedThumbnail(int maxWidth, int maxHeight, int zPosition) throws IOException {
		double downsample = getThumbnailDownsampleFactor(maxWidth, maxHeight);
		return readBufferedImage(
				RegionRequest.createInstance(getPath(), downsample, 0, 0, getWidth(), getHeight(), zPosition, 0));
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
		double[] downsamples = getPreferredDownsamplesArray();
		return ServerTools.getClosestDownsampleIndex(downsamples, requestedDownsample);
	}
	
	@Override
	public double getPreferredDownsampleFactor(double requestedDownsample) {
		double[] downsamples = getPreferredDownsamplesArray();
		int ind = getPreferredResolutionLevel(requestedDownsample);
		return downsamples[ind];
	}
	
	@Override
	public double getDownsampleForResolution(int level) {
		return getPreferredDownsamplesArray()[level];
	}
	
	@Override
	public void close() throws Exception {
		logger.trace("Server " + this + " being closed now...");		
	}
	
	/**
	 * Request the preferred downsamples from the image metadata.
	 * <p>
	 * Note that this returns the array directly; any modifications may result 
	 * in this array becoming corrupted.  This method exists for performance reasons 
	 * to avoid always needing to make defensive copies.
	 * 
	 * @return
	 * 
	 * @see #getPreferredDownsamples()
	 */
	protected double[] getPreferredDownsamplesArray() {
		return getMetadata().getPreferredDownsamples();
	}
	
	@Override
	public int nResolutions() {
		return getPreferredDownsamplesArray().length;
	}
	
	/**
	 * Request the preferred downsamples from the image metadata.
	 * <p>
	 * Note that this makes a defensive copy of the array.
	 * 
	 * @see #getPreferredDownsamplesArray()
	 */
	@Override
	public double[] getPreferredDownsamples() {
		return getMetadata().getPreferredDownsamples().clone();
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
	 * Estimate image width for specified pyramid level.
	 * <p>
	 * Default implementation just divides the image width by the downsample; 
	 * subclasses can override this if they have additional information.
	 * 
	 * @param level
	 * @return
	 */
	public int getLevelWidth(int level) {
		return (int)(getWidth() / getPreferredDownsamplesArray()[level]);
	}
	
	/**
	 * Estimate image height for specified pyramid level.
	 * <p>
	 * Default implementation just divides the image height by the downsample; 
	 * subclasses can override this if they have additional information.
	 * 
	 * @param level
	 * @return
	 */
	public int getLevelHeight(int level) {
		return (int)(getHeight() / getPreferredDownsamplesArray()[level]);
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
	public String getSubImagePath(String imageName) {
		throw new RuntimeException("Cannot construct sub-image with name " + imageName + " for " + getClass().getSimpleName());
	}
	
	@Override
	public File getFile() {
		File file = new File(getPath());
		if (file.exists())
			return file;
		return null;
	}
	
	@Override
	public String getPath() {
		return getMetadata().getPath();
	}

	@Override
	public double getMagnification() {
		return getMetadata().getMagnification();
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
	public int getPreferredTileWidth() {
//		return 1024; // Some servers default to 256, however in a few cases (e.g. NDPI z-stacks) this is too small; here, we aim for a compromise choosing larger tiles
		return getMetadata().getPreferredTileWidth();
	}

	@Override
	public int getPreferredTileHeight() {
//		return 1024;
		return getMetadata().getPreferredTileHeight();
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
	public TimeUnit getTimeUnit() {
		return getMetadata().getTimeUnit();
	}
	
	@Override
	public boolean usesOriginalMetadata() {
		return getOriginalMetadata().equals(getMetadata());
	}
	
	@Override
	public ImageServerMetadata getMetadata() {
		return userMetadata == null ? getOriginalMetadata() : userMetadata;
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		if (!getOriginalMetadata().isCompatibleMetadata(metadata))
			throw new RuntimeException("Specified metadata is incompatible with original metadata for " + this);
		userMetadata = metadata;
	}
	
	@Override
	public double getTimePoint(int ind) {
		return ind * getMetadata().getSizeT();
	}
	
	@Override
	public PathImage<T> readRegion(RegionRequest request) throws IOException {
		T img = readBufferedImage(request);
		if (img == null)
			return null;
		return new DefaultPathImage<>(this, request, img);
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
	public boolean containsSubImages() {
		return false;
	}

	@Override
	public boolean usesBaseServer(ImageServer<?> server) {
		return this == server;
	}
	
	
	@Override
	public Integer getDefaultChannelColor(int channel) {
		return getMetadata().getChannel(channel).getColor();
	}
	
	
	@Override
	public String getChannelName(int channel) {
		return getMetadata().getChannel(channel).getName();
	}
	
	
	private static double LOG2 = Math.log10(2);
	
	/**
	 * Estimate the downsample value for a specific level based on the full resolution image dimensions 
	 * and the level dimensions.
	 * <p>
	 * This method is provides so that different ImageServer implementations can potentially use the same logic.
	 * 
	 * @param fullWidth width of the full resolution image
	 * @param fullHeight height of the full resolution image
	 * @param levelWidth width of the pyramid level of interest
	 * @param levelHeight height of the pyramid level of interest
	 * @param level Resolution level.  Not required for the calculation, but if &geq; 0 and the computed x & y downsamples are very different a warning will be logged.
	 * @return
	 */
	protected static double estimateDownsample(final int fullWidth, final int fullHeight, final int levelWidth, final int levelHeight, final int level) {
		// Calculate estimated downsamples for width & height independently
		double downsampleX = (double)fullWidth / levelWidth;
		double downsampleY = (double)fullHeight / levelHeight;
		
		// Check if the nearest power of 2 is within 1 pixel
		double downsampleAverage = (downsampleX + downsampleY) / 2.0;
		double closestPow2 = Math.pow(2, Math.round(Math.log10(downsampleAverage)/LOG2));
		if (Math.abs(fullHeight / closestPow2 - levelHeight) < 2 && Math.abs(fullWidth / closestPow2 - levelWidth) < 2)
			return closestPow2;
		
		
		// If the difference is less than 1 pixel from what we'd get by downsampling by closest integer, 
		// adjust the downsample factors - we're probably aiming at integer downsampling
		boolean xPow2 = false;
		boolean yPow2 = false;
		if (Math.abs(fullWidth / (double)Math.round(downsampleX)  - levelWidth) <= 1) {
			downsampleX = Math.round(downsampleX);
			xPow2 = Integer.bitCount((int)downsampleX) == 1;
		}
		if (Math.abs(fullHeight / (double)Math.round(downsampleY) - levelHeight) <= 1) {
			downsampleY = Math.round(downsampleY);	
			yPow2 = Integer.bitCount((int)downsampleY) == 1;
		}
		// If one of these is a power of two, use it - this is usually the case
		if (xPow2)
			downsampleY = downsampleX;
		else if (yPow2)
			downsampleX = downsampleY;
		
		/*
		 * Average the calculated downsamples for x & y, warning if they are substantially different.
		 * 
		 * The 'right' way to do this is a bit unclear... 
		 * * OpenSlide also seems to use averaging: https://github.com/openslide/openslide/blob/7b99a8604f38280d14a34db6bda7a916563f96e1/src/openslide.c#L272
		 * * OMERO's rendering may use the 'lower' ratio: https://github.com/openmicroscopy/openmicroscopy/blob/v5.4.6/components/insight/SRC/org/openmicroscopy/shoola/env/rnd/data/ResolutionLevel.java#L96
		 * 
		 * However, because in the majority of cases the rounding checks above will have resolved discrepancies, it is less critical.
		 */
		
		// Average the calculated downsamples for x & y
		double downsample = (downsampleX + downsampleY) / 2;
		
		// Give a warning if the downsamples differ substantially
		if (level >= 0 && !GeneralTools.almostTheSame(downsampleX, downsampleY, 0.001))
			logger.warn("Calculated downsample values differ for x & y for level {}: x={} and y={} - will use value {}", level, downsampleX, downsampleY, downsample);
		return downsample;
	}
	
		
	/**
	 * Default implementation, with returns {@code getFile().lastModified()} if a file is available, otherwise 0L.
	 */
	public long getLastChangeTimestamp() {
		if (timestamp == null) {
			File file = getFile();
			if (file == null)
				timestamp = Long.valueOf(0L);
			else
				timestamp = Long.valueOf(file.lastModified());
		}
		return timestamp.longValue();
	}
	
	
	/**
	 * Default implementation that requests a thumbnail from the first timepoint and 
	 * the center of the z-stack.
	 */
	@Override
	public T getDefaultThumbnail() throws IOException {
		return getDefaultThumbnail(nZSlices()/2, 0);
	}
	
	@Override
	public T getDefaultThumbnail(int z, int t) throws IOException {
		int ind = nResolutions() - 1;
		double targetDownsample = Math.sqrt(getWidth() / 1024.0 * getHeight() / 1024.0);
		double[] downsamples = getPreferredDownsamplesArray();
		while (ind > 0 && downsamples[ind-1] >= targetDownsample)
			ind--;
		double downsample = getPreferredDownsamplesArray()[ind];
		RegionRequest request = RegionRequest.createInstance(getPath(), downsample, 0, 0, getWidth(), getHeight(), z, t);
		return readBufferedImage(request);
	}
		
	
	
	
	
	private TileRequestManager tileRequestManager;
	
	protected synchronized TileRequestManager getTileRequestManager() {
		if (tileRequestManager == null) {
			tileRequestManager = new TileRequestManager(TileRequest.getAllTileRequests(this));
		}
		return tileRequestManager;
	}
	
	public Collection<TileRequest> getAllTileRequests() {
		return getTileRequestManager().getAllTiles();
	}
	
	public TileRequest getTile(int level, int x, int y, int z, int t) {
		return getTileRequestManager().getTile(level, x, y, z, t);
	}
	
	public Collection<TileRequest> getTiles(final RegionRequest request) {
		return getTileRequestManager().getTiles(request);
	}
	
	
	
	private class TileRequestManager {
		
		private Collection<TileRequest> allTiles;
		private Map<String, Set<TileRequest>> tiles = new LinkedHashMap<>();
		
		private String getKey(TileRequest tile) {
			return getKey(tile.getLevel(), tile.getZ(), tile.getT());
		}
		
		private String getKey(int level, int z, int t) {
			return level + ":" + z + ":" + t;
		}
		
		TileRequestManager(Collection<TileRequest> tiles) {
			allTiles = Collections.unmodifiableList(new ArrayList<>(tiles));
			for (var tile : allTiles) {
				var key = getKey(tile);
				var set = this.tiles.get(key);
				if (set == null) {
					set = new LinkedHashSet<>();
					this.tiles.put(key, set);
				}
				set.add(tile);
			}
		}
		
		public Collection<TileRequest> getAllTiles() {
			return allTiles;
		}
		
		public TileRequest getTile(int level, int x, int y, int z, int t) {
			var key = getKey(level, z, t);
			var set = tiles.get(key);
			if (set != null) {
				for (var tile : tiles.get(key)) {
					if (tile.getLevel() == level && tile.getRegionRequest().contains(x, y, z, t))
						return tile;
				}				
			}
			return null;
		}
		
		public List<TileRequest> getTiles(RegionRequest request) {
			int level = getPreferredResolutionLevel(request.getDownsample());
			var key = getKey(level, request.getZ(), request.getT());
			var set = tiles.get(key);
			var list = new ArrayList<TileRequest>();
			if (set != null) {
				for (var tile : set) {
					if (request.intersects(tile.getRegionRequest()))
						list.add(tile);
				}
			}
			return list;
		}
		
	}
	
	
}