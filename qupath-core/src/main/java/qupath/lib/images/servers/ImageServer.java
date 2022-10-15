/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;

/**
 * 
 * Generic ImageServer, able to return pixels and metadata.
 * <p>
 * The idea behind making this generic was that it could be used on various platforms and with different UIs, e.g. Swing, JavaFX, 
 * or with different kinds of image reader.
 * In practice, the generic parameter is almost always {@link java.awt.image.BufferedImage}.
 * <p>
 * <b>Important!</b> The {@link #readBufferedImage(RegionRequest)} method was replaced by {@link #readRegion(RegionRequest)} in 
 * v0.4.0. Implementing classes need only to worry about overriding {@link #readRegion(RegionRequest)} - and legacy implementations 
 * should be updated to override the new method rather than the old one.
 * Default implementations of both methods exist to try to ease the transition, but this may change in the future and 
 * {@link #readBufferedImage(RegionRequest)} is likely to be removed.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface ImageServer<T> extends AutoCloseable {
	
	/**
	 * Get a String path that can uniquely identify this image.
	 * <p>
	 * For most standard images, this should be a String representation of an absolute URI. 
	 * If multiple images are stored within the same file, then this information should be encoded in the URI.
	 * <p>
	 * For images that are generated some other way (e.g. created dynamically) the path may not lend itself to 
	 * a URI representation, but must still be unique so that it can be used for caching tiles.
	 * 
	 * @return
	 */
	public String getPath();
	
	/**
	 * Get the URIs for images required for this server. 
	 * In the simplest case, this is a singleton list returning a URI representing a local 
	 * file. However, some ImageServers may not have an associated URI at all, whereas others 
	 * may depend upon multiple URIs (e.g. if concatenating images).
	 * <p>
	 * Note: A URI alone may not be sufficient to recreate even a simple ImageServer; see {@link #getBuilder()}.
	 * @return
	 */
	public Collection<URI> getURIs();
	
	/**
	 * Get an array of downsample factors supported by the server
	 * @return
	 */
	public double[] getPreferredDownsamples();
	
	/**
	 * Number of resolutions for the image.
	 * <p>
	 * This is equivalent to {@code getPreferredDownsamples().length}.
	 * 
	 * @return
	 */
	public int nResolutions();
	
	/**
	 * Get the downsample factor for a specified resolution level, where level 0 is the full resolution image 
	 * and nResolutions() - 1 is the lowest resolution available.
	 * 
	 * @param level Resolution level, should be 0 &lt;= level &lt; nResolutions().
	 * @return
	 */
	public double getDownsampleForResolution(int level);
	
	/**
	 * Width of the full-resolution image in pixels.
	 * @return
	 */
	public int getWidth();

	/**
	 * Height of the full-resolution image in pixels.
	 * @return
	 */
	public int getHeight();

	/**
	 * Number of channels (3 for RGB).
	 * @return
	 */
	public int nChannels();
	
	/**
	 * True if the image has 8-bit red, green &amp; blue channels (and nothing else), false otherwise.
	 * @return
	 */
	public boolean isRGB();
	
	/**
	 * Number of slices in a z-stack.
	 * @return
	 */
	public int nZSlices();
	
	/**
	 * Number of time points in a time series.
	 * @return
	 */
	public int nTimepoints();
	
	/**
	 * Get the PixelCalibration object from the current metadata.
	 * @return
	 */
	public default PixelCalibration getPixelCalibration() {
		return getMetadata().getPixelCalibration();
	}

	/**
	 * Get a cached tile, or null if the tile has not been cached.
	 * <p>
	 * This is useful whenever it is important to return quickly rather than wait for a tile to be fetched or generated.
	 * <p>
	 * <b>Warning!</b> This method should be used very cautiously, as it is permitted to return the tile stored internally 
	 * in the cache for performance. This <b>must not be modified</b> by any code requesting the tile.
	 * 
	 * @param tile
	 * @return the tile if it has been cached, or null if no cached tile is available for the request.
	 * @implSpec because this method exists for performance, the tile may be returned directly without defensive copying.
	 *           If the tile is mutable, consumers must be extremely careful to avoid modifying the tile.
	 */
	public T getCachedTile(TileRequest tile);
	
	/**
	 * Legacy method to request pixels for a {@link RegionRequest}, now replaced by {@link #readRegion(RegionRequest)}.
	 * This method is deprecated; if the generic parameter {@code <T>} represented 
	 * anything other than a {@link java.awt.image.BufferedImage} then its name became misleading.
	 * 
	 * @param request the region for which pixels are requested
	 * @return pixels for the region being requested
	 * @throws IOException 
	 * @deprecated since v0.4.0. Implementations of {@link ImageServer} should override {@link #readRegion(RegionRequest)} instead.
	 *             This method will be removed in a future release; it exists now only to maintain compatibility with QuPath 
	 *             extensions that have not yet been updated to use the newer method.
	 *             
	 * @implNote The default implementation simply calls {@link #readRegion(RegionRequest)}
	 */
	@Deprecated
	public default T readBufferedImage(RegionRequest request) throws IOException {
		// Log if this is the first time
		ImageServers.logFirstDeprecatedReadCall(getClass());
		return readRegion(request);		
	}
 
	/**
	 * Read a 2D(+C) image region for a specified {@link RegionRequest}.
	 * Coordinates and bounding box dimensions from the request are in pixel units, 
	 * at the full image resolution (i.e. when downsample = 1).
	 * <p>
	 * All channels are always returned.
	 * <p>
	 * No specific checking is guaranteed to ensure that the request is valid, e.g. if it extends beyond the image
	 * boundary then it is likely (but not certain) that the returned image will be cropped accordingly - 
	 * but some implementations may contain empty padding instead.
	 * Therefore it is up to the caller to ensure that the requests are within range.
	 * <p>
	 * However, it is expected that any returnable region will be at least 1x1 pixel in size, even if via high downsampling 
	 * one might otherwise expect a 0x0 image. This is consistent with the idea of pixels representing point samples 
	 * rather than little squares.
	 * <p>
	 * Note: One should avoid returning null, as this cannot be stored as a value in some map implementations 
	 * that may be used for caching.
	 * 
	 * @param request the region for which pixels are requested
	 * @return pixels for the region being requested
	 * @throws IOException
	 * @see #readRegion(double, int, int, int, int, int, int)
	 * @see #readRegion(double, int, int, int, int)
	 * @see #readBufferedImage(RegionRequest)
	 * @since v0.4.0
	 * 
	 * @implNote Implementations of {@link ImageServer} must override this method! The default implementation 
	 *           exists to search for the (deprecated) {@link #readBufferedImage(RegionRequest)} method, 
	 *           so as to maintain compatibility with older {@link ImageServer} implementations - and throws 
	 *           an {@link UnsupportedOperationException} if this is not found.
	 */
	public default T readRegion(RegionRequest request) throws IOException {
		try {
			// Try to find if this is a legacy subclass that implemented readBufferedImage(RegionRequest) only
			// If so, call that method and log a warning if it's the first time
			var m = getClass().getMethod("readBufferedImage", RegionRequest.class);
			if (m != null && !Objects.equals(ImageServer.class, m.getDeclaringClass())) {
				ImageServers.logFirstDeprecatedReadCall(getClass());
				return (T)m.invoke(this, request);
			}
		} catch (Exception e) {
			LoggerFactory.getLogger(getClass()).debug(e.getLocalizedMessage(), e);
		}
		throw new UnsupportedOperationException("readRegion is not implemented!");
	}

	/**
	 * Read a 2D(+C) image region for a specified z-plane and timepoint.
	 * Coordinates and bounding box dimensions are in pixel units, at the full image resolution 
	 * (i.e. when downsample = 1).
	 * <p>
	 * All channels are always returned.
	 * 
	 * @param downsample downsample factor for the region
	 * @param x x coordinate of the top left of the region bounding box
	 * @param y y coordinate of the top left of the region bounding box
	 * @param width bounding box width
	 * @param height bounding box height
	 * @param z index for the z-position
	 * @param t index for the timepoint
	 * @return pixels for the region being requested
	 * @throws IOException
	 * @see #readRegion(RegionRequest)
	 * @since v0.4.0
	 */
	public default T readRegion(double downsample, int x, int y, int width, int height, int z, int t) throws IOException {
		return readRegion(RegionRequest.createInstance(getPath(), downsample, x, y, width, height, z, t));
	}

	/**
	 * Read a 2D(+C) image region from the default {@link ImagePlane} (i.e. z and t are 0).
	 * Coordinates and bounding box dimensions are in pixel units, at the full image resolution 
	 * (i.e. when downsample = 1).
	 * <p>
	 * All channels are always returned.
	 * 
	 * @param downsample downsample factor for the region
	 * @param x x coordinate of the top left of the region bounding box
	 * @param y y coordinate of the top left of the region bounding box
	 * @param width bounding box width
	 * @param height bounding box height
	 * @return pixels for the region being requested
	 * @throws IOException
	 * @see #readRegion(RegionRequest)
	 * @see #readRegion(double, int, int, int, int, int, int)
	 * @since v0.4.0
	 */
	public default T readRegion(double downsample, int x, int y, int width, int height) throws IOException {
		return readRegion(downsample, x, y, width, height, 0, 0);
	}

	
	/**
	 * A string describing the type of server, for example the name of the library used (Openslide, Bioformats...)
	 * @return 
	 */
	public String getServerType();
	
	/**
	 * Get a list of 'associated images', e.g. thumbnails or slide overview images.
	 * <p>
	 * Each associated image is simply a T that does not warrant (or require) a full ImageServer, and most likely would never be analyzed.
	 * 
	 * @see #getAssociatedImage(String)
	 * 
	 * @return
	 */
	public List<String> getAssociatedImageList();
	
	/**
	 * Get the image for a given associated image name.
	 * 
	 * @see #getAssociatedImageList()
	 * 
	 * @param name
	 * @return
	 */
	public T getAssociatedImage(String name);
	
	
	/**
	 * Test whether a region is empty, i.e. it contains nothing to be painted (e.g. the server paints objects
	 * but there are no objects present in the region) and readBufferedImage(RegionRequest region) would return null.
	 * <p>
	 * This makes it possible to avoid a (potentially more expensive) request to {@link #readRegion(RegionRequest)},
	 * or to add it to a request queue, if we know there will be nothing to show for it.
	 * <p>
	 * Note: if this method returns true, it is safe to assume readBufferedImage would return null.
	 * However, even if the method returns false it is possible that the region is still empty -
	 * the purpose of this method is to assist performance, and it should return quickly.
	 * Therefore if the calculations needed to identify if the region is empty are too onerous, it may conservatively return false.
	 * 
	 * @param request
	 * @return
	 */
	public boolean isEmptyRegion(RegionRequest request);
	
	/**
	 * The bit-depth and type of the image. This refers to a single channel, e.g. an 
	 * 8-bit RGB image will have a type of {@link PixelType#UINT8}.
	 * 
	 * @return
	 */
	public PixelType getPixelType();
	
	/**
	 * Request information for one channel (0-based index).
	 * 
	 * @param channel
	 * @return
	 * 
	 * @see ImageServerMetadata#getChannels()
	 */
	public ImageChannel getChannel(int channel);
	
	/**
	 * Get essential metadata associated with the ImageServer as a distinct object.  This may be edited by the user.
	 * @return
	 * @see #getOriginalMetadata()
	 */
	public ImageServerMetadata getMetadata();
	
	/**
	 * Set the metadata to use, e.g. to change the pixel size in microns.
	 * 
	 * @param metadata
	 * @throws IllegalArgumentException if the metadata is incompatible (e.g. different image path, different bit-depth).
	 */
	public void setMetadata(ImageServerMetadata metadata) throws IllegalArgumentException;
	
	/**
	 * Get the original metadata read during creation of the server.  This may or may not be correct.
	 * @return
	 * @see #getMetadata()
	 */
	public ImageServerMetadata getOriginalMetadata();
	
	/**
	 * Get the default thumbnail for a specified z-slice and timepoint.
	 * <p>
	 * This should be the lowest resolution image that is available in the case of the multiresolution 
	 * image, or else the full image.  For large datasets, it may be used to determine basic statistics or 
	 * histograms without requiring every pixel to be visited in the full resolution image.
	 * 
	 * @param z
	 * @param t
	 * @return
	 * @throws IOException 
	 */
	public T getDefaultThumbnail(int z, int t) throws IOException;
	
		
	/**
	 * Get a TileRequestManager that can be used to identify image tiles that may be efficiently requested
	 * from this ImageServer.
	 * <p>
	 * This is useful because managing arbitrary RegionRequests can result in inefficiencies if a request 
	 * straddles multiple tiles unnecessarily. Also, it can be used to help ensure consistency whenever 
	 * requesting regions at different resolutions, where rounding errors might otherwise occur.
	 * <p>
	 * Note that the TileRequestManager is not guaranteed to remain the same for the lifecycle of the server. 
	 * For example, if the image metadata is changed then a new manager may be constructed.
	 * @return
	 */
	public TileRequestManager getTileRequestManager();
	

	/**
	 * Get the class of the image representation returned by this ImageServer.
	 * @return
	 */
	public Class<T> getImageClass();
	
	/**
	 * Get a ServerBuilder capable of building a server the same as this one.
	 * <p>
	 * The purpose of this is to aid serialization of servers by switching to a simpler representation.
	 * <p>
	 * The default implementation returns null, indicating that rebuilding the server is not supported.
	 * @return
	 */
	public default ServerBuilder<T> getBuilder() {
		return null;
	}
	
}