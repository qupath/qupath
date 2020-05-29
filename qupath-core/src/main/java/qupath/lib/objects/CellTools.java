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

package qupath.lib.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.Centroid;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.simplify.VWSimplifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.DelaunayTools;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Helper class for working with {@linkplain PathObject PathObjects} that represent cells.
 * 
 * @author Pete Bankhead
 */
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

	/**
	 * Adjust cell boundary ROIs to be non-overlapping, by assigning overlaps to the cell with the closest nucleus.
	 * Results are returned as a new list of cells.
	 * @param cells input cells
	 * @return a new list of cells, potentially containing some of the original cells and other adjusted cells
	 */
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
			var geomCell = estimateCellBoundary(geomNucleus, distance, nucleusScale, transform);
			map.put(detection, geomCell);
		}
		return detectionsToCells(map);
	}
	
	/**
	 * Estimate a cell boundary using {@link Geometry} objects.
	 * This avoids the need to create {@link ROI} or {@link PathObject} first.
	 * 
	 * @param geomNucleus {@link Geometry} representing the cell nucleus
	 * @param distance distance to expand the nucleus, in pixels
	 * @param nucleusScale optional maximum expansion distance defined in terms of scaling the nucleus ROI about its centroid; ignored if &le; 1
	 * @return a {@link Geometry} providing a cell boundary estimate
	 */
	public static Geometry estimateCellBoundary(Geometry geomNucleus, double distance, double nucleusScale) {
		return estimateCellBoundary(geomNucleus, distance, nucleusScale, new AffineTransformation());
	}
	
	private static Geometry estimateCellBoundary(Geometry geomNucleus, double distance, double nucleusScale, AffineTransformation transform) {
		var geomCell = geomNucleus.buffer(distance);
		if (nucleusScale > 1) {
			geomNucleus = geomNucleus.convexHull();
			var centroid = new Centroid(geomNucleus).getCentroid();
			double x = centroid.getX();
			double y = centroid.getY();
			transform.setToTranslation(-x, -y);
			transform.scale(nucleusScale, nucleusScale);
			transform.translate(x, y);
			var geomNucleusExpanded = transform.transform(geomNucleus);
			if (!geomNucleusExpanded.covers(geomCell))
				geomCell = geomCell.intersection(geomNucleusExpanded);
			if (geomNucleus.getNumGeometries() == 1 && geomCell.getNumGeometries() == 1 && !geomCell.covers(geomNucleus))
				geomCell = geomCell.union(geomNucleus);
		}
		return geomCell;
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
		int max = 500;
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
	
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
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
		
		var subdivision = DelaunayTools.newBuilder(allDetections)
			.preferNucleus(true)
			.roiBounds(2.0, 1)
			.build();
		
		var cells = new ArrayList<PathObject>();
		var faces = subdivision.getVoronoiFaces();
		for (var detection : detections) {
			var face = faces.get(detection);
			var bounds = cellBoundaryMap.get(detection);
			if (face == null || bounds == null) {
				logger.warn("Missing boundary information for {} - will skip", detection);
				continue;
			}
			var geomCell = face;
			try {
				geomCell = GeometryTools.ensurePolygonal(face.intersection(bounds));
				geomCell = VWSimplifier.simplify(geomCell, 1.0);
			} catch (Exception e) {
				if (face.getArea() > bounds.getArea()) {
					geomCell = bounds;
					logger.warn("Error computing intersection between cell boundary and Voronoi face - will use bounds result: " + e.getLocalizedMessage(), e);
				} else
					logger.warn("Error computing intersection between cell boundary and Voronoi face - will use Voronoi result: " + e.getLocalizedMessage(), e);
			}
			var roiNucleus = PathObjectTools.getROI(detection, true);
			var roiCell = roiNucleus;
			if (geomCell.isEmpty()) {
				logger.warn("Unable to create cell ROI for {} - I'll use the nucleus ROI instead", detection);
			} else {
				var geomNucleus = roiNucleus.getGeometry();
				if (!geomCell.covers(geomNucleus)) {
					try {
						geomNucleus = geomCell.intersection(geomNucleus);
						roiNucleus = GeometryTools.geometryToROI(geomNucleus, roiNucleus.getImagePlane());
					} catch (Exception e) {
						logger.debug("Error constraining nucleus to cell: {}", e.getLocalizedMessage());
					}
				}
				roiCell = GeometryTools.geometryToROI(geomCell, roiNucleus.getImagePlane());
			}
			cells.add(PathObjects.createCellObject(roiCell, roiNucleus, detection.getPathClass(), detection.getMeasurementList()));
		}
		return cells;
	}
	
	
	
	/**
	 * Constrain a cell boundary to fall within a maximum region, determined by buffering the convex hull of the nucleus ROI by a fixed distance.
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
		  var geomNucleus = roiNucleus.getGeometry().convexHull();
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