/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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


package qupath.lib.analysis.heatmaps;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.heatmaps.ColorModels.ColorModelBuilder;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.geom.Point2;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectPredicates.PathObjectPredicate;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ml.pixel.PixelClassifierTools;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.tools.OpenCVTools;
import qupath.opencv.tools.OpenCVTools.IndexedPixel;
import qupath.opencv.ml.pixel.PixelClassifierTools.CreateObjectOptions;

/**
 * Class for constructing and using density maps.
 * <p>
 * A density map is an {@link ImageServer} with the channel type {@link ChannelType#DENSITY}.
 * The pixel values relate to the density of objects of some kind, based upon applying one or more predicates.
 * <p>
 * Currently, only 2D density maps are supported.
 * There are several supported density map types, defined with {@link DensityMapType}.
 * 
 * @author Pete Bankhead
 */
public class DensityMaps {
	
	private static final Logger logger = LoggerFactory.getLogger(DensityMaps.class);
	
	/**
	 * Channel name for the channel with all object counts (not always present).
	 */
	public static final String CHANNEL_ALL_OBJECTS = "Counts";
	
	private static int preferredTileSize = 2048;
	
	/**
	 * Default location to use when storing density maps in a project.
	 * @see Project#getResources(String, Class, String)
	 */
	public static final String PROJECT_LOCATION = "classifiers/density_maps";
	
	
	/**
	 * Density map types.
	 */
	public static enum DensityMapType {
		
		/**
		 * No normalization; maps provide raw object counts in a defined radius.
		 * This is equivalent to applying a circular sum filter to object counts per pixel.
		 */
		SUM,
		
		/**
		 * Gaussian-weighted area normalization; maps provide weighted averaged object counts in a defined radius.
		 * This is equivalent to applying a Gaussian filter to object counts per pixel.
		 */
		GAUSSIAN,
		
		/**
		 * Maps contain at least two channels. The last channel contains the total count of objects within 
		 * the density region.
		 * All other channels contain the proportion of objects meeting specific criteria, expressed as a 
		 * percentage of the corresponding total object count.
		 * <p>
		 * This is useful, for example, to identify the Positive % cells.
		 */
		PERCENT;
		
		@Override
		public String toString() {
			switch(this) {
			case SUM:
				return "By area (raw counts)";
			case PERCENT:
				return "Objects %";
			case GAUSSIAN:
				return "Gaussian-weighted";
			default:
				throw new IllegalArgumentException("Unknown enum " + this);
			}
		}
		
	}
	
	
	/**
	 * Create a new {@link DensityMapBuilder} to generate a customized density map.
	 * @param mainObjectFilter predicate to identify which objects will be included in the density map
	 * @return the builder
	 */
	public static DensityMapBuilder builder(PathObjectPredicate mainObjectFilter) {
		return new DensityMapBuilder(mainObjectFilter);
	}
	
	/**
	 * Create a new {@link DensityMapBuilder} initialized with the same properties as an existing builder.
	 * @param builder the existing builder
	 * @return the new builder
	 */
	public static DensityMapBuilder builder(DensityMapBuilder builder) {
		return new DensityMapBuilder(builder.params);
	}
	
	
	
	/**
	 * Class for storing parameters to build a {@link ImageServer} representing a density map.
	 * @see DensityMapBuilder
	 */
	public static class DensityMapParameters {
		
		private PixelCalibration pixelSize = null;
		
		private int maxWidth = 1536;
		private int maxHeight = maxWidth;
		
		private double radius = 0;
		private DensityMapType densityType = DensityMapType.SUM;

		private PathObjectPredicate mainObjectFilter;
		private Map<String, PathObjectPredicate> secondaryObjectFilters = new LinkedHashMap<>();
		
		private DensityMapParameters() {}
		
		private DensityMapParameters(DensityMapParameters params) {
			this.pixelSize = params.pixelSize;
			this.radius = params.radius;
			this.densityType = params.densityType;
			this.maxWidth = params.maxWidth;
			this.maxHeight = params.maxHeight;
			
			this.mainObjectFilter = params.mainObjectFilter;
			this.secondaryObjectFilters = new LinkedHashMap<>(params.secondaryObjectFilters);
		}
		
		/**
		 * Get the radius for the density map, in calibrated units.
		 * @return
		 */
		public double getRadius() {
			return radius;
		}
		
		/**
		 * Get the maximum width of the density map. Ignored if {@link #getPixelSize()} is not null.
		 * @return
		 */
		public int getMaxWidth() {
			return maxWidth;
		}
		
