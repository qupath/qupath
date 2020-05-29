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

package qupath.opencv.ml.objects.features;

import java.nio.FloatBuffer;
import java.util.Arrays;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.classifiers.Normalization;
import qupath.lib.io.OpenCVTypeAdapters;

/**
 * Helper class for preprocessing input for machine learning algorithms using OpenCV Mats.
 * 
 * @author Pete Bankhead
 */
public class Preprocessing {

	/**
	 * Create a principle components analysis projection to reduce features.
	 * @param data input data used to create the projector
	 * @param retainedVariance variance to retain (determines the number of output features)
	 * @param normalize if true, normalize the output features
	 * @return a {@link PCAProjector} that can be applied to new data
	 */
	public static PCAProjector createPCAProjector(Mat data, double retainedVariance, boolean normalize) {
		return new PCAProjector(data, retainedVariance, normalize);
	}

	/**
	 * Create a simple normalizer to rescale input data.
	 * 
	 * @param normalization the method of normalization to apply
	 * @param samples the input samples used to determine the normalization parameter
	 * @param missingValue an optional value that may be used to replace non-finite (i.e. missing) feature values
	 * @return a {@link Normalizer} that may be applied to new data
	 */
	public static Normalizer createNormalizer(final Normalization normalization, final Mat samples, final double missingValue) {

		Mat features;
		if (samples.channels() == 1)
			features = samples;
		else
			features = samples.reshape(1, samples.rows() * samples.cols());

		int nSamples = features.rows();
		int nFeatures = features.cols();

		var offsets = new double[nFeatures];
		var scales = new double[nFeatures];
		Arrays.fill(scales, 1.0);

		if (normalization == Normalization.NONE) {
			return Normalizer.createNormalizer(offsets, scales, missingValue);
		}

		var indexer = samples.createIndexer();
		var inds = new long[2];
		for (int c = 0; c < nFeatures; c++) {
			var stats = new RunningStatistics();
			inds[1] = c;
			for (int r = 0; r < nSamples; r++) {
				inds[0] = r;
				var val = indexer.getDouble(inds);
				if (Double.isFinite(val))
					stats.addValue(val);
			}
			offsets[c] = 0.0;
			scales[c] = 1.0;
			if (stats.size() > 0) {
				if (normalization == Normalization.MEAN_VARIANCE) {
					offsets[c] = -stats.getMean();
					scales[c] = 1.0/stats.getStdDev();
				} else if (normalization == Normalization.MIN_MAX) {
					offsets[c] = -stats.getMin();
					scales[c] = 1.0/(stats.getMax() - stats.getMin());
				}
			}
		}
		indexer.release();

		if (features != samples)
			features.release();

		return Normalizer.createNormalizer(offsets, scales, missingValue);
	}


	/**
	 * Apply a {@link Normalizer} to new training data samples.
	 * Features may be either columns or channels.
	 * @param samples the input data
	 * @param normalizer the normalizer to apply
	 */
	public static void normalize(final Mat samples, final Normalizer normalizer) {
		if (normalizer == null || normalizer.isIdentity()) {
			// If we have an identify normalizer, 
			if (Double.isFinite(normalizer.getMissingValue())) {
				opencv_core.patchNaNs(samples, normalizer.getMissingValue());
			}
			return;
		}

		boolean doChannels = samples.channels() > 1 && samples.channels() == normalizer.nFeatures();

		// It makes a major difference to performance if these values are extracted rather than requested in the loops
		int nChannels = samples.channels();
		int nRows = samples.rows();
		int nCols = samples.cols();

		//		FloatIndexer indexer = samples.createIndexer();
		//		for (int channel = 0; channel < nChannels; channel++) {
		//			for (int row = 0; row < nRows; row++) {
		//				for (int col = 0; col < nCols; col++) {
		//					int f = doChannels ? channel : col;
		//					double val = indexer.get(row, col, channel);
		//					val = normalizer.normalizeFeature(f, val);
		//					indexer.put(row, col, channel, (float)val);
		//				}				
		//			}			
		//		}
		//		indexer.release();

		var indexer = samples.createIndexer();
		var inds = new long[3];

		for (int channel = 0; channel < nChannels; channel++) {
			inds[2] = channel;
			for (int row = 0; row < nRows; row++) {
				inds[0] = row;
				for (int col = 0; col < nCols; col++) {
					inds[1] = col;
					int f = doChannels ? channel : col;
					double val = indexer.getDouble(inds);
					val = normalizer.normalizeFeature(f, val);
					indexer.putDouble(inds, val);
				}				
			}			
		}
		indexer.release();	
	}


	/**
	 * Helper class to apply PCA projection.
	 */
	@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
	public static class PCAProjector implements AutoCloseable {

		private static final Logger logger = LoggerFactory.getLogger(PCAProjector.class);

		private static final double DEFAULT_EPSILON = 1e-5;

		@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
		private Mat mean = new Mat();

		@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
		private Mat eigenvectors = new Mat();

		@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
		private Mat eigenvalues = new Mat();

		@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
		private transient Mat eigenvaluesSqrt;

		private boolean normalize = true;


		/**
		 * Apply PCA for dimensionality reduction.
		 * 
		 * @param data the data from which eigenvectors should be computed
		 * @param retainedVariance value between 0 and 1
		 * @param normalize applied during {@link #project(Mat, Mat)}
		 */
		PCAProjector(Mat data, double retainedVariance, boolean normalize) {
			this.normalize = normalize;
			opencv_core.PCACompute2(data, mean, eigenvectors, eigenvalues, retainedVariance);
			//			System.err.println(mean.createIndexer());
			logger.info("Reduced dimensions from {} to {}",data.cols(), eigenvectors.rows());
		}
		
		/**
		 * Number of output components.
		 * @return
		 */
		public int nComponents() {
			return eigenvectors == null ? 0 : eigenvectors.rows();
		}

		/**
		 * Apply the projection.
		 * @param data input data
		 * @param result output
		 */
		public void project(Mat data, Mat result) {
			opencv_core.PCAProject(data, mean, eigenvectors, result);
			if (normalize)
				doNormalize(result);
		}

		void doNormalize(Mat result) {
			if (eigenvaluesSqrt == null) {
				eigenvaluesSqrt = new Mat();
				eigenvalues.copyTo(eigenvaluesSqrt);
				opencv_core.add(eigenvaluesSqrt, Scalar.all(DEFAULT_EPSILON));
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

}