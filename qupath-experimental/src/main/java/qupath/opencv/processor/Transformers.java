package qupath.opencv.processor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleResultsBuilder;
import qupath.opencv.tools.OpenCVTools;

public class Transformers {
	
	public static enum RGBConvert {
		
		RGB2HSV, RGB2CIELAB;
		
		int getCode() {
			switch (this) {
			case RGB2CIELAB:
				return opencv_imgproc.COLOR_RGB2Lab;
			case RGB2HSV:
				return opencv_imgproc.COLOR_RGB2HSV;
			default:
				throw new IllegalArgumentException("Unknown conversion " + this);
			}
		}
	}
	
	
	public static interface ImageDataServer<T> extends ImageServer<T> {
		
		public ImageData<T> getImageData();
		
	}
	
	
	/**
	 * Request pixels from an image, potentially applying further transforms.
	 */
	public static interface ImageDataTransformer {
		
		public Mat transform(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException;
		
		/**
		 * Query whether this transform can be applied to the specified image.
		 * Reasons why it may not be include the type or channel number being incompatible.
		 * @param imageData
		 * @return
		 */
		boolean supportsImage(ImageData<BufferedImage> imageData);
		
		/**
		 * Get appropriate channels to reflect the output of this transform, given the input.
		 * 
		 * @param imageData 
		 * 
		 * @return
		 */
		public List<ImageChannel> getChannels(ImageData<BufferedImage> imageData);
		
	}
	
	public static ImageDataServer<BufferedImage> buildServer(ImageData<BufferedImage> imageData, ImageDataTransformer transformer, PixelCalibration resolution) {
		return buildServer(imageData, transformer, resolution, 512, 512);
	}
	
	public static ImageDataServer<BufferedImage> buildServer(ImageData<BufferedImage> imageData, ImageDataTransformer transformer, PixelCalibration resolution, int tileWidth, int tileHeight) {
		double downsample = resolution.getAveragedPixelSize().doubleValue() / imageData.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue();
		return new TransformedTileableImageServer(imageData, downsample, tileWidth, tileHeight, transformer);
	}
	
	
	static class DefaultImageDataTransformer implements ImageDataTransformer {
		
		private Transformer transformer;
		
		DefaultImageDataTransformer(Transformer transformer) {
			this.transformer = transformer;
		}
		
		public Mat transform(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
			BufferedImage img;
			if (transformer == null) {
				img = imageData.getServer().readBufferedImage(request);
				return OpenCVTools.imageToMat(img);
			} else {
				var padding = transformer.getPadding();
				img = PixelClassifierTools.getPaddedRequest(imageData.getServer(), request, padding);
				var mat = OpenCVTools.imageToMat(img);
				mat.convertTo(mat, opencv_core.CV_32F);
				return transformer.transform(mat);
			}
		}

		@Override
		public List<ImageChannel> getChannels(ImageData<BufferedImage> imageData) {
			if (transformer == null)
				return imageData.getServer().getMetadata().getChannels();
			else
				return transformer.getChannels(imageData.getServer().getMetadata().getChannels());
		}



		@Override
		public boolean supportsImage(ImageData<BufferedImage> imageData) {
			return true;
		}
		
	}
	
	static class ChannelImageDataTransformer implements ImageDataTransformer {
		
		private ColorTransform[] colorTransforms;
		private Transformer transformer;
		
		ChannelImageDataTransformer(Transformer transformer, ColorTransform... colorTransforms) {
			this.colorTransforms = colorTransforms.clone();
			this.transformer = transformer;
		}
		
		@Override
		public boolean supportsImage(ImageData<BufferedImage> imageData) {
			for (var t : colorTransforms) {
				if (!t.supportsImage(imageData.getServer()))
					return false;
			}
			return true;
		}
		
