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

package qupath.opencv.io;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_core.SparseMat;
import org.bytedeco.opencv.opencv_ml.EM;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.junit.jupiter.api.Test;
import org.bytedeco.opencv.opencv_core.TermCriteria;

import com.google.gson.GsonBuilder;


@SuppressWarnings("javadoc")
public class TestOpenCVTypeAdapters {

	@SuppressWarnings("unchecked")
	@Test
	public void testMats() {
		
		var gson = new GsonBuilder()
				.registerTypeAdapterFactory(OpenCVTypeAdapters.getOpenCVTypeAdaptorFactory())
				.create();

		try (PointerScope scope = new PointerScope()) {
			
			// Check writing/reading a Mat
			var matOrig = Mat.eye(5, 5, opencv_core.CV_32FC1).asMat();
			var json = gson.toJson(matOrig);			
			var matRead = gson.fromJson(json, Mat.class);
			
			assertTrue(matEquals(matOrig, matRead));
			
			var sparseMatOrig = new SparseMat(matOrig);
			var jsonSparse = gson.toJson(sparseMatOrig);			
			var sparseMatRead = gson.fromJson(jsonSparse, SparseMat.class);
			
			assertTrue(matEquals(sparseMatOrig, sparseMatRead));
			
		}
		
	}
		
	@SuppressWarnings("unchecked")
	@Test
	public void testStatModels() {

		var gson = new GsonBuilder()
				.registerTypeAdapterFactory(OpenCVTypeAdapters.getOpenCVTypeAdaptorFactory())
				.create();
		
		try (PointerScope scope = new PointerScope()) {

			// Create a model with some non-default parameters
			var model = EM.create();
			int nClusters = 4;
			int nIterations = 8;
			var termCrit = new TermCriteria(TermCriteria.COUNT, nIterations, 0);
			model.setClustersNumber(nClusters);
			model.setTermCriteria(termCrit);
			
			var samples = new Mat(50, 50, opencv_core.CV_32FC1, Scalar.ZERO);
			var one = new Mat(1, 1, opencv_core.CV_32FC1, Scalar.ONE);
			var two = new Mat(1, 1, opencv_core.CV_32FC1, Scalar.all(2.0));
			opencv_core.randn(samples.rowRange(0, 25), one, two);
			opencv_core.randn(samples.rowRange(25, 50), two, one);
			model.trainEM(samples);
			
			var jsonModel = gson.toJson(model);
			var modelRead = (EM)gson.fromJson(jsonModel, StatModel.class);
			assertNotNull(modelRead);
			assertEquals(nClusters, modelRead.getClustersNumber());
			assertEquals(nIterations, modelRead.getTermCriteria().maxCount());
			
			var modelRead2 = gson.fromJson(jsonModel, EM.class);
			assertNotNull(modelRead2);
			assertEquals(nClusters, modelRead2.getClustersNumber());
			assertEquals(nIterations, modelRead2.getTermCriteria().maxCount());
			
			
		}
			
	}
	
	
	@SuppressWarnings("unchecked")
	@Test
	public void testSizeAndScalars() {
		
		var gson = new GsonBuilder()
				.registerTypeAdapterFactory(OpenCVTypeAdapters.getOpenCVTypeAdaptorFactory())
				.create();

		
		try (PointerScope scope = new PointerScope()) {

			// Test scalar
			var scalarArray = new double[] {1.1, 2.2, 3.3, 4.4};
			for (int n = 0; n <= 4; n++) {
				var arr = Arrays.copyOf(scalarArray, n);
				Scalar scalar;
				if (n == 0)
					scalar = new Scalar();
				else if (n == 1)
					scalar = new Scalar(arr[0]);
				else if (n == 2)
					scalar = new Scalar(arr[0], arr[1]);
				else if (n == 3)
					scalar = new Scalar(arr[0], arr[1], arr[2], 0.0);
				else
					scalar = new Scalar(arr[0], arr[1], arr[2], arr[3]);
				
				assertArrayEquals(arr, scalarToArray(scalar, n), 1e-3);

				var scalarJson = gson.toJson(scalar);
				assertArrayEquals(arr, scalarToArray(gson.fromJson(scalarJson, Scalar.class), n), 1e-3);
			}
			
			// Test size
			for (int w : new int[] {102, 234, -1}) {
				var size = new Size(w, w*2);
				var sizeJson = gson.toJson(size);
				var size2 = gson.fromJson(sizeJson, Size.class);
				assertEquals(size.width(), size2.width());
				assertEquals(size.height(), size2.height());
			}
			
		}
	}
	
	static double[] scalarToArray(Scalar scalar, int n) {
		double[] array = new double[n];
		scalar.get(array);
		return array;
	}
	
	
	static boolean matEquals(Mat mat1, Mat mat2) {
		var matResult = new Mat();
		opencv_core.compare(mat1, mat2, matResult, opencv_core.CMP_NE);
		return opencv_core.countNonZero(matResult) == 0;
	}
	
	static boolean matEquals(SparseMat mat1, SparseMat mat2) {
		var mat11 = new Mat();
		var mat22 = new Mat();
		mat1.copyTo(mat11);
		mat2.copyTo(mat22);
		return matEquals(mat11, mat22);
	}
	

	@SuppressWarnings("unchecked")
	@Test
	public void testGetTypeAdaptor() {
		var gson = new GsonBuilder()
				.registerTypeAdapter(Mat.class, OpenCVTypeAdapters.getTypeAdaptor(Mat.class))
				.registerTypeAdapter(SparseMat.class, OpenCVTypeAdapters.getTypeAdaptor(SparseMat.class))
				.create();

		
		try (PointerScope scope = new PointerScope()) {
			
			// Check writing/reading a Mat
			var matOrig = Mat.eye(5, 5, opencv_core.CV_32FC1).asMat();
			var json = gson.toJson(matOrig);			
			var matRead = gson.fromJson(json, Mat.class);
			
			assertTrue(matEquals(matOrig, matRead));
			
			// Check writing/reading a SparseMat
			var sparseMatOrig = new SparseMat(matOrig);
			var jsonSparse = gson.toJson(sparseMatOrig);			
			var sparseMatRead = gson.fromJson(jsonSparse, SparseMat.class);
			
			assertTrue(matEquals(sparseMatOrig, sparseMatRead));
		}
	}

}