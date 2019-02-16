package qupath.lib.classifiers.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.IOException;

import qupath.lib.classifiers.gui.ClassificationColorModelFactory;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata.OutputType;
import qupath.lib.display.ChannelDisplayInfo.SingleChannelDisplayInfo;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.RegionRequest;

public class SimplePixelClassifier implements PixelClassifier {
	
	private PixelClassifierMetadata metadata;
	private SingleChannelDisplayInfo channel;
	private double threshold;
	
	public SimplePixelClassifier(
			SingleChannelDisplayInfo channel,
			double requestedPixelSizeMicrons,
			double threshold,
			PathClass belowThreshold,
			PathClass aboveThreshold) {
		
		this.channel = channel;
		this.threshold = threshold;
		this.metadata = new PixelClassifierMetadata.Builder()
				.channels(getChannel(belowThreshold), getChannel(aboveThreshold))
				.inputPixelSizeMicrons(requestedPixelSizeMicrons)
				.inputShape(512, 512)
				.setOutputType(OutputType.Classification)
				.build();
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

		var transformed = channel.getValues(img, 0, 0, img.getWidth(), img.getHeight(), null);
		
		var colorModel = (IndexColorModel)ClassificationColorModelFactory.geClassificationColorModel(metadata.getChannels());
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

	@Override
	public PixelClassifierMetadata getMetadata() {
		return metadata;
	}

}
