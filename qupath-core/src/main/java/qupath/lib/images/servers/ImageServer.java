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

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import qupath.lib.images.PathImage;
import qupath.lib.regions.RegionRequest;

/**
 * 
 * Generic ImageServer, able to return image tiles.
 * <p>
 * The idea behind making this generic is that so that it can be used on various platforms and with different UIs, e.g. Swing, JavaFX.
 * For Swing/AWT, the expected generic parameter is BufferedImage.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface ImageServer<T> {
	
	/**
	 * Get either the URL for this image, or the path to a file.
	 * <p>
	 * This should uniquely identify the image; if multiple images are stored within the same file, then this information should be encoded (somehow) in the path.
	 * @return
	 * 
	 * @see #getFile
	 */
	public String getPath();

	/**
	 * Get a short name for the server, derived from getServerPath().
	 * @return
	 */
	public String getShortServerName();

	/**
	 * Get an array of downsample factors supported by the server
	 * @return
	 */
	public double[] getPreferredDownsamples();
	
	/**
	 * Get the downsample factor supported by the server that is the best match for the requested downsample.
	 * Generally, this will be &lt;= the requested downsample (but it may be slightly more if the error introduced
	 * would be very small, i.e. if 4 is requested and 4.0001 is available, 4.0001 would be returned).
	 * 
	 * @param requestedDownsample
	 * @return
	 */
	public double getPreferredDownsampleFactor(double requestedDownsample);
	
	/**
	 * A suggested tile width (in pixels), derived from the full-resolution image.
	 * If no tile size suggestion can be provided from the file, this returns -1.
	 * @return
	 */
	public int getPreferredTileWidth();

	/**
	 * A suggested tile height (in pixels), derived from the full-resolution image.
	 * If no tile size suggestion can be provided from the file, this returns -1.
	 * @return
	 */
	public int getPreferredTileHeight();

	/**
	 * The magnification at which the full-resolution image was acquired, or Double.NaN if this is unknown.
	 * @return
	 */
	public double getMagnification();
	
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
	 * Time point (in getTimeUnits() for a time series; returns 0 if the image is not a time series
	 * @return
	 */
	public double getTimePoint(int ind);
	
	/**
	 * Time interval units for a time series
	 * @return
	 */
	public TimeUnit getTimeUnit();
	
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
	 * TRUE if the pixel size is known, FALSE otherwise.
	 * @return
	 */
	public boolean hasPixelSizeMicrons();
	
	/**
	 * Obtain a T thumbnail, no larger than the maxWidth &amp; maxHeigth specified.
	 * Aspect ratio will be maintained, so only one dimension needs to be specified - the other can be -1.
	 * <p>
	 * Note: The aim of this method is to supply a T that would look sensible when drawn,
	 *       *not* one that preserves (resampled) pixel values.  Therefore some brightness/contrast scaling
	 *       may have been applied, particularly for non-8-bit images.
	 * 
	 * @param maxWidth
	 * @param maxHeight
	 * @param zPosition
	 * @return
	 */
	public T getBufferedThumbnail(int maxWidth, int maxHeight, int zPosition);

	/**
	 * Read a requested region, returning PathImage containing additional metadata.
	 * <p>
	 * 'region' must contain integer pixel coordinates from the full-resolution image, while downsampleFactor can be any double 
	 * (generally &gt;= 1; 'upsampling' may not be supported, depending on the concrete implementations).
	 * <p>
	 * For pyramid images, no guarantee is provided as to which level will actually be used, but it is most likely
	 * to be the level closest to - but not lower-resolution than - the requested downsampleFactor.
	 * <p>
	 * While the downsampleFactor can be a double, it should be kept in mind that this can lead to rounding issues.
	 * Therefore if it is essential that extracted regions will later need to be related back to the full-resolution data
	 * (e.g. after segmenting objects at a lower magnification), then the region and downsampleFactor should be chosen cautiously.
	 * <p>
	 * In general, in such cases it is a good idea to make downsampleFactor a power of 2, and ensure at least that the requested
	 * width and height are multiples of the downsampleFactor.  This can be ensured by first refining the region with
	 * 		WholeSlideImageHelper.refineRegion(Rectangle region, double downsampleFactor)
	 * 
	 * 
	 * @param request - the image region being requested, including the downsample factor
	 * @return
	 */
	public PathImage<T> readRegion(RegionRequest request);
	
	/**
	 * Read a buffered image for a specified RegionRequest, cropping and downsampling as required.  No specific checking is guaranteed
	 * to ensure that the request is valid, e.g. if it extends beyond the image boundary then it is likely (but not certain) that
	 * the returned image will be cropped accordingly - but some implementations may contain empty padding instead.  Therefore
	 * it is up to the caller to ensure that the requests are within range.
	 * 
	 * @param request
	 * @return
	 */
	public T readBufferedImage(RegionRequest request);

	/**
	 * Method that may be required by some servers.
	 */
	public void close();
        
	
	/**
	 * A string describing the type of server, e.g. the name of the library used (Openslide, Bioformats...)
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
	 * @see #getSubImagePath
	 * @see #getAssociatedImage
	 */
	public List<String> getSubImageList();
	
	/**
	 * Get a full path for a sub-image of this server.
	 * 
	 * @return
	 * 
	 * @see #getSubImageList
	 */
	public String getSubImagePath(String imageName);
	
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
	 * @see #getAssociatedImage
	 * 
	 * @return
	 */
	public List<String> getAssociatedImageList();
	
	/**
	 * Get the T for a given AssociatedImage name.
	 * 
	 * @see #getAssociatedImageList
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
	
	/**
	 * Returns true if this is a sub-image stored within the same file as other images.
	 * 
	 * @return
	 */
	public boolean containsSubImages();
	
	
	/**
	 * Returns true either if this server *is* the specified PathImageServer, or if it is a wrapper for it.
	 * @param server
	 * @return
	 */
	public boolean usesBaseServer(ImageServer<?> server);
	
	
	/**
	 * Returns file containing the server (image) data, or null if the server does not receive its data from a stored file (e.g. it is computed dynamically or read from a URL).
	 * <p>
	 * Note that this is not necessarily the same as <code>new File(server.getPath());</code> but some implementations may encode additional information (e.g. regarding sub-images) in the server path.
	 * @return
	 */
	public File getFile();
	
	
	/**
	 * Test whether a region is empty, i.e. it contains nothing to be painted (e.g. the server paints objects
	 * but there are no objects present in the region) and readBufferedImage(RegionRequest region) would return null.
	 * <p>
	 * This makes it possible to avoid a (potentially more expensive) request to readBufferedImage,
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
	 * For an RGB image, this is considered to be 8, i.e. color channels are considered separately.
	 * 
	 * @return
	 */
	public int getBitsPerPixel();
	
	
	/**
	 * In cases where multiple channels are available, return the default color for the specified channel (first channel is 0).
	 * @param channel
	 * @return
	 */
	public Integer getDefaultChannelColor(int channel);
	
	
	/**
	 * Get essential metadata associated with the ImageServer as a distinct object.  This may be edited by the user.
	 * @return
	 * @see #getOriginalMetadata
	 */
	public ImageServerMetadata getMetadata();
	
	/**
	 * Set the metadata to use, e.g. to change the pixel size in microns.
	 * @see #getMetadata
	 * @see #getOriginalMetadata
	 */
	public void setMetadata(ImageServerMetadata metadata);
	
	/**
	 * Get the original metadata read during creation of the server.  This may or may not be correct.
	 * @return
	 * @see #getMetadata
	 */
	public ImageServerMetadata getOriginalMetadata();
	
	/**
	 * Tests whether the original metadata (e.g. pixel sizes in microns, magnification) is being used.
	 * @return
	 * @see #getMetadata
	 * @see #getOriginalMetadata
	 */
	public boolean usesOriginalMetadata();
	
	
}