		public Mat transform(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
			BufferedImage img;
			if (transformer == null)
				img = imageData.getServer().readBufferedImage(request);
			else
				img = PixelClassifierTools.getPaddedRequest(imageData.getServer(), request, transformer.getPadding());
			
			float[] pixels = null;
			var server = imageData.getServer();
			List<Mat> channels = new ArrayList<>();
			for (var t : colorTransforms) {
				var mat = new Mat(img.getWidth(), img.getHeight(), opencv_core.CV_32FC1);
				pixels = t.extractChannel(server, img, pixels);
				try (FloatIndexer idx = mat.createIndexer()) {
					idx.put(0L, pixels);
				}
				channels.add(mat);
			}
			var mat = OpenCVTools.mergeChannels(channels, null);
			if (transformer != null) {
				mat = transformer.transform(mat);
			}
			return mat;
		}

		@Override
		public List<ImageChannel> getChannels(ImageData<BufferedImage> imageData) {
			var channels = Arrays.stream(colorTransforms).map(c -> ImageChannel.getInstance(c.getName(), null)).collect(Collectors.toList());
			if (transformer == null)
				return channels;
			else
				return transformer.getChannels(channels);
		}
		
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	
//	public static class TransformedImageServer extends TransformingImageServer<BufferedImage> {
//		
//		private final static Logger logger = LoggerFactory.getLogger(TransformedImageServer.class);
//
//		private Transformer transformer;
//		
//		protected TransformedImageServer(ImageServer<BufferedImage> server, Transformer transformer) {
//			super(server);
//			this.transformer = transformer;
//			
//			// TODO: Set metadata!
//			logger.warn("Must set metadata!");
//		}
//		
//		@Override
//		public BufferedImage readBufferedImage(final RegionRequest request) throws IOException {
//			var padding = transformer.getPadding();
//			var img = PixelClassifierTools.getPaddedRequest(getWrappedServer(), request, padding;
//			var mat = OpenCVTools.imageToMat(img);
//			mat.convertTo(mat, opencv_core.CV_32F);
//			mat = transformer.transform(mat);
//			return OpenCVTools.matToBufferedImage(mat);
//		}
//
//		@Override
//		public String getServerType() {
//			return "Transformed server";
//		}
//
//		@Override
//		protected ServerBuilder<BufferedImage> createServerBuilder() {
//			// TODO Auto-generated method stub
//			return null;
//		}
//
//		@Override
//		protected String createID() {
//			return UUID.randomUUID().toString();
//		}
//		
//	}
	
	
	
	
	public static class TransformedTileableImageServer extends AbstractTileableImageServer implements ImageDataServer<BufferedImage> {
		
		private final static Logger logger = LoggerFactory.getLogger(TransformedTileableImageServer.class);
		
		private ImageData<BufferedImage> imageData;
		private ImageDataTransformer transformer;
		private ImageServerMetadata metadata;
		
		TransformedTileableImageServer(ImageData<BufferedImage> imageData, double downsample, int tileWidth, int tileHeight, ImageDataTransformer transformer) {
			super();
			
			this.imageData = imageData;
			this.transformer = transformer;
			
			// TODO: UPDATE PIXEL TYPE!
			logger.warn("Using default pixel type!");
			var pixelType = PixelType.FLOAT32;
			
			// Update channels according to the transformer
			var channels = transformer.getChannels(imageData);
						
			metadata = new ImageServerMetadata.Builder(imageData.getServer().getMetadata())
					.levelsFromDownsamples(downsample)
					.preferredTileSize(tileWidth, tileHeight)
					.pixelType(pixelType)
					.channels(channels)
					.rgb(false)
					.build();
			
		}
		
		@Override
		public ImageData<BufferedImage> getImageData() {
			return imageData;
		}

		@Override
		public Collection<URI> getURIs() {
			return imageData.getServer().getURIs();
		}

		@Override
		public String getServerType() {
			return "Transformer server";
		}

		@Override
		public ImageServerMetadata getOriginalMetadata() {
			return metadata;
		}

		@Override
		protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
			var mat = transformer.transform(imageData, tileRequest.getRegionRequest());
			return OpenCVTools.matToBufferedImage(mat);
		}

