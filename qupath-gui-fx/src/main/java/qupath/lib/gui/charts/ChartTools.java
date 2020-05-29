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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.Chart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * Collection of static methods to help when working with JavaFX charts, 
 * and adapting these for QuPath's purposes.
 * 
 * @author Pete Bankhead
 *
 */
public class ChartTools {
	
	private final static Logger logger = LoggerFactory.getLogger(ChartTools.class);
	
	/**
	 * Cache of stylesheets for pie charts, which are temp files.
	 */
	private static Map<String, String> piechartStyleSheets = new HashMap<>();


	/**
	 * Get a string representation of chart data, in such a way that it could be pasted into a spreadsheet.
	 * 
	 * @param chart
	 * @return
	 */
	public static String getChartDataAsString(final XYChart<Number, Number> chart) {

		StringBuilder sb = new StringBuilder();
		String delim = "\t";
		int maxLength = 0;
		// Determine how many rows we need
		int count = 0;
		for (Series<Number, Number> series : chart.getData()) {
			maxLength = Math.max(maxLength, series.getData().size());
			count++;
			if (series.getName() != null)
				sb.append(series.getName()).append(delim).append(delim);
			else
				sb.append("Series " + count).append(delim).append(delim);				
		}
		sb.append("\n");

		// Add axes
		for (int i = 0; i < chart.getData().size(); i++) {
			sb.append(chart.getXAxis().getLabel()).append(delim);
			sb.append(chart.getYAxis().getLabel()).append(delim);
		}
		sb.append("\n");

		// Loop through data
		for (int row = 0; row < maxLength; row++) {
			for (Series<Number, Number> series : chart.getData()) {
				if (row < series.getData().size()) {
					Data<Number, Number> d = series.getData().get(row);
					sb.append(d.getXValue()).append(delim);
					sb.append(d.getYValue()).append(delim);
				} else
					sb.append(delim).append(delim);
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	/**
	 * Make it possible to select chart regions to zoom in, and scroll to navigate around it.
	 * 
	 * Also double-click to stop zooming.
	 * 
	 * @param chart
	 * @param xAxis
	 * @param yAxis
	 */
	public static void makeChartInteractive(final Chart chart, final NumberAxis xAxis, final NumberAxis yAxis) {
		Function<MouseEvent, Point2D> convertChartPoint = new Function<MouseEvent, Point2D>() {
			@Override
			public Point2D apply(MouseEvent event) {
				Point2D mouseScenePoint = new Point2D(event.getSceneX(), event.getSceneY());
				double x = xAxis.getValueForDisplay(xAxis.sceneToLocal(mouseScenePoint).getX()).doubleValue();
				double y = yAxis.getValueForDisplay(yAxis.sceneToLocal(mouseScenePoint).getY()).doubleValue();
				return new Point2D(x, y);
			}
		};

		Rectangle rect = new Rectangle();
		chart.setOnMouseClicked(e -> {
			if (e.getClickCount() > 1) {
				xAxis.setAutoRanging(true);
				yAxis.setAutoRanging(true);
			}
		});
		chart.setOnMousePressed(e -> {
			Point2D p = convertChartPoint.apply(e);
			rect.setX(p.getX());
			rect.setY(p.getY());
			rect.setWidth(0);
			rect.setHeight(0);
		});
		chart.setOnMouseDragged(e -> {
			Point2D p = convertChartPoint.apply(e);
			rect.setWidth(p.getX() - rect.getX());
			rect.setHeight(p.getY() - rect.getY());
		});
		chart.setOnMouseReleased(e -> {
			// Don't do anything if not pressing shift
			if (!e.isShiftDown()) {
				rect.setWidth(0);
				rect.setHeight(0);
				return;
			}
			Point2D p = convertChartPoint.apply(e);
			rect.setWidth(p.getX() - rect.getX());
			rect.setHeight(p.getY() - rect.getY());
			logger.debug("Rectangle drawn on chart: {}", rect);
			if (rect.getWidth() != 0 && rect.getHeight() != 0) {
				double x1 = rect.getX();
				double y1 = rect.getY();
				double x2 = rect.getX() + rect.getWidth();
				double y2 = rect.getY() + rect.getHeight();
				xAxis.setAutoRanging(false);
				yAxis.setAutoRanging(false);
				xAxis.setLowerBound(Math.min(x1, x2));
				xAxis.setUpperBound(Math.max(x1, x2));
				yAxis.setLowerBound(Math.min(y1, y2));
				yAxis.setUpperBound(Math.max(y1, y2));
			}
		});
		chart.setOnScroll(e -> {
			if (xAxis.isAutoRanging() || yAxis.isAutoRanging())
				return;
			double xDiff = -e.getDeltaX() / xAxis.getScale();
			double yDiff = -e.getDeltaY() / yAxis.getScale();
			xAxis.setLowerBound(xAxis.getLowerBound() + xDiff);
			xAxis.setUpperBound(xAxis.getUpperBound() + xDiff);
			yAxis.setLowerBound(yAxis.getLowerBound() + yDiff);
			yAxis.setUpperBound(yAxis.getUpperBound() + yDiff);
		});
	}


	/**
	 * Add a menu item to a context menu for displaying a chart for export.
	 * 
	 * @param chart
	 * @param menu
	 */
	public static void addChartExportMenu(final XYChart<Number, Number> chart, final ContextMenu menu) {
		MenuItem menuItem = new MenuItem("Export chart");
		menuItem.setOnAction(e -> ExportChartPane.showExportChartDialog(chart));
		if (menu == null) {
			ContextMenu menu2 = new ContextMenu();
			menu2.getItems().add(menuItem);
			chart.setOnContextMenuRequested(e -> menu2.show(chart, e.getScreenX(), e.getScreenY()));
		} else
			menu.getItems().add(menuItem);
	}

	/**
	 * Charts tend to show their legends with circular/square markers... which isn't much use 
	 * when line strokes distinguish between different Series.
	 * 
	 * This method addresses this by setting the graphics for legend labels to be lines with the appropriate strokes.
	 * 
	 * @param chart
	 * @param length
	 */
	public static void setLineChartLegendLines(final Chart chart, final double length) {
		Region legend = (Region)chart.lookup(".chart-legend");
		int count = 0;
		for (Node legendItem : legend.getChildrenUnmodifiable()) {
			if (legendItem instanceof Label) {
				Label label = (Label)legendItem;
				Line line = new Line(0, 4, 25, 4);
				if (chart instanceof XYChart<?, ?>) {
					XYChart<?, ?> xyChart = (XYChart<?, ?>)chart;
					if (xyChart.getData().get(count).getData().isEmpty()) {
						label.setGraphic(null);
						count++;
						continue;
					}
				}
				line.getStyleClass().setAll("chart-series-line", "default-color"+count);
				label.setGraphic(line);
				count++;
			}
		}
	}

	
	/**
	 * Expand the clip region for a chart.
	 * 
	 * This helps to avoid clipping off markers at the chart boundaries.
	 * 
	 * @param chart
	 * @param pad The amount by which to expand the clip rectangle in each direction; if &lt; 0, the clip will be completely removed
	 */
	public static void expandChartClip(final Chart chart, final double pad) {
		Rectangle newClip;
		Region chartContent = (Region)chart.lookup(".chart-content");
		for (Node node: chartContent.getChildrenUnmodifiable()) {
		    if (node instanceof Group) {
		        Group plotArea = (Group)node;
		        if (pad < 0)
		        	plotArea.setClip(null);
		        else if (plotArea.getClip() instanceof Rectangle) {
		        	Rectangle previousClip = (Rectangle)plotArea.getClip();
			        newClip = new Rectangle();
			        newClip.xProperty().bind(previousClip.xProperty().subtract(pad));
			        newClip.yProperty().bind(previousClip.yProperty().subtract(pad));
			        newClip.widthProperty().bind(previousClip.widthProperty().add(pad*2));
			        newClip.heightProperty().bind(previousClip.heightProperty().add(pad*2));
			        plotArea.setClip(newClip);
		        }
		    }
		}
	}

	/**
		 * Set pie chart data from a count map.
		 * 
		 * @param <T> the type of the data being counted
		 * @param chart the pie chart to update
		 * @param counts mapping between items and their counts
		 * @param stringFun function to extract a string from each item (may be null to use default {@code toString()} method)
		 * @param colorFun function to extract a color from each item (may be null to use default colors)
		 * @param convertToPercentages if true, convert counts to percentages; if false, use original values
		 * @param includeTooltips if true, install tooltips for each 'slice' to display the numeric information
		 */
		public static <T> void setPieChartData(PieChart chart, Map<T, ? extends Number> counts,
				Function<T, String> stringFun, Function<T, Color> colorFun, boolean convertToPercentages, boolean includeTooltips) {
			
			StringBuilder style = null;
			if (colorFun != null)
				style = new StringBuilder();
	
			double sum = counts.values().stream().mapToDouble(i -> i.doubleValue()).sum();
			var newData = new ArrayList<PieChart.Data>();
			int ind = 0;
			var tooltips = new HashMap<PieChart.Data, Tooltip>();
			for (Entry<T, ? extends Number> entry : counts.entrySet()) {
				var item = entry.getKey();
				String name;
				if (stringFun != null)
					name = stringFun.apply(item);
				else
					name = Objects.toString(item);
				double value = entry.getValue().doubleValue();
				if (convertToPercentages)
					value = value / sum * 100.0;
				var datum = new PieChart.Data(name, value);
				newData.add(datum);
	
				if (style != null) {
					var color = colorFun.apply(item);
					if (color != null) {
						String colorString;
						// TODO: Use alpha?
	//					if (color.isOpaque())
							colorString = String.format("rgb(%d, %d, %d)", (int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255));
	//					else
	//						colorString = String.format("rgba(%f, %f, %f, %f)", color.getRed(), color.getGreen(), color.getBlue(), 1.0-color.getOpacity());
						style.append(String.format(".default-color%d.chart-pie { -fx-pie-color: %s; }", ind, colorString)).append("\n");
					}
					ind++;
				}
	
				if (includeTooltips) {
					var text = String.format("%s: %.1f%%", name, value);
					tooltips.put(datum, new Tooltip(text));
				}
			}
	
			if (style != null) {
				var styleString = style.toString();
				var sheet = piechartStyleSheets.get(styleString);
				sheet = null;
				if (sheet == null) {
					try {
						var file = File.createTempFile("chart", ".css");
						file.deleteOnExit();
						var writer = new PrintWriter(file);
						writer.println(styleString);
						writer.close();
						sheet = file.toURI().toURL().toString();
						piechartStyleSheets.put(styleString, sheet);
					} catch (IOException e) {
						logger.error("Error creating temporary piechart stylesheet", e);
					}			
				}
				if (sheet != null)
					chart.getStylesheets().setAll(sheet);
			}
	
			chart.setAnimated(false);
			chart.getData().setAll(newData);
	
			if (includeTooltips) {
				for (var entry : tooltips.entrySet()) {
					Tooltip.install(entry.getKey().getNode(), entry.getValue());
				}
			}
	
		}
	
	

}
