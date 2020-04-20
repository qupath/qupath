package qupath.lib.gui.images.stores;

import java.awt.Graphics;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import qupath.lib.images.servers.ImageServer;

/**
 * Interface for painting regions of an {@link ImageServer} onto a {@link Graphics} object.
 * 
 * @author Pete Bankhead
 */
public interface ImageRegionRenderer {
	
	/**
	 * Similar to {@link #paintRegion(ImageServer, Graphics, Shape, int, int, double, BufferedImage, ImageObserver, ImageRenderer)}, 
	 * but wait until all the tiles have arrived (or abort if it is taking too long).
	 *
	 * @param server the server representing the image that shown be painted
	 * @param g the graphics object upon which to paint
	 * @param clipShapeVisible the visible shape representing the area of the graphics object that should be filled
	 * @param zPosition the z-stack position
	 * @param tPosition the timepoint position
	 * @param downsampleFactor the downsample factor
	 * @param observer an {@link ImageObserver} (often ignored)
	 * @param renderer an {@link ImageRenderer} to convert images to RGB
	 * @param timeoutMilliseconds Timeout after which a request is made from the PathImageServer directly, rather than waiting for tile requests.
	 */
	public void paintRegionCompletely(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, ImageObserver observer, ImageRenderer renderer, long timeoutMilliseconds);
	
	/**
	 * Paint an image region.
	 * 
	 * @param server the server representing the image that shown be painted
	 * @param g the graphics object upon which to paint
	 * @param clipShapeVisible the visible shape representing the area of the graphics object that should be filled
	 * @param zPosition the z-stack position
	 * @param tPosition the timepoint position
	 * @param downsampleFactor the downsample factor
	 * @param imgThumbnail a thumbnail image; if not null, this will be used to 'fill the gaps'
	 * @param observer an {@link ImageObserver} (often ignored)
	 * @param renderer an {@link ImageRenderer} to convert images to RGB
	 */
	public void paintRegion(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, BufferedImage imgThumbnail, ImageObserver observer, ImageRenderer renderer);

}
