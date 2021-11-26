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
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

// TODO: What if the downsample is not rounded? (e.g. dwnsmple=1.5, then img will be rounded, and ImageRegion will be rounded again?)
final class ViewTrackerDataMaps {
	
	private static final Logger logger = LoggerFactory.getLogger(ViewTrackerDataMaps.class);
	
	private final ViewTracker tracker;
	
	private final int nZSlices;
	private final int nTimepoints;
	private final int fullWidth;
	private final int fullHeight;
	private final double downsample;
	
	private int dataMapWidth;
	private int dataMapHeight;
	
	// These variables will change every time the data maps are updated
	private long timeStart = -1;
	private long timeStop = -1;
	private double downsampleMin = -1;
	private double downsampleMax = -1;
	private boolean timeNormalized = true;	// If false, it's magnification-normalized
	
	private ColorMap colorMap;
	
	private final Map<ImageRegion, BufferedImage> regionMaps;
	
	private BooleanProperty generatingOverlayProperty = new SimpleBooleanProperty(false);

	ViewTrackerDataMaps(ViewTracker tracker, int fullWidth, int fullHeight, int nZSlices, int nTimepoints, double[] downsamples) {
		this.tracker = tracker;
		this.fullWidth = fullWidth;
		this.fullHeight = fullHeight;
		this.dataMapWidth = fullWidth;
		this.dataMapHeight = fullHeight;
		this.nZSlices = nZSlices;
		this.nTimepoints = nTimepoints;
		this.regionMaps = new HashMap<>();

		// Set width and height of img
		int index = 0;
		double divider = downsamples[0];
		while ((long)dataMapWidth * dataMapHeight > 2000 * 2000) {
			// Compute downsample to reach img within pixel limit (2k * 2k)
			index++;
			if (index >= downsamples.length)
				divider = downsamples[downsamples.length-1]*2;
			else
				divider = downsamples[index];
			dataMapWidth = (int)Math.round(fullWidth / divider);
			dataMapHeight = (int)Math.round(fullHeight / divider);
		}
		downsample = divider;
	}
	
	ViewTrackerDataMaps(ImageServer<?> server, ViewTracker tracker) {
		this(tracker, server.getWidth(), server.getHeight(), server.nZSlices(), server.nTimepoints(), server.getPreferredDownsamples());
	}
		
	Map<ImageRegion, BufferedImage> getRegionMaps() {
		return regionMaps;
	}
	
	BooleanProperty generatingOverlayProperty() {
		return generatingOverlayProperty;
	}
	
	void updateDataImage(long timeStart, long timeStop, double downsampleMin, double downsampleMax, boolean timeNormalised, ColorMap colorMapper) {
		this.timeStart = timeStart;
		this.timeStop = timeStop;
		this.downsampleMin = downsampleMin;
		this.downsampleMax = downsampleMax;
		this.timeNormalized = timeNormalised;
		this.colorMap = colorMapper;
		
		populateRegionMap();
	}
	private Map<ImageRegion, BufferedImage> populateRegionMap() {
		var startTime = System.currentTimeMillis();
		regionMaps.clear();
		for (int z = 0; z < nZSlices; z++) {
			for (int t = 0; t < nTimepoints; t++) {
				ImageRegion region = ImageRegion.createInstance(0, 0, fullWidth, fullHeight, z, t);
				BufferedImage img = createDataImage(z, t);
				regionMaps.put(region, img);
			}
		}
		//TODO: remove next line
		logger.info("Processing time for populateRegionMap(): " + (System.currentTimeMillis()-startTime));
		return regionMaps;
	}
	
