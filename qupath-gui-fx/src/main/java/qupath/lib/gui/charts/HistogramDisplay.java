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

package qupath.lib.gui.charts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.charts.HistogramChart.HistogramData;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.plugins.parameters.IntParameter;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * Wrapper close to enable the generation and display of histograms relating to a data table.
 * Other UI controls are provided to enable selection of specific data columns for display in the histogram.
 * 
 * @author Pete Bankhead
 *
 */
public class HistogramDisplay implements ParameterChangeListener {

	static final Logger logger = LoggerFactory.getLogger(HistogramDisplay.class);

	private PathTableData<?> model;
	private final BorderPane pane = new BorderPane();

	private final ComboBox<String> comboName = new ComboBox<>();
	private final HistogramChart histogramChart = new HistogramChart();
	private final ParameterPanelFX panelParams;

	private int currentBins;
	private double[] currentValues;
	private String currentColumn = null;

	private final ParameterList paramsHistogram = new ParameterList()
			.addChoiceParameter("countsTransform", "Counts",
					HistogramChart.CountsTransformMode.RAW, Arrays.asList(HistogramChart.CountsTransformMode.values()),
					"Normalize counts (probability distribution)")
			.addIntParameter("nBins", "Number of bins", 32, null, "Number of histogram bins (>= 2 and <= 1000)")
			.addBooleanParameter("drawGrid", "Draw grid", true, "Draw grid")
			.addBooleanParameter("drawAxes", "Draw axes", true, "Draw axes")
			.addBooleanParameter("animate", "Animate changes", false, "Animate changes");
	private final TableView<Property<Number>> table = new TableView<>();

	/**
	 * Constructor.
	 * @param model the table data for histogramming
	 * @param showTable if true, include a measurement summary table along with the histogram
	 */
	public HistogramDisplay(final PathTableData<?> model, final boolean showTable) {
		String selectColumn = null;
		this.model = model;
		comboName.getItems().setAll(model.getMeasurementNames());
		if (comboName.getItems().isEmpty()) {
			logger.debug("No items to display!");
//			return;
		}
		// Try to select the first column that isn't for 'centroids'...
		for (String name : model.getMeasurementNames()) {
			if (!name.toLowerCase().startsWith("centroid")) {
				selectColumn = name;
				break;
			}
			if (selectColumn == null)
				selectColumn = name;
		}
		if (selectColumn != null)
			comboName.getSelectionModel().select(selectColumn);

		
		TableColumn<Property<Number>, String> colName = new TableColumn<>("Measurement");
		colName.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getName()));
		TableColumn<Property<Number>, Number> colValue = new TableColumn<>("Value");
		colValue.setCellValueFactory(p -> p.getValue());
		colValue.setCellFactory(column -> {
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

		comboName.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			setHistogram(model, n);
		});
		histogramChart.setShowTickLabels(paramsHistogram.getBooleanParameterValue("drawAxes"));


		panelParams = new ParameterPanelFX(paramsHistogram);
		panelParams.addParameterChangeListener(this);
		panelParams.getPane().setPadding(new Insets(20, 5, 5, 5));
		panelParams.getPane().setMinWidth(Pane.USE_PREF_SIZE);
		updateTable(null);

