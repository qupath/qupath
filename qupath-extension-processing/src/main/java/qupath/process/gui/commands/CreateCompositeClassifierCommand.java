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

package qupath.process.gui.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.controlsfx.control.ListSelectionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.projects.Project;
import qupath.process.gui.commands.ml.ProjectClassifierBindings;

/**
 * Command to create a composite classifier by merging together two or more other classifiers.
 * 
 * @author Pete Bankhead
 */
public class CreateCompositeClassifierCommand implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(CreateCompositeClassifierCommand.class);
	
	private static String title = "Create composite classifier";
	
	private QuPathGUI qupath;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public CreateCompositeClassifierCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		ListSelectionView<ClassifierWrapper<BufferedImage>> view = GuiTools.createListSelectionView();
		try {
			updateAvailableClassifiers(view);
		} catch (IOException e) {
			Dialogs.showErrorNotification(title, e);
		}
		
		String instructions = 
				"Move individual classifiers to the column on the right to be included in the composite classifier.\n" +
				"Note that the order of classifiers in the list determines the order in which they will be applied.";
				
		var labelInstructions = new Label(instructions);
		labelInstructions.setAlignment(Pos.CENTER);
		
		view.setTooltip(new Tooltip("Classifier selection view."));
		
		var paneName = new GridPane();
		var labelName = new Label("Classifier name");
		var tfName = new TextField();
		ProjectClassifierBindings.bindObjectClassifierNameInput(tfName, qupath.projectProperty());
		tfName.setPromptText("Enter composite classifier name");
		labelName.setLabelFor(tfName);
		PaneTools.setMaxWidth(Double.MAX_VALUE, tfName);
		PaneTools.setFillWidth(Boolean.TRUE, tfName);
		PaneTools.setHGrowPriority(Priority.ALWAYS, tfName);
		
		Button btnSave = new Button("Save");
		btnSave.setTooltip(new Tooltip("Save the composite classifier without applying it"));
		btnSave.setOnAction(e -> {
			tryToSave(qupath.getProject(), view.getTargetItems(), tfName.getText());
			tfName.requestFocus();
			btnSave.requestFocus();
		});
		
		PaneTools.addGridRow(paneName, 0, 0, "Enter a name for the composite classifier", labelName, tfName, btnSave);
		paneName.setHgap(5.0);
		
		var pane = new BorderPane(view);
		pane.setTop(labelInstructions);
		pane.setBottom(paneName);
		
