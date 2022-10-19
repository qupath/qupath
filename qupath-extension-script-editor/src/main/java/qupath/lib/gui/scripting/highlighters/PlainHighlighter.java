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

import java.util.Collection;
import java.util.ServiceLoader;

import org.fxmisc.richtext.model.StyleSpans;

/**
 * Highlighting for plain text (which means no highlighting).
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class PlainHighlighter implements ScriptHighlighter {
	
	/**
	 * Instance of this language. Can't be final because of {@link ServiceLoader}.
	 */
	private static PlainHighlighter INSTANCE;

	/**
	 * Get the static instance of this class.
	 * @return instance
	 */
	public static ScriptHighlighter getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Constructor for a simple Plain Highlighter (which does nothing). 
	 * This constructor should never be called. Instead, use the 
	 * static {@link #getInstance()} method.
	 * <p>
	 * Note: this has to be public for the {@link ServiceLoader} to work.
	 */
	public PlainHighlighter() {
		if (INSTANCE != null)
			throw new UnsupportedOperationException("Highlighter classes cannot be instantiated more than once!");
		
		// Because of ServiceLoader, have to assign INSTANCE here.
		PlainHighlighter.INSTANCE = this;
	}
	
	@Override
	public String getLanguageName() {
		return "None";
	}
	
	@Override
	public StyleSpans<Collection<String>> computeEditorHighlighting(String text) {
		return ScriptHighlighterProvider.getPlainStyling(text);
	}
	
}
