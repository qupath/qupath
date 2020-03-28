package qupath.lib.roi;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator;
import org.locationtech.jts.awt.GeometryCollectionShape;
import org.locationtech.jts.awt.PointTransformation;
import org.locationtech.jts.awt.ShapeReader;
import org.locationtech.jts.awt.ShapeWriter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
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
	
	private final static GeometryFactory DEFAULT_FACTORY = new GeometryFactory(
			new PrecisionModel(100.0),
			0,
			PackedCoordinateSequenceFactory.FLOAT_FACTORY);

	private final static PrecisionModel INTEGER_PRECISION_MODEL = new PrecisionModel(1);
    
    private static GeometryConverter DEFAULT_INSTANCE = new GeometryConverter.Builder()
    		.build();
    
    /**
     * Get the default GeometryFactory to construct Geometries within QuPath.
     * @return
     */
    public static GeometryFactory getDefaultFactory() {
    	return DEFAULT_FACTORY;
    }
    
    
    /**
     * Convert an {@link AffineTransformation} to an {@link AffineTransform}.
     * @param transform
     * @return
     */
    public static AffineTransform convertTransform(AffineTransformation transform) {
    	double[] mat = transform.getMatrixEntries();
    	return new AffineTransform(mat[0], mat[3], mat[1], mat[4], mat[2], mat[5]);
    }
    
    /**
     * Convert an {@link AffineTransform} to an {@link AffineTransformation}.
     * @param transform
     * @return
     */
    public static AffineTransformation convertTransform(AffineTransform transform) {
    	double[] mat = new double[6];
    	transform.getMatrix(mat);
    	return new AffineTransformation(mat[0], mat[2], mat[4], mat[1], mat[3], mat[5]);
    }
    
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
	 * Round coordinates in a Geometry to integer values, and constrain to the specified bounding box.
	 * @param geometry
	 */
	protected Geometry roundAndConstrain(Geometry geometry, double minX, double minY, double maxX, double maxY) {
		geometry = GeometryPrecisionReducer.reduce(geometry, new PrecisionModel(1));
		geometry = TopologyPreservingSimplifier.simplify(geometry, 0.0);
		geometry = geometry.intersection(GeometryTools.createRectangle(minX, minY, maxX-minX, maxY-minY));
		return geometry;
//		roundingFilter.setBounds(minX, minY, maxX, maxY);
//		geometry.apply(roundingFilter);
//		return VWSimplifier.simplify(geometry, 0.5);
	}
	
	
	/**
	 * Round coordinates in a Geometry to integer values.
	 * @param geometry the updated Geometry
	 * @return 
	 */
	public static Geometry roundCoordinates(Geometry geometry) {
		// Warning! This can result in polygons containing holes to lose some holes...
		geometry = GeometryPrecisionReducer.reduce(geometry, INTEGER_PRECISION_MODEL);
		// Remove unnecessary coordinates occurring along straight lines
		geometry = DouglasPeuckerSimplifier.simplify(geometry, 0.0);
		return geometry;
	}
	
	/**
	 * Compute the intersection of a Geometry and a specified bounding box.
	 * The original Geometry <i>may</i> be returned unchanged if no changes are required to fit within the bounds.
	 * @param geometry the updated Geometry
	 * @param x 
	 * @param y 
	 * @param width 
	 * @param height 
	 * @return 
	 */
	public static Geometry constrainToBounds(Geometry geometry, double x, double y, double width, double height) {
		var env = geometry.getEnvelopeInternal();
		if (env.getMinX() < x || env.getMinY() < y || env.getMaxX() >= x + width || env.getMaxY() >= y + height)
			geometry = geometry.intersection(GeometryTools.createRectangle(x, y, width, height));
		return geometry;
	}
	
    
    /**
     * Create a rectangular Geometry for the specified bounding box.
     * @param x
     * @param y
     * @param width
     * @param height
     * @return
     */
    public static Geometry createRectangle(double x, double y, double width, double height) {
    	var shapeFactory = new GeometricShapeFactory(DEFAULT_FACTORY);
		shapeFactory.setEnvelope(
				new Envelope(
						x, x+width, y, y+height)
				);
		return shapeFactory.createRectangle();
    }
    
    
    /**
     * Convert a JTS Geometry to a QuPath ROI.
     * @param geometry
     * @param plane 
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
    	coords[0] = DEFAULT_INSTANCE.createCoordinate(region.getMinX(), region.getMinY(), region.getZ());
    	coords[1] = DEFAULT_INSTANCE.createCoordinate(region.getMaxX(), region.getMinY(), region.getZ());
    	coords[2] = DEFAULT_INSTANCE.createCoordinate(region.getMaxX(), region.getMaxY(), region.getZ());
    	coords[3] = DEFAULT_INSTANCE.createCoordinate(region.getMinX(), region.getMaxY(), region.getZ());
    	coords[4] = coords[0];
    	return DEFAULT_INSTANCE.factory.createPolygon(coords);
    }
    
    
    /**
     * Calculate the union of multiple Geometry objects.
     * @param geometries
     * @return
     */
    public static Geometry union(Collection<? extends Geometry> geometries) {
    	return union(geometries, false);
    }
    
    
    /**
     * Calculate the union of multiple Geometry objects.
     * @param geometries
     * @param fastUnion if true, it can be assumed that the Geometries are valid and cannot overlap. This may permit a faster union operation.
     * @return
     */
    private static Geometry union(Collection<? extends Geometry> geometries, boolean fastUnion) {
    	if (geometries.isEmpty())
    		return DEFAULT_INSTANCE.factory.createPolygon();
    	if (geometries.size() == 1)
    		return geometries.iterator().next();
    	if (fastUnion) {
    		var geometryArray = geometries.toArray(Geometry[]::new);
    		double areaSum = Arrays.stream(geometryArray).mapToDouble(g -> g.getArea()).sum();
    		var union = DEFAULT_INSTANCE.factory.createGeometryCollection(geometryArray).buffer(0);
    		double areaUnion = Arrays.stream(geometryArray).mapToDouble(g -> g.getArea()).sum();
    		if (GeneralTools.almostTheSame(areaSum, areaUnion, 0.00001)) {
    			return union;
    		}
    		logger.warn("Fast union failed with different areas ({} before vs {} after)", areaSum, areaUnion);
    	}
    	try {
    		return UnaryUnionOp.union(geometries);
    	} catch (Exception e) {
    		// Throw exception if we have no other options
    		if (fastUnion)
    			throw e;
    		else {
    			// Try again with other path
    			logger.warn("Exception attempting default union: {}", e.getLocalizedMessage());
    			return union(geometries, true);
    		}
    	}
    }
    
    /**
     * Strip non-polygonal parts from a GeometryCollection (non-recursive).
     * @param geometry
     * @return a Geometry containing only Polygons, which may be the same as the input Geometry or empty
     */
    public static Geometry ensurePolygonal(Geometry geometry) {
    	if (geometry instanceof Polygonal)
    		return geometry;
    	if (!(geometry instanceof GeometryCollection))
    		return geometry.getFactory().createPolygon();
    		
    	List<Geometry> keepGeometries = new ArrayList<>();
		for (int i = 0; i < geometry.getNumGeometries(); i++) {
			if (geometry.getGeometryN(i) instanceof Polygonal) {
				keepGeometries.add(geometry.getGeometryN(i));
			}
		}
		if (keepGeometries.isEmpty())
			return geometry.getFactory().createPolygon();
		if (keepGeometries.size() < geometry.getNumGeometries())
			return geometry.getFactory().buildGeometry(keepGeometries);
		else
			return geometry;
    }
    
    /**
     * Ensure a GeometryCollection contains only Geometries of the same type (Polygonal, Lineal or Puntal).
     * Other geometries (with lower dimension) are discarded.
     * @param geometry
     * @return
     */
    public static Geometry homogenizeGeometryCollection(Geometry geometry) {
    	if (geometry instanceof Polygonal || geometry instanceof Puntal || geometry instanceof Lineal) {
    		return geometry;
    	}
    	boolean hasPolygons = false;
    	boolean hasLines = false;
    	boolean hasPoints = false;
    	List<Geometry> collection = new ArrayList<>();
    	for (int i = 0; i < geometry.getNumGeometries(); i++) {
    		var geom = homogenizeGeometryCollection(geometry.getGeometryN(i));
    		if (geom instanceof Polygonal) {
    			if (!hasPolygons)
    				collection.clear();
   				collection.add(geom);
    			hasPolygons = true;
    		} else if (geom instanceof Lineal) {
    			if (hasPolygons)
    				continue;
    			if (!hasLines)
    				collection.clear();
   				collection.add(geom);
    			hasLines = true;
    		} else if (geom instanceof Puntal) {
    			if (hasPolygons || hasLines)
    				continue;
    			collection.add(geom);
    			hasPoints = true;
    		}
    	}
    	if (collection.size() == geometry.getNumGeometries())
    		return geometry;
    	return geometry.getFactory().buildGeometry(collection);
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
	    	if (factory == null) {
	        	if (pixelWidth == 1 && pixelHeight == 1)
	        		this.factory = DEFAULT_FACTORY;
	        	else
	        		this.factory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE), 0, PackedCoordinateSequenceFactory.FLOAT_FACTORY);
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
	    	var coords = roi.getAllPoints().stream().map(p -> createCoordinate(p.getX() * pixelWidth, p.getY() * pixelHeight)).toArray(Coordinate[]::new);
	    	return factory.createLineString(coords);
	    }
	    
	    private Coordinate createCoordinate(double x, double y) {
	    	var precisionModel = factory.getPrecisionModel();
	    	return new CoordinateXY(precisionModel.makePrecise(x), precisionModel.makePrecise(y));
	    }
	    
	    private Coordinate createCoordinate(double x, double y, double z) {
	    	var precisionModel = factory.getPrecisionModel();
	    	return new Coordinate(precisionModel.makePrecise(x), precisionModel.makePrecise(y), precisionModel.makePrecise(z));
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
			
			PrecisionModel precisionModel = factory.getPrecisionModel();
			
			double areaTempSigned = 0;
			double areaCached = 0;
			
			// Helpful for debugging where errors in conversion may occur
			double areaPositive = 0;
			double areaNegative = 0;
			
			double precision = 1.0e-4 * flatness;
	//		double minDisplacement2 = precision * precision;
			
			int totalCount = 0;
			int errorCount = 0;
	
			double[] seg = new double[6];
			double startX = Double.NaN, startY = Double.NaN;
			double x0 = 0, y0 = 0, x1 = 0, y1 = 0;
			boolean closed = false;
			while (!iter.isDone()) {
				switch(iter.currentSegment(seg)) {
				case PathIterator.SEG_MOVETO:
					// Log starting positions - need them again for closing the path
					startX = precisionModel.makePrecise(seg[0]);
					startY = precisionModel.makePrecise(seg[1]);
					x0 = startX;
					y0 = startY;
					iter.next();
					areaCached += areaTempSigned;
					areaTempSigned = 0;
					points.clear();
					points.add(new CoordinateXY(startX, startY));
					closed = false;
					continue;
				case PathIterator.SEG_CLOSE:
					x1 = startX;
					y1 = startY;
					closed = true;
					break;
				case PathIterator.SEG_LINETO:
					x1 = precisionModel.makePrecise(seg[0]);
					y1 = precisionModel.makePrecise(seg[1]);
					// We only wand to add a point if the displacement is above a specified tolerance, 
					// because JTS can be very sensitive to any hint of self-intersection - and does not always 
					// like what the PathIterator provides
					var next = new CoordinateXY(x1, y1);
					if (points.isEmpty() || points.get(points.size()-1).distance(next) > precision)
						points.add(next, false);
	//				double dx = x1 - points;
	//				double dy = y1 - y0;
	//				if (dx*dx + dy*dy > minDisplacement2)
	//					points.add(new CoordinateXY(x1, y1));
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
				if (closed && points.size() == 1) {
					logger.warn("Cannot create polygon from cordinate array of length 1!");
				} else if (closed) {
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

//						// Faster method of fixing polygons... main disadvantage is that it doesn't always work
//						var polygonizer = new Polygonizer();
//						var union = factory.createLineString(coords).union(factory.createPoint(coords[0]));
//						polygonizer.add(union);
//						var polygons = polygonizer.getPolygons();
//						var iter2 = polygons.iterator();
//						Geometry geom = (Geometry)iter2.next();
//						while (iter2.hasNext())
//							geom = geom.symDifference((Geometry)iter2.next());
////						factory.buildGeometry(polygons);
						
						// Try fast buffer trick to make valid (but sometimes this can 'break', e.g. with bow-tie shapes)
						Geometry geom = polygon.buffer(0);
						double areaAfter = geom.getArea();
						if (!GeneralTools.almostTheSame(areaBefore, areaAfter, 0.0001)) {
							// Resort to the slow method of fixing polygons if we have to
							logger.debug("Unable to fix Geometry with buffer(0) - will try snapToSelf instead");
							double distance = GeometrySnapper.computeOverlaySnapTolerance(polygon);
							geom = GeometrySnapper.snapToSelf(polygon,
									distance,
									true);
							areaAfter = geom.getArea();
						}
						
						// Sanity check & warning if something went wrong
						if (!GeneralTools.almostTheSame(areaBefore, areaAfter, 0.0001)) {
							logger.warn("Unable to fix geometry (area before: {}, area after: {})", areaBefore, areaAfter);
							logger.trace("Original geometry: {}", polygon);
							logger.trace("Will attempt to proceed using {}", geom);
						} else {
							logger.debug("Geometry fix looks ok (area before: {}, area after: {})", areaBefore, areaAfter);
						}
						polygon = geom;
						errorCount++;
					}
					totalCount++;
					if (areaTempSigned < 0) {
						areaNegative += areaTempSigned;
						for (int i = 0; i < polygon.getNumGeometries(); i++) {
							Polygon p = (Polygon)polygon.getGeometryN(i);
							negative.add(p);
						}
					} else if (areaTempSigned > 0) {
						areaPositive += areaTempSigned;
						for (int i = 0; i < polygon.getNumGeometries(); i++) {
							Polygon p = (Polygon)polygon.getGeometryN(i);
							positive.add(p);
						}
					}
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
			double areaOuter;
			double areaHoles;
			if (areaCached < 0) {
				areaOuter = -areaNegative;
				areaHoles = areaPositive;
				outer = negative;
				holes = positive;
			} else if (areaCached > 0) {
				areaOuter = areaPositive;
				areaHoles = -areaNegative;
				outer = positive;
				holes = negative;
			} else {
				return factory.createPolygon();
			}
			
			Geometry geometry;
			Geometry geometryOuter;
			if (holes.isEmpty()) {
				// If we have no holes, just use the outer geometry
				geometryOuter = union(outer, true);
				geometry = geometryOuter;
			} else if (outer.size() == 1) {
				// If we just have one outer geometry, remove all the holes
				geometryOuter = union(outer, true);
				geometry = geometryOuter.difference(union(holes, true));
			} else {
				// We need to handle holes... and, in particular, additional objects that may be nested within holes.
				// To do that, we iterate through the holes and try to match these with the containing polygon, updating it accordingly.
				// By doing this in order (largest first) we should find the 'correct' containing polygon.
				
				// Cache areas so we can use them for sorting without recalculating them every time
				var areaMap = new HashMap<Geometry, Double>();
				for (var g : outer)
					areaMap.put(g, g.getArea());
				for (var g : holes)
					areaMap.put(g, g.getArea());
				
				// For each hole, find the smallest polygon that contains it
				var ascendingArea = Comparator.comparingDouble(g -> areaMap.get(g));
				outer.sort(ascendingArea);
				holes.sort(ascendingArea);
				Map<Geometry, List<Geometry>> matches = new HashMap<>();
				for (var tempHole : holes) {
					double holeArea = areaMap.get(tempHole);
					// We assume a single point inside is sufficient because polygons should be non-overlapping
					var point = tempHole.getCoordinate();
					var iterOuter = outer.iterator();
					int count = 0;
					while (iterOuter.hasNext()) {
						var tempOuter = iterOuter.next();
						if (holeArea > areaMap.get(tempOuter)) {
							continue;
						}
						if (SimplePointInAreaLocator.isContained(point, tempOuter)) {
							var list = matches.get(tempOuter);
							if (list == null) {
								list = new ArrayList<>();
								matches.put(tempOuter, list);
							}
							list.add(tempHole);
							break;
						}
					}
				}
				
				// Loop through the outer polygons and remove all their holes
				List<Geometry> fixedGeometries = new ArrayList<>();
				for (var tempOuter : outer) {
					var list = matches.getOrDefault(tempOuter, null);
					if (list == null || list.isEmpty()) {
						fixedGeometries.add(tempOuter);
					} else {
						var mergedHoles = union(list);
						fixedGeometries.add(tempOuter.difference(mergedHoles));
					}
				}
				geometry = union(fixedGeometries);
				geometryOuter = geometry;
				
				
//				var comparator = Comparator.comparingDouble(g -> areaMap.get(g)).reversed();
//				outer.sort(comparator);
//				holes.sort(comparator);
//				for (var tempHole : holes) {
//					double holeArea = areaMap.get(tempHole);
//					// We assume a single point inside is sufficient because polygons should be non-overlapping
//					var point = tempHole.getCoordinate();
//					var iterOuter = outer.iterator();
//					while (iterOuter.hasNext()) {
//						var tempOuter = iterOuter.next();
//						if (holeArea > areaMap.get(tempOuter)) {
//							logger.warn("No polygons found containing hole!");
//							break;
//						}
//						if (SimplePointInAreaLocator.isContained(point, tempOuter)) {
//							iterOuter.remove();
//							var temp = tempOuter.difference(tempHole);
//							areaMap.put(temp, temp.getArea());
//							outer.add(temp);
//							outer.sort(comparator);
//							break;
//						}
//						if (!iterOuter.hasNext())
//							logger.warn("No polygons found containing hole!");
//					}
//				}
//				geometry = union(outer);
//				geometryOuter = geometry;
			}
			
			// Perform a sanity check using areas
			double computedArea = Math.abs(areaCached);
			double geometryArea = geometry.getArea();
			if (!GeneralTools.almostTheSame(computedArea, geometryArea, 0.01)) {
				logger.debug("{}/{} geometries had topology validation errors", errorCount, totalCount);
//				try {
//					double geometryAreaSum = 0;
//					for (var g : outer)
//						geometryAreaSum += g.getArea();
//					double geometryHoleAreaSum = 0;
//					for (var g : holes)
//						geometryHoleAreaSum += g.getArea();
//					System.err.println("Positive area: " + areaOuter + ", Geometry: " + geometryOuter.getArea() + ", summed Geometry area: " + geometryAreaSum);
//					System.err.println("Holes area: " + areaHoles + ", Geometry: " + (geometryHoles == null ? 0 : geometryHoles.getArea()) + ", summed Geometry area: " + geometryHoleAreaSum);
//					String dirTemp = "";
//					ImageIO.write(BufferedImageTools.createROIMask(area, 1), "PNG", new File(dirTemp, "Shape mask.png"));
//					ImageIO.write(BufferedImageTools.createROIMask(GeometryTools.geometryToShape(geometry), 1), "PNG", new File(dirTemp, "Geometry mask.png"));
//					ImageIO.write(BufferedImageTools.createROIMask(GeometryTools.geometryToShape(geometryOuter), 1), "PNG", new File(dirTemp, "Geometry outer mask.png"));
//					if (!holes.isEmpty())
//						ImageIO.write(BufferedImageTools.createROIMask(GeometryTools.geometryToShape(union(holes)), 1), "PNG", new File(dirTemp, "Geometry holes mask.png"));
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				double percent = Math.abs(computedArea - geometryArea) / (computedArea/2.0 + geometryArea/2.0) * 100.0;
				logger.warn("Difference in area after JTS conversion! Computed area: {}, Geometry area: {} ({} %%)", Math.abs(areaCached), geometry.getArea(),
						GeneralTools.formatNumber(percent, 3));
			}
			return geometry;
		}
	    
	    
	    private Geometry pointsToGeometry(ROI points) {
	    	var coords = points.getAllPoints().stream().map(p -> createCoordinate(p.getX()*pixelWidth, p.getY()*pixelHeight))
	    			.toArray(Coordinate[]::new);
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
	    	// Make sure out Geometry is all of the same type
	    	var geometry2 = homogenizeGeometryCollection(geometry);
	    	if (geometry2 != geometry) {
	    		logger.warn("Geometries must all be of the same type! Converted {} to {}.", geometry.getGeometryType(), geometry2.getGeometryType());
	    		geometry = geometry2;
	    	}
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