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

import java.util.Arrays;

import org.bytedeco.opencv.opencv_ml.SVM;

import qupath.lib.plugins.parameters.ParameterList;

/**
 * Wrapper for OpenCV's SVM classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class SVMClassifier extends ParameterizableOpenCvClassifier<SVM> {

	@Override
	protected SVM createClassifier() {
		SVM svm = SVM.create();
		ParameterList params = getParameterList();
//		System.out.println("Default C: " + svm.getC());
		
		if (params != null) {
			String kernel = (String)params.getChoiceParameterValue("kernel");
			kernel = kernel.toLowerCase();
			if (kernel.equals("linear"))
				svm.setKernel(SVM.LINEAR);
			else if (kernel.equals("polynomial"))
				svm.setKernel(SVM.POLY);
			else if (kernel.equals("rbf"))
				svm.setKernel(SVM.RBF);
			else if (kernel.equals("sigmoid"))
				svm.setKernel(SVM.SIGMOID);
			else if (kernel.equals("chi2"))
				svm.setKernel(SVM.CHI2);
			else if (kernel.equals("histogram intersection"))
				svm.setKernel(SVM.INTER);
			
			double c = params.getDoubleParameterValue("c");
			if (c > 0)
				svm.setC(c);
			svm.setGamma(params.getDoubleParameterValue("gamma"));
			svm.setDegree(params.getIntParameterValue("degree"));
		}
		return svm;
	}
	
	@Override
	public String getName() {
		return "SVM";
	}

	@Override
	public boolean supportsAutoUpdate() {
		return false;
	}

	@Override
	protected ParameterList createParameterList() {
		return new ParameterList()
				.addChoiceParameter("kernel", "Kernel type", "RBF", Arrays.asList("Linear", "Polynomial", "RBF", "Histogram intersection"), "SVM kernel type")
				.addDoubleParameter("c", "C", 1, null, "C parameter for SVM optimization; must be > 0")
				.addDoubleParameter("gamma", "Gamma", 1, null, "Gamma parameter for SVM optimization")
				.addIntParameter("degree", "Polynomial degree", 0, null, "Set polynomial degree - only relevant to 'polynomial' kernal type. Lower values preferred for stability.");
	}

}
