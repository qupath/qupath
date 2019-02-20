package qupath.lib.roi.jts;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.ShapeReader;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.simplify.VWSimplifier;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathLine;
import qupath.lib.roi.interfaces.PathPoints;
import qupath.lib.roi.interfaces.ROI;

/**
 * Convert between QuPath {@code ROI} objects and Java Topology Suite {@code Geometry} objects.
 *
 * @author Pete Bankhead
 *
 */
public class ConverterJTS {

    private GeometryFactory factory;

    private double pixelHeight, pixelWidth;
    private double flatness = 1;

    private AffineTransform transform = null;

    private ShapeReader shapeReader;
    private ShapeWriter shapeWriter;
    
    private static ConverterJTS DEFAULT_INSTANCE = new Builder().build();
    
    /**
     * Convert a JTS Geometry to a QuPath ROI.
     * @param geometry
     * @return
     */
    public static ROI convertGeometryToROI(Geometry geometry, ImagePlane plane) {
    	return DEFAULT_INSTANCE.geometryToROI(geometry, plane);
    }
    
    /**
     * Convert to QuPath ROI to a JTS Geometry.
     * @param roi
     * @return
     */
    public static Geometry convertROIToGeometry(ROI roi) {
    	return DEFAULT_INSTANCE.roiToGeometry(roi);
    }

    /**
     * Convert a JTS Geometry to a java.awt.Shape.
     * @param geometry
     * @return
     */
    public static Shape convertROIToShape(Geometry geometry) {
    	return DEFAULT_INSTANCE.geometryToShape(geometry);
    }

    
    public static class Builder {
    	
    	private GeometryFactory factory;

        private double pixelHeight = 1;
        private double pixelWidth = 1;
        private double flatness = 0.5;
    	
    	public Builder() {}
    	
    	public Builder pixelSize(double pixelWidth, double pixelHeight) {
    		this.pixelWidth = pixelWidth;
    		this.pixelHeight = pixelHeight;
    		return this;
    	}
    	
    	public Builder flatness(double flatness) {
    		this.flatness = flatness;
    		return this;
    	}
    	
    	public Builder factory(GeometryFactory factory) {
    		this.factory = factory;
    		return this;
    	}
    	
    	public ConverterJTS build() {
    		return new ConverterJTS(factory, pixelWidth, pixelHeight, flatness);
    	}
    	
    }
    

    private ConverterJTS(final GeometryFactory factory, final double pixelWidth, final double pixelHeight, final double flatness) {
        this.factory = factory == null ? new GeometryFactory() : factory;
        this.flatness = flatness;
        this.pixelHeight = pixelHeight;
        this.pixelWidth = pixelWidth;
        if (pixelWidth != 1 && pixelHeight != 1)
            transform = AffineTransform.getScaleInstance(pixelWidth, pixelHeight);
    }

    /**
     * Convert a QuPath ROI to a JTS Geometry.
     * 
     * @param roi
     * @return
     */
    public Geometry roiToGeometry(ROI roi) {
    	if (roi instanceof PathPoints)
    		return pointsToGeometry((PathPoints)roi);
    	if (roi instanceof PathArea)
    		return areaToGeometry((PathArea)roi);
    	if (roi instanceof PathLine)
    		return lineToGeometry((PathLine)roi);
    	throw new UnsupportedOperationException("Unknown ROI " + roi + " - cannot convert to a Geometry!");
    }
    
    private Geometry lineToGeometry(PathLine roi) {
    	var coords = roi.getPolygonPoints().stream().map(p -> new Coordinate(p.getX(), p.getY())).toArray(Coordinate[]::new);
    	return factory.createLineString(coords);
    }
    
    private Geometry areaToGeometry(PathArea roi) {
    	Shape shape = PathROIToolsAwt.getArea(roi);
    	PathIterator iterator = shape.getPathIterator(transform, flatness);
    	// Use simplifier to ensure a valid geometry
    	return VWSimplifier.simplify(getShapeReader().read(iterator), 0);
    }
    
    private Geometry pointsToGeometry(PathPoints points) {
    	var coords = points.getPointList().stream().map(p -> new Coordinate(p.getX(), p.getY())).toArray(Coordinate[]::new);
    	if (coords.length == 1)
    		return factory.createPoint(coords[0]);
    	return factory.createMultiPoint(coords);
    }


    private ShapeReader getShapeReader() {
        if (shapeReader == null)
            shapeReader = new ShapeReader(factory);
        return shapeReader;
    }


    private ShapeWriter getShapeWriter() {
        if (shapeWriter == null)
            shapeWriter = new ShapeWriter(new Transformer());
        return shapeWriter;
    }


    private CoordinateSequence toCoordinates(PolygonROI roi) {
        CoordinateList list = new CoordinateList();
        for (Point2 p : roi.getPolygonPoints())
            list.add(new Coordinate(p.getX() * pixelWidth, p.getY() * pixelHeight));
        return new CoordinateArraySequence(list.toCoordinateArray());
    }

    public Shape geometryToShape(Geometry geometry) {
        return getShapeWriter().toShape(geometry);
    }

    public ROI geometryToROI(Geometry geometry, ImagePlane plane) {
        return PathROIToolsAwt.getShapeROI(geometryToShape(geometry), plane, flatness);
    }


    private class Transformer implements PointTransformation {

        @Override
        public void transform(Coordinate src, Point2D dest) {
            dest.setLocation(
                    src.x / pixelWidth,
                    src.y / pixelHeight);
        }

    }


}