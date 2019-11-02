package qupath.lib.images.servers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.GeneratingImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.RegionRequest;


/**
 * A special ImageServer implementation that doesn't have a backing image, but rather
 * constructs tiles from a PathObjectHierarchy where pixel values are integer labels corresponding 
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
	
	private final static Logger logger = LoggerFactory.getLogger(LabeledImageServer.class);
	
	static long counter = 0;
	
	private ImageServerMetadata originalMetadata;
	
	private PathObjectHierarchy hierarchy;
	
	private ColorModel colorModel;
	private boolean multichannelOutput;
	
	private LabeledServerParameters params;
	
	private LabeledImageServer(final ImageData<BufferedImage> imageData, double downsample, int tileWidth, int tileHeight, LabeledServerParameters params, boolean multichannelOutput) {
		super();
		
		this.multichannelOutput = multichannelOutput;
		this.hierarchy = imageData.getHierarchy();
		
		this.params = params;
		
		var server = imageData.getServer();
		
		// Generate mapping for labels; it is permissible to have multiple classes for the same labels, in which case a derived class will be used
		Map<Integer, PathClass> classificationLabels = new TreeMap<>();
		for (var entry : params.labels.entrySet()) {
			var pathClass = entry.getKey();
			var label = entry.getValue();
			var previousClass = classificationLabels.put(label, pathClass);
			if (previousClass != null && previousClass != PathClassFactory.getPathClassUnclassified()) {
				classificationLabels.put(label, PathClassFactory.getDerivedPathClass(previousClass, pathClass.getName(), null));
			}
		}
		for (var entry : params.boundaryLabels.entrySet()) {
			var pathClass = entry.getKey();
			var label = entry.getValue();
			var previousClass = classificationLabels.put(label, pathClass);
			if (previousClass != null && previousClass != PathClassFactory.getPathClassUnclassified()) {
				classificationLabels.put(label, PathClassFactory.getDerivedPathClass(previousClass, pathClass.getName(), null));
			}
		}
		
		if (tileWidth <= 0)
			tileWidth = 512;
		if (tileHeight <= 0)
			tileHeight = tileWidth;
		
		var metadataBuilder = new ImageServerMetadata.Builder(server.getOriginalMetadata())
				.preferredTileSize(tileWidth, tileHeight)
				.levelsFromDownsamples(downsample)
				.pixelType(PixelType.UINT8)
				.rgb(false);
		
		// Check the labels are valid
		var labelStats = classificationLabels.keySet().stream().mapToInt(i -> i).summaryStatistics();
		int minLabel = labelStats.getMin();
		int maxLabel = labelStats.getMax();
		if (maxLabel > 255) {
			throw new IllegalArgumentException("Maximum possible label value is 255! Requested maximum was " + maxLabel);
		}
		if (minLabel < 0) {
			throw new IllegalArgumentException("Minimum possible label value is 0! Requested minimum was " + maxLabel);
		}
		
		if (multichannelOutput) {
			int nLabels = maxLabel - minLabel + 1;
			if (minLabel != 0 || nLabels != classificationLabels.size()) {
				throw new IllegalArgumentException("Labels for multichannel output must be consecutive integers starting from 0! Requested labels " + classificationLabels.keySet());
			}
			var channels = PathClassifierTools.classificationLabelsToChannels(classificationLabels, false);
			metadataBuilder = metadataBuilder
					.channelType(ChannelType.MULTICLASS_PROBABILITY)
					.channels(channels)
					.classificationLabels(classificationLabels);
			colorModel = ColorModelFactory.createColorModel(PixelType.UINT8, channels);
		} else {
			metadataBuilder = metadataBuilder
					.channelType(ChannelType.CLASSIFICATION)
					.classificationLabels(classificationLabels);
			colorModel = ColorModelFactory.createIndexedColorModel(params.labelColors, false);
		}
		
		// Set metadata, using the underlying server as a basis
		this.originalMetadata = metadataBuilder.build();
	}
	
	private static class LabeledServerParameters {
		
		/**
		 * Background class (name must not clash with any 'real' class)
		 */
		private PathClass unannotatedClass = PathClassFactory.getPathClass("Unannotated " + UUID.randomUUID().toString());
		
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
			this.labels = new LinkedHashMap<>(params.labels);
			this.boundaryLabels = new LinkedHashMap<>(params.boundaryLabels);
			this.labelColors = new LinkedHashMap<>(params.labelColors);
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
			return backgroundLabel(label, ColorTools.makeRGB(255, 255, 255));
		}
		
		/**
		 * Specify the background label (0 by default) and color.
		 * @param label
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
			return addLabel(PathClassFactory.getPathClass(pathClassName), label, color);
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
			return setBoundaryLabel(PathClassFactory.getPathClass(pathClassName), label, color);
		}
		
		private Builder addLabel(Map<PathClass, Integer> map, PathClass pathClass, int label, Integer color) {
			map.put(pathClass, label);
			if (color != null)
				params.labelColors.put(label, color);
			else if (!params.labelColors.containsKey(label))
				params.labelColors.put(label, pathClass.getColor());
			return this;
		}
		
		/**
		 * Build the {@link ImageServer} with the requested parameters.
		 * @return
		 */
		public LabeledImageServer build() {
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
	 */
	@Override
	public boolean isEmptyRegion(RegionRequest request) {
		return !hierarchy.getObjectsForRegion(PathAnnotationObject.class, request, null).stream().anyMatch( p -> {
				return params.labels.keySet().contains(p.getPathClass()) || params.boundaryLabels.keySet().contains(p.getPathClass());
			});
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
		
		var pathObjects = hierarchy.getObjectsForRegion(PathAnnotationObject.class, tileRequest.getRegionRequest(), null);
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
				var pathClass = entry.getKey();
				for (var pathObject : pathObjects) {
					if (pathObject.getPathClass() == pathClass) {
						var roi = pathObject.getROI();
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
				var pathClass = entry.getKey();
				for (var pathObject : pathObjects) {
					if (params.labels.containsKey(pathObject.getPathClass()) && !PathClassTools.isIgnoredClass(pathObject.getPathClass())) {
//					if (pathObject.getPathClass() == pathClass) {
						var roi = pathObject.getROI();
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
	
	
	private BufferedImage createIndexedColorTile(TileRequest tileRequest, Collection<PathObject> pathObjects) {

		RegionRequest request = tileRequest.getRegionRequest();

		double downsampleFactor = request.getDownsample();

		// Fill in the background color
		int width = tileRequest.getTileWidth();
		int height = tileRequest.getTileHeight();
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		WritableRaster raster = img.getRaster();
		
		Graphics2D g2d = img.createGraphics();
		int bgLabel = params.labels.get(params.unannotatedClass);
		Color color = ColorToolsAwt.getCachedColor(bgLabel, bgLabel, bgLabel);
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
				var pathClass = entry.getKey();
				int c = entry.getValue();
				color = ColorToolsAwt.getCachedColor(c, c, c);
				for (var pathObject : pathObjects) {
					if (pathObject.getPathClass() == pathClass) {
						var roi = pathObject.getROI();
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
									raster.setSample(x, y, 0, c);
								}
							}
						}
					}
				}
			}
			for (var entry : params.boundaryLabels.entrySet()) {
				var pathClass = entry.getKey();
				int c = entry.getValue();
				color = ColorToolsAwt.getCachedColor(c, c, c);
				for (var pathObject : pathObjects) {
//					if (pathObject.getPathClass() == pathClass) {
					if (params.labels.containsKey(pathObject.getPathClass()) && !PathClassTools.isIgnoredClass(pathObject.getPathClass())) {
						var roi = pathObject.getROI();
						if (roi.isArea()) {
							g2d.setColor(color);
							g2d.draw(roi.getShape());
						}
					}
				}
			}
		}

		g2d.dispose();
		return new BufferedImage((IndexColorModel)colorModel, img.getRaster(), false, null);
	}
	

}