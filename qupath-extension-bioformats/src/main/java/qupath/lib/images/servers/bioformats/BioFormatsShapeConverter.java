package qupath.lib.images.servers.bioformats;

import ome.xml.model.BinData;
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
import qupath.lib.analysis.images.ContourTracing;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.analysis.images.SimpleModifiableImage;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.geom.AffineTransform;
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
     * @throws NullPointerException if the provided shape is null
     */
    public static Optional<ROI> convertShapeToRoi(Shape shape) {
        logger.debug("Converting {} to QuPath ROI", shape);

        Optional<ROI> optionalRoi = switch (shape) {
            case Mask mask -> Optional.ofNullable(convertMask(mask));
            case Rectangle rectangle -> Optional.of(convertRectangle(rectangle));
            case Ellipse ellipse -> Optional.of(convertEllipse(ellipse));
            case Label label -> Optional.of(convertLabel(label));
            case Line line -> Optional.of(convertLine(line));
            case Point point -> Optional.of(convertPoint(point));
            case Polygon polygon -> Optional.of(convertPolygon(polygon));
            case Polyline polyline -> Optional.of(convertPolyLine(polyline));
            default -> {
                logger.debug("Unknown shape {}. Cannot convert it to QuPath ROI", shape);
                yield Optional.empty();
            }
        };
        return optionalRoi.map(roi -> {
            ome.xml.model.AffineTransform transform = shape.getTransform();
            if (transform == null) {
                logger.debug("No transform in {}, so returning {} without applying any transform", shape, roi);
                return roi;
            }

            logger.debug("Transform {} detected in {}, so applying it to {}", transform, shape, roi);
            return RoiTools.transformROI(
                    roi,
                    new AffineTransform(
                            transform.getA00(),
                            transform.getA10(),
                            transform.getA01(),
                            transform.getA11(),
                            transform.getA02(),
                            transform.getA12()
                    )
            );
        });
    }

    private static ROI convertMask(Mask mask) {
        logger.debug("Converting mask {} to QuPath ROI", mask);

        BinData binData = mask.getBinData();
        if (binData == null) {
            logger.debug("Binary data of {} null, cannot convert it to ROI", mask);
            return null;
        }
        long nPixels = binData.getLength() == null ? binData.getBase64Binary().length : binData.getLength().getValue();

        double maskWidth = mask.getWidth() == null ? 0 : mask.getWidth();
        double maskHeight = mask.getHeight() == null ? 0 : mask.getHeight();
        // Aspect ratios of binData and mask are the same, hence the formula below. We assume square pixels
        int width = (int) Math.round(Math.sqrt(nPixels * (maskWidth / maskHeight)));
        int height = (int)(nPixels / width);
        if (((long)width * height) != nPixels) {
            logger.debug("Couldn't figure out dimensions: {}x{} != {} pixels. Cannot convert {}", width, height, nPixels, mask);
            return null;
        }

        byte[] array = binData.getBase64Binary();
        if (array == null) {
            logger.debug("Base64 byte array of {} null, cannot convert it to ROI", mask);
            return null;
        }
        SimpleModifiableImage simpleImage = SimpleImages.createFloatImage(width, height);
        for (int i = 0; i < nPixels; i++) {
            if (array[i] != 0) {
                simpleImage.setValue(i % width, i / width, 1.0f);
            }
        }

        return ContourTracing.createTracedROI(
                simpleImage,
                1,
                1,
                RegionRequest.createInstance(
                        "",
                        1.0,
                        mask.getX() == null ? 0 : mask.getX().intValue(),     // x and y assumed to be integers
                        mask.getY() == null ? 0 : mask.getY().intValue(),
                        simpleImage.getWidth(),
                        simpleImage.getHeight(),
                        createImagePlane(mask)
                )
        );
    }

    private static ROI convertRectangle(Rectangle rectangle) {
        logger.debug("Converting rectangle {} to QuPath rectangle ROI", rectangle);

        return ROIs.createRectangleROI(
                rectangle.getX() == null ? 0 : rectangle.getX(),
                rectangle.getY() == null ? 0 : rectangle.getY(),
                rectangle.getWidth() == null ? 0 : rectangle.getWidth(),
                rectangle.getHeight() == null ? 0 : rectangle.getHeight(),
                createImagePlane(rectangle)
        );
    }

    private static ROI convertEllipse(Ellipse ellipse) {
        logger.debug("Converting ellipse {} to QuPath ellipse ROI", ellipse);

        double x = ellipse.getX() == null ? 0 : ellipse.getX();
        double y = ellipse.getY() == null ? 0 : ellipse.getY();
        double radiusX = ellipse.getRadiusX() == null ? 0 : ellipse.getRadiusX();
        double radiusY = ellipse.getRadiusY() == null ? 0 : ellipse.getRadiusY();

        return ROIs.createEllipseROI(
                x - radiusX,
                y - radiusY,
                radiusX * 2,
                radiusY * 2,
                createImagePlane(ellipse)
        );
    }

    private static ROI convertLabel(Label label) {
        logger.debug("Converting label {} to QuPath point ROI", label);

        return ROIs.createPointsROI(
                label.getX() == null ? 0 : label.getX(),
                label.getY() == null ? 0 : label.getY(),
                createImagePlane(label)
        );
    }

    private static ROI convertLine(Line line) {
        logger.debug("Converting line {} to QuPath line ROI", line);

        return ROIs.createLineROI(
                line.getX1() == null ? 0 : line.getX1(),
                line.getY1() == null ? 0 : line.getY1(),
                line.getX2() == null ? 0 : line.getX2(),
                line.getY2() == null ? 0 : line.getY2(),
                createImagePlane(line)
        );
    }

    private static ROI convertPoint(Point point) {
        logger.debug("Converting point {} to QuPath point ROI", point);

        return ROIs.createPointsROI(
                point.getX() == null ? 0 : point.getX(),
                point.getY() == null ? 0 : point.getY(),
                createImagePlane(point)
        );
    }

    private static ROI convertPolygon(Polygon polygon) {
        logger.debug("Converting polygon {} to QuPath polygon ROI", polygon);

        return ROIs.createPolygonROI(
                parseStringPoints(polygon.getPoints() == null ? "" : polygon.getPoints()),
                createImagePlane(polygon)
        );
    }

    private static ROI convertPolyLine(Polyline polyline) {
        logger.debug("Converting polyline {} to QuPath polyline ROI", polyline);

        return ROIs.createPolylineROI(
                parseStringPoints(polyline.getPoints() == null ? "" : polyline.getPoints()),
                createImagePlane(polyline)
        );
    }

    private static ImagePlane createImagePlane(Shape shape) {
        if (shape.getTheC() == null) {
            return ImagePlane.getPlane(
                    shape.getTheZ() == null ? 0 : shape.getTheZ().getValue(),
                    shape.getTheT() == null ? 0 : shape.getTheT().getValue()
            );
        } else {
            return ImagePlane.getPlaneWithChannel(
                    shape.getTheC().getValue(),
                    shape.getTheZ() == null ? 0 : shape.getTheZ().getValue(),
                    shape.getTheT() == null ? 0 : shape.getTheT().getValue()
            );
        }
    }

    private static List<Point2> parseStringPoints(String pointsString) {
        logger.debug("Converting {} to a list of points", pointsString);

        return Arrays.stream(pointsString.split(POINT_DELIMITER))
                .map(pointStr -> {
                    String[] point = pointStr.split(POINT_COORDINATE_DELIMITER);
                    if (point.length > 1) {
                        try {
                            return new Point2(Double.parseDouble(point[0]), Double.parseDouble(point[1]));
                        } catch (NumberFormatException e) {
                            logger.debug("Cannot convert {} to two double elements", pointStr, e);
                            return null;
                        }
                    } else {
                        logger.debug("Cannot find two elements in {}", pointStr);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
