package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.geom.ImmutableDimension;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.tools.MultiscaleFeatures;
import qupath.opencv.tools.LocalNormalization;
import qupath.opencv.tools.LocalNormalization.LocalNormalizationType;
import qupath.opencv.tools.LocalNormalization.SmoothingScale;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleResultsBuilder;
import qupath.opencv.tools.OpenCVTools;

@JsonAdapter(FeatureCalculators.FeatureCalculatorTypeAdapterFactory.class)
public class MultiscaleFeatureCalculator implements FeatureCalculator<BufferedImage> {
	
	private LocalNormalizationType localNormalization;
	private List<TransformedFeatureComputer> multiscaleComputers;
	private ImmutableDimension size = ImmutableDimension.getInstance(512, 512);
	
	MultiscaleFeatureCalculator(ImmutableDimension size, Collection<TransformedFeatureComputer> featureComputers, LocalNormalizationType localNormalization) {
		this.size = size;
		this.localNormalization = localNormalization;
		this.multiscaleComputers = Collections.unmodifiableList(new ArrayList<>(featureComputers));
	}
	
	@Override
	public boolean supportsImage(ImageData<BufferedImage> imageData) {
		for (var computer : multiscaleComputers) {
			if (!computer.transform.supportsImage(imageData.getServer())) {
				return false;
			}
		}
		return true;
	}
	
	public static class Builder {
		
		private List<TransformedFeatureComputer> featureComputers = new ArrayList<>();
		private ImmutableDimension size = ImmutableDimension.getInstance(512, 512);
		
		private LocalNormalizationType localNormalization;
		
		/**
		 * Preferred input/output tile size when calculating features.
		 * @param width
		 * @param height
		 * @return
		 */
		public Builder size(int width, int height) {
			this.size = ImmutableDimension.getInstance(width, height);
			return this;
		}
		
		public Builder localNormalization(SmoothingScale scale, double varianceScaleRatio) {
			if (scale == null)
				return localNormalization(null);
			else
				return localNormalization(LocalNormalizationType.getInstance(scale, varianceScaleRatio));
		}
		
		public Builder localNormalization(LocalNormalizationType normalization) {
			this.localNormalization = normalization;
			return this;
		}
		
		public Builder addFeatures(TransformedFeatureComputer... features) {
			return addFeatures(Arrays.asList(features));
		}
		
		public Builder addFeatures(Collection<TransformedFeatureComputer> features) {
			this.featureComputers.addAll(features);
			return this;
		}
		
		public MultiscaleFeatureCalculator build() {
			return new MultiscaleFeatureCalculator(size, featureComputers, localNormalization);
		}
		
		
	}
	
	
	@Override
	public List<PixelFeature> calculateFeatures(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
		
		List<PixelFeature> features = new ArrayList<>();

		ImageServer<BufferedImage> server = imageData.getServer();
		
		// Determine how much smoothing we need in each dimension
		double maxSigmaXY = 0;
		double maxSigmaZ = 0;
		var cal = imageData.getServer().getPixelCalibration();
		for (TransformedFeatureComputer computer : multiscaleComputers) {
			maxSigmaXY = Math.max(maxSigmaXY, computer.getMaxSigmaXY());
			maxSigmaZ = Math.max(maxSigmaZ, computer.getMaxSigmaZ(cal, request.getDownsample()));
		}
		
		// Calculate a suitable amount of padding
		var localNormalizeScale = localNormalization == null ? null : localNormalization.scale;
		boolean doLocalNormalize = localNormalizeScale != null && localNormalizeScale.getSigma() > 0;
		if (doLocalNormalize)
			maxSigmaXY = Math.sqrt(localNormalizeScale.getSigma()*localNormalizeScale.getSigma() + maxSigmaXY*maxSigmaXY);
		int padding = (int)Math.ceil(maxSigmaXY) * 8;
		
		// Request all the images we need - either a single plane of a z-stack
		List<BufferedImage> images = new ArrayList<>();
		int ind = 0;
		if (maxSigmaZ > 0) {
			for (int z = 0; z < server.nZSlices(); z++) {
				RegionRequest request2 = RegionRequest.createInstance(
						request.getPath(),
						request.getDownsample(),
						request.getX(),
						request.getY(),
						request.getWidth(),
						request.getHeight(),
						z,
						request.getT());
				BufferedImage img = PixelClassifierTools.getPaddedRequest(server, request2, padding);
				images.add(img);
			}
			ind = request.getZ();
		} else {
			BufferedImage img = PixelClassifierTools.getPaddedRequest(server, request, padding);
			images.add(img);
		}
		
		// Get the required dimensions
		int width = images.get(ind).getWidth();
		int height = images.get(ind).getHeight();
		float[] pixels = new float[width * height];
		
		// Preallocate a Mat to hold the transformed pixels
		List<Mat> mats = new ArrayList<>();
		for (int i = 0; i < images.size(); i++) {
			mats.add(new Mat(height, width, opencv_core.CV_32FC1));
		}
		
		// Loop through all the transforms
		for (TransformedFeatureComputer computer : multiscaleComputers) {
			
			// Extract transformed pixels & set to a Mat
			int i = 0;
			for (BufferedImage img : images) {
				computer.transform.extractChannel(imageData.getServer(), img, pixels);
				Mat mat = mats.get(i);
				FloatIndexer idx = mat.createIndexer();
				idx.put(0L, pixels);
				idx.release();
				i++;
			}
			
			if (doLocalNormalize) {
				double downsample = request.getDownsample();
				var calScaled = cal.createScaledInstance(downsample, downsample, 1.0);
				LocalNormalization.gaussianNormalize(mats, localNormalization, calScaled, opencv_core.BORDER_REPLICATE);
			}
			
			// Calculate all the features for the specified transform
//			if (computer.features.isEmpty()) {
//				var matTemp = mats.get(ind);
//				if (padding > 0)
//					matTemp = matTemp.apply(new Rect(padding, padding, matTemp.cols()-padding*2, matTemp.rows()-padding*2)).clone();
//				float[] tempPixels = OpenCVTools.extractPixels(matTemp, null);
//				features.add(new DefaultPixelFeature<>(computer.transform.getName(), tempPixels, matTemp.cols(), matTemp.rows()));
//				if (padding > 0)
//					matTemp.close();
//			} else {
				for (var temp : computer.features) {
					List<Mat> mats2 = mats;
					var mat = mats2.get(ind);
					features.addAll(temp.calculateFeatures(cal, request.getDownsample(), computer.transform.getName(), mat, padding, mats2.toArray(Mat[]::new)));
				}
//			}
		}
		for (var mat : mats)
			mat.release();
		
		return features;
		
	}
	
	
	static double getSigmaZ(SmoothingScale scale, PixelCalibration cal, double downsample) {
		if (downsample == 1)
			return scale.getSigmaZ(cal);
		return scale.getSigmaZ(cal.createScaledInstance(downsample, downsample, 1.0));
	}
	
	
	@Override
	public ImmutableDimension getInputSize() {
		return size;
	}
	
	
	public static class TransformedFeatureComputer {
		
