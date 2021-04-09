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

package qupath.opencv.ml;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_ml.*;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.common.GeneralTools;
import qupath.lib.io.GsonTools;
import qupath.lib.io.OpenCVTypeAdapters;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.opencv.tools.OpenCVTools;

/**
 * QuPath wrappers for OpenCV classifiers, which are instances of StatModel.
 * There are two main reasons to use these wrappers rather than StatModel directly:
 * <ol>
 *   <li>Improved API consistency when exchanging between classifiers. For example, some require 
 *   training data to be in a specified form (labels or one-hot encoding).</li>
 *   <li>Easier serialization to JSON along with other QuPath objects via {@link GsonTools}.</li>
 * </ol>
 * 
 * @author Pete Bankhead
 *
 */
public class OpenCVClassifiers {
	
	private static final Logger logger = LoggerFactory.getLogger(OpenCVClassifiers.class);
	
	/**
	 * Create an {@link OpenCVStatModel} for a specific class of {@link StatModel}.
	 * @param cls
	 * @return
	 */
	public static OpenCVStatModel createStatModel(Class<? extends StatModel> cls) {		
		if (RTrees.class.equals(cls))
			return new RTreesClassifier();

		if (Boost.class.equals(cls))
			return new BoostClassifier();
		
		if (DTrees.class.equals(cls))	
			return new DTreesClassifier();
		
		if (KNearest.class.equals(cls))
			return new KNearestClassifierCV();
		
		if (ANN_MLP.class.equals(cls))
			return new ANNClassifierCV();
		
		if (LogisticRegression.class.equals(cls))
			return new LogisticRegressionClassifier();
		
		if (EM.class.equals(cls))
			return new EMClusterer();

		if (NormalBayesClassifier.class.equals(cls))
			return new NormalBayesClassifierCV();
		
		if (SVM.class.equals(cls))
			return new SVMClassifierCV();
		
		if (SVMSGD.class.equals(cls))
			return new SVMSGDClassifierCV();
		
		throw new IllegalArgumentException("Unknown StatModel class " + cls);
	}
	
	
//	/**
//	 * Create a multiclass {@link StatModel}. Currently removed because it is hard to use.
//	 * @param cls
//	 * @return
//	 */
//	public static OpenCVStatModel createMulticlassStatModel(Class<? extends StatModel> cls) {		
//		if (ANN_MLP.class.equals(cls))
//			return new MulticlassANNClassifierCV();
//		
//		throw new IllegalArgumentException("Unknown StatModel class " + cls);
//	}

	
	/**
	 * Create an {@link OpenCVStatModel} by wrapping an existing {@link StatModel}.
	 * @param statModel
	 * @return
	 */
	public static OpenCVStatModel wrapStatModel(StatModel statModel) {
		var cls = statModel.getClass();
		
		if (RTrees.class.equals(cls))
			return new RTreesClassifier((RTrees)statModel);

		if (Boost.class.equals(cls))
			return new BoostClassifier((Boost)statModel);
		
		if (DTrees.class.equals(cls))	
			return new DTreesClassifier((DTrees)statModel);
		
		if (KNearest.class.equals(cls))
			return new KNearestClassifierCV((KNearest)statModel);
		
		if (ANN_MLP.class.equals(cls))
			return new ANNClassifierCV((ANN_MLP)statModel);
		
		if (LogisticRegression.class.equals(cls))
			return new LogisticRegressionClassifier((LogisticRegression)statModel);
		
		if (EM.class.equals(cls))
			return new EMClusterer((EM)statModel);

		if (NormalBayesClassifier.class.equals(cls))
			return new NormalBayesClassifierCV((NormalBayesClassifier)statModel);
		
		if (SVM.class.equals(cls))
			return new SVMClassifierCV((SVM)statModel);
		
		if (SVMSGD.class.equals(cls))
			return new SVMSGDClassifierCV((SVMSGD)statModel);
		
		throw new IllegalArgumentException("Unknown StatModel class " + cls);
	}
	

	/**
	 * Wrapper class for a {@link StatModel}, which standardizes how training may be performed and 
	 * parameters can be set.
	 */
	@JsonAdapter(OpenCVClassifierTypeAdapter.class)
	public static abstract class OpenCVStatModel {
		
		/**
		 * Classifier can handle missing (NaN) values
		 * @return true if NaNs are supported, false otherwise
		 */
		public abstract boolean supportsMissingValues();
		
		/**
		 * User-friendly, readable name for the classifier
		 * @return the classifier name
		 */
		public abstract String getName();
		
		/**
		 * Classifier has already been trained and is ready to predict.
		 * @return true if the classifier is trained, false otherwise
		 */
		public abstract boolean isTrained();
		
		/**
		 * Classifier is able to handle more than one outputs for a single sample.
		 * @return true if multiclass classification is supported, false otherwise
		 */
		public abstract boolean supportsMulticlass();
		
		/**
		 * Classifier can be trained interactively  (i.e. quickly).
		 * @return true if interactive classification is supported, false otherwise
		 */
		public abstract boolean supportsAutoUpdate();
		
		/**
		 * Classifier can output a prediction confidence (expressed between 0 and 1), 
		 * so may be interpreted as a probability... even if it isn't necessarily one.
		 * @return true if (pseudo-)probabilities can be provided
		 */
		public abstract boolean supportsProbabilities();
		
		/**
		 * Retrieve a list of adjustable parameter that can be used to customize the classifier.
		 * After making changes to the {@link ParameterList}, the classifier should be retrained 
		 * before being used.
		 * @return the parameter list for this classifier
		 */
		public abstract ParameterList getParameterList();
				
