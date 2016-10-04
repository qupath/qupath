/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.panels.classify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import qupath.lib.objects.classes.PathClass;

/**
 * Helper class for creating a confusion matrix.
 * 
 * @author Pete Bankhead
 *
 */
class ConfusionMatrix {

	private List<PathClass> pathClasses = new ArrayList<>();
	private int[][] matrix;
	private int errors = 0;

	/**
	 * Set up confusion matrix considering the specific classes.
	 * 
	 * @param pathClasses
	 */
	ConfusionMatrix(final Collection<PathClass> pathClasses) {
		this.pathClasses.addAll(pathClasses);
		int n = pathClasses.size();
		matrix = new int[n][n];
	}
	
	/**
	 * Register a classification, and update the confusion matrix accordingly.
	 * 
	 * @param trueClass
	 * @param assignedClass
	 */
	void registerClassification(final PathClass trueClass, final PathClass assignedClass) {
		int indTrue = pathClasses.indexOf(trueClass);
		int indAssigned = trueClass == assignedClass ? indTrue : pathClasses.indexOf(assignedClass);
		if (indTrue < 0 || indAssigned < 0) {
			errors++;
			return;
		}
		matrix[indTrue][indAssigned]++;
	}

	@Override
	public String toString() {
		int nChars = 5;
		for (PathClass pc : pathClasses) {
			nChars = Math.max(nChars, pc.getName().length());
		}
		String fString = "%1$" + nChars + "s";

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < nChars; i++)
			sb.append(" ");
		for (PathClass pc : pathClasses) {
			sb.append("\t").append(String.format(fString, pc.getName()));
		}
		sb.append("\n");
		for (int i = 0; i < pathClasses.size(); i++) {
			sb.append(String.format(fString, pathClasses.get(i).getName()));
			for (int j = 0; j < pathClasses.size(); j++) {

				sb.append("\t").append(String.format(fString, matrix[i][j]));

				//					sb.append("\t").append(matrix[i][j]);
			}
			sb.append("\n");
		}
		return sb.toString();
	}

}