		private static Logger logger = LoggerFactory.getLogger(TransformedFeatureComputer.class);
				
		private ColorTransform transform;
		
		private List<MultiscaleFeatureComputer> features;
		
		private TransformedFeatureComputer(ColorTransform transform, Collection<MultiscaleFeatureComputer> features) {
			this.transform = transform;
			this.features = Collections.unmodifiableList(new ArrayList<>(features));
		}
		
		public static class Builder {
			
			private ColorTransform transform;
			private Collection<MultiscaleFeatureComputer> features = new ArrayList<>();
			
			Builder(ColorTransform transform) {
				this.transform = transform;
			}
			
			/**
			 * Add an identify feature, which applies no smoothing and returns the input pixels.
			 * This is identified as being a Gaussian-smoothed feature with sigma of 0.
			 * @return
			 */
			public Builder addIdentityFeature() {
				return addFeatures(SmoothingScale.get2D(0.0), MultiscaleFeature.GAUSSIAN);
			}
			
			/**
			 * Add features calculated at a specific scale.
			 * 
			 * @param scale
			 * @param features
			 * @return
			 */
			public Builder addFeatures(SmoothingScale scale, MultiscaleFeature... features) {
				if (features.length == 0)
					return this;
				Objects.requireNonNull(scale);
				this.features.add(new MultiscaleFeatureComputer(scale, features));
				return this;
			}
			
			public TransformedFeatureComputer build() {
				return new TransformedFeatureComputer(transform, features);
				
			}
			
		}
		
		/**
		 * Returns the larger sigma value for the z dimension.
		 * @return
		 */
		public double getMaxSigmaZ(PixelCalibration cal, double downsample) {
			double sigma = 0;
			for (MultiscaleFeatureComputer feature : features) {
				if (feature == null || feature.scale == null)
					continue;
				double temp = getSigmaZ(feature.scale, cal, downsample);
				if (temp > sigma)
					sigma = temp;
			}
			return sigma;
		}
		
		/**
		 * Returns the larger sigma value for the x or y dimension (if different).
		 * @return
		 */
		public double getMaxSigmaXY() {
			double sigma = 0;
			for (MultiscaleFeatureComputer feature : features) {
				if (feature == null || feature.scale == null)
					continue;
				double temp = feature.scale.getSigma();
				if (temp > sigma)
					sigma = temp;
			}
			return sigma;
		}
		
	}
	
	
	private static class MultiscaleFeatureComputer {
		
