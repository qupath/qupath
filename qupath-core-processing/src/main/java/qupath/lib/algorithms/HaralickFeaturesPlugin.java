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

package qupath.lib.algorithms;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.features.HaralickFeatureComputer;
import qupath.lib.analysis.features.HaralickFeatures;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.analysis.images.SimpleModifiableImage;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.analysis.stats.StatisticsHelper;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

/**
 * Plugin for calculating Haralick texture features, within or around detections or tiles.
 * <p>
 * The ROIs of the detections can be used directly as masks, or else the textures can alternatively be 
 * calculated within square or circular regions around the object centroids.
 * This latter option makes it possible to calculate a high density of tiles (for example), and then to 
 * compute textures at different resolutions independently of the tile size.
 * 
 * @author Pete Bankhead
 *
 */
public class HaralickFeaturesPlugin extends AbstractInteractivePlugin<BufferedImage> {
	
	private final static Logger logger = LoggerFactory.getLogger(HaralickFeaturesPlugin.class);
	
	private ParameterList params;
	
	/**
	 * Default constructor.
	 */
	public HaralickFeaturesPlugin() {
		params = new ParameterList().
				addDoubleParameter("downsample", "Downsample", 1, null, "Amount to downsample the image before calculating textures; choose 1 to use full resolution, or a higher value to use a smaller image").
				addDoubleParameter("magnification", "Magnification", 5, null, "Magnification factor of the image used to calculate the textures").
				addDoubleParameter("pixelSizeMicrons", "Preferred pixel size", 2, GeneralTools.micrometerSymbol(), "Preferred pixel size of the image used to calculate the tetures - higher values means coarser (lower resolution) images").
				addChoiceParameter("stainChoice", "Color transforms", "Optical density", Arrays.asList("Optical density", "H-DAB", "H&E", "H-DAB (8-bit)", "H&E (8-bit)", "RGB OD", "RGB", "Grayscale", "HSB"), "Color transforms to apply before calculating textures");
		
		params.addDoubleParameter("tileSizeMicrons", "Tile diameter", 25, GeneralTools.micrometerSymbol(), "Diameter of square tile around the object centroid used to calculate textures.\nIf <= 0, the tile itself will be used to defined the ROI in which textures are calculated");
		params.addDoubleParameter("tileSizePx", "Tile diameter", 200, "px (full resolution image)", "Diameter of square tile around the object centroid used to calculate textures.\nIf <= 0, the tile itself will be used to defined the ROI in which textures are calculated");

		params.addBooleanParameter("includeStats", "Include basic statistics", true, "Include basic statistics (mean, min, max, std dev) as well as Haralick textures").
				addBooleanParameter("doCircular", "Use circular tiles", false, "If the tile diameter > 0, calculate textures in a circular (rather than square) region around the centroid of each object").
				addBooleanParameter("useNucleusROIs", "Use cell nucleus ROIs", true, "If textures are computed around cell objects, the nucleus ROI is used where available").
				addIntParameter("haralickDistance", "Haralick distance", 1, null, "Spacing between pixels used in computing the co-occurrence matrix for Haralick textures (default = 1)").
				addIntParameter("haralickBins", "Haralick number of bins", 32, null, 8, 256, "Number of intensity bins to use when computing the co-occurrence matrix for Haralick textures (default = 32)");
	}
	
	
	
	static ImmutableDimension getPreferredTileSizePixels(final ImageServer<BufferedImage> server, final ParameterList params) {
		// Determine tile size
		int tileWidth, tileHeight;
		PixelCalibration cal = server.getPixelCalibration();
		if (cal.hasPixelSizeMicrons()) {
			double tileSize = params.getDoubleParameterValue("tileSizeMicrons");
			tileWidth = (int)(tileSize / cal.getPixelWidthMicrons() + .5);
			tileHeight = (int)(tileSize / cal.getPixelHeightMicrons() + .5);
		} else {
			tileWidth = (int)(params.getDoubleParameterValue("tileSizePx") + .5);
			tileHeight = tileWidth;
		}
		return ImmutableDimension.getInstance(tileWidth, tileHeight);
	}
	
