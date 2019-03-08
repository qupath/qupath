package qupath.lib.classifiers.opencv;

import java.nio.FloatBuffer;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

import qupath.opencv.processing.TypeAdaptersCV;

@JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
public class PCAProjector implements AutoCloseable {
		
		private static final Logger logger = LoggerFactory.getLogger(PCAProjector.class);
		
		public static final double EPSILON = 1e-5;
		
		@JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
		private Mat mean = new Mat();
		
		@JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
		private Mat eigenvectors = new Mat();
		
		@JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
		private Mat eigenvalues = new Mat();
		
		@JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
		private transient Mat eigenvaluesSqrt;
		
		private double retainedVariance = -1;
		private boolean normalize = true;
		
		
		
		public static PCAProjector createPCAProjector(Mat data, double retainedVariance, boolean normalize) {
			return new PCAProjector(data, retainedVariance, normalize);
		}
		
		
		private PCAProjector(Mat data, double retainedVariance, boolean normalize) {
			this.retainedVariance = retainedVariance;
			this.normalize = normalize;
			opencv_core.PCACompute2(data, mean, eigenvectors, eigenvalues, retainedVariance);
//			System.err.println(mean.createIndexer());
			logger.info("Reduced dimensions from {} to {}",data.cols(), eigenvectors.rows());
		}
		
		public double getRetainedVariance() {
			return retainedVariance;
		}
		
		public void project(Mat data, Mat result) {
			opencv_core.PCAProject(data, mean, eigenvectors, result);
			if (normalize)
				doNormalize(result);
		}
		
		void doNormalize(Mat result) {
			if (eigenvaluesSqrt == null) {
				eigenvaluesSqrt = new Mat();
				eigenvalues.copyTo(eigenvaluesSqrt);
				opencv_core.add(eigenvaluesSqrt, Scalar.all(EPSILON));
				opencv_core.sqrt(eigenvaluesSqrt, eigenvaluesSqrt);
				eigenvaluesSqrt.put(eigenvaluesSqrt.t());
//				eigenvaluesSqrt.convertTo(eigenvaluesSqrt, opencv_core.CV_64FC1);
//				eigenvaluesSqrt.put(opencv_core.divide(1.0, eigenvaluesSqrt));
			}
//			var indexer = result.createIndexer();
//			var before = indexer.getDouble(0L);
						

			// Because we're likely either to have one row or many, either divide by row 
			// or work by column (even though rows may seem more natural, it's much slower)
			if (result.rows() == 1)
				opencv_core.dividePut(result, eigenvaluesSqrt);
			else {
				FloatBuffer buffer = (FloatBuffer)eigenvaluesSqrt.createBuffer();
				for (int c = 0; c < result.cols(); c++) {
					opencv_core.dividePut(result.col(c), buffer.get(c));
				}				
			}
		}

		@Override
		public void close() throws Exception {
			mean.release();
			eigenvectors.release();
			eigenvalues.release();
			if (eigenvaluesSqrt != null)
				eigenvaluesSqrt.release();
		}
		
	}