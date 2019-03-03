package qupath.lib.gui.images.stores;

import java.awt.Graphics;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

import qupath.lib.images.servers.ImageServer;

public interface ImageRegionRenderer {
	
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
	 * @param renderer
	 * @param timeoutMilliseconds Timeout after which a request is made from the PathImageServer directly, rather than waiting for tile requests.
	 */
	public void paintRegionCompletely(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, ImageObserver observer, ImageRenderer renderer, long timeoutMilliseconds);
	
	
	public void paintRegion(ImageServer<BufferedImage> server, Graphics g, Shape clipShapeVisible, int zPosition, int tPosition, double downsampleFactor, BufferedImage imgThumbnail, ImageObserver observer, ImageRenderer renderer);

}
