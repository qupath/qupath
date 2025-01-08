/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.GeometryCombiner;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.hprtree.HPRtree;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.IncrementalDelaunayTriangulator;
import org.locationtech.jts.triangulate.quadedge.LastFoundQuadEdgeLocator;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeLocator;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.LogTools;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Tools for creating and querying Delaunay triangulations and Voronoi diagrams.
 * 
 * @author Pete Bankhead
 */
public class DelaunayTools {
	
	private static final Logger logger = LoggerFactory.getLogger(DelaunayTools.class);
	
	private static boolean calibrated(PixelCalibration cal) {
		return cal != null && (cal.getPixelHeight().doubleValue() != 1 || cal.getPixelWidth().doubleValue() != 1);
	}
	
	private static Function<PathObject, Collection<Coordinate>> createGeometryExtractor(PixelCalibration cal, boolean preferNucleus, double densifyFactor, double erosion) {

		PrecisionModel precision = calibrated(cal) ? null : GeometryTools.getDefaultFactory().getPrecisionModel();
		AffineTransformation transform = calibrated(cal) ? 
				AffineTransformation.scaleInstance(cal.getPixelWidth().doubleValue(), cal.getPixelHeight().doubleValue()) : null;
		
		return p -> {
			var roi = PathObjectTools.getROI(p, preferNucleus);
			if (roi == null || roi.isEmpty())
				return Collections.emptyList();
			
			var geom = roi.getGeometry();
			
			if (transform != null)
				geom = transform.transform(geom);
			
			// Shared boundaries can be problematic, so try to buffer away from these
			double buffer = -Math.abs(erosion);
			if (buffer < 0 && geom instanceof Polygonal) {
				var geomBefore = geom;
				geom = GeometryTools.attemptOperation(geom, g -> g.buffer(buffer));
				// Do not permit Geometry to disappear
				if (geom.isEmpty()) {
					geom = geomBefore;
				}
			}
			
			// Somehow, empty coordinate arrays could sometimes be produced (although not replicated by me).
			// See https://forum.image.sc/t/error-running-stardist-on-qupath-v0-3-0-rc2-using-opencv-converted-model/56216/22
			// Therefore we take extra care in case empty geometries are being generated accidentally.
			if (precision != null) {
				var geom2 = GeometryTools.attemptOperation(geom, g -> GeometryPrecisionReducer.reduce(g, precision));
				if (!geom2.isEmpty())
					geom = geom2;
			}

			if (densifyFactor > 0) {
				var geom2 = GeometryTools.attemptOperation(geom, g -> Densifier.densify(g, densifyFactor));
				if (!geom2.isEmpty())
					geom = geom2;
			}

			// Making precise is essential! Otherwise, small artifacts can occur
			var coords = geom.getCoordinates();
			var output = new LinkedHashSet<Coordinate>();
			var p2 = precision;
			Coordinate lastCoordinate = null;
			if (p2 == null)
				p2 = GeometryTools.getDefaultFactory().getPrecisionModel();
			
			// Add coordinates, unless they are extremely close to an existing coordinate
			int n = coords.length;
			if (n == 0) {
				logger.warn("Empty Geometry found for {}", p);
				return Collections.emptyList();
			}
			double minDistance = densifyFactor*0.5;
			var firstCoordinate = coords[0];
			while (n > 2 && firstCoordinate.distance(coords[n-1]) < minDistance)
				n--;
			for (int i = 0; i < n ; i++) {
				var c = coords[i];
				p2.makePrecise(c);
				// Three rules:
				// 1. Always add the first coordinate
				// 2. Add 'intermediate' coordinates if they are sufficiently far from the last that was added
				// 3. Add the last coordinate if it is sufficiently far from the first, and from the last added
				if (i == 0 || (c.distance(lastCoordinate) > minDistance &&
						(i < n-1 || c.distance(coords[0]) > minDistance))) {
					output.add(c);
					lastCoordinate = c;
				}
			}
			
			return output;
		};
	}
	
	
	private static Function<PathObject, Collection<Coordinate>> createCentroidExtractor(PixelCalibration cal, boolean preferNucleus) {
		PrecisionModel precision = calibrated(cal) ? null : GeometryTools.getDefaultFactory().getPrecisionModel();
		
		return p -> {
			var roi = PathObjectTools.getROI(p, preferNucleus);
			if (roi != null) {
				double x = roi.getCentroidX();
				double y = roi.getCentroidY();
				if (precision != null) {
					x = precision.makePrecise(x);
					y = precision.makePrecise(y);
				}
				if (Double.isFinite(x) && Double.isFinite(y))
					return Collections.singletonList(new Coordinate(x, y));
			}
			return Collections.emptyList();
		};
	}
	
	/**
	 * Create a new {@link Builder} to compute a triangulation using the specified objects.
	 * @param pathObjects
	 * @return
	 */
	public static Builder newBuilder(Collection<PathObject> pathObjects) {
		return new Builder(pathObjects);
	}
	
	/**
	 * Builder class to create a {@link Subdivision} based on Delaunay triangulation.
	 */
	public static class Builder {
		
		private enum ExtractorType {CUSTOM, CENTROIDS, ROI}
		
		private ExtractorType extractorType = ExtractorType.CENTROIDS;
		private boolean preferNucleusROI = true;
		
		private PixelCalibration cal = PixelCalibration.getDefaultInstance();
		private double densifyFactor = Double.NaN;
		