	private BufferedImage createDataImage(int z, int t) {
		// This could only happen if calling createDataImage before updateDataImage()
		if (timeStart == -1 || timeStop == -1 || downsampleMin == -1 || downsampleMax == -1 || colorMap == null)
			return null;
		
		// Get relevant frames
		int frameStartIndex = tracker.getFrameIndexForTime(timeStart);
		int frameStopIndex = tracker.getFrameIndexForTime(timeStop);
		
		List<ViewRecordingFrame> relevantFrames = new ArrayList<>();
		ViewRecordingFrame previousFrame = tracker.getFrame(frameStopIndex);
		for (int nFrame = frameStopIndex; nFrame > frameStartIndex; nFrame--) {
			var frame = tracker.getFrame(nFrame);
			if (frame.getZ() != z && frame.getT() != t)
				continue;
			if (frame.getDownsampleFactor() < downsampleMin || frame.getDownsampleFactor() > downsampleMax)
				continue;
			if (sameImageBounds(frame, previousFrame))
				continue;
		
			relevantFrames.add(frame);
			previousFrame = frame;
		}

		double[] array = calculateMapValues(relevantFrames.toArray(ViewRecordingFrame[]::new), timeNormalized, downsample, dataMapWidth, dataMapHeight);
		DataBufferByte byteBuffer = new DataBufferByte(array.length);
		for (int i = 0; i < array.length; i++) {
			byteBuffer.setElem(i, (int)(array[i]*255));
    	}
		var sampleModel = new BandedSampleModel(byteBuffer.getDataType(), dataMapWidth, dataMapHeight, 1);
		WritableRaster raster = Raster.createWritableRaster(sampleModel , byteBuffer, null);
		IndexColorModel cm = ColorModelFactory.createIndexedColorModel8bit(colorMap, 0);
		BufferedImage img = new BufferedImage(dataMapWidth, dataMapHeight, BufferedImage.TYPE_BYTE_INDEXED, cm);
		img.setData(raster);
		return img;
	}
	
	private static boolean sameImageBounds(ViewRecordingFrame f1, ViewRecordingFrame f2) {
		var b1 = f1.getImageBounds();
		var b2 = f2.getImageBounds();
		
		if (b1.getX() != b2.getX() ||
				b1.getY() != b2.getY() ||
				b1.getWidth() != b2.getWidth() ||
				b1.getHeight() != b2.getHeight())
			return false;
		
		if (f1.getRotation() != f2.getRotation())
			return false;
		return true;
			
		
	}
	
	/**
	 * Calculate the map values for each pixel, returned in a double array whose size is specified through the specified {@code targetWidth} & {@code targetHeight}.
	 * @param frames 				all frames to process
	 * @param timeNormalized	whether it is normalized by time or by magnification
	 * @param downsample		How much to downsample the values of each frames to match target size
	 * @param targetWidth		width of the map
	 * @param targetHeight		height of the map
	 * @return map values in double array
	 */
	private static double[] calculateMapValues(ViewRecordingFrame[] frames, boolean timeNormalized, double downsample, int targetWidth, int targetHeight) {
		double[] array = new double[targetHeight * targetWidth];
		Arrays.fill(array, 0.0);
		
		if (frames.length <= 1)
			return array;
				
		// Get max value (for normalization)
		double maxValue;
		if (timeNormalized)
			maxValue = frames[frames.length-1].getTimestamp() - frames[0].getTimestamp();
		else
			maxValue = Arrays.asList(frames).stream()
					.mapToDouble(e -> e.getDownsampleFactor())
					.max()
					.orElseThrow();
		
		for (int nFrame = 0; nFrame < frames.length; nFrame++) {
			var frame = frames[nFrame];
			Rectangle downsampledBounds = getDownsampledBounds(frame.getImageBounds(), downsample);
			if (nFrame >= frames.length-1)
				break;
	        
	        double value;
	        if (timeNormalized)
	        	value = frames[nFrame+1].getTimestamp() - frame.getTimestamp();
	        else
	        	value = frame.getDownsampleFactor();
	        
	        downsampledBounds = getCroppedBounds(downsampledBounds, targetWidth, targetHeight);
	        Shape rotated = null;	 // if rotation != 0, this variable will be initialised
	        if (frame.getRotation() != 0) {
	        	AffineTransform transform = new AffineTransform();
				Point2D center = frame.getFrameCentre();
				transform.rotate(-frame.getRotation(), center.getX()/downsample, center.getY()/downsample);
				rotated = transform.createTransformedShape(downsampledBounds);
				downsampledBounds = rotated.getBounds();
	        }
	        
	        // Iterate through all the pixel in the bounding box of the rotated rectangle
	        for (int y = (int) downsampledBounds.getY(); y < downsampledBounds.getY() + downsampledBounds.getHeight(); y++) {
        		for (int x = (int) downsampledBounds.getX(); x < downsampledBounds.getX() + downsampledBounds.getWidth(); x++) {
        			int index =  y * targetWidth+ x;
        			if (rotated == null || (rotated.contains(new Point2D.Double(x, y)) && index > 0 && index < array.length)) {
        				if (timeNormalized)
        					array[index] += value/maxValue;
        				else if (array[index] < 1-value/maxValue)
        					array[index] = 1-value/maxValue;        				
        			}
        		}
        	}
		}
		return array;
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