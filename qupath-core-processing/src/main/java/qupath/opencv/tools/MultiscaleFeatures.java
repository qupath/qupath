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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatExpr;
import org.bytedeco.opencv.opencv_core.Rect;

import qupath.lib.images.servers.PixelCalibration;

/**
 * Calculate pixel-based features in both 2D and 3D.
 * 
 * @author Pete Bankhead
 *
 */
public class MultiscaleFeatures {
	
	/**
	 * Image features, dependent on Gaussian scale.
	 */
	public static enum MultiscaleFeature {
		/**
		 * Gaussian filter
		 */
		GAUSSIAN,
		/**
		 * Laplacian of Gaussian filter
		 */
		LAPLACIAN,
		/**
		 * Weighted standard deviation
		 * <p>TODO: Document calculation
		 */
		WEIGHTED_STD_DEV,
		/**
		 * Gradient magnitude
		 */
		GRADIENT_MAGNITUDE,
		/**
		 * Maximum eigenvalue of the 2x2 or 3x3 structure tensor, calculated per pixel (by value, not absolute value)
		 */
		STRUCTURE_TENSOR_EIGENVALUE_MAX,
		/**
		 * Middle eigenvalue of the 3x3 structure tensor, calculated per pixel (by value, not absolute value)
		 */
		STRUCTURE_TENSOR_EIGENVALUE_MIDDLE,
		/**
		 * Minimum eigenvalue of the 2x2 or 3x3 structure tensor, calculated per pixel (by value, not absolute value)
		 */
		STRUCTURE_TENSOR_EIGENVALUE_MIN,
		/**
		 * Coherence, defined as {@code ((l1 - l2)/(l1 + l2))^2} where {@code l1} and {@code l2} are the largest and second largest 
		 * eigenvalues of the structure tensor respectively. Where {@code l1 == l2} the value 0 is used.
		 */
		STRUCTURE_TENSOR_COHERENCE,
		/**
		 * Determinant of the Hessian matrix, calculated per pixel
		 */
		HESSIAN_DETERMINANT,
		/**
		 * Maximum eigenvalue of the 2x2 or 3x3 Hessian matrix, calculated per pixel (by value, not absolute value)
		 */
		HESSIAN_EIGENVALUE_MAX,
		/**
		 * Middle eigenvalue of the 3x3 Hessian matrix, calculated per pixel (by value, not absolute value)
		 */
		HESSIAN_EIGENVALUE_MIDDLE,
		/**
		 * Minimum eigenvalue of the 2x2 or 3x3 Hessian matrix, calculated per pixel (by value, not absolute value)
		 */
		HESSIAN_EIGENVALUE_MIN;
		
		/**
		 * Returns true if the feature can be computed for 2D images.
		 * @return
		 */
		public boolean supports2D() {
			return this != HESSIAN_EIGENVALUE_MIDDLE && this != STRUCTURE_TENSOR_EIGENVALUE_MIDDLE;
		}

		/**
		 * Returns true if the feature can be computed for 3D images (z-stacks).
		 * @return
		 */
		public boolean supports3D() {
			return true;
		}

		@Override
		public String toString() {
			switch (this) {
			case GAUSSIAN:
				return "Gaussian";
			case GRADIENT_MAGNITUDE:
				return "Gradient magnitude";
			case HESSIAN_DETERMINANT:
				return "Hessian determinant";
			case HESSIAN_EIGENVALUE_MAX:
				return "Hessian max eigenvalue";
			case HESSIAN_EIGENVALUE_MIDDLE:
				return "Hessian middle eigenvalue";
			case HESSIAN_EIGENVALUE_MIN:
				return "Hessian min eigenvalue";
			case LAPLACIAN:
				return "Laplacian of Gaussian";
			case WEIGHTED_STD_DEV:
				return "Weighted deviation";
			case STRUCTURE_TENSOR_COHERENCE:
				return "Structure tensor coherence";
			case STRUCTURE_TENSOR_EIGENVALUE_MAX:
				return "Structure tensor max eigenvalue";
			case STRUCTURE_TENSOR_EIGENVALUE_MIDDLE:
				return "Structure tensor middle eigenvalue";
			case STRUCTURE_TENSOR_EIGENVALUE_MIN:
				return "Structure tensor min eigenvalue";
			default:
				return super.toString();
			}
		}
	}
	
	
	/**
	 * Define how to handle image boundaries when applying filters.
	 */
	static enum FilterBorderType {
		
		REPLICATE, REFLECT, WRAP;
		
		@Override
		public String toString() {
			switch (this) {
			case REFLECT:
				return "Reflect border";
			case REPLICATE:
				return "Replicate border";
			case WRAP:
				return "Wrap border";
			default:
				return super.toString();
			}
		}
		
		int getOpenCVCode() {
			switch (this) {
			case REFLECT:
				return opencv_core.BORDER_REFLECT;
			case REPLICATE:
				return opencv_core.BORDER_REPLICATE;
			case WRAP:
				return opencv_core.BORDER_WRAP;
			default:
				throw new IllegalArgumentException("Unknown border type " + this); 
			}
		}
		
	}
	
	
	/**
	 * Default border strategy when filtering.
	 */
	private static final FilterBorderType BORDER_DEFAULT = FilterBorderType.REPLICATE;
	


	
	
	/**
	 * Helper-class for computing pixel-features at a specified scale.
	 */
	public static class MultiscaleResultsBuilder {
		
		private PixelCalibration pixelCalibration = PixelCalibration.getDefaultInstance();
		private double downsampleXY = 1.0;
		
		private double sigmaX = 1.0, sigmaY = 1.0, sigmaZ = 0.0;
		
//		private boolean scaleNormalize = true;
		
		private boolean gaussianSmoothed = false;
		private boolean weightedStdDev = false;

		private boolean gradientMagnitude = false;

		private boolean structureTensorEigenvalues = false;
//		private boolean structureTensorCoherence = false;

		private boolean laplacianOfGaussian = false;
		private boolean hessianEigenvalues = false;
//		private boolean hessianEigenvectors = false;
		private boolean hessianDeterminant = false;
		
		private int paddingXY = 0;
		
		private int border = BORDER_DEFAULT.getOpenCVCode();
		
		/**
		 * Default constructor.
		 */
		public MultiscaleResultsBuilder() {}
		
