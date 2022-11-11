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
import java.util.UUID;
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
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
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
	
	private static final Logger logger = LoggerFactory.getLogger(PathObjectTools.class);

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
	 * Get all the objects with ROIs that are outside the bounds of an image.
	 * @param pathObjects the input objects to check
	 * @param server the image to check
	 * @param ignoreIntersecting if true, consider objects that overlap the image boundary to be inside (and therefore don't include them in the output); 
	 *                           if false, consider them to be outside and include them in the output
	 * @return a filtered list of the input object containing those considered outside the image
	 * @since v0.4.0
	 * @see #findObjectsOutsideRegion(Collection, ImageRegion, boolean)
	 * @see #findObjectsOutsideRegion(Collection, ImageRegion, int, int, int, int, boolean)
	 */
	public static List<PathObject> findObjectsOutsideImage(Collection<? extends PathObject> pathObjects, ImageServer<?> server, boolean ignoreIntersecting) {
		return findObjectsOutsideRegion(pathObjects, 
				RegionRequest.createInstance(server), 0, server.nZSlices(), 0, server.nTimepoints(), ignoreIntersecting);
	}
	
	/**
	 * Get all the objects in a collection that are outside a defined region.
	 * @param pathObjects input objects to check
	 * @param region 2D region
	 * @param ignoreIntersecting if true, consider objects that overlap the region boundary to be inside (and therefore don't include them in the output); 
	 *                           if false, consider them to be outside and include them in the output
	 * @return a filtered list of the input object containing those considered outside the region
	 * @since v0.4.0
	 * @see #findObjectsOutsideRegion(Collection, ImageRegion, int, int, int, int, boolean)
	 */
	public static List<PathObject> findObjectsOutsideRegion(Collection<? extends PathObject> pathObjects, ImageRegion region, boolean ignoreIntersecting) {
		return findObjectsOutsideRegion(pathObjects, region, region.getZ(), region.getZ()+1, region.getT(), region.getT()+1, ignoreIntersecting);
	}
	
	/**
	 * Get all the objects in a collection that are outside a defined region, expanded for multiple z-slices and timepoints.
	 * @param pathObjects input objects to check
	 * @param region 2D region
	 * @param minZ minimum z for the region (inclusive)
	 * @param maxZ maximum z for the region (exclusive)
	 * @param minT minimum t for the region (inclusive)
	 * @param maxT maximum t for the region (exclusive)
	 * @param ignoreIntersecting if true, consider objects that overlap the region boundary to be inside (and therefore don't include them in the output); 
	 *                           if false, consider them to be outside and include them in the output
	 * @return a filtered list of the input object containing those considered outside the region
	 * @since v0.4.0
	 * @see #findObjectsOutsideRegion(Collection, ImageRegion, boolean)
	 */
	public static List<PathObject> findObjectsOutsideRegion(Collection<? extends PathObject> pathObjects, ImageRegion region, int minZ, int maxZ, int minT, int maxT, boolean ignoreIntersecting) {
		return pathObjects
				.stream()
				.filter(p -> !checkRegionContainsROI(p.getROI(), region, minZ, maxZ, minT, maxT, ignoreIntersecting))
				.collect(Collectors.toList());
	}
	
	
	private static boolean checkRegionContainsROI(ROI roi, ImageRegion region, int minZ, int maxZ, int minT, int maxT, boolean ignoreIntersecting) {
		if (roi == null)
			return false;
		if (roi.getZ() < minZ || roi.getZ() >= maxZ || roi.getT() < minT || roi.getT() >= maxT)
			return false;
		boolean regionContainsBounds = region.getX() <= roi.getBoundsX() && 
				region.getY() <= roi.getBoundsY() &&
				region.getMaxX() >= roi.getBoundsX() + roi.getBoundsWidth() &&
				region.getMaxY() >= roi.getBoundsY() + roi.getBoundsHeight();
		if (regionContainsBounds)
			return true;
		else if (ignoreIntersecting)
			// Ensure planes match (since we've already handled that)
			return RoiTools.intersectsRegion(roi.updatePlane(region.getImagePlane()), region);
		else {
			return false;
		}
	}
	
	/**
	 * Update the ROI plane for a single object, and any descendant objects.
	 * 
	 * @param pathObject the original object (this will be unchanged)
	 * @param plane the plane for the new ROIs
	 * @param copyMeasurements if true, measurements and metadata should be copied; this may not be suitable since 
	 *                         intensity measurements probably aren't appropriate for the new plane
	 * @param createNewIDs if true, create new IDs for the object (recommended)
	 * @return the new object, with ROIs on the requested plane
	 * @since v0.4.0
	 * @see #updatePlane(PathObject, ImagePlane, boolean, boolean)
	 */
	public static PathObject updatePlaneRecursive(PathObject pathObject, ImagePlane plane, boolean copyMeasurements, boolean createNewIDs) {
		var newObj = transformObjectImpl(pathObject, r -> r.updatePlane(plane), copyMeasurements, createNewIDs);
		if (pathObject.hasChildObjects()) {
			List<PathObject> newChildObjects = pathObject.getChildObjects()
				.parallelStream()
				.map(p -> updatePlaneRecursive(p, plane, copyMeasurements, createNewIDs))
				.collect(Collectors.toList());
			newObj.addChildObjects(newChildObjects);
		}
		return newObj;
	}
	
	/**
	 * Update the ROI plane for a single object and any descendant objects, creating new object IDs and ignoring 
	 * any additional measurements.
	 * 
	 * @param pathObject the original object (this will be unchanged)
	 * @param plane the plane for the new ROIs
	 * @return the new object, with ROIs on the requested plane
	 * @since v0.4.0
	 * @see #updatePlaneRecursive(PathObject, ImagePlane, boolean, boolean)
	 */
	public static PathObject updatePlaneRecursive(PathObject pathObject, ImagePlane plane) {
		return updatePlaneRecursive(pathObject, plane, false, true);
	}
	
	/**
	 * Update the ROI plane for a single object.
	 * Any child objects are discarded; if these should also be copied (and updated), 
	 * use {@link #updatePlaneRecursive(PathObject, ImagePlane, boolean, boolean)}.
	 * 
	 * @param pathObject the original object (this will be unchanged)
	 * @param plane the plane for the new ROIs
	 * @param copyMeasurements if true, measurements and metadata should be copied; this may not be suitable since 
	 *                         intensity measurements probably aren't appropriate for the new plane
	 * @param createNewIDs if true, create new IDs for the object (recommended)
	 * @return the new object, with ROIs on the requested plane
	 * @since v0.4.0
	 * @see #updatePlaneRecursive(PathObject, ImagePlane, boolean, boolean)
	 */
	public static PathObject updatePlane(PathObject pathObject, ImagePlane plane, boolean copyMeasurements, boolean createNewIDs) {
		return transformObjectImpl(pathObject, r -> r.updatePlane(plane), copyMeasurements, createNewIDs);
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
	 * Create a new regular {@link TMAGrid} and set it as active on the hierarchy for an image.
	 * <p>
	 * For the label string format, see see {@link #parseTMALabelString(String)}.
	 * 
	 * @param imageData the image to which the TMA grid should be added. This is used to determine 
	 *                  dimensions and pixel calibration. If there is a ROI selected, it will be used 
	 *                  to define the bounding box for the grid.
	 * @param hLabels a String representing horizontal labels
	 * @param vLabels a String representing vertical labels
	 * @param rowFirst true if the horizontal label should be added before the vertical label, false otherwise
	 * @param diameterCalibrated the diameter of each core, in calibrated units
	 */
	public static void addTMAGrid(ImageData<?> imageData, String hLabels, String vLabels, boolean rowFirst, double diameterCalibrated) {
		// Convert diameter to pixels
		double diameterPixels = diameterCalibrated / imageData.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue();
		
		// Get the current ROI
		var hierarchy = imageData.getHierarchy();
		var selected = hierarchy.getSelectionModel().getSelectedObject();
		var roi = selected == null ? null : selected.getROI();
		var region = roi == null ? 
				ImageRegion.createInstance(0, 0, imageData.getServer().getWidth(), imageData.getServer().getHeight(), 0, 0) :
					ImageRegion.createInstance(roi);
		
		var tmaGrid = createTMAGrid(hLabels, vLabels, rowFirst, diameterPixels, region);
		hierarchy.setTMAGrid(tmaGrid);
	}
	
	/**
	 * Create a new regular {@link TMAGrid}, fit within a specified region.
	 * <p>
	 * For the label string format, see see {@link #parseTMALabelString(String)}.
	 * 
	 * @param hLabels a String representing horizontal labels
	 * @param vLabels a String representing vertical labels
	 * @param rowFirst true if the horizontal label should be added before the vertical label, false otherwise
	 * @param diameterPixels the diameter of each core, in pixels
	 * @param region bounding box and spacing for the grid (required)
	 * @return
	 */
	public static TMAGrid createTMAGrid(String hLabels, String vLabels, boolean rowFirst, double diameterPixels, ImageRegion region) {
		// TODO: Consider method that uses a polygonal ROI to create a warped/rotated grid
		
		// Enter the number of horizontal & vertical cores here
		var hLabelsSplit = PathObjectTools.parseTMALabelString(hLabels);
		var vLabelsSplit = PathObjectTools.parseTMALabelString(vLabels);
		int numHorizontal = hLabelsSplit.length;
		int numVertical = vLabelsSplit.length;
		
		// Create the cores
		var cores = new ArrayList<TMACoreObject>();
		double xSpacing = (region.getWidth() - diameterPixels) / Math.max(1, numHorizontal - 1);
		double ySpacing = (region.getHeight() - diameterPixels) / Math.max(1, numVertical - 1);
		for (int i = 0; i < numVertical; i++) {
		    for (int j = 0; j < numHorizontal; j++) {
		        double x = numHorizontal <= 1 ? region.getMinX() + region.getWidth()/2.0 : region.getMinX() + diameterPixels / 2 + xSpacing * j;
		        double y = numVertical <= 1 ? region.getMinY() + region.getHeight()/2.0 : region.getMinY() + diameterPixels / 2 + ySpacing * i;
		        cores.add(PathObjects.createTMACoreObject(x, y, diameterPixels, false, region.getImagePlane()));
		    }
		}
		// Create & set the grid
		var grid = DefaultTMAGrid.create(cores, numHorizontal);
		
		relabelTMAGrid(grid, hLabels, vLabels, rowFirst);
		
		return grid;
	}
	
	
	/**
	 * Relabel a TMA grid.  This will only be effective if enough labels are supplied for the full grid - otherwise no changes will be made.
	 * <p>
	 * For a TMA core at column c and row r, the label format will be 'Hc-Vr' or 'Hc-Vr', where H is the horizontal label and V the vertical label, 
	 * depending upon the status of the 'rowFirst' flag.
	 * <p>
	 * An examples of label would be 'A-1', 'A-2', 'B-1', 'B-2' etc.
	 * 
	 * @param grid the TMA grid to relabel
	 * @param labelsHorizontal a String containing labels for each TMA column, separated by spaces, or a numeric or alphabetic range (e.g. 1-10, or A-G)
	 * @param labelsVertical a String containing labels for each TMA row, separated by spaces, or a numeric or alphabetic range (e.g. 1-10, or A-G)
	 * @param rowFirst true if the horizontal label should be added before the vertical label, false otherwise
	 * @return true if there were sufficient horizontal and vertical labels to label the entire grid, false otherwise.
	 */
	public static boolean relabelTMAGrid(final TMAGrid grid, final String labelsHorizontal, final String labelsVertical, final boolean rowFirst) {
		String[] columnLabels = PathObjectTools.parseTMALabelString(labelsHorizontal);
		String[] rowLabels = PathObjectTools.parseTMALabelString(labelsVertical);
		if (columnLabels.length < grid.getGridWidth()) {
			logger.error("Cannot relabel full TMA grid - not enough column labels specified!");
			return false;			
		}
		if (rowLabels.length < grid.getGridHeight()) {
			logger.error("Cannot relabel full TMA grid - not enough row labels specified!");
			return false;			
		}
		
		for (int r = 0; r < grid.getGridHeight(); r++) {
			for (int c = 0; c < grid.getGridWidth(); c++) {
				String name;
				if (rowFirst)
					name = rowLabels[r] + "-" + columnLabels[c];
				else
					name = columnLabels[c] + "-" + rowLabels[r];
				grid.getTMACore(r, c).setName(name);
			}			
		}
		return true;
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
		hierarchy.addObjects(points);
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
		var color = pathObject.getColor();
		if (name == null)
			pathObject.resetPathClass();
		else
			pathObject.setPathClass(PathClass.fromString(name, color));
		if (pathClass == null) {
			pathObject.setName(null);
			if (includeColor)
				pathObject.setColor(null);
		} else {
			pathObject.setName(pathClass.toString());
			if (includeColor)
				pathObject.setColor(pathClass.getColor());				
		}
	}
	
	/**
	 * Parse a string input representing potential TMA core labels.
	 * This can be a space-separated list, or an ascending or descending numeric or alphabetic range.
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>{@code "A-H"}<li>
	 * <li>{@code "1-9"}<li>
	 * <li>{@code "H-A"}<li>
	 * <li>{@code "A B D E"}<li>
	 * </ul>
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
		if (pathObject == null || !pathObject.hasChildObjects())
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
			if (childObject.hasChildObjects())
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
		hierarchy.addObjects(toAdd);
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
	 * Create a transformed version of a {@link PathObject} with a new ID.
	 * If the transform is null or the identity transform, then a duplicate object is generated instead.
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
	 * @see #transformObject(PathObject, AffineTransform, boolean, boolean)
	 */
	public static PathObject transformObject(PathObject pathObject, AffineTransform transform, boolean copyMeasurements) {
		return transformObject(pathObject, transform, copyMeasurements, false);
	}
	
	/**
	 * Create a transformed version of a {@link PathObject}, optionally with a new ID.
	 * If the transform is null or the identity transform, then a duplicate object is generated instead.
	 * <p>
	 * Note: only detections (including tiles and cells), annotations and root objects are fully supported by this method.
	 * TMA core objects are transformed only if the resulting transform creates an ellipse ROI, since this is 
	 * currently the only ROI type supported for a TMA core (this behavior may change).
	 * Any other object types result in an {@link UnsupportedOperationException} being thrown.
	 * 
	 * @param pathObject the object to transform; this will be unchanged
	 * @param transform optional affine transform; if {@code null}, this effectively acts to duplicate the object
	 * @param copyMeasurements if true, the measurements and metadata maps of the new object will be populated with those from the pathObject
	 * @param createNewIDs if true, create new IDs for each copied object; otherwise, retain the same ID.
	 * 
	 * @return a duplicate of pathObject, with affine transform applied to the object's ROI(s) if required
	 * @since v0.4.0
	 */
	public static PathObject transformObject(PathObject pathObject, AffineTransform transform, boolean copyMeasurements, boolean createNewIDs) {
		return transformObjectImpl(pathObject, r -> maybeTransformROI(r, transform), copyMeasurements, createNewIDs);
	}
	
	
	
	/**
	 * Apply a transform to the ROI of a PathObject, creating a new object of the same type with the new ROI.
	 * @param pathObject the object to transform; this will be unchanged
	 * @param roiTransformer the ROI transform to apply
	 * @param copyMeasurements if true, the measurements and metadata maps of the new object will be populated with those from the pathObject
	 * @param createNewIDs if true, create new IDs for each copied object; otherwise, retain the same ID.
	 * @return
	 */
	private static PathObject transformObjectImpl(PathObject pathObject, Function<ROI, ROI> roiTransformer, boolean copyMeasurements, boolean createNewIDs) {
		ROI roi = roiTransformer.apply(pathObject.getROI());
		PathClass pathClass = pathObject.getPathClass();
		PathObject newObject;
		if (pathObject instanceof PathCellObject) {
			ROI roiNucleus = roiTransformer.apply(((PathCellObject)pathObject).getNucleusROI());
			newObject = PathObjects.createCellObject(roi, roiNucleus, pathClass, null);
		} else if (pathObject instanceof PathTileObject) {
			newObject = PathObjects.createTileObject(roi, pathClass, null);
		} else if (pathObject instanceof PathDetectionObject) {
			newObject = PathObjects.createDetectionObject(roi, pathClass, null);
		} else if (pathObject instanceof PathAnnotationObject) {
			newObject = PathObjects.createAnnotationObject(roi, pathClass, null);
		} else if (pathObject instanceof PathRootObject) {
			newObject = new PathRootObject();
		} else if (pathObject instanceof TMACoreObject) {
			var core = (TMACoreObject)pathObject;
			// TODO: Consider supporting non-ellipse ROIs for TMA cores
			newObject = PathObjects.createTMACoreObject(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight(), core.isMissing());
		} else
			throw new UnsupportedOperationException("Unable to transform object " + pathObject);
		if (copyMeasurements && !pathObject.getMeasurementList().isEmpty()) {
			MeasurementList measurements = pathObject.getMeasurementList();
			newObject.getMeasurementList().putAll(measurements);
			newObject.getMeasurementList().close();
		}
		// Copy name, color & locked properties
		newObject.setName(pathObject.getName());
		newObject.setColor(pathObject.getColor());
		newObject.setLocked(pathObject.isLocked());
		// Copy over metadata if we have it
		if (copyMeasurements && newObject instanceof MetadataStore)
			((MetadataStore)newObject).getMetadataMap().putAll(pathObject.getUnmodifiableMetadataMap());
		// Retain the ID, if needed
		if (!createNewIDs)
			newObject.setID(pathObject.getID());
		return newObject;
	}
	
	/**
	 * Create (optionally) transformed versions of the {@link PathObject} and all its descendants, recursively.
	 * This method can be applied to all objects in a hierarchy by supplying its root object. The parent-children 
	 * relationships are kept after transformation.
	 * Measurements are copied to the new object, and new IDs are created.
	 * 
	 * @param pathObject the object to transform; this will be unchanged
	 * @param transform optional affine transform; if {@code null}, this effectively acts to duplicate the object
	 * @param copyMeasurements if true, the measurement list of the new object will be populated with the measurements of pathObject
	 * @return the new object, including all child objects
	 */
	public static PathObject transformObjectRecursive(PathObject pathObject, AffineTransform transform, boolean copyMeasurements) {
		return transformObjectRecursive(pathObject, transform, true, true);
	}

	/**
	 * Create (optionally) transformed versions of the {@link PathObject} and all its descendants, recursively, optionally assigning
	 * new IDs to the created objects. 
	 * This method can be applied to all objects in a hierarchy by supplying its root object. The parent-children 
	 * relationships are kept after transformation.
	 * 
	 * @param pathObject the object to transform; this will be unchanged
	 * @param transform optional affine transform; if {@code null}, this effectively acts to duplicate the object
	 * @param copyMeasurements if true, the measurement list of the new object will be populated with the measurements of pathObject
	 * @param createNewIDs if true, create new IDs for each copied object; otherwise, retain the same ID.
	 * @return the new object, including all child objects
	 * @since v0.4.0
	 */
	public static PathObject transformObjectRecursive(PathObject pathObject, AffineTransform transform, boolean copyMeasurements, boolean createNewIDs) {
		var newObj = transformObject(pathObject, transform, copyMeasurements, createNewIDs);
		// Parallelization can help
		if (pathObject.hasChildObjects()) {
			var newChildObjects = pathObject.getChildObjects()
				.parallelStream()
				.map(p -> transformObjectRecursive(p, transform, copyMeasurements, createNewIDs))
				.collect(Collectors.toList());
			
			newObj.addChildObjects(newChildObjects);
//			for (var child: pathObject.getChildObjects()) {
//				newObj.addPathObject(transformObjectRecursive(child, transform, copyMeasurements, createNewIDs));
//			}
		}
		
		return newObj;
	}
	
	private static ROI maybeTransformROI(ROI roi, AffineTransform transform) {
		if (roi == null || transform == null || transform.isIdentity())
			return roi;
		return RoiTools.transformROI(roi, transform);
	}
	
	/**
	 * Find objects based on a String representation of their IDs.
	 * @param ids IDs to match; each will correspond to a key in the output map
	 * @param pathObjects the objects that may contain corresponding IDs
	 * @return a map between ids and any matched objects (or null if no matched object was found)
	 * 
	 * @see #findByUUID(Collection, Collection)
	 * @see #matchByID(Collection, Collection)
	 * @since v0.4.0
	 */
	public static Map<String, PathObject> findByStringID(Collection<String> ids, Collection<? extends PathObject> pathObjects) {
		var map = pathObjects.stream().collect(Collectors.toMap(p -> p.getID().toString(), p -> p));
		var output = new HashMap<String, PathObject>();
		for (var id : ids) {
			output.put(id, map.getOrDefault(id, null));
		}
		return output;
	}
	
	/**
	 * Find objects based on their IDs.
	 * @param ids IDs to match; each will correspond to a key in the output map
	 * @param pathObjects the objects that may contain corresponding IDs
	 * @return a map between ids and any matched objects (or null if no matched object was found)
	 * 
	 * @see #findByStringID(Collection, Collection)
	 * @see #matchByID(Collection, Collection)
	 * @since v0.4.0
	 */
	public static Map<UUID, PathObject> findByUUID(Collection<UUID> ids, Collection<? extends PathObject> pathObjects) {
		var map = pathObjects.stream().collect(Collectors.toMap(p -> p.getID(), p -> p));
		var output = new HashMap<UUID, PathObject>();
		for (var id : ids) {
			output.put(id, map.getOrDefault(id, null));
		}
		return output;
	}

	/**
	 * Match objects according to their IDs.
	 * @param sourceObjects source objects; each will correspond to a key in the output map
	 * @param targetObjects target objects; each will correspond to a value in the output map provided it has a match in sourceObjects
	 * @return a map between sourceObjects and any matched target objects (or null if no matched object was found)
	 * 
	 * @see #findByUUID(Collection, Collection)
	 * @see #findByStringID(Collection, Collection)
	 * @since v0.4.0
	 */
	public static Map<PathObject, PathObject> matchByID(Collection<? extends PathObject> sourceObjects, Collection<? extends PathObject> targetObjects) {
		var map = targetObjects.stream().collect(Collectors.toMap(p -> p.getID(), p -> p));
		var output = new HashMap<PathObject, PathObject>();
		for (var sourceObject : sourceObjects) {
			output.put(sourceObject, map.getOrDefault(sourceObject.getID(), null));
		}
		return output;
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
	 * Duplicate the specified objects, assigning new IDs for each object.
	 * 
	 * @param hierarchy hierarchy containing the objects to duplicate
	 * @param pathObjects objects that should be duplicated
	 * @return true if the hierarchy is changed, false otherwise
	 * @see #duplicateObjects(PathObjectHierarchy, Collection, boolean)
	 */
	public static boolean duplicateObjects(PathObjectHierarchy hierarchy, Collection<PathObject> pathObjects) {
		return duplicateObjects(hierarchy, pathObjects, true);
	}
	
	/**
	 * Duplicate the specified objects, optionally creating new IDs.
	 * 
	 * @param hierarchy hierarchy containing the objects to duplicate
	 * @param pathObjects objects that should be duplicated
	 * @param createNewIDs if true, create new IDs for each copied object; otherwise, retain the same ID.
	 * @return true if the hierarchy is changed, false otherwise
	 * @since v0.4.0
	 */
	public static boolean duplicateObjects(PathObjectHierarchy hierarchy, Collection<PathObject> pathObjects, boolean createNewIDs) {
		var map = pathObjects
				.stream()
				.collect(Collectors.toMap(p -> p,
						p -> PathObjectTools.transformObject(p, null, true, createNewIDs)));
		if (map.isEmpty()) {
			logger.error("No selected objects to duplicate!");
			return false;
		}
		// Add objects using the default add method (not trying to resolve location)
		hierarchy.addObjects(map.values());
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
	
	
	
	/**
	 * Set selected objects to have the specified 'locked' status.
	 * @param hierarchy the hierarchy; if provided, an event will be fired if any objects have their status changed
	 * @param pathObjects the objects to update
	 * @param setToLocked the target locked status
	 */
	private static void setSelectedObjectsLocked(final PathObjectHierarchy hierarchy,
			final Collection<? extends PathObject> pathObjects, 
			final boolean setToLocked) {
		
		List<PathObject> changed = new ArrayList<>();
		for (var pathObject : pathObjects) {
			if (pathObject instanceof PathROIObject) {
				if (pathObject.isLocked() != setToLocked) {
					pathObject.setLocked(setToLocked);
					changed.add(pathObject);
				}
			}
		}
		
		if (hierarchy != null && !changed.isEmpty())
			hierarchy.fireObjectsChangedEvent(PathObjectTools.class, changed);
	}
	
	/**
	 * Set specified objects to be 'locked'.
	 * @param hierarchy if not null, fire an update event if the locked status for any object is changed
	 * @param pathObjects the objects to update
	 */
	public static void lockObjects(final PathObjectHierarchy hierarchy, final Collection<? extends PathObject> pathObjects) {
		setSelectedObjectsLocked(hierarchy, pathObjects, true);
	}
	
	/**
	 * Set specified objects to be 'unlocked'.
	 * @param hierarchy if not null, fire an update event if the locked status for any object is changed
	 * @param pathObjects the objects to update
	 */
	public static void unlockObjects(final PathObjectHierarchy hierarchy, final Collection<? extends PathObject> pathObjects) {
		setSelectedObjectsLocked(hierarchy, pathObjects, false);
	}
	
	/**
	 * Set selected objects to be 'locked', firing an update event if the status of any object is changed.
	 * @param hierarchy
	 */
	public static void lockSelectedObjects(final PathObjectHierarchy hierarchy) {
		if (hierarchy == null)
			return;
		setSelectedObjectsLocked(hierarchy, hierarchy.getSelectionModel().getSelectedObjects(), true);
	}
	
	/**
	 * Set selected objects to be 'unlocked', firing an update event if the status of any object is changed.
	 * @param hierarchy
	 */
	public static void unlockSelectedObjects(final PathObjectHierarchy hierarchy) {
		if (hierarchy == null)
			return;
		setSelectedObjectsLocked(hierarchy, hierarchy.getSelectionModel().getSelectedObjects(), false);
	}
	
	/**
	 * Toggle the 'locked' status of selected objects, firing an update event if the status of any object is changed.
	 * @param hierarchy
	 */
	public static void toggleSelectedObjectsLocked(final PathObjectHierarchy hierarchy) {
		if (hierarchy == null)
			return;
		toggleObjectsLocked(hierarchy, hierarchy.getSelectionModel().getSelectedObjects());
	}
	
	/**
	 * Toggle the 'locked' status of specified objects.
	 * @param hierarchy if not null, fire an update event if the locked status for any object is changed
	 * @param pathObjects the objects to update
	 */
	public static void toggleObjectsLocked(final PathObjectHierarchy hierarchy, final Collection<? extends PathObject> pathObjects) {
		if (hierarchy == null)
			return;
		
		List<PathObject> changed = new ArrayList<>();
		for (var pathObject : pathObjects) {
			if (pathObject instanceof PathROIObject) {
				pathObject.setLocked(!pathObject.isLocked());
				changed.add(pathObject);
			}
		}
		
		if (hierarchy != null && !changed.isEmpty())
			hierarchy.fireObjectsChangedEvent(PathObjectTools.class, changed);
	}

	/**
	 * Get a set containing the names of all measurements found in the measurement lists of a specified object collection.
	 * 
	 * @param pathObjects
	 * @return
	 */
	public static Set<String> getAvailableFeatures(final Collection<? extends PathObject> pathObjects) {
		Set<String> featureSet = new LinkedHashSet<>();
		// This has a small optimization that takes into consideration the fact that many objects share references to exactly the same MeasurementLists -
		// so by checking the last list that was added, there is no need to bother the set to add the same thing again.
		List<String> lastNames = null;
		for (PathObject pathObject : pathObjects) {
			if (!pathObject.hasMeasurements())
				continue;
			List<String> list = pathObject.getMeasurementList().getMeasurementNames();
			if (lastNames != list)
				featureSet.addAll(list);
			lastNames = list;
		}
		return featureSet;
	}

	/**
	 * Create a mapping between {@linkplain PathObject PathObjects} and their current {@linkplain PathClass PathClasses}.
	 * This can be useful to preserve a classification so that it may be reset later.
	 * <p>
	 * Note: classification probabilities are not retained using this approach.
	 * @param pathObjects the objects containing classifications
	 * @return a mapping between objects and their current classifications
	 * @see PathObjectTools#restoreClassificationsFromMap(Map)
	 */
	public static Map<PathObject, PathClass> createClassificationMap(Collection<? extends PathObject> pathObjects) {
		Map<PathObject, PathClass> mapPrevious = new HashMap<>();
		for (var pathObject : pathObjects) {
			mapPrevious.put(pathObject, pathObject.getPathClass());
		}
		return mapPrevious;
	}

	/**
	 * Reassign classifications to objects, as were previously obtained using {@link #createClassificationMap(Collection)}.
	 * 
	 * @param classificationMap the map containing objects and the classifications that should be applied
	 * @return a collection containing all objects with classifications that were changed. This can be used to fire update events.
	 * @see #createClassificationMap(Collection)
	 */
	public static Collection<PathObject> restoreClassificationsFromMap(Map<PathObject, PathClass> classificationMap) {
		var changed = new ArrayList<PathObject>();
		for (var entry : classificationMap.entrySet()) {
			var pathObject = entry.getKey();
			var pathClass = entry.getValue();
			if (pathClass == PathClass.NULL_CLASS)
				pathClass = null;
			if (!Objects.equals(pathObject.getPathClass(), pathClass)) {
				pathObject.setPathClass(pathClass);
				changed.add(pathObject);
			}
		}
		return changed;
	}

	/**
	 * Get a set of the represented path classes, i.e. those with at least 1 manually-labelled object.
	 * @param hierarchy 
	 * @param cls 
	 * 
	 * @return
	 */
	public static Set<PathClass> getRepresentedPathClasses(final PathObjectHierarchy hierarchy, final Class<? extends PathObject> cls) {
		Set<PathClass> pathClassSet = new LinkedHashSet<>();
		for (PathObject pathObject : hierarchy.getObjects(null, cls)) {
			if (pathObject.getPathClass() != null)
				pathClassSet.add(pathObject.getPathClass());
		}
		return pathClassSet;
	}

	/**
	 * Assign cell classifications as positive or negative based upon a specified measurement, using up to 3 intensity bins.
	 * 
	 * An IllegalArgumentException is thrown if &lt; 1 or &gt; 3 intensity thresholds are provided.<p>
	 * If the object does not have the required measurement, its {@link PathClass} will be set to its 
	 * first 'non-intensity' ancestor {@link PathClass}.
	 * <p>
	 * Note that as of v0.3.0, all ignored classes (see {@link PathClassTools#isIgnoredClass(PathClass)} are ignored and therefore 
	 * will not be 'intensity classified'.
	 * 
	 * @param pathObject 		the object to classify.
	 * @param measurementName 	the name of the measurement to use for thresholding.
	 * @param thresholds 		between 1 and 3 intensity thresholds, used to indicate negative/positive, or negative/1+/2+/3+
	 * @return 					the PathClass of the object after running this method.
	 */
	public static PathClass setIntensityClassification(final PathObject pathObject, final String measurementName, final double... thresholds) {
		if (thresholds.length == 0 || thresholds.length > 3)
			throw new IllegalArgumentException("Between 1 and 3 intensity thresholds required!");
		
		// Can't perform any classification if measurement is null or blank
		if (measurementName == null || measurementName.isEmpty())
			throw new IllegalArgumentException("Measurement name cannot be empty or null!");
		
		PathClass baseClass = PathClassTools.getNonIntensityAncestorClass(pathObject.getPathClass());
		
		// Don't do anything with the 'ignore' class
		if (!PathClassTools.isNullClass(baseClass) && PathClassTools.isIgnoredClass(baseClass))
			return pathObject.getPathClass();
		
		double intensityValue = pathObject.getMeasurementList().get(measurementName);
		
		boolean singleThreshold = thresholds.length == 1;
	
		if (Double.isNaN(intensityValue))	// If the measurement is missing, reset to base class
			pathObject.setPathClass(baseClass);
		else if (intensityValue < thresholds[0])
			pathObject.setPathClass(PathClass.getNegative(baseClass));
		else {
			if (singleThreshold)
				pathObject.setPathClass(PathClass.getPositive(baseClass));
			else if (thresholds.length >= 3 && intensityValue >= thresholds[2])
				pathObject.setPathClass(PathClass.getThreePlus(baseClass));				
			else if (thresholds.length >= 2 && intensityValue >= thresholds[1])
				pathObject.setPathClass(PathClass.getTwoPlus(baseClass));				
			else if (intensityValue >= thresholds[0])
				pathObject.setPathClass(PathClass.getOnePlus(baseClass));				
		}
		return pathObject.getPathClass();
	}

	/**
	 * Set the intensity classifications for the specified objects.
	 * 
	 * @param pathObjects
	 * @param measurementName measurement to threshold
	 * @param thresholds either 1 or 3 thresholds, depending upon whether objects should be classified as Positive/Negative or Negative/1+/2+/3+
	 */
	public static void setIntensityClassifications(final Collection<? extends PathObject> pathObjects, final String measurementName, final double... thresholds) {
		pathObjects.stream().forEach(p -> setIntensityClassification(p, measurementName, thresholds));
	}
	
}