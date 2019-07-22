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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener.Change;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.chart.Axis;
import javafx.scene.chart.Axis.TickMark;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
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
import javafx.scene.layout.TilePane;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import qupath.lib.analysis.stats.survival.KaplanMeierData;
import qupath.lib.analysis.stats.survival.LogRankTest;
import qupath.lib.analysis.stats.survival.LogRankTest.LogRankResult;
import qupath.lib.common.GeneralTools;
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
	
	private KaplanMeierChart chart = new KaplanMeierChart(xAxis, yAxis);
	
	
	static class KaplanMeierChart extends LineChart<Number, Number> {
		
		public KaplanMeierChart(Axis<Number> xAxis, Axis<Number> yAxis) {
			super(xAxis, yAxis);
		}
		
		@Override
		protected void updateLegend() {
			super.updateLegend();
			
			if (getLegend() instanceof TilePane) {
				TilePane tp = (TilePane)getLegend();
	//			if (getLegendSide() == Side.RIGHT)
	//				tp.setTranslateX(-tp.getWidth());
				tp.setTranslateX(0);
				tp.setOrientation(Orientation.VERTICAL);
			}
			
			// Here, we remove the graphic for any series that doesn't have data
			// This allows us to add in any 'empty' series to provide extra info 
			// in the legend (i.e. p-values)
			for (Node node : ((Pane)getLegend()).getChildren()) {
				if (node instanceof Label) {
					Label label = (Label)node;
					String name = label.getText();
					for (Series<?, ?> series : getData()) {
						if (series.getName().equals(name)) {
							if (series.getData().isEmpty()) {
//								int ind = name.indexOf("p = ");
//								if (ind > 0) {
//									label.setText(name.substring(0, ind));
//									label.setGraphic(new Label(name.substring(ind)));
//									label.setContentDisplay(ContentDisplay.RIGHT);
//									
//									label.setAlignment(Pos.CENTER_LEFT);
//								} else
									label.setGraphic(null);
//								label.setAlignment(Pos.CENTER_RIGHT);
								label.setMaxWidth(Double.MAX_VALUE);
							} else
								label.setContentDisplay(ContentDisplay.LEFT);
							break;
						}
					}
				}
			}
		}
		
	}
	
	

	private BooleanProperty showAtRisk = new SimpleBooleanProperty(false);
	
	KaplanMeierChartWrapper(final String survivalKey) {
		super();
		//			yAxis.setAutoRanging(false);
		//			yAxis.setUpperBound(1);
		//			yAxis.setLowerBound(0);
		yAxis.setTickUnit(0.1);
		yAxis.setLabel("Probability");
		xAxis.setLabel(survivalKey);
		//			yAxis.setPadding(new Insets(5, 5, 5, 5));
		
		xAxis.setTickLabelFormatter(new StringConverter<Number>() {

			@Override
			public String toString(Number object) {
				double d = object.doubleValue();
				String s = GeneralTools.formatNumber(d, 2);
				if (showAtRisk.get()) {
//					s += "\n---";
					s += d == 0 ? "\n-At risk-" : "\n---";
					for (KaplanMeierData kmData : kmList) {
						if (d == 0) {
							s += "\n" + kmData.getName() + ": " + kmData.getAtRisk(d);
						} else {
							s += "\n" + kmData.getAtRisk(d-0.0001);
						}
					}
				}
				return s;
			}

			@Override
			public Number fromString(String s) {
				if (s == null)
					return null;
				return Double.parseDouble(s.split("\n")[0]);
			}
			
		});
		
		xAxis.getTickMarks().addListener((Change<? extends TickMark<Number>> v) -> {
			// Center all the text children
			for (Node node : xAxis.getChildrenUnmodifiable()) {
				if (node instanceof Text) {
					Text text = (Text)node;
					text.setTextAlignment(TextAlignment.CENTER);
				}
			}
			
//			for (TickMark<Number> mark : v.getList()) {
//				mark.
//				TickMark.class.getField("")
//			}
//				System.err.println(v.getList().get(0).getClass());			
		});
//		xAxis.lay
		
		
		showAtRisk.addListener((v, o, n) -> {
			updateChart();
			chart.layout();
//			xAxis.requestAxisLayout();
//			chart.requestLayout();
//			chart.layout();
		});
		

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

//		if (kms.length == 2) {
//			LogRankResult logRankResult = LogRankTest.computeLogRankTest(kms[0], kms[1]);
//			String pValueString = "p = " + GeneralTools.getFormatter(5).format(logRankResult.getPValue());
//			Series<Number, Number> series = new Series<>();
//			series.setName(pValueString);
//			series.setNode(null);
//			chart.getData().add(series);
//		}

		
		return this;
	}


	//		public Canvas getCanvas() {
	//			return canvas;
	//		}

	public Node getCanvas() {
		return chart;
	}


	public void setShowKey(final boolean showKey) {
		chart.setLegendVisible(showKey);
	}

	public boolean getShowKey() {
		return chart.isLegendVisible();
	}
	
	
	public boolean getShowAtRisk() {
		return showAtRisk.get();
	}

	public void setShowAtRisk(boolean show) {
		showAtRisk.set(show);
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
		
		
		// Show p-values if we have 2 or 3
		Series<Number, Number> series;
		if (kmList.size() == 2) {
			LogRankResult logRankResult = LogRankTest.computeLogRankTest(kmList.get(0), kmList.get(1));
			String pValueString = "p = " + GeneralTools.formatNumber(logRankResult.getPValue(), 3);
			
//			series = new Series<>();
//			series.setName("Log-rank test");
//			series.setNode(null);
//			chart.getData().add(series);

			series = new Series<>();
			series.setName(pValueString);
			series.setNode(null);
			chart.getData().add(series);
		} else if (kmList.size() == 3) {
			LogRankResult logRankResult = LogRankTest.computeLogRankTest(kmList.get(0), kmList.get(1));
			
//			series = new Series<>();
//			series.setName("Log-rank test");
//			series.setNode(null);
//			chart.getData().add(series);
			
			series = new Series<>();
			series.nameProperty().bind(
					chart.getData().get(0).nameProperty()
					.concat(" vs ")
					.concat(chart.getData().get(1).nameProperty())
					.concat(String.format(": p = %.4f", logRankResult.getPValue())));
			series.setNode(null);
			chart.getData().add(series);
			
			logRankResult = LogRankTest.computeLogRankTest(kmList.get(0), kmList.get(2));
			series = new Series<>();
			series.nameProperty().bind(
					chart.getData().get(0).nameProperty()
					.concat(" vs ")
					.concat(chart.getData().get(2).nameProperty())
					.concat(String.format(": p = %.4f", logRankResult.getPValue())));
			series.setNode(null);
			chart.getData().add(series);

			logRankResult = LogRankTest.computeLogRankTest(kmList.get(1), kmList.get(2));
			series = new Series<>();
			series.nameProperty().bind(
					chart.getData().get(1).nameProperty()
					.concat(" vs ")
					.concat(chart.getData().get(2).nameProperty())
					.concat(String.format(": p = %.4f", logRankResult.getPValue())));
			series.setNode(null);
			chart.getData().add(series);
			
//			series = new Series<>();
//			series.nameProperty().bind(
//					chart.getData().get(0).nameProperty()
//					.concat(" vs ")
//					.concat(chart.getData().get(1).nameProperty())
//					.concat(": p = " + GeneralTools.getFormatter(3).format(logRankResult.getPValue())));
//			series.setNode(null);
//			chart.getData().add(series);
//			
//			logRankResult = LogRankTest.computeLogRankTest(kmList.get(0), kmList.get(2));
//			series = new Series<>();
//			series.nameProperty().bind(
//					chart.getData().get(0).nameProperty()
//					.concat(" vs ")
//					.concat(chart.getData().get(2).nameProperty())
//					.concat(": p = " + GeneralTools.getFormatter(3).format(logRankResult.getPValue())));
//			series.setNode(null);
//			chart.getData().add(series);
//
//			logRankResult = LogRankTest.computeLogRankTest(kmList.get(1), kmList.get(2));
//			series = new Series<>();
//			series.nameProperty().bind(
//					chart.getData().get(1).nameProperty()
//					.concat(" vs ")
//					.concat(chart.getData().get(2).nameProperty())
//					.concat(": p = " + GeneralTools.getFormatter(3).format(logRankResult.getPValue())));
//			series.setNode(null);
//			chart.getData().add(series);
		}

		
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
				KaplanMeierChartWrapper kmPlotter2 = new KaplanMeierChartWrapper(kmPlotter.chart.getXAxis().getLabel());
				NumberAxis xAxis = kmPlotter2.xAxis;
				NumberAxis yAxis = kmPlotter2.yAxis;
				kmPlotter2.kmList.addAll(kmPlotter.kmList);
//				xAxis.labelProperty().bind(kmPlotter.chart.getXAxis().labelProperty());
				int n = kmPlotter.kmList.size();
				if (!kmPlotter.kmList.isEmpty()) {
					xAxis.setAutoRanging(false);
					xAxis.setLowerBound(0);
					xAxis.setUpperBound(Math.ceil(kmPlotter.kmList.get(n-1).getMaxTime()));
				}
				yAxis.setLabel(kmPlotter.chart.getYAxis().getLabel());
//				yAxis.labelProperty().bind(kmPlotter.chart.getYAxis().labelProperty());
				yAxis.setAutoRanging(false);
				yAxis.setLowerBound(0);
				yAxis.setUpperBound(1);
				kmPlotter2.updateChart();
				
				kmPlotter2.setShowAtRisk(kmPlotter.getShowAtRisk());
				kmPlotter2.setShowCensoredTicks(kmPlotter.getShowCensoredTicks());
				
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
		}
	
	

}