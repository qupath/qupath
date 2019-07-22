package qupath.lib.color;

import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import qupath.lib.images.servers.ImageChannel;

/**
 * Factory methods to help create ColorModels for use with BufferedImages.
 * 
 * @author Pete Bankhead
 *
 */
public final class ColorModelFactory {
	
	private static Map<List<ImageChannel>, IndexColorModel> classificationModels = Collections.synchronizedMap(new HashMap<>());

	private static Map<List<ImageChannel>, ColorModel> probabilityModels8 = Collections.synchronizedMap(new HashMap<>());
	private static Map<List<ImageChannel>, ColorModel> probabilityModels32 = Collections.synchronizedMap(new HashMap<>());

	
	private ColorModelFactory() {
		throw new AssertionError();
	}
	
	/**
	 * Get a ColorModel suitable for showing output pixel classifications, using an 8-bit 
	 * labeled image.
	 * 
	 * @param channels
	 * @return
	 */
    public static ColorModel getIndexedColorModel(List<ImageChannel> channels) {
    	var map = classificationModels.get(channels);
    	if (map == null) {
            int[] cmap = channels.stream().mapToInt(c -> c.getColor()).toArray();
            if (cmap.length > 256)
            	throw new IllegalArgumentException("Only 256 possible classifications supported!");
            map = new IndexColorModel(8, channels.size(), cmap, 0, true, -1, DataBuffer.TYPE_BYTE);    		
            classificationModels.put(new ArrayList<>(channels), map);
    	}
    	return map;
    }
    
    
    /**
     * Get a ColorModel suitable for showing 8-bit pseudo-probabilities for multiple channels.
     * <p>
     * The range of values is assumed to be 0-255, treated as probabilities rescaled from 0-1.
     * 
     * @param channels
     * @return
     */
    public static ColorModel geProbabilityColorModel8Bit(List<ImageChannel> channels) {
    	var map = probabilityModels8.get(channels);
    	if (map == null) {
            int[] colors = channels.stream().mapToInt(c -> c.getColor()).toArray();
    		map = ColorModelFactory.createProbabilityColorModel(8, channels.size(), channels.size() == 1, colors);
    		probabilityModels8.put(new ArrayList<>(channels), map);
    	}
    	return map;
    }
    
    
    /**
     * Get a ColorModel suitable for showing 32-bit (pseudo-)probabilities for multiple channels.
     * <p>
     * The range of values is assumed to be 0-1.
     * 
     * @param channels
     * @return
     */
    public static ColorModel geProbabilityColorModel32Bit(List<ImageChannel> channels) {
    	var map = probabilityModels32.get(channels);
    	if (map == null) {
            int[] colors = channels.stream().mapToInt(c -> c.getColor()).toArray();
    		map = ColorModelFactory.createProbabilityColorModel(32, channels.size(), channels.size() == 1, colors);
    		probabilityModels32.put(new ArrayList<>(channels), map);
    	}
    	return map;
    }

	/**
	 * Get a dummy ColorModel instance.
	 * <p>
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
	 * <p>
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
