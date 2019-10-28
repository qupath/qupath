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
			labels.put(PathClassFactory.getPathClassUnclassified(), 0);
			labelColors.put(0, ColorTools.makeRGB(255, 255, 255));
		}
		
		LabeledServerParameters(LabeledServerParameters params) {
			this.lineThickness = params.lineThickness;
			this.labels = new LinkedHashMap<>(params.labels);
			this.boundaryLabels = new LinkedHashMap<>(params.boundaryLabels);
			this.labelColors = new LinkedHashMap<>(params.labelColors);
		}
		
	}
	
	public static class Builder {
		
		private ImageData<BufferedImage> imageData;
		private double downsample = 1.0;
		private int tileWidth, tileHeight;
		
		private boolean multichannelOutput = false;

		private LabeledServerParameters params = new LabeledServerParameters();
		
		public Builder(ImageData<BufferedImage> imageData) {
			this.imageData = imageData;
		}
		
		public Builder downsample(double downsample) {
			this.downsample = downsample;
			return this;
		}
		
		public Builder tileSize(int tileSize) {
			return tileSize(tileSize, tileSize);
		}
		
		public Builder tileSize(int tileWidth, int tileHeight) {
			this.tileWidth = tileWidth;
			this.tileHeight = tileHeight;
			return this;
		}
		
		public Builder lineThickness(float thickness) {
			params.lineThickness = thickness;
			return this;
		}
		
		public Builder multichannelOutput(boolean doMultichannel) {
			this.multichannelOutput = doMultichannel;
			return this;
		}
		
		public Builder backgroundLabel(int label) {
			return backgroundLabel(label, ColorTools.makeRGB(255, 255, 255));
		}
		
		public Builder backgroundLabel(int label, Integer color) {
			addLabel(params.unannotatedClass, color);
			return this;
		}
		
		public Builder addLabel(PathClass pathClass, int label) {
			return addLabel(pathClass, label, null);
		}
		
		public Builder addLabel(PathClass pathClass, int label, Integer color) {
			return addLabel(params.labels, pathClass, label, color);
		}
		
		public Builder addBoundaryLabel(PathClass pathClass, int label) {
			return addBoundaryLabel(pathClass, label, null);
		}
		
		public Builder addBoundaryLabel(PathClass pathClass, int label, Integer color) {
			return addLabel(params.boundaryLabels, pathClass, label, color);
		}
		
		private Builder addLabel(Map<PathClass, Integer> map, PathClass pathClass, int label, Integer color) {
			map.put(pathClass, label);
			if (color != null)
				params.labelColors.put(label, color);
			else if (!params.labelColors.containsKey(label))
				params.labelColors.put(label, pathClass.getColor());
			return this;
		}
		
		public LabeledImageServer build() {
			return new LabeledImageServer(
					imageData, downsample, tileWidth, tileHeight,
					new LabeledServerParameters(params),
					multichannelOutput);
		}

	}
	
//	private static class ClassificationLabel {
//		
//		private final PathClass pathClass;
//		public final int label;
//		public final int color;
//		public final float boundaryThickness;
//		public final int boundaryLabel;
//		
//		ClassificationLabel(final PathClass pathClass, int label, int color, float boundaryThickness, int boundaryLabel) {
//			this.pathClass = pathClass;
//			this.label = label;
//			this.color = color;
//			this.boundaryThickness = boundaryThickness;
//			this.boundaryLabel = boundaryLabel;
//		}
//		
//	}
	
	
	
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
		Graphics2D g2d = img.createGraphics();
		
		if (!pathObjects.isEmpty()) {
			
			RegionRequest request = tileRequest.getRegionRequest();
			double downsampleFactor = request.getDownsample();
			
			g2d.setClip(0, 0, width, height);
			double scale = 1.0/downsampleFactor;
			g2d.scale(scale, scale);
			g2d.translate(-request.getX(), -request.getY());
			g2d.setColor(Color.WHITE);

			// We want to order consistently to avoid confusing overlaps
			for (var entry : params.labels.entrySet()) {
				if (entry.getValue() != label)
					continue;
				var pathClass = entry.getKey();
				for (var pathObject : pathObjects) {
					if (pathObject.getPathClass() == pathClass) {
						var shape = pathObject.getROI().getShape();
						g2d.fill(shape);
					}
				}
			}
			BasicStroke stroke = null;
			for (var entry : params.boundaryLabels.entrySet()) {
				if (entry.getValue() != label)
					continue;
				var pathClass = entry.getKey();
				for (var pathObject : pathObjects) {
					if (pathObject.getPathClass() == pathClass) {
						if (stroke == null)
							stroke = new BasicStroke((float)(params.lineThickness * tileRequest.getDownsample()));
						var shape = pathObject.getROI().getShape();
						g2d.draw(shape);
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
		Graphics2D g2d = img.createGraphics();
		Color color = ColorToolsAwt.getCachedColor(params.labels.get(params.unannotatedClass));
		g2d.setColor(color);
		g2d.fillRect(0, 0, width, height);

		if (!pathObjects.isEmpty()) {
			g2d.setClip(0, 0, width, height);
			double scale = 1.0/downsampleFactor;
			g2d.scale(scale, scale);
			g2d.translate(-request.getX(), -request.getY());
			// We want to order consistently to avoid confusing overlaps
			for (var entry : params.labels.entrySet()) {
				var pathClass = entry.getKey();
				int c = entry.getValue();
				color = ColorToolsAwt.getCachedColor(c, c, c);
				for (var pathObject : pathObjects) {
					if (pathObject.getPathClass() == pathClass) {
						var shape = pathObject.getROI().getShape();
						g2d.setColor(color);
						g2d.fill(shape);
					}
				}
			}
			BasicStroke stroke = null;
			for (var entry : params.boundaryLabels.entrySet()) {
				var pathClass = entry.getKey();
				int c = entry.getValue();
				color = ColorToolsAwt.getCachedColor(c, c, c);
				for (var pathObject : pathObjects) {
					if (pathObject.getPathClass() == pathClass) {
						if (stroke == null)
							stroke = new BasicStroke((float)(params.lineThickness * tileRequest.getDownsample()));
						var shape = pathObject.getROI().getShape();
						g2d.setColor(color);
						g2d.draw(shape);
					}
				}
			}
		}

		g2d.dispose();
		return new BufferedImage((IndexColorModel)colorModel, img.getRaster(), false, null);
	}
	

}