		@Override
		protected ServerBuilder<BufferedImage> createServerBuilder() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		protected String createID() {
			return UUID.randomUUID().toString();
		}
		
	}

	
	
	public static class Builder {
		
		private List<Transformer> transformers = new ArrayList<>();
		
		public Builder gaussianBlur(double sigma) {
			transformers.add(new GaussianFilter(sigma, sigma));
			return this;
		}
		
		public Builder channels(int... channels) {
			transformers.add(new ExtractChannels(channels));
			return this;
		}
		
		public Builder deconvolve(ColorDeconvolutionStains stains) {
			transformers.add(new ColorDeconvolution(stains));
			return this;
		}
		
		public Builder multiply(double value) {
			transformers.add(new Multiplier(value));
			return this;
		}
		
//		public Builder normalizeMeanStd() {
//			transformers.add(new NormalizeMeanStd());
//			return this;
//		}
		
		public Builder normalizeMinMax(double outputMin, double outputMax) {
			transformers.add(new NormalizeMinMax(outputMin, outputMax));
			return this;
		}
		
		public Builder normalizeMinMax() {
			return normalizeMinMax(0.0, 1.0);
		}
		
		public Builder normalizePercentile(double percentileMin, double percentileMax) {
			transformers.add(new NormalizePercentile(percentileMin, percentileMax));
			return this;
		}
		
		public Builder statModel(OpenCVStatModel statModel, boolean requestProbabilities) {
			transformers.add(new StatModelTransformer(statModel, requestProbabilities));
			return this;
		}
		
		public Builder threshold(double... thresholds) {
			transformers.add(new FixedThresholder(thresholds));
			return this;
		}
		
		public Builder thresholdMeanStd(double... k) {
			transformers.add(new MeanStdDevThresholder(k));
			return this;
		}
		
		public Builder thresholdMedianAbsDev(double... k) {
			transformers.add(new MedianAbsDevThresholder(k));
			return this;
		}
		
		public Builder sqrt() {
			transformers.add(new Sqrt());
			return this;
		}
		
		public Builder power(double power) {
			transformers.add(new Power(power));
			return this;
		}
		
		public Builder features(Collection<MultiscaleFeature> features, double sigmaX, double sigmaY) {
			transformers.add(new FeatureTransformer(features, sigmaX, sigmaY));
			return this;
		}
		
		public Builder transformer(Transformer transformer) {
			transformers.add(transformer);
			return this;
		}
		
		public Builder splitMerge(Transformer... transformers) {
			if (transformers.length == 0)
				return this;
			if (transformers.length == 1)
				return transformer(transformers[0]);
			this.transformers.add(new SplitMergeTransform(transformers));
			return this;
		}
		
		public Builder splitMerge(Collection<? extends Transformer> transformers) {
			return splitMerge(transformers.toArray(Transformer[]::new));
		}
		
		public Transformer build() {
			if (transformers.size() == 1)
				return transformers.get(0);
			return new SequentialTransform(transformers);
		}

		public ImageServer<BufferedImage> buildServer(ImageData<BufferedImage> imageData, double downsample, int tileWidth, int tileHeight, ColorTransform... inputChannels) {
			return new TransformedTileableImageServer(imageData, downsample, tileWidth, tileHeight, buildImageTransformer(inputChannels));
		}

		public ImageServer<BufferedImage> buildServer(ImageData<BufferedImage> imageData) {
			return buildServer(imageData, 1.0, 512, 512);
		}
		
		public ImageDataTransformer buildImageTransformer(ColorTransform... inputChannels) {
			return inputChannels.length == 0 ? new DefaultImageDataTransformer(build()) : new ChannelImageDataTransformer(build(), inputChannels);
		}
		
	}
	
	
	static class RGBTransform implements Transformer {
		
		private RGBConvert conversion;
		
		public Mat transform(Mat input) {
			opencv_imgproc.cvtColor(input, input, conversion.getCode());
			return input;
		}
		
	}
	
	
	static class Multiplier implements Transformer {

