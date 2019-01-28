package qupath.lib.classifiers.opencv;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_ml;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.JsonAdapter;

import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.bytedeco.javacpp.opencv_core.TermCriteria;
import org.bytedeco.javacpp.opencv_core.UMat;
import org.bytedeco.javacpp.opencv_ml.ANN_MLP;
import org.bytedeco.javacpp.opencv_ml.Boost;
import org.bytedeco.javacpp.opencv_ml.DTrees;
import org.bytedeco.javacpp.opencv_ml.EM;
import org.bytedeco.javacpp.opencv_ml.KNearest;
import org.bytedeco.javacpp.opencv_ml.LogisticRegression;
import org.bytedeco.javacpp.opencv_ml.NormalBayesClassifier;
import org.bytedeco.javacpp.opencv_ml.RTrees;
import org.bytedeco.javacpp.opencv_ml.SVM;
import org.bytedeco.javacpp.opencv_ml.SVMSGD;
import org.bytedeco.javacpp.opencv_ml.StatModel;
import org.bytedeco.javacpp.opencv_ml.TrainData;

import qupath.lib.classifiers.Normalization;
import qupath.lib.common.GeneralTools;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.opencv.processing.TypeAdaptersCV;

public class OpenCVClassifiers {
	
	
	
	public static class FeaturePreprocessor {
		
		private Normalizer normalizer;
		private PCAProjector pca;
		
		public void apply(Mat mat) {
			int rows = mat.rows();
			int cols = mat.cols();
			int channels = mat.channels();
			if (channels > 1)
				mat.put(mat.reshape(1, rows * cols));
			if (normalizer != null)
				Normalizer.normalize(mat, normalizer);
			if (pca != null)
				pca.project(mat, mat);
			if (channels > 1)
				mat.put(mat.reshape((int)(mat.total()/(rows*cols)), rows));
		}
		
		public static class Builder {
			
			private Normalization normalization = Normalization.NONE;
			private double missingValue = Double.NaN;
			private double pcaRetainedVariance = -1;
			private boolean pcaNormalize = true;
			
			private Normalizer normalizer;
			private PCAProjector pca;
			
			public Builder() {}
			
			public Builder normalize(Normalization normalization) {
				this.normalization = normalization;
				return this;
			}
			
			public Builder missingValue(double missingValue) {
				this.missingValue = missingValue;
				return this;
			}
						
			public Builder pca(double retainedVariance, boolean pcaNormalize) {
				this.pcaRetainedVariance = retainedVariance;
				this.pcaNormalize = pcaNormalize;
				return this;
			}
			
			public FeaturePreprocessor buildAndApply(Mat trainingData) {
				if (normalization != Normalization.NONE || !Double.isNaN(missingValue)) {
					this.normalizer = Normalizer.createNormalizer(normalization, trainingData, missingValue);
					Normalizer.normalize(trainingData, normalizer);						
				}
				
				if (pcaRetainedVariance > 0) {
					this.pca = PCAProjector.createPCAProjector(trainingData, pcaRetainedVariance, pcaNormalize);
					this.pca.project(trainingData, trainingData);
				}
				var features = new FeaturePreprocessor();
				features.normalizer = this.normalizer;
				features.pca = this.pca;
				return features;
			}
			
		}
		
	}
	
	
	
	
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
	
	public static abstract class OpenCVStatModel {
		
		public abstract boolean supportsMissingValues();
		
		public abstract String getName();
		
		public abstract boolean isTrained();
		
		public abstract boolean supportsAutoUpdate();
		
		public abstract boolean supportsProbabilities();
		
		public abstract ParameterList getParameterList();
				
		public abstract TrainData createTrainData(Mat samples, Mat targets);
		
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
		
	}
	
	
	
	static abstract class AbstractOpenCVClassifierML<T extends StatModel> extends OpenCVStatModel {

		@JsonAdapter(TypeAdaptersCV.OpenCVTypeAdaptorFactory.class)
		private T model;
		private transient ParameterList params; // Should take defaults from the serialized model
		
		transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		
		abstract ParameterList createParameterList(T model);
		
		abstract T createStatModel();
		
		abstract void updateModel(T model, ParameterList params, TrainData trainData);
		
		AbstractOpenCVClassifierML() {
			model = createStatModel();
			params = createParameterList(model);
		}
		
		AbstractOpenCVClassifierML(T model) {
			this.model = model;
			params = createParameterList(model);
		}
		
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
		
		T getStatModel() {
			return model;
		}
		
		@Override
		public boolean isTrained() {
			return getStatModel().isTrained();
		}
		
		@Override
		public ParameterList getParameterList() {
			return params;
		}
				
		@Override
		public String toString() {
			return getName();
		}
		
		public TrainData createTrainData(Mat samples, Mat targets) {
			if (useUMat()) {
				UMat uSamples = samples.getUMat(opencv_core.ACCESS_READ);
				UMat uTargets = samples.getUMat(opencv_core.ACCESS_READ);
				return TrainData.create(uSamples, opencv_ml.ROW_SAMPLE, uTargets);
			}
			return TrainData.create(samples, opencv_ml.ROW_SAMPLE, targets);
		}
		
