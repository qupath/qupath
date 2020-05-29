/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.opencv.ops;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_dnn.Net;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.RuntimeTypeAdapterFactory;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.GsonTools;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.OpenCVDNN;
import qupath.opencv.ml.FeaturePreprocessor;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.tools.LocalNormalization;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleResultsBuilder;
import qupath.opencv.tools.OpenCVTools;

/**
 * Create and use {@link ImageOp} and {@link ImageDataOp} objects.
 * 
 * @author Pete Bankhead
 */
public class ImageOps {
	
	private final static Logger logger = LoggerFactory.getLogger(ImageOps.class);
	
	
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	private @interface OpType {
		String value();
	}
	
	@SuppressWarnings("unchecked")
	private static <T> void registerTypes(RuntimeTypeAdapterFactory<T> factory, Class<T> factoryType, Class<?> cls, String base) {
		var annotation = cls.getAnnotation(OpType.class);
		if (annotation != null) {
			base = base + "." + annotation.value();
			if (factoryType.isAssignableFrom(cls)) {
				factory.registerSubtype((Class<? extends T>)cls, base);
			}
		}
		for (var c : cls.getDeclaredClasses()) {
			registerTypes(factory, factoryType, c, base);
		}
	}


	static {
		RuntimeTypeAdapterFactory<ImageOp> factoryOps = RuntimeTypeAdapterFactory.of(ImageOp.class, "type");
		registerTypes(factoryOps, ImageOp.class, ImageOps.class, "op");
		GsonTools.getDefaultBuilder().registerTypeAdapterFactory(factoryOps);

		RuntimeTypeAdapterFactory<ImageDataOp> factoryDataOps = RuntimeTypeAdapterFactory.of(ImageDataOp.class, "type");
		registerTypes(factoryDataOps, ImageDataOp.class, ImageOps.class, "data.op");
		GsonTools.getDefaultBuilder().registerTypeAdapterFactory(factoryDataOps);
	}
	
	/**
	 * Build an {@link ImageServer} that generates pixels on demand from an {@link ImageData} by applying an {@link ImageDataOp}.
	 * <p>
	 * Warning! Because {@link ImageData} are mutable, this can potentially result in inconsistencies if a change is made that impacts 
	 * the behavior of the op while tiles are still being requested.
	 * 
	 * @param imageData the {@link ImageData} to wrap
	 * @param dataOp the op performing pixel transformations
	 * @param resolution the resolution at which the op should be applied
	 * @return the {@link ImageDataServer}
	 */
	public static ImageDataServer<BufferedImage> buildServer(ImageData<BufferedImage> imageData, ImageDataOp dataOp, PixelCalibration resolution) {
		return buildServer(imageData, dataOp, resolution, 512, 512);
	}
	
	/**
	 * Build an {@link ImageServer} that generates pixels on demand from an {@link ImageData} by applying an {@link ImageDataOp}.
	 * <p>
	 * Warning! Because {@link ImageData} are mutable, this can potentially result in inconsistencies if a change is made that impacts 
	 * the behavior of the op while tiles are still being requested.
	 * 
	 * @param imageData the {@link ImageData} to wrap
	 * @param dataOp the op performing pixel transformations
	 * @param resolution the resolution at which the op should be applied
	 * @param tileWidth the tile width for the server
	 * @param tileHeight the tile height of the server
	 * @return the {@link ImageDataServer}
	 */
	public static ImageDataServer<BufferedImage> buildServer(ImageData<BufferedImage> imageData, ImageDataOp dataOp, PixelCalibration resolution, int tileWidth, int tileHeight) {
		double downsample = resolution.getAveragedPixelSize().doubleValue() / imageData.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue();
		return new ImageOpServer(imageData, downsample, tileWidth, tileHeight, dataOp);
	}
	
	/**
	 * Create an {@link ImageDataOp}, optionally using a specified array of input channels.
	 * @param inputChannels array of {@link ColorTransform} objects used to extract the pixels that will form the channels of the output {@link Mat}.
	 * 						If empty, the original image channels will be used.
	 * @return the {@link ImageDataOp}
	 */
	public static ImageDataOp buildImageDataOp(ColorTransform... inputChannels) {
		if (inputChannels == null || inputChannels.length == 0)
			return new DefaultImageDataOp(null);
		else
			return new ChannelImageDataOp(null, inputChannels);
	}
	
	/**
	 * Create an {@link ImageDataOp}, optionally using a specified collection of input channels.
	 * @param inputChannels array of {@link ColorTransform} objects used to extract the pixels that will form the channels of the output {@link Mat}.
	 * 						If empty, the original image channels will be used.
	 * @return the {@link ImageDataOp}
	 */
	public static ImageDataOp buildImageDataOp(Collection<? extends ColorTransform> inputChannels) {
		return buildImageDataOp(inputChannels.toArray(ColorTransform[]::new));
	}
	

	
	@OpType("default")
	static class DefaultImageDataOp implements ImageDataOp {
		
		private ImageOp op;
		
		DefaultImageDataOp(ImageOp op) {
			this.op = op;
		}
		
		@Override
		public Mat apply(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
			BufferedImage img;
			if (op == null) {
				img = imageData.getServer().readBufferedImage(request);
				return OpenCVTools.imageToMat(img);
			} else {
				var padding = op.getPadding();
				img = ServerTools.getPaddedRequest(imageData.getServer(), request, padding);
				var mat = OpenCVTools.imageToMat(img);
				mat.convertTo(mat, opencv_core.CV_32F);
				return op.apply(mat);
			}
		}

		@Override
		public List<ImageChannel> getChannels(ImageData<BufferedImage> imageData) {
			if (op == null)
				return imageData.getServer().getMetadata().getChannels();
			else
				return op.getChannels(imageData.getServer().getMetadata().getChannels());
		}

		@Override
		public boolean supportsImage(ImageData<BufferedImage> imageData) {
			return true;
		}

		@Override
		public ImageDataOp appendOps(ImageOp... ops) {
			if (ops.length == 0)
				return this;
			if (this.op == null)
				return new DefaultImageDataOp(Core.sequential(ops));
			var allOps = new ArrayList<ImageOp>();
			allOps.add(op);
			allOps.addAll(Arrays.asList(ops));
			var newOp = Core.sequential(allOps);
			return new DefaultImageDataOp(newOp);
		}
		
