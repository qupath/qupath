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

package qupath.lib.gui.measure.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.controlsfx.control.action.Action;
import org.controlsfx.glyphfont.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.controls.PredicateTextField;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.charts.HistogramDisplay;
import qupath.lib.gui.charts.ScatterPlotDisplay;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.SystemMenuBar;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PathObjectImageViewers;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.WorkflowStep;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Control to show a table to view measurements and properties of {@link PathObject}.
 *
 * @since v0.6.0
 */
public class SummaryMeasurementTable {

    private static final Logger logger = LoggerFactory.getLogger(SummaryMeasurementTable.class);

    private static final String PROPERTY_KEY = "summary.measurement.table.";

    private static final BooleanProperty PROP_USE_REGEX = PathPrefs.createPersistentPreference(
            PROPERTY_KEY + "useRegex", false);

    private static final BooleanProperty PROP_SHOW_TOOLBAR = PathPrefs.createPersistentPreference(
            PROPERTY_KEY + "showToolbar", true);

    private static final BooleanProperty PROP_SHOW_TOOLBAR_TEXT = PathPrefs.createPersistentPreference(
            PROPERTY_KEY + "showToolbarText", true);

    private final BooleanProperty PROP_BIND_VISIBILITY = PathPrefs.createPersistentPreference(
            PROPERTY_KEY + "bindVisibility", false);


    private final KeyCombination centerCode = new KeyCodeCombination(KeyCode.SPACE);

    private final ImageData<BufferedImage> imageData;
    private final PathObjectHierarchy hierarchy;

    private final BooleanProperty useRegexColumnFilter = createPartiallyBoundProperty(PROP_USE_REGEX);

    private final BooleanProperty showThumbnailsProperty = createPartiallyBoundProperty(PathPrefs.showMeasurementTableThumbnailsProperty());
    private final BooleanProperty showObjectIdsProperty = createPartiallyBoundProperty(PathPrefs.showMeasurementTableObjectIDsProperty());

    private final BooleanProperty showToolbar = createPartiallyBoundProperty(PROP_SHOW_TOOLBAR);
    private final BooleanProperty showToolbarText = createPartiallyBoundProperty(PROP_SHOW_TOOLBAR_TEXT);

    private final BooleanProperty bindToOverlayOptions = createPartiallyBoundProperty(PROP_BIND_VISIBILITY);
    private final ObjectBinding<ContentDisplay> toolbarContentDisplayBinding = createToolbarContentDisplayBinding();

    private ObjectBinding<Predicate<PathObject>> overlayVisibilityPredicate;

    private final ObservableMeasurementTableData model = new ObservableMeasurementTableData();

    private final Map<String, Tooltip> tooltips = new ConcurrentHashMap<>();

    private BorderPane pane;

    private QuPathViewer viewer;
    private ViewerTableSynchronizer synchronizer;
    private final PathObjectHierarchyListener listener = this::handleHierarchyChange;

    private TableView<PathObject> table;

    private final SplitPane splitPane = new SplitPane();

    private final TabPane plotTabs = new TabPane();
    private HistogramDisplay histogramDisplay;
    private ScatterPlotDisplay scatterPlotDisplay;

    private final Predicate<PathObject> primaryFilter;

    // Column for displaying thumbnail images
    private TableColumn<PathObject, PathObject> colThumbnails;
    private final double thumbnailPadding = 10.0;

    /**
     * Create a new measurement table.
     * @param imageData the image data that the table should relate to (i.e. source of the objects)
     * @param primaryFilter main filter used to extract objects (e.g. detections, annotations)
     * @see PathObjectFilter
     */
    public SummaryMeasurementTable(ImageData<BufferedImage> imageData,
                                   Predicate<PathObject> primaryFilter) {
        Objects.requireNonNull(imageData);
        Objects.requireNonNull(primaryFilter);
        this.imageData = imageData;
        this.hierarchy = imageData.getHierarchy();
        this.primaryFilter = primaryFilter;
    }

    /**
     * Create a new boolean property that can set the value of an existing property, but is not updated when the existing
     * property changes.
     * <p>
     * This is useful when we want to update a persistent property based on the last user-specified value, but we don't
     * necessarily want to update other tables using related properties (i.e. we might hide a toolbar in one table,
     * but not for previously-opened tables that are still showing).
     *
     * @param prop the existing property
     * @return the new property; it will take its initial value from the existing property
     */
    private static BooleanProperty createPartiallyBoundProperty(BooleanProperty prop) {
        var prop2 = new SimpleBooleanProperty(prop.getValue());
        prop.bind(prop2);
        return prop2;
    }



