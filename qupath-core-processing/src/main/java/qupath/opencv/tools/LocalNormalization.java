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

package qupath.opencv.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.PixelCalibration;

/**
 * Methods to normalize the local image intensity within an image, to have (approximately) zero mean and unit variance.
 * Calculations are made using Gaussian filters to give a smooth result.
 * 
 * @author Pete Bankhead
 */
public class LocalNormalization {
	
	/**
	 * Local normalization type.
	 */
	public static enum NormalizationType {
		/**
		 * No local normalization
		 */
		NONE,
		/**
		 * Subtract Gaussian-filtered image
		 */
		GAUSSIAN_MEAN_ONLY,
		/**
		 * Subtract Gaussian-filtered image, then divide by a weighted estimate of the local standard deviation
		 */
		GAUSSIAN_MEAN_VARIANCE
	}
	
	/**
	 * Helper class to store local normalization parameters.
	 */
	public static class LocalNormalizationType {
		
		/**
		 * Smoothing scale for Gaussian subtraction.
		 */
		final public SmoothingScale scale;
		/**
		 * Smoothing scale for Gaussian-weighted standard deviation estimate.
		 */
		final public SmoothingScale scaleVariance;
		
//		final private boolean subtractOnly;

		private LocalNormalizationType(SmoothingScale scale, SmoothingScale scaleVariance) {
			this.scale = scale;
			this.scaleVariance = scaleVariance;
		}

		/**
		 * Get an object containing the parameters necessary for normalization.
		 * 
		 * @param scale Gaussian sigma value used for initial filters (mean subtraction)
		 * @param scaleVariance sigma value used for variance estimation (may be null to apply subtraction only)
		 * @return
		 */
		public static LocalNormalizationType getInstance(SmoothingScale scale, SmoothingScale scaleVariance) {
			Objects.nonNull(scale);
			return new LocalNormalizationType(scale, scaleVariance);
		}
		
		/**
		 * Get an object containing the parameters necessary for normalization.
		 * 
		 * @param scale Gaussian sigma value used for initial filters (mean subtraction)
		 * @param varianceScaleRatio multiplicative factor applied to scale to determine the variance estimation scale
		 * @return
		 */
		public static LocalNormalizationType getInstance(SmoothingScale scale, double varianceScaleRatio) {
			Objects.nonNull(scale);
			if (varianceScaleRatio <= 0)
				return getInstance(scale, null);
			return getInstance(scale, SmoothingScale.getInstance(scale.scaleType, scale.getSigma() * varianceScaleRatio));
		}
		
	}
	
	
	/**
	 * Define how filters should be applied to 2D images and z-stacks when calculating multiscale features.
	 */
	static enum ScaleType { 
		/**
		 * Apply 2D filters.
		 */
		SCALE_2D,
		/**
		 * Apply 3D filters where possible.
		 */
		SCALE_3D,
		/**
		 * Apply 3D filters where possible, correcting for anisotropy in z-resolution to match xy resolution.
		 */
		SCALE_3D_ISOTROPIC
	}
	
	/**
	 * 2D or 3D Gaussian scale. See {@link #getSigmaZ(PixelCalibration)} for the key distinctions.
	 */
	public static class SmoothingScale {
		
		final private double sigma;
		final private ScaleType scaleType;
		
		private SmoothingScale(ScaleType scaleType, double sigma) {
			this.sigma = sigma;
			this.scaleType = scaleType;
		}
		
		/**
		 * Get a 2D Gaussian scale.
		 * @param sigma sigma value for x and y
		 * @return
		 */
		public static SmoothingScale get2D(double sigma) {
			return getInstance(ScaleType.SCALE_2D, sigma);
		}
		
		/**
		 * Get a 3D anisotropic Gaussian scale.
		 * @param sigma sigma value for x, y and z
		 * @return
		 */
		public static SmoothingScale get3DAnisotropic(double sigma) {
			return getInstance(ScaleType.SCALE_3D, sigma);
		}
		
		/**
		 * Get a 3D isotropic Gaussian scale.
		 * @param sigma sigma value for x, y and z
		 * @return
		 */
		public static SmoothingScale get3DIsotropic(double sigma) {
			return getInstance(ScaleType.SCALE_3D_ISOTROPIC, sigma);
		}
		
		static SmoothingScale getInstance(ScaleType scaleType, double sigma) {
			return new SmoothingScale(scaleType, sigma);
		}
		
//		public ScaleType getScaleType() {
//			return scaleType;
//		}
		
