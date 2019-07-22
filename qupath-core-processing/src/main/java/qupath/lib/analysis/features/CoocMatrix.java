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

package qupath.lib.analysis.features;

/**
 * Data structure for containing co-occurrence matrix for Haralick texture features.
 * 
 * @author Pete Bankhead
 *
 */
class CoocMatrix {
	
	private int[] mat;
	private int n;
	private int sum = 0;
	private double logSum = 0;
	// Compute all the logs we need in one go
	// TODO: Preallocate this!
	private static double[] logTable;
	
	static {
		
		logTable = new double[256*256];
		for (int i = 1; i < logTable.length; i++)
			logTable[i] = Math.log(i);
		
	}
	
	public CoocMatrix(int n) {
		this.n = n;
		this.mat = new int[n * n];
	}
	
	public int getN() {
		return n;
	}
	
	public void addToEntrySymmetric(int row, int col) {
		addToEntry(row, col);
		addToEntry(col, row);
	}
	
	public void addToEntry(int row, int col) {
		mat[row * n + col] += 1;
		sum++;
	}
	
	/**
	 * Call this after populating the matrix
	 */
	public void finalizeMatrix() {
		logSum = Math.log(sum);
//J		int max = 0;
//J		for (int v : mat)
//J			if (v > max)
//J				max = v; //J this is not really leading anywhere!
//		System.out.println("Logs with max " + max);
//		logTable = new double[max+1];
//		logTable[0] = Math.log(0.000001);
//		for (int i = 1; i < logTable.length; i++) {
//			logTable[i] = Math.log(i);
//		}
	}

	/**
	 * Return probability (i.e. value divided by sum)
	 * 
	 * @param row
	 * @param col
	 * @return
	 */
	public double get(int row, int col) {
		return (double)mat[row * n + col] / sum;
	}
	
	public int getRawCounts(int row, int col) {
		return mat[row * n + col];
	}

	/**
	 * Return log of probability value
	 * 
	 * @param row
	 * @param col
	 * @return
	 */
	public double getLog(int row, int col) {
		int ind = mat[row * n + col];
		if (ind < logTable.length)
			return logTable[ind] - logSum;
		else
			return Math.log(ind) - logSum;
	}

	public double getMean() {
		return (double)sum / (n * n);
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				sb.append(getRawCounts(i, j));
				if (j < n-1)
					sb.append(", ");
			}
			if (i < n-1)
				sb.append("\n");
		}
		sb.append("]");
		return sb.toString();
	}
	
}