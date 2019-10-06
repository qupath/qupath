package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.regions.RegionRequest;

/**
 * Helper class intended to make a single-resolution image act more like an image pyramid.
 * This does not avoid the fact that pixels must still be requested at the resolution of the original server, 
 * but enables tile caching at other resolutions - which may substantially improve performance in some cases.
 * 
 * @author Pete Bankhead
 */
class PyramidGeneratingImageServer extends AbstractTileableImageServer {
	
	private ImageServer<BufferedImage> server;
	private ImageServerMetadata metadata;
	
	PyramidGeneratingImageServer(ImageServer<BufferedImage> server, int tileWidth, int tileHeight, double... downsamples) {
		this(server, createDefaultMetadata(server, tileWidth, tileHeight, downsamples));
	}
	
	static ImageServerMetadata createDefaultMetadata(ImageServer<BufferedImage> server, int tileWidth, int tileHeight, double... downsamples) {
		return new ImageServerMetadata.Builder(server.getMetadata())
				.preferredTileSize(tileWidth, tileHeight)
				.levelsFromDownsamples(downsamples)
				.build();
	}
	
	PyramidGeneratingImageServer(ImageServer<BufferedImage> server, ImageServerMetadata metadata) {
		this.server = server;
		this.metadata = metadata;
	}

	@Override
	public Collection<URI> getURIs() {
		return server.getURIs();
	}
	
	@Override
	public String getServerType() {
		return "Generated pyramid (" + server.getServerType() + ")";
	}
	
	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return metadata;
	}
	
	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		// Request directly from the original server if that's most efficient
		RegionRequest request = tileRequest.getRegionRequest();
		double downsample = request.getDownsample();
		int level = ServerTools.getPreferredResolutionLevel(this, downsample);
		double closestOriginalDownsample = ServerTools.getPreferredDownsampleFactor(server, downsample);
		if (level == 0 || closestOriginalDownsample >= getDownsampleForResolution(level - 1))
			return server.readBufferedImage(RegionRequest.createInstance(server.getPath(), request.getDownsample(), request));
		
		// Read image from the 'previous' resolution
		RegionRequest request2 = RegionRequest.createInstance(request.getPath(), getDownsampleForResolution(level - 1),
				request.getX(), request.getY(), request.getWidth(), request.getHeight(), request.getZ(), request.getT());
		
		// If we have an empty tile, we should also return an empty tile
		BufferedImage img = readBufferedImage(request2);
//		if (img == null)
//			return null;
		if (img == null || isEmptyTile(img))
			return getEmptyTile(tileRequest.getTileWidth(), tileRequest.getTileHeight());
		
//		if (img == null || isEmptyTile(img))
//			return getEmptyTile(tileRequest.getTileWidth(), tileRequest.getTileHeight());
		
		// Resize to the required size
		return BufferedImageTools.resize(img, tileRequest.getTileWidth(), tileRequest.getTileHeight(), allowSmoothInterpolation());
	}
	
	/**
	 * Override the default method to request the value from the wrapped server, if possible
	 */
	@Override
	protected boolean allowSmoothInterpolation() {
		if (server instanceof AbstractTileableImageServer)
			return ((AbstractTileableImageServer)server).allowSmoothInterpolation();
		else
			return super.allowSmoothInterpolation();
	}
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		var builder = server.getBuilder();
		if (builder == null)
			return null;
		return new ImageServers.PyramidGeneratingServerBuilder(getMetadata(), builder);
	}
	
	@Override
	protected String createID() {
		return getClass().getSimpleName() + ":" + server.getPath();
	}

}