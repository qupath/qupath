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

import java.awt.image.BufferedImage;
import java.util.Collection;

import javax.script.ScriptContext;
import javax.script.ScriptException;

import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;

/**
 * Functional interface for {@link ScriptLanguage}s that are runnable (e.g. Groovy, JavaScript).
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public interface RunnableLanguage {
	
	/**
	 * Execute the given script String.
	 * @param script 						the script to run
	 * @param project 						the current project (can be null if none)
	 * @param imageData 				the current image data (can be null if none)
	 * @param defaultImports 			a collection of the classes to import
	 * @param defaultStaticImports 		a collection of classes to import as static classes
	 * @param context 					the script context to run this script
	 * @return 
	 * @throws ScriptException 
	 */
	Object executeScript(final String script, final Project<BufferedImage> project, final ImageData<BufferedImage> imageData, final Collection<Class<?>> defaultImports, final Collection<Class<?>> defaultStaticImports, final ScriptContext context) throws ScriptException;

	/**
	 * Get the import statements as a String, to add at the beginning of the executed script.
	 * @param classes a collection of the classes to import 
	 * @return import string
	 */
	String getImportStatements(Collection<Class<?>> classes);

	/**
	 * Get the static import statements as a String, to add at the beginning of the executed script.
	 * @param classes	a collection of classes to import as static classes
	 * @return import string
	 */
	String getStaticImportStatments(Collection<Class<?>> classes);
}
