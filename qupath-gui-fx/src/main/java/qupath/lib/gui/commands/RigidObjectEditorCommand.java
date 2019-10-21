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

package qupath.lib.gui.commands;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.DisplayHelpers.DialogButton;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathHierarchyPaintingHelper;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools.CombineOp;
import qupath.lib.roi.interfaces.ROI;

/**
 * Action enabling a selected annotation object to be modified interactively by a rigid transformation,
 * i.e. through rotation and translation.
 * 
 * @author Pete Bankhead
 *
 */
public class RigidObjectEditorCommand implements PathCommand, ImageDataChangeListener<BufferedImage>, QuPathViewerListener {

	private QuPathGUI qupath;
	
	private QuPathViewer viewer = null;
	private PathOverlay overlay = null;
	
	private PathObject originalObject = null;
	private Map<PathObject, ROI> originalObjectROIs = new HashMap<>();
	
	private RoiAffineTransformer transformer = null;
	
	private RigidMouseListener mouseListener = new RigidMouseListener();

	public RigidObjectEditorCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.qupath.addImageDataChangeListener(this);
	}
	
	
	/**
	 * Get the currently-selected object if it is compatible, or null if there is no compatible object selected
	 * @return
	 */
	PathObject getSelectedObject(final QuPathViewer viewer) {
		return viewer.getSelectedObject();
	}
	
	
	private boolean isSuitableAnnotation(PathObject pathObject) {
		return pathObject  instanceof PathAnnotationObject
//				&& pathObject.isEditable()
				&& (pathObject.hasROI() && pathObject.getROI().isArea() || pathObject.getROI() instanceof PolylineROI);
	}
	

	@Override
	public void run() {
		// Object is already being edited
		if (this.originalObject != null) {
//			transformer.rotate(Math.PI / 10);
////			viewer.repaint();
//			tempObject = createTransformedObject();
//			((PathAnnotationObject)tempObject).setLocked(true);
//			viewer.setSelectedObject(tempObject);
			return;
		}
		
		// Get the selected object
		viewer = qupath.getViewer();
		PathObject pathObject = getSelectedObject(viewer);
		if (pathObject == null || !(pathObject.isAnnotation() || pathObject.isTMACore())) {
			DisplayHelpers.showErrorNotification("Rotate annotation", "No annotation selected!");
			return;
		}
		if (pathObject.isLocked()) {
			DisplayHelpers.showErrorNotification("Rotate annotation", "Selected annotation is locked!");
			return;
		}
		if (pathObject.getROI().isPoint()) {
			DisplayHelpers.showErrorNotification("Rotate annotation", "Point annotations cannot be rotated, sorry!");
			return;
		}
		ImageRegion bounds = viewer.getServerBounds();
		
		if (pathObject.isTMACore()) {
			for (PathObject child : pathObject.getChildObjectsAsArray()) {
				if (isSuitableAnnotation(child)) {
					originalObjectROIs.put(child, child.getROI());
				}
			}
			if (originalObjectROIs.isEmpty()) {
				DisplayHelpers.showErrorMessage("Rigid refinement problem", "TMA core must contain empty annotations objects for rigid refinement");
				return;
			}
		}
		originalObjectROIs.put(pathObject, pathObject.getROI());
		
		this.originalObject = pathObject;
		
		
		viewer.setMode(null);
		qupath.setModeSwitchingEnabled(false);
		viewer.addViewerListener(this);
		viewer.getView().addEventHandler(MouseEvent.ANY, mouseListener);
		
//		// Remove selected object & create an overlay showing the currently-being-edited version
//		viewer.getHierarchy().removeObject(originalObject, true, true);
		
		transformer = new RoiAffineTransformer(bounds, originalObject.getROI());
//		editingROI = new RotatedROI((PathArea)originalObject.getROI());
//		editingROI.setAngle(Math.PI/3);
		
		overlay = new AffineEditOverlay(viewer.getOverlayOptions());
		viewer.getCustomOverlayLayers().add(overlay);
		
		PathPrefs.setPaintSelectedBounds(false);
		// Create & show temporary object
		for (Entry<PathObject, ROI> entry : originalObjectROIs.entrySet())
			((PathROIObject)entry.getKey()).setROI(transformer.getTransformedROI(entry.getValue()));
		
		// Reset any existing editor (and its visible handles)
		viewer.getROIEditor().setROI(null);
		
		viewer.repaint();
//		tempObject = createTransformedObject();
//		((PathAnnotationObject)tempObject).setLocked(true);
//		viewer.setSelectedObject(tempObject);

	}

	
	
	private void commitChanges(final boolean ignoreChanges) {
		if (this.originalObject == null)
			return;
		
//		PathObject pathObject = null;
		if (!ignoreChanges) {
		
			DialogButton option = DisplayHelpers.showYesNoCancelDialog(
					"Affine object editing", "Confirm object changes?");
			
			if (option == DialogButton.CANCEL)
				return;
			
			if (option == DialogButton.NO) {
				for (Entry<PathObject, ROI> entry : originalObjectROIs.entrySet())
					((PathROIObject)entry.getKey()).setROI(entry.getValue());
			} else
				viewer.getHierarchy().fireHierarchyChangedEvent(this, originalObject);
		}

		// Update the mode if the viewer is still active
		qupath.setModeSwitchingEnabled(true);
		if (viewer == qupath.getViewer())
			viewer.setMode(qupath.getMode());
		
		viewer.getView().removeEventHandler(MouseEvent.ANY, mouseListener);
		viewer.getCustomOverlayLayers().remove(overlay);
		viewer.removeViewerListener(this);
		
//		if (pathObject != null)
//			viewer.getHierarchy().addPathObject(pathObject, true);
		
//		// Ensure the object is selected
//		viewer.setSelectedObject(pathObject);

		viewer = null;
		overlay = null;
		originalObjectROIs.clear();
		originalObject = null;
		transformer = null;
	}
	
	
	PathObject createTransformedObject() {
		ROI roi = originalObject.getROI();
		ROI shape = RoiTools.getShapeROI(new Area(transformer.getTransformedShape()), roi.getImagePlane());
		return PathObjects.createAnnotationObject(shape, originalObject.getPathClass());
	}
	

	@Override
	public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		commitChanges(false);
	}
	
	
	
	class AffineEditOverlay extends AbstractOverlay {

		AffineEditOverlay(final OverlayOptions overlayOptions) {
			super();
			this.overlayOptions = overlayOptions;
		}
		
		@Override
		public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor,
				ImageObserver observer, boolean paintCompletely) {
			
			if (transformer == null)
				return;
			
			Stroke stroke = PathHierarchyPaintingHelper.getCachedStroke(PathPrefs.getThickStrokeThickness() * downsampleFactor);
			
			// Paint bounding box to show rotation
			Color color = ColorToolsAwt.getCachedColor(0, 0, 0, 96);
			PathHierarchyPaintingHelper.paintShape(transformer.getTransformedBounds(), g2d, color, stroke, null, downsampleFactor);
			
			// Paint line to rotation handle
			Line2D line = transformer.getRotationHandleLine(downsampleFactor);
			PathHierarchyPaintingHelper.paintShape(line, g2d, color, stroke, null, downsampleFactor);
			
			// Paint rotation handle
			Shape ellipse = transformer.getRotationHandle(downsampleFactor);
			Color color2 = ColorToolsAwt.getCachedColor(255, 255, 255, 96);
			PathHierarchyPaintingHelper.paintShape(ellipse, g2d, color, stroke, color2, downsampleFactor);
			
			// Ensure objects are all painted
			for (PathObject pathObject : originalObjectROIs.keySet()) {
				PathHierarchyPaintingHelper.paintObject(pathObject, false, g2d, null, overlayOptions, viewer.getHierarchy().getSelectionModel(), downsampleFactor);
			}
			
//			// Replicate painting of the object so it doesn't disappear immediately when unselected
//			color = ColorToolsAwt.getCachedColor(PathPrefs.getSelectedObjectColor());
//			Color colorFill = ColorToolsAwt.getCachedColor(color.getRed(), color.getGreen(), color.getBlue(), 50);
//			PathHierarchyPaintingHelper.paintShape(transformer.getTransformedShape(), g2d, color, stroke, colorFill, downsampleFactor);
		}
		
	}
	
	
	class RigidMouseListener implements EventHandler<MouseEvent> {
		
		private Point2D lastPoint;
		private boolean isRotating = false;
		private boolean isTranslating = false;

		public void mouseClicked(MouseEvent e) {
			if (e.getClickCount() <= 1)
				return;
			
			Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), new Point2D.Double(), false);
			if (!transformer.getTransformedBounds().contains(p))
				commitChanges(false);
		}

		public void mousePressed(MouseEvent e) {
			if (transformer == null)
				return;
			Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), new Point2D.Double(), false);
			if (transformer.getRotationHandle(viewer.getDownsampleFactor()).contains(p)) {
				isRotating = true;
				lastPoint = p;
			} else if (transformer.getTransformedBounds().contains(p)) {
				isTranslating = true;
				lastPoint = p;				
			}
			e.consume();
		}

		public void mouseReleased(MouseEvent e) {
			isRotating = false;
			isTranslating = false;
			lastPoint = null;
			e.consume();
		}

		public void mouseDragged(MouseEvent e) {
			if (lastPoint == null)
				return;
			Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), new Point2D.Double(), false);
			if (isTranslating) {
				double dx = p.getX() - lastPoint.getX();
				double dy = p.getY() - lastPoint.getY();
				transformer.translate(dx, dy);
			}
			if (isRotating) {
				transformer.setRotationByVector(p.getX(), p.getY());
			}
			lastPoint = p;
			
			for (Entry<PathObject, ROI> entry : originalObjectROIs.entrySet()) {
				ROI roiTransformed = transformer.getTransformedROI(entry.getValue());
				((PathROIObject)entry.getKey()).setROI(roiTransformed);
			}
			viewer.repaint();
			e.consume();
		}
		
		
		
		@Override
		public void handle(MouseEvent e) {
			if (e.getEventType() == MouseEvent.MOUSE_DRAGGED)
				mouseDragged(e);
			else if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
				mouseClicked(e);
			else if (e.getEventType() == MouseEvent.MOUSE_PRESSED)
				mousePressed(e);
			else if (e.getEventType() == MouseEvent.MOUSE_RELEASED)
				mouseReleased(e);
		}
		
		
	}
	
	
	
	
	static class RoiAffineTransformer {
		
//		private PathArea roi;
		// Starting anchors
		private ROI roiBounds;
		private double anchorX;
		private double anchorY;
		
		private Shape shapeOrig;
		private Rectangle2D boundsOrig;
		private Shape shapeTransformed;
		private Shape boundsTransformed;
		
		private double dx = 0;
		private double dy = 0;
		private double theta = 0;
		private AffineTransform transform;
		
		RoiAffineTransformer(final ImageRegion bounds, final ROI roi) {
			if (bounds != null)
				roiBounds = ROIs.createRectangleROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), ImagePlane.getPlane(bounds));
