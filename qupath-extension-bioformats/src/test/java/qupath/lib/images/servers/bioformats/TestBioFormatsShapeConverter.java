package qupath.lib.images.servers.bioformats;

import ome.xml.model.AffineTransform;
import ome.xml.model.BinData;
import ome.xml.model.Ellipse;
import ome.xml.model.Label;
import ome.xml.model.Line;
import ome.xml.model.Mask;
import ome.xml.model.Point;
import ome.xml.model.Polygon;
import ome.xml.model.Polyline;
import ome.xml.model.Rectangle;
import ome.xml.model.primitives.NonNegativeInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.List;

public class TestBioFormatsShapeConverter {

    @Test
    void Check_Null_Shape() {
        Assertions.assertThrows(NullPointerException.class, () -> BioFormatsShapeConverter.convertShapeToRoi(null));
    }

    @Test
    void Check_Rectangle_With_XY_Coordinates() {
        Rectangle rectangle = new Rectangle();
        rectangle.setY(7.563);
        rectangle.setWidth(9.34234);
        rectangle.setHeight(5.445);
        ROI expectedRoi = ROIs.createRectangleROI(0, 7.563, 9.34234, 5.445);

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(rectangle).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }

    @Test
    void Check_Rectangle_With_XYZCT_Coordinates() {
        Rectangle rectangle = new Rectangle();
        rectangle.setX(5.34);
        rectangle.setY(7.563);
        rectangle.setWidth(9.34234);
        rectangle.setHeight(5.445);
        rectangle.setTheZ(new NonNegativeInteger(4));
        rectangle.setTheC(new NonNegativeInteger(788));
        rectangle.setTheT(new NonNegativeInteger(47));
        ROI expectedRoi = ROIs.createRectangleROI(
                5.34,
                7.563,
                9.34234,
                5.445,
                ImagePlane.getPlaneWithChannel(788, 4, 47)
        );

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(rectangle).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }

    @Test
    void Check_Rectangle_With_XYZCT_Coordinates_And_Transform() {
        Rectangle rectangle = new Rectangle();
        rectangle.setX(5d);
        rectangle.setY(7d);
        rectangle.setWidth(9d);
        rectangle.setHeight(3d);
        rectangle.setTheZ(new NonNegativeInteger(4));
        rectangle.setTheC(new NonNegativeInteger(788));
        rectangle.setTheT(new NonNegativeInteger(47));
        rectangle.setTransform(AffineTransform.createRotationTransform(Math.PI / 2));
        ROI expectedRoi = ROIs.createRectangleROI(
                7,
                -14,
                3,
                9,
                ImagePlane.getPlaneWithChannel(788, 4, 47)
        );

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(rectangle).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }

    @Test
    void Check_Ellipse_With_XY_Coordinates() {
        Ellipse ellipse = new Ellipse();
        ellipse.setY(65.);
        ellipse.setRadiusX(3.324);
        ellipse.setRadiusY(234.234);
        ROI expectedRoi = ROIs.createEllipseROI(-3.324, 65.-234.234, 3.324*2, 234.234*2);

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(ellipse).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }

    @Test
    void Check_Label_With_XY_Coordinates() {
        Label label = new Label();
        label.setY(65.);
        ROI expectedRoi = ROIs.createPointsROI(0, 65.);

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(label).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }

    @Test
    void Check_Line_With_XY_Coordinates() {
        Line line = new Line();
        line.setY1(65.);
        line.setX2(-7.57484);
        line.setY2(.864);
        ROI expectedRoi = ROIs.createLineROI(0, 65., -7.57484, .864);

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(line).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }

    @Test
    void Check_Point_With_XY_Coordinates() {
        Point point = new Point();
        point.setY(65.);
        ROI expectedRoi = ROIs.createPointsROI(0, 65.);

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(point).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }

    @Test
    void Check_Polygon_With_XY_Coordinates() {
        Polygon polygon = new Polygon();
        polygon.setPoints("2.45,.5748 4.46,7.56 -5,9.6");
        ROI expectedRoi = ROIs.createPolygonROI(List.of(
                new Point2(2.45, .5748),
                new Point2(4.46, 7.56),
                new Point2(-5, 9.6)
        ));

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(polygon).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }

    @Test
    void Check_PolyLine_With_XY_Coordinates() {
        Polyline polyline = new Polyline();
        polyline.setPoints("2.45,.5748 4.46,7.56 -5,9.6");
        ROI expectedRoi = ROIs.createPolylineROI(List.of(
                new Point2(2.45, .5748),
                new Point2(4.46, 7.56),
                new Point2(-5, 9.6)
        ));

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(polyline).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }

    @Test
    void Check_Mask_With_XY_Coordinates() {
        Mask mask = new Mask();
        mask.setX(5.);
        mask.setY(6.);
        mask.setWidth(6.);
        mask.setHeight(8.);
        BinData binData = new BinData();
        binData.setBase64Binary(new byte[] {
                0, 1, 1,
                0, 1, 1,
                0, 1, 1
        });
        mask.setBinData(binData);
        ROI expectedRoi = ROIs.createRectangleROI(6, 6, 2, 3);

        ROI roi = BioFormatsShapeConverter.convertShapeToRoi(mask).orElse(null);

        Assertions.assertEquals(expectedRoi, roi);
    }
}
