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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.images.ImageData;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;

/**
 * 
 * Panel used to display a list of currently-available measurements; also allows these to be selected/deselected.
 * 
 * Purpose is for use with a classifer.
 * 
 * 
 * @author Pete Bankhead
 *
 */
class FeatureSelectionPanel {

	private QuPathGUI qupath;

	private PathIntensityClassifierPanel panelIntensities;
	private TableView<SelectableFeature> tableFeatures = new TableView<>();

	private BorderPane pane;

	FeatureSelectionPanel(final QuPathGUI qupath, final PathIntensityClassifierPanel panelIntensities) {
		this.qupath = qupath;
		this.panelIntensities = panelIntensities;
		pane = makeFeatureSelectionPanel(qupath);
	}


	public Pane getPanel() {
		return pane;
	}


	public List<String> getSelectedFeatures() {
		List<String> selectedFeatures = new ArrayList<>();
		for (SelectableFeature feature : tableFeatures.getItems()) {
			if (feature.isSelected())
				selectedFeatures.add(feature.getFeatureName());
		}
		return selectedFeatures;
	}


	public List<String> getAvailableFeatures() {
		List<String> features = new ArrayList<>();
		for (SelectableFeature feature : tableFeatures.getItems()) {
			features.add(feature.getFeatureName());
		}
		return features;
	}

	
	/**
	 * Set all available features to be selected.
	 * This doesn't automatically update the available features - ensureMeasurementsUpdated (or updateMeasurements) should be called first.
	 */
	void selectAllAvailableFeatures() {
		for (SelectableFeature feature : tableFeatures.getItems())
			feature.setSelected(true);
	}
	

	BorderPane makeFeatureSelectionPanel(final QuPathGUI qupath) {
		tableFeatures.setTooltip(new Tooltip("Select object features to be used by the classifier"));
		
		TableColumn<SelectableFeature, String> columnName = new TableColumn<>("Feature name");
		columnName.setCellValueFactory(new PropertyValueFactory<>("featureName"));
		columnName.setEditable(false);

		TableColumn<SelectableFeature, Boolean> columnSelected = new TableColumn<>("Selected");
		columnSelected.setCellValueFactory(new PropertyValueFactory<>("selected"));
		columnSelected.setCellFactory(column -> new CheckBoxTableCell<>());
		columnSelected.setEditable(true);
		columnSelected.setResizable(false);

		columnName.prefWidthProperty().bind(tableFeatures.widthProperty().subtract(columnSelected.widthProperty()));

		tableFeatures.getColumns().add(columnName);
		tableFeatures.getColumns().add(columnSelected);
		tableFeatures.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tableFeatures.setEditable(true);
		

		ContextMenu menu = new ContextMenu();
		MenuItem itemSelect = new MenuItem("Select");
		itemSelect.setOnAction(e -> {
			for (SelectableFeature feature : tableFeatures.getSelectionModel().getSelectedItems())
				feature.setSelected(true);
		});
		menu.getItems().add(itemSelect);
		MenuItem itemDeselect = new MenuItem("Deselect");
		itemDeselect.setOnAction(e -> {
			for (SelectableFeature feature : tableFeatures.getSelectionModel().getSelectedItems())
				feature.setSelected(false);
		});
		menu.getItems().add(itemDeselect);


		menu.getItems().add(new SeparatorMenuItem());
		MenuItem itemDelete = new MenuItem("Delete highlighted features");
		itemDelete.setOnAction(e -> {
			List<String> highlightedFeatures = new ArrayList<>();
			for (SelectableFeature feature : tableFeatures.getSelectionModel().getSelectedItems())
				highlightedFeatures.add(feature.getFeatureName());
			int nFeatures = highlightedFeatures.size();

			ImageData<?> imageData = qupath.getImageData();
			if (nFeatures == 0 || imageData == null || imageData.getHierarchy().isEmpty())
				return;

			String f = nFeatures == 1 ? "1 feature" : nFeatures + " features";

			if (!DisplayHelpers.showYesNoDialog("Delete feature measurements", 
					"Are you sure you want to permenently delete " + f + " from all objects?"))
				return;

			// Determine the features to remove
			// Loop through objects and delete features
			List<PathObject> changedObjects = new ArrayList<>();
			for (PathObject pathObject : imageData.getHierarchy().getFlattenedObjectList(null)) {
				// TODO: Consider if non-detection objects should be supported
				if (!pathObject.isDetection())
					continue;

				// Remove measurements & log as changed, if necessary
				MeasurementList ml = pathObject.getMeasurementList();
				int sizeBefore = ml.size();
				ml.removeMeasurements(highlightedFeatures.toArray(new String[0]));
				ml.close();
				int sizeAfter = ml.size();
				if (sizeAfter != sizeBefore)
					changedObjects.add(pathObject);
			}
			imageData.getHierarchy().fireObjectMeasurementsChangedEvent(this, changedObjects);

			tableFeatures.getSelectionModel().clearSelection();
			if (!hasFeatures())
				ensureMeasurementsUpdated();

			//				classifierData.setFeaturesSelected(features, false);
			//				tableFeatures.repaint();
		});
		menu.getItems().add(itemDelete);


		tableFeatures.setContextMenu(menu);

		// Button to update the features
		BorderPane panelButtons = new BorderPane();
//		Button btnUpdateFeatures = new Button("Update feature table");
//		btnUpdateFeatures.setTooltip(new Tooltip("Check all objects & available features"));
//		btnUpdateFeatures.setOnAction(e -> {
//			ensureMeasurementsUpdated();
//			if (panelIntensities != null)
//				panelIntensities.setAvailableMeasurements(getAvailableFeatures(), "mean", "dab");
//		});
		Button btnSelectAll = new Button("Select all");
		btnSelectAll.setOnAction(e -> {
			if (!hasFeatures())
				ensureMeasurementsUpdated();
			for (SelectableFeature feature : tableFeatures.getItems())
				feature.setSelected(true);

		});
		Button btnSelectNone = new Button("Select none");
		btnSelectNone.setOnAction(e -> {
			if (!hasFeatures())
				ensureMeasurementsUpdated();
			for (SelectableFeature feature : tableFeatures.getItems())
				feature.setSelected(false);
		});
		GridPane panelSelectButtons = PaneToolsFX.createColumnGridControls(btnSelectAll, btnSelectNone);

		panelButtons.setTop(panelSelectButtons);
//		panelButtons.setBottom(btnUpdateFeatures);
//		btnUpdateFeatures.prefWidthProperty().bind(panelButtons.widthProperty());

		BorderPane panelFeatures = new BorderPane();
		panelFeatures.setCenter(tableFeatures);
		panelFeatures.setBottom(panelButtons);

		ensureMeasurementsUpdated();

		return panelFeatures;
	}

	
	public ObservableList<SelectableFeature> getSelectableFeatures() {
		return tableFeatures.getItems();
	}
	

