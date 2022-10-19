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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Class with static methods to fetch all the available {@link ScriptHighlighter}s.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class ScriptHighlighterProvider {
	
	private static ServiceLoader<ScriptHighlighter> serviceLoader = ServiceLoader.load(ScriptHighlighter.class);
	
	/**
	 * Get all the currently installed {@link ScriptHighlighter}s in a list.
	 * @return list of installed highlighters
	 */
	public static List<ScriptHighlighter> getInstalledScriptHighlighters() {
		List<ScriptHighlighter> highlighters = new ArrayList<>();
		synchronized (serviceLoader) {
			for (ScriptHighlighter h : serviceLoader) {
				highlighters.add(h);
			}
		}
		return highlighters;
	}

	/**
	 * Get the {@link ScriptHighlighter} object corresponding to the specified {@link ScriptLanguage}. 
	 * If the language cannot be matched, {@link PlainHighlighter} is returned.
	 * @param language
	 * @return corresponding highlighter, or {@link PlainHighlighter} if no match.
	 */
	public static ScriptHighlighter getHighlighterFromLanguage(ScriptLanguage language) {
		String name = language.getName().toLowerCase();
		synchronized (serviceLoader) {
			for (ScriptHighlighter h : serviceLoader) {
				for (var supported : h.getLanguageNames()) {
					if (name.equalsIgnoreCase(supported))
						return h;
				}
			}
		}
		return PlainHighlighter.getInstance();
	}
	
	
	
	/**
	 * Get simple styling that does not apply any classes.
	 * @param text the text to process highlighting for
	 * @return
	 */
	static StyleSpans<Collection<String>> getPlainStyling(String text) {
		StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
		spansBuilder.add(Collections.emptyList(), text.length());
		return spansBuilder.create();
	}
	
	
	private static final Pattern PATTERN_NEWLINES = Pattern.compile("^|\\n");
	
	/**
	 * Get styling for use with a logger.
	 * @param text the text to process highlighting for
	 * @return
	 */
	static StyleSpans<Collection<String>> getLogStyling(String text) {
		var builder = new StyleSpansCollectionBuilder(text);
		
		var logClassMap = Map.of("INFO:", "info", "WARN:", "warning", "ERROR:", "error", "DEBUG:", "debug", "TRACE:", "trace");
		var logKeys = logClassMap.keySet();
		
		var matcher = PATTERN_NEWLINES.matcher(text);
		int start = 0;
		int len = text.length();
		String nextStyle = null;
		String lastStyle = null;
		while (true) {
			// Check for the start of each line
			if (start >= len)
				break;
			for (var key : logKeys) {
				int keyLength = key.length();
				if (start + keyLength >= len)
					continue;
				if (text.regionMatches(start, key, 0, keyLength)) {
					nextStyle = logClassMap.get(key);
					break;
				}
			}
			
			// Push new style if needed
			if (nextStyle != null || !Objects.equals(nextStyle, lastStyle)) {
				builder.appendStyle(start);
				if (lastStyle != null)
					builder.popStyle();
				builder.pushStyle(nextStyle);
				lastStyle = nextStyle;
			}
			
			// Move to next line
			if (!matcher.find())
				break;
			start = matcher.start()+1;
		}
		
		builder.appendStyle(len, true);
		var styles = builder.spansBuilder.create();
		return styles;
	}
	
	
	static class StyleSpansCollectionBuilder {

		private String text;
		
		private StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
		private int[] lineSums;
		private int lastInd;
		
		private Deque<String> currentStyle = new ArrayDeque<>();

		StyleSpansCollectionBuilder(String text) {
			this.text = text;
			var lengths = text.lines().mapToInt(l -> l.length()+1).toArray();
			lineSums = new int[lengths.length+1];
			for (int i = 0; i < lengths.length; i++) {
				lineSums[i+1] = lineSums[i] + lengths[i];
			}
		}
		
		public StyleSpans<Collection<String>> buildStyles() {
			appendStyle(text.length(), true);
			return spansBuilder.create();
		}
		
		public void appendStyle(int untilInd) {
			appendStyle(untilInd, false);
		}
		
		public void pushStyle(String style) {
			currentStyle.push(style);
		}
		
		public String popStyle() {
			return currentStyle.pop();
		}
		
		public void appendStyle(int untilInd, boolean lastStyle) {
			if (untilInd > lastInd || lastStyle) {
				if (currentStyle.isEmpty())
					spansBuilder.add(Collections.emptyList(), untilInd - lastInd);
				else if (currentStyle.size() == 1)
					spansBuilder.add(Collections.singletonList(currentStyle.peek()), untilInd - lastInd);
				else
					spansBuilder.add(new ArrayList<>(currentStyle), untilInd - lastInd);					
				lastInd = untilInd;
			} else if (untilInd == lastInd)
				return;
			else
				throw new IllegalArgumentException("Cannot append empty style from " + lastInd + "-" + untilInd + " (must be ascending)");
		}
		
	}

	
}