		private double erosion = 1.0;
		
		private final ImagePlane plane;
		private final Collection<PathObject> pathObjects = new ArrayList<>();
		
		private Function<PathObject, Collection<Coordinate>> coordinateExtractor;
		
		
		private Builder(Collection<PathObject> pathObjects) {
			ImagePlane plane = null;
			for (var pathObject : pathObjects) {
				var currentPlane = pathObject.getROI().getImagePlane();
				if (plane == null)
					plane = currentPlane;
				else if (!plane.equals(currentPlane)) {
					logger.warn("Non-matching image planes: {} and {}! Object will be skipped...", plane, currentPlane);
					continue;
				}
				this.pathObjects.add(pathObject);
			}
			this.plane = plane == null ? ImagePlane.getDefaultPlane() : plane;
		}
		
		/**
		 * Specify pixel calibration, which is used to calibrate the x and y coordinates.
		 * @param cal the calibration to use
		 * @return this builder
		 */
		public Builder calibration(PixelCalibration cal) {
			this.cal = cal;
			return this;
		}
		
		/**
		 * Specify that the triangulation should be based on ROI centroids.
		 * @return this builder
		 */
		public Builder centroids() {
			this.extractorType = ExtractorType.CENTROIDS;
			return this;
		}
		
		/**
		 * Specify that the triangulation should be based on ROI boundary coordinates with the default densify factor.
		 * @return this builder
		 */
		public Builder roiBounds() {
			return roiBounds(densifyFactor, -2);
		}
		
		/**
		 * Specify that the triangulation should be based on ROI boundary coordinates with a specified densify factor.
		 * @param densify how much to 'densify' the coordinates; recommended default value is 4.0 (assuming uncalibrated pixels).
		 *                Decreasing the value will give a denser (and slower) triangulation; this can achieve more accuracy but 
		 *                also lead to numerical problems. Try adjusting this value only if the default results in errors.
		 * @param erosion amount to erode each {@link Geometry} in pixels. If non-zero, this can fix artifacts occurring at shared boundaries.
		 * @return this builder
		 */
		public Builder roiBounds(double densify, double erosion) {
			this.extractorType = ExtractorType.ROI;
			this.densifyFactor = densify;
			this.erosion = erosion;
			return this;
		}
		
		/**
		 * Specify that the triangulation should be based on nucleus ROIs where possible (only affects cell objects).
		 * @param prefer if true, use the nucleus ROI for cell objects where possible
		 * @return this builder
		 */
		public Builder preferNucleus(boolean prefer) {
			this.preferNucleusROI = prefer;
			return this;
		}
		
		/**
		 * Specify a default method of extracting coordinates for triangulation from an object, rather than centroids or the ROI boundary.
		 * @param coordinateExtractor the custom coordinate extractor
		 * @return this builder
		 */
		public Builder coordinateExtractor(Function<PathObject, Collection<Coordinate>> coordinateExtractor) {
			this.extractorType = ExtractorType.CUSTOM;
			this.coordinateExtractor = coordinateExtractor;
			return this;
		}
		
		/**
		 * Build the {@link Subdivision} with the current parameters.
		 * @return
		 */
		public Subdivision build() {
			
			logger.debug("Creating subdivision for {} objects", pathObjects.size());
			
			var coords = new HashMap<Coordinate, PathObject>();
			
			double densify = densifyFactor;
			if (!Double.isFinite(densify))
				densify = cal.getAveragedPixelSize().doubleValue() * 4.0;
			
			var extractor = coordinateExtractor;
			switch (extractorType) {
			case CENTROIDS:
				extractor = createCentroidExtractor(cal, preferNucleusROI);
				break;
			case ROI:
				extractor = createGeometryExtractor(cal, preferNucleusROI, densify, erosion);
				break;
			case CUSTOM:
			default:
				break;
			}
			
			for (var pathObject : pathObjects) {
				for (var c : extractor.apply(pathObject)) {
					coords.put(c, pathObject);
				}
			}
			
			double tolerance = cal.getAveragedPixelSize().doubleValue() / 1000.0;
			return new Subdivision(createSubdivision(coords.keySet(), tolerance), pathObjects, coords, plane);
		}
		
	}
	
	
	/**
	 * Create a {@link Subdivision} using the centroid coordinates of ROIs.
	 * <p>
	 * Note: centroids must be distinct. If multiple objects have identical centroids, one or more objects may be lost 
	 * from the resulting {@link Subdivision}.
	 * 
	 * @param pathObjects collection of objects from which to construct the {@link Subdivision}
	 * @param preferNucleusROI if true, prefer the nucleus ROI when extracting the centroid from a cell
	 * @return a new {@link Subdivision} computed from the centroids of the provided objects
	 * 
	 * @see #createFromGeometryCoordinates(Collection, boolean, double)
	 */
	public static Subdivision createFromCentroids(Collection<PathObject> pathObjects, boolean preferNucleusROI) {
		
		logger.debug("Creating subdivision from ROI centroids for {} objects", pathObjects.size());
		
		var coords = new HashMap<Coordinate, PathObject>();
		ImagePlane plane = null;
		
		var precisionModel = GeometryTools.getDefaultFactory().getPrecisionModel();
		
		for (var pathObject : pathObjects) {
			var roi = PathObjectTools.getROI(pathObject, preferNucleusROI);
			
			if (plane == null)
				plane = roi.getImagePlane();
			else if (!plane.equals(roi.getImagePlane())) {
				logger.warn("Non-matching image planes: {} and {}! Object will be skipped...", plane, roi.getImagePlane());
				continue;
			}
			double x = precisionModel.makePrecise(roi.getCentroidX());
			double y = precisionModel.makePrecise(roi.getCentroidY());
			var coord = new Coordinate(x, y);
			coords.put(coord, pathObject);
		}
		return new Subdivision(createSubdivision(coords.keySet(), 0.01), pathObjects, coords, plane);
	}
	
