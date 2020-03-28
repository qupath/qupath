package qupath.lib.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.geom.util.GeometryCombiner;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.simplify.VWSimplifier;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

public class CellTools {
	
	private static final Logger logger = LoggerFactory.getLogger(CellTools.class);
	
	
	/**
	 * Constrain a cell boundary to fall within a maximum region, determined by scaling the nucleus ROI by a fixed scale factor 
	 * about its centroid.
	 * This can be used to create more biologically plausible cell boundaries in cases where the initial boundary estimates may be 
	 * too large.
	 * 
	 * @param cell original cell object
	 * @param nucleusScaleFactor scale factor by which the nucleus should be expanded to defined maximum cell size
	 * @param keepMeasurements if true, retain the measurements of the original cell if creating a new cell; if false, discard existing measurements
	 * @return the updated cell object, or the original cell object either if its boundary falls within the specified limit or it lacks both boundary and nucleus ROIs
	 */
	public static PathCellObject constrainCellByScaledNucleus(PathCellObject cell, double nucleusScaleFactor, boolean keepMeasurements) {
		  var roi = cell.getROI();
		  var roiNucleus = cell.getNucleusROI();
		  if (roi == null || roiNucleus == null)
		    return cell;
		  var geom = roi.getGeometry();
		  var geomNucleus = roiNucleus.getGeometry();
		  var centroid = geomNucleus.getCentroid();
		  var transform = AffineTransformation.scaleInstance(
				  nucleusScaleFactor, nucleusScaleFactor, centroid.getX(), centroid.getY());
		  var geomNucleusExpanded = transform.transform(geomNucleus);
		  if (geomNucleusExpanded.covers(geom))
		    return cell;
		  geom = geom.intersection(geomNucleusExpanded);
		  geom = GeometryTools.ensurePolygonal(geom);
		  roi = GeometryTools.geometryToROI(geom, roi.getImagePlane());
		  return (PathCellObject)PathObjects.createCellObject(
		          roi, roiNucleus, cell.getPathClass(), keepMeasurements ? cell.getMeasurementList() : null
		          );
	}

	
	public static List<PathObject> constrainCellOverlaps(Collection<? extends PathObject> cells) {
		var map = new HashMap<PathObject, Geometry>();
		for (var cell : cells) {
			if (!cell.isCell()) {
				logger.warn("{} is not a cell - will be skipped!", cell);
				continue;
			}
			map.put(cell, cell.getROI().getGeometry());
 		}
		return detectionsToCells(map);
	}
	
	/**
	 * Create cell objects by expanding the (nucleus) ROIs from existing detections to approximate the full cell boundary.
	 * 
	 * @param detections the detection objects from which to create the cells; these define the nuclei
	 * @param distance the maximum distance (in pixels) to expand each nucleus
	 * @param nucleusScale the maximum size of the cell relative to the nucleus (ignored if &le; 1).
	 * @return cell objects derived from the supplied detections. This may have fewer entries if not all detections could be used successfully.
	 */
	public static List<PathObject> detectionsToCells(Collection<? extends PathObject> detections, double distance, double nucleusScale) {
		
		var transform = new AffineTransformation();
		
		var map = new HashMap<PathObject, Geometry>();
		for (var detection : detections) {
			var roiNucleus = PathObjectTools.getROI(detection, true);
			var geomNucleus = roiNucleus.getGeometry();
			var geomCell = geomNucleus.buffer(distance);
			if (nucleusScale > 1) {
				double x = roiNucleus.getCentroidX();
				double y = roiNucleus.getCentroidY();
				transform.setToTranslation(-x, -y);
				transform.scale(nucleusScale, nucleusScale);
				transform.translate(x, y);
				var geomNucleusExpanded = transform.transform(geomNucleus);
				if (!geomNucleusExpanded.covers(geomCell))
					geomCell = geomCell.intersection(geomNucleusExpanded);
				if (geomNucleus.getNumGeometries() == 1 && geomCell.getNumGeometries() == 1 && !geomCell.covers(geomNucleus))
					geomCell = geomCell.union(geomNucleus);
			}
			map.put(detection, geomCell);
		}
		return detectionsToCells(map);
	}
	
