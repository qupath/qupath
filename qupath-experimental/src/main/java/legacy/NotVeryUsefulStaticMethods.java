package legacy;

import java.util.ArrayList;
import java.util.List;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_ml.NormalBayesClassifier;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.StatModel;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.Reclassifier;

@Deprecated
public class NotVeryUsefulStaticMethods {
	
	
	/**
	 * Default prediction method.  Makes no attempt to populate results matrix or to provide probabilities.
	 * (Results matrix only given as a parameter in case it is needed)
	 * <p>
	 * Subclasses may choose to override this method if they can do a better prediction, e.g. providing probabilities as well.
	 * <p>
	 * Upon returning, it is assumed that the PathClass of the PathObject will be correct, but it is not assumed that the results matrix will
	 * have been updated.
	 * 
	 * @param classifier
	 * @param pathClasses
	 * @param samples
	 * @param pathObjects
	 * @param reclassifications
	 */
	private static void calculatePredictedClass(final StatModel classifier, final List<PathClass> pathClasses, final Mat samples, final List<PathObject> pathObjects, final List<Reclassifier> reclassifications) {
		// Use number of trees as a probability estimate for 2-class RTrees
		var results = new Mat();
		
		if (classifier instanceof RTrees) {
			
//			int nTrees = (int)((RTrees) classifier).getRoots().limit();
			
			((RTrees) classifier).getVotes(samples, results, RTrees.PREDICT_AUTO);
			
			IntIndexer indexer = results.createIndexer();
			List<PathClass> orderedPathClasses = new ArrayList<>();
			int nClasses = pathClasses.size();
			for (int c = 0; c < nClasses; c++) {
				orderedPathClasses.add(pathClasses.get(indexer.get(0, c)));
			}
			long row = 1;
			for (var pathObject : pathObjects) {
				double sum = 0;
				int maxCount = -1;
				int maxInd = -1;
				PathClass pathClass = null;
				for (long c = 0; c < nClasses; c++) {
					int count = indexer.get(row, c);
					if (count > maxCount) {
						maxCount = count;
						maxInd = (int)c;
					}
					sum += count;
				}
				pathClass = orderedPathClasses.get(maxInd);
				double probability = maxCount / sum;
				reclassifications.add(new Reclassifier(pathObject, pathClass, true, probability));
				row++;
			}
			return;
		} else if (classifier instanceof NormalBayesClassifier) {
//			results.create(pathObjects.size(), 1, CV_32SC1);
			var matProbabilities = new Mat();
			
			((NormalBayesClassifier)classifier).predictProb(samples, results, matProbabilities, 0);
			
			IntIndexer idxResults = results.createIndexer();
			FloatIndexer idxProbabilities = matProbabilities.createIndexer();
			int row = 0;
			for (var pathObject : pathObjects) {
				int prediction = idxResults.get(row);
				double sum = 0;
				double rawProbValue = idxProbabilities.get(row, prediction);
				for (int i = 0; i < idxProbabilities.cols(); i++) {
					sum += idxProbabilities.get(row, i);
				}
				double probability;
				PathClass pathClass = pathClasses.get(prediction);
				if (Double.isInfinite(rawProbValue))
					probability = 1.0;
				else if (sum == 0) {
					probability = Double.NaN;
//					pathClass = null;
				} else
					probability = rawProbValue / sum;
				if (probability > 1)
					System.err.println(probability);
				reclassifications.add(new Reclassifier(pathObject, pathClass, true, probability));
				row++;
			}
			matProbabilities.close();
			return;
		}
		// Default to standard assignment of classification
		
//		results.create(samples.rows(), 1, CV_32SC1);
		classifier.predict(samples, results, 0);
		int row = 0;
		var indexer = results.createIndexer();
		int nClasses = pathClasses.size();
		if (indexer.cols() == 1) {
			for (var pathObject : pathObjects) {
				double rawPrediction = indexer.getDouble(row);
				int prediction = (int)rawPrediction;
				PathClass pathClass = pathClasses.get(prediction);
				reclassifications.add(new Reclassifier(pathObject, pathClass, true));
				row++;
			}
		} else {
			for (var pathObject : pathObjects) {
				double sum = 0;
				double maxCount = Double.NEGATIVE_INFINITY;
				int maxInd = -1;
				for (long c = 0; c < nClasses; c++) {
					double count = indexer.getDouble(row, c);
					if (count > maxCount) {
						maxCount = count;
						maxInd = (int)c;
					}
					sum += count;
				}
				double probability = maxCount; // / sum;
				PathClass pathClass = pathClasses.get(maxInd);
				reclassifications.add(new Reclassifier(pathObject, pathClass, true, probability));
				row++;
			}				
		}
	}
	
	
	