	/**
	 * Create a {@link Subdivision} using the boundary coordinates of ROIs.
	 * This is primarily useful for computing Voronoi faces centered on ROIs rather than single points, 
	 * i.e. to identify pixels closest to specific objects.
	 * <p>
	 * Notes:
	 * <ul>
	 * 	 <li>This is typically <i>much</i> slower than {@link #createFromCentroids(Collection, boolean)}</li>
	 *   <li>For interpretable results, ROIs should be non-overlapping.</li>
	 * </ul>
	 * 
	 * @param pathObjects collection of objects from which to construct the {@link Subdivision}
	 * @param preferNucleusROI if true, prefer the nucleus ROI when extracting geometries from a cell
	 * @param densifyFactor amount to 'density' each ROI; this is needed to interpolate coordinates (suggested value = 4.0)
	 * @return a new {@link Subdivision} computed from the provided objects
	 * 
	 * @see #createFromCentroids(Collection, boolean)
	 */
	public static Subdivision createFromGeometryCoordinates(Collection<PathObject> pathObjects, boolean preferNucleusROI, double densifyFactor) {
		
		logger.debug("Creating subdivision from geometry coordinates for {} objects", pathObjects.size());
		
		var coords = new HashMap<Coordinate, PathObject>();
		ImagePlane plane = null;
		
		for (var pathObject : pathObjects) {
			var roi = PathObjectTools.getROI(pathObject, preferNucleusROI);
			
			if (plane == null)
				plane = roi.getImagePlane();
			else if (!plane.equals(roi.getImagePlane())) {
				logger.warn("Non-matching image planes: {} and {}! Object will be skipped...", plane, roi.getImagePlane());
				continue;
			}
			
			var geom = roi.getGeometry();
			
			if (densifyFactor > 0)
				geom = Densifier.densify(geom, densifyFactor);
			
			var coordsTemp = geom.getCoordinates();
			for (var c : coordsTemp) {
				var previous = coords.put(c, pathObject);
				if (previous != null)
                    logger.warn("Previous coordinate: {}", previous);
			}
		}
		
		// Attempts to call VoronoiDiagramBuilder would sometimes fail when clipping to the envelope - 
		// Because we do our own clipping anyway, we skip that step by requesting the diagram via the subdivision instead
		return new Subdivision(createSubdivision(coords.keySet(), 0.001), pathObjects, coords, plane);
	}
	
	
	private static QuadEdgeSubdivision createSubdivision(Collection<Coordinate> coords, double tolerance) {
		var envelope = DelaunayTriangulationBuilder.envelope(coords);
		var subdiv = new QuadEdgeSubdivision(envelope, tolerance);
		var triangulator = new IncrementalDelaunayTriangulator(subdiv);
		// We need to turn this off to use Voronoi faces - otherwise we often get invalid polygons at boundaries
		triangulator.forceConvex(false);

		subdiv.setLocator(getDefaultLocator(subdiv));
		var edgeSet = new HashSet<QuadEdge>();
		for (var coord : prepareCoordinates(coords)) {
			var edge = triangulator.insertSite(new Vertex(coord));
			if (!edgeSet.add(edge))
				logger.debug("Found duplicate edge!");
		}
		return subdiv;
		
		// Simple alternative (but with fewer options, often slower)
//		var builder = new VoronoiDiagramBuilder();
//		builder.setSites(prepareCoordinates(coords));
//		return builder.getSubdivision();
	}
	
	static QuadEdgeLocator getDefaultLocator(QuadEdgeSubdivision subdiv) {
		return new FirstVertexLocator(subdiv);
	}
	
	/**
	 * The order in which coordinates are added to IncrementalDelaunayTriangulator matters a lot for performance
	 * @param coords
	 * @return
	 */
	private static Collection<Coordinate> prepareCoordinates(Collection<Coordinate> coords) {
		return DelaunayTriangulationBuilder.unique(coords.toArray(Coordinate[]::new));
	}
	
	/**
	 * BiPredicate that returns true for objects that share the same classification.
	 * @return
	 */
	public static BiPredicate<PathObject, PathObject> sameClassificationPredicate() {
		return (p1, p2) -> p1.getPathClass() == p2.getPathClass();
	}
	
	/**
	 * BiPredicate that returns true for objects with ROI centroids within a specified distance.
	 * @param maxDistance maximum separation between ROI centroids
	 * @param preferNucleus if true, prefer nucleus centroids for cell objects
	 * @return true for object pairs with close centroids
	 */
	public static BiPredicate<PathObject, PathObject> centroidDistancePredicate(double maxDistance, boolean preferNucleus) {
		return (p1, p2) -> {
			var r1 = PathObjectTools.getROI(p1, preferNucleus);
			var r2 = PathObjectTools.getROI(p2, preferNucleus);
			return RoiTools.getCentroidDistance(r1, r2) <= maxDistance;
		};
	}
	
