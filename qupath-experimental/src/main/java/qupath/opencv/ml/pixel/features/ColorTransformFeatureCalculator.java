package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.pixel.features.ColorTransforms.ColorTransform;

/**
 * Feature calculator that simply applies one or more color transforms to an image, pixel-wise.
 * 
 * @author Pete Bankhead
 *
 */
@JsonAdapter(FeatureCalculators.FeatureCalculatorTypeAdapterFactory.class)
class ColorTransformFeatureCalculator implements FeatureCalculator<BufferedImage> {
	
	private static Logger logger = LoggerFactory.getLogger(ColorTransformFeatureCalculator.class);
	
	private ColorTransform[] transforms;
	
	private ImmutableDimension inputShape = ImmutableDimension.getInstance(256, 256);
	
	ColorTransformFeatureCalculator(ColorTransform...transforms) {
		this.transforms = transforms;
	}

	@Override
	public List<PixelFeature> calculateFeatures(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
		BufferedImage img = imageData.getServer().readBufferedImage(request);

		List<PixelFeature> features = new ArrayList<>();

		int width = img.getWidth();
		int height = img.getHeight();
		float[] pixels = null;
		for (var transform : transforms) {
			// Extract pixels for the current band
			pixels = transform.extractChannel(imageData, img, pixels);
			String name = transform.getName();
			features.add(new DefaultPixelFeature<>(name, pixels, img.getWidth(), img.getHeight()));
		}
		return features;
	}

	@Override
	public ImmutableDimension getInputSize() {
		return inputShape;
	}

	@Override
	public boolean supportsImage(ImageData<BufferedImage> imageData) {
		for (var transform : transforms) {
			if (!transform.supportsImage(imageData))
				return false;
		}
		return true;
	}
	
}