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

import java.util.Set;

import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Class for the representation of plain text in QuPath.
 * <p>
 * This class stores the QuPath implementation of Plain syntaxing and plain auto-completion (both do nothing, as it's plain text).
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class PlainLanguage extends ScriptLanguage {

	private static final PlainLanguage INSTANCE = new PlainLanguage();

	private PlainLanguage() {
		super("None", Set.of(".txt", ".tsv", ".csv"));
	}
	
	/**
	 * Get the static instance of this class.
	 * @return instance
	 */
	public static PlainLanguage getInstance() {
		return INSTANCE;
	}

}
