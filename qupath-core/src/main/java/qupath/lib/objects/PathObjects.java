package qupath.lib.objects;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Helper class to create {@link PathObject}.  This should be used in preference of any constructors, 
 * which linger only for historical reasons and compatibility.
 * 
 * @author Pete Bankhead
 *
 */
public class PathObjects {
	
	/**
	 * Create a classified annotation object with a specified measurement list.
	 * <p>
	 * Annotation objects are used to represent mutable objects, prizing flexibility over efficiency.
	 * 
	 * @param roi
	 * @param pathClass
	 * @param measurements
	 * @return
	 */
	public static PathObject createAnnotationObject(final ROI roi, final PathClass pathClass, final MeasurementList measurements) {
		if (roi == null)
			throw new IllegalArgumentException("A ROI is required to create an annotation object!");
		return new PathAnnotationObject(roi, pathClass, measurements);
	}

	/**
	 * Create a classified annotation object.
	 * <p>
	 * Annotation objects are used to represent mutable objects, prizing flexibility over efficiency.
	 * 
	 * @param roi
	 * @param pathClass
	 * @return
	 */
	public static PathObject createAnnotationObject(final ROI roi, final PathClass pathClass) {
		return createAnnotationObject(roi, pathClass, null);
	}

	/**
	 * Create an unclassified annotation object.
	 * <p>
	 * Annotation objects are used to represent mutable objects, prizing flexibility over efficiency.
	 * 
	 * @param roi
	 * @return
	 */
	public static PathObject createAnnotationObject(final ROI roi) {
		return createAnnotationObject(roi, null);
	}
	
	/**
	 * Create a TMA core object with an circular ROI.
	 * 
	 * @param xCenter
	 * @param yCenter
	 * @param diameter
	 * @param isMissing
	 * @return
	 */
	public static TMACoreObject createTMACoreObject(double xCenter, double yCenter, double diameter, boolean isMissing) {
		return createTMACoreObject(xCenter-diameter/2, yCenter-diameter/2, diameter, diameter, isMissing);
	}
	
	/**
	 * Create a TMA core object with an ellipse ROI.
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param isMissing
	 * @return
	 */
	public static TMACoreObject createTMACoreObject(double x, double y, double width, double height, boolean isMissing) {
		ROI roi = ROIs.createEllipseROI(x, y, width, height, ImagePlane.getDefaultPlane());
		return new TMACoreObject(roi, isMissing);
	}
	
	/**
	 * Create a classified detection object with a specified measurement list.
	 * <p>
	 * Detection objects are used to represent immutable objects that could be very numerous, prizing efficiency over flexibility.
	 * 
	 * @param roi
	 * @param pathClass
	 * @param measurements
	 * @return
	 * 
	 * @see #createCellObject(ROI, ROI, PathClass, MeasurementList)
	 */
	public static PathObject createDetectionObject(final ROI roi, final PathClass pathClass, final MeasurementList measurements) {
		if (roi == null)
			throw new IllegalArgumentException("A ROI is required to create a detection object!");
		return new PathDetectionObject(roi, pathClass, measurements);
	}
	
	/**
	 * Create a classified detection object.
	 * <p>
	 * Detection objects are used to represent immutable objects that could be very numerous, prizing efficiency over flexibility.
	 * 
	 * @param roi
	 * @param pathClass
	 * @return
	 */
	public static PathObject createDetectionObject(final ROI roi, final PathClass pathClass) {
		return createDetectionObject(roi, pathClass, null);
	}

	/**
	 * Create an unclassified detection object.
	 * <p>
	 * Detection objects are used to represent immutable objects that could be very numerous, prizing efficiency over flexibility.
	 * 
	 * @param roi
	 * @return
	 */
	public static PathObject createDetectionObject(final ROI roi) {
		return createDetectionObject(roi, null);
	}

	/**
	 * Create a tile object.
	 * <p>
	 * Tile objects represent a special case of a detection objects, were the ROI doesn't represent any particular structure 
	 * (e.g. it is a superpixel or square tile representing a local collection of pixels used on the path to region segmentation).
	 * 
	 * @param roi
	 * @param pathClass
	 * @param measurements
	 * @return
	 * 
	 * @see #createDetectionObject
	 */
	public static PathObject createTileObject(final ROI roi, final PathClass pathClass, final MeasurementList measurements) {
		if (roi == null)
			throw new IllegalArgumentException("A ROI is required to create a detection object!");
		return new PathTileObject(roi, pathClass, measurements);
	}
	
	/**
	 * Create an unclassified tile object.
	 * <p>
	 * Tile objects represent a special case of a detection objects, were the ROI doesn't represent any particular structure 
	 * (e.g. it is a superpixel or square tile representing a local collection of pixels used on the path to region segmentation).
	 * 
	 * @param roi
	 * @return
	 * 
	 * @see #createDetectionObject
	 */
	public static PathObject createTileObject(final ROI roi) {
		if (roi == null)
			throw new IllegalArgumentException("A ROI is required to create a detection object!");
		return new PathTileObject(roi);
	}
	
	/**
	 * Create a cell object.
	 * <p>
	 * Cell objects represent a special case of a detection objects, where an additional ROI can be stored representing 
	 * the cell nucleus.
	 * 
	 * @param roiCell
	 * @param roiNucleus
	 * @param pathClass
	 * @param measurements
	 * @return
	 * 
	 * @see #createDetectionObject
	 */
	public static PathObject createCellObject(final ROI roiCell, final ROI roiNucleus, final PathClass pathClass, final MeasurementList measurements) {
		return new PathCellObject(roiCell, roiNucleus, pathClass, measurements);
	}
	

}
