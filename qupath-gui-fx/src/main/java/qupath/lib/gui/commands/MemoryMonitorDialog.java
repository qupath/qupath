/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.gui.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.Duration;
import qupath.lib.gui.QuPathGUI;
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
class MemoryMonitorDialog {

	private final static Logger logger = LoggerFactory.getLogger(MemoryMonitorDialog.class);

	private QuPathGUI qupath;

	private Stage stage;

	private XYChart.Series<Number, Number> seriesTotal = new XYChart.Series<>();
	private XYChart.Series<Number, Number> seriesUsed = new XYChart.Series<>();

	// Store time
	private long startTimeMillis;
	private LongProperty timeMillis = new SimpleLongProperty();

	// Observable properties to store memory values
	private LongProperty maxMemory = new SimpleLongProperty();
	private LongProperty totalMemory = new SimpleLongProperty();
	private LongProperty usedMemory = new SimpleLongProperty();

	// Observable properties to store cache values
	private LongProperty cachedTiles = new SimpleLongProperty();
	private LongProperty undoRedoSizeBytes = new SimpleLongProperty();

	// Let's sometimes scale to MB, sometimes to GB
	private final static double scaleMB = 1.0/1024.0/1024.0;
	private final static double scaleGB = scaleMB/1024.0;
	
	private final MemoryService service = new MemoryService();
	
