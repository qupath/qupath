/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.tma;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.controlsfx.control.MasterDetailPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableColumn.CellDataFeatures;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.helpers.ChartToolsFX;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaintingToolsFX;
import qupath.lib.gui.models.HistogramDisplay;
import qupath.lib.gui.models.ObservableMeasurementTableData;
import qupath.lib.gui.models.PathTableData;
import qupath.lib.gui.panels.survival.KaplanMeierDisplay;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.OverlayOptions.CellDisplayMode;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.PathIO;
import qupath.lib.io.TMAScoreImporter;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;


/**
 * Standalone viewer for looking at TMA summary results.
 * 
 * @author Pete Bankhead
 *
 */
public class TMASummaryViewer {
	
	public final static Logger logger = LoggerFactory.getLogger(TMASummaryViewer.class);
	
	private Map<String, ImageServer<BufferedImage>> serverMap = new HashMap<>();
	
	private static String MISSING_COLUMN = "Missing";

	private final Stage stage;

	private List<String> metadataNames = new ArrayList<>();
	private ObservableList<String> measurementNames = FXCollections.observableArrayList();
	private ObservableList<String> filteredMeasurementNames = new FilteredList<>(measurementNames, m -> {
		return !(TMACoreObject.KEY_OS_CENSORED.equals(m) ||
				TMACoreObject.KEY_OVERALL_SURVIVAL.equals(m) ||
				TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL.equals(m) ||
				TMACoreObject.KEY_RFS_CENSORED.equals(m));
	});

	private ObservableList<String> survivalColumns = FXCollections.observableArrayList();
	private ComboBox<String> comboSurvival = new ComboBox<>(survivalColumns);

	private ObservableList<TMAEntry> entriesBase = FXCollections.observableArrayList();
//	private FilteredList<TMAEntry> entries = new FilteredList<>(entriesBase);
	
	/**
	 * Maintain a reference to columns that were previously hidden whenever loading new data.
	 * This helps maintain some continuity, so that if any columns have the same names then they 
	 * can be hidden as well.
	 */
	private Set<String> lastHiddenColumns = new HashSet<>();
	
	private String colCensored = null;
	
	private Scene scene;

	
	/**
	 * Methods that may be used to combine measurements when multiple cores are available.
	 */
	private static enum MeasurementCombinationMethod {
		MEAN, MEDIAN, MIN, MAX;
		
		public double calculate(final List<TMAEntry> entries, final String measurementName, final boolean skipMissing) {
			switch (this) {
			case MAX:
				return getMaxMeasurement(entries, measurementName, skipMissing);
			case MEAN:
				return getMeanMeasurement(entries, measurementName, skipMissing);
			case MEDIAN:
				return getMedianMeasurement(entries, measurementName, skipMissing);
			case MIN:
				return getMinMeasurement(entries, measurementName, skipMissing);
			default:
				return Double.NaN;
			}
		}
		
		@Override
		public String toString() {
			switch (this) {
			case MAX:
				return "Maximum";
			case MEAN:
				return "Mean";
			case MEDIAN:
				return "Median";
			case MIN:
				return "Minimum";
			default:
				return null;
			}
		}
		
	};
	
	
	/**
	 * A combo-box representing the main measurement.
	 * This will be used for any survival curves.
	 */
	private ComboBox<String> comboMainMeasurement = new ComboBox<>(filteredMeasurementNames);
	
	/**
	 * A combo-box representing how measurements are combined whenever multiple cores are available per patient.
	 * 
	 * Options include min, max, mean & median.
	 */
	private ComboBox<MeasurementCombinationMethod> comboMeasurementMethod = new ComboBox<>();
	private ReadOnlyObjectProperty<MeasurementCombinationMethod> selectedMeasurementCombinationProperty = comboMeasurementMethod.getSelectionModel().selectedItemProperty();
	
	
	private TreeTableView<TMAEntry> table = new TreeTableView<>();
	private TMATableModel model;

	private OverlayOptions overlayOptions = new OverlayOptions();
	private TMAEntry entrySelected = null;

	private BooleanProperty showAnalysisProperty = new SimpleBooleanProperty(true);
	private BooleanProperty useSelectedProperty = new SimpleBooleanProperty(false);
	private BooleanProperty skipMissingCoresProperty = new SimpleBooleanProperty(true);

	private HistogramDisplay histogramDisplay;
	private	KaplanMeierDisplay kmDisplay;
	private ScatterPane scatterPane = new ScatterPane();
	
	private ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	
	
	private ObjectProperty<Predicate<? super TMAEntry>> predicate = new SimpleObjectProperty<>();

	private ObservableValue<Predicate<? super TMAEntry>> combinedPredicate = Bindings.createObjectBinding(() -> {
		if (predicate.get() == null) {
			if (!skipMissingCoresProperty.get())
				return c -> true;
			else
				return c -> !c.isMissing();
		}return predicate.get().and(c -> !((TMAEntry)c).isMissing() || !skipMissingCoresProperty.get());
	}, predicate, skipMissingCoresProperty);

	

	public TMASummaryViewer(final Stage stage) {
		if (stage == null)
			this.stage = new Stage();
		else
			this.stage = stage;
		initialize();
		this.stage.setTitle("TMA Results Viewer");
		this.stage.setScene(scene);
		this.stage.setOnCloseRequest(e -> {
			for (ImageServer<?> server : serverMap.values())
				server.close();
			pool.shutdown();
		});
		new DragDropTMADataImportListener(this);
	}
	

