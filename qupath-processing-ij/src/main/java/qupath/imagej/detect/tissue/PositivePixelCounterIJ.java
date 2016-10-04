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

package qupath.imagej.detect.tissue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.imagej.objects.PathImagePlus;
import qupath.imagej.objects.ROIConverterIJ;
import qupath.lib.color.ColorDeconvolution;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.plugins.AbstractDetectionPlugin;
import qupath.lib.plugins.DetectionPluginTools;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * Simple command to detect regions with positive staining.
 * 
 * @author Pete Bankhead
 *
 */
public class PositivePixelCounterIJ extends AbstractDetectionPlugin<BufferedImage> {
	
	private final static Logger logger = LoggerFactory.getLogger(PositivePixelCounterIJ.class);
	
	transient private PositivePixelDetector detector;
	
	
	static class PositivePixelDetector implements ObjectDetector<BufferedImage> {
		
		private final PathObject parent;
		private String lastMessage = null;
		
		PositivePixelDetector(final PathObject parent) {
			this.parent = parent;
		}
	
		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) {
			// Reset any detected objects
			List< PathObject> pathObjects = new ArrayList<>();
			
			
			// Parse parameters
			double downsample = Math.max(1, params.getIntParameterValue("downsampleFactor"));
			
			double thresholdStain1 = params.getDoubleParameterValue("thresholdStain1");
			double thresholdStain2 = params.getDoubleParameterValue("thresholdStain2");
			double gaussianSigmaMicrons = params.getDoubleParameterValue("gaussianSigmaMicrons");
			
			// Derive more useful values
			double pixelSize = imageData.getServer().getAveragedPixelSizeMicrons() * downsample;
			double gaussianSigma = gaussianSigmaMicrons / pixelSize;
			
			// Read the image, if necessary
			RegionRequest request = RegionRequest.createInstance(imageData.getServerPath(), downsample, pathROI);
			PathImage<ImagePlus> pathImage = PathImagePlus.createPathImage(imageData.getServer(), request);
			ImagePlus imp = pathImage.getImage();
			
			int w = imp.getWidth();
			int h = imp.getHeight();
			
			// Extract the color deconvolved channels
			// TODO: Support alternative stain vectors
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
//			boolean isH_DAB = stains.isH_DAB() && imp.getType() == ImagePlus.COLOR_RGB;
			boolean isRGB = stains != null && imp.getType() == ImagePlus.COLOR_RGB;
			if (!isRGB) {
				logger.error("Only brightfield RGB images are supported!");
				return Collections.emptyList();
			}
			ColorProcessor cp = (ColorProcessor)imp.getProcessor();
			int[] rgb = (int[])cp.getPixels();
			
			float[] pxHematoxylin = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 0, null);
			float[] pxDAB = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 1, null);
			
			// Create images
			FloatProcessor fpHematoxylin = new FloatProcessor(w, h, pxHematoxylin);
			FloatProcessor fpDAB = new FloatProcessor(w, h, pxDAB);
					
			// Apply Gaussian filter
			fpHematoxylin.blurGaussian(gaussianSigma);
			fpDAB.blurGaussian(gaussianSigma);
			
			// Threshold
			ByteProcessor bpH = new ByteProcessor(w, h);
			ByteProcessor bpDAB = new ByteProcessor(w, h);
			// Apply mask, if necessary
			if (pathROI != null && !(pathROI instanceof RectangleROI)) {
				bpH.set(1);
				Roi roi = ROIConverterIJ.convertToIJRoi(pathROI, pathImage);
				bpH.setValue(0);
				bpH.fill(roi);
			}
			int nNegative = 0;
			int nPositive = 0;
			double sumPositive = 0;
			double sumNegative = 0;
			for (int i = 0; i < w*h; i++) {
				// Check mask
				if (bpH.get(i) != (byte)0)
					continue;
				float valH = fpHematoxylin.getf(i);
				float valDAB = fpDAB.getf(i);
				if (valDAB >= thresholdStain2) {
//					if (valDAB > valH) {
						bpDAB.set(i, (byte)255);
						sumPositive += valDAB;
						nPositive++;
//					} else {
//						bpH.set(i, (byte)255);
//						nStained++;
//					}
				} else if (valH >= thresholdStain1) {
					bpH.set(i, (byte)255);
					sumNegative += valH;
					nNegative++;
				}
			}
			
			bpH.setThreshold(128, Double.MAX_VALUE, ImageProcessor.NO_LUT_UPDATE);
			bpDAB.setThreshold(128, Double.MAX_VALUE, ImageProcessor.NO_LUT_UPDATE);
			Roi roiStained = nNegative > 0 ? new ThresholdToSelection().convert(bpH) : null;
			Roi roiDAB = nPositive > 0 ? new ThresholdToSelection().convert(bpDAB) : null;
			
			double meanPositive = nPositive == 0 ? Double.NaN : sumPositive / nPositive;
			double meanNegative = nNegative == 0 ? Double.NaN : sumNegative / nNegative;
			
			if (roiStained != null) {
				ROI roiTissue = ROIConverterIJ.convertToPathROI(roiStained, pathImage);
				PathObject pathObject = new PathDetectionObject(roiTissue, PathClassFactory.getNegative(null, PathClassFactory.COLOR_NEGATIVE));
				pathObject.getMeasurementList().addMeasurement("Num pixels", nNegative);
				pathObject.getMeasurementList().addMeasurement("Mean hematoxylin OD", meanNegative);
				pathObjects.add(pathObject);
			}
			if (roiDAB != null) {
				ROI roiPositive = ROIConverterIJ.convertToPathROI(roiDAB, pathImage);
//				roiDAB = ShapeSimplifierAwt.simplifyShape(roiDAB, simplifyAmount);
				PathObject pathObject = new PathDetectionObject(roiPositive, PathClassFactory.getPositive(null, PathClassFactory.COLOR_POSITIVE));
				pathObject.getMeasurementList().addMeasurement("Num pixels", nPositive);
				pathObject.getMeasurementList().addMeasurement("Mean DAB OD", meanPositive);
				pathObjects.get(0).addPathObject(pathObject);
			}
			
			boolean addMeasurements = params.getBooleanParameterValue("addSummaryMeasurements");
			double positivePercentage = nPositive * 100.0 / (nPositive + nNegative);
			if (addMeasurements) {
				parent.getMeasurementList().putMeasurement("Positive pixel %", positivePercentage);
				parent.getMeasurementList().putMeasurement("Positive pixel count", nPositive);
				parent.getMeasurementList().putMeasurement("Negative pixel count", nNegative);
				parent.getMeasurementList().putMeasurement("Mean positive DAB staining OD", meanPositive);
				parent.getMeasurementList().putMeasurement("Stained pixel count", nPositive + nNegative);
				parent.getMeasurementList().closeList();
			}
			
			lastMessage = String.format("Positive percentage: %.2f%%", positivePercentage);
			
			return pathObjects;
		}
		
		
		
		@Override
		public String getLastResultsDescription() {
			return lastMessage;
		}

		
	}
	
	
	
	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		ParameterList params = new ParameterList()
				.addIntParameter("downsampleFactor", "Downsample factor", 4, "", 1, 32, "Amount to downsample image prior to detection - higher values lead to smaller images (and faster but less accurate processing)")
				.addDoubleParameter("gaussianSigmaMicrons", "Gaussian sigma", 2, GeneralTools.micrometerSymbol(), "Gaussian filter size - higher values give a smoother (less-detailed) result")
				.addDoubleParameter("thresholdStain1", "Hematoxylin threshold", 0.1, "OD units", "Threshold to use for hemtaoxylin detection")
				.addDoubleParameter("thresholdStain2", "DAB threshold", 0.3, "OD units", "Threshold to use for DAB stain detection")
				.addBooleanParameter("addSummaryMeasurements", "Add summary measurements", true, "Add summary measurements to parent objects");
		
