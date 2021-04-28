/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.opencv.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.DoubleBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.Test;

import qupath.opencv.tools.MultiscaleFeatures.Hessian;

@SuppressWarnings("javadoc")
public class TestMultiscaleFeatures {

	@Test
	public void test_eigenvalues() {
		var rng = new Random(100L);
		
		try (var scope = new PointerScope()) {
		
			for (int type : new int[] {opencv_core.CV_32FC1, opencv_core.CV_64FC1}) {
			
				var mat = new Mat(3, 3, type);
				var idx = mat.createIndexer();
				for (int i = 0; i < 10; i++) {
					// Create a random 3x3 matrix
					double dxx = rng.nextDouble();
					double dxy = rng.nextDouble();
					double dxz = rng.nextDouble();
					double dyy = rng.nextDouble();
					double dyz = rng.nextDouble();
					double dzz = rng.nextDouble();
					idx.putDouble(new long[] {0, 0}, dxx);
					idx.putDouble(new long[] {0, 1}, dxy);
					idx.putDouble(new long[] {0, 2}, dxz);
					idx.putDouble(new long[] {1, 0}, dxy);
					idx.putDouble(new long[] {1, 1}, dyy);
					idx.putDouble(new long[] {1, 2}, dyz);
					idx.putDouble(new long[] {2, 0}, dxz);
					idx.putDouble(new long[] {2, 1}, dyz);
					idx.putDouble(new long[] {2, 2}, dzz);
					
					var matDxx = mat.row(0).col(0);
					var matDxy = mat.row(0).col(1);
					var matDxz = mat.row(0).col(2);
					var matDyy = mat.row(1).col(1);
					var matDyz = mat.row(1).col(2);
					var matDzz = mat.row(2).col(2);
					
					Hessian hessian;
					double[] eigenvalues;
					double[][] eigenvectors;
					
					var mat2D = mat.rowRange(0, 2).colRange(0, 2);
					mat2D = OpenCVTools.ensureContinuous(mat2D, false);
					
					// Compute 2D eigenvalues & eigenvectors
					hessian = new MultiscaleFeatures.Hessian2D(matDxx, matDxy, matDyy, false);
					eigenvalues = getValues(hessian.getEigenvalues(false));
					assertTrue(eigenvalues[0] >= eigenvalues[1]);
					eigenvalues = getValues(hessian.getEigenvalues(true));
					assertTrue(Math.abs(eigenvalues[0]) >= Math.abs(eigenvalues[1]));
					
					hessian = new MultiscaleFeatures.Hessian2D(matDxx, matDxy, matDyy, true);
					eigenvalues = getValues(hessian.getEigenvalues(false));
					assertTrue(eigenvalues[0] >= eigenvalues[1]);
									
					eigenvectors = getAllValues(hessian.getEigenvectors(false));
					for (int v = 0; v < 2; v++) {
						assertEquals(length(eigenvectors[v]), 1.0, 1e-6);
						assertArrayEquals(
								multiply(eigenvectors[v], eigenvalues[v]),
								new double[] {
										dot(getAllValues(mat2D.row(0)), eigenvectors[v]),
										dot(getAllValues(mat2D.row(1)), eigenvectors[v])
								},
								1e-6
								);
					}				
					
					eigenvalues = getValues(hessian.getEigenvalues(true));
					assertTrue(Math.abs(eigenvalues[0]) >= Math.abs(eigenvalues[1]));
					
					eigenvectors = getAllValues(hessian.getEigenvectors(true));
					for (int v = 0; v < 2; v++) {
						assertEquals(length(eigenvectors[v]), 1.0, 1e-6);
						assertArrayEquals(
								multiply(eigenvectors[v], eigenvalues[v]),
								new double[] {
										dot(getAllValues(mat2D.row(0)), eigenvectors[v]),
										dot(getAllValues(mat2D.row(1)), eigenvectors[v])
								},
								1e-6
								);
					}
					
					
					// Compute 3D eigenvalues & eigenvectors
					hessian = new MultiscaleFeatures.Hessian3D(matDxx, matDxy, matDxz, matDyy, matDyz, matDzz, false);
					eigenvalues = getValues(hessian.getEigenvalues(false));
					assertTrue(eigenvalues[0] >= eigenvalues[1]);
					assertTrue(eigenvalues[1] >= eigenvalues[2]);
					eigenvalues = getValues(hessian.getEigenvalues(true));
					assertTrue(Math.abs(eigenvalues[0]) >= Math.abs(eigenvalues[1]));
					assertTrue(Math.abs(eigenvalues[1]) >= Math.abs(eigenvalues[2]));
					
					hessian = new MultiscaleFeatures.Hessian3D(matDxx, matDxy, matDxz, matDyy, matDyz, matDzz, true);
					eigenvalues = getValues(hessian.getEigenvalues(false));
					assertTrue(eigenvalues[0] >= eigenvalues[1]);
					assertTrue(eigenvalues[1] >= eigenvalues[2]);
					
					eigenvectors = getAllValues(hessian.getEigenvectors(false));
					for (int v = 0; v < 3; v++) {
						assertEquals(length(eigenvectors[v]), 1.0, 1e-6);
						assertArrayEquals(
								multiply(eigenvectors[v], eigenvalues[v]),
								new double[] {
										dot(getAllValues(mat.row(0)), eigenvectors[v]),
										dot(getAllValues(mat.row(1)), eigenvectors[v]),
										dot(getAllValues(mat.row(2)), eigenvectors[v])
								},
								1e-6
								);
					}
					
					eigenvalues = getValues(hessian.getEigenvalues(true));
					eigenvectors = getAllValues(hessian.getEigenvectors(true));
					assertTrue(Math.abs(eigenvalues[0]) >= Math.abs(eigenvalues[1]));
					assertTrue(Math.abs(eigenvalues[1]) >= Math.abs(eigenvalues[2]));
					
					for (int v = 0; v < 3; v++) {
						assertArrayEquals(
								multiply(eigenvectors[v], eigenvalues[v]),
								new double[] {
										dot(getAllValues(mat.row(0)), eigenvectors[v]),
										dot(getAllValues(mat.row(1)), eigenvectors[v]),
										dot(getAllValues(mat.row(2)), eigenvectors[v])
								},
								1e-6
								);
					}
					
				}
				
			}
			
		}
	}
	
	
	
