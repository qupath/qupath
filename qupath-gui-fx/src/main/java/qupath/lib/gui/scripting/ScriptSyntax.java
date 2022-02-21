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
	public default boolean handleBackspace(ScriptEditorControl control, final boolean smartEditing) {
		return false;
	}
	
	/**
	 * Handle TAB {@link KeyCode}.
	 * @param control the text/code area
	 * @param shiftDown
	 */
	public default void handleTabPress(final ScriptEditorControl control, final boolean shiftDown) {
		control.insertText(control.getCaretPosition(), tabString);
	}
}
