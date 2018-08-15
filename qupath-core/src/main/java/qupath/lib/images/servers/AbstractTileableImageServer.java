package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
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
	private BufferedImage getTile(final RegionRequest request) {
		BufferedImage imgCached = cache.get(request);
		if (imgCached != null) { 
			logger.trace("Returning cached tile: {}", request);
			return imgCached;
		}
		logger.trace("Reading tile: {}", request);
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
		// Check for the special case where we are requesting a single tile, which exactly matches the request
		if (requests.size() == 1 && request.equals(requests.get(0))) {
			return getTile(requests.get(0));
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
//			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			for (RegionRequest tileRequest : requests) {
				BufferedImage imgTile = getTile(tileRequest);
				g2d.drawImage(imgTile, tileRequest.getX(), tileRequest.getY(), tileRequest.getWidth(), tileRequest.getHeight(), null);
			}
			g2d.dispose();
			
			long endTime = System.currentTimeMillis();
			logger.trace("Requested " + requests.size() + " tiles in " + (endTime - startTime) + " ms (RGB)");

			return imgResult;
		} else {
			// Figure out which tiles we need
			RegionRequest[][] requestGrid = getTileGridToRequest(this, request, -1, -1);
			// Request all of the tiles we need & figure out image dimensions
			BufferedImage[][] tileGrid = new BufferedImage[requestGrid.length][];
			int tempHeight = 0;
			int tempWidth = 0;
			BufferedImage imgTile = null;
			for (int y = 0; y < requestGrid.length; y++) {
				RegionRequest[] requestRow = requestGrid[y];
				BufferedImage[] tileRow = new BufferedImage[requestRow.length];
				for (int x = 0; x < requestRow.length; x++) {
					imgTile = getTile(requestRow[x]);
					tileRow[x] = imgTile;
					if (y == 0)
						tempWidth += imgTile.getWidth();
					if (x == 0)
						tempHeight += imgTile.getHeight();
				}	
				tileGrid[y] = tileRow;
			}
			// Create the image
			if (imgTile == null)
				return null;
			
			// Create a raster at the downsample level of the tile requests
			// TODO: Consider resizing tiles while requesting; this would reduce spikes in memory use
			// but hopefully will not be necessary because of the use of a suitable pyramidal format
			SampleModel model = imgTile.getSampleModel().createCompatibleSampleModel(tempWidth, tempHeight);
			WritableRaster raster = WritableRaster.createWritableRaster(model, null);
			ColorModel colorModel = imgTile.getColorModel();
			boolean alphaPremultiplied = imgTile.isAlphaPremultiplied();
			// Put all the tile into a single raster
			int yy = 0;
			for (int y = 0; y < tileGrid.length; y++) {
				BufferedImage[] tileRow = tileGrid[y];
				int xx = 0;
				int lastHeight = 0;
				for (int x = 0; x < tileRow.length; x++) {
					// Get the tile
					imgTile = tileRow[x];
					// Insert tile within the image
					raster.setDataElements(xx, yy, imgTile.getRaster());
					xx += imgTile.getWidth();
					lastHeight = imgTile.getHeight();
				}
				yy += lastHeight;
			}
			// Extract the region of interest from the full raster, if we need to
			RegionRequest firstTile = requestGrid[0][0];
			RegionRequest[] lastRequestRow = requestGrid[requestGrid.length-1];
			RegionRequest lastTile = lastRequestRow[lastRequestRow.length-1];
			int xTileStart = firstTile.getX();
			int yTileStart = firstTile.getY();
			int xTileEnd = lastTile.getX() + lastTile.getWidth();
			int yTileEnd = lastTile.getY() + lastTile.getHeight();
			if (xTileStart != request.getX() || yTileStart != request.getY() ||
					xTileEnd != request.getX() + request.getWidth() ||
					yTileEnd != request.getY() + request.getHeight()) {
				double tileDownsample = firstTile.getDownsample();
				int parentX = (int)Math.round((request.getX() - xTileStart) / tileDownsample);
				int parentY = (int)Math.round((request.getY() - yTileStart) / tileDownsample);
				int parentX2 = parentX + (int)Math.round(request.getWidth() / tileDownsample);
				int parentY2 = parentY + (int)Math.round(request.getHeight() / tileDownsample);
				// TODO: Consider creating an entirely separate (non-child) raster to improve efficiency
				try {
					// Handle the fact that regions might be requested beyond the image boundary
					int prependX = 0, prependY = 0, appendX = 0, appendY = 0;
					if (parentX < 0) {
						prependX = -parentX;
						parentX = 0;
					} if (parentY < 0) {
						prependY = -parentY;
						parentY = 0;
					} if (parentX2 >= raster.getWidth()) {
						appendX = parentX2 - raster.getWidth();
						parentX2 = raster.getWidth()-1;
					} if (parentY2 >= raster.getHeight()) {
						appendY = parentY2 - raster.getHeight();
						parentY2 = raster.getHeight()-1;
					}
					raster = raster.createWritableChild(parentX, parentY, parentX2 - parentX, parentY2 - parentY, 0, 0, null);
					int finalWidth = raster.getWidth() + prependX + appendX;
					int finalHeight = raster.getHeight() + prependY + appendY;
					if (raster.getWidth() != finalWidth || raster.getHeight() != finalHeight) {
						logger.debug("Padding image from {}, {} to dimensions {}, {}", raster.getWidth(), raster.getHeight(), finalWidth, finalHeight);
						WritableRaster raster2 = raster.createCompatibleWritableRaster(finalWidth, finalHeight);						
						raster2.setRect(prependX, prependY, raster);
						raster = raster2;
					}
				} catch (Exception e) {
					logger.error("Problem extracting region with coordinates ({}, {}, {}, {}) from raster with size ({}, {})", 
							parentX, parentY, parentX2 - parentX, parentY2 - parentY, raster.getWidth(), raster.getHeight());
					throw(e);
				}
			}
			
			// Return the image, resizing if necessary
			BufferedImage imgResult = new BufferedImage(colorModel, raster, alphaPremultiplied, null);
			imgResult = resize(imgResult, width, height, false);
			
			long endTime = System.currentTimeMillis();
			logger.trace("Requested " + requests.size() + " tiles in " + (endTime - startTime) + " ms (non-RGB)");
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
		
//		// Warning!  This doesn't actually work!  It performs some unwelcome rescaling of pixel intensities
//		logger.warn("Resizing not implemented properly for images with type {} - pixel values will be surreptitiously rescaled", img.getType());
//		return AWTImageTools.scale(img, finalWidth, finalHeight, false);
	}
	
	
	
	public static RegionRequest[][] getTileGridToRequest(ImageServer<?> server, RegionRequest request, int tileWidth, int tileHeight) {

		double downsamplePreferred = server.getPreferredDownsampleFactor(request.getDownsample());

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
		int tileWidthForLevel;
		int tileHeightForLevel;
		if (GeneralTools.almostTheSame(downsamplePreferred, (int)(downsamplePreferred + .5), 0.001)) {
			tileWidthForLevel = (int)(tileWidth * (int)(downsamplePreferred + .5) + .5);
			tileHeightForLevel = (int)(tileHeight * (int)(downsamplePreferred + .5) + .5);
		}
		else {
			tileWidthForLevel = (int)(tileWidth * downsamplePreferred + .5);
			tileHeightForLevel = (int)(tileHeight * downsamplePreferred + .5);
		}

		// Get the starting indices, shifted to actual tile boundaries
		int xStart = (int)Math.max(0, (int)(request.getX() / tileWidthForLevel) * tileWidthForLevel);
		int yStart = (int)Math.max(0, (int)(request.getY()/ tileHeightForLevel) * tileHeightForLevel);
		// Determine the visible image dimensions at the current downsample
		double visibleWidth = request.getWidth();
		double visibleHeight = request.getHeight();

		int serverWidth = server.getWidth();
		int serverHeight = server.getHeight();

		// Get the ending image indices (non-inclusive), again shifted to actual tile boundaries or the image end
		int xEnd = (int)Math.min(serverWidth, Math.ceil(request.getX() + visibleWidth));
		int yEnd = (int)Math.min(serverHeight, Math.ceil(request.getY() + visibleHeight));
		
		// Try to ensure that we have at least one full tile
		if (serverWidth - xStart < tileWidthForLevel && serverWidth >= tileWidthForLevel)
			xStart = serverWidth - tileWidthForLevel;
		if (serverHeight - yStart < tileHeightForLevel && serverHeight >= tileHeightForLevel)
			yStart = serverHeight - tileHeightForLevel;

		// Loop through and create the tile requests
		// Here, I've attempted to request central regions first in order to improve the perception of image loading
		int nx = (int)Math.ceil((double)(xEnd - xStart) / tileWidthForLevel);
		int ny = (int)Math.ceil((double)(yEnd - yStart) / tileHeightForLevel);
		
		RegionRequest[][] regions = new RegionRequest[ny][nx];

		for (int yi = 0; yi < ny; yi++) {
			
			int yy = yStart + yi * tileHeightForLevel;
			int hh = tileHeightForLevel;
			if (yy + hh > serverHeight)
				hh = serverHeight - yy;
//			int yRemainder = serverHeight - (yy + tileHeightForLevel);
//			if (yRemainder < tileHeightForLevel) {
//				hh = serverHeight - yy;
//			}
			
			for (int xi = 0; xi < nx; xi++) {

				int xx = xStart + xi * tileWidthForLevel;
				int ww = tileWidthForLevel;
				
				// Check, if we have a partial tile - if so, then skip
				// Otherwise, if we have a tile that is right next to the image boundary, expand it to include the rest of the image
				if (xx + ww > serverWidth)
					ww = serverWidth - xx;
//				int xRemainder = serverWidth - (xx + tileWidthForLevel);
//				if (xRemainder < tileWidthForLevel) {
//					ww = serverWidth - xx;
//				}

				RegionRequest tileRequest = RegionRequest.createInstance(request.getPath(), downsamplePreferred, 
						xx, yy, ww, hh,
						request.getZ(), request.getT());

				regions[yi][xi] = tileRequest;
			}
		}
		return regions;
	}
	
	
}
