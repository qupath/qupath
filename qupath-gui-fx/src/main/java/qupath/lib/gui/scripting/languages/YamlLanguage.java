/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022, 2024 QuPath developers, The University of Edinburgh
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

import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Class for the representation of YAML syntax in QuPath.
 * 
 * @author Pete Bankhead (based on Melvin Gelbard's classes)
 * @since v0.4.0
 */
public class YamlLanguage extends ScriptLanguage {

	private static final YamlLanguage INSTANCE = new YamlLanguage();
	
	private YamlLanguage() {
		super("YAML", ".yaml");
	}

	/**
	 * Get the static instance of this class.
	 * @return instance
	 */
	public static YamlLanguage getInstance() {
		return INSTANCE;
	}

}
