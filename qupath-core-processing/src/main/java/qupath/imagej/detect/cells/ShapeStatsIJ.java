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

package qupath.imagej.detect.cells;

import ij.gui.Roi;
import ij.measure.Measurements;
import ij.process.ImageStatistics;

/**
 * Class to help with adding shape measurements calculated by ImageJ to PathObjects.
 * 
 * @author Pete Bankhead
 *
 */
class ShapeStatsIJ {
	
	public static int SHAPE_MEASUREMENT_OPTIONS = Measurements.AREA | Measurements.PERIMETER | Measurements.SKEWNESS | Measurements.ELLIPSE | Measurements.FERET;
	
	private double area, perimeter, xCentroid, yCentroid, majorAxisLength, minorAxisLength;
	private double maxCaliper, minCaliper;
	
	// TODO: Introduce convex area & solidity
	
	public ShapeStatsIJ(Roi roi, ImageStatistics stats) {
//		if (roi.isLine())
//			this.area = 0;
//		else
		this.area = stats.area;
		this.perimeter = roi.getLength();
		this.xCentroid = stats.xCentroid;
		this.yCentroid = stats.yCentroid;
		this.majorAxisLength = stats.major;
		this.minorAxisLength = stats.minor;
		
		double[] feretValues = roi.getFeretValues();
		this.maxCaliper = feretValues[0];
		this.minCaliper = feretValues[2];
	}
	
	public double area() {
		return area;
	}

	public double perimeter() {
		return perimeter;
	}
	
	public double maxCaliper() {
		return maxCaliper;
	}
	
	public double minCaliper() {
		return minCaliper;
	}

	public int xCentroidInt() {
		return (int)(xCentroid + .5);
	}

	public int yCentroidInt() {
		return (int)(yCentroid + .5);
	}

	public double xCentroid() {
		return xCentroid;
	}
	
	public double yCentroid() {
		return yCentroid;
	}

	public double majorAxisLength() {
		return majorAxisLength;
	}

	public double minorAxisLength() {
		return minorAxisLength;
	}
	
	public double eccentricity() {
		double majAxis = majorAxisLength();
		double minAxis = minorAxisLength();
		return 2*Math.sqrt(((majAxis * majAxis * 0.25) - (minAxis * minAxis * 0.25))) / majAxis;
	}

	public double aspectRatio() {
		return majorAxisLength / minorAxisLength;
	}

	public double roundness() {
		return 1 / aspectRatio();
	}

	public double circularity() {
		return Math.min(4.0 * Math.PI * area / (perimeter * perimeter), 1);
	}

}