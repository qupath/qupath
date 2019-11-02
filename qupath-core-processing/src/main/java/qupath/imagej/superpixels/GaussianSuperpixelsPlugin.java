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

package qupath.imagej.superpixels;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.EDM;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;

import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.processing.SimpleThresholding;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

/**
 * A simple superpixel-generating command.
 * 
 * It's entirely experimental at the moment, and not accessible from the main GUI...
 * 
 * @author Pete Bankhead
 *
 */
public class GaussianSuperpixelsPlugin extends AbstractTileableDetectionPlugin<BufferedImage> {

	@Override
	public String getName() {
		return "Gaussian-based superpixels";
	}

	@Override
	public String getLastResultsDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected double getPreferredPixelSizeMicrons(final ImageData<BufferedImage> imageData, final ParameterList params) {
		double pixelSize = params.getDoubleParameterValue("downsampleFactor");
		if (imageData != null && imageData.getServer().getPixelCalibration().hasPixelSizeMicrons())
			pixelSize *= imageData.getServer().getPixelCalibration().getAveragedPixelSizeMicrons();
		return pixelSize;
	}

	@Override
	protected ObjectDetector<BufferedImage> createDetector(final ImageData<BufferedImage> imageData, final ParameterList params) {
		return new DoGSuperpixelDetector();
	}

	@Override
	protected int getTileOverlap(ImageData<BufferedImage> imageData, ParameterList params) {
		return 0;
	}

	@Override
	public ParameterList getDefaultParameterList(ImageData<BufferedImage> imageData) {
		ParameterList params = new ParameterList().
				addDoubleParameter("downsampleFactor", "Downsample factor", 8, null, "Downsample factor, used to determine the resolution of the image being processed").
				addDoubleParameter("sigmaPixels", "Gaussian sigma", 10, "px", "Sigma value used for smoothing; higher values result in larger regions being created").
				addDoubleParameter("sigmaMicrons", "Gaussian sigma", 10, GeneralTools.micrometerSymbol(), "Sigma value used for smoothing; higher values result in larger regions being created").
				addDoubleParameter("minThreshold", "Minimum intensity threshold", 10, null, "Regions with average values below this threshold will be discarded; this helps remove background or artefacts").
				addDoubleParameter("maxThreshold", "Maximum intensity threshold", 230, null, "Regions with average values above this threshold will be discarded; this helps remove background or artefacts").
				addDoubleParameter("noiseThreshold", "Noise threshold", 1, null, "Local threshold used to determine the number of regions created")
				;
		
		boolean hasMicrons = imageData != null && imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		params.getParameters().get("sigmaPixels").setHidden(hasMicrons);
		params.getParameters().get("sigmaMicrons").setHidden(!hasMicrons);
		
		return params;
	}
	
	
	
	static class DoGSuperpixelDetector implements ObjectDetector<BufferedImage> {
		
		private static Logger logger = org.slf4j.LoggerFactory.getLogger(DoGSuperpixelDetector.class);
		
		private PathImage<ImagePlus> pathImage = null;
		private ROI pathROI = null;
		
		private String lastResultSummary = null;

