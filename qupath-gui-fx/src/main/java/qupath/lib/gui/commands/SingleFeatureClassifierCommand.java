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

package qupath.lib.gui.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;

/**
 * Command to classify detection objects based on thresholding a single measurement.
 * 
 * Note: This command is not scriptable!
 * 
 * TODO: Make a scriptable version of a single feature classifier...
 * 
 * @author Pete Bankhead
 *
 */
public class SingleFeatureClassifierCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(SingleFeatureClassifierCommand.class);
	
	private static String commandName = "Single feature classifier";
	
	private QuPathGUI qupath;
	private Class<? extends PathObject> cls;
	
	private BorderPane pane;
	
	private InputPane paneInput;
	private MeasurementPane paneMeasurement;
	private OutputPane paneOutput;

	private TextArea textArea;
	
	private ObservableList<PathClass> availableClasses;
	private ObservableList<String> availableFeatures;

	
	public SingleFeatureClassifierCommand(final QuPathGUI qupath, final Class<? extends PathObject> cls) {
		this.qupath = qupath;
		this.cls = cls;
		
		this.availableClasses = qupath.getAvailablePathClasses();
		this.availableFeatures = FXCollections.observableArrayList();
	}
	
	
	
	void createScene() {
		
		
		// INPUT CLASSES PANE
		// Input classes - classification will only be applied to objects of this class
		paneInput = new InputPane(availableClasses);
		
		// MEASUREMENT PANE
		// Selected feature to use
		paneMeasurement = new MeasurementPane(availableFeatures);
		
		// OUTPUT PANE
		// Target class - output will be classifications of this class where PathObject's measurement > this value
		paneOutput = new OutputPane(availableClasses);
		
		
		
//		TilePane paneTiles = new TilePane();
//		paneTiles.setPrefColumns(3);
//		paneTiles.getChildren().addAll(
//				paneInput.getPane(),
//				paneMeasurement.getPane(),
//				paneOutput.getPane()
//				);
		
		GridPane paneTiles = new GridPane();
		paneTiles.add(paneInput.getPane(), 0, 0);
		paneTiles.add(paneMeasurement.getPane(), 1, 0);
		paneTiles.add(paneOutput.getPane(), 2, 0);
		
//		paneMeasurement.prefHeightProperty().bind(paneTiles.heightProperty());
//		paneOutput.prefHeightProperty().bind(paneTiles.heightProperty());
		paneTiles.setHgap(10);
		
		textArea = new TextArea();
		textArea.setPrefRowCount(4);
		
		pane = new BorderPane();
		TitledPane paneCreate = new TitledPane("Create classifier", paneTiles);
		paneCreate.setCollapsible(false);
		pane.setCenter(paneCreate);
		TitledPane paneSummary = new TitledPane("Summary", textArea);
		paneSummary.setCollapsible(false);
		pane.setBottom(paneSummary);
		
		paneInput.getSelectedInputClasses().addListener((Observable o) -> updateSummary());
		paneMeasurement.selectedMeasurementProperty().addListener((Observable o) -> updateSummary());
		paneMeasurement.thresholdValueProperty().addListener((Observable o) -> updateSummary());
		paneOutput.getPathClassAboveProperty().addListener((Observable o) -> updateSummary());
		paneOutput.getPathClassEqualsProperty().addListener((Observable o) -> updateSummary());
		paneOutput.getPathClassBelowProperty().addListener((Observable o) -> updateSummary());
		
		
		
//		Button btnApply = new Button("Apply");
//		pane.setBottom(btnApply);
//		btnApply.setOnAction(event -> {
//			applyClassification();
//		});
	}
	
	
	
	
	void showDialog() {
		
		ImageData<?> imageData = qupath.getImageData();
		if (imageData == null) {
			DisplayHelpers.showErrorMessage(commandName, "No image available!");
			return;
		}
		Set<String> features = PathClassifierTools.getAvailableFeatures(imageData.getHierarchy().getObjects(null, cls));
		if (features.isEmpty()) {
			DisplayHelpers.showErrorMessage(commandName, "No features available!");
			return;
		}
		
		paneMeasurement.updateAvailableFeatures(features);


		// Create a map of all the object classifications upon opening the dialog, so these can be restored
		Map<PathClass, List<PathObject>> mapReset = new HashMap<>();
		for (PathObject temp : imageData.getHierarchy().getObjects(null, cls)) {
			PathClass key = temp.getPathClass();
			if (key == null)
				key = PathClassFactory.getPathClassUnclassified();
			List<PathObject> list = mapReset.get(key);
			if (list == null) {
				list = new ArrayList<>();
				mapReset.put(key, list);
			}
			list.add(temp);
		}

		// Reset text area
		textArea.setText("");
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle(commandName);
		dialog.initModality(Modality.NONE);
		dialog.getDialogPane().setContent(pane);
		
		ButtonType buttonTypeTest = new ButtonType("Test", ButtonData.APPLY);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK, buttonTypeTest);
		
		final Button buttonTest = (Button)dialog.getDialogPane().lookupButton(buttonTypeTest);
		buttonTest.addEventFilter(ActionEvent.ACTION, event -> {
			applyClassification(imageData.getHierarchy(), mapReset);
			event.consume();
		});
		
		Optional<ButtonType> result = dialog.showAndWait();
		if (result.isPresent() && result.get() == ButtonType.OK)
			applyClassification(imageData.getHierarchy(), mapReset);
		else {
			// Restore the classifications to their former state
			List<PathObject> changedObjects = new ArrayList<>();
			for (Entry<PathClass, List<PathObject>> entry : mapReset.entrySet()) {
				PathClass pathClass = entry.getKey();
				if (pathClass == PathClassFactory.getPathClassUnclassified())
					pathClass = null;
				for (PathObject temp : entry.getValue())
					temp.setPathClass(pathClass);
				changedObjects.addAll(entry.getValue());
			}
			imageData.getHierarchy().fireObjectClassificationsChangedEvent(this, changedObjects);
		}
	}
	
	
	
	
	void applyClassification(final PathObjectHierarchy hierarchy, final Map<PathClass, List<PathObject>> map) {
		if (map == null || map.isEmpty()) {
			textArea.setText("No objects available!");
			return;
		}
		
		Set<PathClass> inputClasses = new HashSet<>(paneInput.getSelectedInputClasses());
		
		String measurementName = paneMeasurement.getSelectedMeasurement();
		if (measurementName == null) {
			String s = "No measurement selected!";
			textArea.setText(s);
			logger.error(s);
			return;
		}
		double threshold = paneMeasurement.getThresholdValue();
		if (Double.isNaN(threshold)) {
			String s = "No threshold set!";
			textArea.setText(s);
			logger.error(s);
			return;
		}
		
		
		PathClass classAbove = paneOutput.getPathClassAbove();
		PathClass classEquals = paneOutput.getPathClassEquals();
		PathClass classBelow = paneOutput.getPathClassBelow();
		

		String s = getSummary();
		textArea.setText(s);
		logger.info(s);
		
		
		// Reset everything first
		for (Entry<PathClass, List<PathObject>> entry : map.entrySet()) {
			PathClass pathClass = entry.getKey();
			if (pathClass == PathClassFactory.getPathClassUnclassified())
				pathClass = null;
			for (PathObject temp : entry.getValue())
				temp.setPathClass(pathClass);
		}
		
		
		// Apply new classification
		List<PathObject> changedObjects = new ArrayList<>();
		for (PathClass key : inputClasses) {
			if (!map.containsKey(key)) {
				logger.info("No objects with class {} - skipping...", key);
				continue;
			}
			for (PathObject pathObject : map.get(key)) {
				double value = pathObject.getMeasurementList().getMeasurementValue(measurementName);					
				PathClass newClass = null;
				if (value > threshold) {
					newClass = classAbove;
				}
				else if (value < threshold) {
					newClass = classBelow;
				}
				else if (value == threshold) {
					newClass = classEquals;
				}
				if (newClass != null && newClass != pathObject.getPathClass()) {
					// Deal with special case of 'Unclassified' class
					if (newClass.getName() == null)
						pathObject.setPathClass(null);
					else
						pathObject.setPathClass(newClass);
					changedObjects.add(pathObject);
				}
			}
		}
		if (!changedObjects.isEmpty())
			hierarchy.fireObjectClassificationsChangedEvent(this, changedObjects);
	}
	
	
	
	
