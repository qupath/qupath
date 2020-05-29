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

import org.bytedeco.opencv.opencv_ml.KNearest;

import qupath.lib.plugins.parameters.ParameterList;


/**
 * Wrapper for OpenCV's K Nearest Neighbor classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class KNearestClassifier extends ParameterizableOpenCvClassifier<KNearest> {

	@Override
	protected KNearest createClassifier() {
		KNearest classifier = KNearest.create();
//		System.out.println("Default K: " + classifier.getDefaultK());
		ParameterList params = getParameterList();
		if (params != null) {
			classifier.setIsClassifier(true);
			classifier.setDefaultK(params.getIntParameterValue("k"));
		}
		return classifier;
	}

	@Override
	public String getName() {
		return "K Nearest Neighbors";
	}
	
	@Override
	public boolean supportsAutoUpdate() {
		return false;
	}

	@Override
	protected ParameterList createParameterList() {
		return new ParameterList().addIntParameter("k", "K neighbors", 10, null, "Number of nearest neighbors to use");
	}

}
