package qupath.opencv.processing;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;

public class LocalNormalization {

	public static void gaussianNormalize3D(List<Mat> stack, double sigmaX, double sigmaY, double sigmaZ, int border) {
		
		Mat kx = OpenCVTools.getGaussianDerivKernel(sigmaX, 0, false);
		Mat ky = OpenCVTools.getGaussianDerivKernel(sigmaY, 0, true);
		Mat kz = OpenCVTools.getGaussianDerivKernel(sigmaZ, 0, false);
		
		// Apply z-filtering if required, or clone planes otherwise
		List<Mat> stack2;
		if (sigmaZ > 0)
			stack2 = OpenCVTools.filterZ(stack, kz, -1, border);
		else
			stack2 = stack.stream().map(m -> m.clone()).collect(Collectors.toList());
		
		// Complete separable filtering & subtract from original
		for (int i = 0; i < stack.size(); i++) {
			Mat mat = stack.get(i);
			mat.convertTo(mat, opencv_core.CV_32F);
			Mat matSmooth = stack2.get(i);
			opencv_imgproc.sepFilter2D(matSmooth, matSmooth, opencv_core.CV_32F, kx, ky, null, 0.0, border);
			opencv_core.subtractPut(mat, matSmooth);
			// Square the subtracted images & smooth again
			matSmooth.put(mat.mul(mat));
			opencv_imgproc.sepFilter2D(matSmooth, matSmooth, opencv_core.CV_32F, kx, ky, null, 0.0, border);
		}
		
		// Complete the 3D smoothing of the squared values
		if (sigmaZ > 0)
			stack2 = OpenCVTools.filterZ(stack2, kz, -1, border);
		
		// Divide by the smoothed values
		for (int i = 0; i < stack.size(); i++) {
			Mat mat = stack.get(i);
			Mat matSmooth = stack2.get(i);
			opencv_core.sqrt(matSmooth, matSmooth);
			mat.put(opencv_core.divide(mat, matSmooth));
			matSmooth.release();
		}
	
	}

	public static void gaussianNormalize2D(Mat mat, double sigmaX, double sigmaY, int border) {
		gaussianNormalize3D(Collections.singletonList(mat), sigmaX, sigmaY, 0.0, border);
	}

	public static void gaussianNormalize2D(List<Mat> stack, double sigmaX, double sigmaY, int border) {
		gaussianNormalize3D(stack, sigmaX, sigmaY, 0.0, border);
	}

}
