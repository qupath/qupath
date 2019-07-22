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

import qupath.lib.analysis.stats.RunningStatistics;

/**
 * Helper class for computing Haralick features given a cooccurrence matrix.
 * 
 * @author Pete Bankhead
 *
 */
public class HaralickFeatures {
	
	/**
	 * Value of {@code Math.log(2)} (natural logarithm).
	 */
	private final static double LOG2 = Math.log(2);
	// Add a small constant to avoid log of zero
//	public final static double eps = 0.0000001;
	
	private CoocMatrix matrix;
	
	private double[] f = new double[13];
	
	private final static String[] FEATURE_NAMES = new String[]{
		"Angular second moment",
		"Contrast",
		"Correlation",
		"Sum of squares",
		"Inverse difference moment",
		"Sum average",
		"Sum variance",
		"Sum entropy",
		"Entropy",
		"Difference variance",
		"Difference entropy",
		"Information measure of correlation 1",
		"Information measure of correlation 2"
	};
	
	/**
	 * Constructor.
	 * @param matrix precomputed co-occurrence matrix.
	 */
	HaralickFeatures(final CoocMatrix matrix) {
		this.matrix = matrix;
		computeFeatures();
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (double d : f)
			sb.append(d + ", ");
		return sb.toString();
	}
	
	private static RunningStatistics getStatistics(double[] array) {
		RunningStatistics stats = new RunningStatistics();
		for (double d : array)
			stats.addValue(d);
		return stats;
	}
		
	private void computeFeatures() {
		if (matrix == null)
			return;
		
//		IJ.log(matrix.toString());
				
		double[] px, py, px_and_y, px_y;

		
		matrix.finalizeMatrix();
		int n = matrix.getN();
		
		// Normalize to sum while computing required vectors
		px = new double[n];
		py = new double[n];
		px_and_y = new double[2*n+1];
		px_y = new double[n];
		double mx = 0; 
		double my = 0;  
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double val = matrix.get(i, j);
				px[i] += val;
				py[j] += val;
				px_and_y[i + j] += val;
				px_y[Math.abs(i - j)] += val;
				
				mx += (i + 1) * val; 
				my += (j + 1) * val;
			}
		}
		
		// Standard deviations for marginal-probability matrices
		double sx = 0;
		double sy = 0;
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= n; j++) {
				double val = matrix.get(i-1, j-1);
				sx += (i - mx) * (i - mx) * val;
				sy += (j - my) * (j - my) * val;
			}
		}
		sx = Math.sqrt(sx);
		sy = Math.sqrt(sy);
		
		// Compute textural features
		
		// Angular second moment (f1)
		// Correlation (f3)
		// Inverse difference moment (f5)
		// Entropy (f9)
		double f1 = 0;
		double f3 = 0;
		double f5 = 0;
		double f9 = 0;
				
		double Hxy1 = 0; // Hxy1 & Hxy2 for (more) entropies
		double Hxy2 = 0;
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= n; j++) {
				double val = matrix.get(i-1, j-1);
				double logVal = matrix.getLog(i-1, j-1) / LOG2;
				f1 += val * val;
				
				f3 += i*j * val;
				
				f5 += val / (1 + (i - j)*(i - j));
				
				f9 -= val * logVal;
				
				double temp = px[i-1] * py[j-1];
				if (temp != 0) {
					double logTemp = Math.log(temp) / LOG2;
					Hxy1 -= val * logTemp;
					Hxy2 -= temp * logTemp;
				}
				
			}
		}
		double Hxy = f9;
		f3 -= mx*my;
		f3 /= sx*sy;
		
		// Sum of squares (f4)
		double f4 = sx*sx;
				
		// Contrast (f2)
		double f2 = 0;
		for (int nn = 0; nn < n; nn++) {
			f2 += nn * nn * px_y[nn];
		}
		
		// Sum average (f6)
		// Sum entropy (f8)
		double f6 = 0;
		double f8 = 0;
		for (int i = 2; i <= 2*n; i++) {
			double val = px_and_y[i];
			if (val != 0) {
				//J double logVal = Math.log(val) / LOG2;
				f6 += i * val;
				f8 -= val * (Math.log(val) / LOG2); //J
			}
		}
		
		// Sum variance (f7)
		double f7 = 0;
		for (int i = 2; i <= 2*n; i++) {
			//J double val = px_and_y[i];
			// f6 rather than f8 in Haralick's original paper... see
			// https://github.com/CellProfiler/CellProfiler/blob/master/cellprofiler/cpmath/haralick.py and
			// http://xy-27.pythonxy.googlecode.com/hg-history/fec21bbbbd9f0a71cca43858991f4c468c6ce211/src/python/mahotas/PLATLIB/mahotas/features/texture.py
			f7 += (i - f6) * (i - f6) * px_and_y[i]; //J
//			f7 += (i - f8)*(i - f8)*val;
		}
		
		// Difference entropy (f11)
		double f11 = 0;
		for (int i = 0; i < n; i++) {
			double val = px_y[i];
			if (val != 0)
				f11 -= val * (Math.log(val) / LOG2); //J
		}
		
		// Difference variance (f10)
		RunningStatistics px_yStats = getStatistics(px_y);
		double f10 = px_yStats.getVariance();
		

		double Hx = 0; // Hx & Hy for entropies
		double Hy = 0;
		for (int i = 0; i < n; i++) {
			double val = px[i];
			if (val != 0)
				Hx -= val * Math.log(val)/LOG2;
			val = py[i];
			if (val != 0)
				Hy -= val * Math.log(val)/LOG2;
		}
