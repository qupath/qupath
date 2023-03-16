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

package qupath.lib.gui.scripting.languages;

import java.util.ServiceLoader;
import java.util.Set;

import qupath.lib.scripting.languages.ScriptAutoCompletor;
import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Class for the representation of properties and config files in QuPath.
 * <p>
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class PropertiesLanguage extends ScriptLanguage {
	
	/**
	 * Instance of this language. Can't be final because of {@link ServiceLoader}.
	 */
	private static PropertiesLanguage INSTANCE;

	/**
	 * Constructor for a Java properties as a script editor language. This constructor should never be 
	 * called. Instead, use the static {@link #getInstance()} method.
	 * <p>
	 * Note: this has to be public for the {@link ServiceLoader} to work.
	 */
	public PropertiesLanguage() {
		super("Properties", Set.of(".properties", ".cfg"));
		
		if (INSTANCE != null)
			throw new UnsupportedOperationException("Language classes cannot be instantiated more than once!");
		
		// Because of ServiceLoader, have to assign INSTANCE here.
		PropertiesLanguage.INSTANCE = this;
	}
	
	/**
	 * Get the static instance of this class.
	 * @return instance
	 */
	public static PropertiesLanguage getInstance() {
		return INSTANCE;
	}
	
	@Override
	public ScriptAutoCompletor getAutoCompletor() {
		return null;
	}
}