//		var scene = new Scene(pane);
		var dialog = new Dialog<ButtonType>();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle(title);
		dialog.getDialogPane().setContent(pane);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
		var btnApply = dialog.getDialogPane().lookupButton(ButtonType.APPLY);
		if (btnApply instanceof Button)
			((Button)btnApply).setText("Save & apply");
		var option = dialog.showAndWait();
		
		// Apply to the current image
		if (option.orElse(ButtonType.CANCEL).equals(ButtonType.APPLY)) {
			try {
				var entry = tryToSave(qupath.getProject(), view.getTargetItems(), tfName.getText());
				var classifier = entry == null ? null : entry.getValue();
				var name = entry == null ? null : entry.getKey();
//				var classifier = tryToBuild(view.getTargetItems());
				if (classifier == null)
					return;
				var imageData = qupath.getImageData();
				if (imageData != null) {
					var pathObjects = classifier.getCompatibleObjects(imageData);
					if (classifier.classifyObjects(imageData, pathObjects, true) > 0)
						imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, pathObjects);
					if (name != null && !name.isBlank()) {
						logger.debug("Adding object classifier '{}' to the workflow", name);
						imageData.getHistoryWorkflow().addStep(
								createObjectClassifierStep(Collections.singletonList(name)));
					} else
						logger.warn("Object classifier has no name, so cannot be written to the workflow - sorry...");
				}
			} catch (Exception e) {
				Dialogs.showErrorMessage(title, e);
			}
		}
	}
	
	static WorkflowStep createObjectClassifierStep(List<String> classifierNames) {
		String names = classifierNames.stream().map(n -> "\"" + n + "\"").collect(Collectors.joining(", "));
		return new DefaultScriptableWorkflowStep("Run object classifier",
						"runObjectClassifier(" + names + ");"
						);
	}

	
	
	private static ObjectClassifier<BufferedImage> tryToBuild(Collection<ClassifierWrapper<BufferedImage>> wrappers) throws IOException {
		var classifiers = new LinkedHashSet<ObjectClassifier<BufferedImage>>();
		for (var wrapper : wrappers) {
			classifiers.add(wrapper.getClassifier());
		}
		if (classifiers.size() < 2) {
			Dialogs.showErrorMessage(title, "At least two different classifiers must be selected to create a composite!");
			return null;
		}
		return ObjectClassifiers.createCompositeClassifier(classifiers);
	}
	
	
	/**
	 * Try to save a composite classifier to a project or file.
	 * @param project
	 * @param wrappers
	 * @param name 
	 * @return an entry containing the classifier and the name used to save it, or null if the classifier was not saved.
	 *         If the name is a file path, it should be properly escaped so that it may be used in a script.
	 */
	private static Map.Entry<String, ObjectClassifier<BufferedImage>> tryToSave(Project<BufferedImage> project, Collection<ClassifierWrapper<BufferedImage>> wrappers, String name) {
		try {
			var composite = tryToBuild(wrappers);
			if (composite == null)
				return null;
			
			name = name == null ? null : GeneralTools.stripInvalidFilenameChars(name);
			if (project != null && name != null && !name.isBlank()) {
				if (project.getObjectClassifiers().contains(name)) {
					if (!Dialogs.showConfirmDialog(title, "Overwrite existing classifier called '" + name + "'?"))
						return null;
				}
				logger.info("Saving classifier to project as {}", name);
				project.getObjectClassifiers().put(name, composite);
				Dialogs.showInfoNotification(title, "Classifier written to project as " + name);
			} else {
				var file = Dialogs.promptToSaveFile(title, null, name, "JSON", ".json");
				if (file != null) {
					logger.info("Writing classifier to {}", file.getAbsolutePath());
					name = file.getAbsolutePath().replaceAll("\\\\", "/");
					ObjectClassifiers.writeClassifier(composite, file.toPath());
					Dialogs.showInfoNotification(title, "Classifier written to " + file.getAbsolutePath());
				} else
					return null;
			}
			return new AbstractMap.SimpleImmutableEntry<>(name, composite);
//			return composite;
		} catch (Exception e) {
			Dialogs.showErrorMessage(title, e);
			return null;
		}
		
	}
	
		
	
	/**
	 * Ensure we have all classifiers for the current project represented.
	 * @param view
	 * @throws IOException
	 */
	private void updateAvailableClassifiers(ListSelectionView<ClassifierWrapper<BufferedImage>> view) throws IOException {
		var project = qupath.getProject();
		if (project == null)
			return;
		var manager = project.getObjectClassifiers();
		var source = view.getSourceItems();
		var target = view.getTargetItems();
		for (String name : manager.getNames()) {
			var wrapper = new ProjectClassifierWrapper<>(project, name);
			if (!source.contains(wrapper) && !target.contains(wrapper))
				source.add(wrapper);
		}
	}
	
	
	static interface ClassifierWrapper<T> {
		
		public ObjectClassifier<T> getClassifier() throws IOException ;
		
	}
	
	static class ProjectClassifierWrapper<T> implements ClassifierWrapper<T> {
		
		private Project<T> project;
		private String name;
		
		public ProjectClassifierWrapper(Project<T> project, String name) {
			this.project = project;
			this.name = name;
		}
		
		@Override
		public ObjectClassifier<T> getClassifier() throws IOException {
			return project.getObjectClassifiers().get(name);
		}
		
		@Override
		public String toString() {
			return name;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((project == null) ? 0 : project.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ProjectClassifierWrapper other = (ProjectClassifierWrapper) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (project == null) {
				if (other.project != null)
					return false;
			} else if (!project.equals(other.project))
				return false;
			return true;
		}
		
	}
	

}