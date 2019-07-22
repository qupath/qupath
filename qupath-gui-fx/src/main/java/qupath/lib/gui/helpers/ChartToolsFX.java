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

package qupath.lib.gui.helpers;

import java.util.function.Function;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.Chart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import qupath.lib.gui.panels.ExportChartPanel;
import qupath.lib.gui.tma.TMASummaryViewer;

/**
 * Collection of static methods to help when working with JavaFX charts, 
 * and adapting these for QuPath's purposes.
 * 
 * @author Pete Bankhead
 *
 */
public class ChartToolsFX {

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
			TMASummaryViewer.logger.debug("Rectangle drawn on chart: {}", rect);
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
		menuItem.setOnAction(e -> ExportChartPanel.showExportChartPanel(chart));
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
	
	

}
