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

import java.util.Collection;
import java.util.Collections;

import qupath.lib.gui.scripting.completors.GroovyAutoCompletor;
import qupath.lib.scripting.languages.AutoCompletions;
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

	private static final GroovyLanguage INSTANCE_WITH_COMPLETIONS = new GroovyLanguage();
	private static final GroovyLanguage INSTANCE_NO_COMPLETIONS = new GroovyLanguage(Collections.emptyList());

	private GroovyLanguage(Collection<? extends AutoCompletions.Completion> completions) {
		super("Groovy", Collections.singleton(".groovy"), new GroovyAutoCompletor(completions));
	}

	private GroovyLanguage() {
		super("Groovy", Collections.singleton(".groovy"), new GroovyAutoCompletor());
	}
	
	/**
	 * Get the static instance of this class, using the default code completions.
	 * @return instance
	 */
	public static GroovyLanguage getInstance() {
		return INSTANCE_WITH_COMPLETIONS;
	}

	/**
	 * Get an instead of this class that uses the specified code completions, rather than the defaults.
	 * @return instance
	 */
	public static GroovyLanguage getInstanceWithCompletions(Collection<? extends AutoCompletions.Completion> completions) {
		if (completions == null || completions.isEmpty())
			return INSTANCE_NO_COMPLETIONS;
		return new GroovyLanguage(completions);
	}
	
	@Override
	protected ImportStatementGenerator getImportStatementGenerator() {
		return JAVA_IMPORTER;
	}

}
