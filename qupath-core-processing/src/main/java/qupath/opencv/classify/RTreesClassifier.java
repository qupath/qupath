/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.opencv.classify;

import java.util.List;

import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.TermCriteria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.parameters.Parameterizable;


/**
 * Wrapper for OpenCV's Random Forests classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class RTreesClassifier extends ParameterizableOpenCvClassifier<RTrees> implements Parameterizable {
	
	private static final long serialVersionUID = 214514118006174724L;
	
	private static Logger logger = LoggerFactory.getLogger(RTreesClassifier.class);
	
//	private transient String lastDescription = null;
	
	private transient TermCriteria termCriteria;
	
	@Override
	protected ParameterList createParameterList() {
		ParameterList params = new ParameterList();
		params.addIntParameter("maxDepth", "Max tree depth", 0, null, 0, 20, "Maximum tree depth; if zero, then the depth is effectively unlimited (set to max int)");
		params.addIntParameter("minSamples", "Min samples per node", 10, null, "Minimum number of samples per node - the node will not be split if the number of samples is less than this (default = 10)");
		params.addBooleanParameter("use1SE", "Prune aggressively", true, "If true, more aggressive pruning is used - likely sacrificing some accuracy for more robustness");
		
		params.addIntParameter("termCritMaxTrees", "Termination criterion - max trees", 50, null, "Optional termination criterion based on maximum number of trees - set <= 0 to disable and use accuracy criterion only");
		params.addDoubleParameter("termCritEPS", "Termination criterion - accuracy", 0.0, null, "Optional termination criterion based on out-of-bag error - set <= 0 to disable and use max trees only.  Note: probabilities are only provided when this is zero.");
		return params;
	}

	@Override
	protected RTrees createClassifier() {
		RTrees trees = RTrees.create();
		ParameterList params = getParameterList();
		if (params != null) {
			int maxDepth = params.getIntParameterValue("maxDepth");
			int minSamples = params.getIntParameterValue("minSamples");
			boolean use1SE = params.getBooleanParameterValue("use1SE");
			trees.setMaxDepth(maxDepth == 0 ? Integer.MAX_VALUE : maxDepth);
			trees.setMinSampleCount(minSamples);
			trees.setUse1SERule(use1SE);
			
			// setUseSurrogates should help with missing data... but it appears not actually to be implemented
//			System.out.println("DEFAULT SURROGATES: " + trees.getUseSurrogates());
//			trees.setUseSurrogates(true);
			
			// Set termination criteria
			int termCritMaxTrees = params.getIntParameterValue("termCritMaxTrees");
			double termCritEPS = params.getDoubleParameterValue("termCritEPS");
			termCriteria = createTerminationCriteria(termCritMaxTrees, termCritEPS);
			if (termCriteria != null)
				trees.setTermCriteria(termCriteria);
			else
				termCriteria = trees.getTermCriteria();
			
			logger.info("RTrees classifier termination criteria: {}", termCriteria);
		}
//			lastDescription = getName() + "\n\nMain parameters:\n  " + DefaultPluginWorkflowStep.getParameterListJSON(params, "\n  ") + "\n\nTermination criteria:\n  " + termCriteria.toString();
//			
//		} else
//			lastDescription = null;
		
//		trees.setCVFolds(5); // Seems to cause segfault...
//		trees.setCalculateVarImportance(true); // Seems to require surrogates, but...
//		trees.setUseSurrogates(true); // // Seems not yet to be supported...
		
		return trees;
	}
	
	
	@Override
	protected void setPredictedClass(final RTrees classifier, final List<PathClass> pathClasses, final Mat samples, final Mat results, final PathObject pathObject) {
		if (pathClasses.size() == 2 && termCriteria != null && ((TermCriteria.EPS & termCriteria.type()) == 0) && termCriteria.maxCount() > 0) {
			double prediction = classifier.predict(samples, results, RTrees.PREDICT_SUM)  / termCriteria.maxCount();
			int index = (int)Math.round(prediction); // Round the prediction
			// Convert to a probability based on the number of trees
			double probability = prediction;
			if (index == 0)
				probability = 1 - probability;
			// Set the class & probability
			PathClass pathClass = pathClasses.get(index);
			pathObject.setPathClass(pathClass, probability);			
		} else
			super.setPredictedClass(classifier, pathClasses, samples, results, pathObject);
	}
	
	
	
//	public void updateClassifier(final ImageData<?> imageData, final List<String> measurements, final int maxTrainingInstances) {
//		super.updateClassifier(imageData, measurements, maxTrainingInstances);
//		// Output the variable importance
//		logger.info("Active var count: {}", classifier.getActiveVarCount());
//		logger.info("Var count: {}", classifier.getVarCount());
//		logger.info("Priors: {}", classifier.getPriors().dump());
////		logger.info("CV FOLDS: " + trees.getCVFolds());
////		logger.info("Variable importance: {}", trees.getVarImportance().dump());
//	}
	
	
	
	@Override
	public String getName() {
		return "Random Trees";
	}

	@Override
	public boolean supportsAutoUpdate() {
		return true;
	}
	
	
//	@Override
//	public String getDescription() {
//		return (isValid() && lastDescription != null) ? lastDescription : super.getDescription();
//	}
	

}