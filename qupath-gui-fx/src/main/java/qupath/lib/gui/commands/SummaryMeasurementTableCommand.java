/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tooltip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TableView.TableViewSelectionModel;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.fx.controls.PredicateTextField;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.HistogramDisplay;
import qupath.lib.gui.charts.ScatterPlotDisplay;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;


/**
 * Show a summary table for an object of a particular type (annotation, detection, TMA core...)
 * 
 * @author Pete Bankhead
 */
public class SummaryMeasurementTableCommand {

	private static final Logger logger = LoggerFactory.getLogger(SummaryMeasurementTableCommand.class);

	private static final BooleanProperty useRegexColumnFilter = PathPrefs.createPersistentPreference("summaryMeasurementTableUseRegexColumnFilter", false);

	private final QuPathGUI qupath;
	
	private BooleanProperty showThumbnailsProperty ;

	private BooleanProperty showObjectIdsProperty;

	/**
	 * Command to show a summary measurement table, for PathObjects of a specified type (e.g. annotation, detection).
	 * @param qupath
	 */
	public SummaryMeasurementTableCommand(final QuPathGUI qupath) {
		super();
		this.qupath = qupath;
	}

	/**
	 * Show a measurement table for the specified image data.
	 * @param imageData the image data
	 * @param type the object type to show
	 */
	public void showTable(ImageData<BufferedImage> imageData, Class<? extends PathObject> type) {
		if (imageData == null) {
			GuiTools.showNoImageError("Show measurement table");
			return;
		}
		
		if (showThumbnailsProperty == null)
			showThumbnailsProperty = new SimpleBooleanProperty(PathPrefs.showMeasurementTableThumbnailsProperty().get());

		if (showObjectIdsProperty == null)
			showObjectIdsProperty = new SimpleBooleanProperty(PathPrefs.showMeasurementTableObjectIDsProperty().get());

		final PathObjectHierarchy hierarchy = imageData.getHierarchy();

		ObservableMeasurementTableData model = new ObservableMeasurementTableData();
		model.setImageData(imageData, imageData.getHierarchy().getObjects(null, type));

		SplitPane splitPane = new SplitPane();
		TabPane plotTabs = new TabPane();
		HistogramDisplay histogramDisplay = new HistogramDisplay(model, true);
		ScatterPlotDisplay scatterPlotDisplay = new ScatterPlotDisplay(model);

		Tab tabHistogram = new Tab("Histogram", histogramDisplay.getPane());
		tabHistogram.setClosable(false);
		plotTabs.getTabs().add(tabHistogram);

		Tab tabScatter = new Tab("Scatter plot", scatterPlotDisplay.getPane());
		tabScatter.setClosable(false);
		plotTabs.getTabs().add(tabScatter);

		//		table.setTableMenuButtonVisible(true);
		TableView<PathObject> table = new TableView<>();
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		table.getSelectionModel().getSelectedItems().addListener(new ListChangeListener<>() {
            @Override
            public void onChanged(ListChangeListener.Change<? extends PathObject> c) {
                synchronizeSelectionModelToTable(hierarchy, c, table);
            }
        });
		StringProperty displayedName = new SimpleStringProperty(ServerTools.getDisplayableImageName(imageData.getServer()));
		var title = Bindings.createStringBinding(() -> {
			if (type == null)
				return "Measurements " + displayedName.get();
			else
				return PathObjectTools.getSuitableName(type, true) + ": " + displayedName.get();
		}, displayedName);
		

		// Handle double-click as a way to center on a ROI
		var centerCode = new KeyCodeCombination(KeyCode.SPACE);
		table.setRowFactory(params -> {
			var row = new TableRow<PathObject>();
			row.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2) {
					maybeCenterROI(row.getItem());
				}
			});
			return row;
		});
		table.setOnKeyPressed(e -> {
			if (centerCode.match(e)) {
				var selected = table.getSelectionModel().getSelectedItem();
				if (selected != null)
					maybeCenterROI(selected);
			}
		});
				
		// Add a column to display images thumbnails
		TableColumn<PathObject, PathObject> colThumbnails = new TableColumn<>("Thumbnail");
		colThumbnails.setCellValueFactory(val -> new SimpleObjectProperty<>(val.getValue()));
		colThumbnails.visibleProperty().bind(showThumbnailsProperty);
		double padding = 10;
		var viewer = qupath.getAllViewers().stream().filter(v -> v.getImageData() == imageData).findFirst().orElse(null);
		colThumbnails.setCellFactory(column -> PathObjectImageManagers.createTableCell(
				viewer, imageData.getServer(), true, padding,
				qupath.getThreadPoolManager().getSingleThreadExecutor(this)));
