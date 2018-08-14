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

package qupath.lib.gui.commands;

import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableView.TableViewSelectionModel;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.GUIActions;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.PaintingToolsFX;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.gui.models.HistogramDisplay;
import qupath.lib.gui.models.ObservableMeasurementTableData;
import qupath.lib.gui.models.PathTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.stores.ImageRegionStore;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;


/**
 * Show a summary table for an object of a particular type (annotation, detection, TMA core...)
 * 
 * @author Pete Bankhead
 */
public class SummaryMeasurementTableCommand implements PathCommand {

	final private static Logger logger = LoggerFactory.getLogger(SummaryMeasurementTableCommand.class);

	private QuPathGUI qupath;
	private Class<? extends PathObject> type;
	
	/**
	 * Max thumbnails to store in cache
	 */
	private static int MAX_CACHE_SIZE = 200;
	
	/**
	 * Cache for storing image thumbnails
	 */
	private static Map<ROI, Image> cache = new LinkedHashMap<ROI, Image>() {
		private static final long serialVersionUID = 1L;
		@Override
		protected synchronized boolean removeEldestEntry(Map.Entry<ROI, Image> eldest) {
			return size() > MAX_CACHE_SIZE;
		}

	};
	
	/**
	 * Max thumbnail size
	 */
	private static double maxDimForTMACore = Runtime.getRuntime().maxMemory() > 1024L*1024L*1024L*4L ? 500 : 250;

	public SummaryMeasurementTableCommand(final QuPathGUI qupath, final Class<? extends PathObject> type) {
		super();
		this.qupath = qupath;
		this.type = type;
	}

	@Override
	public void run() {
		final ImageData<BufferedImage> imageData = qupath.getViewer().getImageData();
		if (imageData == null)
			return;

		final PathObjectHierarchy hierarchy = imageData.getHierarchy();

		ObservableMeasurementTableData model = new ObservableMeasurementTableData();
		model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));

		SplitPane splitPane = new SplitPane();
		HistogramDisplay histogramDisplay = new HistogramDisplay(model);

		//		table.setTableMenuButtonVisible(true);
		TableView<PathObject> table = new TableView<>();
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		table.getSelectionModel().getSelectedItems().addListener(new ListChangeListener<PathObject>() {
			@Override
			public void onChanged(ListChangeListener.Change<? extends PathObject> c) {
				synchronizeSelectionModelToTable(hierarchy, c, table);
			}
		});
		String displayedName = imageData.getServer().getDisplayedImageName();
		String name;
		if (type == null)
			name = "Results " + displayedName;
		else
			name = PathObjectTools.getSuitableName(type, false) + " results - " + displayedName;


		// Create columns according to the table model
//		for (int i = 0; i < model.getColumnCount(); i++) {
//			// Add string column
//			if (model.getColumnClass(i).equals(String.class)) {
//				TableColumn<PathObject, String> col = null;
//				col = new TableColumn<>(model.getColumnName(i));
//				col.setCellValueFactory(new Callback<CellDataFeatures<PathObject, String>, ObservableValue<String>>() {
//					public ObservableValue<String> call(CellDataFeatures<PathObject, String> val) {
//						return new SimpleStringProperty(val.getValue().getDisplayedName());
//					}
//				});
//				col.setCellFactory(column -> new BasicTableCell<String>());
//				table.getColumns().add(col);
//			}
//		}
		
		boolean tmaCoreList = TMACoreObject.class.isAssignableFrom(type);
		
		// Add TMA core columns, if suitable
		if (tmaCoreList) {
			TableColumn<PathObject, ROI> col = new TableColumn<>("Image");
			col.setCellValueFactory(val -> new SimpleObjectProperty<>(val.getValue().getROI()));
			double maxWidth = maxDimForTMACore;
			double padding = 10;
			col.setCellFactory(column -> new TMACoreTableCell(table, qupath.getImageRegionStore(), imageData.getServer(), maxWidth, padding));
			col.widthProperty().addListener((v, o, n) -> table.refresh());
			col.setMaxWidth(maxWidth + padding*2);
			table.getColumns().add(col);
			
			// While here, make sure we have fewer bins - don't usually have all that many cores
			histogramDisplay.setNumBins(10);
		}
		
