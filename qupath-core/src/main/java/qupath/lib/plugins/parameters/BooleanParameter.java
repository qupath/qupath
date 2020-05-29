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
 * Parameter that can take on true of false value - or null.
 * <p>
 * May be displayed as a checkbox.
 * 
 * @author Pete Bankhead
 *
 */
public class BooleanParameter extends AbstractParameter<Boolean> {

	private static final long serialVersionUID = 1L;

	BooleanParameter(String prompt, Boolean defaultValue, Boolean lastValue, String helpText, boolean isHidden) {
		super(prompt, defaultValue, lastValue, helpText, isHidden);
	}
	
	BooleanParameter(String prompt, Boolean defaultValue, Boolean lastValue, String helpText) {
		this(prompt, defaultValue, lastValue, helpText, false);
	}

	BooleanParameter(String prompt, Boolean defaultValue, String helpText) {
		this(prompt, defaultValue, null, helpText);
	}

	@Override
	public boolean setStringLastValue(Locale locale, String value) {
		try {
			boolean b = Boolean.parseBoolean(value);
			return setValue(b);
		} catch (Exception e) {}
		return false;
	}

	@Override
	public boolean isValidInput(Boolean value) {
		return value != null;
	}

	@Override
	public Parameter<Boolean> duplicate() {
		return new BooleanParameter(getPrompt(), getDefaultValue(), getValue(), getHelpText(), isHidden());
	}


}
