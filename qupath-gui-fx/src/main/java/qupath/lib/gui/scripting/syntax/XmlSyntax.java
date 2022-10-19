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

package qupath.lib.gui.scripting.syntax;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.scripting.EditableText;

/**
 * Class that takes care of XML syntax.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
class XmlSyntax extends GeneralCodeSyntax {
	
	private static final Logger logger = LoggerFactory.getLogger(XmlSyntax.class);
	
	// Empty constructor
	XmlSyntax() {}
	
	@Override
	public void handleLineComment(final EditableText control) {
		String text = control.getText();
		
		int rangeStart = control.getSelectionStart();
		int rangeEnd = control.getSelectionEnd();
		int rangeLength = control.getSelectionLength();
		
		boolean hasSelection = rangeLength > 0;
		boolean hasMultilineSelection = hasSelection && control.getSelectedText().contains("\n");
		String textBetween = control.getSelectedText();
		if (hasSelection && !hasMultilineSelection && !textBetween.startsWith("<!--") && !textBetween.startsWith("-->")) {
			// Add inline comment
			String newText = "<!--" + textBetween + "-->";
			control.replaceSelection(newText);
			control.selectRange(rangeStart, rangeStart + newText.length());
		} else {
			int startRowPos = getRowStartPosition(text, rangeStart);
			int endRowPos = getRowEndPosition(text, rangeEnd);
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
					control.replaceSelection(newText);
					control.selectRange(startRowPos, startRowPos + newText.length());
				}
			} else {
				// Add comment
				String newText = "<!--" + textBetween + "-->";
				control.selectRange(startRowPos, endRowPos);
				control.replaceSelection(newText);
				control.selectRange(startRowPos, startRowPos + newText.length());
			}
		}	
	}
	
	// With thanks to https://stackoverflow.com/questions/5511096/java-convert-formatted-xml-file-to-one-line-string
	private static String trimWhitespace = "<?xml version=\"1.0\"?>\n"
			+ "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
			+ "    <xsl:output indent=\"no\" />\n"
			+ "    <xsl:strip-space elements=\"*\"/>\n"
			+ "    <xsl:template match=\"@*|node()\">\n"
			+ "        <xsl:copy>\n"
			+ "            <xsl:apply-templates select=\"@*|node()\"/>\n"
			+ "        </xsl:copy>\n"
			+ "    </xsl:template>\n"
			+ "</xsl:stylesheet>";
	
	
	private static Transformer createTransformer(boolean indent) throws TransformerConfigurationException, TransformerFactoryConfigurationError {
		var transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(new StringReader(trimWhitespace)));
		if (indent) {
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		} else {
			transformer.setOutputProperty(OutputKeys.INDENT, "no");
		}
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");		
		return transformer;
	}
	
	private static String applyTransform(Transformer transformer, String text) throws TransformerException {
		var source = new StreamSource(new StringReader(text));
		var writer = new StringWriter();
		var result = new StreamResult(writer);
		transformer.transform(source, result);
		return result.getWriter().toString();
	}
	
	
	@Override
	public String beautify(String text) {
		try {
			var transformer = createTransformer(true);
			return applyTransform(transformer, text);
		} catch (Exception ex) {
			logger.warn("Could not beautify this XML", ex.getLocalizedMessage());
			return text;
		}
	}
	
	@Override
	public boolean canBeautify() {
		return true;
	}

	@Override
	public boolean canCompress() {
		return true;
	}

	@Override
	public String compress(String text) {
		try {
			var transformer = createTransformer(false);
			return applyTransform(transformer, text);
		} catch (Exception ex) {
			logger.warn("Could not compress this XML", ex.getLocalizedMessage());
			return text;
		}
	}
	
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("xml");
	}
	
}