		private static Logger logger = LoggerFactory.getLogger(MultiscaleFeatureComputer.class);
		
		private SmoothingScale scale;
		
		private Collection<MultiscaleFeatures.MultiscaleFeature> features;
		
		MultiscaleFeatureComputer(SmoothingScale scale, MultiscaleFeature... features) {
			this.scale = scale;
			this.features = Arrays.asList(features);
		}
		
		public List<PixelFeature> calculateFeatures(PixelCalibration cal, double downsample, String baseName, Mat mat, int paddingXY, Mat... stack) {
			List<Mat> mats = stack.length == 0 ? Collections.singletonList(mat) : Arrays.asList(stack);
			int ind = mats.indexOf(mat);
			
			double sigmaXY = scale.getSigma();
			double sigmaZ = getSigmaZ(scale, cal, downsample);
			
			Map<MultiscaleFeature, Mat> map = new MultiscaleResultsBuilder()
				.sigmaX(sigmaXY)
				.sigmaY(sigmaXY)
				.sigmaZ(sigmaZ)
				.paddingXY(paddingXY)
				.gaussianSmoothed(features.contains(MultiscaleFeatures.MultiscaleFeature.GAUSSIAN))
				.weightedStdDev(features.contains(MultiscaleFeatures.MultiscaleFeature.WEIGHTED_STD_DEV))
				.gradientMagnitude(features.contains(MultiscaleFeatures.MultiscaleFeature.GRADIENT_MAGNITUDE))
				.laplacianOfGaussian(features.contains(MultiscaleFeatures.MultiscaleFeature.LAPLACIAN))
				.hessianDeterminant(features.contains(MultiscaleFeatures.MultiscaleFeature.HESSIAN_DETERMINANT))
				.structureTensorEigenvalues(
						features.contains(MultiscaleFeatures.MultiscaleFeature.STRUCTURE_TENSOR_COHERENCE) ||
						features.contains(MultiscaleFeatures.MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MAX) ||
						features.contains(MultiscaleFeatures.MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MIDDLE) ||
						features.contains(MultiscaleFeatures.MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MIN)
				)
				.hessianEigenvalues(
						features.contains(MultiscaleFeatures.MultiscaleFeature.HESSIAN_EIGENVALUE_MIN) ||
						features.contains(MultiscaleFeatures.MultiscaleFeature.HESSIAN_EIGENVALUE_MIDDLE) ||
						features.contains(MultiscaleFeatures.MultiscaleFeature.HESSIAN_EIGENVALUE_MAX))
				.build(mats, ind);
			
			String sigmaString = scale.toString();
			
			List<PixelFeature> output = new ArrayList<>();
			for (MultiscaleFeature feature : features) {
				String name = feature.toString() + " " + sigmaString;
				if (baseName != null)
					name = baseName + " " + name;
				Mat matFeature = map.get(feature);
				if (matFeature == null)
					logger.debug("No feature for {}", feature);
				else {
					output.add(new DefaultPixelFeature<>(name, OpenCVTools.matToSimpleImage(matFeature, 0)));
					matFeature.release();
				}
			}
			return output;
		}
		
	}
	
//	static String sigmaString(PixelCalibration cal) {
//		String sigmaString = "\u03C3: %s %s";
//		
//		String xUnits = cal.getPixelWidthUnit();
//		String yUnits = cal.getPixelHeightUnit();
//		String zUnits = cal.getZSpacingUnit();
//		double sigmaX = cal.getPixelWidth().doubleValue();
//		double sigmaY = cal.getPixelHeight().doubleValue();
//		double sigmaXY = cal.getAveragedPixelSize().doubleValue();
//		double sigmaZ = cal.getZSpacing().doubleValue();
//		if (!Double.isFinite(sigmaZ))
//			sigmaZ = 0;
//		
//		double tol = 0.001;
//		// Check if everything is isotropic, and if so just return that value
//		if (xUnits.equals(yUnits) && GeneralTools.almostTheSame(sigmaX, sigmaXY, tol) &&
//				GeneralTools.almostTheSame(sigmaY, sigmaXY, tol) &&
//				(sigmaZ <= 0 || (GeneralTools.almostTheSame(sigmaZ, sigmaXY, tol)) && zUnits.equals(xUnits)))
//			sigmaString = String.format(sigmaString, GeneralTools.formatNumber(sigmaXY, 2), xUnits);
//		else {
//			String temp = "x=" + GeneralTools.formatNumber(sigmaX, 2) + " " + xUnits +
//					    ", y=" + GeneralTools.formatNumber(sigmaY, 2) + " " + yUnits;
//			if (sigmaZ > 0)
//				temp += ", z=" + GeneralTools.formatNumber(sigmaZ, 2) + " " + zUnits;
//			sigmaString = String.format("\u03C3: %s", temp);
//		}
//		return sigmaString;
//	}
	

}