		boolean useUMat() {
			return false;
		}

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
		 * @see predictWithLock
		 */
		public void trainWithLock(TrainData trainData) {
			var statModel = getStatModel();
			updateModel(statModel, params, trainData);
//			statModel.train(trainData);
			statModel.train(trainData, getTrainFlags());
		}
		
		protected int getTrainFlags() {
			return 0;
		}

		@Override
		public String getName() {
			var model = getStatModel();
			
			if (ANN_MLP.class.equals(model.getClass()))
				return "Artificial neural network (ANN_MLP)";
			else if (RTrees.class.equals(model.getClass()))
				return "Random trees (RTrees)";
			else if (Boost.class.equals(model.getClass()))
				return "Boosted trees (Boost)";
			else if (DTrees.class.equals(model.getClass()))
				return "Decision tree (DTrees)";
			else if (EM.class.equals(model.getClass()))
				return "Expectation maximization";
			else if (KNearest.class.equals(model.getClass()))
				return "K nearest neighbor";
			else if (LogisticRegression.class.equals(model.getClass()))
				return "Logistic regression";
			else if (NormalBayesClassifier.class.equals(model.getClass()))
				return "Normal Bayes classifier";
			
			return model.getClass().getSimpleName();
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
		 * @see trainWithLock
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
		public boolean supportsMissingValues() {
			return getStatModel() instanceof DTrees;
		}
		
		
	}
	
	
	static class DefaultOpenCVStatModel<T extends StatModel> extends AbstractOpenCVClassifierML<T> {

		private T model;
		
		DefaultOpenCVStatModel(T model) {
			this.model = model;
		}
		
		@Override
		ParameterList createParameterList(T model) {
			return new ParameterList();
		}

		@Override
		T createStatModel() {
			return model;
		}

		/**
		 * No updates performed.
		 */
		@Override
		void updateModel(StatModel model, ParameterList params, TrainData trainData) {
			// Perform no updates
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
			model.setMaxDepth(maxDepth < 1 ? 1 : maxDepth);
			model.setMinSampleCount(minSampleCount < 1 ? 1 : minSampleCount);
//			model.setRegressionAccuracy(regressionAccuracy < 1e-6f ? 1e-6f : regressionAccuracy);
			model.setUse1SERule(use1SERule);
		}
		
		
	}
	
	
	static class DTreesClassifier extends AbstractTreeClassifier<DTrees> {

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
		
	}
	
	
	static class RTreesClassifier extends AbstractTreeClassifier<RTrees> {
		
		private double[] featureImportance;
		
		RTreesClassifier() {
			super();
		}
		
		RTreesClassifier(final RTrees model) {
			super(model);
		}
		
