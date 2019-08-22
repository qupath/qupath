package qupath.lib.io;

import static org.junit.Assert.*;

import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.SparseMat;
import org.bytedeco.opencv.opencv_ml.EM;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.junit.Test;

import com.google.gson.GsonBuilder;

import qupath.lib.io.OpenCVTypeAdapters;

@SuppressWarnings("javadoc")
public class TypeAdaptersCVTest {

	@Test
	public void testGetOpenCVTypeAdaptorFactory() {
		
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