	/**
	 * BiPredicate that returns true for objects with ROI boundaries within a specified distance.
	 * @param maxDistance maximum separation between ROI boundaries, in pixels
	 * @param preferNucleus if true, prefer nucleus ROIs for cell objects
	 * @return true for object pairs with close boundaries
	 */
	public static BiPredicate<PathObject, PathObject> boundaryDistancePredicate(double maxDistance, boolean preferNucleus) {
		return (p1, p2) -> {
			var r1 = PathObjectTools.getROI(p1, preferNucleus);
			var r2 = PathObjectTools.getROI(p2, preferNucleus);
			return r1.getGeometry().isWithinDistance(r2.getGeometry(), maxDistance);
//			return RoiTools.getBoundaryDistance(r1, r2) <= maxDistance;
		};
	}

	/**
	 * Create annotations surrounding objects within a specified subdivision based on Voronoi faces.
	 * This can be used to create annotations based upon existing detections.
	 * 
	 * @param subdivision subdivision representing object relationships
	 * @param bounds if provided, clip the annotations to fit within the ROI
	 * @return a list of annotations, one for each classification represented by objects within the subdivision within the bounds
	 */
	public static List<PathObject> createAnnotationsFromSubdivision(Subdivision subdivision, ROI bounds) {
		var mapVoronoi = subdivision.getVoronoiFaces();
		var map = new HashMap<PathClass, List<Geometry>>();
		for (var entry : mapVoronoi.entrySet()) {
			var pathClass = entry.getKey().getPathClass();
			var list = map.computeIfAbsent(pathClass, p -> new ArrayList<>());
			list.add(entry.getValue());
		}
		var clip = bounds == null ? null : bounds.getGeometry();
		var annotations = new ArrayList<PathObject>();
		var plane = subdivision.getImagePlane();
		for (var entry : map.entrySet()) {
			var geometry = GeometryTools.union(entry.getValue());
			if (clip != null && !clip.covers(geometry))
				geometry = clip.intersection(geometry);
			if (geometry.isEmpty())
				continue;
			var roi = GeometryTools.geometryToROI(geometry, plane);
			var annotation = PathObjects.createAnnotationObject(roi, entry.getKey());
			annotation.setLocked(true);
			annotations.add(annotation);
		}
		return annotations;
	}
	
	/**
	 * Assign object classifications based upon pre-computed clusters.
	 * @param clusters a collection of {@link PathObject} collections, each of which corresponds to a cluster of related objects.
	 * @param pathClassFun function used to create a {@link PathClass} from a cluster number (determined by where it falls within the collection).
	 * @return a collection of objects that have had their classifications set by this method
	 */
	public static Collection<PathObject> classifyObjectsByCluster(Collection<Collection<? extends PathObject>> clusters, Function<Integer, PathClass> pathClassFun) {
		int c = 0;
		var list = new ArrayList<PathObject>();
		for (var cluster : clusters) {
			var pathClass = pathClassFun.apply(c);
			for (var pathObject : cluster) {
				pathObject.setPathClass(pathClass);
				list.add(pathObject);
			}
			c++;
		}
		return list;
	}
	
	/**
	 * Assign object classifications based upon pre-computed clusters.
	 * @param clusters a collection of {@link PathObject} collections, each of which corresponds to a cluster of related objects.
	 * @return a collection of objects that have had their classifications set by this method
	 */
	public static Collection<PathObject> classifyObjectsByCluster(Collection<Collection<? extends PathObject>> clusters) {
		return classifyObjectsByCluster(clusters, c -> PathClass.getInstance("Cluster " + (c + 1)));
	}
	
	
	/**
	 * Assign object classifications based upon pre-computed clusters.
	 * @param clusters a collection of {@link PathObject} collections, each of which corresponds to a cluster of related objects.
	 * @param pathClassFun function used to create a {@link PathClass} from a cluster number (determined by where it falls within the collection).
	 *                     However rather than set this as the object classification, it will be used to set the name and color of the object 
	 *                     (so as to avoid overriding an existing classification).
	 * @return a collection of objects that have had their classifications set by this method
	 */
	public static Collection<PathObject> nameObjectsByCluster(Collection<Collection<? extends PathObject>> clusters, Function<Integer, PathClass> pathClassFun) {
		int c = 0;
		var list = new ArrayList<PathObject>();
		for (var cluster : clusters) {
			var pathClass = pathClassFun.apply(c);
			String name = pathClass == null ? null : pathClass.getName();
			Integer color = pathClass == null ? null : pathClass.getColor();
			for (var pathObject : cluster) {
				pathObject.setName(name);
				pathObject.setColor(color);
				list.add(pathObject);
			}
			c++;
		}
		return list;
	}
	
	/**
	 * Assign object names based upon pre-computed clusters.
	 * @param clusters a collection of {@link PathObject} collections, each of which corresponds to a cluster of related objects.
	 * @return a collection of objects that have had their classifications set by this method
	 */
	public static Collection<PathObject> nameObjectsByCluster(Collection<Collection<? extends PathObject>> clusters) {
		return nameObjectsByCluster(clusters, c -> PathClass.getInstance("Cluster " + (c + 1)));
	}
	
	
	/**
	 * Helper class for extracting information from a Delaunay triangulation computed from {@linkplain PathObject PathObjects}.
	 */
	public static class Subdivision {
		
