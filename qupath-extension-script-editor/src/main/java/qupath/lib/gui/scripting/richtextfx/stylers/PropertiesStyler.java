/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

import java.util.Collection;
import java.util.Set;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;

/**
 * Styling to apply to a {@link CodeArea}, based on Java .properties and .cfg file syntax.
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class PropertiesStyler implements ScriptStyler {
	
	/**
	 * Constructor.
	 */
	PropertiesStyler() {}
	
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("properties", "config", "cfg");
	}
	
	@Override
	public StyleSpans<Collection<String>> computeEditorStyles(final String text) {
		
		var visitor = new StyleSpanVisitor(text);
		String[] lines = text.split("\n");
		int ind = 0;
		boolean isContinuation = false;
		for (var line: lines) {
			
			String stripped = line.strip();
			if (isContinuation) {
				if (lineContinues(stripped))
					continue;
				isContinuation = false;
			} else {
				if (isComment(stripped)) {
					visitor.push("comment");
				} else if (isConfigHeader(line)) {
					visitor.push("keyword");
				} else {
					int indSplit = findSplitIndex(line);
					if (indSplit < 0) {
						visitor.push("error");
					} else {
						visitor.push("text");
						visitor.appendStyle(ind + indSplit);
						visitor.pop();
						visitor.push("semicolon");
						visitor.appendStyle(ind + indSplit + 1);
						visitor.pop();
						visitor.push("string");
						isContinuation = lineContinues(stripped);
					}
				}
			}
			
			if (!isContinuation) {
				visitor.appendStyle(ind + line.length());
				visitor.pop();
			}
			ind += line.length() + 1;
		}
		return visitor.buildStyles();
    }
	
	private static int findSplitIndex(String line) {
		int indEquals = line.indexOf("=");
		int indColon = line.indexOf(":");
		if (indEquals >= 0) {
			if (indColon < 0 || indColon > indEquals)
				return indEquals;
			else
				return indColon;
		}
		return indColon;
	}
	
	private static boolean lineContinues(String line) {
		return line.endsWith("\\");
	}
	
	private static boolean isConfigHeader(String line) {
		return line.startsWith("[") && line.endsWith("]");
	}
	
	private static boolean isComment(String line) {
		return line.startsWith("#") || line.startsWith("!");
	}
	
	@Override
	public StyleSpans<Collection<String>> computeConsoleStyles(final String text, boolean logConsole) {
		return ScriptStylerProvider.getLogStyling(text);
	}
	
}
