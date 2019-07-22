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

import java.io.Serializable;
import java.util.Locale;

/**
 * Interface defining algorithm parameters, which need to be displayed to users somehow.
 * 
 * @author Pete Bankhead
 *
 * @param <S>
 */
public interface Parameter<S> extends Serializable {
	
	/**
	 * Get a default value to use if the Parameter has not been otherwise set.
	 * @return
	 */
	public S getDefaultValue();

	/**
	 * Set the Parameter to have a specified value.
	 * @param value
	 * @return
	 */
	public boolean setValue(S value);

	/**
	 * Set last value using a string; implementing classes may need to parse this
	 */
	public boolean setStringLastValue(Locale locale, String value);

	/**
	 * Set last value to null (so default can be used).
	 */
	public void resetValue();

	/**
	 * Get the current set value (may be null).
	 * 
	 * @see #setValue
	 * @see #getValueOrDefault
	 * @return
	 */
	public S getValue();

	/**
	 * Get the current set value, or any default if no value has been set.
	 * 
	 * @see #setValue
	 * @see #getValue
	 * @return
	 */
	public S getValueOrDefault();
	
	/**
	 * Get some prompt text that may be displayed to a user.
	 * @return
	 */
	public String getPrompt();
	
	/**
	 * Query if a specified value would be valid for this parameter.
	 * @param value
	 * @return true if the value would be valid, false otherwise
	 */
	public boolean isValidInput(S value);
	
	/**
	 * Mark that a parameter should not be displayed to a user.
	 * This is useful, for example, if a parameter list changes the parameters to be displayed depending upon
	 * the image available or current settings, e.g. using different parameters when the pixel size is known in microns.
	 * 
	 * @param hidden
	 */
	public void setHidden(boolean hidden);
	
	/**
	 * Test is the 'hidden' flag is set for the parameter.
	 * 
	 * @return
	 */
	public boolean isHidden();
	
	/**
	 * Create a new Parameter with the same text and value.
	 * @return
	 */
	public Parameter<S> duplicate();
	
	/**
	 * Query whether getHelpText() returns a meaningful String (as opposed to null).
	 * @return
	 */
	public boolean hasHelpText();
	
	/**
	 * Get a description of the meaning of the Parameter; may be displayed e.g. as a tooltip.
	 * @return
	 */
	public String getHelpText();
	
}
