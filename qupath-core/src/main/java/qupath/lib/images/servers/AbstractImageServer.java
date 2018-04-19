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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
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
	public T getBufferedThumbnail(int maxWidth, int maxHeight, int zPosition) {
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
	
	@Override
	public double getPreferredDownsampleFactor(double requestedDownsample) {
		double[] downsamples = getPreferredDownsamples();
		int ind = ServerTools.getClosestDownsampleIndex(downsamples, requestedDownsample);
		return downsamples[ind];
	}
	
	@Override
	public void close() {
		logger.trace("Server " + this + " being closed now...");		
	}
	
	@Override
	public double[] getPreferredDownsamples() {
		return getMetadata().getPreferredDownsamples();
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

	
	/**
	 * Method that may be used to get RGB colors.
	 * Classes that only provide RGB images may call this from their getDefaultChannelColors method.
	 * 
	 * @param channel
	 * @return
	 */
	protected Integer getDefaultRGBChannelColors(int channel) {
		if (nChannels() == 1)
			return ColorTools.makeRGB(255, 255, 255);
		switch (channel) {
		case 0: return ColorTools.makeRGB(255, 0, 0);
		case 1: return ColorTools.makeRGB(0, 255, 0);
		case 2: return ColorTools.makeRGB(0, 0, 255);
		default:
			return ColorTools.makeRGB(255, 255, 255);
		}
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
	
	
	/**
	 * Similar to getDefaultRGBChannelColors, but including Magenta, Cyan &amp; Yellow to return colors for up to 6 channels.
	 * If only one channel is present, or a channel number &gt; 6 is requested, Color.WHITE is returned.
	 * 
	 * @param channel
	 * @return
	 */
	protected Integer getExtendedDefaultChannelColor(int channel) {
		if (nChannels() == 1)
			return ColorTools.makeRGB(255, 255, 255);
		switch (channel) {
		case 0: return ColorTools.makeRGB(255, 0, 0);
		case 1: return ColorTools.makeRGB(0, 255, 0);
		case 2: return ColorTools.makeRGB(0, 0, 255);
		case 3: return ColorTools.makeRGB(255, 255, 0);
		case 4: return ColorTools.makeRGB(0, 255, 255);
		case 5: return ColorTools.makeRGB(255, 255, 0);
		default:
			return ColorTools.makeRGB(255, 255, 255);
		}
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
	public PathImage<T> readRegion(RegionRequest request) {
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
		return getShortServerName();
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
		if (isRGB()) {
			return getDefaultRGBChannelColors(channel);
		}
		// Grayscale
		if (nChannels() == 1)
			return ColorTools.makeRGB(255, 255, 255);
		
		return getExtendedDefaultChannelColor(channel);
	}
	
}