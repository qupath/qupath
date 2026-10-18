/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.stores;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.MemoryTools;
import qupath.lib.images.cache.GenericImageCache;
import qupath.lib.images.cache.ImageCache;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * An ImageRegionStore suitable for either Swing or JavaFX applications.
 */
public class DefaultImageRegionStore extends AbstractImageRegionStore<BufferedImage> implements ImageRegionRenderer {

	private static final Logger logger = LoggerFactory.getLogger(DefaultImageRegionStore.class);

	public static final DefaultImageRegionStore SHARED_INSTANCE = new DefaultImageRegionStore(ImageCache.getSharedInstance());
	private static final int DEFAULT_THUMBNAIL_WIDTH = 1000;

	private static boolean DEBUG_TILES = !Objects.equals(System.getProperty("qupath.debug.tiles", "false"), "false");

	// Submitting tile requests is expensive enough to cause noticeable lagging in the viewer,
	// so for tiles not needed immediately we delegate this task to a background thread and
	// accumulate the requests in a queue.
	private final BlockingDeque<Request> requestQueue = new LinkedBlockingDeque<>();

	// A cache to store images converted to RGB for display.
	// Because display settings change frequently (e.g., brightness/contrast) we don't want to use the main ImageCache:
	// we'd fill it up too quickly, and lose some of the caching benefits.
	// Also, because display transforms are *quite* quick to compute (but not so fast that they can be calculated
	// on every repaint without lag), the cache can be synchronous and use soft references.
	private final Cache<RegionRequest, BufferedImage> displayCache;

	private DefaultImageRegionStore(GenericImageCache<BufferedImage> cache, int thumbnailWidth) {
		super(cache, thumbnailWidth);
		Thread.ofVirtual().name("tile-requestor").start(this::processRequests);
		long displayCacheSize = calculateDisplayCacheSize();
		logger.info("Display cache size: {} MB", GeneralTools.formatNumber(displayCacheSize / (1024.0 * 1024.0), 1));
		displayCache = Caffeine.newBuilder()
				.weigher(DefaultImageRegionStore::weighRGB)
				.maximumWeight(calculateDisplayCacheSize())
				.initialCapacity(1024)
				.softValues()
				.build();
	}

	private static long calculateDisplayCacheSize() {
		// 10% of memory, clamped between 100 MB and 2 GB.
		// If unknown, default to 500 MB.
		// That could store over 10k x 10k pixels of an RGB image, which would be enough for a few
		// ultra-high resolution monitors...
		return Math.clamp(
				MemoryTools.getMaxMemoryPercent(10.0).orElse(MemoryTools.megabytesToBytes(500)),
				MemoryTools.megabytesToBytes(100),
				MemoryTools.gigabytesToBytes(2));
	}

	/**
	 * Get the shared region store instance.
	 * This is usually the only instance required throughout the whole QuPath application.
	 * @return the shared region store
	 * @since v0.8.0
	 */
	public static DefaultImageRegionStore getSharedInstance() {
		return SHARED_INSTANCE;
	}

	/**
	 * Get the maximum size of the display cache.
	 * This is distinct from {@link ImageCache}, and used only for storing RGB versions of image tiles to
	 * improve viewer performance.
	 * @return the maximum size of the display cache
	 * @see #setDisplayCacheMaxSizeBytes(long)
	 */
	public long getDisplayCacheMaxSizeBytes() {
        return displayCache.policy().eviction().map(Policy.Eviction::getMaximum).orElse(0L);
    }

	/**
	 * Set the maximum size of the display cache.
	 * This is distinct from {@link ImageCache}, and used only for storing RGB versions of image tiles to
	 * improve viewer performance.
	 * @see #getDisplayCacheMaxSizeBytes()
	 */
	public void setDisplayCacheMaxSizeBytes(long maxSize) {
		displayCache.policy().eviction().ifPresent(e -> e.setMaximum(Math.max(0L, maxSize)));
	}

