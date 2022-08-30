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

package qupath.lib.roi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;


/**
 * Helper class for interactively modifying existing ROIs.
 * <p>
 * Modification of ROIs has been made intentionally quite awkward to help ensure they are fairly consistent
 * (i.e. limited mutability), but this can be a bit infuriating when the user wishes to make annotations interactively.
 * <p>
 * Also, currently PathObjects have their ROIs set at creation time - adding further annoyance to the lack of easy ROI editability.
 * <p>
 * RoiEditors provide GUIs with a mechanism for controlled ROI manipulation, when the natural alternative 
 * (creating new ROIs) might be too computationally expensive.
 * By consciously having to make changes via a RoiEditor, it is hoped that programmers will remember inform the PathObjectHierarchy
 * whenever an object has been changed (note that this does not happen automatically - the RoiEditor knows nothing of PathObjects
 * and hierarchies... only ROIs of various flavors).
 * <p>
 * Where any other ROI processing is required, the 'correct' approach is to create a new PathObject as required.
 * 
 * @author Pete Bankhead
 *
 */
public class RoiEditor {
	
	private static final Logger logger = LoggerFactory.getLogger(RoiEditor.class);
	
	private ROI pathROI;
	private MutablePoint activeHandle;
	
	private boolean isTranslating = false;
	private ROI roiTranslateOrigin;
	private MutablePoint pTranslateOrigin;
	private MutablePoint pTranslateCurrent;
	private boolean translateSnapToPixel;
	
	private transient RoiHandleAdjuster<?> adjuster;
	
	// Don't instantiate directly - implementation may change
	private RoiEditor() {};
	
	/**
	 * Create a new RoiEditor.
	 * @return
	 */
	public static RoiEditor createInstance() {
		return new RoiEditor();
	}
	
	/**
	 * Set the active ROI, stopping any ROI translation currently in progress.
	 * @param roi
	 */
	public void setROI(ROI roi) {
		setROI(roi, true);
	}
	
	/**
	 * Set the active ROI, optionally stopping any ROI translation currently in progress.
	 * 
	 * @param roi
	 * @param stopTranslating if true, then any ROI currently being translated will have its translation completed.
	 * 						  Normally this should be true, but it may be false if the new ROI being set is part of the same translation event.
	 */
	public void setROI(ROI roi, boolean stopTranslating) {
//		if (stopTranslating)
//			System.out.println("Stopping translating: " + stopTranslating + " - " + pathROI);

		if (this.pathROI == roi)
			return;
		if (isTranslating() && stopTranslating) {
			finishTranslation();
			activeHandle = null;
		}
		this.pathROI = roi;
		
		if (pathROI instanceof RectangleROI)
			adjuster = new RectangleHandleAdjuster((RectangleROI)pathROI);
		else if (pathROI instanceof EllipseROI)
			adjuster = new EllipseHandleAdjuster((EllipseROI)pathROI);
		else if (pathROI instanceof PolygonROI)
			adjuster = new PolygonHandleAdjuster((PolygonROI)pathROI);
		else if (pathROI instanceof PolylineROI)
			adjuster = new PolylineHandleAdjuster((PolylineROI)pathROI);
		else if (pathROI instanceof LineROI)
			adjuster = new LineHandleAdjuster((LineROI)pathROI);
		else if (pathROI instanceof PointsROI)
			adjuster = new PointsHandleAdjuster((PointsROI)pathROI);
		else {
			adjuster = null;
			return;
		}
	}
	
	
	/**
	 * Returns true if the current ROI is translatable, and at the end of this call the translation has started.
	 * 
	 * @param x
	 * @param y
	 * @param snapToPixel if true, request that translations snap to pixel coordinates
	 * @return
	 */
	public boolean startTranslation(double x, double y, boolean snapToPixel) {
		if (pathROI == null)
			return false;
		pTranslateOrigin = new MutablePoint(x, y);
		pTranslateCurrent = new MutablePoint(x, y);
		isTranslating = true;
		roiTranslateOrigin = pathROI;
		translateSnapToPixel = snapToPixel;
		return true;
	}
	
	
	