    private void init() {
        updateObjects();

        findViewer();
        initOverlayVisibilityBinding();
        initTable();

        synchronizer = new ViewerTableSynchronizer(viewer, hierarchy, table);

        initSplitPane();
        initTabPane();

        model.getItems().addListener(this::handleObjectsChanged);

        initActions();

        pane = new BorderPane();
        var centerPane = new BorderPane(splitPane);
        var toolbar = createToolbar();
        centerPane.setTop(toolbar);
        centerPane.topProperty().bind(Bindings.createObjectBinding(
                () -> showToolbar.get() ? toolbar : null
        , showToolbar));

        pane.setCenter(centerPane);
        pane.setTop(createMenuBar());

        // Only listen with the pane is attached to a scene that is showing
        pane.sceneProperty()
                .flatMap(Scene::windowProperty)
                .flatMap(Window::showingProperty)
                .addListener(this::handleVisibilityChanged);

        // Add ability to remove entries from table
        table.setContextMenu(createContextMenu());
    }

    private void initOverlayVisibilityBinding() {
        if (viewer == null)
            return;
        var options = viewer.getOverlayOptions();
        overlayVisibilityPredicate = Bindings.createObjectBinding(() -> {
            if (bindToOverlayOptions.get())
                return (PathObject p) -> !options.isHidden(p);
            else
                return null;
        }, options.selectedClassesProperty(), options.useExactSelectedClassesProperty(),
                options.selectedClassVisibilityModeProperty(),
                bindToOverlayOptions);

        overlayVisibilityPredicate.addListener((v, o, n) -> model.setPredicate(n));
        model.setPredicate(overlayVisibilityPredicate.get());
    }

    private void handleObjectsChanged(ListChangeListener.Change<? extends PathObject> c) {
        histogramDisplay.refreshHistogram();
        scatterPlotDisplay.refreshScatterPlot();
    }

    /**
     * Extract the required objects from the hierarchy & update the model.
     */
    private void updateObjects() {
        Collection<PathObject> list;
        if (primaryFilter instanceof PathObjectFilter f) {
            list = switch (f) {
                case DETECTIONS_ALL -> hierarchy.getDetectionObjects();
                case ANNOTATIONS -> hierarchy.getAnnotationObjects();
                case CELLS -> hierarchy.getCellObjects();
                case TILES -> hierarchy.getTileObjects();
                default -> getAllObjectsFiltered();
            };
        } else {
            list = getAllObjectsFiltered();
        }
        model.setImageData(imageData, list);
    }

    private Collection<PathObject> getAllObjectsFiltered() {
        if (primaryFilter == null)
            return hierarchy.getAllObjects(false);
        else
            return hierarchy.getAllObjects(true)
                    .stream()
                    .filter(primaryFilter)
                    .toList();
    }

    private void findViewer() {
        // Try to find a viewer containing the image data
        var qupath = QuPathGUI.getInstance();
        viewer = qupath == null ? null : qupath.getAllViewers()
                .stream()
                .filter(v -> v.getImageData() == imageData)
                .findFirst()
                .orElse(null);
    }

    private void initTable() {
        // Create the table
        table = new TableView<>();
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // Handle double-click as a way to center on a ROI
        table.setRowFactory(this::createTableRow);
        table.setOnKeyPressed(this::handleTableKeypress);

        // Add a column to display images thumbnails - but only if we have a viewer
        if (viewer != null) {
            colThumbnails = createThumbnailColumn();
            table.getColumns().add(colThumbnails);
        }

        // Set fixed cell size - this can avoid large numbers of non-visible cells being computed
        table.fixedCellSizeProperty().bind(Bindings.createDoubleBinding(() -> {
            if (colThumbnails.isVisible())
                return Math.max(24, colThumbnails.getWidth() + thumbnailPadding);
            else
                return 24d;//-1.0;
        }, colThumbnails.widthProperty(), colThumbnails.visibleProperty()));

        // Add main table columns
        boolean appendIdColumn = false;
        for (String columnName : model.getAllNames()) {
            if (ObservableMeasurementTableData.NAME_OBJECT_ID.equals(columnName))
                appendIdColumn = true;
            else if (model.isNumericMeasurement(columnName)) {
                var col = createNumericTableColumn(columnName);
                table.getColumns().add(col);
            } else {
                var col = createStringTableColumn(columnName);
                table.getColumns().add(col);
            }
        }

        // Add object ID column at the end, since it takes quite a lot of space
        if (appendIdColumn) {
            var colObjectIDs = createStringTableColumn(ObservableMeasurementTableData.NAME_OBJECT_ID);
            colObjectIDs.visibleProperty().bind(showObjectIdsProperty);
            table.getColumns().add(colObjectIDs);
        }

        // Set the PathObjects - need to deal with sorting, since a FilteredList won't handle it directly
        SortedList<PathObject> items = new SortedList<>(model.getItems());
        items.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(items);
    }

