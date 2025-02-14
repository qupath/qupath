/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
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
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

/**
 * An interactive {@link ScatterChart} implementation for showing large(ish) numbers of {@link PathObject},
 * optionally linked to a {@link QuPathViewer}.
 * <p>
 * A goal of this class is to reduce expensive chart manipulations, such as adding and removing data points.
 * Instead, data points are reused and updated where possible.
 * <p>
 * Additionally, the maximum number of data points can be specified, so that the chart can automatically randomly
 * subsample points to show only some.
 *
 * @since v0.6.0
 */
public class PathObjectScatterChart extends ScatterChart<Number, Number> {

    private static final Logger logger = LoggerFactory.getLogger(PathObjectScatterChart.class);

    /**
     * Seed value to use to specify that no shuffling should be applied before displaying the objects.
     */
    public static final int NO_SHUFFLE_SEED = -1;

    private final WeakReference<QuPathViewer> viewer;

    private final IntegerProperty rngSeed = new SimpleIntegerProperty(NO_SHUFFLE_SEED);
    private final DoubleProperty pointOpacity = new SimpleDoubleProperty(1);
    private final DoubleProperty pointRadius = new SimpleDoubleProperty(5);
    private final IntegerProperty maxPoints = new SimpleIntegerProperty(10000);

    private final BooleanProperty autorangeToFullData = new SimpleBooleanProperty(true);

    // List of all objects to display - we retain this only so that we can shuffle reproducibly if the seed changes
    private final ObservableList<PathObject> allData = FXCollections.observableArrayList();

    // Shuffled objects - this is the main list we use, in preference to allData
    private final ObservableList<PathObject> shuffledData = FXCollections.observableArrayList();

    // Represented classes - for legend
    private final List<PathClass> pathClasses = new ArrayList<>();

    // Functions to calculate x and y values from object
    private Function<PathObject, Number> xFun;
    private Function<PathObject, Number> yFun;

    // Store extrema
    private double xMin = Double.POSITIVE_INFINITY;
    private double xMax = Double.NEGATIVE_INFINITY;
    private double yMin = Double.POSITIVE_INFINITY;
    private double yMax = Double.NEGATIVE_INFINITY;

    // Use one series for everything
    private final Series<Number, Number> series = new Series<>("All objects", FXCollections.observableArrayList());

    private final Effect pointHoverEffect = new DropShadow(
            BlurType.THREE_PASS_BOX,
            new Color(0, 0, 0, 0.5d),
            4, 0, 1, 1);

    private final EventHandler<MouseEvent> pointEventHandler = this::handleMouseEvent;


    /**
     * Create an interactive scatter plot using the current viewer
     * @param viewer The QuPath viewer.
     */
    public PathObjectScatterChart(QuPathViewer viewer) {
        super(new NumberAxis(), new NumberAxis());
        this.viewer = viewer == null ? null : new WeakReference<>(viewer);
        maxPoints.addListener(o -> ensureMaxPoints());
        pointOpacity.addListener(o -> updateOpacity());
        pointRadius.addListener(o -> updateRadius());
        rngSeed.addListener(this::handleRngSeedChange);
        autorangeToFullData.addListener((v, o, n) -> {
            resampleAndUpdate();
            updateAxisRange();
        });

        // Animation is unlikely to go well if we have lots of points
        setAnimated(false);

        // This is the *only* series we use
        getData().add(series);
        resampleAndUpdate();
    }

    private void handleRngSeedChange(ObservableValue<? extends Number> val, Number oldValue, Number newValue) {
        shuffleData();
        // We need to update even if not subsampling because
        // shuffling changes the order in which points are plotted
        resampleAndUpdate();
    }

    private void updateOpacity() {
        double opacity = pointOpacity.get();
        for (var item : series.getData()) {
            if (item.getNode() instanceof Circle circle) {
                circle.setOpacity(opacity);
            }
        }
    }

    private void updateRadius() {
        double radius = pointRadius.get();
        for (var item : series.getData()) {
            if (item.getNode() instanceof Circle circle) {
                circle.setRadius(radius);
            }
        }
    }

