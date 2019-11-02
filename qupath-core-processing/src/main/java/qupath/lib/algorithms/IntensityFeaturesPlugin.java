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

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.algorithms.IntensityFeaturesPlugin.BasicFeatureComputer.Feature;
import qupath.lib.analysis.features.CoocurranceMatrices;
import qupath.lib.analysis.features.HaralickFeatureComputer;
import qupath.lib.analysis.features.HaralickFeatures;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.analysis.images.SimpleModifiableImage;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.analysis.stats.StatisticsHelper;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Plugin for calculating intensity-based features, including Haralick textures, within or around detections or tiles.
 * <p>
 * The ROIs of the detections can be used directly as masks, or else the textures can alternatively be 
 * calculated within square or circular regions around the object centroids.
 * This latter option makes it possible to calculate a high density of tiles (for example), and then to 
 * compute textures at different resolutions independently of the tile size.
 * 
 * @author Pete Bankhead
 *
 */
public class IntensityFeaturesPlugin extends AbstractInteractivePlugin<BufferedImage> {
	
	private final static Logger logger = LoggerFactory.getLogger(IntensityFeaturesPlugin.class);
	
	private boolean parametersInitialized = false;
	
	private static Map<Integer, BasicChannel> channelMap = new HashMap<>();
	
	static enum RegionType {
		ROI, SQUARE, CIRCLE, NUCLEUS;
		
		@Override
		public String toString() {
			switch(this) {
			case CIRCLE:
				return "Circular tiles";
			case ROI:
				return "ROI";
			case SQUARE:
				return "Square tiles";
			case NUCLEUS:
				return "Cell nucleus";
			default:
				return "Unknown";
			}
		}
		
	}
	
	static interface FeatureColorTransform {
		
		public String getPrompt(final ImageData<?> imageData);
		
		public float[] getTransformedPixels(final BufferedImage img, int[] buf, final ColorDeconvolutionStains stains, float[] pixels);
		
		public boolean supportsImage(ImageData<?> imageData);
		
		public String getKey();
		
		public double[] getHaralickMinMax();
		
		public String getName(ImageData<?> imageData, boolean useSimpleNames);
		
	}
	
	static class BasicChannel implements FeatureColorTransform {
		
		private int channel;
		
		BasicChannel(int channel) {
			this.channel = channel;
		}
		
		@Override
		public String getPrompt(ImageData<?> imageData) {
			return getImageChannelName(imageData);
		}

		@Override
		public float[] getTransformedPixels(BufferedImage img, int[] buf, ColorDeconvolutionStains stains,
				float[] pixels) {
			return img.getRaster().getSamples(0, 0, img.getWidth(), img.getHeight(), channel, pixels);
		}

		@Override
		public boolean supportsImage(ImageData<?> imageData) {
			return !imageData.getServer().isRGB() && channel >= 0 && channel < imageData.getServer().nChannels();
		}

		@Override
		public String getKey() {
			return "channel" + (channel+1);
		}

		@Override
		public double[] getHaralickMinMax() {
			return null;
		}
		
		private String getImageChannelName(ImageData<?> imageData) {
			if (imageData == null)
				return getSimpleChannelName();
			return imageData.getServer().getChannel(channel).getName();
		}
		
		private String getSimpleChannelName() {
			return "Channel " + (channel + 1); // Note in v0.2.0-m3 and before this didn't + 1
		}

		@Override
		public String getName(final ImageData<?> imageData, final boolean useSimpleNames) {
			String simpleName = getSimpleChannelName();
			if (useSimpleNames)
				return simpleName;
			String name = getImageChannelName(imageData);
			if (name == null || name.isBlank())
				return simpleName;
			return name;
		}
		
	}
	
	
	static synchronized List<FeatureColorTransform> getBasicChannelTransforms(int nChannels) {
		var list = new ArrayList<FeatureColorTransform>();
		for (int i = 0; i < nChannels; i++) {
			var channel = channelMap.get(i);
			if (channel == null) {
				channel = new BasicChannel(i);
				channelMap.put(i, channel);
			}
			list.add(channel);
		}
		return list;
	}
	
	
	static enum FeatureColorTransformEnum implements FeatureColorTransform {
		
		OD("colorOD", "Optical density sum"),
		STAIN_1("colorStain1", "Color Deconvolution Stain 1"),
		STAIN_2("colorStain2", "Color Deconvolution Stain 2"),
		STAIN_3("colorStain3", "Color Deconvolution Stain 3"),
		RED("colorRed", "Red"),
		GREEN("colorGreen", "Green"),
		BLUE("colorBlue", "Blue"),
		HUE("colorHue", "Hue (mean only)"),
		SATURATION("colorSaturation", "Saturation"),
		BRIGHTNESS("colorBrightness", "Brightness"),
		;
		
		private String key;
		private String prompt;
		
		FeatureColorTransformEnum(final String key, final String prompt) {
			this.key = key;
			this.prompt = prompt;
		}