	/**
	 * Update a ROI by translation, optionally constraining its movement within a specified boundary.
	 * <p>
	 * Returns the same ROI if translation was not possible, or the translation resulted in no movement,
	 * of if isTranslating() returns false.
	 * Otherwise returns a translated version of the ROI;
	 * 
	 * @param x
	 * @param y
	 * @param constrainRegion
	 * @return
	 */
	public ROI updateTranslation(double x, double y, ImageRegion constrainRegion) {
		if (!isTranslating())
			return pathROI;
		
		double dx = x - pTranslateCurrent.getX();
		double dy = y - pTranslateCurrent.getY();
//		if (snapToPixel) {
//			dx = Math.round(dx);
//			dy = Math.round(dy);
//		}
		
		// Optionally constrain translation to keep within specified bounds (e.g. the image itself)
		Rect constrainBounds = new Rect(constrainRegion.getX(), constrainRegion.getY(), constrainRegion.getWidth(), constrainRegion.getHeight());
		if (constrainBounds != null) {
			Rect bounds = new Rect(pathROI.getBoundsX(), pathROI.getBoundsY(), pathROI.getBoundsWidth(), pathROI.getBoundsHeight());
			if (bounds.getMinX() + dx < constrainBounds.getMinX())
				dx = constrainBounds.getMinX() - bounds.getMinX();
			else if (bounds.getMaxX() + dx >= constrainBounds.getMaxX())
				dx = constrainBounds.getMaxX() - bounds.getMaxX() - 1;
			if (bounds.getMinY() + dy < constrainBounds.getMinY())
				dy = constrainBounds.getMinY() - bounds.getMinY();
			else if (bounds.getMaxY() + dy >= constrainBounds.getMaxY())
				dy = constrainBounds.getMaxY() - bounds.getMaxY() - 1;
		}

		pTranslateCurrent.setLocation(pTranslateCurrent.getX() + dx, pTranslateCurrent.getY() + dy);

		if (dx == 0 && dy == 0)
			return pathROI;

//		pathROI = ((TranslatableROI)pathROI).translate(dx, dy);
		setROI(pathROI.translate(dx, dy), false);
//		// TODO: Fix the inelegance... setting the ROI this way off translating, so we need to turn it back on again...
//		pTranslateStart = new MutablePoint(x, y);
		return pathROI;
	}
	
	
	/**
	 * Notify the editor that translation should end.
	 * 
	 * @return true if there is any displacement between the current and starting translation points, false otherwise.
	 */
	public boolean finishTranslation() {
		boolean displacement = isTranslating && pTranslateOrigin.distanceSq(pTranslateCurrent) > 0;
		if (displacement && translateSnapToPixel && roiTranslateOrigin != null) {
			// If we want to snap to pixel translations, we return to the original and move it all in one go
			double dx = Math.round(pTranslateCurrent.getX() - pTranslateOrigin.getX());
			double dy = Math.round(pTranslateCurrent.getY() - pTranslateOrigin.getY());
			isTranslating = false;
			pTranslateOrigin = null;
			pTranslateCurrent = null;
			setROI(roiTranslateOrigin.translate(dx, dy), false);
		} else {
			isTranslating = false;
			pTranslateOrigin = null;
			pTranslateCurrent = null;
		}
		return displacement;
	}
	
	
	/**
	 * Query if a ROI is currently being translated through this editor.
	 * @return
	 */
	public boolean isTranslating() {
		return isTranslating;
	}
	
	
	
	/**
	 * In the event that the current ROI has been modified elsewhere (which generally it shouldn't be)
	 * request the handles to be recomputed to avoid inconsistency.
	 */
	public void ensureHandlesUpdated() {
		if (adjuster == null)
			return;
		adjuster.ensureHandlesUpdated();
	}
	
