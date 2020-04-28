package qupath.process.gui.ml;

/**
 * Define the area of an image to which pixel classification should be applied.
 * 
 * @author Pete Bankhead
 */
public enum ClassificationRegion {
	/**
	 * The entire image
	 */
	ENTIRE_IMAGE,
	/**
	 * Annotated-regions only
	 */
	ANNOTATIONS_ONLY;
	
	@Override
	public String toString() {
		switch(this) {
		case ANNOTATIONS_ONLY:
			return "Annotations only";
		case ENTIRE_IMAGE:
			return "Entire image";
		default:
			return "Unknown";
		}
	}
}