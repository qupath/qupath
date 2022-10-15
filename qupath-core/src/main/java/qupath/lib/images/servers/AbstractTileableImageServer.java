/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
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
	
	private ColorModel colorModel;
	private Map<String, BufferedImage> emptyTileMap = new HashMap<>();
	
	private transient Set<TileRequest> emptyTiles = new HashSet<>();
	
	private static final Long ZERO = Long.valueOf(0L);
	
	// Maintain a record of tiles that could not be cached, so we warn for each only once
	private transient Set<RegionRequest> failedCacheTiles = new HashSet<>();
		
	protected AbstractTileableImageServer() {
		super(BufferedImage.class);
	}
	
	protected BufferedImage getEmptyTile(int width, int height) throws IOException {
		return getEmptyTile(width, height, true);
//		return getEmptyTile(width, height,
//				width == getMetadata().getPreferredTileWidth() &&
//				height == getMetadata().getPreferredTileHeight());
	}
	
	/**
	 * Create an empty tile for this server, using the default color model.
	 * @param width
	 * @param height
	 * @param doCache
	 * @return
	 * @throws IOException
	 */
	protected BufferedImage getEmptyTile(int width, int height, boolean doCache) throws IOException {
		String key = width + "x" + height;
		BufferedImage imgTile = emptyTileMap.get(key);
		if (imgTile == null) {
			imgTile = createEmptyTile(getDefaultColorModel(), width, height);
			if (doCache)
				emptyTileMap.put(key, imgTile);
		}
		return imgTile;
	}
	
	/**
	 * Get an appropriate colormodel that may be used. The default implementation uses 
	 * the default RGB color model for RGB images, or else requests a low-resolution thumbnail 
	 * to extract the color model from it. If neither implementation is sufficient, subclasses 
	 * should override this method.
	 * @return
	 * @throws IOException
	 */
	protected ColorModel getDefaultColorModel() throws IOException {
		if (colorModel == null) {
			if (isRGB())
				colorModel = ColorModel.getRGBdefault();
			else
				colorModel = ColorModelFactory.createColorModel(
						getPixelType(),
						getMetadata().getChannels());
		}
		return colorModel;
	}
	
	/**
	 * Create an 'empty' image tile for a specific color model.
	 * @param colorModel
	 * @param width
	 * @param height
	 * @return
	 */
	static BufferedImage createEmptyTile(ColorModel colorModel, int width, int height) {
		var raster = colorModel.createCompatibleWritableRaster(width, height);
		Hashtable<Object, Object> emptyProperties = new Hashtable<>();
		emptyProperties.put("QUPATH_CACHE_SIZE", ZERO);
		return new BufferedImage(colorModel, raster, false, emptyProperties);
	}
	
	/**
	 * Returns true if the tile is flagged as being empty.
	 * @param img
	 * @return
	 */
	static boolean isEmptyTile(BufferedImage img) {
		return ZERO.equals(img.getProperty("QUPATH_CACHE_SIZE"));
	}
	
	
	synchronized void resetEmptyTileCache() {
		logger.debug("Resetting empty tile cache");
		emptyTileMap.clear();
	}
	
	
	/**
	 * Read a single image tile.
	 * 
	 * @param tileRequest
	 * @return
	 * @throws IOException 
	 */
	protected abstract BufferedImage readTile(final TileRequest tileRequest) throws IOException;
	
	
	/**
	 * Map of tiles currently being requested, so avoid duplicate requests (wait instead for the first request to return).
	 */
	private Map<TileRequest, TileTask> pendingTiles = new ConcurrentHashMap<>();
	
	/**
	 * Count of how many duplicate requests are received for a pending tile.
	 * QuPath *should* strive to minimize these.
	 */
	private int duplicateRequestClashCount = 0;
	
	private static class TileTask extends FutureTask<BufferedImage> {
		
		private Thread thread;

		public TileTask(Thread thread, Callable<BufferedImage> callable) {
			super(callable);
			this.thread = thread;
		}
		
		
		
	}
		
	/**
	 * Get a tile for the request - ideally from the cache, but otherwise read it and 
	 * then add it to the cache.
	 * 
	 * @param tileRequest
	 * @return
	 * @throws IOException 
	 */
	protected BufferedImage getTile(final TileRequest tileRequest) throws IOException {
		// Try to get tile from one of the caches
		var request = tileRequest.getRegionRequest();
		if (emptyTiles.contains(tileRequest))
			return getEmptyTile(tileRequest.getTileWidth(), tileRequest.getTileHeight());
		
		var cache = getCache();
		if (cache != null) {
			var imgCached = cache.get(request);
			if (imgCached != null) { 
				logger.trace("Returning cached tile: {}", request);
				return imgCached;
			}
		}
		logger.trace("Reading tile: {}", request);
		
		BufferedImage imgCached;
		var futureTask = pendingTiles.computeIfAbsent(tileRequest, t -> new TileTask(Thread.currentThread(), () -> readTile(t)));
		var myTask = futureTask.thread == Thread.currentThread();
		try {
			if (myTask)
				futureTask.run();
			else {
				duplicateRequestClashCount++;
				logger.debug("Duplicate request for a pending tile ({} total) - {}", duplicateRequestClashCount, tileRequest.getRegionRequest());
			}
			imgCached = futureTask.get();
		} catch (ExecutionException | InterruptedException e) {
			if (e.getCause() instanceof IOException)
				throw (IOException)e.getCause();
			throw new IOException(e);
		}
		
//		var imgCached = readTile(tileRequest);
		
		// Put the tile in the appropriate cache
		if (myTask) {
			if (imgCached != null) {
				if (isEmptyTile(imgCached)) {
					emptyTiles.add(tileRequest);
				} else if (cache != null) {
					cache.put(request, imgCached);
					// Check if we were able to cache the tile; sometimes we can't if it is too big
					if (!cache.containsKey(request) && failedCacheTiles.add(request))
						logger.warn("Unable to add {} to cache.\nYou might need to give QuPath more memory, or to increase the 'Percentage memory for tile caching' preference.", request);
				}
			}
			pendingTiles.remove(tileRequest);
		}
		
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
	
	
	
	@Override
	public BufferedImage readRegion(final RegionRequest request) throws IOException {
		// Check if we already have a tile for precisely this occasion - with the right server path
		// Make a defensive copy, since the cache is critical
		var cache = getCache();
		BufferedImage img = request.getPath().equals(getPath()) && cache != null ? cache.get(request) : null;
		if (img != null)
			return BufferedImageTools.duplicate(img);
		
		// Figure out which tiles we need
		Collection<TileRequest> tiles = getTileRequestManager().getTileRequests(request);
		
		// If no tiles found, we assume a sparse image with nothing relevant to display for this location
		// Note: This can be problematic for tile caching
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
				return BufferedImageTools.duplicate(imgTile);
			}
		}
		
		// Ensure all tiles are either cached or pending before we continue
		prerequestTiles(tiles);
		
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
			
			// Requests could be parallelized, but should consider the number of threads created/what happens if one blocks