    /**
     * Get a tooltip for use in a table cell.
     * Tooltips can be reused (specified in the javadocs).
     * This can have a noticeable performance impact; tooltip initialization for one image was costing
     * more than a second of processing time in one case.
     */
    private Tooltip getTooltip(String text) {
        if (text == null || text.isEmpty())
            return null;
        else
            return tooltips.computeIfAbsent(text, Tooltip::new);
    }

    private TableColumn<PathObject, Number> createNumericTableColumn(String name) {
        var tooltipText = model.getHelpText(name);
        TableColumn<PathObject, Number> col = new TableColumn<>(name);
        col.setCellValueFactory(cellData -> createNumericMeasurement(model, cellData.getValue(), cellData.getTableColumn().getText()));
        col.setCellFactory(column -> new NumericTableCell<>(getTooltip(tooltipText), histogramDisplay));
        return col;
    }

    private TableColumn<PathObject, String> createStringTableColumn(String name) {
        var tooltipText = model.getHelpText(name);
        TableColumn<PathObject, String> col = new TableColumn<>(name);
        col.setCellValueFactory(column -> createStringMeasurement(model, column.getValue(), column.getTableColumn().getText()));
        col.setCellFactory(column -> new BasicTableCell<>(getTooltip(tooltipText)));
        return col;
    }

    private TableColumn<PathObject, PathObject> createThumbnailColumn() {
        var colThumbnails = new TableColumn<PathObject, PathObject>("Thumbnail");
        colThumbnails.setCellValueFactory(val -> new SimpleObjectProperty<>(val.getValue()));
        colThumbnails.visibleProperty().bind(showThumbnailsProperty);
        colThumbnails.setCellFactory(column -> PathObjectImageViewers.createTableCell(
                viewer, imageData.getServer(), true, thumbnailPadding));
        return colThumbnails;
    }