    private void ensureMaxPoints() {
        int max = GeneralTools.clipValue(maxPoints.get(), 0, shuffledData.size());
        int n = series.getData().size();
        if (max == n)
            return;
        // We want to remove or add as a bulk operation - without having to change everything
        if (n > max) {
            long startTime = System.currentTimeMillis();
            // You'd think removing from the end of the list would be faster...
            // alas, it takes more than twice as long as removing from the start of the list.
            // This makes it well worth the cost of refreshing the data.
//            series.getData().remove(max, n); // Simpler (slower) code
            series.getData().remove(0, n-max);
            refreshData();
            long endTime = System.currentTimeMillis();
            logger.trace("Removal time: {} ms", endTime - startTime);
        } else {
            var toAdd = shuffledData.subList(n, max).stream().map(this::createDataItem).toList();
            series.getData().addAll(toAdd);
        }
    }

    private Data<Number, Number> createDataItem(PathObject pathObject) {
        var item = new Data<Number, Number>();
        updateDataItem(item, pathObject);
        return item;
    }

    // Update a Data for the specified object.
    // This allows us to reuse objects and avoid adding/removing objects (which is expensive)
    private void updateDataItem(Data<Number, Number> item, PathObject pathObject) {
        if (pathObject != item.getExtraValue())
            item.setExtraValue(pathObject);
        Circle circle;
        if (item.getNode() instanceof Circle c) {
            circle = c;
        } else {
            circle = createSymbol();
            item.setNode(circle);
        }
        circle.setRadius(pointRadius.get());
        circle.setOpacity(pointOpacity.get());
        circle.setFill(ColorToolsFX.getDisplayedColor(pathObject));

        // TODO: Check if this is necessary, or if there's no appreciable overhead when setting to unchanged value
        var x = xFun.apply(pathObject);
        if (!Objects.equals(item.getXValue(), x))
            item.setXValue(x);

        var y = yFun.apply(pathObject);
        if (!Objects.equals(item.getYValue(), y))
            item.setYValue(y);
    }

    /**
     * Set the value of {@link #autorangeToFullDataProperty()}.
     * @param useFullData whether to use the full data when setting the axis autorange
     */
    public void setAutorangeToFullData(boolean useFullData) {
        this.autorangeToFullData.set(useFullData);
    }

    /**
     * Get a property representing whether to use the entire dataset when calculating axis
     * limits automatically.
     * This is useful whenever we are subsampling data points.
     * @return whether to use the full data when setting the axis autorange
     */
    public BooleanProperty autorangeToFullDataProperty() {
        return autorangeToFullData;
    }

    /**
     * Get the value of {@link #autorangeToFullDataProperty()}
     * @return whether to use the full data when setting the axis autorange
     */
    public boolean getAutorangeToFullData() {
        return autorangeToFullData.get();
    }

    /**
     * Set the value of {@link #maxPointsProperty()}.
     * @param maxPoints the maximum number of elements
     */
    public void setMaxPoints(int maxPoints) {
        this.maxPoints.set(maxPoints);
    }

    /**
     * Get a property representing the maximum number of points that the plot will display.
     * Subsampling will be used to ensure only this number of points or fewer are displayed.
     * @return the maximum number of points to show
     */
    public IntegerProperty maxPointsProperty() {
        return maxPoints;
    }

    /**
     * Get the value of {@link #maxPointsProperty()}
     * @return the maximum number of points to show
     */
    public int getMaxPoints() {
        return maxPoints.get();
    }

    /**
     * Set the value of {@link #rngSeedProperty()}.
     * @param rngSeed the random number generator seed to use
     */
    public void setRngSeed(int rngSeed) {
        this.rngSeed.set(rngSeed);
    }

    /**
     * Get a property representing the random number generator's seed for subsampling.
     * The default value is #NO_SHUFFLE_SEED which indicates no shuffling should be performed.
     * @return the rng seed property
     */
    public IntegerProperty rngSeedProperty() {
        return rngSeed;
    }

    /**
     * Get the value of {@link #rngSeedProperty()}.
     * @return the random number generator
     */
    public int getRngSeed() {
        return this.rngSeed.get();
    }

    /**
     * Set the value of {@link #pointOpacityProperty()}.
     * @param pointOpacity the new point opacity
     */
    public void setPointOpacity(double pointOpacity) {
        this.pointOpacity.set(pointOpacity);
    }

    /**
     * Get a property representing the opacity of all data points.
     * Making points translucent can be helpful when there are a lot of them.
     * @return the point opacity property
     */
    public DoubleProperty pointOpacityProperty() {
        return pointOpacity;
    }

    /**
     * Get the value o {@link #pointOpacityProperty()}
     * @return the point opacity property
     */
    public double getPointOpacity() {
        return pointOpacity.get();
    }

    /**
     * Set the value of {@link #pointOpacityProperty()}
     * @param radius the point radius
     */
    public void setPointRadius(double radius) {
        this.pointRadius.set(radius);
    }

