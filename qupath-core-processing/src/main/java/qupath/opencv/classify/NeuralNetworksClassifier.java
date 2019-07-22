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

import static org.bytedeco.opencv.global.opencv_core.*;

import org.bytedeco.opencv.global.opencv_ml;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_ml.*;

import java.nio.FloatBuffer;

import org.bytedeco.javacpp.indexer.FloatIndexer;

import qupath.lib.plugins.parameters.ParameterList;

/**
 * Wrapper for OpenCV's Neural Networks classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class NeuralNetworksClassifier extends ParameterizableOpenCvClassifier<ANN_MLP> {

//	private transient String lastDescription = null;
	
	
	@Override
	protected ANN_MLP createClassifier() {
		ANN_MLP classifier = ANN_MLP.create();
		classifier.setLayerSizes(new Mat());
		return classifier;
	}

	@Override
	public String getName() {
		return "Neural Networks";
	}

	@Override
	public boolean supportsAutoUpdate() {
		return false;
	}


	@Override
	protected void createAndTrainClassifier() {
		// Create the required Mats
		int nMeasurements = measurements.size();
		Mat matTraining = new Mat(arrayTraining.length / nMeasurements, nMeasurements, CV_32FC1);
		((FloatBuffer)matTraining.createBuffer()).put(arrayTraining);

		// Parse parameters
		ParameterList params = getParameterList();
		int nHidden = Math.max(2, params.getIntParameterValue("nHidden"));
		int termIterations = params.getIntParameterValue("termCritMaxIterations");
		double termEPS = params.getDoubleParameterValue("termCritEPS");
		TermCriteria crit = createTerminationCriteria(termIterations, termEPS);

		
		// Create & train the classifier
		classifier = createClassifier();
		ANN_MLP nnet = (ANN_MLP)classifier;
		System.out.println(nnet.getLayerSizes());
		Mat layers = new Mat(3, 1, CV_32F);
		int n = arrayTraining.length / nMeasurements;
//		layers.put(0, 0, new float[]{nMeasurements, nHidden, pathClasses.size()});
		FloatBuffer bufLayers = layers.createBuffer();
		bufLayers.clear();
		bufLayers.put(nMeasurements);
		bufLayers.put(nHidden); // Number of hidden layers
		bufLayers.put(pathClasses.size());
		if (crit != null)
			nnet.setTermCriteria(crit);
		else
			crit = nnet.getTermCriteria();
		nnet.setLayerSizes(layers);
		//			matResponses.convertTo(matResponses, CV_32F);
		Mat matResponses = new Mat(n, pathClasses.size(), CV_32F, Scalar.ZERO);
		FloatIndexer indexerResponses = matResponses.createIndexer();
		for (int i = 0; i < n; i++) {
			indexerResponses.put(i, arrayResponses[i], 1);
		indexerResponses.release();
//			matResponses.put(i, arrayResponses[i], 1);
		}
		nnet.setActivationFunction(ANN_MLP.SIGMOID_SYM, 1, 1);
		nnet.train(matTraining, opencv_ml.ROW_SAMPLE, matResponses);
		
//		lastDescription = getName() + "\n\nMain parameters:\n  " + DefaultPluginWorkflowStep.getParameterListJSON(params, "\n  ") + "\n\nTermination criteria:\n  " + crit.toString();
	}

	@Override
	protected ParameterList createParameterList() {
		ParameterList params = new ParameterList();
		params.addIntParameter("nHidden", "Number of hidden layers", 8, null, "Number of hidden layers for neural network (must be >= 2)");
		
		params.addIntParameter("termCritMaxIterations", "Termination criterion - max iterations", 100, null, "Optional termination criterion based on maximum number of iterations - set <= 0 to disable and use accuracy criterion only");
		params.addDoubleParameter("termCritEPS", "Termination criterion - accuracy", 0.01, null, "Optional termination criterion based on out-of-bag error - set <= 0 to disable and use max trees only");
		return params;
	}
	
	
//	@Override
//	public String getDescription() {
//		return (isValid() && lastDescription != null) ? lastDescription : super.getDescription();
//	}


}
