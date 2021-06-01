/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.roi;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Function;
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
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.PolygonExtracter;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
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
     * Parse the matrix (String) to create and return an {@link AffineTransformation}. 
     * <p>
     * The order of the matrix elements should be the following:<p>
     * <li>{@code m00 m01 m02}<p></li>
     * <li>{@code m10 m11 m12}<p></li>
     * 
     * @param text
     * @return affineTransformation
     * @throws ParseException 
     */
    public static AffineTransformation parseTransformMatrix(String text) throws ParseException {
    	String delims = "\n\t ";
		// If we have any periods, then use a comma as an acceptable delimiter as well
		if (text.contains(".")) {
			delims += ",";
			// Flatten the matrix
			text = text.replace(System.lineSeparator(), ",");
		} else
			text = text.replace(System.lineSeparator(), " ");

		var nf = NumberFormat.getInstance(Locale.getDefault());
		var tokens = new StringTokenizer(text, delims);
		if (tokens.countTokens() != 6)
			throw new IllegalArgumentException("Affine transform should be tab-delimited and contain 6 numbers only");

		double m00 = nf.parse(tokens.nextToken()).doubleValue();
		double m01 = nf.parse(tokens.nextToken()).doubleValue();
		double m02 = nf.parse(tokens.nextToken()).doubleValue();
		double m10 = nf.parse(tokens.nextToken()).doubleValue();
		double m11 = nf.parse(tokens.nextToken()).doubleValue();
		double m12 = nf.parse(tokens.nextToken()).doubleValue();
		return new AffineTransformation(m00, m01, m02, m10, m11, m12);
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
    
    
//    /**
//	 * Round coordinates in a Geometry to integer values, and constrain to the specified bounding box.
//	 * @param geometry
//     * @param minX 
//     * @param minY 
//     * @param maxX 
//     * @param maxY 
//     * @return 
//	 */
//	protected Geometry roundAndConstrain(Geometry geometry, double minX, double minY, double maxX, double maxY) {
//		geometry = GeometryPrecisionReducer.reduce(geometry, new PrecisionModel(1));
//		geometry = TopologyPreservingSimplifier.simplify(geometry, 0.0);
//		geometry = geometry.intersection(GeometryTools.createRectangle(minX, minY, maxX-minX, maxY-minY));
//		return geometry;
////		roundingFilter.setBounds(minX, minY, maxX, maxY);
////		geometry.apply(roundingFilter);
////		return VWSimplifier.simplify(geometry, 0.5);
//	}
	
	
    /**
     * Convert an {@link Envelope} to an {@link ImageRegion}.
     * @param env envelop
     * @param z z index for the region (default is 0)
     * @param t timepoint for the region (default is 0)
     * @return the smallest {@link ImageRegion} that contains the specified envelop
     */
	public static ImageRegion envelopToRegion(Envelope env, int z, int t) {
		int x = (int)Math.floor(env.getMinX());
		int y = (int)Math.floor(env.getMinY());
		int width = (int)Math.ceil(env.getMaxX()) - x;
		int height = (int)Math.ceil(env.getMaxY()) - y;
		return ImageRegion.createInstance(x, y, width, height, z, t);
	}
	
	
	/**
	 * Attempt to apply a function to a geometry, returning the input unchanged if there was an exception.
	 * <p>
	 * The purpose of this is to make it easier to apply non-essential functions that might fail (e.g. with a {@link TopologyException} 
	 * and to recover easily.
	 * 
	 * @param input the input geometry
	 * @param fun the function to (attempt) to apply
	 * @return the new geometry if the function succeeded, otherwise the original geometry
	 */
	public static Geometry attemptOperation(Geometry input, Function<Geometry, Geometry> fun) {
		try {
			return fun.apply(input);
		} catch (Exception e) {
			logger.debug(e.getLocalizedMessage(), e);
			return input;
		}
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
    		double areaSum = geometries.stream().mapToDouble(g -> g.getArea()).sum();
    		var union = DEFAULT_INSTANCE.factory.buildGeometry(geometries).buffer(0);
    		double areaUnion = union.getArea();
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
    	
    	// Create an empty polygon if we have something else (e.g. points, lines)
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
//    	boolean hasPoints = false;
    	List<Geometry> collection = new ArrayList<>();
    	for (var geom : flatten(geometry, null)) {
    		geom = homogenizeGeometryCollection(geom);
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
//    			hasPoints = true;
    		}
    	}
//    	if (collection.size() == geometry.getNumGeometries())
//    		return geometry;
    	// Factory helps to ensure we have the correct type (e.g. MultiPolygon rather than GeometryCollection)
    	return geometry.getFactory().buildGeometry(collection);
    }
    
    private static List<Geometry> flatten(Geometry geometry, List<Geometry> list) {
    	if (list == null) {
    		list = new ArrayList<>();
    	}
    	for (int i = 0; i < geometry.getNumGeometries(); i++) {
    		var geom = geometry.getGeometryN(i);
    		if (geom instanceof GeometryCollection)
    			flatten(geom, list);
    		else
    			list.add(geom);
    	}
    	return list;
    }
    
    /**
     * Fill all interior rings for the specified geometry that have an area &lt; a specified threshold.
     * <p>
     * Note that this assumes that the geometry is valid, and does not contain self-intersections or overlapping pieces. 
     * No checks are made to confirm this (for performance reasons).
     * 
     * @param geometry
     * @param minRingArea
     * @return
     */
    public static Geometry removeInteriorRings(Geometry geometry, double minRingArea) {
    	if (minRingArea <= 0)
    		return geometry;
    	
    	// Single polygons are easy... just remove the interior rings
    	if (geometry instanceof Polygon)
    		return removeInteriorRings((Polygon)geometry, minRingArea, null);
    	
//    	// Quick check to see if we are filling all holes - this is also rather a lot easier
//    	if (!Double.isFinite(minRingArea))
//    		return fillHoles(geometry);
    	
    	// Remove interior rings that are too small, logging their location in case we need it
    	// Also keep a list of small geometries, which might be inside rings that have been removed
    	var list = flatten(geometry, null);
    	var smallGeometries = new HashSet<Geometry>();
    	Quadtree tree = null;
    	var preparedFactory = new PreparedGeometryFactory();
    	
    	var removedRingList = new ArrayList<LinearRing>();
    	for (int i = 0; i < list.size(); i++) {
    		var temp = list.get(i);
    		if (temp instanceof Polygon) {
    			var poly = removeInteriorRings((Polygon)temp, minRingArea, removedRingList);
    			if (poly != temp) {
    				// If the polygon has changed, we need to check if it has swallowed any nested holes
    				// TODO: Add the holes rather than the full polygons
    				if (tree == null)
    					tree = new Quadtree();
    				for (var ring : removedRingList) {
    					var hole = preparedFactory.create(ring.getFactory().createPolygon(ring));
    					tree.insert(ring.getEnvelopeInternal(), hole);
    				}
        			list.set(i, poly);
        			removedRingList.clear();
    			}
    			// Check also if the polygon could also be swallowed by another filled hole
    			if (org.locationtech.jts.algorithm.Area.ofRing(poly.getExteriorRing().getCoordinateSequence()) < minRingArea)
    				smallGeometries.add(poly);
    		} else if (temp.getArea() < minRingArea)
    			smallGeometries.add(temp);
    	}
    	
    	// If we don't have a tree, we didn't change anything
    	if (tree == null)
    		return geometry;
    	
    	// Loop through and remove any small polygons nested inside rings that were removed
    	var iter = list.iterator();
    	while (iter.hasNext()) {
    		var small = iter.next();
    		if (smallGeometries.contains(small)) {
        		var query = (List<PreparedPolygon>)tree.query(small.getEnvelopeInternal());
        		for (PreparedPolygon hole : query) {
        			if (hole.covers(small)) {
        				iter.remove();
        				break;
        			}
//        			if (PointLocation.isInRing(small.getInteriorPoint().getCoordinate(), ring.getCoordinates())) {
//        				iter.remove();
//        				break;
//        			}
        		}
    		}
    	}
    	
    	// Build a geometry from what is left
    	return geometry.getFactory().buildGeometry(list);
    	
//    	var filtered = list.stream().map(g -> {
//    		if (g instanceof Polygon)
//    			return removeInteriorRings((Polygon)g, minRingArea);
//    		else
//    			return g;
//    	}).collect(Collectors.toList());
    	
    	
    	// TODO: Find out how to avoid the Union operation (which can be very slow)
    	// We need to use union because there may be polygons nested within holes that have been filled
//    	return GeometryTools.union(filtered);
    }
    
    private static Polygon removeAllInteriorRings(Polygon polygon) {
    	if (polygon.getNumInteriorRing() == 0)
			return polygon;
		else {
			var factory = polygon.getFactory();
			return factory.createPolygon(polygon.getExteriorRing().getCoordinateSequence());
		}
    }
    
    static double externalRingArea(Polygon polygon) {
    	return org.locationtech.jts.algorithm.Area.ofRing(polygon.getExteriorRing().getCoordinates());
    }
    
    private static Polygon removeInteriorRings(Polygon polygon, double minArea, List<LinearRing> removedRings) {
    	int nRings = polygon.getNumInteriorRing();
    	if (nRings == 0)
			return polygon;
    	
    	var holes = new ArrayList<LinearRing>();
    	for (int i = 0; i < nRings; i++) {
    		var ring = polygon.getInteriorRingN(i);
    		var coords = ring.getCoordinates();
    		if (org.locationtech.jts.algorithm.Area.ofRing(coords) >= minArea) {
    			holes.add(ring);
    		} else if (removedRings != null)
    			removedRings.add(ring);
    	}
    	
    	if (holes.size() == nRings)
    		return polygon;

    	var factory = polygon.getFactory();
    	if (holes.isEmpty())
    		return factory.createPolygon(polygon.getExteriorRing());

    	return factory.createPolygon(
    			polygon.getExteriorRing(),
    			holes.toArray(LinearRing[]::new)
    			);
    }
    
    /**
     * Fill all interior rings for the specified geometry.
     * @param geometry
     * @return
     */
    public static Geometry fillHoles(Geometry geometry) {
    	if (geometry instanceof Polygon)
    		return removeAllInteriorRings((Polygon)geometry);
    	
    	var list = flatten(geometry, null);
    	var filtered = list.stream().map(g -> {
    		if (g instanceof Polygon)
    			return removeAllInteriorRings((Polygon)g);
    		else
    			return g;
    	}).collect(Collectors.toList());
    	if (list.equals(filtered))
    		return geometry;
    	
    	// TODO: Find out how to avoid the Union operation (which can be very slow)
    	// We need to use union because there may be polygons nested within holes that have been filled
    	return GeometryTools.union(filtered);
//    	return geometry.getFactory().buildGeometry(filtered);
    }
    
    /**
     * Remove fragments smaller than the specified area from a Geometry, ignoring internal rings.
     * 
     * @param geometry the geometry to refine
     * @param minArea the minimum area of a fragment to retain. If &le; 0, the geometry is returned unchanged.
     *                Otherwise, polygons will be extracted from the geometry and all that have an external ring area smaller than minArea will be removed.
     * @return the refined Geometry, or an empty polygon if all pieces of the geometry were removed.
     */
    public static Geometry removeFragments(Geometry geometry, double minArea) {
    	if (minArea <= 0)
    		return geometry;
    	if (geometry instanceof Polygon) {
    		if (externalRingArea((Polygon)geometry) >= minArea)
    			return geometry;
    		else
    			return geometry.getFactory().createPolygon();
    	}
    	@SuppressWarnings("unchecked")
		var polygons = (List<Polygon>)PolygonExtracter.getPolygons(geometry);
    	if (polygons.isEmpty())
    		return null;
    	var filtered = polygons
    			.stream()
    			.filter(g -> externalRingArea(g) >= minArea)
    			.collect(Collectors.toList());
    	if (filtered.isEmpty())
    		return geometry.getFactory().createPolygon();
    	return geometry.getFactory().buildGeometry(filtered);
    }
    
    
    /**
     * Test a polygon for validity, attempting to fix TopologyValidationErrors if possible.
     * This attempts a range of tricks (starting with Geometry.buffer(0)), although none
     * are guaranteed to work. The first that largely preserves the polygon's area is returned.
     * <p>
     * The result is guaranteed to be valid, but not necessarily to be a close match to the 
     * original polygon; in particular, if everything failed the result will be empty.
     * <p>
     * Code that calls this method can test if the output is equal to the input to determine 
     * if any changes were made.
     * 
     * @param polygon input (possibly-invalid) polygon
     * @return the input polygon (if valid), an adjusted polygon (if attempted fixes helped),
     *         or an empty polygon if the situation could not be resolved
     */
    public static Geometry tryToFixPolygon(Polygon polygon) {
    	TopologyValidationError error = new IsValidOp(polygon).getValidationError();
    	if (error == null)
    		return polygon;
    	
		logger.debug("Invalid polygon detected! Attempting to correct {}", error.toString());
		
		// Area calculations seem to be reliable... even if the topology is invalid
		double areaBefore = polygon.getArea();
		
		double tol = 0.0001;

		// Try fast buffer trick to make valid (but sometimes this can 'break', e.g. with bow-tie shapes)
		Geometry geomBuffered = polygon.buffer(0);
		double areaBuffered = geomBuffered.getArea();
		if (geomBuffered.isValid() && GeneralTools.almostTheSame(areaBefore, areaBuffered, tol))
			return geomBuffered;
		
		// If the buffer trick gave us an exceedingly small area, try removing this and see if that resolves things
		if (!geomBuffered.isEmpty() && areaBuffered < areaBefore * 0.001) {
			try {
				Geometry geomDifference = polygon.difference(geomBuffered);
				if (geomDifference.isValid())
					return geomDifference;
			} catch (Exception e) {
				logger.debug("Attempting to fix by difference failed: " + e.getLocalizedMessage(), e);
			}
		}
		
		// Resort to the slow method of fixing polygons if we have to
		logger.debug("Unable to fix Geometry with buffer(0) - will try snapToSelf instead");
		double distance = GeometrySnapper.computeOverlaySnapTolerance(polygon);
		Geometry geomSnapped = GeometrySnapper.snapToSelf(polygon,
				distance,
				true);
		
		if (geomSnapped.isValid())
			return geomSnapped;
		
		// If everything failed, return an empty polygon (which will at least be valid...)
		return polygon.getFactory().createPolygon();
    }
    
    
    /**
     * Remove small fragments and fill interior rings within a Geometry.
     * 
     * @param geometry input geometry to refine
     * @param minSizePixels minimum area of a fragment to keep (the area of interior rings for polygons will be ignored)
     * @param minHoleSizePixels minimum size of an interior hole to keep
     * @return the refined geometry (possibly the original unchanged), or null if the changes resulted in the Geometry disappearing
     * 
     * @see #removeFragments(Geometry, double)
     * @see #removeInteriorRings(Geometry, double)
     */
    public static Geometry refineAreas(Geometry geometry, double minSizePixels, double minHoleSizePixels) {
		
    	if (minSizePixels <= 0 && minHoleSizePixels <= 0)
			return geometry;
    	
    	var geom2 = geometry;
		
//    	// Fill interior rings first
//    	geom2 = removeInteriorRings(geometry, minHoleSizePixels);
    	
    	// Remove fragments first, so we don't need to worry about whether they fall in holes
    	geom2 = removeFragments(geom2, minSizePixels);

    	// Fill interior rings
    	geom2 = removeInteriorRings(geom2, minHoleSizePixels);

    	return geom2;
	}
	
	
	static LinearRing toLinearRing(LineString lineString) {
		if (lineString instanceof LinearRing)
			return (LinearRing)lineString;
		return lineString.getFactory().createLinearRing(lineString.getCoordinateSequence());
	}
	

    /**
     * Converter to help switch from a {@link ROI} to a {@link Geometry}.
     */
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
	    
	    /**
	     * @implNote This method generates {@link Coordinate}s, whereas in QuPath v0.2.0 (using JTS 1.16.1)
	     * it used {@link CoordinateXY}.
	     * The change was required to avoid test failures in JTS v1.17.0, caused by mixed-dimension coordinates 
	     * being generated within some operations as described by https://github.com/locationtech/jts/issues/434
    	 * Consequently, there may be some loss of efficiency.
    	 * 
	     * @param x
	     * @param y
	     * @return
	     */
	    private Coordinate createCoordinate(double x, double y) {
	    	var precisionModel = factory.getPrecisionModel();
	    	return new Coordinate(precisionModel.makePrecise(x), precisionModel.makePrecise(y));
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
	    	if ((shape instanceof Path2D || shape instanceof GeneralPath) && containsClosedSegments(iterator)) {
	    		// Arbitrary paths that correspond to an area can fail with JTS ShapeReader, so convert to areas instead
	    		return shapeToGeometry(new Area(shape));
	    	} else
	    		iterator = shape.getPathIterator(transform, flatness);
        	return getShapeReader().read(iterator);
	    }
	    
	    /**
	     * Test of an iterator contains closed segments, indicating the iterator relates to an area.
	     * @param iterator
	     * @return true if any SEG_CLOSE segments are found
	     */
	    private static boolean containsClosedSegments(PathIterator iterator) {
	    	double[] coords = new double[6];
	    	while (!iterator.isDone()) {
	    		iterator.next();
	    		if (iterator.currentSegment(coords) == PathIterator.SEG_CLOSE)
	    			return true;
	    	}
	    	return false;
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
	     * <p>
	     * @implNote An alternative (more complex) method was used in QuPath v0.2.0, using JTS 1.16.1.
	     * The one advantage of the older method was that it used {@link CoordinateXY} - however this 
	     * resulted in test failures in JTS v1.17.0, caused by mixed-dimension coordinates 
	     * being generated within some operations as described by https://github.com/locationtech/jts/issues/434
    	 * Consequently, there may be some loss of efficiency.
    	 * <p>
    	 * See also https://github.com/locationtech/jts/issues/408
	     * 
	     * @param area
	     * @param transform
	     * @param flatness
	     * @param factory
	     * @return a geometry corresponding to the Area object
	     */
	    private static Geometry convertAreaToGeometry(final Area area, final AffineTransform transform, final double flatness, final GeometryFactory factory) {

	    	PathIterator iter = area.getPathIterator(transform, flatness);

	    	PrecisionModel precisionModel = factory.getPrecisionModel();
	    	Polygonizer polygonizer = new Polygonizer(true);

	    	List<Coordinate[]> coords = (List<Coordinate[]>)ShapeReader.toCoordinates(iter);
	    	List<Geometry> geometries = new ArrayList<>();
	    	for (Coordinate[] array : coords) {
	    		for (var c : array)
	    			precisionModel.makePrecise(c);

	    		LineString lineString = factory.createLineString(array);
	    		geometries.add(lineString);
	    	}
	    	polygonizer.add(factory.buildGeometry(geometries).union());
	    	return polygonizer.getGeometry();

	    }

	    /**
	     * Legacy version of {@link #convertAreaToGeometry(Area, AffineTransform, double, GeometryFactory)} before v0.3.0.
	     * 
	     * @param area
	     * @param transform
	     * @param flatness
	     * @param factory
	     * @return a geometry corresponding to the Area object
	     */
	    @Deprecated
	    private static Geometry convertAreaToGeometryLegacy(final Area area, final AffineTransform transform, final double flatness, final GeometryFactory factory) {
	
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
					points.add(new Coordinate(startX, startY));
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
					var next = new Coordinate(x1, y1);
					if (points.isEmpty() || points.get(points.size()-1).distance(next) > precision)
						points.add(next, false);
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
					logger.debug("Error when converting area to Geometry: cannot create polygon from coordinate array of length 1!");
				} else if (closed) {
					points.closeRing();
					if (points.size() <= 3) {
						logger.debug("Discarding small 'ring' segment during area conversion (only 3 coordinates)");
						x0 = x1;
						y0 = y1;
						iter.next();
						continue;
					}
					
					Coordinate[] coords = points.toCoordinateArray();
					
					// Need to ensure polygons are valid at this point
					// Sometimes, self-intersections can thwart validity
					Geometry polygon = factory.createPolygon(coords);
					Geometry geomValid = tryToFixPolygon((Polygon)polygon);
					if (polygon != geomValid) {
						double areaBefore = polygon.getArea();
						double areaAfter = geomValid.getArea();
						if (GeneralTools.almostTheSame(areaBefore, areaAfter, 0.0001))
							logger.debug("Invalid polygon detected and fixed! Original area: {}, Area after fix: {}", areaBefore, areaAfter);
						else
							logger.warn("Invalid polygon detected! Beware of changes. Original area: {}, Area after attempted fix: {}", areaBefore, areaAfter);
						polygon = geomValid;
						errorCount++;
					}
					if (!polygon.isEmpty()) {
						totalCount++;
						if (areaTempSigned < 0) {
							areaNegative += areaTempSigned;
							for (int i = 0; i < polygon.getNumGeometries(); i++) {
								Polygon p = (Polygon)polygon.getGeometryN(i);
								if (!p.isEmpty())
									negative.add(p);
							}
						} else if (areaTempSigned > 0) {
							areaPositive += areaTempSigned;
							for (int i = 0; i < polygon.getNumGeometries(); i++) {
								Polygon p = (Polygon)polygon.getGeometryN(i);
								if (!p.isEmpty())
									positive.add(p);
							}
						}
					}
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
			@SuppressWarnings("unused")
			double areaOuter;
			@SuppressWarnings("unused")
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
				// By doing this in order we should find the 'correct' containing polygon.
				var ascendingArea = Comparator.comparingDouble((GeometryWithArea g) -> g.area);
				var outerWithArea = outer.stream().map(g -> new GeometryWithArea(g)).sorted(ascendingArea).collect(Collectors.toList());
				var holesWithArea = holes.stream().map(g -> new GeometryWithArea(g)).sorted(ascendingArea).collect(Collectors.toList());
				
				// For each hole, find the smallest polygon that contains it
				Map<Geometry, List<Geometry>> matches = new HashMap<>();
				for (var tempHole : holesWithArea) {
					double holeArea = tempHole.area;
					// We assume a single point inside is sufficient because polygons should be non-overlapping
					var point = tempHole.geom.getCoordinate();
					var iterOuter = outerWithArea.iterator();
					@SuppressWarnings("unused")
					int count = 0;
					while (point != null && iterOuter.hasNext()) {
						var tempOuter = iterOuter.next();
						if (holeArea > tempOuter.area) {
							continue;
						}
						if (SimplePointInAreaLocator.isContained(point, tempOuter.geom)) {
							var list = matches.get(tempOuter.geom);
							if (list == null) {
								list = new ArrayList<>();
								matches.put(tempOuter.geom, list);
							}
							list.add(tempHole.geom);
							break;
						}
					}
				}
				
				// Loop through the outer polygons and remove all their holes
				List<Geometry> fixedGeometries = new ArrayList<>();
				for (var tempOuter : outerWithArea) {
					var list = matches.getOrDefault(tempOuter.geom, null);
					if (list == null || list.isEmpty()) {
						fixedGeometries.add(tempOuter.geom);
					} else {
						var mergedHoles = union(list);
						fixedGeometries.add(tempOuter.geom.difference(mergedHoles));
					}
				}
				geometry = union(fixedGeometries);
				geometryOuter = geometry;
			}
			
			// Perform a sanity check using areas
			double computedArea = Math.abs(areaCached);
			double geometryArea = geometry.getArea();
			if (!GeneralTools.almostTheSame(computedArea, geometryArea, 0.01)) {
				logger.debug("{}/{} geometries had topology validation errors", errorCount, totalCount);
				double percent = Math.abs(computedArea - geometryArea) / (computedArea/2.0 + geometryArea/2.0) * 100.0;
				logger.warn("Difference in area after JTS conversion! Computed area: {}, Geometry area: {} ({} %%)", Math.abs(areaCached), geometry.getArea(),
						GeneralTools.formatNumber(percent, 3));
			}
			return geometry;
		}
	    
	    private static class GeometryWithArea {
	    	
	    	private final Geometry geom;
	    	private final double area;
	    	
	    	private GeometryWithArea(Geometry geom) {
	    		this.geom = geom;
	    		this.area = geom.getArea();
	    	}
	    	
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
	    	if (geometry.isEmpty())
	    		return ROIs.createEmptyROI(plane);
	    	
	    	// Make sure out Geometry is all of the same type
	    	var geometry2 = homogenizeGeometryCollection(geometry);
	    	if (geometry2 != geometry) {
	    		logger.warn("Geometries must all be of the same type when converting to a ROI! Converted {} to {}.", geometry.getGeometryType(), geometry2.getGeometryType());
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