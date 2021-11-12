/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer.recording;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.BufferedImageOverlay;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

// TODO: What if the downsample is not rounded? (e.g. dwnsmple=1.5, then img will be rounded, and ImageRegion will be rounded again?)
final class ViewTrackerDataOverlay {
	
	private static final Logger logger = LoggerFactory.getLogger(ViewTrackerDataOverlay.class);
	
	private ViewTracker tracker;
	private QuPathViewer viewer;
	private ImageServer<?> server;
	
	private int imgWidth;
	private int imgHeight;
	private double downsample;
	
	private long timeStart;
	private long timeStop;
	private double downMin;
	private double downMax;
	private boolean timeNormalized;	// If false, it's magnification-normalized
	
	private ColorMap colorMap;
	
	private Map<ImageRegion, BufferedImage> regions;
	
	private BooleanProperty generatingOverlayProperty = new SimpleBooleanProperty(false);

	ViewTrackerDataOverlay(ImageServer<?> server, QuPathViewer viewer, ViewTracker tracker) {
		this.tracker = tracker;
		this.viewer = viewer;
		this.server = server;
		this.imgWidth = server.getWidth();
		this.imgHeight = server.getHeight();
		this.regions = new HashMap<>();

		// Set width and height of img
		double[] preferredDownsamples = server.getPreferredDownsamples();
		int index = 0;
		double divider = preferredDownsamples[0];
		while ((long)imgWidth * imgHeight > 2000 * 2000) {
			// Compute downsample to reach img within pixel limit (2k * 2k)
			index++;
			if (index >= preferredDownsamples.length)
				divider = preferredDownsamples[preferredDownsamples.length-1]*2;
			else
				divider = preferredDownsamples[index];
			imgWidth = (int)Math.round(server.getWidth() / divider);
			imgHeight = (int)Math.round(server.getHeight() / divider);
		}
		downsample = divider;
	}
	
	void updateDataImage(long timeStart, long timeStop, double downMin, double downMax, boolean timeNormalised, ColorMap colorMapper) {
		this.timeStart = timeStart;
		this.timeStop = timeStop;
		this.downMin = downMin;
		this.downMax = downMax;
		this.timeNormalized = timeNormalised;
		this.colorMap = colorMapper;
		
		regions = getImageRegions();
		viewer.repaint();
	}
	
	BufferedImageOverlay getOverlay() {
		return new BufferedImageOverlay(viewer, regions);
	}
	

	BooleanProperty generatingOverlayProperty() {
		return generatingOverlayProperty;
	}
	
	private Map<ImageRegion, BufferedImage> getImageRegions() {
		var startTime = System.currentTimeMillis();
		regions.clear();
		for (int z = 0; z < server.nZSlices(); z++) {
			for (int t = 0; t < server.nTimepoints(); t++) {
				ImageRegion region = ImageRegion.createInstance(0, 0, server.getWidth(), server.getHeight(), z, t);
				BufferedImage img = getBufferedImage(z, t);
				regions.put(region, img);
			}
		}
		//TODO: remove next line
		logger.info("Processing time for getImageRegions(): " + (System.currentTimeMillis()-startTime));
		return regions;
	}
	
	private BufferedImage getBufferedImage(int z, int t) {
		int frameStartIndex = tracker.getFrameIndexForTime(timeStart);
		int frameStopIndex = tracker.getFrameIndexForTime(timeStop);
		
		ViewRecordingFrame[] relevantFrames = tracker.getAllFrames().subList(frameStartIndex, frameStopIndex+1).parallelStream()
				.filter(frame -> frame.getZ() == z && frame.getT() == t)
				.filter(frame -> frame.getDownFactor() >= downMin && frame.getDownFactor() <= downMax)
				.toArray(ViewRecordingFrame[]::new);
		
		BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_BYTE_INDEXED, createColorModel(colorMap));
		byte[] imgBuffer = ((DataBufferByte)img.getRaster().getDataBuffer()).getData();
		
		if (relevantFrames.length <= 1)
			return img;
				
