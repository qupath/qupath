/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.analysis.features;

import java.util.Arrays;

/**
 * Data structure to hold cooccurrence matrices for computation of Haralick features.
 * 
 * @author Pete Bankhead
 *
 */
public class CoocurranceMatrices {
	
	private CoocMatrix mat0, mat45, mat90, mat135;
	
	private HaralickFeatures[] features = null;
	
	/**
	 * Initialize coocurrence matrices.
	 * @param n number of bins
	 */
	public CoocurranceMatrices(int n) {
		mat0 = new CoocMatrix(n);
		mat45 = new CoocMatrix(n);
		mat90 = new CoocMatrix(n);
		mat135 = new CoocMatrix(n);
	}
	
	/**
	 * Record coocurrence for a (binned) value pair without rotation
	 * @param i
	 * @param j
	 */
	public void put0(int i, int j) {
		if (i >= 0 && j >= 0)
			mat0.addToEntrySymmetric(i, j);
	}

	/**
	 * Record coocurrence for a (binned) value pair with 45 degree rotation
	 * @param i
	 * @param j
	 */
	public void put45(int i, int j) {
		if (i >= 0 && j >= 0)
			mat45.addToEntrySymmetric(i, j);
	}

	/**
	 * Record coocurrence for a (binned) value pair with 90 degree rotation
	 * @param i
	 * @param j
	 */
	public void put90(int i, int j) {
		if (i >= 0 && j >= 0)
			mat90.addToEntrySymmetric(i, j);
	}

	/**
	 * Record coocurrence for a (binned) value pair with 135 degree rotation
	 * @param i
	 * @param j
	 */
	public void put135(int i, int j) {
		if (i >= 0 && j >= 0)
			mat135.addToEntrySymmetric(i, j);
	}
	
	/**
	 * Compute features.
	 */
	public void computeFeatures() {
		features = new HaralickFeatures[4];
		features[0] = new HaralickFeatures(mat0);
		features[1] = new HaralickFeatures(mat45);
		features[2] = new HaralickFeatures(mat90);
		features[3] = new HaralickFeatures(mat135);
//		double n2 = mat0.getN()*mat0.getN();
//		System.out.println("Num entries: " + mat0.getMean()*n2 + ", " + mat45.getMean()*n2 + ", " + mat90.getMean()*n2 + ", " + mat135.getMean()*n2);
	}
	
	/**
	 * Compute averaged features over all four rotations
	 * @return
	 */
	public HaralickFeatures getMeanFeatures() {
		if (features == null)
			computeFeatures();
		HaralickFeatures featuresMean = new HaralickFeatures(null);
		double[] featuresMeanArray = featuresMean.features();
		for (int i = 0; i < features.length; i++) {
			double[] featuresTemp = features[i].features();
			int ind = 0;
			for (double val : featuresTemp) {
				featuresMeanArray[ind] += val/4;
				ind++;
			}
		}
		return featuresMean;
	}
	
	/**
	 * Compute minimum features from all four rotations
	 * @return
	 */
	public HaralickFeatures getMinFeatures() {
		if (features == null)
			computeFeatures();
		HaralickFeatures featuresMin = new HaralickFeatures(null);
		double[] featuresMinArray = featuresMin.features();
		Arrays.fill(featuresMinArray, Float.NaN);
		for (int i = 0; i < features.length; i++) {
			double[] featuresTemp = features[i].features();
			int ind = 0;
			for (double val : featuresTemp) {
				if (!(featuresMinArray[ind] < val))
					featuresMinArray[ind] = val;
				ind++;
			}
		}
		return featuresMin;
	}
	
	/**
	 * Compute maximum features from all four rotations
	 * @return
	 */
	public HaralickFeatures getMaxFeatures() {
		if (features == null)
			computeFeatures();
		HaralickFeatures featuresMax = new HaralickFeatures(null);
		double[] featuresMaxArray = featuresMax.features();
		Arrays.fill(featuresMaxArray, Float.NaN);
		for (int i = 0; i < features.length; i++) {
			double[] featuresTemp = features[i].features();
			int ind = 0;
			for (double val : featuresTemp) {
				if (!(featuresMaxArray[ind] > val))
					featuresMaxArray[ind] = val;
				ind++;
			}
		}
		return featuresMax;
	}

}