		/**
		 * Create training data in the format required by this classifier.
		 * @param samples
		 * @param targets
		 * @param weights optional weights
		 * @param doMulticlass 
		 * @return
		 * @see #train(TrainData)
		 */
		public abstract TrainData createTrainData(Mat samples, Mat targets, Mat weights, boolean doMulticlass);
		
		/**
		 * Train the classifier using data in an appropriate format.
		 * @param trainData
		 * @see #createTrainData(Mat, Mat, Mat, boolean)
		 */
		public abstract void train(TrainData trainData);

		/**
		 * Apply classification, optionally requesting probability estimates.
		 * <p>
		 * Not all StatModels are capable of estimating probability values, in which case 
		 * probabilities will be null (if not supplied) or an empty matrix.
		 * <p>
		 * Note also that if probabilities are required, these will not necessarily be normalized 
		 * between 0 and 1 (although they generally are).  They represent a best-effort for the 
		 * StatModel to provide confidence values, but are not (necessarily) strictly probabilities.
		 * <p>
		 * For example, RTrees estimates probabilities based on the proportion of votes for the 'winning' 
		 * classification.
		 * 
		 * @param samples the input samples
		 * @param results a Mat to receive the results
		 * @param probabilities a Mat to receive probability estimates, or null if probabilities are not needed
		 */
		public abstract void predict(Mat samples, Mat results, Mat probabilities);
		
		abstract StatModel getStatModel();
		
		@Override
		public String toString() {
			return String.format("OpenCV ", getStatModel().getClass().getSimpleName());
		}
		
	}
	
	
	static class OpenCVClassifierTypeAdapter extends TypeAdapter<OpenCVStatModel> {

		@Override
		public void write(JsonWriter out, OpenCVStatModel value) throws IOException {
			OpenCVTypeAdapters.getTypeAdaptor(StatModel.class).write(out, value.getStatModel());
		}

		@Override
		public OpenCVStatModel read(JsonReader in) throws IOException {
			var statModel = OpenCVTypeAdapters.getTypeAdaptor(StatModel.class).read(in);
			return wrapStatModel(statModel);
//			return new DefaultOpenCVStatModel<StatModel>(statModel);
		}
		
	}
	
	
	
	static abstract class AbstractOpenCVClassifierML<T extends StatModel> extends OpenCVStatModel {

		@JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
		private T model;
		private transient ParameterList params; // Should take defaults from the serialized model
		
		transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		
		abstract ParameterList createParameterList(T model);
		
		abstract T createStatModel();
		
		abstract void updateModel(T model, ParameterList params, TrainData trainData);
		
		AbstractOpenCVClassifierML() {}
		
		AbstractOpenCVClassifierML(T model) {
			this.model = model;
			params = createParameterList(model);
		}
		
		/**
		 * Returns false (the default value).
		 */
		@Override
		public boolean supportsMulticlass() {
			return false;
		}
		
		/**
		 * Returns true (the default value).
		 */
		@Override
		public boolean supportsAutoUpdate() {
			return true;
		}
		
		@Override
		public boolean supportsProbabilities() {
			var model = getStatModel();
			return model instanceof RTrees ||
					model instanceof ANN_MLP ||
					model instanceof NormalBayesClassifier;
		}
		
		@Override
		T getStatModel() {
			if (model == null)
				model = createStatModel();
			return model;
		}
		
		@Override
		public boolean isTrained() {
			return getStatModel().isTrained();
		}
		
		@Override
		public ParameterList getParameterList() {
			if (params == null)
				params = createParameterList(getStatModel());
			return params;
		}
				
		@Override
		public String toString() {
			return getName();
		}
		
		@Override
		public TrainData createTrainData(Mat samples, Mat targets, Mat weights, boolean doMulticlass) {
			if (doMulticlass && !supportsMulticlass())
				logger.warn("Multiclass classification requested, but not supported");
			if (useUMat()) {
				UMat uSamples = samples.getUMat(opencv_core.ACCESS_READ);
				UMat uTargets = targets.getUMat(opencv_core.ACCESS_READ);
				if (weights == null || weights.empty())
					return TrainData.create(uSamples, opencv_ml.ROW_SAMPLE, uTargets);
				UMat uWeights = weights.getUMat(opencv_core.ACCESS_READ);
				return TrainData.create(uSamples, opencv_ml.ROW_SAMPLE, uTargets, null, null, uWeights, null);				
			}
			if (weights == null || weights.empty())
				return TrainData.create(samples, opencv_ml.ROW_SAMPLE, targets);
			else
				return TrainData.create(samples, opencv_ml.ROW_SAMPLE, targets, null, null, weights, null);
		}
		
		boolean useUMat() {
			return false;
		}

		@Override
		public void train(TrainData trainData) {
			lock.writeLock().lock();
			try {
				trainWithLock(trainData);
			} finally {
				lock.writeLock().unlock();
			}
		}
		
		/**
		 * Implement trainWithLock rather than train directly to ensure a lock is set 
		 * when training, which can be used to prevent prediction occurring simultaneously.
		 * 
		 * @param trainData
		 * 
		 * @see #predictWithLock
		 */
		public void trainWithLock(TrainData trainData) {
			var statModel = getStatModel();
			opencv_core.setRNGSeed(1012);
			updateModel(statModel, getParameterList(), trainData);
//			statModel.train(trainData);
			statModel.train(trainData, getTrainFlags());
		}
		
		protected int getTrainFlags() {
			return 0;
		}
		
		abstract Class<? extends StatModel> getStatModelClass();