		private double value;
		
		Multiplier(double value) {
			this.value = value;
		}
		
		@Override
		public Mat transform(Mat input) {
			input.put(opencv_core.multiply(input, value));
			return input;
		}
		
	}
	
//	static class Adder implements Transformer {
//
//		private double value;
//		private transient Scalar scalar;
//		
//		Adder(double value) {
//			this.value = value;
//		}
//		
//		@Override
//		public Mat transform(Mat input) {
//			input.put(opencv_core.multiply(input, value));
//			if (input.channels() <= 4)
//				input.put(opencv_core.add(input, getScalar()));
//			else {
//				OpenCVTools.
//			}
//			return input;
//		}
//		
//		private Scalar getScalar() {
//			if (scalar == null)
//				scalar = Scalar.all(value);
//			return scalar;
//		}
//		
//	}
	
	
	static abstract class AbstractThresholder implements Transformer {
		
		@Override
		public Mat transform(Mat input) {
			var matvec = new MatVector();
			opencv_core.split(input, matvec);
			var matvec2 = new MatVector();
			for (int c = 0; c < matvec.size(); c++) {
				var mat = matvec.get(c);
				var mat2 = new Mat();
				double threshold = getThreshold(mat, c);
				opencv_imgproc.threshold(mat, mat2, threshold, 1, opencv_imgproc.THRESH_BINARY);
				matvec2.push_back(mat2);
			}
			opencv_core.merge(matvec2, input);
			return input;
		}
		
		public abstract double getThreshold(Mat mat, int channel);
		
	}
	
	static class FixedThresholder extends AbstractThresholder {
		
		private double[] thresholds;
		
		FixedThresholder(double... thresholds) {
			this.thresholds = thresholds.clone();
		}
		
		@Override
		public double getThreshold(Mat mat, int channel) {
			return thresholds[Math.min(channel, thresholds.length-1)];
		}
		
	}
	
	static class Sqrt implements Transformer {
		
		@Override
		public Mat transform(Mat input) {
			opencv_core.sqrt(input, input);
			return input;
		}
		
	}
	
	static class Power implements Transformer {
		
		private double power;
		
		Power(double power) {
			this.power = power;
		}
		
		@Override
		public Mat transform(Mat input) {
			opencv_core.pow(input, power, input);
			return input;
		}
		
	}
	
	/**
	 * Set a threshold as the {@code mean + k * std.dev}.
	 */
	static class MeanStdDevThresholder extends AbstractThresholder {
		
		private double[] k;
		
		MeanStdDevThresholder(double... k) {
			this.k = k.clone();
		}
		
		@Override
		public double getThreshold(Mat mat, int channel) {
			var mean = new Mat();
			var stddev = new Mat();
			opencv_core.meanStdDev(mat, mean, stddev);
			var m = mean.createIndexer().getDouble(0L);
			var s = stddev.createIndexer().getDouble(0L);
			var k = this.k[Math.min(channel,  this.k.length-1)];
			return m + s * k;
		}
		
	}
	
	
	/**
	 * Set a threshold as the {@code mean + k * std.dev}.
	 */
	static class MedianAbsDevThresholder extends AbstractThresholder {
		
		private double[] k;
		
		MedianAbsDevThresholder(double... k) {
			this.k = k.clone();
		}
		
		@Override
		public double getThreshold(Mat mat, int channel) {
			double median = median(mat);
			var matAbs = opencv_core.abs(opencv_core.subtract(mat, Scalar.all(median))).asMat();
			double mad = median(matAbs) / 0.6750;
			var k = this.k[Math.min(channel,  this.k.length-1)];
			return median + mad * k;
		}
		
	}
	
	
	static class StatModelTransformer implements Transformer {

		private OpenCVStatModel model;
		private boolean requestProbabilities;
		
		StatModelTransformer(OpenCVStatModel model, boolean requestProbabilities) {
			this.model = model;
			this.requestProbabilities = requestProbabilities;
		}
		
