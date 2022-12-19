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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Doubles;

/**
 * Styling to apply to a {@link CodeArea}, based on Groovy or Java syntax.
 * @author Pete Bankhead
 * @since v0.4.1
 * 
 * @implNote This was rewritten for v0.4.1 to avoid relying on regex, 
 *           which could sometimes result in a {@link StackOverflowError}.
 */
public class GroovyStyler implements ScriptStyler {
	
	private static final Logger logger = LoggerFactory.getLogger(GroovyStyler.class);
	
	/**
	 * Main keywords in Java
	 */
	private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
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
            "true", "false", "var"
    ));
	
	/**
	 * Additional keywords in Groovy
	 */
	private static final Set<String> GROOVY_KEYWORDS = Set.of(
			"def", "in", "with", "trait"
			);
	
	
	private boolean isGroovy;
	
	private GroovyStyler(boolean isGroovy) {
		this.isGroovy = isGroovy;
	}
	
	/**
	 * Create a Groovy styler
	 * @return
	 */
	static ScriptStyler createGroovyStyler() {
		return new GroovyStyler(true);
	}

	/**
	 * Create a Java styler; this is like a Groovy styler, but with some of Groovy's 
	 * extra parts removed
	 * @return
	 */
	static ScriptStyler createJavaStyler() {
		return new GroovyStyler(false);
	}

	
	@Override
	public Set<String> getLanguageNames() {
		if (isGroovy)
			return Set.of("groovy");
		else
			return Set.of("java");
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
				} else
					handleToken(visitor, ind, buffer);
				break;
			case '\'':
				// Handle single or triple single quotes
				if (isGroovy && ind < n - 2 && chars[ind+1] == '\'' && chars[ind+2] == '\'') {
					// Handle triple single quotes
					ind = handleString(visitor, text, ind, "'''", "'''", buffer);
				} else {
					// Handle single quotes
					ind = handleString(visitor, text, ind, "'", "'", buffer);
				}
				break;
			case '"':
				// Handle single or triple double quotes
				if (isGroovy && ind < n - 2 && chars[ind+1] == '"' && chars[ind+2] == '"') {
					// Handle triple double quotes
					ind = handleString(visitor, text, ind, "\"\"\"", "\"\"\"", buffer);
				} else {
					// Handle double quotes
					ind = handleString(visitor, text, ind, "\"", "\"", buffer);
				}
				break;
			case '/':
				if (lastChar == '/') {
					// Handle line comment
					handleToken(visitor, ind-1, buffer);
					visitor.appendStyle(ind-1);
					visitor.push("comment");
					while (ind < n && chars[ind] != '\n')
						ind++;
					visitor.appendStyle(ind);
					visitor.pop();
				} else if (isGroovy) {
					// Possibly handle slashy string
					if (ind > 0 && chars[ind-1] == '$') {
						// Handle dollar slashy string
						ind = handleString(visitor, text, ind-1, "/$", "/$", buffer);
					} else if (ind < n-1 && chars[ind+1] != '*') {
						// Don't try to handle a regular slashy string, since it causes trouble with the division operator 
						// (and we don't have proper parsing to handle that)
//						ind = handleString(visitor, text, ind, "/", "/", buffer);
						handleToken(visitor, ind, buffer);
					}
				} else 
					handleToken(visitor, ind, buffer);
				break;
			default:
				// Check for token
				boolean isLastCharacter = ind == n-1;
