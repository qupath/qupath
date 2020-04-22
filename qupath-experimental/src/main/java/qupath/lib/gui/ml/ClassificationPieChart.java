package qupath.lib.gui.ml;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.chart.PieChart;
import qupath.lib.gui.charts.ChartTools;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.objects.classes.PathClass;

/**
 * Pie chart for displaying PathClasses and counts.
 * This can be used to track proportions of different classifications in a training set.
 * 
 * @author Pete Bankhead
 */
public class ClassificationPieChart {
	
	private final static Logger logger = LoggerFactory.getLogger(ClassificationPieChart.class);

	private PieChart chart = new PieChart();
	
	/**
	 * Set the pie chart data.
	 * @param counts count
	 * @param convertToPercentages if true, convert counts to percentages
	 */
	public void setData(Map<PathClass, ? extends Number> counts, boolean convertToPercentages) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> setData(counts, convertToPercentages));
			return;
		}
		ChartTools.setPieChartData(chart, counts, PathClass::toString, p -> ColorToolsFX.getCachedColor(p.getColor()), convertToPercentages, true);
	}

	/**
	 * Get the {@link PieChart} for addition to a scene.
	 * @return
	 */
	public PieChart getChart() {
		return chart;
	}


}