		/**
		 * Constructor prepared to calculate specified features.
		 * 
		 * @param features
		 */
		public MultiscaleResultsBuilder(Collection<MultiscaleFeature> features) {
			for (var feature : features) {
				switch(feature) {
				case GAUSSIAN:
					gaussianSmoothed(true);
					break;
				case GRADIENT_MAGNITUDE:
					gradientMagnitude(true);
					break;
				case HESSIAN_DETERMINANT:
					hessianDeterminant(true);
					break;
				case HESSIAN_EIGENVALUE_MAX:
				case HESSIAN_EIGENVALUE_MIDDLE:
				case HESSIAN_EIGENVALUE_MIN:
					hessianEigenvalues(true);
					break;
				case LAPLACIAN:
					laplacianOfGaussian(true);
					break;
				case STRUCTURE_TENSOR_COHERENCE:
				case STRUCTURE_TENSOR_EIGENVALUE_MAX:
				case STRUCTURE_TENSOR_EIGENVALUE_MIDDLE:
				case STRUCTURE_TENSOR_EIGENVALUE_MIN:
					structureTensorEigenvalues(true);
					break;
				case WEIGHTED_STD_DEV:
					weightedStdDev(true);
					break;
				default:
					break;
				}
			}
		}

		MultiscaleResultsBuilder(MultiscaleResultsBuilder builder) {
			this.pixelCalibration = builder.pixelCalibration;
			this.downsampleXY = builder.downsampleXY;
			this.sigmaX = builder.sigmaX;
			this.sigmaY = builder.sigmaY;
			this.sigmaZ = builder.sigmaZ;
			this.paddingXY = builder.paddingXY;
//			this.scaleNormalize = builder.scaleNormalize;
			this.gaussianSmoothed = builder.gaussianSmoothed;
			this.gradientMagnitude = builder.gradientMagnitude;
			this.structureTensorEigenvalues = builder.structureTensorEigenvalues;
//			this.structureTensorCoherence = builder.structureTensorCoherence;
			this.laplacianOfGaussian = builder.laplacianOfGaussian;
			this.hessianEigenvalues = builder.hessianEigenvalues;
//			this.hessianEigenvectors = builder.hessianEigenvectors;
			this.hessianDeterminant = builder.hessianDeterminant;
			this.border = builder.border;
		}
		
		/**
		 * Calculate the Gaussian-smoothed image.
		 * @param calculate
		 * @return
		 */
		public MultiscaleResultsBuilder gaussianSmoothed(boolean calculate) {
			this.gaussianSmoothed = calculate;
			return this;
		}
		
		/**
		 * Specify the number of pixels that the input image is padded (left, right, above, below). 
		 * This padding will be stripped prior to outputting the results. Default is 0.
		 * @param padding
		 * @return
		 */
		public MultiscaleResultsBuilder paddingXY(int padding) {
			this.paddingXY = padding;
			return this;
		}
		
		/**
		 * Calculate a Gaussian-weighted standard deviation.
		 * @param calculate
		 * @return
		 */
		public MultiscaleResultsBuilder weightedStdDev(boolean calculate) {
			this.weightedStdDev = calculate;
			return this;
		}
			
		/**
		 * Calculate the gradient magnitude.
		 * @param calculate
		 * @return
		 */
		public MultiscaleResultsBuilder gradientMagnitude(boolean calculate) {
			this.gradientMagnitude = calculate;
			return this;
		}
		
		/**
		 * Calculate the eigenvalues of the structure tensor (not yet implemented!).
		 * @param calculate
		 * @return
		 */
		public MultiscaleResultsBuilder structureTensorEigenvalues(boolean calculate) {
			this.structureTensorEigenvalues = calculate;
			return this;
		}

		/**
		 * Calculate the Laplacian of Gaussian image.
		 * @param calculate
		 * @return
		 */
		public MultiscaleResultsBuilder laplacianOfGaussian(boolean calculate) {
			this.laplacianOfGaussian = calculate;
			return this;
		}
		
		/**
		 * Calculate the eigenvalues of the Hessian matrix per pixel.
		 * @param calculate
		 * @return
		 */
		public MultiscaleResultsBuilder hessianEigenvalues(boolean calculate) {
			this.hessianEigenvalues = calculate;
			return this;
		}
		
//		public HessianResultsBuilder hessianEigenvectors(boolean calculate) {
//			this.hessianEigenvectors = calculate;
//			return this;
//		}
		
		/**
		 * Calculate the determinant of the Hessian matrix per pixel.
		 * @param calculate
		 * @return
		 */
		public MultiscaleResultsBuilder hessianDeterminant(boolean calculate) {
			this.hessianDeterminant = calculate;
			return this;
		}
				
//		/**
//		 * Apply scale normalization for the features
//		 * @param doNormalize
//		 * @return
//		 */
//		public HessianResultsBuilder scaleNormalize(boolean doNormalize) {
//			this.scaleNormalize = doNormalize;
//			return this;
//		}
		
		/**
		 * Set the pixel calibration, with optional x,y scaling.
		 * <p>
		 * If available, this will convert the units in which Gaussian sigma values are applied.
		 * This helps apply an isotropic filtering more easily, by specifying a single sigma value 
		 * and supplying the pixel calibration so that any differences in pixel dimensions is 
		 * automatically adjusted for.
		 * <p>
		 * DownsampleXY may be further used to scale the pixel units, meaning that even when working 
		 * with a downsampled image it is possible to pass the original {@code PixelCalibration} object 
		 * and specify the level of downsampling (rather than need to create a new scaled {@code PixelCalibration}).
		 * 
		 * @param cal
		 * @param downsampleXY
		 * @return
		 * 
		 * @see #sigmaXY(double)
		 * @see #sigmaX(double)
		 * @see #sigmaY(double)
		 * @see #sigmaZ(double)
		 */
		public MultiscaleResultsBuilder pixelCalibration(PixelCalibration cal, double downsampleXY) {
			this.pixelCalibration = cal;
			this.downsampleXY = downsampleXY;
			return this;
		}
		
		/**
		 * Set all Gaussian sigma values (x, y) to the same value.
		 * <p>
		 * Note that this value is in pixels by default, or may be microns is supported 
		 * by setting the pixel calibration.
		 * 
		 * @param sigma
		 * @return
		 * 
		 * @see #pixelCalibration(PixelCalibration, double)
		 * @see #sigmaX(double)
		 * @see #sigmaY(double)
		 */
		public MultiscaleResultsBuilder sigmaXY(double sigma) {
			this.sigmaX = sigma;
			this.sigmaY = sigma;
			return this;
		}
		
