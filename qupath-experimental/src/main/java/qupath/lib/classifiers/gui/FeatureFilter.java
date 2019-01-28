package qupath.lib.classifiers.gui;

import java.util.List;

import org.bytedeco.javacpp.opencv_core.Mat;

import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(FeatureFilters.FeatureFilterTypeAdapterFactory.class)
public abstract class FeatureFilter {
	    	
	public abstract String getName();
	
	public abstract int getPadding();
	
	public abstract void calculate(Mat matInput, List<Mat> output);
	
	@Override
	public String toString() {
		return getName();
	}
	
}