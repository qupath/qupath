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

package qupath.lib.objects;

import java.util.function.Predicate;

/**
 * Enumeration of filters (predicates) that can be used to select objects based on their type.
 * 
 * @author Pete Bankhead
 *
 */
public enum PathObjectFilter implements Predicate<PathObject> {
	/**
	 * Accept annotation objects
	 */
	ANNOTATIONS,
	
	/**
	 * Accept detection objects (no subtypes, e.g. cells)
	 */
	DETECTIONS,
	
	/**
	 * Accept detection objects (all subtypes)
	 */
	DETECTIONS_ALL,
	
	/**
	 * Accept cells
	 */
	CELLS,
	
	/**
	 * Accept tiles
	 */
	TILES,
	
	/**
	 * Accept TMA cores
	 */
	TMA_CORES,
	
	/**
	 * Accept any object that is 'unlocked'
	 */
	UNLOCKED,
	
	/**
	 * Accept any object that has a ROI
	 */
	ROI,
	
	/**
	 * Accept any object that has a line ROI
	 */
	ROI_LINE,

	/**
	 * Accept any object that has an area ROI
	 */
	ROI_AREA,

	/**
	 * Accept any object that has a points ROI
	 */
	ROI_POINT
	;
	
	
	@Override
	public String toString() {
		switch (this) {
		case ANNOTATIONS:
			return "Annotations";
		case CELLS:
			return "Cells";
		case DETECTIONS:
			return "Detections (no subtypes)";
		case DETECTIONS_ALL:
			return "Detections (all)";
		case TILES:
			return "Tiles";
		case TMA_CORES:
			return "TMA cores";
		case UNLOCKED:
			return "Unlocked";
		case ROI:
			return "Has ROI";
		case ROI_LINE:
			return "Has line ROI";
		case ROI_AREA:
			return "Has area ROI";
		case ROI_POINT:
			return "Has point ROI";
		default:
			throw new IllegalArgumentException();
		}
	}

	@Override
	public boolean test(PathObject p) {
		switch (this) {
		case ANNOTATIONS:
			return p.isAnnotation();
		case CELLS:
			return p.isCell();
		case DETECTIONS_ALL:
			return p.isDetection();
		case DETECTIONS:
			return p.isDetection() && PathDetectionObject.class.equals(p.getClass());
		case TILES:
			return p.isTile();
		case TMA_CORES:
			return p.isTMACore();
		case UNLOCKED:
			return !p.isLocked();
		case ROI:
			return p.hasROI();
		case ROI_LINE:
			return p.hasROI() && p.getROI().isLine();
		case ROI_AREA:
			return p.hasROI() && p.getROI().isArea();
		case ROI_POINT:
			return p.hasROI() && p.getROI().isPoint();
		default:
			throw new IllegalArgumentException();
		}
	}
	
}