    private Pane createColumnFilterPane() {
        var tfColumnFilter = new PredicateTextField<String>();
        tfColumnFilter.useRegexProperty().bindBidirectional(useRegexColumnFilter);

        var columnFilter = tfColumnFilter.predicateProperty();
        columnFilter.addListener((v, o, n) -> {
            for (TableColumn<?, ?> col : table.getColumns()) {
                if (col == colThumbnails || col.visibleProperty().isBound()) // Retain thumbnails
                    continue;
                var name = col.getText();
                col.setVisible(n.test(name));
            }
        });

        GridPane paneFilter = new GridPane();
        paneFilter.add(new Label("Column filter"), 0, 0);
        paneFilter.add(tfColumnFilter, 1, 0);
        GridPane.setHgrow(tfColumnFilter, Priority.ALWAYS);
        paneFilter.setHgap(5);

        if (primaryFilter == PathObjectFilter.TMA_CORES) {
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
        return paneFilter;
    }

    private Pane createTablePane() {
        BorderPane paneTable = new BorderPane();
        paneTable.setCenter(table);

        var paneBottom = new BorderPane(createColumnFilterPane());
        paneBottom.setRight(createObjectCountPane());
        paneTable.setBottom(paneBottom);
        return paneTable;
    }

    private Pane createObjectCountPane() {
        var label = new Label();
        label.textProperty().bind(Bindings.createStringBinding(this::getObjectCountText, table.getItems()));
        label.setAlignment(Pos.CENTER_RIGHT);
//        label.setPrefWidth(120);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPadding(new Insets(0, 5, 0, 5));
        return new BorderPane(label);
    }

    private String getObjectCountText() {
        int n = table.getItems().size();
        return n == 1 ? "1 object" : n + " objects";
    }

    private void initSplitPane() {
        splitPane.getItems().add(createTablePane());
    }


    private void initTabPane() {
        histogramDisplay = new HistogramDisplay(model, true);
        scatterPlotDisplay = new ScatterPlotDisplay();

        Tab tabHistogram = new Tab("Histogram", histogramDisplay.getPane());
        tabHistogram.setClosable(false);
        plotTabs.getTabs().add(tabHistogram);

        Tab tabScatter = new Tab("Scatter plot", scatterPlotDisplay.getPane());
        tabScatter.setClosable(false);
        plotTabs.getTabs().add(tabScatter);

        plotTabs.getSelectionModel().selectFirst();

        // We want to set the scatterpane only if it is shown
        tabScatter.selectedProperty().addListener((v, o, n) -> {
            if (n)
                scatterPlotDisplay.setModel(model);
        });

        FXUtils.makeTabUndockable(tabHistogram);
        FXUtils.makeTabUndockable(tabScatter);
    }

    private Action actionShowPlots;
    private Action actionCopy;
    private Action actionSave;
    private Action actionThumbnails;
    private Action actionId;
    private Action actionShowToolbar;
    private Action actionToolbarText;
    private Action actionBindVisibility;

    private Action actionSelectAll;
    private Action actionSelectNone;

    private Action createShowPlotsAction() {
        var action = new Action("Show plots");
        action.setGraphic(IconFactory.createNode(FontAwesome.Glyph.BAR_CHART));
        action.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN));
        action.selectedProperty().addListener((v, o, n) -> {
            if (n) {
                splitPane.getItems().add(plotTabs);
            } else {
                splitPane.getItems().remove(plotTabs);
            }
        });
        return action;
    }

    private Action createCopyAction() {
        var action = new Action("Copy", e -> handleCopyButton());
        action.setLongText("Copy the table contents to the system clipboard");
        action.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
        action.setGraphic(IconFactory.createNode(FontAwesome.Glyph.CLIPBOARD));
        action.disabledProperty().bind(Bindings.isEmpty(table.getSelectionModel().getSelectedItems()));
        return action;
    }

    private Action createSaveAction() {
        var action = new Action("Save...", e -> handleSaveButton());
        action.setLongText("Save the table contents");
        action.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        action.setGraphic(IconFactory.createNode(FontAwesome.Glyph.SAVE));
        return action;
    }

    private Action createShowThumbnailsAction() {
        var action = new Action("Show images");
        action.setLongText("Show or hide object thumbnail image column (usually the first column in the table)");
        action.setGraphic(IconFactory.createNode(FontAwesome.Glyph.IMAGE));
        action.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        action.selectedProperty().bindBidirectional(showThumbnailsProperty);
        return action;
    }

    private Action createShowIdAction() {
        var action = new Action("Show object IDs");
        action.setLongText("Show or hide object ID column (usually the last column in the table)");
        action.setGraphic(IconFactory.createFontAwesome('\uf2c2'));
        action.setAccelerator(new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        action.selectedProperty().bindBidirectional(showObjectIdsProperty);
        return action;
    }

    private Action createBindVisibilityAction() {
        var action = new Action("Apply class visibility");
        action.setLongText("Use class visibility settings from the viewer to filter objects for display in the table");
        action.setGraphic(IconFactory.createNode(FontAwesome.Glyph.EYE));
        action.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        action.selectedProperty().bindBidirectional(bindToOverlayOptions);
        return action;
    }

    private Action createToolbarShowAction() {
        var action = new Action("Show toolbar");
        action.setLongText("Show or hide the toolbar");
        action.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN));
        action.selectedProperty().bindBidirectional(showToolbar);
        return action;
    }

    private Action createToolbarTextAction() {
        var action = new Action("Show button text");
        action.setLongText("Show the full text for toolbar buttons (requires more space)");
        action.selectedProperty().bindBidirectional(showToolbarText);
        return action;
    }

    private Action createSelectAllAction() {
        var action = new Action("Select all", e -> table.getSelectionModel().selectAll());
        action.setLongText("Select all rows in the title");
        return action;
    }

    private Action createSelectNoneAction() {
        var action = new Action("Select none", e -> table.getSelectionModel().clearSelection());
        action.setLongText("Deselect all rows in the table");
        return action;
    }

    private void initActions() {
        actionShowPlots = createShowPlotsAction();
        actionCopy = createCopyAction();
        actionSave = createSaveAction();
        actionShowToolbar = createToolbarShowAction();
        actionToolbarText = createToolbarTextAction();

        actionSelectAll = createSelectAllAction();
        actionSelectNone = createSelectNoneAction();
        actionThumbnails = createShowThumbnailsAction();
        actionId = createShowIdAction();

        if (overlayVisibilityPredicate != null) {
            actionBindVisibility = createBindVisibilityAction();
        }
    }

    private ToolBar createToolbar() {

        var btnPlots = ActionTools.createToggleButton(actionShowPlots);
        btnPlots.contentDisplayProperty().bind(toolbarContentDisplayBinding);

        var btnCopy = ActionTools.createButton(actionCopy);
        btnCopy.contentDisplayProperty().bind(toolbarContentDisplayBinding);

        var btnSave = ActionTools.createButton(actionSave);
        btnSave.contentDisplayProperty().bind(toolbarContentDisplayBinding);

        var btnThumbnails = ActionTools.createToggleButton(actionThumbnails);
        btnThumbnails.contentDisplayProperty().bind(toolbarContentDisplayBinding);

        var btnIds = ActionTools.createToggleButton(actionId);
        btnIds.contentDisplayProperty().bind(toolbarContentDisplayBinding);

        var toolbar = new ToolBar();
        toolbar.getItems().setAll(
                btnPlots,
                new Separator(),
                btnSave,
                new Separator(),
                btnCopy,
                new Separator(),
                btnThumbnails,
                btnIds
        );

        // Add visibility binding, if available
        if (actionBindVisibility != null) {
            var btnVisibility = ActionTools.createToggleButton(actionBindVisibility);
            btnVisibility.contentDisplayProperty().bind(toolbarContentDisplayBinding);
            toolbar.getItems().addAll(
                    new Separator(),
                    btnVisibility
            );
        }

        return toolbar;
    }


    private ObjectBinding<ContentDisplay> createToolbarContentDisplayBinding() {
        return Bindings.createObjectBinding(() -> {
            if (showToolbarText.get())
                return ContentDisplay.LEFT; // TODO: Consider using TOP
            else
                return ContentDisplay.GRAPHIC_ONLY;
        }, showToolbarText);
    }


    /**
     * Handle copy request.
     */
    private void handleCopyButton() {
        Set<String> excludeColumns = getExcludedColumns();
        var items = table.getSelectionModel().getSelectedItems();
        if (items.isEmpty())
            items = table.getItems();

        var strings = model.getRowStrings(new ArrayList<>(items),
                PathPrefs.tableDelimiterProperty().get(),
                -1,
                c -> !excludeColumns.contains(c));

        try {
            var content = new ClipboardContent();
            content.putString(String.join(System.lineSeparator(), strings));
            Clipboard.getSystemClipboard().setContent(content);
        } catch (OutOfMemoryError e) {
            logger.error("Error attempting to copy measurements: {}", e.getMessage(), e);
            Dialogs.showErrorMessage("Copy measurements",
                    "Measurement table is too long to copy - please select fewer items");
        }
    }

    private Set<String> getExcludedColumns() {
        Set<String> excludeColumns = new HashSet<>();
        for (TableColumn<?, ?> col : table.getColumns()) {
            if (!col.isVisible())
                excludeColumns.add(col.getText());
        }
        return excludeColumns;
    }

    private boolean hasProject() {
        var qupath = QuPathGUI.getInstance();
        return qupath != null && qupath.getProject() != null;
    }

    /**
     * Handle save request.
      */
    private void handleSaveButton() {
        Set<String> excludeColumns = getExcludedColumns();
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
            String path = !hasProject() ? fileOutput.toURI().getPath() : fileOutput.getParentFile().toURI().getPath();
            if (primaryFilter == PathObjectFilter.TMA_CORES) {
                step = new DefaultScriptableWorkflowStep("Save TMA measurements",
                        String.format("saveTMAMeasurements('%s'%s)", path, includeColumns)
                );
            }
            else if (primaryFilter == PathObjectFilter.ANNOTATIONS) {
                step = new DefaultScriptableWorkflowStep("Save annotation measurements",
                        String.format("saveAnnotationMeasurements('%s'%s)", path, includeColumns)
                );
            } else if (primaryFilter == PathObjectFilter.DETECTIONS_ALL) {
                step = new DefaultScriptableWorkflowStep("Save detection measurements",
                        String.format("saveDetectionMeasurements('%s'%s)", path, includeColumns)
                );
            } else if (primaryFilter == PathObjectFilter.CELLS) {
                step = new DefaultScriptableWorkflowStep("Save cell measurements",
                        String.format("saveCellMeasurements('%s'%s)", path, includeColumns)
                );
            } else if (primaryFilter == PathObjectFilter.TILES) {
                step = new DefaultScriptableWorkflowStep("Save tile measurements",
                        String.format("saveTileMeasurements('%s'%s)", path, includeColumns)
                );
            }  else {
                // TODO: Is there any way to log for an arbitrary filter?
                logger.debug("Can't log measurement export for filter {}", primaryFilter);
                return;
            }
            imageData.getHistoryWorkflow().addStep(step);
        }
    }


    // Minimal context menu
    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getItems().setAll(
                ActionTools.createMenuItem(actionSelectAll),
                ActionTools.createMenuItem(actionSelectNone)
        );
        return menu;
    }


    private void handleHierarchyChange(PathObjectHierarchyEvent event) {
        if (event.isChanging())
            return;

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> handleHierarchyChange(event));
            return;
        }

        // TODO: Consider if this can be optimized to avoid rebuilding the full table so often
        if (event.isStructureChangeEvent()) {
            updateObjects();
        } else {
            table.refresh();
            histogramDisplay.refreshHistogram();
            scatterPlotDisplay.refreshScatterPlot();
        }
    }

    private TableRow<PathObject> createTableRow(TableView<PathObject> table) {
        var row = new TableRow<PathObject>();
        row.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                maybeCenterROI(row.getItem());
            }
        });
        return row;
    }

    private void handleTableKeypress(KeyEvent e) {
        if (centerCode.match(e)) {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected != null)
                maybeCenterROI(selected);
        }
    }


    public Pane getPane() {
        if (table == null)
            init();
        return pane;
    }


    private void handleVisibilityChanged(ObservableValue<? extends Boolean> obs, Boolean oldValue, Boolean newValue) {
        if (newValue) {
            logger.debug("Attaching listeners");
            imageData.getHierarchy().addListener(listener);
            if (synchronizer != null)
                synchronizer.attachListeners();
        } else {
            logger.debug("Removing listeners");
            imageData.getHierarchy().removeListener(listener);
            if (synchronizer != null)
                synchronizer.removeListeners();
        }
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
        if (pathObject == null || viewer == null || viewer.getHierarchy() != hierarchy)
            return;
        var roi = pathObject.getROI();
        if (roi != null) {
            // v0.6.0 - centre the ROI only if it isn't already visible
            if (!viewer.getDisplayedRegionShape()
                    .contains(roi.getCentroidX(), roi.getCentroidY())) {
                viewer.centerROI(roi);
            }
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
            logger.error("Error writing file to {}", fileOutput, e);
        }
        return false;
    }


    private MenuBar createMenuBar() {
        var menuFile = MenuTools.createMenu("File",
                actionSave
        );

        var menuEdit = MenuTools.createMenu("Edit",
                actionCopy
        );

        var menuView = MenuTools.createMenu("View",
                ActionTools.createCheckMenuItem(actionShowPlots),
                null,
                ActionTools.createCheckMenuItem(actionShowToolbar),
                ActionTools.createCheckMenuItem(actionToolbarText)
        );

        var menuTable = MenuTools.createMenu("Table",
                ActionTools.createMenuItem(actionSelectAll),
                ActionTools.createMenuItem(actionSelectNone),
                null,
                ActionTools.createCheckMenuItem(actionThumbnails),
                ActionTools.createCheckMenuItem(actionId)
        );

        if (actionBindVisibility != null) {
            MenuTools.addMenuItems(
                    menuTable,
                    null,
                    ActionTools.createCheckMenuItem(actionBindVisibility)
            );
        }

        var menubar = new MenuBar(
                menuFile,
                menuEdit,
                menuView,
                menuTable
        );
        SystemMenuBar.manageChildMenuBar(menubar);
        return menubar;
    }


}
