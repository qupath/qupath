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

package qupath.lib.gui.scripting.languages;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.IndexRange;
import qupath.lib.gui.scripting.ScriptEditorControl;

/**
 * Class that takes care of XML syntax.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
class XmlSyntax extends GeneralCodeSyntax {
	
	private static final XmlSyntax INSTANCE = new XmlSyntax();
	private static final Logger logger = LoggerFactory.getLogger(XmlSyntax.class);
	
	// Empty constructor
	private XmlSyntax() {}
	
	static XmlSyntax getInstance() {
		return INSTANCE;
	}
	
	/**
	 * XML does not easily support line comments comments. Therefore this method does nothing.
	 */
	@Override
	public void handleLineComment(final ScriptEditorControl control) {
		// Do nothing
		
		String text = control.getText();
		IndexRange range = control.getSelection();
		boolean hasSelection = range.getLength() > 0;
		boolean hasMultilineSelection = hasSelection && control.getSelectedText().contains("\n");
		String textBetween = control.getSelectedText();
		if (hasSelection && !hasMultilineSelection && !textBetween.startsWith("<!--") && !textBetween.startsWith("-->")) {
			String newText = "<!--" + textBetween + "-->";
			control.paste(newText);
			control.selectRange(range.getStart(), range.getStart() + newText.length());
		} else {
			int startRowPos = getRowStartPosition(text, range.getStart());
			int endRowPos = getRowEndPosition(text, range.getEnd());
			textBetween = text.substring(startRowPos, endRowPos);
			if (textBetween.trim().startsWith("<!--") && textBetween.trim().endsWith("-->")) {
				// Remove comment
				int indStart2 = textBetween.indexOf("<!--");
				int indEnd2 = textBetween.lastIndexOf("-->");
				endRowPos = startRowPos + indEnd2 + 3;
				startRowPos += indStart2;
				if (indEnd2 > indStart2 + 4) {
					control.selectRange(startRowPos, endRowPos);
					String newText = textBetween.substring(indStart2+4, indEnd2);
					control.paste(newText);
					control.selectRange(startRowPos, startRowPos + newText.length());
				}
			} else {
				// Add comment
				String newText = "<!--" + textBetween + "-->";
				control.selectRange(startRowPos, endRowPos);
				control.paste(newText);
				control.selectRange(startRowPos, startRowPos + newText.length());
//				control.positionCaret(pos + 4);
			}
		}	
	}
	
	@Override
	public String beautify(String text) {
		try {
			var transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			
			var source = new StreamSource(new StringReader(text));
			var writer = new StringWriter();
			var result = new StreamResult(writer);
			
			transformer.transform(source, result);
			
			return result.getWriter().toString();
		} catch (TransformerException ex) {
			logger.warn("Could not beautify this XML", ex.getLocalizedMessage());
			return text;
		}
	}
}
