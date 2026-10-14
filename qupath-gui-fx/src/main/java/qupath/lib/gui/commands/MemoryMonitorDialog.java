/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedAreaChart;
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
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.MemoryTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.cache.ImageCache;

import java.text.MessageFormat;

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
 */
class MemoryMonitorDialog {

	private static final Logger logger = LoggerFactory.getLogger(MemoryMonitorDialog.class);

	private final QuPathGUI qupath;

	private final Stage stage;

	// It's very unlikely (impossibly?) that no max memory is available, but we certainly can't use Long.MAX_VALUE,
	// so we need some kind of sensible default to set axes.
	private final long DEFAULT_MAX_MEMORY = MemoryTools.gibibytesToBytes(64L);

	private final XYChart.Series<Number, Number> seriesTileCache = new XYChart.Series<>();
	private final XYChart.Series<Number, Number> seriesUsed = new XYChart.Series<>();
	private final XYChart.Series<Number, Number> seriesTotal = new XYChart.Series<>();

	// Store time
	private long startTimeMillis;
	private final LongProperty timeMillis = new SimpleLongProperty();

	// Observable properties to store memory values
	private final LongProperty maxMemory = new SimpleLongProperty();
	private final LongProperty totalMemory = new SimpleLongProperty();
	private final LongProperty usedMemory = new SimpleLongProperty();

	// Observable properties to store cache values
	private final LongProperty cachedTiles = new SimpleLongProperty();
	private final LongProperty cacheSizeBytes = new SimpleLongProperty();
	private final LongProperty cacheMaxSizeBytes = new SimpleLongProperty();
	private final DoubleProperty cacheFillPercent = new SimpleDoubleProperty();
	private final LongProperty undoRedoSizeBytes = new SimpleLongProperty();

	// Let's sometimes scale to MB, sometimes to GB
	// Change for v0.8: use 1000, rather than 1024
	private static final double scaleMB = 1.0/1000.0/1000.0;
	private static final double scaleGB = scaleMB/1000.0;
	
	private final MemoryService service = new MemoryService();

	MemoryMonitorDialog(QuPathGUI qupath) {
		this(qupath, true);
	}

