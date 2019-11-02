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

package qupath.lib.gui.viewer.recording;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.animation.Animation.Status;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.stage.Window;
import javafx.util.Duration;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Controller for playback for view tracking data.
 * 
 * @author Pete Bankhead
 *
 */
class ViewTrackerPlayback {
	
	final private static Logger logger = LoggerFactory.getLogger(ViewTrackerPlayback.class);
	
	private QuPathViewer viewer;
	private ViewTracker tracker;
	
	private BooleanProperty playing;
	
	private Timeline timeline;
	private long startTimestamp;
	
	public ViewTrackerPlayback(final ViewTracker tracker, final QuPathViewer viewer) {
		this.tracker = tracker;
		this.viewer = viewer;
		this.playing = new SimpleBooleanProperty(false);
		
		timeline = new Timeline(
				new KeyFrame(
						Duration.ZERO,
						new EventHandler<ActionEvent>() {
							@Override
							public void handle(ActionEvent actionEvent) {
								handleUpdate();
							}
						}
						),
				new KeyFrame(
						Duration.millis(50)
						)
				);
		timeline.setCycleCount(Timeline.INDEFINITE);

		
		
		
		this.playing.addListener((v, o, n) -> {
			if (n)
				doStartPlayback();
			else
				doStopPlayback();
		});
		
	}

	/**
	 * Returns true if playback is started, returns false otherwise (i.e. the tracker is empty, so got nothing to play back)
	 * 
	 * @return
	 */
	boolean doStartPlayback() {
		if (tracker.isEmpty())
			return false;
		startTimestamp = System.currentTimeMillis();
		if (timeline.getStatus() == Status.RUNNING)
			timeline.playFromStart();
		else
			timeline.play();
		playing.set(true);
		return true;
	}
	
	
	static void resizeViewer(QuPathViewer viewer, Dimension newSize) {
		if (viewer.getSize().equals(newSize))
			return;
		int dw = newSize.width - viewer.getWidth();
		int dh = newSize.height - viewer.getHeight();
//		System.out.println("DW: " + dw);
		Window window = viewer.getView().getScene().getWindow();
		window.setWidth(window.getWidth() + dw);
		window.setHeight(window.getHeight() + dh);
	}
	

	public boolean isPlaying() {
		return timeline.getStatus() == Status.RUNNING;
	}

	void doStopPlayback() {
		timeline.stop();
		playing.set(false);
	}
		
	
	void handleUpdate() {
		if (tracker.isEmpty())
			return;
		
		long timestamp = System.currentTimeMillis();
		
		long timestampOfFirstFrame = tracker.nFrames() > 0 ? tracker.getFrame(0).getTimestamp() : 0;
		
		ViewRecordingFrame frame = tracker.getFrameForTime((timestamp - startTimestamp) + timestampOfFirstFrame);
		boolean requestStop;
		if (frame == null)
			requestStop = true;
		else {
			setViewerForFrame(viewer, frame);
			requestStop = tracker.isLastFrame(frame);
		}
		
//		if (!requestStop) {
//			// If we are controlling the cursor, check for the spacebar being pressed
//			viewer.requestFocus();
//			requestStop = viewer.isSpaceDown();
//		}
		
		// Stop playback, if required
		if (requestStop) {
			timeline.stop();
			playing.set(false);
		}
	}
	
	
	
	public void setPlaying(final boolean playing) {
		if (isPlaying() == playing)
			return;
		this.playing.set(playing);
//		if (playing)
//			doStartPlayback();
//		else
//			doStopPlayback();
	}
	
	
	
	public static void setViewerForFrame(final QuPathViewer viewer, final ViewRecordingFrame frame) {
		
		// Resize the viewer (if necessary)
		resizeViewer(viewer, frame.getSize());
		
		Rectangle imageBounds = frame.getImageBounds();
		Dimension canvasSize = frame.getSize();
		// Compute & set downsample
		double downsampleX = (double)imageBounds.width / canvasSize.width;
		double downsampleY = (double)imageBounds.height / canvasSize.height;
		double downsample = 0.5 * (downsampleX + downsampleY);
//		if (Math.abs(viewer.getDownsampleFactor() - downsample)/downsample > 0.0001)
			viewer.setDownsampleFactor(downsample);
		// Set location
		viewer.setCenterPixelLocation(imageBounds.x + imageBounds.width * .5, imageBounds.y + imageBounds.height * .5);
		
		
		if (frame.hasCursorPosition()) {
			Point2D p2d = viewer.imagePointToComponentPoint(frame.getCursorPosition(), null, false);
			Point p = new Point((int)(p2d.getX() + .5), (int)(p2d.getY() + .5));
			
//			// TODO: Check displaying cursor position?
//			SwingUtilities.convertPointToScreen(p, viewer);
//			robot.mouseMove(p.x, p.y);
			
			// Drawing the cursor does not really work, because the viewer overrides it
//			viewer.setCursor(Cursor.getPredefinedCursor(frame.getCursorType()));
		}
		
		if (frame.hasEyePosition()) {
			Point2D p2d = frame.getEyePosition();
			
			ROI point = ROIs.createPointsROI(p2d.getX(), p2d.getY(), ImagePlane.getDefaultPlane());
////			if (Boolean.TRUE.equals(frame.isEyeFixated()))
//				point.setPointRadius(viewer.getDownsampleFactor() * 10); // This was only removed because setPointRadius was removed!
////			else
////				point.setPointRadius(viewer.getDownsampleFactor() * 8);
			PathObject pathObject = PathObjects.createAnnotationObject(point);
			pathObject.setName("Eye tracking position");
			viewer.setSelectedObject(pathObject);
			logger.debug("Eye position: " + p2d);
		}
	}
	
	
	public BooleanProperty playingProperty() {
		return playing;
	}
	
	
	
}