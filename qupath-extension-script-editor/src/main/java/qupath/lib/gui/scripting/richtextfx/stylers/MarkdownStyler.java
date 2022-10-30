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
import java.util.Set;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.LogTools;

/**
 * Styling to apply to a {@link CodeArea}, based on Markdown syntax.
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class MarkdownStyler implements ScriptStyler {
	
	private static final Logger logger = LoggerFactory.getLogger(MarkdownStyler.class);
	
	/**
	 * Constructor.
	 */
	MarkdownStyler() {}
	
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("markdown");
	}
	
	/**
	 * Returns {@code "-fx-font-family: sans-serif"} to indicate the text should not be formatted as code by default.
	 */
	@Override
	public String getBaseStyle() {
		return "-fx-font-family: sans-serif;";
	}
	
	private static Parser parser = Parser.builder()
			.includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
			.build();

	@Override
	public StyleSpans<Collection<String>> computeEditorStyles(String text) {
		
		// Stop quickly if we have really long lines, which can cause QuPath to freeze
		int longLine = ScriptStylerTools.DEFAULT_LONG_LINE_LENGTH;
		if (ScriptStylerTools.containsLongLines(text, longLine)) {
			LogTools.logOnce(logger, "Text contains lines longer than " + longLine + " - no styling will be applied");
			return ScriptStylerProvider.getPlainStyling(text);
		}
		
		long startTime = System.currentTimeMillis();
		var doc = parser.parse(text);
		var visitor = new StyleSpanVisitor(text);
		doc.accept(visitor);
		var styles = visitor.buildStyles();
		
		long endTime = System.currentTimeMillis();
		
		logger.trace("Markdown styling time: {}", (endTime - startTime));
        
        return styles;
	}
	
	static class StyleSpanVisitor extends AbstractVisitor {

		private String text;
		
		private StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
		private int[] lineSums;
		private int lastInd;
		
		private Deque<String> currentStyle = new ArrayDeque<>();

		StyleSpanVisitor(String text) {
			this.text = text;
			var lengths = text.lines().mapToInt(l -> l.length()+1).toArray();
			lineSums = new int[lengths.length+1];
			for (int i = 0; i < lengths.length; i++) {
				lineSums[i+1] = lineSums[i] + lengths[i];
			}
			currentStyle.add("md");
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
		
		private void visitAny(Node node, String style) {
			var spans = node.getSourceSpans();
			var sourceFirst = spans.get(0);
			var sourceLast = spans.get(spans.size()-1);
			
			int indStart = lineSums[sourceFirst.getLineIndex()] + sourceFirst.getColumnIndex();
			int indEnd = lineSums[sourceLast.getLineIndex()] + sourceLast.getColumnIndex() + sourceLast.getLength();
			
			appendStyle(indStart);
			currentStyle.push(style);
			visitChildren(node);
			appendStyle(indEnd);	
			currentStyle.pop();
		}

		@Override
		public void visit(BlockQuote blockQuote) {
			visitAny(blockQuote, "quote");
		}

		@Override
		public void visit(BulletList bulletList) {
			visitAny(bulletList, "list");
		}

		@Override
		public void visit(Code code) {
			visitAny(code, "code");
		}

		@Override
		public void visit(Emphasis emphasis) {
			visitAny(emphasis, "emph");
		}

		@Override
		public void visit(FencedCodeBlock fencedCodeBlock) {
			visitAny(fencedCodeBlock, "code");
		}

		@Override
		public void visit(Heading heading) {
			visitAny(heading, "h" + Math.min(6, heading.getLevel()));
		}

		@Override
		public void visit(HtmlInline htmlInline) {
			visitAny(htmlInline, "raw");
		}

		@Override
		public void visit(HtmlBlock htmlBlock) {
			visitAny(htmlBlock, "raw");
		}

		@Override
		public void visit(Image image) {
			visitAny(image, "image");
		}

		@Override
		public void visit(IndentedCodeBlock indentedCodeBlock) {
			visitAny(indentedCodeBlock, "code");
		}

		@Override
		public void visit(Link link) {
			visitAny(link, "link");
		}

		@Override
		public void visit(OrderedList orderedList) {
			visitAny(orderedList, "list");
		}

		@Override
		public void visit(StrongEmphasis strongEmphasis) {
			visitAny(strongEmphasis, "strong");
		}

		@Override
		public void visit(LinkReferenceDefinition linkReferenceDefinition) {
			visitChildren(linkReferenceDefinition);
		}

		@Override
		public void visit(CustomBlock customBlock) {
			visitChildren(customBlock);
		}

		@Override
		public void visit(CustomNode customNode) {
			visitChildren(customNode);
		}

	}
	
}