	private void initialize() {
		
		model = new TMATableModel();
		overlayOptions.setShowObjects(true);
		overlayOptions.setFillObjects(true);
		overlayOptions.setCellDisplayMode(CellDisplayMode.NUCLEI_ONLY);

		MenuBar menuBar = new MenuBar();
		Menu menuFile = new Menu("File");
		MenuItem miOpen = new MenuItem("Open...");
		miOpen.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
		miOpen.setOnAction(e -> {
			File file = QuPathGUI.getDialogHelper(stage).promptForFile(null, null, "TMA data files", new String[]{"qptma"});
			if (file == null)
				return;
			setInputFile(file);
		});
		
		MenuItem miSave = new MenuItem("Save As...");
		miSave.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		miSave.setOnAction(e -> SummaryMeasurementTableCommand.saveTableModel(model, null, Collections.emptyList()));
		
		
		MenuItem miImportFromImage = new MenuItem("Import from current image...");
		miImportFromImage.setAccelerator(new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		miImportFromImage.setOnAction(e -> setTMAEntriesFromOpenImage());
		
		
		MenuItem miImportClipboard = new MenuItem("Import from clipboard...");
		miImportClipboard.setOnAction(e -> {
			String text = Clipboard.getSystemClipboard().getString();
			if (text == null) {
				DisplayHelpers.showErrorMessage("Import scores", "Clipboard is empty!");
				return;
			}
			int n = importScores(text);
			if (n > 0) {
				setTMAEntries(new ArrayList<>(entriesBase));
			}
			DisplayHelpers.showMessageDialog("Import scores", "Number of scores imported: " + n);
		});
		
		Menu menuEdit = new Menu("Edit");
		MenuItem miCopy = new MenuItem("Copy table to clipboard");
		miCopy.setOnAction(e -> {
			SummaryMeasurementTableCommand.copyTableContentsToClipboard(model, Collections.emptyList());
		});
		
		MenuItem miPredicate = new MenuItem("Set predicate");
		miPredicate.setOnAction(e -> {
			promptForPredicate(entriesBase);
		});
		predicate.addListener((v, o, n) -> {
			Platform.runLater(() -> {
				table.refresh();
				histogramDisplay.refreshHistogram();
				updateSurvivalCurves();
				scatterPane.updateChart();
			});
		});
		
//		miPredicate.selectedProperty().addListener((v, o, n) -> {
//			String predicateString = "\"Num Tumor\" > 500";
//			if (n) {
//				logger.warn("Testing predicates is incomplete!  Currently hard-coded to as {}", predicateString);
//				predicate.set(new TablePredicate(predicateString));
////				predicate.set(e -> {
//////					String name = e.getName().trim().toLowerCase();
//////					boolean keep = false;
//////					keep = name.startsWith("a") || name.startsWith("b") || name.startsWith("c");
//////					keep = name.startsWith("d") || name.startsWith("e") || name.startsWith("f");
//////					keep = name.startsWith("g") || name.startsWith("h") || name.startsWith("i");
//////					keep = keep && (e.getMeasurement("Num Tumor").doubleValue() + e.getMeasurement("Num Stroma").doubleValue()) > 250;
//////					System.err.println(name + ": " + keep);
//////					return keep;
////					Number value = e.getMeasurement(predicateMeasurement);
////					return value != null && value.doubleValue() >= predicateMin;
////				});
////				predicate.set(e -> !Double.isNaN(e.getMeasurement("H-score").doubleValue()));
//			} else
//				predicate.set(null);
//			refreshTableData(table, null, false, getColumnFilter());
//			refreshDetailTable();
//			table.refresh();
//			tableDetail.refresh();
//			histogramDisplay.refreshHistogram();
//			updateSurvivalCurves();
//			scatterPane.updateChart();
//		});
		menuEdit.getItems().add(miPredicate);
		
		
		// Reset the scores for missing cores - this ensures they will be NaN and not influence subsequent results
		MenuItem miResetMissingScores = new MenuItem("Reset scores for missing cores");
		miResetMissingScores.setOnAction(e -> {
			int changes = 0;
			for (TMAEntry entry : entriesBase) {
				if (!entry.isMissing())
					continue;
				boolean changed = false;
				for (String m : entry.getMeasurementNames().toArray(new String[0])) {
					if (!isSurvivalColumn(m) && !Double.isNaN(entry.getMeasurementAsDouble(m))) {
						entry.putMeasurement(m, null);
						changed = true;
					}
				}
				if (changed)
					changes++;
			}
			if (changes == 0) {
				logger.info("No changes made when resetting scores for missing cores!");
				return;
			}
			logger.info("{} change(s) made when resetting scores for missing cores!", changes);
			table.refresh();
			updateSurvivalCurves();
			if (scatterPane != null)
				scatterPane.updateChart();
			if (histogramDisplay != null)
				histogramDisplay.refreshHistogram();
		});
		menuEdit.getItems().add(miResetMissingScores);
		
		

		QuPathGUI.addMenuItems(
				menuFile,
				miOpen,
				miSave,
				null,
				miImportClipboard,
				null,
				miImportFromImage
				);
		menuBar.getMenus().add(menuFile);
		menuEdit.getItems().add(miCopy);
		menuBar.getMenus().add(menuEdit);

		
		menuFile.setOnShowing(e -> {
			boolean imageDataAvailable = QuPathGUI.getInstance() != null && QuPathGUI.getInstance().getImageData() != null && QuPathGUI.getInstance().getImageData().getHierarchy().getTMAGrid() != null;
			miImportFromImage.setDisable(!imageDataAvailable);
		});
		
		// Double-clicking previously used for comments... but conflicts with tree table expansion
//		table.setOnMouseClicked(e -> {
//			if (!e.isPopupTrigger() && e.getClickCount() > 1)
//				promptForComment();
//		});

		
//		Button btnSurvival = new Button("Kaplan Meier");
//		btnSurvival.setOnAction(e -> {
//			
//			updateSurvivalCurves();
//				
//
//		});

		

		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		
		BorderPane pane = new BorderPane();
		pane.setTop(menuBar);
		menuBar.setUseSystemMenuBar(true);

		// Create options
		ToolBar toolbar = new ToolBar();
		Label labelMeasurementMethod = new Label("Combination method");
		labelMeasurementMethod.setLabelFor(comboMeasurementMethod);
		labelMeasurementMethod.setTooltip(new Tooltip("Method whereby measurements for multiple cores with the same " + TMACoreObject.KEY_UNIQUE_ID + " will be combined"));
		
		CheckBox cbShowAnalysis = new CheckBox("Show analysis pane");
		cbShowAnalysis.setSelected(showAnalysisProperty.get());
		cbShowAnalysis.selectedProperty().bindBidirectional(showAnalysisProperty);
		
		CheckBox cbUseSelected = new CheckBox("Use selection only");
		cbUseSelected.selectedProperty().bindBidirectional(useSelectedProperty);
		
		CheckBox cbSkipMissing = new CheckBox("Skip missing cores");
		cbSkipMissing.selectedProperty().bindBidirectional(skipMissingCoresProperty);
		skipMissingCoresProperty.addListener((v, o, n) -> {
			table.refresh();
			updateSurvivalCurves();
			if (histogramDisplay != null)
				histogramDisplay.refreshHistogram();
			updateSurvivalCurves();
			if (scatterPane != null)
				scatterPane.updateChart();
		});
		
		toolbar.getItems().addAll(
				labelMeasurementMethod,
				comboMeasurementMethod,
				new Separator(Orientation.VERTICAL),
				cbShowAnalysis,
				new Separator(Orientation.VERTICAL),
				cbUseSelected,
				new Separator(Orientation.VERTICAL),
				cbSkipMissing
				);
		comboMeasurementMethod.getItems().addAll(MeasurementCombinationMethod.values());
		comboMeasurementMethod.getSelectionModel().select(MeasurementCombinationMethod.MEDIAN);
		selectedMeasurementCombinationProperty.addListener((v, o, n) -> table.refresh());

		
		ContextMenu popup = new ContextMenu();
		MenuItem miExpand = new MenuItem("Expand all");
		miExpand.setOnAction(e -> {
			if (table.getRoot() == null)
				return;
			for (TreeItem<?> item : table.getRoot().getChildren()) {
				item.setExpanded(true);
			}
		});
		MenuItem miCollapse = new MenuItem("Collapse all");
		miCollapse.setOnAction(e -> {
			if (table.getRoot() == null)
				return;
			for (TreeItem<?> item : table.getRoot().getChildren()) {
				item.setExpanded(false);
			}
		});
		popup.getItems().addAll(miExpand, miCollapse);
		table.setContextMenu(popup);
		
		table.setRowFactory(e -> {
			TreeTableRow<TMAEntry> row = new TreeTableRow<>();
			
//			// Make rows invisible if they don't pass the predicate
//			row.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
//					TMAEntry entry = row.getItem();
//					if (entry == null || (entry.isMissing() && skipMissingCoresProperty.get()))
//							return false;
//					return entries.getPredicate() == null || entries.getPredicate().test(entry);
//					},
//					skipMissingCoresProperty,
//					entries.predicateProperty()));
			
			// Style rows according to what they contain
			row.styleProperty().bind(
					Bindings.createStringBinding(
							() -> {
								if (row.isSelected())
									return "";
								TMAEntry entry = row.getItem();
								if (entry == null || entry instanceof TMASummaryEntry)
									return "";
								else if (entry.isMissing())
									return "-fx-background-color:rgb(225,225,232)";				
								else
									return "-fx-background-color:rgb(240,240,245)";	
							},
							row.itemProperty(),
							row.selectedProperty())
					);
//			row.itemProperty().addListener((v, o, n) -> {
//				if (n == null || n instanceof TMASummaryEntry || row.isSelected())
//					row.setStyle("");
//				else if (n.isMissing())
//					row.setStyle("-fx-background-color:rgb(225,225,232)");				
//				else
//					row.setStyle("-fx-background-color:rgb(240,240,245)");				
//			});
			return row;
		});
		
		
		BorderPane paneTable = new BorderPane();
		paneTable.setTop(toolbar);
		paneTable.setCenter(table);

		MasterDetailPane mdTablePane = new MasterDetailPane(Side.RIGHT, paneTable, createSidePane(), true);
		
		mdTablePane.showDetailNodeProperty().bind(showAnalysisProperty);

		pane.setCenter(mdTablePane);
		
		
		
		model.getEntries().addListener(new ListChangeListener<TMAEntry>() {
			@Override
			public void onChanged(ListChangeListener.Change<? extends TMAEntry> c) {
				if (histogramDisplay != null)
					histogramDisplay.refreshHistogram();
				updateSurvivalCurves();
				if (scatterPane != null)
					scatterPane.updateChart();
			}
		});
		
		
		Label labelPredicate = new Label();
		labelPredicate.setPadding(new Insets(5, 5, 5, 5));
		labelPredicate.setAlignment(Pos.CENTER);
//		labelPredicate.setStyle("-fx-background-color: rgba(20, 120, 20, 0.15);");
		labelPredicate.setStyle("-fx-background-color: rgba(120, 20, 20, 0.15);");
		
		labelPredicate.textProperty().addListener((v, o, n) -> {
			if (n.trim().length() > 0)
				pane.setBottom(labelPredicate);
			else
				pane.setBottom(null);
		});
		labelPredicate.setMaxWidth(Double.MAX_VALUE);
		labelPredicate.setMaxHeight(labelPredicate.getPrefHeight());
		labelPredicate.setTextAlignment(TextAlignment.CENTER);
		predicate.addListener((v, o, n) -> {
			if (n == null)
				labelPredicate.setText("");
			else if (n instanceof TablePredicate) {
				TablePredicate tp = (TablePredicate)n;
				if (tp.getOriginalCommand().trim().isEmpty())
					labelPredicate.setText("");
				else
					labelPredicate.setText("Predicate: " + tp.getOriginalCommand());
			} else
				labelPredicate.setText("Predicate: " + n.toString());
		});
//		predicate.set(new TablePredicate("\"Tumor\" > 100"));
		
		scene = new Scene(pane);
		
		scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			KeyCode code = e.getCode();
			if ((code == KeyCode.SPACE || code == KeyCode.ENTER) && entrySelected != null) {
				promptForComment();
				return;
			}
		});

	}
	
	
	
	private static Map<String, Image> imageMap = new LinkedHashMap<String, Image>(200, 1, true) {
		
		private static final long serialVersionUID = 4814360294521533841L;
		
		private static final int MAX_ENTRIES = 200;
		
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
	        return size() > MAX_ENTRIES;
	     }
		
	};
	
	
	
	public Stage getStage() {
		return stage;
	}
	
	
	private class ImageTableCell extends TreeTableCell<TMAEntry, Image> {
		
		final private Canvas canvas = new Canvas();
		
		ImageTableCell() {
			super();
			canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
			canvas.setWidth(100);
			canvas.setHeight(100);
			canvas.heightProperty().bind(canvas.widthProperty());
		}
		
		@Override
		protected void updateItem(Image item, boolean empty) {
			super.updateItem(item, empty);
			if (item == null || empty) {
				setGraphic(null);
				return;
			}
			
			double w = getTableColumn().getWidth()-10;
			canvas.setWidth(w);
			setGraphic(canvas);
			
			this.setContentDisplay(ContentDisplay.CENTER);
			this.setAlignment(Pos.CENTER);
			
			GraphicsContext gc = canvas.getGraphicsContext2D();
			gc.clearRect(0, 0, w, w);
			PaintingToolsFX.paintImage(canvas, item);
//			else if (!waitingImages.contains(item)) {
//				waitingImages.add(item);
//				pool.execute(new ImageWorker(item));
//			}
		}
		
	}
	
	
	
	/**
	 * Depending on the survival type, get the correct (unambiguous) column title for censoring.
	 * 
	 * @param survivalColumn
	 * @return
	 */
	static String getRequestedSurvivalCensoredColumn(final String survivalColumn) {
		if (TMACoreObject.KEY_OVERALL_SURVIVAL.equals(survivalColumn)) {
			return TMACoreObject.KEY_OS_CENSORED;
		} else if (TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL.equals(survivalColumn)) {
			return TMACoreObject.KEY_RFS_CENSORED;
		}
		return null;
	}
	
	
	private String getSurvivalColumn() {
		return comboSurvival.getSelectionModel().getSelectedItem();
	}
	
	
	private void updateSurvivalCurves() {
		String colID = null;
		String colScore = null;
		colCensored = null;
		for (String nameOrig : model.getAllNames()) {
			if (nameOrig.equals(TMACoreObject.KEY_UNIQUE_ID))
				colID = nameOrig;
//			else if (nameOrig.equals(TMACoreObject.KEY_CENSORED))
//				colCensored = nameOrig;
//			else if (!Number.class.isAssignableFrom())
//				continue;
			else  {
				if (nameOrig.trim().length() == 0 || !model.getMeasurementNames().contains(nameOrig))
					continue;
				String name = nameOrig.toLowerCase();
				if (name.equals("h-score"))
					colScore = nameOrig;
				else if (name.equals("positive %") && colScore == null)
					colScore = nameOrig;
			}
		}
		
		// Check for a column with the exact requested name
		String colCensoredRequested = null;
		String colSurvival = getSurvivalColumn();
		if (colSurvival != null) {
			colCensoredRequested = getRequestedSurvivalCensoredColumn(colSurvival);
			if (model.getAllNames().contains(colCensoredRequested))
				colCensored = colCensoredRequested;
			// Check for a general 'censored' column... less secure since it doesn't specify OS or RFS (but helps with backwards-compatibility)
			else if (model.getAllNames().contains("Censored")) {
				logger.warn("Correct censored column for \"{}\" unavailable - should be \"{}\", but using \"Censored\" column instead", colSurvival, colCensoredRequested);
				colCensored = "Censored";
			}
		}
		if (colCensored == null && colSurvival != null) {
			logger.warn("Unable to find censored column - survival data will be uncensored");
		}
		
		colScore = comboMainMeasurement.getSelectionModel().getSelectedItem();
		if (colID == null || colSurvival == null || colCensored == null) {// || colScore == null) {
			// Adjust priority depending on whether we have any data at all..
			if (!model.getEntries().isEmpty())
				logger.warn("No survival data found!");
			else
				logger.trace("No entries or survival data available");
			return;
		}
		
		// Generate a pseudo TMA core hierarchy
		Map<String, List<TMAEntry>> scoreMap = createScoresMap(model.getEntries(), colScore, colID);
		
//		System.err.println("Score map size: " + scoreMap.size() + "\tEntries: " + model.getEntries().size());
		
		List<TMACoreObject> cores = new ArrayList<>(scoreMap.size());
		double[] scores = new double[15];
		for (Entry<String, List<TMAEntry>> entry : scoreMap.entrySet()) {

			TMACoreObject core = new TMACoreObject();
			core.setName("ID: " + entry.getKey());
			MeasurementList ml = core.getMeasurementList();
			Arrays.fill(scores, Double.POSITIVE_INFINITY);
			List<TMAEntry> list = entry.getValue();
			// Increase array size, if needed
			if (list.size() > scores.length)
				scores = new double[list.size()];
			for (int i = 0; i < list.size(); i++) {
				scores[i] = model.getNumericValue(list.get(i), colScore);
//				scores[i] = list.get(i).getMeasurement(colScore).doubleValue();
			}
			Arrays.sort(scores);
			int n = list.size();
			double score;
			if (n % 2 == 1)
				score = scores[n / 2];
			else
				score = (scores[n/2-1] + scores[n/2]) / 2;

			core.putMetadataValue(TMACoreObject.KEY_UNIQUE_ID, entry.getKey());
//			System.err.println("Putting: " + list.get(0).getMeasurement(colSurvival).doubleValue() + " LIST: " + list.size());
			ml.putMeasurement(colSurvival, list.get(0).getMeasurementAsDouble(colSurvival));
			ml.putMeasurement(colCensoredRequested, list.get(0).getMeasurementAsDouble(colCensored));
			if (colScore != null)
				ml.putMeasurement(colScore, score);

			cores.add(core);
			
//			logger.info(entry.getKey() + "\t" + score);
		}

		TMAGrid grid = new DefaultTMAGrid(cores, 1);
		PathObjectHierarchy hierarchy = new PathObjectHierarchy();
		hierarchy.setTMAGrid(grid);
		kmDisplay.setHierarchy(hierarchy, colSurvival, colCensoredRequested);
		kmDisplay.setScoreColumn(comboMainMeasurement.getSelectionModel().getSelectedItem());
//		new KaplanMeierPlotTMA.KaplanMeierDisplay(hierarchy, colScore).show(frame, colScore);
	}
	
	
	
	
	private Pane createSidePane() {
		BorderPane pane = new BorderPane();
		
		TabPane tabPane = new TabPane();

		kmDisplay = new KaplanMeierDisplay(null, null, null, null);
		BorderPane paneKaplanMeier = new BorderPane();
		paneKaplanMeier.setCenter(kmDisplay.getView());
		paneKaplanMeier.setPadding(new Insets(10, 10, 10, 10));
//		comboMainMeasurement.prefWidthProperty().bind(paneKaplanMeier.widthProperty());
		comboMainMeasurement.setMaxWidth(Double.MAX_VALUE);
		comboMainMeasurement.setTooltip(new Tooltip("Measurement thresholded to create survival curves etc."));

		GridPane kmTop = new GridPane();
		kmTop.add(new Label("Score"), 0, 0);
		kmTop.add(comboMainMeasurement, 1, 0);
		kmTop.add(new Label("Survival type"), 0, 1);
		kmTop.add(comboSurvival, 1, 1);
		comboSurvival.setTooltip(new Tooltip("Specify overall or recurrence-free survival (if applicable)"));
		comboSurvival.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(comboMainMeasurement, Priority.ALWAYS);
		GridPane.setHgrow(comboSurvival, Priority.ALWAYS);
		kmTop.setHgap(5);
		paneKaplanMeier.setTop(kmTop);
//		kmDisplay.setOrientation(Orientation.VERTICAL);
		
		histogramDisplay = new HistogramDisplay(model, false);
		
		comboMainMeasurement.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			histogramDisplay.refreshCombo();
			histogramDisplay.showHistogram(n);
			updateSurvivalCurves();
		});
		comboMeasurementMethod.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			histogramDisplay.refreshHistogram();
			scatterPane.updateChart();
			updateSurvivalCurves();
		});
		comboSurvival.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			updateSurvivalCurves();
		});
		
		
		
		TableView<TreeTableColumn<TMAEntry, ?>> tableColumns = new TableView<>();
		tableColumns.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tableColumns.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		tableColumns.setItems(table.getColumns().filtered(p -> !p.getText().trim().isEmpty()));
		TableColumn<TreeTableColumn<TMAEntry, ?>, String> columnName = new TableColumn<>("Column");
		columnName.setCellValueFactory(v -> v.getValue().textProperty());
		TableColumn<TreeTableColumn<TMAEntry, ?>, Boolean> columnVisible = new TableColumn<>("Visible");
		columnVisible.setCellValueFactory(v -> v.getValue().visibleProperty());
