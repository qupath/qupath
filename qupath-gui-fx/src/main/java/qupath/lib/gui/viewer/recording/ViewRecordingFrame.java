/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Point2D;

import qupath.lib.gui.viewer.tools.PathTool;

/**
 * Interface representing a snapshot of viewer recording information.
 * <p>
 * Warning! This interface is subject to change in the future. It is currently too AWT/Swing-focused 
 * for historical reasons and should be updated for better use with JavaFX.
 *
 * @author Pete Bankhead
 *
 */
interface ViewRecordingFrame {

	long getTimestamp();

	/**
	 * Returns the x- and y- align rectangle of the visible region.
	 * The actual visible region is the returned rectangle rotated by theta.
	 * @param theta
	 * @return
	 */
	Rectangle getImageBounds();
	
	double getDownFactor();
	
	Point2D getFrameCentre();

	Point2D getCursorPosition();

	boolean hasCursorPosition();
	
	PathTool getActiveTool();
	
	boolean hasActiveTool();

	Point2D getEyePosition();

	/**
	 * Query if the eye seems to be fixated, if such information is available.
	 * @return Boolean.TRUE if the eye tracking information suggests the eye is fixated, Boolean.FALSE if not, and null if no information is available.
	 */
	Boolean isEyeFixated();

	boolean hasEyePosition();
	
	boolean hasZAndT();
	
	int getZ();
	
	int getT();

	Shape getImageShape();

	Dimension getSize();

	double getRotation();

	Shape getShape();

}