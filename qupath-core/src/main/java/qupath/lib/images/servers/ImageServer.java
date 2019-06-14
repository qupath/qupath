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

package qupath.lib.images.servers;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import qupath.lib.regions.RegionRequest;

/**
 * 
 * Generic ImageServer, able to return pixels and metadata.
 * <p>
 * The idea behind making this generic is that so that it can be used on various platforms and with different UIs, e.g. Swing, JavaFX.
 * For Swing/AWT, the expected generic parameter is BufferedImage.
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
	 * Get the URI for this ImageServer, or null if no URI is associated with the server.
	 * 
	 * @return
	 */
	public URI getURI();
	
	/**
	 * Get a short name for the server, derived from {@code getPath()}.
	 * @return
	 */
	public String getShortServerName();

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
	 * Get the downsample factor supported by the server that is the best match for the requested downsample.
	 * <p>
	 * Generally, this will be &lt;= the requested downsample (but it may be slightly more if the error introduced
	 * would be very small, i.e. if 4 is requested and 4.0001 is available, 4.0001 would be returned).
	 * 
	 * @param requestedDownsample
	 * @return
	 */
	public double getPreferredDownsampleFactor(double requestedDownsample);
	
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
	 * TRUE if the image has 8-bit red, green &amp; blue channels (and nothing else), false otherwise.
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
	 * Spacing between slices of a z-stack, or Double.NaN if this is unknown of the image is not a z-stack.
	 * @return
	 */
	public double getZSpacingMicrons();

	/**
	 * The pixel width of the full-resolution image in microns, or Double.NaN if this is unknown.
	 * @return
	 */
	public double getPixelWidthMicrons();

	/**
	 * The pixel height of the full-resolution image in microns, or Double.NaN if this is unknown.
	 * @return
	 */
	public double getPixelHeightMicrons();
	
	/**
	 * The mean of the pixel width &amp; height, if available; for square pixels this is the same as either width * height
	 * @return
	 */
	public double getAveragedPixelSizeMicrons();

	/**
	 * True if the pixel size is known, False otherwise.
	 * @return
	 */
	public boolean hasPixelSizeMicrons();
	
	/**
	 * Get the output type, used to interpret what channels mean.
	 * 
	 * @return
	 */
	public default ImageServerMetadata.ChannelType getOutputType() {
		return ImageServerMetadata.ChannelType.DEFAULT;
	}

	/**
	 * Get a cached tile, or null if the tile has not been cached.
	 * <p>
	 * This is useful whenever it is important to return quickly rather than wait for a tile to be fetched or generated.
	 * 
	 * @param tile
	 * @return the tile if it has been cached, or null if no cached tile is available for the request.
	 */
	public T getCachedTile(TileRequest tile);
	
	/**
	 * Read a buffered image for a specified RegionRequest, cropping and downsampling as required.
	 * <p>
	 * No specific checking is guaranteed
	 * to ensure that the request is valid, e.g. if it extends beyond the image boundary then it is likely (but not certain) that
	 * the returned image will be cropped accordingly - but some implementations may contain empty padding instead.  Therefore
	 * it is up to the caller to ensure that the requests are within range.
	 * 
	 * @param request
	 * @return
	 */
	public T readBufferedImage(RegionRequest request) throws IOException;
        
	
	/**
	 * A string describing the type of server, for example the name of the library used (Openslide, Bioformats...)
	 */
	public String getServerType();
	
	/**
	 * Get a list of images (or subimages) accessible by this server.
	 * This is for occasions in which the same file contains multiple images, each of which could be accessed via an ImageServer.
	 * <p>
	 * In the event that the server only has a single image, an empty list is returned.
	 * If the server has multiple images, a list of image names is returned - with the default image occurring first.
	 * 
	 * @return
	 * 
	 * @see #openSubImage(String)
	 * @see #getAssociatedImage
	 */
	public List<String> getSubImageList();
	
	/**
	 * Open a sub-image as a new ImageServer.
	 * 
	 * @return
	 * 
	 * @see #getSubImageList
	 */
	public ImageServer<T> openSubImage(String imageName) throws IOException;
	
//	/**
//	 * Create a (child) image server to be used to access an image contained within the images that this server supports.
//	 * Not all image servers (or image files) contain multiple images.
//	 * If this is used, getImageList().contains(imageName) must evaluate to true - if it does not, then this
//	 * method will throw an IllegalArgumentException.
//	 * 
//	 * @param imageName
//	 * @return
//	 */
//	@Deprecated
////	public ImageServer getSubImageServer(String imageName);
	
	
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
	 * Get the name of the image supplied by this server.
	 * <p>
	 * If the server only has one image, then it will be the same as getShortServerName().
	 * However if the server contains multiple images, this will identify the image whose
	 * metadata &amp; pixels are provided by the server.
	 * 
	 * @return
	 */
	public String getDisplayedImageName();
	
	
//	/**
//	 * Returns an absolute URI representing the server (image) data, or null if the server does not receive its data from a stored file (e.g. it is computed dynamically).
//	 * @return
//	 */
//	public URI getURI();
	
	
	/**
	 * Test whether a region is empty, i.e. it contains nothing to be painted (e.g. the server paints objects
	 * but there are no objects present in the region) and readBufferedImage(RegionRequest region) would return null.
	 * <p>
	 * This makes it possible to avoid a (potentially more expensive) request to {@link #readBufferedImage(RegionRequest)},
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
	 * The bit-depth of the image.
	 * <p>
	 * For an RGB image, this is considered to be 8, i.e. color channels are considered separately.
	 * 
	 * @return
	 */
	public int getBitsPerPixel();
	
	
	/**
	 * Request information for one channel.
	 * 
	 * @param channel
	 * @return
	 * 
	 */
	public ImageChannel getChannel(int channel);
	
	
	/**
	 * Get a list providing the name and default color for each image channel.
	 * @return
	 */
	public List<ImageChannel> getChannels();
	
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
	 */
	public T getDefaultThumbnail(int z, int t) throws IOException;
	
	
	/**
	 * Get {@link TileRequest} objects for <i>all</i> tiles that this server supports.
	 * 
	 * @return
	 */
	public Collection<TileRequest> getAllTileRequests();
	
	/**
	 * Get the {@link TileRequest} containing a specified pixel, or null if no such request exists.
	 * 
	 * @param level
	 * @param x
	 * @param y
	 * @param z
	 * @param t
	 * @return
	 */
	public TileRequest getTile(int level, int x, int y, int z, int t);
	
	/**
	 * Get a collection of {@link TileRequest} objects necessary to fulfil a specific {@link RegionRequest}.
	 * 
	 * @param request
	 * @return
	 */
	public Collection<TileRequest> getTiles(final RegionRequest request);
	
	/**
	 * Get the class of the image representation returned by this ImageServer.
	 * @return
	 */
	public Class<T> getImageClass();
	
}
