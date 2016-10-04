/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.imagej.objects.measure;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import qupath.imagej.objects.ROIConverterIJ;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.PolygonROI;

/**
 * Collection of static methods to help with adding ImageJ measurements to PathObjects.
 * 
 * @author Pete Bankhead
 *
 */
public class ObjectMeasurements {
	
	public final static char SPLIT_CHAR = ':';

	public static enum IntensityMeasurements {
			MEAN,
			SUM,
			STD_DEV,
			COEFFICIENT_OF_VARIATION,
			MIN,
			MAX,
			RANGE,
			SKEWNESS,
			KURTOSIS,
			MEMBRANE;
	//		MEAN_MEMBRANE,
	//		STD_DEV_MEMBRANE;
			@Override
			public String toString() {
				String s = this.name().substring(0,1) + this.name().substring(1).replace("_", " ").toLowerCase();
				return s;
			}
		}
	
	public static void addIntensityMeasurements(List<? extends PathObject> pathObjects, IntensityMeasurements measurement, ImageProcessor ip, String ipName, Calibration cal, double downsampleFactor) {
		addIntensityMeasurements(pathObjects, Collections.singletonList(measurement), ip, ipName, cal, downsampleFactor);
	}
	
	public static void addIntensityMeasurements(List<? extends PathObject> pathObjects, List<IntensityMeasurements> measurements, ImageProcessor ip, String ipName, Calibration cal, double downsampleFactor) {
		
		int measurementFlags = Measurements.AREA;
		boolean doMean = false, doSum = false, doStdDev = false, doCoV = false, doMin = false, doMax = false, doRange = false, doSkewness = false, doKurtosis = false, doMembrane = false;
		for (IntensityMeasurements m : measurements) {
			switch (m) {
			case MEAN:
				doMean = true;
				measurementFlags = measurementFlags | Measurements.MEAN;
				break;
			case SUM:
				doSum = true;
				measurementFlags = measurementFlags | Measurements.MEAN;
				break;
			case STD_DEV:
				doStdDev = true;
				measurementFlags = measurementFlags | Measurements.STD_DEV;
				break;
			case COEFFICIENT_OF_VARIATION:
				doCoV = true;
				measurementFlags = measurementFlags | Measurements.STD_DEV | Measurements.MEAN;
				break;
			case MIN:
				doMin = true;
				measurementFlags = measurementFlags | Measurements.MIN_MAX;
				break;
			case MAX:
				doMax = true;
				measurementFlags = measurementFlags | Measurements.MIN_MAX;
				break;
			case RANGE:
				doRange = true;
				measurementFlags = measurementFlags | Measurements.MIN_MAX;
				break;
			case SKEWNESS:
				doSkewness = true;
				measurementFlags = measurementFlags | Measurements.SKEWNESS;
				break;
			case KURTOSIS:
				doKurtosis = true;
				measurementFlags = measurementFlags | Measurements.KURTOSIS;
				break;
			case MEMBRANE:
				doMembrane = true;
				break;
			}
		}
		
//		ImagePlus impTemp = null; 
		for (PathObject pathObject: pathObjects) {
			
			MeasurementList measurementList = pathObject.getMeasurementList();
			
			Roi roi = ROIConverterIJ.convertToIJRoi(pathObject.getROI(), cal, downsampleFactor);
			ip.setRoi(roi);
			ImageStatistics stats; // = ImageStatistics.getStatistics(ip, measurementFlags, cal);
			
//			if (roi.isLine()) {
//				if (impTemp == null) {
//					impTemp = new ImagePlus("Temp", ip);
////					impTemp.setCalibration(cal);
//				}
////				IJ.log("This happens");
////				ImageProcessor ip2 = (new Straightener()).straighten(impTemp, roi, (int)roi.getStrokeWidth());
//				impTemp.setRoi(roi);
//				ImageProcessor ip2 = (new Straightener()).straighten(impTemp, roi, (int)(roi.getStrokeWidth() + .5));
////				IJ.log("Ok so far");
////				new ImagePlus("Again", ip2).show();
//				stats = ImageStatistics.getStatistics(ip2, measurementFlags, cal);
////				IJ.log("I reach this");
//			} else
			stats = ImageStatistics.getStatistics(ip, measurementFlags, cal);

			// SPLIT_CHAR will be a special character used to split; make sure it is not present in the processor name
			ipName = ipName.replace(SPLIT_CHAR, ' ');
			if (doMean)
				measurementList.addMeasurement("Mean"+SPLIT_CHAR+" "+ipName, stats.mean);
			if (doSum)
				measurementList.addMeasurement("Sum"+SPLIT_CHAR+" "+ipName, stats.mean * stats.pixelCount);
			if (doStdDev)
				measurementList.addMeasurement("Std.Dev"+SPLIT_CHAR+" "+ipName, stats.stdDev);
			if (doCoV)
				measurementList.addMeasurement("Coeff.Var"+SPLIT_CHAR+" "+ipName, stats.stdDev / stats.mean);
			if (doMin)
				measurementList.addMeasurement("Min"+SPLIT_CHAR+" "+ipName, stats.min);
			if (doMax)
				measurementList.addMeasurement("Max"+SPLIT_CHAR+" "+ipName, stats.max);
			if (doRange)
				measurementList.addMeasurement("Range"+SPLIT_CHAR+" "+ipName, stats.max - stats.min);
			if (doSkewness)
				measurementList.addMeasurement("Skewness"+SPLIT_CHAR+" "+ipName, stats.skewness);
			if (doKurtosis)
				measurementList.addMeasurement("Kurtosis"+SPLIT_CHAR+" "+ipName, stats.kurtosis);
			if (doMembrane)
				measureMembrane(pathObject, ip, ipName, cal, downsampleFactor);
		}
	}
	
	
	