		@Override
		public String getPrompt(final ImageData<?> imageData) {
			ColorDeconvolutionStains stains = imageData == null ? null : imageData.getColorDeconvolutionStains();
			if (stains != null) {
				switch (this) {
				case STAIN_1:
					return stains.getStain(1).getName() + " (color deconvolved)";
				case STAIN_2:
					return stains.getStain(2).getName() + " (color deconvolved)";
				case STAIN_3:
					return stains.getStain(3).getName() + " (color deconvolved)";
				default:
					break;
				}
			}
			return prompt;
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public double[] getHaralickMinMax() {
			switch (this) {
			case HUE:
				return null;
			case RED:
			case GREEN:
			case BLUE:
				return new double[]{0, 255};
			case BRIGHTNESS:
				return new double[]{0, 1};
			case SATURATION:
				return new double[]{0, 1};
			case OD:
				return new double[]{0, 2.5};
			case STAIN_1:
			case STAIN_2:
			case STAIN_3:
				return new double[]{0, 1.5};
			default:
				return null;
			}
		}
		
		
		@Override
		public String getName(final ImageData<?> imageData, boolean useSimpleNames) {
			var stains = imageData == null ? null : imageData.getColorDeconvolutionStains();
			switch (this) {
			case STAIN_1:
				return stains == null ? "Stain 1" : stains.getStain(1).getName();
			case STAIN_2:
				return stains == null ? "Stain 2" : stains.getStain(2).getName();
			case STAIN_3:
				return stains == null ? "Stain 3" : stains.getStain(3).getName();
			case HUE:
				return "Hue";
			case OD:
				return "OD Sum";
			default:
				return getPrompt(null);
			}
		}
		

		@Override
		public boolean supportsImage(final ImageData<?> imageData) {
			switch (this) {
			case BRIGHTNESS:
			case HUE:
			case RED:
			case GREEN:
			case BLUE:
			case SATURATION:
				return imageData.getServer().isRGB();
			case OD:
			case STAIN_1:
			case STAIN_2:
			case STAIN_3:
				return imageData.isBrightfield() && imageData.getServer().isRGB();
			default:
				return false;
			}
		}

		
		@Override
		public float[] getTransformedPixels(final BufferedImage img, int[] buf, final ColorDeconvolutionStains stains, float[] pixels) {
			if (pixels == null)
				pixels = new float[img.getWidth() * img.getHeight()];
			
			switch (this) {
			case BRIGHTNESS:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Brightness, pixels, stains);
			case HUE:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Hue, pixels, stains);
			case OD:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Optical_density_sum, pixels, stains);
			case RED:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Red, pixels, stains);
			case GREEN:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Green, pixels, stains);
			case BLUE:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Blue, pixels, stains);
			case SATURATION:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Saturation, pixels, stains);
			case STAIN_1:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Stain_1, pixels, stains);
			case STAIN_2:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Stain_2, pixels, stains);
			case STAIN_3:
				return ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Stain_3, pixels, stains);
			default:
				break;
			}
			
			return null;
		}
		
		
	}
	
	// Commented out the option with the cumulative histogram... for now
