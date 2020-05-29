/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.gui.tma;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.textfield.TextFields;
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
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableColumn.CellDataFeatures;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.ChartTools;
import qupath.lib.gui.charts.HistogramDisplay;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tma.TMAEntries.TMAEntry;
import qupath.lib.gui.tma.TMAEntries.TMAObjectEntry;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.PathIO;
import qupath.lib.io.TMAScoreImporter;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;


/**
 * Standalone viewer for looking at TMA summary results.
 * 
 * @author Pete Bankhead
 *
 */
public class TMASummaryViewer {
	
	private final static Logger logger = LoggerFactory.getLogger(TMASummaryViewer.class);
	
	private IntegerProperty maxSmallWidth = new SimpleIntegerProperty(150);
	
	private TMAImageCache imageCache = new TMAImageCache(maxSmallWidth.get());
	
	private static String MISSING_COLUMN = "Missing";

	private final Stage stage;

	private ObservableList<String> metadataNames = FXCollections.observableArrayList();
	private ObservableList<String> measurementNames = FXCollections.observableArrayList();
	private ObservableList<String> filteredMeasurementNames = new FilteredList<>(measurementNames, m -> !TMASummaryEntry.isSurvivalColumn(m));

	private ObservableList<String> survivalColumns = FXCollections.observableArrayList();
	private ComboBox<String> comboSurvival = new ComboBox<>(survivalColumns);

	private ObservableList<TMAEntry> entriesBase = FXCollections.observableArrayList();
	
	/**
	 * If trimUniqueIDs is true, Unique ID string will be trimmed to remove whitespace.
	 * This can help with alignment problems due to the ID containing (unrecognized) additional spaces.
	 */
	private boolean trimUniqueIDs = true;
	
	/**
	 * Maintain a reference to columns that were previously hidden whenever loading new data.
	 * This helps maintain some continuity, so that if any columns have the same names then they 
	 * can be hidden as well.
	 */
	private Set<String> lastHiddenColumns = new HashSet<>();
	
	private String colCensored = null;
	
	private Scene scene;

	
	private static enum ImageAvailability {IMAGE_ONLY, OVERLAY_ONLY, BOTH, NONE}
	private static ObjectProperty<ImageAvailability> imageAvailability = new SimpleObjectProperty<>(ImageAvailability.NONE);
	
	
	
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
	private ComboBox<TMAEntries.MeasurementCombinationMethod> comboMeasurementMethod = new ComboBox<>();
	private ReadOnlyObjectProperty<TMAEntries.MeasurementCombinationMethod> selectedMeasurementCombinationProperty = comboMeasurementMethod.getSelectionModel().selectedItemProperty();
	
	
	private TreeTableView<TMAEntry> table = new TreeTableView<>();
	private TMATableModel model;

	private TMAEntry entrySelected = null;

	private BooleanProperty hidePaneProperty = new SimpleBooleanProperty(false);
	private BooleanProperty useSelectedProperty = new SimpleBooleanProperty(false);
	private BooleanProperty skipMissingCoresProperty = new SimpleBooleanProperty(true);
	private BooleanProperty groupByIDProperty = new SimpleBooleanProperty(true);

	private HistogramDisplay histogramDisplay;
	private	KaplanMeierDisplay kmDisplay;
	private ScatterPane scatterPane = new ScatterPane();
	
	
	private ObservableValue<Predicate<TMAEntry>> predicateHideMissing = Bindings.createObjectBinding(() -> {
		if (!skipMissingCoresProperty.get())
			return c -> true;
		else
			return c -> !c.isMissing();
		}, skipMissingCoresProperty);
	
	private ObjectProperty<Predicate<TMAEntry>> predicateMetadataFilter = new SimpleObjectProperty<>();
	
	private ObjectProperty<Predicate<TMAEntry>> predicateMeasurements = new SimpleObjectProperty<>();

	private ObservableValue<Predicate<TMAEntry>> combinedPredicate;

	
	/**
	 * Constructor.
	 * @param stage stage that should be used for this TMA summary viewer. If null, a new stage will be created.
	 */
	public TMASummaryViewer(final Stage stage) {
		if (stage == null)
			this.stage = new Stage();
		else
			this.stage = stage;
		
		logger.trace("Creating TMA summary viewer");
		
		combinedPredicate = Bindings.createObjectBinding(() -> {
			Predicate<TMAEntry> thisPredicate = predicateHideMissing.getValue();
			if (predicateMeasurements.get() != null)
				thisPredicate = thisPredicate.and(predicateMeasurements.getValue());
			if (predicateMetadataFilter.get() != null)
				thisPredicate = thisPredicate.and(predicateMetadataFilter.getValue());
			return thisPredicate;
		}, predicateMeasurements, predicateHideMissing, predicateMetadataFilter);
		
		initialize();
		this.stage.setTitle("TMA Results Viewer");
		this.stage.setScene(scene);
		new DragDropTMADataImportListener(this);
	}
	

