package qupath.opencv.ops;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.io.GsonTools;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.opencv.tools.OpenCVTools;

/**
 * An {@link ImageServer} that applies an {@link ImageDataOp} to transform pixels as they are read.
 * 
 * @author Pete Bankhead
 */
class ImageOpServer extends AbstractTileableImageServer implements ImageDataServer<BufferedImage> {
	
	private final static Logger logger = LoggerFactory.getLogger(ImageOpServer.class);
	
	private ImageData<BufferedImage> imageData;
	private ImageDataOp dataOp;
	private ImageServerMetadata metadata;
	
	ImageOpServer(ImageData<BufferedImage> imageData, double downsample, int tileWidth, int tileHeight, ImageDataOp dataOp) {
		super();
		
		this.imageData = imageData;
		this.dataOp = dataOp;
		
		var pixelType = dataOp.getOutputType(imageData.getServer().getPixelType());
		
		// Update channels according to the op
		var channels = dataOp.getChannels(imageData);
					
		metadata = new ImageServerMetadata.Builder(imageData.getServer().getMetadata())
				.levelsFromDownsamples(downsample)
				.preferredTileSize(tileWidth, tileHeight)
				.pixelType(pixelType)
				.channels(channels)
				.rgb(false)
				.build();
		
	}
	
	@Override
	public ImageData<BufferedImage> getImageData() {
		return imageData;
	}

	@Override
	public Collection<URI> getURIs() {
		return imageData.getServer().getURIs();
	}

	@Override
	public String getServerType() {
		return "ImageOp server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}

	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		var mat = dataOp.apply(imageData, tileRequest.getRegionRequest());
		return OpenCVTools.matToBufferedImage(mat);
	}

	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		logger.warn("Server builder not supported!");
		return null;
	}

	@Override
	protected String createID() {
		// Try to create an ID from JSON, because this allows us to cache tiles
		// Note that because the ImageData may well have changed, we shouldn't retain this for long
		try {
			return getServerType() + ": " + imageData + " " + GsonTools.getInstance().toJson(dataOp);
		} catch (Exception e) {
			logger.debug("Unable to create ID from JSON");
		}
		return UUID.randomUUID().toString();
	}
	
}