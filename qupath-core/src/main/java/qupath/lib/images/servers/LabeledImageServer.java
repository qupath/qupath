/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.images.servers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;


/**
 * A special ImageServer implementation that doesn't have a backing image, but rather
 * constructs tiles from a {@link PathObjectHierarchy} where pixel values are integer labels corresponding 
 * stored and classified annotations.
 * <p>
 * <i>Warning!</i> This is intend for temporary use when exporting labelled images. No attempt is made to 
 * respond to changes within the hierarchy. For consistent results, the hierarchy must remain static for the 
 * time in which this server is being used.
 * 
 * @author Pete Bankhead
 *
 */
public class LabeledImageServer extends AbstractTileableImageServer implements GeneratingImageServer<BufferedImage> {
	
	private static final Logger logger = LoggerFactory.getLogger(LabeledImageServer.class);
	
	private ImageServerMetadata originalMetadata;
	
	// Easy way to get the default color models...
	private static final ColorModel COLOR_MODEL_GRAY_UINT8 = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY).getColorModel();
	private static final ColorModel COLOR_MODEL_GRAY_UINT16 = new BufferedImage(1, 1, BufferedImage.TYPE_USHORT_GRAY).getColorModel();
	
	private PathObjectHierarchy hierarchy;
		
	private ColorModel colorModel;
	private boolean multichannelOutput;
	
	private LabeledServerParameters params;
	
	/**
	 * The maximum requested label; this is used to determine the output depth for indexed images.
	 */
	private int maxLabel;
	
	private Map<PathObject, Integer> instanceClassMap = null;	
	private Map<Integer, PathObject> instanceClassMapInverse = null;	
	
	private LabeledImageServer(final ImageData<BufferedImage> imageData, double downsample, int tileWidth, int tileHeight, LabeledServerParameters params, boolean multichannelOutput) {
		super();
		
		this.multichannelOutput = multichannelOutput;
		this.hierarchy = imageData.getHierarchy();
		
		this.params = params;
		
		var server = imageData.getServer();
		
		// Generate mapping for labels; it is permissible to have multiple classes for the same labels, in which case a derived class will be used
		Map<Integer, PathClass> classificationLabels = new TreeMap<>();
		if (params.createInstanceLabels) {
			var pathObjects = imageData.getHierarchy().getObjects(null, null).stream()
					.filter(params.objectFilter)
					.collect(Collectors.toCollection(ArrayList::new));
			// Shuffle the objects, this helps when using grayscale lookup tables, since labels for neighboring objects are otherwise very similar
			if (params.shuffleInstanceLabels)
				Collections.shuffle(pathObjects, new Random(100L));
			Integer count = multichannelOutput ? 0 : 1;
			instanceClassMap = new HashMap<>();
			instanceClassMapInverse = new HashMap<>();
			for (var pathObject : pathObjects) {
				var pathClass = instanceLabelToClass(count);
				instanceClassMap.put(pathObject, count);
				instanceClassMapInverse.put(count, pathObject);
				classificationLabels.put(count, pathClass);
				params.labelColors.put(count, pathClass.getColor());
				params.labels.put(pathClass, count);
				count++;
			}
		} else {
			for (var entry : params.labels.entrySet()) {
				var pathClass = getPathClass(entry.getKey());
				var label = entry.getValue();
				var previousClass = classificationLabels.put(label, pathClass);
				if (previousClass != null && previousClass != PathClass.NULL_CLASS) {
					classificationLabels.put(label, PathClass.getInstance(previousClass, pathClass.getName(), null));
				}
			}
		}
		
		for (var entry : params.boundaryLabels.entrySet()) {
			var pathClass = getPathClass(entry.getKey());
			var label = entry.getValue();
			var previousClass = classificationLabels.put(label, pathClass);
			if (previousClass != null && previousClass != PathClass.NULL_CLASS) {
				classificationLabels.put(label, PathClass.getInstance(previousClass, pathClass.getName(), null));
			}
		}
		
		if (tileWidth <= 0)
			tileWidth = 512;
		if (tileHeight <= 0)
			tileHeight = tileWidth;
		
		var metadataBuilder = new ImageServerMetadata.Builder(server.getMetadata())
				.preferredTileSize(tileWidth, tileHeight)
				.levelsFromDownsamples(downsample)
				.pixelType(PixelType.UINT8)
				.rgb(false);
		
		// Check the labels are valid
		var labelStats = classificationLabels.keySet().stream().mapToInt(i -> i).summaryStatistics();
		int minLabel = labelStats.getMin();
		maxLabel = labelStats.getMax();
		if (minLabel < 0) {
			throw new IllegalArgumentException("Minimum possible label value is 0! Requested minimum was " + maxLabel);
		}
		if (multichannelOutput) {
			int nChannels = maxLabel + 1;
			if (params.maxOutputChannelLimit > 0 && nChannels > params.maxOutputChannelLimit)
				throw new IllegalArgumentException("You've requested " + nChannels + " output channels, but the maximum supported number is " + params.maxOutputChannelLimit);
		}
		
		if (multichannelOutput) {
			int nLabels = maxLabel - minLabel + 1;
			if (minLabel != 0 || nLabels != classificationLabels.size()) {
				throw new IllegalArgumentException("Labels for multichannel output must be consecutive integers starting from 0! Requested labels " + classificationLabels.keySet());
			}
			var channels = ServerTools.classificationLabelsToChannels(classificationLabels, false);
			// It's a bit sad... but if we want grayscale output, we need to set the channels here
			if (params.grayscaleLut)
				channels = channels.stream().map(c -> ImageChannel.getInstance(c.getName(), ColorTools.WHITE)).collect(Collectors.toList());
			metadataBuilder = metadataBuilder
					.channelType(ChannelType.MULTICLASS_PROBABILITY)
					.channels(channels)
					.classificationLabels(classificationLabels);
			colorModel = ColorModelFactory.createColorModel(PixelType.UINT8, channels);
		} else {
			metadataBuilder = metadataBuilder
					.channelType(ChannelType.CLASSIFICATION)
					.classificationLabels(classificationLabels);
			
			// Update the color map, ensuring we don't have null
			var colors = new LinkedHashMap<Integer, Integer>();
			for (var entry : params.labelColors.entrySet()) {
				var key = entry.getKey();
				var value = entry.getValue();
				if (key == null) {
					logger.debug("Missing key in label map! Will be skipped.");
					continue;
				}
				if (value == null) {
					// Flip the bits of the background color, if needed
					logger.debug("Missing color in label map! Will be derived from the background color.");
					var backgroundColor = params.labelColors.get(params.labels.get(params.unannotatedClass));
					value = backgroundColor == null ? 0 : ~backgroundColor.intValue();
				}
				colors.put(key, value);
			}
			
			if (params.grayscaleLut) {
				if (maxLabel < 255)
					colorModel = COLOR_MODEL_GRAY_UINT8;
				else if (maxLabel < 65536){
					colorModel = COLOR_MODEL_GRAY_UINT16;
					metadataBuilder.pixelType(PixelType.UINT16);
				} else {
					colorModel = ColorModelFactory.createColorModel(PixelType.FLOAT32,
							ColorMaps.createColorMap("labels", 255, 255, 255),
							0,
							0,
							maxLabel,
							-1,
							null);
					metadataBuilder.pixelType(PixelType.FLOAT32);
				}
			} else {
				if (maxLabel < 65536) {
					colorModel = ColorModelFactory.createIndexedColorModel(colors, false);
					if (maxLabel > 255)
						metadataBuilder.pixelType(PixelType.UINT16);
				} else {
					colorModel = ColorModelFactory.getDummyColorModel(32);
					metadataBuilder.channels(ImageChannel.getDefaultRGBChannels());
				}
			}
		}
		
		// Set metadata, using the underlying server as a basis
		this.originalMetadata = metadataBuilder.build();
	}
	
	/**
	 * @param pathClass
	 * @return the input classification, or the unclassified classification if the input is null
	 */
	private static PathClass getPathClass(PathClass pathClass) {
		return pathClass == null ? PathClass.NULL_CLASS : pathClass;
	}
	
	/**
	 * Get a standardized classification for an object. 
	 * If unique labels are requested, this will return the unique classification associated with this object 
	 * or null if no unique classification is available (i.e. the object should not be included).
	 * Otherwise, it will return either the objects's classification or the unclassified class (not null).
	 * @param pathObject
	 * @return
	 */
	private PathClass getPathClass(PathObject pathObject) {
		if (instanceClassMap != null)
			return instanceLabelToClass(instanceClassMap.get(pathObject));
		return getPathClass(pathObject.getPathClass());
	}
	
	
	private static PathClass instanceLabelToClass(Integer label) {
		if (label == null)
			return null;
		return PathClass.getInstance("Label " + label);
	}
	
