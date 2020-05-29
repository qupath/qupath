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

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import qupath.lib.measurements.MeasurementList;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.RoiTools;

/**
 * Collection of static methods to help with adding ImageJ measurements to PathObjects.
 * 
 * @author Pete Bankhead
 *
 */
class ObjectMeasurements {
	
	/**
	 * Add basic shape measurements to a MeasurementList.
	 * @param measurementList
	 * @param roi
	 * @param pixelWidth
	 * @param pixelHeight
	 * @param prefix
	 */
	public static void addShapeStatistics(MeasurementList measurementList, PolygonROI roi, double pixelWidth, double pixelHeight, String prefix) {
		measurementList.putMeasurement(prefix + "Area", roi.getScaledArea(pixelWidth, pixelHeight));
		measurementList.putMeasurement(prefix + "Perimeter", roi.getScaledLength(pixelWidth, pixelHeight));
		measurementList.putMeasurement(prefix + "Circularity", RoiTools.getCircularity(roi, pixelWidth, pixelHeight));
//		measurementList.putMeasurement(prefix + "Convex area", roi.getScaledConvexArea(pixelWidth, pixelHeight));
		measurementList.putMeasurement(prefix + "Solidity", roi.getSolidity());
	}

	public static void addShapeStatistics(MeasurementList measurementList, Roi roi, ImageProcessor ip, Calibration cal, String prefix) {
		ip.setRoi(roi);
		ImageStatistics stats = ImageStatistics.getStatistics(ip, ShapeStatsIJ.SHAPE_MEASUREMENT_OPTIONS, cal);
	
		// For some shape stats it is necessary for the ImagePlus to be set... to ensure calibration is applied properly
		// So check this is the case, and set the ROI image as needed
		ImagePlus impRoi = roi.getImage();
		boolean calibrationValid = impRoi != null && impRoi.getCalibration().equals(cal);
		if (!calibrationValid) {
			ImagePlus impTemp = new ImagePlus("Temp", ip);
			impTemp.setCalibration(cal);
			roi.setImage(impTemp);
		}
		ShapeStatsIJ shapeStats = new ShapeStatsIJ(roi, stats);
		// Reset the ROI image, if necessary
		if (!calibrationValid)
			roi.setImage(impRoi);
	
		// TODO: Add units!
		if (roi.isArea())
			measurementList.putMeasurement(prefix + "Area", shapeStats.area());
		measurementList.putMeasurement(prefix + "Perimeter", shapeStats.perimeter());
		measurementList.putMeasurement(prefix + "Circularity", shapeStats.circularity());
		measurementList.putMeasurement(prefix + "Max caliper", shapeStats.maxCaliper());
		measurementList.putMeasurement(prefix + "Min caliper", shapeStats.minCaliper());
//		measurementList.putMeasurement(prefix + "Major axis", shapeStats.majorAxisLength());
//		measurementList.putMeasurement(prefix + "Minor axis", shapeStats.minorAxisLength());
		measurementList.putMeasurement(prefix + "Eccentricity", shapeStats.eccentricity());
	
		// Note: Roundness correlates closely with eccentricity, so was removed
		// Major & Minor axis correlate closely with max & min caliper, so were removed
		
		
//		// If pixels are not square (sadly sometimes the case...) ellipse measurements fail... so we need to compute roundness manually
//		// (we assume square pixels to do so, but otherwise the measurement is dimensionless)
//		if (shapeStats.majorAxisLength() == shapeStats.minorAxisLength() && shapeStats.majorAxisLength() == 0) {
//			stats = ImageStatistics.getStatistics(ip, Measurements.ELLIPSE, null);
//			measurementList.addMeasurement(prefix + "Roundness", stats.minor / stats.major);
//		} else {
//			measurementList.addMeasurement(prefix + "Roundness", shapeStats.roundness());
//		}
	}
	
}