		@Override
		public Mat transform(Mat input) {
			int w = input.cols();
			int h = input.rows();
			input.reshape(1, w * h);
			if (requestProbabilities)
				model.predict(input, null, input);
			else
				model.predict(input, input, null);
			input.put(input.reshape(input.cols(), h));
			return input;
		}

	}
	
	
	static class ColorDeconvolution implements Transformer {
		
		private ColorDeconvolutionStains stains;
		private transient Mat matInv;
		
		ColorDeconvolution(ColorDeconvolutionStains stains) {
			this.stains = stains;
		}

		@Override
		public Mat transform(Mat input) {
			assert input.channels() == 3; // Must be RGB
			
			int w = input.cols();
			int h = input.rows();
			
			input.convertTo(input, opencv_core.CV_32F);
			
			var matCols = input.reshape(1, w * h);
			double[] max = new double[] {stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue()};
			for (int c = 0; c < 3; c++) {
				var col = matCols.col(c);
				var expr = opencv_core.max(col, 1.0);
				col.put(opencv_core.divide(expr, max[c]));
				opencv_core.log(col, col);
				expr = opencv_core.divide(col, -Math.log(10.0));
				col.put(expr);
			}
			matCols.put(opencv_core.max(matCols, 0.0));
			
			matCols.put(matCols.reshape(3, h));
			opencv_core.transform(matCols, matCols, getMatInv());
			
			input.put(matCols);
			return input;
		}
		
		private Mat getMatInv() {
			if (matInv == null) {
				matInv = new Mat(3, 3, opencv_core.CV_64FC1, Scalar.ZERO);
				var inv = stains.getMatrixInverse();
				try (DoubleIndexer idx = matInv.createIndexer()) {
					idx.put(0, 0, inv[0]);
					idx.put(1, 0, inv[1]);
					idx.put(2, 0, inv[2]);
				}
				matInv.put(matInv.t());
			}
			return matInv;
		}
		
		@Override
		public List<ImageChannel> getChannels(List<ImageChannel> channels) {
			return Arrays.asList(
				ImageChannel.getInstance(stains.getStain(1).getName(), stains.getStain(1).getColor()),
				ImageChannel.getInstance(stains.getStain(2).getName(), stains.getStain(2).getColor()),	
				ImageChannel.getInstance(stains.getStain(3).getName(), stains.getStain(3).getColor())	
				);
		}
		
	}
	
	
	static class ExtractChannels implements Transformer {
		
		private int[] channels;
		
		ExtractChannels(int... channels) {
			this.channels = channels.clone();
		}
		
		public Mat transform(Mat input) {
			var matvec = new MatVector();
			opencv_core.split(input, matvec);
			var matvec2 = new MatVector();
			for (int c : channels)
				matvec2.push_back(matvec.get(c));
			opencv_core.merge(matvec2, input);
			return input;
		}
		
		@Override
		public List<ImageChannel> getChannels(List<ImageChannel> channels) {
			List<ImageChannel> newChannels = new ArrayList<>();
			for (int c : this.channels)
				newChannels.add(channels.get(c));
			return newChannels;
		}
		
		@Override
		public String toString() {
			if (channels == null || channels.length == 0)
				return "No channels";
			if (channels.length == 1)
				return "Channel " + channels[0];
			return "Channels [" + Arrays.stream(channels).mapToObj(c -> Integer.toString(c)).collect(Collectors.joining(",")) + "]";
		}
		
	}
	
	/**
	 * Normalize by rescaling channels into a fixed range (usually 0-1) using the min/max values.
	 */
	static class NormalizeMinMax implements Transformer {
		
		private double outputMin = 0.0;
		private double outputMax = 1.0;
		
		NormalizeMinMax(double outputMin, double outputMax) {
			this.outputMin = outputMin;
			this.outputMax = outputMax;
		}

