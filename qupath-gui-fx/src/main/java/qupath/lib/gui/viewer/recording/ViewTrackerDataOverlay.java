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
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.gui.tools.MeasurementMapper.ColorMapper;
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
	
	private ColorMapper colorMapper;
	
	private Map<ImageRegion, BufferedImage> regions;

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
	
	void updateDataImage(long timeStart, long timeStop, double downMin, double downMax, boolean timeNormalised, ColorMapper colorMapper) {
		this.timeStart = timeStart;
		this.timeStop = timeStop;
		this.downMin = downMin;
		this.downMax = downMax;
		this.timeNormalized = timeNormalised;
		this.colorMapper = colorMapper;
		
		regions = getImageRegions();
		viewer.repaint();
	}
	
	BufferedImageOverlay getOverlay() {
		return new BufferedImageOverlay(viewer, regions);
	}
	
	private Map<ImageRegion, BufferedImage> getImageRegions() {
		var startTime = System.currentTimeMillis();
		regions.clear();
		for (int z = 0; z < server.nZSlices(); z++) {
			for (int t = 0; t < server.nTimepoints(); t++) {
//				ImageRegion region = ImageRegion.createInstance(0, 0, (int)Math.round(imgWidth*downsample), (int)Math.round(imgHeight*downsample), z, t);
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
		
		BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_BYTE_INDEXED, createColorModel(colorMapper));
		byte[] imgBuffer = ((DataBufferByte)img.getRaster().getDataBuffer()).getData();
		float[] buffer = new float[imgBuffer.length];
		
		if (relevantFrames.length <= 1)
			return img;
		
		// Get max time (for normalization)
		double maxValue;
		if (timeNormalized) {
			maxValue = IntStream.range(0, relevantFrames.length-1)
					.map(index -> index < relevantFrames.length ? (int)(relevantFrames[index+1].getTimestamp() - relevantFrames[index].getTimestamp()) : 0)
					.max()
					.orElseThrow();
		} else {
			maxValue = Arrays.asList(relevantFrames).stream()
					.mapToDouble(e -> e.getDownFactor())
					.max()
					.orElseThrow();
		}
		
		Arrays.fill(buffer, 0);
		for (int nFrame = 0; nFrame < relevantFrames.length; nFrame++) {
			var frame = relevantFrames[nFrame];
			Rectangle downsampledBounds = getDownsampledBounds(frame.getImageBounds(), downsample);
//			if (frame.getRotation() == 0) {
//				Rectangle downsampleBoundsCropped = getCroppedDownsampledBounds(downsampledBounds);
//				for (int x = downsampleBoundsCropped.x; x < downsampleBoundsCropped.x + downsampleBoundsCropped.width; x++) {
//					for (int y = downsampleBoundsCropped.y; y < downsampleBoundsCropped.y + downsampleBoundsCropped.height; y++) {
//						if (x < 0 || x >= imgWidth || y < 0 || y >= imgHeight)
//							continue;
//						if (nFrame < relevantFrames.length-1) {
//							if (timeNormalized)
//								buffer[y*imgWidth + x] += (int)(relevantFrames[nFrame+1].getTimestamp() - frame.getTimestamp());
//							else
//								buffer[y*imgWidth + x] = buffer[y*imgWidth + x] < maxValue-frame.getDownFactor() ? (float)(maxValue-frame.getDownFactor()) : buffer[y*imgWidth + x];
//						}
//						
//					}
//				}
//			} else {
////				Shape shape = frame.getImageShape();
////				PathIterator it = shape.getPathIterator(null);
////				double[] segment = new double[6];
////				int[] xs = new int[4];
////				int[] ys = new int[4];
////				for (int i = 0; i < 4; i++) {
////					if (it.isDone())
////						return null;
////					
////			        it.currentSegment(segment);
////			        xs[i] = (int)Math.round(segment[0]/downsample);
////			        ys[i] = (int)Math.round(segment[1]/downsample);
////			        
////			        it.next();
////				}
////				
////				Polygon poly = new Polygon(xs, ys, 4);
////				for (int x = 0; x < imgWidth; x++) {
////					for (int y = 0; y < imgHeight; y++) {
////						Point2D p = new Point2D.Double(x, y);
////						if (poly.contains(p)) {
////							if (timeNormalized && nFrame < relevantFrames.length-1)
////								buffer[y*imgWidth + x] += (int)(frame.getTimestamp() - relevantFrames[nFrame+1].getTimestamp());
////							else if (!timeNormalized)
////								buffer[y*imgWidth + x] = buffer[y*imgWidth + x] < maxValue-frame.getDownFactor() ? (float)(maxValue-frame.getDownFactor()) : buffer[y*imgWidth + x];
////						}
////					}
////				}
//				
//				// Iterating through x and y, checking if they're included in frame.getImageBounds() when rotated
//				AffineTransform transform = new AffineTransform();
//				Point2D center = frame.getFrameCentre();
//				transform.rotate(-frame.getRotation(), center.getX()/downsample, center.getY()/downsample);
//
//				// TODO: Uncomment these
////				for (int x = 0; x < imgWidth; x++) {
////					for (int y = 0; y < imgHeight; y++) {
////						Point2D[] pts = new Point2D[] {new Point2D.Double(x, y)};
////						transform.transform(pts, 0, pts, 0, 1);
////						if (downsampledBounds.contains(new Point2D.Double(x, y))) {
////							//if (nFrame < relevantFrames.length-1 && new Rectangle(0, 0, imgWidth, imgHeight).contains(pts[0])) {
////							if (nFrame < relevantFrames.length-1) {
////								// Index of the rotated point in the buffer (flatten)
////								int index = (int)(Math.round(pts[0].getY())*imgWidth + Math.round(pts[0].getX()));
////								
////								// Precision errors means that it could potentially go over edges
////								if (index < 0 || index > buffer.length)
////									continue;
////
////								// Update buffer
////								if (timeNormalized)
////									buffer[index] += (int)(frame.getTimestamp() - relevantFrames[nFrame+1].getTimestamp());
////								else
////									buffer[index] = buffer[index] < maxValue-frame.getDownFactor() ? (float)(maxValue-frame.getDownFactor()) : buffer[index];
////							}
////						}
////					}
////				}
				
				
			/**
			 * Trying something here
			 */
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
	        for (int i = 0; i < buffer.length; i++) {
	        	if (timeNormalized)
	        		imgBuffer[i] += imgNewBuffer[i];
	        	else {
	        		if (imgNewBuffer[i] < buffer[i])
	        			imgBuffer[i] = imgNewBuffer[i];
	        	}
	        }
				
//			}
		}
		// Normalize
//	    for (int i = 0; i < buffer.length; i++)
//	    	imgBuffer[i] = (byte)(buffer[i] / maxValue * 255);
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
	 * @return cropped rectangle
	 */
	private static Rectangle getCroppedBounds(Rectangle bounds, int width, int height) {
		int x = bounds.x < 0 ? 0 : bounds.x < width ? bounds.x : width;
		int y = bounds.y < 0 ? 0 : bounds.y < height ? bounds.y : height;
		int newWidth = bounds.width < 0 ? 0 : (bounds.width + x > width ? width - x : bounds.width);
		int newHeight = bounds.height < 0 ? 0 : (bounds.height + y > height ? height - y : bounds.height);
		return new Rectangle(x, y, newWidth, newHeight);
	}
	
	private static IndexColorModel createColorModel(ColorMapper colorMapper) {
	    int[] rgba = new int[256];
	    for (int i = 0; i < 256; i++) {
	        int rgb = colorMapper.getColor(i, 0, 255);
	        rgba[i] = ColorTools.makeRGBA(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb), i);
	    }
	    return new IndexColorModel(8, 256, rgba, 0, true, 0, DataBuffer.TYPE_BYTE);
	}
}