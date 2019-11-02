package qupath.lib.gui.viewer.tools;

import java.awt.geom.Point2D;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.input.MouseEvent;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.interfaces.ROI;

public abstract class AbstractPolyROITool extends AbstractPathROITool {
	
	private final static Logger logger = LoggerFactory.getLogger(AbstractPolyROITool.class);
	
	AbstractPolyROITool(ModeWrapper modes) {
		super(modes);
	}

	boolean isFreehandPolyROI = false;

	
	@Override
	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);
		
		// If we double-clicked a polygon, we're done with it
		PathObject currentObject = viewer == null ? null : viewer.getSelectedObject();
		if (currentObject != null && e.getClickCount() == 1) {
			RoiEditor editor = viewer.getROIEditor();
			logger.trace("Adjusting polygon {}", e);
			Point2D p2 = mouseLocationToImage(e, true, requestPixelSnapping());
			ROI roiUpdated = editor.requestNewHandle(p2.getX(), p2.getY());
			if (currentObject != null && currentObject.getROI() != roiUpdated && currentObject instanceof PathROIObject) {
				((PathROIObject)currentObject).setROI(roiUpdated);
			}
			viewer.repaint();
		}
		else {
			commitObjectToHierarchy(e, currentObject);
		}
		
		ROI currentROI = currentObject == null ? null : currentObject.getROI();
		if (isPolyROI(currentROI) && currentROI.isEmpty())
			isFreehandPolyROI = true;
	}
	
	
	@Override
	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);
		
		PathObject currentObject = viewer.getSelectedObject();
		ROI currentROI = currentObject == null ? null : currentObject.getROI();
		if (isFreehandPolyROI) {
			if (isPolyROI(currentROI) && currentROI.isEmpty()) {
				isFreehandPolyROI = false;
			} else if (PathPrefs.enableFreehandTools()) {
				commitObjectToHierarchy(e, currentObject);
//				completePolygon(e);
			}
		}
	}
	
	
	@Override
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
		
		ROI currentROI = viewer.getCurrentROI();
		RoiEditor editor = viewer.getROIEditor();
		
		if (isPolyROI(currentROI) && editor.getROI() == currentROI) {
			Point2D p = mouseLocationToImage(e, true, requestPixelSnapping());
			ROI roiUpdated = editor.setActiveHandlePosition(p.getX(), p.getY(), viewer.getDownsampleFactor(), e.isShiftDown());
			PathObject pathObject = viewer.getSelectedObject();
			if (roiUpdated != currentROI && pathObject instanceof PathROIObject) {
				((PathROIObject)pathObject).setROI(roiUpdated);
				viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(pathObject), true);
			}
		}
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		// Note: if the 'freehand' part of the polygon creation isn't desired, just comment out this whole method
		super.mouseDragged(e);
		if (!e.isPrimaryButtonDown()) {
            return;
        }

		ROI currentROI = viewer.getCurrentROI();
		RoiEditor editor = viewer.getROIEditor();
		
		if (isPolyROI(currentROI) && editor.getROI() == currentROI) {
			Point2D p = mouseLocationToImage(e, true, requestPixelSnapping());
			ROI roiUpdated = editor.requestNewHandle(p.getX(), p.getY());
			PathObject pathObject = viewer.getSelectedObject();
			if (roiUpdated != currentROI && pathObject instanceof PathROIObject) {
				((PathROIObject)pathObject).setROI(roiUpdated);
				viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(pathObject), true);
			}
		}
	}
	
	
	boolean isPolyROI(ROI roi) {
		return roi instanceof PolygonROI || roi instanceof PolylineROI;
	}

}
