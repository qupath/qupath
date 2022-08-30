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
import java.util.Collections;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Highlighting to apply to a {@link CodeArea}, based on JSON syntax.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class JsonHighlighter implements ScriptHighlighter {
	
	/**
	 * Instance of this highlighter. Can't be final because of {@link ServiceLoader}.
	 */
	private static JsonHighlighter INSTANCE;

    static final String PAREN_PATTERN = "\\(|\\)";
    static final String BRACE_PATTERN = "\\{|\\}";
    static final String BRACKET_PATTERN = "\\[|\\]";
    static final String DOUBLE_QUOTE_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    static final String SINGLE_QUOTE_PATTERN = "'([^'\\\\]|\\\\.)*\'";
    static final String REMAINING_PATTERN = "[^,.:]";
	
    private static Pattern PATTERN = Pattern.compile(
            "(?<PAREN>" + PAREN_PATTERN + ")"
            + "|(?<BRACE>" + BRACE_PATTERN + ")"
            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
            + "|(?<DOUBLEQUOTES>" + DOUBLE_QUOTE_PATTERN + ")"
            + "|(?<SINGLEQUOTES>" + SINGLE_QUOTE_PATTERN + ")"
            + "|(?<REMAINING>" + REMAINING_PATTERN + ")"
    );
    
	
	/**
	 * Get the static instance of this class.
	 * @return instance
	 */
	public static ScriptHighlighter getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Constructor for a JSON Highlighter. This constructor should never be 
	 * called. Instead, use the static {@link #getInstance()} method.
	 * <p>
	 * Note: this has to be public for the {@link ServiceLoader} to work.
	 */
	public JsonHighlighter() {
		if (INSTANCE != null)
			throw new UnsupportedOperationException("Highlighter classes cannot be instantiated more than once!");
		
		// Because of ServiceLoader, have to assign INSTANCE here.
		JsonHighlighter.INSTANCE = this;
	}
	
	@Override
	public String getLanguageName() {
		return "JSON";
	}

	@Override
	public StyleSpans<Collection<String>> computeEditorHighlighting(String text) {
		Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("PAREN") != null ? "paren" :
                    matcher.group("BRACE") != null ? "brace" :
                    matcher.group("BRACKET") != null ? "bracket" :
                    matcher.group("DOUBLEQUOTES") != null ? "string" :
                    matcher.group("SINGLEQUOTES") != null ? "string" :
                    matcher.group("REMAINING") != null ? "remaining" :
                    null; /* never happens */
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
	}

	@Override
	public StyleSpans<Collection<String>> computeConsoleHighlighting(String text) {
		return ScriptHighlighter.getPlainStyling(text);
	}
}
