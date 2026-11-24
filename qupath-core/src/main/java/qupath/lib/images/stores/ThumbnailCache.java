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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.cache.GenericImageCache;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * A specialized cache that can be used to access 'thumbnail' images.
 * These are images that represent a single plane of the image, with a standardized size.
 * <p>
 * For some 'small', non-pyramidal image, the thumbnail may be equivalent to the full image.
 * However, for large and pyramidal images, the thumbnail is usually a low-resolution version of the full
 * field of view.
 * @param <T> generic image parameter
 * @since v0.8.0
 */
public class ThumbnailCache<T> {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailCache.class);

    private final GenericImageCache<T> cache;

    /**
     * Maximum size of thumbnail, in any dimension.
     */
    private final int maxThumbnailSize;

    /**
     * Minimum size of thumbnail, in any dimension.
     */
    private final int minThumbnailSize = 16;

    ThumbnailCache(GenericImageCache<T> cache, int maxThumbnailSize) {
        this.cache = cache;
        this.maxThumbnailSize = maxThumbnailSize;
    }

	/**
	 * Calculate the downsample value to use when generating a thumbnail image.
	 * @return
	 */
	private double calculateThumbnailDownsample(ImageServer<?> server) {
		// We'll have trouble if we try to downsample until we have very few pixels in any dimension
		double fullResolutionDownsample = server.getDownsampleForResolution(0);
		double targetDownsample = fullResolutionDownsample;
		if (server.nResolutions() == 1)
			return targetDownsample;
		int width = server.getWidth();
		int height = server.getHeight();
		double maxDim = Math.max(width, height);
		double minDim = Math.min(width, height);
		if (minDim > minThumbnailSize) {
			// Try to use a downsample that corresponds to an actual pyramid level, to avoid unnecessary
			// requests and caching
			double maxDownsample = minDim / minThumbnailSize;
			double target = Math.min(maxDim / maxThumbnailSize, maxDownsample);
			targetDownsample = Arrays.stream(server.getPreferredDownsamples())
					.filter(d -> d <= maxDownsample && GeneralTools.almostTheSame(target, d, 0.25))
					.sorted()
					.findFirst()
					.orElse(target);
		}
		targetDownsample = Math.max(targetDownsample, fullResolutionDownsample);
		logger.trace("Thumbnail downsample: {}, ({} x {})",
				targetDownsample, Math.round(width/targetDownsample), Math.round(height/targetDownsample));
		return targetDownsample;
	}

	private static ImagePlane getDefaultThumbnailPlane(final ImageServer<?> server) {
		return ImagePlane.getPlane(server.nZSlices()/2, server.nTimepoints()/2);
	}

	/**
	 * Get the region request that would be used to get the default thumbnail.
	 * For a z-stack and time series, this is taken from the center of the stack, under the assumption it is likely 
	 * to be more representative than the first slice.
	 * @param server the image server
	 * @return a region request for (possibly downsampled) version of the selected slice
	 */
	public RegionRequest getThumbnailRequest(final ImageServer<T> server) {
		return getThumbnailRequest(server, getDefaultThumbnailPlane(server));
	}

	/**
	 * Get the region request that would be used to get a thumbnail for a specified plane (z-slice, time point).
	 * @param server the image server
	 * @return a region request for (possibly downsampled) version of the selected slice
	 */
	public RegionRequest getThumbnailRequest(final ImageServer<T> server, final ImagePlane plane) {
		return RegionRequest.createInstance(
				server.getPath(),
				calculateThumbnailDownsample(server),
				0, 0, server.getWidth(), server.getHeight(), plane);
	}

	private T getIfPresent(final RegionRequest request) {
		return cache.getIfPresent(request);
	}

	/**
	 * Get a thumbnail for a specified plane, only if it is cached.
	 * @param server the image server
	 * @return a thumbnail image, or null if the thumbnail is not cached
	 */
	public T getCachedThumbnail(final ImageServer<T> server, final ImagePlane plane) {
		RegionRequest request = getThumbnailRequest(server, plane);
		return getIfPresent(request);
	}

	/**
	 * Get a cached thumbnail that is 'closest' to a specified plane.
	 * This is useful when a thumbnail is not available for the desired plane, but a placeholder is needed.
	 * @param server the image server
	 * @return a thumbnail image, or null if no thumbnail is cached for this image
	 * @implNote Currently this uses the absolute displacement in terms of z-slice and time point indices.
	 *           The identified thumbnail is therefore not necessarily unique and no guarantees are made regarding 
	 *           which will be returned. In this future, this behavior may change (probably to be more predictable).
	 */
	public T getClosestCachedThumbnail(final ImageServer<T> server, final ImagePlane plane) {
		RegionRequest request = getThumbnailRequest(server, plane);
		var dist = new PlaneDistance(request.getZ(), request.getT());
		return cache.asMap().keySet().stream().filter(r -> sameRegionIgnoringPlane(request, r))
				.sorted(Comparator.comparingDouble(dist::distance))
				.map(this::getIfPresent)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	private record PlaneDistance(int z, int t) {

		public int distance(RegionRequest region) {
			int dz = Math.abs(z - region.getZ());
			int dt = Math.abs(t - region.getT());
			return dz + dt;
		}

	}

	private static boolean sameRegionIgnoringPlane(RegionRequest r1, RegionRequest r2) {
		return Objects.equals(r1.getPath(), r2.getPath()) &&
				r1.getX() == r2.getX() && r1.getY() == r2.getY() &&
				r1.getWidth() == r2.getWidth() && r1.getHeight() == r2.getHeight();
	}


	/**
	 * Get a thumbnail image if it is cached, otherwise request it asynchronously and return null.
	 * @param server the image server
	 * @param plane the plane for the thumbnail
	 * @return the thumbnail if it is cached, or null if it is not cached
	 */
	public T getOrRequestThumbnail(final ImageServer<T> server, final ImagePlane plane) {
		RegionRequest request = getThumbnailRequest(server, plane);
		var future = cache.requestImageTile(request, server);
		return future.isDone() ? future.resultNow() : null; // TODO: Consider possible failures
	}

	/**
	 * Get a thumbnail image for a specified plane, waiting for it if it is not already available.
	 * @param server the image server
	 * @param plane the plane for the thumbnail
	 * @return a thumbnail, or null if the request failed (e.g., due to an exception)
	 */
	public T getThumbnail(final ImageServer<T> server, final ImagePlane plane) {
		RegionRequest request = getThumbnailRequest(server, plane);
		var future = cache.requestImageTile(request, server);
		try {
			return future.get();
		} catch (Exception e) {
			logger.error("Error reading image region: {} ({})", e.getMessage(), request);
			return null;
		}
	}

	/**
	 * Get a default thumbnail image, waiting for it if it is not already available.
	 * @param server the image server
	 * @return a thumbnail, or null if the request failed (e.g., due to an exception)
	 * @see #getThumbnailRequest(ImageServer) 
	 */
	public T getThumbnail(final ImageServer<T> server) {
		return getThumbnail(server, getDefaultThumbnailPlane(server));
	}

}