//		// TODO: Create object columns
//		TableColumn<PathObject, String> colObject = new TableColumn<>("Object");
//		colObject.setCellValueFactory(column -> new SimpleStringProperty(column.getValue().getDisplayedName()));
//		colObject.setCellFactory(column -> new BasicTableCell<>());
//		table.getColumns().add(colObject);
//		
//		if (!tmaCoreList) {
//			TableColumn<PathObject, String> colClass = new TableColumn<>("Class");
//			colClass.setCellValueFactory(column -> new SimpleStringProperty(column.getValue().getPathClass() == null ? "-" : column.getValue().getPathClass().toString()));
//			colClass.setCellFactory(column -> new BasicTableCell<>());
//			table.getColumns().add(colClass);
//		}
		
//		// If we have annotations, include shape
//		if (PathAnnotationObject.class.isAssignableFrom(type)) {
//			TableColumn<PathObject, String> colClass = new TableColumn<>("ROI");
//			colClass.setCellValueFactory(column -> new SimpleStringProperty(column.getValue().getROI() == null ? "-" : column.getValue().getROI().getROIType()));
//			colClass.setCellFactory(column -> new BasicTableCell<>());
//			table.getColumns().add(colClass);
//		}
			
			
		// Create numeric columns
		for (String columnName : model.getAllNames()) {
			// Add column
			if (model.isStringMeasurement(columnName)) {
				TableColumn<PathObject, String> col = new TableColumn<>(columnName);
				col.setCellValueFactory(column -> model.createStringMeasurement(column.getValue(), column.getTableColumn().getText()));
				col.setCellFactory(column -> new BasicTableCell<>());
				table.getColumns().add(col);			
			} else {
				TableColumn<PathObject, Number> col = new TableColumn<>(columnName);
				col.setCellValueFactory(column -> model.createNumericMeasurement(column.getValue(), column.getTableColumn().getText()));
				col.setCellFactory(column -> new NumericTableCell<PathObject>(histogramDisplay));
				table.getColumns().add(col);			
			}
		}


		// Set the PathObjects - need to deal with sorting, since a FilteredList won't handle it directly
		SortedList<PathObject> items = new SortedList<>(model.getEntries());
		items.comparatorProperty().bind(table.comparatorProperty());
		table.setItems(items);




		List<ButtonBase> buttons = new ArrayList<>();
		
		ToggleButton btnHistogram = new ToggleButton("Show histograms");
		btnHistogram.selectedProperty().addListener((v, o, n) -> {
			if (n) {
				Pane paneHistograms = histogramDisplay.getPane();
				splitPane.getItems().add(paneHistograms);
			} else if (histogramDisplay != null)
				splitPane.getItems().remove(histogramDisplay.getPane());
		});
		buttons.add(btnHistogram);

