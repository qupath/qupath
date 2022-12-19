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

package qupath.lib.gui.scripting.syntax;

import java.util.Set;

import qupath.lib.gui.scripting.EditableText;

/**
 * Class to take care of the Groovy syntax formatting.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
class GroovySyntax extends GeneralCodeSyntax {
	
	private static final String ifStatementPattern = "(else\\s*)?if\\s*\\(.*\\)$";
	private static final String elseStatementPattern = "\\}?\\s*else$";

	// Empty constructor
	GroovySyntax() {}
	
	@Override
	public String getLineCommentString() {
		return "//";
	}

	/**
	 * Handle adding a new line, by checking current line for appropriate indentation.
	 * Note: this method should be called <em>instead</em> of simply accepting the newline character,
	 * i.e. the method itself will add the newline as required.
	 * <p>
	 * Additionally, it handles new lines following a '{' character:
	 * <li> It creates a block of '{' + new line + indentation + new line + '}' </li>
	 * <li> The caret position is set to inside the block </li>
	 * <li> If there originally was some text after '{', the text will be included inside the block </li>
	 * <li> If the amount of '{' and '}' in the text is equal, it will add the new line but won't create a block </li>
	 * <li> The original indentation is accounted for</li>
	 * 
	 * <p>
	 * 
	 * And new line as part of a non-braced 'if(/else)' statement:
	 * <li> It indents the line following an if statement without '{'</li>
	 * <li> It removes the indentation after the line inside a one-line if statement </li>
	 * <li> It indents the line following an else statement without '{' </li>
	 * <li> It removes the indentation after the line inside a one-line else statement </li>
	 * 
	 * <p>
	 * 
	 * As well as new lines which start with '/*':
	 * <li> It creates a comment block of '/' + '*' + new line + space + * + space + new line + '/' + '*' </li>
	 * <li> The caret position is set to inside the block </li>
	 * <li> The original indentation is accounted for </li>
	 * 
	 * <p>
	 * 
	 * And new lines which start with  '*':
	 * <li> The new line will automatically start with '*' + space, as to continue the comment block </li>
	 * <li> The original indentation is accounted for </li>
	 */
	@Override
	public void handleNewLine(final EditableText control, final boolean smartEditing) {
		int caretPos = control.getCaretPosition();
		String text = control.getText();
		int startRowPos = getRowStartPosition(text, caretPos);
		int endRowPos = getRowEndPosition(text, caretPos);
		String subString = text.substring(startRowPos, caretPos);
		String trimmedSubString = subString.trim();
		int indentation = subString.length() - subString.stripLeading().length();
		int ind = trimmedSubString.length() == 0 ? subString.length() : subString.indexOf(trimmedSubString);
		int finalPos = caretPos;
		String insertText = System.lineSeparator();

		if (!smartEditing) {
			super.handleNewLine(control, smartEditing);
			return;
		}
		
		var tabString = getTabString();

		if (trimmedSubString.startsWith("/*") && !trimmedSubString.contains("*/")) {	// Start of a comment block
			insertText = ind == 0 ? "\n" + subString.substring(0, indentation) + " * \n */" : "\n" + subString.substring(0, indentation) + " * \n" + subString.substring(0, indentation) + " */ ";
			control.insertText(caretPos, insertText);
			finalPos += insertText.length() - (indentation == 0 ? -1 : indentation) - 5;
			control.positionCaret(finalPos);
			return;
		} else if (trimmedSubString.startsWith("*") && !trimmedSubString.contains("*/")) {	// Inside a comment block
			insertText = ind == 0 ? "\n* " : "\n" + subString.substring(0, ind) + "* ";
			control.insertText(caretPos, insertText);
			finalPos += insertText.length();
			return;
		} else if (trimmedSubString.endsWith("{")) {		// Start of a '{'/'}' block
			String lineRemainder = text.substring(startRowPos + subString.length(), endRowPos);
			insertText =  "\n" + subString.substring(0, indentation) + tabString + lineRemainder.strip();
			if (text.replaceAll("[^{]", "").length() != text.replaceAll("[^}]", "").length())
				insertText += "\n" + subString.substring(0, indentation) + "}";

			finalPos += 1 + indentation + tabString.length() + lineRemainder.strip().length();

			// If '{' is not preceded by a space, insert one (this is purely aesthetic)
			if (trimmedSubString.length() >= 2 && trimmedSubString.charAt(trimmedSubString.length() - 2) != ' ')
				control.insertText(++caretPos - 2, " ");			
			control.insertText(caretPos, insertText);
			control.deleteText(control.getCaretPosition(), control.getCaretPosition() + lineRemainder.length());
			control.positionCaret(finalPos);
		} else if (trimmedSubString.endsWith("[")) {		// Start of a '['/']' block (e.g. list or map)
			String lineRemainder = text.substring(startRowPos + subString.length(), endRowPos);
			insertText =  "\n" + subString.substring(0, indentation) + tabString + lineRemainder.strip();
			if (text.replaceAll("[^\\[]", "").length() != text.replaceAll("[^\\]]", "").length())
				insertText += "\n" + subString.substring(0, indentation) + "]";

			finalPos += 1 + indentation + tabString.length() + lineRemainder.strip().length();

			control.insertText(caretPos, insertText);
			control.deleteText(control.getCaretPosition(), control.getCaretPosition() + lineRemainder.length());
			control.positionCaret(finalPos);
		} else if (!trimmedSubString.endsWith("{")) {
			if (trimmedSubString.matches(ifStatementPattern) || trimmedSubString.matches(elseStatementPattern)) {	// Start of a one-line if/else statement
				insertText = "\n" + subString.substring(0, ind) + tabString;
				control.insertText(caretPos, insertText);
				finalPos += insertText.length();
			} else {	// Normal new line (which keeps indentation, except if part of a one-line if/else statement)
				if (text.substring(0, startRowPos).contains("\n") && indentation > 0) {
					int startPrevRowPos = getRowStartPosition(text, startRowPos-1);
					int endPrevRowPos = getRowEndPosition(text, startPrevRowPos);
					String prevSubString = text.substring(startPrevRowPos, endPrevRowPos);
					if (prevSubString.matches(ifStatementPattern) || prevSubString.matches(elseStatementPattern)) {	// If prev line is one-line if/else statement
						insertText = "\n" + subString.substring(0, Math.max(0, ind-tabString.length()));
						control.insertText(caretPos, insertText);
						finalPos += insertText.length();
						return;
					}
				}

				// Otherwise treat this new line as normal, and keep the indentation
				String lineRemainder = text.substring(startRowPos + subString.length(), endRowPos);
				insertText =  "\n" + subString.substring(0, indentation) + lineRemainder.strip();
				finalPos += 1 + indentation;
				control.insertText(caretPos, insertText);
				control.deleteText(control.getCaretPosition(), control.getCaretPosition() + lineRemainder.length());
				control.positionCaret(finalPos);
//				control.requestFollowCaret();
			}
		}
	}
	
	
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("groovy", "java");
	}
	
}