	/**
	 * Convert detections to cells, using pre-computed boundary geometry estimates.
	 * This purpose of this method is to apply an additional Voronoi constraint to refine the estimates.
	 * @param cellBoundaryMap
	 * @return
	 */
	private static List<PathObject> detectionsToCells(Map<PathObject, Geometry> cellBoundaryMap) {
		
		// Creating a large, dense triangulation can be very slow
		// Here, we create a spatial cache with a mapping to the preferred expanded Geometry
		int max = 200;
		if (cellBoundaryMap.size() > max) {
			var cache = new STRtree(max);
			var envelopes = new HashMap<PathObject, Envelope>();
			var geometries = new HashMap<PathObject, Geometry>();
			var nucleusGeometries = new HashMap<PathObject, Geometry>();
			for (var entry : cellBoundaryMap.entrySet()) {
				var detection = entry.getKey();
				var roi = PathObjectTools.getROI(detection, true);
				var geomNucleus = roi.getGeometry();
				var geomCell = entry.getValue();
				var env = geomCell.getEnvelopeInternal();
				envelopes.put(detection, env);
				geometries.put(detection, geomCell);
				nucleusGeometries.put(detection, geomNucleus);
				cache.insert(env, detection);
			}
			cache.build();
			var items = cache.itemsTree();
			return detectionsToCellsSubtree(cache, items, cellBoundaryMap, envelopes);
		} else
			return detectionsToCells(cellBoundaryMap.keySet(), cellBoundaryMap.keySet(), cellBoundaryMap);
	}
	
	
	private static List<PathObject> detectionsToCellsSubtree(STRtree tree, List<?> list, Map<PathObject, Geometry> cellBoundaryMap, Map<PathObject, Envelope> envelopes) {
		if (list.isEmpty())
			return Collections.emptyList();
		var first = list.get(0);
		
		boolean doParallel = true;

		if (first instanceof PathObject) {
			var env = new Envelope();
			var pathObjects = (Collection<PathObject>)list;
			for (var pathObject : pathObjects)
				env.expandToInclude(envelopes.get(pathObject));
			var allObjects = (List<PathObject>)tree.query(env);
			try {
				return detectionsToCells(pathObjects, allObjects, cellBoundaryMap);
			} catch (Exception e) {
				logger.error("Error converting detections to cells: " + e.getLocalizedMessage(), e);
				return Collections.emptyList();
			}
		} else if (first instanceof List) {
			if (doParallel) {
				return list.parallelStream()
						.map(sublist -> detectionsToCellsSubtree(tree, (List)sublist, cellBoundaryMap, envelopes))
						.flatMap(Collection::stream)
						.collect(Collectors.toList());
			} else {
				var cells = new ArrayList<PathObject>();
				for (var sublist : list)
					cells.addAll(detectionsToCellsSubtree(tree, (List)sublist, cellBoundaryMap, envelopes));
				return cells;
			}
		} else
			throw new IllegalArgumentException("Expected a list of PathObjects or a list of lists, but got a list of " + first.getClass());
	}
	
	
	private static List<PathObject> detectionsToCells(Collection<PathObject> detections, Collection<PathObject> allDetections, Map<PathObject, Geometry> cellBoundaryMap) {
		
		double densityFactor = 4.0;
		
		var coords = new HashMap<Coordinate, PathObject>();
		var map = new HashMap<PathObject, List<Geometry>>();
		
		// We need to densify
		for (var detection : allDetections) {
			var roi = PathObjectTools.getROI(detection, true);
			var geom = roi.getGeometry();
			
			var list = new ArrayList<Geometry>();
			map.put(detection, list);
			
			geom = Densifier.densify(geom, densityFactor);
			var coordsTemp = geom.getCoordinates();
			
			for (var c : coordsTemp) {
				coords.put(c, detection);
			}
		}
		
		// Attempts to call VoronoiDiagramBuilder would sometimes fail when clipping to the envelope - 
		// Because we do our own clipping anyway, we skip that step by requesting the diagram via the subdivision instead
		var builder = new VoronoiDiagramBuilder();
		builder.setSites(coords.keySet());
		var diagram = builder.getSubdivision().getVoronoiDiagram(GeometryTools.getDefaultFactory());
//		var diagram = builder.getDiagram(GeometryTools.getDefaultFactory());
		
		var cells = new ArrayList<PathObject>();
		for (int i = 0; i < diagram.getNumGeometries(); i++) {
			var geomCell = diagram.getGeometryN(i);
			if (!(geomCell instanceof Polygonal))
				continue;
			var coord = (Coordinate)geomCell.getUserData();
			var detection = coords.get(coord);
			map.get(detection).add(geomCell);
		}
		
		for (var detection : detections) {
			var faces = map.get(detection);
			var roiNucleus = PathObjectTools.getROI(detection, true);

			var geomCell = GeometryCombiner.combine(faces).buffer(0.0);
			
			// If we have a polygon, it shouldn't contain any holes
			if (geomCell instanceof Polygon) {
				var poly = (Polygon)geomCell;
				if (poly.getNumInteriorRing() > 0) {
					var factory = poly.getFactory();
					geomCell = factory.createPolygon(poly.getExteriorRing().getCoordinateSequence());
				}
			}
			
			// Constrain by voronoi
			var nucleusBuffered = cellBoundaryMap.get(detection);
			try {
				geomCell = geomCell.intersection(nucleusBuffered);
				geomCell = GeometryTools.ensurePolygonal(geomCell);
			} catch (Exception e) {
				logger.error("Problem with Voronoi cell intersection: " + e.getLocalizedMessage(), e);
				logger.debug("Voronoi cell valid: {}, Buffered nucleus valid: {}", geomCell.isValid(), nucleusBuffered.isValid());
			}
			
			try {
				int nPoints = geomCell.getNumPoints();
				geomCell = VWSimplifier.simplify(geomCell, 1.0);
				int nPointsAfter = geomCell.getNumPoints();
				logger.debug("Reduced cell boundary from {} to {} points", nPoints, nPointsAfter);
			} catch (Exception e) {
				logger.error("Problem simplifying cell boundary: " + e.getLocalizedMessage(), e);
			}
			
			ROI roiCell;
			if (geomCell.isEmpty()) {
				logger.warn("Failed to create cell for Geometry: using the nucleus instead");
				roiCell = roiNucleus;
			} else
				roiCell = GeometryTools.geometryToROI(geomCell, roiNucleus.getImagePlane());
			
			var geomNucleus = roiNucleus.getGeometry();
			if (geomCell.getNumGeometries() == 1 && geomNucleus.getNumGeometries() == 1 && !geomCell.covers(geomNucleus))
				roiNucleus = GeometryTools.geometryToROI(geomCell.intersection(geomNucleus), roiNucleus.getImagePlane());
			
			cells.add(PathObjects.createCellObject(roiCell, roiNucleus, detection.getPathClass(), detection.getMeasurementList()));
 		}
		
		return cells;
	}
	
	
	private static PathObject detectionToCell(PathObject detection, double distance) {
		var roiNucleus = PathObjectTools.getROI(detection, true);
		var geomCell = roiNucleus.getGeometry();
		geomCell = geomCell.convexHull();
//		geomCell = geomCell.buffer(distance);
//		geomCell = new OctagonalEnvelope(geomCell).toGeometry(geomCell.getFactory());
		var roiCell = GeometryTools.geometryToROI(geomCell, roiNucleus.getImagePlane());
		return PathObjects.createCellObject(roiCell, roiNucleus, detection.getPathClass(), detection.getMeasurementList());
	}
	
	
	