		@Override
		RTrees createStatModel() {
			return RTrees.create();
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
					int nFeatures = (int)indexer.rows();
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
	
	
	static class BoostClassifier extends AbstractTreeClassifier<Boost> {
		
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
	
	
	static class LogisticRegressionClassifier extends AbstractOpenCVClassifierML<LogisticRegression> {
		
		private static Map<Integer, String> regularization;
		static {
			regularization = new LinkedHashMap<Integer, String>();
			regularization.put(LogisticRegression.REG_DISABLE, "None");
			regularization.put(LogisticRegression.REG_L1, "L1");
			regularization.put(LogisticRegression.REG_L2, "L2");
			regularization = Collections.unmodifiableMap(regularization);
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
//			int miniBatchSize = model.getMiniBatchSize();
			
			params.addDoubleParameter("learningRate", "Learning rate", learningRate);
			params.addIntParameter("nIterations", "Number of iterations", nIterations);
//			params.addIntParameter("miniBatchSize", "Mini batch size", miniBatchSize);
			params.addChoiceParameter("regularization", "Regularization", regularization.get(reg), regularization.values().toArray(new String[0]));
			
			return params;
		}
		
		public TrainData createTrainData(Mat samples, Mat targets) {
			targets.convertTo(targets, opencv_core.CV_32F);
			return super.createTrainData(samples, targets);
		}

		@Override
		LogisticRegression createStatModel() {
			return LogisticRegression.create();
		}
		
		@Override
		void updateModel(LogisticRegression model, ParameterList params, TrainData trainData) {
			double learningRate = params.getDoubleParameterValue("learningRate");
			int nIterations = params.getIntParameterValue("nIterations");
			String regString = (String)params.getChoiceParameterValue("regularization");
			
			int reg = model.getRegularization();
			for (var entry : regularization.entrySet()) {
				if (entry.getValue().equals(regString)) {
					reg = entry.getKey();
					break;
				}
			}
			
			model.setLearningRate(learningRate);
			model.setIterations(nIterations);
			model.setRegularization(reg);
		}
		
	}
	
	
	
	static class NormalBayesClassifierCV extends AbstractOpenCVClassifierML<NormalBayesClassifier> {

		NormalBayesClassifierCV() {
			super();
		}
		
		NormalBayesClassifierCV(final NormalBayesClassifier model) {
			super(model);
		}
		
		@Override
		ParameterList createParameterList(NormalBayesClassifier model) {
			var params = new ParameterList();
			return params;
		}

		@Override
		NormalBayesClassifier createStatModel() {
			return NormalBayesClassifier.create();
		}

		@Override
		void updateModel(NormalBayesClassifier model, ParameterList params, TrainData trainData) {}
		
		public void predictWithLock(Mat samples, Mat results, Mat probabilities) {
			var model = getStatModel();
			if (probabilities == null)
				probabilities = new Mat();
			model.predictProb(samples, results, probabilities, 0);
		}
	}
	
	
	static class EMClusterer extends AbstractOpenCVClassifierML<EM> {
		
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
		EM createStatModel() {
			return EM.create();
		}

		@Override
		void updateModel(EM model, ParameterList params, TrainData trainData) {
			model.setClustersNumber(params.getIntParameterValue("nClusters"));
		}
		
	}
	
	static class SVMClassifierCV extends AbstractOpenCVClassifierML<SVM> {

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
		void updateModel(SVM model, ParameterList params, TrainData trainData) {
			// TODO Auto-generated method stub
		}
		
	}
	
	
	static class SVMSGDClassifierCV extends AbstractOpenCVClassifierML<SVMSGD> {

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
		public boolean supportsAutoUpdate() {
			return false;
		}

		@Override
		void updateModel(SVMSGD model, ParameterList params, TrainData trainData) {
			// TODO Auto-generated method stub
		}
		
	}
	
	
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
		void updateModel(KNearest model, ParameterList params, TrainData trainData) {
			int defaultK = params.getIntParameterValue("defaultK");
			model.setDefaultK(defaultK);
			model.setIsClassifier(true);
		}
		
	}
	
	
	static class ANNClassifierCV extends AbstractOpenCVClassifierML<ANN_MLP> {
		
		private static Logger logger = LoggerFactory.getLogger(ANNClassifierCV.class);
		
		private static int MAX_HIDDEN_LAYERS = 5;
		
		/**
		 * Alpha value for activation function (influences 'steepness')
		 */
		private double activationAlpha = 1.0;
		
		/**
		 * Beta value for activation function (influences range)
		 */
		private double activationBeta = 1.0;
		
		ANNClassifierCV() {
			super();
		}
		
		ANNClassifierCV(final ANN_MLP model) {
			super(model);
		}

		@Override
		ParameterList createParameterList(ANN_MLP model) {
			model.getLayerSizes();
			
			var params = new ParameterList();
			
			for (int i = 1; i <= MAX_HIDDEN_LAYERS; i++) {
				params.addIntParameter("hidden" + i, "Hidden layer " + i, 0, "Nodes", "Size of first hidden layer (0 to omit layer)");				
			}
			
			return params;
		}
		
		protected int getTrainFlags() {
			return ANN_MLP.NO_OUTPUT_SCALE;
		}

		@Override
		ANN_MLP createStatModel() {
			return ANN_MLP.create();
		}
		
		public TrainData createTrainData(Mat samples, Mat targets) {
			
			IntBuffer buffer = targets.createBuffer();
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
			
			return super.createTrainData(samples, targets);
		}
		
		
		public void predictWithLock(Mat samples, Mat results, Mat probabilities) {
			double beta = activationBeta;
			if (probabilities == null)
				probabilities = new Mat();
			super.predictWithLock(samples, results, probabilities);
			// Convert to the range 0-1
			var indexer = probabilities.createIndexer();
			long[] inds = new long[2];
			long rows = indexer.rows();
			long cols = indexer.cols();
			for (long r = 0; r < rows; r++) {
				inds[0] = r;
//				double max = 0;
				for (long c = 0; c < cols; c++) {
					inds[1] = c;
					double val = indexer.getDouble(inds) / (beta * 2.0) + 0.5;
//					val = val > 1 ? 1 : val;
//					val = val < 0 ? 0 : val;
					indexer.putDouble(inds, val);
//					max = Math.max(max, val);
				}
			}
			indexer.release();
		}

		@Override
		void updateModel(ANN_MLP model, ParameterList params, TrainData trainData) {
			int nMeasurements = trainData.getNVars();
			int nClasses = trainData.getResponses().cols();
			
			var layers = new double[MAX_HIDDEN_LAYERS + 2];
			layers[0] = nMeasurements;
			int n = 1;
			for (int i = 1; i <= MAX_HIDDEN_LAYERS; i++) {
				int size = params.getIntParameterValue("hidden" + i);
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
			model.setActivationFunction(ANN_MLP.SIGMOID_SYM, activationAlpha, activationBeta);
			
			logger.info("Initializing with layer sizes: " + GeneralTools.arrayToString(Locale.getDefault(), layers, 0));
		}
		
	}

}