	static String getDiameterString(final ImageServer<BufferedImage> server, final ParameterList params) {
		if (server.getPixelCalibration().hasPixelSizeMicrons())
			return String.format("%.1f %s", params.getDoubleParameterValue("tileSizeMicrons"), GeneralTools.micrometerSymbol());
		else
			return String.format("%d px", (int)(params.getDoubleParameterValue("tileSizePx") + .5));
	}
	
	
	@Override
	protected void addRunnableTasks(final ImageData<BufferedImage> imageData, final PathObject parentObject, List<Runnable> tasks) {
		final ParameterList params = getParameterList(imageData);
		final ImageServer<BufferedImage> server = imageData.getServer();
		tasks.add(new HaralickRunnable(server, parentObject, params, imageData.getColorDeconvolutionStains()));
	}
	
	
//	@Override
//	protected Collection<Runnable> getTasks(final PluginRunner<BufferedImage> runner) {
//		Collection<Runnable> tasks = super.getTasks(runner);
//		// If we have a region store, it can be preferable to shuffle the tasks for performance.
//		// This is because regions larger than the requested tile size will be cached,
//		// so threads waiting for adjacent tiles can both block waiting for the same image -
//		// causing fetching regions to become a bottleneck.
//		// By shuffling tiles, all the threads put in requests for different requests at the start
//		// (which is slow), but as the image is processed then increasingly the required regions are
//		// already in the cache when they are needed - causing a dramatic speedup during runtime.
//		// Overall throughput should be improved, since the time spend blocked is minimized.
//		// *However* this is only likely to work if the cache is sufficiently big... otherwise
//		// a slowdown is possible, due to adjacent regions needing to be requested multiple times
//		// because the cache has been emptied in the interim.
////		if (regionStore != null & Runtime.getRuntime().totalMemory() >= 1024L*1024L*1024L*4L) {
////			if (!(tasks instanceof List))
////				tasks = new ArrayList<>(tasks);
////			Collections.shuffle((List<?>)tasks);
//			
//		return tasks;
//	}
	
	
	static class HaralickRunnable implements Runnable {
		
		private ImageServer<BufferedImage> server;
		private ParameterList params;
		private PathObject parentObject;
		private ColorDeconvolutionStains stains;
		
		public HaralickRunnable(final ImageServer<BufferedImage> server, final PathObject parentObject, final ParameterList params, final ColorDeconvolutionStains stains) {
			this.server = server;
			this.parentObject = parentObject;
			this.params = params;
			this.stains = stains;
		}

		@Override
		public void run() {
			try {
				processObject(parentObject, params, server, stains);
			} catch (IOException e) {
				logger.error("Unable to process " + parentObject, e);
			} finally {
				parentObject.getMeasurementList().close();
				server = null;
				params = null;
			}
		}
		
		
		@Override
		public String toString() {
			// TODO: Give a better toString()
			return "Haralick features";
		}
		
	}
	
	
	

