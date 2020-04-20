package qupath.lib.gui.viewer;

/**
 * Supported interpolation methods when displaying images.
 * 
 * @author Pete Bankhead
 */
public enum ImageInterpolation {
	
	/**
	 * Nearest neighbor interpolation.
	 */
	NEAREST,
	
	/**
	 * Bilinear interpolation.
	 */
	BILINEAR;
	
	@Override
	public String toString() {
		if (this == NEAREST)
			return "Nearest neighbor";
		else if (this == BILINEAR)
			return "Bilinear";
		throw new IllegalArgumentException("Unknown interpolation!");
	}

}
