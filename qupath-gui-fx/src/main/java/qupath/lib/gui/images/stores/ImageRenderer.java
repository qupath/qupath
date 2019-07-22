package qupath.lib.gui.images.stores;

import java.awt.image.BufferedImage;

public interface ImageRenderer {
	
	/**
	 * Apply the required transforms to a BufferedImage to get the appropriate display.
	 * imgOutput should always be an RGB image (of some kind), or null if a new image should be created.
	 * 
	 * imgInput should always be an image of the kind that matches the imgData, e.g. RGB/non-RGB, same number of channels,
	 * same bit-depth.
	 * 
	 * @param imgInput input image
	 * @param imgOutput output image, with the same width and height as the input; 
	 *        if null or the image size is inconsistent, a new RGB image should be created
	 * @return imgOutput, or a new RGB image created for the output
	 */
	public BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput);
	
	/**
	 * Timestamp of the last change (probably in milliseconds).
	 * <p>
	 * This can be used to identify when the status has changed.
	 * 
	 * @return
	 */
	public long getLastChangeTimestamp();
	
	/**
	 * Get a unique key, which will be used for caching.
	 * <p>
	 * The only requirement is that the key is unique for the {@code ImageRenderer} in its 
	 * current state.  It is suggested to base it on the full class name, a counter for instances 
	 * of this class, and a timestamp derived from the last change.
	 * 
	 * @return
	 */
	public String getUniqueID();
	
}
