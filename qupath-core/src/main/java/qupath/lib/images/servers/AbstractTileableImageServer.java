package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.regions.RegionRequest;

/**
 * Abstract {@link ImageServer} for BufferedImages that internally breaks up requests into constituent tiles.
 * <p>
 * The actual request is then handled by assembling the tiles, resizing as required.
 * This makes it possible to cache tiles and reuse them more efficiently, and often requires less effort 
 * to implement a new {@link ImageServer}.
 * 
 * @author Pete Bankhead
 *
 */
public abstract class AbstractTileableImageServer extends AbstractImageServer<BufferedImage> {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractTileableImageServer.class);
		
	protected AbstractTileableImageServer() {
		super(BufferedImage.class);
	}
	
	
	/**
	 * Read a single image tile.
	 * 
	 * @param tileRequest
	 * @return
	 */
	protected abstract BufferedImage readTile(final TileRequest tileRequest) throws IOException;
	
	
	/**
	 * Get a tile for the request - ideally from the cache, but otherwise read it and 
	 * then add it to the cache.
	 * 
	 * @param tileRequest
	 * @return
	 */
	protected BufferedImage getTile(final TileRequest tileRequest) throws IOException {
		var cache = getCache();
		BufferedImage imgCached = cache == null ? null : cache.get(tileRequest.getRegionRequest());
		if (imgCached != null) { 
			logger.trace("Returning cached tile: {}", tileRequest.getRegionRequest());
			return imgCached;
		}
		logger.trace("Reading tile: {}", tileRequest.getRegionRequest());
		
		imgCached = readTile(tileRequest);
		
		if (cache != null)
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
	
	
	static BufferedImage duplicate(BufferedImage img) {
		return new BufferedImage(
				img.getColorModel(),
				img.copyData(img.getRaster().createCompatibleWritableRaster()),
				img.isAlphaPremultiplied(),
				null);
	}
	

	@Override
	public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
		// Check if we already have a tile for precisely this occasion - with the right server path
		// Make a defensive copy, since the cache is critical
		var cache = getCache();
		BufferedImage img = request.getPath().equals(getPath()) && cache != null ? cache.get(request) : null;
		if (img != null)
			return duplicate(img);
		
		// Figure out which tiles we need
		Collection<TileRequest> tiles = getTileRequestManager().getTileRequests(request);
		
		// If no tiles found, we assume a sparse image with nothing relevant to display for this location
		if (tiles.isEmpty())
			return null;
		
		// Check for the special case where we are requesting a single tile, which exactly matches the request
		boolean singleTile = tiles.size() == 1;
		if (singleTile) {
			var firstTile = tiles.iterator().next();
			if (firstTile.getRegionRequest().equals(request)) {
				var imgTile = getTile(firstTile);
				if (imgTile == null)
					return null;
				return duplicate(imgTile);
			}
		}
		
		long startTime = System.currentTimeMillis();
		// Handle the general case for RGB
		int width = (int)Math.max(1, Math.round(request.getWidth() / request.getDownsample()));
		int height = (int)Math.max(1, Math.round(request.getHeight() / request.getDownsample()));
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
			
			if (singleTile) {
				// Use the raster directly, if appropriate (because copying can be expensive)
				BufferedImage imgTile = getTile(tiles.iterator().next());
				if (imgTile != null) {
					raster = imgTile.getRaster();
					colorModel = imgTile.getColorModel();
					alphaPremultiplied = imgTile.isAlphaPremultiplied();
				}
			} else {
				for (var tileRequest : tiles) {
					BufferedImage imgTile = getTile(tileRequest);
					if (imgTile != null) {
						// Figure out coordinates
						int dx = tileRequest.getTileX() - tileMinX;
						int dy = tileRequest.getTileY() - tileMinY;
						int tileWidth = tileMaxX - tileMinX;
						int tileHeight = tileMaxY - tileMinY;
						// Preallocate a raster if we need to, and everything else the tile might give us
						if (raster == null) {
							raster = imgTile.getRaster().createCompatibleWritableRaster(tileWidth, tileHeight);
							colorModel = imgTile.getColorModel();
							alphaPremultiplied = imgTile.isAlphaPremultiplied();							
						}
						// Insert the tile into the raster
						if (dx >= raster.getWidth() ||
								dy >= raster.getHeight()
								)
							continue;
						raster.setRect(
								dx,
								dy,
	//							Math.min(raster.getWidth() - tileMinX, imgTile.getWidth()),
	//							Math.min(raster.getHeight() - tileMinY, imgTile.getHeight()),							
								imgTile.getRaster());
					}
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
			
			if (xEnd > getWidth() || yEnd > getHeight())
				logger.warn("Region request is too large for {}x{} image: {}", getWidth(), getHeight(), request);
			
			// Do cropping, if we need to
			if (xStart > 0 || yStart > 0 || xEnd != raster.getWidth() || yEnd != raster.getHeight()) {
				// Best avoid creating a child raster, for memory & convenience reasons
				// (i.e. sometimes weird things happen when not expecting to have a child raster)
				int x = Math.max(xStart, 0);
				int y = Math.max(yStart, 0);
				int w = xEnd - xStart;
				int h = yEnd - yStart;
//				int w = Math.min(raster.getWidth() - xStart, xEnd - xStart);
//				int h = Math.min(raster.getHeight() - yStart, yEnd - yStart);
				var raster2 = raster.createCompatibleWritableRaster(w, h);
				raster2.setRect(-x, -y, (Raster)raster);
				raster = raster2;
			}

			// Return the image, resizing if necessary
			BufferedImage imgResult = new BufferedImage(colorModel, raster, alphaPremultiplied, null);
			int currentWidth = imgResult.getWidth();
			int currentHeight = imgResult.getHeight();
			if (currentWidth != width || currentHeight != height)
				imgResult = BufferedImageTools.resize(imgResult, width, height);
			
			long endTime = System.currentTimeMillis();
			logger.trace("Requested " + tiles.size() + " tiles in " + (endTime - startTime) + " ms (non-RGB)");
			return imgResult;
		}
	}
	
	
	
}
