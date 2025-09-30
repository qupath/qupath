package qupath.lib.gui.viewer.tools.handlers;

import javafx.scene.input.MouseEvent;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

public class PDL1ToolEventHandler extends AbstractPathDraggingROIToolEventHandler{
    @Override
    protected ROI createNewROI(MouseEvent e, double x, double y, ImagePlane plane) {
        return ROIs.createPDL1RectangleROI(x, y, 0, 0, plane);
    }
}
