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

package qupath.lib.gui.panels;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.util.Callback;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.models.ObservableMeasurementTableData;
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
public class SelectedMeasurementTableView implements PathObjectSelectionListener, ImageDataChangeListener<BufferedImage>, PathObjectHierarchyListener {
	
	private final static Logger logger = LoggerFactory.getLogger(SelectedMeasurementTableView.class);
	
	private static int nDecimalPlaces = 4;
	
	private ImageData<?> imageData;
	
	private TableView<?> tableMeasurements;
	
	private ObservableMeasurementTableData tableModel = new ObservableMeasurementTableData();
	
	
	public SelectedMeasurementTableView(final QuPathGUI qupath) {
		qupath.addImageDataChangeListener(this);
	}
	
	
	@SuppressWarnings("unchecked")
	private TableView<String> createMeasurementTable() {
		TableView<String> tableMeasurements = new TableView<>(tableModel.getAllNames());
		
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

		return tableMeasurements;
	}
	
	private List<PathObject> getSelectedObjectList() {
		PathObject selected = getSelectedObject();
		if (selected == null)
			return Collections.emptyList();
		return Collections.singletonList(selected);
	}
	
	private PathObject getSelectedObject() {
		if (imageData == null)
			return null;
		return imageData.getHierarchy().getSelectionModel().getSelectedObject();
	}

	private String getSelectedObjectMeasurementValue(final String name) {
		return tableModel.getStringValue(getSelectedObject(), name, nDecimalPlaces);
	}
	
	public TableView<?> getTable() {
		if (tableMeasurements == null)
			tableMeasurements = createMeasurementTable();
		return tableMeasurements;
	}
	
	private void updateTableModel() {
		tableModel.setImageData(this.imageData, getSelectedObjectList());
		tableMeasurements.refresh();
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
	public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {
		if (this.imageData != null) {
			this.imageData.getHierarchy().removePathObjectListener(this);
			this.imageData.getHierarchy().getSelectionModel().removePathObjectSelectionListener(this);
		}
		this.imageData = imageDataNew;
		if (this.imageData != null) {
			this.imageData.getHierarchy().addPathObjectListener(this);
			this.imageData.getHierarchy().getSelectionModel().addPathObjectSelectionListener(this);
		}
		logger.trace("Image data set to {}", imageData);
		updateTableModel();
	}

	@Override
	public void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject) {
		updateTableModel();
	}
	
}