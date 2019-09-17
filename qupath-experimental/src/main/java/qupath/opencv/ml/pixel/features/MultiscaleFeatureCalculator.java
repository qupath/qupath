package qupath.opencv.ml.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.pixel.features.ColorTransforms.ColorTransform;
import qupath.opencv.tools.MultiscaleFeatures;
import qupath.opencv.tools.LocalNormalization;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleResultsBuilder;
import qupath.opencv.tools.OpenCVTools;

class MultiscaleFeatureCalculator implements FeatureCalculator<BufferedImage> {
	
	private double localNormalizeSigma;
	
	private List<TransformedFeatureComputer> multiscaleComputers = new ArrayList<>();
	
	private ImmutableDimension size = ImmutableDimension.getInstance(512, 512);
	
	MultiscaleFeatureCalculator(PixelCalibration cal, int[] channels, double[] sigmaValues, double localNormalizeSigma, boolean do3D, MultiscaleFeature... features) {
		
		this.localNormalizeSigma = localNormalizeSigma;
		
		for (int c : channels) {
			TransformedFeatureComputer computer = new TransformedFeatureComputer();
			computer.transform = ColorTransforms.createChannelExtractor(c);
			String baseName = computer.transform.getName();
			for (double sigma : sigmaValues) {
				GaussianScale scale;
				if (do3D) {
					double sigmaZ = sigma / cal.getZSpacingMicrons() * cal.getAveragedPixelSizeMicrons();
					scale = GaussianScale.create(sigma, sigma, sigmaZ);
				} else {
					scale = GaussianScale.create(sigma, sigma, 0);
				}
				computer.features.add(new MultiscaleFeatureComputer(baseName, scale, features));				
			}
			multiscaleComputers.add(computer);
		}
	}
	

	@Override
	public List<PixelFeature> calculateFeatures(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {
		
		List<PixelFeature> features = new ArrayList<>();

		ImageServer<BufferedImage> server = imageData.getServer();
		
		// Determine how much smoothing we need in each dimension
		double maxSigmaXY = 0;
		double maxSigmaZ = 0;
		for (TransformedFeatureComputer computer : multiscaleComputers) {
			maxSigmaXY = Math.max(maxSigmaXY, computer.getMaxSigmaXY());
			maxSigmaZ = Math.max(maxSigmaZ, computer.getMaxSigmaZ());
		}
		
		// Calculate a suitable amount of padding
		if (localNormalizeSigma > 0)
			maxSigmaXY = Math.sqrt(localNormalizeSigma*localNormalizeSigma + maxSigmaXY*maxSigmaXY);
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
				computer.transform.extractChannel(imageData, img, pixels);
				Mat mat = mats.get(i);
				FloatIndexer idx = mat.createIndexer();
				idx.put(0L, pixels);
				idx.release();
				i++;
			}
			
			if (localNormalizeSigma > 0) {
				double downsample = request.getDownsample();
				PixelCalibration cal = server.getPixelCalibration();
				double pixelSize = cal.hasPixelSizeMicrons() ? cal.getAveragedPixelSizeMicrons() * downsample : downsample;
				double zSpacing = cal.getZSpacingMicrons();
				if (!Double.isFinite(zSpacing))
					zSpacing = 1.0;
				double sigmaX = localNormalizeSigma;
				double sigmaY = localNormalizeSigma;
				double sigmaZ = computer.getMaxSigmaZ() > 0 ? localNormalizeSigma / zSpacing * pixelSize : 0;
				if (sigmaZ > 0)
					LocalNormalization.gaussianNormalize3D(mats, sigmaX, sigmaY, sigmaZ, opencv_core.BORDER_REPLICATE);
				else
					LocalNormalization.gaussianNormalize2D(mats, sigmaX, sigmaY, opencv_core.BORDER_REPLICATE);
			}
			
			// Calculate all the features for the specified transform
			for (var temp : computer.features) {
				List<Mat> mats2 = mats;
				features.addAll(temp.calculateFeatures(mats2.get(ind), padding, mats2.toArray(Mat[]::new)));
			}
		}
		