		@Override
		public Mat transform(Mat input) {
			var matvec = new MatVector();
			opencv_core.split(input, matvec);
			for (int i = 0; i < matvec.size(); i++) {
				var mat = matvec.get(i);
				// TODO: Should 0 and 1 be swapped? Help docs are unclear...
				opencv_core.normalize(mat, mat, outputMin, outputMax, opencv_core.NORM_MINMAX, -1, null);
			}
			opencv_core.merge(matvec, input);
			return input;
		}
		
	}
	
	/**
	 * Normalize by rescaling channels into a fixed range (usually 0-1) using the min/max values.
	 */
	static class NormalizePercentile implements Transformer {
		
		private double[] percentiles;
		
		NormalizePercentile(double percentileMin, double percentileMax) {
			this.percentiles = new double[] {percentileMin, percentileMax};
		}

		@Override
		public Mat transform(Mat input) {
			var matvec = new MatVector();
			opencv_core.split(input, matvec);
			for (int i = 0; i < matvec.size(); i++) {
				var mat = matvec.get(i);
				var range = percentiles(mat, percentiles);
				double scale = 1./(range[1] - range[0]);
				double offset = -range[0];
				mat.convertTo(mat, mat.type(), scale, offset*scale);
			}
			opencv_core.merge(matvec, input);
			return input;
		}
		
	}
	
	static abstract class PaddedTransformer implements Transformer {
		
		private Padding padding;
		
		protected abstract Padding calculatePadding();

		/**
		 * Transform, but ignoring padding.
		 * Non-empty padding will be removed automatically elsewhere.
		 * @param input
		 * @return
		 */
		protected abstract Mat transformPadded(Mat input);

		@Override
		public Mat transform(Mat input) {
//			long before = input.cols();
			var mat = transformPadded(input);
			var padding = getPadding();
			if (padding.isEmpty())
				return mat;
			mat.put(stripPadding(mat, getPadding()));
//			long after = mat.cols();
//			System.err.println(getClass().getSimpleName() + ": \tBefore " + before + ", after " + after + " - " + padding.getX1());
			return mat;
		}
		
		@Override
		public Padding getPadding() {
			if (padding == null)
				padding = calculatePadding();
			return padding;
		}
		
	}
	
	
	static Mat stripPadding(Mat mat, Padding padding) {
		if (padding.isEmpty())
			return mat;
		return mat.apply(new Rect(
				padding.getX1(), padding.getY1(),
				mat.cols()-padding.getXSum(), mat.rows()-padding.getYSum())).clone();
	}
	
	
	static class Filter2D extends PaddedTransformer {
		
		private Mat kernel;
		
		Filter2D(Mat kernel) {
			this.kernel = kernel;
		}

		@Override
		protected Padding calculatePadding() {
			int x = kernel.cols()/2;
			int y = kernel.rows()/2;
			return Padding.getPadding(x, y);
		}

		@Override
		protected Mat transformPadded(Mat input) {
			var matvec = new MatVector();
			opencv_core.split(input, matvec);
			for (int i = 0; i < matvec.size(); i++) {
				var mat = matvec.get(i);
				opencv_imgproc.filter2D(mat, mat, -1, kernel);
			}
			opencv_core.merge(matvec, input);
			return input;
		}
		
	}
	
	
	
	static class FeatureTransformer extends PaddedTransformer {
		
		private List<MultiscaleFeature> features;
		private double sigmaX, sigmaY;
		private transient MultiscaleResultsBuilder builder;
		
		FeatureTransformer(Collection<MultiscaleFeature> features, double sigmaX, double sigmaY) {
			this.features = new ArrayList<>(new LinkedHashSet<>(features));
			this.sigmaX = sigmaX;
			this.sigmaY = sigmaY;
		}

		@Override
		protected Padding calculatePadding() {
			return Padding.symmetric(padValue());
		}

		@Override
		protected Mat transformPadded(Mat input) {
			var builder = getBuilder();
			var output = new ArrayList<Mat>();
			for (var mat : OpenCVTools.splitChannels(input)) {
				var results = builder.build(mat);
				output.addAll(results.values());
			}
			return OpenCVTools.mergeChannels(output, input);
		}
		
