/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.images.stores;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.display.ImageDisplay;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;


/**
 * An ImageRegionStore suitable for either Swing or JavaFX applications.
 *
 * @author Pete Bankhead
 *
 */
public class DefaultImageRegionStore extends AbstractImageRegionStore<BufferedImage> {

	static final int DEFAULT_THUMBNAIL_WIDTH = 1000;

	static Logger logger = LoggerFactory.getLogger(DefaultImageRegionStore.class);

	protected DefaultImageRegionStore(int thumbnailWidth, long tileCacheSize) {
		super(new BufferedImageSizeEstimator(), thumbnailWidth, tileCacheSize);
	}

	protected DefaultImageRegionStore(long tileCacheSize) {
		this(DEFAULT_THUMBNAIL_WIDTH, tileCacheSize);
	}



	@Override
	@SuppressWarnings("unchecked")
	public BufferedImage getImage(final ImageServer<BufferedImage> server, final RegionRequest request, final long timeoutMillis, final boolean nullIfTimeout) {

//		RegionRequest.createInstance(server.getPath(), downsampleFactor, bounds.x, bounds.y, bounds.width, bounds.height, zPosition, tPosition)

		// If we don't have an RGB image, then we have to read directly
		if (!server.isRGB())
			return server.readBufferedImage(request);

		double downsampleFactor = request.getDownsample();
		int w = (int)(request.getWidth() / downsampleFactor);
		int h = (int)(request.getHeight() / downsampleFactor);
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); // TODO: DON'T ENFORCE THE TYPE!!!

//		// Improve the rendering quality for downsampled images...
//		Graphics2D g2d = img.createGraphics();
//		if (downsampleFactor > 1)
//			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		// DON'T improve the quality for downsampled images; this produces strange artefacts with texture computations (e.g. Haralick features),
		// as well as things like min/max/range
		Graphics2D g2d = img.createGraphics();
		if (downsampleFactor > 1)
			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);


		g2d.scale(1.0/downsampleFactor, 1.0/downsampleFactor);
		g2d.translate(-request.getX(), -request.getY());

		// Check to see if the thumbnail is all we need
		if (!isTiledImageServer(server)) {
			BufferedImage imgThumbnail = getThumbnail(server, request.getZ(), request.getT(), true);
			g2d.drawImage(imgThumbnail, 0, 0, null);
			g2d.dispose();
			return img;
		}


		// Loop through and create the image
		List<TileWorker<BufferedImage>> workers = new ArrayList<>();
		for (RegionRequest tileRequest : ImageRegionStoreHelpers.getTilesToRequest(server, request, null)) {

			Object result = requestImageTile(server, tileRequest, cache, true);

			// If we have an image, paint it & record coordinates
			if (result instanceof BufferedImage)
				g2d.drawImage((BufferedImage)result, tileRequest.getX(), tileRequest.getY(), tileRequest.getWidth(), tileRequest.getHeight(), null);
			else if (result instanceof TileWorker) {
				// If we've a tile worker, prepare for requesting its results soon...
				workers.add((TileWorker<BufferedImage>)result);
			}
//			else {
//				// If we have neither, something went wrong... fall back on just requesting the whole image
//				System.out.println(String.format("Requesting image tiles for %s... will request image from server instead", server.getServerPath()));
//				return server.readBufferedImage(bounds, downsampleFactor);
//			}
		}
		// Loop through any workers now, drawing their tiles too
		for (TileWorker<BufferedImage> worker : workers) {
			BufferedImage imgTile;
			try {
				if (timeoutMillis > 0)
					imgTile = worker.get(timeoutMillis, TimeUnit.MILLISECONDS);
				else
					imgTile = worker.get();//(10, TimeUnit.SECONDS); // TODO: Consider timeout?
				RegionRequest region = worker.getRequest();
				g2d.drawImage(imgTile, region.getX(), region.getY(), region.getWidth(), region.getHeight(), null);
			} catch (Exception e) {
//				logger.warn("{}", e);
				if (nullIfTimeout) {
					logger.trace("Timed out requesting tile for {}... returning null", server.getPath());
					return null;
				}
				logger.warn("Timed out requesting tile for {}... will request image from server instead", server.getPath());
				return server.readBufferedImage(worker.getRequest());
			}
		}
		return img;
	}



	/**
	 * Similar to paintRegion, but wait until all the tiles have arrived (or abort if it is taking too long)
	 *
	 * @param server
	 * @param g
	 * @param clipShapeVisible
	 * @param zPosition
	 * @param tPosition
	 * @param downsampleFactor
	 * @param observer
	 * @param imageDisplay
	 * @param timeoutMilliseconds Timeout after which a request is made from the PathImageServer directly, rather than waiting for tile requests.
	 */
	@SuppressWarnings("unchecked")
	public void paintRegionCompletely(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, ImageObserver observer, ImageDisplay imageDisplay, long timeoutMilliseconds) {

//		if (downsampleFactor > 1)
//			((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
//		else
//			((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);


		// Loop through and create the image
		List<TileWorker<BufferedImage>> workers = new ArrayList<>();
		BufferedImage imgTemp = null;

		for (RegionRequest request : ImageRegionStoreHelpers.getTilesToRequest(server, clipShapeVisible, downsampleFactor, zPosition, tPosition, null)) {

			Object result = requestImageTile(server, request, cache, true);

			// If we have an image, paint it & record coordinates
			if (result instanceof BufferedImage) {
				if (imageDisplay != null) {
					imgTemp = imageDisplay.applyTransforms((BufferedImage)result, imgTemp);
					g.drawImage(imgTemp, request.getX(), request.getY(), request.getWidth(), request.getHeight(), observer);
				} else
					g.drawImage((BufferedImage)result, request.getX(), request.getY(), request.getWidth(), request.getHeight(), observer);
			} else if (result instanceof TileWorker) {
				// If we've a tile worker, prepare for requesting its results soon...
//				System.out.println(((TileWorker)result).getRegion());
				workers.add((TileWorker<BufferedImage>)result);
			}
		}

		// Loop through any workers now, drawing their tiles too
		for (TileWorker<BufferedImage> worker : workers) {
			BufferedImage imgTile = null;
			try {
				imgTile = worker.get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
			} catch (CancellationException e) {
				logger.debug("Repaint skipped...");
//				e.printStackTrace();
				continue;
			} catch (InterruptedException e) {
				logger.warn("Tile request interrupted in 'paintRegionCompletely': {}", e.getLocalizedMessage());
				return;
			} catch (ExecutionException e) {
				logger.error("Execution exception in 'paintRegionCompletely'", e);
				return;
			} catch (TimeoutException e) {
				// If we timed out, try reading directly
				logger.warn("Timed out requesting region ({} ms)... {}", timeoutMilliseconds, worker.getRequest());
				RegionRequest request = worker.getRequest();
				if (server.isEmptyRegion(request))
					imgTile = null;
				else {
					if (worker.cancel(false)) {
						imgTile = server.readBufferedImage(request);
						cache.put(request, imgTile);
					} else
						try {
							imgTile = worker.get();
						} catch (InterruptedException e1) {
							logger.warn("Tile request interrupted; {}", e1.getLocalizedMessage());
						} catch (ExecutionException e1) {
							logger.warn("Execution exception during tile request: {}", e1.getLocalizedMessage());
						} catch (CancellationException e1) {
							logger.warn("Tile request cancelled: {}", e1.getLocalizedMessage());
						}
				}
			}
			if (imgTile == null)
				continue;
			RegionRequest request = worker.getRequest();
			if (imageDisplay != null) {
				imgTemp = imageDisplay.applyTransforms(imgTile, imgTemp);
				g.drawImage(imgTemp, request.getX(), request.getY(), request.getWidth(), request.getHeight(), observer);

			} else
				g.drawImage(imgTile, request.getX(), request.getY(), request.getWidth(), request.getHeight(), observer);
		}

	}



	public void paintRegion(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, BufferedImage imgThumbnail, ImageObserver observer, ImageDisplay imageDisplay) {
		registerRequest(null, server, clipShapeVisible, downsampleFactor, zPosition, tPosition);
		paintRegionInternal(server, g, clipShapeVisible, zPosition, tPosition, downsampleFactor, imgThumbnail, observer, imageDisplay);
	}


	private void paintRegionInternal(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, BufferedImage imgThumbnail, ImageObserver observer, ImageDisplay imageDisplay) {

//		// We don't need it... but try to request the thumbnail to keep it present in the cache, if it is there
//		cache.get(getThumbnailRequest(server, zPosition, tPosition));

		// Check if we have all the regions required for this request
		List<RegionRequest> requests = ImageRegionStoreHelpers.getTilesToRequest(server, clipShapeVisible, downsampleFactor, zPosition, tPosition, null);

//		System.out.println("Requesting tiles: " + requests.size());
//		System.out.println("Requesting tiles " + server.getServerPath() + ": " + requests.size());

		// If we should be painting recursively, ending up with the thumbnail, do so
		if (imgThumbnail != null) {
			Rectangle missingBounds = null;
			for (RegionRequest request : requests) {
				// Load the image
				BufferedImage img = getCachedTile(server, request);
				if (img == null && !cache.containsKey(request)) {
					if (missingBounds == null)
						missingBounds = AwtTools.getBounds(request);
					else
						missingBounds = missingBounds.union(AwtTools.getBounds(request));
				}
			}

			// If we are missing regions, try (recursively) to repaint at a lower resolution
			if (missingBounds != null) {
				double[] preferredDownsamples = server.getPreferredDownsamples();
				Arrays.sort(preferredDownsamples);
				double nextDownsample = -1; // -1 is the flag that indicates that we don't have a lower resolution to go to
				for (double d : preferredDownsamples) {
					if (d > Math.max(downsampleFactor, 1)) {
						nextDownsample = d;
						break;
					}
				}
				// Get the next downsample level if we can
				if (nextDownsample > 0)
//					paintRegion(server, g, clipShapeVisible, zPosition, tPosition, nextDownsample, imgThumbnail, observer, imageDisplay);
					paintRegionInternal(server, g, missingBounds, zPosition, tPosition, nextDownsample, imgThumbnail, observer, imageDisplay);
				else if (imgThumbnail != null) {
					// The best we can do is paint the thumbnail
					if (imageDisplay != null) {
						BufferedImage imgTemp = imageDisplay.applyTransforms(imgThumbnail, null);
						imgThumbnail = imgTemp;
					}
					g.drawImage(imgThumbnail, 0, 0, server.getWidth(), server.getHeight(), observer);
				}
			}
		}

		// If we're compositing channels, it's worthwhile to cache RGB tiles for so long as the ImageDisplay remains constant
		boolean useDisplayCache = imageDisplay != null && !server.isRGB() && server.nChannels() > 1;
		long displayTimestamp = imageDisplay == null ? 0L : imageDisplay.getLastChangeTimestamp();
		String displayCachePath = useDisplayCache ? server.getPath() + ":" + imageDisplay.toJSON(false) + ":" + displayTimestamp : null;

		// Loop through and draw whatever tiles we've got
		BufferedImage imgTemp = null;
		for (RegionRequest request : requests) {
			// Load the image
			BufferedImage img = getCachedRegion(server, request);

			// If there is no image tile, try to get a lower-resolution version to draw - 
			// this can actually paint over previously-available regions, but they will be repainted again when this region's request comes through
			if (img == null)
				continue;

			// If we have an image, paint it & record coordinates
			// Apply any required color transformations
			if (imageDisplay != null) {
				// We can abort now - we know the display has changed, additional painting is futile...
				if (displayTimestamp != imageDisplay.getLastChangeTimestamp())
					return;
				if (useDisplayCache) {
					// Apply transforms, creating & caching new temp images
					RegionRequest requestCache = RegionRequest.createInstance(displayCachePath, request.getDownsample(), request);
					imgTemp = cache.get(requestCache);
					if (imgTemp == null) {
						imgTemp = imageDisplay.applyTransforms(img, null);
						// Store this if we know we've still got the same display settings
						// This avoids making the cache inconsistent
						if (displayTimestamp == imageDisplay.getLastChangeTimestamp())
							cache.put(requestCache, imgTemp);
						else
							return;
					}
				} else {
					// Apply transforms, trying to reuse temp image
					if (imgTemp != null && (imgTemp.getWidth() != img.getWidth() || imgTemp.getHeight() != img.getHeight()))
						imgTemp = null;
					imgTemp = imageDisplay.applyTransforms(img, imgTemp);
				}
				img = imgTemp;
			}
			g.drawImage(img, request.getX(), request.getY(), request.getWidth(), request.getHeight(), observer);
		}

	}


	@Override
	public void close() {
		super.close();
	}


}