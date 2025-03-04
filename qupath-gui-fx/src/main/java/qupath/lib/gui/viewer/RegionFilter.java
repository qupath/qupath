/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiPredicate;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

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
	enum StandardRegionFilters implements RegionFilter {
	
		/**
		 * Accept all requests
		 */
		EVERYWHERE,
		/**
		 * Regions overlapping the ROIs of any objects
		 */
		ANY_OBJECTS,
		/**
		 * Regions overlapping the ROIs of any annotations
		 */
		ANY_ANNOTATIONS,
		/**
		 * Regions overlapping the bounding box of any objects
		 */
		ANY_OBJECTS_BOUNDS,
		/**
		 * Regions overlapping the bounding box of any annotations
		 */
		ANY_ANNOTATIONS_BOUNDS,
		/**
		 * Accept all requests for the image where the region is non-empty
		 */
		IMAGE;
		
		@Override
		public String toString() {
            return switch (this) {
                case EVERYWHERE -> "Everywhere";
                case IMAGE -> "Image (non-empty regions)";
                case ANY_OBJECTS -> "Any object ROI";
                case ANY_ANNOTATIONS -> "Any annotation ROI";
                case ANY_OBJECTS_BOUNDS -> "Any object bounds (fast)";
                case ANY_ANNOTATIONS_BOUNDS -> "Any annotation bounds (fast)";
                default -> "Unknown";
            };
		}

		@Override
		public boolean test(ImageData<?> imageData, RegionRequest region) {
            return switch (this) {
                case ANY_ANNOTATIONS -> {
                    var annotations = imageData.getHierarchy().getAnnotationsForRegion(region, null);
                    yield overlapsObjects(annotations, region);
                }
                case ANY_OBJECTS -> {
                    var pathObjects = imageData.getHierarchy().getAllObjectsForRegion(region, null);
                    yield overlapsObjects(pathObjects, region);
                }
                case IMAGE -> !imageData.getServer().isEmptyRegion(region);
                case ANY_OBJECTS_BOUNDS -> imageData.getHierarchy().hasObjectsForRegion(null, region);
                case ANY_ANNOTATIONS_BOUNDS ->
                        imageData.getHierarchy().hasObjectsForRegion(PathAnnotationObject.class, region);
                default -> true;
            };
		}
		
	}
	
	private static boolean overlapsObjects(Collection<? extends PathObject> pathObjects, ImageRegion region) {
		var rois = pathObjects.stream()
				.map(PathObject::getROI)
				.filter(Objects::nonNull)
				.sorted(Comparator.comparingInt(ROI::getNumPoints))
				.toList();
		for (var r : rois) {
			if (r.intersects(region))
				return true;
		}
		return false;
	}
	
	
}