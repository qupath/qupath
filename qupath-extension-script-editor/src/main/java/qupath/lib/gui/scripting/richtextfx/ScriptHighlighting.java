package qupath.lib.gui.scripting.richtextfx;

import java.util.Collection;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;

/**
 * Interface for classes that apply some highlighting to a RichTextFX's {@link CodeArea}.
 * @author Melvin Gelbard
 */
public interface ScriptHighlighting {

	/**
	 * Compute highlighting for the specified {@code text}, considering it will be used in the main editor..
	 * @param text
	 * @return stylespans
	 */
	StyleSpans<Collection<String>> computeEditorHighlighting(final String text);
	
	
	/**
	 * Compute highlighting for the specified {@code text}, considering it will be used in the console.
	 * @param text
	 * @return stylespans
	 */
	StyleSpans<Collection<String>> computeConsoleHighlighting(final String text);
	

}
