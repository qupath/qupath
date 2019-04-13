package qupath.lib.classifiers.pixel;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ImageResolutionLevel;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.RegionRequest;

/**
 * ImageServer that delivers pixels derived from applying a PixelClassifier to another ImageServer.
 *
 * @author Pete Bankhead
 *
 */
public class PixelClassificationImageServer extends AbstractTileableImageServer {
	
	private static Logger logger = LoggerFactory.getLogger(PixelClassificationImageServer.class);
	
	private static int DEFAULT_TILE_SIZE = 512;
	
	private ImageData<BufferedImage> imageData;
	private ImageServer<BufferedImage> server;
	
	@JsonAdapter(PixelClassifiers.PixelClassifierTypeAdapterFactory.class)
	private PixelClassifier classifier;
	
	private ImageServerMetadata metadata;

	public PixelClassificationImageServer(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
		super();
		this.classifier = classifier;
		this.imageData = imageData;
		this.server = imageData.getServer();
		
		var classifierMetadata = classifier.getMetadata();
		
		String path;
//		try {
//			// If we can construct a path (however long) that includes the full serialization info, then cached tiles can be reused even if the server is recreated
//			path = server.getPath() + "::" + new Gson().toJson(classifier);
//		} catch (Exception e) {
			path = server.getPath() + "::" + UUID.randomUUID().toString();			
//		}
		
		var bitDepth = 8;
		
		var tileWidth = classifierMetadata.getInputWidth();
		var tileHeight = classifierMetadata.getInputHeight();
		if (tileWidth <= 0)
			tileWidth = DEFAULT_TILE_SIZE;
		if (tileHeight <= 0)
			tileHeight = DEFAULT_TILE_SIZE;
		
		double inputPixelSize = classifierMetadata.getInputPixelSize();
		double downsample = inputPixelSize / server.getAveragedPixelSizeMicrons();
		if (!Double.isFinite(downsample))
			downsample = inputPixelSize;
		
		int width = server.getWidth();
		int height = server.getHeight();
		
		var levels = new ImageResolutionLevel.Builder(width, height)
						.addLevelByDownsample(downsample)
						.build();
		
		int pad = classifierMetadata.strictInputSize() ? classifierMetadata.getInputPadding() : 0;
		
		var builder = new ImageServerMetadata.Builder(getClass(), server.getMetadata())
				.path(path)
				.width(width)
				.height(height)
				.output(classifierMetadata.getOutputType())
				.preferredTileSize(tileWidth-pad*2, tileHeight-pad*2)
				.levels(levels)
				.channels(classifierMetadata.getChannels())
				.bitDepth(bitDepth)
				.rgb(false);
				
		metadata = builder.build();
		
	}
	
	public ImageData<BufferedImage> getImageData() {
		return imageData;
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
	 * @param z
	 * @param t
	 * @return
	 * @throws IOException
	 */
	public int getClassification(int x, int y, int z, int t) throws IOException {
		
		var type = classifier.getMetadata().getOutputType();
		if (type != ImageServerMetadata.OutputType.CLASSIFICATION && type != ImageServerMetadata.OutputType.PROBABILITIES)
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
		if (nBands == 1 && type == ImageServerMetadata.OutputType.CLASSIFICATION) {
			try {
				return img.getRaster().getSample(xx, yy, 0);
			} catch (Exception e) {
				logger.error("Error requesting classification", e);
				return -1;
			}
		} else if (type == ImageServerMetadata.OutputType.PROBABILITIES) {
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
