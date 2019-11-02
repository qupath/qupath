package qupath.lib.roi;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.awt.GeometryCollectionShape;
import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.ShapeReader;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.locationtech.jts.util.GeometricShapeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.interfaces.ROI;

/**
 * Convert between QuPath {@code ROI} objects and Java Topology Suite {@code Geometry} objects.
 *
 * @author Pete Bankhead
 */
public class GeometryTools {
	
	private static Logger logger = LoggerFactory.getLogger(GeometryTools.class);
    
    private static GeometryConverter DEFAULT_INSTANCE = new GeometryConverter.Builder().build();
    
    /**
     * Convert a java.awt.Shape to a JTS Geometry.
     * @param shape
     * @return
     */
    public static Geometry shapeToGeometry(Shape shape) {
//    	System.err.println("Shape area: " + new ClosedShapeStatistics(shape).getArea());
    	var geometry = DEFAULT_INSTANCE.shapeToGeometry(shape);
//    	System.err.println("Geometry area: " + geometry.getArea());
    	return geometry;
//    	return ShapeReader.read(shape, DEFAULT_INSTANCE.flatness, DEFAULT_INSTANCE.factory);
    }
    
    /**
     * Convert a JTS Geometry to a QuPath ROI.
     * @param geometry
     * @return
     */
    public static ROI geometryToROI(Geometry geometry, ImagePlane plane) {
    	return DEFAULT_INSTANCE.geometryToROI(geometry, plane);
    }
    
    /**
     * Convert to QuPath ROI to a JTS Geometry.
     * @param roi
     * @return
     */
    public static Geometry roiToGeometry(ROI roi) {
    	return DEFAULT_INSTANCE.roiToGeometry(roi);
    }

    /**
     * Convert a JTS Geometry to a java.awt.Shape.
     * @param geometry
     * @return
     */
    public static Shape geometryToShape(Geometry geometry) {
    	return DEFAULT_INSTANCE.geometryToShape(geometry);
    }
    
    /**
     * Convert an ImageRegion to a rectangular Geometry.
     * The z-position is retained, but timepoint is lost.
     * @param region
     * @return
     */
    public static Geometry regionToGeometry(ImageRegion region) {
    	var coords = new Coordinate[5];
    	coords[0] = new Coordinate(region.getMinX(), region.getMinY(), region.getZ());
    	coords[1] = new Coordinate(region.getMaxX(), region.getMinY(), region.getZ());
    	coords[2] = new Coordinate(region.getMaxX(), region.getMaxY(), region.getZ());
    	coords[3] = new Coordinate(region.getMinX(), region.getMaxY(), region.getZ());
    	coords[4] = coords[0];
    	return DEFAULT_INSTANCE.factory.createPolygon(coords);
    }
    
    
    /**
     * Calculate the union of multiple Geometry objects.
     * @param geometries
     * @return
     */
    public static Geometry union(Collection<Geometry> geometries) {
    	return UnaryUnionOp.union(geometries);
    }
    
    
    /**
     * Remove small fragments and fill holes within a Geometry.
     * 
     * @param geometry
     * @param minSizePixels
     * @param minHoleSizePixels
     * @return the refined geometry (possibly the original unchanged), or null if the changes resulted in the Geometry disappearing
     */
    public static Geometry refineAreas(Geometry geometry, double minSizePixels, double minHoleSizePixels) {
		if (geometry.isEmpty())
			return null;
		
		if (minSizePixels <= 0 && minHoleSizePixels <= 0)
			return geometry;
		
		if (geometry.getNumGeometries() == 1) {
			// Check area
			if (minSizePixels > 0 && geometry.getArea() < minSizePixels)
				return null;
			// Check interior holes if we have a polygon
			if (minHoleSizePixels > 0 && geometry instanceof Polygon) {
				var polygon = (Polygon)geometry;
				int nHoles = polygon.getNumInteriorRing();
				if (nHoles == 0)
					return geometry;
				
				List<LinearRing> holes = new ArrayList<>();
				for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
					var ring = polygon.getInteriorRingN(i);
					if (org.locationtech.jts.algorithm.Area.ofRing(ring.getCoordinateSequence()) >= minHoleSizePixels) {
						holes.add(toLinearRing(ring));
					}
				}
				if (nHoles == holes.size())
					return geometry;
				else
					return geometry.getFactory().createPolygon(
							toLinearRing(polygon.getExteriorRing()),
							holes.toArray(LinearRing[]::new));
			}
			return geometry;
		}
		
