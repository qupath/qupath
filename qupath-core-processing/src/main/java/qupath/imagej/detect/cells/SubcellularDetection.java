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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.gui.PolygonRoi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.plugin.filter.EDM;
import ij.plugin.filter.MaximumFinder;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.processing.SimpleThresholding;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.StainVector;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.measurements.MeasurementList.MeasurementListType;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.plugins.AbstractInteractivePlugin;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Experimental plugin to help with the quantification of subcellular structures.
 * 
 * @author Pete Bankhead
 *
 */
public class SubcellularDetection extends AbstractInteractivePlugin<BufferedImage> {
	
	private final static Logger logger = LoggerFactory.getLogger(SubcellularDetection.class);
	
	
	@Override
	public boolean runPlugin(final PluginRunner<BufferedImage> pluginRunner, final String arg) {
		boolean success = super.runPlugin(pluginRunner, arg);
		getHierarchy(pluginRunner).fireHierarchyChangedEvent(this);
		return success;
	}
	
	
	@Override
	protected void addRunnableTasks(final ImageData<BufferedImage> imageData, final PathObject parentObject, List<Runnable> tasks) {
		final ParameterList params = getParameterList(imageData);
		tasks.add(new SubcellularDetectionRunnable(imageData, parentObject, params));
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
//		if (regionStore != null) {
//			int n = tasks.size();
//			Runnable[] tasks2 = new Runnable[n];
//			if (rearrangeByStride(tasks, tasks2, Runtime.getRuntime().availableProcessors()))
//				tasks = Arrays.asList(tasks2);
//		}
//		return tasks;
//	}
	
	
	static class SubcellularDetectionRunnable implements Runnable {
		
		private ImageData<BufferedImage> imageData;
		private ParameterList params;
		private PathObject parentObject;
		
		public SubcellularDetectionRunnable(final ImageData<BufferedImage> imageData, final PathObject parentObject, final ParameterList params) {
			this.imageData = imageData;
			this.parentObject = parentObject;
			this.params = params;
		}

		@Override
		public void run() {
			try {
				if (parentObject instanceof PathCellObject)
					processObject(parentObject, params, new ImageWrapper(imageData));
				else {
					List<PathObject> cellObjects = PathObjectTools.getFlattenedObjectList(parentObject, null, false).stream().filter(p -> p instanceof PathCellObject).collect(Collectors.toList());
					for (PathObject cell : cellObjects)
						processObject(cell, params, new ImageWrapper(imageData));
				}
			} catch (InterruptedException e) {
				logger.error("Processing interrupted", e);
			} catch (IOException e) {
				logger.error("Error processing " + parentObject, e);
			} finally {
				parentObject.getMeasurementList().close();
				imageData = null;
				params = null;
			}
		}
		
		
		@Override
		public String toString() {
			// TODO: Give a better toString()
			return "Subcellular detection";
		}
		
	}
	
	/**
	 * Initial version of subcellular detection processing.
	 * 
	 * @param pathObject
	 * @param params
	 * @param imageWrapper
	 * @return
	 * @throws InterruptedException
	 * @throws IOException 
	 */
	static boolean processObject(final PathObject pathObject, final ParameterList params, final ImageWrapper imageWrapper) throws InterruptedException, IOException {

		// Get the base classification for the object as it currently stands
		PathClass baseClass = PathClassTools.getNonIntensityAncestorClass(pathObject.getPathClass());
		
		// Variable to hold estimated spot count
		double estimatedSpots;

		// We assume that after this processing, any previous sub-cellular objects should be removed
		pathObject.clearPathObjects();

		// Ensure we have no existing subcellular detection measurements - if we do, remove them
		String[] existingMeasurements = pathObject.getMeasurementList().getMeasurementNames().stream().filter(n -> n.startsWith("Subcellular:")).toArray(n -> new String[n]);
		if (existingMeasurements.length > 0) {
			pathObject.getMeasurementList().removeMeasurements(existingMeasurements);
			pathObject.getMeasurementList().close();
		}

		//		// If we're part of a TMA core, request the whole core...
		//		if (pathObject.getParent() instanceof TMACoreObject && pathObject.getParent().hasROI()) {
		//			regionStore.getImage(server, RegionRequest.createInstance(server.getPath(), 1, pathObject.getParent().getROI()), 25, true);
		//		}

		ROI pathROI = pathObject.getROI();
		if (pathROI == null || pathROI.isEmpty())
			return false;

		//		double downsample = 0.5;
		double downsample = 1;

		// Determine spot size
		ImageServer<BufferedImage> server = imageWrapper.getServer();
		PixelCalibration cal = server.getPixelCalibration();
		double spotSizeMicrons = cal.hasPixelSizeMicrons() ? params.getDoubleParameterValue("spotSizeMicrons") : Double.NaN;
		double minSpotSizeMicrons = cal.hasPixelSizeMicrons() ? params.getDoubleParameterValue("minSpotSizeMicrons") : Double.NaN;
		double maxSpotSizeMicrons = cal.hasPixelSizeMicrons() ? params.getDoubleParameterValue("maxSpotSizeMicrons") : Double.NaN;
		double pixelWidth = cal.getPixelWidthMicrons() * downsample;
		double pixelHeight = cal.getPixelHeightMicrons() * downsample;
		double singleSpotArea = spotSizeMicrons / (pixelWidth * pixelHeight);
		double minSpotArea = minSpotSizeMicrons / (pixelWidth * pixelHeight);
		double maxSpotArea = maxSpotSizeMicrons / (pixelWidth * pixelHeight);
		boolean includeClusters = Boolean.TRUE.equals(params.getBooleanParameterValue("includeClusters"));
		boolean doSmoothing = Boolean.TRUE.equals(params.getBooleanParameterValue("doSmoothing"));
		boolean splitByIntensity = Boolean.TRUE.equals(params.getBooleanParameterValue("splitByIntensity"));
		boolean splitByShape = Boolean.TRUE.equals(params.getBooleanParameterValue("splitByShape"));
		
		// Get region to request - give a pixel as border
		int xStart = (int)Math.max(0, pathROI.getBoundsX() - 1);
		int yStart = (int)Math.max(0, pathROI.getBoundsY() - 1);
		int width = (int)Math.min(server.getWidth()-1, pathROI.getBoundsX() + pathROI.getBoundsWidth() + 1.5) - xStart;
		int height = (int)Math.min(server.getHeight()-1, pathROI.getBoundsY() + pathROI.getBoundsHeight() + 1.5) - yStart;

		if (width <= 0 || height <= 0) {
			logger.error("Negative ROI size for {}", pathROI);
			pathObject.setPathClass(baseClass);
			return false;
		}

		RegionRequest region = RegionRequest.createInstance(server.getPath(), 1.0, xStart, yStart, width, height, pathROI.getT(), pathROI.getZ());

		// Mask to indicate pixels within the cell
		byte[] cellMask = null;

		for (String channelName : imageWrapper.getChannelNames(true, true)) {
			
			double detectionThreshold = params.getDoubleParameterValue("detection["+channelName+"]");
			if (Double.isNaN(detectionThreshold) || detectionThreshold < 0)
				continue;

			SimpleImage img = imageWrapper.getRegion(region, channelName);

			// Get an ImageJ-friendly calibration for ROI conversion
			Calibration calIJ = new Calibration();
			calIJ.xOrigin = -xStart/downsample;
			calIJ.yOrigin = -yStart/downsample;

			// Create a cell mask
			if (cellMask == null) {
				BufferedImage imgMask = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
				Graphics2D g2d = imgMask.createGraphics();
				if (downsample != 1)
					g2d.scale(1.0/downsample, 1.0/downsample);
				g2d.translate(-xStart, -yStart);

				Shape shape = RoiTools.getShape(pathROI);
				g2d.setColor(Color.WHITE);
				g2d.fill(shape);
				g2d.dispose();
				cellMask = (byte[])((DataBufferByte)imgMask.getRaster().getDataBuffer()).getData(0);				
			}

			// Get a buffer containing the image pixels
			int w = img.getWidth();
			int h = img.getHeight();

			// Identify (& try to separate) spots
			// Mask out non-cell areas as we go
			FloatProcessor fpDetection = new FloatProcessor(w, h);
			if (doSmoothing) {
				for (int i = 0; i < w*h; i++)
					fpDetection.setf(i, img.getValue(i%w, i/w));
				fpDetection.smooth();
				for (int i = 0; i < w*h; i++) {
					if (cellMask[i] == (byte)0)
						fpDetection.setf(i, 0f);
				}
			} else {
				for (int i = 0; i < w*h; i++) {
					if (cellMask[i] == (byte)0)
						fpDetection.setf(i, 0f);
					else
						fpDetection.setf(i, img.getValue(i%w, i/w));
				}
			}
			ByteProcessor bpSpots;
			if (splitByIntensity)
				bpSpots = new MaximumFinder().findMaxima(fpDetection, detectionThreshold/10.0, detectionThreshold, MaximumFinder.SEGMENTED, false, false);
			else
				bpSpots = SimpleThresholding.thresholdAboveEquals(fpDetection, (float)detectionThreshold);
			
			if (splitByShape) {
				new EDM().toWatershed(bpSpots);
			}

			// Loop through spot ROIs & make a decision
			bpSpots.setThreshold(1, ImageProcessor.NO_THRESHOLD, ImageProcessor.NO_LUT_UPDATE);
			List<PolygonRoi> possibleSpotRois = RoiLabeling.getFilledPolygonROIs(bpSpots, Wand.FOUR_CONNECTED);
			List<PathObject> spotObjects = new ArrayList<>();
			List<PathObject> clusterObjects = new ArrayList<>();
			estimatedSpots = 0;
			for (PolygonRoi spotRoi : possibleSpotRois) {
				fpDetection.setRoi(spotRoi);
				ImageStatistics stats = fpDetection.getStatistics();
				
				ImagePlane plane = ImagePlane.getPlaneWithChannel(spotRoi.getCPosition(), spotRoi.getZPosition(), spotRoi.getTPosition());

				PathObject cluster = null;
				if (stats.pixelCount > minSpotArea && stats.pixelCount <= maxSpotArea) {
					ROI roi = IJTools.convertToROI(spotRoi, calIJ, downsample, plane);
//					cluster = new SubcellularObject(roi, 1);
					cluster = createSubcellularObject(roi, 1);
					estimatedSpots += 1;
				} else if (includeClusters && stats.pixelCount > minSpotArea) {
					// Add a cluster
					ROI roi = IJTools.convertToROI(spotRoi, calIJ, downsample, plane);
					double nSpots = stats.pixelCount / singleSpotArea;
					estimatedSpots += nSpots;
//					cluster = new SubcellularObject(roi, nSpots);
					cluster = createSubcellularObject(roi, nSpots);
				}
				if (cluster != null) {
					
					boolean isCluster = cluster.getMeasurementList().getMeasurementValue("Num spots") > 1;
					int rgb = imageWrapper.getChannelColor(channelName);
					rgb = isCluster ? ColorTools.makeScaledRGB(rgb, 0.5) : ColorTools.makeScaledRGB(rgb, 1.5);
					PathClass pathClass = PathClassFactory.getDerivedPathClass(cluster.getPathClass(), channelName + " object", rgb);
					cluster.setPathClass(pathClass);
					
					cluster.getMeasurementList().putMeasurement("Subcellular cluster: " + channelName + ": Area", stats.pixelCount * pixelWidth * pixelHeight);					
					cluster.getMeasurementList().putMeasurement("Subcellular cluster: " + channelName +  ": Mean channel intensity", stats.mean);
//					cluster.getMeasurementList().putMeasurement("Subcellular cluster: " + channelName +  ": Max channel intensity", stats.max);
					cluster.getMeasurementList().close();
					spotObjects.add(cluster);
				}
			}

			// Add measurements
			MeasurementList measurementList = pathObject.getMeasurementList();
			measurementList.putMeasurement("Subcellular: " + channelName +  ": Num spots estimated", estimatedSpots);
			measurementList.putMeasurement("Subcellular: " + channelName +  ": Num single spots", spotObjects.size());
			measurementList.putMeasurement("Subcellular: " + channelName +  ": Num clusters", clusterObjects.size());

			// Add spots
			pathObject.addPathObjects(spotObjects);
			pathObject.addPathObjects(clusterObjects);

		}
		return true;
	}
	
	
	
	@Override
	public ParameterList getDefaultParameterList(final ImageData<BufferedImage> imageData) {
		
		ParameterList params = new ParameterList()
				.addTitleParameter("Detection parameters");
		
		for (String name : new ImageWrapper(imageData).getChannelNames(true, true)) {
			params.addDoubleParameter("detection["+name+"]", "Detection threshold (" + name + ")", -1.0, "", "Intensity threshold for detection - if < 0, no detection will be applied to this channel");
		}
		params.addBooleanParameter("doSmoothing", "Smooth before detection", false, "Apply 3x3 smoothing filter to reduce noise prior to detection");
		params.addBooleanParameter("splitByIntensity", "Split by intensity", false, "Attempt to split merged spots based on intensity peaks");
		params.addBooleanParameter("splitByShape", "Split by shape", false, "Attempt to split merged spots according to shape (i.e. looking for rounder spots)");
		
		params.addTitleParameter("Spot & cluster parameters");
		params.addDoubleParameter("spotSizeMicrons", "Expected spot size", 1, GeneralTools.micrometerSymbol()+"^2", "Estimated area of a single spot - used to estimate total spot counts");
		params.addDoubleParameter("minSpotSizeMicrons", "Min spot size", 0.5, GeneralTools.micrometerSymbol()+"^2", "Minimum spot area - smaller spots will be excluded");
		params.addDoubleParameter("maxSpotSizeMicrons", "Max spot size", 2.0, GeneralTools.micrometerSymbol()+"^2", "Maximum spot area - larger spots will be counted as clusters");
		
		params.addBooleanParameter("includeClusters", "Include clusters", true, "Store anything larger than 'Max spot size' as a cluster, instead of ignoring it");
		
		boolean hasMicrons = imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		params.setHiddenParameters(!hasMicrons, "spotSizeMicrons", "minSpotSizeMicrons", "maxSpotSizeMicrons", "includeClusters");
		return params;
	}

	@Override
	public String getName() {
		return "Subcellular spot detection";
	}

	@Override
	public String getLastResultsDescription() {
		return "";
	}

	@Override
	public String getDescription() {
		return "Add subcellular detections to existing cells";
	}

	@Override
	protected Collection<PathObject> getParentObjects(final PluginRunner<BufferedImage> runner) {
		Collection<Class<? extends PathObject>> parentClasses = getSupportedParentObjectClasses();
		List<PathObject> parents = new ArrayList<>();
		for (PathObject parent : getHierarchy(runner).getSelectionModel().getSelectedObjects()) {
			for (Class<? extends PathObject> cls : parentClasses) {
				if (cls.isAssignableFrom(parent.getClass())) {
					parents.add(parent);
					break;
				}
			}
		}
		return parents;
	}

	@Override
	public Collection<Class<? extends PathObject>> getSupportedParentObjectClasses() {
		List<Class<? extends PathObject>> parents = new ArrayList<>();
		parents.add(TMACoreObject.class);
		parents.add(PathAnnotationObject.class);
		parents.add(PathCellObject.class);
		return parents;
	}
	
	
	static PathObject createSubcellularObject(final ROI roi, final double nSpots) {
		var pathObject = PathObjects.createDetectionObject(roi);
		if (nSpots != 1)
			pathObject.setPathClass(PathClassFactory.getPathClass("Subcellular cluster", ColorTools.makeRGB(220, 200, 50)));
		else
			pathObject.setPathClass(PathClassFactory.getPathClass("Subcellular spot", ColorTools.makeRGB(100, 220, 50)));
		pathObject.getMeasurementList().putMeasurement("Num spots", nSpots);
		pathObject.getMeasurementList().close();
		return pathObject;
	}
	
	
	/**
	 * Special class for representing a subcellular detection object.
	 * <p>
	 * This should NO LONGER BE USED; subclassing PathObjects is <i>not</i> a good idea.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	@Deprecated
	static class SubcellularObject extends PathDetectionObject {
		
		final private static long serialVersionUID = 1L;
		
		static Integer color = ColorTools.makeRGB(200, 200, 50);
		
		public SubcellularObject() {
			super();
		};
		
		SubcellularObject(final ROI roi, final double nSpots) {
			super(roi, null);
			if (nSpots != 1)
				setPathClass(PathClassFactory.getPathClass("Subcellular cluster", ColorTools.makeRGB(220, 200, 50)));
			else
				setPathClass(PathClassFactory.getPathClass("Subcellular spot", ColorTools.makeRGB(100, 220, 50)));
			getMeasurementList().putMeasurement("Num spots", nSpots);
			getMeasurementList().close();
//			color = isCluster ? ColorTools.makeRGB(220, 200, 50) : ColorTools.makeRGB(100, 220, 50);
		}
		
		/**
		 * Always returns false - subcellular objects shouldn't be edited.
		 */
		@Override
		public boolean isEditable() {
			return false;
		}
		
		/**
		 * Default to a simple, float measurement list.
		 */
		@Override
		protected MeasurementList createEmptyMeasurementList() {
			return MeasurementListFactory.createMeasurementList(0, MeasurementListType.FLOAT);
		}
		
		@Override
		public void setPathClass(final PathClass pathClass, final double probability) {
			super.setPathClass(pathClass, probability);
		}
		
		@Override
		public String getName() {
			return super.getName();
//			return "Subcellular cluster";
		}
		
		@Override
		public String getDisplayedName() {
			return super.getDisplayedName();
//			return getName();
		}
		
	}
	
	
	/**
	 * Wrapper class to help extract multiple channels from an image that may or may not 
	 * require color deconvolution for meaningful channel separation.
	 * <p>
	 * Note: This may be moved into its own class at a later date...
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class ImageWrapper {
		
		private final ImageData<BufferedImage> imageData;
		private Map<RegionRequest, BufferedImage> cachedRegions = new HashMap<>();
		
		public ImageWrapper(final ImageData<BufferedImage> imageData) {
			this.imageData = imageData;
		}
		
		public ImageServer<BufferedImage> getServer() {
			return imageData.getServer();
		}
		
		/**
		 * Request pixels for a specified channel.
		 * 
		 * If ColorDeconvolutionStains are available, these will be used.
		 * 
		 * @param region
		 * @param channelName
		 * @return
		 * @throws IOException 
		 */
		public SimpleImage getRegion(final RegionRequest region, final String channelName) throws IOException {
			for (int i = 0; i < nChannels(); i++) {
				if (channelName.equals(getChannelName(i)))
					return getRegion(region, i);
			}
			return null;
		}
		