//				boolean isWhitespace = Character.isWhitespace(c);
				boolean isBreakingCharacter = !Character.isLetterOrDigit(c) && 
						c != '.' &&
						c != '_' &&
						c != '-'; // For negative numbers
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
		logger.debug("Style time: {} (length={})", endTime - startTime, n);
		
		return styles;
    }
	
	/**
	 * Pattern for Groovy string interpolation using "${something here}" or "$something.else"
	 * Note that this is basic & doesn't handle nested brackets.
	 * (Requires negative lookbehind to handle escaped dollars, but does't check for number of 
	 * escape characters)
	 */
	private static Pattern patternStringInterpolation = Pattern.compile("((?<!\\\\)\\$\\{[^}]*\\})|"
			+ "((?<!\\\\)\\$[\\w\\.]+)");
	
	
	
	/**
	 * Handle a string block (Groovy supports different kinds - see https://groovy-lang.org/syntax.html#_string_summary_table )
	 * @param visitor
	 * @param text
	 * @param startInd
	 * @param startSequence
	 * @param endSequence
	 * @param buffer
	 * @return
	 */
	private int handleString(StyleSpanVisitor visitor, String text, int startInd, String startSequence, String endSequence, StringBuffer buffer) {
		resetToken(buffer);
		
		boolean isMultiline = startSequence.length() > 1 || (startSequence.equals("'") || startSequence.equals("\""));
		char escapeChar = startSequence.equals("$/") ? '$' : '\\';
		
		int endInd = findNextEnd(text, startInd + startSequence.length(), endSequence, escapeChar);
		
		if (!isMultiline) {
			int newlineInd = findNextEnd(text, startInd + startSequence.length(), "\n", escapeChar);
			if (endInd < 0 || newlineInd < endInd)
				endInd = newlineInd;
		}
		
		if (endInd < 0)
			endInd = text.length();

		// Start string styling
		visitor.appendStyle(startInd);
		visitor.push("string");

		// Check for string interpolation
		boolean canInterpolate = isGroovy && 
				!startSequence.equals("'") && 
				!startSequence.equals("'''") && 
				!startSequence.equals("/$");
		if (canInterpolate) {
			String substring = text.substring(startInd, endInd);
			if (substring.indexOf("$") >= 0) {
				var matcher = patternStringInterpolation.matcher(substring);
				while (matcher.find()) {
					visitor.appendStyle(startInd + matcher.start());
					visitor.pop();					
					visitor.appendStyle(startInd + matcher.end());
					visitor.push("string");					
				}
			}
		}
		
		visitor.appendStyle(endInd);
		visitor.pop();
		return endInd - 1; // endInd really points to the next character - subtract one so we can then increment
	}
	
	
	private static int findNextEnd(String text, int startInd, String endSequence, char escapeChar) {
		int ind = text.indexOf(endSequence, startInd);
		if (ind < 0)
			return ind;
		// Check if we end with an odd number of escape characters - if so, skip the sequence
		int nEscapes = 0;
		int indEscape = ind-1;
		while (indEscape >= startInd && text.charAt(indEscape) == escapeChar) {
			nEscapes++;
			indEscape--;
		}
		if (nEscapes % 2 != 0)
			return findNextEnd(text, ind+1, endSequence, escapeChar);
		return ind + endSequence.length();
	}
	
	
	
	private static void resetToken(StringBuffer buffer) {
		buffer.setLength(0);
	}
	
	
	private void handleToken(StyleSpanVisitor visitor, int ind, StringBuffer buffer) {
		if (buffer.length() > 0) {
			var s = buffer.toString();
			int startInd = ind - s.length();
			if (JAVA_KEYWORDS.contains(s) || (isGroovy && GROOVY_KEYWORDS.contains(s))) {
				if (startInd > 0)
					visitor.appendStyle(startInd);
				visitor.push("keyword");
				visitor.appendStyle(ind);
				visitor.pop();
			}
			// Check for number
			if (isNumeric(s)) {
				if (startInd > 0)
					visitor.appendStyle(startInd);
				visitor.push("number");
				visitor.appendStyle(ind);
				visitor.pop();							
			}
		}
		buffer.setLength(0);
	}
	
	
	/**
	 * Pattern to match underscore, used to string underscores when checking if a number is valid
	 */
	private static Pattern patternUnderscore = Pattern.compile("_");
	
	private static Map<String, Boolean> numberTokenCache = new ConcurrentHashMap<>();
	
	/**
	 * Check if a string represents a valid number
	 * @param s
	 * @return
	 */
	private static boolean isNumeric(String s) {
		if (s.length() == 1)
			return Character.isDigit(s.charAt(0));
		// Strip underscores before checking a number can be parsed
		// (Underscores allowed since Java 7)
		// Since this could be expensive, cache results
		var isNumeric = numberTokenCache.getOrDefault(s, null);
		if (isNumeric == null) {
			if (s.indexOf('_') > 0) {
				var s2 = patternUnderscore.matcher(s).replaceAll("");
				isNumeric = s2.length() > 0 && Doubles.tryParse(s2) != null;
				numberTokenCache.put(s, isNumeric);
				return isNumeric;
			} else
				return Doubles.tryParse(s) != null;
		}
		return isNumeric;
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