//			col.widthProperty().addListener((v, o, n) -> table.refresh());
//		colThumbnails.setMaxWidth(maxWidth + padding*2);
		table.getColumns().add(colThumbnails);
		
		// Set fixed cell size - this can avoid large numbers of non-visible cells being computed
		table.fixedCellSizeProperty().bind(Bindings.createDoubleBinding(() -> {
			if (colThumbnails.isVisible())
				return Math.max(24, colThumbnails.getWidth() + padding);
			else
				return -1.0;
		}, colThumbnails.widthProperty(), colThumbnails.visibleProperty()));
		
		// Have fewer histogram bins if we have TMA cores (since there aren't usually very many)
		boolean tmaCoreList = TMACoreObject.class.isAssignableFrom(type);
		if (tmaCoreList)
			histogramDisplay.setNumBins(10);			


		// Create numeric columns
		TableColumn<PathObject, String> colObjectIDs = null;
		for (String columnName : model.getAllNames()) {
			// Add column
			var tooltipText = model.getHelpText(columnName);
			if (!model.isNumericMeasurement(columnName)) {
				TableColumn<PathObject, String> col = new TableColumn<>(columnName);
				col.setCellValueFactory(column -> createStringMeasurement(model, column.getValue(), column.getTableColumn().getText()));
				col.setCellFactory(column -> new BasicTableCell<>(tooltipText));
				if (ObservableMeasurementTableData.NAME_OBJECT_ID.equals(columnName)) {
					colObjectIDs = col;
				} else {
					table.getColumns().add(col);			
				}
			} else {
				TableColumn<PathObject, Number> col = new TableColumn<>(columnName);
				col.setCellValueFactory(cellData -> createNumericMeasurement(model, cellData.getValue(), cellData.getTableColumn().getText()));
				col.setCellFactory(column -> new NumericTableCell<>(tooltipText, histogramDisplay));
				table.getColumns().add(col);			
			}
		}
		// Add object ID column at the end, since it takes quite a lot of space
		if (colObjectIDs != null) {
			colObjectIDs.visibleProperty().bind(showObjectIdsProperty);
			table.getColumns().add(colObjectIDs);		
		}


		// Set the PathObjects - need to deal with sorting, since a FilteredList won't handle it directly
		SortedList<PathObject> items = new SortedList<>(model.getItems());
		items.comparatorProperty().bind(table.comparatorProperty());
		table.setItems(items);


		// Add buttons at the bottom
		List<ButtonBase> buttons = new ArrayList<>();
		
		ToggleButton btnPlots = new ToggleButton("Show plots");
		btnPlots.selectedProperty().addListener((v, o, n) -> {
			if (n) {
				splitPane.getItems().add(plotTabs);
			} else {
				splitPane.getItems().remove(plotTabs);
			}
		});
		buttons.add(btnPlots);
		
		Button btnCopy = new Button("Copy to clipboard");
		btnCopy.setOnAction(e -> {
			// TODO: Deal with repetition immediately below...
			Set<String> excludeColumns = new HashSet<>();
			for (TableColumn<?, ?> col : table.getColumns()) {
				if (!col.isVisible())
					excludeColumns.add(col.getText());
			}
			copyTableContentsToClipboard(model, excludeColumns);
		});
		buttons.add(btnCopy);

		Button btnSave = new Button("Save");
		btnSave.setOnAction(e -> {
			Set<String> excludeColumns = new HashSet<>();
			for (TableColumn<?, ?> col : table.getColumns()) {
				if (!col.isVisible())
					excludeColumns.add(col.getText());
			}
			File fileOutput = promptForOutputFile();
			if (fileOutput == null)
				return;
			if (saveTableModel(model, fileOutput, excludeColumns)) {
				WorkflowStep step;
				String includeColumns;
				if (excludeColumns.isEmpty())
					includeColumns = "";
				else {
					List<String> includeColumnList = new ArrayList<>(model.getAllNames());
					includeColumnList.removeAll(excludeColumns);
					includeColumns = ", " + includeColumnList.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", "));
				}
				String path = qupath.getProject() == null ? fileOutput.toURI().getPath() : fileOutput.getParentFile().toURI().getPath();
				if (type == TMACoreObject.class) {
					step = new DefaultScriptableWorkflowStep("Save TMA measurements",
							String.format("saveTMAMeasurements('%s'%s)", path, includeColumns)
							);
				}
				else if (type == PathAnnotationObject.class) {
					step = new DefaultScriptableWorkflowStep("Save annotation measurements",
							String.format("saveAnnotationMeasurements('%s'%s)", path, includeColumns)
							);
				} else if (type == PathDetectionObject.class) {
					step = new DefaultScriptableWorkflowStep("Save detection measurements",
							String.format("saveDetectionMeasurements('%s'%s)", path, includeColumns)
							);
				} else if (type == PathCellObject.class) {
					step = new DefaultScriptableWorkflowStep("Save cell measurements",
							String.format("saveCellMeasurements('%s'%s)", path, includeColumns)
					);
				} else if (type == PathTileObject.class) {
					step = new DefaultScriptableWorkflowStep("Save tile measurements",
							String.format("saveTileMeasurements('%s'%s)", path, includeColumns)
					);
				} else {
					step = new DefaultScriptableWorkflowStep("Save measurements",
							String.format("saveMeasurements('%s', %s%s)", path, type == null ? null : type.getName(), includeColumns)
							);
				}
				imageData.getHistoryWorkflow().addStep(step);
			}
		});
		buttons.add(btnSave);


		Stage frame = new Stage();
		FXUtils.addCloseWindowShortcuts(frame);
		frame.initOwner(qupath.getStage());
		frame.titleProperty().bind(title);


		BorderPane paneTable = new BorderPane();
		paneTable.setCenter(table);
		// Add text field to filter visible columns
		var tfColumnFilter = new PredicateTextField<String>();
		tfColumnFilter.useRegexProperty().bindBidirectional(useRegexColumnFilter);
		GridPane paneFilter = new GridPane();
		paneFilter.add(new Label("Column filter"), 0, 0);
		paneFilter.add(tfColumnFilter, 1, 0);
		GridPane.setHgrow(tfColumnFilter, Priority.ALWAYS);
		paneFilter.setHgap(5);
		if (tmaCoreList) {
			CheckBox cbHideMissing = new CheckBox("Hide missing cores");
			paneFilter.add(cbHideMissing, 2, 0);
			cbHideMissing.selectedProperty().addListener((v, o, n) -> {
				if (n) {
					model.setPredicate(p -> (!(p instanceof TMACoreObject)) || !((TMACoreObject)p).isMissing());
				} else
					model.setPredicate(null);
			});
			cbHideMissing.setSelected(true);
		}
		
		paneFilter.setPadding(new Insets(2, 5, 2, 5));
		paneTable.setBottom(paneFilter);
		var columnFilter = tfColumnFilter.predicateProperty();
		columnFilter.addListener((v, o, n) -> {
			for (TableColumn<?, ?> col : table.getColumns()) {
				if (col == colThumbnails || col.visibleProperty().isBound()) // Retain thumbnails
					continue;
				var name = col.getText();
				col.setVisible(n.test(name));
			}
		});
		
		// Add some extra options
		var popup = new ContextMenu();
		var miShowImages = new CheckMenuItem("Show thumbnails");
		miShowImages.selectedProperty().bindBidirectional(showThumbnailsProperty);
		popup.getItems().setAll(
				miShowImages
				);

		var miShowObjectIds = new CheckMenuItem("Show Object IDs");
		miShowObjectIds.selectedProperty().bindBidirectional(showObjectIdsProperty);
		popup.getItems().setAll(
				miShowImages,
				miShowObjectIds
				);
		
		var btnExtra = GuiTools.createMoreButton(popup, Side.RIGHT);


		BorderPane pane = new BorderPane();
		splitPane.getItems().add(paneTable);
		pane.setCenter(splitPane);
		var paneButtons = GridPaneUtils.createColumnGridControls(buttons.toArray(new ButtonBase[0]));
		var paneButtons2 = new BorderPane(paneButtons);
		paneButtons2.setRight(btnExtra);
		pane.setBottom(paneButtons2);

		
		PathObjectHierarchyListener listener = new PathObjectHierarchyListener() {

			@Override
			public void hierarchyChanged(PathObjectHierarchyEvent event) {
				if (event.isChanging())
					return;

				if (!Platform.isFxApplicationThread()) {
					Platform.runLater(() -> hierarchyChanged(event));
					return;
				}
                displayedName.set(ServerTools.getDisplayableImageName(imageData.getServer()));

				// TODO: Consider if this can be optimized to avoid rebuilding the full table so often
				if (event.isStructureChangeEvent())
					model.setImageData(imageData, imageData.getHierarchy().getObjects(null, type));

				table.refresh();
				if (histogramDisplay != null) {
					histogramDisplay.refreshHistogram();
				}
				if (scatterPlotDisplay != null) {
					scatterPlotDisplay.refreshScatterPlot();
				}
			}

		};
		
		
		TableViewerListener tableViewerListener = new TableViewerListener(viewer, table);

		frame.setOnShowing(e -> {
			hierarchy.addListener(listener);
			if (viewer != null)
				viewer.addViewerListener(tableViewerListener);
		});
		frame.setOnHiding(e -> {
			hierarchy.removeListener(listener);
			if (viewer != null)
				viewer.removeViewerListener(tableViewerListener);
		});

		Scene scene = new Scene(pane, 800, 500);
		frame.setScene(scene);
		frame.show();
		
		
		// Add ability to remove entries from table
		ContextMenu menu = new ContextMenu();
		Menu menuLimitClasses = new Menu("Show classes");
		menu.setOnShowing(e -> {
			Set<PathClass> representedClasses = model.getBackingListEntries()
					.stream()
					.map(p -> p.getPathClass() == null ? null : p.getPathClass().getBaseClass())
					.collect(Collectors.toCollection(HashSet::new));
			representedClasses.remove(null);
            menuLimitClasses.setVisible(!representedClasses.isEmpty());
			menuLimitClasses.getItems().clear();
			List<PathClass> sortedClasses = new ArrayList<>(representedClasses);
			Collections.sort(sortedClasses);
			MenuItem miClass = new MenuItem("All");
			miClass.setOnAction(e2 -> {
				model.setPredicate(null);
				histogramDisplay.refreshHistogram();
			});
			menuLimitClasses.getItems().add(miClass);
			for (PathClass pathClass : sortedClasses) {
				miClass = new MenuItem(pathClass.getName());
				miClass.setOnAction(e2 -> {
					model.setPredicate(p -> pathClass.isAncestorOf(p.getPathClass()));
					histogramDisplay.refreshHistogram();
				});
				menuLimitClasses.getItems().add(miClass);
			}
		});

	}


	/**
	 * Create a specific numeric measurement.
	 * <p>
	 * Warning! This binding is not guaranteed to update its value automatically upon changes to the
	 * underlying object or data.
	 *
	 * @param pathObject
	 * @param column
	 * @return
	 */
	private ObservableValue<Number> createNumericMeasurement(final ObservableMeasurementTableData model, final PathObject pathObject, final String column) {
		return Bindings.createDoubleBinding(() -> model.getNumericValue(pathObject, column));
	}

	/**
	 * Create a specific String measurement.
	 * <p>
	 * Warning! This binding is not guaranteed to update its value automatically upon changes to the
	 * underlying object or data.
	 *
	 * @param pathObject
	 * @param column
	 * @return
	 */
	private ObservableValue<String> createStringMeasurement(final ObservableMeasurementTableData model, final PathObject pathObject, final String column) {
		return Bindings.createStringBinding(() -> model.getStringValue(pathObject, column));
	}

	
	private void maybeCenterROI(PathObject pathObject) {
		if (pathObject == null)
			return;
		var roi = pathObject.getROI();
		var viewer = qupath.getViewer();
		if (roi != null && viewer != null && viewer.getHierarchy() != null)
			viewer.centerROI(roi);
	}



	static class BasicTableCell<S, T> extends TableCell<S, T> {

		public BasicTableCell(String tooltipText) {
			setAlignment(Pos.CENTER);
			if (tooltipText != null && !tooltipText.isEmpty())
				setTooltip(new Tooltip(tooltipText));
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



	static class NumericTableCell<T> extends TableCell<T, Number> {

		private final HistogramDisplay histogramDisplay;

		public NumericTableCell(String tooltipText, HistogramDisplay histogramDisplay) {
			this.histogramDisplay = histogramDisplay;
			if (tooltipText != null && !tooltipText.isEmpty())
				setTooltip(new Tooltip(tooltipText));
		}


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
						setText(GeneralTools.formatNumber(item.doubleValue(), 1));
					else if (item.doubleValue() >= 10)
						setText(GeneralTools.formatNumber(item.doubleValue(), 2));
					else
						setText(GeneralTools.formatNumber(item.doubleValue(), 3));
				}


				setOnMouseClicked(e -> {
					if (e.isAltDown() && histogramDisplay != null) {
						histogramDisplay.showHistogram(getTableColumn().getText());
						e.consume();
					}
				});
			}
		}

	}



	class TableViewerListener implements QuPathViewerListener {

		private final TableView<PathObject> table;
		private QuPathViewer viewer;

		TableViewerListener(final QuPathViewer viewer, final TableView<PathObject> table) {
			this.viewer = viewer;
			this.table = table;
		}

		@Override
		public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
			// Stop listening to the viewer when the data changes
			if (this.viewer == viewer && imageDataNew != imageDataOld)
				viewer.removeViewerListener(this);
		}

		@Override
		public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}

		@Override
		public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(() -> selectedObjectChanged(viewer, pathObjectSelected));
				return;
			}
			synchronizeTableToSelectionModel(viewer.getHierarchy(), table);
		}

		@Override
		public void viewerClosed(QuPathViewer viewer) {
			viewer.removeViewerListener(this);
			this.viewer = null; // Remove reference
		}

	}


	/**
	 * Get a list of Strings representing table data.
	 * <p>
	 * Each entry in the list corresponds to a row.
	 * 
	 * @param <T> the data type for the table
	 * @param model
	 * @param delim
	 * @param excludeColumns
	 * @return
	 */
	public static <T> List<String> getTableModelStrings(final PathTableData<T> model, final String delim, Collection<String> excludeColumns) {
		var toExclude = Set.of(excludeColumns.toArray(String[]::new));
		return model.getRowStrings(delim, -1, s -> !toExclude.contains(s));
	}


	/**
	 * Get a single String representing the data in a table.
	 * <p>
	 * Note: if the required String is too long (approximately Integer.MAX_VALUE characters), this will throw an IllegalArgumentException.
	 * 
	 * @param <T> the data type for the items in the table
	 * @param model
	 * @param delim
	 * @param excludeColumns
	 * @return
	 * @throws IllegalArgumentException 
	 */
	public static <T> String getTableModelString(final PathTableData<T> model, final String delim, Collection<String> excludeColumns) throws IllegalArgumentException {
		List<String> rows = getTableModelStrings(model, delim, excludeColumns);
		int nSeparators = rows.size() * System.lineSeparator().length();
		long length = rows.stream().mapToLong(String::length).sum() + nSeparators;
		long maxLength = Integer.MAX_VALUE - 1;
		if (length > Integer.MAX_VALUE)
			throw new IllegalArgumentException("Requested string is too long! Requires " + maxLength + " characters, but Java arrays limited to " + maxLength);
		logger.debug("Getting table string (approx {} characters, {} % of maximum)", length, Math.round(length / (double)maxLength  * 100));
		return String.join(System.lineSeparator(), rows);
	}

	/**
	 * Get a single String representing the data in a table and copy it to the clipboard.
	 * <p>
	 * Note: this may not be possible if the String is too long, see {@link #getTableModelString(PathTableData, String, Collection)}.
	 * 
	 * @param model
	 * @param excludeColumns
	 */
	public static void copyTableContentsToClipboard(final PathTableData<?> model, Collection<String> excludeColumns) {
		if (model == null) {
			logger.warn("No table available to copy!");
			return;
		}
		String string = getTableModelString(model, PathPrefs.tableDelimiterProperty().get(), excludeColumns);
		Clipboard clipboard = Clipboard.getSystemClipboard();
		ClipboardContent content = new ClipboardContent();
		content.putString(string);
		clipboard.setContent(content);
	}
	
	
	private static File promptForOutputFile() {
		String ext = ",".equals(PathPrefs.tableDelimiterProperty().get()) ? "csv" : "txt";
		return FileChoosers.promptToSaveFile(FileChoosers.createExtensionFilter("Results data", ext));
	}
	
	/**
	 * Save the data from a table to a text file, using the default delimiter from {@link PathPrefs}.
	 * @param tableModel the data to export
	 * @param fileOutput the file to write the text to; if null, a file chooser will be shown
	 * @param excludeColumns headings for columns that should be excluded
	 * @return
	 */
	public static boolean saveTableModel(final PathTableData<?> tableModel, File fileOutput, Collection<String> excludeColumns) {
		if (fileOutput == null) {
			fileOutput = promptForOutputFile();
			if (fileOutput == null)
				return false;
		}
		try (PrintWriter writer = new PrintWriter(fileOutput, StandardCharsets.UTF_8)) {
			for (String row : getTableModelStrings(tableModel, PathPrefs.tableDelimiterProperty().get(), excludeColumns))
				writer.println(row);
			writer.close();
			return true;
		} catch (IOException e) {
			logger.error("Error writing file to " + fileOutput, e);
		}
		return false;
	}
	
	
	private boolean synchronizingTableToModel = false;
	private boolean synchronizingModelToTable = false;
	
	private void synchronizeSelectionModelToTable(final PathObjectHierarchy hierarchy, final ListChangeListener.Change<? extends PathObject> change, final TableView<PathObject> table) {
		if (synchronizingTableToModel || hierarchy == null)
			return;
		
		PathObjectSelectionModel model = hierarchy.getSelectionModel();
		if (model == null) {
			return;
		}
		
		boolean wasSynchronizingToTree = synchronizingModelToTable;
		try {
			synchronizingModelToTable = true;
			
			// Check - was anything removed?
			boolean removed = false;
			if (change != null) {
				while (change.next())
					removed = removed | change.wasRemoved();
			}
			
			MultipleSelectionModel<PathObject> treeModel = table.getSelectionModel();
			List<PathObject> selectedItems = treeModel.getSelectedItems();
			
			// If we just have no selected items, and something was removed, then clear the selection
			if (selectedItems.isEmpty() && removed) {
				model.clearSelection();
				return;				
			}
			
			// If we just have one selected item, and also items were removed from the selection, then only select the one item we have
//			if (selectedItems.size() == 1 && removed) {
			if (selectedItems.size() == 1) {
				model.setSelectedObject(selectedItems.get(0), false);
				return;
			}
			
			// If we have multiple selected items, we need to ensure that everything in the tree matches with everything in the selection model
			Set<PathObject> toSelect = new HashSet<>(treeModel.getSelectedItems());
			PathObject primary = treeModel.getSelectedItem();
			model.setSelectedObjects(toSelect, primary);
		} finally {
			synchronizingModelToTable = wasSynchronizingToTree;
		}
	}
	
	
	private void synchronizeTableToSelectionModel(final PathObjectHierarchy hierarchy, final TableView<PathObject> table) {
		if (synchronizingModelToTable || hierarchy == null)
			return;
		boolean ownsChanges = !synchronizingTableToModel;
		try {
			synchronizingTableToModel = true;
			
			PathObjectSelectionModel model = hierarchy.getSelectionModel();
			TableViewSelectionModel<PathObject> tableModel = table.getSelectionModel();
			if (model == null || model.noSelection()) {
				tableModel.clearSelection();
				return;
			}
			
			if (model.singleSelection() || tableModel.getSelectionMode() == SelectionMode.SINGLE) {
				int ind = table.getItems().indexOf(model.getSelectedObject());
				if (ind >= 0) {
					if (tableModel.getSelectedItem() != model.getSelectedObject()) {
						tableModel.clearAndSelect(ind);
						table.scrollTo(ind);
					}
				} else
					tableModel.clearSelection();
				return;
			}
			
			// Loop through all possible selections, and select them if they should be selected (and not if they shouldn't)
			// For performance reasons, we need to do this using arrays - otherwise way too many events may be fired
			int n = table.getItems().size();
			PathObject mainSelectedObject = model.getSelectedObject();
			int mainObjectInd = -1;
			int[] indsToSelect = new int[table.getItems().size()];
			int count = 0;
			for (int i = 0; i < n; i++) {
				PathObject temp = table.getItems().get(i);
				if (temp == mainSelectedObject)
					mainObjectInd = i;
				if (model.isSelected(temp)) {
					indsToSelect[count] = i;
					count++;
				}
			}
			tableModel.clearSelection();
			if (count > 0) {
				int maxCount = 1000;
				if (count > maxCount) {
					logger.warn("Only the first {} items will be selected in the table (out of {} total) - otherwise QuPath can grind to a halt, sorry",
							maxCount, count);
					count = maxCount;
				}
				tableModel.selectIndices(indsToSelect[0], Arrays.copyOfRange(indsToSelect, 1, count));
			}

			// Ensure that the main object is focussed & its node expanded
			if (mainObjectInd >= 0 && model.singleSelection()) {
				tableModel.select(mainObjectInd);
				table.scrollTo(mainObjectInd);
			}
			
		} finally {
			if (ownsChanges)
				synchronizingTableToModel = false;
		}
	}
	

}
