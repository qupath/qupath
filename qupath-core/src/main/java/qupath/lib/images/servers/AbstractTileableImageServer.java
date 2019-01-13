package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.regions.ImageRegion;
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
	protected abstract BufferedImage readTile(final TileRequest tileRequest) throws IOException;
	
	
	private TileRequestManager tileRequestManager;
	
	
	/**
	 * Construct a tileable ImageServer, providing a cache in which to store &amp; retrieve tiles.
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
	private BufferedImage getTile(final TileRequest tileRequest) throws IOException {
		BufferedImage imgCached = cache.get(tileRequest.getRegionRequest());
		if (imgCached != null) { 
			logger.trace("Returning cached tile: {}", tileRequest.getRegionRequest());
			return imgCached;
		}
		logger.trace("Reading tile: {}", tileRequest.getRegionRequest());
		
		imgCached = readTile(tileRequest);
		cache.put(tileRequest.getRegionRequest(), imgCached);
		return imgCached;
	}
	
	
	/**
	 * Create the default (blank) RGB image for this server.
	 * <p>
	 * By default this will have {@code TYPE_INT_RGB} but a subclass may change this if necessary 
	 * (e.g. to incorporate an alpha channel).
	 * 
	 * @param width
	 * @param height
	 * @return
	 */
	protected BufferedImage createDefaultRGBImage(int width, int height) {
		return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
	}

	
	protected TileRequestManager getTileRequestManager() {
		if (tileRequestManager == null) {
			tileRequestManager = new TileRequestManager(getAllTileRequests());
		}
		return tileRequestManager;
	}
	
	
	static BufferedImage duplicate(BufferedImage img) {
		return new BufferedImage(
				img.getColorModel(),
				img.copyData(null),
				img.isAlphaPremultiplied(),
				null);
	}
	

	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
		// Check if we already have a tile for precisely this occasion
		// Make a defensive copy, since the cache is critical
		BufferedImage img = cache.get(request);
		if (img != null)
			return duplicate(img);
		
		// Figure out which tiles we need
		var manager = getTileRequestManager();
		var tiles = manager.getTiles(request);
		
		// If no tiles found, we assume a sparse image with nothing relevant to display for this location
		if (tiles.isEmpty())
			return null;
		
		// Check for the special case where we are requesting a single tile, which exactly matches the request
		if (tiles.size() == 1 && request.equals(tiles.get(0).getRegionRequest())) {
			var imgTile = getTile(tiles.get(0));
			if (imgTile == null)
				return null;
			return duplicate(imgTile);
		}
		
		long startTime = System.currentTimeMillis();
		// Handle the general case for RGB
		int width = (int)Math.round(request.getWidth() / request.getDownsample());
		int height = (int)Math.round(request.getHeight() / request.getDownsample());
		if (isRGB()) {
			BufferedImage imgResult = createDefaultRGBImage(width, height);
			Graphics2D g2d = imgResult.createGraphics();
			g2d.scale(1.0/request.getDownsample(), 1.0/request.getDownsample());
			g2d.translate(-request.getX(), -request.getY());
			// Interpolate if downsampling
			if (request.getDownsample() > 1)
				g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			for (TileRequest tileRequest : tiles) {
				BufferedImage imgTile = getTile(tileRequest);
				g2d.drawImage(imgTile, tileRequest.getImageX(), tileRequest.getImageY(), tileRequest.getImageWidth(), tileRequest.getImageHeight(), null);
			}
			g2d.dispose();
			
			long endTime = System.currentTimeMillis();
			logger.trace("Requested " + tiles.size() + " tiles in " + (endTime - startTime) + " ms (RGB)");

			return imgResult;
		} else {
			// Request all of the tiles we need & figure out image dimensions
			// Do all this at the pyramid level of the tiles
			SampleModel model = null;
			WritableRaster raster = null;
			ColorModel colorModel = null;
			boolean alphaPremultiplied = false;
			
			// Get the dimensions, based on tile coordinates & at the tiled resolution
			int tileMinX = Integer.MAX_VALUE;
			int tileMinY = Integer.MAX_VALUE;
			int tileMaxX = Integer.MIN_VALUE;
			int tileMaxY = Integer.MIN_VALUE;
			double tileDownsample = Double.NaN;
			for (var tileRequest : tiles) {
				if (Double.isNaN(tileDownsample)) {
					tileDownsample = tileRequest.getRegionRequest().getDownsample();
				}
				tileMinX = Math.min(tileRequest.getTileX(), tileMinX);
				tileMinY = Math.min(tileRequest.getTileY(), tileMinY);
				tileMaxX = Math.max(tileRequest.getTileX() + tileRequest.getTileWidth(), tileMaxX);
				tileMaxY = Math.max(tileRequest.getTileY() + tileRequest.getTileHeight(), tileMaxY);
			}
			
			
			for (var tileRequest : tiles) {
				
				BufferedImage imgTile = getTile(tileRequest);
				if (imgTile != null) {
					// Preallocate a raster if we need to, and everything else the tile might give us
					if (raster == null) {
						model = imgTile.getSampleModel().createCompatibleSampleModel(tileMaxX - tileMinX, tileMaxY - tileMinY);
						raster = WritableRaster.createWritableRaster(model, null);
						colorModel = imgTile.getColorModel();
						alphaPremultiplied = imgTile.isAlphaPremultiplied();							
					}
					// Insert the tile into the raster
					raster.setDataElements(
							tileRequest.getTileX() - tileMinX,
							tileRequest.getTileY() - tileMinY,
//							Math.min(raster.getWidth() - tileMinX, imgTile.getWidth()),
//							Math.min(raster.getHeight() - tileMinY, imgTile.getHeight()),							
							imgTile.getRaster());
				}
			}
			// Maybe we don't have anything at all (which is not an error if the image is sparse!)
			if (raster == null)
				return null;
			
			// Calculate the requested region mapped to the pyramidal level, and relative to the tiled image
			int xStart = (int)Math.round(request.getX() / tileDownsample) - tileMinX;
			int yStart = (int)Math.round(request.getY() / tileDownsample) - tileMinY;
			int xEnd = (int)Math.round((request.getX() + request.getWidth()) / tileDownsample) - tileMinX;
			int yEnd = (int)Math.round((request.getY() + request.getHeight()) / tileDownsample) - tileMinY;
			
			// Do cropping, if we need to
			if (xStart > 0 || yStart > 0 || xEnd < raster.getWidth() || yEnd < raster.getHeight()) {
				raster = raster.createWritableChild(
						Math.max(xStart, 0),
						Math.max(yStart, 0),
						Math.min(raster.getWidth() - Math.max(xStart, 0), xEnd - Math.max(xStart, 0)),
						Math.min(raster.getHeight() - Math.max(yStart, 0), yEnd - Math.max(yStart, 0)),
						0, 0, null);
			}

			// Return the image, resizing if necessary
			BufferedImage imgResult = new BufferedImage(colorModel, raster, alphaPremultiplied, null);
			imgResult = resize(imgResult, width, height, false);
			
			long endTime = System.currentTimeMillis();
			logger.trace("Requested " + tiles.size() + " tiles in " + (endTime - startTime) + " ms (non-RGB)");
			return imgResult;
		}
	}

	
	
	/**
	 * Resize the image to have the requested width/height, using nearest neighbor interpolation.
	 * 
	 * @param img input image to be resized
	 * @param finalWidth target output width
	 * @param finalHeight target output height
	 * @param isRGB request that the image be handled as RGB; this is typically faster, but might fail depending on
	 *  the image type and need to fall back on slower (default) resizing
	 * @return
	 */
	public static BufferedImage resize(final BufferedImage img, final int finalWidth, final int finalHeight, final boolean isRGB) {
		// Check if we need to do anything
		if (img.getWidth() == finalWidth && img.getHeight() == finalHeight)
			return img;
		
		// RGB can generally be converted more easily
		if (isRGB) {
			try {
				BufferedImage img2 = new BufferedImage(finalWidth, finalHeight, img.getType());
				Graphics2D g2d = img2.createGraphics();
				g2d.drawImage(img, 0, 0, finalWidth, finalHeight, null);
				g2d.dispose();
				return img2;
			} catch (Exception e) {
				logger.debug("Error rescaling (supposedly) RGB image {}, will default to slower rescaling: {}", img, e.getLocalizedMessage());
			}
		}
		
		// Create an image with the same ColorModel / data type as the original
		WritableRaster raster = img.getColorModel().createCompatibleWritableRaster(finalWidth, finalHeight);

		// Get the pixels & resize for each band
		float[] pixels = null;
		for (int b = 0; b < raster.getNumBands(); b++) {
			pixels = img.getRaster().getSamples(0, 0, img.getWidth(), img.getHeight(), b, pixels);
			double xScale = (double)img.getWidth() / finalWidth;
			double yScale = (double)img.getHeight() / finalHeight;
			
			// Perform rescaling with nearest neighbor interpolation
			// TODO: Consider 'better' forms of interpolation
			float[] pixelsNew = new float[finalWidth*finalHeight];
			int w = img.getWidth();
			int h = img.getHeight();
			for (int y = 0; y < finalHeight; y++) {
				int row = (int)(y * yScale + 0.5);
				if (row >= h)
					row = h-1;
				for (int x = 0; x < finalWidth; x++) {
					int col = (int)(x * xScale + 0.5);
					if (col >= w)
						col = w-1;
					int ind = row*img.getWidth() + col;
					pixelsNew[y*finalWidth + x] = pixels[ind];
				}			
			}
			raster.setSamples(0, 0, finalWidth, finalHeight, b, pixelsNew);
		}
		return new BufferedImage(img.getColorModel(), raster, img.getColorModel().isAlphaPremultiplied(), null);
	}
	
	
	/**
	 * A wrapper for a RegionRequest, useful to precisely specify image tiles at a particular resolution.
	 * <p>
	 * Why?
	 * <p>
	 * Because downsamples can be defined with floating point precision, and are not always unambiguous when 
	 * calculated as the ratios of pyramid level dimensions (i.e. different apparent horizontal & vertical scaling), 
	 * a RegionRequest is too fuzzy a way to refer to a specific rectangle of pixels from a specific pyramid level. 
	 * Rounding errors can easily occur, and different image readers might respond differently to the same request.
	 * <p>
	 * Consequently, TileRequests provide a means to reproducibly define coordinates at pyramid levels and not only 
	 * the full resolution image space.  They wrap a RegionRequest, because this is still used for caching purposes. 
	 */
	public static class TileRequest {
		
		private transient RegionRequest request;
		
		private final String path;
		private final double downsample;
		private final int level;
		private final ImageRegion tileRegion;
		
		public TileRequest(final String path, 
				final double downsample, final int level, final ImageRegion tileRegion) {
			this.path = path;
			this.downsample = downsample;
			this.level = level;
			this.tileRegion = tileRegion;
		}
		
		public RegionRequest getRegionRequest() {
			if (request == null) {
				double x1 = tileRegion.getX() * downsample;
				double y1 = tileRegion.getY() * downsample;
				double x2 = (tileRegion.getX() + tileRegion.getWidth()) * downsample;
				double y2 = (tileRegion.getY() + tileRegion.getHeight()) * downsample;
				request = RegionRequest.createInstance(path, downsample,
						(int)Math.round(x1),
						(int)Math.round(y1),
						(int)Math.round(x2 - Math.round(x1)),
						(int)Math.round(y2 - Math.round(y1)),
						tileRegion.getZ(),
						tileRegion.getT());
			}
			return request;
		}
		
		public double getDownsample() {
			return downsample;
		}
		
		public int getLevel() {
			return level;
		}
		
		public int getImageX() {
			return request.getX();
		}
		
		public int getImageY() {
			return request.getY();
		}
		
		public int getImageWidth() {
			return request.getWidth();
		}
		
		public int getImageHeight() {
			return request.getHeight();
		}
		
		public int getTileX() {
			return tileRegion.getX();
		}
		
		public int getTileY() {
			return tileRegion.getY();
		}
		
		public int getTileWidth() {
			return tileRegion.getWidth();
		}
		
		public int getTileHeight() {
			return tileRegion.getHeight();
		}
		
		public int getZ() {
			return tileRegion.getZ();
		}
		
		public int getT() {
			return tileRegion.getT();
		}
		
	}

	private Collection<TileRequest> tileRequests;
	
	protected synchronized Collection<TileRequest> getAllTileRequests() {
		if (tileRequests == null)
			tileRequests = Collections.unmodifiableCollection(getAllTileRequests(this));
		return tileRequests;
	}
	
	/**
	 * Request a collection of <i>all</i> tiles that this server must be capable of returning. 
	 * The default implementation provides a dense grid of all expected tiles across all resolutions, 
	 * time points, z-slices and x,y coordinates.
	 * 
	 * @return
	 */
	protected static Collection<TileRequest> getAllTileRequests(ImageServer<?> server) {
		var set = new LinkedHashSet<TileRequest>();
		
		var path = server.getPath();
		var tileWidth = server.getPreferredTileWidth();
		var tileHeight = server.getPreferredTileHeight();
		var downsamples = server.getPreferredDownsamples();
		
		for (int level = 0; level < downsamples.length; level++) {
			double downsample = downsamples[level];
			int height = server.getLevelHeight(level);
			int width = server.getLevelWidth(level);
			for (int t = 0; t < server.nTimepoints(); t++) {
				for (int z = 0; z < server.nZSlices(); z++) {
					for (int y = 0; y < height; y += tileHeight) {
						int th = tileHeight;
						if (y + th > height)
							th = height - y;
						
						for (int x = 0; x < width; x += tileWidth) {
							int tw = tileWidth;
							if (x + tw > width)
								tw = width - x;
							
							var tile = new TileRequest(
									path, downsample, level, ImageRegion.createInstance(
											x, y, tw, th, z, t)
									);
							set.add(tile);
						}
					}					
				}
			}
		}
		return set;
	}
	
	
	protected class TileRequestManager {
		
		private Map<String, Set<TileRequest>> tiles = new LinkedHashMap<>();
		
		private String getKey(TileRequest tile) {
			return getKey(tile.getLevel(), tile.getZ(), tile.getT());
		}
		
		private String getKey(int level, int z, int t) {
			return level + ":" + z + ":" + t;
		}
		
		TileRequestManager(Collection<TileRequest> tiles) {
			for (var tile : tiles) {
				var key = getKey(tile);
				var set = this.tiles.get(key);
				if (set == null) {
					set = new LinkedHashSet<>();
					this.tiles.put(key, set);
				}
				set.add(tile);
			}
		}
		
		public TileRequest getTile(int level, int x, int y, int z, int t) {
			var key = getKey(level, z, t);
			var set = tiles.get(key);
			if (set != null) {
				for (var tile : tiles.get(key)) {
					if (tile.getLevel() == level && tile.getRegionRequest().contains(x, y, z, t))
						return tile;
				}				
			}
			return null;
		}
		
		public List<TileRequest> getTiles(RegionRequest request) {
			int level = getPreferredResolutionLevel(request.getDownsample());
			var key = getKey(level, request.getZ(), request.getT());
			var set = tiles.get(key);
			var list = new ArrayList<TileRequest>();
			if (set != null) {
				for (var tile : set) {
					if (request.intersects(tile.getRegionRequest()))
						list.add(tile);
				}
			}
			return list;
		}
		
	}
	
}
