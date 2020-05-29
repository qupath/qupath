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

package qupath.imagej.superpixels;

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.RankFilters;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.tools.IJTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.plugins.AbstractTileableDetectionPlugin;
import qupath.lib.plugins.ObjectDetector;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * A simple superpixel-generating command based upon applying ImageJ's watershed transform to the
 * absolute values of a Difference-of-Gaussians filtered image.
 * <p>
 * This provides tile objects that generally correspond to regions containing reasonably similar 
 * intensities or textures, which might then be classified.
 * 
 * @author Pete Bankhead
 *
 */
public class DoGSuperpixelsPlugin extends AbstractTileableDetectionPlugin<BufferedImage> {

	private static Logger logger = LoggerFactory.getLogger(DoGSuperpixelsPlugin.class);
	
	@Override
	public String getName() {
		return "DoG superpixel creator";
	}

	@Override
	public String getLastResultsDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected double getPreferredPixelSizeMicrons(final ImageData<BufferedImage> imageData, final ParameterList params) {
		double pixelSize = params.getDoubleParameterValue("downsampleFactor");
		PixelCalibration cal = imageData.getServer().getPixelCalibration();
		if (imageData != null && cal.hasPixelSizeMicrons())
			pixelSize *= cal.getAveragedPixelSizeMicrons();
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
			if (!pathROI.equals(this.pathROI) || true) {
				ImageServer<BufferedImage> server = imageData.getServer();
				

				double downsample = params.getDoubleParameterValue("downsampleFactor");
				
				// Create an expanded request (we will clip to the actual ROI later)
				var request = RegionRequest.createInstance(server.getPath(), downsample, pathROI)
						.pad2D((int)Math.ceil(downsample * 2), (int)Math.ceil(downsample * 2))
						.intersect2D(0, 0, server.getWidth(), server.getHeight());
				
				this.pathImage = IJTools.convertToImagePlus(server, request);
				
//				this.pathImage = IJTools.createPathImage(server, pathROI, params.getDoubleParameterValue("downsampleFactor"));
				this.pathROI = pathROI;
			}
			
//			String imageName = "Grayscale";
			
			// Get a float processor
			ImageProcessor ipOrig = this.pathImage.getImage().getProcessor();
			FloatProcessor fp;
//			if (imageData.isBrightfield() && ipOrig instanceof ColorProcessor) {
//				ImageProcessor ipTemp = ColorTransforms.doTransform((ColorProcessor)ipOrig, ColorMethod.Optical_density_sum);
//				imageName = "OD sum";
//				if (ipTemp instanceof FloatProcessor)
//					fp = (FloatProcessor)ipTemp;
//				else
//					fp = ipTemp.convertToFloatProcessor();
//			} else {
				fp = ipOrig.convertToFloatProcessor();
//			}
			
			// Apply DoG filter
			FloatProcessor fpOrig = (FloatProcessor)fp.duplicate();
			FloatProcessor fp2 = (FloatProcessor)fpOrig.duplicate();
			double sigma = getSigma(pathImage, params);
			fp.blurGaussian(sigma);
			fp2.blurGaussian(sigma*1.6);
			fp.copyBits(fp2, 0, 0, Blitter.SUBTRACT);
			
			fp.resetRoi();
			
			// Normalizing to std dev would ensure some data-dependence... which has fors an againsts
//			double stdDev = fp.getStatistics().stdDev;
			
			// Compute absolute value
			fp.abs();
			
//			fp.multiply(1.0/stdDev);
			
			// Apply Find Maxima
			ByteProcessor bp = new MaximumFinder().findMaxima(fp, params.getDoubleParameterValue("noiseThreshold"), MaximumFinder.SEGMENTED, false);
			
			// Dilate to remove outlines
			bp.setThreshold(128, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
			ImageProcessor ipLabels = RoiLabeling.labelImage(bp, 0.5f, false);
			new RankFilters().rank(ipLabels, 1, RankFilters.MAX);
			
			if (Thread.currentThread().isInterrupted())
				return Collections.emptyList();
			
			// Convert to tiles & create a labelled image for later
			PolygonRoi[] polygons = RoiLabeling.labelsToFilledROIs(ipLabels, (int)ipLabels.getMax());
			List<PathObject> pathObjects = new ArrayList<>(polygons.length);
			// Set thresholds - regions means must be within specified range
			double minThreshold = params.getDoubleParameterValue("minThreshold");
			double maxThreshold = params.getDoubleParameterValue("maxThreshold");
			if (!Double.isFinite(minThreshold))
				minThreshold = Double.NEGATIVE_INFINITY;
			if (!Double.isFinite(maxThreshold))
				maxThreshold = Double.POSITIVE_INFINITY;
			boolean hasThreshold = (minThreshold != maxThreshold) && (Double.isFinite(minThreshold) || Double.isFinite(maxThreshold));
			try {
				Collection<ROI> superpixelROIs = new ArrayList<>();
				for (PolygonRoi roi : polygons) {
					if (roi == null)
						continue;
					if (hasThreshold) {
						fpOrig.setRoi(roi);
						double meanValue = fpOrig.getStatistics().mean;
						if (meanValue < minThreshold || meanValue > maxThreshold)
							continue;
					}
					if (Thread.currentThread().isInterrupted())
						return Collections.emptyList();

					superpixelROIs.add(IJTools.convertToROI(roi, pathImage));
				}
				if (pathROI != null)
					superpixelROIs = RoiTools.clipToROI(pathROI, superpixelROIs);
				
				for (var superpixelROI : superpixelROIs) {
					PathObject tile = PathObjects.createTileObject(superpixelROI);
					pathObjects.add(tile);
				}
			} catch (Exception e) {
				logger.error("Error creating superpixels", e);
			}
			fpOrig.resetRoi();
			
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