//			this.roi = roi;
			this.shapeOrig = RoiTools.getShape(roi);
			this.boundsOrig = shapeOrig.getBounds2D();
			this.transform = new AffineTransform();
			
			this.anchorX = boundsOrig.getCenterX();
			this.anchorY = boundsOrig.getCenterY();
		}
		
		void resetCachedShapes() {
			this.shapeTransformed = null;
			this.boundsTransformed = null;
		}
		
		public ROI getTransformedROI(final ROI roi) {
			ROI transformedROI = getUnclippedTransformedROI(roi);
			if (roiBounds == null) // Should this work for points? || !(transformedROI instanceof PathShape))
				return transformedROI;
			return RoiTools.combineROIs(transformedROI, roiBounds, CombineOp.INTERSECT);
		}
		
		public ROI getUnclippedTransformedROI(final ROI roi) {
			Shape shape = RoiTools.getShape(roi);
			
			double flatness = 0.5;
			// Try to return an ellipse, if appropriate
			if (shape instanceof Ellipse2D) {
				Rectangle2D bounds = shape.getBounds2D();
				if (theta == 0 || GeneralTools.almostTheSame(bounds.getWidth(), bounds.getHeight(), 0.01)) {
					return ROIs.createEllipseROI(bounds.getX()+dx, bounds.getY()+dy, bounds.getWidth(), bounds.getHeight(), ImagePlane.getPlaneWithChannel(roi));
				}
//				// Don't flatten an ellipse
//				flatness = 0.5;
			}
			
			updateTransform();
			shape = transform.createTransformedShape(shape);
			// TODO: Improve the choice of shape this returns
			if (roi != null && roi.isArea())
				return RoiTools.getShapeROI(shape, roi.getImagePlane(), flatness);
			else {
				// Check if we have a line
				if (shape instanceof Line2D) {
					Line2D line = (Line2D)shape;
					return ROIs.createLineROI(line.getX1(), line.getY1(), line.getX2(), line.getY2(), ImagePlane.getPlaneWithChannel(roi));
				}
				// Polyline is the only other option (currently?)
				PathIterator iter = shape.getPathIterator(null);
				List<Point2> points = new ArrayList<>();
				double[] seg = new double[6];
				while (!iter.isDone()) {
					switch(iter.currentSegment(seg)) {
					case PathIterator.SEG_MOVETO:
						// Fall through
					case PathIterator.SEG_LINETO:
						points.add(new Point2(seg[0], seg[1]));
						break;
					case PathIterator.SEG_CLOSE:
						throw new IllegalArgumentException("Found a closed segment in a non-area ROI!");
					default:
						throw new RuntimeException("Invalid connection - only straight lines are allowed");
					};
					iter.next();
				}
				return ROIs.createPolylineROI(points, ImagePlane.getPlaneWithChannel(roi));
			}
		}
		
		
		public Shape getTransformedShape() {
			if (shapeTransformed == null) {
				updateTransform();
				shapeTransformed = transform.createTransformedShape(shapeOrig);
			}
			return shapeTransformed;
		}

		public Shape getTransformedBounds() {
			if (boundsTransformed == null) {
				updateTransform();
				boundsTransformed = transform.createTransformedShape(boundsOrig);
			}
			return boundsTransformed;
		}
		
