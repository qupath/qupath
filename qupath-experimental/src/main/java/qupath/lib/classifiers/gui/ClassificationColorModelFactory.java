package qupath.lib.classifiers.gui;

import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import qupath.lib.awt.color.model.ColorModelFactory;
import qupath.lib.images.servers.ImageChannel;

public class ClassificationColorModelFactory {
	
	private static Map<List<ImageChannel>, IndexColorModel> classificationModels = Collections.synchronizedMap(new HashMap<>());

	private static Map<List<ImageChannel>, ColorModel> probabilityModels8 = Collections.synchronizedMap(new HashMap<>());
	private static Map<List<ImageChannel>, ColorModel> probabilityModels32 = Collections.synchronizedMap(new HashMap<>());

	
	private ClassificationColorModelFactory() {
		throw new AssertionError();
	}
	
	/**
	 * Get a ColorModel suitable for showing output pixel classifications, using an 8-bit 
	 * labeled image.
	 * 
	 * @param channels
	 * @return
	 */
    public static ColorModel geClassificationColorModel(List<ImageChannel> channels) {
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
    		map = ColorModelFactory.createProbabilityColorModel(8, channels.size(), false, colors);
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
    		map = ColorModelFactory.createProbabilityColorModel(32, channels.size(), false, colors);
    		probabilityModels32.put(new ArrayList<>(channels), map);
    	}
    	return map;
    }
    

}
