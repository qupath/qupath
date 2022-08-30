/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;

/**
 * Class with static methods to fetch all the available {@link ScriptLanguage}s.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class ScriptLanguageProvider {
	
	private static final Logger logger = LoggerFactory.getLogger(ScriptLanguageProvider.class);
	
	private static ServiceLoader<ScriptLanguage> serviceLoader = ServiceLoader.load(ScriptLanguage.class);
	private static final ScriptEngineManager manager = createManager();

	/**
	 * Get all the currently installed {@link ScriptLanguage}s in a list (i.e. custom {@link ScriptEngine}s 
	 * without a built-in {@link ScriptLanguage} implementation won't be included).
	 * @return list of installed languages
	 */
	public static List<ScriptLanguage> getAvailableScriptLanguages() {
		List<ScriptLanguage> languages = new ArrayList<>();
		
		// Load all built-in implementations of ScriptLanguage
		synchronized (serviceLoader) {
			for (ScriptLanguage l : serviceLoader) {
				languages.add(l);
			}
		}
		
		// Load all ScriptEngines on the build path that don't have a built-in ScriptLanguage QuPath implementation
		ScriptEngineManager manager = new ScriptEngineManager(QuPathGUI.getExtensionClassLoader());
		for (ScriptEngineFactory factory : manager.getEngineFactories()) {
			boolean builtIn = false;
				synchronized (serviceLoader) {
					for (ScriptLanguage l : serviceLoader) {
					if (factory.getNames().contains(l.getName().toLowerCase())) {
						builtIn = true;
						break;
					}
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
	
	/**
	 * Get the {@link ScriptLanguage} object corresponding to the specified String. 
	 * If the string cannot be matched, {@link PlainLanguage} is returned.
	 * @param languageString
	 * @return corresponding script language, or {@link PlainLanguage} if no match
	 */
	// TODO: This creates a new instance of custom (ScriptEngine based-) ScriptLanguages each time it's called, is it a problem?
	public static ScriptLanguage fromString(String languageString) {
		return getAvailableScriptLanguages().stream().filter(l -> l.getName().equals(languageString)).findFirst().orElseGet(() -> PlainLanguage.getInstance());
	}
	
	/**
	 * Return the first {@link ScriptLanguage} compatible with the specified extension (can be runnable or not).
	 * @param ext the extension of the script file
	 * @return compatible script language
	 */
	public static ScriptLanguage getLanguageFromExtension(String ext) {
		synchronized (serviceLoader) {
			for (ScriptLanguage l : serviceLoader) {
				for (String tempExt: l.getExtensions()) {
					if (tempExt.equals(ext))
						return l;
				}		
			}
		}
		return null;
	}
	
	private static ScriptEngineManager createManager() {
		Thread.currentThread().setContextClassLoader(QuPathGUI.getExtensionClassLoader());
		ScriptEngineManager manager = new ScriptEngineManager(QuPathGUI.getExtensionClassLoader());
		for (ScriptEngineFactory factory : manager.getEngineFactories()) {
			boolean builtIn = false;
			synchronized (serviceLoader) {
				for (ScriptLanguage l : serviceLoader) {
					if (factory.getNames().contains(l.getName().toLowerCase())) {
						manager.registerEngineName(l.toString(), factory);
	
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
	
						builtIn = true;
						break;
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
		return manager.getEngineByName(languageName);
	}
}