		@Override
		public List<ImageChannel> getChannels(List<ImageChannel> channels) {
			var list = new ArrayList<ImageChannel>();
			for (var c : channels) {
				var color = c.getColor();
				var name = c.getName();
				for (var f : features) {
					list.add(ImageChannel.getInstance(
							String.format("%s (%s, sigma=%.1f,%.1f)", name, f.toString(), sigmaX, sigmaY),
							color));
				}
			}
			return list;
		}
		
		private int padValue() {
			return (int)(Math.ceil(Math.max(sigmaX, sigmaY) * 4) * 2 + 1);
		}
		
		private MultiscaleResultsBuilder getBuilder() {
			if (builder == null) {
				var b = new MultiscaleResultsBuilder(features);
				b.sigmaX(sigmaX);
				b.sigmaY(sigmaY);
//				b.paddingXY(padValue());
//				b.pixelCalibration(PixelCalibration.getDefaultInstance(), 1.0);
				builder = b;
			}
			return builder;
		}
		
		
	}
	
	
	
	static class GaussianFilter extends PaddedTransformer {
		
		private double sigmaX, sigmaY;
		
		GaussianFilter(double sigmaX, double sigmaY) {
			this.sigmaX = sigmaX;
			this.sigmaY = sigmaY;
		}
		
		@Override
		public Mat transformPadded(Mat input) {
			if (sigmaX == 0 && sigmaY == 0)
				return input;
			var matvec = new MatVector();
			opencv_core.split(input, matvec);
			var padding = getPadding();
			var size = new Size(padding.getX1()*2+1, padding.getY1()*2+1);
			for (int i = 0; i < matvec.size(); i++) {
				var mat = matvec.get(i);
				opencv_imgproc.GaussianBlur(mat, mat, size, sigmaX, sigmaY, opencv_core.BORDER_REFLECT);
			}
			opencv_core.merge(matvec, input);
			return input;
		}

		@Override
		protected Padding calculatePadding() {
			int padX = (int)Math.ceil(sigmaX * 3) + 1;
			int padY = (int)Math.ceil(sigmaY * 3) + 1;
			return Padding.getPadding(padX, padX, padY, padY);
		}
		
	}
	
//	static class PercentileNormalize implements Transformer {
//		
//		@Override
//		public Mat transform(Mat input) {
//			var matvec = new MatVector();
//			opencv_core.split(input, matvec);
//			for (int i = 0; i < matvec.size(); i++) {
//				var mat = matvec.get(i);
//				opencv_core.sort
//				opencv_core.normalize(mat, mat, 0.0, 1.0, opencv_core.NORM_MINMAX, -1, null);
//			}
//			opencv_core.merge(matvec, input);
//			return input;
//		}
//		
//	}
	
	
	static void normalize(Mat mat, double[] subtract, double[] scale) {
		int n = mat.arrayChannels();
		assert subtract.length == n;
		assert scale.length == n;
		var matvec = new MatVector();
		opencv_core.split(mat, matvec);
		for (int i = 0; i < n; i++) {
			var temp = matvec.get(i);
			temp.put(
					opencv_core.multiply(
							opencv_core.subtract(temp, Scalar.all(subtract[i])), scale[i])
					);
		}
		opencv_core.merge(matvec, mat);
	}
	
	static void normalize(Mat mat, double offset, double scale) {
		if (offset == 0 && scale == 1)
			return;
		mat.convertTo(mat, mat.type(), scale, -offset*scale);
//		if (offset == 0) {
//			if (scale == 1)
//				return;
//			mat.put(opencv_core.multiply(mat, scale));
//			return;
//		} else {
//			var expr = opencv_core.add(mat, Scalar.all(offset));
//			if (scale == 1) {
//				mat.put(expr);
//			} else {
//				mat.put(opencv_core.multiply(expr, scale));
//			}
//		}
	}
	
	
	static class SequentialTransform extends PaddedTransformer {
		
