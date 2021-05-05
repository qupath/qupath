/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.junit.jupiter.api.Test;

class TestGroovyCV {
	
	@Test
	void testStats() {
		
		double[] values = new double[] {-1, 0, 2, 305.2, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
		double[] valuesTrimmed = Arrays.copyOf(values, 5); // Keep NaN but remove infinity
		double max = 305.2;
		double min = -1;
		double sum = -1 + 0 + 2 + 305.2;
		double mean = sum / 4;
	
		double eps = 1e-4;
		
		try (var scope = new PointerScope()) {
			
			var mat = new Mat(values);
			var matTrimmed = new Mat(valuesTrimmed);
			
			assertEquals(Double.POSITIVE_INFINITY, GroovyCV.max(mat), eps);
			assertEquals(Double.NEGATIVE_INFINITY, GroovyCV.min(mat), eps);
			assertEquals(Double.NaN, GroovyCV.sum(mat), eps);
			assertEquals(Double.NaN, GroovyCV.mean(mat), eps);
			
			assertEquals(max, GroovyCV.max(matTrimmed), eps);
			assertEquals(min, GroovyCV.min(matTrimmed), eps);
			assertEquals(sum, GroovyCV.sum(matTrimmed), eps);
			assertEquals(mean, GroovyCV.mean(matTrimmed), eps);
			
			
		}
	}
	
	
	@Test
	void testOperations() {
		
		double[] values = new double[] {-1, 0, 2, 305.2, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
		double eps = 1e-6;
		
		try (var scope = new PointerScope()) {
			for (int c = 2; c < 8; c++) {
				for (double v1 : values) {
					for (double v2 : values) {
						
						var m1 = OpenCVTools.scalarMatWithType(v1, opencv_core.CV_64FC(c));
						var m2 = OpenCVTools.scalarMatWithType(v2, opencv_core.CV_64FC(c));
						
//						System.err.println("v1: " + v1 + ", v2: " + v2 + ", c: " + c);
						
						checkAllValues(GroovyCV.plus(m1, m1), v1 + v1, c, eps);
						checkAllValues(GroovyCV.plus(m1, m2), v1 + v2, c, eps);
						checkAllValues(GroovyCV.plus(m2, m1), v2 + v1, c, eps);
						checkAllValues(GroovyCV.plus(m2, v1), v2 + v1, c, eps);
						checkAllValues(GroovyCV.plus(m1, v2), v1 + v2, c, eps);
						
						checkAllValues(GroovyCV.minus(m1, m1), v1 - v1, c, eps);
						checkAllValues(GroovyCV.minus(m1, m2), v1 - v2, c, eps);
						checkAllValues(GroovyCV.minus(m2, m1), v2 - v1, c, eps);
						checkAllValues(GroovyCV.minus(m2, v1), v2 - v1, c, eps);
						checkAllValues(GroovyCV.minus(m1, v2), v1 - v2, c, eps);
						
						checkAllValues(GroovyCV.multiply(m1, m1), v1 * v1, c, eps);
						checkAllValues(GroovyCV.multiply(m1, m2), v1 * v2, c, eps);
						checkAllValues(GroovyCV.multiply(m2, m1), v2 * v1, c, eps);
						checkAllValues(GroovyCV.multiply(m2, v1), v2 * v1, c, eps);
						checkAllValues(GroovyCV.multiply(m1, v2), v1 * v2, c, eps);

						checkAllValues(GroovyCV.div(m1, m1), v1 / v1, c, eps);
						checkAllValues(GroovyCV.div(m1, m2), v1 / v2, c, eps);
						checkAllValues(GroovyCV.div(m2, m1), v2 / v1, c, eps);
						checkAllValues(GroovyCV.div(m2, v1), v2 / v1, c, eps);
						checkAllValues(GroovyCV.div(m1, v2), v1 / v2, c, eps);
						
						// Currently, maximum and minimum with NaNs don't give fully reliable results with OpenCV - 
						// the order of the input seems to matter
						if (!Double.isNaN(v1 + v2)) {
							checkAllValues(GroovyCV.maximum(m1, m1), Math.max(v1, v1), c, eps);
							checkAllValues(GroovyCV.maximum(m1, m2), Math.max(v1, v2), c, eps);
							checkAllValues(GroovyCV.maximum(m2, m1), Math.max(v2, v1), c, eps);
							checkAllValues(GroovyCV.maximum(m2, v1), Math.max(v2, v1), c, eps);
							checkAllValues(GroovyCV.maximum(m1, v2), Math.max(v1, v2), c, eps);

							checkAllValues(GroovyCV.minimum(m1, m1), Math.min(v1, v1), c, eps);
							checkAllValues(GroovyCV.minimum(m1, m2), Math.min(v1, v2), c, eps);
							checkAllValues(GroovyCV.minimum(m2, m1), Math.min(v2, v1), c, eps);
							checkAllValues(GroovyCV.minimum(m2, v1), Math.min(v2, v1), c, eps);
							checkAllValues(GroovyCV.minimum(m1, v2), Math.min(v1, v2), c, eps);
						}
						
						checkAllValues(GroovyCV.gt(m1, m1), v1 > v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.gt(m1, m2), v1 > v2 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.gt(m2, m1), v2 > v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.gt(m2, v1), v2 > v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.gt(m1, v2), v1 > v2 ? 255 : 0, c, eps);
						
						checkAllValues(GroovyCV.geq(m1, m1), v1 >= v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.geq(m1, m2), v1 >= v2 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.geq(m2, m1), v2 >= v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.geq(m2, v1), v2 >= v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.geq(m1, v2), v1 >= v2 ? 255 : 0, c, eps);
						
						checkAllValues(GroovyCV.lt(m1, m1), v1 < v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.lt(m1, m2), v1 < v2 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.lt(m2, m1), v2 < v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.lt(m2, v1), v2 < v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.lt(m1, v2), v1 < v2 ? 255 : 0, c, eps);
						
						checkAllValues(GroovyCV.leq(m1, m1), v1 <= v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.leq(m1, m2), v1 <= v2 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.leq(m2, m1), v2 <= v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.leq(m2, v1), v2 <= v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.leq(m1, v2), v1 <= v2 ? 255 : 0, c, eps);

						checkAllValues(GroovyCV.eq(m1, m1), v1 == v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.eq(m1, m2), v1 == v2 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.eq(m2, m1), v2 == v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.eq(m2, v1), v2 == v1 ? 255 : 0, c, eps);
						checkAllValues(GroovyCV.eq(m1, v2), v1 == v2 ? 255 : 0, c, eps);
						
						if (!Double.isFinite(v1))
							assertThrows(IllegalArgumentException.class, () -> GroovyCV.power(m2, v1));
						else
							checkAllValues(GroovyCV.power(m2, v1), Math.pow(v2, v1), c, eps);
						if (!Double.isFinite(v2))
							assertThrows(IllegalArgumentException.class, () -> GroovyCV.power(m1, v2));
						else
							checkAllValues(GroovyCV.power(m1, v2), Math.pow(v1, v2), c, eps);

						checkAllValues(GroovyCV.abs(m1), Math.abs(v1), c, eps);
						checkAllValues(GroovyCV.abs(m2), Math.abs(v2), c, eps);
						
						checkAllValues(GroovyCV.negative(m1), -v1, c, eps);
						checkAllValues(GroovyCV.negative(m2), -v2, c, eps);

					}
				}
			}
		}
		
	}
	
	static double maxIgnoreNaN(double v1, double v2) {
//		if (Double.isNaN(v1) || Double.isNaN(v2))
//			return Double.NaN;
		if (Double.isNaN(v1))
			return v2;
		if (Double.isNaN(v2))
			return v1;
		return Math.max(v1, v2);
	}
	
	
	static void checkAllValues(Mat mat, double value, int length, double eps) {
		double[] doubles = OpenCVTools.extractDoubles(mat);
		assertEquals(length, doubles.length);
		for (double d : doubles)
			assertEquals(value, d, eps);
	}
	
	

}