		/**
		 * Get the maximum height of the density map. Ignored if {@link #getPixelSize()} is not null.
		 * @return
		 */
		public int getMaxHeight() {
			return maxHeight;
		}
		
		/**
		 * Get the requested pixel size for the density map. This may be null if an appropriate resolution can be generated automatically.
		 * @return
		 */
		public PixelCalibration getPixelSize() {
			return pixelSize;
		}
		
		/**
		 * Get the normalization type of the density map.
		 * @return
		 */
		public DensityMapType getDensityType() {
			return densityType;
		}
		
		/**
		 * Get the primary object filter.
		 * @return
		 */
		public PathObjectPredicate getMainObjectFilter() {
			return mainObjectFilter;
		}
		
		/**
		 * Get the secondary object filters.
		 * @return
		 */
		public Map<String, PathObjectPredicate> getSecondaryObjectFilters() {
			return Collections.unmodifiableMap(secondaryObjectFilters);
		}
		
	}
	
	
	/**
	 * Builder for an {@link ImageServer} representing a density map or for {@link DensityMapParameters}.
	 */
	public static class DensityMapBuilder {
		
		private DensityMapParameters params = null;
		private ColorModelBuilder colorModelBuilder = null;
		
		private DensityMapBuilder(DensityMapParameters params) {
			Objects.requireNonNull(params);
			this.params = new DensityMapParameters(params);
		}
		
		private DensityMapBuilder(PathObjectPredicate allObjects) {
			Objects.requireNonNull(allObjects);
			params = new DensityMapParameters();
			params.mainObjectFilter = allObjects;
		}
		
		/**
		 * Requested pixel size to determine the resolution of the density map, in calibrated units.
		 * <p>
		 * If this is not specified, an {@link ImageData} should be provided to {@link #buildClassifier(ImageData)} 
		 * and used to determine a suitable pixel size based upon the radius value and the image dimensions.
		 * <p>
		 * This is recommended, since specifying a pixel size could potentially result in creating maps 
		 * that are too large or too small, causing performance or memory problems.
		 * 
		 * @param requestedPixelSize
		 * @return this builder
		 * @see #radius(double)
		 */
		public DensityMapBuilder pixelSize(PixelCalibration requestedPixelSize) {
			params.pixelSize = requestedPixelSize;
			return this;
		}
		
		/**
		 * The type of the density map, which determines any associated normalization.
		 * @param type
		 * @return this builder
		 */
		public DensityMapBuilder type(DensityMapType type) {
			params.densityType = type;
			return this;
		}
		
		/**
		 * Add a filter for computing densities.
		 * This is added on top of the filter specified in {@link DensityMaps#builder(PathObjectPredicate)} to 
		 * extract a subset of objects for which densities are determined.
		 * 
		 * @param name name of the filter; usually this is the name of a classification that the objects should have
		 * @param filter the filter itself (predicate that must be JSON-serializable)
		 * @return this builder
		 */
		public DensityMapBuilder addDensities(String name, PathObjectPredicate filter) {
			params.secondaryObjectFilters.put(name, filter);
			return this;
		}
			
		/**
		 * Set a {@link ColorModelBuilder} that can be used in conjunction with {@link #buildServer(ImageData)}.
		 * If this is not set, the default {@link ColorModel} used with {@link #buildServer(ImageData)} may not 
		 * convert well to RGB.
		 * @param colorModelBuilder
		 * @return
		 */
		public DensityMapBuilder colorModel(ColorModelBuilder colorModelBuilder) {
			this.colorModelBuilder = colorModelBuilder;
			return this;
		}
		
		
		/**
		 * The radius of the filter used to calculate densities.
		 * @param radius
		 * @return this builder
		 */
		public DensityMapBuilder radius(double radius) {
			params.radius = radius;
			return this;
		}
		
		/**
		 * Build a {@link DensityMapParameters} object containing the main density map parameters.
		 * @return
		 */
		public DensityMapParameters buildParameters() {
			return new DensityMapParameters(params);
		}
		
		
		/**
		 * Build a {@link PixelClassifier} for a density map using the current parameters and the specified {@link ImageData}.
		 * @param imageData
		 * @return the density map
		 * @see #buildServer(ImageData)
		 */
		public PixelClassifier buildClassifier(ImageData<BufferedImage> imageData) {
			logger.debug("Building density map classifier for {}", imageData);
			return createClassifier(imageData, params);
		}
		
