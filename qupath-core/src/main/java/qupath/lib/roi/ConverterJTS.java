package qupath.lib.roi;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.ShapeReader;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import qupath.lib.geom.Point2;
import qupath.lib.roi.PathROIToolsAwt;
import qupath.lib.roi.PolygonROI;
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
	
	private static ConverterJTS converter = new ConverterJTS(1, 1, 0.5);
	
	public static Geometry getGeometry(final ROI roi) {
		return converter.roiToGeometry(roi);
	}
	
	
	ConverterJTS(final double pixelWidth, final double pixelHeight, final double flatness) {
		this(new GeometryFactory(), pixelWidth, pixelHeight, flatness);
	}

	ConverterJTS(final GeometryFactory factory, final double pixelWidth, final double pixelHeight, final double flatness) {
		this.factory = factory;
		this.flatness = flatness;
		this.pixelHeight = pixelHeight;
		this.pixelWidth = pixelWidth;
		if (pixelWidth != 1 && pixelHeight != 1)
			transform = AffineTransform.getScaleInstance(pixelWidth, pixelHeight);
	}
	
	public Geometry roiToGeometry(ROI roi) {
		if (roi.isArea())
			return roiAreaToGeometry(roi);
		if (roi.isLine())
			return roiLineToGeometry(roi);
		if (roi.isPoint())
			return roiPointsToGeometry(roi);
		throw new IllegalArgumentException("Cannot create Geometry from unknown ROI: " + roi);
	}
	
	
	private Geometry roiPointsToGeometry(ROI roi) {
		// Extract the coordinates
		List<Point2> points = roi.getPolygonPoints();
		if (points.isEmpty())
			return factory.createPoint();
		Coordinate coords[] = points.stream().map(p -> new Coordinate(p.getX(), p.getY())).toArray(Coordinate[]::new);
		if (coords.length == 1) {
			return factory.createPoint(coords[0]);
		}
		return factory.createMultiPointFromCoords(coords);
	}
	
	
	private Geometry roiLineToGeometry(ROI roi) {
		// Extract the coordinates
		Shape shape = PathROIToolsAwt.getShape(roi);
	 	PathIterator iterator = shape.getPathIterator(transform, flatness);
		return shapeReader.read(iterator);
	}


	private Geometry roiAreaToGeometry(ROI roi) {
		
//
//		Shape shape = PathROIToolsAwt.getShape(roi);
//		try {
//			Geometry geometry = getShapeReader().read(shape.getPathIterator(transform, flatness));
//			// If the Geometry is already valid, we're done
//			if (geometry.isValid()) {
//				return geometry;
//			}
//		} catch (Exception e) {}
//		
//		// Convert to an Area
//		Area area = new Area(shape);
////		Shape area;
////		if (roi instanceof AreaROI)
////			area = PathROIToolsAwt.getArea(roi);
////		else
//			area = PathROIToolsAwt.getShape(roi);
		
		Area area = PathROIToolsAwt.getArea(roi);

		
		// Extract the coordinates
	 	PathIterator iterator = area.getPathIterator(transform, flatness);
		List<Coordinate[]> list = ShapeReader.toCoordinates(iterator);
		
		// 
		List<Polygon> positivePolygons = new ArrayList<>();
		List<Polygon> negativePolygons = new ArrayList<>();
		for (Coordinate[] coords : list) {
			double signedArea = org.locationtech.jts.algorithm.Area.ofRingSigned(coords);
			if (signedArea > 0)
				positivePolygons.add(factory.createPolygon(coords));
			else if (signedArea < 0) {
				negativePolygons.add(factory.createPolygon(coords));
			}
		}
		
		Geometry positive;
		if (positivePolygons.size() == 1)
			positive = positivePolygons.get(0);
		else
			positive = factory.createMultiPolygon(positivePolygons.toArray(new Polygon[0]));
		
		Geometry negative = null;
		if (negativePolygons.size() == 1)
			negative = negativePolygons.get(0);
		else if (!negativePolygons.isEmpty())
			negative = factory.createMultiPolygon(negativePolygons.toArray(new Polygon[0]));
		
		if (negative != null)
			return positive.difference(negative);
		
		return positive;
	}

	public Geometry roiToGeometrySimple(ROI roi) {
		Area shape = PathROIToolsAwt.getArea(roi);
		PathIterator iterator = shape.getPathIterator(transform, flatness);
		return getShapeReader().read(iterator);
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


	public CoordinateSequence toCoordinates(PolygonROI roi) {
		CoordinateList list = new CoordinateList();
		for (Point2 p : roi.getPolygonPoints())
			list.add(new Coordinate(p.getX() * pixelWidth, p.getY() * pixelHeight));
		return new CoordinateArraySequence(list.toCoordinateArray());
	}

	public Shape geometryToShape(Geometry geometry) {
		return getShapeWriter().toShape(geometry);
	}

	public ROI geometryToROI(Geometry geometry) {
		return PathROIToolsAwt.getShapeROI(geometryToShape(geometry), -1, 0, 0, flatness);
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
