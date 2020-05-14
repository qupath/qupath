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

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.scripting.QP;


/**
 * Simple dialog box for managing measurements - especially removing them.
 * 
 * @author Pete Bankhead
 *
 */
class MeasurementManager {
	
	private static ObservableList<String> data;
	private static FilteredList<String> currentList;
	private static ListView<String> listView;
	
	/**
	 * Show a simple dialog for viewing (and optionally removing) detection measurements.
	 * @param qupath
	 * @param imageData
	 */
	public static void showDetectionMeasurementManager(QuPathGUI qupath, ImageData<?> imageData) {
		if (imageData == null) {
			Dialogs.showNoImageError("Measurement Manager");
			return;
		}
		
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		
		// Get all the objects we have
		Map<Class<? extends PathObject>, List<PathObject>> map = new HashMap<>();
		Map<Class<? extends PathObject>, Set<String>> mapMeasurements = new HashMap<>();
		for (PathObject p : hierarchy.getFlattenedObjectList(null)) {
			
			List<String> names = p.getMeasurementList().getMeasurementNames();
			if (names.isEmpty())
				continue;
			
			List<PathObject> list = map.get(p.getClass());
			if (list == null) {
				list = new ArrayList<>();
				map.put(p.getClass(), list);
			}
			list.add(p);
			
			Set<String> setMeasurements = mapMeasurements.get(p.getClass());
			if (setMeasurements == null) {
				setMeasurements = new LinkedHashSet<>();
				mapMeasurements.put(p.getClass(), setMeasurements);
			}
			setMeasurements.addAll(p.getMeasurementList().getMeasurementNames());
		}
		
		// Check we have something to show
		if (map.isEmpty()) {
			Dialogs.showErrorMessage("Measurement Manager", "No objects found!");
			return;
		}
		
		
		// Get a mapping of suitable names for each class
		Map<String, Class<? extends PathObject>> classMap = new TreeMap<>();
		map.keySet().stream().forEach(k -> classMap.put(PathObjectTools.getSuitableName(k, true), k));
		
		// Create a ComboBox to choose between object types
		ComboBox<String> comboBox = new ComboBox<>();
		comboBox.getItems().setAll(classMap.keySet());
		comboBox.getSelectionModel().selectFirst();
		comboBox.setMaxWidth(Double.MAX_VALUE);
		BorderPane paneTop = new BorderPane(comboBox);
		Tooltip.install(paneTop, new Tooltip("Select an object type to view all associated measurements"));
		Label label = new Label("Object type: ");
		label.setMaxHeight(Double.MAX_VALUE);
		paneTop.setLeft(label);
		
		TextField tfFilter = new TextField();
		tfFilter.setPromptText("Filter measurements");
		tfFilter.setTooltip(new Tooltip("Type something to filter measurements"));
		tfFilter.setMaxHeight(Double.MAX_VALUE);
		paneTop.setBottom(tfFilter);
		paneTop.setPadding(new Insets(0, 0, 10, 0));

		// Create a view to show the measurements
		listView = new ListView<>();
		listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		var currPathObject = classMap.get(comboBox.getSelectionModel().getSelectedItem());
		updateCurrentList(mapMeasurements, currPathObject);
		
		TitledPane titledMeasurements = new TitledPane("Measurements", listView);
		titledMeasurements.setCollapsible(false);
		titledMeasurements.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		BorderPane paneMeasurements = new BorderPane(titledMeasurements);
		Button btnRemove = new Button("Delete selected");
		btnRemove.setMaxWidth(Double.MAX_VALUE);
		paneMeasurements.setBottom(btnRemove);
		btnRemove.setOnAction(e -> {
			var selectedItems = listView.getSelectionModel().getSelectedItems();
			if (selectedItems.isEmpty()) {
				Dialogs.showErrorMessage("Remove measurements", "No measurements selected!");
				return;
			}
			String number = selectedItems.size() == 1 ? String.format("'%s'", selectedItems.iterator().next()) : selectedItems.size() + " measurements";
			if (Dialogs.showConfirmDialog("Remove measurements", "Are you sure you want to permanently remove " + number + "?")) {
				Class<? extends PathObject> cls = classMap.get(comboBox.getSelectionModel().getSelectedItem());
				QP.removeMeasurements(hierarchy, cls, selectedItems.toArray(new String[selectedItems.size()]));
				
				// Keep for scripting
				WorkflowStep step = new DefaultScriptableWorkflowStep("Remove measurements",
						String.format("removeMeasurements(%s, %s);", cls.getName(), String.join(", ", selectedItems.stream().map(m -> "\"" + m + "\"").collect(Collectors.toList())))
						);
				imageData.getHistoryWorkflow().addStep(step);
				
				// Update
				mapMeasurements.get(cls).removeAll(selectedItems);
				updateCurrentList(mapMeasurements, classMap.get(comboBox.getSelectionModel().getSelectedItem()));
				titledMeasurements.setText("Measurements (" + mapMeasurements.get(cls).size() + ")");
				tfFilter.setText("");
			}
		});
		btnRemove.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
		
		// Operate on backspace too
		listView.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE)
				btnRemove.fire();
		});
		
		
		tfFilter.textProperty().addListener((v, o, n) -> {
			updatePredicate(n);
			titledMeasurements.setText("Measurements (" + currentList.size() + ")");
		});
		

		// Listen for object type selections
		comboBox.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			Class<? extends PathObject> cls = classMap.get(n);
			updateCurrentList(mapMeasurements, classMap.get(n));
			titledMeasurements.setText("Measurements (" + mapMeasurements.get(cls).size() + ")");
		});
		
		
		// Try to select most sensible default
		for (String defaultName : Arrays.asList(
				PathObjectTools.getSuitableName(PathCellObject.class, true),
				PathObjectTools.getSuitableName(PathDetectionObject.class, true),
				PathObjectTools.getSuitableName(PathAnnotationObject.class, true),
				PathObjectTools.getSuitableName(TMACoreObject.class, true)
				)) {
			if (classMap.containsKey(defaultName)) {
				comboBox.getSelectionModel().select(defaultName);
				break;
			}			
		}

		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Measurement Manager");
		
		BorderPane pane = new BorderPane();
		pane.setTop(paneTop);
		pane.setCenter(paneMeasurements);
		pane.setPadding(new Insets(10, 10, 10, 10));
		
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		if (screenSize != null && screenSize.getWidth() > 100)
			dialog.setScene(new Scene(pane, Math.min(600, screenSize.getWidth()/2), -1));
		else
			dialog.setScene(new Scene(pane));
		dialog.show();
		
	}
	
	/**
	 * Update the keyword for filtering the (displayed) currentList
	 * @param text
	 */
	static void updatePredicate(String text) {
		if (text == null || text.isBlank())
			currentList.setPredicate(p -> true);
		else
			currentList.setPredicate(p -> p.toLowerCase().contains(text.toLowerCase()));
		refreshListView();
	}
	
	/**
	 * For whenever a measurement is removed, update 
	 * the filtered list and call refresh display
	 * 
	 * @param mapMeasurements
	 * @param currPathObject
	 */
	static void updateCurrentList(Map<Class<? extends PathObject>, Set<String>> mapMeasurements, Class<? extends PathObject> currPathObject) {
		data = FXCollections.observableArrayList(mapMeasurements.get(currPathObject));
		currentList = new FilteredList<>(data, s -> true);
		refreshListView();
	}
	
	/**
	 * Refresh for display
	 */
	static void refreshListView() {
		listView.getItems().clear();
		listView.getItems().addAll(currentList);
	}
	
}