//	private static List<FeatureComputerBuilder> builders = Arrays.asList(new BasicFeatureComputerBuilder(), new MedianFeatureComputerBuilder(), new HaralickFeatureComputerBuilder(), new CumulativeHistogramFeatureComputerBuilder());
	private static List<FeatureComputerBuilder> builders = Arrays.asList(new BasicFeatureComputerBuilder(), new MedianFeatureComputerBuilder(), new HaralickFeatureComputerBuilder());
	
	
	@Override
	public boolean runPlugin(final PluginRunner<BufferedImage> pluginRunner, final String arg) {
		boolean success = super.runPlugin(pluginRunner, arg);
		getHierarchy(pluginRunner).fireHierarchyChangedEvent(this);
		return success;
	}
	
	
	
	private static ImmutableDimension getPreferredTileSizePixels(final ImageServer<BufferedImage> server, final ParameterList params) {
		// Determine tile size
		int tileWidth, tileHeight;
		PixelCalibration cal = server.getPixelCalibration();
		if (cal.hasPixelSizeMicrons()) {
			double tileSize = params.getDoubleParameterValue("tileSizeMicrons");
			tileWidth = (int)Math.round(tileSize / cal.getPixelWidthMicrons());
			tileHeight = (int)Math.round(tileSize / cal.getPixelHeightMicrons());
		} else {
			tileWidth = (int)Math.round(params.getDoubleParameterValue("tileSizePixels"));
			tileHeight = tileWidth;
		}
		return ImmutableDimension.getInstance(tileWidth, tileHeight);
	}
	
	static String getDiameterString(final ImageServer<BufferedImage> server, final ParameterList params) {
		RegionType regionType = (RegionType)params.getChoiceParameterValue("region");
		PixelCalibration cal = server.getPixelCalibration();
		String shape = regionType == RegionType.SQUARE ? "Square" : (regionType == RegionType.CIRCLE ? "Circle" : "ROI");
		String unit = cal.hasPixelSizeMicrons() ? GeneralTools.micrometerSymbol() : "px";
		double pixelSize = cal.hasPixelSizeMicrons() ? params.getDoubleParameterValue("pixelSizeMicrons") : params.getDoubleParameterValue("downsample");
		double regionSize = cal.hasPixelSizeMicrons() ? params.getDoubleParameterValue("tileSizeMicrons") : params.getDoubleParameterValue("tileSizePixels");
		
		if (regionType == RegionType.ROI) {
			return String.format("ROI: %.2f %s per pixel", pixelSize, unit);
		} else if (regionType == RegionType.NUCLEUS) {
			return String.format("Nucleus: %.2f %s per pixel", pixelSize, unit);
		} else {
			return String.format("%s: Diameter %.1f %s: %.2f %s per pixel", shape, regionSize, unit, pixelSize, unit);
		}
	}
	
	
	@Override
	protected void addRunnableTasks(final ImageData<BufferedImage> imageData, final PathObject parentObject, List<Runnable> tasks) {
		final ParameterList params = getParameterList(imageData);
		final ImageServer<BufferedImage> server = imageData.getServer();
		// Don't add any tasks if the downsample isn't value
		PixelCalibration cal = server.getPixelCalibration();
		double downsample = calculateDownsample(cal, params);
		if (downsample <= 0) {
			throw new IllegalArgumentException("Effective downsample must be > 0 (requested value " + GeneralTools.formatNumber(downsample, 1) + ")");
		}
		tasks.add(new IntensityFeatureRunnable(imageData, parentObject, params));
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
//		if (regionStore != null) {
//			int n = tasks.size();
//			Runnable[] tasks2 = new Runnable[n];
//			if (rearrangeByStride(tasks, tasks2, Runtime.getRuntime().availableProcessors()))
//				tasks = Arrays.asList(tasks2);
//		}
//		return tasks;
//	}
	
	
	
	static class IntensityFeatureRunnable implements Runnable {
		
		private ImageData<BufferedImage> imageData;
		private ParameterList params;
		private PathObject parentObject;
		
		public IntensityFeatureRunnable(final ImageData<BufferedImage> imageData, final PathObject parentObject, final ParameterList params) {
			this.imageData = imageData;
			this.parentObject = parentObject;
			this.params = params;
		}

		@Override
		public void run() {
			try {
				processObject(parentObject, params, imageData);
			} catch (IOException e) {
				logger.error("Unable to process " + parentObject, e);
			} finally {
				parentObject.getMeasurementList().close();
				imageData = null;
				params = null;
			}
		}
		
		
		@Override
		public String toString() {
			return "Intensity measurements";
		}
		
	}
	
	
	static double calculateDownsample(PixelCalibration cal, ParameterList params) {
		if (cal.hasPixelSizeMicrons()) {
			return params.getDoubleParameterValue("pixelSizeMicrons") / cal.getAveragedPixelSizeMicrons();
		} else
			return params.getDoubleParameterValue("downsample");

	}
	

	static boolean processObject(final PathObject pathObject, final ParameterList params, final ImageData<BufferedImage> imageData) throws IOException {

		// Determine amount to downsample
		var server = imageData.getServer();
		var stains = imageData.getColorDeconvolutionStains();
		
		PixelCalibration cal = server.getPixelCalibration();
		double downsample = calculateDownsample(cal, params);
		
		if (downsample <= 0) {
			logger.warn("Effective downsample must be > 0 (requested value {})", downsample);
		}

		// Determine region shape
		RegionType regionType = (RegionType)params.getChoiceParameterValue("region");
			
		// Try to get ROI
		boolean useROI = regionType == RegionType.ROI || regionType == RegionType.NUCLEUS;
		ROI roi = null;
		if (regionType == RegionType.NUCLEUS) {
			if (pathObject instanceof PathCellObject)
				roi = ((PathCellObject)pathObject).getNucleusROI();
		} else
			roi = pathObject.getROI();
//		if (pathObject instanceof PathCellObject && ((PathCellObject)pathObject).getNucleusROI() != null)
//			pathROI = ((PathCellObject)pathObject).getNucleusROI();
		if (roi == null)
			return false;
		
		// Create a map - this is useful for occasions when tiling is needed
		Map<FeatureColorTransform, List<FeatureComputer>> map = new LinkedHashMap<>();
		if (server.isRGB()) {
			for (FeatureColorTransform transform : FeatureColorTransformEnum.values()) {
				List<FeatureComputer> list = new ArrayList<>();
				map.put(transform, list);
				for (FeatureComputerBuilder builder : builders) {
					list.add(builder.build());
				}
			}
		} else {
			for (FeatureColorTransform transform : getBasicChannelTransforms(server.nChannels())) {
				List<FeatureComputer> list = new ArrayList<>();
				map.put(transform, list);
				for (FeatureComputerBuilder builder : builders) {
					list.add(builder.build());
				}
			}
		}
		
		String prefix = getDiameterString(server, params);

		// Create tiled ROIs, if required
		ImmutableDimension sizePreferred = ImmutableDimension.getInstance((int)(2000*downsample), (int)(2000*downsample));
//		ImmutableDimension sizePreferred = new ImmutableDimension((int)(200*downsample), (int)(200*downsample));
		Collection<? extends ROI> rois = RoiTools.computeTiledROIs(roi, sizePreferred, sizePreferred, false, 0);
		if (rois.size() > 1)
			logger.info("Splitting {} into {} tiles for intensity measurements", roi, rois.size());
		
		for (ROI pathROI : rois) {
			
			if (Thread.currentThread().isInterrupted()) {
				logger.warn("Measurement skipped - thread interrupted!");
				return false;
			}
			
			// Get bounds
			RegionRequest region;
			if (useROI) {
				region = RegionRequest.createInstance(server.getPath(), downsample, pathROI);
			} else {
				ImmutableDimension size = getPreferredTileSizePixels(server, params);
				//		RegionRequest region = RegionRequest.createInstance(server.getPath(), downsample, (int)(pathROI.getCentroidX() + .5) - size.width/2, (int)(pathROI.getCentroidY() + .5) - size.height/2, size.width, size.height, pathROI.getT(), pathROI.getZ());
				// Try to align with pixel boundaries according to the downsample being used - otherwise, interpolation can cause some strange, pattern artefacts
				int xStart = (int)(Math.round(pathROI.getCentroidX() / downsample) * downsample) - size.width/2;
				int yStart = (int)(Math.round(pathROI.getCentroidY() / downsample) * downsample) - size.height/2;
				int width = Math.min(server.getWidth(), xStart + size.width) - xStart;
				int height = Math.min(server.getHeight(), yStart + size.height) - yStart;
				region = RegionRequest.createInstance(server.getPath(), downsample, xStart, yStart, width, height, pathROI.getT(), pathROI.getZ());			
			}
			
//			// Check image large enough to do *anything* of value
//			if (region.getWidth() / downsample < 1 || region.getHeight() / downsample < 1) {
//				logger.trace("Requested region is too small! {}", region);
//				return false;
//			}
	
	//		System.out.println(bounds);
	//		System.out.println("Size: " + size);
	
			BufferedImage img = server.readBufferedImage(region);
			if (img == null) {
				logger.error("Could not read image - unable to compute intensity features for {}", pathObject);
				return false;
			}
	
			// Create mask ROI if necessary
			// If we just have 1 pixel, we want to use it so that the mean/min/max measurements are valid (even if nothing else is)
			byte[] maskBytes = null;
			if (useROI && img.getWidth() * img.getHeight() > 1) {
				BufferedImage imgMask = BufferedImageTools.createROIMask(img.getWidth(), img.getHeight(), pathROI, region);
				maskBytes = ((DataBufferByte)imgMask.getRaster().getDataBuffer()).getData();
			}
			
			boolean isRGB = server.isRGB();
			List<FeatureColorTransform> transforms;
			if (isRGB)
				transforms = Arrays.asList(FeatureColorTransformEnum.values());
			else
				transforms = getBasicChannelTransforms(server.nChannels());
			
			int w = img.getWidth();
	 		int h = img.getHeight();
			int[] rgbBuffer = isRGB ? img.getRGB(0, 0, w, h, null, 0, w) : null;
			float[] pixels = null;
			for (FeatureColorTransform transform : transforms) {
				// Check if the color transform is requested
				if (params.containsKey(transform.getKey()) && Boolean.TRUE.equals(params.getBooleanParameterValue(transform.getKey()))) {
					
					// Transform the pixels
					pixels = transform.getTransformedPixels(img, rgbBuffer, stains, pixels);
					
					// Create the simple image
					SimpleModifiableImage pixelImage = SimpleImages.createFloatImage(pixels, w, h);
					
					// Apply any arbitrary mask
					if (maskBytes != null) {
						for (int i = 0; i < pixels.length; i++) {
							if (maskBytes[i] == (byte)0)
								pixelImage.setValue(i % w, i / w, Float.NaN);
						}
					} else if (regionType == RegionType.CIRCLE) {
						// Apply circular tile mask
						double cx = (w-1) / 2;
						double cy = (h-1) / 2;
						double radius = Math.max(w, h) * .5;
						double distThreshold = radius * radius;
						for (int y = 0; y < h; y++) {
							for (int x = 0; x < w; x++) {
								if ((cx - x)*(cx - x) + (cy - y)*(cy - y) > distThreshold)
									pixelImage.setValue(x, y, Float.NaN);
							}			
						}
					}
					
					// Do the computations
					for (FeatureComputer computer : map.get(transform)) {
						computer.updateFeatures(pixelImage, transform, params);
					}
				}
			}
		}
		
		// Add measurements to the parent object
		for (Entry<FeatureColorTransform, List<FeatureComputer>> entry : map.entrySet()) {
			String name = prefix + ": " + entry.getKey().getName(imageData, false) + ":";
			for (FeatureComputer computer : entry.getValue())
				computer.addMeasurements(pathObject, name, params);
		}
		pathObject.getMeasurementList().close();
		
		// Lock any measurements that require it
		if (pathObject instanceof PathAnnotationObject)
			((PathAnnotationObject)pathObject).setLocked(true);
		else if (pathObject instanceof TMACoreObject)
			((TMACoreObject)pathObject).setLocked(true);
		
		return true;
	}
	
	
	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		
		if (!parametersInitialized) {
			
			params = new ParameterList();
			
			// Regions & resolution
			params.addTitleParameter("Resolution");
			params.addDoubleParameter("downsample", "Downsample", 1, null, "Amount to downsample the image before calculating textures; choose 1 to use full resolution, or a higher value to use a smaller image").
					addDoubleParameter("pixelSizeMicrons", "Preferred pixel size", 2, GeneralTools.micrometerSymbol(), "Preferred pixel size of the image used to calculate the tetures - higher values means coarser (lower resolution) images")
					;

			// Regions & resolution
			params.addTitleParameter("Regions");
			params.addChoiceParameter("region", "Region", RegionType.ROI, Arrays.asList(RegionType.values()), "The region within which to calculate the features");
			params.addDoubleParameter("tileSizeMicrons", "Tile diameter", 25, GeneralTools.micrometerSymbol(), "Diameter of tile around the object centroid used to calculate textures.\nOnly matters if tiles are being used (i.e. the region parameter isn't ROI).");
			params.addDoubleParameter("tileSizePixels", "Tile diameter", 200, "px (full resolution image)", "Diameter of tile around the object centroid used to calculate textures.\nOnly matters if tiles are being used (i.e. the region parameter isn't ROI).");
			
			boolean hasMicrons = imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
			
			params.getParameters().get("pixelSizeMicrons").setHidden(!hasMicrons);
			params.getParameters().get("downsample").setHidden(hasMicrons);
			
			params.getParameters().get("tileSizeMicrons").setHidden(!hasMicrons);
			params.getParameters().get("tileSizePixels").setHidden(hasMicrons);
			
			// Color transforms
			params.addTitleParameter("Channels/Color transforms");
			if (imageData.getServer().isRGB()) {
				for (FeatureColorTransform transform : FeatureColorTransformEnum.values()) {
					if (transform.supportsImage(imageData))
						params.addBooleanParameter(transform.getKey(), transform.getPrompt(imageData), false);
				}
			} else {
				for (FeatureColorTransform transform : getBasicChannelTransforms(imageData.getServer().nChannels())) {
					params.addBooleanParameter(transform.getKey(), transform.getPrompt(imageData), false);
				}
			}
	
			// Add feature-related parameters
			for (FeatureComputerBuilder builder : builders) {
				builder.addParameters(imageData, params);
			}
		}
		
		parametersInitialized = true;
		
		return params;
	}

	@Override
	public String getName() {
		return "Compute intensity features";
	}

	@Override
	public String getLastResultsDescription() {
		return "";
	}

	@Override
	public String getDescription() {
		return "Add intensity features to existing object measurements";
	}

	@Override
	protected Collection<PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		return runner.getImageData().getHierarchy().getSelectionModel().getSelectedObjects();
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		List<Class<? extends PathObject>> parents = new ArrayList<>();
		parents.add(PathCellObject.class);
		parents.add(PathDetectionObject.class);
		parents.add(PathAnnotationObject.class);
		parents.add(TMACoreObject.class);
		return parents;
	}
	
	
	@Override
	public boolean alwaysPromptForObjects() {
		return true;
	}

	
	
	
	
	
	
	
	static interface FeatureComputerBuilder {
		
		/**
		 * Add any required parameters (and title) to the ParameterList.
		 * 
		 * @param params
		 */
		public abstract void addParameters(final ImageData<?> imageData, final ParameterList params);

		/**
		 * Create a new FeatureComputer.
		 * 
		 * @return
		 */
		public abstract FeatureComputer build();
		
	}
	
	static interface FeatureComputer {
		
		/**
		 * Update the features.  This is used, rather than a compute features method, to support tiling - 
		 * i.e. it may be that only part of the image is passed at this stage.
		 * 
		 * @param img
		 * @param transform
		 * @param params
		 */
		public abstract void updateFeatures(final SimpleImage img, FeatureColorTransform transform, final ParameterList params);
		
		/**
		 * Add the final measurements to the specified object, calculated and updated previously.
		 * 
		 * The 'name' should encapsulate everything else that is required, i.e. color transform, resolution etc. 
		 * Therefore only the specific feature name needs to be appended to this.
		 * 
		 * @param pathObject
		 * @param name
		 * @param params
		 */
		public abstract void addMeasurements(final PathObject pathObject, final String name, final ParameterList params);
		
	}
	
	
	static class BasicFeatureComputer implements FeatureComputer {
		
		static enum Feature {
			MEAN("doMean", "Mean", "Compute mean intensity"),
			STD_DEV("doStdDev", "Standard deviation", "Compute standard deviation of intensities"),
			MIN_MAX("doMinMax", "Min & Max", "Compute minimum & maximum of intensities"),
//			SKEW_KURTOSIS("doSkewKurtosis", "Skew & Kurtosis", "Compute skew & kurtosis of intensities"),
			;
			
			private String key, prompt, help;
			
			Feature(String key, String prompt, String help) {
				this.key = key;
				this.prompt = prompt;
				this.help = help;
			}
			
		}

		private RunningStatistics stats;
		private HueStats hueStats;

		@Override
		public void updateFeatures(final SimpleImage img,  FeatureColorTransform transform, final ParameterList params) {
			// Check if we need to do these computations at all
			boolean requireFeatures = false;
			for (Feature feature : Feature.values()) {
				if (Boolean.TRUE.equals(params.getBooleanParameterValue(feature.key))) {
					requireFeatures = true;
					break;
				}
			}
			if (!requireFeatures)
				return;
			
			// Handle Hue differently (due to its circular nature)
			if (transform == FeatureColorTransformEnum.HUE) {
				if (hueStats == null)
					hueStats = new HueStats();
				hueStats.update(img);
				return;
			}
			
			// Update statistics
			if (stats == null)
				stats = new RunningStatistics();
			StatisticsHelper.updateRunningStatistics(stats, img);
		}

		@Override
		public void addMeasurements(PathObject pathObject, String name, ParameterList params) {
			// Handle Hue special case
			if (hueStats != null) {
				pathObject.getMeasurementList().putMeasurement(name + " Mean", hueStats.getMeanHue());
				return;
			}
			
			// Handle everything else
			if (stats == null)
				return;
			
			MeasurementList measurementList = pathObject.getMeasurementList();
			if (params.getBooleanParameterValue(Feature.MEAN.key))
				measurementList.putMeasurement(name + " Mean", stats.getMean());
			if (params.getBooleanParameterValue(Feature.STD_DEV.key))
				measurementList.putMeasurement(name + " Std.dev.", stats.getStdDev());
			if (params.getBooleanParameterValue(Feature.MIN_MAX.key)) {
				measurementList.putMeasurement(name + " Min", stats.getMin());
				measurementList.putMeasurement(name + " Max", stats.getMax());
			}
			
			logger.trace("Measured pixel count: {}", stats.size());
			
		}
		
	}
	
	
	
	static class MedianFeatureComputerBuilder implements FeatureComputerBuilder {
		
		private int originalBitsPerPixel;

		@Override
		public void addParameters(ImageData<?> imageData, ParameterList params) {
			this.originalBitsPerPixel = imageData.getServer().getPixelType().getBitsPerPixel();
			if (originalBitsPerPixel > 16)
				return;
			params.addBooleanParameter("doMedian", "Median", false, "Calculate approximate median of pixel values (based on a generated histogram)");
		}

		@Override
		public FeatureComputer build() {
			return new MedianFeatureComputer(originalBitsPerPixel);
		}
		
	}
	
	
	static class MedianFeatureComputer implements FeatureComputer {
		
		private int originalBitsPerPixel;
		private double minBin, maxBin;
		private long n;
		private int nBins;
		private long[] histogram;
		
		MedianFeatureComputer(final int originalBitsPerPixel) {
			this.originalBitsPerPixel = originalBitsPerPixel;
		}

		@Override
		public void updateFeatures(SimpleImage img, FeatureColorTransform transform, ParameterList params) {
			// Don't do anything if we don't have a meaningful transform
			if (transform == null || transform == FeatureColorTransformEnum.HUE || (histogram != null && histogram.length == 0))
				return;
			
			// Create a new histogram if we need one
			if (histogram == null) {
				if (transform instanceof FeatureColorTransformEnum) {
					switch((FeatureColorTransformEnum)transform) {
					case BLUE:
					case GREEN:
					case RED:
						nBins = 256;
						minBin = 0;
						maxBin = 255;
						break;
					case SATURATION:
					case BRIGHTNESS:
						nBins = 1001;
						minBin = 0;
						maxBin = 1;
						break;
					case HUE:
						break;
					case OD:
					case STAIN_1:
					case STAIN_2:
					case STAIN_3:
						nBins = 4001;
						minBin = 0;
						maxBin = 4.0;
						break;
					default:
						// We have something that unfortunately we can't handle...
						histogram = new long[0];
						return;
					}
				} else {
					if (originalBitsPerPixel <= 16) {
						nBins = (int)Math.pow(2, originalBitsPerPixel);
						minBin = 0;
						maxBin = nBins-1;
					} else {
						// Can't handle more bits per pixel...
						histogram = new long[0];
						return;
					}
				}
				// Create histogram
				histogram = new long[nBins];
			}
			
			// Check we can do anything
			if (nBins == 0)
				return;
			
			// Loop through and update histogram
//			List<Double> testList = new ArrayList<>();
			double binWidth = (maxBin - minBin) / (nBins - 1);
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					double val = img.getValue(x, y);
					if (!Double.isFinite(val))
						continue;
					int bin = (int)((val - minBin)/binWidth);
					if (bin >= nBins)
						histogram[nBins-1]++;
					else if (bin < 0)
						histogram[0]++;
					else
						histogram[bin]++;
					n++;
					
//					testList.add(val);
				}
			}
			