//		columnVisible.setCellValueFactory(col -> {
//			SimpleBooleanProperty prop = new SimpleBooleanProperty(col.getValue().isVisible());
//			prop.addListener((v, o, n) -> col.getValue().setVisible(n));
//			return prop;
//		});
		tableColumns.setEditable(true);
		columnVisible.setCellFactory(v -> new CheckBoxTableCell<>());
		tableColumns.getColumns().add(columnName);
		tableColumns.getColumns().add(columnVisible);
		ContextMenu contextMenu = new ContextMenu();
		MenuItem miShowSelected = new MenuItem("Show selected columns");
		miShowSelected.setOnAction(e -> {
			for (TreeTableColumn<?, ?> col : tableColumns.getSelectionModel().getSelectedItems()) {
				if (col != null)
					col.setVisible(true);
				else {
					// Not sure why this happens...?
					logger.trace("Selected column is null!");
				}
			}
		});
		MenuItem miHideSelected = new MenuItem("Hide selected columns");
		miHideSelected.setOnAction(e -> {
			for (TreeTableColumn<?, ?> col : tableColumns.getSelectionModel().getSelectedItems()) {
				if (col != null)
					col.setVisible(false);
				else {
					// Not sure why this happens...?
					logger.trace("Selected column is null!");
				}
			}
		});
		contextMenu.getItems().addAll(miShowSelected, miHideSelected);
		tableColumns.setContextMenu(contextMenu);
		tableColumns.setTooltip(new Tooltip("Show or hide table columns - right-click to change multiple columns at once"));
		BorderPane paneColumns = new BorderPane(tableColumns);

		
		ScrollPane scrollPane = new ScrollPane(paneKaplanMeier);
		scrollPane.setFitToWidth(true);
		scrollPane.setFitToHeight(true);
		scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
		scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
		Tab tabSurvival = new Tab("Survival", scrollPane);
		tabPane.getTabs().addAll(
				new Tab("Table", paneColumns),
				new Tab("Histogram", histogramDisplay.getPane()),
				new Tab("Scatterplot", scatterPane.getPane()),
				tabSurvival
				);
		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		
		
