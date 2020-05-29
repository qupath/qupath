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

/**
 * Class to help with simple feature normalization, by adding an offset and then multiplying by a scaling factor.
 * 
 * @author Pete Bankhead
 *
 */
public class Normalizer {
	
	private double[] offsets;
	private double[] scales;
	private Double missingValue; // Use Double to simplify life with JSON serialization (lest it be NaN)
	private transient Boolean isIdentity;
	
	private Normalizer(double[] offsets, double[] scales, double missingValue) {
		if (offsets.length != scales.length)
			throw new IllegalArgumentException("Length of offsets and scales arrays do not match!");
		this.missingValue = Double.isNaN(missingValue) ? null : missingValue;
		this.offsets = offsets.clone();
		this.scales = scales.clone();
	}
	
	/**
	 * Create a {@link Normalizer} with the specified parameters.
	 * @param offsets value to add to each feature
	 * @param scales value to multiply each feature, after applying the offset
	 * @param missingValue replacement value for non-finite features
	 * @return a {@link Normalizer} initialized accordingly
	 */
	public static Normalizer createNormalizer(double[] offsets, double[] scales, double missingValue) {
		return new Normalizer(offsets, scales, missingValue);
	}
	
	/**
	 * Normalize a single feature.
	 * @param idx the index of the feature; this is required to identify the corresponding offset and scale
	 * @param originalValue the original value of the feature
	 * @return the normalized value of the feature
	 */
	public double normalizeFeature(int idx, double originalValue) {
		double val = originalValue;
		if (!isIdentity())
			val = (originalValue + offsets[idx]) * scales[idx];
		if (Double.isFinite(val))
			return val;
		else
			return getMissingValue();
	}
	
	/**
	 * Test is all entries of an array are identical to a specified value.
	 * @param array
	 * @param val
	 * @return true if {@code array[i] == val} for all i within the array, false otherwise.
	 */
	private static boolean allEqual(double[] array, double val) {
		for (double d : array) {
			if (d != val)
				return false;
		}
		return true;
	}
	
	/**
	 * Returns true if this normalizer does not actually do anything.
	 * This is the case if all offsets are zero and all scales are 1.
	 * @return
	 */
	public boolean isIdentity() {
		if (isIdentity == null) {
			isIdentity = Boolean.valueOf(allEqual(offsets, 0) && allEqual(scales, 1));			
		}
		return isIdentity.booleanValue();
	}
	
	/**
	 * Return the value that will be output after normalization if the computed value is not finite.
	 * @return
	 */
	public double getMissingValue() {
		return missingValue == null ? Double.NaN : missingValue.doubleValue();
	}
	
	/**
	 * The total number of features supported by this {@link Normalizer}
	 * @return
	 */
	public int nFeatures() {
		return scales.length;
	}
	
	/**
	 * Get the offset for the specified feature
	 * @param ind index of the feature
	 * @return
	 */
	public double getOffset(int ind) {
		return offsets[ind];
	}

	/**
	 * Get the scale factor for the specified feature
	 * @param ind index of the feature
	 * @return
	 */
	public double getScale(int ind) {
		return scales[ind];
	}
	
}