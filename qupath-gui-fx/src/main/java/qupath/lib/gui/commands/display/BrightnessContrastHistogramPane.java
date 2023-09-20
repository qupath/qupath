package qupath.lib.gui.commands.display;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.common.ColorTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.charts.ChartThresholdPane;
import qupath.lib.gui.charts.HistogramChart;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BrightnessContrastHistogramPane extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(BrightnessContrastChannelPane.class);

    private static final DecimalFormat df = new DecimalFormat("#.###");

    private final HistogramChart histogramChart = new HistogramChart();

    private final ChartThresholdPane chartPane = new ChartThresholdPane(histogramChart);

    private final BooleanProperty doLogCounts = PathPrefs.createPersistentPreference("brightnessContrastLogCounts", false);

    private final Tooltip chartTooltip = new Tooltip(); // Basic stats go here now

    private final DoubleProperty minValueProperty = new SimpleDoubleProperty(Double.NEGATIVE_INFINITY);
    private final DoubleProperty maxValueProperty = new SimpleDoubleProperty(Double.NEGATIVE_INFINITY);

    BrightnessContrastHistogramPane() {
        logger.trace("Creating histogram pane");
        initialize();
    }

    private void initialize() {
        initializeHistogram();
        chartPane.setPrefWidth(200);
        chartPane.setPrefHeight(150);
        chartPane.getThresholds().setAll(minValueProperty, maxValueProperty);
        chartPane.setIsInteractive(true);
        setCenter(chartPane);
    }

    private void initializeHistogram() {
        doLogCountsProperty().addListener(this::handleDoLogCountsChange);
        handleDoLogCountsChange(doLogCounts, null, doLogCounts.get());
        histogramChart.setDisplayMode(HistogramChart.DisplayMode.AREA);

        histogramChart.setShowTickLabels(false);
        histogramChart.setAnimated(false);
        histogramChart.setHideIfEmpty(true);

        histogramChart.getXAxis().setAutoRanging(false);
        histogramChart.getXAxis().setTickLabelsVisible(true);

        histogramChart.getYAxis().setAutoRanging(false);
        histogramChart.getYAxis().setTickLabelsVisible(false);
    }


    private void handleDoLogCountsChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        if (newValue) {
            histogramChart.countsTransformProperty().set(HistogramChart.CountsTransformMode.LOGARITHM);
        } else {
            histogramChart.countsTransformProperty().set(HistogramChart.CountsTransformMode.RAW);
        }
        updateYAxis();
    }


    public BooleanProperty doLogCountsProperty() {
        return doLogCounts;
    }

    public DoubleProperty minValueProperty() {
        return minValueProperty;
    }

    public DoubleProperty maxValueProperty() {
        return maxValueProperty;
    }

    public void updateHistogram(ImageDisplay imageDisplay, ChannelDisplayInfo channelSelected) {
        Histogram histogram = (imageDisplay == null || channelSelected == null) ? null : imageDisplay.getHistogram(channelSelected);
        if (histogram == null) {
            // Try to show RGB channels together
            if (channelSelected != null && imageDisplay != null &&
                    imageDisplay.getImageData() != null &&
                    imageDisplay.getImageData().getServer().isRGB() &&
                    "original".equalsIgnoreCase(channelSelected.getName())) {
                var allHistograms = getRGBHistograms(imageDisplay);
                histogramChart.getHistogramData().setAll(allHistograms);
            } else {
                histogramChart.getHistogramData().clear();
            }
        } else {
            HistogramChart.HistogramData histogramData = HistogramChart.createHistogramData(histogram, getColor(channelSelected));
            histogramChart.getHistogramData().setAll(histogramData);
        }
        updateAxes(channelSelected);
        updateTooltip(histogram);
    }

    private void updateAxes(ChannelDisplayInfo channelSelected) {
        NumberAxis xAxis = (NumberAxis) histogramChart.getXAxis();
        if (channelSelected != null && channelSelected.getMaxAllowed() == 255 && channelSelected.getMinAllowed() == 0) {
            if (!xAxis.lowerBoundProperty().isBound())
                xAxis.setLowerBound(0);
            if (!xAxis.upperBoundProperty().isBound())
                xAxis.setUpperBound(255);
        } else if (channelSelected != null) {
            if (!xAxis.lowerBoundProperty().isBound())
                xAxis.setLowerBound(channelSelected.getMinAllowed());
            if (!xAxis.upperBoundProperty().isBound())
                xAxis.setUpperBound(channelSelected.getMaxAllowed());
        }
        if (channelSelected != null) {
            if (!xAxis.tickLabelsVisibleProperty().isBound())
                xAxis.setTickUnit(channelSelected.getMaxAllowed() - channelSelected.getMinAllowed());
            updateYAxis();
        }
    }

    private void updateYAxis() {
        // Don't use the first or last count if it's an outlier & we have many bins
        NumberAxis yAxis = (NumberAxis) histogramChart.getYAxis();
        double maxCount = histogramChart.getHistogramData()
                .stream()
                .mapToDouble(h -> h.getHistogram().getMaxCount())
                .max()
                .orElse(1.0);
        double yMax;
        if (doLogCounts.get())
            yMax = Math.log(maxCount);
        else
            yMax = maxCount;
        if (!yAxis.lowerBoundProperty().isBound())
            yAxis.setLowerBound(0);
        if (!yAxis.upperBoundProperty().isBound())
            yAxis.setUpperBound(yMax);
        if (!yAxis.tickLabelsVisibleProperty().isBound())
            yAxis.setTickLabelsVisible(false);
        if (!yAxis.tickUnitProperty().isBound())
            yAxis.setTickUnit(yMax);
        // Minor ticks *should* have a log scale, but don't (since we don't have a proper log axis)
        // so better just not to show them.
        if (!yAxis.minorTickCountProperty().isBound()) {
            if (doLogCounts.get())
                yAxis.setMinorTickCount(0);
            else
                yAxis.setMinorTickCount(5);
        }
    }

    private void updateTooltip(Histogram histogram) {
        GridPane pane = new GridPane();
        pane.setHgap(4);
        pane.setVgap(2);
        int row = 0;
        if (histogram != null) {
            pane.add(new Label("Min"), 0, row);
            pane.add(new Label(df.format(histogram.getMinValue())), 1, row);
            row++;
            pane.add(new Label("Max"), 0, row);
            pane.add(new Label(df.format(histogram.getMaxValue())), 1, row);
            row++;
            pane.add(new Label("Mean"), 0, row);
            pane.add(new Label(df.format(histogram.getMeanValue())), 1, row);
            row++;
            pane.add(new Label("Std.dev"), 0, row);
            pane.add(new Label(df.format(histogram.getStdDev())), 1, row);
            row++;
        }
        chartTooltip.setGraphic(pane);

        if (row == 0)
            Tooltip.uninstall(histogramChart, chartTooltip);
        else
            Tooltip.install(histogramChart, chartTooltip);
    }

    private static List<HistogramChart.HistogramData> getRGBHistograms(ImageDisplay imageDisplay) {
        List<HistogramChart.HistogramData> data = new ArrayList<>();
        for (var c : imageDisplay.availableChannels()) {
            var method = c.getMethod();
            if (method == null)
                continue;
            switch (method) {
                case Red:
                case Green:
                case Blue:
                    var hist = imageDisplay.getHistogram(c);
                    if (hist != null) {
                        var histogramData = HistogramChart.createHistogramData(hist, getColor(c));
                        data.add(histogramData);
                    }
                    break;
                default:
                    break;
            }
        }
        return data;
    }


    private static Color getColor(ChannelDisplayInfo channel) {
        if (channel == null)
            return null;
        Integer color = channel.getColor();
        // We want default color for black or white - since otherwise the channel may not be visible in the histogram
        if (color == null || Objects.equals(ColorTools.BLACK, color) || Objects.equals(ColorTools.WHITE, color))
            return null;
        return ColorToolsFX.getCachedColor(color);
    }

}
