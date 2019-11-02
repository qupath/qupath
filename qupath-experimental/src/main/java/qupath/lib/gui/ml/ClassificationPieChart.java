package qupath.lib.gui.ml;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.PieChart.Data;
import javafx.scene.control.Tooltip;
import qupath.lib.common.ColorTools;
import qupath.lib.objects.classes.PathClass;

/**
 * Pie chart for displaying PathClasses and counts.
 * This can be used to track proportions of different classifications in a training set.
 * 
 * @author Pete Bankhead
 */
class ClassificationPieChart {
	
	private final static Logger logger = LoggerFactory.getLogger(ClassificationPieChart.class);
	
	private static Map<String, String> piechartStyleSheets = new HashMap<>();


	private PieChart chart = new PieChart();

	void setData(Map<PathClass, Integer> counts, boolean convertToPercentages) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> setData(counts, convertToPercentages));
		}

		var style = new StringBuilder();

		double sum = counts.values().stream().mapToDouble(i -> i).sum();
		var newData = new ArrayList<Data>();
		int ind = 0;
		var tooltips = new HashMap<Data, Tooltip>();
		for (Entry<PathClass, Integer> entry : counts.entrySet()) {
			var pathClass = entry.getKey();
			var name = pathClass.toString();
			double value = entry.getValue();
			if (convertToPercentages)
				value = value / sum * 100.0;
			var datum = new Data(name, value);
			newData.add(datum);

			var color = pathClass.getColor();
			style.append(String.format(".default-color%d.chart-pie { -fx-pie-color: rgb(%d, %d, %d); }", ind++, 
					ColorTools.red(color),
					ColorTools.green(color),
					ColorTools.blue(color))).append("\n");

			var text = String.format("%s: %.1f%%", name, value);
			tooltips.put(datum, new Tooltip(text));
		}

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

		chart.setAnimated(false);
		chart.getData().setAll(newData);

		for (var entry : tooltips.entrySet()) {
			Tooltip.install(entry.getKey().getNode(), entry.getValue());
		}

	}

	public PieChart getChart() {
		return chart;
	}


}