	@Test
	public void test_features2D() {
		
		try (var scope = new PointerScope()) {
			
			for (int type : new int[] {opencv_core.CV_32FC1, opencv_core.CV_64FC1}) {
				
				var mat = new Mat(512, 768, type);
				opencv_core.randn(mat, new Mat(1, 1, type, Scalar.ONE), new Mat(1, 1, type, Scalar.ONE));

				var featureMap = new MultiscaleFeatures.MultiscaleResultsBuilder()
						.sigmaX(5.0)
						.gaussianSmoothed(true)
						.retainHessian(true)
						.weightedStdDev(true)
						.gradientMagnitude(true)
						.hessianDeterminant(true)
						.hessianEigenvalues(true)
						.laplacianOfGaussian(true)
						.structureTensorEigenvalues(true)
						.build(mat);
				
				for (var feature : MultiscaleFeatures.MultiscaleFeature.values()) {
					if (feature.supports2D()) {
						assertNotNull(featureMap.get(feature));
						// Currently, the output features are always 32-bit
						assertEquals(featureMap.get(feature).depth(), opencv_core.CV_32F);
					} else
						assertTrue(featureMap.get(feature) == null);						
				}
				
			}
			
		}
				
	}
	
	
	
	
	
	private static double getValue(Mat mat) {
		try (var idx = mat.createIndexer()) {
			return idx.getDouble(0L);
		}
	}
	
	private static double[] getAllValues(Mat mat) {
		var mat2 = new Mat();
		mat.convertTo(mat2, opencv_core.CV_64F);
		DoubleBuffer buffer = mat2.createBuffer();
		var values = new double[buffer.limit()];
		buffer.get(values);
		mat2.close();
		return values;
	}
	
	private static double[] getValues(List<Mat> mats) {
		double[] values = new double[mats.size()];
		int ind = 0;
		for (var m : mats)
			values[ind++] = getValue(m);
		return values;
	}
	
	private static double[][] getAllValues(List<Mat> mats) {
		double[][] values = new double[mats.size()][];
		int ind = 0;
		for (var m : mats)
			values[ind++] = getAllValues(m);
		return values;
	}
	
	private static double[] multiply(double[] values, double scalar) {
		return Arrays.stream(values).map(v -> v * scalar).toArray();
	}
	
	private static double dot(double[] v1, double[] v2) {
		assert v1.length == v2.length;
		double result = 0;
		for (int i = 0; i < v1.length; i++)
			result += v1[i] * v2[i];
		return result;
	}
	
	private static double length(double[] v1) {
		double result = 0;
		for (int i = 0; i < v1.length; i++)
			result += v1[i] * v1[i];
		return result;
	}
	

}