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

package qupath.lib.gui.scripting.syntax;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class PropertiesSyntax implements ScriptSyntax {
	
	// Empty constructor
	PropertiesSyntax() {}
	
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("properties");
	}
	
	@Override
	public String getLineCommentString() {
		return "#";
	}
	
	@Override
	public String compress(String text) {
		return text.lines()
			.filter(l -> !l.isBlank())
			.map(PropertiesSyntax::compressLine)
			.collect(Collectors.joining("\n"));
	}
	
	@Override
	public boolean canCompress() {
		return true;
	}
	
	@Override
	public String beautify(String text) {
		List<String> lines = text.lines()
			.filter(l -> !l.isBlank())
			.map(l -> l.strip())
			.toList();
		
		var sb = new StringBuilder();
		boolean lastLineIsComment = false;
		boolean isContinuation = false;
		for (var line : lines) {
			boolean prependNewline = false;
			if (!isContinuation) {
				if (isComment(line)) {
					if (sb.length() > 0 && !lastLineIsComment)
						prependNewline = true;
					lastLineIsComment = true;
				} else {
					lastLineIsComment = false;
					if (isConfigHeader(line)) {
						prependNewline = true;
					} else {
						// One space around equals
						int indSplit = findSplitIndex(line);
						if (indSplit >= 0) {
							if (line.charAt(indSplit) == '=') {
								line = line.substring(0, indSplit).strip() + " = " + line.substring(indSplit+1).strip();
							} else if (line.charAt(indSplit) == ':') {
								line = line.substring(0, indSplit).strip() + ": " + line.substring(indSplit+1).strip();
							}
						}
						isContinuation = lineContinues(line);
					}
				}
			}
			if (prependNewline)
				sb.append("\n");
			sb.append(line);
			sb.append("\n");
		}
		return sb.toString();
	}
	
	
	private static String compressLine(String line) {
		if (isComment(line) || isConfigHeader(line))
			return line;
		int indSplit = findSplitIndex(line);
		if (indSplit < 0)
			return line;
		return line.substring(0, indSplit).strip() + line.charAt(indSplit) + line.substring(indSplit+1).strip();
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
	public boolean canBeautify() {
		return true;
	}
	
}