		private final static Logger logger = LoggerFactory.getLogger(SequentialTransform.class);
		
		private List<Transformer> transformers;
		
		SequentialTransform(Collection<Transformer> transformers) {
			this.transformers = new ArrayList<>(transformers);
		}

		@Override
		protected Padding calculatePadding() {
			 var padding = Padding.empty();
			for (var t : transformers)
				padding = padding.add(t.getPadding());
			return padding;
		}

		@Override
		public Mat transform(Mat input) {
			for (var t : transformers)
				input = t.transform(input);
			return input;
		}
		
		/**
		 * Should not be called!
		 */
		@Override
		protected Mat transformPadded(Mat input) {
			logger.warn("transformPadded(Mat) should not be called directly for this class!");
			return transform(input);
		}
		
		@Override
		public List<ImageChannel> getChannels(List<ImageChannel> channels) {
			for (var t : transformers)
				channels = t.getChannels(channels);
			return channels;
		}
		
	}
	

	static double median(Mat mat) {
		return percentiles(mat, 50.0)[0];
	}
	
	static double[] percentiles(Mat mat, double... percentiles) {
		double[] result = new double[percentiles.length];
		if (result.length == 0)
			return result;
		int n = (int)mat.total();
		var mat2 = mat.reshape(1, n);
		var matSorted = new Mat();
		opencv_core.sort(mat2, matSorted, opencv_core.CV_SORT_ASCENDING + opencv_core.CV_SORT_EVERY_COLUMN);
		try (var idx = matSorted.createIndexer()) {
			for (int i = 0; i < result.length; i++) {
				long ind = (long)(percentiles[i] / 100.0 * n);
				result[i] = idx.getDouble(ind);
			}
		}
		matSorted.release();
		return result;
	}
	
	
	/**
	 * Duplicate the input {@link Mat} and apply different transformers to the duplicates, 
	 * merging the result at the end.
	 */
	static class SplitMergeTransform extends PaddedTransformer {
		
		private List<Transformer> transformers;
		private boolean doParallel = false;
		
		SplitMergeTransform(Transformer...transformers) {
			this(false, transformers);
		}
		
		SplitMergeTransform(boolean doParallel, Transformer...transformers) {
			this.doParallel = doParallel;
			this.transformers = new ArrayList<>();
			for (var t : transformers) {
				this.transformers.add(t);
			}
		}

		@Override
		public Mat transform(Mat input) {
			return transformPadded(input);
		}
		
		@Override
		public List<ImageChannel> getChannels(List<ImageChannel> channels) {
			return transformers.stream()
					.flatMap(t -> t.getChannels(channels).stream())
					.collect(Collectors.toList());
		}

		@Override
		protected Padding calculatePadding() {
			var padding = Padding.empty();
			for (var t : transformers)
				padding = padding.max(t.getPadding());
			return padding;
		}

		@Override
		protected Mat transformPadded(Mat input) {
			if (transformers.isEmpty())
				return new Mat();
			if (transformers.size() == 1)
				return transformers.get(0).transform(input);
			var stream = transformers.stream();
			if (doParallel)
				stream = stream.parallel();
			// TODO: Handle non-equal padding for different transforms!
			var mats = stream.map(t -> t.transform(input.clone())).collect(Collectors.toList());
			var padding = getPadding();
			for (int i = 0; i < transformers.size(); i++) {
				var t = transformers.get(i);
				var pad = t.getPadding();
				var padExtra = Padding.getPadding(
						padding.getX1() - pad.getX1(),
						padding.getX2() - pad.getX2(),
						padding.getY1() - pad.getY1(),
						padding.getY2() - pad.getY2()
						);
				if (!padExtra.isEmpty())
					mats.get(i).put(stripPadding(mats.get(i), padExtra));
			}
			return OpenCVTools.mergeChannels(mats, null);
		}

		
	}

}