//		tabSurvival.visibleProperty().bind(
//				Bindings.createBooleanBinding(() -> !survivalColumns.isEmpty(), survivalColumns)
//				);

		pane.setCenter(tabPane);

		pane.setMinWidth(350);
		
		return pane;
	}
	
	

	
	
	private Map<String, List<TMAEntry>> createScoresMap(final List<TMAEntry> entries, final String colScore, final String colID) {
		// Create a map of entries
		Map<String, List<TMAEntry>> scoreMap = new HashMap<>();
		for (TMAEntry entry : entries) {
			Number score = model.getNumericValue(entry, colScore);
			String id = entry.getMetadataValue(colID);
			if (id == null && entry.getMeasurement(colID) != null)
				id = Double.toString(entry.getMeasurement(colID).doubleValue());
			if (id != null && score != null && !Double.isNaN(score.doubleValue())) {
				List<TMAEntry> list = scoreMap.get(id);
				if (list == null) {
					list = new ArrayList<>();
					scoreMap.put(id, list);
				}
				list.add(entry);
			}
		}
		return scoreMap;
	}
	
	
	private void setTMAEntriesFromOpenImage() {
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath == null || qupath.getImageData() == null || qupath.getImageData().getHierarchy().getTMAGrid() == null) {
			DisplayHelpers.showErrorMessage("Show TMA summary", "No TMA data available!");
			return;
		}
		ImageData<?> imageData = qupath.getImageData();
		String serverPath = imageData.getServerPath();
		ObservableMeasurementTableData data = new ObservableMeasurementTableData();
		data.setImageData(imageData, imageData.getHierarchy().getTMAGrid().getTMACoreList());
		List<TMAEntry> entriesNew = new ArrayList<>();
		for (TMACoreObject core : imageData.getHierarchy().getTMAGrid().getTMACoreList()) {
			entriesNew.add(new TMAObjectEntry(data, serverPath, core));
		}
		setTMAEntries(entriesNew);
		stage.setTitle("TMA Results Viewer: Current image");
	}
	

	public void setInputFile(File file) {
		if (file == null)
			return;
		
		if (file.getName().toLowerCase().endsWith(PathPrefs.getSerializationExtension())) {
			ImageData<BufferedImage> imageData = PathIO.readImageData(file, null, null, BufferedImage.class);
			serverMap.put(imageData.getServerPath(), imageData.getServer());
			List<TMAEntry> entries = new ArrayList<>();
			ObservableMeasurementTableData data = new ObservableMeasurementTableData();
			if (imageData.getHierarchy().getTMAGrid() != null) {
				data.setImageData(imageData, imageData.getHierarchy().getTMAGrid().getTMACoreList());
				for (TMACoreObject core : imageData.getHierarchy().getTMAGrid().getTMACoreList()) {
//					data.getEntries().add(core);
					entries.add(new TMAObjectEntry(data, imageData.getServerPath(), core));
				}
			}
			setTMAEntries(entries);
			
			stage.setTitle("TMA Results View: " + file.getName());
			
			return;
		}
		

		List<TMAEntry> entriesTemp = new ArrayList<>();

		File dir = file.isDirectory() ? file : file.getParentFile();

		for (File fileInput : dir.listFiles()) {
			if (fileInput.isHidden() || fileInput.isDirectory() || !fileInput.getName().toLowerCase().endsWith(".qptma"))
				continue;
			parseInputFile(fileInput, entriesTemp);
		}

		if (entriesTemp.isEmpty()) {
			logger.error("No data found for " + file.getAbsolutePath());
			return;
		}
		
		setTMAEntries(entriesTemp);
		stage.setTitle("TMA Results View: " + dir.getName());

	}
	
	
	void setTMAEntries(final Collection<TMAEntry> newEntries) {
		// Turn off use-selected - can be crashy when replacing entries
		if (!newEntries.equals(entriesBase))
			useSelectedProperty.set(false);
		this.entriesBase.clear();
		this.entriesBase.addAll(newEntries);
		
		// Store the names of any currently hidden columns
		lastHiddenColumns = table.getColumns().stream().filter(c -> !c.isVisible()).map(c -> c.getText()).collect(Collectors.toSet());
		
		this.table.getColumns().clear();
		
//		// Useful for a paper, but not generally...
//		int count = 0;
//		int nCells = 0;
//		int nTumor = 0;
//		for (TMAEntry entry : entriesBase) {
//			if (!entry.isMissing() && (predicate.get() == null || predicate.get().test(entry))) {
//				count++;
//				nCells += (int)(entry.getMeasurement("Num Tumor").doubleValue() + entry.getMeasurement("Num Stroma").doubleValue());
//				nTumor += (int)(entry.getMeasurement("Num Tumor").doubleValue());
//			}
//		}
//		System.err.println(String.format("Num entries:\t%d\tNum tumor:\t%d\tNum cells:\t%d", count, nTumor, nCells));
		
		
		// Update measurement names
		Set<String> namesMeasurements = new LinkedHashSet<>();
		Set<String> namesMetadata = new LinkedHashSet<>();
//		boolean containsSummaries = false;
		for (TMAEntry entry : newEntries) {
			namesMeasurements.addAll(entry.getMeasurementNames());
			namesMetadata.addAll(entry.getMetadataNames());
//			containsSummaries = containsSummaries || entry instanceof TMASummaryEntry;
		}
		
		// Get the available survival columns
		String currentSurvival = getSurvivalColumn();
		survivalColumns.clear();
		if (namesMeasurements.contains(TMACoreObject.KEY_OVERALL_SURVIVAL))
			survivalColumns.add(TMACoreObject.KEY_OVERALL_SURVIVAL);
		if (namesMeasurements.contains(TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL))
			survivalColumns.add(TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL);
		if (currentSurvival != null && survivalColumns.contains(currentSurvival))
			comboSurvival.getSelectionModel().select(currentSurvival);
		else if (!survivalColumns.isEmpty())
			comboSurvival.getSelectionModel().select(survivalColumns.get(0));
		
		// Add the count of non-missing cores if we are working with summaries
//		if (containsSummaries)
		namesMeasurements.add("Available cores");
		
		// Make sure there are no nulls or other unusable values
		namesMeasurements.remove(null);
		namesMeasurements.remove("");
//		measurementNames.clear();
		String selectedMainMeasurement = comboMainMeasurement.getSelectionModel().getSelectedItem();
		measurementNames.setAll(namesMeasurements);
		if (namesMeasurements.contains(selectedMainMeasurement))
			comboMainMeasurement.getSelectionModel().select(selectedMainMeasurement);
		else {
			namesMeasurements.remove(TMACoreObject.KEY_UNIQUE_ID);
			namesMeasurements.remove(TMACoreObject.KEY_OVERALL_SURVIVAL);
			namesMeasurements.remove(TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL);
			namesMeasurements.remove(TMACoreObject.KEY_OS_CENSORED);
			namesMeasurements.remove(TMACoreObject.KEY_RFS_CENSORED);
			namesMeasurements.remove("Censored"); // For historical reasons when there was only one censored column supported...
			if (!namesMeasurements.isEmpty())
				comboMainMeasurement.getSelectionModel().select(0);
		}
		metadataNames.clear();
		metadataNames.addAll(namesMetadata);
		
		refreshTableData(table, createSummaryEntries(entriesBase));
		
		model.refreshList();
	}

	
	
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void refreshTableData(final TreeTableView<TMAEntry> table, final Collection<? extends TMAEntry> entries) {

		// Ensure that we don't try to modify a filtered list
		List<TreeTableColumn<TMAEntry, ?>> columns = new ArrayList<>();

		if (table.getColumns().isEmpty()) {
			
			// Add an empty column.
			// Its purpose is to provide the space needed for the little expansion arrows, to avoid 
			// these stealing space from the first interesting column.
			// Note: there's nothing to prevent the user reordering it along with other columns... 
			// but hopefully it looks 'right' enough where it is that few would try to do that
			TreeTableColumn<TMAEntry, String> columnEmpty = new TreeTableColumn<>("  ");
			columnEmpty.setCellValueFactory(new Callback<CellDataFeatures<TMAEntry, String>, ObservableValue<String>>() {
				@Override
				public ObservableValue<String> call(CellDataFeatures<TMAEntry, String> p) {
					return Bindings.createStringBinding(() -> "");
				}
			});
			columnEmpty.setSortable(false);
			columnEmpty.setResizable(false);
			columns.add(columnEmpty);
			
			// Check if we have any images or overlays
			boolean hasImages = entries.stream().anyMatch(e -> e.hasImage());
			boolean hasOverlay = entries.stream().anyMatch(e -> e.hasOverlay());
			
			// Add columns to show images, if we have them
			if (hasImages || hasOverlay) {
				TreeTableColumn<TMAEntry, Image> columnImage = hasImages ? new TreeTableColumn<>("Thumbnail") : null;
				TreeTableColumn<TMAEntry, Image> columnOverlay = hasOverlay ? new TreeTableColumn<>("Overlay") : null;
	
				if (hasImages) {
					columnImage.setCellValueFactory(new Callback<CellDataFeatures<TMAEntry, Image>, ObservableValue<Image>>() {
						@Override
						public ObservableValue<Image> call(CellDataFeatures<TMAEntry, Image> p) {
							return new SimpleObjectProperty<>(p.getValue().getValue().getImage());
						}
					});
					columnImage.setCellFactory(c -> new ImageTableCell());
					if (hasOverlay) {
						columnImage.widthProperty().addListener((v, o, n) -> {
							if (n.doubleValue() == columnImage.getPrefWidth())
								return;
							columnOverlay.setPrefWidth(n.doubleValue());
							table.refresh();
						});
					}
					columns.add(columnImage);
				}
	
				if (hasOverlay) {
					columnOverlay.setCellValueFactory(new Callback<CellDataFeatures<TMAEntry, Image>, ObservableValue<Image>>() {
						@Override
						public ObservableValue<Image> call(CellDataFeatures<TMAEntry, Image> p) {
							return new SimpleObjectProperty<>(p.getValue().getValue().getOverlay());
						}
					});
					columnOverlay.setCellFactory(c -> new ImageTableCell());
					if (hasImages) {
						columnOverlay.widthProperty().addListener((v, o, n) -> {
							if (n.doubleValue() == columnOverlay.getPrefWidth())
								return;
							columnImage.setPrefWidth(n.doubleValue());
							table.refresh();
						});
					}
					columns.add(columnOverlay);
				}
			}

		}
//		else
//			columns.remove(1, table.getColumns().size());
//		//				table.getColumns().remove(2, table.getColumns().size());


		for (String name : model.getAllNames()) {
			if (model.getMeasurementNames().contains(name)) {
				TreeTableColumn<TMAEntry, Number> column = new TreeTableColumn<>(name);
				column.setCellValueFactory(new Callback<CellDataFeatures<TMAEntry, Number>, ObservableValue<Number>>() {
					@Override
					public ObservableValue<Number> call(CellDataFeatures<TMAEntry, Number> p) {
						double value = p.getValue() == null ? Double.NaN : model.getNumericValue(p.getValue().getValue(), name);
						return new SimpleDoubleProperty(value);
					}
				});
				column.setCellFactory(c -> new NumericTableCell<>());
				columns.add(column);
			} else {
				TreeTableColumn<TMAEntry, Object> column = new TreeTableColumn<>(name);
				column.setCellValueFactory(new Callback<CellDataFeatures<TMAEntry, Object>, ObservableValue<Object>>() {
					@Override
					public ObservableValue<Object> call(CellDataFeatures<TMAEntry, Object> p) {
						return new SimpleObjectProperty<>(p.getValue() == null ? null : model.getStringValue(p.getValue().getValue(), name));
					}
				});
				column.setCellFactory(c -> new BasicTableCell<>());
				columns.add(column);
			}
		}
		
		// Set the column visibility depending upon whether they were hidden previously
		columns.stream().forEach(c -> c.setVisible(!lastHiddenColumns.contains(c.getText())));
		
		// Set columns for table
		table.getColumns().setAll(columns);
		
//		// Create TreeItems and add to table
//		List<TreeItem<TMAEntry>> items = new ArrayList<>();
//		for (TMAEntry entry : entries) {
//			if (entry instanceof TMASummaryEntry) {
//				items.add(new SummaryTreeItem((TMASummaryEntry)entry));
//			} else {
//				items.add(new TreeItem<>(entry));
//			}
//		}
		TreeItem<TMAEntry> root = new RootTreeItem(entries);
		table.setShowRoot(false);
		table.setRoot(root);
	}
	
	
	
	class RootTreeItem extends TreeItem<TMAEntry> implements ChangeListener<Predicate<? super TMAEntry>> {
		
		private List<TreeItem<TMAEntry>> entries = new ArrayList<>();
		
		RootTreeItem(final Collection<? extends TMAEntry> entries) {
			super(null);
			for (TMAEntry entry : entries) {
				if (entry instanceof TMASummaryEntry)
					this.entries.add(new SummaryTreeItem((TMASummaryEntry)entry));
				else
					this.entries.add(new TreeItem<>(entry));					
			}
			combinedPredicate.addListener(new WeakChangeListener<Predicate<? super TMAEntry>>(this));
			updateChildren();
		}
		
		private void updateChildren() {
			ArrayList<TreeItem<TMAEntry>> children = new ArrayList<>();
			for (TreeItem<TMAEntry> entry : entries) {
				if (entry instanceof SummaryTreeItem) {
					SummaryTreeItem summaryItem = (SummaryTreeItem)entry;
					summaryItem.updateChildren();
					if (!summaryItem.getChildren().isEmpty())
						children.add(summaryItem);
				} else if (combinedPredicate.getValue().test(entry.getValue()))
					children.add(entry);
			}
			super.getChildren().setAll(children);
		}

		@Override
		public void changed(ObservableValue<? extends Predicate<? super TMAEntry>> observable,
				Predicate<? super TMAEntry> oldValue, Predicate<? super TMAEntry> newValue) {
			updateChildren();
		}
		
	}
	
	class SummaryTreeItem extends TreeItem<TMAEntry> {
		
		private TMASummaryEntry entry;
		
		SummaryTreeItem(final TMASummaryEntry entry) {
			super(entry);
			this.entry = entry;
			updateChildren();
		}
		
		private void updateChildren() {
			ArrayList<TreeItem<TMAEntry>> children = new ArrayList<>();
			for (TMAEntry subEntry : entry.getEntries())
				children.add(new TreeItem<>(subEntry));
			super.getChildren().setAll(children);
		}
		
	}
	