    /**
     * Get a property representing the radius of all data points.
     * @return the point radius property
     */
    public DoubleProperty pointRadiusProperty() {
        return pointRadius;
    }

    /**
     * Get the value of {@link #pointOpacityProperty()}
     * @return the point radius
     */
    public double getPointRadius() {
        return pointRadius.get();
    }


    @Override
    protected void updateLegend() {
        List<Label> legendList = new ArrayList<>();
        for (PathClass pathClass : pathClasses) {
            var circle = new Circle();
            // Binding to radius & opacity doesn't work great, as we might not see the colors
            circle.setRadius(5.0);
            Integer rgb;
            if (pathClass == null)
                rgb = PathPrefs.colorDefaultObjectsProperty().get();
            else
                rgb = pathClass.getColor();
            circle.setFill(ColorToolsFX.getCachedColor(rgb));
            var item = new Label(pathClass == null ? "Unclassified" : pathClass.toString(), circle);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setContentDisplay(ContentDisplay.LEFT);
            legendList.add(item);
        }
        if (!legendList.isEmpty()) {
            var legend = new TilePane();
            legend.getChildren().setAll(legendList);
            setLegend(legend);
        } else {
            setLegend(null);
        }
    }

    private void shuffleData() {
        var seed = rngSeed.get();
        if (seed == NO_SHUFFLE_SEED) {
            shuffledData.setAll(allData);
        } else {
            var toShuffle = new ArrayList<>(allData);
            Collections.shuffle(toShuffle, new Random(seed));
            shuffledData.setAll(toShuffle);
        }
    }

    private void resampleAndUpdate() {
        long startTime = System.currentTimeMillis();
        int max = maxPoints.get();
        int n = GeneralTools.clipValue(shuffledData.size(), 0, max);
        List<Data<Number, Number>> toAdd = new ArrayList<>();
        var items = series.getData();
        int nItems = items.size();
        // It's convoluted, but it's faster to remove items from the start of the list
        // than the end
        if (n < nItems) {
            // We have items to remove
            // Warning! This is slower than adding... but I don't see how to optimize it further
            items.remove(0, nItems - n);
            nItems = items.size();
        }

        for (int i = 0; i < n; i++) {
            var pathObject = shuffledData.get(i);
            if (i < nItems) {
                updateDataItem(items.get(i), pathObject);
            } else {
                toAdd.add(createDataItem(pathObject));
            }
        }
        if (!toAdd.isEmpty()) {
            // We have items to add
            items.addAll(toAdd);
        } else if (n < nItems) {
            // This code isn't needed any more, since we remove items at the start!
            // It's kept for posterity... an in case removing at the end ever becomes faster
            items.remove(n, nItems);
        }
        long endTime = System.currentTimeMillis();
        logger.trace("Resample & update time: {} ms", endTime - startTime);
        recalculateExtrema();
        ensureSingleSeries();
        requestChartLayout(); // TODO: Check if this is necessary!
    }

    private void recalculateExtrema() {
        resetExtrema();
        if (!autorangeToFullData.get())
            return;
        var data = series.getData();
        int nItems = data.size();
        for (int i = 0; i < shuffledData.size(); i++) {
            double x, y;
            if (i < nItems) {
                var item = data.get(i);
                x = item.getXValue().doubleValue();
                y = item.getYValue().doubleValue();
            } else {
                var pathObject = shuffledData.get(i);
                x = xFun.apply(pathObject).doubleValue();
                y = yFun.apply(pathObject).doubleValue();
            }
            if (x < xMin)
                xMin = x;
            if (x > xMax)
                xMax = x;
            if (y < yMin)
                yMin = y;
            if (y > yMax)
                yMax = y;
        }
    }

    private void resetExtrema() {
        xMin = Double.POSITIVE_INFINITY;
        xMax = Double.NEGATIVE_INFINITY;
        yMin = Double.POSITIVE_INFINITY;
        yMax = Double.NEGATIVE_INFINITY;
    }

    private void ensureSingleSeries() {
        if (getData().size() != 1 || getData().getFirst() != series) {
            logger.debug("Resetting series!");
            getData().setAll(List.of(series));
        }
    }

    /**
     * Recalculate the values of all data points.
     * This may be useful when measurements or classifications may have changed.
     */
    public void refreshData() {
        if (xFun == null || yFun == null) {
            series.getData().clear();
            return;
        }
        for (var item : series.getData()) {
            if (item.getExtraValue() instanceof PathObject pathObject) {
                updateDataItem(item, pathObject);
            }
        }
    }

