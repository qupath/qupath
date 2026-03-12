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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.Axis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;
import org.controlsfx.control.PropertySheet.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.fx.prefs.controlsfx.PropertySheetUtils;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.io.GsonTools;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for displaying a chart in an export-friendly way.
 * 
 * Options are provided to adjust labels, legends etc. and to save or copy the chart
 * as an image.
 * 
 * @author Pete Bankhead
 *
 */
class ExportChartPane {

	private static final Logger logger = LoggerFactory.getLogger(ExportChartPane.class);

	public static enum ChartStyle {
		COLOR("Color"), GRAY("Gray"), BLACK("Black");

		private String name;

		ChartStyle(final String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
		
		private String getResourceName() {
			switch (this) {
			case BLACK:
				return "css/charts/chart_style_black.css";
			case COLOR:
				return "css/charts/chart_style_color.css";
			case GRAY:
				return "css/charts/chart_style_gray.css";
			default:
				return null;
			}
		}

		public String getStylesheet() {
			return getClass().getClassLoader().getResource(getResourceName()).toExternalForm();
		}
		
		
		/**
		 * Request a stylesheet adapted to support a specified number of series.
		 * 
		 * @param nSeries
		 * @return
		 */
		public String getStylesheet(final int nSeries) {
			String resourceName = getResourceName();
			String alternative = resourceName.replace(".css", nSeries+".css");
			URL url = getClass().getClassLoader().getResource(alternative);
			if (url == null)
				return getStylesheet();
			else
				return url.toExternalForm();
		}

		//		@Override
		//		public String toString() {
		//			return name;
		//		}

	};

	public static enum ChartStrokeWidth {
		NONE("None"), THINNEST("Thinnest"), THIN("Thin"), MODERATE("Moderate"), THICK("Thick"), THICKEST("Thickest");

		private String name;

		ChartStrokeWidth(final String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public String getStylesheet() {
			switch (this) {
			case MODERATE:
				return getClass().getClassLoader().getResource("css/charts/chart_stroke_moderate.css").toExternalForm();
			case NONE:
				return getClass().getClassLoader().getResource("css/charts/chart_stroke_none.css").toExternalForm();
			case THICK:
				return getClass().getClassLoader().getResource("css/charts/chart_stroke_thick.css").toExternalForm();
			case THICKEST:
				return getClass().getClassLoader().getResource("css/charts/chart_stroke_thickest.css").toExternalForm();
			case THIN:
				return getClass().getClassLoader().getResource("css/charts/chart_stroke_thin.css").toExternalForm();
			case THINNEST:
				return getClass().getClassLoader().getResource("css/charts/chart_stroke_thinnest.css").toExternalForm();
			default:
				return null;
			}
		}
		
		/**
		 * Request a stylesheet adapted to support a specified number of series.
		 * 
		 * (Currently, this always returns the same, default stylesheet.  But in the future it may not...)
		 * 
		 * @param nSeries
		 * @return
		 */
		public String getStylesheet(final int nSeries) {
			return getStylesheet();
		}

		//		@Override
		//		public String toString() {
		//			return name;
		//		}

	};

	public static enum ExportResolution {
		LOW("Low", 1), MEDIUM("Medium", 2), HIGH("High", 4), HIGHEST("Highest", 8);

		private String name;
		private double scale;

		ExportResolution(final String name, final double scale) {
			this.name = name;
			this.scale = scale;
		}

		public String getName() {
			return name;
		}

		public double getScale() {
			return scale;
		}

		//		@Override
		//		public String toString() {
		//			return name;
		//		}

	};

	private static ObjectProperty<Mode> persistentModeProperty = new SimpleObjectProperty<>(Mode.NAME);
	private static BooleanProperty persistentSolidLinesProperty = new SimpleBooleanProperty(false);
	private static ObjectProperty<ChartStyle> persistentChartStyleProperty = new SimpleObjectProperty<>(ChartStyle.COLOR);
	private static ObjectProperty<ChartStrokeWidth> persistentStrokeWidthProperty = new SimpleObjectProperty<>(ChartStrokeWidth.MODERATE);
	private static IntegerProperty requestedWidthProperty = new SimpleIntegerProperty(); //PathPrefs.createPersistentPreference("chartRequestedWidth", 0);
	private static IntegerProperty requestedHeightProperty = new SimpleIntegerProperty(); //PathPrefs.createPersistentPreference("chartRequestedHeight", 0);

