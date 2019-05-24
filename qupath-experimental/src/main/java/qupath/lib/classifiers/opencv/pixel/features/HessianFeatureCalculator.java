package qupath.lib.classifiers.opencv.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.bytedeco.opencv.opencv_core.Mat;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.processing.TypeAdaptersCV;

@JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
public class HessianFeatureCalculator implements OpenCVFeatureCalculator {
	
	

	@Override
	public Mat calculateFeatures(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
		
		
		
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PixelClassifierMetadata getMetadata() {
		// TODO Auto-generated method stub
		return null;
	}

}
