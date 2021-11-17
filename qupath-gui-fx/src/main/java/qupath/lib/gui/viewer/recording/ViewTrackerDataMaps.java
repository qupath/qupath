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

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.BufferedImageOverlay;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

// TODO: What if the downsample is not rounded? (e.g. dwnsmple=1.5, then img will be rounded, and ImageRegion will be rounded again?)
final class ViewTrackerDataMaps {
	
	private static final Logger logger = LoggerFactory.getLogger(ViewTrackerDataMaps.class);
	
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

	ViewTrackerDataMaps(ImageServer<?> server, QuPathViewer viewer, ViewTracker tracker) {
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
		
		ViewRecordingFrame[] relevantFrames = tracker.getAllFrames().subList(frameStartIndex, frameStopIndex+1).stream()
				.filter(frame -> frame.getZ() == z && frame.getT() == t)
				.filter(frame -> frame.getDownsampleFactor() >= downMin && frame.getDownsampleFactor() <= downMax)
				.toArray(ViewRecordingFrame[]::new);
		
		DataBufferByte byteBuffer = new DataBufferByte(imgHeight * imgWidth);
		double[] blackArray = new double[byteBuffer.getSize()];
		Arrays.fill(blackArray, 0.0);
		DataBufferDouble doubleBuffer = new DataBufferDouble(blackArray, blackArray.length);
		
		// TODO: This used to be return img
		if (relevantFrames.length <= 1)
			return null;
				
		// Get max value (for normalization)
		double maxValue;
		if (timeNormalized)
			maxValue = relevantFrames[relevantFrames.length-1].getTimestamp() - relevantFrames[0].getTimestamp();
		else
			maxValue = Arrays.asList(relevantFrames).stream()
					.mapToDouble(e -> e.getDownsampleFactor())
					.max()
					.orElseThrow();
		
		for (int nFrame = 0; nFrame < relevantFrames.length; nFrame++) {
			var frame = relevantFrames[nFrame];
			Rectangle downsampledBounds = getDownsampledBounds(frame.getImageBounds(), downsample);
			if (nFrame >= relevantFrames.length-1)
				break;
			
//			BufferedImage imgNew = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_BYTE_INDEXED);
	        
	        double value;
	        if (timeNormalized)
	        	value = relevantFrames[nFrame+1].getTimestamp() - frame.getTimestamp();
	        else
	        	value = frame.getDownsampleFactor();
	        
	        if (frame.getRotation() != 0) {
	        	AffineTransform transform = new AffineTransform();
				Point2D center = frame.getFrameCentre();
				transform.rotate(-frame.getRotation(), center.getX()/downsample, center.getY()/downsample);
				// TODO: Deal with rotation
//
//				Graphics2D g2d = imgNew.createGraphics();
//				value = (int) (value / maxValue * 65535);	// Normalize
//				g2d.setColor(new Color((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, 255));
//	        	g2d.setTransform(transform);
//	        	g2d.fill(downsampledBounds);
//				g2d.dispose();
	        } else {
	        	downsampledBounds = getCroppedBounds(downsampledBounds, imgWidth, imgHeight);
	        	for (int y = (int) downsampledBounds.getY(); y < downsampledBounds.getY() + downsampledBounds.getHeight(); y++) {
	        		for (int x = (int) downsampledBounds.getX(); x < downsampledBounds.getX() + downsampledBounds.getWidth(); x++) {
	        			int index =  y * imgWidth+ x;
	        			if (timeNormalized)
	        				doubleBuffer.setElemDouble(index, doubleBuffer.getElemDouble(index) + value/maxValue);
	        			else if (doubleBuffer.getElemDouble(index) < 1-value/maxValue)
	        				doubleBuffer.setElemDouble(index, 1-value/maxValue);
	        		}
	        	}
	        }
		}
		for (int i = 0; i < byteBuffer.getSize(); i++) {
			byteBuffer.setElem(i, (int)(doubleBuffer.getElemDouble(i)*255));
    	}
		var sampleModel = new BandedSampleModel(byteBuffer.getDataType(), imgWidth, imgHeight, 1);
		WritableRaster raster = Raster.createWritableRaster(sampleModel , byteBuffer, null);
		IndexColorModel cm = ColorModelFactory.createIndexedColorModel8bit(colorMap, 0);
		BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_BYTE_INDEXED, cm);
		img.setData(raster);
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
}