	private static ObservableList<String> storedPrefs = FXCollections.observableArrayList();
	
	private ObjectProperty<ChartStrokeWidth> strokeWidthProperty = new SimpleObjectProperty<>(ChartStrokeWidth.MODERATE);
	private ObjectProperty<ChartStyle> chartStyleProperty = new SimpleObjectProperty<>(persistentChartStyleProperty.get());
	private ObjectProperty<ExportResolution> exportResolutionProperty = new SimpleObjectProperty<>(ExportResolution.LOW);

	private BorderPane pane = new BorderPane();
	private ScrollPane scrollPane;
	private Chart chart;
	PropertySheet sheet = PropertySheetUtils.createDefaultPropertySheet();

	private IntegerProperty requestedWidth = new SimpleIntegerProperty(requestedWidthProperty.get());
	private IntegerProperty requestedHeight = new SimpleIntegerProperty(requestedHeightProperty.get());

	private static BooleanProperty useSolidLines = new SimpleBooleanProperty(persistentSolidLinesProperty.get());

	public ExportChartPane(final Chart chart) {
		this.chart = chart;

		Button btnCopy = new Button(QuPathResources.getString("Charts.ExportChartPane.copy"));
		btnCopy.setOnAction(e -> {
			Image img = getChartImage();
			ClipboardContent content = new ClipboardContent();
			content.putImage(img);
			Clipboard.getSystemClipboard().setContent(content);
		});

		Button btnSave = new Button(QuPathResources.getString("Charts.ExportChartPane.save"));
		btnSave.setOnAction(e -> {
			Image img = getChartImage();
			String title = chart.getTitle() == null || chart.getTitle().isEmpty() ? null : chart.getTitle();
			Window owner = chart.getScene() == null ? null : chart.getScene().getWindow();
			File fileOutput = FileChoosers.promptToSaveFile(
					owner,
					QuPathResources.getString("Charts.ExportChartPane.saveChart"),
					title == null ? null : new File(title),
					FileChoosers.createExtensionFilter("PNG", ".png")
			);
			if (fileOutput != null) {
				try {
					ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", fileOutput);
				} catch (Exception e1) {
					Dialogs.showErrorMessage(QuPathResources.getString("Charts.ExportChartPane.saveChartError"), e1);
					logger.error(e1.getMessage(), e1);
				}
			}
		});

		Pane paneButtons = GridPaneUtils.createColumnGridControls(btnCopy, btnSave);
		paneButtons.setPadding(new Insets(5, 5, 5, 5));

		// Listen for changes to stroke setting
		useSolidLines.addListener(v -> updateChartStyles());

		//		chart.getStylesheets()

		scrollPane = new ScrollPane(chart);
		scrollPane.setStyle("-fx-background: white;");
		BorderPane paneMain = new BorderPane();
		paneMain.setCenter(scrollPane);
		paneMain.setBottom(paneButtons);
		pane.setCenter(paneMain);

		updateChartStyles();

		requestedWidth.addListener(v -> refreshChartDisplay());
		requestedHeight.addListener(v -> refreshChartDisplay());

		sheet.modeProperty().addListener((v, o, n) -> persistentModeProperty.set(n));

		sheet.getItems().addAll(
				new PropertyItemBuilder<>(chartStyleProperty, ChartStyle.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.style"))
						.description(QuPathResources.getString("Charts.ExportChartPane.styleDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.display"))
						.build(),
				new PropertyItemBuilder<>(chart.titleProperty(), String.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.title"))
						.description(QuPathResources.getString("Charts.ExportChartPane.titleDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.title"))
						.build(),
				new PropertyItemBuilder<>(chart.titleSideProperty(), Side.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.titleSide"))
						.description(QuPathResources.getString("Charts.ExportChartPane.titleSideDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.title"))
						.build(),
				new PropertyItemBuilder<>(chart.legendVisibleProperty(), Boolean.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.showLegend"))
						.description(QuPathResources.getString("Charts.ExportChartPane.showLegendDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.legend"))
						.build(),
				new PropertyItemBuilder<>(chart.legendSideProperty(), Side.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.legendPosition"))
						.description(QuPathResources.getString("Charts.ExportChartPane.legendPositionDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.legend"))
						.build()
		);

		if (chart instanceof LineChart<?, ?> lineChart) {
            sheet.getItems().addAll(
					new PropertyItemBuilder<>(strokeWidthProperty, ChartStrokeWidth.class)
							.name(QuPathResources.getString("Charts.ExportChartPane.lineWidth"))
							.description(QuPathResources.getString("Charts.ExportChartPane.lineWidthDescription"))
							.category(QuPathResources.getString("Charts.ExportChartPane.display"))
							.build(),
					new PropertyItemBuilder<>(useSolidLines, Boolean.class)
							.name(QuPathResources.getString("Charts.ExportChartPane.solidLines"))
							.description(QuPathResources.getString("Charts.ExportChartPane.solidLinesDescription"))
							.category(QuPathResources.getString("Charts.ExportChartPane.display"))
							.build(),
					// Warning! Toggling on and off symbols changes any special efforts that went into creating them,
					// e.g. censored ticks for survival curves
					new PropertyItemBuilder<>(lineChart.createSymbolsProperty(), Boolean.class)
							.name(QuPathResources.getString("Charts.ExportChartPane.useMarkers"))
							.description(QuPathResources.getString("Charts.ExportChartPane.useMarkersDescription"))
							.category(QuPathResources.getString("Charts.ExportChartPane.display"))
							.build()
			);
		}

		if (chart instanceof XYChart<?, ?> xyChart) {
            sheet.getItems().addAll(
					new PropertyItemBuilder<>(xyChart.horizontalGridLinesVisibleProperty(), Boolean.class)
							.name(QuPathResources.getString("Charts.ExportChartPane.horizontalGridLines"))
							.description(QuPathResources.getString("Charts.ExportChartPane.horizontalGridLinesDescription"))
							.category(QuPathResources.getString("Charts.ExportChartPane.grid"))
							.build(),
					new PropertyItemBuilder<>(xyChart.horizontalZeroLineVisibleProperty(), Boolean.class)
							.name(QuPathResources.getString("Charts.ExportChartPane.horizontalZeroLine"))
							.description(QuPathResources.getString("Charts.ExportChartPane.horizontalZeroLineDescription"))
							.category(QuPathResources.getString("Charts.ExportChartPane.grid"))
							.build(),
					new PropertyItemBuilder<>(xyChart.verticalGridLinesVisibleProperty(), Boolean.class)
							.name(QuPathResources.getString("Charts.ExportChartPane.verticalGridLines"))
							.description(QuPathResources.getString("Charts.ExportChartPane.verticalGridLinesDescription"))
							.category(QuPathResources.getString("Charts.ExportChartPane.grid"))
							.build(),
					new PropertyItemBuilder<>(xyChart.verticalZeroLineVisibleProperty(), Boolean.class)
							.name(QuPathResources.getString("Charts.ExportChartPane.verticalZeroLine"))
							.description(QuPathResources.getString("Charts.ExportChartPane.verticalZeroLineDescription"))
							.category(QuPathResources.getString("Charts.ExportChartPane.grid"))
							.build()
			);

			if (xyChart.getXAxis() instanceof NumberAxis axis) {
				sheet.getItems().addAll(
						new PropertyItemBuilder<>(axis.labelProperty(), String.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.xAxisLabel"))
								.description(QuPathResources.getString("Charts.ExportChartPane.xAxisLabelDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.xAxis"))
								.build(),
						new PropertyItemBuilder<>(axis.autoRangingProperty(), Boolean.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.xAxisAutorange"))
								.description(QuPathResources.getString("Charts.ExportChartPane.xAxisAutorangeDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.xAxis"))
								.build(),
						new PropertyItemBuilder<>(axis.lowerBoundProperty(), Double.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.xLowerBound"))
								.description(QuPathResources.getString("Charts.ExportChartPane.xLowerBoundDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.xAxis"))
								.build(),
						new PropertyItemBuilder<>(axis.upperBoundProperty(), Double.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.xUpperBound"))
								.description(QuPathResources.getString("Charts.ExportChartPane.xUpperBoundDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.xAxis"))
								.build(),
						new PropertyItemBuilder<>(axis.tickUnitProperty(), Double.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.xTickUnit"))
								.description(QuPathResources.getString("Charts.ExportChartPane.xTickUnitDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.xAxis"))
								.build()
				);

				int counter = 0;
				for (Series<?, ?> series : xyChart.getData()) {
					counter++;
					if (!series.nameProperty().isBound()) {
						sheet.getItems().add(new PropertyItemBuilder<>(series.nameProperty(), String.class)
								.name(MessageFormat.format(
										QuPathResources.getString("Charts.ExportChartPane.seriesName"),
										counter
								))
								.description(QuPathResources.getString("Charts.ExportChartPane.seriesNameDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.series"))
								.build()
						);
					}
				}
			} else
				sheet.getItems().add(new PropertyItemBuilder<>(xyChart.getXAxis().labelProperty(), String.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.xAxisLabel"))
						.description(QuPathResources.getString("Charts.ExportChartPane.xAxisLabelDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.xAxis"))
						.build()
				);

			if (xyChart.getYAxis() instanceof NumberAxis axis) {
				sheet.getItems().addAll(
						new PropertyItemBuilder<>(axis.labelProperty(), String.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.yAxisLabel"))
								.description(QuPathResources.getString("Charts.ExportChartPane.yAxisLabelDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.yAxis"))
								.build(),
						new PropertyItemBuilder<>(axis.autoRangingProperty(), Boolean.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.yAxisAutorange"))
								.description(QuPathResources.getString("Charts.ExportChartPane.yAxisAutorangeDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.yAxis"))
								.build(),
						new PropertyItemBuilder<>(axis.lowerBoundProperty(), Double.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.yLowerBound"))
								.description(QuPathResources.getString("Charts.ExportChartPane.yLowerBoundDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.yAxis"))
								.build(),
						new PropertyItemBuilder<>(axis.upperBoundProperty(), Double.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.yUpperBound"))
								.description(QuPathResources.getString("Charts.ExportChartPane.yUpperBoundDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.yAxis"))
								.build(),
						new PropertyItemBuilder<>(axis.tickUnitProperty(), Double.class)
								.name(QuPathResources.getString("Charts.ExportChartPane.yTickUnit"))
								.description(QuPathResources.getString("Charts.ExportChartPane.yTickUnitDescription"))
								.category(QuPathResources.getString("Charts.ExportChartPane.yAxis"))
								.build()
				);
			} else
				sheet.getItems().add(new PropertyItemBuilder<>(xyChart.getYAxis().labelProperty(), String.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.yAxisLabel"))
						.description(QuPathResources.getString("Charts.ExportChartPane.yAxisLabelDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.yAxis"))
						.build()
				);
		}

		sheet.getItems().addAll(
				new PropertyItemBuilder<>(exportResolutionProperty, ExportResolution.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.exportResolution"))
						.description(QuPathResources.getString("Charts.ExportChartPane.exportResolutionDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.export"))
						.build(),
				new PropertyItemBuilder<>(requestedWidth, Integer.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.width"))
						.description(QuPathResources.getString("Charts.ExportChartPane.widthDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.export"))
						.build(),
				new PropertyItemBuilder<>(requestedHeight, Integer.class)
						.name(QuPathResources.getString("Charts.ExportChartPane.height"))
						.description(QuPathResources.getString("Charts.ExportChartPane.heightDescription"))
						.category(QuPathResources.getString("Charts.ExportChartPane.export"))
						.build()
		);

		chartStyleProperty.addListener(o -> updateChartStyles());
		strokeWidthProperty.addListener(o -> updateChartStyles());

		//		sheet.setMode(Mode.CATEGORY);

		BorderPane paneLeft = new BorderPane();
		paneLeft.setTop(initializePrefsManager());
		paneLeft.setCenter(sheet);
		pane.setLeft(paneLeft);

		refreshChartDisplay();
		
		updateStoredPrefs();
	}



	/**
	 * Get a Image of the Chart, scaled according to the requested resolution as required.
	 * 
	 * @return
	 */
	public Image getChartImage() {
		// Create a snapshot at the requested resolution
		double scale = exportResolutionProperty.get().getScale();
		int w = (int)(chart.getWidth() * scale + 0.5);
		int h = (int)(chart.getHeight() * scale + 0.5);
		SnapshotParameters params = new SnapshotParameters();
		params.setTransform(Transform.scale(scale, scale));
		return chart.snapshot(params, new WritableImage(w, h));
	}



	/**
	 * Create and display an export chart panel.
	 * 
	 * @param chart The chart to display.
	 * @param duplicator A duplicator (optional) to create a duplicate chart, rather than using the original.
	 * @return
	 */
	public static ExportChartPane showExportChartDialog(final Chart chart, final Callback<Chart, Chart> duplicator) {
		ExportChartPane panel = new ExportChartPane(duplicator == null ? chart : duplicator.call(chart));
		Scene scene = new Scene(panel.getPane());
		Stage stage = new Stage();
		FXUtils.addCloseWindowShortcuts(stage);
		stage.setTitle(QuPathResources.getString("Charts.ExportChartPane.exportChart"));
		stage.setScene(scene);
		stage.show();
		panel.refreshChartDisplay();
		return panel;
	}

	/**
	 * Create and display an export chart panel.
	 * 
	 * The supplied chart will be duplicated first, if possible.
	 * 
	 * @param chart
	 */
	public static void showExportChartDialog
	(final XYChart<Number, Number> chart) {
		try {
			XYChart<Number, Number> chart2 = copyChart(chart);
			showExportChartDialog(chart2, null);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			Dialogs.showErrorNotification(
					QuPathResources.getString("Charts.ExportChartPane.exportChartError"),
					QuPathResources.getString("Charts.ExportChartPane.unableDuplicateChart")
			);
			logger.error("Error duplicating chart", e);
		}
	}


	public void refreshChartDisplay() {
		if (chart == null || chart.getScene() == null || chart.getScene().getWindow() == null) {
			if (chart != null)
				chart.autosize();
			return;
		}

		int w = GeneralTools.clipValue(requestedWidth.get(), 5, 5000);
		int h = GeneralTools.clipValue(requestedHeight.get(), 5, 5000);

		if (requestedWidth.get() <= 0)
			scrollPane.setFitToWidth(true);
		else {
			scrollPane.setFitToWidth(false);
			chart.setPrefWidth(w);
		}

		if (requestedHeight.get() <= 0)
			scrollPane.setFitToHeight(true);
		else {
			scrollPane.setFitToHeight(false);
			chart.setPrefHeight(h);
		}

		chart.autosize();
		chart.requestLayout();

		// Store for next time
		requestedWidthProperty.set(requestedWidth.get());
		requestedHeightProperty.set(requestedHeight.get());

		////		chart.setLegendSide(value);
		//		Region legend = (Region)chart.lookup(".chart-legend");
		//		chart.setLegendVisible(true);
		//		double dx = -legend.getWidth()-5;
		////		if (dx != legend.getTranslateX()) {
		//			legend.setTranslateX(dx);
		//			chart.setLegendVisible(false);
		//			chart.requestLayout();
		//			chart.autosize();
		//			legend.setVisible(true);
		////			return;
		////		}
		////		legend.setTranslateY(legend.getHeight()-20);
		////		chart.requestLayout();


		updateChartStyles();
	}


	private void updateChartStyles() {
		
		int n = chart instanceof XYChart<?, ?> ? ((XYChart<?, ?>)chart).getData().size() : 1;
		
		List<String> stylesheets = new ArrayList<>();
		stylesheets.add(getClass().getClassLoader().getResource("css/charts/chart_base.css").toExternalForm());
		stylesheets.add(chartStyleProperty.get().getStylesheet(n));
		stylesheets.add(strokeWidthProperty.get().getStylesheet(n));
		if (!useSolidLines.get())
			stylesheets.add(getClass().getClassLoader().getResource("css/charts/chart_strokes_dashed.css").toExternalForm());


		chart.getStylesheets().setAll(stylesheets);
		
		// Set the legend to be more appropriate for a line chart
		if (chart instanceof LineChart<?, ?> && chart.isLegendVisible())
			ChartTools.setLineChartLegendLines(chart, 25);

		persistentStrokeWidthProperty.set(strokeWidthProperty.get());
		persistentChartStyleProperty.set(chartStyleProperty.get());
		persistentSolidLinesProperty.set(useSolidLines.get());
	}


	public Pane getPane() {
		return pane;
	}

	
	private static synchronized void updateStoredPrefs() {
		List<String> prefs = new ArrayList<>();
		try {
			PathPrefs.getUserPreferences().sync();
			for (String pref : PathPrefs.getUserPreferences().keys()) {
				if (pref.startsWith(EXPORT_CHART_PREFS_KEY))
					prefs.add(pref.substring(EXPORT_CHART_PREFS_KEY.length()));
			}
		} catch (Exception e) {
			logger.error("Problem reading preferences", e);
		}
		if (!storedPrefs.equals(prefs))
			storedPrefs.setAll(prefs);
	}
	

	public static XYChart<Number, Number> copyChart(final XYChart<Number, Number> chart) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

		@SuppressWarnings("unchecked")
		XYChart<Number, Number> chart2 = (XYChart<Number, Number>)chart.getClass().getConstructor(Axis.class, Axis.class).newInstance(new NumberAxis(), new NumberAxis());

		// Set the axis appropriately
		setAxisProperties(chart.getXAxis(), chart2.getXAxis());
		setAxisProperties(chart.getYAxis(), chart2.getYAxis());

		// Add all the data
		for (Series<Number, Number> series : chart.getData()) {
			Series<Number, Number> series2 = new Series<>();
			series2.setName(series.getName());
			for (Data<Number, Number> data : series.getData()) {
				series2.getData().add(new Data<>(data.getXValue(), data.getYValue(), data.getExtraValue()));
			}
			chart2.getData().add(series2);

			//			// Set visibility of nodes, as required
			//			int counter = 0;
			//			for (Data<Number, Number> data : series.getData()) {
			//				if (data.getNode() != null && series2.getData().get(counter).getNode() != null) {
			//					series2.getData().get(counter).getNode().setVisible(data.getNode().isVisible());
			//				}
			//			}
		}

		// Set other main properties
		chart2.setTitle(chart.getTitle());
		chart2.setStyle(chart.getStyle());

		return chart2;

	}


	private static void setAxisProperties(final Axis<Number> axisOrig, final Axis<Number> axisNew) {
		if (axisOrig instanceof NumberAxis && axisNew instanceof NumberAxis) {
			NumberAxis nAxisOrig = (NumberAxis)axisOrig;
			NumberAxis nAxisNew = (NumberAxis)axisNew;
			nAxisNew.setLowerBound(nAxisOrig.getLowerBound());
			nAxisNew.setUpperBound(nAxisOrig.getUpperBound());
			nAxisNew.setTickUnit(nAxisOrig.getTickUnit());
		}
		axisNew.setAutoRanging(axisOrig.isAutoRanging());
		axisNew.setLabel(axisOrig.getLabel());
	}



	private static String EXPORT_CHART_PREFS_KEY = "chart.export.";

	/**
	 * Save export preferences for another occasion.
	 * 
	 * @param items
	 * @param name
	 * @throws IOException
	 */
	static void saveExportPreferences(final List<Item> items, final String name) throws IOException {
		Map<String, Serializable> map = getPreferenceMap(items);
		
		var json = GsonTools.getInstance(false).toJson(map);
		// TODO: Next line might break as Preferences values' size is limited to 0.75*MAX_VALUE_LENGTH 
		PathPrefs.getUserPreferences().put(EXPORT_CHART_PREFS_KEY + name, json);
	}

	/**
	 * Load & apply previously-saved preferences
	 * 
	 * @param items
	 * @param name
	 * @return 
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	static boolean loadExportPreferences(final List<Item> items, final String name) throws IOException, ClassNotFoundException {

		String json = PathPrefs.getUserPreferences().get(EXPORT_CHART_PREFS_KEY + name, null);
		if (json == null)
			return false;
		
		Map<?, ?> map = GsonTools.getInstance(false).fromJson(json, Map.class);
		
		if (map == null)
			return false;
		
		int count = 0;
		for (Item item : items) {
			// Don't want to load Series properties - these might vary
			if (QuPathResources.getString("Charts.ExportChartPane.series").equals(item.getCategory()))
				continue;
			Object value = map.getOrDefault(item.getName(), null);
			if (value != null) {
				try {
					var cls = item.getType();
					if (cls.isInstance(value))
						item.setValue(value);
					else if (cls.isEnum() && value instanceof String) {
						item.setValue(Enum.valueOf((Class<? extends Enum>)cls, (String)value));
					}
				} catch (Exception e) {
					logger.warn(e.getLocalizedMessage(), e);
				}
				count++;
			}
		}
		logger.debug("{} preferences set", count);

		return true;
	}

	
	
	

	/**
	 * Get a map representing all the current export preferences with Serializable values.
	 * 
	 * @param items
	 * @return
	 */
	static Map<String, Serializable> getPreferenceMap(final List<Item> items) {
		Map<String, Serializable> prefsMap = new LinkedHashMap<>();
		for (Item item : items) {
			String name = item.getName();
			Object value = item.getValue();
			if (value instanceof Serializable)
				prefsMap.put(name, (Serializable)value);
		}
		return prefsMap;
	}

//	/**
//	 * Apply a map of preferences to the current export preference items list.
//	 * 
//	 * @param items
//	 * @param prefsMap
//	 */
//	static void applyPreferenceMap(final List<Item> items, final Map<String, Serializable> prefsMap) {
//		// Create a map for easy access to items
//		Map<String, Item> itemMap = new HashMap<>();
//		for (Item item : items) {
//			itemMap.put(item.getName(), item);
//		}
//
//		// Set the value of each item
//		for (Entry<String, Serializable> entry : prefsMap.entrySet()) {
//			Item item = itemMap.get(entry.getKey());
//			if (item == null)
//				continue;
//			item.setValue(entry.getValue());
//		}
//	}

	
	Node initializePrefsManager() {
		
		ComboBox<String> combo = new ComboBox<>(storedPrefs);
		combo.setTooltip(new Tooltip(QuPathResources.getString("Charts.ExportChartPane.previouslyStoredPreferences")));
		combo.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (n != null) {
				try {
					loadExportPreferences(sheet.getItems(), n);
				} catch (Exception e) {
					Dialogs.showErrorMessage(
							QuPathResources.getString("Charts.ExportChartPane.errorLoadingPrefs"),
							MessageFormat.format(
									QuPathResources.getString("Charts.ExportChartPane.errorLoadingPrefsMessage"),
									n
							)
					);
					logger.error("Error loading prefs", e);
				}
			}
		});
		
		Button btnAdd = new Button(QuPathResources.getString("Charts.ExportChartPane.add"));
		btnAdd.setTooltip(new Tooltip(QuPathResources.getString("Charts.ExportChartPane.addCurrentPreferences")));
		btnAdd.setOnAction(e -> {
			String name = Dialogs.showInputDialog(
					QuPathResources.getString("Charts.ExportChartPane.exportChart"),
					QuPathResources.getString("Charts.ExportChartPane.enterNameForPreferences"),
					chart.getTitle()
			);
			if (name != null && !name.trim().isEmpty()) {
				try {
					saveExportPreferences(sheet.getItems(), name);
					updateStoredPrefs();
					combo.getSelectionModel().select(name);
				} catch (Exception e1) {
					Dialogs.showErrorMessage(
							QuPathResources.getString("Charts.ExportChartPane.errorSavingPrefs"),
							MessageFormat.format(
									QuPathResources.getString("Charts.ExportChartPane.errorSavingPrefsMessage"),
									name
							));
					logger.error("Error saving prefs", e1);
				}
			}
		});

		Button btnRemove = new Button(QuPathResources.getString("Charts.ExportChartPane.remove"));
		btnRemove.setTooltip(new Tooltip(QuPathResources.getString("Charts.ExportChartPane.removeCurrentPreferences")));
		btnRemove.setOnAction(e -> {
			String selected = combo.getSelectionModel().getSelectedItem();
			if (selected != null && !selected.trim().isEmpty()) {
				if (Dialogs.showConfirmDialog(
						QuPathResources.getString("Charts.ExportChartPane.removeExportPrefs"),
						MessageFormat.format(
								QuPathResources.getString("Charts.ExportChartPane.removeX"),
								selected
						)
				)) {
					PathPrefs.getUserPreferences().remove(EXPORT_CHART_PREFS_KEY + selected);
					updateStoredPrefs();
				}
			}
		});
		
		
		BorderPane pane = new BorderPane();
		combo.setMaxWidth(Double.MAX_VALUE);
		pane.setCenter(combo);
		pane.setBottom(GridPaneUtils.createColumnGridControls(btnAdd, btnRemove));
		
		TitledPane titledPane = new TitledPane(QuPathResources.getString("Charts.ExportChartPane.presets"), pane);
		titledPane.setCollapsible(false);
		return titledPane;
	}



}
