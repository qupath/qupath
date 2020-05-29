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

package qupath.lib.gui.commands;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
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
	
	private final static Logger logger = LoggerFactory.getLogger(MeasurementManager.class);
	
	private ComboBox<Class<? extends PathObject>> comboBox;
	private ListView<String> listView;
	
	private BorderPane pane;
	
	private StringProperty filterText = new SimpleStringProperty();
	
	private ImageData<?> imageData;
	private Map<Class<? extends PathObject>, Set<String>> mapMeasurements;
	
	private ObservableList<String> measurementList = FXCollections.observableArrayList();
	private FilteredList<String> filteredList = measurementList.filtered(p -> true);
	
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
		
		var manager = new MeasurementManager(imageData);
		if (!manager.hasMeasurements()) {
			Dialogs.showErrorMessage("Measurement Manager", "No measurements found!");
			return;
		}
		
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		Dialogs.builder()
				.title("Measurement Manager")
				.content(manager.getPane())
				.buttons(ButtonType.CLOSE)
				.width(Math.min(600, screenSize.getWidth()/2))
				.resizable()
				.showAndWait();
		
	}
	
	private MeasurementManager(ImageData<?> imageData) {
		this.imageData = imageData;
		refreshMeasurements();
	}
	
	Pane getPane() {
		if (pane == null)
			initializePane();
		return pane;
	}
	
	
	private synchronized void refreshMeasurements() {
		var objects = imageData.getHierarchy().getObjects(null, null);
		Map<Class<? extends PathObject>, List<PathObject>> map = objects.stream().filter(p -> p.hasMeasurements()).collect(Collectors.groupingBy(p -> p.getClass(), Collectors.toList()));
		mapMeasurements = new HashMap<>();
		for (var entry : map.entrySet()) {
			var set = new LinkedHashSet<String>();
			for (var pathObject : entry.getValue())
				set.addAll(pathObject.getMeasurementList().getMeasurementNames());
			mapMeasurements.put(entry.getKey(), set);
		}
		if (comboBox != null) {
			var selected = comboBox.getSelectionModel().getSelectedItem();
			comboBox.getItems().setAll(mapMeasurements.keySet());
			if (selected != null && mapMeasurements.containsKey(selected))
				comboBox.getSelectionModel().select(selected);
			else if (!mapMeasurements.isEmpty())
				comboBox.getSelectionModel().selectFirst();
		}
	}
	
	
	private synchronized void initializePane() {
		
		// Create a ComboBox to choose between object types
		comboBox = new ComboBox<>();
		comboBox.setCellFactory(data -> GuiTools.createCustomListCell(c -> PathObjectTools.getSuitableName(c, true)));
		comboBox.setButtonCell(GuiTools.createCustomListCell(c -> PathObjectTools.getSuitableName(c, true)));
		comboBox.getItems().setAll(mapMeasurements.keySet());
		comboBox.getSelectionModel().selectFirst();
		comboBox.setMaxWidth(Double.MAX_VALUE);
		BorderPane paneTop = new BorderPane(comboBox);
		Tooltip.install(paneTop, new Tooltip("Select an object type to view all associated measurements"));
		Label label = new Label("Object type: ");
		label.setMaxHeight(Double.MAX_VALUE);
		paneTop.setLeft(label);
		
		paneTop.setPadding(new Insets(0, 0, 10, 0));

		// Create a view to show the measurements
		listView = new ListView<>(filteredList);
		listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		updateCurrentList();

		TitledPane titledMeasurements = new TitledPane("Measurements", listView);
		titledMeasurements.textProperty().bind(Bindings.createStringBinding(() -> {
			return "Measurements (" + measurementList.size() + ")";
		}, measurementList));
		titledMeasurements.setCollapsible(false);
		titledMeasurements.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		
		TextField tfFilter = new TextField();
		tfFilter.setPromptText("Filter measurements");
		tfFilter.setTooltip(new Tooltip("Enter text to filter measurements"));
		tfFilter.setMaxHeight(Double.MAX_VALUE);
		tfFilter.textProperty().bindBidirectional(filterText);

		BorderPane paneMeasurements = new BorderPane(titledMeasurements);
		Button btnRemove = new Button("Delete selected");
		btnRemove.setMaxWidth(Double.MAX_VALUE);
		btnRemove.setOnAction(e -> promptToRemoveMeasurements());
		btnRemove.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
		
		Button btnRemoveAll = new Button("Delete all");
		btnRemoveAll.setMaxWidth(Double.MAX_VALUE);
		btnRemoveAll.setOnAction(e -> promptToRemoveAllMeasurements());
		btnRemoveAll.disableProperty().bind(Bindings.isEmpty(listView.getItems()));

		var paneButton = PaneTools.createRowGrid(tfFilter,
				PaneTools.createColumnGrid(btnRemoveAll, btnRemove));
		paneMeasurements.setBottom(paneButton);

		// Operate on backspace too
		listView.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE)
				btnRemove.fire();
		});


		filterText.addListener((v, o, n) -> updatePredicate(n));


		// Listen for object type selections
		comboBox.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateCurrentList());


		// Try to select most sensible default
		for (var defaultClass : Arrays.asList(
				PathCellObject.class,
				PathDetectionObject.class,
				PathAnnotationObject.class,
				TMACoreObject.class)
				) {
			if (mapMeasurements.containsKey(defaultClass)) {
				comboBox.getSelectionModel().select(defaultClass);
				break;
			}			
		}

		pane = new BorderPane();
		pane.setTop(paneTop);
		pane.setCenter(paneMeasurements);
		pane.setPadding(new Insets(10, 10, 10, 10));
	}
	
	
	boolean hasMeasurements() {
		return !mapMeasurements.isEmpty();
	}
	
	private boolean promptToRemoveAllMeasurements() {
		var selectedClass = comboBox.getSelectionModel().getSelectedItem();
		var selectedItems = new ArrayList<>(measurementList);
		if (selectedClass == null || selectedItems.isEmpty()) {
			Dialogs.showErrorMessage("Remove measurements", "No measurements selected!");
			return false;
		}
		String number = selectedItems.size() == 1 ? String.format("'%s'", selectedItems.iterator().next()) : selectedItems.size() + " measurements";
		if (!Dialogs.showConfirmDialog("Remove measurements", "Are you sure you want to permanently remove " + number + "?"))
			return false;
		
		logger.info("Removing all measurements for ", PathObjectTools.getSuitableName(selectedClass, true));
		Class<? extends PathObject> cls = comboBox.getSelectionModel().getSelectedItem();
		String script;
		var hierarchy = imageData.getHierarchy();
		if (cls == PathAnnotationObject.class) {
			script = "clearAnnotationMeasurements()";
			QP.clearAnnotationMeasurements(hierarchy);
		} else if (cls == PathCellObject.class) {
			script = "clearCellMeasurements()";
			QP.clearCellMeasurements(hierarchy);
		} else if (cls == PathTileObject.class) {
			script = "clearTileMeasurements()";
			QP.clearTileMeasurements(hierarchy);
		} else if (cls == PathRootObject.class) {
			script = "clearRootMeasurements()";
			QP.clearRootMeasurements(hierarchy);
		} else if (cls == TMACoreObject.class) {
			script = "clearTMACoreMeasurements()";
			QP.clearTMACoreMeasurements(hierarchy);
		} else {
			script = "clearMeasurements(" + cls.getName() + ")";
			QP.clearMeasurements(hierarchy, cls);
		}
	
		// Keep for scripting
		WorkflowStep step = new DefaultScriptableWorkflowStep("Clear all measurements", script);
		imageData.getHistoryWorkflow().addStep(step);

		// Update
		refreshMeasurements();
		updateCurrentList();
		filterText.set("");
		return true;
	}
	
	
	private boolean promptToRemoveMeasurements() {
		var selectedItems = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
		if (selectedItems.isEmpty()) {
			Dialogs.showErrorMessage("Remove measurements", "No measurements selected!");
			return false;
		}
		String number = selectedItems.size() == 1 ? String.format("'%s'", selectedItems.iterator().next()) : selectedItems.size() + " measurements";
		if (!Dialogs.showConfirmDialog("Remove measurements", "Are you sure you want to permanently remove " + number + "?"))
			return false;
		
		String removeString = selectedItems.stream().map(m -> "\"" + m + "\"").collect(Collectors.joining(", "));
		logger.info("Removing measurements: {}", removeString);
		Class<? extends PathObject> cls = comboBox.getSelectionModel().getSelectedItem();
		QP.removeMeasurements(imageData.getHierarchy(), cls, selectedItems.toArray(String[]::new));

		// Keep for scripting
		WorkflowStep step = new DefaultScriptableWorkflowStep("Remove measurements",
				String.format("removeMeasurements(%s, %s);", cls.getName(), removeString)
				);
		imageData.getHistoryWorkflow().addStep(step);

		// Update
		refreshMeasurements();
		updateCurrentList();
		filterText.set("");
		return true;
	}
	
	
	/**
	 * Update the keyword for filtering the (displayed) currentList
	 * @param text
	 */
	private void updatePredicate(String text) {
		if (text == null || text.isBlank())
			filteredList.setPredicate(p -> true);
		else {
			String lower = text.toLowerCase();
			filteredList.setPredicate(p -> p.toLowerCase().contains(lower));
		}
	}
	
	/**
	 * For whenever a measurement is removed, update 
	 * the filtered list and call refresh display
	 */
	private void updateCurrentList() {
		var selected = comboBox == null ? null : comboBox.getSelectionModel().getSelectedItem();
		if (selected == null)
			measurementList.clear();
		else
			measurementList.setAll(mapMeasurements.getOrDefault(selected, Collections.emptySet()));
	}
	
}