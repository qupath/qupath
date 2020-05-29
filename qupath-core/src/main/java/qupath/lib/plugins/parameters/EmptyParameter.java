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

package qupath.lib.plugins.parameters;

import java.util.Locale;

/**
 * Parameter that doesn't actually store any value, but might contain some 
 * useful text (or a title) that may need to be displayed.
 * 
 * @author Pete Bankhead
 *
 */
public class EmptyParameter extends AbstractParameter<String> {
	
	private static final long serialVersionUID = 1L;

	protected boolean isTitle = false;
	
	EmptyParameter(String prompt, boolean isTitle, boolean isHidden) {
		super(prompt, null, null, null, isHidden);
		this.isTitle = isTitle;
	}
	
	/**
	 * An empty parameter, which does not take any input, always returning null.
	 * 
	 * @param prompt text to display
	 * @param isTitle identifies whether the prompt corresponds to a title, 
	 * so that it might be displayed differently (e.g. in bold)
	 */
	EmptyParameter(String prompt, boolean isTitle) {
		this(prompt, isTitle, false);
	}
	
	EmptyParameter(String prompt) {
		this(prompt, false);
	}
	
	/**
	 * Returns true if the parameter should be considered a title. It may therefore be displayed differently.
	 * @return
	 */
	public boolean isTitle() {
		return isTitle;
	}

	/**
	 * Always returns false
	 */
	@Override
	public boolean isValidInput(String value) {
		return false;
	}

	@Override
	public boolean setStringLastValue(Locale locale, String value) {
		return false;
	}
	
	@Override
	public String toString() {
		return getPrompt();
	}

	@Override
	public Parameter<String> duplicate() {
		return new EmptyParameter(getPrompt(), isTitle);
	}

}