	static boolean processObject(final PathObject pathObject, final ParameterList params, final ImageServer<BufferedImage> server, final ColorDeconvolutionStains stains) throws IOException {
		String stainsName = (String)params.getChoiceParameterValue("stainChoice");
		double mag = params.getDoubleParameterValue("magnification");
		int d = params.getIntParameterValue("haralickDistance");
		int nBins = params.getIntParameterValue("haralickBins");
		boolean includeStats = params.getBooleanParameterValue("includeStats");
		boolean doCircular = params.getBooleanParameterValue("doCircular");

		double downsample;
		boolean hasMagnification = !Double.isNaN(server.getMetadata().getMagnification());
		PixelCalibration cal = server.getPixelCalibration();
		if (hasMagnification)
			downsample = server.getMetadata().getMagnification() / mag;
		else if (cal.hasPixelSizeMicrons()) {
			downsample = params.getDoubleParameterValue("pixelSizeMicrons") / cal.getAveragedPixelSizeMicrons();
		} else
			downsample = params.getDoubleParameterValue("downsample");
			
//		double downsample = server.getMagnification() / mag;
		
		// Try to get ROI
		ROI pathROI = null;
		if (pathObject instanceof PathCellObject && Boolean.TRUE.equals(params.getBooleanParameterValue("useNucleusROIs")))
			pathROI = ((PathCellObject)pathObject).getNucleusROI();
		else
			pathROI = pathObject.getROI();
		if (pathROI == null)
			return false;
		
		// Get bounds
		ImmutableDimension size = getPreferredTileSizePixels(server, params);
		
		RegionRequest region;
		boolean createMaskROI = false;
		if (size.getWidth() <= 0 || size.getHeight() <= 0) {
			region = RegionRequest.createInstance(server.getPath(), downsample, pathObject.getROI());
			createMaskROI = true;
			doCircular = false;
		} else if (size.getWidth() / downsample < 1 || size.getHeight() / downsample < 1)
			// Positive size, but insufficient to make measurements
			return false;
		else {
	//		RegionRequest region = RegionRequest.createInstance(server.getPath(), downsample, (int)(pathROI.getCentroidX() + .5) - size.width/2, (int)(pathROI.getCentroidY() + .5) - size.height/2, size.width, size.height, pathROI.getT(), pathROI.getZ());
			// Try to align with pixel boundaries according to the downsample being used - otherwise, interpolation can cause some strange, pattern artefacts
			int xStart = (int)((int)(pathROI.getCentroidX() / downsample + .5) * downsample) - size.width/2;
			int yStart = (int)((int)(pathROI.getCentroidY() / downsample + .5) * downsample) - size.height/2;
			int width = Math.min(server.getWidth(), xStart + size.width) - xStart;
			int height = Math.min(server.getHeight(), yStart + size.height) - yStart;
			region = RegionRequest.createInstance(server.getPath(), downsample, xStart, yStart, width, height, pathROI.getT(), pathROI.getZ());
		}

		// Check image large enough to do *anything* of value
		if (region.getWidth() / downsample < 3 || region.getHeight() / downsample < 3)
			return false;

//		System.out.println(bounds);
//		System.out.println("Size: " + size);

		BufferedImage img = server.readBufferedImage(region);

		if (img == null) {
			logger.error("Could not read image - unable to compute Haralick features for {}", pathObject);
			return false;
		}

		// Create mask ROI if necessary
		byte[] maskBytes = null;
		if (createMaskROI) {
			ROI roi = pathObject.getROI();
//			if (pathObject instanceof PathCellObject && ((PathCellObject)pathObject).getNucleusROI() != null)
//				roi = ((PathCellObject)pathObject).getNucleusROI();
			BufferedImage imgMask = BufferedImageTools.createROIMask(img.getWidth(), img.getHeight(), roi, region);
			maskBytes = ((DataBufferByte)imgMask.getRaster().getDataBuffer()).getData();
		}
		
		double minValue = Double.NaN;
		double maxValue = Double.NaN;
		
		// Get a buffer containing the image pixels
		int w = img.getWidth();
		int h = img.getHeight();
		int[] buf = img.getRGB(0, 0, w, h, null, 0, w);

		// Create a color transformer to get the images we need
		float[] pixels = new float[buf.length];
		SimpleModifiableImage pxImg = SimpleImages.createFloatImage(pixels, w, h);
		
		MeasurementList measurementList = pathObject.getMeasurementList();
		
		String postfix = maskBytes == null ? " (" + getDiameterString(server, params) + ")" : "";

		if (stainsName.equals("H-DAB")) {
			minValue = 0;
			maxValue = 2.0;
			processTransformedImage(pxImg, buf, pixels, measurementList, "Hematoxylin"+postfix, ColorTransformer.ColorTransformMethod.Hematoxylin_H_DAB, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "DAB"+postfix, ColorTransformer.ColorTransformMethod.DAB_H_DAB, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
		}
		else if (stainsName.equals("H&E")) {
			minValue = 0;
			maxValue = 2;
			processTransformedImage(pxImg, buf, pixels, measurementList, "Hematoxylin"+postfix, ColorTransformer.ColorTransformMethod.Hematoxylin_H_E, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Eosin"+postfix, ColorTransformer.ColorTransformMethod.Eosin_H_E, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
		} else if (stainsName.equals("H-DAB (8-bit)")) {
			minValue = 0;
			maxValue = 255;
			processTransformedImage(pxImg, buf, pixels, measurementList, "Hematoxylin 8-bit"+postfix, ColorTransformer.ColorTransformMethod.Hematoxylin_H_DAB_8_bit, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "DAB 8-bit"+postfix, ColorTransformer.ColorTransformMethod.DAB_H_DAB_8_bit, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
		}
		else if (stainsName.equals("H&E (8-bit)")) {
			minValue = 0;
			maxValue = 255;
			processTransformedImage(pxImg, buf, pixels, measurementList, "Hematoxylin 8-bit"+postfix, ColorTransformer.ColorTransformMethod.Hematoxylin_H_E_8_bit, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Eosin 8-bit"+postfix, ColorTransformer.ColorTransformMethod.Eosin_H_E_8_bit, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
		} else if (stainsName.equals("Optical density")) {
			minValue = 0;
			maxValue = 2.5;
			processTransformedImage(pxImg, buf, pixels, measurementList, "OD sum"+postfix, ColorTransformer.ColorTransformMethod.Optical_density_sum, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
		} else if (stainsName.equals("RGB")) {
			minValue = 0;
			maxValue = 255;
			processTransformedImage(pxImg, buf, pixels, measurementList, "Red"+postfix, ColorTransformer.ColorTransformMethod.Red, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Green"+postfix, ColorTransformer.ColorTransformMethod.Green, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Blue"+postfix, ColorTransformer.ColorTransformMethod.Blue, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
		} else if (stainsName.equals("RGB OD")) {
			minValue = 0;
			maxValue = 1.5; // Actual possible max is around 2.4 for 8-bit input... but this gives a lot of bins for (almost) saturated pixels
			processTransformedImage(pxImg, buf, pixels, measurementList, "Red OD"+postfix, ColorTransformer.ColorTransformMethod.Red_OD, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Green OD"+postfix, ColorTransformer.ColorTransformMethod.Green_OD, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
			processTransformedImage(pxImg, buf, pixels, measurementList, "Blue OD"+postfix, ColorTransformer.ColorTransformMethod.Blue_OD, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
		} else if (stainsName.equals("Grayscale")) {
			minValue = 0;
			maxValue = 255;
			processTransformedImage(pxImg, buf, pixels, measurementList, "Grayscale"+postfix, ColorTransformer.ColorTransformMethod.RGB_mean, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
		} else if (stainsName.equals("HSB")) {
			minValue = 0;
			maxValue = 1;
			
			
			float[] hsb = null;
			double sinX = 0;
			double cosX = 0;
			float[] pixelsBrightness = new float[pixels.length];
			float[] pixelsSaturation = new float[pixels.length];
			for (int i = 0; i < buf.length; i++) {
				if (maskBytes != null && maskBytes[i] == (byte)0)
					continue;
				int val = buf[i];
				hsb = Color.RGBtoHSB(ColorTools.red(val), ColorTools.green(val), ColorTools.blue(val), hsb);
				pixelsSaturation[i] = hsb[1];
				pixelsBrightness[i] = hsb[2];
				double alpha = hsb[0] * 2 * Math.PI;
				sinX += Math.sin(alpha);
				cosX += Math.cos(alpha);
			}
			measurementList.putMeasurement("Mean hue", Math.atan2(sinX, cosX) / (2 * Math.PI) + 0.5);
//			measurementList.putMeasurement("Mean saturation", hsb[1]);
//			measurementList.putMeasurement("Mean brightness", hsb[2]);
			processTransformedImage(SimpleImages.createFloatImage(pixelsSaturation, w, h), buf, pixelsSaturation, measurementList, "Saturation"+postfix, null, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
			processTransformedImage(SimpleImages.createFloatImage(pixelsBrightness, w, h), buf, pixelsBrightness, measurementList, "Brightness"+postfix, null, minValue, maxValue, d, nBins, stains, maskBytes, includeStats, doCircular);
		}
		measurementList.close();
		
		return true;
		
	}
	

	
	static void processTransformedImage(SimpleModifiableImage pxImg, int[] buf, float[] pixels, MeasurementList measurementList, String name, ColorTransformer.ColorTransformMethod method, double minValue, double maxValue, int d, int nBins, ColorDeconvolutionStains stains, byte[] maskBytes, boolean includeStats, boolean doCircular) {
		// Transform pixels, if we have a method
		if (method != null)
			ColorTransformer.getTransformedPixels(buf, method, pixels, stains);
		// Apply circular mask, if required
		if (doCircular) {
			double w = pxImg.getWidth();
			double h = pxImg.getHeight();
			double cx = (w-1) / 2;
			double cy = (h-1) / 2;
			double radius = Math.max(w, h) * .5;
			double distThreshold = radius * radius;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if ((cx - x)*(cx - x) + (cy - y)*(cy - y) > distThreshold)
						pxImg.setValue(x, y, Float.NaN);
				}			
			}
		}
		// Apply mask, if required
		if (maskBytes != null) {
			int w = pxImg.getWidth();
			for (int i = 0; i < pixels.length; i++) {
				if (maskBytes[i] == (byte)0)
					pxImg.setValue(i % w, i / w, Float.NaN);
			}
		}
		
		
		if (includeStats)
			addBasicStatistics(pxImg, measurementList, name);
		if (d > 0)
			addHaralickFeatures(HaralickFeatureComputer.measureHaralick(pxImg, null, nBins, minValue, maxValue, d), measurementList, name);
	}
	
	

	static void addBasicStatistics(final SimpleImage img, final MeasurementList measurementList, final String name) {
		RunningStatistics stats = StatisticsHelper.computeRunningStatistics(img);
		measurementList.putMeasurement(name + " Mean", stats.getMean());
		measurementList.putMeasurement(name + " Min", stats.getMin());
		measurementList.putMeasurement(name + " Max", stats.getMax());
		measurementList.putMeasurement(name + " Range", stats.getRange());
		measurementList.putMeasurement(name + " Std.dev.", stats.getStdDev());
		
		// Compute skewness and kurtosis
		double m = stats.getMean();
		double skewness = 0;
		double kurtosis = 0;
		double variance = 0;
		double n = stats.size();
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				float val = img.getValue(x, y);
				if (Float.isNaN(val))
					continue;
				double d = val - m;
				double d3 = d*d*d;
				variance += d*d/n;
				skewness += d3/n;
				kurtosis += d3*d/n;
			}			
		}
		// TODO: Reinsert skewness & kurtosis measurements, after checking
////		double sigma = stats.getStdDev();
////		System.out.println("Variance difference: " + variance + ",  " + stats.getVariance());
////		measurementList.putMeasurement(name + " Variance (again)", variance);
		measurementList.putMeasurement(name + " Skewness", skewness/(variance*Math.sqrt(variance)));
		measurementList.putMeasurement(name + " Kurtosis", kurtosis/(variance*variance));
	}

	
	static void addHaralickFeatures(final HaralickFeatures haralickFeatures, final MeasurementList measurementList, final String name) {
		for (int i = 0; i < haralickFeatures.nFeatures(); i++) {
			measurementList.putMeasurement(String.format("%s Haralick %s (F%d)", name,
					haralickFeatures.getFeatureName(i),
					i), haralickFeatures.getFeature(i));
		}
	}



	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		boolean hasMicrons = imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		boolean hasMagnification = !Double.isNaN(imageData.getServer().getMetadata().getMagnification());
		
		params.getParameters().get("tileSizeMicrons").setHidden(!hasMicrons);
		params.getParameters().get("tileSizePx").setHidden(hasMicrons);
		
		params.getParameters().get("magnification").setHidden(!hasMagnification);
		params.getParameters().get("pixelSizeMicrons").setHidden(!hasMicrons || hasMagnification);
		
		params.getParameters().get("downsample").setHidden(hasMicrons || hasMagnification);
		
		return params;
	}

	@Override
	public String getName() {
		return "Add Haralick texture features";
	}

	@Override
	public String getLastResultsDescription() {
		return "";
	}

	@Override
	public String getDescription() {
		return "Add Haralick texture features to existing object measurements";
	}

	@Override
	protected Collection<PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		return runner.getImageData().getHierarchy().getSelectionModel().getSelectedObjects();
//		return runner.getImageData().getHierarchy().getSelectionModel().getSelectedObjects();
//		return runner.getImageData().getHierarchy().getObjects(null, PathDetectionObject.class);
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		List<Class<? extends PathObject>> parents = new ArrayList<>();
		parents.add(PathDetectionObject.class);
		parents.add(TMACoreObject.class);
		return parents;
	}

}
