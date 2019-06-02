package qupath.lib.classifiers.opencv.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.classifiers.gui.PixelClassifierStatic;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.OpenCVTypeAdapters;
import qupath.lib.regions.RegionRequest;

@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
public class BasicFeatureCalculator implements OpenCVFeatureCalculator {

//	static {
//		FeatureCalculators.FeatureCalculatorTypeAdapterFactory.registerSubtype(BasicFeatureCalculator.class);
//	}

	private String name;
	private List<Integer> channels = new ArrayList<>();

	@JsonAdapter(FeatureFilters.FeatureFilterTypeAdapterFactory.class)
	private List<FeatureFilter> filters = new ArrayList<>();
	private PixelClassifierMetadata metadata;

	private int nPyramidLevels = 1;
	private int padding = 0;

	public BasicFeatureCalculator(String name, List<Integer> channels, List<FeatureFilter> filters, double pixelSizeMicrons) {
		this.name = name;
		this.channels.addAll(channels);
		this.filters.addAll(filters);

		var outputChannels = new ArrayList<ImageChannel>();
		for (var channel : channels) {
			for (var filter : filters) {
				for (String featureName : filter.getFeatureNames())
					outputChannels.add(ImageChannel.getInstance("Channel " + channel + ": " + featureName, ColorTools.makeRGB(255, 255, 255)));
				//    				outputChannels.add(new PixelClassifierOutputChannel(channel.getName() + ": " + filter.getName(), ColorTools.makeRGB(255, 255, 255)));
			}
		}

		padding = filters.stream().mapToInt(f -> f.getPadding()).max().orElseGet(() -> 0);
		metadata = new PixelClassifierMetadata.Builder()
				.channels(outputChannels)
				.inputPixelSize(pixelSizeMicrons)
				.inputShape(512, 512)
				.build();


		for (int i = 1; i< nPyramidLevels; i++) {
			padding *= 2;
		}

	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public Mat calculateFeatures(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {

		BufferedImage img = PixelClassifierStatic.getPaddedRequest(server, request, padding);

		List<Mat> output = new ArrayList<Mat>();

		int w = img.getWidth();
		int h = img.getHeight();
		float[] pixels = new float[w * h];
		Mat mat = new Mat(h, w, opencv_core.CV_32FC1);
		FloatIndexer idx = mat.createIndexer();
		for (var channel : channels) {
			pixels = img.getRaster().getSamples(0, 0, w, h, channel, pixels);
			//				channel.getValues(img, 0, 0, w, h, pixels);
			idx.put(0L, pixels);

			addFeatures(mat, output);

			if (nPyramidLevels > 1) {
				Mat matLastLevel = mat;
				var size = mat.size();
				for (int i = 1; i < nPyramidLevels; i++) {
					// Downsample pyramid level
					Mat matPyramid = new Mat();
					opencv_imgproc.pyrDown(matLastLevel, matPyramid);
					// Add features to a temporary list (because we'll need to resize them
					List<Mat> tempList = new ArrayList<>();
					addFeatures(matPyramid, tempList);
					for (var temp : tempList) {
						// Upsample
						for (int k = i; k > 0; k--)
							opencv_imgproc.pyrUp(temp, temp);
						// Adjust size if necessary
						if (temp.rows() != size.height() || temp.cols() != size.width())
							opencv_imgproc.resize(temp, temp, size, 0, 0, opencv_imgproc.INTER_CUBIC);
						output.add(temp);
					}
					if (matLastLevel != mat)
						matLastLevel.release();
					matLastLevel = matPyramid;
				}
				matLastLevel.release();
			}

		}

		opencv_core.merge(new MatVector(output.toArray(Mat[]::new)), mat);
		if (padding > 0)
			mat.put(mat.apply(new Rect(padding, padding, mat.cols()-padding*2, mat.rows()-padding*2)).clone());

		return mat;
	}


	void addFeatures(Mat mat, List<Mat> output) {
		for (var filter : filters) {
			filter.calculate(mat, output);
		}
	}


	@Override
	public PixelClassifierMetadata getMetadata() {
		return metadata;
	}

}