		/**
		 * Build an {@link ImageServer} representing this density map.
		 * <p>
		 * Note that this involved generating a unique ID and caching all tiles.
		 * The reason is that density maps can change over time as the object hierarchy changes, 
		 * and therefore one should be generated that represents a snapshot in time.
		 * However, this imposes a limit on the size of density map that can be generated to 
		 * avoid memory errors.
		 * <p>
		 * If greater control is needed over when and how the density map is created, using {@link #buildClassifier(ImageData)} instead.
		 * 
		 * @param imageData
		 * @return
		 * @see #buildClassifier(ImageData)
		 */
		public ImageServer<BufferedImage> buildServer(ImageData<BufferedImage> imageData) {
			
			logger.debug("Building density map server for {}", imageData);

			var classifier = createClassifier(imageData, params);
			
			var id = UUID.randomUUID().toString();
			var sb = new StringBuilder();
			sb.append("Density map (radius=");
			sb.append(params.radius);
			sb.append(")-");
			sb.append(imageData.getServerPath());
			sb.append("-");
			sb.append(id);
			
			var colorModel = colorModelBuilder == null ? null : colorModelBuilder.build();
			return PixelClassifierTools.createPixelClassificationServer(imageData, classifier, sb.toString(), colorModel, true);
		}
		
	}
	
	
	
	private static PixelClassifier createClassifier(ImageData<BufferedImage> imageData, DensityMapParameters params) {
		
		var pixelSize = params.pixelSize;
		if (pixelSize == null) {
			if (imageData == null) {
				throw new IllegalArgumentException("You need to specify a pixel size or provide an ImageData to generate a density map!");
			}
			var cal = imageData.getServer().getPixelCalibration();
			double radius = params.radius;
			var server = imageData.getServer();
			
			double maxDownsample = Math.round(
					Math.max(
							server.getWidth() / (double)params.maxWidth,
							server.getHeight() / (double)params.maxHeight
							)
					);
			
			if (maxDownsample < 1)
				maxDownsample = 1;
			
			var minPixelSize = cal.createScaledInstance(maxDownsample, maxDownsample);
			if (radius > 0) {
				double radiusPixels = radius / cal.getAveragedPixelSize().doubleValue();
				double radiusDownsample = Math.round(radiusPixels / 10);
				pixelSize = cal.createScaledInstance(radiusDownsample, radiusDownsample);
			}
			if (pixelSize == null || pixelSize.getAveragedPixelSize().doubleValue() < minPixelSize.getAveragedPixelSize().doubleValue())
				pixelSize = minPixelSize;
		}
		
		int radiusInt = (int)Math.round(params.radius / pixelSize.getAveragedPixelSize().doubleValue());
		logger.debug("Creating classifier with pixel size {}, radius = {}", pixelSize, radiusInt);
					    
		var dataOp = new DensityMapDataOp(
		        radiusInt,
		        params.secondaryObjectFilters,
		        params.mainObjectFilter,
		        params.densityType
		);
		
		var metadata = new PixelClassifierMetadata.Builder()
			    .inputShape(preferredTileSize, preferredTileSize)
			    .inputResolution(pixelSize)
			    .setChannelType(ImageServerMetadata.ChannelType.DENSITY)
			    .outputChannels(dataOp.getChannels())
			    .build();

		return PixelClassifiers.createClassifier(dataOp, metadata);
	}
	
	/**
	 * Load a {@link DensityMapBuilder} from the specified path.
	 * @param path
	 * @return
	 * @throws IOException
	 */
	public static DensityMapBuilder loadDensityMap(Path path) throws IOException {
		logger.debug("Loading density map from {}", path);
		try (var reader = Files.newBufferedReader(path)) {
			return GsonTools.getInstance().fromJson(reader, DensityMapBuilder.class);
		}
	}
	
	/**
	 * Threshold a single channel of a density map to generate new annotations.
	 * 
	 * @param hierarchy hierarchy to which objects should be added
	 * @param densityServer density map
	 * @param channel channel to threshold; this is also used to determine the class name for the created annotations
	 * @param threshold threshold value
	 * @param options additional objects when creating the annotations
	 * @return true if changes were made, false otherwise
	 * @throws IOException 
	 */
	public static boolean threshold(PathObjectHierarchy hierarchy, ImageServer<BufferedImage> densityServer, int channel, double threshold, CreateObjectOptions... options) throws IOException {
		var pathClassName = densityServer.getChannel(channel).getName();
		return threshold(hierarchy, densityServer, Map.of(channel, threshold), pathClassName, options);
	}
	