	boolean hasFeatures() {
		return !tableFeatures.getItems().isEmpty();
	}


	void ensureMeasurementsUpdated() {
		updateMeasurements(qupath.getImageData());
	}




	void updateMeasurements(final ImageData<?> imageData) {
		if (imageData != null)
			updateMeasurements(imageData.getHierarchy().getDetectionObjects());
	}

	void updateMeasurementsByNames(final Collection<String> availableFeatureNames) {
		// Ensure we have a set, to avoid duplicate woes
		Set<String> availableFeatureNameSet;
		if (availableFeatureNames instanceof Set)
			availableFeatureNameSet = (Set<String>)availableFeatureNames;
		else
			availableFeatureNameSet = new TreeSet<>(availableFeatureNames);
		
		List<SelectableFeature> features = new ArrayList<>();
		for (String measurement : availableFeatureNameSet) {
			features.add(getFeature(measurement));
		}

		// It may be the case that this was requested on a background thread - if so, make sure the GUI is updated correctly
		if (Platform.isFxApplicationThread()) {
			tableFeatures.getItems().setAll(features);
			if (panelIntensities != null)
				panelIntensities.setAvailableMeasurements(availableFeatureNameSet);
		} else {
			Platform.runLater(() -> {
				tableFeatures.getItems().setAll(features);
				if (panelIntensities != null)
					panelIntensities.setAvailableMeasurements(availableFeatureNameSet);				
			});
		}
	}

	void updateMeasurements(final Collection<PathObject> pathObjects) {
		Collection<String> availableFeatureNames;
		if (pathObjects == null || pathObjects.isEmpty())
			availableFeatureNames = Collections.emptyList();
		else
			availableFeatureNames = PathClassifierTools.getAvailableFeatures(pathObjects);
		updateMeasurementsByNames(availableFeatureNames);
	}



	Map<String, SelectableFeature> featurePool = new HashMap<>();



	SelectableFeature getFeature(final String name) {
		SelectableFeature feature = featurePool.get(name);
		if (feature == null) {
			feature = new SelectableFeature(name);
			featurePool.put(name, feature);
		}
		return feature;
	}


	public static class SelectableFeature {

		private StringProperty featureName = new SimpleStringProperty();
		private BooleanProperty selected = new SimpleBooleanProperty(false);

		public SelectableFeature(final String name) {
			this.featureName.set(name);
		}

		public ReadOnlyStringProperty featureNameProperty() {
			return featureName;
		}

		public BooleanProperty selectedProperty() {
			return selected;
		}

		public boolean isSelected() {
			return selected.get();
		}

		public void setSelected(final boolean selected) {
			this.selected.set(selected);
		}

		public String getFeatureName() {
			return featureName.get();
		}

	}


}