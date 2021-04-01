/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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
 *
 */
public interface ViewTracker {

	BooleanProperty recordingProperty();
	
	void setRecording(boolean recording);
	
	boolean isRecording();	
	
	void resetRecording();
	
	boolean hasCursorTrackingData();
	
	boolean hasActiveToolTrackingData();
	
	boolean hasEyeTrackingData();
	
	boolean hasZAndT();
	
	int getFrameIndexForTime(long t);
	
	ViewRecordingFrame getFrameForTime(long timestamp);

	boolean isLastFrame(ViewRecordingFrame frame);
	
	void appendFrame(ViewRecordingFrame frame);
	
	int nFrames();

	ViewRecordingFrame getFrame(int index);

	boolean isEmpty();
	
	String getName();
	
	void setName(String name);

	File getFile();

	void setFile(File file);
	
	long getStartTime();
	
	long getLastTime();

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
	 * Set optional parameter for the tracker.
	 * This method is useful when importing a tracker that 
	 * was not recorded during this session.
	 * 
	 * @param ZandT
	 * @param cursorTracking
	 * @param activeToolTracking
	 * @param eyeTracking
	 */
	void includeOptionals(boolean ZandT, boolean cursorTracking, boolean activeToolTracking, boolean eyeTracking);
}