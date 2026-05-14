/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022, 2024 QuPath developers, The University of Edinburgh
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.scripting.languages.ScriptLanguage;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Class with static methods to fetch all the available {@linkplain ScriptLanguage ScriptLanguages}.
 * 
 * @author Melvin Gelbard
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class ScriptLanguageProvider {
	
	private static final Logger logger = LoggerFactory.getLogger(ScriptLanguageProvider.class);
	
	private static ScriptEngineManager manager;
	
	private static Collection<ScriptLanguage> availableLanguages;

	private static Collection<ScriptLanguage> getAvailableScriptLanguages() {
		if (availableLanguages == null) {
			availableLanguages = loadAvailableScriptLanguages();
		}
		return availableLanguages;
	}

	/**
	 * Get all the currently installed {@link ScriptLanguage}s in a list (i.e. custom {@link ScriptEngine}s 
	 * without a built-in {@link ScriptLanguage} implementation won't be included).
	 * @return list of installed languages
	 */
	private static Collection<ScriptLanguage> loadAvailableScriptLanguages() {

        Set<ScriptLanguage> languages = new LinkedHashSet<>(getDefaultLanguages());
		
		// Load all ScriptEngines on the build path that don't have a built-in ScriptLanguage QuPath implementation
		ScriptEngineManager manager = new ScriptEngineManager(getExtensionClassLoader());
		for (ScriptEngineFactory factory : manager.getEngineFactories()) {
			boolean builtIn = false;
			for (ScriptLanguage l : languages) {
				if (factory.getNames().contains(l.getName().toLowerCase())) {
					builtIn = true;
					break;
				}
			}
			if (!builtIn) {
				try {
					ScriptLanguage l = new DefaultScriptLanguage(factory);
					languages.add(l);
				} catch (Exception ex) {
					logger.warn("Could not integrate script engine [{}] in the Script Editor: {}", factory.getEngineName(), ex.getLocalizedMessage());
				}
			}
		}
		
		return languages;
	}

	private static List<ScriptLanguage> getDefaultLanguages() {
		return List.of(
				GroovyLanguage.getInstance(),
				MarkdownLanguage.getInstance(),

				CssLanguage.getInstance(),
				JsonLanguage.getInstance(),
				ImageJMacroLanguage.getInstance(),
				PropertiesLanguage.getInstance(),
				XmlLanguage.getInstance(),
				YamlLanguage.getInstance(),
				PlainLanguage.getInstance()
		);
	}
	
	
	private static ClassLoader getExtensionClassLoader() {
		return QuPathGUI.getExtensionCatalogManager().getExtensionClassLoader();
	}
	
	
	/**
	 * Get the available script languages.
	 * 
	 * @return
	 */
	public static Collection<ScriptLanguage> getAvailableLanguages() {
		return List.copyOf(getAvailableScriptLanguages());
	}
	
	
	/**
	 * Given a file name, determine the associated language - or null if no suitable (supported) language can be found.
	 * 
	 * @param name
	 * @return
	 */
	public static ScriptLanguage getLanguageFromName(String name) {
		for (ScriptLanguage l : getAvailableScriptLanguages()) {
			for (String possibleExt: l.getExtensions()) {
				if (name.toLowerCase().endsWith(possibleExt))
					return l;
			}
		}
		return PlainLanguage.getInstance();
	}
	
	
	
	/**
	 * Get the {@link ScriptLanguage} object corresponding to the specified String. 
	 * If the string cannot be matched, {@link PlainLanguage} is returned.
	 * @param languageString
	 * @return corresponding script language, or {@link PlainLanguage} if no match
	 */
	public static ScriptLanguage fromString(String languageString) {
		return getAvailableScriptLanguages().stream()
				.filter(l -> l.getName().equalsIgnoreCase(languageString))
				.findFirst()
				.orElseGet(PlainLanguage::getInstance);
	}
	
	/**
	 * Return the first {@link ScriptLanguage} compatible with the specified extension (can be runnable or not).
	 * @param ext the extension of the script file
	 * @return compatible script language
	 */
	public static ScriptLanguage getLanguageFromExtension(String ext) {
		for (ScriptLanguage l : getAvailableScriptLanguages()) {
			for (String tempExt: l.getExtensions()) {
				if (tempExt.equals(ext))
					return l;
			}
		}
		return null;
	}

	/**
	 * Install a new script language.
	 * @param language the language to install
	 * @return true if the language was installed, false otherwise (e.g. it was already installed)
	 */
	public static boolean installLanguage(ScriptLanguage language) {
		getAvailableLanguages();
		return availableLanguages.add(language);
	}
	
	private static ScriptEngineManager createManager() {
		Thread.currentThread().setContextClassLoader(getExtensionClassLoader());
		ScriptEngineManager manager = new ScriptEngineManager(getExtensionClassLoader());
		for (ScriptEngineFactory factory : manager.getEngineFactories()) {
			boolean builtIn = false;
			for (ScriptLanguage l : getAvailableScriptLanguages()) {
				if (factory.getNames().contains(l.getName().toLowerCase())) {

					try {
						logger.trace("-------------------------------");
						logger.trace(factory.getLanguageName());
						logger.trace(factory.getLanguageVersion());
						logger.trace(factory.getEngineName());
						logger.trace(factory.getEngineVersion());
						logger.trace("Names: {}", factory.getNames());
						logger.trace("MIME types: {}", factory.getMimeTypes().toString());
						logger.trace("Extensions: {}", factory.getExtensions().toString());

						logger.trace(factory.getMethodCallSyntax("QuPath", "runPlugin", "imageData", "\"{ key : value }\""));
						logger.trace(factory.getOutputStatement("This is my output"));

						manager.registerEngineName(l.toString(), factory);

						builtIn = true;
						break;
					} catch (Exception e) {
						logger.warn("Exception registering script language: {}", e.getMessage(), e);
					}

				}
			}
			
			// Script Engine without built-in implementation
			if (!builtIn)
				manager.registerEngineName(factory.getEngineName(), factory);
		}
		// TODO: Should we sort them alphabetically?
		//		Collections.sort(serviceLoader, (l1, l2) -> Comparator.comparing(String::toString).compare(l1.getName(), l2.getName())); 
		return manager;
	}

	/**
	 * Get the {@link ScriptEngine} based on its name.
	 * @param languageName
	 * @return script engine
	 */
	public static ScriptEngine getEngineByName(String languageName) {
		if (manager == null) {
			manager = createManager();
		}
		return manager.getEngineByName(languageName);
	}
}