		@Override
		public PixelType getOutputType(PixelType inputType) {
			if (op == null)
				return PixelType.FLOAT32;
			return op.getOutputType(PixelType.FLOAT32);
		}
		
	}
	
	@OpType("channels")
	static class ChannelImageDataOp implements ImageDataOp {
		
		private ColorTransform[] colorTransforms;
		private ImageOp op;
		
		ChannelImageDataOp(ImageOp op, ColorTransform... colorTransforms) {
			this.colorTransforms = colorTransforms.clone();
			this.op = op;
		}
		
		@Override
		public boolean supportsImage(ImageData<BufferedImage> imageData) {
			for (var t : colorTransforms) {
				if (!t.supportsImage(imageData.getServer()))
					return false;
			}
			return true;
		}
		 
		@Override
		public Mat apply(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
			BufferedImage img;
			if (op == null)
				img = imageData.getServer().readBufferedImage(request);
			else
				img = ServerTools.getPaddedRequest(imageData.getServer(), request, op.getPadding());
			
			float[] pixels = null;
			var server = imageData.getServer();
			List<Mat> channels = new ArrayList<>();
			for (var t : colorTransforms) {
				var mat = new Mat(img.getHeight(), img.getWidth(), opencv_core.CV_32FC1);
				pixels = t.extractChannel(server, img, pixels);
				try (FloatIndexer idx = mat.createIndexer()) {
					idx.put(0L, pixels);
				}
				channels.add(mat);
			}
			var mat = OpenCVTools.mergeChannels(channels, null);
			if (op != null) {
				mat = op.apply(mat);
			}
			return mat;
		}

		@Override
		public List<ImageChannel> getChannels(ImageData<BufferedImage> imageData) {
			var channels = Arrays.stream(colorTransforms).map(c -> ImageChannel.getInstance(c.getName(), null)).collect(Collectors.toList());
			if (op == null)
				return channels;
			else
				return op.getChannels(channels);
		}
		
		@Override
		public ImageDataOp appendOps(ImageOp... ops) {
			if (ops.length == 0)
				return this;
			if (this.op == null)
				return new ChannelImageDataOp(Core.sequential(ops), colorTransforms);
			var allOps = new ArrayList<ImageOp>();
			allOps.add(op);
			allOps.addAll(Arrays.asList(ops));
			var newOp = Core.sequential(allOps);
			return new ChannelImageDataOp(newOp, colorTransforms);
		}

		@Override
		public PixelType getOutputType(PixelType inputType) {
			if (op == null)
				return inputType;
			return op.getOutputType(inputType);
		}
		
	}
	
	
	/**
	 * Normalization operations.
	 */
	public static class Normalize {
		
		/**
		 * Normalize the minimum and maximum values of the image to fall into the range 'outputMin - outputMax'.
		 * <p>
		 * This method is applied per-channel.
		 * @param outputMin
		 * @param outputMax
		 * @return
		 */
		public static ImageOp minMax(double outputMin, double outputMax) {
			return new NormalizeMinMaxOp(outputMin, outputMax);
		}
		
		/**
		 * Normalize the minimum and maximum values of the image to fall into the range 0 - 1.
		 * <p>
		 * This method is applied per-channel.
		 * @return
		 */
		public static ImageOp minMax() {
			return minMax(0, 1);
		}
		
		/**
		 * Normalize the image similar to {@link #minMax()}, but using low and high percentiles rather than minimum and 
		 * maximum respectively. {@code 100-percentileMin-percentileMax %} of the values then fall in the range 0-1.
		 * <p>
		 * This method is applied per-channel.
		 * @param percentileMin
		 * @param percentileMax
		 * @return
		 */
		public static ImageOp percentile(double percentileMin, double percentileMax) {
			return new NormalizePercentileOp(percentileMin, percentileMax);
		}
		
		/**
		 * Normalize channels so that they sum to the specified value.
		 * <p>
		 * Note: negative values in the input are clipped to 0.
		 * NaNs may occur if the sum is zero.
		 * 
		 * @param maxValue usually 1.0, but may be different (e.g. if the output should be 8-bit)
		 * @return
		 */
		public static ImageOp channelSum(double maxValue) {
			return new NormalizeChannelsOp(maxValue, false);			
		}
		
		/**
		 * Apply softmax, with the specified output maxValue.
		 * 
		 * @param maxValue usually 1.0, but may be different (e.g. if the output should be 8-bit)
		 * @return
		 */
		public static ImageOp channelSoftmax(double maxValue) {
			return new NormalizeChannelsOp(maxValue, true);
		}
		
		
		/**
		 * Apply local 2D normalization using Gaussian-weighted mean subtraction and (optionally) variance 
		 * estimation.
		 * <p>
		 * This method is applied per-channel.
		 * 
		 * @param sigmaMean sigma for Gaussian filter to use for subtraction
		 * @param sigmaVariance sigma for Gaussian filter to use for local variance estimation
		 * @return
		 */
		public static ImageOp localNormalization(double sigmaMean, double sigmaVariance) {
			return new LocalNormalizationOp(sigmaMean, sigmaVariance);
		}
		
		
		
		@OpType("channels")
		static class NormalizeChannelsOp implements ImageOp {
			
			private double maxValue;
			private boolean doSoftmax;
			
			NormalizeChannelsOp(double maxValue, boolean doSoftmax) {
				this.maxValue = maxValue;
				this.doSoftmax = doSoftmax;
			}

			@Override
			public Mat apply(Mat input) {
				int nChannels = input.channels();
				int nRows = input.rows();
				input.put(input.reshape(1, input.rows()*input.cols()));
				rescaleChannelsToProbabilities(input, input, maxValue, doSoftmax);
				input.put(input.reshape(nChannels, nRows));
				return input;
			}
			
		}
		
		
		/**
		 * Normalize by rescaling channels into a fixed range (usually 0-1) using the min/max values.
		 */
		@OpType("min-max")
		static class NormalizeMinMaxOp implements ImageOp {
			