	public static void measureMembrane(PathObject po, ImageProcessor ip, String ipName, Calibration cal, double downsampleFactor) {
		Roi roi = ROIConverterIJ.convertToIJRoi(po.getROI(), cal, downsampleFactor);
		Rectangle bounds = roi.getBounds();
		ByteProcessor bp = new ByteProcessor(bounds.width, bounds.height);
		roi.setLocation(0, 0);
		bp.setValue(255);
		bp.draw(roi);
		double sum = 0;
		int count = 0;
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (int y = 0; y < bounds.height; y++) {
			for (int x = 0; x < bounds.width; x++) {
				if (bp.get(x, y) != 0) {
					double val = ip.getf(x+bounds.x, y+bounds.y);
					sum += val;
					count++;
					if (val > max)
						max = val;
					if (val < min)
						min = val;
				}
					
			}			
		}
		roi.setLocation(bounds.x, bounds.y);
		po.getMeasurementList().addMeasurement("Membrane mean"+SPLIT_CHAR+" "+ipName, sum/count);
//		po.addMeasurement("Membrane min"+SPLIT_CHAR+" "+ipName, min);
//		po.addMeasurement("Membrane max"+SPLIT_CHAR+" "+ipName, max);
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
			measurementList.addMeasurement(prefix + "Area", shapeStats.area());
		measurementList.addMeasurement(prefix + "Perimeter", shapeStats.perimeter());
		measurementList.addMeasurement(prefix + "Circularity", shapeStats.circularity());
		measurementList.addMeasurement(prefix + "Max caliper", shapeStats.maxCaliper());
		measurementList.addMeasurement(prefix + "Min caliper", shapeStats.minCaliper());
//		measurementList.addMeasurement(prefix + "Major axis", shapeStats.majorAxisLength());
//		measurementList.addMeasurement(prefix + "Minor axis", shapeStats.minorAxisLength());
		measurementList.addMeasurement(prefix + "Eccentricity", shapeStats.eccentricity());
	
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

//	public static void computeShapeStatistics(PathObject pathObject, PathImage<ImagePlus> pathImage, ImageProcessor ip, Calibration cal) {
//		ObjectMeasurements.computeShapeStatistics(pathObject, pathImage, ip, cal, "");
//	}
//
//	public static void computeShapeStatistics(PathObject pathObject, PathImage<ImagePlus> pathImage, ImageProcessor ip, Calibration cal, String prefix) {
//		Roi roi = PathROIConverterIJ.convertToIJRoi(pathObject.getROI(), pathImage);
//		addShapeStatistics(pathObject.getMeasurementList(), roi, ip, cal, prefix);
//	}
	
	
	
	
	public static void addShapeStatistics(MeasurementList measurementList, PolygonROI roi, double pixelWidth, double pixelHeight, String prefix) {
		measurementList.addMeasurement(prefix + "Area", roi.getScaledArea(pixelWidth, pixelHeight));
		measurementList.addMeasurement(prefix + "Perimeter", roi.getScaledPerimeter(pixelWidth, pixelHeight));
		measurementList.addMeasurement(prefix + "Circularity", roi.getCircularity());
//		measurementList.addMeasurement(prefix + "Convex area", roi.getScaledConvexArea(pixelWidth, pixelHeight));
		measurementList.addMeasurement(prefix + "Solidity", roi.getSolidity());
	}
	
	
	
}
