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

package qupath.process.gui.ml;

import org.bytedeco.opencv.opencv_core.Mat;

import qupath.lib.classifiers.Normalization;
import qupath.opencv.ml.FeaturePreprocessor;

/**
 * Helper class for storing parameters that will eventually be used to create a {@link FeaturePreprocessor}.
 * 
 * @author Pete Bankhead
 */
class FeatureNormalization {
	
	/**
     * Feature normalization method
     */
    private Normalization normalization = Normalization.NONE;
    
    /**
     * Feature normalization method. Applied before any dimensionality reduction.
     * @param normalization
     * @see #setPCARetainedVariance(double)
     * @see #setPCANormalize(boolean)
     */
    public void setNormalization(Normalization normalization) {
    	if (this.normalization == normalization)
    		return;
    	this.normalization = normalization;
    }
    
    /**
     * Get the current feature normalization method for preprocessing.
     * @return
     */
    public Normalization getNormalization() {
    	return normalization;
    }
    
    /**
     * Missing value for training data
     */
    private double missingValue = 0;
    
    /**
     * Retained variance for PCA
     */
    private double pcaRetained = 0;
    
    /**
     * Normalize features after dimensionality reduction using PCA
     */
    private boolean pcaNormalize = true;
       
    /**
     * Apply Principal Component Analysis for feature dimensionality reduction.
     * @param retained retained variance after dimensionality reduction; set &le; 0 if PCA should not be applied
     * @see #setNormalization(Normalization)
     */
    public void setPCARetainedVariance(double retained) {
    	if (pcaRetained == retained)
    		return;
    	pcaRetained = retained;
    }
    
    /**
     * Get the retained variance after applying PCA for dimensionality reduction
     * @return
     * @see #setPCARetainedVariance(double)
     */
    public double getPCARetainedVariance() {
    	return pcaRetained;
    }
    
    /**
     * Request features to be normalized <i>after</i> dimensionality reduction using PCA.
     * @param normalize
     * @see #setPCARetainedVariance(double)
     */
    public void setPCANormalize(boolean normalize) {
    	if (pcaNormalize == normalize)
    		return;
    	pcaNormalize = normalize;
    }

    /**
     * Query whether reduced features are normalized <i>after</i> PCA.
     * @return
     * @see #setPCANormalize(boolean)
     * @see #setPCARetainedVariance(double)
     */
    public boolean doPCANormalize() {
    	return pcaNormalize;
    }
    
    
    public FeaturePreprocessor build(Mat matTraining, boolean applyToTraining) {
    	return FeaturePreprocessor.builder()
    			.normalize(normalization)
    			.pca(pcaRetained, pcaNormalize)
    			.missingValue(missingValue)
    			.build(matTraining, applyToTraining);
    	
    }
	
}