		/**
		 * Get the sigma value.
		 * @return
		 */
		public double getSigma() {
			return sigma;
		}
		
		/**
		 * Get the sigma value for the z dimension.
		 * This is interpreted depending upon the scale type:
		 * <ul>
		 *   <li>if 2D, the result is 0</li>
		 *   <li>if 3D anisotropic, the result is equivalent to {@link #getSigma()}</li>
		 *   <li>if 3D anisotropic, the result is equivalent to {@link #getSigma()} scaled for isotropic according to the pixel calibration information</li>
		 * </ul>
		 * 
		 * @param cal pixel calibration; this is only relevant is the scale type is 3D isotropic.
		 * @return
		 */
		public double getSigmaZ(PixelCalibration cal) {
			switch (scaleType) {
			case SCALE_2D:
				return 0;
			case SCALE_3D:
				return sigma;
			case SCALE_3D_ISOTROPIC:
				double pixelSize = cal.getAveragedPixelSize().doubleValue();
				double zSpacing = cal.getZSpacing().doubleValue();
				if (!Double.isFinite(zSpacing))
					zSpacing = 1.0;
				return sigma / zSpacing * pixelSize;
			default:
				throw new IllegalArgumentException("Unknown smoothing scale " + sigma);
			}
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((scaleType == null) ? 0 : scaleType.hashCode());
			long temp;
			temp = Double.doubleToLongBits(sigma);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SmoothingScale other = (SmoothingScale) obj;
			if (scaleType != other.scaleType)
				return false;
			if (Double.doubleToLongBits(sigma) != Double.doubleToLongBits(other.sigma))
				return false;
			return true;
		}

		@Override
		public String toString() {
			String sigmaString = String.format("\u03C3: %s", GeneralTools.formatNumber(sigma, 2));
			switch (scaleType) {
			case SCALE_3D:
				return sigmaString + " (3D)";
			case SCALE_3D_ISOTROPIC:
				return sigmaString + " (3D isotropic)";
			case SCALE_2D:
			default:
				return sigmaString;
			}
		}
		
	}