//			Map<TileRequest, BufferedImage> map = tiles.stream()
//					.collect(Collectors.toMap(tileRequest -> tileRequest,
//							tileRequest -> {
//					try {
//						return getTile(tileRequest);
//					} catch (Exception ex) {
//						return null;
//					}
//					}));
//			for (TileRequest tileRequest : tiles) {
//				BufferedImage imgTile = map.get(tileRequest);
//				if (imgTile != null)
//					g2d.drawImage(imgTile, tileRequest.getImageX(), tileRequest.getImageY(), tileRequest.getImageWidth(), tileRequest.getImageHeight(), null);
//			}
			
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
			
			boolean isEmptyRegion = true;
			if (singleTile) {
				// Use the raster directly, if appropriate (because copying can be expensive)
				BufferedImage imgTile = getTile(tiles.iterator().next());
				if (imgTile != null) {
					raster = imgTile.getRaster();
					colorModel = imgTile.getColorModel();
					alphaPremultiplied = imgTile.isAlphaPremultiplied();
					isEmptyRegion = isEmptyTile(imgTile);
				}
			} else {
				for (var tileRequest : tiles) {
					BufferedImage imgTile = getTile(tileRequest);
					if (imgTile != null && !isEmptyTile(imgTile)) {
						isEmptyRegion = false;
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
						
						copyPixels(imgTile.getRaster(), dx, dy, raster);

//						raster.setRect(
//								dx,
//								dy,
//								imgTile.getRaster());
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
			else if (xEnd - xStart <= 0 || yEnd - yStart <= 0)
				return null;
						
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
				copyPixels(raster, -x, -y, raster2);
				raster = raster2;
			}

			// If we have an empty region, try to use an empty tile
			if (isEmptyRegion) {
				return getEmptyTile(raster.getWidth(), raster.getHeight());
			}

			// Return the image, resizing if necessary
			BufferedImage imgResult = new BufferedImage(colorModel, raster, alphaPremultiplied, null);
			int currentWidth = imgResult.getWidth();
			int currentHeight = imgResult.getHeight();
			if (currentWidth != width || currentHeight != height) {
				imgResult = BufferedImageTools.resize(imgResult, width, height, allowSmoothInterpolation());
			}
			
			long endTime = System.currentTimeMillis();
			logger.trace("Requested " + tiles.size() + " tiles in " + (endTime - startTime) + " ms (non-RGB)");
			return imgResult;
		}
	}
	
	/**
	 * Ensure all tiles in a list are either cached or requested.
	 * If a tile is neither, then a blocking request is made so that the tile will be present later.
	 * The purpose of this is to avoid sequentially requesting the same tiles from multiple threads,
	 * which could cause all threads to block waiting on the same tile - rather than trying to 
	 * get the next one.
	 * @param tiles
	 */
	private void prerequestTiles(Collection<TileRequest> tiles) {
		var cache = getCache();
		for (var tile : tiles) {
			if (cache == null || !cache.containsKey(tile.getRegionRequest()) && !pendingTiles.containsKey(tile)) {
				var futureTask = pendingTiles.computeIfAbsent(tile, t -> new TileTask(Thread.currentThread(), () -> readTile(t)));
				if (futureTask.thread == Thread.currentThread())
					futureTask.run();
			}
		}
	}
	
	/**
	 * This method essentially wraps a call to {@link WritableRaster#setRect(int, int, Raster)}, while working 
	 * around an inconvenient JDK bug: https://bugs.openjdk.java.net/browse/JDK-4847156
	 * <p>
	 * Note that the underlying code is
	 * <pre>
	 * 	dest.setRect(dx, dy, (Raster)source)
	 * </pre>
	 * 
	 * @param source raster containing source pixels
	 * @param dx x-origin of the pixels to copy in the destination raster (may be negative)
	 * @param dy y-origin of the pixels to copy in the destination raster (may be negative)
	 * @param dest destination raster to update
	 */
	private static void copyPixels(WritableRaster source, int dx, int dy, WritableRaster dest) {
		if (dest.getClass().getName().contains("ByteInterleavedRaster") && (dx < 0 || dy < 0)) {
			int sx = Math.max(-dx, 0);
			int sy = Math.max(-dy, 0);
			if (sx >= source.getWidth() || sy >= source.getHeight()) {
				logger.warn("No pixels needed to be copied!");
				return;
			}
			dest.setRect(0, 0, source.createChild(sx, sy, source.getWidth()-sx, source.getHeight()-sy, 0, 0, null));
		} else
			dest.setRect(dx, dy, (Raster)source);
	}
	
	
	/**
	 * Returns true if this server is permitted to use smooth interpolation when resizing.
	 * The default implementation returns true if the channel type is not {@link ChannelType#CLASSIFICATION}.
	 * @return
	 */
	protected boolean allowSmoothInterpolation() {
		return getMetadata().getChannelType() != ChannelType.CLASSIFICATION;
	}
	
	
}