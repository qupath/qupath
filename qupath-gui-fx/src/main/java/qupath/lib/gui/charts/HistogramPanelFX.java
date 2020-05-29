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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.WritableNumberValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.gui.tools.ColorToolsFX;

/**
 * Component to control the display of histograms using JavaFX charts.
 * 
 * @author Pete Bankhead
 *
 */
public class HistogramPanelFX {
	
	private final static Logger logger = LoggerFactory.getLogger(HistogramPanelFX.class);

	private final NumberAxis xAxis = new NumberAxis();
	private final NumberAxis yAxis = new NumberAxis();
	private final AreaChart<Number, Number> chart = new AreaChart<>(xAxis,yAxis);
	
	private ObservableList<HistogramData> histogramData = FXCollections.observableArrayList();
	
	@SuppressWarnings("javadoc")
	public HistogramPanelFX() {
		super();
		
		chart.setHorizontalZeroLineVisible(false);
        chart.setVerticalZeroLineVisible(false);
        
        xAxis.setLabel(null);
        yAxis.setLabel(null);
		xAxis.setTickLabelsVisible(true);
		xAxis.setTickMarkVisible(false);
		yAxis.setTickLabelsVisible(true);
		yAxis.setTickMarkVisible(false);
		chart.setVerticalGridLinesVisible(false);
		chart.setHorizontalGridLinesVisible(false);
		chart.setLegendVisible(false);
		chart.setCreateSymbols(false);
		chart.setVisible(!chart.getData().isEmpty());
		
		histogramData.addListener(new ListChangeListener<HistogramData>() {
			@Override
			public void onChanged(Change<? extends HistogramData> c) {
				while (c.next()) {
					// Pattern taken from https://docs.oracle.com/javase/8/javafx/api/javafx/collections/ListChangeListener.Change.html
					// Hower I only really need to worry about adding/removing (I hope)
					if (c.wasPermutated()) {
						for (int i = c.getFrom(); i < c.getTo(); ++i) {
							//permutate
						}
					} else if (c.wasUpdated()) {
						//update item
					} else {
						for (HistogramData removedItem : c.getRemoved()) {
							chart.getData().remove(removedItem.getSeries());
						}
						for (HistogramData addedItem : c.getAddedSubList()) {
							chart.getData().add(addedItem.getSeries());
						}
						chart.setVisible(!chart.getData().isEmpty());//.isVisible());
					}
				}
			}
		     });
	}
	
	/**
	 * Get all histogram data objects.
	 * @return
	 */
	public ObservableList<HistogramData> getHistogramData() {
		return histogramData;
	}
	
//	public void addRGBHistograms(final Histogram histogramRed, final Histogram histogramGreen, final Histogram histogramBlue) {
//		map.put(histogramRed, new HistogramData(histogramRed, Color.RED));
//		map.put(histogramGreen, new HistogramData(histogramGreen, Color.GREEN));
//		map.put(histogramBlue, new HistogramData(histogramBlue, Color.BLUE));
//		updateChart();
//	}
	
	
	
	/**
	 * Create a HistogramData object to wrap a histogram &amp; some info about its display.
	 * 
	 * @param histogram
	 * @param areaPlot If true, an area plot (simplified, fewer vertices) will be used instead.  Good for approximate display of dense histograms, less good if there are few bins.
	 * @param color
	 * @return
	 */
	public static HistogramData createHistogramData(final Histogram histogram, final boolean areaPlot, final Color color) {
		return new HistogramData(histogram, areaPlot, color);
	}
	
	
	/**
	 * Create a HistogramData object to wrap a histogram &amp; some info about its display.
	 * 
	 * @param histogram
	 * @param areaPlot If true, an area plot (simplified, fewer vertices) will be used instead.  Good for approximate display of dense histograms, less good if there are few bins.
	 * @param color Packed RGB representation of the color.
	 * @return
	 */
	public static HistogramData createHistogramData(final Histogram histogram, final boolean areaPlot, final Integer color) {
		return new HistogramData(histogram, areaPlot, color == null ? null : ColorToolsFX.getCachedColor(color));
	}
	