		@Override
		public String getName() {
			var cls = getStatModelClass();
			
			if (ANN_MLP.class.equals(cls))
				return "Artificial neural network (ANN_MLP)";
			else if (RTrees.class.equals(cls))
				return "Random trees (RTrees)";
			else if (Boost.class.equals(cls))
				return "Boosted trees (Boost)";
			else if (DTrees.class.equals(cls))
				return "Decision tree (DTrees)";
			else if (EM.class.equals(cls))
				return "Expectation maximization";
			else if (KNearest.class.equals(cls))
				return "K nearest neighbor";
			else if (LogisticRegression.class.equals(cls))
				return "Logistic regression";
			else if (NormalBayesClassifier.class.equals(cls))
				return "Normal Bayes classifier";
			
			return getStatModel().getClass().getSimpleName();
		}
		
		/**
		 * Default implementation calling
		 * <pre>
		 * statModel.predict(samples, results, 0);
		 * </pre>
		 * before attempting to sanitize the outcome so that results always contains a signed int Mat containing 
		 * classifications.
		 * <p>
		 * If results originally had more than 1 column, it will be returned as probabilities 
		 * (if probabilities is not null);
		 * {@code probabilities} will be an empty matrix (i.e. no probabilities calculated).
		 */
		@Override
		public void predict(Mat samples, Mat results, Mat probabilities) {
			lock.readLock().lock();
			try {
				predictWithLock(samples, results, probabilities);
			} finally {
				lock.readLock().unlock();
			}
		}
		
		/**
		 * Implement predictWithLock rather than predict to ensure predict is not called while 
		 * training.
		 * 
		 * @param samples
		 * @param results
		 * @param probabilities
		 * 
		 * @see #trainWithLock
		 */
		protected void predictWithLock(Mat samples, Mat results, Mat probabilities) {
			var statModel = getStatModel();
			statModel.predict(samples, results, 0);
			
			int nSamples = results.rows();
			
			if (results.cols() > 1) {
				var indexer = results.createIndexer();
				int nClasses = results.cols();
				
				var matResultsnew = new Mat(nSamples, 1, opencv_core.CV_32SC1);
				IntIndexer idxResults = matResultsnew.createIndexer();
				if (probabilities != null) {
					probabilities.create(nSamples, nClasses, opencv_core.CV_32FC1);
					probabilities.put(results);
				}
				
				var inds = new long[2];
				for (int row = 0; row < nSamples; row++) {
					double maxValue = Double.NEGATIVE_INFINITY;
					int maxInd = -1;
					inds[0] = row;
					for (long c = 0; c < nClasses; c++) {
						inds[1] = c;
						double val = indexer.getDouble(inds);
						if (val > maxValue) {
							maxValue = val;
							maxInd = (int)c;
						}
					}
					idxResults.put(row,  maxInd);
				}
				indexer.release();
				idxResults.release();
				results.put(matResultsnew);
			} else {
				results.convertTo(results, opencv_core.CV_32SC1);
				if (probabilities != null) {
					// Ensure we have an empty matrix for probabilities
					probabilities.create(0, 0, opencv_core.CV_32FC1);
				}
			}
		}
		
		/**
		 * Tree classifiers in OpenCV support missing values, others do not.
		 */
		@Override
		public boolean supportsMissingValues() {
			return getStatModel() instanceof DTrees;
		}
		
		
	}
	
	
	/**
	 * Add TermCriteria parameters to an existing list.
	 * This will be an int parameter 'termIterators' and a double parameter 'termEpsilon'.
	 * 
	 * @param params the parameter list to which the parameters should be added
	 * @param defaultCriteria the current (default) TermCriteria, used to initialize the values
	 * 
	 * @see #updateTermCriteria(ParameterList, TermCriteria)
	 */
	static void addTerminationCriteriaParameters(ParameterList params, TermCriteria defaultCriteria) {
		// Set termination criteria
		params.addTitleParameter("Termination criteria");
		params.addIntParameter("termIterations", "Max iterations", defaultCriteria.maxCount(), null, "Maximum number of iterations for training");
		params.addDoubleParameter("termEpsilon", "Epsilon", defaultCriteria.epsilon(), null, "Desired accuracy for training");
	}
	
	/**
	 * Parse the TermCriteria parameters, returning a new object if needed.
	 * 
	 * @param params
	 * @param termCriteria
	 * @return termCriteria if the parameters are unchanged, or a new TermCriteria reflecting the parameters if required
	 * 
	 * @see #addTerminationCriteriaParameters(ParameterList, TermCriteria)
	 */
	static TermCriteria updateTermCriteria(ParameterList params, TermCriteria termCriteria) {
		int count = params.getIntParameterValue("termIterations");
		double epsilon = params.getDoubleParameterValue("termEpsilon");
		
		if (termCriteria != null && termCriteria.maxCount() == count && termCriteria.epsilon() == epsilon)
			return termCriteria;
		
		int type = 0;
		int termIterations = params.getIntParameterValue("termIterations");
		double termEpsilon = params.getDoubleParameterValue("termEpsilon");
		if (termIterations >= 1)
			type += TermCriteria.MAX_ITER;
		if (termIterations > 0)
			type += TermCriteria.EPS;
		return new TermCriteria(type, termIterations, termEpsilon);
	}
	
	
	static class DefaultOpenCVStatModel<T extends StatModel> extends AbstractOpenCVClassifierML<T> {

		DefaultOpenCVStatModel(T model) {
			super(model);
		}
		
		@Override
		ParameterList createParameterList(T model) {
			return new ParameterList();
		}
		
		@Override
		T createStatModel() {
			return getStatModel();
		}

		/**
		 * No updates performed.
		 */
		@Override
		void updateModel(T model, ParameterList params, TrainData trainData) {
			// TODO Auto-generated method stub
		}

		@Override
		Class<? extends StatModel> getStatModelClass() {
			return getStatModel().getClass();
		}
		
	}
	
	static abstract class AbstractTreeClassifier<T extends DTrees> extends AbstractOpenCVClassifierML<T> {

		AbstractTreeClassifier() {
			super();
		}
		
