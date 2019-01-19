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

package qupath.lib.images.servers.openslide;

import java.awt.Color;
import java.awt.Graphics2D;
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

import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer implementation using OpenSlide.
 * 
 * @author Pete Bankhead
 *
 */
public class OpenslideImageServer extends AbstractTileableImageServer {
	
	final private static Logger logger = LoggerFactory.getLogger(OpenslideImageServer.class);

	private ImageServerMetadata originalMetadata;

	private List<String> associatedImageList = null;
	private Map<String, AssociatedImage> associatedImages = null;

	private OpenSlide osr;
	private Color backgroundColor;
	
	
	private double readNumericPropertyOrDefault(Map<String, String> properties, String name, double defaultValue) {
		// Try to read a tile size
		String value = properties.get(name);
		if (value == null) {
			logger.warn("Openslide: Property '{}' not available, will return default value {}", name, defaultValue);
			return defaultValue;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			logger.error("Openslide: Could not parse property '{}' with value {} as a number, will return default value {}", name, value, defaultValue);
			return defaultValue;
		}
	}
	
	public OpenslideImageServer(String path) throws IOException {
		this(null, path);
	}


	public OpenslideImageServer(Map<RegionRequest, BufferedImage> cache, String path) throws IOException {
		super(cache);

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
		double pixelWidth = readNumericPropertyOrDefault(properties, OpenSlide.PROPERTY_NAME_MPP_X, Double.NaN);
		double pixelHeight = readNumericPropertyOrDefault(properties, OpenSlide.PROPERTY_NAME_MPP_Y, Double.NaN);
		double magnification = readNumericPropertyOrDefault(properties, OpenSlide.PROPERTY_NAME_OBJECTIVE_POWER, Double.NaN);
		
		// Loop through the series again & determine downsamples
		int levelCount = (int)osr.getLevelCount();
		double[] downsamples = new double[levelCount];
		downsamples[0] = 1;
		for (int i = 1; i < levelCount; i++) {
			// When requesting downsamples from OpenSlide, these seem to be averaged from the width & height ratios:
			// https://github.com/openslide/openslide/blob/7b99a8604f38280d14a34db6bda7a916563f96e1/src/openslide.c#L272
			// However this can result in inexact floating point values whenever the 'true' downsample is 
			// almost certainly an integer value, therefore we prefer to use our own calculation.
			// Other ImageServer implementations can also draw on our calculation for consistency (or override it if they can do better).
			downsamples[i] = estimateDownsample(width, height, (int)osr.getLevelWidth(i), (int)osr.getLevelHeight(i), i);
//			downsamples[i] = osr.getLevelDownsample(i);
		}

		// Create metadata objects
		originalMetadata = new ImageServerMetadata.Builder(path, width, height).
				setSizeC(3). // Assume 3 channels (RGB)
				setRGB(true).
				setBitDepth(8).
				setPreferredTileSize(tileWidth, tileHeight).
				setPixelSizeMicrons(pixelWidth, pixelHeight).
				setMagnification(magnification).
				setPreferredDownsamples(downsamples).
				build();
		
		/*
		 * TODO: Determine associated image names
		 * This works, but need to come up with a better way of returning usable servers
		 * based on the associated images
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
	public BufferedImage readTile(TileRequest tileRequest) throws IOException {
		
		int tileWidth = tileRequest.getTileWidth();
		int tileHeight = tileRequest.getTileHeight();

//		double downsampleFactor = getPreferredDownsamplesArray()[downsampleInd];
		BufferedImage img = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        int data[] = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
        
		// Create a thumbnail for the region
//        	osr.paintRegionOfLevel(g, dx, dy, sx, sy, w, h, level);
		osr.paintRegionARGB(data, tileRequest.getImageX(), tileRequest.getImageY(), tileRequest.getLevel(), tileWidth, tileHeight);
		
		// Previously tried to take shortcut and only repaint if needed - 
		// but transparent pixels happened too often, and it's really needed to repaint every time
//			if (backgroundColor == null && GeneralTools.almostTheSame(downsample, downsampleFactor, 0.001))
//				return img;
		
		BufferedImage img2 = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = img2.createGraphics();
		if (backgroundColor != null) {
			g2d.setColor(backgroundColor);
			g2d.fillRect(0, 0, tileWidth, tileHeight);
		}
		g2d.drawImage(img, 0, 0, tileWidth, tileHeight, null);
		g2d.dispose();
		return img2;
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
			logger.error("Error requesting associated image " + name, e);
		}
		throw new IllegalArgumentException("Unable to find sub-image with the name " + name);
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}

}