		private static final Logger logger = LoggerFactory.getLogger(Subdivision.class);
		
		private final Collection<PathObject> pathObjects;
		private final Map<Coordinate, PathObject> coordinateMap;
		private final QuadEdgeSubdivision subdivision;
		
		private final ImagePlane plane;
		
		private transient volatile Map<PathObject, Geometry> voronoiFaces;

		/**
		 * A map to lookup neighbors, and an edge index to speed up finding objects where the edge intersects a
		 * specific rectangle.
		 * This is used to speed object painting.
		 */
		private record NeighborMap(Map<PathObject, List<PathObject>> neighbors, SpatialIndex index) {}

		private transient volatile NeighborMap neighbors;

		
		private Subdivision(QuadEdgeSubdivision subdivision, Collection<PathObject> pathObjects, Map<Coordinate, PathObject> coordinateMap, ImagePlane plane) {
			this.subdivision = subdivision;
			this.pathObjects = pathObjects.stream().distinct().toList();
			this.plane = plane == null ? pathObjects.stream()
					.filter(PathObject::hasROI)
					.map(PathObject::getROI)
					.map(ROI::getImagePlane)
					.findFirst()
					.orElse(ImagePlane.getDefaultPlane()) : plane;
			this.coordinateMap = Map.copyOf(coordinateMap);
		}
		
		/**
		 * Get the {@link ImagePlane} for this subdivision.
		 * Because the subdivision is 2D, all object ROIs are expected to belong to the same plane.
		 * @return
		 */
		public ImagePlane getImagePlane() {
			return plane;
		}
		
		/**
		 * Get a map of Voronoi faces as JTS {@link Geometry} objects.
		 * @return
		 * @see #getVoronoiROIs(Geometry)
		 */
		public Map<PathObject, Geometry> getVoronoiFaces() {
			if (voronoiFaces == null) {
				synchronized (this) {
					if (voronoiFaces == null)
						voronoiFaces = Collections.unmodifiableMap(calculateVoronoiFaces());
				}
			}
			return voronoiFaces;
		}
		
		/**
		 * Get a map of Voronoi faces, converted to {@link ROI} objects.
		 * @param clip optional region used to clip the total extent of the ROIs
		 * @return
		 * @see #getVoronoiFaces()
		 */
		public Map<PathObject, ROI> getVoronoiROIs(Geometry clip) {
			var faces = getVoronoiFaces();
			var map = new HashMap<PathObject, ROI>();
			for (var entry : faces.entrySet()) {
				var pathObject = entry.getKey();
				var face = entry.getValue();
				if (clip != null && !clip.covers(face))
					face = clip.intersection(face);
				var roi = GeometryTools.geometryToROI(face, pathObject.getROI().getImagePlane());
				map.put(pathObject, roi);
			}
			return map;
		}
		
		/**
		 * Get all the objects associated with this subdivision.
		 * @return
		 * @deprecated v0.6.0 use {@link #getObjects()} instead.
		 */
		@Deprecated
		public Collection<PathObject> getPathObjects() {
			LogTools.warnOnce(logger, "getPathObjects() is deprecated; use getObjects() instead");
			return pathObjects;
		}

		/**
		 * Get all the objects associated with this subdivision.
		 * @return
		 */
		public Collection<PathObject> getObjects() {
			return pathObjects;
		}

		/**
		 * Get objects with edges that <i>may</i> intersect a specific region.
		 * <p>
		 * This is especially useful for requesting objects that should be considered when drawing edges for a
		 * specific region, where the objects themselves don't need to fall within the region - but their edge might.
		 * <p>
		 * The method should return all objects that have an edge that intersects the region, but it may also return
		 * additional objects that are not strictly necessary for drawing the region.
		 * @param region
		 * @return
		 */
		public Collection<PathObject> getObjectsForRegion(ImageRegion region) {
			if (region.getZ() != plane.getZ() || region.getT() != plane.getT())
				return Collections.emptyList();
			var env = new Envelope(
					region.getX(),
					region.getX() + region.getWidth(),
					region.getY(),
					region.getY() + region.getHeight());
			var edges = getEdgeIndex().query(env);
			List<PathObject> pathObjects = new ArrayList<>();
			for (var item : edges) {
				QuadEdge edge = (QuadEdge) item;
				pathObjects.add(getPathObject(edge.orig()));
				pathObjects.add(getPathObject(edge.dest()));
			}
			return pathObjects.stream().distinct().toList();
		}
		
		/**
		 * Get the nearest neighbor for the specified object.
		 * @param pathObject the object whose neighbor is requested
		 * @return the nearest neighbor, or null if no neighbor can be found
		 */
		public PathObject getNearestNeighbor(PathObject pathObject) {
			var temp = getNeighbors(pathObject);
			if (temp.isEmpty())
				return null;
			return temp.get(0);
		}
		
		/**
		 * Get the nearest neighbor for the specified object, filtered by a predicate.
		 * @param pathObject the object whose neighbor is requested
		 * @param predicate predicate used to establish weather two objects may be considered neighbors
		 * @return the nearest neighbor, or null if no neighbor can be found
		 */
		public PathObject getNearestNeighbor(PathObject pathObject, BiPredicate<PathObject, PathObject> predicate) {
			var temp = getFilteredNeighbors(pathObject, predicate);
			if (temp.isEmpty())
				return null;
			return temp.get(0);
		}

