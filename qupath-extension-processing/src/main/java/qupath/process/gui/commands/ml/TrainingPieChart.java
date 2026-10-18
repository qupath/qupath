package qupath.process.gui.commands.ml;

import java.util.Collections;
import java.util.Map;
import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.chart.PieChart;
import qupath.lib.gui.charts.ChartTools;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.objects.classes.PathClass;

class TrainingPieChart extends PieChart {

    TrainingPieChart() {
        initialize();
    }

    private void initialize() {
        getStyleClass().add("training-chart");
        setAnimated(false);

        setLabelsVisible(false);
        setLegendVisible(true);
        setMinSize(40, 40);
        setPrefSize(120, 120);
        setLegendSide(Side.RIGHT);
    }

    void reset() {
        updateCounts(Collections.emptyMap());
    }

    void updateCounts(Map<PathClass, ? extends Number> counts) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateCounts(counts));
            return;
        }
        ChartTools.setPieChartData(this, counts, PathClass::toString, p -> ColorToolsFX.getCachedColor(p.getColor()), true, !counts.isEmpty());
        if (counts.isEmpty())
            setTitle(null);
        else
            setTitle("Training data");
    }

}
