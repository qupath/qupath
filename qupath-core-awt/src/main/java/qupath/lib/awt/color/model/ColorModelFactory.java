package qupath.lib.awt.color.model;

import java.awt.image.ColorModel;

public class ColorModelFactory {
	
	/**
	 * Get a dummy ColorModel instance.
	 * This isn't very highly recommended; it is here to help in cases where a {@code BufferedImage} 
	 * is required, but really only a raster is needed.
	 * 
	 * @param bpp
	 * @return
	 */
	public static ColorModel getDummyColorModel(int bpp) {
		return new DummyColorModel(bpp);
	}

	/**
	 * Create a ColorModel that can be used to display an image where pixels per channel reflect 
	 * probabilities, either as float or byte.
	 * 
	 * It is assumed that the probabilities sum to 1; if they sum to less than 1, <code>alphaResidual</code> 
	 * can be used to make 'unknown' pixels transparent/translucent rather than black.
	 * 
	 * @param bpp Bits per pixel.
	 * @param nChannels Number of color channels.
	 * @param alphaResidual If true, the alpha value is scaled according to the sum of the other probabilities.
	 *                      This makes pixels with low probabilities for all other channels appear transparent.
	 * @param colors Packed RGB representations of each color, in order.  A single channel can also be set to <code>BACKGROUND_COLOR</code>,
	 * 						which indicates that it is used directly to control the alpha values, overriding <code>alphaResidual</code>.
	 * @return
	 */
	public static ColorModel createProbabilityColorModel(final int bpp, final int nChannels, final boolean alphaResidual, final int...colors) {
		return new ProbabilityColorModel(bpp, nChannels, alphaResidual, colors);
	}

}
