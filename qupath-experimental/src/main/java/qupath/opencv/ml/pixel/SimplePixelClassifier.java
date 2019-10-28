package qupath.opencv.ml.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.pixel.ValueToClassification.ThresholdClassifier;
import qupath.opencv.ml.pixel.features.FeatureCalculator;

class SimplePixelClassifier implements PixelClassifier {
	
	private final static Logger logger = LoggerFactory.getLogger(SimplePixelClassifier.class);
	
	private transient PixelClassifierMetadata metadata;
	private transient IndexColorModel colorModel;
	
	private PixelCalibration inputResolution;
	private FeatureCalculator<BufferedImage> featureCalculator;
	private ThresholdClassifier thresholder;
	
	private transient Map<PathClass, Integer> map;
	
	
	SimplePixelClassifier(
			FeatureCalculator<BufferedImage> featureCalculator,
			PixelCalibration inputResolution,
			ThresholdClassifier thresholder) {
		
		this.inputResolution = inputResolution;
		this.featureCalculator = featureCalculator;
		this.thresholder = thresholder;
	}
	
	static ImageChannel getChannel(PathClass pathClass) {
		if (pathClass == null || !pathClass.isValid())
			return ImageChannel.getInstance("None*", null);
		return ImageChannel.getInstance(pathClass.getName(), PathClassTools.isIgnoredClass(pathClass) ? null : pathClass.getColor());
	}
	
	@Override
    public boolean supportsImage(ImageData<BufferedImage> imageData) {
		return featureCalculator.supportsImage(imageData);
    }

	@Override
	public BufferedImage applyClassification(ImageData<BufferedImage> imageData, RegionRequest request)
			throws IOException {
		
		int width, height;
		float[] transformed;
		var features = featureCalculator.calculateFeatures(imageData, request);
		if (features.size() != 1)
			logger.warn("Expected 1 features, but got {}", features.size());
		var img = features.get(0).getFeature();
		transformed = SimpleImages.getPixels(img, true);
		width = img.getWidth();
		height = img.getHeight();
		
		var colorModel = getColorModel();
		var imgResult = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
		var raster = imgResult.getRaster();
		var bytes = ((DataBufferByte)raster.getDataBuffer()).getData();
		
		var mapIndex = getIndexMap();
		
		for (int i = 0; i < transformed.length; i++) {
			double val = transformed[i];
			int index = mapIndex.get(thresholder.getClassification(val));
			bytes[i] = (byte)index;
		}
		
		return imgResult;
	}
	
	
	private synchronized IndexColorModel getColorModel() {
		if (colorModel == null) {
			var metadata = getMetadata();
			this.colorModel = (IndexColorModel)ColorModelFactory.getIndexedClassificationColorModel(metadata.getClassificationLabels());
		}
		return colorModel;
	}
	
	private Map<PathClass, Integer> getIndexMap() {
		if (map == null) {
			getMetadata();
		}
		return map;
	}

	@Override
	public synchronized PixelClassifierMetadata getMetadata() {
		if (metadata == null) {
			var pathClasses = thresholder.getPathClasses();
			map = new HashMap<>();
			int i = 0;
			for (var pathClass : pathClasses)
				map.put(pathClass, i++);
			this.metadata = new PixelClassifierMetadata.Builder()
					.outputChannels(pathClasses.stream().map(p -> getChannel(p)).collect(Collectors.toList()))
					.inputResolution(inputResolution)
					.inputShape(512, 512)
					.setChannelType(ImageServerMetadata.ChannelType.CLASSIFICATION)
					.build();
		}
		return metadata;
	}
	
	@Override
	public String toString() {
		return String.format("Threshold classifier (%s): %s",
				thresholder.toString(),
				inputResolution.toString());
	}

}
