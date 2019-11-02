package qupath.lib.gui.commands;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * A basic GUI to help monitor memory usage in QuPath.
 * <p>
 * This helps both to find and address out-of-memory troubles by
 * <ol>
 *   <li>Showing how much memory is in use over time</li>
 *   <li>Giving a button to clear the tile cache - which can be
 *      using up precious memory</li>
 *   <li>Giving quick access to control the number of threads used
 *      for parallel processing</li>
 * </ol>
 * If you find QuPath crashing/freezing, look to see if the memory
 * use is especially high.
 * You can run this command in the background while going about your
 * normal analysis, and check in to see how it is doing.
 * <p>
 * If QuPath crashes when running memory-hungry commands like cell detection
 * across a large image or TMA, try reducing the number of parallel threads.
 *
 * @author Pete Bankhead
 */
public class MemoryMonitorCommand implements PathCommand {
	
	private final static Logger logger = LoggerFactory.getLogger(MemoryMonitorCommand.class);
	
	private QuPathGUI qupath;
	
	public MemoryMonitorCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		new MemoryMonitor(qupath).getStage().show();
	}
	
	
	static class MemoryMonitor {
		
		private QuPathGUI qupath;
		
		private Stage stage;
		
		private XYChart.Series<Number, Number> seriesTotal = new XYChart.Series<>();
		private XYChart.Series<Number, Number> seriesUsed = new XYChart.Series<>();

		// Observable properties to store memory values
		private LongProperty maxMemory = new SimpleLongProperty();
		private LongProperty totalMemory = new SimpleLongProperty();
		private LongProperty usedMemory = new SimpleLongProperty();
		
		// Let's sometimes scale to MB, sometimes to GB
		private final static double scaleMB = 1.0/1024.0/1024.0;
		private final static double scaleGB = scaleMB/1024.0;
		
		private long sampleFrequency = 1000L;
		
		MemoryMonitor(QuPathGUI qupath) {
			this.qupath = qupath;
			
			// Create a timer to poll for memory status once per second
			var timer = new Timer("QuPath memory monitor", true);

			// Create a chart to show how memory use evolves over time
			var xAxis = new NumberAxis();
			xAxis.setLabel("Time (samples)");
			var yAxis = new NumberAxis();
			yAxis.setLabel("Memory (GB)");
			var chart = new AreaChart<Number, Number>(xAxis, yAxis);
			yAxis.setAutoRanging(false);
			yAxis.setLowerBound(0.0);
			yAxis.setTickUnit(1.0);
			yAxis.setUpperBound(Math.ceil(Runtime.getRuntime().maxMemory() * scaleGB));
			xAxis.setAutoRanging(true);
			// Bind the series names to the latest values, in MB
			seriesTotal.nameProperty().bind(Bindings.createStringBinding(
			        () -> String.format("Total memory (%.1f MB)", totalMemory.get() * scaleMB), totalMemory));
			seriesUsed.nameProperty().bind(Bindings.createStringBinding(
					() -> String.format("Used memory (%.1f MB)", usedMemory.get() * scaleMB), usedMemory));
			chart.getData().add(seriesTotal);
			chart.getData().add(seriesUsed);
			chart.setLegendVisible(true);
			chart.setLegendSide(Side.TOP);
			chart.setAnimated(false);
			chart.setCreateSymbols(false);

			// Add it button to make it possible to clear the tile cache
			// This is a bit of a hack, since there is no clean way to do it yet
			var btnClearCache = new Button("Clear tile cache");
			btnClearCache.setOnAction(e -> {
			    try {
			        logger.info("Clearing cache...");
			        QuPathGUI.getInstance().getViewer().getImageRegionStore().clearCache();
			        System.gc();
			    } catch (Exception e2) {
			        logger.error("Error clearing cache", e);;
			    }
			});
			btnClearCache.setMaxWidth(Double.MAX_VALUE);

			// Add a button to run the garbage collector
			var btnGarbageCollector = new Button("Reclaim memory");
			btnGarbageCollector.setOnAction(e -> System.gc());
			btnGarbageCollector.setMaxWidth(Double.MAX_VALUE);

			// Add a text field to adjust the number of parallel threads
			// This is handy to scale back memory use when running things like cell detection
			var runtime = Runtime.getRuntime();
			var labThreads = new Label("Parallel threads");
			var tfThreads = new TextField(Integer.toString(PathPrefs.getNumCommandThreads()));
			PathPrefs.numCommandThreadsProperty().addListener((v, o, n) -> {
			    var text = n.toString();
			    if (!text.trim().equals(tfThreads.getText().trim()));
			        tfThreads.setText(text);
			});
			tfThreads.setPrefColumnCount(4);
			tfThreads.textProperty().addListener((v, o, n) -> {
			    try {
			        PathPrefs.setNumCommandThreads(Integer.parseInt(n.trim()));
			    } catch (Exception e) {}
			});
			labThreads.setLabelFor(tfThreads);

			// Create a pane to show it all
			var paneBottom = new GridPane();
			int col = 0;
			int row = 0;
			paneBottom.add(new Label("Num processors: " + runtime.availableProcessors()), col, row++, 1, 1);
			paneBottom.add(labThreads, col, row, 1, 1);
			paneBottom.add(tfThreads, col+1, row++, 1, 1);
			paneBottom.add(btnClearCache, col, row++, 2, 1);
			paneBottom.add(btnGarbageCollector, col, row++, 2, 1);
			paneBottom.setPadding(new Insets(10));
			paneBottom.setVgap(5);
			var pane = new BorderPane(chart);
			pane.setRight(paneBottom);
			
		    // Create a timer that will snapshot the current memory usage & update the chart
		    timer.schedule(new MemoryTimerTask(), 0L, sampleFrequency);

		    // Show the GUI
		    stage = new Stage();
		    stage.initOwner(qupath.getStage());
		    stage.setScene(new Scene(pane));
		    stage.setTitle("Memory monitor");
		    stage.setOnHiding(e -> timer.cancel());
		}
		
		public Stage getStage() {
			return stage;
		}
		
		/**
		 * Add a data point for the current memory usage
		 */
		void snapshot() {
		    var time = seriesUsed.getData().size() + 1;
		    seriesUsed.getData().add(new XYChart.Data<Number, Number>(time, usedMemory.get()*scaleGB));
		    seriesTotal.getData().add(new XYChart.Data<Number, Number>(time, totalMemory.get()*scaleGB));
		}
		
		class MemoryTimerTask extends TimerTask {
			
			private Runtime runtime = Runtime.getRuntime();

			@Override
			public void run() {
				Platform.runLater(() -> {
					totalMemory.set(runtime.totalMemory());
					maxMemory.set(runtime.maxMemory());
					usedMemory.set(runtime.totalMemory() - runtime.freeMemory());
					snapshot();
				});
			}
			
		}
		
		
	}
	

}