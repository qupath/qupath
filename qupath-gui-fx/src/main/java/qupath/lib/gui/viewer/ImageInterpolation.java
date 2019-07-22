package qupath.lib.gui.viewer;

/**
 * Supported interpolation methods when displaying images.
 * 
 * @author Pete Bankhead
 */
public enum ImageInterpolation {
	
	NEAREST, BILINEAR;
	
	@Override
	public String toString() {
		if (this == NEAREST)
			return "Nearest neighbor";
		else if (this == BILINEAR)
			return "Bilinear";
		throw new IllegalArgumentException("Unknown interpolation!");
	}

}
