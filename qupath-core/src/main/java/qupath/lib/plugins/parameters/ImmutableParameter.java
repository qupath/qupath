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
 * An immutable parameter - attempts to set its value will produce UnsupportedOperationExceptions.
 * 
 * @author Pete Bankhead
 *
 * @param <S>
 */
class ImmutableParameter<S> implements Parameter<S> {
	
	private static final long serialVersionUID = 1L;
	
	private Parameter<S> parameter;
	
	ImmutableParameter(Parameter<S> parameter) {
		this.parameter = parameter;
	}

	@Override
	public S getDefaultValue() {
		return parameter.getDefaultValue();
	}

	@Override
	public boolean setValue(S value) {
		throw new UnsupportedOperationException(this + " is immutable - value cannot be set");
	}

	@Override
	public boolean setStringLastValue(Locale locale, String value) {
		throw new UnsupportedOperationException(this + " is immutable - value cannot be set");
	}

	@Override
	public void resetValue() {
		throw new UnsupportedOperationException(this + " is immutable - value cannot be reset");
	}

	@Override
	public S getValue() {
		return parameter.getValue();
	}

	@Override
	public S getValueOrDefault() {
		return parameter.getValueOrDefault();
	}

	@Override
	public String getPrompt() {
		return parameter.getPrompt();
	}

	@Override
	public boolean isValidInput(S value) {
		return parameter.isValidInput(value);
	}

	@Override
	public Parameter<S> duplicate() {
		// Since the source parameter remains mutable, create a duplicate of this
		return new ImmutableParameter<>(parameter.duplicate());
	}
	
	@Override
	public String toString() {
		return parameter.toString();
	}

	@Override
	public void setHidden(boolean hidden) {
		throw new UnsupportedOperationException(this + " is immutable - value cannot be set");
	}

	@Override
	public boolean isHidden() {
		return parameter.isHidden();
	}

	@Override
	public boolean hasHelpText() {
		return parameter.hasHelpText();
	}

	@Override
	public String getHelpText() {
		return parameter.getHelpText();
	}

}
