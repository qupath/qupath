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
				return "Image (non-empty regions)";
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