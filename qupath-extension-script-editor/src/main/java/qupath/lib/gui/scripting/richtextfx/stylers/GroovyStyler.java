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

package qupath.lib.gui.scripting.richtextfx.stylers;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Doubles;

/**
 * Styling to apply to a {@link CodeArea}, based on Groovy syntax.
 * @author Pete Bankhead
 * @since v0.4.1
 * 
 * @implNote This was rewritten for v0.4.1 to avoid relying on regex, 
 *           which could sometimes result in a {@link StackOverflowError}.
 */
public class GroovyStyler implements ScriptStyler {
	
	private static final Logger logger = LoggerFactory.getLogger(GroovyStyler.class);
	
	private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "def", "in", "with", "trait", "true", "false", "var"
    ));
	
	/**
	 * Constructor.
	 */
	GroovyStyler() {}
	
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("groovy", "java");
	}
	
	
	@Override
	public StyleSpans<Collection<String>> computeEditorStyles(final String text) {
		
		long startTime = System.currentTimeMillis();
		
		var visitor = new StyleSpanVisitor(text);
		
		var chars = text.toCharArray();
		int ind = 0;
		int n = chars.length;
		var buffer = new StringBuffer();
		while (ind < n) {
			// Get current and previous character
			char c = chars[ind];
			char lastChar = ind > 0 ? chars[ind-1] : '\n';
			
			switch (c) {
			case '(':
			case ')':
				handleToken(visitor, ind, buffer);
				handleSingleCharacterStyle(visitor, ind, "paren");
				break;
			case '[':
			case ']':
				handleToken(visitor, ind, buffer);
				handleSingleCharacterStyle(visitor, ind, "bracket");
				break;
			case '{':
			case '}':
				handleToken(visitor, ind, buffer);
				handleSingleCharacterStyle(visitor, ind, "brace");
				break;
			case ';':
				handleToken(visitor, ind, buffer);
				handleSingleCharacterStyle(visitor, ind, "semicolon");
				break;
			case '\\':
				if (lastChar == '\\') {
					// Handle line comment
					handleToken(visitor, ind-1, buffer);
					visitor.appendStyle(ind-1);
					visitor.push("comment");
					while (ind < n && chars[ind] != '\n')
						ind++;
				}
				break;
			case ',':
				handleToken(visitor, ind, buffer);
				break;
			case '=':
				handleToken(visitor, ind, buffer);
				break;
			case '*':
				if (lastChar == '/') {
					// Handle block comment
					handleToken(visitor, ind-1, buffer);
					visitor.appendStyle(ind-1);
					visitor.push("comment");
					ind++;
					while (ind < n && !(chars[ind] == '/' && chars[ind-1] == '*')) {
						ind++;
					}
					visitor.appendStyle(ind);
					visitor.pop();
				}
				break;
			case '\'':
				// Handle single or triple single quotes
				resetToken(buffer);
				visitor.appendStyle(ind);
				visitor.push("string");
				if (ind < n - 2 && chars[ind+1] == '\'' && chars[ind+2] == '\'') {
					// Handle triple single quotes
					ind += 5;
					while (ind < n && !(chars[ind] == '\'' && chars[ind-1] == '\'' && chars[ind-2] == '\''))
						ind++;
				} else {
					// Handle single quotes
					ind++;
					while (ind < n && chars[ind] != '\'' && chars[ind] != '\n')
						ind++;
				}
				visitor.appendStyle(ind+1);
				visitor.pop();	
				break;
			case '"':
				// Handle double or triple double quotes
				resetToken(buffer);
				visitor.appendStyle(ind);
				visitor.push("string");
				if (ind < n - 2 && chars[ind+1] == '"' && chars[ind+2] == '"') {
					// Handle triple quotes
					ind += 5;
					while (ind < n && !(chars[ind] == '"' && chars[ind-1] == '"' && chars[ind-2] == '"'))
						ind++;
				} else {
					// Handle double quotes
					ind++;
					while (ind < n && chars[ind] != '"' && chars[ind] != '\n')
						ind++;
				}
				visitor.appendStyle(ind+1);
				visitor.pop();					
				break;
			default:
				// Check for token
				boolean isLastCharacter = ind == n-1;
//				boolean isWhitespace = Character.isWhitespace(c);
				boolean isBreakingCharacter = !Character.isLetterOrDigit(c) && 
						c != '.' &&
						c != '_';
				if (!isBreakingCharacter) {
					buffer.append(c);
				}
				if (isBreakingCharacter)
					handleToken(visitor, ind, buffer);
				else if (isLastCharacter)
					handleToken(visitor, n, buffer);
				break;
			}
			// Increment counter
			ind++;
		}
		
		var styles = visitor.buildStyles();
		long endTime = System.currentTimeMillis();
		logger.info("Style time: {} (length={})", endTime - startTime, n);
		
		return styles;
    }
	
	
	private static void resetToken(StringBuffer buffer) {
		buffer.setLength(0);
	}
	
	
	private static void handleToken(StyleSpanVisitor visitor, int ind, StringBuffer buffer) {
		if (!buffer.isEmpty()) {
			var s = buffer.toString();
			int startInd = ind - s.length();
			if (KEYWORDS.contains(s)) {
				if (startInd > 0)
					visitor.appendStyle(startInd);
				visitor.push("keyword");
				visitor.appendStyle(ind);
				visitor.pop();
			}
			// Check for number
			boolean isNumeric;
			if (s.length() == 1) {
				isNumeric = Character.isDigit(s.charAt(0));
			} else {
				isNumeric = Doubles.tryParse(s) != null;
//				isNumeric = NumberUtils.isCreatable(s); // Requires Apache commons-lang3
			}
			if (isNumeric) {
				if (startInd > 0)
					visitor.appendStyle(startInd);
				visitor.push("number");
				visitor.appendStyle(ind);
				visitor.pop();							
			}
		}
		buffer.setLength(0);
	}
	
	
	private static void handleSingleCharacterStyle(StyleSpanVisitor visitor, int ind, String style) {
		if (ind > 0)
			visitor.appendStyle(ind);
		visitor.push(style);
		visitor.appendStyle(ind+1);
		visitor.pop();
	}
	
	
	@Override
	public StyleSpans<Collection<String>> computeConsoleStyles(final String text, boolean logConsole) {
		return ScriptStylerProvider.getLogStyling(text);
	}
	
}