		/**
		 * Set all Gaussian sigma values for the horizontal filter.
		 * <p>
		 * Note that this value is in pixels by default, or may be microns is supported 
		 * by setting the pixel calibration.
		 * 
		 * @param sigma
		 * @return
		 * 
		 * @see #pixelCalibration(PixelCalibration, double)
		 * @see #sigmaXY(double)
		 * @see #sigmaY(double)
		 * @see #sigmaZ(double)
		 */
		public MultiscaleResultsBuilder sigmaX(double sigma) {
			this.sigmaX = sigma;
			return this;
		}

		/**
		 * Set all Gaussian sigma values for the vertical filter.
		 * <p>
		 * Note that this value is in pixels by default, or may be microns is supported 
		 * by setting the pixel calibration.
		 * 
		 * @param sigma
		 * @return
		 * 
		 * @see #pixelCalibration(PixelCalibration, double)
		 * @see #sigmaXY(double)
		 * @see #sigmaX(double)
		 * @see #sigmaZ(double)
		 */
		public MultiscaleResultsBuilder sigmaY(double sigma) {
			this.sigmaY = sigma;
			return this;
		}
			
		/**
		 * Set all Gaussian sigma values for the z-dimension filter.
		 * <p>
		 * Note that this value is in pixels by default, or may be microns is supported 
		 * by setting the pixel calibration.
		 * 
		 * @param sigma
		 * @return
		 * 
		 * @see #pixelCalibration(PixelCalibration, double)
		 * @see #sigmaXY(double)
		 * @see #sigmaX(double)
		 * @see #sigmaY(double)
		 */
		public MultiscaleResultsBuilder sigmaZ(double sigma) {
			this.sigmaZ = sigma;
			return this;
		}
		
		/**
		 * Calculate results for a single Mat.
		 * @param mat
		 * @return
		 */
		public Map<MultiscaleFeature, Mat> build(Mat mat) {
			if (sigmaZ > 0) {
				return build3D(Collections.singletonList(mat), 0).get(0);
			}
			return build2D(Arrays.asList(mat)).get(0);
		}
		
		/**
		 * Calculate results for a map of features and Mats for one slice of a z-stack.
		 * @param mats a list of mats, one for each slice of the z-stack
		 * @param ind the index of the slice to use
		 * @return
		 */
		public Map<MultiscaleFeature, Mat> build(List<Mat> mats, int ind) {
			if (sigmaZ > 0) {
				return build3D(mats, ind).get(0);
			}
			return build2D(Collections.singletonList(mats.get(ind))).get(0);
		}
		
		/**
		 * Calculate results as a list of maps connecting features and Mats for all slices of a z-stack.
		 * @param mats
		 * @return
		 */		public List<Map<MultiscaleFeature, Mat>> build(List<Mat> mats) {
			if (sigmaZ > 0) {
				return build3D(mats, -1);
			}
			return build2D(mats);
		}
		
		
		/**
		 * Strip output padding if there is any.
		 * @param mat
		 * @return input matrix mat, with padding removed (in-place)
		 */
		private Mat stripPadding(Mat mat) {
			if (paddingXY == 0)
				return mat;
			mat.put(mat.apply(new Rect(paddingXY, paddingXY, mat.cols()-paddingXY*2, mat.rows()-paddingXY*2)).clone());
			return mat;
		}
		
		
		private List<Map<MultiscaleFeature, Mat>> build2D(List<Mat> mats) {
			
			double sigmaX = this.sigmaX;
			double sigmaY = this.sigmaY;
			if (pixelCalibration.hasPixelSizeMicrons()) {
				sigmaX /= pixelCalibration.getPixelWidthMicrons() * downsampleXY;
				sigmaY /= pixelCalibration.getPixelHeightMicrons() * downsampleXY;
			}
			
			Mat kx0 = OpenCVTools.getGaussianDerivKernel(sigmaX, 0, false);
			Mat kx1 = OpenCVTools.getGaussianDerivKernel(sigmaX, 1, false);
			Mat kx2 = OpenCVTools.getGaussianDerivKernel(sigmaX, 2, false);
			
			Mat ky0 = OpenCVTools.getGaussianDerivKernel(sigmaY, 0, true);
			Mat ky1 = OpenCVTools.getGaussianDerivKernel(sigmaY, 1, true);
			Mat ky2 = OpenCVTools.getGaussianDerivKernel(sigmaY, 2, true);
			
			// Calculate image derivatives
			Mat dxx = new Mat();
			Mat dxy = new Mat();
			Mat dyy = new Mat();
			
			// Check if we do Hessian or Structure Tensor-based features
			boolean doSmoothed = weightedStdDev || gaussianSmoothed;
//			boolean doStructureTensor = structureTensorEigenvalues;
			boolean doHessian = hessianDeterminant || hessianEigenvalues || laplacianOfGaussian; // || hessianEigenvectors;

			List<Map<MultiscaleFeature, Mat>> results = new ArrayList<>();
			
			Hessian2D hessian = null;
			
//			double scaleT = sigmaX * sigmaY;
			
			// TODO: Consder if some calculations need to be done in 64-bit
//			int depth = structureTensorEigenvalues || doHessian ? opencv_core.CV_64F : opencv_core.CV_32F;
			int depth = opencv_core.CV_32F;

			
			for (Mat mat : mats) {
				
				Map<MultiscaleFeature, Mat> features = new LinkedHashMap<>();
				
				Mat matSmooth = null;
				if (doSmoothed) {
					if (sigmaX > 0 || sigmaY > 0) {
						matSmooth = new Mat();
						opencv_imgproc.sepFilter2D(mat, matSmooth, depth, kx0, ky0, null, 0.0, border);
					} else
						matSmooth = mat.clone();
					
					stripPadding(matSmooth);
					if (gaussianSmoothed)
						features.put(MultiscaleFeature.GAUSSIAN, matSmooth);
					
					if (weightedStdDev) {
						Mat matSquaredSmoothed = mat.mul(mat).asMat();
						opencv_imgproc.sepFilter2D(matSquaredSmoothed, matSquaredSmoothed, depth, kx0, ky0, null, 0.0, border);
						stripPadding(matSquaredSmoothed);
						matSquaredSmoothed.put(opencv_core.subtract(matSquaredSmoothed, matSmooth.mul(matSmooth)));
						opencv_core.sqrt(matSquaredSmoothed, matSquaredSmoothed);
						features.put(MultiscaleFeature.WEIGHTED_STD_DEV, matSquaredSmoothed);					
					}
				}
								
				if (structureTensorEigenvalues) {
					// Allow use of the same Mats as we might need for derivatives later
					opencv_imgproc.Sobel(mat, dxx, depth, 1, 0);
					opencv_imgproc.Sobel(mat, dyy, depth, 0, 1);
					dxy.put(dxx.mul(dyy));
					dxx.put(dxx.mul(dxx));
					dyy.put(dyy.mul(dyy));
					opencv_imgproc.sepFilter2D(dxx, dxx, depth, kx0, ky0, null, 0.0, border);
					opencv_imgproc.sepFilter2D(dyy, dyy, depth, kx0, ky0, null, 0.0, border);					
					opencv_imgproc.sepFilter2D(dxy, dxy, depth, kx0, ky0, null, 0.0, border);
					
					var temp = new EigenSymm2(dxx, dxy, dyy, false);
					var stMax = stripPadding(temp.eigvalMax);
					var stMin = stripPadding(temp.eigvalMin);
					var coherence = calculateCoherence(stMax, stMin);
					
					features.put(MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MAX, stMax);
					features.put(MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MIN, stMin);
					features.put(MultiscaleFeature.STRUCTURE_TENSOR_COHERENCE, coherence);
				}
				
				if (gradientMagnitude) {
					opencv_imgproc.sepFilter2D(mat, dxx, depth, kx1, ky0, null, 0.0, border);
					opencv_imgproc.sepFilter2D(mat, dyy, depth, kx0, ky1, null, 0.0, border);					
					Mat magnitude = new Mat();
					opencv_core.magnitude(dxx, dyy, magnitude);
					features.put(MultiscaleFeature.GRADIENT_MAGNITUDE, stripPadding(magnitude));
				}
				
				if (doHessian) {
					opencv_imgproc.sepFilter2D(mat, dxx, depth, kx2, ky0, null, 0.0, border);
					opencv_imgproc.sepFilter2D(mat, dyy, depth, kx0, ky2, null, 0.0, border);
					opencv_imgproc.sepFilter2D(mat, dxy, depth, kx1, ky1, null, 0.0, border);
					
					// Strip padding now to reduce necessary calculations
					stripPadding(dxx);
					stripPadding(dxy);
					stripPadding(dyy);
					
					hessian = new Hessian2D(dxx, dxy, dyy, false);
					if (laplacianOfGaussian) {
						Mat temp = hessian.getLaplacian();
//						if (scaleNormalize)
//							opencv_core.multiplyPut(temp, scaleT);
						features.put(MultiscaleFeature.LAPLACIAN, temp);
					}
					
					if (hessianDeterminant) {
						Mat temp = hessian.getDeterminant();
//						if (scaleNormalize)
//							opencv_core.multiplyPut(temp, scaleT * scaleT);
						features.put(MultiscaleFeature.HESSIAN_DETERMINANT, temp);
					}
					
					if (hessianEigenvalues) {
						List<Mat> eigenvalues = hessian.getEigenvalues();
						assert eigenvalues.size() == 2;
						features.put(MultiscaleFeature.HESSIAN_EIGENVALUE_MAX, eigenvalues.get(0));
						features.put(MultiscaleFeature.HESSIAN_EIGENVALUE_MIN, eigenvalues.get(1));
					}
					
				}
				
				// Ensure our output is 32-bit
				if (depth != opencv_core.CV_32F) {
					for (var matFeature : features.values()) {
						matFeature.convertTo(matFeature, opencv_core.CV_32F);
					}
				}
				
				results.add(features);
			}
			
//			if (hessian != null)
//				hessian.close();

			kx0.release();
			kx1.release();
			kx2.release();
			ky0.release();
			ky1.release();
			ky2.release();
			
			return results;
		}
		
