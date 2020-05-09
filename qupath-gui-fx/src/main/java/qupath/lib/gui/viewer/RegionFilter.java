package qupath.lib.gui.viewer;

import java.util.function.BiPredicate;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.regions.RegionRequest;

/**
 * Define the area of an image to which pixel classification should be applied.
 * This is used to filter regions during live prediction.
 * 
 * @author Pete Bankhead
 */
public interface RegionFilter extends BiPredicate<ImageData<?>, RegionRequest> {
	
	/**
	 * Standard classification regions (hopefully all you will ever need).
	 */
	public enum StandardRegionFilters implements RegionFilter {
	
		/**
		 * Accept all requests
		 */
		EVERYWHERE,
		/**
		 * Accept all requests for the image where the region is non-empty
		 */
		IMAGE,
		/**
		 * Regions overlapping any objects
		 */
		ANY_OBJECTS,
		/**
		 * Annotated-regions only
		 */
		ANY_ANNOTATIONS;
		
		@Override
		public String toString() {
			switch(this) {
			case EVERYWHERE:
				return "Everywhere";
			case IMAGE:
				return "Entire image (non-empty regions)";
			case ANY_OBJECTS:
				return "Any objects";
			case ANY_ANNOTATIONS:
				return "Any annotations";
			default:
				return "Unknown";
			}
		}

		@Override
		public boolean test(ImageData<?> imageData, RegionRequest region) {
			switch (this) {
			case ANY_ANNOTATIONS:
				return imageData.getHierarchy().hasObjectsForRegion(PathAnnotationObject.class, region);
			case ANY_OBJECTS:
				return imageData.getHierarchy().hasObjectsForRegion(null, region);
			case IMAGE:
				return !imageData.getServer().isEmptyRegion(region);
			case EVERYWHERE:
			default:
				return true;
			}
		}
		
	}
	
}