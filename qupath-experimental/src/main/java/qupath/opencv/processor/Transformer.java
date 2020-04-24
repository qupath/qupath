package qupath.opencv.processor;

import org.bytedeco.opencv.opencv_core.Mat;

/**
 * A transformer that may be applied to a {@link Mat}.
 * This is intended to apply simple transforms to an image (e.g. color transforms, channel extraction, filtering, normalization).
 */
public interface Transformer {
	
	/**
	 * Transform the image. The input may be modified (and the transform applied in place), therefore 
	 * should be duplicated if a copy is required to be kept.
	 * @param input input image
	 * @return output image, which may be the same as the input image
	 */
	public Mat transform(Mat input);
	
	/**
	 * Get the padding requested for this transform.
	 * 
	 * The default is to return {@link Padding#empty()}.
	 * Subclasses that perform neighborhood operations should override this and 
	 * 
	 * @return
	 */
	public default Padding getPadding() {
		return Padding.empty();
	}

}
