package qupath.lib.classifiers.opencv.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.bytedeco.opencv.opencv_core.Mat;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.OpenCVTypeAdapters;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.processing.HessianCalculator.MultiscaleResultsBuilder;

@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
public class MultiscaleFeatureCalculator implements OpenCVFeatureCalculator {
	
	MultiscaleFeatureCalculator(List<MultiscaleResultsBuilder> builders) {
		throw new UnsupportedOperationException("Not yet implemented!");
	}

	@Override
	public Mat calculateFeatures(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		throw new UnsupportedOperationException("Not yet implemented!");
	}
	
	@Override
	public ImmutableDimension getInputSize() {
		throw new UnsupportedOperationException("Not yet implemented!");
	}

	@Override
	public List<String> getFeatureNames() {
		throw new UnsupportedOperationException("Not yet implemented!");
	}

}
