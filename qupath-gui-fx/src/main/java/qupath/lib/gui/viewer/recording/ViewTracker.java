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

import java.awt.Dimension;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;


/**
 * Default ViewTracker implementation.
 * <p>
 * This tracks only viewer location and cursor position (no eye tracking... because it can't see you). 
 * It does <i>not</i> handle viewer rotations.
 * <p>
 * 
 * @author Pete Bankhead
 * @author Melvin Gelbard
 */
public class ViewTracker implements QuPathViewerListener {
	// TODO: Set a max recording time limit?
	
	private static final Logger logger = LoggerFactory.getLogger(ViewTracker.class);

	protected static final DecimalFormat df = new DecimalFormat("#.##");
	protected static final String LOG_DELIMITER = "\t";

	private transient QuPathGUI qupath;
	private transient QuPathViewer viewer;
	
	private boolean hasZAndT;
	
	private BooleanProperty doCursorTracking = new SimpleBooleanProperty(true);
	private BooleanProperty doActiveToolTracking = new SimpleBooleanProperty();
	private BooleanProperty doEyeTracking = new SimpleBooleanProperty();

	private BooleanProperty recording = new SimpleBooleanProperty(false);

	private File recordingDirectory;
	private File recordingFile = null;
	private OutputStreamWriter fw = null;
	private StringProperty nameProperty = new SimpleStringProperty(null);

	private long startTime = -1;

	private List<ViewRecordingFrame> frames = new ArrayList<>();
	private transient ViewRecordingFrame lastFrame = null;
	private double rotation = 0;
	
	private boolean initialized = false;
	
	private MouseMovementHandler mouseHandler = new MouseMovementHandler();

	ViewTracker(final QuPathGUI qupath) {
		this.qupath = qupath;
		viewer = qupath != null ? qupath.getViewer() : null;
		nameProperty.addListener((v, o, n) -> renameFile(n));
		recording.addListener((v, o, n) -> {
			if (n)
				doStartRecording();
			else
				doStopRecording();
		});
	}
	
	private void renameFile(String newName) {
		if (recording.get() || recordingFile == null || GeneralTools.getNameWithoutExtension(recordingFile).equals(newName))
			return;
		try {
			Files.move(recordingFile.toPath(), recordingFile.toPath().resolveSibling(newName + GeneralTools.getExtension(new File(newName)).orElse(".tsv")));
			recordingFile = recordingFile.toPath().resolveSibling(newName + GeneralTools.getExtension(new File(newName)).orElse(".tsv")).toFile();
		} catch (IOException ex) {
			Dialogs.showErrorMessage("Error", "Could not rename recording  '" + newName + "': " + ex.getLocalizedMessage());
		}
	}

	/**
	 * Return the number of recorded frames.
	 * @return number of recorded frames
	 */
	public int nFrames() {
		return frames.size();
	}

	/**
	 * Return the frame at the specified {@code index}.
	 * <p>
	 * N.B. The index is <b>not</b> equivalent to the frame's timestamp.
	 * @param index
	 * @return
	 * @see #getFrameForTime(long)
	 */
	public ViewRecordingFrame getFrame(int index) {
		return frames.get(index);
	}