//	void updateHistogram(final Collection<PathClass> inputClasses, final Collection<PathObject> pathObjects, final String measurementName) {
//		
//		// Extract all the relevant measurements
//		double[] measurements =
//			pathObjects.parallelStream()
//				.filter(inputClassPredicate(inputClasses))
////				.filter(p -> !(inputClasses.isEmpty() || inputClasses.contains(p.getPathClass()) || (containsUnclassified && p.getPathClass() == null)))
//				.mapToDouble(p -> p.getMeasurementList().getMeasurementValue(measurementName))
//				.toArray();
//		
////		Histogram histogram = new Histogram(measurements, 100);
//		
//	}
	
	
	/**
	 * Obtain predicate for selecting PathClasses according to the current input
	 * @param inputClasses
	 * @return
	 */
	Predicate<PathObject> inputClassPredicate(final Collection<PathClass> inputClasses) {
		boolean containsUnclassified = inputClasses.contains(PathClassFactory.getPathClassUnclassified());
		return p -> !(inputClasses.isEmpty() || inputClasses.contains(p.getPathClass()) || (containsUnclassified && p.getPathClass() == null));
	}
	
	
	void updateSummary() {
		if (textArea != null)
			textArea.setText(getSummary());
	}
	

	String getSummary() {
		String featureName = paneMeasurement.getSelectedMeasurement();
		if (featureName == null)
			return "Please select a feature name";

		double featureValue = paneMeasurement.getThresholdValue();
		if (Double.isNaN(featureValue))
			return "Please enter a feature value as a threshold";

		Collection<PathClass> inputClasses = paneInput.getSelectedInputClasses();
		String inputClassString;
		if (inputClasses.isEmpty())
			inputClassString = "with ANY classification";
		else if (inputClasses.size() == 1)
			inputClassString = "with \"" + inputClasses.iterator().next() + "\" classification";
		else
			inputClassString = "with classification in [" + GeneralTools.arrayToString(inputClasses.toArray(), ", ") + "]";
		
		PathClass classAbove = paneOutput.getPathClassAbove();
		PathClass classEquals = paneOutput.getPathClassEquals();
		PathClass classBelow = paneOutput.getPathClassBelow();
		
		if (classAbove == null && classEquals == null && classBelow == null)
			return "Leave all objects unchanged (no output classes set)";
		
		String sAbove = getClassifyAsString(classAbove);
		String sEquals = getClassifyAsString(classEquals);
		String sBelow = getClassifyAsString(classBelow);
		
		String s = String.format(
				"For all detection objects %s:\n"
				+ "\t%s if %s > %.2f,\n"
				+ "\t%s if %s = %.2f,\n"
				+ "\t%s if %s < %.2f",
				inputClassString,
				sAbove, featureName, featureValue,
				sEquals, featureName, featureValue,
				sBelow, featureName, featureValue
				);
		return s;
	}
	
	
	static String getClassifyAsString(final PathClass pathClass) {
		if (pathClass == null)
			return "leave unchanged";
		else if (pathClass.getName() == null || PathClassFactory.getPathClassUnclassified() == pathClass)
			return "remove classification";
		else
			return "classify as \"" + pathClass.getName() + "\"";
	}
	

	@Override
	public void run() {
//		if (pane == null)
			createScene();
		showDialog();
	}
	
	
	
	
	static class InputPane {
		
		private ListView<PathClass> listInputClasses = new ListView<>();
		private TitledPane pane;
		private ObservableList<PathClass> selectedItemList = FXCollections.observableArrayList();
		
		InputPane(final ObservableList<PathClass> availableClasses) {
			// Input classes - classification will only be applied to objects of this class
			listInputClasses.setItems(availableClasses);
			listInputClasses.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
			listInputClasses.setPrefHeight(200);
			pane = new TitledPane("Input", listInputClasses);
			pane.setCollapsible(false);
			
			listInputClasses.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
				// Not sure why, but this needs to be deferred to later...
				Platform.runLater(() -> selectedItemList.setAll(listInputClasses.getSelectionModel().getSelectedItems()));
			});
			
			Tooltip tooltip = new Tooltip("Select input classifications - only objects with these classes will be reclassified");
			pane.setTooltip(tooltip);
			listInputClasses.setTooltip(tooltip);
		}
		
		public TitledPane getPane() {
			return pane;
		}
		
		public ObservableList<PathClass> getSelectedInputClasses() {
			return selectedItemList;
		}
		
	}
	
	
	static class MeasurementPane {
		
		private ComboBox<String> comboFeatures = new ComboBox<>();
		private TextField tfThreshold = new TextField();
		private TitledPane pane;
		private DoubleProperty threshold = new SimpleDoubleProperty();
		
//		private DoubleBinding threshold;
		
		MeasurementPane(final ObservableList<String> features) {			
			comboFeatures.setItems(features);
			
			GridPane paneMeasurements = new GridPane();
			paneMeasurements.add(comboFeatures, 0, 0, 2, 1);
			Label labelThreshold = new Label("Threshold");
			labelThreshold.setLabelFor(tfThreshold);
			paneMeasurements.add(labelThreshold, 0, 1, 1, 1);
			paneMeasurements.add(tfThreshold, 1, 1, 1, 1);
			
			paneMeasurements.setHgap(5);
			paneMeasurements.setVgap(5);
			
			pane = new TitledPane("Measurement", paneMeasurements);
			pane.setCollapsible(false);
			
			pane.setTooltip(new Tooltip("Select measurement & threshold used to reclassify objects"));
			
			tfThreshold.textProperty().addListener((v, o, n) -> {
				try {
					threshold.set(Double.parseDouble(n));
				} catch (Exception e) {
					threshold.set(Double.NaN);
				}
			});
		}
		
		public TitledPane getPane() {
			return pane;
		}
		
		public void updateAvailableFeatures(final Collection<String> features) {
			// Update the features, trying to maintain the same selection if possible
			String selected = getSelectedMeasurement();
			if (!comboFeatures.getItems().equals(features)) {
				comboFeatures.getItems().setAll(features);
				if (features.contains(selected))
					comboFeatures.getSelectionModel().select(selected);
			}
		}
		
		public String getSelectedMeasurement() {
			return selectedMeasurementProperty().get();
		}
		
		public double getThresholdValue() {
			return threshold.get();
		}
		
		
		public ReadOnlyObjectProperty<String> selectedMeasurementProperty() {
			return comboFeatures.getSelectionModel().selectedItemProperty();
		}
		
		public ReadOnlyDoubleProperty thresholdValueProperty() {
			return threshold;
		}
		
		
	}
	
	
	
	static class OutputPane {
		
		private TitledPane pane;
		
		private ComboBox<PathClass> comboTargetAboveThreshold = new ComboBox<>();
		private ComboBox<PathClass> comboTargetEqualsThreshold = new ComboBox<>();
		private ComboBox<PathClass> comboTargetBelowThreshold = new ComboBox<>();
		
		
		OutputPane(final ObservableList<PathClass> availableClasses) {
			// Target class - output will be classifications of this class where PathObject's measurement > this value
			comboTargetAboveThreshold.setItems(availableClasses);

			// Target class - output will be classifications of this class where PathObject's measurement == this value
			comboTargetEqualsThreshold.setItems(availableClasses);

			// Target class - output will be classifications of this class where PathObject's measurement < this value
			comboTargetBelowThreshold.setItems(availableClasses);
			
			Label labelAbove = new Label("> threshold ");
			labelAbove.setLabelFor(comboTargetAboveThreshold);
			Label labelEquals = new Label("= threshold ");
			labelEquals.setLabelFor(comboTargetEqualsThreshold);
			Label labelBelow = new Label("< threshold ");
			labelBelow.setLabelFor(comboTargetBelowThreshold);

			GridPane gridOutput = new GridPane();
			gridOutput.add(labelAbove, 0, 0);
			gridOutput.add(comboTargetAboveThreshold, 1, 0);
			gridOutput.add(labelEquals, 0, 1);
			gridOutput.add(comboTargetEqualsThreshold, 1, 1);
			gridOutput.add(labelBelow, 0, 2);
			gridOutput.add(comboTargetBelowThreshold, 1, 2);
			gridOutput.setHgap(5);
			gridOutput.setVgap(5);
			pane = new TitledPane("Output", gridOutput);
			pane.setCollapsible(false);
			
			
			pane.setTooltip(new Tooltip("Select output classifications for objects with measurements greater than, equal to, and below the threshold"));

		}
		
		public TitledPane getPane() {
			return pane;
		}
		
		public PathClass getPathClassAbove() {
			return getPathClassAboveProperty().get();
		}

		public PathClass getPathClassEquals() {
			return getPathClassEqualsProperty().get();
		}

		public PathClass getPathClassBelow() {
			return getPathClassBelowProperty().get();
		}
		
		public ReadOnlyObjectProperty<PathClass> getPathClassAboveProperty() {
			return comboTargetAboveThreshold.getSelectionModel().selectedItemProperty();
		}

		public ReadOnlyObjectProperty<PathClass> getPathClassEqualsProperty() {
			return comboTargetEqualsThreshold.getSelectionModel().selectedItemProperty();
		}

		public ReadOnlyObjectProperty<PathClass> getPathClassBelowProperty() {
			return comboTargetBelowThreshold.getSelectionModel().selectedItemProperty();
		}
		
	}
	
	
	

}
