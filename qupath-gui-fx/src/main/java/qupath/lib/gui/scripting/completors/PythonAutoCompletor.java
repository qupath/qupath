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
import java.util.List;

import qupath.lib.scripting.languages.AutoCompletions;
import qupath.lib.scripting.languages.AutoCompletions.Completion;

/**
 * Auto completion support for Python.
 * 
 * @author Pete Bankhead
 *
 */
public class PythonAutoCompletor extends DefaultAutoCompletor {
	
	private static List<String> buildInMethods = Arrays.asList(
			"coerce", "callable", "setattr", "bin", "globals", "delattr", "len",
			"hex", "id", "hasattr", "raw_input", "vars", "zip", "reduce", "oct",
			"iter", "input", "sorted", "execfile", "hash", "repr", "cmp", "range",
			"sum", "isinstance", "min", "pow", "unichr", "__import__", "map", "intern",
			"max", "filter", "divmod", "abs", "issubclass", "open", "dir", "compile",
			"all", "format", "chr", "eval", "next", "getattr", "reload", "ord", "apply",
			"any", "print", "round", "locals", "reversed"
			);
	
	private static List<Completion> PYTHON_AUTOCOMPLETIONS = new ArrayList<>();
	
	static {
		PYTHON_AUTOCOMPLETIONS.addAll(Arrays.asList(
				AutoCompletions.createJavaCompletion(null, "True", "True"),
				AutoCompletions.createJavaCompletion(null, "False", "False"),
				AutoCompletions.createJavaCompletion(null, "None", "None"),
				AutoCompletions.createJavaCompletion(null, "NotImplemented", "NotImplemented")
				));		
		for (var m : buildInMethods) {
			// Don't know if we need args here (or how many), so don't add closing bracket
			PYTHON_AUTOCOMPLETIONS.add(AutoCompletions.createJavaCompletion(null, m + "()", m + "("));
		}		
	}

	/**
	 * Constructor.
	 * @param addQuPathCompletions if true, add standard Java completions for core QuPath classes.
	 */
	public PythonAutoCompletor(boolean addQuPathCompletions) {
		super(addQuPathCompletions);
		addCompletions(PYTHON_AUTOCOMPLETIONS);
	}
	
	

	

}