    /**
     * Set the data to display in the plot.
     * @param pathObjects the objects to display
     * @param xFun a function to extract the x value to plot
     * @param yFun a function to extract the y value to plot
     */
    public void setData(Collection<? extends PathObject> pathObjects,
                 Function<PathObject, Number> xFun,
                 Function<PathObject, Number> yFun) {

        // Store functions for lazy computation
        this.xFun = xFun;
        this.yFun = yFun;

        // Find the represented classes & sort them so they appear nicely in the legend
        var pathClasses = pathObjects
                .stream()
                .map(PathObject::getPathClass)
                .distinct()
                .sorted(Comparator.nullsFirst(PathClass::compareTo))
                .toList();
        this.pathClasses.clear();
        this.pathClasses.addAll(pathClasses);
        updateLegend();

        // Set the data - we'll actually plot the shuffled data
        this.allData.setAll(pathObjects);
        shuffleData();

        // Update
        resampleAndUpdate();
    }

    /**
     * Set the data to display in the plot from a table model.
     * <p>
     * This calls {@link #setData(Collection, Function, Function)} in addition to setting the x and y labels.
     *
     * @param pathObjects the objects to display
     * @param model the table model containing the measurements
     * @param xMeasurement the column to use for x values
     * @param yMeasurement the column to use for y values
     */
    public void setDataFromTable(Collection<? extends PathObject> pathObjects,
                          PathTableData<PathObject> model,
                          String xMeasurement, String yMeasurement) {
        setData(pathObjects,
                p -> model.getNumericValue(p, xMeasurement),
                p -> model.getNumericValue(p, yMeasurement));
        getXAxis().setLabel(xMeasurement);
        getYAxis().setLabel(yMeasurement);
    }


    private Circle createSymbol() {
        var circle = new Circle();
        // Binding point size & opacity here could sometimes cause trouble -
        // adding a single listener and refreshing seems more reliably performant
        circle.addEventHandler(MouseEvent.ANY, pointEventHandler);
        return circle;
    }

    @Override
    protected void dataItemAdded(Series<Number,Number> series, int itemIndex, Data<Number,Number> item) {
        var node = item.getNode();
        if (node == null) {
            // Make sure we create the node, not the superclass
            item.setNode(createSymbol());
        }
        super.dataItemAdded(series, itemIndex, item);
    }

    @Override
    protected void dataItemRemoved(Data<Number,Number> item, Series<Number,Number> series) {
        super.dataItemRemoved(item, series);
    }

    @Override
    protected void updateAxisRange() {
        if (!(autorangeToFullData.get() && xMax > xMin && yMax > yMin)) {
            super.updateAxisRange();
        } else {
            var xAxis = getXAxis();
            if (xAxis.isAutoRanging()) {
                xAxis.invalidateRange(List.of(xMin, xMax));
            }
            var yAxis = getYAxis();
            if (yAxis.isAutoRanging()) {
                yAxis.invalidateRange(List.of(yMin, yMax));
            }
        }
    }

    private void handleMouseEvent(MouseEvent event) {
        if (event.getSource() instanceof Circle circle) {
            if (event.getEventType() == MouseEvent.MOUSE_ENTERED)
                circle.setEffect(pointHoverEffect);
            else if (event.getEventType() == MouseEvent.MOUSE_EXITED)
                circle.setEffect(null);
            else if (event.getEventType() == MouseEvent.MOUSE_CLICKED && viewer != null && event.getButton() == MouseButton.PRIMARY) {
                var viewer = this.viewer.get();
                var hierarchy = viewer == null ? null : viewer.getHierarchy();
                if (hierarchy == null)
                    return;
                // I calculate that clicks will be rare events - and it's better to save the overhead of caching nodes
                // for the cost of iterating the list once
                var pathObject = series
                        .getData()
                        .stream()
                        .filter(d -> d.getNode() == circle && d.getExtraValue() instanceof PathObject)
                        .map(d -> (PathObject)d.getExtraValue())
                        .findFirst()
                        .orElse(null);
                // Need to make sure that the viewer hasn't changed
                if (pathObject != null && PathObjectTools.hierarchyContainsObject(hierarchy, pathObject)) {
                    Charts.ScatterChartBuilder.tryToSelect(
                            pathObject, viewer, viewer.getImageData(),
                            event.isShiftDown(), event.getClickCount() == 2);
                }
                event.consume();
            }
        }
    }

}