//		Button btnScatterplot = new Button("Show scatterplots");
//		btnScatterplot.setOnAction(e -> {
//			SwingUtilities.invokeLater(() -> {
//				JDialog dialog = new ScatterplotDisplay(null, "Scatterplots: " + displayedName, model).getDialog();
//				dialog.setLocationRelativeTo(null);
//				dialog.setVisible(true);
//			});
//		});
//		buttons.add(btnScatterplot);
		
		
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
							String.format("saveAnnotationMeasurements('%s\'%s)", path, includeColumns)
							);
				} else if (type == PathDetectionObject.class) {
					step = new DefaultScriptableWorkflowStep("Save detection measurements",
							String.format("saveDetectionMeasurements('%s'%s)", path, includeColumns)
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
		frame.initOwner(qupath.getStage());
		frame.setTitle(name);


		BorderPane paneTable = new BorderPane();
		paneTable.setCenter(table);
		// Add text field to filter visible columns
		TextField tfColumnFilter = new TextField();
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
		StringProperty columnFilter = tfColumnFilter.textProperty();
		columnFilter.addListener((v, o, n) -> {
			String val = n.toLowerCase().trim();
			if (val.isEmpty()) {
				for (TableColumn<?, ?> col : table.getColumns()) {
					if (!col.isVisible())
						col.setVisible(true);
				}
				return;
			}
			for (TableColumn<?, ?> col : table.getColumns()) {
				col.setVisible(col.getText().toLowerCase().contains(val));
			}
		});


		BorderPane pane = new BorderPane();
		//		pane.setCenter(table);
		splitPane.getItems().add(paneTable);
		pane.setCenter(splitPane);
		GridPane panelButtons = PanelToolsFX.createColumnGridControls(buttons.toArray(new ButtonBase[0]));
		pane.setBottom(panelButtons);

		
		PathObjectHierarchyListener listener = new PathObjectHierarchyListener() {

			@Override
			public void hierarchyChanged(PathObjectHierarchyEvent event) {
				if (!Platform.isFxApplicationThread()) {
					Platform.runLater(() -> hierarchyChanged(event));
					return;
				}
				model.refreshEntries();
				table.refresh();
				if (histogramDisplay != null)
					histogramDisplay.refreshHistogram();
			}
			
		};
		
		
		QuPathViewer viewer = qupath.getViewer();
		TableViewerListener tableViewerListener = new TableViewerListener(viewer, table);

		frame.setOnShowing(e -> {
			hierarchy.addPathObjectListener(listener);
			viewer.addViewerListener(tableViewerListener);
		});
		frame.setOnHiding(e -> {
			hierarchy.removePathObjectListener(listener);
			viewer.removeViewerListener(tableViewerListener);
		});

		Scene scene = new Scene(pane, 600, 500);
		frame.setScene(scene);
		frame.show();


		// I accept this is a terrible hack... I would greatly appreciate someone telling me the proper way to get the accelerator keys to work
		final Action actionShowTMAction = qupath.getAction(GUIActions.SHOW_TMA_GRID);
		final Action actionShowAnnotations = qupath.getAction(GUIActions.SHOW_ANNOTATIONS);
		final Action actionShowObjects = qupath.getAction(GUIActions.SHOW_OBJECTS);
		final Action actionFillObjects = qupath.getAction(GUIActions.FILL_OBJECTS);
		final KeyCombination showTMAKeyStroke = actionShowTMAction.getAccelerator();
		final KeyCombination showAnnotationsKeystroke = actionShowAnnotations.getAccelerator();
		final KeyCombination showObjectsKeystroke = actionShowObjects.getAccelerator();
		final KeyCombination fillObjectsKeystroke = actionFillObjects.getAccelerator();
		scene.setOnKeyPressed(e -> {
			Action action = null;
			if (showObjectsKeystroke.match(e))
				action = actionShowObjects;
			else if (fillObjectsKeystroke.match(e))
				action = actionFillObjects;
			else if (showAnnotationsKeystroke.match(e))
				action = actionShowAnnotations;
			else if (showTMAKeyStroke.match(e))
				action = actionShowTMAction;

			if (action != null) {
				action.setSelected(!action.isSelected());
			}

		});

		
		
		
		// Add ability to remove entries from table
		ContextMenu menu = new ContextMenu();
		Menu menuLimitClasses = new Menu("Show classes");
		menu.setOnShowing(e -> {
			Set<PathClass> representedClasses = model.getBackingListEntries().stream().map(p -> p.getPathClass() == null ? null : p.getPathClass().getBaseClass()).collect(Collectors.toCollection(() -> new HashSet<>()));
			representedClasses.remove(null);
			if (representedClasses.isEmpty()) {
				menuLimitClasses.setVisible(false);
			}
			else {
				menuLimitClasses.setVisible(true);
			}
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
//		miLimitClasses.setOnAction(e -> {
//			List<PathObject> selected = table.getSelectionModel().getSelectedItems();
//			table.getSelectionModel().clearSelection();
//			model.setPredicate(p -> p.getPathClass() != null && p.getPathClass().getBaseClass() == PathClassFactory.getDefaultPathClass(PathClasses.TUMOR));
//			model.getEntries().removeAll(selected);
//			table.getItems().removeAll(selected);
//			table.refresh();
//			histogramDisplay.refreshHistogram();
//		});
		
		if (type != TMACoreObject.class) {
			menu.getItems().add(menuLimitClasses);
			table.setContextMenu(menu);
		}

	}




	class TMACoreTableCell extends TableCell<PathObject, ROI> {

		private TableView<?> table;
		private ImageRegionStore<BufferedImage> regionStore;
		private ImageServer<BufferedImage> server;
		private Canvas canvas = new Canvas();
		private double preferredSize = 100;
		private double maxDim;
		private double padding;

		TMACoreTableCell(final TableView<?> table, final ImageRegionStore<BufferedImage> regionStore, final ImageServer<BufferedImage> server, final double maxDim, final double padding) {
			this.table = table;
			this.server = server;
			this.maxDim = maxDim;
			this.padding = padding;
			this.regionStore = regionStore;
			canvas.setWidth(preferredSize);
			canvas.setHeight(preferredSize);
			canvas.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
			canvas.heightProperty().bind(canvas.widthProperty());
		}


		@Override
		protected void updateItem(ROI roi, boolean empty) {
			super.updateItem(roi, empty);
			if (empty) {
				setText(null);
				setGraphic(null);
				return;
			}
			canvas.setWidth(getTableColumn().getWidth()-padding*2);
			setGraphic(canvas);
			this.setContentDisplay(ContentDisplay.CENTER);
			this.setAlignment(Pos.CENTER);
			canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
			try {
				if (roi == null) {
					setText(null);
					return;
				}
				
				Image image = cache.get(roi);
				if (image != null) {
					PaintingToolsFX.paintImage(canvas, image);
					return;
				}
				qupath.submitShortTask(() -> {
					double downsample = Math.max(roi.getBoundsWidth(), roi.getBoundsHeight()) / maxDim;
					// TODO: Put requests into a background thread!
					RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, roi);
					BufferedImage img = regionStore.getImage(server, request, 100, true);
					if (img == null)
						return;
					Image imageNew = SwingFXUtils.toFXImage(img, null);
					if (imageNew != null) {
						cache.put(roi, imageNew);
						Platform.runLater(() -> table.refresh());
					}
				});
			} catch (Exception e) {
				logger.error("Problem reading thumbnail for core {}: {}", roi, e);
//				setGraphic(null);
			}
		}

	}



	public static class BasicTableCell<S, T> extends TableCell<S, T> {

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



	public static class NumericTableCell<T> extends TableCell<T, Number> {

		private HistogramDisplay histogramDisplay;

		public NumericTableCell(final HistogramDisplay histogramDisplay) {
			this.histogramDisplay = histogramDisplay;
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
					if (e.getClickCount() > 1 && histogramDisplay != null) {
						histogramDisplay.showHistogram(getTableColumn().getText());
						//	            		showChart(column);
					}
				});

				//			                setTooltip(new Tooltip(df6.format(item))); // Performance issue?
			}
		}

	}






	class TableViewerListener implements QuPathViewerListener {

		private TableView<PathObject> table;
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
//			if (this.viewer != null || table.getSelectionModel().getSelectedItem() == pathObjectSelected || !table.getItems().contains(pathObjectSelected))
//				return;
			
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(() -> selectedObjectChanged(viewer, pathObjectSelected));
				return;
			}
			synchronizeTableToSelectionModel(viewer.getHierarchy(), table);