	static void getColumwiseMeanAndStdDev(Mat mat, Mat matMeans, Mat matStdDev) {
		int nCols = mat.cols();
		matMeans.create(1, nCols, mat.type());
		matStdDev.create(1, nCols, mat.type());
		for (int c = 0; c < nCols; c++) {
			var col = mat.col(c);
			opencv_core.meanStdDev(col, matMeans.col(c), matStdDev.col(c));
		}
	}

	static void getColumwiseMinMax(Mat mat, Mat matMin, Mat matMax) {
		opencv_core.reduce(mat, matMin, 0, opencv_core.REDUCE_MIN, -1);
		opencv_core.reduce(mat, matMax, 0, opencv_core.REDUCE_MAX, -1);
	}

	/**
	 * Use columnwise mean & std dev values to determine offset and scale factors for normalization.
	 * <p>
	 * Normalization for a new mat should be applied as {@code (Mat + matOffset) * matScale}.
	 * 
	 * @param mat input Mat
	 * @param matOffset -mean values for each column
	 * @param matScale 1/std values for each column
	 */
	static void getColumwiseMeanAndStdDevRescaling(Mat mat, Mat matOffset, Mat matScale) {
		getColumwiseMeanAndStdDev(mat, matOffset, matScale);
		opencv_core.multiplyPut(matOffset, -1.0);
		matScale.put(opencv_core.divide(1.0, matScale));
	}

	/**
	 * Use columnwise min & max values to determine offset and scale factors for normalization.
	 * <p>
	 * Normalization for a new mat then should be applied as {@code (Mat + matOffset) * matScale}.
	 * 
	 * @param mat input Mat
	 * @param matOffset -min values for each column
	 * @param matScale 1/(max - min) values for each column
	 */
	static void getColumwiseMinMaxRescaling(Mat mat, Mat matOffset, Mat matScale) {
		getColumwiseMinMax(mat, matOffset, matScale);
		matScale.put(opencv_core.divide(1.0, opencv_core.subtract(matScale, matOffset)));
		opencv_core.multiplyPut(matOffset, -1.0);
	}


	static void rescaleRows(Mat mat, Mat matOffset, Mat matScale) {
		for (int r = 0; r < mat.rows(); r++) {
			var row = mat.row(r);
			row.put(opencv_core.multiply(opencv_core.add(mat, matOffset), matScale));
		}
	}


//	static Normalizer getColumwiseMeanAndStdDevNormalizer(Mat mat) {
//		var matMean = new Mat();
//		var matStdDev = new Mat();
//		getColumwiseMeanAndStdDev(mat, matMean, matStdDev);
//
//		int nCols = mat.cols();
//		var idxMean = matMean.createIndexer();
//		var idxStdDev = matStdDev.createIndexer();
//		double[] offsets = new double[nCols];
//		double[] scales = new double[nCols];
//		long[] inds = {0L, 0L};
//		for (int i = 0; i < nCols; i++) {
//			inds[1] = i;
//			offsets[i] = idxMean.getDouble(inds);
//			scales[i] = idxStdDev.getDouble(inds);				
//		}
//		return new Normalizer(offsets, scales);
//	}
//
//	static Normalizer getColumwiseMinMaxNormalizer(Mat mat) {
//		var matMin = new Mat();
//		var matMax = new Mat();
//		getColumwiseMinMax(mat, matMin, matMax);
//
//		int nCols = mat.cols();
//		var idxMin = matMin.createIndexer();
//		var idxMax = matMax.createIndexer();
//		double[] offsets = new double[nCols];
//		double[] scales = new double[nCols];
//		long[] inds = {0L, 0L};
//		for (int i = 0; i < nCols; i++) {
//			inds[1] = i;
//			offsets[i] = idxMin.getDouble(inds);
//			scales[i] = idxMax.getDouble(inds) - offsets[i];				
//		}
//		return new Normalizer(offsets, scales);
//	}
	

}