		private List<Map<MultiscaleFeature, Mat>> build3D(List<Mat> mats, int ind3D) {
			if (mats.size() == 0)
				return Collections.emptyList();
			
			double sigmaX = this.sigmaX;
			double sigmaY = this.sigmaY;
			double sigmaZ = this.sigmaZ;
			if (pixelCalibration.hasPixelSizeMicrons()) {
				sigmaX /= pixelCalibration.getPixelWidthMicrons() * downsampleXY;
				sigmaY /= pixelCalibration.getPixelHeightMicrons() * downsampleXY;
			}
			if (pixelCalibration.hasZSpacingMicrons()) {
				sigmaZ /= pixelCalibration.getZSpacingMicrons();
			}
			
			// Check if we do Hessian or Structure Tensor-based features
			boolean doSmoothed = weightedStdDev || gaussianSmoothed;
//			boolean doStructureTensor = structureTensorEigenvalues;
			boolean doHessian = hessianDeterminant || hessianEigenvalues || laplacianOfGaussian; // || hessianEigenvectors;
			
			// Get all the kernels we will need
			Mat kx0 = OpenCVTools.getGaussianDerivKernel(sigmaX, 0, false);
			Mat kx1 = OpenCVTools.getGaussianDerivKernel(sigmaX, 1, false);
			Mat kx2 = OpenCVTools.getGaussianDerivKernel(sigmaX, 2, false);
			
			Mat ky0 = OpenCVTools.getGaussianDerivKernel(sigmaY, 0, true);
			Mat ky1 = OpenCVTools.getGaussianDerivKernel(sigmaY, 1, true);
			Mat ky2 = OpenCVTools.getGaussianDerivKernel(sigmaY, 2, true);
			
			Mat kz0 = OpenCVTools.getGaussianDerivKernel(sigmaZ, 0, true);
			Mat kz1 = OpenCVTools.getGaussianDerivKernel(sigmaZ, 1, true);
			Mat kz2 = OpenCVTools.getGaussianDerivKernel(sigmaZ, 2, true);
			
			// Apply the awkward filtering along the z-dimension first
			List<Mat> matsZ0 = null;
			if (doSmoothed || gradientMagnitude  || doHessian)
				matsZ0 = OpenCVTools.filterZ(mats, kz0, ind3D, border);
			List<Mat> matsZ1 = null;
			if (doHessian || gradientMagnitude)
				matsZ1 = OpenCVTools.filterZ(mats, kz1, ind3D, border);
			List<Mat> matsZ2 = null;
			if (doHessian)
				matsZ2 = OpenCVTools.filterZ(mats, kz2, ind3D, border);
			
			// Handle structure tensor (which is *much* more memory-hungry and computationally expensive)
			List<Mat> matSTxx = null;
			List<Mat> matSTxy = null;
			List<Mat> matSTxz = null;
			List<Mat> matSTyy = null;
			List<Mat> matSTyz = null;
			List<Mat> matSTzz = null;
			if (structureTensorEigenvalues) {
				// Calculate gradient filters
				matSTxx = new ArrayList<>();
				matSTxy = new ArrayList<>();
				matSTxz = new ArrayList<>();
				matSTyy = new ArrayList<>();
				matSTyz = new ArrayList<>();
				matSTzz = new ArrayList<>();
				for (int i = 0; i < mats.size(); i++) {
					var tempX = new Mat();
					var tempY = new Mat();
					var tempZ = new Mat();
					
					var mat = mats.get(i);
					opencv_imgproc.Sobel(mat, tempX, opencv_core.CV_32F, 1, 0);
					opencv_imgproc.Sobel(mat, tempY, opencv_core.CV_32F, 0, 1);
					
					// Use centred difference for z-dimension
					int iNext = Math.min(i+1, mats.size()-1);
					int iPrev = Math.max(i-1, 0);
					opencv_core.subtract(mats.get(iNext), mats.get(iPrev), tempZ);
					
					matSTxy.add(tempX.mul(tempY).asMat());
					matSTxz.add(tempX.mul(tempZ).asMat());
					matSTyz.add(tempY.mul(tempZ).asMat());
					
					tempX.put(tempX.mul(tempX));
					matSTxx.add(tempX);
					tempY.put(tempY.mul(tempY));
					matSTyy.add(tempY);
					tempZ.put(tempZ.mul(tempZ));
					matSTzz.add(tempZ);
				}
				// Apply Gaussian z-filter, extracting slice of interest if required
				matSTxx = OpenCVTools.filterZ(matSTxx, kz0, ind3D, border);
				matSTxy = OpenCVTools.filterZ(matSTxy, kz0, ind3D, border);
				matSTxz = OpenCVTools.filterZ(matSTxz, kz0, ind3D, border);
				matSTyy = OpenCVTools.filterZ(matSTyy, kz0, ind3D, border);
				matSTyz = OpenCVTools.filterZ(matSTyz, kz0, ind3D, border);
				matSTzz = OpenCVTools.filterZ(matSTzz, kz0, ind3D, border);
				// Apply 2D Gaussian filters
				for (List<Mat> list : Arrays.asList(matSTxx, matSTxy, matSTxz, matSTyy, matSTyz, matSTzz)) {
					for (Mat temp : list)
						opencv_imgproc.sepFilter2D(temp, temp, opencv_core.CV_32F, kx0, ky0, null, 0.0, border);
				}
			}
			
			int nSlices = ind3D >= 0 ? 1 : mats.size();
			
			// Need to square original pixels for weighted std dev calculation
			List<Mat> matsSquaredZ0 = null;
			if (weightedStdDev) {
				matsSquaredZ0 = new ArrayList<>();
				for (Mat mat : mats)
					matsSquaredZ0.add(mat.mul(mat).asMat());
				matsSquaredZ0 = OpenCVTools.filterZ(matsSquaredZ0, kz0, ind3D, border);
			}
			
			// We need some Mats for each plane, but we can reuse them
			Mat dxx = new Mat();
			Mat dxy = new Mat();
			Mat dxz = new Mat();
			
			Mat dyy = new Mat();
			Mat dyz = new Mat();
			
			Mat dzz = new Mat();
			
			Hessian3D hessian = null;
			
			// Loop through and handle the remaining 2D filtering
			// We do this 1 plane at a time so that we don't need to retain all filtered images in memory
			List<Map<MultiscaleFeature, Mat>> output = new ArrayList<>();
			for (int i = 0; i < nSlices; i++) {
				
				Map<MultiscaleFeature, Mat> features = new LinkedHashMap<>();
				
				if (doSmoothed) {
					Mat z0 = matsZ0.get(i);
					Mat matSmooth = new Mat();
					opencv_imgproc.sepFilter2D(z0, matSmooth, opencv_core.CV_32F, kx0, ky0, null, 0.0, border);
					stripPadding(matSmooth);
					if (gaussianSmoothed)
						features.put(MultiscaleFeature.GAUSSIAN, matSmooth);
					
					if (weightedStdDev) {
						// Note that here we modify the original images in-place, since from now on we just need 2D planes
						Mat matSquaredSmoothed = matsSquaredZ0.get(i);
						opencv_imgproc.sepFilter2D(matSquaredSmoothed, matSquaredSmoothed, opencv_core.CV_32F, kx0, ky0, null, 0.0, border);
						stripPadding(matSquaredSmoothed);
						matSquaredSmoothed.put(opencv_core.subtract(matSquaredSmoothed, matSmooth.mul(matSmooth)));
						opencv_core.sqrt(matSquaredSmoothed, matSquaredSmoothed);
						features.put(MultiscaleFeature.WEIGHTED_STD_DEV, matSquaredSmoothed);					
					}
				}
				
				if (structureTensorEigenvalues) {
					var temp = new EigenSymm3(
							matSTxx.get(i),
							matSTxy.get(i),
							matSTxz.get(i),
							matSTyy.get(i),
							matSTyz.get(i),
							matSTzz.get(i),
							false);
					
					// TODO: Check if coherence should be calculated another way for 3D
					var stMax = stripPadding(temp.eigvalMax);
					var stMiddle = stripPadding(temp.eigvalMiddle);
					var stMin = stripPadding(temp.eigvalMin);
					var coherence = calculateCoherence(stMax, stMiddle);
//					var coherence = calculateCoherence(stMax, stMin);
					
					features.put(MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MAX, stMax);
					features.put(MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MIDDLE, stMiddle);
					features.put(MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MIN, stMin);
					features.put(MultiscaleFeature.STRUCTURE_TENSOR_COHERENCE, coherence);
				}
				

				if (gradientMagnitude) {
					Mat z0 = matsZ0.get(i);
					Mat z1 = matsZ1.get(i);
					opencv_imgproc.sepFilter2D(z0, dxx, opencv_core.CV_32F, kx1, ky0, null, 0.0, border);
					opencv_imgproc.sepFilter2D(z0, dyy, opencv_core.CV_32F, kx0, ky1, null, 0.0, border);					
					opencv_imgproc.sepFilter2D(z1, dzz, opencv_core.CV_32F, kx0, ky0, null, 0.0, border);
					Mat magnitude = opencv_core.add(opencv_core.add(dxx.mul(dxx), dyy.mul(dyy)), dzz.mul(dzz)).asMat();
					features.put(MultiscaleFeature.GRADIENT_MAGNITUDE, stripPadding(magnitude));
				}
				
				if (doHessian) {
					
					Mat z0 = matsZ0.get(i);
					Mat z1 = matsZ1.get(i);
					Mat z2 = matsZ2.get(i);
					
					opencv_imgproc.sepFilter2D(z0, dxx, opencv_core.CV_32F, kx2, ky0, null, 0.0, border);
					opencv_imgproc.sepFilter2D(z0, dxy, opencv_core.CV_32F, kx1, ky1, null, 0.0, border);
					opencv_imgproc.sepFilter2D(z1, dxz, opencv_core.CV_32F, kx1, ky0, null, 0.0, border);
					
					opencv_imgproc.sepFilter2D(z0, dyy, opencv_core.CV_32F, kx0, ky2, null, 0.0, border);
					opencv_imgproc.sepFilter2D(z1, dyz, opencv_core.CV_32F, kx0, ky1, null, 0.0, border);
					
					opencv_imgproc.sepFilter2D(z2, dzz, opencv_core.CV_32F, kx0, ky0, null, 0.0, border);
					
					// Strip padding now to reduce necessary calculations
					stripPadding(dxx);
					stripPadding(dxy);
					stripPadding(dxz);
					stripPadding(dyy);
					stripPadding(dyz);
					stripPadding(dzz);
				
					hessian = new Hessian3D(dxx, dxy, dxz, dyy, dyz, dzz, true);
					if (laplacianOfGaussian)
						features.put(MultiscaleFeature.LAPLACIAN, hessian.getLaplacian());
					
					if (hessianDeterminant)
						features.put(MultiscaleFeature.HESSIAN_DETERMINANT, hessian.getDeterminant());
					
					if (hessianEigenvalues) {
						List<Mat> eigenvalues = hessian.getEigenvalues();
						assert eigenvalues.size() == 3;
						features.put(MultiscaleFeature.HESSIAN_EIGENVALUE_MAX, eigenvalues.get(0));
						features.put(MultiscaleFeature.HESSIAN_EIGENVALUE_MIDDLE, eigenvalues.get(1));
						features.put(MultiscaleFeature.HESSIAN_EIGENVALUE_MIN, eigenvalues.get(2));
					}

				}
				output.add(features);
			}
			
			dxx.close();
			dxy.close();
			dxz.close();
			dyz.close();
			dyy.close();
			dzz.close();
//			if (hessian != null)
//				hessian.close();
			
			
			return output;
		}
		
	}
	
