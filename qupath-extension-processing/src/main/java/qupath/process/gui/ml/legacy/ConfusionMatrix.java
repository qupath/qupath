/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.process.gui.ml.legacy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Helper class for creating a confusion matrix.
 * 
 * @author Pete Bankhead
 *
 */
class ConfusionMatrix<T> {

	private List<T> classes = new ArrayList<>();
	private int[][] matrix;
	private int errors = 0;
	private Function<T, String> stringFun;

	/**
	 * Set up confusion matrix considering the specific classes.
	 * 
	 * @param classes a collection of classes, whatever they may be
	 * @param stringFun a function that can convert a class into a string representation
	 */
	ConfusionMatrix(final Collection<T> classes, Function<T, String> stringFun) {
		this.classes.addAll(classes);
		int n = classes.size();
		this.matrix = new int[n][n];
		this.stringFun = stringFun;
	}
	
	ConfusionMatrix(final Collection<T> classes) {
		this(classes, t -> t.toString());
	}
	
	/**
	 * Register a classification, and update the confusion matrix accordingly.
	 * 
	 * @param trueClass
	 * @param assignedClass
	 */
	void registerClassification(final T trueClass, final T assignedClass) {
		int indTrue = classes.indexOf(trueClass);
		int indAssigned = trueClass == assignedClass ? indTrue : classes.indexOf(assignedClass);
		if (indTrue < 0 || indAssigned < 0) {
			errors++;
			return;
		}
		matrix[indTrue][indAssigned]++;
	}

	@Override
	public String toString() {
		int nChars = 5;
		for (T pc : classes) {
			nChars = Math.max(nChars, stringFun.apply(pc).length());
		}
		String fString = "%1$" + nChars + "s";

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < nChars; i++)
			sb.append(" ");
		for (T pc : classes) {
			sb.append("\t").append(String.format(fString, stringFun.apply(pc)));
		}
		sb.append("\n");
		for (int i = 0; i < classes.size(); i++) {
			sb.append(String.format(fString, stringFun.apply(classes.get(i))));
			for (int j = 0; j < classes.size(); j++) {
				sb.append("\t").append(String.format(fString, matrix[i][j]));
			}
			sb.append("\n");
		}
		return sb.toString();
	}

}