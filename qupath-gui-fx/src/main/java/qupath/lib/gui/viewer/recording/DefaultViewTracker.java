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
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;


/**
 * Default ViewTracker implementation.
 * <p>
 * This tracks only viewer location and cursor position (no eye tracking... because it can't see you). 
 * It does <i>not</i> handle viewer rotations.
 * <p>
 * TODO: Deal with rotations in the tracker.
 * 
 * @author Pete Bankhead
 *
 */
class DefaultViewTracker implements ViewTracker, QuPathViewerListener {
	
	private final static Logger logger = LoggerFactory.getLogger(DefaultViewTracker.class);

	static DecimalFormat df = new DecimalFormat("#.##");
	protected static final String LOG_DELIMITER = "\t";

	transient private QuPathViewer viewer;

	private BooleanProperty recording = new SimpleBooleanProperty(false);

	//	private String path;
	private boolean doCursorTracking = false;

	private long startTime = 0;

	private List<ViewRecordingFrame> frames = new ArrayList<>();
	transient ViewRecordingFrame lastFrame = null;
	private boolean hasEyeTrackingData = false;
	
	private boolean initialized = false;
	
	private MouseMovementHandler mouseHandler = new MouseMovementHandler();


	DefaultViewTracker(final QuPathViewer viewer) {
		this.viewer = viewer;
		this.recording.addListener((v, o, n) -> {
			if (n)
				doStartRecording();
			else
				doStopRecording();
		});
	}
	
	@Override
	public int nFrames() {
		return frames.size();
	}

	@Override
	public ViewRecordingFrame getFrame(int index) {
		return frames.get(index);
	}


	@Override
	public void setRecording(final boolean recording) {
		if (isRecording() == recording)
			return;
		this.recording.set(recording);
	}
	
	
	private void doStartRecording() {
		// Check we aren't recording already
		if (viewer == null || viewer.getServer() == null || initialized)
			return;
		// Look for a server
		ImageServer<BufferedImage> server = viewer.getServer();
		initializeRecording(server.getPath(), server.getWidth(), server.getHeight());
		viewer.addViewerListener(this);
		doCursorTracking = PathPrefs.getTrackCursorPosition();
		if (doCursorTracking) {
			viewer.getView().addEventHandler(MouseEvent.MOUSE_MOVED, mouseHandler);
			viewer.getView().addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseHandler);
		}
		// Assume no eye tracking data until we learn otherwise
		hasEyeTrackingData = false;
		visibleRegionChanged(viewer, viewer.getDisplayedRegionShape());

		logger.info("--------------------------------------\n" + 
					"View tracking for image: " + server.getPath() + "\n" +
					ViewTrackers.getLogHeadings(LOG_DELIMITER, doCursorTracking, supportsEyeTracking()));
		