	/**
	 * Apply local normalization to a stack of Mats representing a z-stack.
	 * @param stack
	 * @param type
	 * @param cal
	 * @param border
	 */
	public static void gaussianNormalize(List<Mat> stack, LocalNormalizationType type, PixelCalibration cal, int border) {
		double sigmaX = type.scale.getSigma();
		double sigmaY = type.scale.getSigma();
		double sigmaZ = type.scale.getSigmaZ(cal);
		
		double sigmaVarianceX = 0, sigmaVarianceY = 0, sigmaVarianceZ = 0;
		if (type.scaleVariance != null) {
			sigmaVarianceX = type.scaleVariance.getSigma();
			sigmaVarianceY = type.scaleVariance.getSigma();
			sigmaVarianceZ = type.scaleVariance.getSigmaZ(cal);
		}
		
		gaussianNormalize3D(stack, sigmaX, sigmaY, sigmaZ, sigmaVarianceX, sigmaVarianceY, sigmaVarianceZ, border);
	}
	
	
	/**
	 * Apply local normalization to a 2D Mat.
	 * @param mat
	 * @param sigma
	 * @param sigmaVariance
	 * @param border
	 */
	public static void gaussianNormalize2D(Mat mat, double sigma, double sigmaVariance, int border) {
		LocalNormalizationType type = LocalNormalizationType.getInstance(
				SmoothingScale.get2D(sigma),
				sigmaVariance > 0 ? SmoothingScale.get2D(sigmaVariance) : null);
		gaussianNormalize(Collections.singletonList(mat), type, PixelCalibration.getDefaultInstance(), border);
	}
	
	
//	/**
//	 * Apply 3D normalization.
//	 * <p>
//	 * The algorithm works as follows:
//	 * <ol>
//	 *   <li>A Gaussian filter is applied to a duplicate of the image</li>
//	 *   <li>The filtered image is subtracted from the original</li>
//	 *   <li>The subtracted image is duplicated, squared, Gaussian filtered, and the square root taken to create a normalization image</li>
//	 *   <li>The subtracted image is divided by the value of the normalization image</li>
//	 * </ol>
//	 * The resulting image can be thought of as having a local mean of approximately zero and unit variance, 
//	 * although this is not exactly true. The approach aims to be simple, efficient and yield an image that does not 
//	 * introduce sharp discontinuities by is reliance on Gaussian filters.
//	 * 
//	 * @param stack image z-stack, in which each element is a 2D (x,y) slice
//	 * @param sigmaX horizontal Gaussian filter sigma
//	 * @param sigmaY vertical Gaussian filter sigma
//	 * @param sigmaZ z-dimension Gaussian filter sigma
//	 * @param varianceSigmaX horizontal Gaussian filter sigma for variance estimation
//	 * @param varianceSigmaY vertical Gaussian filter sigma for variance estimation
//	 * @param varianceSigmaZ z-dimension Gaussian filter sigma for variance estimation
//	 * @param border border padding method to use (see OpenCV for definitions)
//	 */
//	public static void gaussianNormalize3D(List<Mat> stack, double sigmaX, double sigmaY, double sigmaZ,
//			double varianceSigmaX, double varianceSigmaY, double varianceSigmaZ, int border) {
//		
//		int inputDepth = stack.get(0).depth();
//		int depth = opencv_core.CV_64F;
//		
//		Mat kx = OpenCVTools.getGaussianDerivKernel(sigmaX, 0, false);
//		Mat ky = OpenCVTools.getGaussianDerivKernel(sigmaY, 0, true);
//		Mat kz = OpenCVTools.getGaussianDerivKernel(sigmaZ, 0, false);
//
//		boolean doVariance = varianceSigmaX > 0 || varianceSigmaY > 0 || varianceSigmaZ > 0;
//		Mat kx2 = kx;
//		Mat ky2 = ky;
//		Mat kz2 = kz;
//		if (doVariance) {
//			kx2 = OpenCVTools.getGaussianDerivKernel(varianceSigmaX, 0, false);
//			ky2 = OpenCVTools.getGaussianDerivKernel(varianceSigmaY, 0, true);
//			kz2 = OpenCVTools.getGaussianDerivKernel(varianceSigmaZ, 0, false);			
//		}
//
//		// Convert depth
//		for (var mat : stack)
//			mat.convertTo(mat, depth);
//		
//		// Apply z-filtering if required, otherwise clone for upcoming smoothing
//		List<Mat> stackSmoothed;
//		if (sigmaZ > 0) {
//			stackSmoothed = OpenCVTools.filterZ(stack, kz, -1, border);
//		} else
//			stackSmoothed = stack.stream().map(m -> m.clone()).collect(Collectors.toList());
//		
//		// Complete separable filtering & subtract from original
//		for (int i = 0; i < stack.size(); i++) {
//			Mat mat = stack.get(i);
//			
//			// Smooth the image & subtract it from the original
//			Mat matSmooth = stackSmoothed.get(i);
//			opencv_imgproc.sepFilter2D(matSmooth, matSmooth, depth, kx, ky, null, 0.0, border);
//			opencv_core.subtract(mat, matSmooth, mat);
//
//			matSmooth.release();
//		}
//		
//		
//		if (doVariance) {
//			// Square the subtracted images
//			List<Mat> stackSquared = new ArrayList<>();
//			for (Mat mat : stack) {
//				stackSquared.add(mat.mul(mat).asMat());
//			}
//			// Smooth the squared images
//			if (sigmaZ > 0) {
//				stackSquared = OpenCVTools.filterZ(stackSquared, kz2, -1, border);
//			}
//			for (int i = 0; i < stack.size(); i++) {
//				Mat mat = stack.get(i);
//				Mat matSquared = stackSquared.get(i);
//				opencv_imgproc.sepFilter2D(matSquared, matSquared, depth, kx2, ky2, null, 0.0, border);
//				
//				opencv_core.sqrt(matSquared, matSquared);
//				opencv_core.divide(mat, matSquared, mat);
//
//				matSquared.release();
//			}
//		}
//		
//		// Give 32-bit output, unless the input was 64-bit
//		if (inputDepth != opencv_core.CV_64F && depth != opencv_core.CV_32F) {
//			for (var mat : stack) {
//				mat.convertTo(mat, opencv_core.CV_32F);
//			}
//		}
//		
//	}
	
	
	
