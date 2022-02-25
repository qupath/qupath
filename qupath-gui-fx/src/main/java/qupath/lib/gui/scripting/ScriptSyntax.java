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

package qupath.lib.gui.scripting;

import javafx.scene.input.KeyCode;

/**
 * Interface for classes that apply some syntax formatting to a {@link ScriptEditorControl}.
 * @author Melvin Gelbard
 */
public interface ScriptSyntax {
	
	/**
	 * String to insert when tab key pressed
	 */
	final String tabString = "    ";
	
	/**
	 * Get the String that represents the start of a comment line.
	 * @return comment string
	 */
	default String getLineCommentString() {
		return "";
	}
	
	/**
	 * Handle left parentheses {@code (}.
	 * @param control the text/code area
	 * @param smartEditing whether smart editing is enabled
	 */
	default void handleLeftParenthesis(ScriptEditorControl control, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), "(");
	}
	
	/**
	 * Handle right parentheses {@code )}.
	 * @param control the text/code area
	 * @param smartEditing whether smart editing is enabled
	 */
	default void handleRightParenthesis(ScriptEditorControl control, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), ")");
	}
	
	/**
	 * Handle single/double quotes.
	 * @param control the text/code area
	 * @param isDoubleQuote whether the input is single/double quotes
	 * @param smartEditing whether smart editing is enabled
	 */
	default void handleQuotes(ScriptEditorControl control, boolean isDoubleQuote, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), "\"");
	}
	
	/**
	 * Handle line comments.
	 * @param control the text/code area
	 */
	default void handleLineComment(ScriptEditorControl control) {
		// Do nothing
	}
	
	/**
	 * Handle a new line.
	 * @param control the text/code area
	 * @param smartEditing whether smart editing is enabled
	 */
	default void handleNewLine(ScriptEditorControl control, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), System.lineSeparator());
	}
	
	/**
	 * Handle a backspace.
	 * @param control 
	 * @param smartEditing whether smart editing is enabled
	 * @return whether the source event should be consumed
	 */
	public default boolean handleBackspace(ScriptEditorControl control, boolean smartEditing) {
		return false;
	}
	
	/**
	 * Handle TAB {@link KeyCode}.
	 * @param control the text/code area
	 * @param shiftDown
	 */
	public default void handleTabPress(ScriptEditorControl control, boolean shiftDown) {
		control.insertText(control.getCaretPosition(), tabString);
	}
	
	/**
	 * Beautifies the specified text, according to the syntax.
	 * @param text the text to beautify
	 * @return beautified text
	 */
	public default String beautify(String text) {
		return text;
	}
}
