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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.io.GsonTools;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.projects.Project;

/**
 * Command to apply a pre-trained object classifier to an image.
 * 
 * @author Pete Bankhead
 *
 */
public final class ObjectClassifierLoadCommand implements Runnable {
	
	private final static Logger logger = LoggerFactory.getLogger(ObjectClassifierLoadCommand.class);
	private final String title = "Object Classifiers";
	
	private QuPathGUI qupath;
	private Project<BufferedImage> project;
	
	
	/**
	 * Will hold external object classifiers (i.e. not from the project directory)
	 */
	private Map<String, ObjectClassifier<BufferedImage>> externalObjectClassifiers;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public ObjectClassifierLoadCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		
		project = qupath.getProject();
		
		var listClassifiers = new ListView<String>();
		
		 externalObjectClassifiers = new HashMap<>();
		
		listClassifiers.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		var labelPlaceholder = new Label("Object classifiers in the\n" + "current project will appear here");
		labelPlaceholder.setAlignment(Pos.CENTER);
		labelPlaceholder.setTextAlignment(TextAlignment.CENTER);
		listClassifiers.setPlaceholder(labelPlaceholder);
		
		refreshNames(listClassifiers.getItems());

		
		// Provide an option to remove a classifier
		var popup = new ContextMenu();
		var miRemove = new MenuItem("Delete selected");
		popup.getItems().add(miRemove);
		miRemove.disableProperty().bind(listClassifiers.getSelectionModel().selectedItemProperty().isNull());
		listClassifiers.setContextMenu(popup);
		miRemove.setOnAction(e -> {
			var selectedItems = new ArrayList<>(listClassifiers.getSelectionModel().getSelectedItems());
			if (selectedItems.isEmpty() || project == null)
				return;
			try {
				String message = selectedItems.size() == 1 ? "'" + selectedItems.get(0) + "'" : selectedItems.size() + " classifiers";
				if (!Dialogs.showConfirmDialog(title, "Are you sure you want to delete " + message + "?"))
					return;
				for (var selected : selectedItems) {
					if (!project.getObjectClassifiers().getNames().contains(selected)) {
						Dialogs.showErrorMessage(title, "Unable to delete " + selected + " - not found in the current project");
						return;
					}
					project.getObjectClassifiers().remove(selected);
					listClassifiers.getItems().remove(selected);
				}
			} catch (Exception ex) {
				Dialogs.showErrorMessage("Error deleting classifier", ex);
			}
		});
		