	/**
	 * Calculate coherence from the max/min eigenvalues of the structure tensor.
	 * @param stMax
	 * @param stMin
	 * @return
	 */
	static Mat calculateCoherence(Mat stMax, Mat stMin) {
		int w = stMax.cols();
		int h = stMax.rows();

		var coherence = new Mat(h, w, opencv_core.CV_32FC1);
		FloatIndexer idxCoherence = coherence.createIndexer();
		var idxMax = stMax.createIndexer();
		var idxMin = stMin.createIndexer();
		long[] inds = new long[2];
		for (int r = 0; r < h; r++) {
			for (int c = 0; c < w; c++) {
				inds[0] = r;
				inds[1] = c;
				double max = idxMax.getDouble(inds);
				double min = idxMin.getDouble(inds);
				double difference = max - min;
				double sum = max + min;
				double co = sum == 0 ? 0 : (difference / sum) * (difference / sum);
				idxCoherence.put(r, c, (float)co);
			}
		}
		idxCoherence.release();
		idxMax.release();
		idxMin.release();
		return coherence;
	}
	
	
//	static class MultiscaleResults {
//		
//		private Mat matGaussian;
//		private Mat matGradMag;
//		private Hessian hessian;
//		
//		MultiscaleResults(Mat matGaussian, Mat matGradMag, Hessian hessian) {
//			this.matGaussian = matGaussian;
//			this.matGradMag = matGradMag;
//			this.hessian = hessian;
//		}
//		
//		public Mat getResults(MultiscaleFeature feature) {
//			switch (feature) {
//			case GAUSSIAN:
//				if (matGaussian != null)
//					return matGaussian;
//				break;
//			case GRADIENT_MAGNITUDE:
//				if (matGradMag != null)
//					return matGradMag;
//				break;
//			case HESSIAN_DETERMINANT:
//				return hessian.getDeterminant();
//			case HESSIAN_EIGENVALUE_MAX:
//				return hessian.getEigenvalues().get(0);
//			case HESSIAN_EIGENVALUE_MIDDLE:
//				List<Mat> eigenvalues = hessian.getEigenvalues();
//				if (eigenvalues.size() == 3)
//					return eigenvalues.get(1);
//				break;
//			case HESSIAN_EIGENVALUE_MIN:
//				eigenvalues = hessian.getEigenvalues();
//				return eigenvalues.get(eigenvalues.size()-1);
//			case LAPLACIAN:
//				return hessian.getLaplacian();
//			default:
//				break;
//			}
//			throw new IllegalArgumentException("Unknown feature " + feature);
//		}
//		
//		
//	}
	
	
	static interface StructureTensor extends AutoCloseable {
		