		initialized = true;
	}


	@Override
	public boolean isRecording() {
		return recording.get();
	}


	protected QuPathViewer getViewer() {
		return viewer;
	}


	private void doStopRecording() {
		initialized = false;
		System.out.println("--------------------------------------");
		viewer.removeViewerListener(this);
		if (doCursorTracking) {
			viewer.getView().removeEventHandler(MouseEvent.MOUSE_MOVED, mouseHandler);
			viewer.getView().removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseHandler);
		}
	}


	@Override
	public void resetRecording() {
		if (isRecording())
			setRecording(false);
		frames.clear();
		lastFrame = null;
		//		initializeRecording(null, 0, 0);
	}

	//	public void initializeRecording(String path, int width, int height) {
	//		this.path = path;
	//		this.width = width;
	//		this.height = height;
	//		lastFrame = null;
	//		initialized = path != null && width > 0 && height > 0;
	//		frames.clear();
	//	}

	// TODO: Confirm if initialization is necessary at all
	@Deprecated
	private void initializeRecording(String path, int width, int height) {
		frames.clear();
		lastFrame = null;
		//		initialized = true;
	}

	//	public boolean isEmpty() {
	//		return !initialized || frames.isEmpty();
	//	}

	@Override
	public boolean isEmpty() {
		return frames.isEmpty();
	}


	protected boolean supportsEyeTracking() {
		return false;
	}

	/**
	 * 
	 * @param timestamp
	 * @param imageBounds
	 * @param canvasSize
	 * @param cursorPoint
	 * @param eyePoint
	 * @return The frame, if one was added, or null otherwise.
	 */
	protected synchronized ViewRecordingFrame addFrame(final long timestamp, final Shape imageBounds, final Dimension canvasSize, final Point2D cursorPoint, final Point2D eyePoint, final Boolean isFixated) {
		if (!isRecording()) {
			logger.error("Recording has not started!  Frame request will be ignored.");
		}
		if (lastFrame == null)
			startTime = timestamp;
		else if (lastFrame.getTimestamp() > timestamp) { // Shouldn't happen... but disregard out-of-order processing
			logger.warn("View tracking frame disregarded with timestamp " + df.format((timestamp - startTime)/1000) + " seconds");
			return null;
		}
		DefaultViewRecordingFrame frame = new DefaultViewRecordingFrame(timestamp-startTime, imageBounds, canvasSize, cursorPoint, eyePoint, isFixated);
		appendFrame(frame);
		// Log the frame
		logger.info(ViewTrackers.toLogString(lastFrame, LOG_DELIMITER, doCursorTracking, supportsEyeTracking()));
		return frame;
	}


	@Override
	public synchronized void appendFrame(final ViewRecordingFrame frame) {
		if (lastFrame != null && lastFrame.getTimestamp() > frame.getTimestamp())
			throw new RuntimeException("Unable to append frame - frame timestamp is earlier than the current timestamp");
		frames.add(frame);
		lastFrame = frame;
		hasEyeTrackingData = hasEyeTrackingData || frame.hasEyePosition();
	}



	@Override
	public boolean hasEyeTrackingData() {
		return hasEyeTrackingData;
	}

	
	/**
	 * Return an unmodifiable list of all the frames stored by this view tracker.
	 * @return
	 */
	public List<ViewRecordingFrame> getFrames() {
		return Collections.unmodifiableList(frames);
	}



	@Override
	public String getSummaryString() {
		StringBuffer sb = new StringBuffer();
		String delimiter = PathPrefs.getTableDelimiter();
		sb.append(ViewTrackers.getLogHeadings(delimiter, doCursorTracking, hasEyeTrackingData));
		sb.append("\n");
		for (ViewRecordingFrame frame : frames) {
			sb.append(ViewTrackers.toLogString(frame, delimiter, doCursorTracking, hasEyeTrackingData));
			sb.append("\n");
		}
		return sb.toString();
	}


	//	public String getSummaryString() {
	//		StringBuffer sb = new StringBuffer();
	////		sb.append("Path:\t").append(path).append("\n");
	////		sb.append("Width:\t").append(width).append("\n");
	////		sb.append("Height:\t").append(height).append("\n");
	//		sb.append("Timestamp (ms)\tx\ty\tw\th\tImage width\tImage height\tCanvas width\tCanvas height");
	//		if (doCursorTracking)
	//			sb.append("\tCursor x\tCursor y");
	//		if (hasEyeTrackingData())
	//			sb.append("\tEye x\tEye y\tIs fixated");
	//		sb.append("\n");
	//		for (ViewRecordingFrame frame : frames) {
	//			Rectangle bounds = frame.getImageBounds();
	//			Dimension canvasSize = frame.getSize();
	//			sb.append(String.format("%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d", frame.getTimestamp(), bounds.x, bounds.y, bounds.width, bounds.height, width, height, canvasSize.width, canvasSize.height));
	//			if (doCursorTracking) {
	//				if (frame.hasCursorPosition()) {
	//					Point2D cursorPosition = frame.getCursorPosition();
	//					sb.append("\t");
	//					sb.append(df.format(cursorPosition.getX()));
	//					sb.append("\t");
	//					sb.append(df.format(cursorPosition.getY()));
	//				} else {
	//					sb.append("\t\t");				
	//				}
	//			}
	//			if (hasEyeTrackingData()) {
	//				if (frame.hasEyePosition()) {
	//					Point2D eyePosition = frame.getEyePosition();
	//					sb.append("\t");
	//					sb.append(df.format(eyePosition.getX()));
	//					sb.append("\t");
	//					sb.append(df.format(eyePosition.getY()));
	//					sb.append("\t");
	//					if (frame.isEyeFixated() == null)
	//						sb.append("\t");
	//					else
	//						sb.append(frame.isEyeFixated().toString());
	//				} else {
	//					sb.append("\t\t\t");				
	//				}				
	//			}
	//			sb.append("\n");
	//		}
	//		return sb.toString();
	//	}

	//	public int getImageWidth() {
	//		return width;
	//	}
	//
	//	public int getImageHeight() {
	//		return height;
	//	}

	//	public String getOriginalPath() {
	//		return path;
	//	}

	@Override
	public boolean isLastFrame(ViewRecordingFrame frame) {
		return frame == lastFrame;
	}



	/**
	 * Returns the frame visible at time t (in ms)
	 * 
	 * @param t
	 * @return
	 */
	@Override
	public ViewRecordingFrame getFrameForTime(long t) {
		// TODO: Improve efficiency of frame identification code!
		for (int i = frames.size()-1; i >= 0; i--) {
			if (frames.get(i).getTimestamp() < t) {
				return frames.get(i);
			}
		}
		return null;
	}

	@Override
	public void visibleRegionChanged(final QuPathViewer viewer, final Shape shape) {
		// If the image has been updated, then it could be because a change of view that we want to track
		if (lastFrame != null && lastFrame.getImageShape().equals(shape) && lastFrame.getSize().equals(viewer.getSize()))
			return;

		addFrame(System.currentTimeMillis(), shape, viewer.getSize(), getMousePointIfRequired(), null, null);
	}


	protected Point2D getMousePointIfRequired() {
		// Get the mouse position, if required
		Point2D p = null;
		if (doCursorTracking) {
			//			p = MouseInfo.getPointerInfo().getLocation(); // We don't want cursor positions outside the component, because they may be confusing when converted to image coordinates
			p = viewer.getMousePosition();
			if (p != null)
				return viewer.componentPointToImagePoint(p, p, false);
		}
		return null;
	}


	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}


	@Override
	public void viewerClosed(QuPathViewer viewer) {
		if (this.viewer == viewer)
			setRecording(false);
	}


	@Override
	public BooleanProperty recordingProperty() {
		return recording;
	}
	
	
	
	class MouseMovementHandler implements EventHandler<MouseEvent> {

		@Override
		public void handle(MouseEvent event) {
			Point2D p = viewer.componentPointToImagePoint(event.getX(), event.getY(), null, false);
			addFrame(System.currentTimeMillis(), viewer.getDisplayedRegionShape(), viewer.getSize(), p, null, null);
		}		
		
	}



	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {}
	

}