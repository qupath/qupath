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

package qupath.opencv.ml;

import org.bytedeco.opencv.opencv_core.Mat;

import qupath.lib.classifiers.Normalization;
import qupath.opencv.ml.objects.features.Normalizer;
import qupath.opencv.ml.objects.features.Preprocessing;
import qupath.opencv.ml.objects.features.Preprocessing.PCAProjector;

/**
 * Create a preprocessor for an image or training matrix.
 * This can include simple normalization (rescaling) and PCA projection.
 */
public class FeaturePreprocessor {
	
	private Normalizer normalizer;
	private PCAProjector pca;
	private int inputLength;
	private int outputLength;
	
	/**
	 * Apply preprocessing in-place.
	 * @param mat
	 * @param channelFeatures treat each channel as a feature; otherwise, treat each column as a feature
	 */
	public void apply(Mat mat, boolean channelFeatures) {
		int rows = mat.rows();
		int cols = mat.cols();
		if (channelFeatures)
			mat.put(mat.reshape(1, rows * cols));
		if (normalizer != null)
			Preprocessing.normalize(mat, normalizer);
		if (pca != null)
			pca.project(mat, mat);
		if (channelFeatures)
			mat.put(mat.reshape((int)(mat.total()/(rows*cols)), rows));
	}

	/**
	 * Apply preprocessing in-place.
	 * If the {@link Mat} has multiple channels, each channel is treated as a feature.
	 * Otherwise, each row is treated as a feature.
	 * @param mat
	 */
	public void apply(Mat mat) {
		apply(mat, mat.channels() > 1);
	}
	
	/**
	 * Returns true if this preprocessor transforms the features beyond a simple normalization.
	 * In practice, for the current implementation this means PCA.
	 * @return
	 */
	public boolean doesFeatureTransform() {
		return pca != null;
	}
	
	/**
	 * Returns true if this preprocessor has any effect.
	 * @return
	 */
	public boolean doesSomething() {
		return normalizer != null || pca != null;
	}
	
	/**
	 * Get the number of features required of the input.
	 * @return
	 */
	public int getInputLength() {
		return inputLength;
	}
	
	/**
	 * Get the number of features expected in the output.
	 * @return
	 */
	public int getOutputLength() {
		return outputLength;
	}
	
	/**
	 * Create a {@link Builder} to build a custom {@link FeaturePreprocessor}.
	 * @return
	 */
	public static FeaturePreprocessor.Builder builder() {
		return new Builder();
	}
	
	@Override
	public String toString() {
		String name = "FeaturePreprocessor";
		if (!doesSomething())
			return name + " (null)";
		if (normalizer != null) {
			if (pca != null)
				return name + " (" + normalizer + ", " + pca + ")";
			else
				return name + " (" + normalizer + ")";
		} else
			return name + " (" + pca + ")";
	}
	
	
	/**
	 * Builder to create a custom {@link FeaturePreprocessor}.
	 */
	public static class Builder {
		
		private Normalization normalization = Normalization.NONE;
		private double missingValue = Double.NaN;
		private double pcaRetainedVariance = -1;
		private boolean pcaNormalize = true;
		
		private Normalizer normalizer;
		private PCAProjector pca;
		
		private Builder() {}
		
		/**
		 * Define normalization type.
		 * @param normalization
		 * @return this builder
		 */
		public FeaturePreprocessor.Builder normalize(Normalization normalization) {
			this.normalization = normalization;
			return this;
		}
		
		/**
		 * 
		 * @param missingValue
		 * @return this builder
		 */
		public FeaturePreprocessor.Builder missingValue(double missingValue) {
			this.missingValue = missingValue;
			return this;
		}
	 
		/**
		 * Perform PCA to reduce features.
		 * @param retainedVariance retained variance, used to determine how many features to keep
		 * @param pcaNormalize if true, normalize the projected features
		 * @return this builder
		 */
		public FeaturePreprocessor.Builder pca(double retainedVariance, boolean pcaNormalize) {
			this.pcaRetainedVariance = retainedVariance;
			this.pcaNormalize = pcaNormalize;
			return this;
		}
		
		/**
		 * Build a {@link FeaturePreprocessor}.
		 * The training data is expected to contain samples as rows and features as columns.
		 * @param trainingData
		 * @param applyToTraining
		 * @return
		 */
		public FeaturePreprocessor build(Mat trainingData, boolean applyToTraining) {
			var features = new FeaturePreprocessor();
			features.inputLength = trainingData.cols();
			features.outputLength = trainingData.cols();
			if (normalization != Normalization.NONE || !Double.isNaN(missingValue)) {
				this.normalizer = Preprocessing.createNormalizer(normalization, trainingData, missingValue);
				if (applyToTraining)
					Preprocessing.normalize(trainingData, normalizer);						
			}
			
			if (pcaRetainedVariance > 0) {
				this.pca = Preprocessing.createPCAProjector(trainingData, pcaRetainedVariance, pcaNormalize);
				features.outputLength = this.pca.nComponents();
				if (applyToTraining)
					this.pca.project(trainingData, trainingData);
			}
			features.normalizer = this.normalizer;
			features.pca = this.pca;
			return features;
		}
		
	}
	
}