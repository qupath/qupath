package qupath.opencv.ml.pixel;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferFloat;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.pixel.features.FeatureCalculator;

/**
 * An ImageServer that extract features from a wrapped server at a single specified resolution.
 * 
 * @author Pete Bankhead
 */
public class FeatureImageServer extends AbstractTileableImageServer {
	
	private ImageServerMetadata metadata;
	private ImageData<BufferedImage> imageData;
	private FeatureCalculator<BufferedImage> calculator;
	
	public FeatureImageServer(ImageData<BufferedImage> imageData, FeatureCalculator<BufferedImage> calculator, PixelCalibration resolution) throws IOException {
		super();
		this.imageData = imageData;
		this.calculator = calculator;
		
		if (!calculator.supportsImage(imageData))
			throw new IllegalArgumentException("Feature calculator is not compatible with " + imageData + "!");
		
		int tileWidth = calculator.getInputSize().getWidth();
		int tileHeight = calculator.getInputSize().getHeight();
		
		double downsample = resolution.getAveragedPixelSize().doubleValue() / imageData.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue();
		
		// We need to request a tile so that we can determine channel names
		var server = imageData.getServer();
		var tempRequest = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, (int)Math.min(server.getWidth(), tileWidth), (int)Math.min(server.getHeight(), tileHeight*downsample));
		var features = calculator.calculateFeatures(imageData, tempRequest);
		List<ImageChannel> channels = new ArrayList<>();
		for (var feature : features)
			channels.add(ImageChannel.getInstance(feature.getName(), ColorTools.makeRGB(255, 255, 255)));
		
		metadata = new ImageServerMetadata.Builder(imageData.getServer().getMetadata())
				.levelsFromDownsamples(downsample)
				.preferredTileSize(tileWidth, tileHeight)
				.channels(channels)
				.channelType(ChannelType.FEATURE)
				.pixelType(PixelType.FLOAT32)
				.rgb(false)
				.build();
		
	}
	
	public ImageData<BufferedImage> getImageData() {
		return imageData;
	}

	@Override
	public Collection<URI> getURIs() {
		return imageData.getServer().getURIs();
	}

	@Override
	public String getServerType() {
		return "Feature calculator";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}

	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
//		System.err.println(tileRequest);
		var tempRequest = RegionRequest.createInstance(imageData.getServer().getPath(), tileRequest.getDownsample(), tileRequest.getRegionRequest());
		var features = calculator.calculateFeatures(imageData, tempRequest);
		
		float[][] dataArray = new float[nChannels()][];
		int width = 0;
		int height = 0;
		int sizeC = nChannels();
		
		if (sizeC != features.size())
			throw new IOException("Unsupported number of features: expected " + sizeC + " but calculated " + features.size());
		
		for (int i = 0; i < sizeC; i++) {
			var img = features.get(i).getFeature();
			if (i == 0) {
				width = img.getWidth();
				height = img.getHeight();
			}
			dataArray[i] = SimpleImages.getPixels(img, true);
		}
		
		var dataBuffer = new DataBufferFloat(dataArray, width * height);
		var sampleModel = new BandedSampleModel(dataBuffer.getDataType(), width, height, sizeC);
		WritableRaster raster = WritableRaster.createWritableRaster(sampleModel, dataBuffer, null);
	
		return new BufferedImage(getDefaultColorModel(), raster, false, null);
	}
	
	// TODO: Consider clearing the cache for this server if we can
	@Override
	public void close() throws Exception {
		super.close();
	}

	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return null;
	}

	@Override
	protected String createID() {
		return String.format("%s %s", imageData.getServerPath(), calculator.toString());
	}
	
}