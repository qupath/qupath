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

import qupath.lib.gui.scripting.EditableText;

/**
 * Abstract class to represent the typical syntaxes found in most programming languages.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
abstract class GeneralCodeSyntax implements ScriptSyntax {

	/**
	 * Handle left parentheses '{@code (}' by automatically adding a right parenthesis '{@code )}' after if not already present.
	 */
	@Override
	public void handleLeftParenthesis(final EditableText control, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), "(");
		if (!smartEditing)
			return;
		String text = control.getText();
		var pos = control.getCaretPosition();
		// If the next character is a letter or digit, we don't want to close the parentheses
		if (pos < text.length()) {
			char nextChar = text.charAt(pos);
			if (Character.isLetterOrDigit(nextChar))
				return;
		}
		control.insertText(pos, ")");
		control.positionCaret(control.getCaretPosition()-1);
	}
	
	/**
	 * Adds a right parenthesis '{@code )}', except if one is already present at the caret position.
	 */
	@Override
	public void handleRightParenthesis(final EditableText control, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), ")");
		if (!smartEditing)
			return;
		
		String text = control.getText();
		var caretPos = control.getCaretPosition();
		if (text.length() >= caretPos + 1 && text.charAt(caretPos) == ')') {
			control.deleteText(caretPos, caretPos + 1);
		}
	}
	
	/**
	 * Handle (single/double) quotes by adding a closing one if not already present.
	 */
	@Override
	public void handleQuotes(final EditableText control, boolean isDoubleQuote, final boolean smartEditing) {
		String quotes = isDoubleQuote ? "\"" : "\'";
		if (control.getSelectionLength() == 0)
			control.insertText(control.getCaretPosition(), quotes);
		else
			control.replaceSelection(quotes);
		if (!smartEditing)
			return;
		
		String text = control.getText();
		var caretPos = control.getCaretPosition();
		
		// Check for triple quotes
		if (caretPos >= 3 && text.substring(caretPos-3, caretPos).equals("\"\"\"")) {
			// Triple quotes are awkward... the following works well for opening them, but not for closing them
			// For now, we don't try to handle them in a smarter way
//			int followingQuotes = 0;
//			for (int i = caretPos; i < text.length(); i++) {
//				if (text.charAt(i) == '"')
//					followingQuotes++;
//				else
//					break;
//			}
//			String toInsert = null;
//			switch (followingQuotes) {
//			case 0:
//				toInsert = "\"\"\"";
//				break;
//			case 1:
//				toInsert = "\"\"";
//				break;
//			case 2:
//				toInsert = "\"";
//				break;
//			case 3:
//			default:
//				break;
//			}
//			if (toInsert != null) {
//				control.insertText(control.getCaretPosition(), toInsert);
//				control.positionCaret(control.getCaretPosition()-toInsert.length());
//			}
		} else {
			// Handle closing if we don't have triple quotes
			if (text.length() >= caretPos + 1 && text.charAt(caretPos) == quotes.charAt(0))
				control.deleteText(caretPos, caretPos + 1);
			else {
				// Insert close quote if the caret is both preceded and followed 
				// by the start/end of the text, or by whitespace
				boolean checkStart = caretPos <= 1 || Character.isWhitespace(text.charAt(caretPos-2));
				boolean checkEnd = caretPos >= text.length() || Character.isWhitespace(text.charAt(caretPos));
				if (checkStart && checkEnd) {
					control.insertText(control.getCaretPosition(), quotes);
					control.positionCaret(control.getCaretPosition()-1);
				}
			}
		}
	}
	
	/**
	 * Handle the press of the / key, with/without shift.
	 * This either inserts comments or uncomments the selected lines, if possible.
	 */
	@Override
	public void handleLineComment(final EditableText control) {
		String commentString = getLineCommentString();
		if (commentString == null)
			return;

		String text = control.getText();

		boolean hasSelection = control.getSelectionLength() > 0;
		int startRowPos = getRowStartPosition(text, control.getSelectionStart());
		int endRowPos = getRowEndPosition(text, control.getSelectionEnd());
		String textBetween = text.substring(startRowPos, endRowPos);
		// Check if every new row starts with a comment string - if so we want to remove these, if not we want to add comments
		
		int nNewLines = textBetween.length() - textBetween.replace("\n", "").length();
		int nCommentLines = (textBetween.length() - textBetween.replace("\n" + commentString, commentString).length());
		boolean allComments = textBetween.startsWith(commentString) && nNewLines == nCommentLines;
		
		String replaceText;
		if (allComments) {
			// Remove tabs at start of selected rows
			replaceText = textBetween.replace("\n"+commentString, "\n");
			if (replaceText.startsWith(commentString))
				replaceText = replaceText.substring(commentString.length());
		} else {
			replaceText = commentString + textBetween.replace("\n", "\n"+commentString);
		}
		
		control.selectRange(startRowPos, endRowPos);
		control.replaceSelection(replaceText);
		if (hasSelection)
			control.selectRange(startRowPos, startRowPos + replaceText.length());
	}
	
	@Override
	public void handleNewLine(EditableText control, final boolean smartEditing) {
		String text = control.getText();
		int caretPos = control.getCaretPosition();
		int startRowPos = getRowStartPosition(text, caretPos);
		String subString = text.substring(startRowPos, caretPos);
		String indentation = subString.substring(0, subString.length() - subString.stripLeading().length());
		ScriptSyntax.super.handleNewLine(control, smartEditing);
		if (smartEditing)
			control.insertText(control.getCaretPosition(), indentation);
	}

	/**
	 * Handle backspace if required, otherwise does nothing (and let the original control deal with the backspace).
	 * <p>
	 * This was implemented this way because there's no point in rewriting all the rules for backspace 
	 * (e.g. {@code SHORTCUT} + {@code BACKSPACE}, {@code BACKSPACE} on a selection, etc..).
	 */
	@Override
	public boolean handleBackspace(final EditableText control, final boolean smartEditing) {
		var caretPos = control.getCaretPosition();
		var selectionLength = control.getSelectionLength();
		if (caretPos -1 < 0 || selectionLength >= 1 || !smartEditing)
			return false;
		
		if (caretPos >= control.getText().length() ||
				(!(control.getText().charAt(caretPos-1) == '(' && control.getText().charAt(caretPos) == ')') &&
				!(control.getText().charAt(caretPos-1) == '"' && control.getText().charAt(caretPos) == '"') &&
				!(control.getText().charAt(caretPos-1) == '\'' && control.getText().charAt(caretPos) == '\'')))
			return false;
		
		control.deleteText(caretPos-1, caretPos+1);
		return true;
	}
	
	/**
	 * Handle the press of the tab key, with/without shift.
	 * <p>
	 * If there is no selection :
	 * <li> It inserts a {@code tabString} at the current caret position if shift is not down </li>
	 * <li> It removes a {@code tabString} at the start of the line if there is one and if shift is down </li>
	 * <p>
	 * If there is a selection:
	 * <li> It indents all the selected rows if shift is not down </li>
	 * <li> It removes one indentation from all the selected rows if shift is down </li>
	 */
	@Override
	public void handleTabPress(final EditableText textArea, final boolean shiftDown) {
		String text = textArea.getText();
		
		int rangeStart = textArea.getSelectionStart();
		int rangeEnd = textArea.getSelectionEnd();
		int rangeLength = textArea.getSelectionLength();
		
		int startRowPos = getRowStartPosition(text, rangeStart);
		int endRowPos = getRowEndPosition(text, rangeEnd);
		String textBetween = text.substring(startRowPos, endRowPos);

		var tabString = getTabString();
		
		if (rangeLength == 0) {
			int caretPos = textArea.getCaretPosition();
			if (shiftDown) {
				if (textBetween.startsWith(tabString)) {
					textArea.deleteText(startRowPos, startRowPos + tabString.length());
					textArea.positionCaret(caretPos - tabString.length());
				} else if (textBetween.startsWith("\t")) {
					// Handle 'real' tabs
					textArea.deleteText(startRowPos, startRowPos + "\t".length());
					textArea.positionCaret(caretPos - "\t".length());
				}
			} else
				textArea.insertText(caretPos, tabString);
			return;
		}

		String replaceText;
		if (shiftDown) {
			// Handle 'real' tabs
			textBetween = textBetween.replaceAll("\t", tabString);
			// Remove tabs at start of selected rows
			replaceText = textBetween.replace("\n"+tabString, "\n");
			if (replaceText.startsWith(tabString))
				replaceText = replaceText.substring(tabString.length());
		} else
			replaceText = tabString + textBetween.replace("\n", "\n"+tabString);
		
		textArea.selectRange(startRowPos, endRowPos);
		textArea.replaceSelection(replaceText);
		textArea.selectRange(startRowPos, startRowPos + replaceText.length());
	}
	
	/**
	 * Get the starting position for the row when the caret is at pos.
	 * @param text
	 * @param pos
	 * @return
	 */
	protected int getRowStartPosition(final String text, final int pos) {
		return text.substring(0, pos).lastIndexOf("\n") + 1;
	}

	/**
	 * Get the ending position for the row when the caret is at pos.
	 * @param text
	 * @param pos
	 * @return
	 */
	protected int getRowEndPosition(final String text, final int pos) {
		int pos2 = text.substring(pos).indexOf("\n");
		if (pos2 < 0)
			return text.length();
		return pos + pos2;
	}
}
