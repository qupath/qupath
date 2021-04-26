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

package qupath.lib.gui.commands;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
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
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Action enabling a selected annotation object to be modified interactively by a rigid transformation,
 * i.e. through rotation and translation.
 * 
 * @author Pete Bankhead
 *
 */
class RigidObjectEditorCommand implements Runnable, ChangeListener<ImageData<BufferedImage>>, QuPathViewerListener {

	private final static Logger logger = LoggerFactory.getLogger(RigidObjectEditorCommand.class);
	
	private QuPathGUI qupath;
	
	private QuPathViewer viewer = null;
	private PathOverlay overlay = null;
	
	private PathObject originalObject = null;
	private Map<PathObject, ROI> originalObjectROIs = new HashMap<>();
	
	private RoiAffineTransformer transformer = null;
	
	private RigidMouseListener mouseListener = new RigidMouseListener();

	public RigidObjectEditorCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.qupath.imageDataProperty().addListener(this);
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
			Dialogs.showErrorNotification("Rotate annotation", "No annotation selected!");
			return;
		}
		if (pathObject.isLocked()) {
			Dialogs.showErrorNotification("Rotate annotation", "Selected annotation is locked!");
			return;
		}
//		if (pathObject.getROI().isPoint()) {
//			Dialogs.showErrorNotification("Rotate annotation", "Point annotations cannot be rotated, sorry!");
//			return;
//		}
		ImageRegion bounds = viewer.getServerBounds();
		
		if (pathObject.isTMACore()) {
			for (PathObject child : pathObject.getChildObjectsAsArray()) {
				if (isSuitableAnnotation(child)) {
					originalObjectROIs.put(child, child.getROI());
				}
			}
			if (originalObjectROIs.isEmpty()) {
				Dialogs.showErrorMessage("Rigid refinement problem", "TMA core must contain empty annotations objects for rigid refinement");
				return;
			}
		}
		originalObjectROIs.put(pathObject, pathObject.getROI());
		
		this.originalObject = pathObject;
		
		
		viewer.setActiveTool(null);
		qupath.setToolSwitchingEnabled(false);
		viewer.addViewerListener(this);
		viewer.getView().addEventHandler(MouseEvent.ANY, mouseListener);
		
//		// Remove selected object & create an overlay showing the currently-being-edited version
//		viewer.getHierarchy().removeObject(originalObject, true, true);
		
		transformer = new RoiAffineTransformer(bounds, originalObject.getROI());
