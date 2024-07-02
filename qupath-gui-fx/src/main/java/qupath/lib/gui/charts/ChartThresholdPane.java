/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Pane that can be used to contain an XYChart, adding adjustable thresholds to be displayed.
 */
public class ChartThresholdPane extends BorderPane {

    public enum ThresholdAxis {
        X,
        Y
    }

    private static final Logger logger = LoggerFactory.getLogger(ChartThresholdPane.class);

    private XYChart<Number, Number> chart;
    private NumberAxis xAxis, yAxis;

    private DoubleProperty lineWidth = new SimpleDoubleProperty(2);

    private BooleanProperty isInteractive = new SimpleBooleanProperty(false);

    private ObservableList<ObservableNumberValue> thresholds = FXCollections.observableArrayList();
    private Map<ObservableNumberValue, Line> vLines = new HashMap<>();


    /**
     * Note: xAxis and yAxis must be instances of NumberAxis.
     *
     * @param chart
     */
    public ChartThresholdPane(final XYChart<Number, Number> chart) {
        this.chart = chart;
        this.xAxis = (NumberAxis) chart.getXAxis();
        this.yAxis = (NumberAxis) chart.getYAxis();
        setCenter(chart);
        thresholds.addListener(this::handleLineListChange);
    }

    private void handleLineListChange(ListChangeListener.Change<? extends ObservableNumberValue> c) {
        while (c.next()) {
            if (!c.wasPermutated()) {
                for (ObservableNumberValue removedItem : c.getRemoved()) {
                    getChildren().remove(vLines.remove(removedItem));
                }
                // for (ObservableNumberValue addedItem : c.getAddedSubList()) {
                //     addThreshold(addedItem);
                // }
            }
        }
    }

    /**
     * Set thresholds, which are visualized as vertical lines.
     *
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
     *
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
     *
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
     *
     * @param x
     * @return
     */
    public ObservableNumberValue addThreshold(final double x) {
        return addThreshold(x, null);
    }

    /**
     * Add a threshold value with its display color.
     *
     * @param x
     * @param color
     * @return
     */
    public ObservableNumberValue addThreshold(final double x, final Color color) {
        return addThreshold(new SimpleDoubleProperty(x), color, ThresholdAxis.X);
    }

    /**
     * Add a threshold value.
     *
     * @param d
     * @return
     */
    public ObservableNumberValue addThreshold(final ObservableNumberValue d) {
        return addThreshold(d, null, ThresholdAxis.X);
    }

    /**
     * Add a threshold value with its display color.
     *
     * @param d
     * @param color
     * @return
     */
    public ObservableNumberValue addThreshold(final ObservableNumberValue d, final Color color, ThresholdAxis whichAxis) {
        Line line = new Line();
        line.getStyleClass().add("qupath-histogram-line");
        if (color != null)
            line.setStroke(color);
        else
            line.setStyle("-fx-stroke: -fx-text-base-color; -fx-opacity: 0.5;");
//            line.setStyle("-fx-stroke: ladder(-fx-background, "
//                    + "derive(-fx-background, 30%) 49%, "
//                    + "derive(-fx-background, -30%) 50%);");

        line.strokeWidthProperty().bind(lineWidth);
        boolean isX = whichAxis == ThresholdAxis.X;

        bindThresholdLine(d, line, isX);

        bindStaticPoints(line, isX);

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
        if (d instanceof WritableNumberValue writableNumberValue) {
            line.setOnMouseDragged(e -> {
                if (isInteractive()) {
                    double xNew = xAxis.getValueForDisplay(xAxis.sceneToLocal(e.getSceneX(), e.getSceneY()).getX()).doubleValue();
                    xNew = Math.max(xNew, xAxis.getLowerBound());
                    xNew = Math.min(xNew, xAxis.getUpperBound());
                    writableNumberValue.setValue(xNew);
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

        if (!thresholds.contains(d))
            thresholds.add(d);
        vLines.put(d, line);
        getChildren().add(line);
        //			updateChart();
        return d;
    }

    private void bindStaticPoints(Line line, boolean isX) {
        // Bind the other coordinates to the extrema of the chart
        // Binding to scale property can cause 2 calls, but this is required
        var axisOther = isX ? yAxis : xAxis;
        var startProp = isX ? line.startYProperty() : line.startXProperty();
        startProp.bind(
                Bindings.createDoubleBinding(() -> {
                            double axisPosition = axisOther.getDisplayPosition(axisOther.getLowerBound());
                            Point2D positionInScene = isX ? axisOther.localToScene(0, axisPosition) : axisOther.localToScene(axisPosition, 0);
                            return isX ? sceneToLocal(positionInScene).getY() : sceneToLocal(positionInScene).getX();
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
        var endProp = isX ? line.endYProperty() : line.endXProperty();
        endProp.bind(
                Bindings.createDoubleBinding(() -> {
                            double axisPosition = axisOther.getDisplayPosition(axisOther.getUpperBound());
                            Point2D positionInScene = isX ? axisOther.localToScene(0, axisPosition) : axisOther.localToScene(axisPosition, 0);
                            return isX ? sceneToLocal(positionInScene).getY() : sceneToLocal(positionInScene).getX();
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
    }

    private void bindThresholdLine(ObservableNumberValue d, Line line, boolean isX) {
        // Bind the requested x position of the line to the 'actual' coordinate within the parent
        var startProp = isX ? line.startXProperty(): line.startYProperty();
        Axis<Number> axisUse = isX ? xAxis : yAxis;
        startProp.bind(
                Bindings.createDoubleBinding(() -> {
                            double axisPosition = axisUse.getDisplayPosition(d.doubleValue());
                            Point2D positionInScene = isX ? axisUse.localToScene(axisPosition, 0) : axisUse.localToScene(0, axisPosition);
                            return isX ? sceneToLocal(positionInScene).getX(): sceneToLocal(positionInScene).getY();
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
        var endProp = isX ? line.endXProperty(): line.endYProperty();

        // End position same as starting position for vertical/horizontal line
        endProp.bind(startProp);
    }

    /**
     * Line width property used for displaying threshold lines.
     *
     * @return
     */
    public DoubleProperty lineWidthProperty() {
        return lineWidth;
    }

    /**
     * Get the threshold line width.
     *
     * @return
     */
    public double getLineWidth() {
        return lineWidth.get();
    }

    /**
     * Set the threshold line width.
     *
     * @param width
     */
    public void setLineWidth(final double width) {
        lineWidth.set(width);
    }

    /**
     * Property indicating whether thresholds can be adjusted interactively.
     *
     * @return
     */
    public BooleanProperty isInteractiveProperty() {
        return isInteractive;
    }

    /**
     * Returns the value of {@link #isInteractiveProperty()}.
     *
     * @return
     */
    public boolean isInteractive() {
        return isInteractive.get();
    }

    /**
     * Sets the value of {@link #isInteractiveProperty()}.
     *
     * @param isInteractive
     */
    public void setIsInteractive(final boolean isInteractive) {
        this.isInteractive.set(isInteractive);
    }


}
