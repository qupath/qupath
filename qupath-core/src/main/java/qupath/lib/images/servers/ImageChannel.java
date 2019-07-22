package qupath.lib.images.servers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import qupath.lib.common.ColorTools;

/**
 * The name and display color for a single image channel.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageChannel {
	
	private static Map<String, ImageChannel> cache = new HashMap<>();
	
	/**
	 * Special color indicating pixel values for a channel indicate levels of transparency.
	 */
	public static final Integer TRANSPARENT = ColorTools.makeRGBA(255, 253, 254, 0);
	
	private String name;
	private Integer color;
	
	/**
	 * Default red channel for RGB images.
	 */
	public final static ImageChannel RED   = getInstance("Red", ColorTools.makeRGB(255, 0, 0));
	
	/**
	 * Default green channel for RGB images.
	 */
	public final static ImageChannel GREEN = getInstance("Green", ColorTools.makeRGB(0, 255, 0));
	
	/**
	 * Default blue channel for RGB images.
	 */
	public final static ImageChannel BLUE  = getInstance("Blue", ColorTools.makeRGB(0, 0, 0255));
	
	/**
	 * Get a channel instance with the specified name and color.
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
	 * Get the default color for a specified channel, for use when no channel colors are already known.
	 * <p>
	 * Note that the exact colors returned may differ in future releases, and it is not guaranteed that all colors 
	 * will be unique.  If the colors must be exactly reproducible then it is better to specify them rather than to
	 * depend on this method.
	 * 
	 * @param channel
	 * @return
	 */
	public static Integer getDefaultChannelColor(int channel) {
		int n = 360;
		if (channel >= n) {
			channel = channel % n;
//			double scale = 1.0 / (channel / 6);
//			return ColorTools.makeScaledRGB(getDefaultChannelColor(channel % 6), scale);
		}
		switch (channel) {
		case 0: return ColorTools.makeRGB(255, 0, 0);
		case 1: return ColorTools.makeRGB(0, 255, 0);
		case 2: return ColorTools.makeRGB(0, 0, 255);
		case 3: return ColorTools.makeRGB(255, 224, 0);
		case 4: return ColorTools.makeRGB(0, 224, 224);
		case 5: return ColorTools.makeRGB(255, 0, 224);
		default:
			int c = channel;
			int hueInc = 128;
			float hue = ((c * hueInc) % 360) / 360f;
			float saturation = 1f - (c / 10) / 20f;
			float brightness = 1f - (c / 10) / 20f;
			return Color.HSBtoRGB(hue, saturation, brightness);
		}
	}
	
	/**
	 * Get default channel list for RGB images.
	 * @return
	 */
	public static List<ImageChannel> getDefaultRGBChannels() {
		return Arrays.asList(RED, GREEN, BLUE);
	}
	
	/**
	 * Get default channel list for an image with a specified number of channels.
	 * This is useful whenever no further channel name or color information is available.
	 * @param nChannels
	 * @return
	 */
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
	 * Name of the output channel.
	 * 
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Color used to display the output channel
	 * 
	 * @return
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