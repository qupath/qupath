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

package qupath.lib.gui.viewer;

import java.util.Collection;
import java.util.function.BiPredicate;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImageRegion;
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
			switch(this) {
			case EVERYWHERE:
				return "Everywhere";
			case IMAGE:
				return "Image (non-empty regions)";
			case ANY_OBJECTS:
				return "Any object ROI";
			case ANY_ANNOTATIONS:
				return "Any annotation ROI";
			case ANY_OBJECTS_BOUNDS:
				return "Any object bounds (fast)";
			case ANY_ANNOTATIONS_BOUNDS:
				return "Any annotation bounds (fast)";
			default:
				return "Unknown";
			}
		}

		@Override
		public boolean test(ImageData<?> imageData, RegionRequest region) {
			switch (this) {
			case ANY_ANNOTATIONS:
				var annotations = imageData.getHierarchy().getObjectsForRegion(PathAnnotationObject.class, region, null);
				return overlapsObjects(annotations, region);
			case ANY_OBJECTS:
				var pathObjects = imageData.getHierarchy().getObjectsForRegion(null, region, null);
				return overlapsObjects(pathObjects, region);
			case IMAGE:
				return !imageData.getServer().isEmptyRegion(region);
			case ANY_OBJECTS_BOUNDS:
				return imageData.getHierarchy().hasObjectsForRegion(null, region);
			case ANY_ANNOTATIONS_BOUNDS:
				return imageData.getHierarchy().hasObjectsForRegion(PathAnnotationObject.class, region);
			default:
				return true;
			}
		}
		
	}
	
	private static boolean overlapsObjects(Collection<? extends PathObject> pathObjects, ImageRegion region) {
		for (var pathObject : pathObjects) {
			var roi = pathObject.getROI();
			if (roi == null)
				continue;
			if (roi.isPoint()) {
				for (var p : roi.getAllPoints()) {
					if (region.contains((int)p.getX(), (int)p.getY(), roi.getZ(), roi.getT()))
						return true;
				}
			} else {
				var shape = roi.getShape();
				if (shape.intersects(region.getX(), region.getY(), region.getWidth(), region.getHeight()))
					return true;
			}
		}
		return false;
	}
	
	
}