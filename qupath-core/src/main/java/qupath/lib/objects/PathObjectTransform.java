package qupath.lib.objects;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.locationtech.jts.geom.util.AffineTransformation;

import qupath.lib.images.ImageData;
import qupath.lib.roi.GeometryTools;

/**
 * Class to help with applying affine transforms to objects.
 * 
 * @author Melvin Gelbard
 */
public final class PathObjectTransform {
	
	// Suppress default constructor for non-instantiability
	private PathObjectTransform() {
		throw new AssertionError();
	}
	
	/**
	 * Transform the collection of {@link PathObject}s according to the given 
	 * affine transformation matrix. The order of the {@code affineTransform} 
	 * array follows the one of jts' {@link AffineTransformation}. The new 
	 * {@link PathObject}s will be added to the {@link ImageData}'s hierarchy, 
	 * while the previous ones will only be kept if the {@code duplicateObjects} 
	 * flag is triggered. The newly-created objects are selected when this 
	 * method terminates.
	 * 
	 * @param imageData
	 * @param objs
	 * @param affineTransform double array with the following order: [m00, m01, m02, m10, m11, m12]
	 * @param keepMeasurements
	 * @param duplicateObjects
	 * @return
	 */
	public static Collection<PathObject> transformObjects(ImageData<BufferedImage> imageData, Collection<PathObject> objs, List<? extends Number> affineTransform, boolean keepMeasurements, boolean duplicateObjects) {
		var hierarchy = imageData.getHierarchy();
		
		// Transform each object in the collection with the requested affine transform
		List<PathObject> newObjs = new ArrayList<>();

		// AffineTransform has a different matrix format from jts or javafx
		AffineTransformation transformation = new AffineTransformation(
			affineTransform.get(0).doubleValue(), 
			affineTransform.get(1).doubleValue(), 
			affineTransform.get(2).doubleValue(), 
			affineTransform.get(3).doubleValue(), 
			affineTransform.get(4).doubleValue(), 
			affineTransform.get(5).doubleValue()
		);
		
		for (PathObject obj: objs) {
			newObjs.add(PathObjectTools.transformObject(obj, GeometryTools.convertTransform(transformation), keepMeasurements));
		}
		
		// Add all transformed PathObjects to the hierarchy at once
		hierarchy.addPathObjects(newObjs);
		
		// Delete previous PathObjects if requested
		if (!duplicateObjects)
			hierarchy.removeObjects(objs, true);
		
		// Select new objects
		hierarchy.getSelectionModel().setSelectedObjects(newObjs, newObjs.iterator().next());
		
		return newObjs;
	}
}
