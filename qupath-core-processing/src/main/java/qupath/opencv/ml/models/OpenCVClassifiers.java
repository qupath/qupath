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

package qupath.opencv.ml.models;

import java.util.List;
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
			return new KNearestClassifier();
		
		if (ANN_MLP.class.equals(cls))
			return new ANNClassifier();
		
		if (LogisticRegression.class.equals(cls))
			return new LogisticRegressionClassifier();
		
		if (EM.class.equals(cls))
			return new EMClusterer();

		if (org.bytedeco.opencv.opencv_ml.NormalBayesClassifier.class.equals(cls))
			return new NormalBayesClassifier();
		
		if (SVM.class.equals(cls))
			return new SVMClassifier();
		
		if (SVMSGD.class.equals(cls))
			return new SVMSGDClassifier();
		
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
			return new KNearestClassifier((KNearest)statModel);
		
		if (ANN_MLP.class.equals(cls))
			return new ANNClassifier((ANN_MLP)statModel);
		
		if (LogisticRegression.class.equals(cls))
			return new LogisticRegressionClassifier((LogisticRegression)statModel);
		
		if (EM.class.equals(cls))
			return new EMClusterer((EM)statModel);

		if (org.bytedeco.opencv.opencv_ml.NormalBayesClassifier.class.equals(cls))
			return new NormalBayesClassifier((org.bytedeco.opencv.opencv_ml.NormalBayesClassifier)statModel);
		
		if (SVM.class.equals(cls))
			return new SVMClassifier((SVM)statModel);
		
		if (SVMSGD.class.equals(cls))
			return new SVMSGDClassifier((SVMSGD)statModel);
		
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