		/**
		 * Get a list of neighbors for a specified object, filtering out objects that do not meet specified criteria.
		 * 
		 * @param pathObject object for which the neighbors are requested
		 * @param predicate predicate that determines if two objects may be considered neighbors
		 * @return list of neighbors
		 */
		public List<PathObject> getFilteredNeighbors(PathObject pathObject, BiPredicate<PathObject, PathObject> predicate) {
			var allNeighbors = getAllNeighbors();
			if (predicate != null) {
				return filterByPredicate(pathObject, allNeighbors.getOrDefault(pathObject, Collections.emptyList()), predicate);
			} else
				return allNeighbors.getOrDefault(pathObject, Collections.emptyList());
		}
		
		/**
		 * Get all neighbors for a specified object.
		 * @param pathObject object for which neighbors are requested
		 * @return list of neighbors
		 */
		public List<PathObject> getNeighbors(PathObject pathObject) {
			return getFilteredNeighbors(pathObject, null);
		}
		
		/**
		 * Get a list of neighbors for all objects, filtering out objects that do not meet specified criteria.
		 * The filter may be used, for example, to impose a distance or classification threshold.
		 * 
		 * @param predicate predicate that determines if two objects may be considered neighbors
		 * @return map in which keys correspond to objects and values represent all corresponding (filtered) neighbors
		 */
		public Map<PathObject, List<PathObject>> getFilteredNeighbors(BiPredicate<PathObject, PathObject> predicate) {
			var allNeighbors = getAllNeighbors();
			if (predicate != null) {
				var map = new LinkedHashMap<PathObject, List<PathObject>>();
				for (var entry : allNeighbors.entrySet()) {
					var pathObject = entry.getKey();
					var list = entry.getValue();
					map.put(pathObject, 
							filterByPredicate(pathObject, list, predicate));
				}
				return Collections.unmodifiableMap(map);
			} else
				return allNeighbors;
		}
		
		/**
		 * Get a list of neighbors for all objects.
		 * 
		 * @return map in which keys correspond to objects and values represent all corresponding neighbors
		 */
		public Map<PathObject, List<PathObject>> getAllNeighbors() {
			return getNeighborMap().neighbors();
		}

		private NeighborMap getNeighborMap() {
			if (neighbors == null) {
				synchronized (this) {
					if (neighbors == null)
						neighbors = calculateAllNeighbors();
				}
			}
			return neighbors;
		}

		/**
		 * Query if the subdivision is empty, i.e. it contains no objects.
		 * @return
		 */
		public boolean isEmpty() {
			return pathObjects.isEmpty();
		}

		/**
		 * Get the number of objects in this subdivision.
		 * @return
		 */
		public int size() {
			return pathObjects.size();
		}

		/**
		 * Return a map of PathObjects and their neighbors, sorted by distance.
		 * @return
		 */
		private synchronized NeighborMap calculateAllNeighbors() {
			
			logger.debug("Calculating all neighbors for {} objects", size());

			// Sort the edges; note that we shouldn't use a parallel stream here, because this can cause
			// get stuck if the common fork join pool is already in use & awaiting the results of this calculation
			@SuppressWarnings("unchecked")
			var edges = (List<QuadEdge>)subdivision.getEdges()
					.stream()
					.sorted(Comparator.comparingDouble(QuadEdge::getLength))
					.toList();

			Map<PathObject, List<PathObject>> neighbors = new HashMap<>();

			var edgeIndex = new HPRtree();
			for (QuadEdge edge : edges) {
				var pathOrigin = getPathObject(edge.orig());
				var pathDest = getPathObject(edge.dest());
				if (pathOrigin == null || pathDest == null || pathDest == pathOrigin ||
					neighbors.getOrDefault(pathOrigin, Collections.emptyList()).contains(pathDest)) {
					continue;
				}
				neighbors.computeIfAbsent(pathOrigin, a -> new ArrayList<>()).add(pathDest);
				neighbors.computeIfAbsent(pathDest, a -> new ArrayList<>()).add(pathOrigin);

				var env = createEnvelope(pathOrigin.getROI(), pathDest.getROI());
				edgeIndex.insert(env, edge);
			}
			for (var entry : neighbors.entrySet()) {
				entry.setValue(List.copyOf(entry.getValue()));
			}
			return new NeighborMap(Map.copyOf(neighbors), edgeIndex);
		}

		private static Envelope createEnvelope(ROI roi1, ROI roi2) {
			double x1 = Math.min(roi1.getBoundsX(), roi2.getBoundsX());
			double x2 = Math.max(roi1.getBoundsX() + roi1.getBoundsWidth(), roi2.getBoundsX() + roi2.getBoundsWidth());
			double y1 = Math.min(roi1.getBoundsY(), roi2.getBoundsY());
			double y2 = Math.max(roi1.getBoundsY() + roi1.getBoundsHeight(), roi2.getBoundsY() + roi2.getBoundsHeight());
			return new Envelope(x1, x2, y1, y2);
		}

		private SpatialIndex getEdgeIndex() {
			return getNeighborMap().index;
		}
		
