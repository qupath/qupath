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

package qupath.opencv.ops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.junit.jupiter.api.Test;
import qupath.lib.io.GsonTools;
import qupath.opencv.tools.OpenCVTools;

@SuppressWarnings("javadoc")
public class TestImageOps {
	
	static {
		// Need to force class initialization
		new ImageOps();
	}
	
	/**
	 * Test the application and serialization of ImageOps.
	 */
	@Test
	public void testSerialization() {
		
		int size = 256;
		int nChannels = 3;
		int seed = 100;
		try (@SuppressWarnings("unchecked")
		var scope = new PointerScope()) {
			// Create a Mat with zeros, then add noise & check it made a difference
			var matZeros = createZerosMat(size, nChannels);
			var matNoise = matZeros.clone();
			assertTrue(matsEqual(matZeros, matNoise, 0));
			
			addNoise(matNoise, seed);
			assertFalse(matsEqual(matZeros, matNoise, 0));
			
			// Create an op & apply it to the noise Mat
			// Ensure it has an impact
			var op = createOp(1, 2);
			var matNoise2 = matNoise.clone();
			matNoise2 = op.apply(matNoise2);
			
			assertFalse(matsEqual(matNoise, matNoise2, 0));
			
			// Reconstruct the op from its JSON-serialized form and check it has the same output
			var json = GsonTools.getInstance(true).toJson(op, ImageOp.class);
			var op2 = GsonTools.getInstance().fromJson(json, ImageOp.class);
			
			var matNoise3 = matNoise.clone();
			matNoise3 = op2.apply(matNoise3);
			
			assertTrue(matsEqual(matNoise2, matNoise3, 1e-6));
			
			// Reconstruct the op from its JSON-serialized form with legacy op names
			// (i.e. missing some categories) and check the results match
			var jsonLegacy = json.replaceAll("op.filters.", "op.")
					.replaceAll("op.ml.", "op.")
					.replaceAll("op.normalize.", "op.")
					.replaceAll("op.threshold.", "op.");
			assertNotEquals(json, jsonLegacy);
			var opLegacy = GsonTools.getInstance().fromJson(jsonLegacy, ImageOp.class);
			
			var matNoiseLegacy = matNoise.clone();
			matNoiseLegacy = opLegacy.apply(matNoiseLegacy);
			
			assertTrue(matsEqual(matNoise2, matNoiseLegacy, 1e-6));
			
			// Ensure that writing the legacy op gives the same JSON
			var jsonLegacyRecovered = GsonTools.getInstance(true).toJson(opLegacy, ImageOp.class);
			assertEquals(json, jsonLegacyRecovered);
		}
				
	}
	
	
	@Test
	public void testZOpNameSerialization() {
		Map<ImageOp, String> ops = Map.of(
				createOp(1, 2), "op.core.sequential",
				ImageOps.Channels.extract(2, 3), "op.channels.extract-channels",
				ImageOps.Channels.sum(), "op.channels.sum",
				ImageOps.Channels.mean(), "op.channels.mean",
				ImageOps.Channels.minimum(), "op.channels.minimum",
				ImageOps.Channels.maximum(), "op.channels.maximum",
				ImageOps.Filters.gaussianBlur(3), "op.filters.gaussian"
				);
		for (var entry : ops.entrySet()) {
			var json = GsonTools.getInstance().toJsonTree(entry.getKey(), ImageOp.class).getAsJsonObject();
			assertEquals(json.get("type").getAsString(), entry.getValue());
		}
	}

	
	@Test
	public void testFilters() {
		int[] types = {opencv_core.CV_32FC1, opencv_core.CV_32FC(5), opencv_core.CV_64F};
//		int[] types = {opencv_core.CV_64F};
		int[] radii = {3, 5, 15};
		double eps = 1e-4;
		try (var scope = new PointerScope()) {
			for (int type : types) {
				var mat = new Mat(400, 300, type);
				OpenCVTools.addNoise(mat, 10, 20);
				
				for (int radius : radii) {
					var matMean = ImageOps.padAndApply(ImageOps.Filters.mean(radius), mat.clone());
//					var matMean = ImageOps.padAndApply(ImageOps.Filters.gaussianBlur(radius), mat.clone());
					var matVariance = ImageOps.padAndApply(ImageOps.Filters.variance(radius), mat.clone());
					var matStdDev = ImageOps.padAndApply(ImageOps.Filters.stdDev(radius), mat.clone());
										
					// Check that standard deviation is the square root of the variance
					double[] pxVariance = OpenCVTools.extractDoubles(matVariance);
					double[] pxStdDev = OpenCVTools.extractDoubles(matStdDev);
					assertEquals(pxVariance.length, pxStdDev.length);
					for (int i = 0; i < pxVariance.length; i++) {
						assertEquals(pxStdDev[i], Math.sqrt(pxVariance[i]), eps);
					}
					
					// Check mean and standard deviation values match first filtered location
					var kernel = OpenCVTools.createDisk(radius, false);
					kernel.convertTo(kernel, opencv_core.CV_8U);
//					kernel.convertTo(kernel, opencv_core.CV_8U, 255, 0);
//					System.err.println(kernel.createIndexer());
					var matLocalMean = new Mat();
					var matLocalStd = new Mat();
					var idxMean = matMean.createIndexer();
					var idxStd = matStdDev.createIndexer();
					var matOrig = new Mat();
					for (int i = 0; i < mat.channels(); i++) {
//						System.err.println("Channel " + i + ", radius " + radius);
						// Extract channels
						opencv_core.extractChannel(mat, matOrig, i);
						// Extract first filtered location
						matOrig = OpenCVTools.crop(matOrig, 0, 0, kernel.cols(), kernel.rows());
						// Extract filtered measurements
						double std = idxStd.getDouble(radius, radius, i);
						double mean = idxMean.getDouble(radius, radius, i);
						// Compute mean & standard deviation
						opencv_core.meanStdDev(matOrig, matLocalMean, matLocalStd, kernel);
						double localStd = OpenCVTools.extractDoubles(matLocalStd)[0];
						double localMean = OpenCVTools.extractDoubles(matLocalMean)[0];
//						OpenCVTools.matToImagePlus(""+radius, mat, matMean, matStdDev).show();
//						System.err.println("Mean: " + localMean + ": " + mean);
//						System.err.println("Std.dev: " + localStd + ": " + std);
						assertEquals(localStd, std, eps);
						assertEquals(localMean, mean, eps);
					}
				}
			}
		}
	}
	
	
	