		Mat getEigenvalues(int n);
		
		Mat getCoherence();
		
	}
	
//	static class StructureTensor2D implements StructureTensor {
//		
//		private Mat dx, dy, dz;
//		
//		StructureTensor2D(Mat dx, Mat dy, Mat dz) {
//			
//		}
//
//		@Override
//		public Mat getEigenvalues(int n) {
//			// TODO Auto-generated method stub
//			return null;
//		}
//
//		@Override
//		public Mat getCoherence() {
//			// TODO Auto-generated method stub
//			return null;
//		}
//
//		@Override
//		public void close() throws Exception {
//			// TODO Auto-generated method stub
//			
//		}
//
//	}
	
	
	
	
	/**
	 * Helper class for storing and computing pixel features from Hessian matrices.
	 */
	static interface Hessian extends AutoCloseable {
		
		/**
		 * Get Laplacian of Gaussian image (calculated by summation without requiring eigenvalues).
		 * @return
		 */
		Mat getLaplacian();

		/**
		 * Get the determinant for each pixel.
		 * @return
		 */
		Mat getDeterminant();
		
		/**
		 * Get the eigenvalues, ranked from highest to lowest.
		 * @return
		 */
		List<Mat> getEigenvalues();
		
		/**
		 * Get the eigenvectors, returned in the same order as the eigenvalues.
		 * Vector elements are stored along the 'channels' dimension.
		 * @return
		 */
		List<Mat> getEigenvectors();

	}
	
	static class Hessian2D implements Hessian {
		