//		IJ.log(String.format("%.3f, %.3f, %.3f, %.3f, %.3f, ", Hx, Hy, Hxy, Hxy1, Hxy2));
		// Information measures of correlation
		double f12 = (Hxy - Hxy1) / Math.max(Hx, Hy);
		double f13 = Math.sqrt(1 - Math.exp(-2 * (Hxy2 - Hxy)));
		
		
		f[0] = f1; // Agrees with Mahotas
		f[1] = f2; // Agrees
		f[2] = f3;
		
		f[3] = f4;

		f[4] = f5; // Agrees
		f[5] = f6; // Agrees (almost?)
		f[6] = f7; // Agrees
		f[7] = f8; // Agrees
		f[8] = f9; // Agrees
		
		f[9] = f10; // Agrees except for variance definition (I think)

		f[10] = f11; // Agrees

		f[11] = f12; // Agrees (apart from eps)
		f[12] = f13; // Agrees (apart from eps)
	}

	//J alternative function with different mean and SD calculations - to compare with above for performance
	private void computeFeaturesJ() {
		if (matrix == null)
			return;
		
//		IJ.log(matrix.toString());
				
		double[] px, py, px_and_y, px_y;

		
		matrix.finalizeMatrix();
		int n = matrix.getN();
		
		// Normalize to sum while computing required vectors
		px = new double[n];
		py = new double[n];
		px_and_y = new double[2*n+1];
		px_y = new double[n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double val = matrix.get(i, j);
				px[i] += val;
				py[j] += val;
				px_and_y[i + j] += val;
				px_y[Math.abs(i - j)] += val;
				
				//J mx += (i + 1) * val; 
				//J my += (j + 1) * val;
			}
		}
		
		// Mean values for marginal-probability matrices
		double mx = 0; //J
		double my = 0; //J 
		for (int i = 0; i < n; i++){ //J
			mx += px[i]; //J
			my += py[i]; //J
		} //J
		mx /= n; //J there is an implicit cast here - keep an eye on this
		my /= n; //J		
		
		// Standard deviations for marginal-probability matrices
		double sx = 0;
		double sy = 0;
