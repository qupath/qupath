/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting.syntax;

import java.util.Set;

import qupath.lib.gui.scripting.EditableText;

/**
 * Interface for classes that apply some syntax formatting to an {@link EditableText}.
 * @author Melvin Gelbard
 * @author Pete Bankhead
 * @since v0.4.0
 */
public interface ScriptSyntax {
	
	/**
	 * Get a set of the scripting languages supported by this syntax.
	 * @return
	 */
	public Set<String> getLanguageNames();
	
	/**
	 * String to insert when tab key pressed
	 * @return 
	 */
	default String getTabString() {
		return "    ";
	}
	
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
	default void handleLeftParenthesis(EditableText control, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), "(");
	}
	
	/**
	 * Handle right parentheses {@code )}.
	 * @param control the text/code area
	 * @param smartEditing whether smart editing is enabled
	 */
	default void handleRightParenthesis(EditableText control, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), ")");
	}
	
	/**
	 * Handle single/double quotes.
	 * @param control the text/code area
	 * @param isDoubleQuote whether the input is single/double quotes
	 * @param smartEditing whether smart editing is enabled
	 */
	default void handleQuotes(EditableText control, boolean isDoubleQuote, final boolean smartEditing) {
		String quote = isDoubleQuote ? "\"" : "'";
		control.replaceSelection(quote);
	}
	
	/**
	 * Handle line comments.
	 * @param control the text/code area
	 */
	default void handleLineComment(EditableText control) {
		// Do nothing
	}
	
	/**
	 * Handle a new line.
	 * @param control the text/code area
	 * @param smartEditing whether smart editing is enabled
	 */
	default void handleNewLine(EditableText control, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), System.lineSeparator());
	}
	
	/**
	 * Handle a backspace.
	 * @param control 
	 * @param smartEditing whether smart editing is enabled
	 * @return whether the source event should be consumed; if this returns false then backspace is handled elsewhere
	 */
	public default boolean handleBackspace(EditableText control, boolean smartEditing) {
		return false;
	}
	
	/**
	 * Handle tab key.
	 * @param control the text/code area
	 * @param shiftDown
	 */
	public default void handleTabPress(EditableText control, boolean shiftDown) {
		control.insertText(control.getCaretPosition(), getTabString());
	}
	
	/**
	 * Beautifies the specified text, according to the syntax.
	 * @param text the text to beautify
	 * @return beautified text
	 */
	public default String beautify(String text) {
		return text;
	}
	
	/**
	 * Returns {@code true} if {@link #beautify(String)} is capable of beautifying the text, {@code false} otherwise.
	 * @return
	 */
	public default boolean canBeautify() {
		return false;
	}
	
	/**
	 * Compresses the specified text by removing extra space, according to the syntax.
	 * @param text the text to compress
	 * @return beautified text
	 */
	public default String compress(String text) {
		return text;
	}
	
	/**
	 * Returns {@code true} if {@link #compress(String)} is capable of compressing the text, {@code false} otherwise.
	 * @return
	 */
	public default boolean canCompress() {
		return false;
	}
	
}
