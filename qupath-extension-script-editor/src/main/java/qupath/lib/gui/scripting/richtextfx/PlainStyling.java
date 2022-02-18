package qupath.lib.gui.scripting.richtextfx;

import java.util.Collection;
import java.util.Collections;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Styling for plain text.
 * @author Melvin Gelbard
 */
public class PlainStyling implements ScriptHighlighting {
	
	@Override
	public StyleSpans<Collection<String>> computeEditorHighlighting(String text) {
		return getPlainStyling(text);
	}

	@Override
	public StyleSpans<Collection<String>> computeConsoleHighlighting(String text) {
		return getPlainStyling(text);
	}
	
	private static StyleSpans<Collection<String>> getPlainStyling(String text) {
		StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
		spansBuilder.add(Collections.emptyList(), text.length());
		return spansBuilder.create();
	}
}