	MemoryMonitorDialog(QuPathGUI qupath, boolean useStackedAreas) {
		this.qupath = qupath;

		// Create a chart to show how memory use evolves over time
		var xAxis = new NumberAxis();
		xAxis.setLabel(QuPathResources.getString("Commands.MemoryMonitor.time"));
		var yAxis = new NumberAxis();
		yAxis.setLabel(QuPathResources.getString("Commands.MemoryMonitor.memory"));
		XYChart<Number, Number> chart;
		if (useStackedAreas) {
			chart = new StackedAreaChart<>(xAxis, yAxis);
			((StackedAreaChart)chart).setCreateSymbols(false);
		} else {
			chart = new AreaChart<>(xAxis, yAxis);
			((AreaChart)chart).setCreateSymbols(false);
		}
		yAxis.setAutoRanging(false);
		yAxis.setLowerBound(0.0);
		yAxis.setTickUnit(1.0);
		yAxis.setUpperBound(Math.ceil(MemoryTools.getMaxMemoryOrDefault(DEFAULT_MAX_MEMORY) * scaleGB));
		xAxis.setAutoRanging(false);
		xAxis.tickUnitProperty().bind(Bindings.createLongBinding(() -> {
			var seconds = (timeMillis.get() - startTimeMillis) / 1000;
			if (seconds <= 60)
				return 5L;
			if (seconds <= 120)
				return 10L;
			if (seconds <= 300)
				return 30L;
			if (seconds <= 600)
				return 60L;
			if (seconds <= 1200)
				return 120L;
			return 1000L;
		}, timeMillis));
		xAxis.upperBoundProperty().bind(Bindings.createDoubleBinding(() -> {
			var seconds = (timeMillis.get() - startTimeMillis) / 1000;
			return Math.max(10.0, seconds);
		}, timeMillis));
		// Bind the series names to the latest values, in MB
		seriesTotal.nameProperty().bind(Bindings.createStringBinding(
				() -> MessageFormat.format(
						QuPathResources.getString("Commands.MemoryMonitor.totalMemory"),
						String.format("%.1f", totalMemory.get() * scaleMB)
				),
				totalMemory
		));
		seriesTileCache.nameProperty().bind(Bindings.createStringBinding(
				() -> MessageFormat.format(
						QuPathResources.getString("Commands.MemoryMonitor.tileCacheMemory"),
						String.format("%.1f", cacheSizeBytes.get() * scaleMB)
				),
				cacheSizeBytes
		));
		seriesUsed.nameProperty().bind(Bindings.createStringBinding(
				() -> MessageFormat.format(
						QuPathResources.getString("Commands.MemoryMonitor.usedMemory"),
						String.format("%.1f", usedMemory.get() * scaleMB)
				),
				usedMemory
		));
		chart.getData().add(seriesTileCache);
		chart.getData().add(seriesUsed);
		chart.getData().add(seriesTotal);
		chart.setLegendVisible(true);
		chart.setLegendSide(Side.TOP);
		chart.setAnimated(false);

		// Add it button to make it possible to clear the tile cache
		Label labelCacheFill = new Label();
		cacheFillPercent.bind(cacheSizeBytes.multiply(100.0).divide(cacheMaxSizeBytes));
		labelCacheFill.textProperty().bind(Bindings.createStringBinding(
				() -> MessageFormat.format(QuPathResources.getString("Commands.MemoryMonitor.cacheFillPercent"), cacheFillPercent.get()),
				cacheFillPercent
		));

		Label labelClearCache = new Label();
		labelClearCache.textProperty().bind(Bindings.createStringBinding(
				() -> MessageFormat.format(QuPathResources.getString("Commands.MemoryMonitor.numCachedTiles"), cachedTiles.get()),
				cachedTiles
		));
		var btnClearCache = new Button(QuPathResources.getString("Commands.MemoryMonitor.clearTileCache"));
		btnClearCache.setTooltip(new Tooltip(QuPathResources.getString("Commands.MemoryMonitor.clearTileCacheDescription")));
		btnClearCache.setOnAction(e -> {
			try {
				logger.info("Clearing cache...");
				ImageCache.getSharedInstance().clearCache();
				System.gc();
			} catch (Exception e2) {
				logger.error("Error clearing cache", e2);
			}
		});
		btnClearCache.setMaxWidth(Double.MAX_VALUE);

		// Clear Undo/Redo manager
		Label labelUndoRedo = new Label();
		labelUndoRedo.textProperty().bind(Bindings.createStringBinding(
				() -> MessageFormat.format(
						QuPathResources.getString("Commands.MemoryMonitor.undoRedoMemory"),
						String.format("%.2f", undoRedoSizeBytes.get()/(1024.0 * 1024.0 * 1024.0))
				),
				undoRedoSizeBytes
		));
		var btnClearUndoRedo = new Button(QuPathResources.getString("Commands.MemoryMonitor.resetUndoRedo"));
		btnClearUndoRedo.setTooltip(new Tooltip(QuPathResources.getString("Commands.MemoryMonitor.resetUndoRedoDescription")));
		btnClearUndoRedo.setOnAction(e -> {
			try {
				logger.info("Clearing undo/redo...");
				qupath.getUndoRedoManager().clear();
				System.gc();
			} catch (Exception e2) {
				logger.error("Error undo/redo", e2);
			}
		});
		btnClearUndoRedo.setMaxWidth(Double.MAX_VALUE);

		// Add a button to run the garbage collector
		var btnGarbageCollector = new Button(QuPathResources.getString("Commands.MemoryMonitor.reclaimMemory"));
		btnGarbageCollector.setTooltip(new Tooltip(QuPathResources.getString("Commands.MemoryMonitor.reclaimMemoryDescription")));
		btnGarbageCollector.setOnAction(e -> System.gc());
		btnGarbageCollector.setMaxWidth(Double.MAX_VALUE);
				
		var btnToggleMonitoring = new ToggleButton();
		btnToggleMonitoring.textProperty().bind(Bindings.createStringBinding(() -> {
			if (btnToggleMonitoring.isSelected())
				return QuPathResources.getString("Commands.MemoryMonitor.stopMonitoring");
			else
				return QuPathResources.getString("Commands.MemoryMonitor.startMonitoring");
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
		var btnReset = new Button(QuPathResources.getString("Commands.MemoryMonitor.resetMonitor"));
		btnReset.setOnAction(e -> {
			startTimeMillis = 0L;
			seriesTileCache.getData().clear();
			seriesUsed.getData().clear();
			seriesTotal.getData().clear();
		});
		btnReset.setMaxWidth(Double.MAX_VALUE);

		// Add a text field to adjust the number of parallel threads
		// This is handy to scale back memory use when running things like cell detection
		var labThreads = new Label(QuPathResources.getString("Commands.MemoryMonitor.parallelThreads"));
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
			} catch (Exception e) {
				logger.debug(e.getLocalizedMessage(), e);
			}
		});
		labThreads.setLabelFor(tfThreads);

		// Create a pane to show it all
		var paneRight = new GridPane();
		int col = 0;
		int row = 0;
		paneRight.add(
				new Label(MessageFormat.format(
						QuPathResources.getString("Commands.MemoryMonitor.availableProcessors"),
						Runtime.getRuntime().availableProcessors()
				)),
				col,
				row++,
				1,
				1
		);
		paneRight.add(labThreads, col, row, 1, 1);
		paneRight.add(tfThreads, col+1, row++, 1, 1);
		paneRight.add(labelCacheFill, col, row++, 2, 1);
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

		paneRight.setPadding(new Insets(10));
		paneRight.setVgap(5);
		var pane = new BorderPane(chart);
		pane.setRight(paneRight);

		// Create a timer that will snapshot the current memory usage & update the chart
		service.setPeriod(Duration.seconds(1.0));
		service.lastValueProperty().subscribe(n -> {
			if (n == null)
				return;
			if (startTimeMillis <= 0)
				startTimeMillis = n.timeMillis;
			timeMillis.set(n.timeMillis);
			maxMemory.set(n.maxMemory);
			totalMemory.set(n.totalMemory);
			usedMemory.set(n.usedMemory);
			undoRedoSizeBytes.set(n.undoRedoSize());
			cacheSizeBytes.set(n.tileCacheBytes());
			cacheMaxSizeBytes.set(n.maxTileCacheBytes());
			cachedTiles.set(n.numCachedTiles());
			
			long time = (timeMillis.get() - startTimeMillis) / 1000;
			double tileCacheValue = cacheSizeBytes.get() * scaleGB;
			double usedMemoryValue = usedMemory.get() * scaleGB;
			double totalMemoryValue = totalMemory.get() * scaleGB;
			seriesTileCache.getData().add(new XYChart.Data<>(time, tileCacheValue));
			if (chart instanceof StackedAreaChart) {
				seriesUsed.getData().add(new XYChart.Data<>(time, Math.max(0, usedMemoryValue - tileCacheValue)));
				seriesTotal.getData().add(new XYChart.Data<>(time, Math.max(0, totalMemoryValue - usedMemoryValue)));
			} else {
				seriesUsed.getData().add(new XYChart.Data<>(time, usedMemoryValue));
				seriesTotal.getData().add(new XYChart.Data<>(time, totalMemoryValue));
			}
		});

		// Show the GUI
		stage = new Stage();
		stage.initOwner(qupath.getStage());
		stage.setScene(new Scene(pane));
		stage.setTitle(QuPathResources.getString("Commands.MemoryMonitor.title"));
		FXUtils.addCloseWindowShortcuts((Window) stage);

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
		seriesUsed.getData().add(new XYChart.Data<>(time, usedMemory.get() * scaleGB));
		seriesTotal.getData().add(new XYChart.Data<>(time, totalMemory.get() * scaleGB));
	}
	
	
	private class MemoryService extends ScheduledService<MemorySnapshot> {

