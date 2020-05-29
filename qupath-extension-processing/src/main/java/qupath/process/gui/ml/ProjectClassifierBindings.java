/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.process.gui.ml;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectExpression;
import javafx.scene.control.TextField;
import qupath.lib.common.GeneralTools;
import qupath.lib.projects.Project;

/**
 * Class to help with formatting text fields for entering pixel and object classifier names.
 * 
 * @author Pete Bankhead
 */
public class ProjectClassifierBindings {
	
	private final static Logger logger = LoggerFactory.getLogger(ProjectClassifierBindings.class);
	
	/**
	 * Set styling for a text field to use pixel classifier names for the current project.
	 * @param textField
	 * @param project
	 */
	public static void bindPixelClassifierNameInput(TextField textField, ObjectExpression<Project<BufferedImage>> project) {
		var existingNames = new TreeSet<String>();
		updatePixelClassifierNames(project.get(), existingNames);
		// Could use autocomplete, but it can be quite annoying whenever tabs interfere by setting the name
//		TextFields.bindAutoCompletion(textField, v -> existingNames);
		bindStyle(textField, existingNames);
		textField.focusedProperty().addListener((v, o, n) -> {
			updatePixelClassifierNames(project.get(), existingNames);
		});
	}

	
	private static void updatePixelClassifierNames(Project<?> project, Collection<String> names) {
		if (project == null) {
			names.clear();
			return;
		}
		try {
			var currentNames = project.getPixelClassifiers().getNames();
			names.clear();
			names.addAll(currentNames.stream().map(n -> n.toLowerCase()).collect(Collectors.toList()));
		} catch (Exception e) {
			logger.debug("Error updating pixel classifier names: {}", e.getLocalizedMessage());
			names.clear();
		}
	}
	
	
	private static void bindStyle(TextField tf, Collection<String> existingNames) {
		tf.styleProperty().bind(Bindings.createStringBinding(
				() -> getStyle(tf.getText(), existingNames),
				tf.textProperty(),
				tf.focusedProperty()));
	}
	
	
	private static String getStyle(String name, Collection<String> existingNames) {
		if (name == null)
			return null;
		name = GeneralTools.stripInvalidFilenameChars(name);
		if (existingNames.contains(name.toLowerCase()))
			return "-fx-font-style: italic;";
		else
			return null;
	}

	
	/**
	 * Set styling for a text field to use object classifier names for the current project.
	 * @param textField
	 * @param project
	 */
	public static void bindObjectClassifierNameInput(TextField textField, ObjectExpression<Project<BufferedImage>> project) {
		var existingNames = new TreeSet<String>();
		updateObjectClassifierNames(project.get(), existingNames);
//		TextFields.bindAutoCompletion(textField, v -> existingNames);
		bindStyle(textField, existingNames);
		textField.focusedProperty().addListener((v, o, n) -> {
			updateObjectClassifierNames(project.get(), existingNames);
		});
	}
	
	private static void updateObjectClassifierNames(Project<?> project, Collection<String> names) {
		if (project == null) {
			names.clear();
			return;
		}
		try {
			var currentNames = project.getObjectClassifiers().getNames();
			names.clear();
			names.addAll(currentNames.stream().map(n -> n.toLowerCase()).collect(Collectors.toList()));
		} catch (Exception e) {
			logger.debug("Error updating object classifier names: {}", e.getLocalizedMessage());
			names.clear();
		}
	}

}