	/**
	 * Threshold one or more channels of a density map to generate new annotations.
	 * 
	 * @param hierarchy hierarchy to which objects should be added
	 * @param densityServer density map
	 * @param thresholds map between channel numbers and thresholds
	 * @param pathClassName name of the classification to apply to the generated annotations
	 * @param options additional options to customize how annotations are created
	 * @return true if changes were made, false otherwise
	 * @throws IOException 
	 */
	public static boolean threshold(PathObjectHierarchy hierarchy, ImageServer<BufferedImage> densityServer, Map<Integer, ? extends Number> thresholds, String pathClassName, CreateObjectOptions... options) throws IOException {

		logger.debug("Thresholding {} with thresholds {}, options", densityServer, thresholds, Arrays.asList(options));

		// Apply threshold to densities
		PathClass lessThan = PathClass.StandardPathClasses.IGNORE;
		PathClass greaterThan = PathClass.fromString(pathClassName);
		
		// If we request to delete existing objects, apply this only to annotations with the target class
		var optionsList = Arrays.asList(options);
		boolean changes = false;
		if (optionsList.contains(CreateObjectOptions.DELETE_EXISTING)) {
			Collection<PathObject> toRemove;
			if (hierarchy.getSelectionModel().noSelection())
				toRemove = hierarchy.getAnnotationObjects().stream().filter(p -> p.getPathClass() == greaterThan).collect(Collectors.toList());
			else {
				toRemove = new HashSet<>();
				var selectedObjects = new LinkedHashSet<>(hierarchy.getSelectionModel().getSelectedObjects());
				for (var selected : selectedObjects) {
					PathObjectTools.getDescendantObjects(selected, toRemove, PathAnnotationObject.class);
				}
				// Don't remove selected objects
				toRemove.removeAll(selectedObjects);
			}
			if (!toRemove.isEmpty()) {
				hierarchy.removeObjects(toRemove, true);
				changes = true;
			}

			// Remove option
			options = optionsList.stream().filter(o -> o != CreateObjectOptions.DELETE_EXISTING).toArray(CreateObjectOptions[]::new);
		}
				
		var thresholdedServer = PixelClassifierTools.createThresholdServer(densityServer, thresholds, lessThan, greaterThan);
		
		return PixelClassifierTools.createAnnotationsFromPixelClassifier(hierarchy, thresholdedServer, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, options) | changes;
	}
	
	
	
