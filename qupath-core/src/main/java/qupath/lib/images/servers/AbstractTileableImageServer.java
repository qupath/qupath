package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import qupath.lib.common.GeneralTools;
import qupath.lib.regions.RegionRequest;

public abstract class AbstractTileableImageServer extends AbstractImageServer<BufferedImage> {
	
	protected AbstractTileableImageServer(URI uri) {
		super(uri, BufferedImage.class);
	}


	private static Logger logger = LoggerFactory.getLogger(AbstractTileableImageServer.class);
	
	
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
		var tiles = getTiles(request);
		
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
				imgResult = resize(imgResult, width, height);
			
			long endTime = System.currentTimeMillis();
			logger.trace("Requested " + tiles.size() + " tiles in " + (endTime - startTime) + " ms (non-RGB)");
			return imgResult;
		}
	}

	
	/**
	 * Resize the image to have the requested width/height, using area averaging and bilinear interpolation.
	 * 
	 * @param img input image to be resized
	 * @param finalWidth target output width
	 * @param finalHeight target output height
	 * @return resized image
	 */
	public static BufferedImage resize(final BufferedImage img, final int finalWidth, final int finalHeight) {
		
//		boolean useLegacyResizing = false;
//		if (useLegacyResizing) {
//			return resize(img, finalWidth, finalHeight, false);
//		}
		
		if (img.getWidth() == finalWidth && img.getHeight() == finalHeight)
			return img;
		
		logger.trace(String.format("Resizing %d x %d -> %d x %d", img.getWidth(), img.getHeight(), finalWidth, finalHeight));
		
		double aspectRatio = (double)img.getWidth()/img.getHeight();
		double finalAspectRatio = (double)finalWidth/finalHeight;
		if (!GeneralTools.almostTheSame(aspectRatio, finalAspectRatio, 0.01)) {
			if (!GeneralTools.almostTheSame(aspectRatio, finalAspectRatio, 0.05))
				logger.warn("Substantial difference in aspect ratio for resized image: {}x{} -> {}x{} ({}, {})", img.getWidth(), img.getHeight(), finalWidth, finalHeight, aspectRatio, finalAspectRatio);
			else
				logger.warn("Slight difference in aspect ratio for resized image: {}x{} -> {}x{} ({}, {})", img.getWidth(), img.getHeight(), finalWidth, finalHeight, aspectRatio, finalAspectRatio);
		}
		
		boolean areaAveraging = true;
		
		var raster = img.getRaster();
		var raster2 = raster.createCompatibleWritableRaster(finalWidth, finalHeight);

		int w = img.getWidth();
		int h = img.getHeight();
		
		var fp = new FloatProcessor(w, h);
		fp.setInterpolationMethod(ImageProcessor.BILINEAR);
		for (int b = 0; b < raster.getNumBands(); b++) {
			float[] pixels = (float[])fp.getPixels();
			raster.getSamples(0, 0, w, h, b, pixels);
			var fp2 = fp.resize(finalWidth, finalHeight, areaAveraging);
			raster2.setSamples(0, 0, finalWidth, finalHeight, b, (float[])fp2.getPixels());
		}
		
		return new BufferedImage(img.getColorModel(), raster2, img.isAlphaPremultiplied(), null);
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
	
	
	
}
