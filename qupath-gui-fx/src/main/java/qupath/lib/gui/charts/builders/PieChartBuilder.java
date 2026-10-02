package qupath.lib.gui.charts.builders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javafx.scene.chart.PieChart;
import javafx.scene.paint.Color;
import qupath.lib.gui.charts.ChartTools;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

/**
 * Builder for creating pie charts.
 */
public class PieChartBuilder extends Charts.ChartBuilder<PieChartBuilder, PieChart> {

    private Map<Object, Number> data = new LinkedHashMap<>();
    private final Function<Object, String> stringFun = null;

    private boolean tooltips = true;
    private boolean convertToPercentages = false;

    PieChartBuilder() {
        this.legendVisible = true;
    }

    @Override
    protected PieChartBuilder getThis() {
        return this;
    }

    @Override
    protected PieChart createNewChart() {
        var pieChart = new PieChart();
        pieChart.setAnimated(false); // Don't animate by default
        return pieChart;
    }

    @Override
    protected String getDefaultWindowTitle() {
        return "Pie Chart";
    }

    /**
     * Specify data for the pie chart as a map.
     * Keys refer to categories, and values are numeric determining the size of the corresponding slice.
     *
     * @param data the data map to show
     * @return this builder
     */
    public PieChartBuilder data(Map<?, ? extends Number> data) {
        for (var entry : data.entrySet()) {
            addSlice(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Request that pie chart values are converted to percentages for tooltips.
     *
     * @param doConvert
     * @return
     */
    public PieChartBuilder convertToPercentages(boolean doConvert) {
        this.convertToPercentages = doConvert;
        return this;
    }

    /**
     * Request tooltips to be shown when the cursor hovers over the pie chart.
     *
     * @param showTooltips
     * @return
     */
    public PieChartBuilder tooltips(boolean showTooltips) {
        this.tooltips = showTooltips;
        return this;
    }

    /**
     * Add a slice to the pie.
     *
     * @param name  object the slice represents
     * @param value number that determines the proportion of the pie for the given slice
     * @return this builder
     */
    public PieChartBuilder addSlice(Object name, Number value) {
        data.put(name, value);
        return this;
    }

    @Override
    protected void updateChart(PieChart chart) {
        super.updateChart(chart);
        ChartTools.setPieChartData(chart, data,
                stringFun,
                PieChartBuilder::colorExtractor, convertToPercentages, tooltips);
    }

    static Color colorExtractor(Object key) {
        Integer rgb = null;
        if (key instanceof PathClass)
            rgb = ((PathClass) key).getColor();
        else if (key instanceof PathObject)
            rgb = ColorToolsFX.getDisplayedColorARGB((PathObject) key);
        return rgb == null ? null : ColorToolsFX.getCachedColor(rgb);
    }


}
