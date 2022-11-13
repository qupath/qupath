/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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


package qupath.process.gui.commands.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import qupath.lib.analysis.heatmaps.DensityMaps;
import qupath.lib.analysis.heatmaps.DensityMaps.DensityMapBuilder;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.projects.Project;
import qupath.lib.projects.ResourceManager.Manager;
import qupath.process.gui.commands.ml.ProjectClassifierBindings;

/**
 * Small JavaFX pane to support saving object/pixel classifiers and density maps within a project in a standardized way.
 * This is not intended for use by external code.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class SaveResourcePaneBuilder<T> {
	
	private Class<T> cls;
	private String title = "Save resource";
	private String textFieldPrompt = "Enter name";
	private String labelText = "Classifier name";
	private ObservableValue<String> tooltipText;
	private StringProperty savedName = new SimpleStringProperty();
	
	private ObjectExpression<? extends T> resource;
	private ObjectExpression<Project<BufferedImage>> project = QuPathGUI.getInstance() == null ? null : QuPathGUI.getInstance().projectProperty();
			
	/**
	 * Constructor.
	 * @param cls class of the resource to save, e.g. PixelClassifier
	 * @param resource expression containing the results
	 */
	public SaveResourcePaneBuilder(Class<T> cls, ObjectExpression<? extends T> resource) {
		Objects.requireNonNull(resource);
		Objects.requireNonNull(cls);
		this.resource = resource;
		this.cls = cls;
	}
	
	/**
	 * Current project
	 * @param project
	 * @return this builder
	 */
	public SaveResourcePaneBuilder<T> project(ObjectExpression<Project<BufferedImage>> project) {
		this.project = project;
		return this;
	}
	
	/**
	 * Prompt to include beside the resource name text field
	 * @param prompt
	 * @return this builder
	 */
	public SaveResourcePaneBuilder<T> labelText(String prompt) {
		this.labelText = prompt;
		return this;
	}
	
	/**
	 * Prompt to include beside the resource name text field
	 * @param prompt
	 * @return this builder
	 */
	public SaveResourcePaneBuilder<T> textFieldPrompt(String prompt) {
		this.textFieldPrompt = prompt;
		return this;
	}
	
	/**
	 * Text to include in any tooltip.
	 * @param tooltip
	 * @return
	 */
	public SaveResourcePaneBuilder<T> tooltip(ObservableValue<String> tooltip) {
		this.tooltipText = tooltip;
		return this;
	}
	
	/**
	 * Title to display in any dialogs
	 * @param title
	 * @return this builder
	 */
	public SaveResourcePaneBuilder<T> title(String title) {
		this.title = title;
		return this;
	}
	
	/**
	 * Property to store the saved name; this is useful if the name is required externally
	 * @param savedName
	 * @return
	 */
	public SaveResourcePaneBuilder<T> savedName(StringProperty savedName) {
		this.savedName = savedName;
		return this;
	}
	
	/**
	 * Build the pane
	 * @return
	 */
	public Pane build() {
		
		var label = new Label(labelText);
		var defaultName = savedName.get();
		var tfClassifierName = new TextField(defaultName == null ? "" : defaultName);
		tfClassifierName.setPromptText(textFieldPrompt);
		
		// Reset the saved name if the classifier changes
		resource.addListener((v, o, n) -> savedName.set(null));
		
		var btnSave = new Button("Save");
		btnSave.setOnAction(e -> {
			var p = project.get();
			var manager = p == null ? null : getResourceManager(p, cls);
			if (manager == null) {
				
			}
			var name = tryToSave(project.get(), title, cls, resource.get(), tfClassifierName.getText(), false);
			if (name != null) {
				Dialogs.showInfoNotification(title, "Saved as \"" + name + "\"");
				savedName.set(name);
				tfClassifierName.requestFocus();
				btnSave.requestFocus();
			}
		});
		btnSave.disableProperty().bind(
				resource.isNull()
					.or(project.isNull())
					.or(tfClassifierName.textProperty().isEmpty()));
		tfClassifierName.disableProperty().bind(project.isNull());
		label.setLabelFor(tfClassifierName);

		var pane = new GridPane();

		if (tooltipText != null) {
			var tooltip = new Tooltip();
			tooltip.textProperty().bind(tooltipText);
			Tooltip.install(pane, tooltip);
		}
		
		PaneTools.addGridRow(pane, 0, 0, null, label, tfClassifierName, btnSave);
		PaneTools.setToExpandGridPaneWidth(tfClassifierName);
		pane.setHgap(5);
		
		ProjectClassifierBindings.bindPixelClassifierNameInput(tfClassifierName, project);
		
		return pane;
	}
	
	
	
	private static <T> String tryToSave(Project<?> project, String title, Class<T> cls, T resource, String name, boolean overwriteQuietly) {
		if (project == null) {
			Dialogs.showWarningNotification(title, "You need a project to be able to save the pixel classifier");
			return null;
		}
		name = GeneralTools.stripInvalidFilenameChars(name);
		if (name.isBlank()) {
			Dialogs.showErrorMessage(title, "Please enter a valid name!");
			return null;
		}
		try {
			var manager = getResourceManager(project, cls);
			if (manager == null) {
				Dialogs.showWarningNotification(title, "Unable to save resources with type '" + cls + "'");
				return null;
			}
			if (!overwriteQuietly && manager.contains(name)) {
				if (!Dialogs.showYesNoDialog(title, "Overwrite existing '" + name + "'?"))
					return null;
			}
			manager.put(name, resource);
			return name;
		} catch (IOException ex) {
			Dialogs.showErrorMessage(title, ex);
			return null;
		}
	}
	
	
	
	private static <T, S extends T> Manager<S> getResourceManager(Project<?> project, Class<T> cls) {
		if (PixelClassifier.class.equals(cls))
			return (Manager<S>)project.getPixelClassifiers();
		if (ObjectClassifier.class.equals(cls))
			return (Manager<S>)project.getObjectClassifiers();
		if (DensityMapBuilder.class.equals(cls))
			return (Manager<S>)project.getResources(DensityMaps.PROJECT_LOCATION, cls, "json");
		return null;
	}
	
}