/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2025 QuPath developers, The University of Edinburgh
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
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
import qupath.fx.controls.PredicateTextField;
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

	private BorderPane pane;
	private TableView<String> tableMeasurements;
	
	private ObservableMeasurementTableData tableModel = new ObservableMeasurementTableData();
	
	private boolean delayedUpdate = false;

	private PredicateTextField<String> filter;

	/**
	 * Property to indicate whether the table is currently visible.
	 * If it isn't, it doesn't need to be updated.
	 */
	private BooleanProperty isShowing = new SimpleBooleanProperty(false);

	private ObservableList<String> allKeys = FXCollections.observableArrayList();
	private FilteredList<String> filteredKeys = new FilteredList<>(allKeys);

	private BooleanProperty useRegex = new SimpleBooleanProperty(false);
	private BooleanProperty ignoreCase = new SimpleBooleanProperty(true);
	private StringProperty filterText = new SimpleStringProperty("");

	/**
	 * Constructor.
	 * @param imageDataProperty the {@link ImageData} associated with this table
	 */
	public SelectedMeasurementTableView(final ObservableValue<ImageData<BufferedImage>> imageDataProperty) {
		this.imageDataProperty = imageDataProperty;
		imageDataProperty.addListener(this);
	}


	private void ensureInitialized() {
		if (pane != null)
			return;
		tableMeasurements = createMeasurementTable();

		filter = createFilter();
		filteredKeys.predicateProperty().bind(filter.predicateProperty());

		pane = new BorderPane();
		pane.setCenter(tableMeasurements);
		pane.setBottom(filter);

		// Ensure we are up-to-date if visibility status changes
		isShowing.bind(tableMeasurements.visibleProperty().and(pane.visibleProperty()));
		isShowing.addListener((v, o, n) -> {
			if (n && delayedUpdate)
				updateTableModel();
		});
	}

	private ObservableValue<String> tableKeyColumnValueFactory(CellDataFeatures<String, String> p) {
		return new SimpleStringProperty(p.getValue());
	}

	private ObservableValue<String> tableValueColumnValueFactory(CellDataFeatures<String, String> p) {
		return new SimpleStringProperty(getSelectedObjectMeasurementValue(p.getValue()));
	}


	@SuppressWarnings("unchecked")
	private TableView<String> createMeasurementTable() {
		TableView<String> tableMeasurements = new TableView<>();
		allKeys.setAll(tableModel.getAllNames());
		tableMeasurements.setItems(filteredKeys);

		TableColumn<String, String> col1 = new TableColumn<>("Key");
		col1.setCellValueFactory(this::tableKeyColumnValueFactory);
		TableColumn<String, String> col2 = new TableColumn<>("Value");
		col2.setCellValueFactory(this::tableValueColumnValueFactory);

		tableMeasurements.getColumns().addAll(col1, col2);
		tableMeasurements.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		tableMeasurements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tableMeasurements.setRowFactory(this::createTableRow);
		tableMeasurements.setOnKeyPressed(this::handleTableKeyPress);

		return tableMeasurements;
	}

	private void handleTableKeyPress(KeyEvent e) {
		if (new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN).match(e))
			copyMeasurementsToClipboard(tableMeasurements.getSelectionModel().getSelectedItems());
		e.consume();
	}

	private TableRow<String> createTableRow(TableView<String> table) {
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

		var tooltip = new Tooltip();
		row.itemProperty().addListener((v, o, n) -> {
			String helpText = n == null ? null : tableModel.getHelpText(n);
			if (helpText == null || helpText.isBlank())
				row.setTooltip(null);
			else {
				tooltip.setText(helpText);
				row.setTooltip(tooltip);
			}
		});

		return row;
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

	private PredicateTextField<String> createFilter() {
		var filter = new PredicateTextField<String>();
		filter.useRegexProperty().bindBidirectional(useRegex);
		filter.textProperty().bindBidirectional(filterText);
		filter.ignoreCaseProperty().bindBidirectional(ignoreCase);
		filter.promptTextProperty().bind(
				Bindings.createStringBinding(() -> {
					if (useRegex.get())
						return "Filter measurements by regular expression";
					else
						return "Filter measurements by key";
				}, useRegex)
		);
		filter.setSpacing(5.0);
		var tooltip = new Tooltip("Enter text to find specific measurements by key");
		Tooltip.install(filter, tooltip);
		return filter;
	}

	/**
	 * Get the {@link TableView}.
	 * Generally, it is better to use {@link #getPane()} instead, to get the component that should be added
	 * to the scene graph.
	 * @return
	 */
	public TableView<String> getTable() {
		ensureInitialized();
		return tableMeasurements;
	}

	/**
	 * Get the {@link javafx.scene.layout.Pane} containing the table and a filter field.
	 * Introduced in v0.6.0 instead of {@link #getTable()} to allow for more flexible layout, and incorporate
	 * a filter field.
	 * @return
	 * @since v0.6.0
	 */
	public Pane getPane() {
		ensureInitialized();
		return pane;
	}

	/**
	 * Get the predict text field that is used to filter measurements.
	 * @return
	 */
	public PredicateTextField<String> getPredicateTextField() {
		return filter;
	}


	private void updateTableModel() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::updateTableModel);
			return;
		}
		
		if (!isShowing.get()) {
			
			logger.debug("Measurement table update skipped (not visible)");
			
			tableModel.setImageData(null, Collections.emptyList());

			// Don't want to do expensive calculations for a table that isn't visible
			allKeys.clear();
			delayedUpdate = true; 
		} else {
			
			logger.debug("Measurement table update requested");
			
			tableModel.setImageData(this.imageData, getSelectedObjectList());
			allKeys.setAll(tableModel.getAllNames());
			
			tableMeasurements.refresh();
			delayedUpdate = false;
		}
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