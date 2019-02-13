package qupath.lib.classifiers.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata.OutputType;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.RegionRequest;

public class PixelClassificationImageServer extends AbstractTileableImageServer {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationImageServer.class);
	
	private static int DEFAULT_TILE_SIZE = 512;
	
	private ImageData<BufferedImage> imageData;
	private ImageServer<BufferedImage> server;
	private PixelClassifier classifier;
	private ImageServerMetadata metadata;

	public PixelClassificationImageServer(Map<RegionRequest, BufferedImage> cache, ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		super(cache);
		this.classifier = classifier;
		this.imageData = imageData;
		this.server = imageData.getServer();
		
		var classifierMetadata = classifier.getMetadata();
		var path = server.getPath() + "::" + classifier.toString();
		
		var bitDepth = 8;
		
		var tileWidth = classifierMetadata.getInputWidth();
		var tileHeight = classifierMetadata.getInputHeight();
		if (tileWidth <= 0)
			tileWidth = DEFAULT_TILE_SIZE;
		if (tileHeight <= 0)
			tileHeight = DEFAULT_TILE_SIZE;
		
		double inputSizeMicrons = classifierMetadata.getInputPixelSizeMicrons();
		double downsample = inputSizeMicrons / server.getAveragedPixelSizeMicrons();
		
		// This code makes it possible for the classification server to return downsampled values
		// The idea is that this might help performance... but it raises questions around interpolating 
		// classifications and can result in the appearance not matching expectations.
//		List<Double> downsampleValues = new ArrayList<>();
//		double factor = 1;
//		do {
//			downsampleValues.add(downsample * factor);
//			factor *= 4;
//		} while (Math.min(tileWidth, tileHeight) / factor > 16);
//		double[] downsamples = downsampleValues.stream().mapToDouble(d -> d).toArray();
		double[] downsamples = new double[] {downsample};
		
		int width = server.getWidth();
		int height = server.getHeight();
		
		var builder = new ImageServerMetadata.Builder(path, width, height)
				.setPreferredTileSize(tileWidth, tileHeight)
				.setPreferredDownsamples(downsamples)
				.channels(classifierMetadata.getChannels())
				.setSizeT(server.nTimepoints())
				.setSizeZ(server.nZSlices())
				.setMagnification(server.getMagnification())
				.setBitDepth(bitDepth)
				.setRGB(false);
		
		if (server.hasPixelSizeMicrons())
			builder = builder.setPixelSizeMicrons(server.getPixelWidthMicrons(), server.getPixelHeightMicrons());
		if (Double.isFinite(server.getMagnification()))
			builder = builder.setMagnification(server.getMagnification());
		if (server.getTimeUnit() != null)
			builder = builder.setTimeUnit(server.getTimeUnit());
		if (Double.isFinite(server.getZSpacingMicrons()))
			builder = builder.setZSpacingMicrons(server.getZSpacingMicrons());
		
		metadata = builder.build();
		
	}
	
	public OutputType getOutputType() {
		return classifier.getMetadata().getOutputType();
	}
	
	public PixelClassifier getClassifier() {
		return classifier;
	}

	@Override
	public String getServerType() {
		return "Pixel classification server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	/**
	 * Not allowed - throws an {@link UnsupportedOperationException}.
	 */
	@Override
	public void setMetadata(ImageServerMetadata metadata) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("Setting metadata is not allowed!");
	}
	
	@Override
	public String getChannelName(int channel) {
		return classifier.getMetadata().getChannels().get(channel).getName();
	}
	
	@Override
	public Integer getDefaultChannelColor(int channel) {
		return classifier.getMetadata().getChannels().get(channel).getColor();
	}


	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		BufferedImage img;
		double fullResDownsample = getDownsampleForResolution(0);
		if (tileRequest.getDownsample() != fullResDownsample && Math.abs(tileRequest.getDownsample() - fullResDownsample) > 1e-6) {
			// If we're generating lower-resolution tiles, we need to request the higher-resolution data accordingly
			var request2 = RegionRequest.createInstance(getPath(), fullResDownsample, tileRequest.getRegionRequest());
			img = readBufferedImage(request2);
			img = resize(img, tileRequest.getImageWidth(), tileRequest.getTileHeight());
		} else {
			// Classify at this resolution if need be
			img = classifier.applyClassification(imageData, tileRequest.getRegionRequest());
		}
		return img;
	}
	
	/**
	 * Request the classification for a specific pixel.
	 * 
	 * @param x
	 * @param y
	 * @param c
	 * @param z
	 * @param t
	 * @return
	 * @throws IOException
	 */
	public int getClassification(int x, int y, int z, int t) throws IOException {
		
		var type = classifier.getMetadata().getOutputType();
		if (type != OutputType.Classification && type != OutputType.Probability)
			return -1;
		
		var tile = getTile(0, x, y, z, t);
		if (tile == null)
			return -1;
		
		int xx = (int)Math.floor(x / tile.getDownsample() - tile.getTileX());
		int yy = (int)Math.floor(y / tile.getDownsample() - tile.getTileY());
		var img = getTile(tile);
		
		if (xx >= img.getWidth())
			xx = img.getWidth() - 1;
		if (xx < 0)
			xx = 0;

		if (yy >= img.getHeight())
			yy = img.getHeight() - 1;
		if (yy < 0)
			yy = 0;

		int nBands = img.getRaster().getNumBands();
		if (nBands == 1 && type == OutputType.Classification) {
			try {
				return img.getRaster().getSample(xx, yy, 0);
			} catch (Exception e) {
				logger.error("Error requesting classification", e);
				return -1;
			}
		} else if (type == OutputType.Probability) {
			int maxInd = -1;
			double maxVal = Double.NEGATIVE_INFINITY;
			var raster = img.getRaster();
			for (int b = 0; b < nBands; b++) {
				double temp = raster.getSampleDouble(xx, yy, b);
				if (temp > maxVal) {
					maxInd = b;
					maxVal = temp;
				}
			}
			return maxInd;
		}
		return -1;
	}
	

}
