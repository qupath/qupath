/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.color;

/**
 * Simple class for representing - and inverting a 3x3 matrix.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorDeconvMatrix3x3 {

	/*
	 * JAMA would be handy here, but we just want to invert a 3x3 matrix... 
	 * so we use an extra little class rather than introduce an extra dependency.
	 */

	private final double m11;
	private final double m12;
	private final double m13;
	private final double m21;
	private final double m22;
	private final double m23;
	private final double m31;
	private final double m32;
	private final double m33;
	
	/**
	 * Constructor for a 3x3 matrix.
	 * 
	 * @param M
	 * @throws IllegalArgumentException if the matrix is not 3x3
	 */
	public ColorDeconvMatrix3x3(double[][] M) throws IllegalArgumentException {
		if (M.length != 3 || M[0].length != 3 || M[1].length != 3 || M[2].length != 3)
			throw new IllegalArgumentException("Invalid matrix size! Must be 3x3.");
		m11 = M[0][0];
		m12 = M[0][1];
		m13 = M[0][2];
		m21 = M[1][0];
		m22 = M[1][1];
		m23 = M[1][2];
		m31 = M[2][0];
		m32 = M[2][1];
		m33 = M[2][2];
	}

	private static double det2(double a11, double a12, double a21, double a22) {
		return a11*a22 - a12*a21;
	}

	/**
	 * Calculate the determinant of the matrix.
	 * @return
	 */
	public double determinant() {
		return m11*det2(m22, m23, m32, m33) -
			m12*det2(m21, m23, m31, m33) +
			m13*det2(m21, m22, m31, m32);
	}

	/**
	 * Calculate the 3x3 matrix inverse.
	 * @return
	 */
	public double[][] inverse() {
		double invDet3 = 1/determinant();
		return new double[][]{
			{invDet3*det2(m22,m23,m32,m33), invDet3*det2(m13,m12,m33,m32), invDet3*det2(m12,m13,m22,m23)},
			{invDet3*det2(m23,m21,m33,m31), invDet3*det2(m11,m13,m31,m33), invDet3*det2(m13,m11,m23,m21)},
			{invDet3*det2(m21,m22,m31,m32), invDet3*det2(m12,m11,m32,m31), invDet3*det2(m11,m12,m21,m22)}};
	}
	
	@Override
	public String toString() {
		return "Matrix:\n " + m11 + ", " + m12 + ", " + m13 + "\n" + m21 + ", " + m22 + ", " + m23 + "\n" + m31 + ", " + m32 + ", " + m33 + "\n";
	}
	
}