package qupath.lib.objects;

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
import java.util.stream.Collectors;

import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.GeometryCombiner;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
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
	
	private final static Logger logger = LoggerFactory.getLogger(DelaunayTools.class);
	
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
		
		for (var pathObject : pathObjects) {
			var roi = PathObjectTools.getROI(pathObject, preferNucleusROI);
			
			if (plane == null)
				plane = roi.getImagePlane();
			else if (!plane.equals(roi.getImagePlane())) {
				logger.warn("Non-matching image planes: {} and {}! Object will be skipped...", plane, roi.getImagePlane());
				continue;
			}
			
			var coord = new Coordinate(roi.getCentroidX(), roi.getCentroidY());
			coords.put(coord, pathObject);
		}
		return new Subdivision(createSubdivision(coords.keySet()), pathObjects, coords, plane, preferNucleusROI);
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
				coords.put(c, pathObject);
			}
		}
		
		// Attempts to call VoronoiDiagramBuilder would sometimes fail when clipping to the envelope - 
		// Because we do our own clipping anyway, we skip that step by requesting the diagram via the subdivision instead
		return new Subdivision(createSubdivision(coords.keySet()), pathObjects, coords, plane, preferNucleusROI);
	}
	
	
	private static QuadEdgeSubdivision createSubdivision(Collection<Coordinate> coords) {
		var builder = new VoronoiDiagramBuilder();
		var coordsList = new ArrayList<Coordinate>(coords);
//		Collections.sort(coordsList, Comparator.comparingDouble(c -> c.getX()*c.getX() + c.getY()*c.getY()));
//		Collections.sort(coordsList, Comparator.comparingDouble(Coordinate::getX).thenComparingDouble(Coordinate::getY));
//		Collections.sort(coordsList, Comparator.comparingDouble(Coordinate::getY).thenComparingDouble(Coordinate::getX));
//		Collections.reverse(coordsList);
		builder.setTolerance(0.01);
//		builder.getSubdivision().setLocator(locator);
		builder.setSites(coordsList);
		return builder.getSubdivision();
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
	 * @param maxDistance maximum separation between ROI boundaries
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
	
	public static Collection<PathObject> classifyObjectsByCluster(Collection<Collection<? extends PathObject>> clusters) {
		int c = 1;
		var list = new ArrayList<PathObject>();
		for (var cluster : clusters) {
			var pathClass = PathClassFactory.getPathClass("Cluster " + c);
			for (var pathObject : cluster) {
				pathObject.setPathClass(pathClass);
				list.add(pathObject);
			}
			c++;
		}
		return list;
	}
	
	
	/**
	 * Helper class for extracting information from a Delaunay triangulation computed from {@linkplain PathObject PathObjects}.
	 */
	public static class Subdivision {
		
		private final static Logger logger = LoggerFactory.getLogger(Subdivision.class);
		
		private Set<PathObject> pathObjects = new LinkedHashSet<>();
		private Map<Coordinate, PathObject> coordinateMap = new HashMap<>();
		private QuadEdgeSubdivision subdivision;
		
		private ImagePlane plane;
		private boolean preferNucleus;
		
		private transient Map<PathObject, List<PathObject>> neighbors;
		private transient Map<PathObject, Geometry> voronoiFaces;
		
		
		private Subdivision(QuadEdgeSubdivision subdivision, Collection<PathObject> pathObjects, Map<Coordinate, PathObject> coordinateMap, ImagePlane plane, boolean preferNucleus) {
			this.subdivision = subdivision;
			this.plane = plane;
			this.pathObjects.addAll(pathObjects);
			this.coordinateMap.putAll(coordinateMap);
			this.pathObjects = Collections.unmodifiableSet(this.pathObjects);
			this.coordinateMap = Collections.unmodifiableMap(this.coordinateMap);
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
		 * @see #getVoronoiROIs(ImageRegion)
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
		 * Get a map of Voronoi faces, convered to {@link ROI} objects.
		 * @param clip optional region used to clip the total extent of the ROIs
		 * @return
		 * @see #getVoronoiFaces()
		 */
		public Map<PathObject, ROI> getVoronoiROIs(ImageRegion clip) {
			var faces = getVoronoiFaces();
			var map = new HashMap<PathObject, ROI>();
			var clipGeom = clip == null ? null : GeometryTools.regionToGeometry(clip);
			for (var entry : faces.entrySet()) {
				var pathObject = entry.getKey();
				var face = entry.getValue();
				if (clipGeom != null && !clipGeom.covers(face))
					face = clipGeom.intersection(face);
				var roi = GeometryTools.geometryToROI(face, pathObject.getROI().getImagePlane());
				map.put(pathObject, roi);
			}
			return map;
		}
		
		/**
		 * Get all the objects associated with this subdivision.
		 * @return
		 */
		public Collection<PathObject> getPathObjects() {
			return pathObjects;
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
			if (neighbors == null) {
				synchronized (this) {
					if (neighbors == null)
						neighbors = Collections.unmodifiableMap(calculateAllNeighbors());
				}
			}
			return neighbors;
		}
		
		private synchronized Map<PathObject, List<PathObject>> calculateAllNeighbors() {
			
			logger.trace("Calculating all neighbors for {} objects", getPathObjects().size());
			
			@SuppressWarnings("unchecked")
			var edges = (List<QuadEdge>)subdivision.getVertexUniqueEdges(false);
			Map<PathObject, List<PathObject>> map = new HashMap<>();
			var distanceMap = new HashMap<PathObject, Double>();
			
			int missing = 0;
			for (var edge : edges) {
				var origin = edge.orig();
				distanceMap.clear();
				
				var pathObject = getPathObject(origin);
				if (pathObject == null) {
					logger.warn("No object found for {}", pathObject);
					continue;
				}
				
				var list = new ArrayList<PathObject>();
				var next = edge;
				do {
					var dest = next.dest();
					var destObject = getPathObject(dest);
					if (destObject == pathObject) {
						continue;
					} else if (destObject == null) {
						missing++;
					} else {
						distanceMap.put(destObject, next.getLength());
						list.add(destObject);
					}
				} while ((next = next.oNext()) != edge);
				Collections.sort(list, Comparator.comparingDouble(p -> distanceMap.get(p)));
				
				map.put(pathObject, Collections.unmodifiableList(list));
			}
			logger.debug("Number of missing neighbors: {}", missing);
			return map;
		}
		
		private PathObject getPathObject(Vertex vertex) {
			return coordinateMap.get(vertex.getCoordinate());
		}
		
		private synchronized Map<PathObject, Geometry> calculateVoronoiFaces() {
			
			logger.trace("Calculating Voronoi faces for {} objects", getPathObjects().size());
			
			@SuppressWarnings("unchecked")
			var polygons = (List<Polygon>)subdivision.getVoronoiCellPolygons(GeometryTools.getDefaultFactory());
			
			var map = new HashMap<PathObject, Geometry>();
			var mapToMerge = new HashMap<PathObject, List<Geometry>>();
			
			// Get the polygons for each object
			for (var polygon : polygons) {
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
					logger.debug("Error doing fast geometry combine: " + e.getLocalizedMessage(), e);
					try {
						geometry = GeometryTools.union(list);
					} catch (Exception e2) {
						logger.debug("Error doing fallback geometry combine: " + e2.getLocalizedMessage(), e2);
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
			for (var pathObject : getPathObjects()) {
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
	

}