//		editingROI = new RotatedROI((PathArea)originalObject.getROI());
//		editingROI.setAngle(Math.PI/3);
		
		overlay = new AffineEditOverlay(viewer.getOverlayOptions());
		viewer.getCustomOverlayLayers().add(overlay);
		
		PathPrefs.paintSelectedBoundsProperty().set(false);
		// Create & show temporary object
		for (Entry<PathObject, ROI> entry : originalObjectROIs.entrySet())
			((PathROIObject)entry.getKey()).setROI(transformer.getTransformedROI(entry.getValue(), false));
		
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
		
			DialogButton option = Dialogs.showYesNoCancelDialog(
					"Affine object editing", "Confirm object changes?");
			
			if (option == DialogButton.CANCEL)
				return;
			
			if (option == DialogButton.NO) {
				for (Entry<PathObject, ROI> entry : originalObjectROIs.entrySet())
					((PathROIObject)entry.getKey()).setROI(entry.getValue());
			} else {
				var transform = transformer.transform;
				var values = transform.getMatrixEntries();
				logger.info("Applied ROI transform: {}",
						String.format("\n %f, %f, %f,\n%f, %f, %f",
								values[0], values[1], values[2],
								values[3], values[4], values[5]));
				// Apply clipping now
				for (Entry<PathObject, ROI> entry : originalObjectROIs.entrySet()) {
					ROI roiTransformed = transformer.getTransformedROI(entry.getValue(), true);
					((PathROIObject)entry.getKey()).setROI(roiTransformed);
				}
				viewer.getHierarchy().fireHierarchyChangedEvent(this, originalObject);
			}
		}

		// Update the mode if the viewer is still active
		qupath.setToolSwitchingEnabled(true);
		if (viewer == qupath.getViewer())
			viewer.setActiveTool(qupath.getSelectedTool());
		
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
		ROI shape = GeometryTools.geometryToROI(transformer.getTransformedShape(), roi.getImagePlane());
		return PathObjects.createAnnotationObject(shape, originalObject.getPathClass());
	}
	

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		commitChanges(false);
	}
	
	
	
	class AffineEditOverlay extends AbstractOverlay {

		AffineEditOverlay(final OverlayOptions overlayOptions) {
			super(overlayOptions);
		}
		
		@Override
		public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor,
				ImageData<BufferedImage> imageData, boolean paintCompletely) {
			
			if (transformer == null)
				return;
			
			Stroke stroke = PathHierarchyPaintingHelper.getCachedStroke(PathPrefs.annotationStrokeThicknessProperty().get() * downsampleFactor);
			
			// Paint bounding box to show rotation
			Color color = ColorToolsAwt.getCachedColor(0, 0, 0, 96);
			PathHierarchyPaintingHelper.paintShape(GeometryTools.geometryToShape(transformer.getTransformedBounds()), g2d, color, stroke, null);
			
			// Paint line to rotation handle
			Line2D line = transformer.getRotationHandleLine(downsampleFactor);
			PathHierarchyPaintingHelper.paintShape(line, g2d, color, stroke, null);
			
			// Paint rotation handle
			Shape ellipse = transformer.getRotationHandle(downsampleFactor);
			Color color2 = ColorToolsAwt.getCachedColor(255, 255, 255, 96);
			PathHierarchyPaintingHelper.paintShape(ellipse, g2d, color, stroke, color2);
			
			// Ensure objects are all painted
			for (PathObject pathObject : originalObjectROIs.keySet()) {
				PathHierarchyPaintingHelper.paintObject(pathObject, false, g2d, null, getOverlayOptions(), viewer.getHierarchy().getSelectionModel(), downsampleFactor);
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
			if (!contains(transformer.getTransformedBounds(), p.getX(), p.getY()))
				commitChanges(false);
		}

		public void mousePressed(MouseEvent e) {
			if (transformer == null)
				return;
			Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), new Point2D.Double(), false);
			if (transformer.getRotationHandle(viewer.getDownsampleFactor()).contains(p)) {
				isRotating = true;
				lastPoint = p;
			} else if (contains(transformer.getTransformedBounds(), p.getX(), p.getY())) {
				isTranslating = true;
				lastPoint = p;				
			}
			e.consume();
		}
		
		
		boolean contains(Geometry geometry, double x, double y) {
			return SimplePointInAreaLocator.isContained(new Coordinate(x, y), geometry);
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
				ROI roiTransformed = transformer.getTransformedROI(entry.getValue(), false);
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
		
		private Geometry shapeOrig;
		private Geometry boundsOrig;
		private Geometry shapeTransformed;
		private Geometry boundsTransformed;
		
		private double dx = 0;
		private double dy = 0;
		private double theta = 0;
		private AffineTransformation transform;
		
		RoiAffineTransformer(final ImageRegion bounds, final ROI roi) {
			if (bounds != null)
				roiBounds = ROIs.createRectangleROI(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), ImagePlane.getPlane(bounds));
//			this.roi = roi;
			this.shapeOrig = roi.getGeometry();
			this.boundsOrig = shapeOrig.getEnvelope();
			this.transform = new AffineTransformation();
			
			var centroid = boundsOrig.getCentroid();
			this.anchorX = centroid.getX();
			this.anchorY = centroid.getY();
		}
		
		void resetCachedShapes() {
			this.shapeTransformed = null;
			this.boundsTransformed = null;
		}
		
		/**
		 * Get an updated ROI, applying the current transformation.
		 * @param roi
		 * @param clipToBounds if true, clip the resulting ROI to the image bounds.
		 * 						This can be slow to compute, so may prefer to set to false for performance.
		 * @return
		 */
		public ROI getTransformedROI(final ROI roi, boolean clipToBounds) {
			Geometry geom = roi.getGeometry();
			updateTransform();
			geom = transform.transform(geom);
			if (clipToBounds && roiBounds != null)
				geom = geom.intersection(roiBounds.getGeometry());
			return GeometryTools.geometryToROI(geom, roi.getImagePlane());
			
			
//			ROI transformedROI = getUnclippedTransformedROI(roi);
//			if (roiBounds == null) // Should this work for points? || !(transformedROI instanceof PathShape))
//				return transformedROI;
//			return RoiTools.combineROIs(transformedROI, roiBounds, CombineOp.INTERSECT);
		}
		
		public ROI getUnclippedTransformedROI(final ROI roi) {
			Geometry shape = roi.getGeometry();
			
//			double flatness = 0.5;
//			// Try to return an ellipse, if appropriate
//			if (shape instanceof Ellipse2D) {
//				Rectangle2D bounds = shape.getBounds2D();
//				if (theta == 0 || GeneralTools.almostTheSame(bounds.getWidth(), bounds.getHeight(), 0.01)) {
//					return ROIs.createEllipseROI(bounds.getX()+dx, bounds.getY()+dy, bounds.getWidth(), bounds.getHeight(), ImagePlane.getPlaneWithChannel(roi));
//				}
////				// Don't flatten an ellipse
////				flatness = 0.5;
//			}
			
			updateTransform();
			shape = transform.transform(shape);
			return GeometryTools.geometryToROI(shape, roi.getImagePlane());
		}
		
		
		public Geometry getTransformedShape() {
			if (shapeTransformed == null) {
				updateTransform();
				shapeTransformed = transform.transform(shapeOrig);
			}
			return shapeTransformed;
		}

		public Geometry getTransformedBounds() {
			if (boundsTransformed == null) {
				updateTransform();
				boundsTransformed = transform.transform(boundsOrig);
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
			transform.setToRotation(theta, anchorX, anchorY);
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
			var env = boundsOrig.getEnvelopeInternal();
			Coordinate p1 = new Coordinate((env.getMinX() + env.getMaxX()) / 2.0, env.getMinY());
			Coordinate p2 = new Coordinate((env.getMinX() + env.getMaxX()) / 2.0, env.getMinY()-displacement);
			transform.transform(p1, p1);
			transform.transform(p2, p2);
			return new Line2D.Double(p1.getX(), p1.getY(), p2.getX(), p2.getY());
		}
		
		Shape getRotationHandle(final double downsampleFactor) {
//			double radius = 10 * Math.min(downsampleFactor, 50);
			double radius = 5 * downsampleFactor;
			double displacement = getDisplacement(downsampleFactor);
			var env = boundsOrig.getEnvelopeInternal();
			
			Coordinate c = new Coordinate((env.getMinX() + env.getMaxX()) / 2.0, env.getMinY()-displacement-radius);
			transform.transform(c, c);
			return new Ellipse2D.Double(
					c.getX() - radius,
					c.getY() - radius,
					radius*2,
					radius*2
					);
			
//			transform.transform(src, dest)
//			Ellipse2D ellipse = new Ellipse2D.Double(
//					(env.getMinX() + env.getMaxX()) / 2.0-radius,
//					env.getMinY()-displacement-radius*2,
//					radius*2,
//					radius*2);
//			
//			return transform.createTransformedShape(ellipse);
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