		private PathObject getPathObject(Vertex vertex) {
			return coordinateMap.get(vertex.getCoordinate());
		}
		
		
		/*
		 * This was an attempt to improve the robustness whenever coordinates are based upon geometry boundaries. 
		 * It does seem to help, although the main issue was that the coordinates needed to be made 'precise'.
		 */
		private synchronized Map<PathObject, Geometry> calculateVoronoiFacesByLocations() {
			
			logger.debug("Calculating Voronoi faces for {} objects by location", size());
			
			// We use a new GeometryFactory because we need floating point precision (it seems) to avoid 
			// invalid polygons being returned
			@SuppressWarnings("unchecked")
			var polygons = (List<Polygon>)subdivision.getVoronoiCellPolygons(new GeometryFactory());
			
			// Create a spatial cache
			var map = new HashMap<PathObject, Geometry>();
			var mapToMerge = new HashMap<PathObject, List<Geometry>>();
			// Keep track of objects associated with invalid polygons (although this is not currently used)
			var invalidPolygons = new HashSet<Geometry>();
			var invalidPolygonObjects = new HashSet<PathObject>();
			for (var polygon : polygons) {
				var coord = (Coordinate)polygon.getUserData();
				if (coord == null) {
					// Shouldn't happen...
					logger.debug("Missing coordinate!");
					continue;
				}
				var pathObject = coordinateMap.getOrDefault(coord, null);
				if (pathObject == null) {
					// Shouldn't happen...
					logger.warn("Missing object for coordinate {}", coord);
					continue;
				}
				// Occasionally happens when a polygon is especially thin (at least with a fixed point precision model) -
				// but this has been mostly addressed by setting IncrementalDelanayTriangulator.forceConvex(false)
				// We don't have a fix now, but we could one day...
				if (!polygon.isValid()) {
					invalidPolygons.add(polygon);
					invalidPolygonObjects.add(pathObject);
				}
				var existing = map.put(pathObject, polygon);
				if (existing != null) {
					var list = mapToMerge.computeIfAbsent(pathObject, g -> {
						var l = new ArrayList<Geometry>();
						l.add(existing);
						return l;
					});
					list.add(polygon);
				}
			}
						
			// Merge anything that we need to, and return to default precision
			var precisionReducer = new GeometryPrecisionReducer(GeometryTools.getDefaultFactory().getPrecisionModel());
			for (var entry : mapToMerge.entrySet()) {
				var pathObject = entry.getKey();
				
				var list = entry.getValue();
				Geometry geometry = null;
				try {
					geometry = GeometryCombiner.combine(list);
					geometry = geometry.buffer(0.0);
					geometry = precisionReducer.reduce(geometry);
				} catch (Exception e) {
                    logger.debug("Error doing fast geometry combine for Voronoi faces: {}", e.getMessage(), e);
					try {
						geometry = GeometryTools.union(list);
					} catch (Exception e2) {
                        logger.debug("Error doing fallback geometry combine for Voronoi faces: {}", e2.getMessage(), e2);
					}
				}
//				// If there were invalid pieces, we could try to fix this
//				if (invalidPolygonObjects.contains(pathObject)) {
//					pathObject.setPathClass(PathClassFactory.getPathClass("Trouble"));
//				}
				map.put(pathObject, geometry);
			}
			if (!invalidPolygons.isEmpty()) {
				// Invalid polygons can happen quite a bit for dense computations - so only warn if we have a *lot*,
				// otherwise just log at debug level
				String message = "Number of invalid polygons found in Voronoi diagram: {}/{}";
				logger.warn(message, invalidPolygons.size(), polygons.size());
			}
			return map;
		}

		
		private synchronized Map<PathObject, Geometry> calculateVoronoiFaces() {
			
			if (pathObjects.size() < coordinateMap.size()) {
				return calculateVoronoiFacesByLocations();
			}

			logger.debug("Calculating Voronoi faces for {} objects", size());

			@SuppressWarnings("unchecked")
			var polygons = (List<Polygon>)subdivision.getVoronoiCellPolygons(GeometryTools.getDefaultFactory());

			var map = new HashMap<PathObject, Geometry>();
			var mapToMerge = new HashMap<PathObject, List<Geometry>>();
			
			// Get the polygons for each object
			for (var polygon : polygons) {
				if (polygon.isEmpty() || !(polygon instanceof Polygonal))
					continue;

				var coord = (Coordinate)polygon.getUserData();
				var pathObject = coordinateMap.get(coord);
				if (pathObject == null) {
					logger.warn("No detection found for {}", coord);
					continue;
				}
				
				var existing = map.put(pathObject, polygon);
				if (existing != null) {
					var list = mapToMerge.computeIfAbsent(pathObject, g -> {
						var l = new ArrayList<Geometry>();
						l.add(existing);
						return l;
					});
					list.add(polygon);
				}
			}
			
			// Merge anything that we need to
			for (var entry : mapToMerge.entrySet()) {
				var pathObject = entry.getKey();
				
				var list = entry.getValue();
				Geometry geometry = null;
				try {
					geometry = GeometryCombiner.combine(list).buffer(0.0);
				} catch (Exception e) {
                    logger.debug("Error doing fast geometry combine for Voronoi faces: {}", e.getMessage(), e);
					try {
						geometry = GeometryTools.union(list);
					} catch (Exception e2) {
                        logger.debug("Error doing fallback geometry combine for Voronoi faces: {}", e2.getMessage(), e2);
					}
				}
				map.put(pathObject, geometry);
			}
			return map;
		}
		
		private List<PathObject> filterByPredicate(PathObject pathObject, List<? extends PathObject> list, BiPredicate<PathObject, PathObject> predicate) {
			return list.stream()
					.filter(p -> predicate.test(pathObject, p))
					.collect(Collectors.toList());
		}
		