//	/**
//	 * Get the label associated with a specific {@link PathObject}.
//	 * This will be based on the instance if {@link Builder#useInstanceLabels()} is selected, 
//	 * or the classification.
//	 * @param pathObject
//	 * @return the label if available, or null if no label is associated with the object
//	 */
//	public Integer getLabel(PathObject pathObject) {
//		if (!this.params.objectFilter.test(pathObject))
//			return null;
//		if (params.createInstanceLabels)
//			return instanceClassMap.get(pathObject);
//		return params.labels.get(getPathClass(pathObject));
//	}
	
	/**
	 * Get a mapping between objects and instance labels.
	 * @return the instance label map, or an empty map if no objects are available or 
	 *         {@link Builder#useInstanceLabels()} was not selected.
	 */
	public Map<PathObject, Integer> getInstanceLabels() {
		if (instanceClassMap == null)
			return Collections.emptyMap();
		return Collections.unmodifiableMap(instanceClassMap);
	}
	
	/**
	 * Get an unmodifiable map of classifications and their corresponding labels.
	 * Note that multiple classifications may use the same integer label.
	 * @return a map of labels, or empty map if none are available or {@code useInstanceLabels()} was selected.
	 */
	public Map<PathClass, Integer> getLabels() {
		if (params.createInstanceLabels)
			return Collections.emptyMap();
		return Collections.unmodifiableMap(params.labels);
	}
	
	/**
	 * Get an unmodifiable map of classifications and their corresponding boundary labels, if available.
	 * Note that multiple classifications may use the same integer label.
	 * @return a map of boundary labels, or empty map if none are available or {@code useInstanceLabels()} was selected.
	 */
	public Map<PathClass, Integer> getBoundaryLabels() {
		if (params.createInstanceLabels)
			return Collections.emptyMap();
		return Collections.unmodifiableMap(params.boundaryLabels);
	}
		
	
	
	private static class LabeledServerParameters {
		
		/**
		 * Background class (name must not clash with any 'real' class)
		 * Previously, this was achieved with a UUID - although this looks strange if exporting classes.
		 */
//		private PathClass unannotatedClass = PathClassFactory.getPathClass("Unannotated " + UUID.randomUUID().toString());
		private PathClass unannotatedClass = PathClass.getInstance("*Background*");
		
		private Predicate<PathObject> objectFilter = PathObjectFilter.ANNOTATIONS;
		private Function<PathObject, ROI> roiFunction = p -> p.getROI();
		
		private boolean createInstanceLabels = false;
		private boolean shuffleInstanceLabels = true; // Only if using instance labels
		
		private int maxOutputChannelLimit = 256;
		
		private boolean grayscaleLut = false;
		
		private float lineThickness = 1.0f;
		private Map<PathClass, Integer> labels = new LinkedHashMap<>();
		private Map<PathClass, Integer> boundaryLabels = new LinkedHashMap<>();
		private Map<Integer, Integer> labelColors = new LinkedHashMap<>();
		
		LabeledServerParameters() {
			labels.put(unannotatedClass, 0);
			labelColors.put(0, ColorTools.WHITE);
		}
		
		LabeledServerParameters(LabeledServerParameters params) {
			this.unannotatedClass = params.unannotatedClass;
			this.lineThickness = params.lineThickness;
			this.objectFilter = params.objectFilter;
			this.labels = new LinkedHashMap<>(params.labels);
			this.boundaryLabels = new LinkedHashMap<>(params.boundaryLabels);
			this.labelColors = new LinkedHashMap<>(params.labelColors);
			this.createInstanceLabels = params.createInstanceLabels;
			this.maxOutputChannelLimit = params.maxOutputChannelLimit;
			this.roiFunction = params.roiFunction;
			this.grayscaleLut = params.grayscaleLut;
			this.shuffleInstanceLabels = params.shuffleInstanceLabels;
		}
		
	}
	
	/**
	 * Helper class for building a {@link LabeledImageServer}.
	 */
	public static class Builder {
		
		private ImageData<BufferedImage> imageData;
		private double downsample = 1.0;
		private int tileWidth, tileHeight;
		
		private boolean multichannelOutput = false;
		
		private LabeledServerParameters params = new LabeledServerParameters();
		
		/**
		 * Create a Builder for a {@link LabeledImageServer} for the specified {@link ImageData}.
		 * @param imageData
		 */
		public Builder(ImageData<BufferedImage> imageData) {
			this.imageData = imageData;
		}
		
		/**
		 * Use detections rather than annotations for labels.
		 * The default is to use annotations.
		 * @return
		 * @see #useAnnotations()
		 */
		public Builder useDetections() {
			params.objectFilter = PathObjectFilter.DETECTIONS_ALL;
			return this;
		}
		
		/**
		 * Use cells rather than annotations for labels.
		 * The default is to use annotations.
		 * @return
		 * @see #useAnnotations()
		 */
		public Builder useCells() {
			params.objectFilter = PathObjectFilter.CELLS;
			return this;
		}
		
		/**
		 * Use cells rather than annotations for labels, requesting the nucleus ROI where available.
		 * The default is to use annotations.
		 * @return
		 * @see #useAnnotations()
		 */
		public Builder useCellNuclei() {
			params.objectFilter = PathObjectFilter.CELLS;
			params.roiFunction = p -> PathObjectTools.getROI(p, true);
			return this;
		}
		
		/**
		 * Use annotations for labels. This is the default.
		 * @return
		 * @see #useDetections()
		 */
		public Builder useAnnotations() {
			params.objectFilter = PathObjectFilter.ANNOTATIONS;
			return this;
		}
		
		/**
		 * Use a custom method of selecting objects for inclusion.
		 * The default is to use annotations.
		 * @param filter the filter that determines whether an object will be included or not
		 * @return
		 * @see #useAnnotations()
		 */
		public Builder useFilter(Predicate<PathObject> filter) {
			params.objectFilter = filter;
			return this;
		}
		
		/**
		 * Use grayscale LUT, rather than deriving colors from classifications.
		 * This can streamline import in software that automatically converts paletted images to RGB.
		 * @return
		 * @since v0.4.0
		 * @see #grayscale(boolean)
		 */
		public Builder grayscale() {
			return grayscale(true);
		}
		
		/**
		 * Optionally use grayscale LUT, rather than deriving colors from classifications.
		 * This can streamline import in software that automatically converts paletted images to RGB.
		 * @param grayscaleLut 
		 * @return
		 * @since v0.4.0
		 * @see #grayscale()
		 */
		public Builder grayscale(boolean grayscaleLut) {
			params.grayscaleLut = grayscaleLut;
			return this;
		}
		
		/**
		 * Specify downsample factor. This is <i>very</i> important because it defines 
		 * the resolution at which shapes will be drawn and the line thickness is determined.
		 * @param downsample
		 * @return
		 */
		public Builder downsample(double downsample) {
			this.downsample = downsample;
			return this;
		}
		
		/**
		 * Set tile width and height (square tiles).
		 * @param tileSize
		 * @return
		 */
		public Builder tileSize(int tileSize) {
			return tileSize(tileSize, tileSize);
		}
		
		/**
		 * Set tile width and height.
		 * @param tileWidth
		 * @param tileHeight
		 * @return
		 */
		public Builder tileSize(int tileWidth, int tileHeight) {
			this.tileWidth = tileWidth;
			this.tileHeight = tileHeight;
			return this;
		}
		
		/**
		 * Thickness of boundary lines and line annotations, defined in terms of pixels at the 
		 * resolution specified by the downsample value of the server.
		 * @param thickness
		 * @return
		 */
		public Builder lineThickness(float thickness) {
			params.lineThickness = thickness;
			return this;
		}
		
		
		/**
		 * @return
		 * @deprecated in favor of {@link #useInstanceLabels()}
		 */
		@Deprecated
		public Builder useUniqueLabels() {
			logger.warn("useUniqueLabels() is deprecated; please switch to useInstanceLabels() instead.");
			return useInstanceLabels();
		}
		
		/**
		 * Request that unique labels are used for all objects, rather than classifications.
		 * If this flag is set, all other label requests are ignored.
		 * @return
		 * @see #useInstanceLabels(boolean)
		 * @see #shuffleInstanceLabels(boolean)
		 */
		public Builder useInstanceLabels() {
			return useInstanceLabels(true);
		}
		
		/**
		 * Optionally request that unique labels are used for all objects, rather than classifications.
		 * If this flag is set, all other label requests are ignored.
		 * @param instanceLabels 
		 * @return
		 * @since v0.4.0
		 * @see #useInstanceLabels()
		 * @see #shuffleInstanceLabels(boolean)
		 */
		public Builder useInstanceLabels(boolean instanceLabels) {
			params.createInstanceLabels = instanceLabels;
			return this;
		}
		
		
		/**
		 * Optionally request that instance labels are shuffled.
		 * Default is true.
		 * Only has an effect if {@link #useInstanceLabels(boolean)} is called with {@code true}.
		 * @param doShuffle 
		 * @return
		 * @since v0.4.0
		 * @see #useInstanceLabels()
		 * @see #useInstanceLabels(boolean)
		 */
		public Builder shuffleInstanceLabels(boolean doShuffle) {
			params.shuffleInstanceLabels = doShuffle;
			return this;
		}
		
		
		/**
		 * If true, the output image consists of multiple binary images concatenated as different channels, 
		 * so that the channel number relates to a classification.
		 * If false, the output image is a single-channel indexed image so that each pixel value relates to 
		 * a classification.
		 * Indexed images are much more efficient, but are unable to support more than one classification per pixel.
		 * @param doMultichannel
		 * @return
		 */
		public Builder multichannelOutput(boolean doMultichannel) {
			this.multichannelOutput = doMultichannel;
			return this;
		}
		
		/**
		 * Specify the background label (0 by default).
		 * @param label
		 * @return
		 */
		public Builder backgroundLabel(int label) {
			return backgroundLabel(label, ColorTools.packRGB(255, 255, 255));
		}
		
		/**
		 * Specify the background label (0 by default) and color.
		 * @param label
		 * @param color 
		 * @return
		 */
		public Builder backgroundLabel(int label, Integer color) {
			addLabel(params.unannotatedClass, label, color);
			return this;
		}
		
		/**
		 * Add multiple labels by classname, where the key represents a classname and the value 
		 * represents the integer label that should be used for annotations of the given class.
		 * @param labelMap
		 * @return
		 */
		public Builder addLabelsByName(Map<String, Integer> labelMap) {
			for (var entry : labelMap.entrySet())
				addLabel(entry.getKey(), entry.getValue());
			return this;
		}

		/**
		 * Add multiple labels by PathClass, where the key represents a PathClass and the value 
		 * represents the integer label that should be used for annotations of the given class.
		 * @param labelMap
		 * @return
		 */
		public Builder addLabels(Map<PathClass, Integer> labelMap) {
			for (var entry : labelMap.entrySet())
				addLabel(entry.getKey(), entry.getValue());
			return this;
		}
		
		/**
		 * Add a single label by classname, where the label represents the integer label used for 
		 * annotations with the given classname.
		 * @param pathClassName
		 * @param label
		 * @return
		 */
		public Builder addLabel(String pathClassName, int label) {
			return addLabel(pathClassName, label, null);
		}

		/**
		 * Add a single label by classname, where the label represents the integer label used for 
		 * annotations with the given classname.
		 * @param pathClassName
		 * @param label the indexed image pixel value or channel number for the given classification
		 * @param color the color of the lookup table used with any indexed image
		 * @return
		 */
		public Builder addLabel(String pathClassName, int label, Integer color) {
			return addLabel(PathClass.fromString(pathClassName), label, color);
		}

		/**
		 * Add a single label by {@link PathClass}, where the label represents the integer label used for 
		 * annotations with the given classification.
		 * @param pathClass
		 * @param label the indexed image pixel value or channel number for the given classification
		 * @return
		 */
		public Builder addLabel(PathClass pathClass, int label) {
			return addLabel(pathClass, label, null);
		}
		
		/**
		 * Add a single label by {@link PathClass}, where the label represents the integer label used for 
		 * annotations with the given classification.
		 * @param pathClass
		 * @param label the indexed image pixel value or channel number for the given classification
		 * @param color the color of the lookup table used with any indexed image
		 * @return
		 */
		public Builder addLabel(PathClass pathClass, int label, Integer color) {
			return addLabel(params.labels, pathClass, label, color);
		}
		
		/**
		 * Add a single label for objects that are unclassified, where the label represents the integer label used for 
		 * annotations that have no classification set.
		 * @param label the indexed image pixel value or channel number without a classification
		 * @param color the color of the lookup table used with any indexed image
		 * @return
		 */
		public Builder addUnclassifiedLabel(int label, Integer color) {
			return addLabel(params.labels, PathClass.NULL_CLASS, label, color);
		}
		
		/**
		 * Add a single label for objects that are unclassified, where the label represents the integer label used for 
		 * annotations that have no classification set.
		 * @param label the indexed image pixel value or channel number without a classification
		 * @return
		 */
		public Builder addUnclassifiedLabel(int label) {
			return addLabel(params.labels, PathClass.NULL_CLASS, label, null);
		}
		
		
		/**
		 * Set the classification and label to use for boundaries for classified areas.
		 * @param pathClass
		 * @param label the indexed image pixel value or channel number for the given classification
		 * @return
		 */
		public Builder setBoundaryLabel(PathClass pathClass, int label) {
			return setBoundaryLabel(pathClass, label, null);
		}
		
		/**
		 * Set the classification and label to use for boundaries for classified areas.
		 * @param pathClass
		 * @param label the indexed image pixel value or channel number for the given classification
		 * @param color the color of the lookup table used with any indexed image
		 * @return
		 */
		public Builder setBoundaryLabel(PathClass pathClass, int label, Integer color) {
			params.boundaryLabels.clear();
			return addLabel(params.boundaryLabels, pathClass, label, color);
		}
		
		/**
		 * Set the classification and label to use for boundaries for classified areas.
		 * @param pathClassName
		 * @param label the indexed image pixel value or channel number for the given classification
		 * @return
		 */
		public Builder setBoundaryLabel(String pathClassName, int label) {
			return setBoundaryLabel(pathClassName, label, null);
		}
		
		/**
		 * Set the classification and label to use for boundaries for classified areas.
		 * @param pathClassName
		 * @param label the indexed image pixel value or channel number for the given classification
		 * @param color the color of the lookup table used with any indexed image
		 * @return
		 */
		public Builder setBoundaryLabel(String pathClassName, int label, Integer color) {
			return setBoundaryLabel(PathClass.fromString(pathClassName), label, color);
		}
		
		private Builder addLabel(Map<PathClass, Integer> map, PathClass pathClass, int label, Integer color) {
			pathClass = getPathClass(pathClass);
			map.put(pathClass, label);
			if (color != null)
				params.labelColors.put(label, color);
			else if (!params.labelColors.containsKey(label))
				params.labelColors.put(label, pathClass.getColor());
			return this;
		}
		
		/**
		 * Specify the maximum number of output channels allowed before QuPath will throw an exception.
		 * This is used to guard against inadvertently requesting a labelled image that would have an infeasibly 
		 * large number of output channels, most commonly with {@link #useInstanceLabels()}.
		 * @param maxChannels the maximum supported channels; set (cautiously!) &le; 0 to ignore the limit entirely.
		 * @return
		 */
		public Builder maxOutputChannelLimit(int maxChannels) {
			params.maxOutputChannelLimit = maxChannels;
			return this;
		}
		
		/**
		 * Build the {@link ImageServer} with the requested parameters.
		 * @return
		 */
		public LabeledImageServer build() {
			if (params.createInstanceLabels) {
				if (!(params.labels.isEmpty() || (params.labels.size() == 1 && params.labels.containsKey(params.unannotatedClass))))
					throw new IllegalArgumentException("You cannot use both useInstanceLabels() and addLabel() - please choose one or the other!");
				if (params.objectFilter == null)
					throw new IllegalArgumentException("Please specify an object filter with useInstanceLabels(), for example useDetections(), useCells(), useAnnotations(), useFilter()");
			}
			
			return new LabeledImageServer(
					imageData, downsample, tileWidth, tileHeight,
					new LabeledServerParameters(params),
					multichannelOutput);
		}

	}
	
		
	/**
	 * Returns null (does not support ServerBuilders).
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return null;
	}
	
	@Override
	public Collection<URI> getURIs() {
		return Collections.emptyList();
	}
	
	/**
	 * Returns a UUID.
	 */
	@Override
	protected String createID() {
		return UUID.randomUUID().toString();
	}
	
	/**
	 * Returns true if there are no objects to be painted within the requested region.
	 * <p>
	 * @apiNote In v0.2 this performed a fast bounding box check only. In v0.3 it was updated to test ROIs fully for 
	 *          an intersection.
	 * @implNote Since v0.3 the request is expanded by the line thickness before testing intersection. In some edge cases, this might result 
	 *           in returning true even if nothing is drawn within the region. There remains a balance between returning quickly and 
	 *           giving an exact result.
	 */
	@Override
	public boolean isEmptyRegion(RegionRequest request) {
		double thicknessScale = request.getDownsample() / getDownsampleForResolution(0);
		int pad = (int)Math.ceil(params.lineThickness * thicknessScale);
		var request2 = pad > 0 ? request.pad2D(pad, pad) : request;
		return !getObjectsForRegion(request2)
				.stream()
				.anyMatch(p -> RoiTools.intersectsRegion(p.getROI(), request2));
	}
	
	/**
	 * Get the objects to be painted that fall within a specified region.
	 * Note that this does not take into consideration line thickness, and therefore results are not guaranteed 
	 * to match {@link #isEmptyRegion(RegionRequest)}; in other worse, an object might fall outside the region 
	 * but still influence an image type because of thick lines being drawn.
	 * If thicker lines should influence the result, the region should be padded accordingly.
	 * 
	 * @param region
	 * 
	 * @return a list of objects with ROIs that intersect the specified region
	 */
	public List<PathObject> getObjectsForRegion(ImageRegion region) {
		return hierarchy.getObjectsForRegion(null, region, null).stream()
				.filter(params.objectFilter)
				.filter(p -> params.createInstanceLabels || params.labels.containsKey(p.getPathClass()) || params.boundaryLabels.containsKey(p.getPathClass()))
				.collect(Collectors.toList());
	}
	
	@Override
	public void close() {}

	@Override
	public String getServerType() {
		return "Labelled image";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}

	/**
	 * Throws an exception - metadata should not be set for a hierarchy image server directly.  Any changes should be made to the underlying
	 * image server for which this server represents an object hierarchy.
	 */
	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		throw new IllegalArgumentException("Metadata cannot be set for a labelled image server!");
	}

	@Override
	protected BufferedImage createDefaultRGBImage(int width, int height) {
//		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
//		return gc.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
		return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}
	
	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		long startTime = System.currentTimeMillis();
		
		var pathObjects = hierarchy.getObjectsForRegion(null, tileRequest.getRegionRequest(), null)
				.stream()
				.filter(params.objectFilter)
				.collect(Collectors.toList());
		BufferedImage img;
		if (multichannelOutput) {
			img = createMultichannelTile(tileRequest, pathObjects);
			
		} else {
			img = createIndexedColorTile(tileRequest, pathObjects);
		}
		
		long endTime = System.currentTimeMillis();
		logger.trace("Labelled tile rendered in {} ms", endTime - startTime);
		return img;
	}
	
	
	private BufferedImage createMultichannelTile(TileRequest tileRequest, Collection<PathObject> pathObjects) {
		
		int nChannels = nChannels();
		if (nChannels == 1)
			return createBinaryTile(tileRequest, pathObjects, 0);
		
		int tileWidth = tileRequest.getTileWidth();
		int tileHeight = tileRequest.getTileHeight();
		byte[][] dataArray = new byte[nChannels][];
		for (int i = 0; i < nChannels; i++) {
			var tile = createBinaryTile(tileRequest, pathObjects, i);
			dataArray[i] = ((DataBufferByte)tile.getRaster().getDataBuffer()).getData();
		}
		DataBuffer buffer = new DataBufferByte(dataArray, tileWidth * tileHeight);
		
		int[] offsets = new int[nChannels];
		for (int b = 0; b < nChannels; b++)
			offsets[b] = b * tileWidth * tileHeight;
		
		var sampleModel = new BandedSampleModel(buffer.getDataType(), tileWidth, tileHeight, nChannels);
//		var sampleModel = new ComponentSampleModel(buffer.getDataType(), tileWidth, tileHeight, 1, tileWidth, offsets);
		
		var raster = WritableRaster.createWritableRaster(sampleModel, buffer, null);
		
		return new BufferedImage(colorModel, raster, false, null);
	}
	
	private BufferedImage createBinaryTile(TileRequest tileRequest, Collection<PathObject> pathObjects, int label) {
		int width = tileRequest.getTileWidth();
		int height = tileRequest.getTileHeight();
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		WritableRaster raster = img.getRaster();
		Graphics2D g2d = img.createGraphics();
		
		if (!pathObjects.isEmpty()) {
			
			RegionRequest request = tileRequest.getRegionRequest();
			double downsampleFactor = request.getDownsample();
			
			g2d.setClip(0, 0, width, height);
			double scale = 1.0/downsampleFactor;
			g2d.scale(scale, scale);
			g2d.translate(-request.getX(), -request.getY());
			g2d.setColor(Color.WHITE);
			
			BasicStroke stroke = new BasicStroke((float)(params.lineThickness * tileRequest.getDownsample()));
			g2d.setStroke(stroke);

			// We want to order consistently to avoid confusing overlaps
			for (var entry : params.labels.entrySet()) {
				if (entry.getValue() != label)
					continue;
				var pathClass = getPathClass(entry.getKey());
				for (var pathObject : pathObjects) {
					if (getPathClass(pathObject) == pathClass) {
						var roi = params.roiFunction.apply(pathObject);
						if (roi.isArea())
							g2d.fill(roi.getShape());
						else if (roi.isLine())
							g2d.draw(roi.getShape());
						else if (roi.isPoint()) {
							for (var p : roi.getAllPoints()) {
								int x = (int)((p.getX() - request.getX()) / downsampleFactor);
								int y = (int)((p.getY() - request.getY()) / downsampleFactor);
								if (x >= 0 && x < width && y >= 0 && y < height) {
									raster.setSample(x, y, 0, 255);
								}
							}
						}
					}
				}
			}
			for (var entry : params.boundaryLabels.entrySet()) {
				if (entry.getValue() != label)
					continue;
				for (var pathObject : pathObjects) {
					var pathClass = getPathClass(pathObject);
					if (params.labels.containsKey(pathClass)) { // && !PathClassTools.isIgnoredClass(pathObject.getPathClass())) {
						var roi = params.roiFunction.apply(pathObject);
						if (roi.isArea()) {
							var shape = roi.getShape();
							g2d.draw(shape);
						}
					}
				}
			}
		}
		
		g2d.dispose();
		return img;
	}
	
	
	private static Color getColorForLabel(int label, boolean doRGB) {
		if (doRGB)
			return new Color(label, false);
		return ColorToolsAwt.getCachedColor(label, label, label);
	}
	
	
	private BufferedImage createIndexedColorTile(TileRequest tileRequest, Collection<PathObject> pathObjects) {

		RegionRequest request = tileRequest.getRegionRequest();

		double downsampleFactor = request.getDownsample();

		// Fill in the background color
		int width = tileRequest.getTileWidth();
		int height = tileRequest.getTileHeight();
		boolean doRGB = maxLabel > 255;
		// If we have > 255 labels, we can only use Graphics2D if we pretend to have an RGB image
		BufferedImage img = doRGB ? new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB) : new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		WritableRaster raster = img.getRaster();
		
		Graphics2D g2d = img.createGraphics();
		int bgLabel = params.labels.get(params.unannotatedClass);
		Color color = getColorForLabel(bgLabel, doRGB);
		g2d.setColor(color);
		g2d.fillRect(0, 0, width, height);

		if (!pathObjects.isEmpty()) {
			g2d.setClip(0, 0, width, height);
			double scale = 1.0/downsampleFactor;
			g2d.scale(scale, scale);
			g2d.translate(-request.getX(), -request.getY());
			
			BasicStroke stroke = new BasicStroke((float)(params.lineThickness * tileRequest.getDownsample()));
			g2d.setStroke(stroke);
			
			// We want to order consistently to avoid confusing overlaps
			for (var entry : params.labels.entrySet()) {
				var pathClass = getPathClass(entry.getKey());
				int c = entry.getValue();
				color = getColorForLabel(c, doRGB);
				List<PathObject> toDraw;
				if (instanceClassMapInverse != null) {
					var temp = instanceClassMapInverse.get(c);
					if (temp == null)
						continue;
					toDraw = Collections.singletonList(temp);
				} else
					toDraw = pathObjects
									.stream()
									.filter(p -> getPathClass(p) == pathClass)
									.collect(Collectors.toList());
				
				for (var pathObject : toDraw) {
					var roi = params.roiFunction.apply(pathObject);
					g2d.setColor(color);
					if (roi.isArea())
						g2d.fill(roi.getShape());
					else if (roi.isLine())
						g2d.draw(roi.getShape());
					else if (roi.isPoint()) {
						for (var p : roi.getAllPoints()) {
							int x = (int)((p.getX() - request.getX()) / downsampleFactor);
							int y = (int)((p.getY() - request.getY()) / downsampleFactor);
							if (x >= 0 && x < width && y >= 0 && y < height) {
								if (doRGB)
									img.setRGB(x, y, color.getRGB());
								else
									raster.setSample(x, y, 0, c);
							}
						}
					}
				}
			}
			for (var entry : params.boundaryLabels.entrySet()) {
				int c = entry.getValue();
				color = getColorForLabel(c, doRGB);
				for (var pathObject : pathObjects) {
//					if (pathObject.getPathClass() == pathClass) {
					var pathClass = getPathClass(pathObject);
					if (params.labels.containsKey(pathClass)) {// && !PathClassTools.isIgnoredClass(pathObject.getPathClass())) {
						var roi = params.roiFunction.apply(pathObject);
						if (roi.isArea()) {
							g2d.setColor(color);
							g2d.draw(roi.getShape());
						}
					}
				}
			}
		}
		g2d.dispose();
		if (doRGB) {
			// Resort to RGB if we have to
			WritableRaster shortRaster = null;
			int w = img.getWidth();
			int h = img.getHeight();
			switch (getPixelType()) {
			case UINT8:
				return img;
			case FLOAT32:
				shortRaster = WritableRaster.createWritableRaster(
						new BandedSampleModel(DataBuffer.TYPE_FLOAT, w, h, 1),
						null);
				break;
			case FLOAT64:
				shortRaster = WritableRaster.createWritableRaster(
						new BandedSampleModel(DataBuffer.TYPE_DOUBLE, w, h, 1),
						null);
				break;
			case INT16:
				shortRaster = WritableRaster.createWritableRaster(
						new BandedSampleModel(DataBuffer.TYPE_SHORT, w, h, 1),
						null);
				break;
			case INT8:
			case UINT16:
				shortRaster = WritableRaster.createWritableRaster(
						new BandedSampleModel(DataBuffer.TYPE_USHORT, w, h, 1),
						null);
				break;
			case INT32:
			case UINT32:
				shortRaster = WritableRaster.createWritableRaster(
						new BandedSampleModel(DataBuffer.TYPE_INT, w, h, 1),
						null);
				break;
			default:
				break;
			}
			if (maxLabel >= 65536 || shortRaster == null) {
				return img;
			}
			// Transfer RGB values as labels to the new raster
			int[] samples = img.getRGB(0, 0, width, height, null, 0, width);
			shortRaster.setSamples(0, 0, width, height, 0, samples);
//			System.err.println("Before: " + Arrays.stream(samples).summaryStatistics());
			raster = shortRaster;
//			samples = raster.getSamples(0, 0, width, height, 0, (int[])null);
//			System.err.println("After: " + Arrays.stream(samples).summaryStatistics());
		}
		return new BufferedImage(colorModel, raster, false, null);
//		return new BufferedImage((IndexColorModel)colorModel, raster, false, null);
	}
	

}