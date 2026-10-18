/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.opencv.ml.models.statmodel;

import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.Boost;
import org.bytedeco.opencv.opencv_ml.DTrees;
import org.bytedeco.opencv.opencv_ml.EM;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.LogisticRegression;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.SVM;
import org.bytedeco.opencv.opencv_ml.SVMSGD;
import org.bytedeco.opencv.opencv_ml.StatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.io.GsonTools;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.opencv.ml.models.PredictionModel;

/**
 * Class provide access to wrappers for OpenCV stat models.
 * There are two main reasons to use these wrappers rather than {@link StatModel} directly:
 * <ol>
 *   <li>Improved API consistency when exchanging between classifiers. For example, some require 
 *   training data to be in a specified form (labels or one-hot encoding).</li>
 *   <li>Easier serialization to JSON along with other QuPath objects via {@link GsonTools}.</li>
 * </ol>
 */
public class OpenCVStatModels {

	private static final Logger logger = LoggerFactory.getLogger(OpenCVStatModels.class);

	/**
	 * Enum representing all the OpenCV {@link StatModel} implementations for which QuPath wrappers have been developed.
	 * The wrappers standardize training and prediction, so that stat models may be used for pixel and object classifiers.
	 */
	public enum Models {

		R_TREES,
		BOOST,
		D_TREES,
		KNN,
		ANN,
		LOGISTIC_REGRESSION,
		EM_CLUSTERER,
		NORMAL_BAYES,
		SVM,
		SVM_SGD;

		/**
		 * Create a trainable wrapper for an OpenCV {@link StatModel}.
		 * @return a trainable model, using the stat model defined by the enum value
		 */
		public TrainableStatModel<?> createTrainableModel() {
			return switch(this) {
                case R_TREES -> new TrainableStatModel<>(new RTreesClassifier());
                case BOOST -> new TrainableStatModel<>(new BoostClassifier());
                case D_TREES -> new TrainableStatModel<>(new DTreesClassifier());
                case KNN -> new TrainableStatModel<>(new KNearestClassifier());
                case ANN -> new TrainableStatModel<>(new ANNClassifier());
                case LOGISTIC_REGRESSION -> new TrainableStatModel<>(new LogisticRegressionClassifier());
                case EM_CLUSTERER -> new TrainableStatModel<>(new EMClusterer());
                case NORMAL_BAYES -> new TrainableStatModel<>(new NormalBayesClassifier());
                case SVM -> new TrainableStatModel<>(new SVMClassifier());
                case SVM_SGD -> new TrainableStatModel<>(new SVMSGDClassifier());
            };
		}

	}

	/**
	 * Create an {@link PredictionModel} by wrapping an existing {@link StatModel}.
	 * @param statModel
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static PredictionModel wrapStatModel(StatModel statModel) {
		// Technically is *is* trainable... but the intended use is for prediction only,
		// since we can't guarantee that parameter values match the actual model
		// (because they can't all be queried)
		return new TrainableStatModel<>(wrap(statModel));
	}

	@SuppressWarnings("unchecked")
	static <T extends StatModel> AbstractOpenCVClassifier<T> wrap(T statModel) {
		var cls = statModel.getClass();

		if (statModel instanceof RTrees rTrees && RTrees.class.equals(cls))
			return (AbstractOpenCVClassifier)new RTreesClassifier(rTrees);

		if (statModel instanceof Boost boost && Boost.class.equals(cls))
			return (AbstractOpenCVClassifier)new BoostClassifier(boost);

		if (statModel instanceof DTrees trees && DTrees.class.equals(cls))
			return (AbstractOpenCVClassifier)new DTreesClassifier(trees);

		if (statModel instanceof KNearest knn && KNearest.class.equals(cls))
			return (AbstractOpenCVClassifier)new KNearestClassifier(knn);

		if (statModel instanceof ANN_MLP ann && ANN_MLP.class.equals(cls))
			return (AbstractOpenCVClassifier)new ANNClassifier(ann);

		if (statModel instanceof LogisticRegression lr && LogisticRegression.class.equals(cls))
			return (AbstractOpenCVClassifier)new LogisticRegressionClassifier(lr);

		if (statModel instanceof EM em && EM.class.equals(cls))
			return (AbstractOpenCVClassifier)new EMClusterer(em);

		if (statModel instanceof org.bytedeco.opencv.opencv_ml.NormalBayesClassifier nb && org.bytedeco.opencv.opencv_ml.NormalBayesClassifier.class.equals(cls))
			return (AbstractOpenCVClassifier)new NormalBayesClassifier(nb);

		if (statModel instanceof SVM svm && SVM.class.equals(cls))
			return (AbstractOpenCVClassifier)new SVMClassifier(svm);

		if (statModel instanceof SVMSGD svmsgd && SVMSGD.class.equals(cls))
			return (AbstractOpenCVClassifier)new SVMSGDClassifier(svmsgd);

		throw new IllegalArgumentException("Unknown StatModel class " + cls);
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


}