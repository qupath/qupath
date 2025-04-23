package qupath.lib.images.servers.bioformats;

import ome.xml.model.Ellipse;
import ome.xml.model.Label;
import ome.xml.model.Line;
import ome.xml.model.Mask;
import ome.xml.model.Point;
import ome.xml.model.Polygon;
import ome.xml.model.Polyline;
import ome.xml.model.Rectangle;
import ome.xml.model.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A converter between BioFormats shapes and QuPath ROIs.
 */
class BioFormatsShapeConverter {

    private static final Logger logger = LoggerFactory.getLogger(BioFormatsShapeConverter.class);
    private static final String POINT_DELIMITER = " ";
    private static final String POINT_COORDINATE_DELIMITER = ",";

    private BioFormatsShapeConverter() {
        throw new AssertionError("This class is not instantiable.");
    }

    /**
     * Convert the provided shape to a ROI.
     *
     * @param shape the shape to convert
     * @return the ROI corresponding to the provided shape, or an empty Optional
     * if it was not possible to convert the shape
     */
    public static Optional<ROI> convertShapeToRoi(Shape shape) {
        logger.debug("Converting {} to QuPath ROI", shape);

        return switch (shape) {
            case Mask mask -> Optional.of(convertMask(mask));
            case Rectangle rectangle -> Optional.of(convertRectangle(rectangle));
            case Ellipse ellipse -> Optional.of(convertEllipse(ellipse));
            case Label label -> Optional.of(convertLabel(label));
            case Line line -> Optional.of(convertLine(line));
            case Point point -> Optional.of(convertPoint(point));
            case Polygon polygon -> Optional.of(convertPolygon(polygon));
            case Polyline polyline -> Optional.of(convertPolyLine(polyline));
            case null, default -> {
                logger.debug("Unknown shape {}. Cannot convert it to QuPath ROI", shape);
                yield Optional.empty();
            }
        };
    }

    private static ROI convertMask(Mask mask) {
        logger.debug("Converting mask {} to QuPath rectangle ROI", mask);

        return ROIs.createRectangleROI(
                mask.getX(),
                mask.getY(),
                mask.getWidth(),
                mask.getHeight(),
                ImagePlane.getPlane(mask.getTheZ().getValue(), mask.getTheT().getValue())
        );
    }

    private static ROI convertRectangle(Rectangle rectangle) {
        logger.debug("Converting rectangle {} to QuPath rectangle ROI", rectangle);

        return ROIs.createRectangleROI(
                rectangle.getX(),
                rectangle.getY(),
                rectangle.getWidth(),
                rectangle.getHeight(),
                ImagePlane.getPlane(rectangle.getTheZ().getValue(), rectangle.getTheT().getValue())
        );
    }

    private static ROI convertEllipse(Ellipse ellipse) {
        logger.debug("Converting ellipse {} to QuPath ellipse ROI", ellipse);

        return ROIs.createEllipseROI(
                ellipse.getX()- ellipse.getRadiusX(),
                ellipse.getY() - ellipse.getRadiusY(),
                ellipse.getRadiusX() * 2,
                ellipse.getRadiusY() * 2,
                ImagePlane.getPlane(ellipse.getTheZ().getValue(), ellipse.getTheT().getValue())
        );
    }

    private static ROI convertLabel(Label label) {
        logger.debug("Converting label {} to QuPath point ROI", label);

        return ROIs.createPointsROI(
                label.getX(),
                label.getY(),
                ImagePlane.getPlane(label.getTheZ().getValue(), label.getTheT().getValue())
        );
    }

    private static ROI convertLine(Line line) {
        logger.debug("Converting line {} to QuPath line ROI", line);

        return ROIs.createLineROI(
                line.getX1(),
                line.getY1(),
                line.getX2(),
                line.getY2(),
                ImagePlane.getPlane(line.getTheZ().getValue(), line.getTheT().getValue())
        );
    }

    private static ROI convertPoint(Point point) {
        logger.debug("Converting point {} to QuPath point ROI", point);

        return ROIs.createPointsROI(
                point.getX(),
                point.getY(),
                ImagePlane.getPlane(point.getTheZ().getValue(), point.getTheT().getValue())
        );
    }

    private static ROI convertPolygon(Polygon polygon) {
        logger.debug("Converting polygon {} to QuPath polygon ROI", polygon);

        return ROIs.createPolygonROI(
                parseStringPoints(polygon.getPoints() == null ? "" : polygon.getPoints()),
                ImagePlane.getPlane(polygon.getTheZ().getValue(), polygon.getTheT().getValue())
        );
    }

    private static ROI convertPolyLine(Polyline polyline) {
        logger.debug("Converting polyline {} to QuPath polyline ROI", polyline);

        return ROIs.createPolylineROI(
                parseStringPoints(polyline.getPoints() == null ? "" : polyline.getPoints()),
                ImagePlane.getPlane(polyline.getTheZ().getValue(), polyline.getTheT().getValue())
        );
    }

    private static List<Point2> parseStringPoints(String pointsString) {
        return Arrays.stream(pointsString.split(POINT_DELIMITER))
                .map(pointStr -> {
                    String[] point = pointStr.split(POINT_COORDINATE_DELIMITER);
                    if (point.length > 1) {
                        return new Point2(Double.parseDouble(point[0]), Double.parseDouble(point[1]));
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
