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

package qupath.lib.gui.scripting.richtextfx.stylers;

import java.util.Collection;
import java.util.Set;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Interface for classes that apply some styling to a RichTextFX's {@link CodeArea}.
 * @author Melvin Gelbard
 * @author Pete Bankhead
 * @since v0.4.0
 */
public interface ScriptStyler {
	
	
	/**
	 * Get a set of the scripting languages supported by this styler.
	 * <p>
	 * Note: One of the names returned by this method must match <b>exactly</b> (but case-insensitive) 
	 * that of the corresponding {@link ScriptLanguage} for QuPath to automatically apply it to the script editor 
	 * when needed.
	 * @return
	 */
	Set<String> getLanguageNames();

	/**
	 * Compute styling for the specified {@code text}, considering it will be used in the main editor.
	 * @param text 			the text to process styling for
	 * @return 				stylespans 	the {@link StyleSpans} to apply
	 */
	StyleSpans<Collection<String>> computeEditorStyles(final String text);
	
	
	/**
	 * Compute styling for the specified {@code text}, considering it will be used in the console.
	 * @param 				text the text to process styling for
	 * @param logConsole if true, the console prints to the log rather than directly
	 * @return 				stylespans 	the {@link StyleSpans} to apply
	 */
	default StyleSpans<Collection<String>> computeConsoleStyles(final String text, boolean logConsole) {
		if (logConsole)
			return ScriptStylerProvider.getLogStyling(text);
		return ScriptStylerProvider.getPlainStyling(text);
	}
	

	/**
	 * Optionally return a base style for the code area.
	 * The default is to return null, but one use is to return "-fx-font-family: sans-serif" if the language should 
	 * not be formatted as code.
	 * @return
	 */
	default String getBaseStyle() {
		return null;
	}
	
	
}