		/**
		 * Request pixels for a specified (0-based) channel.
		 * 
		 * If ColorDeconvolutionStains are available, these will be used.
		 * 
		 * @param region
		 * @param channel
		 * @return
		 * @throws IOException 
		 */
		public SimpleImage getRegion(final RegionRequest region, final int channel) throws IOException {
			BufferedImage img = getBufferedImage(region);
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			float[] pixels = null;
			int w = img.getWidth();
			int h = img.getHeight();
			if (stains != null) {
				int[] buf = img.getRGB(0, 0, w, h, null, 0, w);
				switch (channel) {
					case 0:
						pixels = ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Stain_1, pixels, stains);
						break;
					case 1:
						pixels = ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Stain_2, pixels, stains);
						break;
					case 2:
						pixels = ColorTransformer.getTransformedPixels(buf, ColorTransformMethod.Stain_3, pixels, stains);
						break;
					default:
						throw new IllegalArgumentException("Specified channel should be 0, 1, or 2!");
				}
//				pixels = ColorDeconvolution.colorDeconvolveRGBArray(buf, stains, channel, pixels);
			} else {
				pixels = img.getData().getSamples(0, 0, w, h, channel, pixels);
			}
			return SimpleImages.createFloatImage(pixels, w, h);
		}
		
		/**
		 * Get a suitable color for a channel, or 0 if no color could be determined;
		 * 
		 * @param channelName
		 * @return
		 */
		public int getChannelColor(final String channelName) {
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			if (stains != null) {
				for (int i = 1; i <= 3; i++) {
					if (channelName.equals(stains.getStain(i).getName()))
							return stains.getStain(i).getColor();
				}
			}
			for (int i = 0; i < nChannels(); i++) {
				if (channelName.equals(getChannelName(i)))
					return imageData.getServer().getChannel(i).getColor();
			}
			return 0;
		}
		
		/**
		 * Get the name of a channel.
		 * 
		 * If there are ColorDeconvolutionStains stains present, these are used.
		 * 
		 * If not, then requesting channel=N returns the name "Channel (N+1)".
		 * 
		 * (In other words, the channel is requested with a 0-based index, but the returned name uses a 1-based index.)
		 * 
		 * @param channel 0-based channel number.
		 * @return
		 */
		public String getChannelName(final int channel) {
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			if (stains != null)
				return stains.getStain(channel+1).getName();
			return "Channel " + (channel + 1);
		}
		
		/**
		 * Get the number of channels in the image.
		 * 
		 * For an RGB image, this will be 3 - even if only 2 color deconvolutions stains are present.
		 * 
		 * @return
		 */
		public int nChannels() {
			return imageData.getServer().nChannels();
		}

		/**
		 * Get the names of available channels, optionally discounting any 'residual' or hematoxylin channels if color deconvolution should be used.
		 * 
		 * @param skipHematoxylin
		 * @param skipResidual
		 * @return
		 */
		public List<String> getChannelNames(final boolean skipHematoxylin, final boolean skipResidual) {
			List<String> names = new ArrayList<>();
			ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
			if (stains != null) {
				for (int i = 1; i <= 3; i++) {
					StainVector stain = stains.getStain(i);
					if (skipResidual && stain.isResidual())
						continue;
					if (skipHematoxylin && ColorDeconvolutionStains.isHematoxylin(stain))
						continue;
					names.add(stain.getName());
				}
			} else {
				for (int i = 0; i < nChannels(); i++) {
					names.add(getChannelName(i));
				}
			}
			return names;
		}
		

		private BufferedImage getBufferedImage(final RegionRequest region) throws IOException {
			if (cachedRegions.containsKey(region))
				return cachedRegions.get(region);
			
			BufferedImage img = imageData.getServer().readBufferedImage(region);
			cachedRegions.put(region, img);
			return img;
		}
		
		
	}
	
	

}