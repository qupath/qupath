package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.stores.ImageRegionStoreHelpers;
import qupath.lib.regions.RegionRequest;

public abstract class AbstractTileableImageServer extends AbstractImageServer<BufferedImage> {
	
	private static Logger logger = LoggerFactory.getLogger(AbstractTileableImageServer.class);
	
	private static int SHARED_TILE_CACHE_CAPACITY = 100;
	
	/**
	 * Create a very small, backup shared tile cache (in case we aren't provided with one).
	 */
	private static Map<RegionRequest, BufferedImage> sharedCache = new LinkedHashMap<RegionRequest, BufferedImage>(SHARED_TILE_CACHE_CAPACITY+1, 1f, true) {
		private static final long serialVersionUID = 1L;
		@Override
		protected synchronized boolean removeEldestEntry(Map.Entry<RegionRequest, BufferedImage> eldest) {
			return size() > SHARED_TILE_CACHE_CAPACITY;
		}
	};
	
	/**
	 * Cache to use for storing & retrieving tiles.
	 */
	private Map<RegionRequest, BufferedImage> cache;
	
	/**
	 * Read a single image tile.
	 * 
	 * @param request
	 * @return
	 */
	protected abstract BufferedImage readTile(final RegionRequest request);
	
	/**
	 * Construct a tileable ImageServer, providing a cache in which to store & retrieve tiles.
	 * 
	 * @param cache
	 */
	protected AbstractTileableImageServer(Map<RegionRequest, BufferedImage> cache) {
		if (cache == null) {
			cache = sharedCache;
		}
		this.cache = cache;
	}
	
	/**
	 * Get a tile for the request - ideally from the cache, but otherwise read it & 
	 * then add it to the cache.
	 * 
	 * @param request
	 * @return
	 */
	private BufferedImage getTile(final RegionRequest request) {
		BufferedImage imgCached = cache.get(request);
		if (imgCached != null)
			return imgCached;
		imgCached = readTile(request);
		cache.put(request, imgCached);
		return imgCached;
	}

	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) {
		// Check if we already have a tile for precisely this occasion
		BufferedImage img = cache.get(request);
		if (img != null)
			return img;
		
		List<RegionRequest> requests = ImageRegionStoreHelpers.getTilesToRequest(this, request, null);
		if (requests.isEmpty())
			return null;
		// Check for the special case where we are requesting a single tile
		double tileDownsample = requests.get(0).getDownsample();
		double scale = tileDownsample / request.getDownsample();
		if (requests.size() == 1 && (scale == 1 || GeneralTools.almostTheSame(scale, 1.0, 0.00001))) {
			return getTile(requests.get(0));
		}
		// Handle the general case for RGB
		if (isRGB()) {
			int width = (int)Math.round(request.getWidth() / request.getDownsample());
			int height = (int)Math.round(request.getHeight() / request.getDownsample());
			BufferedImage imgResult = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = imgResult.createGraphics();
			g2d.scale(1.0/request.getDownsample(), 1.0/request.getDownsample());
			g2d.translate(-request.getX(), -request.getY());
//			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			for (RegionRequest tileRequest : requests) {
				BufferedImage imgTile = getTile(tileRequest);
				g2d.drawImage(imgTile, tileRequest.getX(), tileRequest.getY(), tileRequest.getWidth(), tileRequest.getHeight(), null);
			}
			g2d.dispose();
			return imgResult;
		}
		throw new UnsupportedOperationException("Non-RGB images are not yet supported, sorry!");
	}
	
}