		AbstractTreeClassifier(final T model) {
			super(model);
		}
		
		
		@Override
		ParameterList createParameterList(T model) {
			
			int maxDepth = Math.min(model.getMaxDepth(), 1000);
			int minSampleCount = model.getMinSampleCount();
//			float regressionAccuracy = model.getRegressionAccuracy();
			boolean use1SERule = model.getUse1SERule();
			
			// Unused parameters
//			int cvFolds = model.getCVFolds(); // Not implemented
//			int maxCategories = model.getMaxCategories();
//			boolean truncatePrunedTree = model.getTruncatePrunedTree();
//			boolean useSurrogates = model.getUseSurrogates(); // Not implemented in OpenCV at this time

			// TODO: Consider use of priors
//			model.getPriors(null);

			ParameterList params = new ParameterList()
//					.addIntParameter("cvFolds", "Cross-validation folds", cvFolds, "Number of cross-validation folds to use when building the tree")
					.addIntParameter("maxDepth", "Maximum tree depth", maxDepth, null, "Maximum possible tree depth")
					.addIntParameter("minSampleCount", "Minimum sample count", minSampleCount, null, "Minimum number of samples per node")
//					.addDoubleParameter("regressionAccuracy", "Regression accuracy", regressionAccuracy, null, "Termination criterion")
					.addBooleanParameter("use1SERule", "Use 1SE rule", use1SERule, "Harsher pruning, more compact tree")
					;
			
			return params;
		}
		
		@Override
		void updateModel(T model, ParameterList params, TrainData trainData) {
			
//			int cvFolds = params.getIntParameterValue("cvFolds");
			int maxDepth = params.getIntParameterValue("maxDepth");
			int minSampleCount = params.getIntParameterValue("minSampleCount");
//			float regressionAccuracy = params.getDoubleParameterValue("regressionAccuracy").floatValue();
			boolean use1SERule = params.getBooleanParameterValue("use1SERule");
			
//			model.setCVFolds(cvFolds < 1 ? 1 : cvFolds);
			model.setCVFolds(0);
			model.setMaxDepth(maxDepth <= 0 ? Integer.MAX_VALUE : maxDepth);
			model.setMinSampleCount(minSampleCount < 1 ? 1 : minSampleCount);
//			model.setRegressionAccuracy(regressionAccuracy < 1e-6f ? 1e-6f : regressionAccuracy);
			model.setUse1SERule(use1SERule);
		}
		
		
	}
	
	/**
	 * Classifier based on {@link DTrees}.
	 */
	public static class DTreesClassifier extends AbstractTreeClassifier<DTrees> {

		DTreesClassifier() {
			super();
		}
		
		DTreesClassifier(final DTrees model) {
			super(model);
		}
		
		@Override
		DTrees createStatModel() {
			return DTrees.create();
		}

		@Override
		Class<? extends StatModel> getStatModelClass() {
			return DTrees.class;
		}
		
	}
	
	/**
	 * Classifier based on {@link RTrees}.
	 */
	public static class RTreesClassifier extends AbstractTreeClassifier<RTrees> {
		
		private double[] featureImportance;
		
		RTreesClassifier() {
			super();
		}
		
		RTreesClassifier(final RTrees model) {
			super(model);
		}
		
		@Override
		RTrees createStatModel() {
			var model = RTrees.create();
			model.setMaxDepth(0);
			model.setTermCriteria(
					new TermCriteria(TermCriteria.COUNT, 50, 0));
			return model;
		}

		@Override
		ParameterList createParameterList(RTrees model) {
			ParameterList params = super.createParameterList(model);
			
			int activeVarCount = model.getActiveVarCount();
			var termCrit = model.getTermCriteria();
			int maxTrees = termCrit.maxCount();
			double epsilon = termCrit.epsilon();
			boolean calcImportance = model.getCalculateVarImportance();
			
			params.addIntParameter("activeVarCount", "Active variable count", activeVarCount, null, "Number of features per tree node (if <=0, will use square root of number of features)");
			params.addIntParameter("maxTrees", "Maximum number of trees", maxTrees, null, "Maximum possible number of trees - but viewer may be used if 'Termination epsilon' is high");
			params.addDoubleParameter("epsilon", "Termination epsilon", epsilon, null, "Termination criterion - if this is high, viewer trees may be used for classification");
			params.addBooleanParameter("calcImportance", "Calculate variable importance", calcImportance, "Calculate estimate of each variable's importance (this impacts the results of the classifier!)");
			return params;
		}
		
		@Override
		public void train(TrainData trainData) {
			super.train(trainData);
			var trees = getStatModel();
			if (trees.getCalculateVarImportance()) {
//				synchronized (this) {
					var importance = trees.getVarImportance();
					var indexer = importance.createIndexer();
					int nFeatures = (int)indexer.size(0);
					featureImportance = new double[nFeatures];
					for (int r = 0; r < nFeatures; r++) {
						featureImportance[r] = indexer.getDouble(r);
					}
					indexer.release();
//				}
			} else
				featureImportance = null;
		}
		
		/**
		 * Check if the last time train was called, variable (feature) importance was calculated.
		 * @return
		 * 
		 * @see #getFeatureImportance()
		 */
		public synchronized boolean hasFeatureImportance() {
			return featureImportance != null;
		}
		
		/**
		 * Request the variable importance values from the last trained RTrees classifier, if available.
		 * 
		 * @return the ordered array of importance values, or null if this is unavailable
		 * 
		 * @see #hasFeatureImportance()
		 */
		public double[] getFeatureImportance() {
			return featureImportance == null ? null : featureImportance.clone();
		}