//		public void rotate(final double theta) {
//			transform.rotate(theta, anchorX, anchorY);
//			resetCachedShapes();
//		}
		
//		public void setRotation(final double theta) {
//			transform.setToTranslation(
//					anchorX - boundsOrig.getCenterX(),
//					anchorY - boundsOrig.getCenterY());
//			transform.rotate(theta, anchorX, anchorY);
//			resetCachedShapes();
//		}
		
		/**
		 * Set the rotation by using the angle between x,y and the current anchor
		 * @param x
		 * @param y
		 */
		public void setRotationByVector(final double x, final double y) {
			double vecY = (anchorY + dy) - y;
			double vecX = x - (anchorX + dx);
			theta = Math.atan2(vecX, vecY);// + Math.PI/2;
//			System.err.println(String.format("Theta: %.2f (%.2f, %.2f)", theta, vecX, vecY));
			resetCachedShapes();
		}
		
		public void translate(final double dx, final double dy) {
			this.dx += dx;
			this.dy += dy;
			resetCachedShapes();
		}
		
		
		void updateTransform() {
			transform.setToRotation(theta, anchorX+dx, anchorY+dy);
			transform.translate(dx, dy);
			
//			transform.setToTranslation(dx, dy);
//			transform.rotate(theta, anchorX+dx, anchorY+dy);
		}
		
		
		double getDisplacement(final double downsampleFactor) {
//			double displacement = 10 * Math.max(downsampleFactor, 200);
			return downsampleFactor * 10;
		}
		
		
		Line2D getRotationHandleLine(final double downsampleFactor) {
			double displacement = getDisplacement(downsampleFactor);
			Point2D p1 = new Point2D.Double(boundsOrig.getCenterX(), boundsOrig.getMinY());
			Point2D p2 = new Point2D.Double(boundsOrig.getCenterX(), boundsOrig.getMinY()-displacement);
			transform.transform(p1, p1);
			transform.transform(p2, p2);
			return new Line2D.Double(p1, p2);
		}
		
		Shape getRotationHandle(final double downsampleFactor) {
//			double radius = 10 * Math.min(downsampleFactor, 50);
			double radius = 5 * downsampleFactor;
			double displacement = getDisplacement(downsampleFactor);
			Ellipse2D ellipse = new Ellipse2D.Double(
					boundsOrig.getCenterX()-radius,
					boundsOrig.getMinY()-displacement-radius*2,
					radius*2,
					radius*2);
			return transform.createTransformedShape(ellipse);
		}
		
		
	}


	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {
		commitChanges(true);
	}


	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}


	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {
		if (originalObject != pathObjectSelected)
			commitChanges(false);
	}


	@Override
	public void viewerClosed(QuPathViewer viewer) {
		commitChanges(true);
	}
	
}