	/**
	 * Get all the handles for the current ROI being edited, or an empty list if no handles are available.
	 * @return
	 */
	public List<Point2> getHandles() {
		if (adjuster == null)
			return Collections.emptyList();
		return createPoint2List(adjuster.getHandles());
	}
	
	/**
	 * Returns true if this editor currently has a ROI.
	 * @return
	 */
	public boolean hasROI() {
		return pathROI != null;
	}
	
	/**
	 * Retrieve the ROI currently being edited (may be null).
	 * @return
	 */
	public ROI getROI() {
		return pathROI;
	}
	
	/**
	 * Returns true if a handle is currently active, for example being reposition.
	 * @return
	 * 
	 * @see #getHandles()
	 */
	public boolean hasActiveHandle() {
		return activeHandle != null;
	}
	
	/**
	 * Ensure that no handle is active.
	 */
	public void resetActiveHandle() {
		activeHandle = null;
	}
	
	
	/**
	 * Request an updated ROI with a new handle inserted - useful e.g. when drawing a polygon.
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public ROI requestNewHandle(double x, double y) {
//		System.err.println("Requesting new handle: " + activeHandle);
		if (adjuster == null)
			return pathROI;
//		setROI(adjuster.requestNewHandle(x, y), false);
		
		pathROI = adjuster.requestNewHandle(x, y);

		return pathROI;
	}

	
	/**
	 * Try to grab a ROI handle.
	 * This will fail (return false, with an error logged) if isTranslating() returns true.
	 * 
	 * @param x
	 * @param y
	 * @param maxDist define the distance to search for the nearest handle
	 * @param shiftDown determined from a MouseEvent, this may optionally be used to control how the handle is modified 
	 * 					(e.g. to enforce a square bounding box for a rectangle or ellipse).
	 * @return
	 */
	public boolean grabHandle(double x, double y, double maxDist, boolean shiftDown) {
		if (adjuster == null)
			return false;
		if (isTranslating()) {
			logger.error("Cannot grab handle while ROI is being translated - request will be ignored");
			return false;
		}
		activeHandle = adjuster.grabHandle(x, y, maxDist, shiftDown);
		return activeHandle != null;
	}
	
	
	/**
	 * If a handle has been grabbed, update its displacement.
	 * 
	 * @param x
	 * @param y
	 * @param minDisplacement if &gt; 0, smaller movements will be discarded to avoid unnecessary work.
	 * @param shiftDown determined from a MouseEvent, this may optionally be used to control how the handle is modified 
	 * 					(e.g. to enforce a square bounding box for a rectangle or ellipse).
	 * @return
	 */
	public ROI setActiveHandlePosition(double x, double y, double minDisplacement, boolean shiftDown) {
//		System.err.println("Set position: " + activeHandle);
		// Check if we have an active handle, or have moved it anything worth considering
		if (adjuster == null || (activeHandle != null && activeHandle.distanceSq(x, y) < minDisplacement*minDisplacement))
			return pathROI;
		
//		setROI(adjuster.updateActiveHandleLocation(x, y, shiftDown), false);
		pathROI = adjuster.updateActiveHandleLocation(x, y, shiftDown);
		
		return pathROI;
	}
	
	
	
	abstract static class RoiHandleAdjuster<T extends ROI> {
		
		final int TOP_LEFT = 0;
		final int TOP_CENTER = 1;
		final int TOP_RIGHT = 2;
		final int CENTER_RIGHT = 3;
		final int BOTTOM_RIGHT = 4;
		final int BOTTOM_CENTER = 5;
		final int BOTTOM_LEFT = 6;
		final int CENTER_LEFT = 7;

		abstract T updateActiveHandleLocation(double xNew, double yNew, boolean shiftDown);

		abstract void ensureHandlesUpdated();

		abstract MutablePoint grabHandle(double x, double y, double maxDist, boolean shiftDown);
		
