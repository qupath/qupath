package qupath.lib.gui.scripting.richtextfx;

import java.util.Collection;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;

import javafx.concurrent.Task;

/**
 * Interface for classes that apply some styling to a RichTextFX's {@link CodeArea}.
 * @author Melvin Gelbard
 */
public interface ScriptStyling {

	/**
	 * Compute highlighting for the specified {@code text}.
	 * <p>
	 * Make sure to execute the task, then return it.
	 * @param text
	 * @return task
	 */
	Task<StyleSpans<Collection<String>>> computeHighlightingAsync(final String text);
	

}