	private static int weighRGB(RegionRequest request, BufferedImage img) {
		long weight = 4L * img.getWidth() * img.getHeight();
		if (weight > Integer.MAX_VALUE) {
			logger.warn("Very large RGB image detected: {} x {}", img.getWidth(), img.getHeight());
			return Integer.MAX_VALUE;
		} else {
			return Math.toIntExact(weight);
		}
	}

	DefaultImageRegionStore(GenericImageCache<BufferedImage> cache) {
		this(cache, DEFAULT_THUMBNAIL_WIDTH);
	}

	private record Request(ImageServer<BufferedImage> server, RegionRequest request) {}

	private void processRequests() {
		try {
			while (true) {
				// We expect slightly better viewer performance when rapidly zooming/panning
				// if we prioritize the most recent requests.
				var next = requestQueue.takeLast();

				getCache().requestImageTile(next.request(), next.server());
			}
		} catch (InterruptedException e) {
			logger.warn("Request submission interrupted", e);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean paintRegionCompletely(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, ImageRenderer imageDisplay, long timeoutMilliseconds) {

		// Loop through and create the image
		List<RequestedTile> workers = new ArrayList<>();
		BufferedImage imgTemp = null;

		for (RegionRequest request : getTilesToRequest(server, clipShapeVisible, downsampleFactor, zPosition, tPosition, null)) {

			if (server.isEmptyRegion(request)) {
				continue;
			}

			Future<BufferedImage> future = getCache().requestImageTile(request, server);

			// If we have an image, paint it & record coordinates
			if (future.isDone()) {
				BufferedImage img = future.resultNow();
				if (img == null)
					continue;
				else if (imageDisplay != null) {
					imgTemp = imageDisplay.applyTransforms(img, imgTemp);
					g.drawImage(imgTemp, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
				} else
					g.drawImage(img, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
			} else {
				// If we've a tile worker, prepare for requesting its results soon...
				workers.add(new RequestedTile(request, future));
			}

		}

		// Loop through any workers now, drawing their tiles too
		boolean success = true;
		for (var worker : workers) {
			BufferedImage imgTile = null;
			try {
				imgTile = worker.future().get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
			} catch (CancellationException e) {
				logger.debug("Repaint skipped because of cancellation...");
				success = false;
				continue;
			} catch (InterruptedException e) {
				logger.debug("Tile request interrupted in 'paintRegionCompletely': {}", e.getMessage());
				return false;
			} catch (ExecutionException e) {
				logger.error("Execution exception in 'paintRegionCompletely'", e);
				return false;
			} catch (TimeoutException e) {
				// If we timed out, try reading directly
				logger.warn("Timed out requesting region ({} ms)... {}", timeoutMilliseconds, worker.request());
			}
			if (imgTile == null)
				continue;
			RegionRequest request = worker.request();
			if (imageDisplay != null) {
				imgTemp = imageDisplay.applyTransforms(imgTile, imgTemp);
				g.drawImage(imgTemp, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
			} else
				g.drawImage(imgTile, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
		}
		return success;
	}

	private record RequestedTile(RegionRequest request, Future<BufferedImage> future) {}


	@Override
	public boolean paintRegion(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, BufferedImage imgThumbnail, ImageRenderer imageDisplay) {
		return paintRegionImpl(server, g, clipShapeVisible, zPosition, tPosition,downsampleFactor, imgThumbnail, imageDisplay, 0);
	}


	private boolean paintRegionImpl(ImageServer<BufferedImage> server,
								Graphics g,
								Shape clipShapeVisible,
								int zPosition,
								int tPosition,
								double downsampleFactor,
								BufferedImage imgThumbnail,
								ImageRenderer imageDisplay,
								int relativeLevel // 0 for the originally requested downsample, incremented each time we paint at a lower resolution because of missing tiles
	) {

		boolean isComplete = true;

		// Check if we have all the regions required for this request
		List<RegionRequest> requests = getTilesToRequest(server, clipShapeVisible, downsampleFactor, zPosition, tPosition, null);

		// If we should be painting recursively, ending up with the thumbnail, do so
		if (imgThumbnail != null) {
			// Check if there are any pixels not currently cached.
			// If so, create a rectangle that surrounds all of them.
			Rectangle missingBounds = computeMissingBounds(requests);

			// If we are missing regions, try (recursively) to repaint at a lower resolution
			if (missingBounds != null) {
				// Paint the next downsample level, if we can
				double nextDownsample = getNextDownsample(server, downsampleFactor);
				if (nextDownsample > 0 && requests.size() > 1)
					// Only request tiles at the next resolution if such a resolution exists, and we'd need more than 1.
					paintRegionImpl(server, g, missingBounds, zPosition, tPosition, nextDownsample, imgThumbnail, imageDisplay, relativeLevel+1);
				else {
					// We've reached the end: the best we can do is paint the thumbnail
					if (imageDisplay != null) {
                        imgThumbnail = imageDisplay.applyTransforms(imgThumbnail, null);
					}
					g.drawImage(imgThumbnail, 0, 0, server.getWidth(), server.getHeight(), null);
				}
			}
		}

		// If we're compositing channels, it's worthwhile to cache RGB tiles for so long as the ImageDisplay remains constant
		boolean useDisplayCache = server != null && !server.isRGB() &&
				server.getMetadata().getChannelType() != ChannelType.CLASSIFICATION &&
				(server.nChannels() > 1 || server.getPixelType() != PixelType.UINT8);
		long displayTimestamp = imageDisplay == null ? 0L : imageDisplay.getLastChangeTimestamp();
		String displayCachePath = null;
		if (useDisplayCache) {
			if (imageDisplay == null)
				displayCachePath = "RGB::" + server.getPath();
			else
				displayCachePath = server.getPath() + imageDisplay.getUniqueID();
		}

		// Loop through and draw whatever tiles we've got
		BufferedImage imgToDraw = null;
		for (RegionRequest request : requests) {
			// Load the image
			BufferedImage img = getCache().getIfPresent(request);

			// If there is no image tile, try to get a lower-resolution version to draw -
			// this can actually paint over previously-available regions, but they will be repainted again when this region's request comes through
			if (img == null) {
				// Check the tile isn't just empty
				if (!getCache().containsKey(request)) {
					// TODO: Consider how best to avoid piling up requests when repaints are made quickly
					requestQueue.add(new Request(server, request));
					isComplete = false;
				}
				continue;
			}

			// We can abort now - we know the display has changed, additional painting is futile...
			if (imageDisplay != null && displayTimestamp != imageDisplay.getLastChangeTimestamp())
				return false;

			// If we have an image, paint it & record coordinates
			// Apply any required color transformations
			if (useDisplayCache) {
				// Apply transforms, creating & caching an RGB image to speed things up next time
				RegionRequest requestCache = RegionRequest.createInstance(displayCachePath, request.getDownsample(), request);
				imgToDraw = displayCache.get(requestCache, _ -> toRGB(img, imageDisplay));
			} else if (imageDisplay != null) {
				// Apply transforms, trying to reuse the same output image
				// Note: this assumes pixels can't be transparent
				imgToDraw = toRGB(img, imageDisplay, imgToDraw);
			} else {
				imgToDraw = img;
			}
			g.drawImage(imgToDraw, request.getX(), request.getY(), request.getWidth(), request.getHeight(), null);
			if (DEBUG_TILES) {
				paintTileBoundaries(g, request);
			}
		}
		return isComplete;
	}

	/**
	 * Identify the bounding rectangle for requests that aren't currently cached.
	 * @param requests a list of all the requests we need
	 * @return a bounding rectangle for non-cached regions, or null if all regions are cached
	 */
	private Rectangle computeMissingBounds(List<RegionRequest> requests) {
		// Check if there are any pixels not currently cached.
		// If so, create a rectangle that surrounds all of them.
		Rectangle missingBounds = null;
		for (RegionRequest request : requests) {
			BufferedImage img = getCache().getIfPresent(request);
			if (img == null) {// && !mapCache.containsKey(request)) {
				if (missingBounds == null)
					missingBounds = AwtTools.getBounds(request);
				else
					missingBounds = missingBounds.union(AwtTools.getBounds(request));
			}
		}
		return missingBounds;
	}

	/**
	 * Get the next downsample that is higher than the current downsample.
	 * @param server the image server
	 * @param currentDownsample the current downsample
	 * @return the next highest downsample, or -1 if the current downsample is already the highest
	 *         (i.e. it corresponds to the lowest-resolution image).
	 */
	private static double getNextDownsample(ImageServer<BufferedImage> server, double currentDownsample) {
		double[] preferredDownsamples = server.getPreferredDownsamples();
		Arrays.sort(preferredDownsamples);
		for (double d : preferredDownsamples) {
			if (d > Math.max(currentDownsample, 1)) {
				return d;
			}
		}
		return -1;
	}

	private static BufferedImage toRGB(BufferedImage img, ImageRenderer renderer) {
		return toRGB(img, renderer, null);
	}

	private static BufferedImage toRGB(BufferedImage img, ImageRenderer renderer, BufferedImage imgOutput) {
		if (renderer != null) {
			if (imgOutput == null || imgOutput.getWidth() != img.getWidth() || imgOutput.getHeight() != img.getHeight()) {
				return renderer.applyTransforms(img, null);
			} else {
				return renderer.applyTransforms(img, imgOutput);
			}
		} else {
			BufferedImage imgTemp = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = imgTemp.createGraphics();
			g2d.drawImage(img, 0, 0, null);
			g2d.dispose();
			return imgTemp;
		}
	}

	private static void paintTileBoundaries(Graphics g, RegionRequest request) {
		g.setColor(Color.RED);
		g.drawRect(request.getX(), request.getY(), request.getWidth(), request.getHeight());
	}


	/**
	 * Given ImageServer, determine the boundaries of the image tiles that would be needed to paint
	 * a specified shape (defined by coordinates in the full-resolution image space).
	 * The downsampleFactor is used to determine the resolution at which to request the tiles.
	 *
	 * @param server The ImageServer from which the tiles would be requested
	 * @param clipShape The requested shape, defined in the full-resolution image space
	 * @param downsampleFactor The downsampleFactor determining the resolution at which tiles should be requested
	 * @param zPosition The zPosition from which to request tiles
	 * @param tPosition The tPosition from which to request tiles
	 * @param regions regions The list to which requests should be added, or null if a new list should be created
	 * @return The list of requests - identical to the one provided as an input parameter, unless this was null
	 */
	private static List<RegionRequest> getTilesToRequest(ImageServer<?> server, Shape clipShape, double downsampleFactor, int zPosition, int tPosition, List<RegionRequest> regions) {

		if (server == null)
			return List.of();

		var request = RegionRequest.createInstance(server.getPath(), downsampleFactor, AwtTools.getImageRegion(clipShape, zPosition, tPosition));

		if (regions == null)
			regions = new ArrayList<>();

		for (var tile : server.getTileRequestManager().getTileRequests(request))
			regions.add(tile.getRegionRequest());

		double x = request.getX() + request.getWidth() / 2.0;
		double y = request.getY() + request.getHeight() / 2.0;

		regions.sort(Comparator.comparingDouble(r -> distanceToCentroid(r, x, y)));

		return regions;
	}

	private static double distanceToCentroid(ImageRegion r, double x, double y) {
		double dx = x - (r.getMinX() + r.getWidth() / 2.0);
		double dy = y - (r.getMinY() + r.getHeight() / 2.0);
		return (dx * dx) + (dy * dy);
	}


}
