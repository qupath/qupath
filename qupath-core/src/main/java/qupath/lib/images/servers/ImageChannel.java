package qupath.lib.images.servers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import qupath.lib.common.ColorTools;

/**
 * The name & display color for a single image channel.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageChannel {
	
	private static Map<String, ImageChannel> cache = new HashMap<>();
	
	public static final Integer TRANSPARENT = ColorTools.makeRGBA(255, 253, 254, 0);
	
	private String name;
	private Integer color;
	
	public final static ImageChannel RED   = getInstance("Red", ColorTools.makeRGB(255, 0, 0));
	public final static ImageChannel GREEN = getInstance("Green", ColorTools.makeRGB(0, 255, 0));
	public final static ImageChannel BLUE  = getInstance("Blue", ColorTools.makeRGB(0, 0, 0255));
	
	/**
	 * Get a channel instance with the specified name & color.
	 * 
	 * @param name Name for the channel - this must not be null.
	 * @param color Color as a packed (A)RGB value.
	 * @return
	 */
	public synchronized static ImageChannel getInstance(String name, Integer color) {
		name = Objects.requireNonNull(name);
		var key = name + "::" + color;
		var channel = cache.get(key);
		if (channel == null) {
			channel = new ImageChannel(name, color);
			cache.put(key, channel);
		}
		return channel;
	}
	
	@Override
	public String toString() {
		if (color == null)
			return "Image channel: " + name;
		return String.format("Image channel: %s (a=%d, r=%d, g=%d, b=%d)", name, 
				ColorTools.alpha(color), ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color));
	}
	
	
//	/**
//	 * Method that may be used to get RGB colors.
//	 * Classes that only provide RGB images may call this from their getDefaultChannelColors method.
//	 * 
//	 * @param channel
//	 * @return
//	 */
//	protected Integer getDefaultRGBChannelColors(int channel) {
//		if (nChannels() == 1)
//			return ColorTools.makeRGB(255, 255, 255);
//		switch (channel) {
//		case 0: return ColorTools.makeRGB(255, 0, 0);
//		case 1: return ColorTools.makeRGB(0, 255, 0);
//		case 2: return ColorTools.makeRGB(0, 0, 255);
//		default:
//			return ColorTools.makeRGB(255, 255, 255);
//		}
//	}
//
//	
	/**
	 * Similar to getDefaultRGBChannelColors, but including Magenta, Cyan &amp; Yellow to return colors for up to 6 channels.
	 * If only one channel is present, or a channel number &gt; 6 is requested, Color.WHITE is returned.
	 * 
	 * @param channel
	 * @return
	 */
	public static Integer getDefaultChannelColor(int channel) {
		if (channel > 5) {
			double scale = 1.0 / (channel / 6);
			return ColorTools.makeScaledRGB(getDefaultChannelColor(channel % 6), scale);
		}
		switch (channel) {
		case 0: return ColorTools.makeRGB(255, 0, 0);
		case 1: return ColorTools.makeRGB(0, 255, 0);
		case 2: return ColorTools.makeRGB(0, 0, 255);
		case 3: return ColorTools.makeRGB(255, 255, 0);
		case 4: return ColorTools.makeRGB(0, 255, 255);
		case 5: return ColorTools.makeRGB(255, 0, 255);
		default:
			return ColorTools.makeRGB(255, 255, 255);
		}
	}
	
	public static List<ImageChannel> getDefaultRGBChannels() {
		return Arrays.asList(RED, GREEN, BLUE);
	}
	
	
	public static List<ImageChannel> getDefaultChannelList(int nChannels) {
		if (nChannels == 1)
			return Collections.singletonList(getInstance("Channel 1", ColorTools.makeRGB(255, 255, 255)));
		var list = new ArrayList<ImageChannel>();
		for (int i = 0; i < nChannels; i++) {
			list.add(getInstance(
					"Channel " + (i + 1),
					getDefaultChannelColor(i)
					));
		}
		return list;
	}
	
	
	
	
	private ImageChannel(String name, Integer color) {
		this.name = name;
		this.color = color == null ? TRANSPARENT : color;
	}
	
	/**
	 * Check if the color is 'transparent'; this is used for background/ignored channels.
	 * @return
	 */
	public boolean isTransparent() {
		return TRANSPARENT.equals(this.color);
	}
	
	/**
	 * Name of the output channel
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Color used to display the output channel
	 */
	public Integer getColor() {
		return color;
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((color == null) ? 0 : color.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ImageChannel other = (ImageChannel) obj;
		if (color == null) {
			if (other.color != null)
				return false;
		} else if (!color.equals(other.color))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}