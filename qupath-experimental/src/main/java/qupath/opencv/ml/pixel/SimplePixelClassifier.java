package qupath.opencv.ml.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.util.Arrays;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.pixel.features.ColorTransforms.ColorTransform;

class SimplePixelClassifier implements PixelClassifier {
	
	private transient PixelClassifierMetadata metadata;
	private transient IndexColorModel colorModel;
	
	private PixelCalibration inputResolution;
	private ColorTransform transform;
	private double threshold;
	private PathClass leqThreshold;
	private PathClass gtThreshold;
	
	SimplePixelClassifier(
			ColorTransform transform,
			PixelCalibration inputResolution,
			double threshold,
			PathClass leqThreshold,
			PathClass gtThreshold) {
		
		this.inputResolution = inputResolution;
		this.transform = transform;
		this.threshold = threshold;
		this.leqThreshold = leqThreshold;
		this.gtThreshold = gtThreshold;
	}
	
	static ImageChannel getChannel(PathClass pathClass) {
		if (pathClass == null || !pathClass.isValid())
			return ImageChannel.getInstance("None", null);
		return ImageChannel.getInstance(pathClass.getName(), pathClass.getColor());
	}
	

	@Override
	public BufferedImage applyClassification(ImageData<BufferedImage> imageData, RegionRequest request)
			throws IOException {
		
		var server = imageData.getServer();
		var img = server.readBufferedImage(request);

		var transformed = transform.extractChannel(imageData, img, null);
		
		var colorModel = getColorModel();
		var imgResult = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, colorModel);
		var raster = imgResult.getRaster();
		var bytes = ((DataBufferByte)raster.getDataBuffer()).getData();
		
		for (int i = 0; i < transformed.length; i++) {
			double val = transformed[i];
			if (val > threshold)
				bytes[i] = (byte)1;
		}
		
		return imgResult;
	}
	
	
	private synchronized IndexColorModel getColorModel() {
		if (colorModel == null) {
			var metadata = getMetadata();
			this.colorModel = (IndexColorModel)ColorModelFactory.getIndexedColorModel(metadata.getOutputChannels());
		}
		return colorModel;
	}

	@Override
	public synchronized PixelClassifierMetadata getMetadata() {
		if (metadata == null) {
			this.metadata = new PixelClassifierMetadata.Builder()
					.outputChannels(Arrays.asList(getChannel(leqThreshold), getChannel(gtThreshold)))
					.inputResolution(inputResolution)
					.inputShape(512, 512)
					.setChannelType(ImageServerMetadata.ChannelType.CLASSIFICATION)
					.build();
		}
		return metadata;
	}
	
	@Override
	public String toString() {
		return String.format("Threshold classifier (%s <= %s < %s): %s",
				leqThreshold.toString(),
				GeneralTools.formatNumber(threshold, 2),
				gtThreshold.toString(),
				inputResolution.toString());
	}

}