		@Override
		void updateModel(RTrees model, ParameterList params, TrainData trainData) {
			
			super.updateModel(model, params, trainData);

			int activeVarCount = params.getIntParameterValue("activeVarCount");
			int maxTrees = params.getIntParameterValue("maxTrees");
			double epsilon = params.getDoubleParameterValue("epsilon");
			boolean calcImportance = params.getBooleanParameterValue("calcImportance");

			int type = 0;
			if (maxTrees >= 1)
				type += TermCriteria.MAX_ITER;
			if (epsilon > 0)
				type += TermCriteria.EPS;
			var termCrit = new TermCriteria(type, maxTrees, epsilon);

			model.setActiveVarCount(activeVarCount);
			model.setUseSurrogates(false); // Not implemented, throws an exception
			model.setTermCriteria(termCrit);
			model.setCalculateVarImportance(calcImportance);
		}
		
		@Override
		Class<? extends StatModel> getStatModelClass() {
			return RTrees.class;
		}

		
		@Override
		public void predictWithLock(Mat samples, Mat results, Mat probabilities) {
			// If we don't need probabilities, it's quite straightforward
			var model = getStatModel();
			if (probabilities == null) {
				model.predict(samples, results,  RTrees.PREDICT_AUTO);
//				var idx = samples.createIndexer();
//				idx.release();
				results.convertTo(results, opencv_core.CV_32SC1);
				return;
			}
			
			// If we want probabilities, we can try our best using the votes
			var votes = new Mat();
			model.getVotes(samples, votes, RTrees.PREDICT_AUTO);
			
			int nClasses = votes.cols();
			int nSamples = samples.rows();
			IntIndexer indexer = votes.createIndexer();
			
			// Preallocate output
			probabilities.create(nSamples, nClasses, opencv_core.CV_32FC1);
			FloatIndexer idxProbabilities = probabilities.createIndexer();
			results.create(nSamples, 1, opencv_core.CV_32SC1);
			IntIndexer idxResults = results.createIndexer();
			
			int[] orderedClasses = new int[nClasses];
			for (int c = 0; c < nClasses; c++) {
				orderedClasses[c] = indexer.get(0, c);
			}
			long row = 1;
			for (var i = 0; i < nSamples; i++) {
				double sum = 0;
				int maxCount = -1;
				int maxInd = -1;
				for (long c = 0; c < nClasses; c++) {
					int count = indexer.get(row, c);
					if (count > maxCount) {
						maxCount = count;
						maxInd = (int)c;
					}
					sum += count;
				}
				// Update probability estimates
				for (int c = 0; c < nClasses; c++) {
					int count = indexer.get(row, c);
					idxProbabilities.put(i, orderedClasses[c], (float)(count / sum));
				}
				// Update prediction
				int prediction = orderedClasses[maxInd];
				idxResults.put(i, prediction);
				row++;
			}
			votes.release();
		}
		
		
	}
	
	/**
	 * Classifier based on {@link Boost}.
	 */
	public static class BoostClassifier extends AbstractTreeClassifier<Boost> {
		
		BoostClassifier() {
			super();
		}
		
		BoostClassifier(final Boost model) {
			super(model);
		}

		@Override
		Boost createStatModel() {
			return Boost.create();
		}
		
		@Override
		Class<? extends StatModel> getStatModelClass() {
			return Boost.class;
		}

		
		@Override
		ParameterList createParameterList(Boost model) {
			ParameterList params = super.createParameterList(model);
			
//			int boostType = model.getBoostType();
			var weakCount = model.getWeakCount();
			double weightTrimRate = model.getWeightTrimRate();
			
			params.addIntParameter("weakCount", "Number of weak classifiers", weakCount, null, "Number of weak classifiers to train");
			params.addDoubleParameter("weightTrimRate", "Weight trim rate", weightTrimRate, null, 0, 1, "Threshold used to save computational time");
			
			return params;
		}

		@Override
		void updateModel(Boost model, ParameterList params, TrainData trainData) {
			super.updateModel(model, params, trainData);
			
			int weakCount = params.getIntParameterValue("weakCount");
			double weightTrimRate = params.getDoubleParameterValue("weightTrimRate");
			
			model.setWeakCount(weakCount);
			model.setWeightTrimRate(weightTrimRate);
		}
		
	}
	
	/**
	 * Classifier based on {@link LogisticRegression}.
	 */
	public static class LogisticRegressionClassifier extends AbstractOpenCVClassifierML<LogisticRegression> {
		
		static enum Regularization {
			DISABLE, L1, L2;
			
			public int getRegularization() {
				switch(this) {
				case L1:
					return LogisticRegression.REG_L1;
				case L2:
					return LogisticRegression.REG_L2;
				case DISABLE:
				default:
					return LogisticRegression.REG_DISABLE;
				}
			}
			
			@Override
			public String toString() {
				switch(this) {
				case L1:
					return "L1";
				case L2:
					return "L2";
				case DISABLE:
				default:
					return "None";
				}
			}
		}
		
		LogisticRegressionClassifier() {
			super();
		}
		
		LogisticRegressionClassifier(final LogisticRegression model) {
			super(model);
		}

		@Override
		ParameterList createParameterList(LogisticRegression model) {
			var params = new ParameterList();
			double learningRate = model.getLearningRate();
			int nIterations = model.getIterations();
			int reg = model.getRegularization();
			Regularization defaultReg = Regularization.DISABLE;
			for (Regularization temp : Regularization.values()) {
				if (reg == temp.getRegularization()) {
					defaultReg = temp;
					break;
				}
			}
//			int miniBatchSize = model.getMiniBatchSize();
			
			params.addTitleParameter("Logistic regression options");
			params.addDoubleParameter("learningRate", "Learning rate", learningRate);
			params.addIntParameter("nIterations", "Number of iterations", nIterations);
//			params.addIntParameter("miniBatchSize", "Mini batch size", miniBatchSize);
			params.addChoiceParameter("regularization", "Regularization", defaultReg, Arrays.asList(Regularization.values()));
			
			addTerminationCriteriaParameters(params, model.getTermCriteria());
			return params;
		}
		
