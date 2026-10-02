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

package qupath.lib.gui.charts.display;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.controlsfx.control.SearchableComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.charts.impl.HistogramChart;
import qupath.lib.gui.charts.impl.HistogramChart.HistogramData;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.plugins.parameters.IntParameter;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wrapper close to enable the generation and display of histograms relating to a data table.
 * Other UI controls are provided to enable selection of specific data columns for display in the histogram.
 * 
 */
public class HistogramDisplay<T> implements PlotDisplay<T>, ParameterChangeListener {

	private static final Logger logger = LoggerFactory.getLogger(HistogramDisplay.class);

	private final ObjectProperty<PathTableData<T>> model = new SimpleObjectProperty<>();
	private final BorderPane pane = new BorderPane();

	private final SearchableComboBox<String> comboName = new SearchableComboBox<>();
	private final HistogramChart histogramChart = new HistogramChart();
	private final ParameterPanelFX panelParams;

	private final StringProperty selectedColumn = new SimpleStringProperty();

	private int currentBins;
	private double[] currentValues;

	private final ParameterList paramsHistogram = new ParameterList()
			.addChoiceParameter(
					"countsTransform",
					QuPathResources.getString("Charts.HistogramDisplay.counts"),
					HistogramChart.CountsTransformMode.RAW,
					Arrays.asList(HistogramChart.CountsTransformMode.values()),
					QuPathResources.getString("Charts.HistogramDisplay.countsDescription")
			)
			.addIntParameter(
					"nBins",
					QuPathResources.getString("Charts.HistogramDisplay.numberOfBins"),
					32,
					null,
					QuPathResources.getString("Charts.HistogramDisplay.numberOfBinsDescription")
			)
			.addBooleanParameter(
					"drawGrid",
					QuPathResources.getString("Charts.HistogramDisplay.drawGrid"),
					true,
					QuPathResources.getString("Charts.HistogramDisplay.drawGrid")
			)
			.addBooleanParameter(
					"drawAxes",
					QuPathResources.getString("Charts.HistogramDisplay.drawAxes"),
					true,
					QuPathResources.getString("Charts.HistogramDisplay.drawAxes")
			)
			.addBooleanParameter(
					"animate",
					QuPathResources.getString("Charts.HistogramDisplay.animateChanges"),
					false,
					QuPathResources.getString("Charts.HistogramDisplay.animateChanges")
			);
	private final TableView<Property<Number>> table = new TableView<>();