		private boolean doEigenvectors;
		private Mat dxx, dxy, dyy;
		private EigenSymm2 eigen;
		
		Hessian2D(Mat dxx, Mat dxy, Mat dyy, boolean doEigenvectors) {
			this.dxx = dxx;
			this.dxy = dxy;
			this.dyy = dyy;
			this.doEigenvectors = doEigenvectors;
		}
		
		@Override
		public Mat getLaplacian() {
			return opencv_core.add(dxx, dyy).asMat();
		}
		
		private void ensureEigenvalues() {
			if (eigen == null) {
				eigen = new EigenSymm2(dxx, dxy, dyy, doEigenvectors);
			}
		}
		
		@Override
		public List<Mat> getEigenvalues() {
			ensureEigenvalues();
			return Arrays.asList(eigen.eigvalMax, eigen.eigvalMin);
		}
		
		@Override
		public List<Mat> getEigenvectors() {
			ensureEigenvalues();
			if (eigen.eigvecMax == null)
				throw new UnsupportedOperationException("Eigenvectors were not calculated!");
			return Arrays.asList(eigen.eigvecMax, eigen.eigvecMin);
		}
		
		@Override
		public Mat getDeterminant() {
			return EigenSymm2.getDeterminantExpr2x2(dxx, dxy, dyy).asMat();
		}

		@Override
		public void close() {
			dxx.close();
			dxy.close();
			dyy.close();
			if (eigen != null) {
				eigen.close();
			}
		}
		
	}
	
	static class Hessian3D implements Hessian {
		
		private boolean doEigenvectors;
		private Mat dxx, dxy, dxz, dyy, dyz, dzz;
		private EigenSymm3 eigen;
		
		Hessian3D(Mat dxx, Mat dxy, Mat dxz, Mat dyy, Mat dyz, Mat dzz, boolean doEigenvectors) {
			this.dxx = dxx;
			this.dxy = dxy;
			this.dxz = dxz;
			this.dyy = dyy;
			this.dyz = dyz;
			this.dzz = dzz;
			this.doEigenvectors = doEigenvectors;
		}

		@Override
		public Mat getLaplacian() {
			return opencv_core.add(opencv_core.add(dxx, dyy), dzz).asMat();
		}

		private void ensureEigenvalues() {
			if (eigen == null) {
				eigen = new EigenSymm3(dxx, dxy, dxz, dyy, dyz, dzz, doEigenvectors);
			}
		}
		
		@Override
		public List<Mat> getEigenvectors() {
			ensureEigenvalues();
			if (eigen.eigvecMax == null)
				throw new UnsupportedOperationException("Eigenvectors were not calculated!");
			return Arrays.asList(eigen.eigvecMax, eigen.eigvecMiddle, eigen.eigvecMin);
		}
		
		@Override
		public List<Mat> getEigenvalues() {
			ensureEigenvalues();
			return Arrays.asList(eigen.eigvalMax, eigen.eigvalMiddle, eigen.eigvalMin);
		}
		
		@Override
		public Mat getDeterminant() {
			ensureEigenvalues();
			return eigen.eigvalMin.mul(eigen.eigvalMiddle).mul(eigen.eigvalMax).asMat();
		}

		@Override
		public void close() {
			dxx.close();
			dxy.close();
			dxz.close();
			dyy.close();
			dyz.close();
			dzz.close();
			if (eigen != null) {
				eigen.close();
			}
		}
		
	}
	
	
	/**
	 * Calculate eigenvalues and (optionally) eigenvectors for a 2x2 symmetric matrix.
	 */
	private static class EigenSymm2 implements AutoCloseable {
		
		private Mat eigvalMin, eigvalMax;
		private Mat eigvecMin, eigvecMax;
		
		EigenSymm2(Mat dxx, Mat dxy, Mat dyy, boolean doEigenvectors) {
			MatExpr trace = opencv_core.add(dxx, dyy);
			MatExpr det = getDeterminantExpr2x2(dxx, dxy, dyy);
			
			MatExpr t1 = opencv_core.divide(trace, 2.0);
			Mat t2 = opencv_core.subtract(
					trace.mul(trace, 1.0/4.0),
					det).asMat();
			opencv_core.sqrt(t2, t2);
			
			eigvalMin = opencv_core.subtract(t1, t2).asMat();
			eigvalMax = opencv_core.add(t1, t2).asMat();
			
			// NaNs can occur! Remove these to prevent downstream problems (e.g. with any further filtering)
			opencv_core.patchNaNs(eigvalMin, 0.0);
			opencv_core.patchNaNs(eigvalMax, 0.0);
			
			// Try to debug a lot of zeros in the output (turned out normalization was applied too late)
//			double total = eigvalMin.total();
//			double zeroPercentMin = (total - opencv_core.countNonZero(eigvalMin))/total * 100;
//			double zeroPercentMax = (total - opencv_core.countNonZero(eigvalMax))/total * 100;
//			System.err.println(String.format("Zeros min: %.1f%%, max: %.1f%%", zeroPercentMin, zeroPercentMax));
//			if (zeroPercentMax > 5 && zeroPercentMin > 5) {
//				var imp = OpenCVTools.matToImagePlus("Temp", eigvalMin.clone(), eigvalMax.clone(), det.asMat(), trace.asMat());
//				var imp2 = new CompositeImage(imp, CompositeImage.GRAYSCALE);
//				imp2.setDimensions(imp.getStackSize(), 1, 1);
//				imp2.resetDisplayRanges();
//				imp2.show();
//			}
			
			if (doEigenvectors) {
				int width = dxx.cols();
				int height = dxx.rows();
				int n = width * height;
				float[] bufMinVec = new float[n * 2];
				float[] bufMaxVec = new float[n * 2];
				
				float[] c = OpenCVTools.extractPixels(dxy, null);
				float[] d = OpenCVTools.extractPixels(dyy, null);
				float[] l1 = OpenCVTools.extractPixels(eigvalMax, null);
				float[] l2 = OpenCVTools.extractPixels(eigvalMin, null);
				for (int i = 0; i < n; i++) {
					float offDiag = c[i];
					if (offDiag == 0f) {
						bufMaxVec[i*2] = 1f;
						bufMaxVec[i*2+1] = 0f;
						bufMinVec[i*2] = 0f;
						bufMinVec[i*2+1] = 1f;
					} else {
						double temp1 = l1[i] - d[i];
						double temp2 = c[i];
						double len = Math.sqrt(temp1*temp1 + temp2*temp2);
						bufMaxVec[i*2] = (float)(temp1/len);
						bufMaxVec[i*2+1] = (float)(temp2/len);
						
						temp1 = l2[i] - d[i];
						len = Math.sqrt(temp1*temp1 + temp2*temp2);
						bufMinVec[i*2] = (float)(temp1/len);
						bufMinVec[i*2+1] = (float)(temp2/len);
					}
				}
				eigvecMin = new Mat(height, width, opencv_core.CV_32FC2, new FloatPointer(bufMinVec));
				eigvecMax = new Mat(height, width, opencv_core.CV_32FC2, new FloatPointer(bufMaxVec));			
			}
			
			t1.close();
			t2.close();
			trace.close();
			det.close();
		}
		