//		var panelSouth = PaneTools.createColumnGrid(panelParams.getPane(), table);
		
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

		setHistogram(model, comboName.getSelectionModel().getSelectedItem());
	}
	
	/**
	 * Refresh the available measurements.
	 */
	public void refreshCombo() {
		String selected = comboName.getSelectionModel().getSelectedItem();
		if (!model.getAllNames().equals(comboName.getItems())) {
			comboName.getItems().setAll(model.getAllNames());
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

	/**
	 * Get the pane containing the histogram and associated UI components, for addition to a scene.
	 * @return The pane
	 */
	public Pane getPane() {
		return pane;
	}

	void setHistogram(final PathTableData<?> model, final String columnName) {
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
//			histogram.setNormalizeCounts(params.getBooleanParameterValue("normalizeCounts"));

			HistogramData histogramData = HistogramChart.createHistogramData(histogram, (Integer)null);
			updateCountsTransform(histogramChart, paramsHistogram);
			histogramChart.getHistogramData().setAll(histogramData);


			histogramChart.setVerticalGridLinesVisible(true);
			histogramChart.setHorizontalGridLinesVisible(true);
			histogramChart.setLegendVisible(false);
			histogramChart.setCreateSymbols(false); // Can't stop them being orange...
			histogramChart.getXAxis().setLabel("Values");
			histogramChart.getYAxis().setLabel("Counts");
			histogramChart.getYAxis().setTickLabelsVisible(true);
			histogramChart.getYAxis().setTickMarkVisible(true);
			histogramChart.getXAxis().setTickLabelsVisible(true);
			histogramChart.getXAxis().setTickMarkVisible(true);

			histogramChart.setAnimated(paramsHistogram.getBooleanParameterValue("animate"));

			updateTable(histogram);

			currentColumn = columnName;
			currentBins = nBins;
			currentValues = values;
			this.model = model;
		} else
			histogramChart.getHistogramData().clear();
	}

	private static void updateCountsTransform(HistogramChart histogramChart, ParameterList params) {
		var transform = params.getChoiceParameterValue("countsTransform");
		if (transform instanceof HistogramChart.CountsTransformMode mode) {
			histogramChart.setCountsTransform(mode);
			if (transform == HistogramChart.CountsTransformMode.RAW)
				histogramChart.getYAxis().setLabel("Counts");
			else
				histogramChart.getYAxis().setLabel("Counts (" + transform + ")");
		} else
			logger.warn("Histogram counts transform not supported: {}", transform);
	}


	/**
	 * Refresh the currently-displayed histogram (e.g. because underlying data has changed).
	 */
	public void refreshHistogram() {
		setHistogram(model, currentColumn);
	}


	/**
	 * Show the histogram for a specified data column.
	 * @param column the name of the column to show
	 */
	public void showHistogram(final String column) {
		if (comboName.getItems().contains(column))
			comboName.getSelectionModel().select(column);
		else
			logger.debug("Unknown column requested: {}", column);
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
			setHistogram(model, comboName.getSelectionModel().getSelectedItem());
		} else if ("animate".equals(key)) {
			histogramChart.setAnimated(paramsHistogram.getBooleanParameterValue("animate"));
		}
	}



	void updateTable(final Histogram histogram) {
		if (histogram == null) {
			List<Property<Number>> stats = new ArrayList<>();
			stats.add(new SimpleDoubleProperty(null, "Count", Double.NaN));
			stats.add(new SimpleDoubleProperty(null, "Missing", Double.NaN));
			stats.add(new SimpleDoubleProperty(null, "Mean", Double.NaN));
			stats.add(new SimpleDoubleProperty(null, "Std.Dev", Double.NaN));
			stats.add(new SimpleDoubleProperty(null, "Min", Double.NaN));
			stats.add(new SimpleDoubleProperty(null, "Max", Double.NaN));
			table.getItems().setAll(stats);
			return;
		}
		List<Property<Number>> stats = new ArrayList<>();
		stats.add(new SimpleLongProperty(null, "Count", histogram.nValues()));
		stats.add(new SimpleLongProperty(null, "Missing", histogram.nMissingValues()));
		stats.add(new SimpleDoubleProperty(null, "Mean", histogram.getMeanValue()));
		stats.add(new SimpleDoubleProperty(null, "Std.Dev", histogram.getStdDev()));
		stats.add(new SimpleDoubleProperty(null, "Min", histogram.getMinValue()));
		stats.add(new SimpleDoubleProperty(null, "Max", histogram.getMaxValue()));
		table.getItems().setAll(stats);
	}

}