		protected static int getClosestHandleIndex(List<MutablePoint> handles, double x, double y, double maxDist) {
			// Get the closest handle within maxDist pixels away
			double closestDist = Double.POSITIVE_INFINITY;
			double distSq = maxDist*maxDist;
			int activeHandleIndex = -1;
			int counter = 0;
			for (MutablePoint p : handles) {
				double dist = p.distanceSq(x, y);
				if (dist <= distSq && dist <= closestDist) {
					closestDist = dist;
					activeHandleIndex = counter;
				}
				counter++;
			}
			return activeHandleIndex;
		}
		
		abstract List<MutablePoint> getHandles();
		
		abstract T requestNewHandle(double x, double y);

	}
	
	
	
	
	abstract static class BoundedHandleAdjuster<T extends AbstractPathBoundedROI> extends RoiHandleAdjuster<T> {
				
		private T roi;
		private List<MutablePoint> handles = new ArrayList<>(8);
		private Rect bounds;
		private double x, y; // Unchanging
		private double x2, y2; // Changing (at least potentially)
		
//		protected MutablePoint activeHandle = null;
		protected int activeHandleIndex = -1;
		
		BoundedHandleAdjuster(T roi) {
			this.roi = roi;
			bounds = new Rect(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
			handles.clear();
			for (int i = 0; i < 8; i++)
				handles.add(new MutablePoint(Double.NaN, Double.NaN));
//			updateHandles();
		}
		
		@Override
		void ensureHandlesUpdated() {
			updateHandles();
		}
		
		private void setupCoords(boolean xMinChange, boolean yMinChange) {
			if (xMinChange) {
				x = bounds.getMaxX();
				x2 = bounds.getMinX();
			} else {
				x = bounds.getMinX();
				x2 = bounds.getMaxX();
			}
			if (yMinChange) {
				y = bounds.getMaxY();
				y2 = bounds.getMinY();
			} else {
				y = bounds.getMinY();
				y2 = bounds.getMaxY();
			}
		}
		
		
		static boolean handlesOverlap(final List<MutablePoint> handles) {
			if (handles.isEmpty())
				return false;
			MutablePoint h1 = handles.get(0);
			for (MutablePoint h : handles) {
				if (h1.distanceSq(h) > 0)
					return false;
			}
			return true;
		}
		
		
		@Override
		MutablePoint grabHandle(double x, double y, double maxDist, boolean shiftDown) {
			List<MutablePoint> handles = getHandles();
			
			// Check for all handles being in the same place (i.e. ROI being created) - then choose bottom right
			// Otherwise risks that the ROI can't be created sensibly (i.e. has either zero width or height)
			if (handlesOverlap(handles))
				activeHandleIndex = BOTTOM_RIGHT;
			else
				activeHandleIndex = getClosestHandleIndex(handles, x, y, maxDist);
			
			if (activeHandleIndex < 0)
				return null;
			
			switch (activeHandleIndex) {
			case TOP_LEFT:
				setupCoords(true, true);
				break;
			case TOP_CENTER:
				setupCoords(false, true);
				break;
			case TOP_RIGHT:
				setupCoords(false, true);
				break;
			case CENTER_RIGHT:
				setupCoords(false, false);
				break;
			case BOTTOM_RIGHT:
				setupCoords(false, false);
				break;
			case BOTTOM_CENTER:
				setupCoords(false, false);
				break;
			case BOTTOM_LEFT:
				setupCoords(true, false);
				break;
			case CENTER_LEFT:
				setupCoords(true, false);
				break;
			}
			return handles.get(activeHandleIndex);
		}
		
		void updateHandles() {
			bounds = new Rect(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
			handles.get(TOP_LEFT).setLocation(bounds.getMinX(), bounds.getMinY());
			handles.get(TOP_CENTER).setLocation(bounds.getCenterX(), bounds.getMinY());
			handles.get(TOP_RIGHT).setLocation(bounds.getMaxX(), bounds.getMinY());
			handles.get(CENTER_RIGHT).setLocation(bounds.getMaxX(), bounds.getCenterY());
			handles.get(BOTTOM_RIGHT).setLocation(bounds.getMaxX(), bounds.getMaxY());
			handles.get(BOTTOM_CENTER).setLocation(bounds.getCenterX(), bounds.getMaxY());
			handles.get(BOTTOM_LEFT).setLocation(bounds.getMinX(), bounds.getMaxY());
			handles.get(CENTER_LEFT).setLocation(bounds.getMinX(), bounds.getCenterY());
		}
		
		@Override
		T updateActiveHandleLocation(double xNew, double yNew, boolean shiftDown) {
			if (activeHandleIndex < 0)
				return roi;
			boolean isCorner = false;
			switch (activeHandleIndex) {
			case TOP_LEFT:
				x2 = xNew;
				y2 = yNew;
				isCorner = true;
				break;
			case TOP_CENTER:
				y2 = yNew;
				break;
			case TOP_RIGHT:
				x2 = xNew;
				y2 = yNew;
				isCorner = true;
				break;
			case CENTER_RIGHT:
				x2 = xNew;
				break;
			case BOTTOM_RIGHT:
				x2 = xNew;
				y2 = yNew;
				isCorner = true;
				break;
			case BOTTOM_CENTER:
				y2 = yNew;
				break;
			case BOTTOM_LEFT:
				x2 = xNew;
				y2 = yNew;
				isCorner = true;
				break;
			case CENTER_LEFT:
				x2 = xNew;
				break;
			}
			
			
			// Untested!
//			if (shiftDown) {
//				double dx = Math.abs(x2 - x);
//				double dy = Math.abs(y2 - y);
//				if (dx > dy) {
//					if (x2 > x)
//						x2 = x + dy;
//					else
//						x2 = x - dy;						
//				} else if (dy > dx) {
//					if (y2 > y)
//						y2 = y + dx;
//					else
//						y2 = y - dx;											
//				}
//			}
			
			// If pressing shift, constrain to be square
			if (shiftDown && isCorner) {
				double w = this.x - xNew;
				double h = this.y - yNew;
				if (w != 0 && h != 0) {
					double len = Math.min(Math.abs(w), Math.abs(h));
					w = Math.signum(w) * len;
					h = Math.signum(h) * len;
				}
				x2 = x - w;
				y2 = y - h;
			}
			
			roi = createROI(Math.min(x, x2), Math.min(y, y2), Math.abs(x - x2), Math.abs(y - y2), roi.getImagePlane());
			return roi;
		}
		
		abstract T createROI(double x, double y, double x2, double y2, ImagePlane plane);
		
		@Override
		List<MutablePoint> getHandles() {
			updateHandles();
			return handles;
		}
		
		@Override
		public T requestNewHandle(double x, double y) {
			return roi; // Can't add a new handle to a bounded ROI
		}

	}
	
	
	
	static class RectangleHandleAdjuster extends BoundedHandleAdjuster<RectangleROI> {

		RectangleHandleAdjuster(RectangleROI roi) {
			super(roi);
		}

		@Override
		RectangleROI createROI(double x, double y, double width, double height, ImagePlane plane) {
			return new RectangleROI(x, y, width, height, plane);
		}
		
	}
	
	
	static class EllipseHandleAdjuster extends BoundedHandleAdjuster<EllipseROI> {

		EllipseHandleAdjuster(EllipseROI roi) {
			super(roi);
		}

		@Override
		EllipseROI createROI(double x, double y, double width, double height, ImagePlane plane) {
			return new EllipseROI(x, y, width, height, plane);
		}
		
	}
	
	
	
	class PolygonHandleAdjuster extends RoiHandleAdjuster<PolygonROI> {
		
		private PolygonROI roi;
		private List<MutablePoint> handles;
//		private MutablePoint activeHandle = null;
		
		PolygonHandleAdjuster(PolygonROI roi) {
			this.roi = roi;
			ensureHandlesUpdated();
		}
		
		@Override
		void ensureHandlesUpdated() {
			if (handles == null)
				handles = new ArrayList<>();
			else
				handles.clear();
			addPointsToMutablePointList(handles, roi.getAllPoints());
			
			// If we have a single point, create a second handle (which may be adjusted)
			if (handles.size() == 1)
				handles.add(new MutablePoint(handles.get(0).getX(), handles.get(0).getY()));
		}
		
		@Override
		MutablePoint grabHandle(double x, double y, double maxDist, boolean shiftDown) {
			int activeHandleIndex = getClosestHandleIndex(handles, x, y, maxDist);
			if (activeHandleIndex >= 0)
				activeHandle = handles.get(activeHandleIndex);
			else
				activeHandle = null;
			return activeHandle;
		}
		
		@Override
		PolygonROI updateActiveHandleLocation(double xNew, double yNew, boolean shiftDown) {
			if (activeHandle == null)
				return roi;
			activeHandle.setLocation(xNew, yNew);
			roi = new PolygonROI(createPoint2List(handles), roi.getImagePlane());
//			System.out.println("UPDATED HANDLES: " + handles.size() + ", " + roi.nVertices());
			return roi;
		}
		
		@Override
		List<MutablePoint> getHandles() {
			return handles;
		}
		
		@Override
		public PolygonROI requestNewHandle(double x, double y) {
			if (activeHandle == null)
				return roi; // Can only add if there is an active handle - distance to this will be used
			
			// Move the active handle if it is very close to the requested region
			// (removed)

			// Don't add a handle at almost the sample place as an existing handle
			boolean lastHandleSame = false;
			if (handles.size() >= 2 && activeHandle == handles.get(handles.size() - 1)) {
				MutablePoint lastHandle = handles.get(handles.size() - 2);
				if (lastHandle.distanceSq(x, y) < 0.5) {
					return roi;
				}
				lastHandleSame = lastHandle.distanceSq(activeHandle) == 0;
			}

			if (lastHandleSame) {
				activeHandle.setLocation(x, y);
			} else {
				activeHandle = new MutablePoint(x, y);
				roi = new PolygonROI(createPoint2List(handles), roi.getImagePlane());
				handles.add(activeHandle);
			}
			
			return roi;
		}


	}
	
	
	
	class PolylineHandleAdjuster extends RoiHandleAdjuster<PolylineROI> {
		
		private PolylineROI roi;
		private List<MutablePoint> handles;
//		private MutablePoint activeHandle = null;
		
		PolylineHandleAdjuster(PolylineROI roi) {
			this.roi = roi;
			ensureHandlesUpdated();
		}
		
		@Override
		void ensureHandlesUpdated() {
			if (handles == null)
				handles = new ArrayList<>();
			else
				handles.clear();
			addPointsToMutablePointList(handles, roi.getAllPoints());
			
			// If we have a single point, create a second handle (which may be adjusted)
			if (handles.size() == 1)
				handles.add(new MutablePoint(handles.get(0).getX(), handles.get(0).getY()));
		}
		
		@Override
		MutablePoint grabHandle(double x, double y, double maxDist, boolean shiftDown) {
			int activeHandleIndex = getClosestHandleIndex(handles, x, y, maxDist);
			if (activeHandleIndex >= 0)
				activeHandle = handles.get(activeHandleIndex);
			else
				activeHandle = null;
			return activeHandle;
		}
		
		@Override
		PolylineROI updateActiveHandleLocation(double xNew, double yNew, boolean shiftDown) {
			if (activeHandle == null)
				return roi;
			activeHandle.setLocation(xNew, yNew);
			roi = new PolylineROI(createPoint2List(handles), roi.getImagePlane());
//			System.out.println("UPDATED HANDLES: " + handles.size() + ", " + roi.getNumPoints());
			return roi;
		}
		
		@Override
		List<MutablePoint> getHandles() {
			return handles;
		}
		
		@Override
		public PolylineROI requestNewHandle(double x, double y) {
			if (activeHandle == null)
				return roi; // Can only add if there is an active handle - distance to this will be used
			
			// Move the active handle if it is very close to the requested region
			// (removed)
			
			// Don't add a handle at almost the sample place as an existing handle
			if (handles.size() >= 2 && activeHandle == handles.get(handles.size() - 1) && 
					(handles.get(handles.size() - 2).distanceSq(x, y) < 0.5)) {
				return roi;
			}
						
//			// Don't add a handle at almost the sample place as an existing handle
//			if (handles.size() >= 2 && activeHandle == handles.get(handles.size() - 1) && handles.get(handles.size() - 2).distanceSq(x, y) < 0.5) {
//				return roi;
//			}
			
//			// If we have 2 points, which are identical, shift instead of creating
//			if (handles.size() >= 2 && activeHandle == handles.get(handles.size() - 1) && activeHandle.distanceSq(handles.get(handles.size() - 2)) < 0.000001) {
//				System.err.println("UPDATING HANDLE");
//				return updateActiveHandleLocation(x, y, false);
//			}
			
			activeHandle = new MutablePoint(x, y);
			roi = new PolylineROI(createPoint2List(handles), roi.getImagePlane());
			handles.add(activeHandle);
//			System.out.println("UPDATED HANDLES BY REQUEST: " + handles.size());
			return roi;
		}


	}
	

	
	
	class PointsHandleAdjuster extends RoiHandleAdjuster<PointsROI> {
		
		private PointsROI roi;
		private List<MutablePoint> handles;
//		private MutablePoint activeHandle = null;
		
		PointsHandleAdjuster(PointsROI roi) {
			this.roi = roi;
			ensureHandlesUpdated();
		}
		
		@Override
		void ensureHandlesUpdated() {
			if (handles == null)
				handles = new ArrayList<>();
			else
				handles.clear();
			addPointsToMutablePointList(handles, roi.getAllPoints());
		}
		
		@Override
		MutablePoint grabHandle(double x, double y, double maxDist, boolean shiftDown) {
			int activeHandleIndex = getClosestHandleIndex(handles, x, y, maxDist);
			if (activeHandleIndex >= 0)
				activeHandle = handles.get(activeHandleIndex);
			else
				activeHandle = null;
			return activeHandle;
		}
		
		@Override
		PointsROI updateActiveHandleLocation(double xNew, double yNew, boolean shiftDown) {
			if (activeHandle == null)
				return roi;

			activeHandle.setLocation(xNew, yNew);
			roi = new PointsROI(createPoint2List(handles), roi.getImagePlane());
			ensureHandlesUpdated();
			activeHandle = grabHandle(xNew, yNew, Double.POSITIVE_INFINITY, shiftDown);
//			System.err.println("Calling: " + activeHandle + " - " + (handles == null ? 0 : handles.size()));

			return roi;
		}
		
		@Override
		List<MutablePoint> getHandles() {
			return handles;
		}
		
		@Override
		public PointsROI requestNewHandle(double x, double y) {
			activeHandle = new MutablePoint(x, y);
			handles.add(activeHandle);
			roi = new PointsROI(createPoint2List(handles), roi.getImagePlane());
			ensureHandlesUpdated();
			activeHandle = grabHandle(x, y, Double.POSITIVE_INFINITY, false);
//			ensureHandlesUpdated();
//			activeHandle = grabHandle(x, y, 1, false);
//			ensureHandlesUpdated();
//			activeHandle = roi.getNearest(x, y, 0.01);
			return roi;
		}


	}
	
	
	
	
	
	class LineHandleAdjuster extends RoiHandleAdjuster<LineROI> {
		
		private LineROI roi;
		private List<MutablePoint> handles;
		private MutablePoint activeHandle = null;
		private MutablePoint inactiveHandle = null;
		
		LineHandleAdjuster(LineROI roi) {
			this.roi = roi;
			handles = new ArrayList<>(2);
			ensureHandlesUpdated();
		}
		
		@Override
		MutablePoint grabHandle(double x, double y, double maxDist, boolean shiftDown) {
			int activeHandleIndex = getClosestHandleIndex(handles, x, y, maxDist);
			if (activeHandleIndex >= 0) {
				activeHandle = handles.get(activeHandleIndex);
				inactiveHandle = handles.get(1 - activeHandleIndex);
			} else {
				activeHandle = null;
				inactiveHandle = null;
			}
			return activeHandle;
		}
		
		@Override
		LineROI updateActiveHandleLocation(double xNew, double yNew, boolean shiftDown) {
			if (activeHandle == null)
				return roi;
			
			// Update x & y
			// If pressing shift, constrain angles
			double x = inactiveHandle.getX();
			double y = inactiveHandle.getY();
			if (shiftDown) {
				double w = x - xNew;
				double h = y - yNew;
				if (w != 0 && h != 0) {
					int theta = (int)Math.round(Math.atan2(h, w) / Math.PI * 4);
					if (theta % 2 == 0) {
						if (theta % 4 == 0)
							h = 0;
						else
							w = 0;
					} else {
						double len = Math.min(Math.abs(w), Math.abs(h));
						w = Math.signum(w) * len;
						h = Math.signum(h) * len;
					}
				}
				xNew = x - w;
				yNew = y - h;	
			}
			
			activeHandle.setLocation(xNew, yNew);
			
			roi = new LineROI(handles.get(0).getX(), handles.get(0).getY(), handles.get(1).getX(), handles.get(1).getY(), roi.getImagePlane());
//			roi = new LineROI(inactiveHandle.getX(), inactiveHandle.getY(), activeHandle.getX(), activeHandle.getY(), roi.getC(), roi.getZ(), roi.getT());
			return roi;
		}
		
		@Override
		List<MutablePoint> getHandles() {
			return handles;
		}

		@Override
		void ensureHandlesUpdated() {
			handles.clear();
			handles.add(new MutablePoint(roi.getX1(), roi.getY1()));
			handles.add(new MutablePoint(roi.getX2(), roi.getY2()));
		}
		
		@Override
		public LineROI requestNewHandle(double x, double y) {
			return roi; // Can't add a new handle to a line ROI
		}

	}
		
	
	/**
	 * Really basic rectangle class to avoid AWT dependency.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class Rect {
		
		private double x, y, width, height;
		
		Rect(final double x, final double y, final double width, final double height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
		
		public double getMinX() {
			return x;
		}

		public double getMinY() {
			return y;
		}
		
		public double getCenterX() {
			return x + width/2;
		}

		public double getCenterY() {
			return y + height/2;
		}

		public double getWidth() {
			return width;
		}

		public double getHeight() {
			return height;
		}
		
		public double getMaxX() {
			return x + width;
		}

		public double getMaxY() {
			return y + height;
		}

	}
	
	
	/**
	 * Point2 is immutable - but for working with handles, it helps to have mutable points.
	 * 
	 * @author Pete Bankhead
	 */
	static class MutablePoint {
		
		private double x;
		private double y;
		
		MutablePoint(final double x, final double y) {
			this.x = x;
			this.y = y;
		}

		public void setLocation(final double x, final double y) {
			this.x = x;
			this.y = y;
		}
		
		public double getX() {
			return x;
		}
		
		public double getY() {
			return y;
		}
		
		public double distanceSq(final MutablePoint point) {
			return distanceSq(point.getX(), point.getY());
		}
		
		public double distanceSq(final double x2, final double y2) {
			return (x-x2)*(x-x2) + (y-y2)*(y-y2);
		}
		
		@Override
		public String toString() {
			return String.format("Mutable point: %.2f, %.2f", x, y);
		}

	}
	
	
	static void addPointsToMutablePointList(final List<MutablePoint> mutablePoints, final List<Point2> points) {
		for (Point2 p : points) {
			mutablePoints.add(new MutablePoint(p.getX(), p.getY()));
		}
	}
	
	static List<Point2> createPoint2List(final List<MutablePoint> mutablePoints) {
		List<Point2> points = new ArrayList<>(mutablePoints.size());
		for (MutablePoint p : mutablePoints) {
			points.add(new Point2(p.getX(), p.getY()));
		}
		return points;
	}
	
	

}