	@Test
	public void testCore() {
		
		double[] values = {0, 0.5, 1, 2.3, 7, -3, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
		
		try (var scope = new PointerScope()) {
			
			for (int type : new int[] {opencv_core.CV_64F, opencv_core.CV_32F}) {
			
				for (double v1 : values) {
					for (double v2 : values) {
						
						var m1 = OpenCVTools.scalarMat(v1, type);
						var m2 = OpenCVTools.scalarMat(v2, type);
						
						compareValues(m1, ImageOps.Core.add(v2), v1 + v2);
						compareValues(m2, ImageOps.Core.add(v1), v2 + v1);
	
						compareValues(m1, ImageOps.Core.subtract(v2), v1 - v2);
						compareValues(m2, ImageOps.Core.subtract(v1), v2 - v1);
	
						compareValues(m1, ImageOps.Core.multiply(v2), v1 * v2);
						compareValues(m2, ImageOps.Core.multiply(v1), v2 * v1);
	
						compareValues(m1, ImageOps.Core.divide(v2), v1 / v2);
						compareValues(m2, ImageOps.Core.divide(v1), v2 / v1);
						
						compareValues(m1, ImageOps.Core.splitAdd(null, null), v1 + v1);
						compareValues(m2, ImageOps.Core.splitAdd(null, null), v2 + v2);
	
						compareValues(m1, ImageOps.Core.splitSubtract(null, null), v1 - v1);
						compareValues(m2, ImageOps.Core.splitSubtract(null, null), v2 - v2);
	
						compareValues(m1, ImageOps.Core.splitMultiply(null, null), v1 * v1);
						compareValues(m2, ImageOps.Core.splitMultiply(null, null), v2 * v2);
	
						compareValues(m1, ImageOps.Core.splitDivide(null, null), v1 / v1);
						compareValues(m2, ImageOps.Core.splitDivide(null, null), v2 / v2);
						
	//					System.err.println("v1: " + v1);
	//					System.err.println("v2: " + v2);
						
						compareValues(m1, ImageOps.Core.exp(), Math.exp(v1));
						compareValues(m2, ImageOps.Core.exp(), Math.exp(v2));
	
						compareValues(m1, ImageOps.Core.log(), Math.log(v1));
						compareValues(m2, ImageOps.Core.log(), Math.log(v2));
						
						compareValues(m1, ImageOps.Core.sqrt(), Math.sqrt(v1));
						compareValues(m2, ImageOps.Core.sqrt(), Math.sqrt(v2));
						
						compareValues(m1, ImageOps.Core.power(v2), Math.pow(v1, v2));
	
					}
				}
				
			}

		}
		
	}
	
	/**
	 * Apply an op to a (clone of a) Mat and check its values match the target.
	 * @param mat
	 * @param op
	 * @param targetValues
	 */
	private static void compareValues(Mat mat, ImageOp op, double... targetValues) {
		double[] values = OpenCVTools.extractDoubles(op.apply(mat.clone()));
		assertArrayEquals(targetValues, values, 1e-3);
	}
	
	
	
	
	@Test
	public void testChannels() {
		try (var scope = new PointerScope()) {
			int type = opencv_core.CV_32FC1;
			int rows = 25;
			int cols = 30;
			var matList = Arrays.asList(
					new Mat(rows, cols, type, Scalar.all(1)),
					new Mat(rows, cols, type, Scalar.all(5)),				
					new Mat(rows, cols, type, Scalar.all(2)),
					new Mat(rows, cols, type, Scalar.all(4)),
					new Mat(rows, cols, type, Scalar.all(3))
					);
			var mat = OpenCVTools.mergeChannels(matList, null);
			
			var matMean = ImageOps.Channels.mean().apply(mat.clone());
			assertTrue(matsEqual(matMean, new Mat(rows, cols, type, Scalar.all(3)), 1e-6));
			
			var matSum = ImageOps.Channels.sum().apply(mat.clone());
			assertTrue(matsEqual(matSum, new Mat(rows, cols, type, Scalar.all(15)), 1e-6));
			
			var matMax = ImageOps.Channels.maximum().apply(mat.clone());
			assertTrue(matsEqual(matMax, new Mat(rows, cols, type, Scalar.all(5)), 1e-6));
			
			var matMin = ImageOps.Channels.minimum().apply(mat.clone());
			assertTrue(matsEqual(matMin, new Mat(rows, cols, type, Scalar.all(1)), 1e-6));
	
			var matChannels = ImageOps.Channels.extract(1).apply(mat.clone());
			assertEquals(matChannels.channels(), 1);
			assertTrue(matsEqual(matChannels, matList.get(1), 1e-6));
	
			var matChannels2 = ImageOps.Channels.extract(2, 4).apply(mat.clone());
			assertEquals(matChannels2.channels(), 2);
	
			assertThrows(IllegalArgumentException.class, () -> ImageOps.Channels.extract().apply(mat.clone()));
		}
	}
	
	
	
	
	/**
	 * Compare if two Mats are equal in terms of dimensions and values.
	 * @param m1
	 * @param m2
	 * @param tol
	 * @return
	 */
	private static boolean matsEqual(Mat m1, Mat m2, double tol) {
		if (m1.rows() != m2.rows() || m1.cols() != m2.cols() || m1.channels() != m2.channels())
			return false;
		Mat matDiff = new Mat();
		opencv_core.absdiff(m1, m2, matDiff);
		for (var channel : OpenCVTools.splitChannels(matDiff)) {
			if (opencv_core.countNonZero(opencv_core.greaterThan(channel, tol).asMat()) != 0)
				return false;
		}
		return true;
	}
	
	private Mat createZerosMat(int size, int nChannels) {
		return new Mat(size, size, opencv_core.CV_32FC(nChannels), Scalar.ONE);
	}
	
	private void addNoise(Mat mat, int seed) {
		opencv_core.setRNGSeed(seed);
		OpenCVTools.addNoise(mat, 0, 1.0);
//		opencv_core.randn(mat, 
//				new Mat(1, 1, opencv_core.CV_32FC1, Scalar.ZERO),
//				new Mat(1, 1, opencv_core.CV_32FC1, Scalar.ONE));
	}
	
	private ImageOp createOp(int... channels) {
		return ImageOps.Core.sequential(
				ImageOps.Channels.extract(channels),
				ImageOps.Filters.gaussianBlur(4.0),
				ImageOps.Threshold.thresholdMeanStd(0.5),
				ImageOps.Filters.maximum(4),
				ImageOps.Filters.closing(2)
				);
	}

}