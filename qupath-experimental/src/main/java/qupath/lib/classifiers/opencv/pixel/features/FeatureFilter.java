package qupath.lib.classifiers.opencv.pixel.features;

import java.util.Collections;
import java.util.List;

import org.bytedeco.opencv.opencv_core.Mat;

import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(FeatureFilters.FeatureFilterTypeAdapterFactory.class)
public abstract class FeatureFilter {
	   
	/**
	 * Name for this feature (should include any related parameter values to aid interpretation).
	 * @return
	 */
	public abstract String getName();
	
	/**
	 * Name for each of the features added by this calculator.
	 * @return
	 */
	public abstract List<String> getFeatureNames();
	
	/**
	 * Requested padding, in pixels, that should be added prior to calculating these 
	 * features to reduce boundary effects.
	 * <p>
	 * This provides an opportunity to use overlapping image tiles from a large image, 
	 * rather than giving tiles at the desired size and needing to pad some other way 
	 * (e.g. boundary replication or mirror padding).
	 * 
	 * @return
	 */
	public abstract int getPadding();
	
	/**
	 * Input image, and list into which any output images should be added.
	 * 
	 * @param matInput
	 * @param output
	 */
	public abstract void calculate(Mat matInput, List<Mat> output);
	
	@Override
	public String toString() {
		return getName();
	}
	
}