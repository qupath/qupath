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

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Set;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.ScalarEvent;

/**
 * Styling to apply to a {@link CodeArea}, based on YAML syntax.
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class YamlStyler implements ScriptStyler {
	
	private static final Logger logger = LoggerFactory.getLogger(YamlStyler.class);
	
	/**
	 * Constructor.
	 */
	YamlStyler() {}
	
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("yaml");
	}
	
	private static Yaml yaml = new Yaml();

	// TODO: Simplify this if we only handle comments?
	// Code here for potentially more informative styling some day
	@Override
	public StyleSpans<Collection<String>> computeEditorStyles(String text) {
		long startTime = System.currentTimeMillis();
		
		var visitor = new StyleSpanVisitor(text);
		
		try {
			for (var event : yaml.parse(new StringReader(text))) {
				int startInd = event.getStartMark().getIndex();
				int endInd = event.getEndMark().getIndex();
				
				visitor.appendStyle(startInd);
				
				String style = null;
				switch (event.getEventId()) {
				case Alias:
					break;
				case Comment:
					style = "comment";
					break;
				case DocumentEnd:
					break;
				case DocumentStart:
					break;
				case MappingEnd:
					style = "paren";
					break;
				case MappingStart:
					style = "paren";
					break;
				case Scalar:
					style = "string";
					if (event instanceof ScalarEvent) {
						var scalar = (ScalarEvent)event;
						var value = scalar.getValue();
						if (isNumeric(value))
							style = "number";
					}
					break;
				case SequenceEnd:
					style = "bracket";
					break;
				case SequenceStart:
					style = "bracket";
					break;
				case StreamEnd:
					break;
				case StreamStart:
					break;
				default:
					break;
				}
				
				if (style != null) {
					visitor.currentStyle.push(style);
					visitor.appendStyle(endInd);	
					visitor.currentStyle.pop();
				}
			}
		} catch (Exception e) {
			logger.trace(e.getLocalizedMessage(), e);
		}
		var styles = visitor.buildStyles();
		
		long endTime = System.currentTimeMillis();
		
		logger.trace("YAML styling time: {}", (endTime - startTime));
        
        return styles;
	}
	
	
	private static boolean isNumeric(String value) {
		if (value == null || value.isEmpty())
			return false;
		try {
			Double.parseDouble(value);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	

	static class StyleSpanVisitor {

		private String text;
		
		private StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
		private int[] lineSums;
		private int lastInd;
		
		private Deque<String> currentStyle = new ArrayDeque<>();

		StyleSpanVisitor(String text) {
			this.text = text;
//			this.currentStyle.add("code");
			var lengths = text.lines().mapToInt(l -> l.length()+1).toArray();
			lineSums = new int[lengths.length+1];
			for (int i = 0; i < lengths.length; i++) {
				lineSums[i+1] = lineSums[i] + lengths[i];
			}
//			currentStyle.add("yaml");
		}
		
		public StyleSpans<Collection<String>> buildStyles() {
			appendStyle(text.length(), true);
			return spansBuilder.create();
		}
		
		private void appendStyle(int untilInd) {
			appendStyle(untilInd, false);
		}
		
		private void appendStyle(int untilInd, boolean lastStyle) {
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
