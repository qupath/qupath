/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting.highlighters;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Class with static methods to fetch all the available {@link ScriptHighlighter}s.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class ScriptHighlighterProvider {
	
	private static ServiceLoader<ScriptHighlighter> serviceLoader = ServiceLoader.load(ScriptHighlighter.class);
	
	/**
	 * Get all the currently installed {@link ScriptHighlighter}s in a list.
	 * @return list of installed highlighters
	 */
	public static List<ScriptHighlighter> getInstalledScriptHighlighters() {
		List<ScriptHighlighter> highlighters = new ArrayList<>();
		synchronized (serviceLoader) {
			for (ScriptHighlighter h : serviceLoader) {
				highlighters.add(h);
			}
		}
		return highlighters;
	}

	/**
	 * Get the {@link ScriptHighlighter} object corresponding to the specified {@link ScriptLanguage}. 
	 * If the language cannot be matched, {@link PlainHighlighter} is returned.
	 * @param language
	 * @return corresponding highlighter, or {@link PlainHighlighter} if no match.
	 */
	public static ScriptHighlighter getHighlighterFromLanguage(ScriptLanguage language) {
		synchronized (serviceLoader) {
			for (ScriptHighlighter h : serviceLoader) {
				if (language.getName().equals(h.getLanguageName()))
					return h;				
			}
		}
		return PlainHighlighter.getInstance();
	}
}
