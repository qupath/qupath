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

	public BooleanProperty recordingProperty();
	
	public void setRecording(boolean recording);
	
	public boolean isRecording();	
	
	public void resetRecording();
	
	public boolean hasEyeTrackingData();
	
	public ViewRecordingFrame getFrameForTime(long timestamp);

	public boolean isLastFrame(ViewRecordingFrame frame);
	
	public void appendFrame(ViewRecordingFrame frame);
	
	public int nFrames();

	public ViewRecordingFrame getFrame(int index);

	public boolean isEmpty();
	
	public String getSummaryString();
	
}