	/**
	 * Constrain a cell boundary to fall within a maximum region, determined by buffering nucleus ROI by a fixed distance.
	 * This can be used to create more biologically plausible cell boundaries in cases where the initial boundary estimates may be 
	 * too large.
	 * 
	 * @param cell original cell object
	 * @param distance distance (in pixels) by which the nucleus should be expanded to defined maximum cell size
	 * @param keepMeasurements if true, retain the measurements of the original cell if creating a new cell; if false, discard existing measurements
	 * @return the updated cell object, or the original cell object either if its boundary falls within the specified limit or it lacks both boundary and nucleus ROIs
	 */
	public static PathCellObject constrainCellByNucleusDistance(PathCellObject cell, double distance, boolean keepMeasurements) {
		  var roi = cell.getROI();
		  var roiNucleus = cell.getNucleusROI();
		  if (roi == null || roiNucleus == null || distance <= 0)
		    return cell;
		  var geom = roi.getGeometry();
		  var geomNucleus = roiNucleus.getGeometry();
		  var geomNucleusExpanded = geomNucleus.buffer(distance);
		  if (geomNucleusExpanded.covers(geom))
		    return cell;
		  geom = geom.intersection(geomNucleusExpanded);
		  geom = GeometryTools.ensurePolygonal(geom);
		  roi = GeometryTools.geometryToROI(geom, roi.getImagePlane());
		  return (PathCellObject)PathObjects.createCellObject(
		          roi, roiNucleus, cell.getPathClass(), keepMeasurements ? cell.getMeasurementList() : null
		          );
	}
	

}
