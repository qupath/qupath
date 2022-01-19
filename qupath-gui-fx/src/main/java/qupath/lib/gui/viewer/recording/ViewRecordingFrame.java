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
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;

import qupath.lib.gui.viewer.tools.PathTool;

/**
 * Data relating to a single recording frame.
 * @author Pete Bankhead
 * @author Melvin Gelbard
 * 
 */
class ViewRecordingFrame {
	
	private static final DecimalFormat df = new DecimalFormat("#.##");
	
	private long timestamp;	// Time in ms since start of the recording
	private Shape region; // Store vertices
	private Dimension canvasSize;
	private double downFactor;
	private PathTool activeTool;
	private Point2D cursorPosition, eyePosition;
	private Boolean isFixated;
	private double rotation;
	private Integer z;
	private Integer t;
	
	ViewRecordingFrame(long timestamp, Shape region, Dimension canvasSize) {
		this(timestamp, region, canvasSize, 1.0, null);
	}
	
	ViewRecordingFrame(long timestamp, Shape region, Dimension canvasSize, double downFactor, double rotation, int z, int t) {
		this(timestamp, region, canvasSize, downFactor, rotation, null, null, null, false, z, t);
	}
	
	ViewRecordingFrame(long timestamp, Shape region, Dimension canvasSize, double downFactor, Point2D cursorPosition) {
		this(timestamp, region, canvasSize, 0, downFactor, cursorPosition, null, null, false, -1, -1);
	}
	
	ViewRecordingFrame(long timestamp, Shape region, Dimension canvasSize, double downFactor, double rotation, Point2D cursorPosition, PathTool activeTool, Point2D eyePosition, Boolean isFixated, Integer z, Integer t) {
		this.timestamp = timestamp;
		this.region = region;
		this.canvasSize = canvasSize;
		this.downFactor = downFactor;
		this.rotation = rotation;
		this.cursorPosition = cursorPosition;
		this.activeTool = activeTool;
		this.eyePosition = eyePosition;
		this.isFixated = isFixated;
		this.z = z;
		this.t = t;
	}
	
	@Override
	public String toString() {
		String s = String.format("Timestamp: %d, Shape: %s, Canvas size: %d, %d, Rotation: %f", timestamp, region.toString(), canvasSize.width, canvasSize.height, rotation);
		if (cursorPosition != null)
			s += ", Cursor position: " + df.format(cursorPosition.getX()) + ", " + df.format(cursorPosition.getY());
		if (eyePosition != null)
			s += ", Eye position: " + df.format(eyePosition.getX()) + ", " + df.format(eyePosition.getY()) + ", Is fixated: " + isFixated;
		if (z != null || t != null)
			s += ", Z-Slice: " + df.format(z) + ", " + ", Timepoint: " + df.format(t);
		return s;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public Shape getShape() {
		return region;
	}
	
	/**
	 * Return the x- and y- align rectangle of the visible region.
	 * The actual visible region is the returned rectangle rotated by theta.
	 * @return
	 */
	public Rectangle getImageBounds() {
		Point2D center = getFrameCentre();
		AffineTransform at = new AffineTransform();
		at.rotate(rotation, center.getX(), center.getY());
		Shape rec = at.createTransformedShape(region);
		return rec.getBounds();
	}
	
	public Point2D getFrameCentre() {
		// region.getBounds() gives the bounding box, which does not account for rotation
		
		PathIterator it = region.getPathIterator(null);
		double[] segment = new double[6];
		Point2D[] coords = new Point2D[4];
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = 0;
		double maxY = 0;
		for (int i = 0; i < coords.length; i++) {
			if (it.isDone())
				return null;
	        it.currentSegment(segment);
	        coords[i] = new Point2D.Double(segment[0], segment[1]);
	        minX = segment[0] < minX ? minX = segment[0] : minX;
	        maxX = segment[0] > maxX ? maxX = segment[0] : maxX;
	        minY = segment[1] < minY ? minY = segment[1] : minY;
	        maxY = segment[1] > maxY ? maxY = segment[1] : maxY;
	        
	        it.next();
		}
		return new Point2D.Double(minX + 0.5*(maxX-minX), minY + 0.5*(maxY-minY));
		
	}
	
	public Point2D getCursorPosition() {
		return cursorPosition == null ? null : new Point2D.Double(cursorPosition.getX(), cursorPosition.getY());
	}

	public boolean hasCursorPosition() {
		return cursorPosition != null;
	}
	
	public Point2D getEyePosition() {
		return eyePosition == null ? null : new Point2D.Double(eyePosition.getX(), eyePosition.getY());
	}

	/**
	 * Query if the eye seems to be fixated, if such information is available.
	 * @return Boolean.TRUE if the eye tracking information suggests the eye is fixated, Boolean.FALSE if not, and null if no information is available.
	 */
	public Boolean isEyeFixated() {
		return isFixated;
	}

	public boolean hasEyePosition() {
		return eyePosition != null;
	}
	
	public Shape getImageShape() {
		if (region instanceof Rectangle2D)
			return (Shape)((Rectangle2D)region).clone();
		return new Path2D.Double(region);
	}
	
	public Dimension getSize() {
		return canvasSize;
	}

	public int getZ() {
		return z;
	}

	public int getT() {
		return t;
	}
	
	public boolean hasZOrT() {
		return z != null || t != null;
	}

	public double getRotation() {
		return rotation;
	}

	public double getDownsampleFactor() {
		return downFactor;
	}

	public PathTool getActiveTool() {
		return activeTool;
	}

	public boolean hasActiveTool() {
		return activeTool != null;
	}
	
	/**
	 * Return whether this ViewRecordingFrame has the same image bounds as the one supplied.
	 * @param frame
	 * @return true if same bounds, false otherwise
	 */
	boolean sameImageBounds(ViewRecordingFrame frame) {
		if (frame == null)
			return false;
		
		var b1 = getImageBounds();
		var b2 = frame.getImageBounds();
		
		if (b1.getX() != b2.getX() ||
				b1.getY() != b2.getY() ||
				b1.getWidth() != b2.getWidth() ||
				b1.getHeight() != b2.getHeight())
			return false;
		
		if (getRotation() != frame.getRotation())
			return false;
		return true;
	}
}