	private void initialize() {
		
		model = new TMATableModel();
		
		groupByIDProperty.addListener((v, o, n) -> refreshTableData());

		MenuBar menuBar = new MenuBar();
		Menu menuFile = new Menu("File");
		MenuItem miOpen = new MenuItem("Open...");
		miOpen.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
		miOpen.setOnAction(e -> {
			File file = Dialogs.getChooser(stage).promptForFile(null, null, "TMA data files", new String[]{"qptma"});
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
		
		
		MenuItem miImportFromProject = new MenuItem("Import from current project... (experimental)");
		miImportFromProject.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
		miImportFromProject.setOnAction(e -> setTMAEntriesFromOpenProject());
		
		
		MenuItem miImportClipboard = new MenuItem("Import from clipboard...");
		miImportClipboard.setOnAction(e -> {
			String text = Clipboard.getSystemClipboard().getString();
			if (text == null) {
				Dialogs.showErrorMessage("Import scores", "Clipboard is empty!");
				return;
			}
			int n = importScores(text);
			if (n > 0) {
				setTMAEntries(new ArrayList<>(entriesBase));
			}
			Dialogs.showMessageDialog("Import scores", "Number of scores imported: " + n);
		});
		
		Menu menuEdit = new Menu("Edit");
		MenuItem miCopy = new MenuItem("Copy table to clipboard");
		miCopy.setOnAction(e -> {
			SummaryMeasurementTableCommand.copyTableContentsToClipboard(model, Collections.emptyList());
		});
		
		
		combinedPredicate.addListener((v, o, n) -> {
			// We want any other changes triggered by this to have happened, 
			// so that the data has already been updated
			Platform.runLater(() -> handleTableContentChange());
		});
		
		// Reset the scores for missing cores - this ensures they will be NaN and not influence subsequent results
		MenuItem miResetMissingScores = new MenuItem("Reset scores for missing cores");
		miResetMissingScores.setOnAction(e -> {
			int changes = 0;
			for (TMAEntry entry : entriesBase) {
				if (!entry.isMissing())
					continue;
				boolean changed = false;
				for (String m : entry.getMeasurementNames().toArray(new String[0])) {
					if (!TMASummaryEntry.isSurvivalColumn(m) && !Double.isNaN(entry.getMeasurementAsDouble(m))) {
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
		
		

		MenuTools.addMenuItems(
				menuFile,
				miOpen,
				miSave,
				null,
				miImportClipboard,
				null,
				miImportFromImage,
				miImportFromProject
				);
		menuBar.getMenus().add(menuFile);
		menuEdit.getItems().add(miCopy);
		menuBar.getMenus().add(menuEdit);

		
		menuFile.setOnShowing(e -> {
			boolean imageDataAvailable = QuPathGUI.getInstance() != null && QuPathGUI.getInstance().getImageData() != null && QuPathGUI.getInstance().getImageData().getHierarchy().getTMAGrid() != null;
			miImportFromImage.setDisable(!imageDataAvailable);
			boolean projectAvailable = QuPathGUI.getInstance() != null && QuPathGUI.getInstance().getProject() != null && !QuPathGUI.getInstance().getProject().getImageList().isEmpty();
			miImportFromProject.setDisable(!projectAvailable);
		});
		
		// Double-clicking previously used for comments... but conflicts with tree table expansion
//		table.setOnMouseClicked(e -> {
//			if (!e.isPopupTrigger() && e.getClickCount() > 1)
//				promptForComment();
//		});
		
		table.setPlaceholder(new Text("Drag TMA data folder onto window, or choose File -> Open"));
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		
		BorderPane pane = new BorderPane();
		pane.setTop(menuBar);
		menuBar.useSystemMenuBarProperty().bindBidirectional(PathPrefs.useSystemMenubarProperty());
//		menuBar.setUseSystemMenuBar(true);

		// Create options
		ToolBar toolbar = new ToolBar();
		Label labelMeasurementMethod = new Label("Combination method");
		labelMeasurementMethod.setLabelFor(comboMeasurementMethod);
		labelMeasurementMethod.setTooltip(new Tooltip("Method whereby measurements for multiple cores with the same " + TMACoreObject.KEY_UNIQUE_ID + " will be combined"));
		
		CheckBox cbHidePane = new CheckBox("Hide pane");
		cbHidePane.setSelected(hidePaneProperty.get());
		cbHidePane.selectedProperty().bindBidirectional(hidePaneProperty);
		
		CheckBox cbGroupByID = new CheckBox("Group by ID");
		entriesBase.addListener((Change<? extends TMAEntry> event) -> {
			if (!event.getList().stream().anyMatch(e -> e.getMetadataValue(TMACoreObject.KEY_UNIQUE_ID) != null)) {
				cbGroupByID.setSelected(false);
				cbGroupByID.setDisable(true);
			} else {
				cbGroupByID.setDisable(false);
			}
		});
		cbGroupByID.setSelected(groupByIDProperty.get());
		cbGroupByID.selectedProperty().bindBidirectional(groupByIDProperty);
		
		CheckBox cbUseSelected = new CheckBox("Use selection only");
		cbUseSelected.selectedProperty().bindBidirectional(useSelectedProperty);
		
		CheckBox cbSkipMissing = new CheckBox("Hide missing cores");
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
				cbHidePane,
				new Separator(Orientation.VERTICAL),
				cbGroupByID,
				new Separator(Orientation.VERTICAL),
				cbUseSelected,
				new Separator(Orientation.VERTICAL),
				cbSkipMissing
				);
		comboMeasurementMethod.getItems().addAll(TMAEntries.MeasurementCombinationMethod.values());
		comboMeasurementMethod.getSelectionModel().select(TMAEntries.MeasurementCombinationMethod.MEDIAN);
		selectedMeasurementCombinationProperty.addListener((v, o, n) -> table.refresh());

		
		ContextMenu popup = new ContextMenu();
		MenuItem miSetMissing = new MenuItem("Set missing");
		miSetMissing.setOnAction(e -> setSelectedMissingStatus(true));

		MenuItem miSetAvailable = new MenuItem("Set available");
		miSetAvailable.setOnAction(e -> setSelectedMissingStatus(false));
		
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
		popup.getItems().addAll(
				miSetMissing,
				miSetAvailable,
				new SeparatorMenuItem(),
				miExpand,
				miCollapse);
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
		
		mdTablePane.showDetailNodeProperty().bind(
				Bindings.createBooleanBinding(() ->
				!hidePaneProperty.get() && !entriesBase.isEmpty(),
				hidePaneProperty, entriesBase)
				);
		mdTablePane.setDividerPosition(2.0/3.0);

		pane.setCenter(mdTablePane);
		
		
		
		model.getItems().addListener(new ListChangeListener<TMAEntry>() {
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
		predicateMeasurements.addListener((v, o, n) -> {
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
	
	
	
	private void setSelectedMissingStatus(final boolean status) {
		for (TreeItem<TMAEntry> item : table.getSelectionModel().getSelectedItems()) {
			item.getValue().setMissing(status);
		}
		// Refresh the table data if necessary
		if (skipMissingCoresProperty.get()) {
			table.getSelectionModel().clearSelection();
			refreshTableData();
		} else
			table.refresh();
	}
	
	
	
	
	/**
	 * Update data due to a change in table content.
	 */
	private void handleTableContentChange() {
		table.refresh();
		model.refreshList();
		histogramDisplay.refreshHistogram();
		updateSurvivalCurves();
		scatterPane.updateChart();
	}
	
	
	/**
	 * Get the stage for display.
	 * @return
	 */
	public Stage getStage() {
		return stage;
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
		} else
			logger.info("Survival column: {}, Censored column: {}", colSurvival, colCensored);
		
		colScore = comboMainMeasurement.getSelectionModel().getSelectedItem();
		if (colID == null || colSurvival == null || colCensored == null) {// || colScore == null) {
			// Adjust priority depending on whether we have any data at all..
			if (!model.getItems().isEmpty())
				logger.warn("No survival data found!");
			else
				logger.trace("No entries or survival data available");
			return;
		}
		
		// Generate a pseudo TMA core hierarchy
		Map<String, List<TMAEntry>> scoreMap = createScoresMap(model.getItems(), colScore, colID);
		
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

		TMAGrid grid = DefaultTMAGrid.create(cores, 1);
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
		
		
		
		
		
		// Create a Tab for showing images
		BorderPane paneImages = new BorderPane();
		CheckBox cbShowOverlay = new CheckBox("Show overlay");
		imageAvailability.addListener((c, v, n) -> {
			if (n == ImageAvailability.OVERLAY_ONLY)
				cbShowOverlay.setSelected(true);
			else if (n == ImageAvailability.IMAGE_ONLY)
				cbShowOverlay.setSelected(false);
			cbShowOverlay.setDisable(n != ImageAvailability.BOTH);
		});
		ListView<TMAEntry> listImages = new ListView<>();
		listImages.setCellFactory(v -> new ImageListCell(cbShowOverlay.selectedProperty(), imageCache));
		listImages.widthProperty().addListener((v, o, n) -> listImages.refresh());
		listImages.setStyle("-fx-control-inner-background-alt: -fx-control-inner-background ;");
		table.getSelectionModel().getSelectedItems().addListener((Change<? extends TreeItem<TMAEntry>> e) -> {
			List<TMAEntry> entries = new ArrayList<>();
			for (TreeItem<TMAEntry> item : e.getList()) {
				if (item.getChildren().isEmpty()) {
					if (item.getValue().hasImage() || item.getValue().hasOverlay())
						entries.add(item.getValue());
				} else {
					for (TreeItem<TMAEntry> item2 : item.getChildren()) {
						if (item2.getValue().hasImage() || item2.getValue().hasOverlay())
							entries.add(item2.getValue());
					}					
				}
				listImages.getItems().setAll(entries);
			}
		});
		cbShowOverlay.setAlignment(Pos.CENTER);
		cbShowOverlay.setMaxWidth(Double.MAX_VALUE);
		cbShowOverlay.setPadding(new Insets(5, 5, 5, 5));
		cbShowOverlay.selectedProperty().addListener((v, o, n) -> listImages.refresh());
		paneImages.setCenter(listImages);
		paneImages.setTop(cbShowOverlay);
		
		
		
		// Determine visibility based upon whether there are any images to show
//		Tab tabImages = new Tab("Images", paneImages);
		
		
		ScrollPane scrollPane = new ScrollPane(paneKaplanMeier);
		scrollPane.setFitToWidth(true);
		scrollPane.setFitToHeight(true);
		scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
		scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
		Tab tabSurvival = new Tab("Survival", scrollPane);
		tabPane.getTabs().addAll(
				new Tab("Table", getCustomizeTablePane()),
//				tabImages,
				new Tab("Histogram", histogramDisplay.getPane()),
				new Tab("Scatterplot", scatterPane.getPane()),
				tabSurvival
				);
		tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
		
//		if (imageAvailability.get() != ImageAvailability.NONE)
//			tabPane.getTabs().add(1, tabImages);
//		
//		imageAvailability.addListener((c, v, n) -> {
//			if (n == ImageAvailability.NONE)
//				tabPane.getTabs().remove(tabImages);
//			else if (!tabPane.getTabs().contains(tabImages))
//				tabPane.getTabs().add(1, tabImages);
//		});
		
//		tabSurvival.visibleProperty().bind(
//				Bindings.createBooleanBinding(() -> !survivalColumns.isEmpty(), survivalColumns)
//				);

		pane.setCenter(tabPane);

		pane.setMinWidth(350);
		
		return pane;
	}
	
	

	private Pane getCustomizeTablePane() {
		TableView<TreeTableColumn<TMAEntry, ?>> tableColumns = new TableView<>();
		tableColumns.setPlaceholder(new Text("No columns available"));
		tableColumns.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tableColumns.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		
		SortedList<TreeTableColumn<TMAEntry, ?>> sortedColumns = new SortedList<>(table.getColumns().filtered(p -> !p.getText().trim().isEmpty()));
		sortedColumns.setComparator((c1, c2) -> c1.getText().compareTo(c2.getText()));
		tableColumns.setItems(sortedColumns);
		sortedColumns.comparatorProperty().bind(tableColumns.comparatorProperty());
//		sortedColumns.comparatorProperty().bind(tableColumns.comparatorProperty());

		
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
		
		Action actionShowSelected = new Action("Show selected", e -> {
			for (TreeTableColumn<?, ?> col : tableColumns.getSelectionModel().getSelectedItems()) {
				if (col != null)
					col.setVisible(true);
				else {
					// Not sure why this happens...?
					logger.trace("Selected column is null!");
				}
			}
		});
		
		Action actionHideSelected = new Action("Hide selected", e -> {
			for (TreeTableColumn<?, ?> col : tableColumns.getSelectionModel().getSelectedItems()) {
				if (col != null)
					col.setVisible(false);
				else {
					// Not sure why this happens...?
					logger.trace("Selected column is null!");
				}
			}
		});
		
		contextMenu.getItems().addAll(
				ActionUtils.createMenuItem(actionShowSelected),
				ActionUtils.createMenuItem(actionHideSelected));
		tableColumns.setContextMenu(contextMenu);
		tableColumns.setTooltip(new Tooltip("Show or hide table columns - right-click to change multiple columns at once"));
		
		BorderPane paneColumns = new BorderPane(tableColumns);
		paneColumns.setBottom(
				PaneTools.createColumnGridControls(
						ActionUtils.createButton(actionShowSelected),
						ActionUtils.createButton(actionHideSelected)
						)
				);
		
		
		VBox paneRows = new VBox();
		
		// Create a box to filter on some metadata text
		ComboBox<String> comboMetadata = new ComboBox<>();
		comboMetadata.setItems(metadataNames);
		comboMetadata.getSelectionModel().getSelectedItem();
		comboMetadata.setPromptText("Select column");
		TextField tfFilter = new TextField();
		CheckBox cbExact = new CheckBox("Exact");
		// Set listeners
		cbExact.selectedProperty().addListener((v, o, n) -> setMetadataTextPredicate(comboMetadata.getSelectionModel().getSelectedItem(), tfFilter.getText(), cbExact.isSelected(), !cbExact.isSelected()));
		tfFilter.textProperty().addListener((v, o, n) -> setMetadataTextPredicate(comboMetadata.getSelectionModel().getSelectedItem(), tfFilter.getText(), cbExact.isSelected(), !cbExact.isSelected()));
		comboMetadata.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> setMetadataTextPredicate(comboMetadata.getSelectionModel().getSelectedItem(), tfFilter.getText(), cbExact.isSelected(), !cbExact.isSelected()));
		
		GridPane paneMetadata = new GridPane();
		paneMetadata.add(comboMetadata, 0, 0);
		paneMetadata.add(tfFilter, 1, 0);
		paneMetadata.add(cbExact, 2, 0);
		paneMetadata.setPadding(new Insets(10, 10, 10, 10));
		paneMetadata.setVgap(2);
		paneMetadata.setHgap(5);
		comboMetadata.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(tfFilter, Priority.ALWAYS);
		GridPane.setFillWidth(comboMetadata, Boolean.TRUE);
		GridPane.setFillWidth(tfFilter, Boolean.TRUE);
		
		TitledPane tpMetadata = new TitledPane("Metadata filter", paneMetadata);
		tpMetadata.setExpanded(false);
//		tpMetadata.setCollapsible(false);
		Tooltip tooltipMetadata = new Tooltip("Enter text to filter entries according to a selected metadata column");
		Tooltip.install(paneMetadata, tooltipMetadata);
		tpMetadata.setTooltip(tooltipMetadata);
		paneRows.getChildren().add(tpMetadata);		
		
		
		// Add measurement predicate
		TextField tfCommand = new TextField();
		tfCommand.setTooltip(new Tooltip("Predicate used to filter entries for inclusion"));
		
		TextFields.bindAutoCompletion(tfCommand, e -> {
			int ind = tfCommand.getText().lastIndexOf("\"");
			if (ind < 0)
				return Collections.emptyList();
			String part = tfCommand.getText().substring(ind+1);
			return measurementNames.stream().filter(n -> n.startsWith(part)).map(n -> "\"" + n + "\" ").collect(Collectors.toList());
		});

		String instructions = "Enter a predicate to filter entries.\n" + 
				"Only entries passing the test will be included in any results.\n" + 
				"Examples of predicates include:\n" + 
				"    \"Num Tumor\" > 200\n" + 
				"    \"Num Tumor\" > 100 && \"Num Stroma\" < 1000";
//		labelInstructions.setTooltip(new Tooltip("Note: measurement names must be in \"inverted commands\" and\n" + 
//				"&& indicates 'and', while || indicates 'or'."));

		BorderPane paneMeasurementFilter = new BorderPane(tfCommand);
		Label label = new Label("Predicate: ");
		label.setAlignment(Pos.CENTER);
		label.setMaxHeight(Double.MAX_VALUE);
		paneMeasurementFilter.setLeft(label);
		
		Button btnApply = new Button("Apply");
		btnApply.setOnAction(e -> {
			TablePredicate predicateNew = new TablePredicate(tfCommand.getText());
			if (predicateNew.isValid()) {
				predicateMeasurements.set(predicateNew);
			} else {
				Dialogs.showErrorMessage("Invalid predicate", "Current predicate '" + tfCommand.getText() + "' is invalid!");
			}
			e.consume();
		});
		TitledPane tpMeasurementFilter = new TitledPane("Measurement filter", paneMeasurementFilter);
		tpMeasurementFilter.setExpanded(false);
		Tooltip tooltipInstructions = new Tooltip(instructions);
		tpMeasurementFilter.setTooltip(tooltipInstructions);
		Tooltip.install(paneMeasurementFilter, tooltipInstructions);
		paneMeasurementFilter.setRight(btnApply);
		
		paneRows.getChildren().add(tpMeasurementFilter);
		
		logger.info("Predicate set to: {}", predicateMeasurements.get());
		
		
		
		
		VBox pane = new VBox();
//		TitledPane tpColumns = new TitledPane("Select column", paneColumns);
//		tpColumns.setMaxHeight(Double.MAX_VALUE);
//		tpColumns.setCollapsible(false);
		pane.getChildren().addAll(
				paneColumns,
				new Separator(),
				paneRows
				);
		VBox.setVgrow(paneColumns, Priority.ALWAYS);

		return pane;
	}
	
	
	/**
	 * Set a filter based on a (single) metadata column.
	 * 
	 * @param metadataName
	 * @param filterText
	 * @param exact
	 * @param ignoreCase
	 */
	private void setMetadataTextPredicate(final String metadataName, final String filterText, final boolean exact, final boolean ignoreCase) {
		if (metadataName == null || filterText == null || metadataName.trim().isEmpty() || filterText.trim().isEmpty()) {
			predicateMetadataFilter.set(null);
		} else {
			if (ignoreCase) {
				String filterTextLower = filterText.toLowerCase();
				if (exact)
					predicateMetadataFilter.set(t -> t.getMetadataValue(metadataName) != null && t.getMetadataValue(metadataName).toLowerCase().equals(filterTextLower));
				else
					predicateMetadataFilter.set(t -> t.getMetadataValue(metadataName) != null && t.getMetadataValue(metadataName).toLowerCase().contains(filterTextLower));
			} else if (exact)
				predicateMetadataFilter.set(t -> t.getMetadataValue(metadataName) != null && t.getMetadataValue(metadataName).equals(filterText));
			else
				predicateMetadataFilter.set(t -> t.getMetadataValue(metadataName) != null && t.getMetadataValue(metadataName).contains(filterText));
		}
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
			Dialogs.showErrorMessage("Show TMA summary", "No TMA data available!");
			return;
		}
		ImageData<BufferedImage> imageData = qupath.getImageData();
		setTMAEntriesFromImageData(imageData);
	}
	
	
	
	private void setTMAEntriesFromOpenProject() {
		QuPathGUI qupath = QuPathGUI.getInstance();
		if (qupath == null || qupath.getProject() == null || qupath.getProject().getImageList().isEmpty()) {
			Dialogs.showNoProjectError("Show TMA summary");
			return;
		}
		Project<BufferedImage> project = qupath.getProject();
		
		List<TMAEntry> entries = new ArrayList<>();
		for (ProjectImageEntry<BufferedImage> imageEntry : project.getImageList()) {
			if (imageEntry.hasImageData()) {
				try {
					ImageData<BufferedImage> imageData = imageEntry.readImageData();
					entries.addAll(getEntriesForTMAData(imageData));
				} catch (IOException e) {
					logger.error("Unable to read ImageData for {} ({})", imageEntry.getImageName(), e.getLocalizedMessage());
				}
			}
		}
		setTMAEntries(entries);
		stage.setTitle("TMA Viewer: " + project.getName());
	}
	
	
	
	private static List<TMAEntry> getEntriesForTMAData(final ImageData<BufferedImage> imageData) {
		List<TMAEntry> entriesNew = new ArrayList<>();
		if (imageData.getHierarchy().getTMAGrid() == null)
			return entriesNew;
		ObservableMeasurementTableData data = new ObservableMeasurementTableData();
		data.setImageData(imageData, imageData.getHierarchy().getTMAGrid().getTMACoreList());
		for (TMACoreObject core : imageData.getHierarchy().getTMAGrid().getTMACoreList()) {
			entriesNew.add(TMAEntries.createTMAObjectEntry(imageData, data, core));
		}
		return entriesNew;
	}
	
	
	/**
	 * Set the TMA entries from the TMACoreObjects of a specific ImageData.
	 * 
	 * @param imageData
	 */
	public void setTMAEntriesFromImageData(final ImageData<BufferedImage> imageData) {
		setTMAEntries(getEntriesForTMAData(imageData));
		stage.setTitle("TMA Viewer: " + ServerTools.getDisplayableImageName(imageData.getServer()));
	}
	

	/**
	 * Set the input file for the summary viewer.
	 * @param file
	 */
	public void setInputFile(File file) {
		if (file == null)
			return;
		
		if (file.getName().toLowerCase().endsWith(PathPrefs.getSerializationExtension())) {
			try {
				ImageData<BufferedImage> imageData = PathIO.readImageData(file, null, null, BufferedImage.class);
				setTMAEntriesFromImageData(imageData);
			} catch (IOException e) {
				logger.error("Error reading image data", e);
			}
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
		
//		boolean containsSummaries = newEntries.stream().anyMatch(e -> e instanceof TMASummaryEntry);
		
		// Turn off use-selected - can be crashy when replacing entries
		if (!newEntries.equals(entriesBase)) {
			useSelectedProperty.set(false);
			
			// Reset the cache
			imageCache.clear();
			
			// Try to load small images in a background thread
			List<TMAEntry> duplicateEntries = new ArrayList<>(newEntries);
			ExecutorService service = Executors.newSingleThreadExecutor();
			service.submit(() -> {
				duplicateEntries.parallelStream().forEach(entry -> {
					imageCache.getImage(entry, maxSmallWidth.get());
					imageCache.getOverlay(entry, maxSmallWidth.get());
				});
			});
			service.shutdown();
			
		}
		this.entriesBase.setAll(newEntries);
		
		// Store the names of any currently hidden columns
		lastHiddenColumns = table.getColumns().stream().filter(c -> !c.isVisible()).map(c -> c.getText()).collect(Collectors.toSet());
		
//		this.table.getColumns().clear();
		
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
		
//		// Add the count of non-missing cores if we are working with summaries
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
		
		
		refreshTableData();
		
		// The next time the table is empty, show a different placeholder 
		// from the original (which is for loading/import)
		table.setPlaceholder(new Text("No data"));
	}

	
	
	
	private void refreshTableData() {
		
//		int nn = 0;
//		double nPositive = 0;
//		for (TMAEntry entry : entriesBase) {
//			if (entry.isMissing())
//				continue;
//			nPositive += entry.getMeasurementAsDouble("Num Positive");
//			nn++;
//		}
//		System.err.println(nPositive + " positive cells across " + nn + " tissue samples");
		
		Collection<? extends TMAEntry> entries = groupByIDProperty.get() ? createSummaryEntries(entriesBase) : entriesBase;

		// Ensure that we don't try to modify a filtered list
		List<TreeTableColumn<TMAEntry, ?>> columns = new ArrayList<>();

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
			TreeTableColumn<TMAEntry, TMAEntry> columnImage = hasImages ? new TreeTableColumn<>("Thumbnail") : null;
			TreeTableColumn<TMAEntry, TMAEntry> columnOverlay = hasOverlay ? new TreeTableColumn<>("Overlay") : null;

			if (hasImages) {
				columnImage.setCellValueFactory(new Callback<CellDataFeatures<TMAEntry, TMAEntry>, ObservableValue<TMAEntry>>() {
					@Override
					public ObservableValue<TMAEntry> call(CellDataFeatures<TMAEntry, TMAEntry> p) {
						return p.getValue().valueProperty();
					}
				});
				columnImage.setCellFactory(c -> new ImageTableCell(imageCache, false));
				columnImage.maxWidthProperty().bind(maxSmallWidth);
				columnImage.widthProperty().addListener((v, o, n) -> {
					if (n.doubleValue() == columnImage.getPrefWidth())
						return;
					if (hasOverlay)
						columnOverlay.setPrefWidth(n.doubleValue());
					table.refresh();
				});
				columns.add(columnImage);
			}

			if (hasOverlay) {
				columnOverlay.setCellValueFactory(new Callback<CellDataFeatures<TMAEntry, TMAEntry>, ObservableValue<TMAEntry>>() {
					@Override
					public ObservableValue<TMAEntry> call(CellDataFeatures<TMAEntry, TMAEntry> p) {
						return p.getValue().valueProperty();
					}
				});
				columnOverlay.setCellFactory(c -> new ImageTableCell(imageCache, true));
				columnOverlay.maxWidthProperty().bind(maxSmallWidth);
				columnOverlay.widthProperty().addListener((v, o, n) -> {
					if (n.doubleValue() == columnOverlay.getPrefWidth())
						return;
					columnImage.setPrefWidth(n.doubleValue());
					if (hasImages)
						table.refresh();
				});
				columns.add(columnOverlay);
			}
		}


		// Update image availability
		if (hasImages) {
			if (hasOverlay)
				imageAvailability.set(ImageAvailability.BOTH);
			else
				imageAvailability.set(ImageAvailability.IMAGE_ONLY);
		} else if (hasOverlay) {
			imageAvailability.set(ImageAvailability.OVERLAY_ONLY);
		} else
			imageAvailability.set(ImageAvailability.NONE);



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
				column.setCellFactory(c -> new NumericTreeTableCell<>());
				columns.add(column);
			} else {
				TreeTableColumn<TMAEntry, Object> column = new TreeTableColumn<>(name);
				column.setCellValueFactory(new Callback<CellDataFeatures<TMAEntry, Object>, ObservableValue<Object>>() {
					@Override
					public ObservableValue<Object> call(CellDataFeatures<TMAEntry, Object> p) {
						return new SimpleObjectProperty<>(p.getValue() == null ? null : model.getStringValue(p.getValue().getValue(), name));
					}
				});
				column.setCellFactory(c -> new CenteredTreeTableCell<>());
				columns.add(column);
			}
		}
		
		// Set the column visibility depending upon whether they were hidden previously
		columns.stream().forEach(c -> c.setVisible(!lastHiddenColumns.contains(c.getText())));
		
		// Set columns for table
		table.getColumns().setAll(columns);
		
		// Set new root for table
		TreeItem<TMAEntry> root = new RootTreeItem(entries, combinedPredicate);
		table.setShowRoot(false);
		table.setRoot(root);
		
		model.refreshList();
	}
	
	
	
	static class RootTreeItem extends TreeItem<TMAEntry> implements ChangeListener<Predicate<TMAEntry>> {
		
		private List<TreeItem<TMAEntry>> entries = new ArrayList<>();
		private ObservableValue<Predicate<TMAEntry>> combinedPredicate;
		
		RootTreeItem(final Collection<? extends TMAEntry> entries, final ObservableValue<Predicate<TMAEntry>> combinedPredicate) {
			super(null);
			for (TMAEntry entry : entries) {
				if (entry instanceof TMASummaryEntry)
					this.entries.add(new SummaryTreeItem((TMASummaryEntry)entry));
				else
					this.entries.add(new TreeItem<>(entry));					
			}
			this.combinedPredicate = combinedPredicate;
			this.combinedPredicate.addListener(new WeakChangeListener<>(this));
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
		public void changed(ObservableValue<? extends Predicate<TMAEntry>> observable,
				Predicate<TMAEntry> oldValue, Predicate<TMAEntry> newValue) {
			updateChildren();
		}
		
	}
	
	static class SummaryTreeItem extends TreeItem<TMAEntry> {
		
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
			logger.error("Error parsing input file", e);
		}
		if (serverPath == null) { // || !(new File(serverPath).exists())) {
			logger.error("Unable to find a server with path " + serverPath + " - cannot parse " + file.getAbsolutePath());
			return;
		}		

		File dirData = new File(file.getAbsolutePath() + ".data");
		
		try {
			File fileResults = getTMAResultsFile(dirData);
			if (fileResults == null) {
				logger.error("No results file found for {}", dirData.getAbsolutePath());
				return;
			}
			Map<String, List<String>> csvData = TMAScoreImporter.readCSV(fileResults);
			if (csvData.isEmpty()) {
				logger.warn("Results file empty: {}", fileResults.getAbsolutePath());
				return;
			}
			
			// Identify metadata and numeric columns
			Map<String, List<String>> metadataColumns = new LinkedHashMap<>();
			Map<String, double[]> measurementColumns = new LinkedHashMap<>();
			List<String> idColumn = csvData.remove(TMACoreObject.KEY_UNIQUE_ID);
			if (idColumn != null) {
				metadataColumns.put(TMACoreObject.KEY_UNIQUE_ID, idColumn);
				
				// Make sure IDs are trimmed
				if (trimUniqueIDs) {
					for (int i = 0; i < idColumn.size(); i++)
						idColumn.set(i, idColumn.get(i) == null ? null : idColumn.get(i).trim());
				}
			}
			List<String> nameColumn = csvData.remove("Name");
			if (nameColumn == null)
				nameColumn = csvData.remove("Object");
			// Handle 'missing-ness' separately from general metadata
			List<String> missingColumn = csvData.remove(MISSING_COLUMN);
			int n = idColumn == null ? 0 : idColumn.size(); //csvData.values().iterator().next().size();
			for (Entry<String, List<String>> entry : csvData.entrySet()) {
				List<String> list = entry.getValue();
				n = list.size();
				double[] values = TMAScoreImporter.parseNumeric(list, true);
				if (values == null || GeneralTools.numNaNs(values) == list.size())
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
				TMAEntry entry = TMAEntries.createDefaultTMAEntry(serverPath, fileImage.getAbsolutePath(), fileOverlayImage.getAbsolutePath(), name, missing);
				for (Entry<String, List<String>> temp : metadataColumns.entrySet()) {
					entry.putMetadata(temp.getKey(), temp.getValue().get(i));
				}
				for (Entry<String, double[]> temp : measurementColumns.entrySet()) {
					entry.putMeasurement(temp.getKey(), temp.getValue()[i]);
				}
				entries.add(entry);
			}
		} catch (Exception e) {
			logger.error("Error parsing input file " + file, e);
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
				return entry.getImageName();
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
			return GeneralTools.formatNumber(getNumericValue(entry, column), 4);
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
			List<TMAEntry> entries = getItems();
			double[] values = new double[entries.size()];
			for (int i = 0; i < entries.size(); i++)
				values[i] = getNumericValue(entries.get(i), column);
			return values;
		}

		@Override
		public ObservableList<TMAEntry> getItems() {
			return list;
//			if (useSelectedProperty.get())
//				return Collections.unmodifiableList(table.getSelectionModel().getSelectedItems());
//			return Collections.unmodifiableList(table.getItems());
		}


	}


	private void promptForComment() {
		String input = Dialogs.showInputDialog( 
				"Add comment",
				"Type comment for " + entrySelected.getName() + "(" + entrySelected.getImageName() + ")", entrySelected.getComment());
		if (input == null)
			return;
		entrySelected.setComment(input);
		table.refresh();
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
			colValue.setCellValueFactory(v -> new SimpleStringProperty(GeneralTools.formatNumber(v.getValue().getValue(), 3)));
			tableScatter.getColumns().add(colName);
			tableScatter.getColumns().add(colValue);
			tableScatter.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
			tableScatter.setPrefHeight(25*8);
			
			pane.setTop(topGrid);
			pane.setCenter(chart);
			pane.setBottom(tableScatter);
			
			
			// Make it possible to navigate around the chart
			ChartTools.makeChartInteractive(chart, xAxis, yAxis);
			ChartTools.addChartExportMenu(chart, null);
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
			
			List<TMAEntry> entries = model.getItems();
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
						
						// Select the item
						TreeItem<TMAEntry> item = getItem(table.getRoot(), entry);
						if (item != null) {
							item.setExpanded(true);
//							if (item.getParent() != null)
//								item.getParent().setExpanded(true);
							table.getSelectionModel().select(item);
							table.layout();
							int ind = table.getSelectionModel().getSelectedIndex();
							if (ind >= 0) {
								table.scrollTo(ind);
							}
						}
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
			int nNanX = GeneralTools.numNaNs(x);
			int nNanY = GeneralTools.numNaNs(y);
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
	
	
	/**
	 * Recursively search for a TreeItem, based upon the TMAEntry it represents.
	 * 
	 * @param item
	 * @param entry
	 * @return
	 */
	private TreeItem<TMAEntry> getItem(final TreeItem<TMAEntry> item, final TMAEntry entry) {
		if (item == null)
			return null;
		if (item.getValue() == entry)
			return item;
		for (TreeItem<TMAEntry> item2 : item.getChildren()) {
			TreeItem<TMAEntry> found = getItem(item2, entry);
			if (found != null)
				return found;
		}
		return null;
	}
	
	
	
	private int importScores(final String text) {
		Map<String, List<String>> data = TMAScoreImporter.readCSV(text);
		List<String> idColumn = data.remove(TMACoreObject.KEY_UNIQUE_ID);
		if (idColumn == null) {
			Dialogs.showErrorMessage("Import TMA data", "No '" + TMACoreObject.KEY_UNIQUE_ID + "' column found!");
			return 0;
		}
		// Nothing left to import...
		if (data.isEmpty())
			return 0;
		
		// Get the numeric columns, if possible
		Map<String, double[]> dataNumeric = new HashMap<>();
		for (String key : data.keySet().toArray(new String[0])) {
			double[] vals = TMAScoreImporter.parseNumeric(data.get(key), true);
			if (vals != null && GeneralTools.numNaNs(vals) != vals.length) {
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
		
		Optional<TMAEntry> objectEntry = entriesBase.stream().filter(t -> t instanceof TMAObjectEntry).findAny();
		if (objectEntry.isPresent()) {
			Dialogs.showInfoNotification("TMA data update", "TMA cores updated!");
		}
		
		return counter;
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
	
	
}