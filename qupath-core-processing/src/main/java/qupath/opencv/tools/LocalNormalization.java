package qupath.opencv.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Methods to normalize the local image intensity within an image.
 * 
 * @author Pete Bankhead
 */
public class LocalNormalization {
	
	/**
	 * Apply 3D normalization.
	 * <p>
	 * The algorithm works as follows:
	 * <ol>
	 *   <li>A Gaussian filter is applied to a duplicate of the image</li>
	 *   <li>The filtered image is subtracted from the original</li>
	 *   <li>The subtracted image is duplicated, squared, Gaussian filtered, and the square root taken to create a normalization image</li>
	 *   <li>The subtracted image is divided by the value of the normalization image</li>
	 * </ol>
	 * The resulting image can be thought of as having a local mean of approximately zero and unit variance, 
	 * although this is not exactly true. The approach aims to be simple, efficient and yield an image that does not 
	 * introduce sharp discontinuities by is reliance on Gaussian filters.
	 * 
	 * @param stack image z-stack, in which each element is a 2D (x,y) slice
	 * @param sigmaX horizontal Gaussian filter sigma
	 * @param sigmaY vertical Gaussian filter sigma
	 * @param sigmaZ z-dimension Gaussian filter sigma
	 * @param subtractOnly if true, subtract the Gaussian-filtered image only, do not divide by deviation
	 * @param border border padding method to use (see OpenCV for definitions)
	 */
	public static void gaussianNormalize3D(List<Mat> stack, double sigmaX, double sigmaY, double sigmaZ, boolean subtractOnly, int border) {
		
		Mat kx = OpenCVTools.getGaussianDerivKernel(sigmaX, 0, false);
		Mat ky = OpenCVTools.getGaussianDerivKernel(sigmaY, 0, true);
		Mat kz = OpenCVTools.getGaussianDerivKernel(sigmaZ, 0, false);
		
		// Ensure we have float images & their squared versions
		List<Mat> stackSquared = new ArrayList<>();
		for (Mat mat : stack) {
			mat.convertTo(mat, opencv_core.CV_32F);
			if (!subtractOnly)
				stackSquared.add(mat.mul(mat).asMat());
		}
		
		// Apply z-filtering if required, otherwise clone for upcoming smoothing
		List<Mat> stackSmoothed;
		if (sigmaZ > 0) {
			stackSmoothed = OpenCVTools.filterZ(stack, kz, -1, border);
			if (!subtractOnly)
				stackSquared = OpenCVTools.filterZ(stackSquared, kz, -1, border);
		} else
			stackSmoothed = stack.stream().map(m -> m.clone()).collect(Collectors.toList());
		
		// Complete separable filtering & subtract from original
		for (int i = 0; i < stack.size(); i++) {
			Mat mat = stack.get(i);
			
			// Smooth the image & subtract it from the original
			Mat matSmooth = stackSmoothed.get(i);
			opencv_imgproc.sepFilter2D(matSmooth, matSmooth, opencv_core.CV_32F, kx, ky, null, 0.0, border);
			opencv_core.subtract(mat, matSmooth, mat);

			if (!subtractOnly) {
				// Square the smoothed image
				matSmooth.put(matSmooth.mul(matSmooth));
	
				// Smooth the squared image
				Mat matSquaredSmooth = stackSquared.get(i);
				opencv_imgproc.sepFilter2D(matSquaredSmooth, matSquaredSmooth, opencv_core.CV_32F, kx, ky, null, 0.0, border);
				
				opencv_core.subtract(matSquaredSmooth, matSmooth, matSmooth);
				opencv_core.sqrt(matSmooth, matSmooth);
				
				opencv_core.divide(mat, matSmooth, mat);
				
				matSquaredSmooth.release();
			}
			matSmooth.release();
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

	/**
	 * Apply 2D normalization.
	 * @param mat input image
	 * @param sigmaX horizontal Gaussian filter sigma
	 * @param sigmaY vertical Gaussian filter sigma
	 * @param subtractOnly if true, subtract the Gaussian-filtered image only, do not divide by deviation
	 * @param border border padding method to use (see OpenCV for definitions)
	 * 
	 * @see #gaussianNormalize3D(List, double, double, double, boolean, int)
	 */
	public static void gaussianNormalize2D(Mat mat, double sigmaX, double sigmaY, boolean subtractOnly, int border) {
		gaussianNormalize3D(Collections.singletonList(mat), sigmaX, sigmaY, 0.0, subtractOnly, border);
	}

	/**
	 * Apply 2D normalization to a list of images.
	 * This may be a z-stack, but each 2D image (x,y) plane is treated independently.
	 * @param stack image z-stack, in which each element is a 2D (x,y) slice
	 * @param sigmaX horizontal Gaussian filter sigma
	 * @param sigmaY vertical Gaussian filter sigma
	 * @param subtractOnly if true, subtract the Gaussian-filtered image only, do not divide by deviation
	 * @param border border padding method to use (see OpenCV for definitions)
	 * 
	 * @see #gaussianNormalize3D(List, double, double, double, boolean, int)
	 */
	public static void gaussianNormalize2D(List<Mat> stack, double sigmaX, double sigmaY, boolean subtractOnly, int border) {
		gaussianNormalize3D(stack, sigmaX, sigmaY, 0.0, subtractOnly, border);
	}

}