//			Collections.sort(testList);
//			System.err.println("Exact median for " + transform + ": " + testList.get(testList.size()/2));
			
		}

		@Override
		public void addMeasurements(PathObject pathObject, String name, ParameterList params) {
			// Check if we have a median we can use
			
			boolean doMedian = params.containsKey("doMedian") && Boolean.TRUE.equals(params.getBooleanParameterValue("doMedian"));
			if (!doMedian || histogram == null || histogram.length == 0)
				return;
			
			// Find the bin containing the median value
			double median = Double.NaN;
			double halfway = n/2.0;
			if (n > 0) {
				long sum = 0;
				int bin = 0;
				while (bin < histogram.length) {
					sum += histogram[bin];
					if (sum >= halfway)
						break;
					bin++;
				}
				if (bin == histogram.length)
					median = maxBin;
				else if (bin == 0)
					median = minBin;
				else {
					double binWidth = (maxBin - minBin) / (nBins - 1);
					median = minBin + bin * binWidth + binWidth/2;
				}
			}
			
			// Add to measurement list
			MeasurementList measurementList = pathObject.getMeasurementList();
			measurementList.putMeasurement(name + " Median", median);
		}
		
	}
	
	
	
	static class BasicFeatureComputerBuilder implements FeatureComputerBuilder {
		
		@Override
		public void addParameters(ImageData<?> imageData, ParameterList params) {
			params.addTitleParameter("Basic features");
			for (Feature feature : Arrays.asList(Feature.MEAN, Feature.STD_DEV, Feature.MIN_MAX)) {
				params.addBooleanParameter(feature.key, feature.prompt, false, feature.help);
			}
		}
		
		@Override
		public FeatureComputer build() {
			return new BasicFeatureComputer();
		}
		
	}
	
	
	
	static class HaralickFeaturesComp implements FeatureComputer {
		
		private CoocurranceMatrices matrices;

		@Override
		public void updateFeatures(SimpleImage img, FeatureColorTransform transform, ParameterList params) {
			if (!Boolean.TRUE.equals(params.getBooleanParameterValue("doHaralick")))
				return;
			
			// Don't compute results for Hue - would be confusing...
			if (transform == FeatureColorTransformEnum.HUE)
				return;
			
			double haralickMin = Double.NaN;
			double haralickMax = Double.NaN;
			if (params.containsKey("haralickMin"))
				haralickMin = params.getDoubleParameterValue("haralickMin");
			if (params.containsKey("haralickMax"))
				haralickMax = params.getDoubleParameterValue("haralickMax");
			double[] minMax = transform.getHaralickMinMax();
			if (Double.isFinite(haralickMin) && Double.isFinite(haralickMax) && haralickMax > haralickMin) {
				logger.trace("Using Haralick min/max {}, {}", haralickMin, haralickMax);
				minMax = new double[]{haralickMin, haralickMax};
			} else {
				if (minMax == null)
					return;
				logger.trace("Using default Haralick min/max {}, {}", haralickMin, haralickMax);
			}
			
			int d = params.getIntParameterValue("haralickDistance");
			int nBins = params.getIntParameterValue("haralickBins");
			
			matrices = HaralickFeatureComputer.updateCooccurrenceMatrices(matrices, img, null, nBins, minMax[0], minMax[1], d);
		}

		@Override
		public void addMeasurements(PathObject pathObject, String name, ParameterList params) {
			if (matrices == null)
				return;
			
			MeasurementList measurementList = pathObject.getMeasurementList();
			HaralickFeatures haralickFeatures = matrices.getMeanFeatures();
			for (int i = 0; i < haralickFeatures.nFeatures(); i++) {
				measurementList.putMeasurement(String.format("%s Haralick %s (F%d)", name,
						haralickFeatures.getFeatureName(i),
						i), haralickFeatures.getFeature(i));
			}
		}

	}
	
	
	static class HaralickFeatureComputerBuilder implements FeatureComputerBuilder {

		@Override
		public void addParameters(ImageData<?> imageData, ParameterList params) {
			params.addTitleParameter("Haralick features");
			params.addBooleanParameter("doHaralick", "Compute Haralick features", false, "Calculate Haralick texture features (13 features in total, non-RGB images require min/max values to be set based on the range of the data)");
			
			if (!imageData.getServer().isRGB()) {
				params.addDoubleParameter("haralickMin", "Haralick min", Double.NaN, null, "Minimum value used when calculating grayscale cooccurrence matrix for Haralick features -\nThis should be approximately the lowest pixel value in the image for which textures are meaningful.")
						.addDoubleParameter("haralickMax", "Haralick max", Double.NaN, null, "Maximum value used when calculating grayscale cooccurrence matrix for Haralick features -\nThis should be approximately the highest pixel value in the image for which textures are meaningful.");
			}
			params.addIntParameter("haralickDistance", "Haralick distance", 1, null, "Spacing between pixels used in computing the co-occurrence matrix for Haralick textures (default = 1)")
					.addIntParameter("haralickBins", "Haralick number of bins", 32, null, 8, 256, "Number of intensity bins to use when computing the co-occurrence matrix for Haralick textures (default = 32)");
			
		}

		@Override
		public FeatureComputer build() {
			return new HaralickFeaturesComp();
		}
		
	}
	
	
	/**
	 * Calculate the mean Hue.
	 * 
	 * Given that Hue values are 'circular', this is angle-based rather than a straightforward mean.
	 *
	 */
	static class HueStats {

		private double sinX = 0;
		private double cosX = 0;
		
		public void update(final SimpleImage img) {
			// Handle Hue differently (due to its circular nature)
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					float val = img.getValue(x, y);
					if (Float.isNaN(val))
						continue;
					double alpha = val * 2 * Math.PI;
					sinX += Math.sin(alpha);
					cosX += Math.cos(alpha);
				}				
			}
		}
		
		public double getMeanHue() {
			return Math.atan2(sinX, cosX) / (2 * Math.PI) + 0.5;
		}
		
	}
	
	
	
	
	
	static class CumulativeHistogramFeatureComputerBuilder implements FeatureComputerBuilder {
		
		private int originalBitsPerPixel;

		@Override
		public void addParameters(ImageData<?> imageData, ParameterList params) {
			this.originalBitsPerPixel = imageData.getServer().getPixelType().getBitsPerPixel();
			if (originalBitsPerPixel > 16)
				return;
			params.addTitleParameter("Cumulative histogram");
			params.addBooleanParameter("doCumulativeHistogram", "Cumulative histogram", false);
			params.addDoubleParameter("chMinValue", "Min histogram value", 0);
			params.addDoubleParameter("chMaxValue", "Max histogram value", 1);
			params.addIntParameter("chBins", "Number of bins", 5);
		}

		@Override
		public FeatureComputer build() {
			return new CumulativeHistogramFeatureComputer();
		}
		
	}
	
	
	
	
	
	
	
	
	/**
	 * The following classes aren't currently used, but they may be useful one day (perhaps in a new command).
	 * 
	 * The idea is this: to compute a small histogram, normalized to area, for a specific channel.
	 * Doing so then makes it possible to include both intensity and proportion information in a single threshold.
	 * For example, a threshold may be set to identify cells exhibiting > 10% positive staining.
	 * 
	 * Values in the final output indicate the proportion of the region's staining that is >= each threshold value 
	 * (or, equivalently, the left edges of the histogram bins).
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class CumulativeHistogramFeatureComputer implements FeatureComputer {
		
		private double minBin, maxBin;
		private long n;
		private int nBins;
		private long[] histogram;
		
		@Override
		public void updateFeatures(SimpleImage img, FeatureColorTransform transform, ParameterList params) {
			// Don't do anything if we don't have a meaningful transform
			if (!Boolean.TRUE.equals(params.getBooleanParameterValue("doCumulativeHistogram")) || transform == null || transform == FeatureColorTransformEnum.HUE || (histogram != null && histogram.length == 0))
				return;
			
			// Create a new histogram if we need one
			if (histogram == null) {
				minBin = params.getDoubleParameterValue("chMinValue");
				maxBin = params.getDoubleParameterValue("chMaxValue");
				nBins = params.getIntParameterValue("chBins");
				// Create histogram
				histogram = new long[nBins];
			}
			
			// Check we can do anything
			if (nBins == 0)
				return;
			
			// Loop through and update histogram
			double binWidth = (maxBin - minBin) / (nBins - 1);
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					double val = img.getValue(x, y);
					if (!Double.isFinite(val))
						continue;
					int bin = (int)((val - minBin)/binWidth);
					if (bin >= nBins)
						histogram[nBins-1]++;
					else if (bin < 0)
						histogram[0]++;
					else
						histogram[bin]++;
					n++;
				}
			}
		}

		@Override
		public void addMeasurements(PathObject pathObject, String name, ParameterList params) {
			// Check if we have a median we can use
			if (histogram == null || histogram.length == 0)
				return;
			
			// Start from the end & update
			double total = 0;
			double[] proportions = new double[histogram.length];
			for (int i = histogram.length-1; i >= 0; i--) {
				total += histogram[i] / (double)n;
				proportions[i] = total;
			}
			
			// Add the measurements
			MeasurementList measurementList = pathObject.getMeasurementList();
			double binWidth = (maxBin - minBin) / (nBins - 1);
			NumberFormat formatter = GeneralTools.createFormatter(3);
			for (int i = 0; i < histogram.length; i++) {
				double value = minBin + i * binWidth;
				measurementList.putMeasurement(name + " >= " + formatter.format(value), proportions[i]);
			}
		}
		
	}
	
	
}