		@Override
		public TrainData createTrainData(Mat samples, Mat targets, Mat weights, boolean doMulticlass) {
			targets.convertTo(targets, opencv_core.CV_32F);
			return super.createTrainData(samples, targets, weights, doMulticlass);
		}

		@Override
		LogisticRegression createStatModel() {
			return LogisticRegression.create();
		}

		@Override
		Class<? extends StatModel> getStatModelClass() {
			return LogisticRegression.class;
		}
		
		@Override
		void updateModel(LogisticRegression model, ParameterList params, TrainData trainData) {
			double learningRate = params.getDoubleParameterValue("learningRate");
			int nIterations = params.getIntParameterValue("nIterations");
			Regularization regularization = (Regularization)params.getChoiceParameterValue("regularization");
			model.setRegularization(regularization.getRegularization());
			
			model.setLearningRate(learningRate);
			model.setIterations(nIterations);
			
			model.setTermCriteria(updateTermCriteria(params, model.getTermCriteria()));
		}
		
	}
	
	
	/**
	 * Classifier based on {@link NormalBayesClassifier}.
	 */
	public static class NormalBayesClassifierCV extends AbstractOpenCVClassifierML<NormalBayesClassifier> {

		NormalBayesClassifierCV() {
			super();
		}
		
		NormalBayesClassifierCV(final NormalBayesClassifier model) {
			super(model);
		}
		
		@Override
		ParameterList createParameterList(NormalBayesClassifier model) {
			var params = new ParameterList();
			params.addTitleParameter("No parameters to adjust!");
			return params;
		}

		@Override
		NormalBayesClassifier createStatModel() {
			return NormalBayesClassifier.create();
		}
		
		@Override
		Class<? extends StatModel> getStatModelClass() {
			return NormalBayesClassifier.class;
		}

		@Override
		void updateModel(NormalBayesClassifier model, ParameterList params, TrainData trainData) {}
		
		@Override
		public void predictWithLock(Mat samples, Mat results, Mat probabilities) {
			var model = getStatModel();
			if (probabilities == null)
				probabilities = new Mat();
			model.predictProb(samples, results, probabilities, 0);
		}
	}
	
	/**
	 * Clusterer based on {@link EM}.
	 */
	public static class EMClusterer extends AbstractOpenCVClassifierML<EM> {
		
		EMClusterer() {
			super();
		}
		
		EMClusterer(final EM model) {
			super(model);
		}

		@Override
		ParameterList createParameterList(EM model) {
			var params = new ParameterList();
			
			int nClusters = model.getClustersNumber();
			params.addIntParameter("nClusters", "Number of clusters", nClusters);
			
			return params;
		}

		@Override
		Class<? extends StatModel> getStatModelClass() {
			return EM.class;
		}

		
		@Override
		EM createStatModel() {
			return EM.create();
		}

		@Override
		void updateModel(EM model, ParameterList params, TrainData trainData) {
			model.setClustersNumber(params.getIntParameterValue("nClusters"));
		}
		
	}
	
	/**
	 * Classifier based on {@link SVM}.
	 */
	public static class SVMClassifierCV extends AbstractOpenCVClassifierML<SVM> {

		SVMClassifierCV() {
			super();
		}
		
		SVMClassifierCV(final SVM model) {
			super(model);
		}
		
		@Override
		ParameterList createParameterList(SVM model) {
			var params = new ParameterList();
			return params;
		}

		@Override
		SVM createStatModel() {
			return SVM.create();
		}
		
		@Override
		public boolean supportsAutoUpdate() {
			return false;
		}
		
		@Override
		Class<? extends StatModel> getStatModelClass() {
			return SVM.class;
		}

		@Override
		void updateModel(SVM model, ParameterList params, TrainData trainData) {
			// TODO Auto-generated method stub
		}
		
	}
	
	/**
	 * Classifier based on {@link SVMSGD}.
	 */
	public static class SVMSGDClassifierCV extends AbstractOpenCVClassifierML<SVMSGD> {

		SVMSGDClassifierCV() {
			super();
		}
		
		SVMSGDClassifierCV(final SVMSGD model) {
			super(model);
		}
		
		@Override
		ParameterList createParameterList(SVMSGD model) {
			var params = new ParameterList();
			return params;
		}

		@Override
		SVMSGD createStatModel() {
			return SVMSGD.create();
		}
		
		@Override
		Class<? extends StatModel> getStatModelClass() {
			return SVMSGD.class;
		}
		
		@Override
		public boolean supportsAutoUpdate() {
			return false;
		}

		@Override
		void updateModel(SVMSGD model, ParameterList params, TrainData trainData) {
			// TODO Auto-generated method stub
		}
		
	}
	
	/**
	 * Classifier based on {@link KNearest}.
	 */
	static class KNearestClassifierCV extends AbstractOpenCVClassifierML<KNearest> {

		KNearestClassifierCV() {
			super();
		}
		
		KNearestClassifierCV(final KNearest model) {
			super(model);
		}
		
		@Override
		ParameterList createParameterList(KNearest model) {
			var params = new ParameterList();
			int defaultK = model.getDefaultK();
			params.addIntParameter("defaultK", "Default K", defaultK, null, "Number of nearest neighbors");
			return params;
		}

		@Override
		KNearest createStatModel() {
			return KNearest.create();
		}
		
		@Override
		Class<? extends StatModel> getStatModelClass() {
			return KNearest.class;
		}

		@Override
		void updateModel(KNearest model, ParameterList params, TrainData trainData) {
			int defaultK = params.getIntParameterValue("defaultK");
			model.setDefaultK(defaultK);
			model.setIsClassifier(true);
		}
		
	}
	