		List<Geometry> collection = new ArrayList<>();
		for (int i = 0; i < geometry.getNumGeometries(); i++) {
			var temp = refineAreas(geometry.getGeometryN(i), minSizePixels, minHoleSizePixels);
			if (temp != null)
				collection.add(temp);
		}
		if (collection.isEmpty())
			return null;
		if (collection.size() == 1)
			return collection.get(0);
		return geometry.getFactory().buildGeometry(collection);
	}
	
	
	static LinearRing toLinearRing(LineString lineString) {
		if (lineString instanceof LinearRing)
			return (LinearRing)lineString;
		return lineString.getFactory().createLinearRing(lineString.getCoordinateSequence());
	}
	

    
    public static class GeometryConverter {
    	
        private GeometryFactory factory;

        private double pixelHeight, pixelWidth;
        private double flatness = 0.1;

        private AffineTransform transform = null;
        private Transformer transformer;

        private ShapeReader shapeReader;
        
        // ShapeWriter does not appear to be threadsafe!
        // Currently a new instance is now returned, but could instead use a ThreadLocal?
//        private ShapeWriter shapeWriter;
        

	    private GeometryConverter(final GeometryFactory factory, final double pixelWidth, final double pixelHeight, final double flatness) {
	        this.factory = factory == null ? new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE)) : factory;
	    	if (factory == null) {
	        	if (pixelWidth == 1 && pixelHeight == 1)
	        		this.factory = new GeometryFactory(new PrecisionModel(100.0));
	        	else
	        		this.factory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE));
	    	} else
	    		this.factory = factory;
	        this.flatness = flatness;
	        this.pixelHeight = pixelHeight;
	        this.pixelWidth = pixelWidth;
	        if (pixelWidth != 1 && pixelHeight != 1)
	            transform = AffineTransform.getScaleInstance(pixelWidth, pixelHeight);
	        this.transformer = new Transformer();
	    }
	
	    /**
	     * Convert a QuPath ROI to a JTS Geometry.
	     * 
	     * @param roi
	     * @return
	     */
	    public Geometry roiToGeometry(ROI roi) {
	    	if (roi.isPoint())
	    		return pointsToGeometry(roi);
	    	if (roi.isArea())
	    		return areaToGeometry(roi);
	    	if (roi.isLine())
	    		return lineToGeometry(roi);
	    	throw new UnsupportedOperationException("Unknown ROI " + roi + " - cannot convert to a Geometry!");
	    }
	    
	    private Geometry lineToGeometry(ROI roi) {
	    	var coords = roi.getAllPoints().stream().map(p -> new Coordinate(p.getX() * pixelWidth, p.getY() * pixelHeight)).toArray(Coordinate[]::new);
	    	return factory.createLineString(coords);
	    }
	    
	    private Geometry areaToGeometry(ROI roi) {
	    	if (roi.isEmpty())
	    		return factory.createPolygon();
	    	if (roi instanceof EllipseROI) {
	    		var shapeFactory = new GeometricShapeFactory(factory);
	    		shapeFactory.setEnvelope(
	    				new Envelope(
	    						roi.getBoundsX() * pixelWidth,
	    						(roi.getBoundsX()+roi.getBoundsWidth()) * pixelWidth,
	    						roi.getBoundsY() * pixelHeight,
	    						(roi.getBoundsY()+roi.getBoundsHeight()) * pixelHeight)
	    				);
	    		return shapeFactory.createEllipse();
	    	}
	    	Area shape = RoiTools.getArea(roi);
	    	return areaToGeometry(shape);
	    }
	    
	    private Geometry shapeToGeometry(Shape shape) {
	    	if (shape instanceof Area)
	    		return areaToGeometry((Area)shape);
	    	PathIterator iterator = shape.getPathIterator(transform, flatness);
        	return getShapeReader().read(iterator);
	    }
	    
	    private Geometry areaToGeometry(Area shape) {
//	    	Geometry geometry = null;
//	    	if (shape.isSingular()) {
//	        	PathIterator iterator = shape.getPathIterator(transform, flatness);
//	        	geometry = getShapeReader().read(iterator);
//	    	} else {
	    	return convertAreaToGeometry(shape, transform, flatness, factory);
//	    	}
	    	// Use simplifier to ensure a valid geometry
//	    	var error = new IsValidOp(geometry).getValidationError();
//	    	System.err.println(geometry.getArea());
////	    	geometry = GeometrySnapper.snapToSelf(geometry, GeometryS, cleanResult)
//	    	return VWSimplifier.simplify(geometry, 0);    		
	    }
	    
	    
	    /**
	     * Convert a java.awt.geom.Area to a JTS Geometry, trying to correctly distinguish holes.
	     * 
	     * @param area
	     * @param transform
	     * @param flatness
	     * @param factory
	     * @return
	     */
	    private static Geometry convertAreaToGeometry(final Area area, final AffineTransform transform, final double flatness, final GeometryFactory factory) {
	
			List<Geometry> positive = new ArrayList<>();
			List<Geometry> negative = new ArrayList<>();
	
			PathIterator iter = area.getPathIterator(transform, flatness);
	
			CoordinateList points = new CoordinateList();
			
			double areaTempSigned = 0;
			double areaCached = 0;
			
			double precision = 1.0e-4 * flatness;
	//		double minDisplacement2 = precision * precision;
	
			double[] seg = new double[6];
			double startX = Double.NaN, startY = Double.NaN;
			double x0 = 0, y0 = 0, x1 = 0, y1 = 0;
			boolean closed = false;
			while (!iter.isDone()) {
				switch(iter.currentSegment(seg)) {
				case PathIterator.SEG_MOVETO:
					// Log starting positions - need them again for closing the path
					startX = seg[0];
					startY = seg[1];
					x0 = startX;
					y0 = startY;
					iter.next();
					areaCached += areaTempSigned;
					areaTempSigned = 0;
					points.clear();
					points.add(new Coordinate(startX, startY));
					closed = false;
					continue;
				case PathIterator.SEG_CLOSE:
					x1 = startX;
					y1 = startY;
					closed = true;
					break;
				case PathIterator.SEG_LINETO:
					x1 = seg[0];
					y1 = seg[1];
					// We only wand to add a point if the displacement is above a specified tolerance, 
					// because JTS can be very sensitive to any hint of self-intersection - and does not always 
					// like what the PathIterator provides
					var next = new Coordinate(x1, y1);
					if (points.isEmpty() || points.get(points.size()-1).distance(next) > precision)
						points.add(next);
	//				double dx = x1 - points;
	//				double dy = y1 - y0;
	//				if (dx*dx + dy*dy > minDisplacement2)
	//					points.add(new Coordinate(x1, y1));
					else
						logger.trace("Skipping nearby points");
					closed = false;
					break;
				default:
					// Shouldn't happen because of flattened PathIterator
					throw new RuntimeException("Invalid area computation!");
				};
				areaTempSigned += 0.5 * (x0 * y1 - x1 * y0);
				// Add polygon if it has just been closed
				if (closed) {
					points.closeRing();
					Coordinate[] coords = points.toCoordinateArray();
	//				for (Coordinate c : coords)
	//					model.makePrecise(c);
					
					// Need to ensure polygons are valid at this point
					// Sometimes, self-intersections can thwart validity
					Geometry polygon = factory.createPolygon(coords);
					TopologyValidationError error = new IsValidOp(polygon).getValidationError();
					if (error != null) {
						logger.debug("Invalid polygon detected! Attempting to correct {}", error.toString());
						double areaBefore = polygon.getArea();
						double distance = GeometrySnapper.computeOverlaySnapTolerance(polygon);
						Geometry geom = GeometrySnapper.snapToSelf(polygon,
								distance,
								true);
						double areaAfter = geom.getArea();
						if (!GeneralTools.almostTheSame(areaBefore, areaAfter, 0.001)) {
							logger.warn("Unable to fix geometry (area before: {}, area after: {}, tolerance: {})", areaBefore, areaAfter, distance);
							logger.warn("Original geometry: {}", polygon);
							logger.warn("Will attempt to proceed using {}", geom);
						} else {
							logger.debug("Geometry fix looks ok (area before: {}, area after: {})", areaBefore, areaAfter);
						}
						polygon = geom;
					}
					if (areaTempSigned < 0)
						negative.add(polygon);
					else if (areaTempSigned > 0)
						positive.add(polygon);
					// Zero indicates the shape is empty...
				}
				// Update the coordinates
				x0 = x1;
				y0 = y1;
				iter.next();
			}
			// TODO: Can I count on outer polygons and holes always being either positive or negative?
			// Since I'm not sure, I decide here based on signed areas
			areaCached += areaTempSigned;
			List<Geometry> outer;
			List<Geometry> holes;
			if (areaCached < 0) {
				outer = negative;
				holes = positive;
			} else if (areaCached > 0) {
				outer = positive;
				holes = negative;
			} else {
				return factory.createPolygon();
			}
			Geometry geometry = union(outer);
	
			if (!holes.isEmpty()) {
				Geometry hole = union(holes);
				geometry = geometry.difference((Geometry)hole);
			}
			
			// Perform a sanity check using areas
			double computedArea = Math.abs(areaCached);
			double geometryArea = geometry.getArea();
			if (!GeneralTools.almostTheSame(computedArea, geometryArea, 0.01)) {
				double percent = Math.abs(computedArea - geometryArea) / (computedArea/2.0 + geometryArea/2.0) * 100.0;
				logger.warn("Difference in area after JTS conversion! Computed area: {}, Geometry area: {} ({} %%)", Math.abs(areaCached), geometry.getArea(),
						GeneralTools.formatNumber(percent, 3));
			}
			return geometry;
		}
	    
	    
	    private Geometry pointsToGeometry(ROI points) {
	    	var coords = points.getAllPoints().stream().map(p -> new Coordinate(p.getX()*pixelWidth, p.getY()*pixelHeight)).toArray(Coordinate[]::new);
	    	if (coords.length == 1)
	    		return factory.createPoint(coords[0]);
	    	return factory.createMultiPointFromCoords(coords);
	    }
	
	
	    private ShapeReader getShapeReader() {
	        if (shapeReader == null)
	            shapeReader = new ShapeReader(factory);
	        return shapeReader;
	    }
	
	
	    private ShapeWriter getShapeWriter() {
	    	return new ShapeWriter(transformer);
//	        if (shapeWriter == null)
//	            shapeWriter = new ShapeWriter(new Transformer());
//	        return shapeWriter;
	    }
	
	
	//    private CoordinateSequence toCoordinates(PolygonROI roi) {
	//        CoordinateList list = new CoordinateList();
	//        for (Point2 p : roi.getPolygonPoints())
	//            list.add(new Coordinate(p.getX() * pixelWidth, p.getY() * pixelHeight));
	//        return new CoordinateArraySequence(list.toCoordinateArray());
	//    }
	
	    private Shape geometryToShape(Geometry geometry) {
	        var shape = getShapeWriter().toShape(geometry);
	        // JTS Shapes can have some odd behavior (e.g. lack of contains method), so convert to Area if that is a suitable match
	        if (geometry instanceof Polygonal && shape instanceof GeometryCollectionShape)
	        	return new Area(shape);
	        return shape;
	    }
	
	    private ROI geometryToROI(Geometry geometry, ImagePlane plane) {
	    	if (geometry instanceof Point) {
	    		Coordinate coord = geometry.getCoordinate();
	    		return ROIs.createPointsROI(coord.x, coord.y, plane);
	    	} else if (geometry instanceof MultiPoint) {
	    		Coordinate[] coords = geometry.getCoordinates();
	    		List<Point2> points = Arrays.stream(coords).map(c -> new Point2(c.x, c.y)).collect(Collectors.toList());
	    		return ROIs.createPointsROI(points, plane);
	    	}
	    	// For anything complicated, return a Geometry ROI
	    	if (geometry.getNumGeometries() > 1 || (geometry instanceof Polygon && ((Polygon)geometry).getNumInteriorRing() > 0))
	    		return new GeometryROI(geometry, plane);
	    	// Otherwise return a (possibly easier to edit) ROI
	        return RoiTools.getShapeROI(geometryToShape(geometry), plane, flatness);
	    }
	
	
	    private class Transformer implements PointTransformation {
	
	        @Override
	        public void transform(Coordinate src, Point2D dest) {
	            dest.setLocation(
	                    src.x / pixelWidth,
	                    src.y / pixelHeight);
	        }
	
	    }
	    
	    
	    
	    /**
	     * Builder to help define how ROIs and Geometry objects should be converted.
	     */
	    public static class Builder {
	    	
	    	private GeometryFactory factory;

	        private double pixelHeight = 1;
	        private double pixelWidth = 1;
	        private double flatness = 0.5;
	    	
	        /**
	         * Default constructor for a builder with flatness 0.5 and pixel width/height of 1.0.
	         */
	    	public Builder() {}
	    	
	    	/**
	    	 * Specify the pixel width and height, used to scale x and y coordinates during conversion (default is 1.0 for both).
	    	 * @param pixelWidth
	    	 * @param pixelHeight
	    	 * @return
	    	 */
	    	public Builder pixelSize(double pixelWidth, double pixelHeight) {
	    		this.pixelWidth = pixelWidth;
	    		this.pixelHeight = pixelHeight;
	    		return this;
	    	}
	    	
	    	/**
	    	 * Specify the flatness for any operation where a PathIterator is required.
	    	 * @param flatness
	    	 * @return
	    	 */
	    	public Builder flatness(double flatness) {
	    		this.flatness = flatness;
	    		return this;
	    	}
	    	
	    	/**
	    	 * Specify the GeometryFactory, which can define a precision model in JTS.
	    	 * @param factory
	    	 * @return
	    	 */
	    	public Builder factory(GeometryFactory factory) {
	    		this.factory = factory;
	    		return this;
	    	}
	    	
	    	/**
	    	 * Build a new converter with the specified parameters.
	    	 * @return
	    	 */
	    	public GeometryConverter build() {
	    		return new GeometryConverter(factory, pixelWidth, pixelHeight, flatness);
	    	}
	    	
	    }
	    
    }


}