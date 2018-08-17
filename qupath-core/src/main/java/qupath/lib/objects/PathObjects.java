package qupath.lib.objects;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;

public class PathObjects {
	
	/**
	 * Create an annotation object.
	 * 
	 * <p>Annotation objects are used to represent mutable objects, prizing flexibility over efficiency.
	 * 
	 * @param roi
	 * @param pathClass
	 * @param measurements
	 * @return
	 * 
	 * @see createCellObject
	 */
	public static PathObject createAnnotationObject(final ROI roi, final PathClass pathClass, final MeasurementList measurements) {
		if (roi == null)
			throw new IllegalArgumentException("A ROI is required to create an annotation object!");
		return new PathAnnotationObject(roi, pathClass, measurements);
	}

	public static PathObject createAnnotationObject(final ROI roi, final PathClass pathClass) {
		return createAnnotationObject(roi, pathClass, null);
	}

	public static PathObject createAnnotationObject(final ROI roi) {
		return createAnnotationObject(roi, null);
	}
	
	/**
	 * Create a detection object.
	 * 
	 * <p>Detection objects are used to represent immutable objects that could be very numerous, prizing efficiency over flexibility.
	 * 
	 * @param roi
	 * @param pathClass
	 * @param measurements
	 * @return
	 * 
	 * @see createCellObject
	 */
	public static PathObject createDetectionObject(final ROI roi, final PathClass pathClass, final MeasurementList measurements) {
		if (roi == null)
			throw new IllegalArgumentException("A ROI is required to create a detection object!");
		return new PathDetectionObject(roi, pathClass, measurements);
	}
	
	public static PathObject createDetectionObject(final ROI roi, final PathClass pathClass) {
		return createDetectionObject(roi, null);
	}

	public static PathObject createDetectionObject(final ROI roi) {
		return createDetectionObject(roi);
	}

	/**
	 * Create a tile object.
	 * 
	 * <p>Tile objects represent a special case of a detection objects, were the ROI doesn't represent any particular structure 
	 * (e.g. it is a superpixel or square tile representing a local collection of pixels used on the path to region segmentation).
	 * 
	 * @param roi
	 * @param pathClass
	 * @param measurements
	 * @return
	 * 
	 * @see createDetectionObject
	 */
	public static PathObject createTileObject(final ROI roi, final PathClass pathClass, final MeasurementList measurements) {
		if (roi == null)
			throw new IllegalArgumentException("A ROI is required to create a detection object!");
		return new PathTileObject(roi, pathClass, measurements);
	}
	
	public static PathObject createTileObject(final ROI roi) {
		if (roi == null)
			throw new IllegalArgumentException("A ROI is required to create a detection object!");
		return new PathTileObject(roi);
	}
	
	/**
	 * Create a cell object.
	 * 
	 * <p>Cell objects represent a special case of a detection objects, where an additional ROI can be stored representing 
	 * the cell nucleus.
	 * 
	 * @param roiCell
	 * @param roiNucleus
	 * @param pathClass
	 * @param measurements
	 * @return
	 * 
	 * @see createDetectionObject
	 */
	public static PathObject createCellObject(final ROI roiCell, final ROI roiNucleus, final PathClass pathClass, final MeasurementList measurements) {
		return new PathCellObject(roiCell, roiNucleus, pathClass, measurements);
	}
	

}