	/**
	 * Get the {@link AreaChart} depicting the histogram.
	 * @return
	 */
	public AreaChart<Number, Number> getChart() {
		return chart;
	}
	
	
//	public String getToolTipText(MouseEvent event) {
//		if (histogram == null)
//			return null;
//		double width = getWidth() - padX1 - padX2;
//		double value = ((event.getX() - padX1) / width) * histogram.getEdgeRange() + histogram.getEdgeMin();
//		int ind = histogram.getBinIndexForValue(value);
//		if (ind < 0)
//			return null;
//		if (histogram.getNormalizeCounts())
//			return "Bin center: " + df.format(0.5 * (histogram.getBinLeftEdge(ind) + histogram.getBinRightEdge(ind))) +
//					", Counts :" + df.format(histogram.getCountsForBin(ind)*100) + "%";
//		else
//			return "Bin center: " + df.format(0.5 * (histogram.getBinLeftEdge(ind) + histogram.getBinRightEdge(ind))) +
//					", Counts :" + df.format(histogram.getCountsForBin(ind));
////		if (histogram.getNormalizeCounts())
////			return String.format("Bin center: %.2f, Counts :%.2f%%",
////				0.5 * (histogram.getBinLeftEdge(ind) + histogram.getBinRightEdge(ind)),
////				histogram.getCountsForBin(ind)*100);
////		else
////			return String.format("Bin center: %.2f, Counts :%.2f",
////					0.5 * (histogram.getBinLeftEdge(ind) + histogram.getBinRightEdge(ind)),
////					histogram.getCountsForBin(ind));
//	}
	
	/**
	 * Request that tick labels are visible or not for both x and y axis.
	 * @param showTickLabels
	 */
	public void setShowTickLabels(boolean showTickLabels) {
		xAxis.setTickLabelsVisible(showTickLabels);
		yAxis.setTickLabelsVisible(showTickLabels);
	}
	
	
	/**
	 * Helper class for representing data that may be visualized with a {@link HistogramPanelFX}.
	 */
	public static class HistogramData {
		
		private Histogram histogram;
		private Color colorStroke;
		private Color colorFill;
		private Series<Number, Number> series;
		private boolean areaPlot;
		
		private boolean doNormalizeCounts;
		
		/**
		 * Wrapper for histogram &amp; data relevant to its display.
		 * 
		 * @param histogram
		 * @param areaPlot If true, an area plot (simplified, fewer vertices) will be used instead.  Good for approximate display of dense histograms, less good if there are few bins.
		 * @param color
		 */
		public HistogramData(final Histogram histogram, final boolean areaPlot, final Color color) {
			this.histogram = histogram;
			this.areaPlot = areaPlot;
//			setColor(color == null ? DisplayHelpers.TRANSLUCENT_BLACK_FX : color);
			setColor(color);
		}
		
		/**
		 * Returns true if the counts are normalized for display.
		 * @return
		 */
		public boolean doNormalizeCounts() {
			return doNormalizeCounts;
		}
		
		/**
		 * Request that counts are normalized for display.
		 * @param doNormalizeCounts
		 */
		public void setNormalizeCounts(final boolean doNormalizeCounts) {
			this.doNormalizeCounts = doNormalizeCounts;
		}
		
		/**
		 * Set the histogram stroke color.
		 * @param color
		 */
		private void setColor(final Color color) {
			this.colorStroke = color;
			this.colorFill = color == null ? null : Color.color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity()/4);			
		}
		
		/**
		 * Get the histogram stroke color.
		 * @return
		 */
		public Color getStroke() {
			return colorStroke;
		}
		
		/**
		 * Get the histogram fill color.
		 * @return
		 */
		public Color getFill() {
			return colorFill;
		}
		