	/**
	 * Find hotspots in a density map.
	 * 
	 * @param hierarchy hierarchy used to obtain selected objects and add hotspots
	 * @param densityServer the density map to query
	 * @param channel channel in which to find hotspots (usually 0)
	 * @param nHotspots maximum number of hotspots to find per selected annotation
	 * @param radius hotspot radius, in calibrated units
	 * @param minCount minimum value required in the 'count' channel (the last channel)
	 * @param hotspotClass the classification to apply to hotspots
	 * @param deleteExisting optionally delete existing annotations identified as hotspots
	 * @param peaksOnly optionally restrict hotspots to only include intensity peaks
	 * @throws IOException
	 */
	public static void findHotspots(PathObjectHierarchy hierarchy, ImageServer<BufferedImage> densityServer, int channel, 
			int nHotspots, double radius, double minCount, PathClass hotspotClass, boolean deleteExisting, boolean peaksOnly) throws IOException {

		if (nHotspots <= 0) {
			logger.warn("Number of hotspots requested is {}!", nHotspots);
			return;
		}
		
		logger.debug("Finding {} hotspots in {} for channel {}, radius {}", nHotspots, densityServer, channel, radius);
		
		Collection<PathObject> parents = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
		if (parents.isEmpty())
			parents = Collections.singleton(hierarchy.getRootObject());
		
		double downsample = densityServer.getDownsampleForResolution(0);
		var toDelete = new HashSet<PathObject>();
		
		// Handle deleting existing hotspots
		if (deleteExisting) {
			toDelete.addAll(hierarchy.getAnnotationObjects()
					.stream()
					.filter(p -> p.getPathClass() == hotspotClass && p.isAnnotation() && p.getName() != null && p.getName().startsWith("Hotspot"))
					.collect(Collectors.toList()));
		}

		
		// Convert radius to pixels
		double radiusPixels = radius / densityServer.getPixelCalibration().getAveragedPixelSize().doubleValue();
		
		try (@SuppressWarnings("unchecked")
		var scope = new PointerScope()) {
			
			for (var parent : parents) {
				
				ROI roi = parent.getROI();
												
				// We need a ROI to define the area of interest
				if (roi == null) {
					if (densityServer.nTimepoints() > 1 || densityServer.nZSlices() > 1) {
						logger.warn("Hotspot detection without a parent object not supported for images with multiple z-slices/timepoints.");
						logger.warn("I will apply detection to the first plane only. If you need hotspots elsewhere, create an annotation first and use it to define the ROI.");
					}
					roi = ROIs.createRectangleROI(0, 0, densityServer.getWidth(), densityServer.getHeight(), ImagePlane.getDefaultPlane());
				}
				
				// Erode the ROI & see if any hotspot could fit
				var roiEroded = RoiTools.buffer(roi, -radiusPixels);
				if (roiEroded.isEmpty() || roiEroded.getArea() == 0) {
					logger.warn("ROI is too small! Cannot detected hotspots with radius {} in {}", radius, parent);
					continue;
				}

				// Read the image
				var plane = roi.getImagePlane();
				RegionRequest request = RegionRequest.createInstance(densityServer.getPath(), downsample, 0, 0, densityServer.getWidth(), densityServer.getHeight(), plane.getZ(), plane.getT());
				var img = densityServer.readRegion(request);
								
				// Create a mask
				var imgMask = BufferedImageTools.createROIMask(img.getWidth(), img.getHeight(), roiEroded, request);

				// Switch to OpenCV
				var mat = OpenCVTools.imageToMat(img);
				var matMask = OpenCVTools.imageToMat(imgMask);
				
				// Find hotspots
				var channels = OpenCVTools.splitChannels(mat);
				var density = channels.get(channel);
				if (minCount > 0) {
					var thresholdMask = opencv_core.greaterThan(channels.get(channels.size()-1), minCount).asMat();
					opencv_core.bitwise_and(matMask, thresholdMask, matMask);
					thresholdMask.close();
				}

				// TODO: Limit to peaks
				if (peaksOnly) {
					var matMaxima = OpenCVTools.findRegionalMaxima(density);
					var matPeaks = OpenCVTools.shrinkLabels(matMaxima);
					matPeaks.put(opencv_core.greaterThan(matPeaks, 0));
					opencv_core.bitwise_and(matMask, matPeaks, matMask);
					matPeaks.close();
					matMaxima.close();
				}

				// Sort in descending order
				var maxima = new ArrayList<>(OpenCVTools.getMaskedPixels(density, matMask));
				Collections.sort(maxima, Comparator.comparingDouble((IndexedPixel p) -> p.getValue()).reversed());
				
				// Try to get as many maxima as we need
				// Impose minimum separation
				var points = maxima.stream().map(p -> new Point2(p.getX()*downsample, p.getY()*downsample)).collect(Collectors.toList());
				var hotspotCentroids = new ArrayList<Point2>();
				double distSqThreshold = radiusPixels * radiusPixels * 4;
				for (var p : points) {
					// Check not too close to an existing hotspot
					boolean skip = false;
					for (var p2 : hotspotCentroids) {
						if (p.distanceSq(p2) < distSqThreshold) {
							skip = true;
							break;
						}
					}
					if (!skip) {
						hotspotCentroids.add(p);
						if (hotspotCentroids.size() == nHotspots)
							break;
					}
				}
				
				var hotspots = new ArrayList<PathObject>();
				int i = 0;
				for (var p : hotspotCentroids) {
					i++;
					var ellipse = ROIs.createEllipseROI(p.getX()-radiusPixels, p.getY()-radiusPixels, radiusPixels*2, radiusPixels*2, roi.getImagePlane());
					var hotspot = PathObjects.createAnnotationObject(ellipse, hotspotClass);
					hotspot.setName("Hotspot " + i);
					hotspots.add(hotspot);
				}
				
				if (hotspots.isEmpty())
					logger.warn("No hotspots found in {}", parent);
				else if (hotspots.size() < nHotspots) {
					logger.warn("Only {}/{} hotspots could be found in {}", hotspots.size(), nHotspots, parent);
				}
				parent.addChildObjects(hotspots);
			}
			
			hierarchy.fireHierarchyChangedEvent(DensityMaps.class);

			if (!toDelete.isEmpty())
				hierarchy.removeObjects(toDelete, true);
		}
		
		
	}
	
	

}
