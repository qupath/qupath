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

import java.util.Collections;
import java.util.ServiceLoader;

import qupath.lib.gui.scripting.completors.GroovyAutoCompletor;
import qupath.lib.scripting.languages.ExecutableLanguage;

/**
 * Class for the representation of the Groovy programming language in QuPath.
 * <p>
 * This class stores the QuPath implementation of Groovy syntaxing and Groovy auto-completion.
 * @author Melvin Gelbard
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class GroovyLanguage extends DefaultScriptLanguage implements ExecutableLanguage {
	
	/**
	 * Instance of this language. Can't be final because of {@link ServiceLoader}.
	 */
	private static GroovyLanguage INSTANCE;
	
	/**
	 * Constructor for Groovy Language. This constructor should never be 
	 * called. Instead, use the static {@link #getInstance()} method.
	 * <p>
	 * Note: this has to be public for the {@link ServiceLoader} to work.
	 */
	public GroovyLanguage() {
		super("Groovy", Collections.singleton(".groovy"), new GroovyAutoCompletor(true));
		
		if (INSTANCE != null)
			throw new UnsupportedOperationException("Language classes cannot be instantiated more than once!");
		
		// Because of ServiceLoader, have to assign INSTANCE here.
		GroovyLanguage.INSTANCE = this;
	}
	
	/**
	 * Get the static instance of this class.
	 * @return instance
	 */
	public static GroovyLanguage getInstance() {
		return INSTANCE;
	}
	
	@Override
	protected ImportStatementGenerator getImportStatementGenerator() {
		return JAVA_IMPORTER;
	}

}
