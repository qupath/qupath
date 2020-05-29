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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;
import org.controlsfx.control.PropertySheet.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import javafx.util.Callback;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.PreferencePane;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.PaneTools;

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

	private final static Logger logger = LoggerFactory.getLogger(ExportChartPane.class);

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
	PropertySheet sheet = new PropertySheet();

	private IntegerProperty requestedWidth = new SimpleIntegerProperty(requestedWidthProperty.get());
	private IntegerProperty requestedHeight = new SimpleIntegerProperty(requestedHeightProperty.get());

	private static BooleanProperty useSolidLines = new SimpleBooleanProperty(persistentSolidLinesProperty.get());

	public ExportChartPane(final Chart chart) {
		this.chart = chart;

		//				Node node = chart2.lookup(".chart-legend");
		//				System.err.println(node);
		//				Node node = chart2.lookup(".chart-legend");
		//				System.err.println(((Region)node).getChildrenUnmodifiable());

		Button btnCopy = new Button("Copy");
		btnCopy.setOnAction(e -> {
			Image img = getChartImage();
			ClipboardContent content = new ClipboardContent();
			content.putImage(img);
			Clipboard.getSystemClipboard().setContent(content);
		});

		Button btnSave = new Button("Save");
		btnSave.setOnAction(e -> {
			Image img = getChartImage();
			String title = chart.getTitle() == null || chart.getTitle().isEmpty() ? null : chart.getTitle();
			File fileOutput = Dialogs.getChooser(chart.getScene() == null ? null : chart.getScene().getWindow()).promptToSaveFile("Save chart", null, title, "PNG", ".png");
			if (fileOutput != null) {
				try {
					ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", fileOutput);
				} catch (Exception e1) {
					Dialogs.showErrorMessage("Save chart error", e1);
				}
			}
		});

		Pane paneButtons = PaneTools.createColumnGridControls(btnCopy, btnSave);
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
		sheet.setPropertyEditorFactory(new PreferencePane.PropertyEditorFactory());
		
		sheet.getItems().addAll(
				PreferencePane.createPropertySheetItem(chartStyleProperty, ChartStyle.class).name("Style").description("Color style for chart display").category("Display"),
				PreferencePane.createPropertySheetItem(chart.titleProperty(), String.class).name("Title").description("Chart title").category("Title"),
				PreferencePane.createPropertySheetItem(chart.titleSideProperty(), Side.class).name("Title side").description("Location of title").category("Title"),
				PreferencePane.createPropertySheetItem(chart.legendVisibleProperty(), Boolean.class).name("Show legend").description("Show chart legend").category("Legend"),
				PreferencePane.createPropertySheetItem(chart.legendSideProperty(), Side.class).name("Legend position").description("Position to display legend, relative to the chart").category("Legend")
				);

		if (chart instanceof LineChart<?, ?>) {
			LineChart<?, ?> lineChart = (LineChart<?, ?>)chart;
			sheet.getItems().addAll(
					PreferencePane.createPropertySheetItem(strokeWidthProperty, ChartStrokeWidth.class).name("Line width").description("Thickness of lines used to draw on the chart").category("Display"),
					PreferencePane.createPropertySheetItem(useSolidLines, Boolean.class).name("Solid lines").description("Use solid (rather than dashed) lines for all series").category("Display"),
					// Warning! Toggling on and off symbols changes any special efforts that went into creating them,
					// e.g. censored ticks for survival curves
					PreferencePane.createPropertySheetItem(lineChart.createSymbolsProperty(), Boolean.class).name("Use markers").description("Use markers to indicate each data point").category("Display")
					);
		}

		if (chart instanceof XYChart) {
			XYChart<?, ?> xyChart = (XYChart<?, ?>)chart;

			sheet.getItems().addAll(
					PreferencePane.createPropertySheetItem(xyChart.horizontalGridLinesVisibleProperty(), Boolean.class).name("Horizontal grid lines").description("Display horizontal grid lines").category("Grid"),
					PreferencePane.createPropertySheetItem(xyChart.horizontalZeroLineVisibleProperty(), Boolean.class).name("Horizontal zero line").description("Display horizontal zero line").category("Grid"),
					PreferencePane.createPropertySheetItem(xyChart.verticalGridLinesVisibleProperty(), Boolean.class).name("Vertical grid lines").description("Display vertical grid lines").category("Grid"),
					PreferencePane.createPropertySheetItem(xyChart.verticalZeroLineVisibleProperty(), Boolean.class).name("Vertical zero line").description("Display vertical zero line").category("Grid")
					);

			if (xyChart.getXAxis() instanceof NumberAxis) {
				NumberAxis axis = (NumberAxis)xyChart.getXAxis();
				sheet.getItems().addAll(
						PreferencePane.createPropertySheetItem(axis.labelProperty(), String.class).name("X axis label").description("X axis label").category("X axis"),
						PreferencePane.createPropertySheetItem(axis.autoRangingProperty(), Boolean.class).name("X axis autorange").description("Set X axis range automatically").category("X axis"),
						PreferencePane.createPropertySheetItem(axis.lowerBoundProperty(), Double.class).name("X lower bound").description("X lower bound").category("X axis"),
						PreferencePane.createPropertySheetItem(axis.upperBoundProperty(), Double.class).name("X upper bound").description("X upper bound").category("X axis"),
						PreferencePane.createPropertySheetItem(axis.tickUnitProperty(), Double.class).name("X tick unit").description("Spacing between ticks on x axis").category("X axis")
						);

				int counter = 0;
				for (Series<?, ?> series : xyChart.getData()) {
					counter++;
					if (!series.nameProperty().isBound()) {
						sheet.getItems().addAll(
								PreferencePane.createPropertySheetItem(series.nameProperty(), String.class).name("Series name " + counter + ":").description("Name of the data in the chart (will be used for legend)").category("Series")
								);
					}
				}


			} else
				sheet.getItems().add(PreferencePane.createPropertySheetItem(xyChart.getXAxis().labelProperty(), String.class).name("X axis label").description("X axis label").category("X axis"));

			if (xyChart.getYAxis() instanceof NumberAxis) {
				NumberAxis axis = (NumberAxis)xyChart.getYAxis();
				sheet.getItems().addAll(
						PreferencePane.createPropertySheetItem(axis.labelProperty(), String.class).name("Y axis label").description("Y axis label").category("Y axis"),
						PreferencePane.createPropertySheetItem(axis.autoRangingProperty(), Boolean.class).name("Y axis autorange").description("Set Y axis range automatically").category("Y axis"),
						PreferencePane.createPropertySheetItem(axis.lowerBoundProperty(), Double.class).name("Y lower bound").description("Y lower bound").category("Y axis"),
						PreferencePane.createPropertySheetItem(axis.upperBoundProperty(), Double.class).name("Y upper bound").description("Y upper bound").category("Y axis"),
						PreferencePane.createPropertySheetItem(axis.tickUnitProperty(), Double.class).name("Y tick unit").description("Spacing between ticks on y axis").category("Y axis")
						);
			} else
				sheet.getItems().add(PreferencePane.createPropertySheetItem(xyChart.getYAxis().labelProperty(), String.class).name("Y axis label").description("Y axis label").category("Y axis"));

		}

		sheet.getItems().addAll(
				PreferencePane.createPropertySheetItem(exportResolutionProperty, ExportResolution.class).name("Export resolution").description("Resolution at which to copy/save the chart").category("Export"),
				PreferencePane.createPropertySheetItem(requestedWidth, Integer.class).name("Width").description("Requested chart width").category("Export"),
				PreferencePane.createPropertySheetItem(requestedHeight, Integer.class).name("Height").description("Requested chart height").category("Export")
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
		stage.setTitle("Export chart");
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
			Dialogs.showErrorNotification("Export chart display error", "Unable to duplicate chart");
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

	
	private synchronized static void updateStoredPrefs() {
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
				series2.getData().add(new Data<Number, Number>(data.getXValue(), data.getYValue(), data.getExtraValue()));
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
		try (ByteArrayOutputStream b = new ByteArrayOutputStream()){
			try (ObjectOutputStream o = new ObjectOutputStream(b)){
				o.writeObject(map);
			}
			byte[] bytes = b.toByteArray();
			PathPrefs.getUserPreferences().putByteArray(EXPORT_CHART_PREFS_KEY + name, bytes);
		}
	}

	/**
	 * Load & apply previously-saved preferences
	 * 
	 * @param items
	 * @param name
	 * @throws IOException
	 */
	static boolean loadExportPreferences(final List<Item> items, final String name) throws IOException, ClassNotFoundException {

		byte[] prefsArray = PathPrefs.getUserPreferences().getByteArray(EXPORT_CHART_PREFS_KEY + name, null);
		if (prefsArray == null)
			return false;

		Map<?, ?> map = null;
		try (ByteArrayInputStream b = new ByteArrayInputStream(prefsArray)){
			try (ObjectInputStream o = new ObjectInputStream(b)){
				Object object = o.readObject();
				if (object instanceof Map)
					map = (Map<?, ?>)object;
			}
		}
		if (map == null)
			return false;
		
		int count = 0;
		for (Item item : items) {
			// Don't want to load Series properties - these might vary
			if ("Series".equals(item.getCategory()))
				continue;
			Object value = map.get(item.getName());
			if (value != null) {
				item.setValue(value);
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
		combo.setTooltip(new Tooltip("Previously stored preferences to use for export"));
		combo.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (n != null) {
				try {
					loadExportPreferences(sheet.getItems(), n);
				} catch (Exception e) {
					Dialogs.showErrorMessage("Error loading prefs", "Sorry, unable to load preferences for " + n);
					logger.error("Error loading prefs", e);
				}
			}
		});
		
		Button btnAdd = new Button("Add");
		btnAdd.setTooltip(new Tooltip("Add current preferences to stored list"));
		btnAdd.setOnAction(e -> {
			String name = Dialogs.showInputDialog("Export chart", "Enter name for stored preferences", chart.getTitle());
			if (name != null && !name.trim().isEmpty()) {
				try {
					saveExportPreferences(sheet.getItems(), name);
					updateStoredPrefs();
					combo.getSelectionModel().select(name);
				} catch (Exception e1) {
					Dialogs.showErrorMessage("Error loading prefs", "Sorry, unable to save preferences " + name);					
					logger.error("Error saving prefs", e1);
				}
			}
		});

		Button btnRemove = new Button("Remove");
		btnRemove.setTooltip(new Tooltip("Remove current preferences from stored list"));
		btnRemove.setOnAction(e -> {
			String selected = combo.getSelectionModel().getSelectedItem();
			if (selected != null && !selected.trim().isEmpty()) {
				if (Dialogs.showConfirmDialog("Remove export prefs", "Remove \"" + selected + "\"?")) {
					PathPrefs.getUserPreferences().remove(EXPORT_CHART_PREFS_KEY + selected);
					updateStoredPrefs();
				}
			}
		});
		
		
		BorderPane pane = new BorderPane();
		combo.setMaxWidth(Double.MAX_VALUE);
		pane.setCenter(combo);
		pane.setBottom(PaneTools.createColumnGridControls(btnAdd, btnRemove));
		
		TitledPane titledPane = new TitledPane("Presets", pane);
		titledPane.setCollapsible(false);
		return titledPane;
	}



}