		listClassifiers.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2) {
				List<File> files = Dialogs.promptForMultipleFiles(title, null, "QuPath classifier file", "json");
				if (files == null || files.isEmpty())
					return;

				try {
					addClassifierFiles(files);
					List<String> updatedNames = new ArrayList<>();
					updatedNames.addAll(project.getPixelClassifiers().getNames());
					updatedNames.addAll(externalObjectClassifiers.keySet());
				} catch (IOException ex) {
					Dialogs.showErrorMessage(title, ex);
				}
			}
		});
		
		// Support drag & drop for classifiers
		listClassifiers.setOnDragOver(e -> {
			e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
		
		listClassifiers.setOnDragDropped(e -> {
			Dragboard dragboard = e.getDragboard();
			if (dragboard.hasFiles()) {
				logger.trace("File(s) dragged onto classifier listView");
				try {
					var files = dragboard.getFiles()
							.stream()
							.filter(f -> f.isFile() && !f.isHidden())
							.collect(Collectors.toList());
					
					addClassifierFiles(files);
				} catch (Exception ex) {
					String plural = dragboard.getFiles().size() == 1 ? "" : "s";
					Dialogs.showErrorMessage("Error adding classifier" + plural, ex.getLocalizedMessage());
				}
			}
			refreshNames(listClassifiers.getItems());
			e.consume();
		});

		var label = new Label("Choose classifier");
		label.setLabelFor(listClassifiers);
		
//		var enableButtons = qupath.viewerProperty().isNotNull().and(selectedClassifier.isNotNull());
		var btnApplyClassifier = new Button("Apply classifier");
		btnApplyClassifier.textProperty().bind(Bindings.createStringBinding(() -> {
			if (listClassifiers.getSelectionModel().getSelectedItems().size() > 1)
				return "Apply classifiers sequentially";
			return "Apply classifier";
		}, listClassifiers.getSelectionModel().getSelectedItems()));
		btnApplyClassifier.disableProperty().bind(listClassifiers.getSelectionModel().selectedItemProperty().isNull());
		
		btnApplyClassifier.setOnAction(e -> {
			var imageData = qupath.getImageData();
			if (imageData == null) {
				Dialogs.showErrorMessage(title, "No image open!");
				return;
			}
			runClassifier(imageData, project, externalObjectClassifiers, listClassifiers.getSelectionModel().getSelectedItems(), true);
		});
		
//		var pane = new BorderPane();
//		pane.setPadding(new Insets(10.0));
//		pane.setTop(label);
//		pane.setCenter(comboClassifiers);
//		pane.setBottom(btnApplyClassifier);

		var pane = new GridPane();
		pane.setPadding(new Insets(10.0));
		pane.setHgap(5);
		pane.setVgap(10);
		int row = 0;
		PaneTools.setFillWidth(Boolean.TRUE, label, listClassifiers, btnApplyClassifier);
		PaneTools.setVGrowPriority(Priority.ALWAYS, listClassifiers);
		PaneTools.setHGrowPriority(Priority.ALWAYS, label, listClassifiers, btnApplyClassifier);
		PaneTools.setMaxWidth(Double.MAX_VALUE, label, listClassifiers, btnApplyClassifier);
		PaneTools.addGridRow(pane, row++, 0, "Choose object classification model to apply to the current image", label);
		PaneTools.addGridRow(pane, row++, 0, "Drag and drop a file here to add a new classifier", listClassifiers);
		PaneTools.addGridRow(pane, row++, 0, "Apply object classification to all open images", btnApplyClassifier);
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, listClassifiers, btnApplyClassifier);
				
		var stage = new Stage();
		stage.setTitle(title);
		stage.setScene(new Scene(pane));
		stage.initOwner(qupath.getStage());
//		stage.sizeToScene();
		stage.setWidth(300);
		stage.setHeight(400);
		
		stage.focusedProperty().addListener((v, o, n) -> {
			if (n)
				refreshNames(listClassifiers.getItems());
		});
		
//		stage.setResizable(false);
		stage.show();
		
	}
	
	
	private void addClassifierFiles(List<File> files) throws IOException {
		String plural = files.size() > 1 ? "s" : "";
		var response = Dialogs.showYesNoCancelDialog("Copy classifier file" + plural, "Copy classifier" + plural + " to the current project?");
		if (response == DialogButton.CANCEL)
			return;
		
		List<File> fails = new ArrayList<>();
		for (var file: files) {
			try {
				if (!GeneralTools.getExtension(file).get().equals(".json"))
					Dialogs.showErrorNotification(String.format("Could not add '%s'", file.getName()), 
							String.format("Classifier files should be JSON files (.json), not %s", GeneralTools.getExtension(file).get()));
				else {
					var json = Files.newBufferedReader(file.toPath());
					// TODO: Check if classifier is valid before adding it
					ObjectClassifier<BufferedImage> classifier = GsonTools.getInstance().fromJson(json, ObjectClassifier.class);
					
					// Fix duplicate name
					int index = 1;
					String name = GeneralTools.getNameWithoutExtension(file);
					while (project.getObjectClassifiers().contains(name) || externalObjectClassifiers.containsKey(name))
						name = GeneralTools.getNameWithoutExtension(file) + " (" + index++ + ")";
					
					if (response == DialogButton.YES)
						project.getObjectClassifiers().put(name, classifier);
					else
						externalObjectClassifiers.put(name, classifier);
				}				
			}  catch (IOException ex) {
				Dialogs.showErrorNotification(String.format("Could not add %s", file.getName()), ex.getLocalizedMessage());
				fails.add(file);
			}
		}
		
		if (!fails.isEmpty()) {
			String failedClassifiers = fails.stream().map(e -> "- " + e.getName()).collect(Collectors.joining(System.lineSeparator()));
			String pluralize = fails.size() == 1 ? "" : "s";
			Dialogs.showErrorMessage("Error adding classifier" + pluralize, String.format("Could not add the following classifier%s:%s%s", 
					pluralize,
					System.lineSeparator(), 
					failedClassifiers)
			);
		}
		
		int nSuccess = files.size() - fails.size();
		String plural2 = nSuccess > 1 ? "s" : "";
		if (nSuccess > 0)
			Dialogs.showInfoNotification("Classifier" + plural2 + " added successfully", String.format("%d classifier" + plural2 + " added", nSuccess));
	}

	/**
	 * Refresh names from the current project.
	 * @param availableClassifiers list to which names should be added
	 */
	private void refreshNames(ObservableList<String> availableClassifiers) {
		if (project == null) {
			availableClassifiers.clear();
			return;
		}
		
		try {
			List<String> names = new ArrayList<>();
			names.addAll(project.getObjectClassifiers().getNames());
			names.addAll(externalObjectClassifiers.keySet());
			availableClassifiers.setAll(names);
		} catch (IOException e) {
			Dialogs.showErrorMessage(title, e.getLocalizedMessage());
			return;
		}
	}
	
	
	/**
	 * Run a classifier (or composite classifier), and optionally log the fact that it was run in the workflow.
	 * 
	 * @param imageData
	 * @param project
	 * @param externalClassifiers
	 * @param selectedClassifiersNames
	 * @param logWorkflow
	 */
	private static void runClassifier(ImageData<BufferedImage> imageData, Project<BufferedImage> project, Map<String, ObjectClassifier<BufferedImage>> externalClassifiers, List<String> selectedClassifiersNames, boolean logWorkflow) {
		ObjectClassifier<BufferedImage> classifier;
		try {
			classifier = getClassifier(project, externalClassifiers, selectedClassifiersNames);
		} catch (IOException ex) {
			Dialogs.showErrorMessage("Object classifier", ex);
			return;
		}
		// Perform sanity check for missing features
		logger.info("Running classifier: {}", selectedClassifiersNames);
		var pathObjects = classifier.getCompatibleObjects(imageData);
		var missingCounts = classifier.getMissingFeatures(imageData, pathObjects);
		if (!missingCounts.isEmpty()) {
			var sb = new StringBuilder("There are missing features!");
			int n = pathObjects.size();
			for (var entry : missingCounts.entrySet()) {
				double percent = entry.getValue() * 100.0/n;
				sb.append("\t").append(entry.getKey()).append(": ")
					.append(n).append(" objects (")
					.append(GeneralTools.formatNumber(percent, 2)).append("%)")
					.append("\n");
			}
			if (missingCounts.size() == 1)
				Dialogs.showWarningNotification("Missing features", "Missing feature: " + missingCounts.keySet().iterator().next() + "\n\nSee the log for more details.");
			else
				Dialogs.showWarningNotification("Missing features", missingCounts.size() + " missing features!\n\nSee the log for more details.");
			logger.warn(sb.toString());
		} else
			logger.debug("No missing features found");
		
		if (classifier.classifyObjects(imageData, pathObjects, true) > 0) {
			imageData.getHierarchy().fireObjectClassificationsChangedEvent(classifier, pathObjects);
			if (logWorkflow)
				imageData.getHistoryWorkflow().addStep(createObjectClassifierStep(selectedClassifiersNames));
		}
	}

	static WorkflowStep createObjectClassifierStep(String... classifierNames) {
		return createObjectClassifierStep(Arrays.asList(classifierNames));
	}
	
	static WorkflowStep createObjectClassifierStep(List<String> classifierNames) {
		String names = classifierNames.stream().map(n -> "\"" + n + "\"").collect(Collectors.joining(", "));
		return new DefaultScriptableWorkflowStep("Run object classifier",
						"runObjectClassifier(" + names + ");"
						);
	}
	
	
	
	/**
	 * Load a single or composite classifier. The returned classifier can be fetched from either 
	 * the project directory or from an external source (gathered in {@code externalClassifiers}).
	 * @param project
	 * @param externalClassifiers
	 * @param names
	 * @return
	 * @throws IOException
	 */
	private static ObjectClassifier<BufferedImage> getClassifier(Project<BufferedImage> project, Map<String, ObjectClassifier<BufferedImage>> externalClassifiers, List<String> names) throws IOException {
		if (project == null || names.isEmpty())
			return null;
		
		List<ObjectClassifier<BufferedImage>> classifiers = new ArrayList<>();
		for (var s : names) {
			if (project.getObjectClassifiers().contains(s))
				classifiers.add(project.getObjectClassifiers().get(s));
			else
				classifiers.add(externalClassifiers.get(s));
		}
		
		if (names.size() == 1)
			return classifiers.get(0);
		return ObjectClassifiers.createCompositeClassifier(classifiers);
	}
}