//		double thresholdStain1 = 0.1;
//		double thresholdStain2 = 0.1;
//		double gaussianSigmaMicrons = 5;
//		int separationRadius = 1;

		// TODO: Support parameters properly!
//		
//		if (imageData.getServer().hasPixelSizeMicrons()) {
//			String um = GeneralTools.micrometerSymbol();
//			params.addDoubleParameter("medianRadius", "Median radius", 1, um).
//				addDoubleParameter("gaussianSigma", "Gaussian sigma", 1.5, um).
//				addDoubleParameter("openingRadius", "Opening radius", 8, um).
//				addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 1.0).
//				addDoubleParameter("minArea", "Minimum area", 25, um+"^2");
//		} else {
//			params.addDoubleParameter("medianRadius", "Median radius", 1, "px").
//					addDoubleParameter("gaussianSigma", "Gaussian sigma", 2, "px").
//					addDoubleParameter("openingRadius", "Opening radius", 20, "px").
//					addDoubleParameter("threshold", "Threshold", 0.1, null, 0, 1.0).
//					addDoubleParameter("minArea", "Minimum area", 100, "px^2");
//		}
//		params.addBooleanParameter("splitShape", "Split by shape", true);			
		return params;
	}

	@Override
	public String getName() {
		return "Detect positive staining (TMA, IHC)";
	}
	
	@Override
	public String getDescription() {
		return "Determine positive pixel counts in an H-DAB stained image";
	}

	@Override
	public String getLastResultsDescription() {
		return detector == null ? "" : detector.getLastResultsDescription();
	}
	
	@Override
	protected void addRunnableTasks(ImageData<BufferedImage> imageData, PathObject parentObject, List<Runnable> tasks) {
//		if (detector == null)
			detector = new PositivePixelDetector(parentObject);
		tasks.add(DetectionPluginTools.createRunnableTask(detector, getParameterList(imageData), imageData, parentObject));
	}

	
}