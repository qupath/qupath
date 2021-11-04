/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * A collection of static methods to help work with PathObjects.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjectTools {
	
	final private static Logger logger = LoggerFactory.getLogger(PathObjectTools.class);

	/**
	 * Remove objects with PointsROI from a collection.
	 * @param pathObjects
	 */
	private static void removePoints(Collection<PathObject> pathObjects) {
		logger.trace("Remove points");
		Iterator<PathObject> iter = pathObjects.iterator();
		while (iter.hasNext()) {
			if (hasPointROI(iter.next()))
				iter.remove();
		}
	}
	
	/**
	 * Returns true if a PathObject has a Point ROI.
	 * @param pathObject
	 * @return
	 */
	public static boolean hasPointROI(PathObject pathObject) {
		return pathObject.hasROI() && pathObject.getROI().isPoint();
	}
	
	/**
	 * Count the number of PathObjects in a collection with a specified class or base class.
	 * 
	 * @param pathObjects
	 * @param pathClass
	 * @param useBaseClass
	 * @return
	 */
	public static int countObjectsWithClass(final Collection<? extends PathObject> pathObjects, final PathClass pathClass, final boolean useBaseClass) {
		int count = 0;
		for (PathObject pathObject : pathObjects) {
			PathClass currentClass = pathObject.getPathClass();
			if (useBaseClass)
				currentClass = currentClass == null ? null : currentClass.getBaseClass();
			if (Objects.equals(pathClass, currentClass))
				count++;
		}
		return count;
	}
	
	
	/**
	 * Get the PathObjects in a collection that are instances of a specified class.
	 * 
	 * @param pathObjects
	 * @param cls
	 * @return
	 */
	public static List<PathObject> getObjectsOfClass(final Collection<PathObject> pathObjects, final Class<? extends PathObject> cls) {
		logger.trace("Get objects of class {}", cls);
		List<PathObject> pathObjectsFiltered = new ArrayList<>(pathObjects.size());
		for (PathObject temp : pathObjects) {
			if (cls == null || cls.isInstance(temp)) {
				pathObjectsFiltered.add(temp);
			}
		}
		return pathObjectsFiltered;
	}

	
	/**
	 * Create a predicate that only accepts PathObjects if they have ROIs that fall within a specified ImageRegion.
	 * @param region
	 * @return
	 */
	public static Predicate<PathObject> createImageRegionPredicate(ImageRegion region) {
		return new ImageRegionPredicate(region);
	}
	
	private static class ImageRegionPredicate implements Predicate<PathObject> {
		
		private ImageRegion region;
		private PreparedGeometry geometry;
		
		ImageRegionPredicate(ImageRegion region) {
			this.region = region;
			this.geometry = PreparedGeometryFactory.prepare(ROIs.createRectangleROI(region).getGeometry());
//			this.envelope = new Envelope(region.getMinX(), region.getMaxX(), region.getMinY(), region.getMaxY());
		}
		
		@Override
		public boolean test(PathObject p) {
			return p.hasROI() &&
					region.intersects(ImageRegion.createInstance(p.getROI())) &&
					geometry.intersects(p.getROI().getGeometry());
		}
		
	}
	

	/**
	 * Get all descendant objects as a flattened list.
	 * 
	 * @param parentObject the parent objects whose children and descendants should be added to the list
	 * @param list output list, optional
	 * @param includeParent if true, parentObject will be included in the output list
	 * @return either list, or a new list created if necessary
	 */
	public static List<PathObject> getFlattenedObjectList(PathObject parentObject, List<PathObject> list, boolean includeParent) {
		if (list == null)
			list = new ArrayList<>();
		if (includeParent)
			list.add(parentObject);
		for (PathObject child : parentObject.getChildObjectsAsArray())
			getFlattenedObjectList(child, list, true);
		return list;
	}

	/**
	 * Count the descendants of a PathObject recursively.
	 * 
	 * @param pathObject
	 * @return
	 */
	public static int countDescendants(final PathObject pathObject) {
//		int count = pathObject.nChildObjects();
//		for (PathObject childObject : pathObject.getChildObjectsAsArray())
//			count += countDescendants(childObject);
//		if (count > 0)
//			System.err.println(count);
//		return count;
		return pathObject.nDescendants();
	}

	

	/**
	 * Test whether one ROI is can completely contain a second ROI.
	 * Returns false if either ROI is null.
	 * <p>
	 * Note: This is not a perfect test, since it really only checks if the vertices of the child ROI fall within the parent - it is possible
	 * that connecting lines stray outside the parent, yet it still returns true.  This behavior may change in later versions.
	 * <p>
	 * TODO: Consider improving 'containsROI' method accuracy.
	 * 
	 * @param parentROI
	 * @param childROI
	 * @return
	 */
	@Deprecated
	public static boolean containsROI(final ROI parentROI, final ROI childROI) {
		// Check for nulls... just to be sure
		if (parentROI == null || childROI == null || !parentROI.isArea() || childROI.isEmpty() || parentROI.isEmpty())
			return false;
		
		// Check points
		if (childROI != null && childROI.isPoint()) {
			for (Point2 p : childROI.getAllPoints()) {
				if (!parentROI.contains(p.getX(), p.getY()))
					return false;
			}
			return true;
		}
		
		// Check areas - child can't have a larger area
		if (childROI.isArea()) {
			if (childROI.getArea() > parentROI.getArea())
				return false;
		}

		// Check bounds dimensions
		if (childROI.getBoundsWidth() > parentROI.getBoundsWidth() || childROI.getBoundsHeight() > parentROI.getBoundsHeight())
			return false;

		// Check bounds
		double px = parentROI.getBoundsX();
		double py = parentROI.getBoundsY();
		double px2 = px + parentROI.getBoundsWidth();
		double py2 = py + parentROI.getBoundsHeight();
		double cx = childROI.getBoundsX();
		double cy = childROI.getBoundsY();
		double cx2 = px + childROI.getBoundsWidth();
		double cy2 = py + childROI.getBoundsHeight();
		if (!(cx >= px && cx2 <= px2 && cy >= py && cy2 <= py2))
			return false;
		
		// Check shapes
		for (Point2 p : childROI.getAllPoints()) {
			if (!parentROI.contains(p.getX(), p.getY()))
				return false;
		}
		
		if (parentROI.isArea() && childROI.isArea())
			return parentROI.getGeometry().covers(childROI.getGeometry());
		
//		List<Point> points = parentArea.getPolygonPoints();
//		for (Point p : childROI.getPolygonPoints()) {
//			int windingNumber = WindingTest.getWindingNumber(points, p.getX(), p.getY());
//			//				logger.info("Winding number: " + windingNumber);
//			if (windingNumber == 0)
//				return false;
//		}
		return true;
//		logger.info("Doing standard (AWT) test...");
//		return PathROIToolsAwt.containsShape(parentArea, (PathShape)childROI);

//		return false;

		//			// Check for lines
		//			if (childROI instanceof PathLineROI) {
		//				PathLineROI line = (PathLineROI)childROI;
		//				return parentROI.contains(line.getX1(), line.getY1()) && parentROI.contains(line.getX2(), line.getY2());
		//			}
		//			
		//			// If we have areas, check these
		//			if (parentROI instanceof PathArea && childROI instanceof PathArea) {
		//				double area = ((PathArea)parentROI).getArea();
		//				if (!Double.isNaN(area) && area < ((PathArea)childROI).getArea())
		//					return false;
		//			}
		//			
		//			// Check bounds
		//	//		if (!parentROI.getBounds2D().contains(childROI.getBounds2D())
		//			Rectangle2D childBounds = childROI.getBounds2D();
		//			if (!parentROI.intersects(childBounds))
		//				return false;
		//			
		//			// If we have shapes, do a proper test
		//			if ((parentROI instanceof PathShape) && (childROI instanceof PathShape)) {
		//				PathShape parentShape = (PathShape)parentROI;
		//				if (parentShape.contains(childBounds))
		//					return true;
		//				PathShape childShape = (PathShape)childROI;
		//				Area areaDifference = parentShape.getShapeAsArea();
		//				areaDifference.subtract(childShape.getShapeAsArea());
		//				return areaDifference.isEmpty();
		//			}
		//			return true;
	}

	
	/**
	 * Get a user-friendly name for a specific type of PathObject, based on its Java class.
	 * 
	 * @param cls
	 * @param makePlural
	 * @return
	 */
	public static String getSuitableName(Class<? extends PathObject> cls, boolean makePlural) {
		if (makePlural) {
			if (cls.equals(PathRootObject.class))
				return "Root objects";
			if (cls.equals(PathAnnotationObject.class))
				return "Annotations";
			if (cls.equals(TMACoreObject.class))
				return "TMA cores";
			if (cls.equals(PathDetectionObject.class))
				return "Detections";
			if (cls.equals(PathCellObject.class))
				return "Cells";
			if (cls.equals(PathTileObject.class))
				return "Tiles";
			return cls.getSimpleName() + " objects";
		}
		if (cls.equals(PathRootObject.class))
			return "Root object";
		if (cls.equals(PathAnnotationObject.class))
			return "Annotation";
		if (cls.equals(TMACoreObject.class))
			return "TMA core";
		if (cls.equals(PathDetectionObject.class))
			return "Detection";
		if (cls.equals(PathCellObject.class))
			return "Cell";
		if (cls.equals(PathTileObject.class))
			return "Tile";
		return cls.getSimpleName();
	}
	
	
	/**
	 * Test whether the ROI associated with one object can completely the ROI of a second object.
	 * Returns false if either ROI is null.
	 * 
	 * @param parentObject
	 * @param childObject
	 * @return
	 */
	@Deprecated
	public static boolean containsObject(PathObject parentObject, PathObject childObject) {
		if (parentObject == null || childObject == null)
			return false;
		return containsROI(parentObject.getROI(), childObject.getROI());
	}

	/**
	 * Query if one object is the ancestor of another.
	 * @param pathObject
	 * @param possibleAncestor
	 * @return
	 */
	public static boolean isAncestor(final PathObject pathObject, final PathObject possibleAncestor) {
		PathObject parent = pathObject.getParent();
		while (parent != null) {
			if (parent.equals(possibleAncestor))
				return true;
			parent = parent.getParent();
		}
		return false;
	}

	/**
	 * Extract a list of TMA cores from an object hierarchy.  If no cores are present, an empty list is returned.
	 * 
	 * @param hierarchy
	 * @param includeMissingCores
	 * @return
	 */
	public static List<TMACoreObject> getTMACoreObjects(final PathObjectHierarchy hierarchy, final boolean includeMissingCores) {
		TMAGrid tmaGrid = hierarchy.getTMAGrid();
		if (tmaGrid == null || tmaGrid.nCores() == 0)
			return Collections.emptyList();
		// If we have a TMA grid, add all the objects
		if (includeMissingCores)
			return tmaGrid.getTMACoreList();
		List<TMACoreObject> cores = new ArrayList<>();
		for (TMACoreObject core : tmaGrid.getTMACoreList()) {
			if (!core.isMissing())
				cores.add(core);
		}
		return cores;
	}
	
	/**
	 * Get the TMA core object that contains a specified PathObject, or null if the object is not contained within a TMA core.
	 * <p>
	 * If the passed object already is a TMACore, it is returned directly.  Otherwise, all ancestors are checked.
	 * 
	 * @param pathObject
	 * @return
	 */
	public static TMACoreObject getAncestorTMACore(final PathObject pathObject) {
		PathObject parent = pathObject;
		while (parent != null && !(parent instanceof TMACoreObject))
			parent = parent.getParent();
		return (TMACoreObject)parent;
	}

	/**
	 * Get the TMA core that contains the specified x &amp; y coordinate, or null if no core is available for the coordinates give.
	 * 
	 * @param tmaGrid
	 * @param x
	 * @param y
	 * @return
	 */
	public static TMACoreObject getTMACoreForPixel(final TMAGrid tmaGrid, final double x, final double y) {
		return getPathObjectContainingPixel(tmaGrid.getTMACoreList(), x, y);
	}

	private static <T extends PathObject> T getPathObjectContainingPixel(Collection<T> pathObjects, double x, double y) {
		for (T pathObject: pathObjects) {
			if (RoiTools.areaContains(pathObject.getROI(), x, y))
				return pathObject;
		}
		return null;
	}
	
	
	/**
	 * Convert a collection of PathObjects to Point annotations, based on ROI centroids, and add the points to the hierarchy.
	 * 
	 * @param hierarchy the object hierarchy containing the objects, and to which the points should be added
	 * @param pathObjects input objects; these are expected to have ROIs
	 * @param preferNucleus if true, request the nucleus ROI from cell objects where possible; if false, request the outer ROI. 
	 * 					    This has no effect if the object is not a cell, or does not have two ROIs.
	 * @param deleteObjects if true, delete the objects from the input collection after point conversion; if false, retain both original objects and points
	 * 
	 * @see #convertToPoints(Collection, boolean)
	 */
	public static void convertToPoints(PathObjectHierarchy hierarchy, Collection<PathObject> pathObjects, boolean preferNucleus, boolean deleteObjects) {
		var points = convertToPoints(pathObjects, preferNucleus);
		if (deleteObjects)
			hierarchy.removeObjects(pathObjects, true);
		hierarchy.addPathObjects(points);
	}
	
	
	/**
	 * Convert a collection of PathObjects to Point annotations, based on ROI centroids.
	 * Each output annotation contains all points corresponding to input objects with the same classification.
	 * Consequently, the size of the output collection is equal to the number of distinct classifications 
	 * found among the input objects.
	 * 
	 * @param pathObjects input objects; these are expected to have ROIs
	 * @param preferNucleus if true, request the nucleus ROI from cell objects where possible; if false, request the outer ROI. 
	 * 					    This has no effect if the object is not a cell, or does not have two ROIs.
	 * @return a collection of annotations with point ROIs
	 * 
	 * @see #convertToPoints(PathObjectHierarchy, Collection, boolean, boolean)
	 */
	public static Collection<PathObject> convertToPoints(Collection<PathObject> pathObjects, boolean preferNucleus) {
		// Create Points lists for each class
		Map<PathClass, Map<ImagePlane, List<Point2>>> pointsMap = new HashMap<>();
		for (PathObject pathObject : pathObjects) {
			var roi = PathObjectTools.getROI(pathObject, preferNucleus);
			if (roi == null)
				continue;
			var plane = roi.getImagePlane();
			PathClass pathClass = pathObject.getPathClass();
			var pointsMapByClass = pointsMap.computeIfAbsent(pathClass, p -> new HashMap<>());
			var points = pointsMapByClass.computeIfAbsent(plane, p -> new ArrayList<>());
			points.add(new Point2(roi.getCentroidX(), roi.getCentroidY()));
		}
		
		// Create & add annotation objects to hierarchy
		List<PathObject> annotations = new ArrayList<>();
		for (Entry<PathClass, Map<ImagePlane, List<Point2>>> entry : pointsMap.entrySet()) {
			var pathClass = entry.getKey();
			for (var entry2 : entry.getValue().entrySet()) {
				var plane = entry2.getKey();
				var points = entry2.getValue();
				PathObject pointObject = PathObjects.createAnnotationObject(ROIs.createPointsROI(points, plane), pathClass);
				annotations.add(pointObject);
			}
		}
		return annotations;
	}
	
	
	/**
	 * Check if a hierarchy contains a specified PathObject.
	 * The actual check if carried out by seeing in the PathObject is descended from the root object of the hierarchy.
	 * 
	 * @param hierarchy
	 * @param pathObject
	 * @return
	 */
	public static boolean hierarchyContainsObject(final PathObjectHierarchy hierarchy, final PathObject pathObject) {
		if (pathObject == null)
			return false;
		PathObject testObject = pathObject;
		while (testObject != null && (!(testObject instanceof PathRootObject)))
			testObject = testObject.getParent();
		return testObject == hierarchy.getRootObject();
	}
	
	/**
	 * Get a collection of objects that overlap a specified pixel location.
	 * <p>
	 * For area ROIs, this means the ROI should contain the pixel.  For non-area ROIs an 
	 * optional vertex distance can be used to define a distance tolerance to the nearest vertex 
	 * or line segment.
	 * 
	 * @param hierarchy object hierarchy within which to find the object
	 * @param x x-coordinate of the pixel
	 * @param y y-coordinate of the pixel
	 * @param zPos z-slice number
	 * @param tPos time-point number
	 * @param vertexDistance for non-area ROIs, the distance from the closest vertex or line segment (or &lt; 0 to ignore non-area ROIs).
	 * @return
	 */
	public static Collection<PathObject> getObjectsForLocation(final PathObjectHierarchy hierarchy, 
			final double x, final double y, final int zPos, final int tPos, double vertexDistance) {
			if (hierarchy == null)
				return Collections.emptyList();
			Set<PathObject> pathObjects = new HashSet<>(8);
			hierarchy.getObjectsForRegion(PathObject.class, ImageRegion.createInstance((int)x, (int)y, 1, 1, zPos, tPos), pathObjects);
			if (vertexDistance < 0)
				removePoints(pathObjects); // Ensure we don't have any PointROIs
			
			// Ensure the ROI contains the click
			Iterator<PathObject> iter = pathObjects.iterator();
			double distSq = vertexDistance * vertexDistance;
			while (iter.hasNext()) {
				PathObject temp = iter.next();
				var roi = temp.getROI();
				if (!RoiTools.areaContains(temp.getROI(), x, y)) {
					if (!roi.isArea() && vertexDistance >= 0) {
						boolean isClose = false;
						if (roi instanceof LineROI) {
							var line = (LineROI)roi;
							if (Line2D.ptSegDistSq(
									line.getX1(), line.getY1(),
									line.getX2(), line.getY2(),
									x, y) <= distSq)
								isClose = true;
						} else if (roi.isLine()) {
							Point2 lastPoint = null;
							for (var p : temp.getROI().getAllPoints()) {
								if (p.distanceSq(x, y) <= distSq ||
										(lastPoint != null && Line2D.ptSegDistSq(
												p.getX(), p.getY(),
												lastPoint.getX(), lastPoint.getY(),
												x, y
												) <= distSq)) {
									isClose = true;
									break;
								} else
									lastPoint = p;
							}
						} else {
							for (var p : temp.getROI().getAllPoints()) {
								if (p.distanceSq(x, y) <= distSq) {
									isClose = true;
									break;
								}
							}
						}
						if (isClose)
							continue;
					}
					iter.remove();					
				}

			}
			
			if (pathObjects.isEmpty()) {
				return Collections.emptyList();
			}
			return pathObjects;
		}

	/**
	 * Return a list of object ancestors, starting from the root object and ending with PathObject
	 * (assuming that the object is part of a hierarchy with a root).
	 * 
	 * @param pathObject
	 * @return
	 */
	public static List<PathObject> getAncestorList(final PathObject pathObject) {
		List<PathObject> ancestors = new ArrayList<>();
		PathObject parent = pathObject;
		while (parent != null) {
			ancestors.add(0, parent);
			parent = parent.getParent();
		}
		return ancestors;
	}

	/**
	 * Swap the name and {@link PathClass} of an object.
	 * This can be used as a simple way to preserve a classification that might be required later.
	 * @param pathObject the object to adjust
	 * @param includeColor optionally set the color of the object to the color of the classification
	 */
	public static void swapNameAndClass(PathObject pathObject, boolean includeColor) {
		var pathClass = pathObject.getPathClass();
		var name = pathObject.getName();
		var color = pathObject.getColorRGB();
		if (name == null)
			pathObject.setPathClass(null);
		else
			pathObject.setPathClass(PathClassFactory.getPathClass(name, color));
		if (pathClass == null) {
			pathObject.setName(null);
			if (includeColor)
				pathObject.setColorRGB(null);
		} else {
			pathObject.setName(pathClass.toString());
			if (includeColor)
				pathObject.setColorRGB(pathClass.getColor());				
		}
	}
	
	/**
	 * Parse a string input representing potential TMA core labels.
	 * 
	 * @param labelString
	 * @return
	 */
	public static String[] parseTMALabelString(String labelString) {
		if (labelString == null || labelString.length() == 0)
			return new String[0];
		labelString = labelString.trim();
		String[] labels = labelString.split(" ");
		// If we have a dash indicating a range, split on that
		if (labels.length == 1 && labels[0].contains("-")) {
			String[] labelsSplit = labels[0].split("-");
			try {
				// Try integer labels
				int i1 = Integer.parseInt(labelsSplit[0]);
				int i2 = Integer.parseInt(labelsSplit[1]);
				
				// Are we descending?
				int inc = 1;
				int n = i2 - i1 + 1;
				if (i1 > i2) {
					inc = -1;
					n = i1 - i2 + 1;
				}
				
				// Check - do we want zero-padding?
				String format = "%d";
				if (labelsSplit[0].startsWith("0"))
					format = "%0" + labelsSplit[0].length() + "d";
				
				// Create labels
				labels = new String[n];
				for (int i = 0; i < n; i++)
					labels[i] = String.format(format, i1+i*inc);
				return labels;
			} catch (Exception e) {}
			try {
				// Try string labels
				char c1 = labelsSplit[0].charAt(0);
				char c2 = labelsSplit[1].charAt(0);
				
				// Are we descending?
				int inc = 1;
				int n = c2 - c1 + 1;
				if (c1 > c2) {
					inc = -1;
					n = c1 - c2 + 1;
				}
				
				// Create labels
				labels = new String[n];
				int counter = 0;
				for (char i = 0; i < n; i++) {
					labels[counter] = ""+(char)(c1 + i*inc);
					counter++;
				}
				return labels;
			} catch (Exception e) {}	
		}
		// Just use whatever we have
		return labels;
	}

	/**
	 * From a collection of available objects, extract those that are instances of specified supported classes.
	 * 
	 * @param availableObjects
	 * @param supportedClasses
	 * @return
	 */
	public static Collection<? extends PathObject> getSupportedObjects(final Collection<PathObject> availableObjects, final Collection<Class<? extends PathObject>> supportedClasses) {
		List<PathObject> objects = availableObjects
			.stream()
			.filter(p -> supportedClasses.stream().anyMatch(s -> s.isInstance(p)))
			.collect(Collectors.toList());
		
		return objects;
	}

	/**
	 * Get the ROI for a PathObject, with a preference for the nucleus ROI of a cell.
	 * 
	 * @param pathObject
	 * @param preferNucleus
	 * @return
	 */
	public static ROI getROI(final PathObject pathObject, final boolean preferNucleus) {
		if (preferNucleus && pathObject instanceof PathCellObject) {
			ROI roi = ((PathCellObject)pathObject).getNucleusROI();
			if (roi != null)
				return roi;
		}
		return pathObject.getROI();
	}

	/**
	 * Get all descendant objects with a specified type.
	 * 
	 * @param pathObject
	 * @param pathObjects
	 * @param cls
	 * @return
	 */
	public static Collection<PathObject> getDescendantObjects(PathObject pathObject, Collection<PathObject> pathObjects, Class<? extends PathObject> cls) {
		if (pathObject == null || !pathObject.hasChildren())
			return pathObjects == null ? Collections.emptyList() : pathObjects;
		
		if (pathObjects == null)
			pathObjects = new ArrayList<>();
		if (cls == null)
			return pathObject.getDescendantObjects(pathObjects);
		addPathObjectsRecursively(pathObject.getChildObjectsAsArray(), pathObjects, cls);
		return pathObjects;
		
		// Alternative method (doesn't require a new array to be created every time child objects are requested)
//		List<PathObject> buffer = new ArrayList<>();
//		pathObject.getChildObjects(buffer);
//		addPathObjectsRecursively(buffer, pathObjects, cls);
//		return pathObjects;
	}
	
	private static void addPathObjectsRecursively(PathObject[] pathObjectsInput, Collection<PathObject> pathObjects, Class<? extends PathObject> cls) {
		for (PathObject childObject : pathObjectsInput) {
			if (cls == null || cls.isInstance(childObject)) {
				pathObjects.add(childObject);
			}
			if (childObject.hasChildren())
				addPathObjectsRecursively(childObject.getChildObjectsAsArray(), pathObjects, cls);
		}
	}
	
//	private static void addPathObjectsRecursively(Collection<PathObject> pathObjectsInput, Collection<PathObject> pathObjects, Class<? extends PathObject> cls) {
//		Collection<PathObject> buffer = null;
//		for (PathObject childObject : pathObjectsInput) {
//			if (cls == null || cls.isInstance(childObject)) {
//				pathObjects.add(childObject);
//			}
//			if (childObject.hasChildren()) {
//				if (buffer == null)
//					buffer = new ArrayList<>();
//				else
//					buffer.clear();
//				childObject.getChildObjects(buffer);
//				addPathObjectsRecursively(buffer, pathObjects, cls);
//			}
//		}
//	}
	
	
//	/**
//	 * Split annotations containing multi-point ROIs into separate single-point ROIs.
//	 * 
//	 * @param hierarchy the object hierarchy
//	 * @param selectedOnly if true, consider only annotations that are currently selected; if false, consider all point annotations in the hierarchy
//	 * @return true if changes are made to the hierarchy, false otherwise
//	 */
//	public static boolean splitPoints(PathObjectHierarchy hierarchy, boolean selectedOnly) {
//		if (hierarchy == null) {
//			logger.debug("No hierarchy available, cannot split points!");
//			return false;
//		}
//		return splitPoints(hierarchy, selectedOnly ? hierarchy.getSelectionModel().getSelectedObjects() : hierarchy.getAnnotationObjects());
//	}
//
//	/**
//	 * Split annotations containing multi-point ROIs into separate single-point ROIs.
//	 * 
//	 * @param hierarchy the object hierarchy
//	 * @param pathObjects a collection of point annotations to split; non-points and non-annotations will be ignored
//	 * @return pathObjects if changes are made to the hierarchy, false otherwise
//	 */
//	public static boolean splitPoints(PathObjectHierarchy hierarchy, Collection<PathObject> pathObjects) {
//		var points = pathObjects.stream().filter(p -> p.isAnnotation() && p.getROI().isPoint() && p.getROI().getNumPoints() > 1).collect(Collectors.toList());
//		if (points.isEmpty()) {
//			logger.debug("No (multi)point ROIs available to split!");			
//			return false;
//		}
//		List<PathObject> newObjects = new ArrayList<>();
//		for (PathObject pathObject : points) {
//			ROI p = pathObject.getROI();
//			ImagePlane plane = p.getImagePlane();
//			PathClass pathClass = pathObject.getPathClass();
//			for (Point2 p2 : p.getAllPoints()) {
//				PathObject temp = PathObjects.createAnnotationObject(ROIs.createPointsROI(p2.getX(), p2.getY(), plane), pathClass);
//				newObjects.add(temp);
//			}
//		}
//		hierarchy.removeObjects(points, true);
//		hierarchy.addPathObjects(newObjects);
//		// Reset the selection
//		hierarchy.getSelectionModel().clearSelection();
//		return true;
//	}
	
	/**
	 * Merge point annotations sharing the same {@link PathClass} and {@link ImagePlane} as the selected annotations,
	 * creating multi-point annotations for all matching points and removing the (previously-separated) annotations.
	 * 
	 * @param hierarchy object hierarchy to modify
	 * @return true if changes are made to the hierarchy, false otherwise
	 */
	public static boolean mergePointsForSelectedObjectClasses(PathObjectHierarchy hierarchy) {
		var pathClasses = hierarchy.getSelectionModel().getSelectedObjects().stream()
				.filter(p -> p.isAnnotation() && p.getROI().isPoint())
				.map(p -> p.getPathClass())
				.collect(Collectors.toSet());
		boolean changes = false;
		for (PathClass pathClass : pathClasses)
			changes = changes || mergePointsForClass(hierarchy, pathClass);
		return changes;
	}
	
	/**
	 * Merge point annotations sharing the same {@link PathClass} and {@link ImagePlane}, 
	 * creating multi-point annotations for all matching points and removing the (previously-separated) annotations.
	 * 
	 * @param hierarchy object hierarchy to modify
	 * @return true if changes are made to the hierarchy, false otherwise
	 */
	public static boolean mergePointsForAllClasses(PathObjectHierarchy hierarchy) {
		if (hierarchy == null)
			return false;
		var pathClasses = hierarchy.getAnnotationObjects().stream()
			.filter(p -> p.getROI().isPoint())
			.map(p -> p.getPathClass())
			.collect(Collectors.toSet());
		boolean changes = false;
		for (PathClass pathClass : pathClasses)
			changes = changes || mergePointsForClass(hierarchy, pathClass);
		return changes;
	}
	
	/**
	 * Merge point annotations with the specified {@link PathClass} sharing the same {@link ImagePlane}, 
	 * creating a single multi-point annotation for all matching points and removing the (previously-separated) annotations.
	 * 
	 * @param hierarchy object hierarchy to modify
	 * @param pathClass classification for annotations to merge
	 * @return true if changes are made to the hierarchy, false otherwise
	 * 
	 * @see #mergePointsForAllClasses(PathObjectHierarchy)
	 */
	public static boolean mergePointsForClass(PathObjectHierarchy hierarchy, PathClass pathClass) {
		var map = hierarchy.getAnnotationObjects().stream()
				.filter(p -> p.getROI().isPoint() && p.getPathClass() == pathClass)
				.collect(Collectors.groupingBy(p -> p.getROI().getImagePlane()));
		
		List<PathObject> toRemove = new ArrayList<>();
		List<PathObject> toAdd = new ArrayList<>();
		for (var entry : map.entrySet()) {
			List<PathObject> objectsToMerge = entry.getValue();
			if (objectsToMerge.size() <= 1)
				continue;
			// Create new points object
			List<Point2> pointsList = new ArrayList<>();
			for (PathObject temp : objectsToMerge) {
				pointsList.addAll(((PointsROI)temp.getROI()).getAllPoints());
			}
			var points = ROIs.createPointsROI(pointsList, entry.getKey());
			toAdd.add(PathObjects.createAnnotationObject(points, pathClass));
			toRemove.addAll(objectsToMerge);
		}
		if (toAdd.isEmpty() && toRemove.isEmpty())
			return false;
		hierarchy.removeObjects(toRemove, true);
		hierarchy.addPathObjects(toAdd);
		return true;
	}
	
	/**
	 * Standardize the classifications for a collection of objects.
	 * This involves sorting the names of derived classes alphabetically, and removing duplicates.
	 * 
	 * @param pathObjects collection of objects with classifications that should be standardized
	 * @return true if changes were made, false otherwise
	 */
	public static boolean standardizeClassifications(Collection<PathObject> pathObjects) {
		return standardizeClassifications(pathObjects, Comparator.naturalOrder());
	}
	
	/**
	 * Standardize the classifications for a collection of objects.
	 * This involves sorting the names of derived classes, and removing duplicates.
	 * 
	 * @param pathObjects collection of objects with classifications that should be standardized
	 * @param comparator comparator to use when sorting
	 * @return true if changes were made, false otherwise
	 */
	public static boolean standardizeClassifications(Collection<PathObject> pathObjects, Comparator<String> comparator) {
		int nChanges = 0;
		Map<PathClass, PathClass> map = new HashMap<>();
		for (var pathObject : pathObjects) {
			var pathClass = pathObject.getPathClass();
			if (pathClass == null)
				continue;
			PathClass pathClassNew;
			if (!map.containsKey(pathClass)) {
				pathClassNew =
						PathClassTools.sortNames(
							PathClassTools.uniqueNames(pathClass),
						comparator);
				map.put(pathClass, pathClassNew);
			} else
				pathClassNew = map.get(pathClass);
			
			if (!pathClass.equals(pathClassNew)) {
				pathObject.setPathClass(pathClassNew);
				nChanges++;
			}
		}
		return nChanges > 0;		
	}
	
	
	/**
	 * Create a(n optionally) transformed version of a {@link PathObject}.
	 * <p>
	 * Note: only detections (including tiles and cells) and annotations are fully supported by this method.
	 * Root objects are duplicated.
	 * TMA core objects are transformed only if the resulting transform creates an ellipse ROI, since this is 
	 * currently the only ROI type supported for a TMA core (this behavior may change).
	 * Any other object types result in an {@link UnsupportedOperationException} being thrown.
	 * 
	 * @param pathObject the object to transform; this will be unchanged
	 * @param transform optional affine transform; if {@code null}, this effectively acts to duplicate the object
	 * @param copyMeasurements if true, the measurement list of the new object will be populated with the measurements of pathObject
	 * 
	 * @return a duplicate of pathObject, with affine transform applied to the object's ROI(s) if required
	 */
	public static PathObject transformObject(PathObject pathObject, AffineTransform transform, boolean copyMeasurements) {
		ROI roi = maybeTransformROI(pathObject.getROI(), transform);
		PathClass pathClass = pathObject.getPathClass();
		PathObject newObject;
		if (pathObject instanceof PathCellObject) {
			ROI roiNucleus = maybeTransformROI(((PathCellObject)pathObject).getNucleusROI(), transform);
			newObject = PathObjects.createCellObject(roi, roiNucleus, pathClass, null);
		} else if (pathObject instanceof PathTileObject) {
			newObject = PathObjects.createTileObject(roi, pathClass, null);
		} else if (pathObject instanceof PathDetectionObject) {
			newObject = PathObjects.createDetectionObject(roi, pathClass, null);
		} else if (pathObject instanceof PathAnnotationObject) {
			newObject = PathObjects.createAnnotationObject(roi, pathClass, null);
		} else if (pathObject instanceof PathRootObject) {
			newObject = new PathRootObject();
		} else if (pathObject instanceof TMACoreObject && roi instanceof EllipseROI) {
			var core = (TMACoreObject)pathObject;
			newObject = PathObjects.createTMACoreObject(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight(), core.isMissing());
		} else
			throw new UnsupportedOperationException("Unable to transform object " + pathObject);
		if (copyMeasurements && !pathObject.getMeasurementList().isEmpty()) {
			MeasurementList measurements = pathObject.getMeasurementList();
			for (int i = 0; i < measurements.size(); i++) {
				String name = measurements.getMeasurementName(i);
				double value = measurements.getMeasurementValue(i);
				newObject.getMeasurementList().addMeasurement(name, value);
			}
			newObject.getMeasurementList().close();
		}
		return newObject;
	}
	
	/**
	 * Create (optionally) transformed versions of the {@link PathObject} and all its descendants, recursively. 
	 * This method can be applied to all objects in a hierarchy by supplying its root object. The parent-children 
	 * relationships are kept after transformation.
	 * 
	 * @param pathObject
	 * @param transform
	 * @param copyMeasurements
	 * @return
	 */
	public static PathObject transformObjectRecursive(PathObject pathObject, AffineTransform transform, boolean copyMeasurements) {
		var newObj = transformObject(pathObject, transform, copyMeasurements);
		for (var child: pathObject.getChildObjects()) {
			newObj.addPathObject(transformObjectRecursive(child, transform, copyMeasurements));
		}
		return newObj;
	}
	
	private static ROI maybeTransformROI(ROI roi, AffineTransform transform) {
		if (roi == null || transform == null || transform.isIdentity())
			return roi;
		return RoiTools.transformROI(roi, transform);
	}
	
	/**
	 * Duplicate all the selected objects in a hierarchy.
	 * 
	 * @param hierarchy the hierarchy containing the objects to duplicate
	 * @return true if the hierarchy is changed, false otherwise
	 */
	public static boolean duplicateAllSelectedObjects(PathObjectHierarchy hierarchy) {
		return duplicateSelectedObjects(hierarchy, null);
	}
	
	/**
	 * Duplicate the selected annotation objects. Selected objects that are not annotations will be ignored.
	 * 
	 * @param hierarchy the hierarchy containing the objects to duplicate
	 * @return true if the hierarchy is changed, false otherwise
	 */
	public static boolean duplicateSelectedAnnotations(PathObjectHierarchy hierarchy) {
		return duplicateSelectedObjects(hierarchy, p -> p.isAnnotation());
	}
	
	/**
	 * Duplicate the selected objects 
	 * 
	 * @param hierarchy the hierarchy containing the objects to duplicate
	 * @param predicate optional predicate (may be null) used to filter out invalid selected options that should not be duplicated
	 * @return true if the hierarchy is changed, false otherwise
	 */
	public static boolean duplicateSelectedObjects(PathObjectHierarchy hierarchy, Predicate<PathObject> predicate) {
		if (predicate == null)
			return duplicateObjects(hierarchy, new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects()));
		var list = hierarchy.getSelectionModel().getSelectedObjects()
				.stream()
				.filter(predicate)
				.collect(Collectors.toList());
		return duplicateObjects(hierarchy, list);
	}
	
	/**
	 * Duplicate the specified objects.
	 * @param hierarchy hierarchy containing the objects to duplicate
	 * @param pathObjects objects that should be duplicated
	 * @return true if the hierarchy is changed, false otherwise
	 */
	public static boolean duplicateObjects(PathObjectHierarchy hierarchy, Collection<PathObject> pathObjects) {
		var map = pathObjects
				.stream()
				.collect(Collectors.toMap(p -> p,
						p -> PathObjectTools.transformObject(p, null, true)));
		if (map.isEmpty()) {
			logger.error("No selected objects to duplicate!");
			return false;
		}
		// Add objects using the default add method (not trying to resolve location)
		hierarchy.addPathObjects(map.values());
//		// Add objects, inserting with the same parents as the originals
//		for (var entry : map.entrySet()) {
//			entry.getKey().getParent().addPathObject(entry.getValue());
//		}
		// Conceivably we might not have a hierarchy.
		// If we do, fire update and try to retain the same selected object (if it was already selected)
		if (hierarchy != null) {
			PathObject currentMainObject = hierarchy.getSelectionModel().getSelectedObject();
			hierarchy.fireHierarchyChangedEvent(PathObjectTools.class);
	//		hierarchy.addPathObjects(map.values());
			hierarchy.getSelectionModel().setSelectedObjects(map.values(), map.getOrDefault(currentMainObject, null));
		}
		return true;
	}
	
	
	/**
	 * Resolve overlapping objects by size, retaining the object with the larger ROI and discarding the object with the smaller ROI.
	 * 
	 * @param pathObjects input object collection, which may contain overlapping objects
	 * @param overlapTolerance amount of overlap to permit; recommended value is 0, see {@link #removeOverlaps(Collection, Comparator, double)}
	 * @return output collection of objects, which should have smaller overlapping objects removed
	 */
	public static Collection<PathObject> removeOverlapsBySize(Collection<? extends PathObject> pathObjects, double overlapTolerance) {
		return removeOverlaps(pathObjects, Comparator.comparingDouble((PathObject p) -> getROI(p, false).getArea()).reversed(), overlapTolerance);
	}
	
	/**
	 * Resolve overlapping object by location, retaining the object closest to the image 'origin' and discarding the object further away.
	 * Note that this is determined using first the bounding box, then the centroid.
	 * This is a simpler (and faster) criterion than measuring distance to the original from the ROI itself.
	 * 
	 * @param pathObjects input object collection, which may contain overlapping objects
	 * @param overlapTolerance amount of overlap to permit; recommended value is 0, see {@link #removeOverlaps(Collection, Comparator, double)}
	 * @return output collection of objects, which should have smaller overlapping objects removed
	 */
	public static Collection<PathObject> removeOverlapsByLocation(Collection<? extends PathObject> pathObjects, double overlapTolerance) {
		// Sort according to bounding box, then centroid, then area
		return removeOverlaps(pathObjects,
				Comparator.comparingDouble((PathObject p) -> p.getROI().getBoundsY())
				.thenComparing((PathObject p) -> p.getROI().getBoundsX())
				.thenComparing((PathObject p) -> p.getROI().getCentroidY())
				.thenComparing((PathObject p) -> p.getROI().getCentroidX())
				.thenComparing((PathObject p) -> getROI(p, false).getArea()),
				overlapTolerance);
	}

	
	/**
	 * Resolve overlaps, discarding one and keeping the other.
	 * It assumes that the objects have been sorted so that 'preferred' objects occur first.
	 * <p>
	 * 'How overlapping' can be controlled by the {@code overlapTolerance}, where an overlap will be removed
	 * <ul>
	 * <li>if {@code overlapTolerance > 0} and the area of the intersection between ROIs is {@code < overlapTolerance} (an absolute comparison)
	 * <li>if {@code overlapTolerance < 0} and the proportion of the smaller ROI intersecting the larger ROI is {@code < -overlapTolerance} (a relative comparison)
	 * <li>if {@code overlapTolerance == 0} and there is any non-zero area intersection between ROIs
	 * </ul>
	 * For example, {@code overlapTolerance == 10} will require at least 10 pixels between ROIs to intersect to be considered an overlap,
	 * while {@code overlapTolerance == 0.01} will require at least 1% of the area of the smaller ROI to intersect.
	 * <p>
	 * It is recommended to keep {@code overlapTolerance == 0} in most instances to remove all overlaps.
	 * This is also less computationally expensive because it means intersection areas do not need to be calculated.
	 * 
	 * @param pathObjects input object collection, which may contain overlapping objects
	 * @param comparator comparator, which determines which object is retained when overlaps are found.
	 *                   Considering the collection to be sorted by the comparator, the 'first' object is the one that will be kept.
	 * @param overlapTolerance amount of overlap to permit
	 * @return collection of objects, which should have smaller overlapping objects removed
	 */
	public static Collection<PathObject> removeOverlaps(Collection<? extends PathObject> pathObjects, Comparator<PathObject> comparator, double overlapTolerance) {
		
		if (overlapTolerance != 0 && overlapTolerance <= -1.0) {
			logger.warn("A non-zero overlapTolerance <= -1.0 has no effect! Returning the same objects.");
			return new ArrayList<>(pathObjects);
		}
		
		// Start off by assuming we'll keep everything
		Collection<PathObject> output = new LinkedHashSet<>(pathObjects);
		
		// Identify the objects we can potentially deal with (i.e. with area ROIs)
		List<PathObject> list = new ArrayList<>();
		for (PathObject pathObject : pathObjects) {
			if (pathObject.hasROI() && pathObject.getROI().isArea())
				list.add(pathObject);
		}
		Collections.sort(list, comparator);
		
		// Create a spatial index
		var quadTree = new STRtree();
		var geomMap = new HashMap<PathObject, Geometry>();
		var planeMap = new HashMap<PathObject, ImagePlane>();
		for (PathObject pathObject : list) {
			var roi = PathObjectTools.getROI(pathObject, false);
			var geom = roi.getGeometry();
			quadTree.insert(geom.getEnvelopeInternal(), pathObject);
			geomMap.put(pathObject, geom);
			planeMap.put(pathObject, roi.getImagePlane());
		}
		
		// Identify what needs to be removed
		Set<PathObject> toRemove = new HashSet<>();
		for (PathObject pathObject : list) {
			if (toRemove.contains(pathObject))
				continue;
			var geom = geomMap.get(pathObject);
			var plane = planeMap.get(pathObject);
			@SuppressWarnings("unchecked")
			List<PathObject> potentialOverlaps = (List<PathObject>)quadTree.query(geom.getEnvelopeInternal());
			for (PathObject p : potentialOverlaps) {
				if (p == pathObject || toRemove.contains(p))
					continue;
				var planeP = planeMap.get(p);
				if (plane.getZ() != planeP.getZ() || plane.getT() != planeP.getT())
					continue;
				var geomP = geomMap.get(p);
				// We allow 'touches', since this involves no area intersection
				if (!geom.intersects(geomP) || geom.touches(geomP))
					continue;
				if (overlapTolerance != 0) {
					try {
						var overlap = geom.intersection(geomP).getArea();
						double tol = overlapTolerance;
						if (overlapTolerance < 0) {
							tol = Math.min(geom.getArea(), geom.getArea()) * (-overlapTolerance);
						}
						if (overlap < tol)
							continue;
					} catch (Exception e) {
						logger.warn("Exception attempting to apply overlap tolerance: " + e.getLocalizedMessage(), e);
					}
				}
				toRemove.add(p);
			}
		}
		output.removeAll(toRemove);
		return output;
	}

	/**
	 * Merge objects by calculating the union of their ROIs.
	 * @param pathObjects a collection of annotations, cells, detections or tiles. Note that all objects must be of the same type.
	 * @return a single object with ROI(s) determined by union. The classification and name will be taken from the first ROI in the collection.
	 * @throws IllegalArgumentException if no objects are provided (either null or empty list)
	 */
	public static PathObject mergeObjects(Collection<? extends PathObject> pathObjects) {
		if (pathObjects == null || pathObjects.isEmpty())
			throw new IllegalArgumentException("No objects provided to merge!");
		var first = pathObjects.iterator().next();
		if (pathObjects.size() == 1)
			return first;
		var rois = pathObjects.stream().map(p -> p.getROI()).filter(r -> r != null && !r.isEmpty()).collect(Collectors.toList());
		var roi = RoiTools.union(rois);
		PathObject result;
		if (pathObjects.stream().allMatch(p -> p.isCell())) {
			var nucleusRois = pathObjects.stream().map(p -> ((PathCellObject)p).getNucleusROI()).filter(r -> r != null && !r.isEmpty()).collect(Collectors.toList());
			var nucleusROI = RoiTools.union(nucleusRois);			
			result = PathObjects.createCellObject(roi, nucleusROI, first.getPathClass(), null);
		} else if (pathObjects.stream().allMatch(p -> p.isAnnotation())) {
			result = PathObjects.createAnnotationObject(roi);
		} else if (pathObjects.stream().allMatch(p -> p.isTile())) {
			result = PathObjects.createTileObject(roi);
		} else if (pathObjects.stream().allMatch(p -> p.isDetection())) {
			result = PathObjects.createDetectionObject(roi);
		} else
			throw new IllegalArgumentException("Unknow or mixed object types - cannot merge ROIs for " + pathObjects);
		result.setPathClass(first.getPathClass());
		result.setName(first.getName());
		return result;
	}

	/**
	 * Merge objects that share a property in common.
	 * <p>
	 * Note that objects must all be of the same type (e.g. cells, detections, annotations).
	 * @param pathObjects
	 * @param classifier function extracting the shared property, e.g. {@code p -> p.getName()}
	 * @return a new list of objects generated by merging grouped objects.
	 * @see #mergeObjects(Collection)
	 */
	public static <K> List<PathObject> mergeObjects(Collection<? extends PathObject> pathObjects, 
			Function<? super PathObject, ? extends K> classifier) {
		var groups = pathObjects.stream().collect(Collectors.groupingBy(classifier));
		List<PathObject> output = new ArrayList<>();
		for (var entry : groups.entrySet()) {
			var group = entry.getValue();
			if (group.size() <= 1)
				output.addAll(group);
			else {
				output.add(mergeObjects(group));
			}
		}
		return output;
	}
	
}