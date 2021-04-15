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

import java.io.File;
import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;

/**
 * General interface for a view tracker.
 * <p>
 * Implementations of this may optionally integrate with eye tracking hardware 
 * to supply extra information.
 * <p>
 * Warning! This interface is subject to change in the future. It is currently too AWT/Swing-focused 
 * for historical reasons and should be updated for better use with JavaFX.
 * 
 * @author Pete Bankhead
 */
interface ViewTracker {

	/**
	 * Return the recording property of this {@link ViewTracker}.
	 * @return recording property
	 */
	BooleanProperty recordingProperty();
	
	/**
	 * Set the recording property of this {@link ViewTracker}.
	 * @param recording
	 */
	void setRecording(boolean recording);
	
	/**
	 * Return whether this {@link ViewTracker is currently recording}.
	 * @return whether it is currently recording
	 */
	boolean isRecording();	
	
	/**
	 * Return whether this {@link ViewTracker} tracks the cursor.
	 * @return whether the cursor is tracked
	 */
	boolean hasCursorTrackingData();
	
	/**
	 * Return whether this {@link ViewTracker} tracks the active tool.
	 * @return whether active tool is tracked
	 */
	boolean hasActiveToolTrackingData();
	
	/**
	 * Return whether this {@link ViewTracker} tracks the eye.
	 * @return whether the eye is tracked
	 */
	boolean hasEyeTrackingData();
	
	/**
	 * Return whether this {@link ViewTracker} has Z and T information.
	 * @return whether it has Z and T
	 */
	boolean hasZAndT();
	
	/**
	 * Return the index of the frame visible at the specified {@code timestamp} (in ms).
	 * If the {@code timestamp} is lower than the first recorded frame, 0 will be returned. 
	 * @param timestamp
	 * @return index of frame
	 */
	int getFrameIndexForTime(long timestamp);
	
	/**
	 * Return the frame visible at the specified {@code timestamp} (in ms). 
	 * @param timestamp
	 * @return the corresponding frame
	 */
	ViewRecordingFrame getFrameForTime(long timestamp);

	/**
	 * Return whether the specified {@code frame} is the last one recorded.
	 * @param frame
	 * @return whether it is the last frame
	 */
	boolean isLastFrame(ViewRecordingFrame frame);
	
	/**
	 * Append frame to the collection of frames.
	 * @param frame
	 */
	void appendFrame(ViewRecordingFrame frame);
	
	/**
	 * Return the number of recorded frames.
	 * @return number of recorded frames
	 */
	int nFrames();

	/**
	 * Return the frame at the specified {@code index}.
	 * <p>
	 * N.B. The index is <b>not</b> equivalent to the frame's timestamp.
	 * @param index
	 * @return
	 * @see #getFrameForTime(long)
	 */
	ViewRecordingFrame getFrame(int index);

	/**
	 * Return whether the collection of recorded frames is empty.
	 * @return whether the collection is empty
	 */
	boolean isEmpty();
	
	/**
	 * Return the name of this {@link ViewTracker}.
	 * @return name
	 */
	String getName();
	
	/**
	 * Set the name of this {@link ViewTracker}.
	 * @param name
	 */
	void setName(String name);
	
	/**
	 * Return the String property of this {@link ViewTracker}.
	 * @return
	 */
	StringProperty nameProperty();

	/**
	 * Return the file associated with this {@link ViewTracker}. This can return 
	 * {@code null} if it is not local or if the recording cannot be saved.
	 * @return file
	 */
	File getFile();

	/**
	 * Set the file for this {@link ViewTracker}.
	 * @param file
	 */
	void setFile(File file);
	
	/**
	 * Return the time associated with the first recorded frame.
	 * @return start time
	 */
	long getStartTime();
	
	/**
	 * Return the time associated with the last recorded frame.
	 * @return last time
	 */
	long getLastTime();

	/**
	 * Return the collection of recorded frames.
	 * @return collection of all frames
	 */
	List<ViewRecordingFrame> getAllFrames();

	/**
	 * Return the cursorTracking property of this tracker.
	 * @return doCursorTracking
	 */
	BooleanProperty cursorTrackingProperty();
	
	/**
	 * Return the activeTool property of this tracker.
	 * @return doActiveToolTracking
	 */
	BooleanProperty activeToolProperty();
	
	/**
	 * Return the eyeTracking property of this tracker.
	 * @return doEyeTracking
	 */
	BooleanProperty eyeTrackingProperty();

	/**
	 * Set optional parameters for this {@link ViewTracker}.
	 * 
	 * @param ZandT
	 * @param cursorTracking
	 * @param activeToolTracking
	 * @param eyeTracking
	 */
	void setOptionalParameters(boolean ZandT, boolean cursorTracking, boolean activeToolTracking, boolean eyeTracking);
}