		static MatExpr getDeterminantExpr2x2(Mat dxx, Mat dxy, Mat dyy) {
			return opencv_core.subtract(
					dxx.mul(dyy),
					dxy.mul(dxy)
					);
		}

		@Override
		public void close() {
			eigvalMin.close();
			eigvalMax.close();
			if (eigvecMax != null)
				eigvecMax.close();
			if (eigvecMin != null)
				eigvecMin.close();
		}
		
	}
	
	
	
	/**
	 * Calculate eigenvalues and (optionally) eigenvectors for a 3x3 symmetric matrix.
	 */
	private static class EigenSymm3 implements AutoCloseable {
		
		private Mat eigvalMin, eigvalMiddle, eigvalMax;
		private Mat eigvecMin, eigvecMiddle, eigvecMax;
		
		EigenSymm3(Mat dxx, Mat dxy, Mat dxz, Mat dyy, Mat dyz, Mat dzz, boolean doEigenvectors) {
			
			int height = dxx.rows();
			int width = dxx.cols();
			
			Mat matInput = new Mat(3, 3, opencv_core.CV_32FC1);
			Mat matEigenvalues = new Mat(3, 1, opencv_core.CV_32FC1);
			Mat matEigenvectors = new Mat(3, 3, opencv_core.CV_32FC1);
			
			float[] pxDxx = OpenCVTools.extractPixels(dxx, null);
			float[] pxDxy = OpenCVTools.extractPixels(dxy, null);
			float[] pxDxz = OpenCVTools.extractPixels(dxz, null);
			float[] pxDyy = OpenCVTools.extractPixels(dyy, null);
			float[] pxDyz = OpenCVTools.extractPixels(dyz, null);
			float[] pxDzz = OpenCVTools.extractPixels(dzz, null);
			
			// Buffer to contain values for each row of the Hessian matrices
			float[] bufMin = new float[width * height];
			float[] bufMiddle = new float[width * height];
			float[] bufMax = new float[width * height];

			float[] bufMinVec = doEigenvectors ? new float[width * height * 3] : null;
			float[] bufMiddleVec = doEigenvectors ? new float[width * height * 3] : null;
			float[] bufMaxVec = doEigenvectors ? new float[width * height * 3] : null;

			FloatIndexer idxInput = matInput.createIndexer();
			FloatIndexer idxEigenvalues = matEigenvalues.createIndexer();
			FloatIndexer idxEigenvectors = matEigenvectors.createIndexer();
			
			float[] input = new float[9];
			float[] eigenvalues = new float[3];
			float[] eigenvectors = new float[9];
			
			int ind = 0;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					
					// Arrange values for 3x3 symmetric matrix
					input[0] = pxDxx[ind];
					input[1] = pxDxy[ind];
					input[2] = pxDxz[ind];

					input[3] = pxDxy[ind];
					input[4] = pxDyy[ind];
					input[5] = pxDyz[ind];

					input[6] = pxDxz[ind];
					input[7] = pxDyz[ind];
					input[8] = pxDzz[ind];

					// Populate the Hessian matrix
					idxInput.put(0L, input);
					
					// Calculate required values
					// TODO: Compare performance using opencv_core.eigen vs commons math vs jama
					if (doEigenvectors)
						opencv_core.eigen(matInput, matEigenvalues, matEigenvectors);
					else
						opencv_core.eigen(matInput, matEigenvalues);
					
					// Extract the eigenvalues
					idxEigenvalues.get(0L, eigenvalues);
					bufMax[ind] = eigenvalues[0];
					bufMiddle[ind] = eigenvalues[1];
					bufMin[ind] = eigenvalues[2];
					
					// Extract the eigenvectors if required
					if (doEigenvectors) {
						idxEigenvectors.get(0L, eigenvectors);
						
						bufMinVec[ind * 3] = eigenvectors[0];
						bufMinVec[ind * 3 + 1] = eigenvectors[1];
						bufMinVec[ind * 3 + 2] = eigenvectors[2];
						
						bufMiddleVec[ind * 3] = eigenvectors[3];
						bufMiddleVec[ind * 3 + 1] = eigenvectors[4];
						bufMiddleVec[ind * 3 + 2] = eigenvectors[5];
						
						bufMaxVec[ind * 3] = eigenvectors[6];
						bufMaxVec[ind * 3 + 1] = eigenvectors[7];
						bufMaxVec[ind * 3 + 2] = eigenvectors[8];
					}
					
					ind++;
				}
			}
			
			idxInput.release();
			idxEigenvalues.release();
			idxEigenvectors.release();
			
			matInput.close();
			matEigenvalues.close();
			matEigenvectors.close();
			
			// Store the eigenvalues as Mats
			eigvalMin = new Mat(height, width, opencv_core.CV_32FC1, new FloatPointer(bufMin));
			eigvalMiddle = new Mat(height, width, opencv_core.CV_32FC1, new FloatPointer(bufMiddle));
			eigvalMax = new Mat(height, width, opencv_core.CV_32FC1, new FloatPointer(bufMax));
			
			// Store the eigenvectors
			if (doEigenvectors) {
				eigvecMin = new Mat(height, width, opencv_core.CV_32FC3, new FloatPointer(bufMinVec));
				eigvecMiddle = new Mat(height, width, opencv_core.CV_32FC3, new FloatPointer(bufMiddleVec));
				eigvecMax = new Mat(height, width, opencv_core.CV_32FC3, new FloatPointer(bufMaxVec));				
			}
		}

		@Override
		public void close() {
			eigvalMin.close();
			eigvalMiddle.close();
			eigvalMax.close();
			if (eigvecMax != null)
				eigvecMax.close();
			if (eigvecMiddle != null)
				eigvecMiddle.close();
			if (eigvecMin != null)
				eigvecMin.close();
		}
		
	}
	
}