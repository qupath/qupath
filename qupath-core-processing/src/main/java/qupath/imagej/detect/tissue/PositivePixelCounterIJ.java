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

package qupath.imagej.detect.tissue;

import java.awt.image.BufferedImage;
import java.io.IOException;
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
import qupath.imagej.tools.IJTools;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
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
 * For versions &lt;= v0.1.2 this gave simple measurements that were influenced by the downsample values used.
 * Later versions make calibrated measurements and give more flexibility in terms of output.
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
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, ParameterList params, ROI pathROI) throws IOException {
			// Reset any detected objects
			List< PathObject> pathObjects = new ArrayList<>();
			
			
			// Parse parameters
			double downsample = Math.max(1, params.getIntParameterValue("downsampleFactor"));
			
			double thresholdStain1 = params.getDoubleParameterValue("thresholdStain1");
			double thresholdStain2 = params.getDoubleParameterValue("thresholdStain2");
			double gaussianSigmaMicrons = params.getDoubleParameterValue("gaussianSigmaMicrons");
			
			// Check whether to clear any existing measurements from parent objects
			boolean clearParentMeasurements = Boolean.TRUE.equals(params.getBooleanParameterValue("clearParentMeasurements"));
			boolean appendDetectionParameters = Boolean.TRUE.equals(params.getBooleanParameterValue("appendDetectionParameters"));
			
			// Default to using legacy measurements if no key is present
			boolean useLegacyMeasurements = params.containsKey("legacyMeasurements0.1.2") ? params.getBooleanParameterValue("legacyMeasurements0.1.2") : true;
			if (useLegacyMeasurements) {
				logger.warn("Legacy measurements will be made for compatibility with QuPath v0.1.2 - note that pixel counts will depend on the downsample selected!");
			}
			
			// Derive more useful values
			PixelCalibration cal = imageData.getServer().getPixelCalibration();
			double pixelSize = cal.getAveragedPixelSizeMicrons() * downsample;
			double gaussianSigma = gaussianSigmaMicrons / pixelSize;
			
			// Read the image, if necessary
			RegionRequest request = RegionRequest.createInstance(imageData.getServerPath(), downsample, pathROI);
			PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(imageData.getServer(), request);
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
			
			float[] pxHematoxylin = ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_1, null, stains);
			float[] pxDAB = ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_2, null, stains);
