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

package qupath.lib.gui.panels.survival;

import java.util.ArrayList;
import java.util.List;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Side;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import qupath.lib.analysis.stats.survival.KaplanMeierData;
import qupath.lib.gui.helpers.ChartToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.panels.ExportChartPanel;

/**
 * Wrapper class for custom chart used to show Kaplan Meier survival curves.
 * 
 * @author Pete Bankhead
 *
 */
public class KaplanMeierChartWrapper {
	
	final private static Logger logger = LoggerFactory.getLogger(KaplanMeierChartWrapper.class);

	private List<KaplanMeierData> kmList = new ArrayList<>();

	private NumberAxis xAxis = new NumberAxis();
	private NumberAxis yAxis = new NumberAxis();
	private LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);

	KaplanMeierChartWrapper(final String survivalKey) {
		super();
		//			yAxis.setAutoRanging(false);
		//			yAxis.setUpperBound(1);
		//			yAxis.setLowerBound(0);
		yAxis.setTickUnit(0.1);
		yAxis.setLabel("Probability");
		xAxis.setLabel(survivalKey);
		//			yAxis.setPadding(new Insets(5, 5, 5, 5));


		// TODO: Fix printing - may depend on Java bug fix https://bugs.openjdk.java.net/browse/JDK-8129364
		ContextMenu menu = new ContextMenu();

		Menu menuLegendSide = new Menu("Set legend...");
		ToggleGroup toggleSide = new ToggleGroup();
		Action actionNoLegend = new Action("None", e -> chart.setLegendVisible(false));
		RadioMenuItem riNone = ActionUtils.createRadioMenuItem(actionNoLegend);
		menuLegendSide.getItems().add(riNone);
		toggleSide.getToggles().add(riNone);
		RadioMenuItem riTop = ActionUtils.createRadioMenuItem(new Action("Top", e -> 	{chart.setLegendSide(Side.TOP);
		chart.setLegendVisible(true);}));
		menuLegendSide.getItems().add(riTop);
		toggleSide.getToggles().add(riTop);
		RadioMenuItem riBottom = ActionUtils.createRadioMenuItem(new Action("Bottom", e -> {chart.setLegendSide(Side.BOTTOM);
		chart.setLegendVisible(true);}));
		menuLegendSide.getItems().add(riBottom);
		toggleSide.getToggles().add(riBottom);
		RadioMenuItem riRight = ActionUtils.createRadioMenuItem(new Action("Right", e -> 	{chart.setLegendSide(Side.RIGHT);
		chart.setLegendVisible(true);}));
		menuLegendSide.getItems().add(riRight);
		toggleSide.getToggles().add(riRight);
		RadioMenuItem riLeft = ActionUtils.createRadioMenuItem(new Action("Left", e ->	{chart.setLegendSide(Side.LEFT);
		chart.setLegendVisible(true);}));
		menuLegendSide.getItems().add(riLeft);
		toggleSide.getToggles().add(riLeft);
		toggleSide.selectToggle(riRight);
		
		// Ensure we update the chart with symbols request changes - this should draw censored ticks (not normal markers)
		chart.createSymbolsProperty().addListener((v, o, n) -> updateChart());

		// Expand the clip area to avoid completely cutting off markers occurring at the edge
		ChartToolsFX.expandChartClip(chart, 5);
		
		
		MenuItem miCopyPanel = new MenuItem("Entire panel");
		miCopyPanel.setOnAction(e -> {
			// Create a snapshot at 4x the current resolution
			double scale = 2;
			Region region = (Region)chart.getParent().getParent();
			//				Region region = chart;
			int w = (int)(region.getWidth() * scale + 0.5);
			int h = (int)(region.getHeight() * scale + 0.5);
			SnapshotParameters params = new SnapshotParameters();
			params.setTransform(Transform.scale(scale, scale));

			//				for (Node node : chart.getChildrenUnmodifiable()) {
			//					node.setStyle("-fx-background-color: white");
			//					System.err.println(node.getClass().getName());
			//				}
			//				chart.setStyle(".chart-content {\n"
			//						+ "-fx-background-color: white;\n"
			//						+ "}");

			WritableImage img = region.snapshot(params, new WritableImage(w, h));
			ClipboardContent content = new ClipboardContent();
			content.putImage(img);
			Clipboard.getSystemClipboard().setContent(content);

			//				for (Node node : chart.getChildrenUnmodifiable()) {
			//					node.setStyle(null);
			//					System.err.println(node.getClass().getName());
			//				}
			//				chart.setStyle("");

		});

		MenuItem miCopy = new MenuItem("Chart only");
		miCopy.setOnAction(e -> {
			// Create a snapshot at 4x the current resolution
			double scale = 4;
			Region region = chart;
			int w = (int)(region.getWidth() * scale + 0.5);
			int h = (int)(region.getHeight() * scale + 0.5);
			SnapshotParameters params = new SnapshotParameters();
			params.setTransform(Transform.scale(scale, scale));
			WritableImage img = region.snapshot(params, new WritableImage(w, h));
			ClipboardContent content = new ClipboardContent();
			content.putImage(img);
			Clipboard.getSystemClipboard().setContent(content);
		});
		Menu menuCopy = new Menu("Copy...");
		menuCopy.getItems().addAll(miCopy, miCopyPanel);


		MenuItem miPrint = new MenuItem("Print (experimental)");
		miPrint.setOnAction(e -> {
			PrinterJob job = PrinterJob.createPrinterJob();
			logger.warn("Printing does not yet work properly! (At least not on OSX)");
			if (job != null && job.showPrintDialog(chart.getScene().getWindow())) {
				boolean success = job.printPage(chart.getParent().getParent());
				if (success) {
					job.endJob();
				}
			}
		});

		MenuItem miShowPlot = new MenuItem("Show plot window");
		miShowPlot.setOnAction(e -> showKaplanMeierPlotWindow(this));

		menu.getItems().addAll(
				menuLegendSide,
				menuCopy,
				miPrint,
				new SeparatorMenuItem(),
				miShowPlot
				);
		chart.setOnContextMenuRequested(e -> menu.show(chart, e.getScreenX(), e.getScreenY()));

	}

	KaplanMeierChartWrapper addKaplanMeier(final KaplanMeierData km) {
		kmList.add(km);
		return this;
	}


	KaplanMeierChartWrapper setKaplanMeierCurves(final String survivalKey, final KaplanMeierData... kms) {
		kmList.clear();
		double maxTime = 0;
		for (KaplanMeierData km : kms) {
			kmList.add(km);
			maxTime = Math.max(maxTime, km.getMaxTime());
		}
		xAxis.setAutoRanging(false);
		xAxis.setLowerBound(0);
		xAxis.setUpperBound(maxTime);
		xAxis.setLabel(survivalKey);
		yAxis.setAutoRanging(false);
		yAxis.setLowerBound(0);
		yAxis.setUpperBound(1);

		chart.setLegendSide(Side.RIGHT);

		updateChart();
		return this;
	}


	//		public Canvas getCanvas() {
	//			return canvas;
	//		}

	public LineChart<Number, Number> getCanvas() {
		return chart;
	}


	public void setShowKey(final boolean showKey) {
		chart.setLegendVisible(showKey);
	}

	public boolean getShowKey() {
		return chart.isLegendVisible();
	}


	public boolean getShowCensoredTicks() {
		return chart.getCreateSymbols();
	}

	public void setShowCensoredTicks(final boolean showTicks) {
		chart.setCreateSymbols(showTicks);
	}


	void updateChart() {
		// Loop through the plots to get the maximum time that's needed
		double maxTime = -1;
		for (KaplanMeierData km : kmList) {
			maxTime = Math.max(maxTime, km.getMaxTime());
		}
		if (maxTime < 0)
			return;

		chart.setAnimated(false);

		// Loop through the plots to draw them
		int count = 0;
		for (KaplanMeierData km : kmList) {
			XYChart.Series<Number, Number> series = new XYChart.Series<>();
			series.setName(km.getName());
			double[] times = km.getAllTimes();
			double[] stats = km.getStatistic();
			double x1 = 0;
			double y1 = 1;
			series.getData().add(new XYChart.Data<>(x1, y1));
			for (int i = 0; i < times.length; i++) {
				// Transform coordinates for lines
				double x2 = times[i];
				double y2 = stats[i];
				// Draw
				XYChart.Data<Number, Number> data = new XYChart.Data<>(x2, y1);
				series.getData().add(data);
				if (y1 != y2) {
					data = new XYChart.Data<>(x2, y2);
					data.setExtraValue(Boolean.FALSE);
					series.getData().add(data);
					//						data.setNode(null);
				} else
					data.setExtraValue(Boolean.TRUE);
				//					else {
				//						StackPane itemNode = new StackPane();
				//				        itemNode.setPrefHeight(10);
				//				        itemNode.setPrefWidth(1);
				//						data.setNode(itemNode);
				//					}
				// Update
				x1 = x2;
				y1 = y2;
			}
			if (count < chart.getData().size())
				chart.getData().set(count, series);
			else
				chart.getData().add(series);

			//				Color color = colors[count % colors.length];
			for (XYChart.Data<Number, Number> data : series.getData()) {
				Node node = data.getNode();
				if (node == null)
					continue;
				if (data.getExtraValue() == Boolean.TRUE)
					((StackPane)node).setPrefWidth(2);
				else if (data.getNode() != null)
					node.setVisible(false);
			}

			count++;
		}
		while (count < chart.getData().size())
			chart.getData().remove(count);
		
		
//		for (Series<Number, Number> series : chart.getData()) {
//			System.out.println(series.getName());
//			for (Data<Number, Number> data : series.getData()) {
//				System.err.println(data);
//			}
//		}
		
	}
	
	
	// Attempt to show a separate survival plot window (not yet stable)
		static void showKaplanMeierPlotWindow(final KaplanMeierChartWrapper kmPlotter) {
			
			XYChart<Number, Number> chart2;
			try {
				NumberAxis xAxis = new NumberAxis();
				NumberAxis yAxis = new NumberAxis();
				KaplanMeierChartWrapper kmPlotter2 = new KaplanMeierChartWrapper(kmPlotter.chart.getXAxis().getLabel());
				kmPlotter2.kmList.addAll(kmPlotter.kmList);
				xAxis.labelProperty().bind(kmPlotter.chart.getXAxis().labelProperty());
				yAxis.labelProperty().bind(kmPlotter.chart.getYAxis().labelProperty());
				kmPlotter2.updateChart();
				
				chart2 = kmPlotter2.chart;
				
//				chart2 = ExportChartPanel.copyChart(kmPlotter.chart);
				ExportChartPanel exportPane = new ExportChartPanel(chart2);
				Pane pane = exportPane.getPane();
				Stage stage = new Stage();
				stage.initOwner(kmPlotter.chart.getScene().getWindow());
				stage.setTitle("Kaplan Meier figure");
				stage.setScene(new Scene(pane));
				stage.show();
				exportPane.refreshChartDisplay();
			} catch (Exception e) {
				DisplayHelpers.showErrorNotification("Survival curve error", "Unable to copy the current survival curve!");
				logger.error("Survival curve copy error", e);
			}
			
//			
//			NumberAxis xAxis = new NumberAxis();
//			NumberAxis yAxis = new NumberAxis();
//			KaplanMeierChartWrapper kmPlotter2 = new KaplanMeierChartWrapper(kmPlotter.chart.getXAxis().getLabel());
//			kmPlotter2.kmList.addAll(kmPlotter.kmList);
//			//			LineChart<Number, Number> chart2 = new LineChart<>(xAxis, yAxis);
//			LineChart<Number, Number> chart2 = kmPlotter2.chart;
//			
//			
//			chart2.setStyle("-fx-background-color: rgba(255, 255, 255, 0);");
//			chart2.lookup(".chart-plot-background").setStyle("-fx-background-color: rgba(255, 255, 255, 0);");
//			//			chart2.setOnMouseClicked(e -> {
//			//				if (e.getClickCount() > 0) {
//			//					chart2.setStyle("-fx-background-color: rgba(255, 255, 255, 0);");
//			//					chart2.lookup(".chart-plot-background").setStyle("-fx-background-color: rgba(255, 255, 255, 0);");
//			//				}
//			//			});
//			//			kmPlotter.chart.getData().addListener(new InvalidationListener() {
//			//				@Override
//			//				public void invalidated(Observable observable) {
//			//					chart2.getData().setAll(kmPlotter.chart.getData());
//			//				}
//			//			});
//			//			chart2.getData().setAll(kmPlotter.chart.getData());
//			//			chart2.dataProperty().bind(kmPlotter.chart.dataProperty());
//			chart2.setAnimated(false);
//			xAxis.labelProperty().bind(kmPlotter.chart.getXAxis().labelProperty());
//			yAxis.labelProperty().bind(kmPlotter.chart.getYAxis().labelProperty());
//			kmPlotter2.updateChart();
//
//			Stage dialog = new Stage();
//
//			CheckBox cbLegend = new CheckBox("Show legend");
//			chart2.legendVisibleProperty().bind(cbLegend.selectedProperty());
//			TextField tfWidth = new TextField();
//			tfWidth.setPrefColumnCount(8);
//			TextField tfHeight = new TextField();
//			tfHeight.setPrefColumnCount(8);
//			Button btnSizeApply = new Button("Apply");
//			btnSizeApply.setOnAction(e -> {
//				try {
//					
////					Node node = chart2.lookup(".chart-legend");
////					System.err.println(node);
////					Node node = chart2.lookup(".chart-legend");
////					System.err.println(((Region)node).getChildrenUnmodifiable());
//					
//					int w = Integer.parseInt(tfWidth.getText().trim());
//					int h = Integer.parseInt(tfHeight.getText().trim());
//					if (w > 0 && h > 0) {
//						dialog.setWidth(w);
//						dialog.setHeight(h);
//					} else
//						DisplayHelpers.showErrorMessage("Survival curve size", "Width & height must both be > 0");
//				} catch (NumberFormatException ex) {
//					DisplayHelpers.showErrorMessage("Survival curve size", "Cannot parse sizes");
//				}
//			});
//
//			Button btnCopy = new Button("Copy");
//			btnCopy.setOnAction(e -> {
//				WritableImage img = chart2.snapshot(null, null);
//				ClipboardContent content = new ClipboardContent();
//				content.putImage(img);
//				Clipboard.getSystemClipboard().setContent(content);
//			});
//			Button btnCopyHiRes = new Button("Copy high res");
//			btnCopyHiRes.setOnAction(e -> {
//				// Create a snapshot at 4x the current resolution
//				double scale = 4;
//				Region region = chart2;
//				int w = (int)(region.getWidth() * scale + 0.5);
//				int h = (int)(region.getHeight() * scale + 0.5);
//				SnapshotParameters params = new SnapshotParameters();
//				params.setTransform(Transform.scale(scale, scale));
//				WritableImage img = region.snapshot(params, new WritableImage(w, h));
//				ClipboardContent content = new ClipboardContent();
//				content.putImage(img);
//				Clipboard.getSystemClipboard().setContent(content);
//			});
//
//			GridPane paneButtons = new GridPane();
//			paneButtons.setHgap(5);
//			paneButtons.setVgap(5);
//			paneButtons.setPadding(new Insets(10, 10, 10, 10));
//			int r = 0;
//			int c = 0;
//			paneButtons.add(cbLegend, c++, r);
//			paneButtons.add(new Separator(Orientation.VERTICAL), c++, r);
//			paneButtons.add(new Label("Width"), c++, r);
//			paneButtons.add(tfWidth, c++, r);
//			paneButtons.add(new Label("Height"), c++, r);
//			paneButtons.add(tfHeight, c++, r);
//			paneButtons.add(btnSizeApply, c++, r);
//			r++;
//			Pane paneCopy = PanelToolsFX.createColumnGridControls(btnCopy, btnCopyHiRes);
//			paneButtons.add(paneCopy, 0, r, c, 1);
//			GridPane.setHgrow(paneCopy, Priority.ALWAYS);
//
//
//
//			Window window = kmPlotter.chart.getScene().getWindow();
//			if (window != null)
//				dialog.initOwner(window);
//			BorderPane pane = new BorderPane();
//			pane.setCenter(chart2);
//			pane.setBottom(paneButtons);
//			Scene scene = new Scene(pane);
//			dialog.setTitle("Survival plot");
//			dialog.setScene(scene);
//			dialog.show();
		}
	
	

}