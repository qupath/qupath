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
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.chart.AreaChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.util.Callback;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.charts.HistogramPanelFX.HistogramData;
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

	final static Logger logger = LoggerFactory.getLogger(HistogramDisplay.class);

	private PathTableData<?> model;
	private BorderPane pane = new BorderPane();

	private ComboBox<String> comboName = new ComboBox<>();
	private HistogramPanelFX panelHistogram = new HistogramPanelFX();
	private ParameterPanelFX panelParams;

	private int currentBins;
	private double[] currentValues;
	private String currentColumn = null;

	private ParameterList params = new ParameterList()
			.addBooleanParameter("normalizeCounts", "Normalize counts", false, "Normalize counts (probability distribution)")
			.addBooleanParameter("drawGrid", "Draw grid", true, "Draw grid")
			.addBooleanParameter("drawAxes", "Draw axes", true, "Draw axes")
			.addIntParameter("nBins", "Number of bins", 32, null, "Number of histogram bins (>= 2 and <= 1000)")
			.addBooleanParameter("animate", "Animate changes", false, "Animate changes");
	private TableView<Property<Number>> table = new TableView<>();

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
		colName.setCellValueFactory(new Callback<CellDataFeatures<Property<Number>, String>, ObservableValue<String>>() {
			@Override
			public ObservableValue<String> call(CellDataFeatures<Property<Number>, String> p) {
				return new SimpleStringProperty(p.getValue().getName());
			}
		});
		TableColumn<Property<Number>, Number> colValue = new TableColumn<>("Value");
		colValue.setCellValueFactory(new Callback<CellDataFeatures<Property<Number>, Number>, ObservableValue<Number>>() {
			@Override
			public ObservableValue<Number> call(CellDataFeatures<Property<Number>, Number> p) {
				return p.getValue();
			}
		});
		colValue.setCellFactory(column -> {
			return new TableCell<Property<Number>, Number>() {
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
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		table.maxHeightProperty().bind(table.prefHeightProperty());


		BorderPane panelMain = new BorderPane();
		panelMain.setCenter(panelHistogram.getChart());

		comboName.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			setHistogram(model, n);
		});

		panelHistogram.setShowTickLabels(params.getBooleanParameterValue("drawAxes"));


		panelParams = new ParameterPanelFX(params);
		panelParams.addParameterChangeListener(this);
		panelParams.getPane().setPadding(new Insets(20, 5, 5, 5));
		panelParams.getPane().setMinWidth(Pane.USE_PREF_SIZE);
		updateTable(null);

		table.setPrefHeight(180);
		table.setMinWidth(100);
		table.setStyle("-fx-font-size: 0.8em");
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
	public void setNumBins(final int nBins) {
		if (panelParams != null)
			panelParams.setNumericParameterValue("nBins", nBins);
		else
			((IntParameter)params.getParameters().get("nBins")).setValue(nBins);
	}

	/**
	 * Get the requested number of bins used for the histogram.
	 * @return
	 */
	public int getNumBins() {
		return params.getIntParameterValue("nBins");
	}

	/**
	 * Get the pane containing the histogram and associated UI components, for addition to a scene.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}

	void setHistogram(final PathTableData<?> model, final String columnName) {
		if (model != null && model.getMeasurementNames().contains(columnName)) {
			double[] values = model.getDoubleValues(columnName);
			int nBins = params.getIntParameterValue("nBins");
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

			HistogramData histogramData = HistogramPanelFX.createHistogramData(histogram, false, (Integer)null);
			histogramData.setNormalizeCounts(params.getBooleanParameterValue("normalizeCounts"));
			panelHistogram.getHistogramData().setAll(histogramData);


			AreaChart<Number, Number> chart = panelHistogram.getChart();
			chart.setVerticalGridLinesVisible(true);
			chart.setHorizontalGridLinesVisible(true);
			chart.setLegendVisible(false);
			chart.setCreateSymbols(false); // Can't stop them being orange...
			chart.getXAxis().setLabel("Values");
			chart.getYAxis().setLabel("Counts");
			chart.getYAxis().setTickLabelsVisible(true);
			chart.getYAxis().setTickMarkVisible(true);
			chart.getXAxis().setTickLabelsVisible(true);
			chart.getXAxis().setTickMarkVisible(true);
			
			chart.setAnimated(params.getBooleanParameterValue("animate"));

			updateTable(histogram);

			currentColumn = columnName;
			currentBins = nBins;
			currentValues = values;
			this.model = model;
		} else
			panelHistogram.getHistogramData().clear();
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
		if ("normalizeCounts".equals(key)) {
			boolean doNormalize = params.getBooleanParameterValue("normalizeCounts");
			// This is rather clumsy (compared to just updating the histogram data),
			// but the reason is that the animations are poor when the data is updated in-place
			List<HistogramData> list = new ArrayList<>();
			for (HistogramData histogramData : panelHistogram.getHistogramData()) {
				histogramData.setNormalizeCounts(doNormalize);
				list.add(new HistogramData(histogramData.getHistogram(), false, histogramData.getStroke()));
				//					histogramData.update();
			}
			panelHistogram.getHistogramData().setAll(list);
			return;
		} else if ("drawGrid".equals(key)) {
			panelHistogram.getChart().setHorizontalGridLinesVisible(params.getBooleanParameterValue("drawGrid"));
			panelHistogram.getChart().setVerticalGridLinesVisible(params.getBooleanParameterValue("drawGrid"));
			return;
		} else if ("drawAxes".equals(key)) {
			panelHistogram.setShowTickLabels(params.getBooleanParameterValue("drawAxes"));
			return;
		} else if ("nBins".equals(key)) {
			setHistogram(model, comboName.getSelectionModel().getSelectedItem());
			return;
		} else if ("animate".equals(key)) {
			panelHistogram.getChart().setAnimated(params.getBooleanParameterValue("animate"));
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