		// Get max value (for normalization)
		double maxValue;
		if (timeNormalized)
			maxValue = relevantFrames[relevantFrames.length-1].getTimestamp() - relevantFrames[0].getTimestamp();
		else
			maxValue = Arrays.asList(relevantFrames).stream()
					.mapToDouble(e -> e.getDownFactor())
					.max()
					.orElseThrow();
		
		for (int nFrame = 0; nFrame < relevantFrames.length; nFrame++) {
			var frame = relevantFrames[nFrame];
			Rectangle downsampledBounds = getDownsampledBounds(frame.getImageBounds(), downsample);
			if (nFrame >= relevantFrames.length-1)
				break;
			
			BufferedImage imgNew = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_BYTE_INDEXED);
	        Graphics2D g2d = (Graphics2D) imgNew.getGraphics();
	        
	        int value;
	        if (timeNormalized)
	        	value = (int) (relevantFrames[nFrame+1].getTimestamp() - frame.getTimestamp());
	        else
	        	value = (int) frame.getDownFactor();

	        // Normalize
	        value = (int) (value / maxValue * 65535);
	        g2d.setColor(new Color((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, 255));
	        
	        if (frame.getRotation() != 0) {
	        	AffineTransform transform = new AffineTransform();
				Point2D center = frame.getFrameCentre();
				transform.rotate(-frame.getRotation(), center.getX()/downsample, center.getY()/downsample);
	        	g2d.setTransform(transform);
	        } else
	        	downsampledBounds = getCroppedBounds(downsampledBounds, imgWidth, imgHeight);
	        
	        g2d.fill(downsampledBounds);
	        byte[] imgNewBuffer = ((DataBufferByte)imgNew.getRaster().getDataBuffer()).getData();
	        for (int i = 0; i < imgNewBuffer.length; i++) {
	        	if (timeNormalized)
	        		imgBuffer[i] += imgNewBuffer[i];
	        	else {
	        		if ((imgBuffer[i] == 0 && imgNewBuffer[i] > 0) || imgNewBuffer[i] < imgBuffer[i])
	        			imgBuffer[i] = (byte) (65535 - imgNewBuffer[i]);
	        	}
	        }
		}
		return img;
	}
	
	/**
	 * Scale the coordinates of the given rectangle according 
	 * to the given {@code downsample}.
	 * @param bounds
	 * @param downsample
	 * @return downsampled rectangle
	 */
	private static Rectangle getDownsampledBounds(Rectangle bounds, double downsample) {
		int x = (int)Math.round(bounds.getX()/downsample);
		int y = (int)Math.round(bounds.getY()/downsample);
		int width = (int)Math.round(bounds.getWidth()/downsample);
		int height = (int)Math.round(bounds.getHeight()/downsample);
		return new Rectangle(x, y, width, height);
	}
	
	/**
	 * Ensure that the coordinates of the given rectangle are within the
	 * bounds specified by {@code width} & {@code height}.
	 * <p>
	 * Note: bounds.x is used instead of bounds.getX() to avoid type casting.
	 * @param bounds
	 * @param width 
	 * @param height 
	 * @return cropped rectangle
	 */
	private static Rectangle getCroppedBounds(Rectangle bounds, int width, int height) {
		int x = bounds.x < 0 ? 0 : bounds.x < width ? bounds.x : width;
		int y = bounds.y < 0 ? 0 : bounds.y < height ? bounds.y : height;
		int newWidth = bounds.width < 0 ? 0 : (bounds.width + x > width ? width - x : bounds.width);
		int newHeight = bounds.height < 0 ? 0 : (bounds.height + y > height ? height - y : bounds.height);
		return new Rectangle(x, y, newWidth, newHeight);
	}
	
	private static IndexColorModel createColorModel(ColorMap colorMapper) {
	    int[] rgba = new int[256];
	    for (int i = 0; i < 256; i++) {
	        int rgb = colorMapper.getColor(i, 0, 255);
	        rgba[i] = ColorTools.packARGB(i, ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb));
	    }
	    return new IndexColorModel(8, 256, rgba, 0, true, 0, DataBuffer.TYPE_BYTE);
	}
}