		return features;
		
	}
	
	
	@Override
	public ImmutableDimension getInputSize() {
		return size;
	}
	
	
	static class TransformedFeatureComputer {
				
		private ColorTransform transform;
		
		private Collection<MultiscaleFeatureComputer> features = new ArrayList<>();
		
		
		/**
		 * Returns true if any feature calculations have a non-zero sigma value along the z-dimension.
		 * @return
		 */
		public boolean is3D() {
			for (MultiscaleFeatureComputer feature : features)
				if (feature.scale.sigmaZ > 0)
					return true;
			return false;
		}
		
		/**
		 * Returns the larger sigma value for the z dimension.
		 * @return
		 */
		public double getMaxSigmaZ() {
			double sigma = 0;
			for (MultiscaleFeatureComputer feature : features) {
				if (feature.scale.sigmaZ > sigma)
					sigma = feature.scale.sigmaZ;
			}
			return sigma;
		}
		
		/**
		 * Returns the larger sigma value for the x or y dimension.
		 * @return
		 */
		public double getMaxSigmaXY() {
			double sigma = 0;
			for (MultiscaleFeatureComputer feature : features) {
				if (feature.scale.sigmaX > sigma)
					sigma = feature.scale.sigmaX;
				if (feature.scale.sigmaY > sigma)
					sigma = feature.scale.sigmaY;
			}
			return sigma;
		}
		
		
	}
	
	
	static class MultiscaleFeatureComputer {
		
		private static Logger logger = LoggerFactory.getLogger(MultiscaleFeatureComputer.class);
		
		private String baseName;
		private GaussianScale scale;
		
		private Collection<MultiscaleFeatures.MultiscaleFeature> features;
		
		MultiscaleFeatureComputer(String baseName, GaussianScale scale, MultiscaleFeature... features) {
			this.scale = scale;
			this.baseName = baseName;
			this.features = Arrays.asList(features);
		}
		
		public List<PixelFeature> calculateFeatures(Mat mat, int paddingXY, Mat... stack) {
			List<Mat> mats = stack.length == 0 ? Collections.singletonList(mat) : Arrays.asList(stack);
			int ind = mats.indexOf(mat);
			
			Map<MultiscaleFeature, Mat> map = new MultiscaleResultsBuilder()
				.sigmaX(scale.getSigmaX())
				.sigmaY(scale.getSigmaY())
				.sigmaZ(scale.getSigmaZ())
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
			
			String sigmaString = scale.sigmaString();
			
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
	
	
	/**
	 * Scale for multiresolution analysis. Encodes Gaussian sigma values for x, y and z.
	 */
	public static class GaussianScale {
		
		private final double sigmaX;
		private final double sigmaY;
		private final double sigmaZ;
		
		/**
		 * Create a GaussianScale with all dimensions specified.
		 * @param sigmaX
		 * @param sigmaY
		 * @param sigmaZ
		 */
		GaussianScale(double sigmaX, double sigmaY, double sigmaZ) {
			this.sigmaX = sigmaX;
			this.sigmaY = sigmaY;
			this.sigmaZ = sigmaZ;
		}

		/**
		 * Create a 2D GaussianScale, sigmaZ is 0.
		 * @param sigmaX
		 * @param sigmaY
		 */
		public static GaussianScale create(double sigmaX, double sigmaY) {
			return create(sigmaX, sigmaY, 0);
		}

		/**
		 * Create a GaussianScale with all dimensions specified.
		 * @param sigmaX
		 * @param sigmaY
		 * @param sigmaZ
		 */
		public static GaussianScale create(double sigmaX, double sigmaY, double sigmaZ) {
			return new GaussianScale(sigmaX, sigmaY, sigmaZ);
		}
		
		/**
		 * Create a new {@link GaussianScale} by multiplying the x, y and z values by a spacified scale factors.
		 * @param scale
		 * @param scaleX
		 * @param scaleY
		 * @param scaleZ
		 * @return
		 */
		public static GaussianScale createScaledInstance(GaussianScale scale, double scaleX, double scaleY, double scaleZ) {
			return create(scale.getSigmaX() * scaleX, scale.getSigmaY() * scaleY, scale.getSigmaZ() * scaleZ);
		}
		
		/**
		 * Get the x (horizontal) sigma value.
		 * @return
		 */
		public double getSigmaX() {
			return sigmaX;
		}

		/**
		 * Get the y (vertical) sigma value.
		 * @return
		 */
		public double getSigmaY() {
			return sigmaY;
		}

		/**
		 * Get the sigma value along the z-dimension (may be zero for 2D).
		 * @return
		 */
		public double getSigmaZ() {
			return sigmaZ;
		}
		
		String sigmaString() {
			return "(" + toString() + ")";
		}
		
		@Override
		public String toString() {
			String sigmaString = "\u03C3: %s";
			if (sigmaX == sigmaY && (sigmaX == sigmaZ || sigmaZ <= 0))
				sigmaString = String.format(sigmaString, GeneralTools.formatNumber(sigmaX, 2));
			else {
				String temp = "x="+GeneralTools.formatNumber(sigmaX, 2) + ", y=" + GeneralTools.formatNumber(sigmaY, 2);
				if (sigmaZ > 0)
					temp += ", z=" + GeneralTools.formatNumber(sigmaZ, 2);
				sigmaString = String.format(sigmaString, temp);
			}
			return sigmaString;
		}

	}
	

}
