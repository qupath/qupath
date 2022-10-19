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


package qupath.lib.gui.scripting.completors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import qupath.lib.scripting.languages.AutoCompletions;
import qupath.lib.scripting.languages.AutoCompletions.Completion;

/**
 * Auto completion support for Groovy.
 * 
 * @author Pete Bankhead
 *
 */
public class GroovyAutoCompletor extends DefaultAutoCompletor {
	
	private static List<Completion> GROOVY_AUTOCOMPLETIONS = new ArrayList<>();
	
	static {
		GROOVY_AUTOCOMPLETIONS.addAll(Arrays.asList(
				AutoCompletions.createJavaCompletion(null, "print(Object)", "print()"),
				AutoCompletions.createJavaCompletion(null, "println(Object)", "println()")
				));		
		
		// Groovy imports many default classes - include a few of the most important for static methods
		var classes = Arrays.asList(
				System.class, Runtime.class, Math.class, Thread.class, Collections.class
				);
		for (var c : classes) {
			addStaticMethods(c, GROOVY_AUTOCOMPLETIONS);
		}		
	}
	
	/**
	 * Constructor.
	 * @param addQuPathCompletions if true, add standard Java completions for core QuPath classes.
	 */
	public GroovyAutoCompletor(boolean addQuPathCompletions) {
		super(addQuPathCompletions);
		addCompletions(GROOVY_AUTOCOMPLETIONS);		
	}

}
