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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.openslide.AssociatedImage;
import org.openslide.OpenSlide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.awt.images.PathBufferedImage;
import qupath.lib.images.PathImage;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer implementation using OpenSlide.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenslideImageServer extends AbstractImageServer<BufferedImage> {
	
	final private static Logger logger = LoggerFactory.getLogger(OpenslideImageServer.class);

	private ImageServerMetadata originalMetadata;
	private ImageServerMetadata userMetadata;
	private double[] downsamples;
	
	private List<String> associatedImageList = null;
	private Map<String, AssociatedImage> associatedImages = null;

	private OpenSlide osr;
	private Color backgroundColor;
	
	
	private double readNumericPropertyOrDefault(Map<String, String> properties, String name, double defaultValue) {
		// Try to read a tile size
		String value = properties.get(name);
		if (value == null) {
			logger.error("Openslide: Property not available: {}", name);
			return defaultValue;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			logger.error("Openslide: Could not parse property {} with value {}", name, value);
			return defaultValue;
		}
	}


	public OpenslideImageServer(String path) throws IOException {

		// Ensure the garbage collector has run - otherwise any previous attempts to load the required native library
		// from different classloader are likely to cause an error (although upon first further investigation it seems this doesn't really solve the problem...)
		File file = new File(path);
		System.gc();
		osr = new OpenSlide(file);

		// Parse the parameters
		int width = (int)osr.getLevel0Width();
		int height = (int)osr.getLevel0Height();

		Map<String, String> properties = osr.getProperties();
		
		// Try to read a tile size
		int tileWidth = (int)readNumericPropertyOrDefault(properties, "openslide.level[0].tile-width", -1);
		int tileHeight = (int)readNumericPropertyOrDefault(properties, "openslide.level[0].tile-height", -1);
		// Read other properties
		double pixelWidth = readNumericPropertyOrDefault(properties, "openslide.mpp-x", Double.NaN);
		double pixelHeight = readNumericPropertyOrDefault(properties, "openslide.mpp-y", Double.NaN);
		double magnification = readNumericPropertyOrDefault(properties, "openslide.objective-power", Double.NaN);
		
		// Create metadata objects
		originalMetadata = new ImageServerMetadata.Builder(path, width, height).
				setSizeC(3). // Assume 3 channels (RGB)
				setPreferredTileSize(tileWidth, tileHeight).
				setPixelSizeMicrons(pixelWidth, pixelHeight).
				setMagnification(magnification).
				build();

		// Loop through the series again & determine downsamples
		int levelCount = (int)osr.getLevelCount();
		downsamples = new double[levelCount];
		for (int i = 0; i < levelCount; i++)
			downsamples[i] = osr.getLevelDownsample(i);
		
		/*
		 * TODO: Determine associated image names
		 * This works, but need to come up with a better way of returning usable servers
		 * based on the associated images
		 * 
		 */
		associatedImages = osr.getAssociatedImages();
		associatedImageList = new ArrayList<>(associatedImages.keySet());
		associatedImageList = Collections.unmodifiableList(associatedImageList);
		
		// Try to get a background color
		try {
			String bg = properties.get(OpenSlide.PROPERTY_NAME_BACKGROUND_COLOR);
			if (bg != null) {
				if (!bg.startsWith("#"))
					bg = "#" + bg;
				backgroundColor = Color.decode(bg);
			}
		} catch (Exception e) {
			backgroundColor = null;
			logger.debug("Unable to find background color: {}", e.getLocalizedMessage());
		}
		
		// Try reading a thumbnail... the point being that if this is going to fail,
		// we want it to fail quickly so that it may yet be possible to try another server
		// This can occur with corrupt .svs (.tif) files that Bioformats is able to handle better
		logger.info("Test reading thumbnail with openslide: passed (" + getBufferedThumbnail(200, 200, 0).toString() + ")");

	}
	
	@Override
	public double[] getPreferredDownsamples() {
//		for (double d : downsamples)
//			System.out.println(d);
//		return downsamples.clone();
		return downsamples;
	}

	@Override
	public PathImage<BufferedImage> readRegion(RegionRequest request) {
		BufferedImage img = readBufferedImage(request);
		if (img == null)
			return null;
		return new PathBufferedImage(this, request, img);
	}


	@Override
	public void close() {
		if (osr != null)
			osr.close();
	}

	/**
	 * Retrieve a JSON string representation of the properties, as stored as key-value pairs by OpenSlide.
	 * 
	 * @return
	 */
	public String dumpMetadata() {
		return new GsonBuilder().setPrettyPrinting().create().toJson(osr.getProperties());
	}
	
	@Override
	public String getServerType() {
		return "OpenSlide";
	}

	@Override
	public boolean isRGB() {
		return true; // Only RGB currently supported
	}

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) {
		Rectangle region = AwtTools.getBounds(request);
		if (region == null) {
			region = new Rectangle(0, 0, getWidth(), getHeight());
		}
		
		double downsampleFactor = request.getDownsample();
		int level = ServerTools.getClosestDownsampleIndex(getPreferredDownsamples(), downsampleFactor);
		double downsample = downsamples[level];
		int levelWidth = (int)(region.width / downsample + .5);
		int levelHeight = (int)(region.height / downsample + .5);
		BufferedImage img = new BufferedImage(levelWidth, levelHeight, BufferedImage.TYPE_INT_ARGB_PRE);

        int data[] = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
        
        try {
			// Create a thumbnail for the region
			osr.paintRegionARGB(data, region.x, region.y, level, levelWidth, levelHeight);
			
			// Previously tried to take shortcut and only repaint if needed - 
			// but transparent pixels happened too often, and it's really needed to repaint every time
//			if (backgroundColor == null && GeneralTools.almostTheSame(downsample, downsampleFactor, 0.001))
//				return img;
			
			// Rescale if we have to
			int width = (int)(region.width / downsampleFactor + .5);
			int height = (int)(region.height / downsampleFactor + .5);
//			BufferedImage img2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			BufferedImage img2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = img2.createGraphics();
			if (backgroundColor != null) {
				g2d.setColor(backgroundColor);
				g2d.fillRect(0, 0, width, height);
			}
			g2d.drawImage(img, 0, 0, width, height, null);
			g2d.dispose();
			return img2;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public List<String> getSubImageList() {
		return Collections.emptyList();
	}

	@Override
	public String getDisplayedImageName() {
		// TODO: Implement associated images for OpenSlide
//		logger.error("Image names not implemented for OpenSlide yet...");
		return getShortServerName();
	}
	

	@Override
	public boolean usesBaseServer(ImageServer<?> server) {
		return this == server;
	}

	@Override
	public int getBitsPerPixel() {
		return 8; // Only 8-bit RGB images supported
	}
	
	
	@Override
	public boolean containsSubImages() {
		return false;
	}


	@Override
	public Integer getDefaultChannelColor(int channel) {
		return getDefaultRGBChannelColors(channel);
	}

	@Override
	public List<String> getAssociatedImageList() {
		if (associatedImageList == null)
			return Collections.emptyList();
		return associatedImageList;
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		try {
			return associatedImages.get(name).toBufferedImage();
		} catch (Exception e) {
			e.printStackTrace();
		}
		throw new IllegalArgumentException("Unable to find sub-image with the name " + name);
	}

	@Override
	public File getFile() {
		File file = new File(getPath());
		if (file.exists())
			return file;
		return null;
	}


	@Override
	public double getTimePoint(int ind) {
		return 0;
	}


	@Override
	public ImageServerMetadata getMetadata() {
		return userMetadata == null ? originalMetadata : userMetadata;
	}


	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		if (!originalMetadata.isCompatibleMetadata(metadata))
			throw new RuntimeException("Specified metadata is incompatible with original metadata for " + this);
		userMetadata = metadata;
	}

}
