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

package qupath.lib.gui.panes;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableRow;
import javafx.util.Callback;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;

/**
 * Component to show measurements for a currently-selected object.
 * 
 * @author Pete Bankhead
 *
 */
public class SelectedMeasurementTableView implements PathObjectSelectionListener, ChangeListener<ImageData<BufferedImage>>,
	PathObjectHierarchyListener, PropertyChangeListener {
	
	private static final Logger logger = LoggerFactory.getLogger(SelectedMeasurementTableView.class);
	
	private static int nDecimalPlaces = 4;
	
	/**
	 * Retain reference to prevent garbage collection.
	 */
	@SuppressWarnings("unused")
	private ObservableValue<ImageData<BufferedImage>> imageDataProperty;
	private ImageData<?> imageData;
	
	private TableView<String> tableMeasurements;
	
	private ObservableMeasurementTableData tableModel = new ObservableMeasurementTableData();
	
	private boolean delayedUpdate = false;
	
	/**
	 * Constructor.
	 * @param imageDataProperty the {@link ImageData} associated with this table
	 */
	public SelectedMeasurementTableView(final ObservableValue<ImageData<BufferedImage>> imageDataProperty) {
		this.imageDataProperty = imageDataProperty;
		imageDataProperty.addListener(this);
	}
	
	
	@SuppressWarnings("unchecked")
	private TableView<String> createMeasurementTable() {
		TableView<String> tableMeasurements = new TableView<>();
		tableMeasurements.getItems().setAll(tableModel.getAllNames());
		
		TableColumn<String, String> col1 = new TableColumn<>("Key");
		col1.setCellValueFactory(new Callback<CellDataFeatures<String, String>, ObservableValue<String>>() {
			@Override
			public ObservableValue<String> call(CellDataFeatures<String, String> p) {
				return new SimpleStringProperty(p.getValue());
			}
		});
		TableColumn<String, String> col2 = new TableColumn<>("Value");
		col2.setCellValueFactory(new Callback<CellDataFeatures<String, String>, ObservableValue<String>>() {
			@Override
			public ObservableValue<String> call(CellDataFeatures<String, String> p) {
				return new SimpleStringProperty(getSelectedObjectMeasurementValue(p.getValue()));
			}
		});
		tableMeasurements.getColumns().addAll(col1, col2);
		tableMeasurements.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		tableMeasurements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tableMeasurements.setRowFactory(e -> {
		    final TableRow<String> row = new TableRow<>();
		    final ContextMenu menu = new ContextMenu();
		    final MenuItem copyItem = new MenuItem("Copy");
		    menu.getItems().add(copyItem);
		    copyItem.setOnAction(ev -> copyMeasurementsToClipboard(tableMeasurements.getSelectionModel().getSelectedItems()));
		    
		    // Only display context menu for non-empty rows
		    row.contextMenuProperty().bind(
		    	Bindings.when(row.emptyProperty())
		    	.then((ContextMenu) null)
		    	.otherwise(menu)
		    );
		    
		    return row;
		});
		tableMeasurements.setOnKeyPressed(e -> {
	        if (new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN).match(e))
	        	copyMeasurementsToClipboard(tableMeasurements.getSelectionModel().getSelectedItems());
	        e.consume();
	    });
		
		// Ensure we are up-to-date if visibility status changes
		tableMeasurements.visibleProperty().addListener((v, o, n) -> {
			if (n && delayedUpdate)
				updateTableModel();
		});

		return tableMeasurements;
	}
	
	private void copyMeasurementsToClipboard(List<String> selectedMeasurements) {
		ClipboardContent content = new ClipboardContent();
    	String values = selectedMeasurements.stream()
    			.map(item -> item + "\t" + getSelectedObjectMeasurementValue(item))
    			.collect(Collectors.joining(System.lineSeparator()));
        content.putString(values);
        Clipboard.getSystemClipboard().setContent(content);
	}
	
	private List<PathObject> getSelectedObjectList() {
		PathObject selected = getSelectedObject();
		if (selected == null) {
			return Collections.emptyList();
		}
		return Collections.singletonList(selected);
	}
	
	private PathObject getSelectedObject() {
		if (imageData == null)
			return null;
		var selected = imageData.getHierarchy().getSelectionModel().getSelectedObject();
		if (selected == null)
			return imageData.getHierarchy().getRootObject();
		else
			return selected;
	}

	private String getSelectedObjectMeasurementValue(final String name) {
		var selected = getSelectedObject();
		if (selected == null)
			return null;
		return tableModel.getStringValue(selected, name, nDecimalPlaces);
	}
	
	/**
	 * Get the {@link TableView}.
	 * @return
	 */
	public TableView<String> getTable() {
		if (tableMeasurements == null)
			tableMeasurements = createMeasurementTable();
		return tableMeasurements;
	}
	
	private void updateTableModel() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> updateTableModel());
			return;
		}
		
		if (!tableMeasurements.isVisible()) {
			
			logger.debug("Measurement table update skipped (not visible)");
			
			tableModel.setImageData(null, Collections.emptyList());

			// Don't want to do expensive calculations for a table that isn't visible
			tableMeasurements.getItems().clear();
			delayedUpdate = true; 
		} else {
			
			logger.debug("Measurement table update requested");
			
			tableModel.setImageData(this.imageData, getSelectedObjectList());
			tableMeasurements.getItems().setAll(tableModel.getAllNames());
			
			tableMeasurements.refresh();
			delayedUpdate = false;
		}

//		// Check if objects are outside hierarchy
//		if (imageData != null) {
//			for (PathObject pathObject : getSelectedObjectList()) {
//				if (PathObjectTools.hierarchyContainsObject(imageData.getHierarchy(), pathObject)) {
//					tableModel.setImageData(this.imageData, getSelectedObjectList());
//					tableMeasurements.refresh();
//					return;
//				}
//			}
//		}
//		tableModel.setImageData(null, getSelectedObjectList());
//		tableMeasurements.refresh();
	}

	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		// If still changing, only refresh the table to avoid expensive recomputations
		if (event.isChanging())
			tableMeasurements.refresh();
		else
			updateTableModel();
	}

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {
		if (this.imageData != null) {
			this.imageData.removePropertyChangeListener(this);
			this.imageData.getHierarchy().removeListener(this);
			this.imageData.getHierarchy().getSelectionModel().removePathObjectSelectionListener(this);
		}
		this.imageData = imageDataNew;
		if (this.imageData != null) {
			this.imageData.addPropertyChangeListener(this);
			this.imageData.getHierarchy().addListener(this);
			this.imageData.getHierarchy().getSelectionModel().addPathObjectSelectionListener(this);
		}
		logger.trace("Image data set to {}", imageData);
		updateTableModel();
	}

	@Override
	public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject, Collection<PathObject> allSelected) {
		updateTableModel();
	}


	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		updateTableModel();
	}
	
}