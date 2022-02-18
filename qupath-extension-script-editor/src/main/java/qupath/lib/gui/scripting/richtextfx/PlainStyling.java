package qupath.lib.gui.scripting.richtextfx;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import javafx.concurrent.Task;
import qupath.lib.common.ThreadTools;

/**
 * Styling representing plain text.
 * @author Melvin Gelbard
 */
public class PlainStyling implements ScriptStyling {

	private ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("rich-text-plain-styling", true));
	
	@Override
	public Task<StyleSpans<Collection<String>>> computeHighlightingAsync(String text) {
		Task<StyleSpans<Collection<String>>> task =  new Task<>() {
			@Override
			protected StyleSpans<Collection<String>> call() {
				StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
				spansBuilder.add(Collections.emptyList(), text.length());
				return spansBuilder.create();
			}
		};
		
		executor.execute(task);
		return task;
	}
	

}
