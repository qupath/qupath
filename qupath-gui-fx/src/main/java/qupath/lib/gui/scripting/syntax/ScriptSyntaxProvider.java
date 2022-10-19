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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class with static methods to fetch all the available {@linkplain ScriptSyntax ScriptSyntaxes}.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class ScriptSyntaxProvider {
	
	private static final Logger logger = LoggerFactory.getLogger(ScriptSyntaxProvider.class);
		
	public static final ScriptSyntax GROOVY = new GroovySyntax();
	public static final ScriptSyntax XML = new XmlSyntax();
	public static final ScriptSyntax YAML = new YamlSyntax();
	public static final ScriptSyntax JSON = new JsonSyntax();
	public static final ScriptSyntax PLAIN = new PlainSyntax();
	public static final ScriptSyntax PYTHON = new PythonSyntax();


	private static ServiceLoader<ScriptSyntax> serviceLoader = ServiceLoader.load(ScriptSyntax.class);
//
	private static final Collection<ScriptSyntax> availableSyntaxes = loadAvailableScriptSyntaxes();
	

	private static Collection<ScriptSyntax> loadAvailableScriptSyntaxes() {
		var availableSyntaxes = new LinkedHashSet<ScriptSyntax>();
		
		// Load all built-in implementations of ScriptLanguage
		synchronized (serviceLoader) {
			for (var syntax : serviceLoader) {
				if (syntax != null)
					availableSyntaxes.add(syntax);
			}
		}
		
		// Add defaults now - user-installed syntaxes should then take priority
		availableSyntaxes.add(GROOVY);
		availableSyntaxes.add(XML);
		availableSyntaxes.add(YAML);
		availableSyntaxes.add(JSON);
		availableSyntaxes.add(PYTHON);
		availableSyntaxes.add(PLAIN);
		
		logger.debug("Number of script syntax items loaded: {}", availableSyntaxes.size());
		return availableSyntaxes;
	}
	
	
	/**
	 * Get the available script syntaxes.
	 * 
	 * @return
	 */
	public static Collection<ScriptSyntax> getAvailableSyntaxes() {
		return Collections.unmodifiableCollection(availableSyntaxes);
	}
	
	
	/**
	 * Get a script syntax from a specified language name.
	 * If no specific syntax is found, then a general-purpose (plain) syntax will be returned.
	 * @param name
	 * @return
	 */
	public static ScriptSyntax getSyntaxFromName(String name) {
		for (var syntax : availableSyntaxes) {
			for (var supported : syntax.getLanguageNames()) {
				if (name.equalsIgnoreCase(supported)) {
					logger.debug("Returning {} for {}", syntax, name);
					return syntax;				
				}
			}
		}
		logger.debug("No syntax found for {}", name);
		return PLAIN;
	}
	
	
	
}