	/**
	 * Create a histogramDisplay without underlying data (initially)
	 * @param showTable whether to show summary measurements as a table
	 */
	public HistogramDisplay(final boolean showTable) {
		model.addListener(this::handleModelChange);

		TableColumn<Property<Number>, String> colName = new TableColumn<>(QuPathResources.getString("Charts.HistogramDisplay.measurement"));
		colName.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getName()));
		TableColumn<Property<Number>, Number> colValue = new TableColumn<>(QuPathResources.getString("Charts.HistogramDisplay.value"));
		colValue.setCellValueFactory(TableColumn.CellDataFeatures::getValue);
		colValue.setCellFactory(_ -> {
			return new TableCell<>() {
				@Override
				protected void updateItem(Number item, boolean empty) {
					super.updateItem(item, empty);
					if (item == null || empty) {
						setText(null);
						setStyle("");
					} else {
						if (Double.isNaN(item.doubleValue()))
							setText("-");
						else
							setText(GeneralTools.createFormatter(3).format(item));
					}
				}
			};
		});
		table.getColumns().add(colName);
		table.getColumns().add(colValue);
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		table.maxHeightProperty().bind(table.prefHeightProperty());
		table.setPrefHeight(180);
		table.setMinWidth(100);
		table.setStyle("-fx-font-size: 0.8em");

		BorderPane panelMain = new BorderPane();
		panelMain.setCenter(histogramChart);

		selectedColumn.bindBidirectional(comboName.valueProperty());
		selectedColumn.addListener((_) -> {
			requestReplot();
		});
		histogramChart.setShowTickLabels(paramsHistogram.getBooleanParameterValue("drawAxes"));

		panelParams = new ParameterPanelFX(paramsHistogram);
		panelParams.addParameterChangeListener(this);
		panelParams.getPane().setPadding(new Insets(20, 5, 5, 5));
		panelParams.getPane().setMinWidth(Pane.USE_PREF_SIZE);
		updateTable(null);

		GridPane panelSouth = new GridPane();
		panelSouth.add(panelParams.getPane(), 0, 0);
		if (showTable)
			panelSouth.add(table, 1, 0);
		GridPane.setHgrow(panelParams.getPane(), Priority.NEVER);
		GridPane.setHgrow(table, Priority.ALWAYS);

		pane.setTop(comboName);
		comboName.prefWidthProperty().bind(pane.widthProperty());
		panelMain.setMinSize(200, 200);
		panelMain.setPrefSize(600, 400);
		pane.setCenter(panelMain);
		pane.setBottom(panelSouth);

		pane.setPadding(new Insets(10, 10, 10, 10));

		requestReplot();
	}

	private void handleModelChange(ObservableValue<? extends PathTableData<?>> observable,
								   PathTableData<?> oldValue, PathTableData<?> newValue) {
		String selectColumn = null;
		comboName.getItems().setAll(newValue.getMeasurementNames());
		if (comboName.getItems().isEmpty()) {
			logger.debug("No items to display!");
			return;
		}
		// Try to select the first column that isn't for 'centroids'...
		for (String name : newValue.getMeasurementNames()) {
			if (!name.toLowerCase().startsWith("centroid")) {
				selectColumn = name;
				break;
			}
			if (selectColumn == null)
				selectColumn = name;
		}
		if (selectColumn != null)
			comboName.getSelectionModel().select(selectColumn);
	}


	/**
	 * Constructor.
	 * @param model the table data for histogramming
	 * @param showTable if true, include a measurement summary table along with the histogram
	 */
	public HistogramDisplay(final PathTableData<T> model, final boolean showTable) {
		this(showTable);
		this.model.set(model);
	}
	
	/**
	 * Refresh the available measurements.
	 */
	public void refreshCombo() {
		String selected = comboName.getSelectionModel().getSelectedItem();
		if (!getModel().getAllNames().equals(comboName.getItems())) {
			comboName.getItems().setAll(getModel().getAllNames());
			comboName.getSelectionModel().select(selected);
		}
	}
	
	/**
	 * Set the number of bins for the histogram.
	 * @param nBins the number of bins to use
	 */
	public void setNumBins(int nBins) {
		if (nBins > 1e5) {
			logger.warn("nBins set to strange value {}; resetting to 32.", nBins);
			nBins = 32;
		}
		if (panelParams != null)
			panelParams.setNumericParameterValue("nBins", nBins);
		else
			((IntParameter) paramsHistogram.getParameters().get("nBins")).setValue(nBins);
	}

	/**
	 * Get the requested number of bins used for the histogram.
	 * @return The number of bins
	 */
	public int getNumBins() {
		return paramsHistogram.getIntParameterValue("nBins");
	}

	@Override
	public ObjectProperty<PathTableData<T>> modelProperty() {
		return model;
	}

	@Override
	public String getName() {
		return QuPathResources.getString("Measure.MeasurementTable.histogram");
	}

	@Override
	public Pane getPane() {
		return pane;
	}

	@Override
	public void setModel(PathTableData<T> model) {
		this.model.set(model);
	}

	@Override
	public PathTableData<T> getModel() {
		return model.get();
	}


	@Override
	public void requestReplot() {
		final String columnName = selectedColumn.get();
		var model = getModel();
		if (model != null && model.getMeasurementNames().contains(columnName)) {
			double[] values = model.getDoubleValues(columnName);
			int nBins = paramsHistogram.getIntParameterValue("nBins");
			if (nBins < 2)
				nBins = 2;
			else if (nBins > 1000)
				nBins = 1000;

			// We can have values in the 'wrong' order to facilitate comparison...
			Arrays.sort(values);

			// Check if we've actually changed anything - if not, then abort
			if (nBins == currentBins && currentValues != null && Arrays.equals(currentValues, values))
				return;

			Histogram histogram = new Histogram(values, nBins);

			HistogramData histogramData = HistogramChart.createHistogramData(histogram, (Integer)null);
			updateCountsTransform(histogramChart, paramsHistogram);
			histogramChart.getHistogramData().setAll(histogramData);


			histogramChart.setVerticalGridLinesVisible(true);
			histogramChart.setHorizontalGridLinesVisible(true);
			histogramChart.setLegendVisible(false);
			histogramChart.setCreateSymbols(false); // Can't stop them being orange...
			histogramChart.getXAxis().setLabel(QuPathResources.getString("Charts.HistogramDisplay.values"));
			histogramChart.getYAxis().setLabel(QuPathResources.getString("Charts.HistogramDisplay.counts"));
			histogramChart.getYAxis().setTickLabelsVisible(true);
			histogramChart.getYAxis().setTickMarkVisible(true);
			histogramChart.getXAxis().setTickLabelsVisible(true);
			histogramChart.getXAxis().setTickMarkVisible(true);

			histogramChart.setAnimated(paramsHistogram.getBooleanParameterValue("animate"));

			updateTable(histogram);

			currentBins = nBins;
			currentValues = values;
		} else {
			histogramChart.getHistogramData().clear();
			currentValues = null;
		}
	}



	private static void updateCountsTransform(HistogramChart histogramChart, ParameterList params) {
		var transform = params.getChoiceParameterValue("countsTransform");
		if (transform instanceof HistogramChart.CountsTransformMode mode) {
			histogramChart.setCountsTransform(mode);
			if (transform == HistogramChart.CountsTransformMode.RAW)
				histogramChart.getYAxis().setLabel(QuPathResources.getString("Charts.HistogramDisplay.counts"));
			else
				histogramChart.getYAxis().setLabel(MessageFormat.format(
						QuPathResources.getString("Charts.HistogramDisplay.countsX"),
						transform
				));
		} else
			logger.warn("Histogram counts transform not supported: {}", transform);
	}

	@Override
	public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
		if ("countsTransform".equals(key)) {
			updateCountsTransform(histogramChart, parameterList);
		} else if ("drawGrid".equals(key)) {
			histogramChart.setHorizontalGridLinesVisible(paramsHistogram.getBooleanParameterValue("drawGrid"));
			histogramChart.setVerticalGridLinesVisible(paramsHistogram.getBooleanParameterValue("drawGrid"));
		} else if ("drawAxes".equals(key)) {
			histogramChart.setShowTickLabels(paramsHistogram.getBooleanParameterValue("drawAxes"));
		} else if ("nBins".equals(key)) {
			requestReplot();
		} else if ("animate".equals(key)) {
			histogramChart.setAnimated(paramsHistogram.getBooleanParameterValue("animate"));
		}
	}

	private void updateTable(final Histogram histogram) {
		if (histogram == null) {
			List<Property<Number>> stats = new ArrayList<>();
			stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.count"), Double.NaN));
			stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.missing"), Double.NaN));
			stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.mean"), Double.NaN));
			stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.stdDev"), Double.NaN));
			stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.min"), Double.NaN));
			stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.max"), Double.NaN));
			table.getItems().setAll(stats);
			return;
		}
		List<Property<Number>> stats = new ArrayList<>();
		stats.add(new SimpleLongProperty(null, QuPathResources.getString("Charts.HistogramDisplay.count"), histogram.nValues()));
		stats.add(new SimpleLongProperty(null, QuPathResources.getString("Charts.HistogramDisplay.missing"), histogram.nMissingValues()));
		stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.mean"), histogram.getMeanValue()));
		stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.stdDev"), histogram.getStdDev()));
		stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.min"), histogram.getMinValue()));
		stats.add(new SimpleDoubleProperty(null, QuPathResources.getString("Charts.HistogramDisplay.max"), histogram.getMaxValue()));
		table.getItems().setAll(stats);
	}

	/**
	 * Update plot for specified data columns.
	 * @param name the name of the columns to show
	 */
	public void plotColumn(String name) {
		if (comboName.getItems().contains(name)) {
			selectedColumn.set(name);
		}
		else {
			logger.debug("Unknown column requested: {}", name);
		}
	}
}
