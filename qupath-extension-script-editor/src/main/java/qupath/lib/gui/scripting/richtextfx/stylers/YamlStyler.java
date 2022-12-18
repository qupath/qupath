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
import java.util.Collection;
import java.util.Set;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
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
			int incValue = 0;
			for (var event : yaml.parse(new StringReader(text))) {
				int startInd = incValue + event.getStartMark().getIndex();
				int endInd = incValue + event.getEndMark().getIndex();
				
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
						
						// Unsure about this!
						// It seems to address problems with emoji, which are used in bioimage.io (for example)
						int inc = value.length() - Character.codePointCount(value, 0, value.length());
						if (inc > 0) {
							endInd += inc;
							incValue += inc;
						}
						
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
					visitor.push(style);
					visitor.appendStyle(endInd);	
					visitor.pop();
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
	
}