	MemoryMonitorDialog(QuPathGUI qupath) {
		this.qupath = qupath;

		// Create a chart to show how memory use evolves over time
		var xAxis = new NumberAxis();
		xAxis.setLabel("Time (seconds)");
		var yAxis = new NumberAxis();
		yAxis.setLabel("Memory (GB)");
		var chart = new AreaChart<Number, Number>(xAxis, yAxis);
		yAxis.setAutoRanging(false);
		yAxis.setLowerBound(0.0);
		yAxis.setTickUnit(1.0);
		yAxis.setUpperBound(Math.ceil(Runtime.getRuntime().maxMemory() * scaleGB));
		xAxis.setAutoRanging(false);
		xAxis.upperBoundProperty().bind(Bindings.createLongBinding(() -> {
			return Math.max(10L, (timeMillis.get() - startTimeMillis) / 1000);
		}, timeMillis));
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
		Label labelClearCache = new Label();
		labelClearCache.textProperty().bind(Bindings.createStringBinding(() -> {
			return String.format("Num cached tiles: %d", cachedTiles.get());
		}, cachedTiles));
		var btnClearCache = new Button("Clear tile cache");
		btnClearCache.setTooltip(new Tooltip("Clear the cache used to store image tiles for better viewer performance"));
		btnClearCache.setOnAction(e -> {
			try {
				logger.info("Clearing cache...");
				qupath.getViewer().getImageRegionStore().clearCache();
				System.gc();
			} catch (Exception e2) {
				logger.error("Error clearing cache", e);
			}
		});
		btnClearCache.setMaxWidth(Double.MAX_VALUE);

		// Clear Undo/Redo manager
		Label labelUndoRedo = new Label();
		labelUndoRedo.textProperty().bind(Bindings.createStringBinding(() -> {
			return String.format("Undo/Redo memory: %.2f GB", undoRedoSizeBytes.get()/(1024.0 * 1024.0 * 1024.0));
		}, undoRedoSizeBytes));
		var btnClearUndoRedo = new Button("Reset undo/redo");
		btnClearUndoRedo.setTooltip(new Tooltip("Clear all the data needed to support undo/redo"));
		btnClearUndoRedo.setOnAction(e -> {
			try {
				logger.info("Clearing undo/redo...");
				qupath.getUndoRedoManager().clear();
				System.gc();
			} catch (Exception e2) {
				logger.error("Error undo/redo", e);
			}
		});
		btnClearUndoRedo.setMaxWidth(Double.MAX_VALUE);

		// Add a button to run the garbage collector
		var btnGarbageCollector = new Button("Reclaim memory");
		btnGarbageCollector.setTooltip(new Tooltip("Request all available memory be reclaimed (this helps give a more accurate graph)"));
		btnGarbageCollector.setOnAction(e -> System.gc());
		btnGarbageCollector.setMaxWidth(Double.MAX_VALUE);
				
		var btnToggleMonitoring = new ToggleButton();
		btnToggleMonitoring.textProperty().bind(Bindings.createStringBinding(() -> {
			if (btnToggleMonitoring.isSelected())
				return "Stop monitoring";
			else
				return "Start monitoring";
		}, btnToggleMonitoring.selectedProperty()));
		btnToggleMonitoring.setMaxWidth(Double.MAX_VALUE);
		btnToggleMonitoring.selectedProperty().addListener((v, o, n) -> {
			if (n) {
				if (!service.isRunning())
					service.restart();
			} else {
				if (service.isRunning()) {
					service.cancel();
					service.reset();
				}
			}
		});
		var btnReset = new Button("Reset monitor");
		btnReset.setOnAction(e -> {
			startTimeMillis = 0L;
			seriesUsed.getData().clear();
			seriesTotal.getData().clear();
		});
		btnReset.setMaxWidth(Double.MAX_VALUE);

		// Add a text field to adjust the number of parallel threads
		// This is handy to scale back memory use when running things like cell detection
		var runtime = Runtime.getRuntime();
		var labThreads = new Label("Parallel threads");
		var tfThreads = new TextField(Integer.toString(PathPrefs.numCommandThreadsProperty().get()));
		PathPrefs.numCommandThreadsProperty().addListener((v, o, n) -> {
			var text = n.toString();
			if (!text.trim().equals(tfThreads.getText().trim()));
			tfThreads.setText(text);
		});
		tfThreads.setPrefColumnCount(4);
		tfThreads.textProperty().addListener((v, o, n) -> {
			try {
				PathPrefs.numCommandThreadsProperty().set(Integer.parseInt(n.trim()));
			} catch (Exception e) {}
		});
		labThreads.setLabelFor(tfThreads);

		// Create a pane to show it all
		var paneRight = new GridPane();
		int col = 0;
		int row = 0;
		paneRight.add(new Label("Available processors: " + runtime.availableProcessors()), col, row++, 1, 1);
		paneRight.add(labThreads, col, row, 1, 1);
		paneRight.add(tfThreads, col+1, row++, 1, 1);
		paneRight.add(labelClearCache, col, row++, 2, 1);
		paneRight.add(btnClearCache, col, row++, 2, 1);

		paneRight.add(labelUndoRedo, col, row++, 2, 1);
		paneRight.add(btnClearUndoRedo, col, row++, 2, 1);
		paneRight.add(btnGarbageCollector, col, row++, 2, 1);
		
		var padding = new BorderPane();
		paneRight.add(padding, col, row++, 2, 1);
		padding.setMaxHeight(Double.MAX_VALUE);
		GridPane.setFillHeight(padding, Boolean.TRUE);
		GridPane.setVgrow(padding, Priority.ALWAYS);

		paneRight.add(btnToggleMonitoring, col, row++, 2, 1);
		paneRight.add(btnReset, col, row++, 2, 1);

//		GridPane.setMargin(btnToggleMonitoring, new Insets(10, 0, 0, 0));
		paneRight.setPadding(new Insets(10));
		paneRight.setVgap(5);
		var pane = new BorderPane(chart);
		pane.setRight(paneRight);

		// Create a timer that will snapshot the current memory usage & update the chart
		service.setPeriod(Duration.seconds(1.0));
		service.lastValueProperty().addListener((v, o, n) -> {
			if (n == null)
				return;
			if (startTimeMillis <= 0)
				startTimeMillis = n.timeMillis;
			timeMillis.set(n.timeMillis);
			maxMemory.set(n.maxMemory);
			totalMemory.set(n.totalMemory);
			usedMemory.set(n.usedMemory);
			undoRedoSizeBytes.set(n.undoRedoSizeBytes);
			cachedTiles.set(n.cachedTiles);
			
			long time = (timeMillis.get() - startTimeMillis) / 1000;
			seriesUsed.getData().add(new XYChart.Data<Number, Number>(time, usedMemory.get()*scaleGB));
			seriesTotal.getData().add(new XYChart.Data<Number, Number>(time, totalMemory.get()*scaleGB));
		});

		// Show the GUI
		stage = new Stage();
		stage.initOwner(qupath.getStage());
		stage.setScene(new Scene(pane));
		stage.setTitle("Memory monitor");
		
		stage.setOnShowing(e -> {
			btnToggleMonitoring.setSelected(true);
		});
		
		stage.setOnHiding(e -> {
			btnToggleMonitoring.setSelected(false);
			btnReset.fire();
		});
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
	
	
	class MemoryService extends ScheduledService<MemorySnapshot> {

		@Override
		protected Task<MemorySnapshot> createTask() {
			return new Task<MemoryMonitorDialog.MemorySnapshot>() {
				@Override
				protected MemorySnapshot call() {
					return new MemorySnapshot(qupath, Runtime.getRuntime());
				}
			};
		}
		
	}
	
	
	static class MemorySnapshot {
		
		private long timeMillis;
		private long totalMemory;
		private long maxMemory;
		private long usedMemory;
		private long undoRedoSizeBytes;
		private long cachedTiles;
		
		MemorySnapshot(QuPathGUI qupath, Runtime runtime) {
			this.timeMillis = System.currentTimeMillis();
			this.totalMemory = runtime.totalMemory();
			this.maxMemory = runtime.maxMemory();
			this.usedMemory = totalMemory - runtime.freeMemory();
			this.undoRedoSizeBytes = qupath.getUndoRedoManager().totalBytes();
			this.cachedTiles = qupath.getViewer().getImageRegionStore().getCache().size();
		}
		
	}


}