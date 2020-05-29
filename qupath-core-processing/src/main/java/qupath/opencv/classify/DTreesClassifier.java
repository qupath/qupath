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

import org.bytedeco.opencv.opencv_ml.DTrees;

/**
 * Wrapper for OpenCV's Decision Trees classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class DTreesClassifier extends OpenCvClassifier<DTrees> {

	@Override
	protected DTrees createClassifier() {
		DTrees trees = DTrees.create();
		// Bug at time of writing appears to require these to be set, 
		// see http://code.opencv.org/issues/4480
		trees.setCVFolds(0);
		trees.setMaxDepth(1000);
		return trees;
	}
	
	@Override
	public String getName() {
		return "Decision Trees";
	}
	
	@Override
	public boolean supportsAutoUpdate() {
		return true;
	}

}