		private void createSeries() {
			List<Data<Number,Number>> data = new ArrayList<>();
			
			if (histogram != null && histogram.nBins() > 0) {
				// Add leftmost bin edge (to stop it looking cut off strangely)
				data.add(new XYChart.Data<>(histogram.getBinLeftEdge(0), 0));
				for (int i = 0; i < histogram.nBins(); i++) {
//					// For a 'jagged' appearance with single points use the following
					if (areaPlot) {
						XYChart.Data<Number,Number> dataElement = new XYChart.Data<>(
								(histogram.getBinLeftEdge(i)/2+histogram.getBinRightEdge(i)/2),
								getCounts(histogram, i));
						data.add(dataElement);
					} else {
						// For a proper, 'bar-like' appearance use the following
						XYChart.Data<Number,Number> dataElement = new XYChart.Data<>(
								histogram.getBinLeftEdge(i),
								0);
						data.add(dataElement);
						dataElement = new XYChart.Data<>(
								histogram.getBinLeftEdge(i),
								getCounts(histogram, i));
						data.add(dataElement);
						dataElement = new XYChart.Data<>(
								histogram.getBinRightEdge(i),
								getCounts(histogram, i));
						data.add(dataElement);
						dataElement = new XYChart.Data<>(
								histogram.getBinRightEdge(i),
								0);
						data.add(dataElement);
					}
				}
				// Add rightmost bin edge (to stop it looking cut off strangely)
				data.add(new XYChart.Data<>(histogram.getBinRightEdge(histogram.nBins()-1), 0));
			}

			
			if (series == null) {
				series = new XYChart.Series<>();
				series.nodeProperty().addListener(e -> updateNodeColors());
			}
			series.getData().setAll(data);
			updateNodeColors();
		}
		
		
		
		double getCounts(Histogram histogram, int bin) {
			if (doNormalizeCounts)
				return histogram.getNormalizedCountsForBin(bin);
			return histogram.getCountsForBin(bin);
		}
		
		
		
		void updateNodeColors() {
			// Set the colors, if we can
			if (series.getNode() != null && (colorStroke != null || colorFill != null)) {
				try {
					Group group = (Group)series.getNode();
					Path seriesLine = (Path)group.getChildren().get(1);
					Path fillPath = (Path)group.getChildren().get(0);
					seriesLine.setStroke(colorStroke);
					fillPath.setFill(colorFill);
					
//					for (Data<Number, Number> item : series.getData()) {
//						if (item.getNode() != null) {
//							item.getNode().setStyle("fx-fill: red");
//						}
//					}
					
//					if (group.getChildren().size() > 2) {
//						System.err.println(group.getChildren());
//					}
				} catch (Exception e) {
					logger.error("Failed to set colors for series {}", series);
				}
			}
		}
		
		
		private Series<Number,Number> getSeries() {
			if (series == null)
				createSeries();
			return series;
		}
		
		/**
		 * Set a new histogram.
		 * This will cause any chart using this series to be updated.
		 * It is possible to set the same histogram as is contained here originally,
		 * in order to enforce updates to charts (useful because the histogram itself is not Observable).
		 * @param histogram 
		 * @param color 
		 */
		public void setHistogram(final Histogram histogram, final Color color) {
			this.histogram = histogram;
			if (color != null)
				setColor(color);
			createSeries();
		}
		
