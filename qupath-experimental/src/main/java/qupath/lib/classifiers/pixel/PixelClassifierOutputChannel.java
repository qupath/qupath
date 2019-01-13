package qupath.lib.classifiers.pixel;

import qupath.lib.common.ColorTools;

public class PixelClassifierOutputChannel {
	
	public static final Integer TRANSPARENT = ColorTools.makeRGBA(255, 253, 254, 0);
	
	private String name;
	private Integer color;
	
	public PixelClassifierOutputChannel(String name, Integer color) {
		this.name = name;
		this.color = color;
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
		PixelClassifierOutputChannel other = (PixelClassifierOutputChannel) obj;
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