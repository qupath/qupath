package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.opencv_core.Mat;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.gui.ml.PixelClassifierStatic;
import qupath.lib.images.ImageData;
import qupath.lib.io.OpenCVTypeAdapters;
import qupath.lib.regions.RegionRequest;

/**
 * Feature calculator that simply takes a square of neighboring pixels as the features.
 * <p>
 * Warning! This is incomplete and may be removed. It also makes an unnecessary trip through OpenCV if the output will be converted to a BufferedImage.
 * 
 * @author Pete Bankhead
 *
 */
@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
public class ExtractNeighborsFeatureCalculator implements OpenCVFeatureCalculator {
	
	private int radius;
	private List<String> featureNames;
	private int[] inputChannels;
	private int n;
	
	private ImmutableDimension inputShape = new ImmutableDimension(256, 256);
	
	public ExtractNeighborsFeatureCalculator(String name, double pixelSizeMicrons, int radius, int...inputChannels) {
		this.radius = radius;
		
		n = (radius * 2 + 1) * (radius * 2 + 1) * inputChannels.length;
		this.inputChannels = inputChannels;
				
		featureNames = IntStream.range(0, n)
				.mapToObj(c -> "Feature " + c)
				.collect(Collectors.toList());
	}

	@Override
	public List<Feature<Mat>> calculateFeatures(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
		BufferedImage img = PixelClassifierStatic.getPaddedRequest(imageData.getServer(), request, radius);
		WritableRaster raster = img.getRaster();

		n = (radius * 2 + 1) * (radius * 2 + 1) * inputChannels.length;

		List<Feature<Mat>> features = new ArrayList<>();

		int k = 1;
		for (int b : inputChannels) {
			for (int y = 0; y < radius * 2 + 1; y++) {
				for (int x = 0; x < radius * 2 + 1; x++) {

					Mat mat = new Mat(img.getHeight()-radius*2, img.getWidth()-radius*2, opencv_core.CV_32FC1);
					FloatIndexer idx = mat.createIndexer();
					int rows = mat.rows();
					int cols = mat.cols();

					for (int r = 0; r < rows; r++) {
						for (int c = 0; c < cols; c++) {
							float val = raster.getSampleFloat(c + x, r + y, b);
							//								System.err.println(r + ", " + c + ", " + k);
							idx.put(r, c, val);
						}							
					}
					idx.release();
					features.add(new DefaultFeature<>(String.format("Pixel (%d, %d)", x, y), mat));
				}				
			}
		}
	return features;
}

	@Override
	public ImmutableDimension getInputSize() {
		return inputShape;
	}
	
}