		/**
		 * Get the histogram.
		 * @return
		 */
		public Histogram getHistogram() {
			return histogram;
		}

		
	}
	
	
	
	
	/**
	 * Wrapper for JavaFX charts that enables displaying adjustable thresholds.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	public static class ThresholdedChartWrapper {
		
		private XYChart<Number, Number> chart;
		private NumberAxis xAxis, yAxis;
		private Pane pane = new Pane();
		
		private DoubleProperty lineWidth = new SimpleDoubleProperty(3);
		
		private BooleanProperty isInteractive = new SimpleBooleanProperty(false);
		
		private ObservableList<ObservableNumberValue> thresholds = FXCollections.observableArrayList();
		private Map<ObservableNumberValue, Line> vLines = new HashMap<>();


		/**
		 * Note: xAxis and yAxis must be instances of NumberAxis.
		 * 
		 * @param chart
		 */
		public ThresholdedChartWrapper(final XYChart<Number, Number> chart) {
			this.chart = chart;
			this.xAxis = (NumberAxis)chart.getXAxis();
			this.yAxis = (NumberAxis)chart.getYAxis();
			pane.getChildren().add(chart);
	        chart.prefWidthProperty().bind(pane.widthProperty());
	        chart.prefHeightProperty().bind(pane.heightProperty());

	        thresholds.addListener(new ListChangeListener<ObservableNumberValue>() {

	        	@Override
	        	public void onChanged(ListChangeListener.Change<? extends ObservableNumberValue> c) {
	        		while (c.next()) {
	        			if (c.wasPermutated()) {
	        				continue;
	        			} else {
	        				for (ObservableNumberValue removedItem : c.getRemoved()) {
	        					pane.getChildren().remove(vLines.remove(removedItem));
	        				}
//	        				pane.getChildren().removeAll(c.getRemoved());
//	        				for (ObservableNumberValue addedItem : c.getAddedSubList()) {
//	        					addThreshold(addedItem.getValue().doubleValue());
//	        				}
	        			}
	        		}
	        	}
	        });
	        
		}
		
		/**
		 * Get the pane containing the histogram, which may be added to a scene.
		 * @return
		 */
		public Pane getPane() {
			return pane;
		}
		
		/**
		 * Set thresholds, which are visualized as vertical lines.
		 * @param color
		 * @param thresholds
		 */
		public void setThresholds(Color color, double... thresholds) {
			clearThresholds();
			for (double xx : thresholds)
				addThreshold(xx, color);
		}
		
		/**
		 * Get a list of all thresholds.
		 * @return
		 */
		public ObservableList<ObservableNumberValue> getThresholds() {
			return thresholds;
		}
		
		
		/**
		 * Clear all thresholds.
		 */
		public void clearThresholds() {
			this.thresholds.clear();
		}
		
		/**
		 * Set the color of a specified threshold line.
		 * @param val
		 * @param color
		 */
		public void setThresholdColor(final ObservableNumberValue val, final Color color) {
			Line line = vLines.get(val);
			if (line == null) {
				logger.warn("No threshold line found for {}", val);
				return;
			}
			line.setStroke(color);
		}
		
		/**
		 * Add a threshold value.
		 * @param x
		 * @return
		 */
		public ObservableNumberValue addThreshold(final double x) {
			return addThreshold(x, null);
		}

		/**
		 * Add a threshold value with its display color.
		 * @param x
		 * @param color
		 * @return
		 */
		public ObservableNumberValue addThreshold(final double x, final Color color) {
			return addThreshold(new SimpleDoubleProperty(x), color);
		}
		
		
		/**
		 * Add a threshold value with its display color.
		 * @param d
		 * @param color
		 * @return
		 */
		public ObservableNumberValue addThreshold(final ObservableNumberValue d, final Color color) {
			Line line = new Line();
			if (color != null)
				line.setStroke(color);
			line.strokeWidthProperty().bind(lineWidth);

			// Bind the requested x position of the line to the 'actual' coordinate within the parent
			line.startXProperty().bind(
					Bindings.createDoubleBinding(() -> {
						double xAxisPosition = xAxis.getDisplayPosition(d.doubleValue());
						Point2D positionInScene = xAxis.localToScene(xAxisPosition, 0);
						return pane.sceneToLocal(positionInScene).getX();
					}, 
							d,
							chart.widthProperty(),
							chart.heightProperty(),
							chart.boundsInParentProperty(), 
							xAxis.lowerBoundProperty(),
							xAxis.upperBoundProperty(),
							xAxis.autoRangingProperty(),
							yAxis.autoRangingProperty(),
							yAxis.lowerBoundProperty(),
							yAxis.upperBoundProperty(),
							yAxis.scaleProperty()
							)
					);

			// End position same as starting position for vertical line
			line.endXProperty().bind(line.startXProperty());

			// Bind the y coordinates to the top and bottom of the chart
			// Binding to scale property can cause 2 calls, but this is required
			line.startYProperty().bind(
					Bindings.createDoubleBinding(() -> {
						double yAxisPosition = yAxis.getDisplayPosition(yAxis.getLowerBound());
						Point2D positionInScene = yAxis.localToScene(0, yAxisPosition);
						return pane.sceneToLocal(positionInScene).getY();
					}, 
							chart.widthProperty(),
							chart.heightProperty(),
							chart.boundsInParentProperty(),
							xAxis.lowerBoundProperty(),
							xAxis.upperBoundProperty(),
							xAxis.autoRangingProperty(),
							yAxis.autoRangingProperty(),
							yAxis.lowerBoundProperty(),
							yAxis.upperBoundProperty(),
							yAxis.scaleProperty()
							)
					);
			line.endYProperty().bind(
					Bindings.createDoubleBinding(() -> {
						double yAxisPosition = yAxis.getDisplayPosition(yAxis.getUpperBound());
						Point2D positionInScene = yAxis.localToScene(0, yAxisPosition);
						return pane.sceneToLocal(positionInScene).getY();
					}, 
							chart.widthProperty(),
							chart.heightProperty(),
							chart.boundsInParentProperty(), 
							xAxis.lowerBoundProperty(),
							xAxis.upperBoundProperty(),
							xAxis.autoRangingProperty(),
							yAxis.autoRangingProperty(),
							yAxis.lowerBoundProperty(),
							yAxis.upperBoundProperty(),
							yAxis.scaleProperty()
							)
					);
			
			line.visibleProperty().bind(
					Bindings.createBooleanBinding(() -> {
						if (Double.isNaN(d.doubleValue()))
							return false;
						return chart.isVisible();
					},
							d,
							chart.visibleProperty())
					);

			// We can only bind both ways if we have a writable value
			if (d instanceof WritableNumberValue) {
				line.setOnMouseDragged(e -> {
					if (isInteractive()) {
						double xNew = xAxis.getValueForDisplay(xAxis.sceneToLocal(e.getSceneX(), e.getSceneY()).getX()).doubleValue();
						xNew = Math.max(xNew, xAxis.getLowerBound());
						xNew = Math.min(xNew, xAxis.getUpperBound());
						((WritableNumberValue)d).setValue(xNew);
					}
				});
				
				
				line.setOnMouseEntered(e -> {
					if (isInteractive())
						line.setCursor(Cursor.H_RESIZE);
				});

				line.setOnMouseExited(e -> {
					if (isInteractive())
						line.setCursor(Cursor.DEFAULT);
				});

			}

			thresholds.add(d);
			vLines.put(d, line);
			pane.getChildren().add(line);
			//			updateChart();
			return d;
		}
		
		/**
		 * Line width property used for displaying threshold lines.
		 * @return
		 */
		public DoubleProperty lineWidthProperty() {
			return lineWidth;
		}
		
		/**
		 * Get the threshold line width.
		 * @return
		 */
		public double getLineWidth() {
			return lineWidth.get();
		}
		
		/**
		 * Set the threshold line width.
		 * @param width
		 */
		public void setLineWidth(final double width) {
			lineWidth.set(width);
		}
		
		/**
		 * Property indicating whether thresholds can be adjusted interactively.
		 * @return
		 */
		public BooleanProperty isInteractiveProperty() {
			return isInteractive;
		}

		/**
		 * Returns the value of {@link #isInteractiveProperty()}.
		 * @return
		 */
		public boolean isInteractive() {
			return isInteractive.get();
		}

		/**
		 * Sets the value of {@link #isInteractiveProperty()}.
		 * @param isInteractive 
		 */
		public void setIsInteractive(final boolean isInteractive) {
			this.isInteractive.set(isInteractive);
		}
		
		
	}
	
	
	
}