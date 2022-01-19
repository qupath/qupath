/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

// TODO: What if the downsample is not rounded? (e.g. dwnsmple=1.5, then img will be rounded, and ImageRegion will be rounded again?)
final class ViewTrackerDataMaps {
	
	static enum Feature {
		TIMESTAMP,
		DOWNSAMPLE;
	}
	
	private static final Logger logger = LoggerFactory.getLogger(ViewTrackerDataMaps.class);
	
	private final ViewTracker tracker;
	
	private final int nZSlices;
	private final int nTimepoints;
	private final int fullWidth;
	private final int fullHeight;
	private final double downsample;
	
	private int dataMapWidth;
	private int dataMapHeight;
	
	private final Map<ImageRegion, ViewTrackerDataMap> regionMapsOriginal;
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
		
		this.regionMapsOriginal = new HashMap<>();
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
	
	Map<ImageRegion, BufferedImage> updateDataMaps(long timeStart, long timeStop, double downsampleMin, double downsampleMax, Feature feature, ColorMap colorMap) {
		if (timeStart == -1 || timeStop == -1 || downsampleMin == -1 || downsampleMax == -1 || colorMap == null)
			return null;

		var startTime = System.currentTimeMillis();
		regionMaps.clear();
		regionMapsOriginal.clear();
		for (int z = 0; z < nZSlices; z++) {
			for (int t = 0; t < nTimepoints; t++) {
				var relevantFrames = getRelevantFrames(timeStart, timeStop, downsampleMin, downsampleMax, z, t);
				ImageRegion region = ImageRegion.createInstance(0, 0, fullWidth, fullHeight, z, t);
				var dataMap = new ViewTrackerDataMap(region, feature, relevantFrames, downsample, dataMapWidth, dataMapHeight);
				regionMapsOriginal.put(region, dataMap);
				regionMaps.put(region, dataMap.getBufferedImage(colorMap));
			}
		}
		//TODO: remove next line
		logger.info("Processing time for populateRegionMap(): " + (System.currentTimeMillis()-startTime));
		return regionMaps;
	}
	
	Number getMaxValue(double z, double t) {
		for (ImageRegion map: regionMapsOriginal.keySet()) {
			if (map.getZ() == z && map.getT() == t)
				return regionMapsOriginal.get(map).getMaxValue();
		}
		return null;
	}
	
	
	/**
	 * Return an array of frames, in reverse chronological order (i.e. last frame recorded will be first in the array)
	 * 
	 * @param timeStart
	 * @param timeStop
	 * @param downsampleMin
	 * @param downsampleMax
	 * @param z
	 * @param t
	 * @return reverse-ordered relevant frames
	 */
	private ViewRecordingFrame[] getRelevantFrames(long timeStart, long timeStop, double downsampleMin, double downsampleMax, int z, int t) {
		int frameStartIndex = tracker.getFrameIndexForTime(timeStart);
		int frameStopIndex = tracker.getFrameIndexForTime(timeStop);
		
		List<ViewRecordingFrame> relevantFrames = new ArrayList<>();
		ViewRecordingFrame previousFrame = null;
		for (int nFrame = frameStopIndex; nFrame >= frameStartIndex; nFrame--) {
			var frame = tracker.getFrame(nFrame);
			if (frame.getZ() != z || frame.getT() != t)
				continue;
			if (frame.getDownsampleFactor() < downsampleMin || frame.getDownsampleFactor() > downsampleMax)
				continue;
			if (frame.sameImageBounds(previousFrame) && nFrame != frameStartIndex)
				continue;
		
			relevantFrames.add(frame);
			previousFrame = frame;
		}
		
		return relevantFrames.toArray(ViewRecordingFrame[]::new);
	}
	
	Number getValueFromOriginalLocation(int x, int y, int z, int t) {
		Optional<ImageRegion> imageRegion = regionMaps.keySet().stream()
				.filter(region -> region.contains(x, y, z, t))
				.findFirst();
		
		if (imageRegion.isPresent())
			return regionMapsOriginal.get(imageRegion.get()).getCalculatedValue(x, y);
		return null;
	}
}