/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.prefs.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a general preference.
 * @since v0.5.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Pref {
	/**
	 * Optional bundle for externalized string
	 * @return
	 */
	String bundle() default "";
	/**
	 * Type for the property
	 * @return
	 */
	Class<?> type();
	/**
	 * Key for externalized string that gives the text of the preference
	 * @return
	 */
	String value();
	/**
	 * Name of a method that can be invoked to get a list of available choices.
	 * This is not needed for Enum types, where the choices are already known.
	 * <p>
	 * The method is expected to be defined in the parent object containing the 
	 * annotated property field. It should take no parameters.
	 * @return
	 */
	String choiceMethod() default "";
	
}