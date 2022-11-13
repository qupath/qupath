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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Styling to apply to a {@link CodeArea} for XML.
 * <p>
 * This is based on {@code XMLEditorDemo.java} from RichTextFX, available at 
 * https://github.com/FXMisc/RichTextFX/blob/master/richtextfx-demos/src/main/java/org/fxmisc/richtext/demo/XMLEditorDemo.java
 * and adapted for use in QuPath.
 * <p>
 * The license for RichTextFX is given below:
 * <p><blockquote><pre>
 *   Copyright (c) 2013-2017, Tomas Mikula and contributors
 *   
 *   All rights reserved.
 *   
 *   Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *   
 *   1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *   
 *   2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *   
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 *   INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *   IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 *   DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 *   HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING 
 *   IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * </pre></blockquote>
 * 
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class XmlStyler implements ScriptStyler {
	
	private static final Pattern XML_TAG = Pattern.compile("(?<ELEMENT>(</?\\h*)(\\w+)([^<>]*)(\\h*/?>))"
    		+"|(?<COMMENT><!--(.|\\v)+?-->)");
    
    private static final Pattern ATTRIBUTES = Pattern.compile("(\\w+\\h*)(=)(\\h*\"[^\"]+\")");
    
    private static final int GROUP_OPEN_BRACKET = 2;
    private static final int GROUP_ELEMENT_NAME = 3;
    private static final int GROUP_ATTRIBUTES_SECTION = 4;
    private static final int GROUP_CLOSE_BRACKET = 5;
    private static final int GROUP_ATTRIBUTE_NAME = 1;
    private static final int GROUP_EQUAL_SYMBOL = 2;
    private static final int GROUP_ATTRIBUTE_VALUE = 3;
    
	
	/**
	 * Constructor.
	 */
	XmlStyler() {}
	
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("xml");
	}

	@Override
	public StyleSpans<Collection<String>> computeEditorStyles(String text) {
		return computeStyling(text);
	}

	
	private static StyleSpans<Collection<String>> computeStyling(String text) {
    	
        Matcher matcher = XML_TAG.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
        	
        	appendSpan(spansBuilder, null, matcher.start() - lastKwEnd);
        	if (matcher.group("COMMENT") != null) {
        		appendSpan(spansBuilder, "comment", matcher.end() - matcher.start());
        	}
        	else {
        		if (matcher.group("ELEMENT") != null) {
        			String attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION);
        			
        			appendSpan(spansBuilder, "tagmark", matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));
        			appendSpan(spansBuilder, "anytag", matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));

        			if(!attributesText.isEmpty()) {
        				
        				lastKwEnd = 0;
        				
        				Matcher amatcher = ATTRIBUTES.matcher(attributesText);
        				while (amatcher.find()) {
        					appendSpan(spansBuilder, null, amatcher.start() - lastKwEnd);
        					appendSpan(spansBuilder, "attribute", amatcher.end(GROUP_ATTRIBUTE_NAME) - amatcher.start(GROUP_ATTRIBUTE_NAME));
        					appendSpan(spansBuilder, "tagmark", amatcher.end(GROUP_EQUAL_SYMBOL) - amatcher.end(GROUP_ATTRIBUTE_NAME));
        					appendSpan(spansBuilder, "avalue", amatcher.end(GROUP_ATTRIBUTE_VALUE) - amatcher.end(GROUP_EQUAL_SYMBOL));
        					lastKwEnd = amatcher.end();
        				}
        				if(attributesText.length() > lastKwEnd)
        					appendSpan(spansBuilder, null, attributesText.length() - lastKwEnd);
        			}

        			lastKwEnd = matcher.end(GROUP_ATTRIBUTES_SECTION);
        			
        			appendSpan(spansBuilder, "tagmark", matcher.end(GROUP_CLOSE_BRACKET) - lastKwEnd);
        		}
        	}
            lastKwEnd = matcher.end();
        }
        appendSpan(spansBuilder, null, text.length() - lastKwEnd);
        return spansBuilder.create();
    }
	
	private static final Collection<String> BASE_STYLE = Collections.singletonList("xml");
	
	private static void appendSpan(StyleSpansBuilder<Collection<String>> spansBuilder, String style, int length) {
		if (style == null || style.isEmpty())
			spansBuilder.add(BASE_STYLE, length);
		else
			spansBuilder.add(List.of("xml", style), length);
	}

}
