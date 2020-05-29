/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.color;

import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.objects.classes.PathClassTools;

/**
 * Factory methods to help create ColorModels for use with BufferedImages.
 * 
 * @author Pete Bankhead
 *
 */
public final class ColorModelFactory {
	
	private final static Logger logger = LoggerFactory.getLogger(ColorModelFactory.class);
	
	private static Map<Map<Integer, PathClass>, IndexColorModel> classificationModels = Collections.synchronizedMap(new HashMap<>());

	private static Map<List<ImageChannel>, ColorModel> probabilityModels8 = Collections.synchronizedMap(new HashMap<>());
	private static Map<List<ImageChannel>, ColorModel> probabilityModels32 = Collections.synchronizedMap(new HashMap<>());

	
	private ColorModelFactory() {
		throw new AssertionError();
	}
	
	/**
	 * Get a ColorModel suitable for showing output pixel classifications, using an 8-bit or 16-bit labeled image.
     * A cached model may be retrieved if possible, rather than generating a new one.
	 * 
	 * @param channels
	 * @return
	 */
    public static ColorModel getIndexedClassificationColorModel(Map<Integer, PathClass> channels) {
    	var map = classificationModels.get(channels);

    	var stats = channels.keySet().stream().mapToInt(c -> c).summaryStatistics();
    	if (stats.getMin() < 0)
    		throw new IllegalArgumentException("Minimum label must be >= 0");
    	int length = stats.getMax() + 1;
    	
    	if (map == null) {
            int[] cmap = new int[length];
            
            for (var entry: channels.entrySet()) {
        		var pathClass = entry.getValue();
        		if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified()) {
        			cmap[entry.getKey()] = ColorTools.makeRGBA(255, 255, 255, 0);
        		} else if (PathClassTools.isIgnoredClass(entry.getValue())) {
            		var color = pathClass == null ? 0 : pathClass.getColor();
            		int alpha = 192;
            		if (pathClass == PathClassFactory.getPathClass(StandardPathClasses.IGNORE))
            			alpha = 32;
                	cmap[entry.getKey()] = ColorTools.makeRGBA(ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color), alpha);
            	} else
            		cmap[entry.getKey()] = entry.getValue().getColor();
            }
            if (cmap.length <= 256)
                map = new IndexColorModel(8, length, cmap, 0, true, -1, DataBuffer.TYPE_BYTE);    		
            else if (cmap.length <= 65536)
                map = new IndexColorModel(16, length, cmap, 0, true, -1, DataBuffer.TYPE_USHORT);
            else
            	throw new IllegalArgumentException("Only 65536 possible classifications supported!");
            classificationModels.put(new LinkedHashMap<>(channels), map);
    	}
    	return map;
    }
    
    /**
     * Create an indexed colormap for a labelled (indexed color) image.
     * @param labelColors map with integer labels as keys and packed (A)RGB colors as values.
     * @param includeAlpha if true, allow alpha values to be included in the colormap
     * @return
     */
    public static ColorModel createIndexedColorModel(Map<Integer, Integer> labelColors, boolean includeAlpha) {
    	var stats = labelColors.keySet().stream().mapToInt(c -> c).summaryStatistics();
    	if (stats.getMin() < 0)
    		throw new IllegalArgumentException("Minimum label must be >= 0");
    	int length = stats.getMax() + 1;
    	
        int[] cmap = new int[length];
        
        for (var entry: labelColors.entrySet()) {
        	Integer value = entry.getValue();
        	if (value == null) {
        		logger.warn("No color specified for index {} - using default gray", entry.getKey());
        		cmap[entry.getKey()] = includeAlpha ? ColorTools.makeRGBA(127, 127, 127, 127) : ColorTools.makeRGB(127, 127, 127);
        	} else
        		cmap[entry.getKey()] = entry.getValue();
        }
        if (cmap.length <= 256)
            return new IndexColorModel(8, length, cmap, 0, includeAlpha, -1, DataBuffer.TYPE_BYTE);    		
        if (cmap.length <= 65536)
        	return new IndexColorModel(16, length, cmap, 0, includeAlpha, -1, DataBuffer.TYPE_USHORT);
    	throw new IllegalArgumentException("Only 65536 possible labels supported!");
    }
    
    
    /**
     * Get a ColorModel suitable for showing 8-bit pseudo-probabilities for multiple channels.
     * <p>
     * The range of values is assumed to be 0-255, treated as probabilities rescaled from 0-1.
     * A cached model will be retrieved where possible, rather than generating a new one.
     * 
     * @param channels
     * @return
     */
    public static ColorModel getProbabilityColorModel8Bit(List<ImageChannel> channels) {
    	var map = probabilityModels8.get(channels);
    	if (map == null) {
            int[] colors = channels.stream().mapToInt(c -> c.getColor()).toArray();
    		map = ColorModelFactory.createColorModel(PixelType.UINT8, channels.size(), channels.size() == 1, colors);
    		probabilityModels8.put(new ArrayList<>(channels), map);
    	}
    	return map;
    }
    
    
    /**
     * Get a ColorModel suitable for showing 32-bit (pseudo-)probabilities for multiple channels.
     * <p>
     * The range of values is assumed to be 0-1.
     * A cached model will be retrieved where possible, rather than generating a new one.
     * 
     * @param channels
     * @return
     */
    public static ColorModel getProbabilityColorModel32Bit(List<ImageChannel> channels) {
    	var map = probabilityModels32.get(channels);
    	if (map == null) {
            int[] colors = channels.stream().mapToInt(c -> c.getColor()).toArray();
    		map = ColorModelFactory.createColorModel(PixelType.FLOAT32, channels.size(), channels.size() == 1, colors);
    		probabilityModels32.put(new ArrayList<>(channels), map);
    	}
    	return map;
    }

	/**
	 * Get a dummy ColorModel instance.
	 * <p>
	 * This isn't very highly recommended; it is here to help in cases where a {@code BufferedImage} 
	 * is required, but really only a raster is needed.
	 * The actual color used is undefined (but it will likely be black).
	 * 
	 * @param bpp
	 * @return
	 */
	public static ColorModel getDummyColorModel(int bpp) {
		return new DummyColorModel(bpp);
	}

	/**
	 * Create a new ColorModel that can be used to display an image where pixels per channel reflect 
	 * probabilities, either as float or byte.
	 * <p>
	 * It is assumed that the probabilities sum to 1; if they sum to less than 1, <code>alphaResidual</code> 
	 * can be used to make 'unknown' pixels transparent/translucent rather than black.
	 * 
	 * @param type type for individual pixels
	 * @param nChannels Number of color channels.
	 * @param alphaResidual If true, the alpha value is scaled according to the sum of the other probabilities.
	 *                      This makes pixels with low probabilities for all other channels appear transparent.
	 * @param colors Packed RGB representations of each color, in order.  A single channel can also be set to <code>BACKGROUND_COLOR</code>,
	 * 						which indicates that it is used directly to control the alpha values, overriding <code>alphaResidual</code>.
	 * @return
	 */
	public static ColorModel createColorModel(final PixelType type, final int nChannels, final boolean alphaResidual, final int...colors) {
		return new DefaultColorModel(type, nChannels, alphaResidual, colors);
	}
	
	/**
	 * Create a ColorModel for displaying an image with the specified channel colors.
	 * Note that this currently does not provide any means to change the display range (e.g. for brightness/contrast)
	 * and therefore may not be sufficient on its own for generating a satisfactory (A)RGB image.
	 * 
	 * @param type
	 * @param channels
	 * @return
	 */
	public static ColorModel createColorModel(final PixelType type, final List<ImageChannel> channels) {
		return new DefaultColorModel(type, channels.size(), false, channels.stream().mapToInt(c -> {
			Integer color = c.getColor();
			if (color == null)
				color = ColorTools.makeRGB(255, 255, 255);
			return color;
		}).toArray());
	}
    

}