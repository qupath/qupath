package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	
		

	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
		// Check if we already have a tile for precisely this occasion
		BufferedImage img = cache.get(request);
		if (img != null)
			return img;
		
		// Figure out which tiles we need
		TileRequestGrid requestGrid = getTileGridToRequest(this, request, -1, -1);
		if (requestGrid == null || requestGrid.isEmpty())
			return null;
		
		int nRequests = requestGrid.size();
		
		// Check for the special case where we are requesting a single tile, which exactly matches the request
		if (nRequests == 1 && request.equals(requestGrid.getTileRequest(0, 0).getRegionRequest())) {
			return getTile(requestGrid.getTileRequest(0, 0));
		}
		long startTime = System.currentTimeMillis();
		// Handle the general case for RGB
		int width = (int)Math.round(request.getWidth() / request.getDownsample());
		int height = (int)Math.round(request.getHeight() / request.getDownsample());
		if (isRGB()) {
			BufferedImage imgResult = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = imgResult.createGraphics();
			g2d.scale(1.0/request.getDownsample(), 1.0/request.getDownsample());
			g2d.translate(-request.getX(), -request.getY());
			// Interpolate if downsampling
			if (request.getDownsample() > 1)
				g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			for (TileRequest tileRequest : requestGrid.tileRequests) {
				BufferedImage imgTile = getTile(tileRequest);
				g2d.drawImage(imgTile, tileRequest.getImageX(), tileRequest.getImageY(), tileRequest.getImageWidth(), tileRequest.getImageHeight(), null);
			}
			g2d.dispose();
			
			long endTime = System.currentTimeMillis();
			logger.trace("Requested " + nRequests + " tiles in " + (endTime - startTime) + " ms (RGB)");

			return imgResult;
		} else {
			// Request all of the tiles we need & figure out image dimensions
			// Do all this at the pyramid level of the tiles
			SampleModel model = null;
			WritableRaster raster = null;
			ColorModel colorModel = null;
			boolean alphaPremultiplied = false;
			int yy = 0;
			for (int y = 0; y < requestGrid.nTilesY(); y++) {
				int xx = 0;
				TileRequest tileRequest = null;
				for (int x = 0; x < requestGrid.nTilesX(); x++) {
					tileRequest = requestGrid.getTileRequest(x, y);
					BufferedImage imgTile = getTile(tileRequest);
					if (imgTile != null) {
						// Preallocate a raster if we need to, and everything else the tile might give us
						if (raster == null) {
							model = imgTile.getSampleModel().createCompatibleSampleModel(requestGrid.totalWidth(), requestGrid.totalHeight());
							raster = WritableRaster.createWritableRaster(model, null);
							colorModel = imgTile.getColorModel();
							alphaPremultiplied = imgTile.isAlphaPremultiplied();							
						}
						// Insert the tile into the raster
						raster.setDataElements(xx, yy, imgTile.getRaster());
					}
					xx += tileRequest.getTileWidth();
				}	
				yy += tileRequest.getTileHeight();
			}
			// Maybe we don't have anything at all (which is not an error if the image is sparse!)
			if (raster == null)
				return null;
			
			// Extract the region of interest from the full raster, if we need to
			TileRequest firstTile = requestGrid.tileRequests.get(0);
			TileRequest lastTile = requestGrid.tileRequests.get(requestGrid.tileRequests.size()-1);
			
			// Calculate the requested region mapped to the pyramidal level, and relative to the tiled image
			double tileDownsample = firstTile.getRegionRequest().getDownsample();
			int xStart = (int)Math.round(request.getX() / tileDownsample) - firstTile.getTileX();
			int yStart = (int)Math.round(request.getY() / tileDownsample) - firstTile.getTileY();
			int xEnd = (int)Math.round((request.getX() + request.getWidth()) / tileDownsample) - firstTile.getTileX();
			int yEnd = (int)Math.round((request.getY() + request.getHeight()) / tileDownsample) - firstTile.getTileY();
			
			// Do cropping, if we need to
			if (xStart > 0 || yStart > 0 || xEnd < raster.getWidth() || yEnd < raster.getHeight()) {
				raster = raster.createWritableChild(
						Math.max(xStart, 0),
						Math.max(yStart, 0),
						Math.min(raster.getWidth() - Math.max(xStart, 0), xEnd - Math.max(xStart, 0)),
						Math.min(raster.getHeight() - Math.max(yStart, 0), yEnd - Math.max(yStart, 0)),
						0, 0, null);
			}
//			// TODO: Consider whether to do padding if we need to
//			if (xStart < 0 || yStart < 0 || xEnd > raster.getWidth() || yEnd > raster.getHeight()) {
//				
//			}
//			
//			if (!(xStart == 0 && yStart == 0 && xWidth == raster.getWidth() && yHeight == raster.getHeight())) {
//				System.err.println(String.format("I want %d, %d, %d, %d; I have %d, %d", xStart, yStart, xWidth, yHeight, raster.getWidth(), raster.getHeight()));
//			}
			
			
//			int beforeX = (int)Math.round((request.getX() - firstTile.getImageX()) / firstTile.getRegionRequest().getDownsample());
//			int beforeY = (int)Math.round((request.getY() - firstTile.getImageY()) / firstTile.getRegionRequest().getDownsample());
//			int afterX = (int)Math.round((lastTile.getImageX() + lastTile.getImageWidth() - request.getX() - request.getWidth()) / lastTile.getRegionRequest().getDownsample());
//			int afterY = (int)Math.round((lastTile.getImageY() + lastTile.getImageHeight() - request.getY() - request.getHeight()) / lastTile.getRegionRequest().getDownsample());
//			
//			if (beforeX > 0 || beforeY > 0 || afterX > 0 || afterY > 0) {
//				raster = raster.createWritableChild(parentX, parentY, parentX2 - parentX, parentY2 - parentY, 0, 0, null);
//				int finalWidth = raster.getWidth() + prependX + appendX;
//				int finalHeight = raster.getHeight() + prependY + appendY;
//				if (raster.getWidth() != finalWidth || raster.getHeight() != finalHeight) {
//					logger.debug("Padding image from {}, {} to dimensions {}, {}", raster.getWidth(), raster.getHeight(), finalWidth, finalHeight);
//					WritableRaster raster2 = raster.createCompatibleWritableRaster(finalWidth, finalHeight);						
//					raster2.setRect(prependX, prependY, raster);
//					raster = raster2;
//				}
//			}
//			
//			int xTileStart = firstTile.getImageX();
//			int yTileStart = firstTile.getImageY();
//			int xTileEnd = lastTile.getImageX() + lastTile.getImageWidth();
//			int yTileEnd = lastTile.getImageY() + lastTile.getImageHeight();
//			if (xTileStart != request.getX() || yTileStart != request.getY() ||
//					xTileEnd != request.getX() + request.getWidth() ||
//					yTileEnd != request.getY() + request.getHeight()) {
//				double tileDownsample = firstTile.getRegionRequest().getDownsample();
//				int parentX = (int)Math.round((request.getX() - xTileStart) / tileDownsample);
//				int parentY = (int)Math.round((request.getY() - yTileStart) / tileDownsample);
//				int parentX2 = parentX + (int)Math.round(request.getWidth() / tileDownsample);
//				int parentY2 = parentY + (int)Math.round(request.getHeight() / tileDownsample);
//				// TODO: Consider creating an entirely separate (non-child) raster to improve efficiency
//				try {
//					// Handle the fact that regions might be requested beyond the image boundary
//					int prependX = 0, prependY = 0, appendX = 0, appendY = 0;
//					if (parentX < 0) {
//						prependX = -parentX;
//						parentX = 0;
//					} if (parentY < 0) {
//						prependY = -parentY;
//						parentY = 0;
//					} if (parentX2 >= raster.getWidth()) {
//						appendX = parentX2 - raster.getWidth();
//						parentX2 = raster.getWidth()-1;
//					} if (parentY2 >= raster.getHeight()) {
//						appendY = parentY2 - raster.getHeight();
//						parentY2 = raster.getHeight()-1;
//					}
//					raster = raster.createWritableChild(parentX, parentY, parentX2 - parentX, parentY2 - parentY, 0, 0, null);
//					int finalWidth = raster.getWidth() + prependX + appendX;
//					int finalHeight = raster.getHeight() + prependY + appendY;
//					if (raster.getWidth() != finalWidth || raster.getHeight() != finalHeight) {
//						logger.debug("Padding image from {}, {} to dimensions {}, {}", raster.getWidth(), raster.getHeight(), finalWidth, finalHeight);
//						WritableRaster raster2 = raster.createCompatibleWritableRaster(finalWidth, finalHeight);						
//						raster2.setRect(prependX, prependY, raster);
//						raster = raster2;
//					}
//				} catch (Exception e) {
//					logger.error("Problem extracting region with coordinates ({}, {}, {}, {}) from raster with size ({}, {})", 
//							parentX, parentY, parentX2 - parentX, parentY2 - parentY, raster.getWidth(), raster.getHeight());
//					throw(e);
//				}
//			}
			
			// Return the image, resizing if necessary
			BufferedImage imgResult = new BufferedImage(colorModel, raster, alphaPremultiplied, null);
//			System.err.println(request);
//			System.err.println("Before: " + imgResult.getWidth() + ", " + imgResult.getHeight());
//			System.err.println("Size: " + width + ", " + height);
			imgResult = resize(imgResult, width, height, false);
			
			long endTime = System.currentTimeMillis();
			logger.trace("Requested " + nRequests + " tiles in " + (endTime - startTime) + " ms (non-RGB)");
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
	 * Estimate image width for specified pyramid level.
	 * <p>
	 * Default implementation just divides the image width by the downsample; 
	 * subclasses can override this if they have additional information.
	 * 
	 * @param level
	 * @return
	 */
	protected int getLevelWidth(int level) {
		return (int)(getWidth() / getPreferredDownsamplesArray()[level]);
	}
	
	/**
	 * Estimate image height for specified pyramid level.
	 * <p>
	 * Default implementation just divides the image height by the downsample; 
	 * subclasses can override this if they have additional information.
	 * 
	 * @param level
	 * @return
	 */
	protected int getLevelHeight(int level) {
		return (int)(getHeight() / getPreferredDownsamplesArray()[level]);
	}
	
	
	TileRequestGrid getTileGridToRequest(ImageServer<?> server, RegionRequest request, int tileWidth, int tileHeight) {

		double[] downsamples = server.getPreferredDownsamples();
		int level = ServerTools.getClosestDownsampleIndex(downsamples, request.getDownsample());
		double downsample = downsamples[level];

		// Determine what the tile size will be in the original image space for the requested downsample
		// Aim for a round number - preferred downsamples can be a bit off due to rounding
		if (tileWidth <= 0)
			tileWidth = server.getPreferredTileWidth();
		if (tileHeight <= 0)
			tileHeight = server.getPreferredTileHeight();
		
		// If the preferred sizes are out of range, use defaults
		if (tileWidth <= 0)
			tileWidth = 256;
		if (tileHeight <= 0)
			tileHeight = 256;
		
		// Find starting point, in pyramid level coordinates
		int tileXStart = (int)Math.max(0, request.getX() / downsample / tileWidth) * tileWidth;
		int tileYStart = (int)Math.max(0, request.getY() / downsample / tileHeight) * tileHeight;

		// Find end point, in pyramid level coordinates
		int levelWidth = getLevelWidth(level);
		int levelHeight = getLevelHeight(level);
		int tileXEnd = (int)Math.ceil(Math.min(levelWidth, (request.getX() + request.getWidth()) / downsample / tileWidth)) * tileWidth;
		int tileYEnd = (int)Math.ceil(Math.min(levelHeight, (request.getY() + request.getHeight()) / downsample / tileHeight)) * tileHeight;
		
		// Loop through and create the tile requests
		int nx = (int)Math.ceil((double)(tileXEnd - tileXStart) / tileWidth);
		int ny = (int)Math.ceil((double)(tileYEnd - tileYStart) / tileHeight);
		String path = server.getPath();
		List<TileRequest> tileRequests = new ArrayList<>();
		int z = request.getZ();
		int t = request.getT();
		for (int ty = tileYStart; ty < tileYEnd; ty += tileHeight) {
			for (int tx = tileXStart; tx < tileXEnd; tx += tileWidth) {
				// Get a tile width & height constrained to the level
				int tw = tileWidth;
				int th = tileHeight;
				if (tx + tw >= levelWidth)
					tw = levelWidth - tx;
				if (ty + th >= levelHeight)
					th = levelHeight - ty;
				
				// Calculate closest image coordinates
				int ix = (int)Math.round(tx * downsample);
				int iy = (int)Math.round(ty * downsample);
				int iw = (int)Math.round((tx + tw) * downsample) - ix;
				int ih = (int)Math.round((ty + th) * downsample) - iy;
				RegionRequest tempRequest = RegionRequest.createInstance(path, downsample,
						ix, iy, iw, ih,
						z, t);
				TileRequest tileRequest = new TileRequest(tempRequest, level, tw, th);
				tileRequests.add(tileRequest);
			}
		}
		return new TileRequestGrid(tileRequests, nx);
	}
	
	
	
	static class TileRequestGrid {
		
		private List<TileRequest> tileRequests;
		private int nx, ny;
		
		TileRequestGrid(List<TileRequest> tileRequests, int nx) {
			this.tileRequests = tileRequests;
			this.nx = nx;
			this.ny = tileRequests.size() / nx;
		}
		
		boolean isEmpty() {
			return tileRequests.isEmpty();
		}

		int size() {
			return tileRequests.size();
		}

		int nTilesY() {
			return ny;
		}

		TileRequest getTileRequest(int x, int y) {
			return tileRequests.get(y * nTilesX() + x);
		}

		int nTilesX() {
			return nx;
		}
		
		int totalWidth() {
			int width = 0;
			for (int x = 0; x < nTilesX(); x++)
				width += getTileRequest(x, 0).getTileWidth();
			return width;
		}
		
		int totalHeight() {
			int height = 0;
			for (int y = 0; y < nTilesY(); y++)
				height += getTileRequest(0, y).getTileHeight();
			return height;
		}
		
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
	protected static class TileRequest {
		
		final RegionRequest request;
		
		final int level;
		final int tileWidth;
		final int tileHeight;
		
		TileRequest(final RegionRequest request, final int level, final int tileWidth, final int tileHeight) {
			this.request = request;
			this.level = level;
			this.tileWidth = tileWidth;
			this.tileHeight = tileHeight;
		}
		
		public RegionRequest getRegionRequest() {
			return request;
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
			return (int)Math.round(request.getX() / request.getDownsample());
		}
		
		public int getTileY() {
			return (int)Math.round(request.getY() / request.getDownsample());
		}
		
		public int getTileWidth() {
			return tileWidth;
		}
		
		public int getTileHeight() {
			return tileHeight;
		}
		
		public int getZ() {
			return request.getZ();
		}
		
		public int getT() {
			return request.getT();
		}
		
	}
	
	
	
}