			private double outputMin = 0.0;
			private double outputMax = 1.0;
			
			NormalizeMinMaxOp(double outputMin, double outputMax) {
				this.outputMin = outputMin;
				this.outputMax = outputMax;
			}

			@Override
			public Mat apply(Mat input) {
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
		@OpType("local")
		static class LocalNormalizationOp extends PaddedOp {
			
			private double sigmaMean;
			private double sigmaStdDev;
			
			LocalNormalizationOp(double sigmaMean, double sigmaStdDev) {
				this.sigmaMean = sigmaMean;
				this.sigmaStdDev = sigmaStdDev;
			}

			@Override
			protected Padding calculatePadding() {
				var sigma = Math.max(sigmaMean, sigmaStdDev);
				return getDefaultGaussianPadding(sigma, sigma);
			}

			@Override
			protected Mat transformPadded(Mat input) {
				int depth = input.depth();
				var channels = OpenCVTools.splitChannels(input);
				for (var m : channels)
					LocalNormalization.gaussianNormalize2D(m, sigmaMean, sigmaStdDev, opencv_core.BORDER_REFLECT);
				OpenCVTools.mergeChannels(channels, input);
				
				// We expect this conversion will already have happened, but should make sure in case the local normalization 
				// implementation changes
				if (depth != opencv_core.CV_64F)
					depth = opencv_core.CV_32F;
				input.convertTo(input, depth);
				return input;
			}
			
			@Override
			public PixelType getOutputType(PixelType inputType) {
				return inputType == PixelType.FLOAT64 ? inputType : PixelType.FLOAT32;
			}
			
		}
		
		/**
		 * Similar to {@link NormalizeMinMaxOp}, but using percentiles rather than min/max values.
		 */
	    @OpType("percentile")
		static class NormalizePercentileOp implements ImageOp {
			
			private double[] percentiles;
			
			NormalizePercentileOp(double percentileMin, double percentileMax) {
				this.percentiles = new double[] {percentileMin, percentileMax};
			}

			@Override
			public Mat apply(Mat input) {
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
		
	}
	
	/**
	 * Filtering operations.
	 */
	public static class Filters {
		
		/**
		 * Apply a (possibly anisotropic) 2D Gaussian filter.
		 * @param sigmaX
		 * @param sigmaY
		 * @return
		 */
		public static ImageOp gaussianBlur(double sigmaX, double sigmaY) {
			return new GaussianFilterOp(sigmaX, sigmaY);
		}
		
		/**
		 * Apply a 2D Gaussian filter.
		 * @param sigma
		 * @return
		 */
		public static ImageOp gaussianBlur(double sigma) {
			return gaussianBlur(sigma, sigma);
		}
		
		/**
		 * Apply a 2D filter.
		 * @param kernel
		 * @return
		 */
		public static ImageOp filter2D(Mat kernel) {
			return new FilterOp(kernel);
		}
		
		/**
		 * Compute one or more {@link MultiscaleFeature}s for the specified smoothing values (must be &gt; 0).
		 * @param features
		 * @param sigmaX
		 * @param sigmaY
		 * @return
		 */
		public static ImageOp features(Collection<MultiscaleFeature> features, double sigmaX, double sigmaY) {
			return new MultiscaleFeatureOp(features, sigmaX, sigmaY);
		}
		
		/**
		 * Apply a 2D maximum filter.
		 * @param radius filter radius. Must be 1 or greater. 1 indicates a 3x3 square; larger filters approximate a circle.
		 * @return
		 */
		public static ImageOp maximum(int radius) {
			return new MaximumFilterOp(radius);
		}

		/**
		 * Apply a 2D minimum filter.
		 * @param radius filter radius. Must be 1 or greater. 1 indicates a 3x3 square; larger filters approximate a circle.
		 * @return
		 */
		public static ImageOp minimum(int radius) {
			return new MinimumFilterOp(radius);
		}

		/**
		 * Apply a 2D morphological opening filter.
		 * @param radius filter radius. Must be 1 or greater. 1 indicates a 3x3 square; larger filters approximate a circle.
		 * @return
		 */
		public static ImageOp opening(int radius) {
			return new MorphOpenFilterOp(radius);
		}

		/**
		 * Apply a 2D morphological closing filter.
		 * @param radius filter radius. Must be 1 or greater. 1 indicates a 3x3 square; larger filters approximate a circle.
		 * @return
		 */
		public static ImageOp closing(int radius) {
			return new MorphCloseFilterOp(radius);
		}
		
		/**
		 * Apply a 2D median filter
		 * @param radius filter radius. 1 means a 3x3 filter, 2 means a 5x5 filter. For larger filter sizes, only uint8 input is supported.
		 * 								For radius 1 and 2 the image may also be float32.
		 * @return
		 */
		public static ImageOp median(int radius) {
			return new MedianFilterOp(radius);
		}

		// TODO: Removed until they are tested...
//		/**
//		 * Apply a 2D fast maximum filter.
//		 * This effectively tests for equality between the input {@link Mat} and the result of applying a 
//		 * maximum filter of the specified radius.
//		 * This is not guaranteed to return only maxima (e.g. it can fail at saddle points or constant areas).
//		 * @param radius filter radius. Must be 1 or greater. 1 indicates a 3x3 square; larger filters approximate a circle.
//		 * @return
//		 */
//		public static ImageOp fastMaxima(int radius) {
//			return new FastMaximaOp(radius);
//		}
//
//		/**
//		 * Apply a 2D fast minimum filter.
//		 * This effectively tests for equality between the input {@link Mat} and the result of applying a 
//		 * minimum filter of the specified radius.
//		 * This is not guaranteed to return only minima (e.g. it can fail at saddle points or constant areas).
//		 * @param radius filter radius. Must be 1 or greater. 1 indicates a 3x3 square; larger filters approximate a circle.
//		 * @return
//		 */
//		public static ImageOp fastMinima(int radius) {
//			return new FastMinimaOp(radius);
//		}

		
		@OpType("filter2d")
		static class FilterOp extends PaddedOp {
			
			private Mat kernel;
			
			FilterOp(Mat kernel) {
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
		
		
		@OpType("multiscale")
		static class MultiscaleFeatureOp extends PaddedOp {
			
			private List<MultiscaleFeature> features;
			private double sigmaX, sigmaY;
			private transient MultiscaleResultsBuilder builder;
			
			MultiscaleFeatureOp(Collection<MultiscaleFeature> features, double sigmaX, double sigmaY) {
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
				var channels = OpenCVTools.splitChannels(input);
				for (var mat : channels) {
					var results = builder.build(mat);
					for (var f : features)
						output.add(results.get(f));
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
//					b.paddingXY(padValue());
//					b.pixelCalibration(PixelCalibration.getDefaultInstance(), 1.0);
					builder = b;
				}
				return builder;
			}
			
			
		}
		
		
		@OpType("gaussian")
		static class GaussianFilterOp extends PaddedOp {
			
			private double sigmaX, sigmaY;
			
			GaussianFilterOp(double sigmaX, double sigmaY) {
				this.sigmaX = sigmaX;
				this.sigmaY = sigmaY;
			}
			
			@Override
			public Mat transformPadded(Mat input) {
				if (sigmaX == 0 && sigmaY == 0)
					return input;
				var padding = getPadding();
				var size = new Size(padding.getX1()*2+1, padding.getY1()*2+1);
				OpenCVTools.applyToChannels(input, mat -> opencv_imgproc.GaussianBlur(mat, mat, size, sigmaX, sigmaY, opencv_core.BORDER_REFLECT));
				return input;
			}

			@Override
			protected Padding calculatePadding() {
				return getDefaultGaussianPadding(sigmaX, sigmaY);
			}
			
		}
		
		static abstract class MorphOp extends PaddedOp {
			
			private int radius;
			private transient Mat kernel;
			
			MorphOp(int radius) {
				this.radius = radius;
			}
			
			@Override
			public Mat transformPadded(Mat input) {
				opencv_imgproc.morphologyEx(input, input, getOp(), getKernel());
//				OpenCVTools.applyToChannels(input,
//						mat -> opencv_imgproc.morphologyEx(mat, mat, getOp(), getKernel()));
				return input;
			}
			
			protected abstract int getOp();
			
			private Mat getKernel() {
				if (kernel == null)
					kernel = createDefaultKernel(radius);
				return kernel;
			}

			@Override
			protected Padding calculatePadding() {
				return Padding.symmetric(radius);
			}
			
		}
		
		static Mat createDefaultKernel(int radius) {
			var size = new Size(radius*2+1, radius*2+1);
			if (radius == 1)
				return opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, size);
			else
				return opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, size);
		}
	
		@OpType("median")
		static class MedianFilterOp extends PaddedOp {
			
			private int radius;
			
			MedianFilterOp(int radius) {
				super();
				this.radius = radius;
			}

			@Override
			protected Padding calculatePadding() {
				return Padding.symmetric(radius);
			}

			@Override
			protected Mat transformPadded(Mat input) {
				if (radius > 2 && input.depth() != opencv_core.CV_8U) {
					logger.warn("MedianOp requires uint8 image for radius > 2");
				}
				int c = input.channels();
				if (c == 1 || c == 3 || c == 4)
					opencv_imgproc.medianBlur(input, input, radius*2+1);
				else
					OpenCVTools.applyToChannels(input, m -> opencv_imgproc.medianBlur(m, m, radius*2+1));
				return input;
			}
			
		}
		
		@OpType("maximum")
		static class MaximumFilterOp extends MorphOp {
			
			MaximumFilterOp(int radius) {
				super(radius);
			}

			@Override
			protected int getOp() {
				return opencv_imgproc.MORPH_DILATE;
			}
			
		}
		
		@OpType("minimum")
		static class MinimumFilterOp extends MorphOp {
			
			MinimumFilterOp(int radius) {
				super(radius);
			}

			@Override
			protected int getOp() {
				return opencv_imgproc.MORPH_ERODE;
			}
			
		}
		
		@OpType("morph-open")
		static class MorphOpenFilterOp extends MorphOp {
			
			MorphOpenFilterOp(int radius) {
				super(radius);
			}

			@Override
			protected int getOp() {
				return opencv_imgproc.MORPH_OPEN;
			}
			
		}
		
		@OpType("morph-close")
		static class MorphCloseFilterOp extends MorphOp {
			
			MorphCloseFilterOp(int radius) {
				super(radius);
			}

			@Override
			protected int getOp() {
				return opencv_imgproc.MORPH_CLOSE;
			}
			
		}
		
		@OpType("fast-maxima")
		static class FastMaximaOp extends PaddedOp {
			
			private int radius;
			private transient Mat kernel;
			
			FastMaximaOp(int radius) {
				this.radius = radius;
			}
			
			@Override
			public Mat transformPadded(Mat input) {
				Mat temp = new Mat();
				opencv_imgproc.morphologyEx(input, temp, opencv_imgproc.MORPH_DILATE, getKernel());
				input.put(opencv_core.equals(input, temp));
				temp.release();
				return input;
			}
			
			private Mat getKernel() {
				if (kernel == null)
					kernel = createDefaultKernel(radius);
				return kernel;
			}

			@Override
			protected Padding calculatePadding() {
				return Padding.symmetric(radius);
			}
			
		}
		
		@OpType("fast-minima")
		static class FastMinimaOp extends PaddedOp {
			
			private int radius;
			private transient Mat kernel;
			
			FastMinimaOp(int radius) {
				this.radius = radius;
			}
			
			@Override
			public Mat transformPadded(Mat input) {
				Mat temp = new Mat();
				opencv_imgproc.morphologyEx(input, temp, opencv_imgproc.MORPH_ERODE, getKernel());
				input.put(opencv_core.equals(input, temp));
				temp.release();
				return input;
			}
			
			private Mat getKernel() {
				if (kernel == null)
					kernel = createDefaultKernel(radius);
				return kernel;
			}

			@Override
			protected Padding calculatePadding() {
				return Padding.symmetric(radius);
			}
			
		}
		
	}
	
	/**
	 * Channel and color operations.
	 */
	public static class Channels {
		
		/**
		 * Apply the (fixed) color deconvolution stains to an image.
		 * The input must be a 3-channel image, with values in the range 0-255. 
		 * @param stains
		 * @return
		 */
		public static ImageOp deconvolve(ColorDeconvolutionStains stains) {
			return new ColorDeconvolutionOp(stains);
		}
		
		/**
		 * Extract or rearrange channels by index.
		 * @param channels
		 * @return
		 */
		public static ImageOp extract(int... channels) {
			return new ExtractChannelsOp(channels);
		}
		
		@OpType("color-deconvolution")
		static class ColorDeconvolutionOp implements ImageOp {
			
			private ColorDeconvolutionStains stains;
			private transient Mat matInv;
			
			ColorDeconvolutionOp(ColorDeconvolutionStains stains) {
				this.stains = stains;
			}

			@Override
			public Mat apply(Mat input) {
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
		
		@OpType("extract-channels")
		static class ExtractChannelsOp implements ImageOp {
			
			private int[] channels;
			
			ExtractChannelsOp(int... channels) {
				this.channels = channels.clone();
			}
			
			@Override
			public Mat apply(Mat input) {
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
		
	}
	
	/**
	 * Thresholding operations.
	 */
	public static class Threshold {
		
		/**
		 * Apply a fixed threshold.
		 * @param thresholds either a single-element array (to set the same threshold everywhere), or an array with 
		 *        one element per channel.
		 * @return
		 */
		public static ImageOp threshold(double... thresholds) {
			return new FixedThresholdOp(thresholds);
		}
		
		/**
		 * Threshold each channel based upon the channel mean and standard deviation.
		 * The threshold is {@code mean + k * std.dev.}.
		 * @param k
		 * @return
		 */
		public static ImageOp thresholdMeanStd(double... k) {
			return new  MeanStdDevOp(k);
		}
		
		/**
		 * Threshold each channel based upon the channel median and median absolute deviation.
		 * The threshold is {@code median + k * MAD / 0.6750.}, where the normalizing factor enables 
		 * k to be comparable to a scale factor applied to a standard deviation (assuming a roughly normal distribution).
		 * @param k
		 * @return
		 */
		public static ImageOp thresholdMedianAbsDev(double... k) {
			return new MedianAbsDevThresholdOp(k);
		}
		
		static abstract class AbstractThresholdOp implements ImageOp {
			
			@Override
			public Mat apply(Mat input) {
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
			
			/**
			 * Get or compute a threshold for the single-channel image mat.
			 * @param mat the mat for which a threshold should be calculated
			 * @param channel the original channel number (sometimes required, e.g. if absolute thresholds are stored in an array)
			 * @return the threshold for the specified channel
			 */
			public abstract double getThreshold(Mat mat, int channel);
			
		}
		
		@OpType("constant")
		static class FixedThresholdOp extends AbstractThresholdOp {
			
			private double[] thresholds;
			
			FixedThresholdOp(double... thresholds) {
				this.thresholds = thresholds.clone();
			}
			
			@Override
			public double getThreshold(Mat mat, int channel) {
				return thresholds[Math.min(channel, thresholds.length-1)];
			}
			
		}

		
		/**
		 * Set a threshold as the {@code mean + k * std.dev}.
		 */
		@OpType("mean-std")
		static class MeanStdDevOp extends AbstractThresholdOp {
			
			private double[] k;
			
			MeanStdDevOp(double... k) {
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
		@OpType("median-mad")
		static class MedianAbsDevThresholdOp extends AbstractThresholdOp {
			
			private double[] k;
			
			MedianAbsDevThresholdOp(double... k) {
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
		
	}
	
	/**
	 * Core operations.
	 */
	@OpType("core")
	public static class Core {
		
		/**
		 * Convert the {@link Mat} to match a specified pixel type.
		 * @param type the pixel type that the {@link Mat} should be converted to
		 * @return
		 */
		public static ImageOp ensureType(PixelType type) {
			return new ConvertTypeOp(type);
		}
		
		/**
		 * Multiply all pixels by a constant.
		 * @param values either a single value to apply to all channels, or an array of {@code Mat.channels()} values.
		 * @return
		 */
		public static ImageOp multiply(double... values) {
			return new MultiplyOp(values);
		}
		
		/**
		 * Divide all pixels by a constant.
		 * @param values either a single value to apply to all channels, or an array of {@code Mat.channels()} values.
		 * @return
		 */
		public static ImageOp divide(double... values) {
			return new DivideOp(values);
		}
		
		/**
		 * Add a constant to all pixels.
		 * @param values either a single value to apply to all channels, or an array of {@code Mat.channels()} values.
		 * @return
		 */
		public static ImageOp add(double... values) {
			return new AddOp(values);
		}
		
		/**
		 * Subtract a constant from all pixels.
		 * @param values either a single value to apply to all channels, or an array of {@code Mat.channels()} values.
		 * @return
		 */
		public static ImageOp subtract(double... values) {
			return new SubtractOp(values);
		}
		
		/**
		 * Raise every pixel element to a power.
		 * @param value
		 * @return
		 */
		public static ImageOp power(double value) {
			return new PowerOp(value);
		}
		
		/**
		 * Calculate the square root of all pixel values.
		 * @return
		 */
		public static ImageOp sqrt() {
			return new SqrtOp();
		}
		
		/**
		 * Apply a collection of ops sequentially, chaining the output of one op as the input for the next.
		 * @param ops
		 * @return an op that represents the result of chaining the other ops together
		 */
		public static ImageOp sequential(Collection<? extends ImageOp> ops) {
			if (ops.size() == 1)
				return ops.iterator().next();
			return new SequentialMultiOp(ops);
		}
		
		/**
		 * Apply an array of ops sequentially, chaining the output of one op as the input for the next.
		 * @param ops
		 * @return an op that represents the result of chaining the other ops together
		 */
		public static ImageOp sequential(ImageOp...ops) {
			return sequential(Arrays.asList(ops));
		}
		
		/**
		 * Create an op that applies all the specified ops to the input {@link Mat}, concatenating the results as channels 
		 * of the output.
		 * @param ops
		 * @return a single op that combines all other ops by split and merge
		 */
		public static ImageOp splitMerge(Collection<? extends ImageOp> ops) {
			return new SplitMergeOp(ops.toArray(ImageOp[]::new));
		}
		
		/**
		 * Create an op that applies all the specified ops to the input {@link Mat}, concatenating the results as channels 
		 * of the output.
		 * @param ops
		 * @return a single op that combines all other ops by split and merge
		 */
		public static ImageOp splitMerge(ImageOp...ops) {
			return splitMerge(Arrays.asList(ops));
		}
		
		
		@OpType("convert")
		static class ConvertTypeOp implements ImageOp {

			private PixelType pixelType;
			
			ConvertTypeOp(PixelType pixelType) {
				this.pixelType = pixelType;
			}
			
			@Override
			public Mat apply(Mat input) {
				input.convertTo(input, OpenCVTools.getOpenCVPixelType(pixelType));
				return input;
			}
			
			@Override
			public PixelType getOutputType(PixelType inputType) {
				return pixelType;
			}
			
		}
		
		@OpType("multiply")
		static class MultiplyOp implements ImageOp {

			private double[] values;
			
			MultiplyOp(double... values) {
				this.values = values.clone();
			}
			
			@Override
			public Mat apply(Mat input) {
				if (values.length == 1)
					input.put(opencv_core.multiply(input, values[0]));
				else if (values.length == input.channels()) {
					int i = 0;
					var channels = OpenCVTools.splitChannels(input);
					for (var m : channels) {
						m.put(opencv_core.multiply(m, values[i]));
						i++;
					}
					OpenCVTools.mergeChannels(channels, input);
				} else
					throw new IllegalArgumentException("Multiply requires " + values.length + " channels, but Mat has " + input.channels());
				return input;
			}
			
		}
		
		@OpType("divide")
		static class DivideOp implements ImageOp {

			private double[] values;
			
			DivideOp(double... values) {
				this.values = values.clone();
			}
			
			@Override
			public Mat apply(Mat input) {
				if (values.length == 1)
					input.put(opencv_core.divide(input, values[0]));
				else if (values.length == input.channels()) {
					int i = 0;
					var channels = OpenCVTools.splitChannels(input);
					for (var m : channels) {
						m.put(opencv_core.divide(m, values[i]));
						i++;
					}
					OpenCVTools.mergeChannels(channels, input);
				} else
					throw new IllegalArgumentException("Divide requires " + values.length + " channels, but Mat has " + input.channels());
				return input;
			}
			
		}
		
		@OpType("add")
		static class AddOp implements ImageOp {

			private double[] values;
			
			AddOp(double... values) {
				this.values = values.clone();
			}
			
			@Override
			public Mat apply(Mat input) {
				if (values.length == 1)
					input.put(opencv_core.add(input, Scalar.all(values[0])));
				else if (values.length == input.channels()) {
					int i = 0;
					var channels = OpenCVTools.splitChannels(input);
					for (var m : channels) {
						m.put(opencv_core.add(m, Scalar.all(values[i])));
						i++;
					}
					OpenCVTools.mergeChannels(channels, input);
				} else
					throw new IllegalArgumentException("Add requires " + values.length + " channels, but Mat has " + input.channels());
				return input;
			}
			
		}
		
		@OpType("subtract")
		static class SubtractOp implements ImageOp {

			private double[] values;
			
			SubtractOp(double... values) {
				this.values = values.clone();
			}
			
			@Override
			public Mat apply(Mat input) {
				if (values.length == 1)
					input.put(opencv_core.subtract(input, Scalar.all(values[0])));
				else if (values.length == input.channels()) {
					int i = 0;
					var channels = OpenCVTools.splitChannels(input);
					for (var m : channels) {
						m.put(opencv_core.subtract(m, Scalar.all(values[i])));
						i++;
					}
					OpenCVTools.mergeChannels(channels, input);
				} else
					throw new IllegalArgumentException("Subtract requires " + values.length + " channels, but Mat has " + input.channels());
				return input;
			}
			
		}
		
		@OpType("sqrt")
		static class SqrtOp implements ImageOp {
			
			@Override
			public Mat apply(Mat input) {
				opencv_core.sqrt(input, input);
				return input;
			}
			
		}
		
		@OpType("pow")
		static class PowerOp implements ImageOp {
			
			private double power;
			
			PowerOp(double power) {
				this.power = power;
			}
			
			@Override
			public Mat apply(Mat input) {
				opencv_core.pow(input, power, input);
				return input;
			}
			
		}
		
		
		@OpType("sequential")
		static class SequentialMultiOp extends PaddedOp {
			
			private final static Logger logger = LoggerFactory.getLogger(SequentialMultiOp.class);
			
			private List<ImageOp> ops;
			
			SequentialMultiOp(Collection<? extends ImageOp> ops) {
				this.ops = new ArrayList<>(ops);
			}

			@Override
			protected Padding calculatePadding() {
				 var padding = Padding.empty();
				for (var t : ops)
					padding = padding.add(t.getPadding());
				return padding;
			}

			@Override
			public Mat apply(Mat input) {
				for (var t : ops)
					input = t.apply(input);
				return input;
			}
			
			/**
			 * Should not be called!
			 */
			@Override
			protected Mat transformPadded(Mat input) {
				logger.warn("transformPadded(Mat) should not be called directly for this class!");
				return apply(input);
			}
			
			@Override
			public List<ImageChannel> getChannels(List<ImageChannel> channels) {
				for (var t : ops)
					channels = t.getChannels(channels);
				return channels;
			}
			
			@Override
			public PixelType getOutputType(PixelType inputType) {
				for (var t : ops)
					inputType = t.getOutputType(inputType);
				return inputType;
			}
			
		}
		
		
		/**
		 * Duplicate the input {@link Mat} and apply different ops to the duplicates, 
		 * merging the result at the end.
		 */
		@OpType("split-merge")
		static class SplitMergeOp extends PaddedOp {
			
			private List<ImageOp> ops;
			
			SplitMergeOp(ImageOp...ops) {
				this.ops = new ArrayList<>();
				for (var t : ops) {
					this.ops.add(t);
				}
			}

			@Override
			public Mat apply(Mat input) {
				return transformPadded(input);
			}
			
			@Override
			public List<ImageChannel> getChannels(List<ImageChannel> channels) {
				return ops.stream()
						.flatMap(t -> t.getChannels(channels).stream())
						.collect(Collectors.toList());
			}

			@Override
			protected Padding calculatePadding() {
				var padding = Padding.empty();
				for (var t : ops)
					padding = padding.max(t.getPadding());
				return padding;
			}

			@Override
			protected Mat transformPadded(Mat input) {
				if (ops.isEmpty())
					return new Mat();
				if (ops.size() == 1)
					return ops.get(0).apply(input);
				var mats = new ArrayList<Mat>();
				for (var op : ops) {
					var temp = input.clone();
					var result = op.apply(temp);
					mats.add(result);
					if (result != temp)
						temp.close();
				}
				// Remember we padded all branches the same - but some may have needed more or less than others
				var padding = getPadding();
				for (int i = 0; i < ops.size(); i++) {
					var t = ops.get(i);
					var padExtra = padding.subtract(t.getPadding());
					if (!padExtra.isEmpty())
						mats.get(i).put(stripPadding(mats.get(i), padExtra));
				}
				return OpenCVTools.mergeChannels(mats, null);
			}
			
			@Override
			public PixelType getOutputType(PixelType inputType) {
				// TODO: Handle inconsistent types!
				for (var t : ops)
					inputType = t.getOutputType(inputType);
				return inputType;
			}

		}
		
	}
	
	/**
	 * Machine learning operations.
	 */
	public static class ML {
		
		/**
		 * Apply a {@link StatModel} to pixels to generate a prediction.
		 * @param statModel
		 * @param requestProbabilities
		 * @return
		 */
		public static ImageOp statModel(OpenCVStatModel statModel, boolean requestProbabilities) {
			return new StatModelOp(statModel, requestProbabilities);
		}
		
		/**
		 * Apply a {@link OpenCVDNN} to pixels to generate a prediction.
		 * @param dnn 
		 * @param inputWidth 
		 * @param inputHeight 
		 * @param padding 
		 * @return
		 */
		public static ImageOp dnn(OpenCVDNN dnn, int inputWidth, int inputHeight, Padding padding) {
			return new DnnOp(dnn, inputWidth, inputHeight, padding, false);
		}
		
		/**
		 * Apply a {@link FeaturePreprocessor} to pixels, considering each channel as features.
		 * @param preprocessor
		 * @return
		 */
		public static ImageOp preprocessor(FeaturePreprocessor preprocessor) {
			return new FeaturePreprocessorOp(preprocessor);
		}
		
		@OpType("feature-preprocessor")
		static class FeaturePreprocessorOp implements ImageOp {
			
			private FeaturePreprocessor preprocessor;
			
			private FeaturePreprocessorOp(FeaturePreprocessor preprocessor) {
				this.preprocessor = preprocessor;
			}

			@Override
			public Mat apply(Mat input) {
				input.convertTo(input, opencv_core.CV_32F);
				preprocessor.apply(input, true);
				return input;
			}
			
			@Override
			public List<ImageChannel> getChannels(List<ImageChannel> channels) {
				if (!preprocessor.doesFeatureTransform())
					return channels;
				// TODO: Preserve channel names if now applying PCA
				return IntStream.range(0, preprocessor.getOutputLength())
						.mapToObj(i -> ImageChannel.getInstance("Feature " + i, ColorTools.WHITE))
						.collect(Collectors.toList());
			}
			
			@Override
			public PixelType getOutputType(PixelType inputType) {
				return PixelType.FLOAT32;
			}
			
		}
		
		@OpType("opencv-dnn")
		static class DnnOp extends PaddedOp {

			private OpenCVDNN model;
			private int inputWidth;
			private int inputHeight;
			
			private boolean doParallel;
			
			private Padding padding;
			
			private transient Net net;
			private transient ThreadLocal<Net> localNet = ThreadLocal.withInitial(() -> readNet());
			private transient Exception exception;
			
			/**
			 * A DNN op.
			 * @param model
			 * @param inputWidth
			 * @param inputHeight
			 * @param padding
			 * @param doParallel if true, load the Net for each thread so it may be applied in parallel. 
			 *                   This is not a good idea if the net is 'heavyweight'.
			 */
			DnnOp(OpenCVDNN model, int inputWidth, int inputHeight, Padding padding, boolean doParallel) {
				this.model = model;
				this.inputWidth = inputWidth;
				this.inputHeight = inputHeight;
				this.padding = padding;
				this.doParallel = doParallel;
			}

			@Override
			protected Padding calculatePadding() {
				return padding;
			}
			
			/**
			 * Try to read the {@link Net}, setting the exception if this fails
			 * @return
			 */
			private Net readNet() {
				if (exception == null) {
					try {
						return model.getNet();
					} catch (IOException e) {
						exception = e;
					}
				}
				return null;
			}
			
			private Net getNet() {
				if (doParallel)
					return localNet.get();
				if (net == null)
					net = readNet();
				return net;
			}

			@Override
			protected Mat transformPadded(Mat input) {
				Net net = getNet();
				if (exception == null) {
					if ((inputWidth <= 0 && inputHeight <= 0) || (input.cols() == inputWidth && input.rows() == inputHeight))
						return doClassification(input, net);
					else
						return OpenCVTools.applyTiled(m -> doClassification(m, net), input, inputWidth, inputHeight, opencv_core.BORDER_REFLECT);
				}
				throw new RuntimeException(exception);
			}
			
			@Override
			public PixelType getOutputType(PixelType inputType) {
				return PixelType.FLOAT32;
			}

		}
		
		@OpType("opencv-statmodel")
		static class StatModelOp implements ImageOp {

			private OpenCVStatModel model;
			private boolean requestProbabilities;
			
			StatModelOp(OpenCVStatModel model, boolean requestProbabilities) {
				this.model = model;
				this.requestProbabilities = requestProbabilities;
			}
			
			@Override
			public Mat apply(Mat input) {
				int w = input.cols();
				int h = input.rows();
				input.put(input.reshape(1, w * h));
				var matResult = new Mat();
				if (requestProbabilities) {
					var temp = new Mat();
					model.predict(input, temp, matResult);
					temp.release();
				} else
					model.predict(input, matResult, null);
				input.put(matResult.reshape(matResult.cols(), h));
				matResult.close();
				return input;
			}
			
			@Override
			public PixelType getOutputType(PixelType inputType) {
				return PixelType.FLOAT32;
			}

		}
		
	}
	
	
	private static Mat doClassification(Mat mat, Net net) {
    	// Currently we require 32-bit input
    	mat.convertTo(mat, opencv_core.CV_32F);
    	
        // Net appears not to support multithreading, so we need to synchronize.
        // We also need to extract the results we need at this point while still within the synchronized block,
    	// since it appears that the result of calling model.forward() can become invalid later.
    	Mat matResult = null;
    	Mat blob = null;
    	if (mat.channels() == 3) {
    		blob = opencv_dnn.blobFromImage(mat);
    	} else {
    		// TODO: Don't have any net to test this with currently...
    		logger.warn("Attempting to reshape an image with channels != 3 - this may not work!");
    		int[] shape = new int[mat.dims() + 1];
    		shape[0] = 1;
    		for (int s = 1; s < shape.length; s++) {
    			shape[1] = mat.size(s-1);
    		}
    		if (mat.isContinuous())
    			blob = new Mat(shape, opencv_core.CV_32F, mat);
    		else
        		blob = new Mat(shape, opencv_core.CV_32F, mat.clone());
    	}
    	synchronized(net) {
    		long startTime = System.currentTimeMillis();
    		net.setInput(blob);
    		try {
    			Mat prob = net.forward();
    			MatVector matvec = new MatVector();
    			opencv_dnn.imagesFromBlob(prob, matvec);
    			if (matvec.size() != 1)
    				throw new IllegalArgumentException("DNN result must be a single image - here, the result is " + matvec.size() + " images");
    			// Get the first result & clone it - otherwise can have threading woes
    			matResult = matvec.get(0L).clone();
    			matvec.close();
    		} catch (Exception e2) {
    			logger.error("Error applying classifier", e2);
    		}
    		long endTime = System.currentTimeMillis();
    		logger.trace("Classification time: {} ms", endTime - startTime);
    	}
        
        return matResult;
    }
	
	
	
	/**
     * Rescale the rows of matResult so that they sum to maxValue.
     * <p>
     * If matProbabilities has an integer type, then maxValue should normally reflect the largest supported value 
     * (e.g. 255 for CV_8U).  In this case it is not guaranteed that values will sum exactly to the desired maxValue 
     * due to rounding (e.g. consider a row with values [255.0/2, 255.0/2] or [255.0/3, 255.0/3, 255.0/3].
     * 
     * @param matRawInput input values; each row corresponds to a sample and each column the raw estimate for a particular class
     * @param matProbabilities output mat; may be the same as matRawInput.
     * @param maxValue the maximum value; this would normally be 1.0 for floating point output, or 255.0 for 8-bit output
     * @param doSoftmax if true, {@code Math.exp(value)} will be calculated for each value in matRawInput.
     */
    static void rescaleChannelsToProbabilities(Mat matRawInput, Mat matProbabilities, double maxValue, boolean doSoftmax) {
    	if (matProbabilities == null)
    		matProbabilities = new Mat();
    	
    	if (matRawInput != matProbabilities && matRawInput.rows() != matProbabilities.rows() && matRawInput.cols() != matProbabilities.cols()) {
    		if (matProbabilities.empty())
    			matProbabilities.create(matRawInput.rows(), matRawInput.cols(), matRawInput.type());    		
    		else
    			matProbabilities.create(matRawInput.rows(), matRawInput.cols(), matProbabilities.type());    		
    	}
    	
    	int warnNegativeValues = 0;
        var idxInput = matRawInput.createIndexer();
        var idxOutput = matProbabilities.createIndexer();
        long[] inds = new long[2];
		long rows = idxInput.size(0); // previously .rows()
		long cols = idxOutput.size(1); // previously .cols()
        double[] vals = new double[(int)cols];
        for (long r = 0; r < rows; r++) {
        	inds[0] = r;
        	double sum = 0;
        	for (int k = 0; k < cols; k++) {
            	inds[1] = k;
            	double val = idxInput.getDouble(inds);
            	if (doSoftmax) {
            		val = Math.exp(val);
            	} else if (val < 0) {
            		val = 0;
            		warnNegativeValues++;
            	}
            	vals[k] = val;
            	sum += val;
        	}
        	
        	for (int k = 0; k < cols; k++) {
        		inds[1] = k;
        		idxOutput.putDouble(inds, vals[k] * (maxValue / sum));
        	}
        	// Consider if the output should be integer, could set the highest probability to be 1 - the maximum
        	// The aim is to avoid rounding errors to result in the sum not adding up to what is expected 
        	// (e.g. 255/3 + 255/3 + 255/3).
        	// But as this example shows, it can result in a different interpretation of the results...
        }
        
        if (warnNegativeValues > 0) {
        	long total = rows * cols;
        	logger.warn(
        			String.format("Negative raw 'probability' values detected (%d/%d, %.1f%%) - " +
        					" - these will be clipped to 0.  Should softmax be being used...?", warnNegativeValues, total, warnNegativeValues*(100.0/total)));
        }
    }
	
	/**
	 * Abstract {@link ImageOp} to simplify the process of handling padding.
	 */
	public static abstract class PaddedOp implements ImageOp {
		
		private transient Padding padding;
		
		/**
		 * Calculate the required padding.
		 * @return
		 */
		protected abstract Padding calculatePadding();
		
		/**
		 * Transform, but ignoring padding.
		 * Non-empty padding will be removed automatically elsewhere.
		 * @param input
		 * @return
		 */
		protected abstract Mat transformPadded(Mat input);

		@Override
		public Mat apply(Mat input) {
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
	
	
	
	static Padding getDefaultGaussianPadding(double sigmaX, double sigmaY) {
		int padX = (int)Math.ceil(sigmaX * 3) + 1;
		int padY = (int)Math.ceil(sigmaY * 3) + 1;
		return Padding.getPadding(padX, padX, padY, padY);
	}
	
	
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
	

}