	/**
	 * Apply 3D normalization.
	 * <p>
	 * The algorithm works as follows:
	 * <ol>
	 *   <li>A Gaussian filter is applied to a duplicate of the image</li>
	 *   <li>The filtered image is subtracted from the original</li>
	 *   <li>A local weighted variance estimate image is generated from the original image (by squaring, Gaussian filtering, 
	 *   subtracting the square of the smoothed image previously generated)</li>
	 *   <li>The square root of the weighted variance image is taken to give a normalization image, approximating a local standard deviation)
	 *   <li>The subtracted image is divided by the value of the normalization image</li>
	 * </ol>
	 * The resulting image can be thought of as having a local mean of approximately zero and unit variance, 
	 * although this is not exactly true; in practice there can be substantial differences.
	 * However, the approach aims to be simple, efficient and yield an image that does not 
	 * introduce sharp discontinuities by is reliance on Gaussian filters.
	 * 
	 * @param stack image z-stack, in which each element is a 2D (x,y) slice
	 * @param sigmaX horizontal Gaussian filter sigma
	 * @param sigmaY vertical Gaussian filter sigma
	 * @param sigmaZ z-dimension Gaussian filter sigma
	 * @param varianceSigmaX horizontal Gaussian filter sigma for variance estimation
	 * @param varianceSigmaY vertical Gaussian filter sigma for variance estimation
	 * @param varianceSigmaZ z-dimension Gaussian filter sigma for variance estimation
	 * @param border border padding method to use (see OpenCV for definitions)
	 */
	public static void gaussianNormalize3D(List<Mat> stack, double sigmaX, double sigmaY, double sigmaZ,
			double varianceSigmaX, double varianceSigmaY, double varianceSigmaZ, int border) {
		
		int inputDepth = stack.get(0).depth();
		int depth = opencv_core.CV_64F;
		
		Mat kx = OpenCVTools.getGaussianDerivKernel(sigmaX, 0, false);
		Mat ky = OpenCVTools.getGaussianDerivKernel(sigmaY, 0, true);
		Mat kz = OpenCVTools.getGaussianDerivKernel(sigmaZ, 0, false);

		boolean doVariance = varianceSigmaX > 0 || varianceSigmaY > 0 || varianceSigmaZ > 0;
		Mat kx2 = kx;
		Mat ky2 = ky;
		Mat kz2 = kz;
		if (doVariance) {
			kx2 = OpenCVTools.getGaussianDerivKernel(varianceSigmaX, 0, false);
			ky2 = OpenCVTools.getGaussianDerivKernel(varianceSigmaY, 0, true);
			kz2 = OpenCVTools.getGaussianDerivKernel(varianceSigmaZ, 0, false);			
		}

		// Ensure we have float images & their squared versions
		List<Mat> stackSquared = new ArrayList<>();
		for (Mat mat : stack) {
			mat.convertTo(mat, depth);
			if (doVariance)
				stackSquared.add(mat.mul(mat).asMat());
		}
		
		// Apply z-filtering if required, otherwise clone for upcoming smoothing
		List<Mat> stackSmoothed;
		if (sigmaZ > 0) {
			stackSmoothed = OpenCVTools.filterZ(stack, kz, -1, border);
			if (doVariance)
				stackSquared = OpenCVTools.filterZ(stackSquared, kz2, -1, border);
		} else
			stackSmoothed = stack.stream().map(m -> m.clone()).collect(Collectors.toList());
		
		// Complete separable filtering & subtract from original
		for (int i = 0; i < stack.size(); i++) {
			Mat mat = stack.get(i);
			
			// Smooth the image & subtract it from the original
			Mat matSmooth = stackSmoothed.get(i);
			opencv_imgproc.sepFilter2D(matSmooth, matSmooth, depth, kx, ky, null, 0.0, border);
			opencv_core.subtract(mat, matSmooth, mat);

			if (doVariance) {
				// Square the smoothed image
				matSmooth.put(matSmooth.mul(matSmooth));
	
				// Smooth the squared image
				Mat matSquaredSmooth = stackSquared.get(i);
				opencv_imgproc.sepFilter2D(matSquaredSmooth, matSquaredSmooth, depth, kx2, ky2, null, 0.0, border);
				
				opencv_core.subtract(matSquaredSmooth, matSmooth, matSmooth);
				opencv_core.sqrt(matSmooth, matSmooth);
				
				opencv_core.divide(mat, matSmooth, mat);
				
				matSquaredSmooth.close();
			}
			matSmooth.close();
		}
		
		// Give 32-bit output, unless the input was 64-bit
		if (inputDepth != opencv_core.CV_64F && depth != opencv_core.CV_32F) {
			for (var mat : stack) {
				mat.convertTo(mat, opencv_core.CV_32F);
			}
		}
		
	}
	

//	/**
//	 * Apply 3D normalization.
//	 * <p>
//	 * The algorithm works as follows:
//	 * <ol>
//	 *   <li>A Gaussian filter is applied to a duplicate of the image</li>
//	 *   <li>The filtered image is subtracted from the original</li>
//	 *   <li>The subtracted image is duplicated, squared, Gaussian filtered, and the square root taken to create a normalization image</li>
//	 *   <li>The subtracted image is divided by the value of the normalization image</li>
//	 * </ol>
//	 * The resulting image can be thought of as having a local mean of approximately zero and unit variance, 
//	 * although this is not exactly true. The approach aims to be simple, efficient and yield an image that does not 
//	 * introduce sharp discontinuities by is reliance on Gaussian filters.
//	 * 
//	 * @param stack image z-stack, in which each element is a 2D (x,y) slice
//	 * @param sigmaX horizontal Gaussian filter sigma
//	 * @param sigmaY vertical Gaussian filter sigma
//	 * @param sigmaZ z-dimension Gaussian filter sigma
//	 * @param border border padding method to use (see OpenCV for definitions)
//	 */
//	public static void gaussianNormalize3D(List<Mat> stack, double sigmaX, double sigmaY, double sigmaZ, int border) {
//		
//		Mat kx = OpenCVTools.getGaussianDerivKernel(sigmaX, 0, false);
//		Mat ky = OpenCVTools.getGaussianDerivKernel(sigmaY, 0, true);
//		Mat kz = OpenCVTools.getGaussianDerivKernel(sigmaZ, 0, false);
//		
//		// Apply z-filtering if required, or clone planes otherwise
//		List<Mat> stack2;
//		if (sigmaZ > 0)
//			stack2 = OpenCVTools.filterZ(stack, kz, -1, border);
//		else
//			stack2 = stack.stream().map(m -> m.clone()).collect(Collectors.toList());
//		
//		// Complete separable filtering & subtract from original
//		for (int i = 0; i < stack.size(); i++) {
//			Mat mat = stack.get(i);
//			mat.convertTo(mat, opencv_core.CV_32F);
//			Mat matSmooth = stack2.get(i);
//			opencv_imgproc.sepFilter2D(matSmooth, matSmooth, opencv_core.CV_32F, kx, ky, null, 0.0, border);
//			opencv_core.subtractPut(mat, matSmooth);
//			// Square the subtracted images & smooth again
//			matSmooth.put(mat.mul(mat));
//			opencv_imgproc.sepFilter2D(matSmooth, matSmooth, opencv_core.CV_32F, kx, ky, null, 0.0, border);
//		}
//		
//		// Complete the 3D smoothing of the squared values
//		if (sigmaZ > 0)
//			stack2 = OpenCVTools.filterZ(stack2, kz, -1, border);
//		
//		// Divide by the smoothed values
//		for (int i = 0; i < stack.size(); i++) {
//			Mat mat = stack.get(i);
//			Mat matSmooth = stack2.get(i);
//			opencv_core.sqrt(matSmooth, matSmooth);
//			mat.put(opencv_core.divide(mat, matSmooth));
//			matSmooth.release();
//		}
//	
//	}

//	/**
//	 * Apply 2D normalization.
//	 * @param mat input image
//	 * @param sigmaX horizontal Gaussian filter sigma
//	 * @param sigmaY vertical Gaussian filter sigma
//	 * @param varianceSigmaRatio ratio of sigma value used when calculating the local variance (typically &ge; 1) if zero, only subtraction is performed
//	 * @param border border padding method to use (see OpenCV for definitions)
//	 * 
//	 * @see #gaussianNormalize3D(List, double, double, double, boolean, int)
//	 */
//	public static void gaussianNormalize2D(Mat mat, double sigmaX, double sigmaY, double varianceSigmaRatio, int border) {
//		gaussianNormalize3D(Collections.singletonList(mat), sigmaX, sigmaY, 0.0, varianceSigmaRatio, border);
//	}

//	/**
//	 * Apply 2D normalization to a list of images.
//	 * This may be a z-stack, but each 2D image (x,y) plane is treated independently.
//	 * @param stack image z-stack, in which each element is a 2D (x,y) slice
//	 * @param sigma horizontal and vertical Gaussian filter
//	 * @param sigmaVariance horizontal and vertical Gaussian filter for variance estimation
//	 * @param border border padding method to use (see OpenCV for definitions)
//	 */
//	public static void gaussianNormalize2D(List<Mat> stack, double sigma, double sigmaVariance, int border) {
//		var scale = SmoothingScale.get2D(sigma);
//		SmoothingScale scaleVariance = sigmaVariance <= 0 ? null : SmoothingScale.get2D(sigmaVariance);
//		var type = LocalNormalizationType.getInstance(scale, scaleVariance);
//		gaussianNormalize3D(stack, type, null, border);
//	}

}