		@Override
		public Collection<PathObject> runDetection(final ImageData<BufferedImage> imageData, final ParameterList params, final ROI pathROI) throws IOException {
			
			// TODO: Give a sensible error
			if (pathROI == null) {
				lastResultSummary = "No ROI selected!";
				return null;
			}
			// Get a PathImage if we have a new ROI
			if (!pathROI.equals(this.pathROI)) {
				ImageServer<BufferedImage> server = imageData.getServer();
				this.pathImage = IJTools.convertToImagePlus(server, RegionRequest.createInstance(server.getPath(), params.getDoubleParameterValue("downsampleFactor"), pathROI));
				this.pathROI = pathROI;
			}
			
//			String imageName = "Grayscale";
			
			// Get a float processor
			ImageProcessor ipOrig = this.pathImage.getImage().getProcessor();
			
			int w = ipOrig.getWidth();
			int h = ipOrig.getHeight();
			
//			For each channel
//				Threshold channel
//				Smooth thresholded image
//				Apply minimum area threshold
//				Create outline
//			Create merged outline image
//			Mask by tissue and ROI
//			Reapply minimum area threshold
//			Apply watershed separation to generate regions
//			Label image
//			Dilate 3x3
//			Convert to ROIs
			

			double sigma = getSigma(pathImage, params);
			
			float tissueThreshold = 0.04f;
			double minAreaMicrons = 2000;
			double split = 20;
//			float[] threshold = new float[]{0.08f, 0.25f};
			float[] threshold = new float[]{0.2f, -1f};
			
			// Convert to pixels
			double pixelSize = pathImage.getPixelCalibration().getAveragedPixelSizeMicrons();
			double minArea = minAreaMicrons / (pixelSize * pixelSize);
			split = split / pixelSize;
//			System.err.println(minArea);
			
			// Detect tissue
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			FloatProcessor fpODSum = IJTools.convertToOpticalDensitySum((ColorProcessor)ipOrig, stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
			fpODSum.blurGaussian(sigma);
			ByteProcessor bpTissue = SimpleThresholding.thresholdAbove(fpODSum, tissueThreshold);
			RoiLabeling.removeSmallAreas(bpTissue, minArea, false);
			bpTissue.invert();
			RoiLabeling.removeSmallAreas(bpTissue, minArea, false);
			bpTissue.invert();
			
			// Create tissue ROI
			bpTissue.setThreshold(128, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
//			Roi roiTissue = new ThresholdToSelection().convert(bpTissue);
			
			float[] pixels = ColorTransformer.getTransformedPixels((int[])ipOrig.getPixels(), ColorTransformMethod.Stain_1, null, stains);
//			float[] pixels = ColorDeconvolution.colorDeconvolveRGBArray((int[])ipOrig.getPixels(), imageData.getColorDeconvolutionStains(), 0, null);
			FloatProcessor fp = new FloatProcessor(w, h, pixels);
			ByteProcessor bp = SimpleThresholding.thresholdAbove(fp, threshold[0]);
//			new ImagePlus("this", bp.duplicate()).show();

			bp.blurGaussian(sigma);
//			new ImagePlus("Here", bp.duplicate()).show();
			bp.copyBits(bpTissue, 0, 0, Blitter.MIN);
			bp.setThreshold(1, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			bp = new MaximumFinder().findMaxima(bp, 5, 1, MaximumFinder.SEGMENTED, false, false);
//			bp.threshold(128);

//			// Detect clusters in each channel
//			ByteProcessor bp = (ByteProcessor)bpTissue.duplicate();
//			for (int i = 0; i < 2; i++) {
//				float channelThreshold = threshold[i];
//				if (channelThreshold < 0)
//					continue;
//				float[] pixels = ColorDeconvolution.colorDeconvolveRGBArray((int[])ipOrig.getPixels(), imageData.getColorDeconvolutionStains(), i, null);
//				FloatProcessor fp = new FloatProcessor(w, h, pixels);
//				
//				FloatProcessor fp2 = (FloatProcessor)fp.duplicate();
//				fp2.blurGaussian(sigma*10);
//				fp.copyBits(fp2, 0, 0, Blitter.SUBTRACT);
////				fp.blurGaussian(sigma);
////				new ImagePlus("Subtracted", fp.duplicate()).show();
//
////				FloatProcessor fp = ColorDeconvolutionIJ.convertToOpticalDensitySum((ColorProcessor)ipOrig, stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
//				
//				fp.setRoi(roiTissue);
////				threshold = (float)fp.getStatistics().mean;
//				fp.resetRoi();
//				ByteProcessor bpChannel = SimpleThresholding.thresholdAbove(fp, channelThreshold);
//				bpChannel.blurGaussian(sigma);
//				bpChannel.threshold(128);
//				
//				ROILabeling.removeSmallAreas(bpChannel, minArea, false);
//				bpChannel.invert();
//				ROILabeling.removeSmallAreas(bpChannel, minArea, false);
//				bpChannel.invert();
//				
//				bpChannel.outline();
////				new ImagePlus("Outline", bpChannel.duplicate()).show();
//				
//				bp.copyBits(bpChannel, 0, 0, Blitter.MIN);
//			}
			
//			ROILabeling.removeSmallAreas(bp, minArea, false);
//			bp.invert();
//			ROILabeling.removeSmallAreas(bp, minArea, false);
//			bp.invert();
//			new ImagePlus("Here", bp.duplicate()).show();

			FloatProcessor fpEDM = new EDM().makeFloatEDM(bp, 0, true);
			fpEDM.max(split);
			bp = new MaximumFinder().findMaxima(fpEDM, Math.sqrt(2), 0.5, MaximumFinder.SEGMENTED, false, false);
			
//			bp.copyBits(bpTissue, 0, 0, Blitter.MIN);
			
//			new EDM().toWatershed(bp);
			
//			ROILabeling.removeSmallAreas(bp, minArea, false);
//			new ImagePlus("Before", bp.duplicate()).show();
//			bp.invert();
//			ROILabeling.removeSmallAreas(bp, minArea, false);
//			bp.invert();
//			new ImagePlus("After", bp.duplicate()).show();

			// Remove everything outside the ROI, if required
			if (pathROI != null) {
				Roi roi = IJTools.convertToIJRoi(pathROI, pathImage);
				RoiLabeling.clearOutside(bp, roi);
				// It's important to move away from the containing ROI, to help with brush selections ending up
				// having the correct parent (i.e. don't want to risk moving slightly outside the parent object's ROI)
				bp.setValue(0);
				bp.setLineWidth(2);
				bp.draw(roi);
			}
			
			// Dilate to remove outlines
			bp.setThreshold(128, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			ImageProcessor ipLabels = RoiLabeling.labelImage(bp, 0.5f, false);
			new RankFilters().rank(ipLabels, 1, RankFilters.MAX);
			
//			new ImagePlus("Output", ipLabels.duplicate()).show();

			
			// Convert to tiles & create a labelled image for later
			Roi[] polygons = RoiLabeling.labelsToConnectedROIs(ipLabels, (int)ipLabels.getMax());
			List<PathObject> pathObjects = new ArrayList<>(polygons.length);
			int label = 0;
			// Set thresholds - regions means must be within specified range
			double minThreshold = params.getDoubleParameterValue("minThreshold");
			double maxThreshold = params.getDoubleParameterValue("maxThreshold");
			if (!Double.isFinite(minThreshold))
				minThreshold = Double.NEGATIVE_INFINITY;
			if (!Double.isFinite(maxThreshold))
				maxThreshold = Double.POSITIVE_INFINITY;
//			boolean hasThreshold = (minThreshold != maxThreshold) && (Double.isFinite(minThreshold) || Double.isFinite(maxThreshold));
			try {
				for (Roi roi : polygons) {
					if (roi == null)
						continue;
//					if (hasThreshold) {
//						fpOrig.setRoi(roi);
//						double meanValue = fpOrig.getStatistics().mean;
//						if (meanValue < minThreshold || meanValue > maxThreshold)
//							continue;
//					}
					ROI superpixelROI = IJTools.convertToROI(roi, pathImage);
					if (pathROI == null)
						continue;
					PathObject tile = PathObjects.createTileObject(superpixelROI);
					pathObjects.add(tile);
					label++;
					ipLabels.setValue(label);
					ipLabels.fill(roi);
				}
			} catch (Exception e) {
				logger.error("Error calculating superpixels", e);
			}
			
			
			lastResultSummary = pathObjects.size() + " tiles created";
			
			return pathObjects;
		}
		
		
		static double getSigma(final PathImage<?> pathImage, final ParameterList params) {
			double pixelSizeMicrons = pathImage.getPixelCalibration().getAveragedPixelSizeMicrons();
			if (Double.isNaN(pixelSizeMicrons)) {
				return params.getDoubleParameterValue("sigmaPixels") * params.getDoubleParameterValue("downsampleFactor");				
			} else
				return params.getDoubleParameterValue("sigmaMicrons") / pixelSizeMicrons;
		}
		

		@Override
		public String getLastResultsDescription() {
			return lastResultSummary;
		}
		
	}
	
	
	/**
	 * Get statistics computed from an image, including only pixels in the range mean +/- k*stdDev, 
	 * where the estimate may be refined through multiple iterations.
	 * 
	 * The goal is to aim for the statistics of the background distribution of pixels, to help 
	 * with setting a threshold to detect bright/dark regions.
	 * 
	 * @param ip
	 * @param k
	 * @param iterations
	 * @return
	 */
	public static RunningStatistics getClippedStatistics(final ImageProcessor ip, final double k, final int iterations) {
		RunningStatistics stats = null;
		for (int i = 1; i < iterations; i++) {
			double mean = 0;
			double stdDev = Double.POSITIVE_INFINITY;
			if (stats != null) {
				mean = stats.getMean();
				stdDev = stats.getStdDev();
			}
			stats = new RunningStatistics();
			for (int p = 0; p < ip.getWidth()*ip.getHeight(); p++) {
				double val = ip.getf(p);
//				if (val - mean) < k*stdDev)
				if (Math.abs(val - mean) < k*stdDev)
					stats.addValue(val);
			}
		}
		return stats;
	}
	
	
	
	@Override
	public String getDescription() {
		return "Partition image into tiled regions of irregular shapes, using intensity & boundary information";
	}
	
	
	@Override
	protected synchronized Collection<? extends PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		Collection<? extends PathObject> parents = super.getParentObjects(runner);
		return parents;
		
		// Exploring the use of hidden objects...
//		PathObject pathObjectHidden = new PathTileObject();
//		for (PathObject parent : parents) {
//			pathObjectHidden.addPathObject(new PathTileObject(parent.getROI()));
//		}
//		imageData.getHierarchy().getRootObject().addPathObject(pathObjectHidden);
//		return pathObjectHidden.getPathObjectList();
	}
	

}