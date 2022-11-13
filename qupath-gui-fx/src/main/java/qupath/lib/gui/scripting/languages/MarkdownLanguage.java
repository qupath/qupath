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

import java.util.ServiceLoader;
import java.util.Set;

import javax.script.ScriptException;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import qupath.lib.scripting.ScriptParameters;
import qupath.lib.scripting.languages.ScriptAutoCompletor;
import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Class for the representation of JSON syntax in QuPath.
 * <p>
 * This class stores the QuPath implementation of Markdown syntaxing and a dummy plain auto-completion.
 * 
 * @author Pete Bankhead (based on Melvin Gelbard's code)
 * @since v0.4.0
 */
public class MarkdownLanguage extends ScriptLanguage implements qupath.lib.gui.scripting.languages.HtmlRenderer {
	
	/**
	 * Instance of this language. Can't be final because of {@link ServiceLoader}.
	 */
	private static MarkdownLanguage INSTANCE;
	
	private ScriptAutoCompletor completor = null;
	
	/**
	 * Constructor for JSON language. This constructor should never be 
	 * called. Instead, use the static {@link #getInstance()} method.
	 * <p>
	 * Note: this has to be public for the {@link ServiceLoader} to work.
	 */
	public MarkdownLanguage() {
		super("Markdown", Set.of(".md", ".markdown"));
		
		if (INSTANCE != null)
			throw new UnsupportedOperationException("Language classes cannot be instantiated more than once!");
		
		// Because of ServiceLoader, have to assign INSTANCE here.
		MarkdownLanguage.INSTANCE = this;
	}

	/**
	 * Get the static instance of this class.
	 * @return instance
	 */
	public static MarkdownLanguage getInstance() {
		return INSTANCE;
	}

	@Override
	public ScriptAutoCompletor getAutoCompletor() {
		return completor;
	}
	

	@Override
	public Object execute(ScriptParameters params) throws ScriptException {
		try {
			var doc = Parser.builder().build().parse(params.getScript());
			return HtmlRenderer.builder().build().render(doc);
		} catch (Exception e) {
			throw new ScriptException(e);
		}
	}

}