		/**
		 * Get clusters of connected objects, where connections are made between neighboring objects that meet the specified predicate.
		 * @param predicate predicate used to determine if two otherwise neighboring objects are considered connected
		 * @return a list of clusters, where each cluster is a collection of connected objects
		 */
		public List<Collection<PathObject>> getClusters(BiPredicate<PathObject, PathObject> predicate) {
			var alreadyClustered = new HashSet<PathObject>();
			var output = new ArrayList<Collection<PathObject>>();
			var neighbors = getFilteredNeighbors(predicate);
			for (var pathObject : getObjects()) {
				if (!alreadyClustered.contains(pathObject)) {
					var cluster = buildCluster(pathObject, neighbors, alreadyClustered);
					output.add(cluster);
				}
			}
			return output;
		}
		
		private Collection<PathObject> buildCluster(PathObject parent, Map<PathObject, List<PathObject>> neighbors, Collection<PathObject> alreadyClustered) {
			var cluster = new ArrayList<PathObject>();
			var deque = new ArrayDeque<PathObject>();
			deque.add(parent);
			while (!deque.isEmpty()) {
				var pathObject = deque.pop();
				if (!alreadyClustered.add(pathObject))
					continue;
				
				cluster.add(pathObject);
				
				for (var neighbor : neighbors.get(pathObject)) {
					if (!alreadyClustered.contains(neighbor))
						deque.add(neighbor);
				}
			}
			return cluster;
		}
		
	}

	
	/**
	 * {@link QuadEdgeLocator} that simply starts from the first valid vertex.
	 * This appears to work much faster than the default {@link LastFoundQuadEdgeLocator}.
	 */
	static class FirstVertexLocator implements QuadEdgeLocator {
		
		private final QuadEdgeSubdivision subdiv;
		private QuadEdge firstLiveEdge;
		
		FirstVertexLocator(QuadEdgeSubdivision subdiv) {
			this.subdiv = subdiv;
		}
		
		private QuadEdge firstEdge() {
			if (firstLiveEdge == null || !firstLiveEdge.isLive())
				firstLiveEdge = (QuadEdge)subdiv.getEdges().iterator().next();
			return firstLiveEdge;
		}

		@Override
		public QuadEdge locate(Vertex v) {
			return subdiv.locateFromEdge(v, firstEdge());
		}
		
	}

	/**
	 * A more complicated - but ultimately unsuccessful - {@link QuadEdgeLocator}.
	 * May sometimes outperform {@link LastFoundQuadEdgeLocator}, but often still much slower 
	 * than {@link FirstVertexLocator}.
	 */
	@SuppressWarnings("unused")
	private static class QuadTreeQuadEdgeLocator implements QuadEdgeLocator {
		
		private final Quadtree tree;
		private final QuadEdgeSubdivision subdiv;
		private final Envelope env;
		private QuadEdge lastEdge;
		private final Set<QuadEdge> existingEdges;
		
		private int calledFirst = 0;
		private int usedCache = 0;
		
		QuadTreeQuadEdgeLocator(QuadEdgeSubdivision subdiv) {
			this.subdiv = subdiv;
			this.tree = new Quadtree();
			this.env = new Envelope();
			this.existingEdges = new HashSet<>();
		}
		
		private QuadEdge firstEdge() {
			calledFirst++;
			return (QuadEdge)subdiv.getEdges().iterator().next();
		}
		
		void debugLog() {
			logger.info("Called first edge: {} times", calledFirst);
			logger.info("Used spatial cache: {} times", usedCache);
		}

		@SuppressWarnings("unchecked")
		@Override
		public QuadEdge locate(Vertex v) {
			double pad = 2.0;
			env.init(v.getX()-pad, v.getX()+pad, v.getY()-pad, v.getY()+pad);
			
			QuadEdge closestEdge = null;
			double closestDistance = Double.POSITIVE_INFINITY;
			var coord = v.getCoordinate();
			if (lastEdge != null && lastEdge.isLive()) {
				closestDistance = Math.min(
						lastEdge.orig().getCoordinate().distance(coord),
						lastEdge.dest().getCoordinate().distance(coord)
						);
				closestEdge = lastEdge;
			}
			
			if (closestDistance > pad) {
				var list = (List<QuadEdge>)tree.query(env);
				for (var e : list) {
					if (e.isLive()) {
						double dist = Math.min(
								e.orig().getCoordinate().distance(coord),
								e.dest().getCoordinate().distance(coord)
								);
						if (dist < closestDistance) {
							closestEdge = e;
							closestDistance = dist;
						}
	//					return lastEdge;
					} else {
						existingEdges.remove(e);
						tree.remove(env, e);
					}
				}
				if (closestEdge != null) {
					usedCache++;
					lastEdge = closestEdge;
				}

				if (lastEdge == null || !lastEdge.isLive())
					lastEdge = firstEdge();
			}
			
			lastEdge = subdiv.locateFromEdge(v, lastEdge);
			if (existingEdges.add(lastEdge)) {
//				env.init(lastEdge.orig().getCoordinate(), lastEdge.dest().getCoordinate());
//				tree.insert(env, lastEdge);
				env.init(lastEdge.orig().getCoordinate());
				tree.insert(env, lastEdge);
				env.init(lastEdge.dest().getCoordinate());
				tree.insert(env, lastEdge);
			}
			
			return lastEdge;
		}
		
	}
	

}