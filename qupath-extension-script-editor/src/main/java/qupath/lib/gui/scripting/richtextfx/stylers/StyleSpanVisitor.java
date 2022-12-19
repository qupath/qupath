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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

class StyleSpanVisitor {

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
		
		
		public void push(String style) {
			this.currentStyle.push(style);
		}
		
		public String pop() {
			return this.currentStyle.pop();
		}
		
		public StyleSpans<Collection<String>> buildStyles() {
			appendStyle(text.length(), true);
			return spansBuilder.create();
		}
		
		void appendStyle(int untilInd) {
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
			} else if (untilInd <= lastInd)
				return;
			else
				throw new IllegalArgumentException("Cannot append empty style from " + lastInd + "-" + untilInd + " (must be ascending)");
		}

	}