package qupath.lib.gui.scripting.richtextfx;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Styling to apply to a {@link CodeArea}, based on Groovy syntax.
 * @author Melvin Gelbard
 */
public class GroovyStyling implements ScriptHighlighting {
	
	private static final String[] KEYWORDS = new String[] {
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
    };
	
	private static Pattern PATTERN;
	private static Pattern PATTERN_CONSOLE;
	
	static {
		final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
//		final String METHOD_PATTERN = "[a-zA-Z]+\\(";
//		final String METHOD_PATTERN = "\\b(" + String.join("|", METHOD_NAMES) + ")\\b";
	    final String PAREN_PATTERN = "\\(|\\)";
	    final String BRACE_PATTERN = "\\{|\\}";
	    final String BRACKET_PATTERN = "\\[|\\]";
	    final String SEMICOLON_PATTERN = "\\;";
	    final String TRIPLE_QUOTE_PATTERN = "\"\"\"([^\"\"\"\\\\]|\\\\.)*\"\"\"";
	    final String DOUBLE_QUOTE_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
	    final String SINGLE_QUOTE_PATTERN = "'([^'\\\\]|\\\\.)*\'";
	    final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
	    
	    PATTERN = Pattern.compile(
	            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
	            + "|(?<PAREN>" + PAREN_PATTERN + ")"
	            + "|(?<BRACE>" + BRACE_PATTERN + ")"
	            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
	            + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
	            + "|(?<TRIPLEQUOTES>" + TRIPLE_QUOTE_PATTERN + ")"
	            + "|(?<DOUBLEQUOTES>" + DOUBLE_QUOTE_PATTERN + ")"
	            + "|(?<SINGLEQUOTES>" + SINGLE_QUOTE_PATTERN + ")"
	            + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
	    );
	    
	    
	    final String WARNING_PATTERN = "WARN[^\n]*";
	    final String ERROR_PATTERN = "ERROR:[^\n]*";
	    
	    PATTERN_CONSOLE = Pattern.compile(
	            "(?<ERROR>" + ERROR_PATTERN + ")"
	            + "|(?<WARN>" + WARNING_PATTERN + ")"
	    );
		
	}
	
	GroovyStyling() {
		// Empty constructor
	}
	
	@Override
	public StyleSpans<Collection<String>> computeEditorHighlighting(final String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("PAREN") != null ? "paren" :
                    matcher.group("BRACE") != null ? "brace" :
                    matcher.group("BRACKET") != null ? "bracket" :
                    matcher.group("SEMICOLON") != null ? "semicolon" :
                    matcher.group("TRIPLEQUOTES") != null ? "string" :
                    matcher.group("DOUBLEQUOTES") != null ? "string" :
                    matcher.group("SINGLEQUOTES") != null ? "string" :
                    matcher.group("COMMENT") != null ? "comment" :
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
	public StyleSpans<Collection<String>> computeConsoleHighlighting(final String text) {
        Matcher matcher = PATTERN_CONSOLE.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("ERROR") != null ? "error" :
                    null; /* never happens */
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}