//			float[] pxHematoxylin = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 0, null);
//			float[] pxDAB = ColorDeconvolution.colorDeconvolveRGBArray(rgb, stains, 1, null);
			
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
				Roi roi = IJTools.convertToIJRoi(pathROI, pathImage);
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
			
			boolean hasPixelSizeMicrons = cal.hasPixelSizeMicrons();
			String areaUnits = hasPixelSizeMicrons ? GeneralTools.micrometerSymbol() + "^2" : "px^2";
			double pixelWidth = hasPixelSizeMicrons ? cal.getPixelWidthMicrons() : 1;
			double pixelHeight = hasPixelSizeMicrons ? cal.getPixelHeightMicrons() : 1;
			double areaNegative = 0;
			double areaPositive = 0;
			
			// Create a String to store measurement parameters, if requested
			int maxDP = 3;
			String paramsString = "";
			if (appendDetectionParameters)
				paramsString = String.format(" (d=%s, s=%s, tN=%s, tP=%s)", 
					GeneralTools.formatNumber(downsample, maxDP),
					GeneralTools.formatNumber(gaussianSigmaMicrons, maxDP),
					GeneralTools.formatNumber(thresholdStain1, maxDP),
					GeneralTools.formatNumber(thresholdStain2, maxDP));
						
			
			if (roiStained != null) {
				ROI roiTissue = IJTools.convertToROI(roiStained, pathImage);
				PathObject pathObject = PathObjects.createDetectionObject(roiTissue);
				PathClass pathClass = null;
				if (useLegacyMeasurements) {
					pathObject.getMeasurementList().addMeasurement("Num pixels", nNegative);
					pathObject.getMeasurementList().addMeasurement("Mean hematoxylin OD", meanNegative);
					pathClass = PathClassFactory.getNegative(null);
				} else {
					areaNegative = roiTissue.getScaledArea(pixelWidth, pixelHeight);
					pathObject.getMeasurementList().addMeasurement("Stained area " + areaUnits + paramsString, areaNegative);
					pathObject.getMeasurementList().addMeasurement("Mean " + stains.getStain(1).getName() + " OD" + paramsString, meanNegative);
					pathClass = PathClassFactory.getPathClass("Pixel count negative", ColorTools.makeScaledRGB(PathClassFactory.getNegative(null).getColor(), 1.25));
				}
				pathObject.setPathClass(pathClass);
				pathObject.getMeasurementList().close();
				pathObjects.add(pathObject);
			}
			if (roiDAB != null) {
				ROI roiPositive = IJTools.convertToROI(roiDAB, pathImage);
//				roiDAB = ShapeSimplifierAwt.simplifyShape(roiDAB, simplifyAmount);
				PathClass pathClass = null;
				PathObject pathObject = PathObjects.createDetectionObject(roiPositive);
				if (useLegacyMeasurements) {
					pathObject.getMeasurementList().addMeasurement("Num pixels", nPositive);
					pathObject.getMeasurementList().addMeasurement("Mean DAB OD", meanPositive);
					pathClass = PathClassFactory.getPositive(null);
				} else {
					areaPositive = roiPositive.getScaledArea(pixelWidth, pixelHeight);
					pathObject.getMeasurementList().addMeasurement("Stained area " + areaUnits + paramsString, areaPositive);
					pathObject.getMeasurementList().addMeasurement("Mean " + stains.getStain(2).getName() + " OD" + paramsString, meanPositive);
					pathClass = PathClassFactory.getPathClass("Pixel count positive", ColorTools.makeScaledRGB(PathClassFactory.getPositive(null).getColor(), 1.25));
				}
				pathObject.setPathClass(pathClass);
				pathObject.getMeasurementList().close();
				pathObjects.add(pathObject);
			}
			
			boolean addMeasurements = params.getBooleanParameterValue("addSummaryMeasurements");
			double positivePercentage = nPositive * 100.0 / (nPositive + nNegative);
						
			if (clearParentMeasurements && !parent.getMeasurementList().isEmpty()) {
				parent.getMeasurementList().clear();				
				parent.getMeasurementList().close();			
			}
			
			if (addMeasurements) {
				if (useLegacyMeasurements) {
					parent.getMeasurementList().putMeasurement("Positive pixel %", positivePercentage);
					parent.getMeasurementList().putMeasurement("Positive pixel count", nPositive);
					parent.getMeasurementList().putMeasurement("Negative pixel count", nNegative);
					parent.getMeasurementList().putMeasurement("Mean positive DAB staining OD", meanPositive);
					parent.getMeasurementList().putMeasurement("Stained pixel count", nPositive + nNegative);
					parent.getMeasurementList().close();
				} else {
					parent.getMeasurementList().putMeasurement("Positive % of stained pixels" + paramsString, positivePercentage);
					parent.getMeasurementList().putMeasurement("Positive pixel area " + areaUnits + paramsString, areaPositive);
					parent.getMeasurementList().putMeasurement("Negative pixel area " + areaUnits + paramsString, areaNegative);
					parent.getMeasurementList().putMeasurement("Stained area (Positive + Negative)" + areaUnits + paramsString, areaPositive + areaNegative);					
					//					parent.getMeasurementList().putMeasurement("Positive pixel count (full image)", nPositive * downsample * downsample);
//					parent.getMeasurementList().putMeasurement("Negative pixel count (full image)", nNegative * downsample * downsample);
//					// Uncalibrated counts really only might be useful for a sanity check
//					parent.getMeasurementList().putMeasurement(String.format("Uncalibrated positive pixel count" + paramsString, GeneralTools.formatNumber(downsample, 4)), nPositive);
//					parent.getMeasurementList().putMeasurement(String.format("Uncalibrated negative pixel count" + paramsString, GeneralTools.formatNumber(downsample, 4)), nNegative);
//					parent.getMeasurementList().putMeasurement("Mean positive " + stains.getStain(2).getName() + " staining OD", meanPositive);
					
					// If we can, add measurements relative to the parent ROI
					ROI roiParent = parent.getROI();
					boolean roisMatch = roiParent == pathROI;
					if (!roisMatch) {
						logger.warn("Unexpected mismatch between parent ROI & analysis ROI! No measurements based on ROI area will be added.");
					}
					if (roisMatch && roiParent.isArea()) {
						// Clip to 100% (could conceivably go slightly above because of sub-pixel errors)
						double areaROI = roiParent.getScaledArea(pixelWidth, pixelHeight);
						parent.getMeasurementList().putMeasurement("Total ROI area " + areaUnits + paramsString, areaROI);					
//						parent.getMeasurementList().putMeasurement("Stained % of total ROI area" + paramsString, Math.min(100, (areaPositive + areaNegative) / areaROI * 100.0));					
						parent.getMeasurementList().putMeasurement("Positive % of total ROI area" + paramsString, Math.min(100, areaPositive / areaROI * 100.0));
					}
					
					parent.getMeasurementList().close();					
				}
			}
			
			lastMessage = String.format("Stained positive percentage: %.2f%%", positivePercentage);
			
			return pathObjects;
		}
		
		
		
		@Override
		public String getLastResultsDescription() {
			return lastMessage;
		}

		
	}
	
	
	
	

	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
		String stain1Name = stains == null ? "Hematoxylin" : stains.getStain(1).getName();
		String stain2Name = stains == null ? "DAB" : stains.getStain(2).getName();
		ParameterList params = new ParameterList()
				.addIntParameter("downsampleFactor", "Downsample factor", 4, "", 1, 32, "Amount to downsample image prior to detection - higher values lead to smaller images (and faster but less accurate processing)")
				.addDoubleParameter("gaussianSigmaMicrons", "Gaussian sigma", 2, GeneralTools.micrometerSymbol(), "Gaussian filter size - higher values give a smoother (less-detailed) result")
				.addDoubleParameter("thresholdStain1", stain1Name + " threshold ('Negative')", 0.1, "OD units", "Threshold to use for 'Negative' detection")
				.addDoubleParameter("thresholdStain2", stain2Name + " threshold ('Positive')", 0.3, "OD units", "Threshold to use for 'Positive' stain detection")
				.addBooleanParameter("addSummaryMeasurements", "Add summary measurements to parent", true, "Add summary measurements to parent objects")
				.addBooleanParameter("clearParentMeasurements", "Clear existing parent measurements", true, "Remove any existing measurements from parent objects")
				.addBooleanParameter("appendDetectionParameters", "Add parameters to measurement names", false, "Append the detection parameters to any measurement names")
				.addBooleanParameter("legacyMeasurements0.1.2", "Use legacy measurements (v0.1.2)", false, "Generate measurements compatible with QuPath v0.1.2");	
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