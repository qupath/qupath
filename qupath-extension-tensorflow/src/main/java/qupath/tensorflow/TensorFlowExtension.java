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

package qupath.tensorflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.opencv.ops.ImageOps;

/**
 * Experimental extension to connect QuPath and TensorFlow.
 * 
 * @author Pete Bankhead
 */
public class TensorFlowExtension implements QuPathExtension {
	
	private final static Logger logger = LoggerFactory.getLogger(TensorFlowExtension.class);

	@Override
	public void installExtension(QuPathGUI qupath) {
		logger.debug("Installing TensorFlow extension");
		ImageOps.registerOp(TensorFlowOp.class, "op.ml.ext.tensorflow");
	}

	@Override
	public String getName() {
		return "TensorFlow extension";
	}

	@Override
	public String getDescription() {
		return "Add TensorFlow support to QuPath";
	}
	
	
	
}