	/**
	 * Classifier based on {@link ANN_MLP}.
	 */
	static class ANNClassifierCV extends AbstractOpenCVClassifierML<ANN_MLP> {
		
		private static Logger logger = LoggerFactory.getLogger(ANNClassifierCV.class);
		
		private int MAX_HIDDEN_LAYERS = 5;
		
		static enum ActivationFunction {
			IDENTITY, SIGMOID_SYM, GAUSSIAN, RELU, LEAKY_RELU;
			
			public int getActivationFunction() {
				switch(this) {
				case GAUSSIAN:
					return ANN_MLP.GAUSSIAN;
				case IDENTITY:
					return ANN_MLP.IDENTITY;
				case SIGMOID_SYM:
					return ANN_MLP.SIGMOID_SYM;
				case RELU:
					return ANN_MLP.RELU;
				case LEAKY_RELU:
					return ANN_MLP.LEAKYRELU;
				default:
					return ANN_MLP.SIGMOID_SYM;
				}
			}
		}
		
		static enum TrainingMethod {
			BACKPROP, RPROP, ANNEAL;
			
			public int getTrainingMethod() {
				switch(this) {
				case BACKPROP:
					return ANN_MLP.BACKPROP;
				case RPROP:
					return ANN_MLP.RPROP;
				case ANNEAL:
					return ANN_MLP.ANNEAL;
				default:
					return ANN_MLP.BACKPROP;
				}
			}
		}
		
		
		ANNClassifierCV() {
			super();
		}
		
		ANNClassifierCV(final ANN_MLP model) {
			super(model);
		}

		@Override
		ParameterList createParameterList(ANN_MLP model) {
			// Parse existing layer sizes, if we have them
			Mat sizes = model.getLayerSizes();
			int[] layerSizes;
			if (!sizes.empty()) {
				var idx = sizes.createIndexer();
				int n = (int)sizes.total();
				layerSizes = new int[n];
				for (int i = 0; i < n; i++)
					layerSizes[i] = (int)idx.getDouble(i);
				idx.release();
				MAX_HIDDEN_LAYERS = n;
			} else {
				layerSizes = new int[MAX_HIDDEN_LAYERS];
			}
			
			
			var params = new ParameterList();
			
//			// Set activation function
//			params.addTitleParameter("Activation");
//			params.addChoiceParameter("activation", "Activation function", ActivationFunction.SIGMOID_SYM,
//					Arrays.asList(ActivationFunction.values()), "Choose activation function (only SIGMOID_SYM is fully supported)");
//			params.addDoubleParameter("activationAlpha", "Alpha", 1, null, "Alpha value (influences 'steepness')");
//			params.addDoubleParameter("activationBeta", "Beta", 1, null, "Alpha value (influences 'range')");
			
//			// Set train method
//			params.addTitleParameter("Training method");
//			params.addChoiceParameter("trainMethod", "Training method", TrainingMethod.RPROP,
//					Arrays.asList(TrainingMethod.values()), "Choose training method");
//			params.addDoubleParameter("trainParam1", "Training parameter 1", model.getRpropDW0(), null, "Passed to either setRpropDW0 or setBackpropWeightScale");
//			params.addDoubleParameter("trainParam2", "Training parameter 2", model.getRpropDWMin(), null, "Passed to either setRpropDWMin or setBackpropMomentumScale");
			
			// Hidden layer sizes
			params.addTitleParameter("Hidden layers");
			for (int i = 1; i <= layerSizes.length; i++) {
				params.addIntParameter("hidden" + i, "Layer " + i, layerSizes[i-1], "Nodes", "Size of first hidden layer (0 to omit layer)");				
			}
			
			addTerminationCriteriaParameters(params, model.getTermCriteria());

			return params;
		}
		
		@Override
		protected int getTrainFlags() {
			return ANN_MLP.NO_OUTPUT_SCALE;
		}

		@Override
		ANN_MLP createStatModel() {
			return ANN_MLP.create();
		}
		
		@Override
		Class<? extends StatModel> getStatModelClass() {
			return ANN_MLP.class;
		}
		