		@Override
		protected Task<MemorySnapshot> createTask() {
			return new MemorySnapshotTask();
		}
		
	}


	private final class MemorySnapshotTask extends Task<MemorySnapshot> {

		@Override
		protected MemorySnapshot call() {
			long totalMemory = MemoryTools.getCurrentTotalMemory();
			long maxMemory = MemoryTools.getMaxMemoryOrDefault(DEFAULT_MAX_MEMORY);
			long usedMemory = MemoryTools.getCurrentUsedMemory();
			long undoRedoSizeBytes = qupath.getUndoRedoManager().totalBytes();
			var cache = ImageCache.getSharedInstance();
			return new MemorySnapshot(
					System.currentTimeMillis(),
					totalMemory,
					maxMemory,
					usedMemory,
					undoRedoSizeBytes,
					cache.getCurrentSizeBytes(),
					cache.getMaxSizeBytes(),
					cache.getTileCount()
			);

		}

	}
	
	
	record MemorySnapshot(
			long timeMillis,
			long totalMemory,
			long maxMemory,
			long usedMemory,
			long undoRedoSize,
			long tileCacheBytes,
			long maxTileCacheBytes,
			long numCachedTiles
	) {

		double usedRatio() {
			return (double) usedMemory / (double) totalMemory;
		}

	}


}