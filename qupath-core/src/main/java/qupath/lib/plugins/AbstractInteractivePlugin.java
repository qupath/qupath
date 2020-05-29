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

package qupath.lib.plugins;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Abstract class to help with implementing an interactive plugin.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class AbstractInteractivePlugin<T> extends AbstractPlugin<T> implements PathInteractivePlugin<T> {
	
	transient protected ParameterList params;
	
	private final static Logger logger = LoggerFactory.getLogger(AbstractInteractivePlugin.class);
	
	/**
	 * This should return a default ParameterList containing any information that is needed to repeat the task exactly.
	 * 
	 * @return
	 */
	@Override
	public abstract ParameterList getDefaultParameterList(final ImageData<T> imageData);
	
	@Override
	public boolean alwaysPromptForObjects() {
		return false;
	}

	/**
	 * Get a reference to a ParameterList stored internally, and which will be used for analysis.
	 * <p>
	 * If there is no list presently available, getDefaultParameterList will be called.
	 * <p>
	 * If there is a list available, it will be returned.
	 * <p>
	 * The reason for needing this in addition to getDefaultParameterList, is that parseArgument could 
	 * modify the internal ParameterList that will actually be used, while getDefaultParameterList is useful
	 * when creating GUIs and ensuring that there is always a sensible starting point.
	 * 
	 * @param imageData image data for which the parameters should be generated. This may influence which parameters are shown.
	 * @return
	 */
	protected ParameterList getParameterList(final ImageData<T> imageData) {
		if (params == null)
			params = getDefaultParameterList(imageData);
		return params;
	}
	
	
	// TODO: Consider whether this should be shown or not
	@Override
	protected boolean parseArgument(ImageData<T> imageData, String arg) {
		if (arg != null) {
			logger.trace("Updating parameters with arg: {}", arg);
			// Parse JSON-style arguments
			Map<String, String> map = GeneralTools.parseArgStringValues(arg);
			params = getParameterList(imageData);
			// Use US locale for standardization, and use of decimal points (not commas)
			ParameterList.updateParameterList(params, map, Locale.US);
		}
		return imageData != null;
	}
	
	
	/**
	 * Get a copy of the current parameter list (with empty parameters removed) suitable for logging.
	 * Subclasses might choose to append extra parameters here, which aren't part of the main list
	 * (e.g. because they shouldn't be included in any automatically created dialog box)
	 * @param imageData
	 * @return
	 */
	protected ParameterList getLoggableParameters(final ImageData<T> imageData) {
		ParameterList params = getParameterList(imageData).duplicate();
		params.removeEmptyParameters();
		return params;
	}
	
	
}
