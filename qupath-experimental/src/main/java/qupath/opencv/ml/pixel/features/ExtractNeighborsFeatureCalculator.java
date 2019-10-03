package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.images.ImageData;
import qupath.lib.regions.RegionRequest;

/**
 * Feature calculator that simply takes a square of neighboring pixels as the features.
 * 
 * @author Pete Bankhead
 *
 */
@JsonAdapter(FeatureCalculators.FeatureCalculatorTypeAdapterFactory.class)
class ExtractNeighborsFeatureCalculator implements FeatureCalculator<BufferedImage> {
	
	private static Logger logger = LoggerFactory.getLogger(ExtractNeighborsFeatureCalculator.class);
	
	private int size;
	private int[] inputChannels;
	
	private ImmutableDimension inputShape = ImmutableDimension.getInstance(256, 256);
	
	ExtractNeighborsFeatureCalculator(int size, int...inputChannels) {
		if (size % 2 != 1) {
			logger.warn("Extract neighbors size {}, but really this should be an odd number! I will do my best.", size);
		}
		this.size = size;
		
		this.inputChannels = inputChannels;
	}
	
	
	
//	private synchronized List<String> getFeatureNames() {
//		List<String> featureNames = new ArrayList<>();
//		int xStart = -size/2;
//		int yStart = -size/2;
//		for (int c = 0; c < inputChannels.length; c++) {
//			for (int y = yStart; y < yStart + size; y++) {
//				for (int x = xStart; x < xStart + size; x++) {
//					featureNames.add("Pixel (x=" + x + ", y=" + y +", c=" + c +")");
//				}			
//			}
//		}
//		return featureNames;
//	}

	@Override
	public List<PixelFeature> calculateFeatures(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
		int pad = size / 2;
		BufferedImage img = PixelClassifierTools.getPaddedRequest(imageData.getServer(), request, pad);
		WritableRaster raster = img.getRaster();

		List<PixelFeature> features = new ArrayList<>();

		int width = img.getWidth();
		int height = img.getHeight();
		float[] pixels = null;
		for (int b : inputChannels) {
			// Extract pixels for the current band
			pixels = raster.getSamples(0, 0, width, height, b, pixels);
			// Outer loops extract features in turn
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					// Inner loop extracts for each pixel
					int ww = width - pad*2;
					int hh = height - pad*2;
					float[] f = new float[ww * hh];
					for (int yy = 0; yy < hh; yy++) {
						for (int xx = 0; xx < ww; xx++) {
							float val = pixels[(y + yy)*width + xx + x];
							f[yy*ww + xx] = val;
						}							
					}
					String name = "Pixel (x=" + (x-pad) + ", y=" + (y-pad) +", c=" + b +")";
					features.add(new DefaultPixelFeature<>(name, f, ww, hh));
				}				
			}
		}
		return features;
	}

	@Override
	public ImmutableDimension getInputSize() {
		return inputShape;
	}



	@Override
	public boolean supportsImage(ImageData<BufferedImage> imageData) {
		return imageData.getServer().nChannels() > Arrays.stream(inputChannels).max().orElse(0);
	}
	
}