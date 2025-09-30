package qupath.lib.roi;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class PDL1ROI extends AbstractPathBoundedROI implements Serializable {
    // Default box size if none is dragged
    private static final double DEFAULT_W = 1274.0;
    private static final double DEFAULT_H = 794.0;

    PDL1ROI(double x, double y, double width, double height, ImagePlane plane) {
        super(x, y,
                (width  > 0 ? width  : DEFAULT_W),
                (height > 0 ? height : DEFAULT_H),
                plane);
    }


    @Override
    public String getRoiName() {
        return "PDL1 Rectangle";
    }

    @Override
    public List<Point2> getAllPoints() {
        return Arrays.asList(new Point2(x, y),
                new Point2(x2, y),
                new Point2(x2, y2),
                new Point2(x, y2));
    }

    @Override
    public ROI duplicate() {
        return null;
    }

    @Override
    public Shape getShape() {
        return createShape();
    }

    protected Shape createShape() {
        return new Rectangle2D.Double(x, y, x2-x, y2-y);
    }

    @Override
    public RoiType getRoiType() {
        return null;
    }

    @Override
    public ROI translate(double dx, double dy) {
        if (dx == 0 && dy == 0)
            return this;
        // Shift the bounds
        return new PDL1ROI(getBoundsX()+dx, getBoundsY()+dy, getBoundsWidth(), getBoundsHeight(), getImagePlane());    }

    @Override
    public ROI scale(double scaleX, double scaleY, double originX, double originY) {
        double x1 = RoiTools.scaleOrdinate(getBoundsX(), scaleX, originX);
        double y1 = RoiTools.scaleOrdinate(getBoundsY(), scaleY, originY);
        double x2 = RoiTools.scaleOrdinate(getBoundsX() + getBoundsWidth(), scaleX, originX);
        double y2 = RoiTools.scaleOrdinate(getBoundsY() + getBoundsHeight(), scaleY, originY);
        return new PDL1ROI(x1, y1, x2-x1, y2-y1, getImagePlane());
    }

    @Override
    public double getScaledArea(double pixelWidth, double pixelHeight) {
        return 0;
    }

    @Override
    public double getScaledLength(double pixelWidth, double pixelHeight) {
        return 0;
    }

    @Override
    public boolean contains(double x, double y) {
        return false;
    }

    @Override
    public boolean intersects(double x, double y, double width, double height) {
        return false;
    }

    @Override
    public ROI updatePlane(ImagePlane plane) {
        return null;
    }


}