//	class SummaryTreeItem extends TreeItem<TMAEntry> implements ChangeListener<Predicate<? super TMAEntry>> {
//		
//		private TMASummaryEntry entry;
//		
//		SummaryTreeItem(final TMASummaryEntry entry) {
//			super(entry);
//			this.entry = entry;
//			combinedPredicate.addListener(new WeakChangeListener<Predicate<? super TMAEntry>>(this));
//			updateChildren();
//		}
//		
//		private void updateChildren() {
//			ArrayList<TreeItem<TMAEntry>> children = new ArrayList<>();
//			for (TMAEntry subEntry : entry.getEntries())
//				children.add(new TreeItem<>(subEntry));
//			super.getChildren().setAll(children);
//		}
//
//		@Override
//		public void changed(ObservableValue<? extends Predicate<? super TMAEntry>> observable,
//				Predicate<? super TMAEntry> oldValue, Predicate<? super TMAEntry> newValue) {
//			updateChildren();
//		}
//		
//	}
	
	
	
	/**
	 * Create summaries entries by grouping according to Unique ID.
	 * 
	 * @param entries
	 * @return
	 */
	private Collection<? extends TMAEntry> createSummaryEntries(final List<? extends TMAEntry> entries) {
		Map<String, TMASummaryEntry> summaryEntryMap = new TreeMap<>();
		int maxSummaryLength = 0;
		for (TMAEntry entry : entries) {
			String id = entry.getMetadataValue(TMACoreObject.KEY_UNIQUE_ID);
			if (id == null && entry.getMeasurement(TMACoreObject.KEY_UNIQUE_ID) != null)
				id = entry.getMeasurement(TMACoreObject.KEY_UNIQUE_ID).toString();
			if (id == null || id.trim().length() == 0) {
				if (!"True".equals(entry.getMetadataValue(MISSING_COLUMN)))
					logger.trace("No ID found for {}", entry);
				continue;
			}
			TMASummaryEntry summary = summaryEntryMap.get(id);
			if (summary == null) {
				summary = new TMASummaryEntry(selectedMeasurementCombinationProperty, skipMissingCoresProperty, combinedPredicate);
				summaryEntryMap.put(id, summary);
			}
			summary.addEntry(entry);
			maxSummaryLength = Math.max(maxSummaryLength, summary.getEntries().size());
		}
		
		// If we don't have any summaries, just return the original entries
		if (summaryEntryMap.isEmpty() || maxSummaryLength <= 1)
			return entries;
		return summaryEntryMap.values();
	}
	
	
	
	private void parseInputFile(File file, List<TMAEntry> entries) {
		
		int nEntries = entries.size();

		String serverPath = null;
		try {
			Scanner scanner = new Scanner(file);
			serverPath = scanner.nextLine().trim();
			scanner.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (serverPath == null) { // || !(new File(serverPath).exists())) {
			logger.error("Unable to find a server with path " + serverPath + " - cannot parse " + file.getAbsolutePath());
			return;
		}		

		File dirData = new File(file.getAbsolutePath() + ".data");
		
		try {
			Map<String, List<String>> csvData = TMAScoreImporter.readCSV(getTMAResultsFile(dirData));
			if (csvData.isEmpty())
				return;
			
			// Identify metadata and numeric columns
			Map<String, List<String>> metadataColumns = new LinkedHashMap<>();
			Map<String, double[]> measurementColumns = new LinkedHashMap<>();
			List<String> idColumn = csvData.remove(TMACoreObject.KEY_UNIQUE_ID);
			if (idColumn != null)
				metadataColumns.put(TMACoreObject.KEY_UNIQUE_ID, idColumn);
			List<String> nameColumn = csvData.remove("Name");
			if (nameColumn == null)
				nameColumn = csvData.remove("Object");
			// Handle 'missing-ness' separately from general metadata
			List<String> missingColumn = csvData.remove(MISSING_COLUMN);
			int n = csvData.values().iterator().next().size();
			for (Entry<String, List<String>> entry : csvData.entrySet()) {
				List<String> list = entry.getValue();
				double[] values = TMAScoreImporter.parseNumeric(list, true);
				if (values == null || TMAScoreImporter.numNaNs(values) == list.size())
					metadataColumns.put(entry.getKey(), list);
				else
					measurementColumns.put(entry.getKey(), values);
			}
			
			for (int i = 0; i < n; i++) {
				// Don't permit 'NaN' as an ID
				if (idColumn != null && "NaN".equals(idColumn.get(i)))
					continue;
				String name = nameColumn == null ? idColumn.get(i) : nameColumn.get(i);
				boolean missing = missingColumn != null && "True".equals(missingColumn.get(i));
				File fileImage = new File(dirData, name + ".jpg");
				File fileOverlayImage = new File(dirData, name + "-overlay.jpg");
				if (!fileOverlayImage.exists())
					fileOverlayImage = new File(dirData, name + "-overlay.png");
				TMAEntry entry = new TMAEntry(serverPath, fileImage.getAbsolutePath(), fileOverlayImage.getAbsolutePath(), name, missing);
				for (Entry<String, List<String>> temp : metadataColumns.entrySet()) {
					entry.putMetadata(temp.getKey(), temp.getValue().get(i));
				}
				for (Entry<String, double[]> temp : measurementColumns.entrySet()) {
					entry.putMeasurement(temp.getKey(), temp.getValue()[i]);
				}
				entries.add(entry);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		logger.info("Parsed " + (entries.size() - nEntries) + " from " + file.getName() + " (" + entries.size() + " total)");
	}


	private File getTMAResultsFile(File dir) {
		for (File file : dir.listFiles())
			if ((file.getName().startsWith("TMA results") || file.getName().startsWith("TMA_results")) && file.getName().endsWith(".txt"))
				return file;
		return null;
	}



	private class TMATableModel implements PathTableData<TMAEntry> {
		
		private ObservableList<TMAEntry> list = FXCollections.observableArrayList();
		
		TMATableModel() {
			useSelectedProperty.addListener((v, o, n) -> refreshList());
			table.getSelectionModel().getSelectedItems().addListener(new ListChangeListener<TreeItem<TMAEntry>>() {
				@Override
				public void onChanged(ListChangeListener.Change<? extends TreeItem<TMAEntry>> c) {
					if (useSelectedProperty.get())
						refreshList();
				}
			});
			refreshList();
		}
		
		private void refreshList() {
			if (table.getRoot() == null)
				list.clear();
			else if (useSelectedProperty.get()) {
				List<TMAEntry> selectedList = table.getSelectionModel().getSelectedItems().stream().map(i -> i.getValue()).collect(Collectors.toList());
				// If we have *any* summary entries, then make sure we have *all* summary entries
				if (selectedList.stream().anyMatch(e -> e instanceof TMASummaryEntry))
					selectedList = selectedList.stream().filter(e -> e instanceof TMASummaryEntry).collect(Collectors.toList());
				list.setAll(selectedList);
			} else
				list.setAll(table.getRoot().getChildren().stream().map(i -> i.getValue()).collect(Collectors.toList()));
		}
		
		
		@Override
		public List<String> getAllNames() {
			List<String> namesList = new ArrayList<>();
			namesList.add("Image");
			namesList.add("Core");
			namesList.addAll(metadataNames);
			namesList.addAll(measurementNames);
			namesList.add("Comment");
			return namesList;
		}

		@Override
		public String getStringValue(TMAEntry entry, String column) {
			return getStringValue(entry, column, -1);
		}

		@Override
		public String getStringValue(TMAEntry entry, String column, int decimalPlaces) {
			if ("Image".equals(column))
				return entry.getShortServerName();
			if ("Core".equals(column))
				return entry.getName();
			if ("Comment".equals(column))
				return entry.getComment();
//			if ("Non missing".equals(column))
//				return entry instanceof TMASummaryEntry ? Integer.toString(((TMASummaryEntry)entry).nNonMissingEntries()) : "";
			if (metadataNames.contains(column))
				return entry.getMetadataValue(column);
			double val = getNumericValue(entry, column);
			if (Double.isNaN(val))
				return "NaN";
			return GeneralTools.getFormatter(4).format(getNumericValue(entry, column));
		}

		@Override
		public List<String> getMeasurementNames() {
			return measurementNames;
		}

		@Override
		public double getNumericValue(TMAEntry entry, String column) {
			if (entry == null)
				return Double.NaN;
			if ("Available cores".equals(column))
				return entry instanceof TMASummaryEntry ? ((TMASummaryEntry)entry).nNonMissingEntries() : Double.NaN;
			Number value = entry.getMeasurement(column);
			return value == null ? Double.NaN : value.doubleValue();
		}

		@Override
		public double[] getDoubleValues(String column) {
			List<TMAEntry> entries = getEntries();
			double[] values = new double[entries.size()];
			for (int i = 0; i < entries.size(); i++)
				values[i] = getNumericValue(entries.get(i), column);
			return values;
		}

		@Override
		public ObservableList<TMAEntry> getEntries() {
			return list;
//			if (useSelectedProperty.get())
//				return Collections.unmodifiableList(table.getSelectionModel().getSelectedItems());
//			return Collections.unmodifiableList(table.getItems());
		}


	}


	private void promptForComment() {
		String input = DisplayHelpers.showInputDialog( 
				"Add comment",
				"Type comment for " + entrySelected.getName() + "(" + entrySelected.getShortServerName() + ")", entrySelected.getComment());
		if (input == null)
			return;
		entrySelected.setComment(input);
		table.refresh();
	}



	private class TMAObjectEntry extends TMAEntry {
		
		private ObservableMeasurementTableData data;
		private TMACoreObject core;

		TMAObjectEntry(ObservableMeasurementTableData data, String serverPath, TMACoreObject core) {
			super(serverPath, null, null, null, false);
			this.core = core;
			this.data = data;			
		}
		
		
		@Override
		public Number getMeasurement(String name) {
			return data.getNumericValue(core, name);
		}
		
		@Override
		public Collection<String> getMetadataNames() {
			return data.getMetadataNames();
		}
		
		@Override
		public String getMetadataValue(final String name) {
			return data.getStringValue(core, name);
		}
		
		@Override
		public void putMetadata(String name, String value) {
			core.putMetadataValue(name, value);
		}
		
		@Override
		public boolean isMissing() {
			return core.isMissing();
		}
		
		@Override
		public Collection<String> getMeasurementNames() {
			return data.getMeasurementNames();
		}

		@Override
		public void putMeasurement(String name, Number number) {
			core.getMeasurementList().putMeasurement(name, number == null ? Double.NaN : number.doubleValue());
		}

		@Override
		public String getName() {
			return core.getName();
		}

		@Override
		public Image getImage() {
//			if (imagePath != null) {
//				Image img = imageMap.get(imagePath);
//				if (img != null)
//					return img;
//				try {
//					img = new Image(new File(imagePath).toURI().toURL().toString(), false);
//					imageMap.put(imagePath, img);
//					return img;
//				} catch (MalformedURLException e) {
//					logger.error("Cannot show image: {}", e);
//				}
//			}
			return null;
		}

		@Override
		public Image getOverlay() {
//			if (overlayPath != null) {
//				Image img = imageMap.get(overlayPath);
//				if (img != null)
//					return img;
//				try {
//					img = new Image(new File(overlayPath).toURI().toURL().toString(), false);
//					imageMap.put(overlayPath, img);
//					return img;
//				} catch (MalformedURLException e) {
//					logger.error("Cannot show image: {}", e);
//				}
//			}
			return null;
//			if (overlayPath != null)
//				return new Image(overlayPath, false);
//			return null;
		}
		
		
	}
	

	public static class TMAEntry {
		
		private String serverPath;
		private String shortServerName;
		private String name;
		private String imagePath;
		private String overlayPath;
		private String comment;
		private boolean isMissing;
		private Map<String, String> metadata = new LinkedHashMap<>();
		private Map<String, Number> measurements = new LinkedHashMap<>();

		TMAEntry(String serverPath, String imagePath, String overlayPath, String coreName, boolean isMissing) {
			this.serverPath = serverPath;
			this.shortServerName = serverPath == null ? null : ServerTools.getDefaultShortServerName(serverPath).replace("%20", " ");
			this.name = coreName;
			this.isMissing = isMissing;
			// Only store paths if they actually work...
			this.imagePath = imagePath != null && new File(imagePath).isFile() ? imagePath : null;
			this.overlayPath = overlayPath != null && new File(overlayPath).isFile() ? overlayPath : null;
		}
		
		/**
		 * Get a measurement value.
		 * 
		 * If isMissing() returns true, this always returns NaN.
		 * 
		 * Otherwise it returns whichever value is stored (which may or may not be NaN).
		 * 
		 * @param name
		 * @return
		 */
		public Number getMeasurement(String name) {
			// There's an argument for not returning any measurement for a missing core...
			// but this can be problematic if 'valid' measurements are later imported
//			if (isMissing())
//				return Double.NaN;
			return measurements.get(name);
		}
		
		/**
		 * Get a measurement as a double value.
		 * 
		 * If getMeasurement returns null, this will give NaN.
		 * Otherwise, it will return getMeasurement(name).doubleValue();
		 */
		public double getMeasurementAsDouble(String name) {
			Number measurement = getMeasurement(name);
			if (measurement == null)
				return Double.NaN;
			return measurement.doubleValue();
		}
		
		public Collection<String> getMetadataNames() {
			return metadata.keySet();
		}
		
		public String getMetadataValue(final String name) {
			return metadata.get(name);
		}
		
		public void putMetadata(String name, String value) {
			metadata.put(name, value);
		}
		
		public boolean isMissing() {
			return isMissing;
		}
		
		public Collection<String> getMeasurementNames() {
			return measurements.keySet();
		}

		public void putMeasurement(String name, Number number) {
			if (number == null)
				measurements.remove(name);
			else
				measurements.put(name, number);
		}

		public String getComment() {
			return comment;
		}

		public void setComment(String comment) {
			this.comment = comment.replace("\t", "  ").replace("\n", "  ");
		}

		public String getName() {
			return name;
		}

		public String getShortServerName() {
			if (shortServerName == null)
				this.shortServerName = serverPath == null ? null : ServerTools.getDefaultShortServerName(serverPath).replace("%20", " ");
			return shortServerName;
		}

		/**
		 * Returns true if this entry has (or thinks it has) an image.
		 * It doesn't actually try to load the image, which may be expensive - 
		 * and therefore there can be no guarantee the loading will succeed when getImage() is called.
		 * @return
		 */
		public boolean hasImage() {
			return imagePath != null;
		}
		
		/**
		 * Returns true if this entry has (or thinks it has) an overlay image.
		 * It doesn't actually try to load the image, which may be expensive - 
		 * and therefore there can be no guarantee the loading will succeed when getOverlay() is called.
		 * @return
		 */
		public boolean hasOverlay() {
			return overlayPath != null;
		}
		
		public Image getImage() {
			if (imagePath != null) {
				Image img = imageMap.get(imagePath);
				if (img != null)
					return img;
				try (InputStream stream = new BufferedInputStream(new FileInputStream(new File(imagePath)))) {
					img = new Image(stream);
					imageMap.put(imagePath, img);
					return img;
				} catch (IOException e) {
					logger.error("Cannot show image: {}", e);
				}
//				try {
//					img = new Image(new File(imagePath).toURI().toURL().toExternalForm(), false);
//					imageMap.put(imagePath, img);
//					return img;
//				} catch (MalformedURLException e) {
//					logger.error("Cannot show image: {}", e);
//				}
			}
			return null;
		}

		public Image getOverlay() {
			if (overlayPath != null) {
				Image img = imageMap.get(overlayPath);
				if (img != null)
					return img;
				try {
					img = new Image(new File(overlayPath).toURI().toURL().toString(), false);
					imageMap.put(overlayPath, img);
					return img;
				} catch (MalformedURLException e) {
					logger.error("Cannot show image: {}", e);
				}
			}
			return null;
//			if (overlayPath != null)
//				return new Image(overlayPath, false);
//			return null;
		}
		
		@Override
		public String toString() {
			return "TMA Entry: " + getName();
		}

	}
	
	
	public static double getMeanMeasurement(final List<TMAEntry> entries, final String measurement, final boolean skipMissing) {
		double sum = 0;
		int n = 0;
		for (TMAEntry entry : entries) {
			if (skipMissing && entry.isMissing())
				continue;
			Number val = entry.getMeasurement(measurement);
			if (val == null || Double.isNaN(val.doubleValue()))
				continue;
			sum += val.doubleValue();
			n++;
		}
		return n == 0 ? Double.NaN : sum / n;
	}
	
	
	public static double getMaxMeasurement(final List<TMAEntry> entries, final String measurement, final boolean skipMissing) {
		double max = Double.NEGATIVE_INFINITY;
		int n = 0;
		for (TMAEntry entry : entries) {
			if (skipMissing && entry.isMissing())
				continue;
			Number val = entry.getMeasurement(measurement);
			if (val == null || Double.isNaN(val.doubleValue()))
				continue;
			n++;
			if (val.doubleValue() > max)
				max = val.doubleValue();
		}
		return n == 0 ? Double.NaN : max;
		
		// Test code when checking what happens if taking the most (tumor) cells
//		double max = Double.NEGATIVE_INFINITY;
//		int maxInd = -1;
//		String indMeasurement = "Num Tumor";
//		for (int i = 0; i < entries.size(); i++) {
//			TMAEntry entry = entries.get(i);
//			Number val = entry.getMeasurement(indMeasurement);
//			if (val == null || Double.isNaN(val.doubleValue()))
//				continue;
//			if (val.doubleValue() > max) {
//				max = val.doubleValue();
//				maxInd = i;
//			}
//		}
//		return maxInd < 0 ? Double.NaN : entries.get(maxInd).getMeasurementAsDouble(measurement);
	}
	
	public static double getMinMeasurement(final List<TMAEntry> entries, final String measurement, final boolean skipMissing) {
		double min = Double.POSITIVE_INFINITY;
		int n = 0;
		for (TMAEntry entry : entries) {
			if (skipMissing && entry.isMissing())
				continue;
			Number val = entry.getMeasurement(measurement);
			if (val == null || Double.isNaN(val.doubleValue()))
				continue;
			n++;
			if (val.doubleValue() < min)
				min = val.doubleValue();
		}
		return n == 0 ? Double.NaN : min;
	}
	
	static Set<String> survivalSet = new HashSet<>(
			Arrays.asList(
				TMACoreObject.KEY_OVERALL_SURVIVAL,
				TMACoreObject.KEY_OS_CENSORED,
				TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL,
				TMACoreObject.KEY_RFS_CENSORED,
				"Censored"
				)
			);
	
	/**
	 * Due to the awkward way that survival data is thrown in with all measurements,
	 * sometimes need to check if a column is survival-related or not
	 * 
	 * @param name
	 * @return
	 */
	static boolean isSurvivalColumn(final String name) {
		return survivalSet.contains(name);
	}
	
	/**
	 * Calculate median measurement from a list of entries.
	 * 
	 * Entries are optionally always skipped if the entry has been marked as missing, otherwise
	 * its value will be used if not NaN.
	 * 
	 * @param entries
	 * @param measurement
	 * @return
	 */
	public static double getMedianMeasurement(final List<TMAEntry> entries, final String measurement, final boolean skipMissing) {
		double[] values = new double[entries.size()];
		int n = 0;
		for (TMAEntry entry : entries) {
			if (skipMissing && entry.isMissing())
				continue;
			Number val = entry.getMeasurement(measurement);
			if (val == null || Double.isNaN(val.doubleValue()))
				continue;
			values[n] = val.doubleValue();
			n++;
		}
		if (n == 0)
			return Double.NaN;
		if (n < values.length)
			values = Arrays.copyOf(values, n);
		Arrays.sort(values);
		if (n % 2 == 0)
			return values[n/2-1]/2 + values[n/2]/2;
		return values[n/2];
	}
	
	
	
	
	private static class TMASummaryEntry extends TMAEntry {
		
		private ReadOnlyObjectProperty<MeasurementCombinationMethod> method;
		private ObservableBooleanValue skipMissing;
		private ObservableList<TMAEntry> entriesBase = FXCollections.observableArrayList();
		private FilteredList<TMAEntry> entries = new FilteredList<>(entriesBase);

		TMASummaryEntry(final ReadOnlyObjectProperty<MeasurementCombinationMethod> method, final ObservableBooleanValue skipMissing, final ObservableValue<Predicate<? super TMAEntry>> predicate) {
			super(null, null, null, null, false);
			// Use the same predicate as elsewhere
			this.method = method;
			this.skipMissing = skipMissing;
			entries.predicateProperty().bind(predicate);
		}
		
		public void addEntry(final TMAEntry entry) {
			this.entriesBase.add(entry);
		}
		
		public List<TMAEntry> getEntries() {
			return Collections.unmodifiableList(entries);
		}
		
		@Override
		public boolean isMissing() {
			return nNonMissingEntries() > 0;
		}
		
		public int nNonMissingEntries() {
			int n = 0;
			for (TMAEntry entry : entries) {
				if (!entry.isMissing())
					n++;
			}
			return n;
		}
		
		public boolean hasImage() {
			return entries.stream().anyMatch(e -> e.hasImage());
		}
		
		public boolean hasOverlay() {
			return entries.stream().anyMatch(e -> e.hasOverlay());
		}
		
		@Override
		public String getName() {
			if (entries.isEmpty())
				return null;
			String coreNameCached;
			Set<String> names = entries.stream().filter(e -> e.getName() != null).map(e -> e.getName()).collect(Collectors.toSet());
			if (names.size() == 1)
				coreNameCached = names.iterator().next();
			else if (!names.isEmpty()) {
				coreNameCached = "[" + String.join(", ", names) + "]";
			} else
				coreNameCached = "";
			return coreNameCached;
		}
		
		@Override
		public Number getMeasurement(String name) {
			return method.get().calculate(entries, name, skipMissing.get());
		}
		
		@Override
		public Collection<String> getMetadataNames() {
			Set<String> names = new LinkedHashSet<>();
			for (TMAEntry entry : entries)
				names.addAll(entry.getMetadataNames());
			return names;
		}
		
		@Override
		public String getMetadataValue(final String name) {
			// If we need the ID, try to get everything
			List<TMAEntry> entriesToCheck = entries;
			if (entriesToCheck.isEmpty()) {
				if (TMACoreObject.KEY_UNIQUE_ID.equals(name)) {
					entriesToCheck = entriesBase;
				}
				if (entriesToCheck.isEmpty())	
					return null;
			}
			String value = entriesToCheck.get(0).getMetadataValue(name);
			for (TMAEntry entry : entriesToCheck) {
				String temp = entry.getMetadataValue(name);
				if (value == temp)
					continue;
				if (value != null && !value.equals(temp))
					return "(Mixed)";
				else
					value = temp;
			}
			// TODO: HANDLE METADATA VALUES!!!
			return value;
//			throw new IllegalArgumentException("Cannot get metadata value from " + getClass().getSimpleName());
		}
		
		@Override
		public void putMetadata(String name, String value) {
			throw new IllegalArgumentException("Cannot add metadata value to " + getClass().getSimpleName());
		}
		
		@Override
		public Collection<String> getMeasurementNames() {
			Set<String> names = new LinkedHashSet<>();
			for (TMAEntry entry : entries)
				names.addAll(entry.getMeasurementNames());
			return names;
		}

		@Override
		public void putMeasurement(String name, Number number) {
			throw new IllegalArgumentException("Cannot add measurement to " + getClass().getSimpleName());
		}
		
		
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("TMA Entry: [");
			int count = 0;
			for (TMAEntry entry : entries) {
				sb.append(entry.toString());
				count++;
				if (count < entries.size())
					sb.append(", ");
			}
			sb.append("]");
			return sb.toString();
		}
		
	}
	
	
	class ScatterPane {
		
		private BorderPane pane = new BorderPane();
		private ComboBox<String> comboScatterMainMeasurement = new ComboBox<>();
		private ComboBox<String> comboScatterSecondaryMeasurement = new ComboBox<>();
		
		private NumberAxis xAxis = new NumberAxis();
		private NumberAxis yAxis = new NumberAxis();
		private ScatterChart<Number, Number> chart = new ScatterChart<>(xAxis, yAxis);
		
		private TableView<DoubleProperty> tableScatter = new TableView<>();
		
		ScatterPane() {
			comboScatterMainMeasurement.setItems(measurementNames);
			comboScatterSecondaryMeasurement.setItems(measurementNames);
			
			comboMainMeasurement.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> comboScatterMainMeasurement.getSelectionModel().select(n));
			comboScatterMainMeasurement.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> comboMainMeasurement.getSelectionModel().select(n));
			
			comboScatterMainMeasurement.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateChart());
			comboScatterSecondaryMeasurement.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateChart());
			
			GridPane topGrid = new GridPane();
			Label label = new Label("Main measurement");
			label.minWidthProperty().bind(label.prefWidthProperty());
			topGrid.add(label, 0, 0);
			topGrid.add(comboScatterMainMeasurement, 1, 0);
			label = new Label("Secondary measurement");
			label.minWidthProperty().bind(label.prefWidthProperty());
			topGrid.add(label, 0, 1);
			topGrid.add(comboScatterSecondaryMeasurement, 1, 1);
			topGrid.setHgap(5);
			comboScatterMainMeasurement.setMaxWidth(Double.MAX_VALUE);
			comboScatterSecondaryMeasurement.setMaxWidth(Double.MAX_VALUE);
			GridPane.setHgrow(comboScatterMainMeasurement, Priority.ALWAYS);
			GridPane.setHgrow(comboScatterSecondaryMeasurement, Priority.ALWAYS);
			topGrid.setPadding(new Insets(5, 10, 5, 10));
			topGrid.prefWidthProperty().bind(pane.widthProperty());
			
			// Set up table
			TableColumn<DoubleProperty, String> colName = new TableColumn<>("Name");
			colName.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));
			TableColumn<DoubleProperty, String> colValue = new TableColumn<>("Value");
			colValue.setCellValueFactory(v -> new SimpleStringProperty(GeneralTools.getFormatter(3).format(v.getValue().getValue())));
			tableScatter.getColumns().add(colName);
			tableScatter.getColumns().add(colValue);
			tableScatter.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
			tableScatter.setPrefHeight(25*8);
			
			pane.setTop(topGrid);
			pane.setCenter(chart);
			pane.setBottom(tableScatter);
			
			
			// Make it possible to navigate around the chart
			ChartToolsFX.makeChartInteractive(chart, xAxis, yAxis);
			ChartToolsFX.addChartExportMenu(chart, null);
		}
		
		
		
		
		public Pane getPane() {
			return pane;
		}
		
		
		private void updateChart() {
			String xMeasurement = comboScatterMainMeasurement.getSelectionModel().getSelectedItem();
			String yMeasurement = comboScatterSecondaryMeasurement.getSelectionModel().getSelectedItem();
			
			double[] x = model.getDoubleValues(xMeasurement);
			double[] y = model.getDoubleValues(yMeasurement);
			int count = 0;
			
			List<TMAEntry> entries = model.getEntries();
			ObservableList<XYChart.Data<Number, Number>> data = FXCollections.observableArrayList();
			for (int i = 0; i < x.length; i++) {
				double xx = x[i];
				double yy = y[i];
				if (Double.isNaN(xx) || Double.isNaN(yy))
					continue;
				
				// Adding jitter (but need to consider axis scaling)
//				xx = xx + Math.random()/5;
//				yy = yy + Math.random()/5;
				
				XYChart.Data<Number, Number> item = new XYChart.Data<>(xx, yy, entries.get(i));
				data.add(item);
				
				// Shift values back to replace any NaNs
				x[count] = xx;
				y[count] = yy;
				count++;
			}
			if (chart.getData().isEmpty())
				chart.getData().add(new XYChart.Series<>(data));
			else
				chart.getData().get(0).setData(data);
			
			for (XYChart.Data<Number, Number> element : data) {
				Node node = element.getNode();
				Object value = element.getExtraValue();
				if (value instanceof TMAEntry) {
					TMAEntry entry = (TMAEntry)value;
					if (entry.getMeasurement(colCensored) != null && entry.getMeasurement(colCensored).doubleValue() == 1)
						node.setStyle(""
							+ "-fx-background-color: rgb(60, 200, 60, 0.75); "
							+ "-fx-opacity: 0.5;"
							+ "");
					else {
						node.setStyle(""
								+ "-fx-opacity: 0.75;"
								+ "");
					}
					node.setOnMouseClicked(e -> {
						// Only clear selection if selection isn't used for display
						if (!useSelectedProperty.get())
							table.getSelectionModel().clearSelection();
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						
						// TODO: REINSTATE SELECTION???
//						table.getSelectionModel().select(entry);
//						table.scrollTo(entry);
					});
				}
				DropShadow dropShadow = new DropShadow();
				Node nodeFinal = node;
				nodeFinal.hoverProperty().addListener((v, o, n) -> {
					nodeFinal.setEffect(n ? dropShadow : null);
				});
			}
			
			xAxis.setLabel(xMeasurement);
			yAxis.setLabel(yMeasurement);
			chart.setLegendVisible(false);
			
			
			// Try to update table data
			if (count == 0) {
				tableScatter.getItems().clear();
				return;
			}
			int len = x.length;
			int nNanX = TMAScoreImporter.numNaNs(x);
			int nNanY = TMAScoreImporter.numNaNs(y);
			if (count < x.length) {
				x = Arrays.copyOf(x, count);
				y = Arrays.copyOf(y, count);
			}
			tableScatter.getItems().setAll(
					new SimpleDoubleProperty(null, "Total '" + xMeasurement + "'", len - nNanX),
					new SimpleDoubleProperty(null, "Total '" + yMeasurement + "'", len - nNanY),
					new SimpleDoubleProperty(null, String.format("Total '%s' & '%s'", xMeasurement, yMeasurement), count)
					);
			if (count > 1) {
				double pearsons = new PearsonsCorrelation().correlation(x, y);
				double spearmans = new SpearmansCorrelation().correlation(x, y);
				tableScatter.getItems().addAll(
						new SimpleDoubleProperty(null, "Pearson's correlation coefficient", pearsons),
						new SimpleDoubleProperty(null, "Spearman's correlation coefficient", spearmans)
						);
			}
			
		}
		
	}
	
	
	
	
	private int importScores(final String text) {
		Map<String, List<String>> data = TMAScoreImporter.readCSV(text);
		List<String> idColumn = data.remove(TMACoreObject.KEY_UNIQUE_ID);
		if (idColumn == null) {
			DisplayHelpers.showErrorMessage("Import TMA data", "No '" + TMACoreObject.KEY_UNIQUE_ID + "' column found!");
			return 0;
		}
		// Nothing left to import...
		if (data.isEmpty())
			return 0;
		
		// Get the numeric columns, if possible
		Map<String, double[]> dataNumeric = new HashMap<>();
		for (String key : data.keySet().toArray(new String[0])) {
			double[] vals = TMAScoreImporter.parseNumeric(data.get(key), true);
			if (vals != null && TMAScoreImporter.numNaNs(vals) != vals.length) {
				dataNumeric.put(key, vals);
				data.remove(key);
			}
		}
		
		// Loop through IDs, adding values where needed
		int counter = 0;
		for (int i = 0; i < idColumn.size(); i++) {
			boolean matched = false;
			String id = idColumn.get(i);
			if (id == null) {
				logger.debug("Skipping missing ID");
				continue;
			}
			for (TMAEntry entry : entriesBase) {
				if (id.equals(entry.getMetadataValue(TMACoreObject.KEY_UNIQUE_ID))) {
					matched = true;
					for (Entry<String, double[]> dataEntry : dataNumeric.entrySet()) {
						entry.putMeasurement(dataEntry.getKey(), dataEntry.getValue()[i]);
					}
					for (Entry<String, List<String>> dataEntry : data.entrySet()) {
						entry.putMetadata(dataEntry.getKey(), dataEntry.getValue().get(i));
					}
					counter++;
				}
			}
			if (!matched)
				logger.warn("No match for ID: " + id);
		}
		return counter;
	}
	
	
	
	
	
	
	void promptForPredicate(final List<? extends TMAEntry> entries) {
		
		Set<String> measurementNames = new TreeSet<>();
		for (TMAEntry entry : entries)
			measurementNames.addAll(entry.getMeasurementNames());
		if (measurementNames.isEmpty())
			DisplayHelpers.showErrorMessage("Set predicate error", "No measurements available!");
		
		ListView<String> listNames = new ListView<>();
		listNames.setTooltip(new Tooltip("Double-click on a measurement to insert it into the predicate text field"));
		listNames.getItems().setAll(measurementNames);
		
		TextField tfCommand = new TextField();
		tfCommand.setTooltip(new Tooltip("Predicate used to filter entries for inclusion"));

		IntegerProperty lastCaret = new SimpleIntegerProperty();
		tfCommand.caretPositionProperty().addListener((v, o, n) -> {
			if (tfCommand.isFocused())
				lastCaret.set(n.intValue());
		});
		listNames.setOnMouseClicked(e -> {
			if (e.getClickCount() <= 1)
				return;
			String selected = listNames.getSelectionModel().getSelectedItem();
			if (selected != null) {
				int pos = lastCaret.get();
				if (pos > tfCommand.getText().length())
					pos = 0;
				if (tfCommand.getSelection().getLength() > 0)
					tfCommand.replaceSelection("\"" + selected + "\"");
				else
					tfCommand.insertText(pos, "\"" + selected + "\"");
				tfCommand.requestFocus();
				tfCommand.deselect();
				tfCommand.positionCaret(pos + selected.length()+2);
			}
		});
		
		
		Label labelInstructions = new Label();
		labelInstructions.setText("Enter a predicate to filter entries.\n" + 
				"Only entries passing the test will be included in any results.\n" + 
				"Examples of possible predicates include:\n" + 
				"\"Num Tumor\" > 200\n" + 
				"\"Num Tumor\" > 100 && \"Num Stroma\" < 1000");
		labelInstructions.setTooltip(new Tooltip("Note: measurement names must be in \"inverted commands\" and\n" + 
				"&& indicates 'and', while || indicates 'or'."));
		labelInstructions.setContentDisplay(ContentDisplay.CENTER);
		labelInstructions.setAlignment(Pos.CENTER);
		labelInstructions.setTextAlignment(TextAlignment.CENTER);
		labelInstructions.setMaxWidth(Double.MAX_VALUE);
		
		GridPane pane = new GridPane();
		pane.add(labelInstructions, 0, 0, 2, 1);
		pane.add(listNames, 0, 1, 2, 1);
		pane.add(new Label("Predicate"), 0, 2, 1, 1);
		pane.add(tfCommand, 1, 2, 1, 1);
		pane.setHgap(10);
		pane.setVgap(10);
		
		Predicate<? super TMAEntry> previousPredicate = predicate.get();
		if (previousPredicate instanceof TablePredicate)
			tfCommand.setText(((TablePredicate)previousPredicate).getOriginalCommand());
		
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.initOwner(stage);
		
		dialog.getDialogPane().setContent(pane);
		
		ButtonType buttonTypeTest = new ButtonType("Test");
		ButtonType buttonTypeClear = new ButtonType("Clear");
		dialog.getDialogPane().getButtonTypes().setAll(buttonTypeClear, buttonTypeTest, ButtonType.OK, ButtonType.CANCEL);
		
		dialog.setOnCloseRequest(e -> {
			
		});
		
		((Button)dialog.getDialogPane().lookupButton(buttonTypeTest)).addEventFilter(ActionEvent.ACTION, e -> {
			TablePredicate predicateNew = new TablePredicate(tfCommand.getText());
			if (predicateNew.isValid()) {
				predicate.set(predicateNew);
			} else {
				DisplayHelpers.showErrorMessage("Invalid predicate", "Current predicate '" + tfCommand.getText() + "' is invalid!");
			}
			e.consume();
		});
		((Button)dialog.getDialogPane().lookupButton(buttonTypeClear)).addEventFilter(ActionEvent.ACTION, e -> {
			tfCommand.clear();
			predicate.set(null);
			e.consume();
		});
		
		Optional<ButtonType> result = dialog.showAndWait();
		
		if (result.isPresent() && result.get().equals(ButtonType.OK)) {
			TablePredicate predicateNew = new TablePredicate(tfCommand.getText());
			if (predicateNew.isValid())
				predicate.set(predicateNew);
			else
				DisplayHelpers.showErrorMessage("Invalid predicate", "Current predicate '" + predicateNew + "' is invalid!");
		} else
			predicate.set(previousPredicate);
		
		logger.info("Predicate set to: {}", predicate.get());
	}
	
	
	/**
	 * This is admittedly not the most beautiful or safe way to deal with an arbitrary predicate,
	 * but a bit of sanity-checking & cleanup hopefully avoids the security risk of running a full Javascript 
	 * engine as a glorified expression parser.
	 * 
	 * Its use is to filter out particular TMAEntries, so they don't contribute to any summaries.
	 */
	static class TablePredicate implements Predicate<TMAEntry> {
		
		final String commandOriginal;
		final String command;
		final SimpleBindings bindings = new SimpleBindings();
		final ScriptEngine engine;
		private boolean lastEvaluationSucceeded = true;
		private boolean isValid = false;
		
		TablePredicate(final String predicate) {
			// Predicates are only allowed to contain quoted strings (converted to measurement value requests) 
			// as well as operators or parentheses
			this.commandOriginal = predicate;
			String quotedRegex = "\"([^\"]*)\"";
			String test = predicate.replaceAll(quotedRegex, "");
			isValid = test.replaceAll("[ ()+-<>=*/&|!]", "").trim().isEmpty(); // Check we don't have invalid characters
			
			if (isValid) {
				this.command = predicate.replaceAll(quotedRegex, "entry.getMeasurementAsDouble(\"$1\")").trim();
			}
			else
				this.command = null;
			
			
			ScriptEngineManager manager = new ScriptEngineManager();
	        engine = manager.getEngineByName("JavaScript");
	        engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
		}

		@Override
		public boolean test(TMAEntry entry) {
			if (!isValid)
				throw new RuntimeException("Cannot run invalid predicate! Original command: " + commandOriginal);
			
//			if (!"Yes".equals(entry.getMetadataValue("Chemo")))
//				return false;
//			if (!"No".equals(entry.getMetadataValue("Chemo")))
//				return false;

//			if ("TL".equals(entry.getMetadataValue("Core type")) || "TS".equals(entry.getMetadataValue("Core type")))
//				return false;
//			if (!"T".equals(entry.getMetadataValue("Core type")))
//			return false;
			
//			if (!"Central".equals(entry.getMetadataValue("Location")))
//				return false;
//			if (!"invasive".equals(entry.getMetadataValue("Location")))
//				return false;
			
			// If nothing is included, accept everything
			if (this.command.isEmpty())
				return true;
			// Run script to deal with predicate if required
			bindings.put("entry", entry);
			try {
				Object result = engine.eval(command);
				lastEvaluationSucceeded = result instanceof Boolean;
				return Boolean.TRUE.equals(result);
			} catch (ScriptException e) {
				lastEvaluationSucceeded = false;
				logger.error("Error evaluating {} for {}: {}", command, entry, e.getLocalizedMessage());
				return false;
			}
		}
		
		public String getOriginalCommand() {
			return commandOriginal;
		}

		public String getCommand() {
			return command;
		}
		
		public boolean isValid() {
			return isValid;
		}

		public boolean lastEvaluationSucceeded() {
			return lastEvaluationSucceeded;
		}
		
		@Override
		public String toString() {
			return getCommand();
		}
		
	}
	
	
	
	
	static class NumericTableCell<T> extends TreeTableCell<T, Number> {

		@Override
		protected void updateItem(Number item, boolean empty) {
			super.updateItem(item, empty);
			if (item == null || empty) {
				setText(null);
				setStyle("");
			} else {
				setAlignment(Pos.CENTER);
				if (Double.isNaN(item.doubleValue()))
					setText("-");
				else {
					if (item.doubleValue() >= 1000)
						setText(GeneralTools.getFormatter(1).format(item));
					else if (item.doubleValue() >= 10)
						setText(GeneralTools.getFormatter(2).format(item));
					else
						setText(GeneralTools.getFormatter(3).format(item));
				}
			}
		}

	}
	
	
	
	static class BasicTableCell<S, T> extends TreeTableCell<S, T> {

		public BasicTableCell() {
			setAlignment(Pos.CENTER);
		}

		@Override
		protected void updateItem(T item, boolean empty) {
			super.updateItem(item, empty);
			if (item == null || empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			setText(item.toString());
		}

	}

}