//			Platform.runLater(() -> {
//				if (pathObjectSelected == null)
//					table.getSelectionModel().clearSelection();
//				else {
//					table.getSelectionModel().select(pathObjectSelected);
//					// Scroll to the object if it is present in the table
//					//					if (table.getSelectionModel().getSelectedItem() == pathObjectSelected)
//					table.scrollTo(pathObjectSelected);
//				}
//			});
		}

		@Override
		public void viewerClosed(QuPathViewer viewer) {
			viewer.removeViewerListener(this);
			this.viewer = null; // Remove reference
		}

	}



	//	public static String getTableString(final JTable table, final String delim) {
	//		return getTableModelString(table.getModel(), delim);
	//	}


	public static <T> String getTableModelString(final PathTableData<T> model, final String delim, Collection<String> excludeColumns) {
		StringBuilder sb = new StringBuilder();
//		// Object name column
//		sb.append("Object").append(delim);
		// TODO: Add object class & any annotation ROI shapes!
		// Whatever numeric columns we have
		
		List<String> names = new ArrayList<>(model.getAllNames());
		names.removeAll(excludeColumns);
		
		int nColumns = names.size();
		for (int col = 0; col < nColumns; col++) {
			sb.append(names.get(col));
			if (col < nColumns - 1)
				sb.append(delim);
		}
		sb.append(System.lineSeparator());
		
		for (T object : model.getEntries()) {
//			// TODO: Remove PathObject-specific addition!!!!!
//			if (object instanceof PathObject) {
//				PathObject pathObject = (PathObject)object;
//				sb.append(pathObject.getDisplayedName()).append(delim);
//			}
			for (int col = 0; col < nColumns; col++) {
				String val = model.getStringValue(object, names.get(col));
				if (val != null)
					sb.append(val);
//				double value = model.getNumericValue(object, model.getAllNames().get(col));
//				if (Double.isNaN(value))
//					sb.append("-");
//				else
//					sb.append(GeneralTools.getFormatter(4).format(value));
				if (col < nColumns - 1)
					sb.append(delim);
			}
			sb.append(System.lineSeparator());
		}
		return sb.toString();
	}


	public static void copyTableContentsToClipboard(final PathTableData<?> model, Collection<String> excludeColumns) {
		if (model == null) {
			logger.warn("No table available to copy!");
			return;
		}
		String string = getTableModelString(model, PathPrefs.getTableDelimiter(), excludeColumns);
		Clipboard clipboard = Clipboard.getSystemClipboard();
		ClipboardContent content = new ClipboardContent();
		content.putString(string);
		clipboard.setContent(content);
	}

	
	
	private static File promptForOutputFile() {
		String ext = ",".equals(PathPrefs.getTableDelimiter()) ? "csv" : "txt";
		return QuPathGUI.getSharedDialogHelper().promptToSaveFile(null, null, null, "Results data", ext);
	}
	

	public static boolean saveTableModel(final PathTableData<?> tableModel, File fileOutput, Collection<String> excludeColumns) {
		if (fileOutput == null) {
			fileOutput = promptForOutputFile();
			if (fileOutput == null)
				return false;
		}
		try {
			PrintWriter writer = new PrintWriter(fileOutput);
			writer.println(getTableModelString(tableModel, PathPrefs.getTableDelimiter(), excludeColumns));
			writer.close();
			return true;
		} catch (FileNotFoundException e) {
			logger.error("File {} not found!", fileOutput);
		}
		return false;
	}
	
	
	private boolean synchronizingTableToModel = false;
	private boolean synchronizingModelToTable = false;
	
	private void synchronizeSelectionModelToTable(final PathObjectHierarchy hierarchy, final ListChangeListener.Change<? extends PathObject> change, final TableView<PathObject> table) {
		if (synchronizingTableToModel || hierarchy == null)
			return;
		boolean ownsChanges = !synchronizingModelToTable;
		try {
//			System.out.println(change);
			synchronizingModelToTable = true;
			PathObjectSelectionModel model = hierarchy.getSelectionModel();
			if (model == null) {
				return;
			}
			
			// Check - was anything removed?
			boolean removed = false;
			while (change.next())
				removed = removed | change.wasRemoved();
			
			TableViewSelectionModel<PathObject> tableModel = table.getSelectionModel();
			List<PathObject> selectedItems = tableModel.getSelectedItems();
			// If we just have no selected items, and something was removed, then clear the selection
			if (selectedItems.isEmpty() && removed) {
				model.clearSelection();
				return;				
			}
			// If we just have one selected item, and also items were removed from the selection, then only select the one item we have
			if (selectedItems.size() == 1 && removed) {
				model.setSelectedObject(selectedItems.get(0), false);
				return;
			}
			// If we have multiple selected items, we need to ensure that everything in the tree matches with everything in the selection model
			Set<PathObject> currentSelections = model.getSelectedObjects();
			Set<PathObject> toDeselect = new HashSet<>();
			Set<PathObject> toSelect = new HashSet<>();
			int n = table.getItems().size();
			for (int i = 0; i < n; i++) {
				PathObject temp = table.getItems().get(i);
				if (tableModel.isSelected(i)) {
					if (!currentSelections.contains(temp))
						toSelect.add(temp);
				} else {
					if (currentSelections.contains(temp))
						toDeselect.add(temp);
				}
			}
			model.deselectObjects(toDeselect);
			model.selectObjects(toSelect);
			
			// Ensure that we have the main selected object
			PathObject mainSelection = table.getFocusModel().getFocusedItem();
			if (mainSelection != null && model.isSelected(mainSelection))
				model.setSelectedObject(mainSelection, true);
			
		} finally {
			if (ownsChanges)
				synchronizingModelToTable = false;
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
			
			if (model.singleSelection()) {
				int ind = table.getItems().indexOf(model.getSelectedObject());
				if (ind >= 0) {
					tableModel.clearAndSelect(ind);
					table.scrollTo(ind);
				}
				else
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
			if (count > 0)
				tableModel.selectIndices(indsToSelect[0], Arrays.copyOfRange(indsToSelect, 1, count));
			
//			for (int i = 0; i < n; i++) {
//				PathObject temp = table.getItems().get(i);
//				if (temp == mainSelectedObject)
//					mainObjectInd = i;
//				if (model.isSelected(temp)) {
//					// Only select if necessary, or if this is the main selected object
//					if (!tableModel.isSelected(i))
//						tableModel.select(i);
//				}
//				else
//					tableModel.clearSelection(i);
//			}
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