		// This shows how to potentially re-weight training samples
//		@Override
//		public void trainWithLock(TrainData trainData) {
//			var statModel = getStatModel();
//			updateModel(statModel, getParameterList(), trainData);
//			statModel.train(trainData, getTrainFlags());
//			
//			// Retrain
//			 Mat results = new Mat();
//			 Mat probabilities = new Mat();
//			 var samples = trainData.getTrainSamples();
//			 var targets = trainData.getTrainResponses();
//			 long n = samples.rows();
//			 Mat weights = new Mat((int)n, 1, opencv_core.CV_32FC1);
//			 FloatIndexer idxTargets = targets.createIndexer();
//			 for (int i = 0; i < 5; i++) {
//				 predictWithLock(samples, results, probabilities);
//				 
//				 IntIndexer idxResults = results.createIndexer();
//				 FloatIndexer idxProbabilities = probabilities.createIndexer();
//				 FloatIndexer idxWeights = weights.createIndexer();
//				 int correct = 0;
//				 for (long j = 0; j < n; j++) {
//					 int col = idxResults.get(j);
//					 double pt = idxProbabilities.get(j, col);
//					 boolean isCorrect = idxTargets.get(j, col) == 1f;
//					 if (isCorrect) {
//						 correct++;
//					 } else {
//						 pt = 1 - pt;
//					 }
//					 // TODO: Calculate weights in a smarter way!
//					 double weight = 1-Math.log(Math.max(pt, 0.1));
//					 idxWeights.put(j, (float)weight);
//				 }
//				 System.err.println(String.format("Correct: %.2f %%", correct * 100.0 / n));
//				 idxResults.release();
//				 idxProbabilities.release();
//				 idxWeights.release();
//				 
//			 	 trainData = TrainData.create(
//			 			samples,
//			 			 trainData.getLayout(),
//			 			 targets,
//			 			 new Mat(),
//			 			 new Mat(),
//			 			weights,
//			 			 trainData.getVarType());
//			 	 statModel.train(trainData, getTrainFlags() + ANN_MLP.UPDATE_WEIGHTS);
//			 }
//		}

		
		@Override
		public TrainData createTrainData(Mat samples, Mat targets, Mat weights, boolean doMulticlass) {
			if (doMulticlass) {
				var indexer = targets.createIndexer();
				var targets2 = new Mat(targets.rows(), targets.cols(), opencv_core.CV_32FC1, Scalar.all(-1.0));
				FloatIndexer idxTargets = targets2.createIndexer();
				int nRows = targets.rows();
				int nCols = targets.cols();
				long[] inds = new long[2];
				for (int r = 0; r < nRows; r++) {
					for (int c = 0; c < nCols; c++) {
						inds[0] = r;
						inds[1] = c;
						double val = indexer.getDouble(inds);
						if (val > 0)
							idxTargets.put(inds, 1f);
					}
				}
				targets.put(targets2);
				targets2.close();
			} else {
				IntBuffer buffer = OpenCVTools.ensureContinuous(targets, false).createBuffer();
				int[] vals = new int[targets.rows()];
				buffer.get(vals);
				int max = Arrays.stream(vals).max().orElseGet(() -> 0) + 1;
				var targets2 = new Mat(targets.rows(), max, opencv_core.CV_32FC1, Scalar.all(-1.0));
				FloatIndexer idxTargets = targets2.createIndexer();
				int row = 0;
				for (var v : vals) {
					idxTargets.put(row, v, 1f);
					row++;
				}
				targets.put(targets2);
				targets2.close();
			}
			
			return super.createTrainData(samples, targets, weights, doMulticlass);
		}
		
		
		@Override
		public void predictWithLock(Mat samples, Mat results, Mat probabilities) {
			// Extract parameters
//			var params = getParameterList();
//			var activation = (ActivationFunction)params.getChoiceParameterValue("activation");
//			double beta = params.getDoubleParameterValue("activationBeta");
			
			// For now, we only support SIGMOID_SYM as an activation function
			// (Not least because we must save/reload models, and there is not get method for this)
			boolean isSigmoidSym = true;
			double beta = 1.0;
			
			// Compute raw values
			if (probabilities == null)
				probabilities = new Mat();
			super.predictWithLock(samples, results, probabilities);
			
			// Convert to the range 0-1 if we can
			if (isSigmoidSym) {
						
				var indexer = probabilities.createIndexer();
				long[] inds = new long[2];
				long rows = indexer.size(0); // previously .rows()
				long cols = indexer.size(1); // previously .cols()
				double scale = 0.5 / beta;
				double offset = 0.5;
						
				for (long r = 0; r < rows; r++) {
					inds[0] = r;
	//				double max = 0;
					for (long c = 0; c < cols; c++) {
						inds[1] = c;
						double val = indexer.getDouble(inds) * scale + offset;
	//					val = val > 1 ? 1 : val;
	//					val = val < 0 ? 0 : val;
						indexer.putDouble(inds, val);
	//					max = Math.max(max, val);
					}
				}
				indexer.release();
			}
			// TODO: Consider softmax for identity or relu activations
		}

		@Override
		void updateModel(ANN_MLP model, ParameterList params, TrainData trainData) {
			int nMeasurements = trainData.getNVars();
			int nClasses = trainData.getResponses().cols();
			
			var layers = new double[MAX_HIDDEN_LAYERS + 2];
			layers[0] = nMeasurements;
			int n = 1;
			for (int i = 1; i <= MAX_HIDDEN_LAYERS; i++) {
				String name = "hidden" + i;
				if (!params.containsKey(name))
					continue;
				int size = params.getIntParameterValue(name);
				// Every layer needs more than one neuron
				if (size > 1) {
					layers[n] = size;
					n++;
				}
			}
			layers[n] = nClasses;
			n++;
			if (n < layers.length)
				layers = Arrays.copyOf(layers, n);
			
			var mat = new Mat(n, 1, opencv_core.CV_64F, Scalar.ZERO);
			DoubleIndexer idx = mat.createIndexer();
			for (int i = 0; i < n; i++)
				idx.put(i, layers[i]);
			idx.release();
			
			model.setLayerSizes(mat);
			
			// Set other parameters
//			var activation = (ActivationFunction)params.getChoiceParameterValue("activation");
//			double activationAlpha = params.getDoubleParameterValue("activationAlpha");
//			double activationBeta = params.getDoubleParameterValue("activationBeta");
//			model.setActivationFunction(activation.getActivationFunction(), activationAlpha, activationBeta);
			model.setActivationFunction(ANN_MLP.SIGMOID_SYM, 1, 1);

//			var trainMethod = (TrainingMethod)params.getChoiceParameterValue("trainMethod");
//			double param1 = params.getDoubleParameterValue("trainParam1");
//			double param2 = params.getDoubleParameterValue("trainParam2");
//			model.setTrainMethod(trainMethod.getTrainingMethod(), param1, param2);

			// Set termination criterion
			model.setTermCriteria(updateTermCriteria(params, model.getTermCriteria()));
			
			logger.debug("Initializing ANN with layer sizes: " + GeneralTools.arrayToString(Locale.getDefault(), layers, 0));
		}
		
	}
	
	/**
	 * A multiclass version of ANN.
	 */
	static class MulticlassANNClassifierCV extends ANNClassifierCV {
		
		@Override
		public boolean supportsMulticlass() {
			return true;
		}
		
		@Override
		public String getName() {
			return "ANN MLP (Multiclass)";
		}
		
	}

}