//J		for (int i = 1; i <= n; i++) {
//J			for (int j = 1; j <= n; j++) {
//J				double val = matrix.get(i-1, j-1);
//J				sx += (i - mx) * (i - mx) * val;
//J				sy += (j - my) * (j - my) * val;
//J			}
//J		}
//J		sx = Math.sqrt(sx);
//J		sy = Math.sqrt(sy);
		for (int i = 0; i < n; i++){ //J
			sx += (px[i] - mx) * (px[i] - mx); //J
			sy += (py[i] - my) * (py[i] - my); //J
		} //J
		sx = Math.sqrt(sx/(n-1)); //J
		sy = Math.sqrt(sy/(n-1)); //J
		
		// Compute textural features
		
		// Angular second moment (f1)
		// Correlation (f3)
		// Inverse difference moment (f5)
		// Entropy (f9)
		double f1 = 0;
		double f3 = 0;
		double f5 = 0;
		double f9 = 0;
				
		double Hxy1 = 0; // Hxy1 & Hxy2 for (more) entropies
		double Hxy2 = 0;
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= n; j++) {
				double val = matrix.get(i-1, j-1);
				double logVal = matrix.getLog(i-1, j-1) / LOG2;
				f1 += val * val;
				
				f3 += i*j * val;
				
				f5 += val / (1 + (i - j)*(i - j));
				
				f9 -= val * logVal;
				
				double temp = px[i-1] * py[j-1];
				if (temp != 0) {
					double logTemp = Math.log(temp) / LOG2;
					Hxy1 -= val * logTemp;
					Hxy2 -= temp * logTemp;
				}
				
			}
		}
		double Hxy = f9;
		f3 -= mx*my;
		f3 /= sx*sy;
		
		// Sum of squares (f4)
		double f4 = sx*sx;
				
		// Contrast (f2)
		double f2 = 0;
		for (int nn = 0; nn < n; nn++) {
			f2 += nn * nn * px_y[nn];
		}
		
		// Sum average (f6)
		// Sum entropy (f8)
		double f6 = 0;
		double f8 = 0;
		for (int i = 2; i <= 2*n; i++) {
			double val = px_and_y[i];
			if (val != 0) {
				//J double logVal = Math.log(val) / LOG2;
				f6 += i * val;
				f8 -= val * (Math.log(val) / LOG2); //J
			}
		}
		
		// Sum variance (f7)
		double f7 = 0;
		for (int i = 2; i <= 2*n; i++) {
			//J double val = px_and_y[i];
			// f6 rather than f8 in Haralick's original paper... see
			// https://github.com/CellProfiler/CellProfiler/blob/master/cellprofiler/cpmath/haralick.py and
			// http://xy-27.pythonxy.googlecode.com/hg-history/fec21bbbbd9f0a71cca43858991f4c468c6ce211/src/python/mahotas/PLATLIB/mahotas/features/texture.py
			f7 += (i - f6) * (i - f6) * px_and_y[i]; //J
//			f7 += (i - f8)*(i - f8)*val;
		}
		
		// Difference entropy (f11)
		double f11 = 0;
		for (int i = 0; i < n; i++) {
			double val = px_y[i];
			if (val != 0)
				f11 -= val * (Math.log(val) / LOG2); //J
		}
		
		// Difference variance (f10)
		RunningStatistics px_yStats = getStatistics(px_y);
		double f10 = px_yStats.getVariance();
		

		double Hx = 0; // Hx & Hy for entropies
		double Hy = 0;
		for (int i = 0; i < n; i++) {
			double val = px[i];
			if (val != 0)
				Hx -= val * Math.log(val)/LOG2;
			val = py[i];
			if (val != 0)
				Hy -= val * Math.log(val)/LOG2;
		}
//		IJ.log(String.format("%.3f, %.3f, %.3f, %.3f, %.3f, ", Hx, Hy, Hxy, Hxy1, Hxy2));
		// Information measures of correlation
		double f12 = (Hxy - Hxy1) / Math.max(Hx, Hy);
		double f13 = Math.sqrt(1 - Math.exp(-2 * (Hxy2 - Hxy)));
		
		
		f[0] = f1; // Agrees with Mahotas
		f[1] = f2; // Agrees
		f[2] = f3;
		
		f[3] = f4;

		f[4] = f5; // Agrees
		f[5] = f6; // Agrees (almost?)
		f[6] = f7; // Agrees
		f[7] = f8; // Agrees
		f[8] = f9; // Agrees
		
		f[9] = f10; // Agrees except for variance definition (I think)

		f[10] = f11; // Agrees

		f[11] = f12; // Agrees (apart from eps)
		f[12] = f13; // Agrees (apart from eps)
	}
	
	/**
	 * Get the name of the specified feature.
	 * @param n
	 * @return
	 */
	public String getFeatureName(int n) {
		return FEATURE_NAMES[n];
	}
	
	/**
	 * Total number of Haralick features.
	 * @return
	 */
	public int nFeatures() {
		return 13;
	}

	/**
	 * Get the value of the specified feature.
	 * @param n
	 * @return
	 */
	public double getFeature(int n) {
		return f[n];
	}
	
	double[] features() {
		return f;
	}
	
} 