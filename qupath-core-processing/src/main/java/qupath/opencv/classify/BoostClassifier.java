/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.opencv.classify;

import java.util.Arrays;
import java.util.List;

import org.bytedeco.opencv.opencv_ml.Boost;
import org.bytedeco.opencv.opencv_core.Mat;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.plugins.parameters.ParameterList;


/**
 * Wrapper for OpenCV's Boosted Decision Trees classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class BoostClassifier extends ParameterizableOpenCvClassifier<Boost> {

	@Override
	protected Boost createClassifier() {
		Boost boost = Boost.create();
		
//		System.out.println("Type: " + boost.getBoostType());
//		System.out.println("CV folds: " + boost.getCVFolds());
//		System.out.println("Var count: " + boost.getVarCount());
//		System.out.println("Weak count: " + boost.getWeakCount());
//		System.out.println("Min sample count: " + boost.getMinSampleCount());
//		System.out.println("Max depth: " + boost.getMaxDepth());
		
		ParameterList params = getParameterList();
		if (params != null) {
			String type = (String)params.getChoiceParameterValue("boostType");
			type = type.toLowerCase();
			if (type.equals("discrete"))
				boost.setBoostType(Boost.DISCRETE);
			else if (type.equals("real"))
				boost.setBoostType(Boost.REAL);
			else if (type.equals("logit"))
				boost.setBoostType(Boost.LOGIT);
			else if (type.equals("gentle"))
				boost.setBoostType(Boost.GENTLE);
			boost.setWeakCount(params.getIntParameterValue("weakCount"));			
			boost.setMaxDepth(params.getIntParameterValue("maxDepth"));
		}
		boost.setWeightTrimRate(0);
		
		return boost;
	}
	
	@Override
	public String getName() {
		return "Boosted Decision Trees";
	}
	
	@Override
	public boolean supportsAutoUpdate() {
		return true;
	}

	@Override
	protected ParameterList createParameterList() {
		return new ParameterList()
				.addChoiceParameter("boostType", "Boost type", "Real", Arrays.asList("Discrete", "Real", "Logit", "Gentle"))
				.addIntParameter("weakCount", "Number of weak classifiers", 100, null, 1, 1000, "Number of weak classifiers")
				.addIntParameter("maxDepth", "Max tree depth", 1, null, 1, 5, "Maximum tree depth (default = 1)");
	}

	
	@Override
	protected void setPredictedClass(final Boost classifier, final List<PathClass> pathClasses, final Mat samples, final Mat results, final PathObject pathObject) {
		// It's hard to decipher the raw values... in the end, it classification makes use of the sign of this value (not its magnitude) - therefore it only suits for 2-class problems
//		if (pathClasses.size() == 2) {
//			double predictionRaw = classifier.predict(samples, results, Boost.PREDICT_SUM);
//			double prediction = classifier.predict(samples, results, Boost.RAW_OUTPUT);
////			prediction /= classifier.getWeakCount();
//			int index = (int)Math.round(prediction); // Round the prediction
//			// Convert to a probability based on the number of trees
//			double probability = predictionRaw;
////			if (index == 0)
////				probability = 1 - probability;
//			// Set the class & probability
//			PathClass pathClass = pathClasses.get(index);
//			pathObject.setPathClass(pathClass, probability);			
//		} else
			super.setPredictedClass(classifier, pathClasses, samples, results, pathObject);
	}
	
	
}