	/**
	 * Set the recording property of this {@link ViewTracker}.
	 * @param recording
	 */
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
		initializeRecording();
		viewer.addViewerListener(this);
		if (doCursorTracking.get()) {
			viewer.getView().addEventHandler(MouseEvent.MOUSE_MOVED, mouseHandler);
			viewer.getView().addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseHandler);
		}
		// Assume no eye tracking data until we learn otherwise
		// TODO: viewer.getDisplayedRegionShape() returns a rotated rectangle instead of non-rotated! Change that
		visibleRegionChanged(viewer, viewer.getDisplayedRegionShape());

		logger.debug("--------------------------------------\n" + 
					"View tracking for image: " + server.getPath() + "\n" +
					ViewTrackerTools.getSummaryHeadings(LOG_DELIMITER, doCursorTracking.get(), doActiveToolTracking.get(), doEyeTracking.get(), hasZAndT()));
	}

	private void doStopRecording() {
		// Add a last frame to the list
		ViewRecordingFrame frame = new ViewRecordingFrame(System.currentTimeMillis()-startTime, viewer.getDisplayedRegionShape(), ViewTrackerTools.getSize(viewer), viewer.getDownsampleFactor(), viewer.getRotation(), new Point2D.Double(-1, -1), getActiveToolIfRequired(), getEyePointIfRequired(), getEyeFixatedIfRequired(), getCurrentZ(), getCurrentT());
		appendFrame(frame);
		
		logger.debug("--------------------------------------");
		viewer.removeViewerListener(this);
		if (doCursorTracking.get()) {
			viewer.getView().removeEventHandler(MouseEvent.MOUSE_MOVED, mouseHandler);
			viewer.getView().removeEventHandler(MouseEvent.MOUSE_DRAGGED, mouseHandler);
		}
		if (fw != null) {
			try {
				fw.flush();
				fw.close();
				fw = null;
			} catch (IOException e) {
				logger.error("Error while closing back-up file: ", e);
			}
		}
	}

	private boolean setRecordingDirectory() {
		Path entryPath = qupath.getProject().getEntry(viewer.getImageData()).getEntryPath();
		if (entryPath != null && entryPath.toFile().exists()) {
			File recordingDirectory = new File(entryPath.toFile(), "recordings");
			if (recordingDirectory.exists()) {
				this.recordingDirectory = recordingDirectory;
				return true;
			}
		}
		return false;
	}
	
	private void createRecordingDir(Path entryPath) {
		if (entryPath == null) {
			logger.warn("Could not set recording directory.");
			return;			
		}
		File directory = new File(entryPath.toFile(), "recordings");
		directory.mkdir();
		
		recordingDirectory = directory;
	}

	/**
	 * Return whether this {@link ViewTracker is currently recording}.
	 * @return whether it is currently recording
	 */
	public boolean isRecording() {
		return recording.get();
	}

	private void initializeRecording() {
		var imageData = viewer.getImageData();
		hasZAndT = imageData.getServer().getMetadata().getSizeZ() != 1 || imageData.getServer().getMetadata().getSizeT() != 1;
		frames.clear();
		startTime = System.currentTimeMillis();
		lastFrame = null;
		
		// Create 'recordings' directory if it doesn't exist
		if (!setRecordingDirectory())
			createRecordingDir(qupath.getProject().getEntry(imageData).getEntryPath());
		
		recordingFile = recordingFile != null ? recordingFile : new File(recordingDirectory, nameProperty.get() + ".tsv");
		try {
			fw = new OutputStreamWriter(new FileOutputStream(recordingFile), StandardCharsets.UTF_8);
			fw.write(ViewTrackerTools.getSummaryHeadings(LOG_DELIMITER, doCursorTracking.get(), doActiveToolTracking.get(), doEyeTracking.get(), hasZAndT()));
			fw.write(System.lineSeparator());
		} catch (IOException e) {
			logger.error("Could not create back-up file. Recording will not be saved.", e.getLocalizedMessage());
		}
		
		initialized = true;
	}

	/**
	 * Return whether the collection of recorded frames is empty.
	 * @return whether the collection is empty
	 */
	public boolean isEmpty() {
		return frames.isEmpty();
	}

	/**
	 * Create and add a frame to the list of frames.
	 * 
	 * @param timestamp
	 * @param imageBounds
	 * @param canvasSize
	 * @param downFactor
	 * @param cursorPoint
	 * @param activeTool 
	 * @param eyePoint
	 * @param isFixated 
	 * @param rotation 
	 * @param z 
	 * @param t 
	 * @return The frame, if one was added, or null otherwise.
	 */
	private synchronized ViewRecordingFrame addFrame(final long timestamp, final Shape imageBounds, final Dimension canvasSize, final double downFactor, final Point2D cursorPoint, final PathTool activeTool, final Point2D eyePoint, final Boolean isFixated, double rotation, int z, int t) {
		if (!isRecording()) {
			logger.error("Recording has not started! Frame request will be ignored.");
			return null;
		}

		if (lastFrame != null && lastFrame.getTimestamp() > timestamp) { // Shouldn't happen... but disregard out-of-order processing
			logger.warn("View tracking frame disregarded with timestamp " + df.format((timestamp - startTime)/1000) + " seconds");
			return null;
		}
	
		ViewRecordingFrame frame = new ViewRecordingFrame(timestamp-startTime, imageBounds, canvasSize, downFactor, rotation, cursorPoint, activeTool, eyePoint, isFixated, z, t);
		appendFrame(frame);
		
		// Log the frame
		logger.debug(ViewTrackerTools.getSummary(lastFrame, LOG_DELIMITER, doCursorTracking.get(), doActiveToolTracking.get(), doEyeTracking.get(), hasZAndT));
		return frame;
	}

	/**
	 * Append frame to the collection of frames.
	 * @param frame
	 */
	public synchronized void appendFrame(final ViewRecordingFrame frame) {
		if (lastFrame != null && lastFrame.getTimestamp() > frame.getTimestamp())
			throw new RuntimeException("Unable to append frame - frame timestamp is earlier than the current timestamp");
		frames.add(frame);
		lastFrame = frame;
		
		if (fw != null) {
			try {
				fw.write(ViewTrackerTools.getSummary(frame, "\t", doCursorTracking.get(), doActiveToolTracking.get(), doEyeTracking.get(), hasZAndT));
				fw.write(System.lineSeparator());
			} catch (IOException e) {
				logger.error("Could not write frame to file. Frame will be ignored: ", e);
			}			
		}
	}
	
	private int getCurrentZ() {
		return viewer.getZPosition();
	}
	
	private int getCurrentT() {
		return viewer.getTPosition();
	}
	
	/**
	 * Return an unmodifiable list of all the frames stored by this view tracker.
	 * @return
	 */
	protected List<ViewRecordingFrame> getFrames() {
		return Collections.unmodifiableList(frames);
	}

	/**
	 * Return whether the specified {@code frame} is the last one recorded.
	 * @param frame
	 * @return whether it is the last frame
	 */
	public boolean isLastFrame(ViewRecordingFrame frame) {
		return frame == lastFrame;
	}
	
	/**
	 * Return the index of the frame visible at the specified {@code timestamp} (in ms).
	 * If the {@code timestamp} is lower than the first recorded frame, 0 will be returned. 
	 * @param timestamp
	 * @return index of frame
	 */
	public int getFrameIndexForTime(long timestamp) {
		ArrayList<Long> timestampList = frames.stream().map(e -> e.getTimestamp()).collect(Collectors.toCollection(ArrayList::new));
	    int index = Collections.binarySearch(timestampList, timestamp);
	    if (index < 0)
	        index = ~index-1;
	    return index >= 0 ? index : 0;
	}
	
	/**
	 * Return the frame visible at the specified {@code timestamp} (in ms). 
	 * @param timestamp
	 * @return the corresponding frame
	 */
	public ViewRecordingFrame getFrameForTime(long timestamp) {
		int index = getFrameIndexForTime(timestamp);
	    return frames.get(index);
	}

	@Override
	public void visibleRegionChanged(final QuPathViewer viewer, final Shape shape) {
		// If the image has been updated, then it could be because a change of view that we want to track
		if (lastFrame != null && lastFrame.getImageShape().equals(shape) && lastFrame.getSize().equals(ViewTrackerTools.getSize(viewer)))
			return;
		
		rotation = viewer.getRotation() != rotation ? viewer.getRotation() : rotation;

		addFrame(System.currentTimeMillis(), shape, ViewTrackerTools.getSize(viewer), viewer.getDownsampleFactor(), getMousePointIfRequired(), getActiveToolIfRequired(), null, null, rotation, getCurrentZ(), getCurrentT());
	}

	protected Point2D getMousePointIfRequired() {
		// Get the mouse position, if required
		Point2D p = null;
		if (doCursorTracking.get()) {
			// p = MouseInfo.getPointerInfo().getLocation(); // We don't want cursor positions outside the component, because they may be confusing when converted to image coordinates
			p = viewer.getMousePosition();
			if (p != null)
				return viewer.componentPointToImagePoint(p, p, false);
		}
		
		// To avoid NPE, return a dummy Point2D with -1, -1 coordinates
		return new Point2D.Double(-1, -1);
	}
	
	protected PathTool getActiveToolIfRequired() {
		// Get the current active tool, if required
		return doActiveToolTracking.get() ? viewer.getActiveTool() : null;
	}
	
	protected Point2D getEyePointIfRequired() {
		return null;
	}
	
	protected Boolean getEyeFixatedIfRequired() {
		return null;
	}

	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

	@Override
	public void viewerClosed(QuPathViewer viewer) {
		if (this.viewer == viewer)
			setRecording(false);
	}

	/**
	 * Return the recording property of this {@link ViewTracker}.
	 * @return recording property
	 */
	public BooleanProperty recordingProperty() {
		return recording;
	}

	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {}

	/**
	 * Return the file associated with this {@link ViewTracker}. This can return 
	 * {@code null} if it is not local or if the recording cannot be saved.
	 * @return file
	 */
	public File getFile() {
		return recordingFile;
	}
	
	/**
	 * Set the file for this {@link ViewTracker}.
	 * @param file
	 */
	public void setFile(File file) {
		this.recordingFile = file;
	}

	/**
	 * Return the name of this {@link ViewTracker}.
	 * @return name
	 */
	public String getName() {
		return nameProperty.get();
	}

	/**
	 * Set the name of this {@link ViewTracker}.
	 * @param name
	 */
	public void setName(String name) {
		nameProperty.set(name);
	}
	
	/**
	 * Return the String property of this {@link ViewTracker}.
	 * @return nameProperty
	 */
	public StringProperty nameProperty() {
		return nameProperty;
	}
	
	/**
	 * Return the time associated with the first recorded frame.
	 * @return start time
	 */
	public long getStartTime() {
		if (startTime == -1 && frames.size() > 0)
			return frames.get(0).getTimestamp();
		return startTime;
	}
	
	/**
	 * Return the time associated with the last recorded frame.
	 * @return last time
	 */
	public long getLastTime() {
		return lastFrame.getTimestamp() + getStartTime();
	}

	/**
	 * Return whether this {@link ViewTracker} has Z and T information.
	 * @return whether it has Z and T
	 */
	public boolean hasZAndT() {
		return hasZAndT;
	}
	
	/**
	 * Return whether this {@link ViewTracker} tracks the cursor.
	 * @return whether the cursor is tracked
	 */
	public boolean hasCursorTrackingData() {
		return doCursorTracking.get();
	}
	
	/**
	 * Return whether this {@link ViewTracker} tracks the active tool.
	 * @return whether active tool is tracked
	 */
	public boolean hasActiveToolTrackingData() {
		return doActiveToolTracking.get();
	}
	
	/**
	 * Return whether this {@link ViewTracker} tracks the eye.
	 * @return whether the eye is tracked
	 */
	public boolean hasEyeTrackingData() {
		return doEyeTracking.get();
	}
	
	/**
	 * Return the cursorTracking property of this tracker.
	 * @return doCursorTracking
	 */
	public BooleanProperty cursorTrackingProperty() {
		return doCursorTracking;
	}
	
	/**
	 * Return the activeTool property of this tracker.
	 * @return doActiveToolTracking
	 */
	public BooleanProperty activeToolProperty() {
		return doActiveToolTracking;
	}
	
	/**
	 * Return the eyeTracking property of this tracker.
	 * @return doEyeTracking
	 */
	public BooleanProperty eyeTrackingProperty() {
		return doEyeTracking;
	}

	/**
	 * Return the collection of recorded frames.
	 * @return collection of all frames
	 */
	public List<ViewRecordingFrame> getAllFrames() {
		return frames;
	}

	/**
	 * Set optional parameters for this {@link ViewTracker}.
	 * 
	 * @param ZandT
	 * @param cursorTracking
	 * @param activeToolTracking
	 * @param eyeTracking
	 */
	public void setOptionalParameters(boolean ZandT, boolean cursorTracking, boolean activeToolTracking, boolean eyeTracking) {
		hasZAndT = ZandT;
		doCursorTracking.set(cursorTracking);
		doActiveToolTracking.set(activeToolTracking);
		doEyeTracking.set(eyeTracking);
	}
	
	class MouseMovementHandler implements EventHandler<MouseEvent> {
		@Override
		public void handle(MouseEvent event) {
			//TODO: viewer.getDisplayedRegionShape() returns a rotated rectangle (if rotation !=0), however it should not
			Point2D p = viewer.componentPointToImagePoint(event.getX(), event.getY(), null, false);
			addFrame(System.currentTimeMillis(), viewer.getDisplayedRegionShape(), ViewTrackerTools.getSize(viewer), viewer.getDownsampleFactor(), p, getActiveToolIfRequired(), getEyePointIfRequired(), getEyeFixatedIfRequired(), viewer.getRotation(), getCurrentZ(), getCurrentT());
		}		
	}
}