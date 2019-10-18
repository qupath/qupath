/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.objects;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.PolylineROI;
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
		int count = pathObject.nChildObjects();
		for (PathObject childObject : pathObject.getChildObjectsAsArray())
			count += countDescendants(childObject);
		return count;
	}

	/**
	 * Count the descendants of a PathObject recursively, limited to a specific class.
	 * 
	 * @param pathObject
	 * @return
	 */
	public static int countChildren(final PathObject pathObject, final Class<? extends PathObject> cls, final boolean allDescendents) {
		int count = 0;
		for (PathObject childObject : pathObject.getChildObjectsAsArray()) {
			if (cls.isAssignableFrom(childObject.getClass()))
				count++;
			if (childObject.hasChildren() && allDescendents)
				count += countChildren(childObject, cls, allDescendents);
		}
		return count;
	}

	

	/**
	 * Count the descendants of a PathObject recursively, limited to a specific PathClass.
	 * 
	 * @param pathObject
	 * @return
	 */
	public static int countChildren(final PathObject pathObject, final PathClass pathClass, final boolean allDescendents) {
		int count = 0;
		for (PathObject childObject : pathObject.getChildObjectsAsArray()) {
			if (pathClass.equals(childObject.getPathClass()))
				count++;
			if (childObject.hasChildren() && allDescendents)
				count += countChildren(childObject, pathClass, allDescendents);
		}
		return count;
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
			if (!childROI.contains(p.getX(), p.getY()))
				return false;
		}
		
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
	public static List<PathObject> getAncenstorList(final PathObject pathObject) {
		List<PathObject> ancestors = new ArrayList<>();
		PathObject parent = pathObject;
		while (parent != null) {
			ancestors.add(0, parent);
			parent = parent.getParent();
		}
		return ancestors;
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
		if (pathObjects == null)
			pathObjects = new ArrayList<>();
		if (pathObject == null || !pathObject.hasChildren())
			return pathObjects;
		addPathObjectsRecursively(pathObject.getChildObjectsAsArray(), pathObjects, cls);
		return pathObjects;
	}
	
	private static void addPathObjectsRecursively(PathObject[]pathObjectsInput, Collection<PathObject> pathObjects, Class<? extends PathObject> cls) {
		for (PathObject childObject : pathObjectsInput) {
			if (cls == null || cls.isInstance(childObject)) {
				pathObjects.add(childObject);
			}
			if (childObject.hasChildren())
				addPathObjectsRecursively(childObject.getChildObjectsAsArray(), pathObjects, cls);
		}
	}
	
}
