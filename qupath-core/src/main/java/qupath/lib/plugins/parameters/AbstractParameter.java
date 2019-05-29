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

package qupath.lib.plugins.parameters;

/**
 * Abstract Parameter implementation.
 * 
 * @author Pete Bankhead
 *
 * @param <S>
 */
abstract class AbstractParameter<S> implements Parameter<S> {

	private static final long serialVersionUID = 1L;
	
	private String prompt = null;
	private S defaultValue;	
	
	private String helpText = null;
	private boolean hidden = false;
	
	protected S lastValue = null;
	
	AbstractParameter(String prompt, S defaultValue, S value, String helpText, boolean hidden) {
		this.prompt = prompt;
		this.defaultValue = defaultValue;
		this.lastValue = value;
		this.helpText = helpText;
		this.hidden = hidden;
	}
	
	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
	
	@Override
	public boolean isHidden() {
		return hidden;
	}
	
	@Override
	public S getDefaultValue() {
		return defaultValue;
	}
	
	@Override
	public S getValue() {
		return lastValue;
	}
	
	@Override
	public void resetValue() {
		lastValue = null;
	}

	@Override
	public S getValueOrDefault() {
		if (lastValue != null)
			return lastValue;
		return defaultValue;
	}
	
	@Override
	public String getPrompt() {
		return prompt;
	}
	
	@Override
	public boolean setValue(S value) {
		if (!isValidInput(value))
			return false;
		this.lastValue = value;
		return true;
	}
	
	@Override
	public String toString() {
		// Ensure the prompt doesn't have any colons in it
		return getPrompt().replace(":", "-") + ":\t" + getValueOrDefault();
	}
	
	@Override
	public boolean hasHelpText() {
		return getHelpText() != null;
	}
	
	@Override
	public String getHelpText() {
		return helpText;
	}
	
}
