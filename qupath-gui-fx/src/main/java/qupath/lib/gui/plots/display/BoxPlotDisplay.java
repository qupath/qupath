package qupath.lib.gui.plots.display;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.SearchableComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.plots.SnapshotTools;
import qupath.lib.gui.plots.builders.Charts;
import qupath.lib.gui.plots.charts.BoxplotChart;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

// todo very heavily copied from scatterplotdisplay... needs to be refactored along with histogramdisplay
// todo create abstractplotdisplay class?
/**
 * A wrapper around a box plot for displaying PathObjects.
 */
public class BoxPlotDisplay<T extends PathObject> implements PlotDisplay<T> {

    private static final Logger logger = LoggerFactory.getLogger(BoxPlotDisplay.class);

    private final BoxplotChart<String, Number> boxplot;
    private final SearchableComboBox<String> comboNameY = new SearchableComboBox<>();
    private final CheckComboBox<PathClass> comboPathClasses = new CheckComboBox<>();
    private final DoubleProperty pointRadius = new SimpleDoubleProperty(2);
    private final DoubleProperty pointOpacity = new SimpleDoubleProperty(1);
    private final BooleanProperty showAxes = new SimpleBooleanProperty(true);
    private final BooleanProperty showGrid = new SimpleBooleanProperty(true);
    private final BooleanProperty showAllPoints = new SimpleBooleanProperty(false);
    private final BooleanProperty baseClassOnly = new SimpleBooleanProperty(true);

    private final BorderPane pane = new BorderPane();
    private final ObjectProperty<PathTableData<T>> model = new SimpleObjectProperty<>();
    private boolean isUpdating = false;

    /**
     * Create a boxplot display with the specified model
     * @param model the model
     */
    public BoxPlotDisplay(PathTableData<T> model) {
        this();
        this.model.set(model);
    }

    /**
     * Create a boxplot from a table of PathObject measurements.
     */
    public BoxPlotDisplay() {
        this.model.addListener(this::handleModelChange);
        BorderPane panelMain = new BorderPane();

        boxplot = Charts.boxPlot()
                .useCanvas(true)
                .viewer(QuPathGUI.getInstance().getViewer())
                .build();

        var popup = new ContextMenu();
        var miCopy = new MenuItem(QuPathResources.getString("Charts.ScatterPlotDisplay.copyToClipboard"));
        miCopy.setOnAction(_ -> SnapshotTools.copyScaledSnapshotToClipboard(boxplot, 4));
        popup.getItems().add(miCopy);
        boxplot.setOnContextMenuRequested(e -> popup.show(
                boxplot.getScene().getWindow(), e.getScreenX(), e.getScreenY()));

        panelMain.setCenter(boxplot);

        initProperties();

        baseClassOnly.addListener(_ -> requestReplot());
        comboNameY.getSelectionModel().selectedItemProperty().addListener(_ -> requestReplot());
        comboPathClasses.getItems().addListener((InvalidationListener) _ -> requestReplot());
        comboPathClasses.getItems().addListener((InvalidationListener) _ -> resetTitle());
        comboPathClasses.getCheckModel().getCheckedItems().addListener((InvalidationListener) _ -> resetTitle());
        comboPathClasses.getCheckModel().getCheckedItems().addListener((ListChangeListener<PathClass>) _ -> requestReplot());
        FXUtils.installSelectAllOrNoneMenu(comboPathClasses);

        var topPane = new GridPane();
        topPane.setHgap(5);

        var labelPC = new Label(QuPathResources.getString("Charts.BoxPlotDisplay.pathClasses"));
        comboPathClasses.setTooltip(new Tooltip(QuPathResources.getString("Charts.BoxPlotDisplay.pathClassesDescription")));
        comboPathClasses.setMaxWidth(Double.MAX_VALUE);
        labelPC.setLabelFor(comboPathClasses);
        labelPC.setMinWidth(Label.USE_COMPUTED_SIZE);
        topPane.addRow(1, labelPC, comboPathClasses);

        var labelY = new Label(QuPathResources.getString("Charts.ScatterPlotDisplay.y"));
        comboNameY.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.yDescription")));
        comboNameY.setMaxWidth(Double.MAX_VALUE);
        labelY.setLabelFor(comboNameY);
        labelY.setMinWidth(Label.USE_COMPUTED_SIZE);
        topPane.addRow(2, labelY, comboNameY);

        ColumnConstraints colLabel = new ColumnConstraints();
        ColumnConstraints colCombo = new ColumnConstraints();
        colLabel.setMinWidth(ColumnConstraints.CONSTRAIN_TO_PREF);
        colLabel.setHgrow(Priority.NEVER);
        colCombo.setHgrow(Priority.ALWAYS);
        topPane.getColumnConstraints().setAll(colLabel, colCombo);

        pane.setTop(topPane);
        comboNameY.prefWidthProperty().bind(topPane.prefWidthProperty());
        comboPathClasses.prefWidthProperty().bind(topPane.prefWidthProperty());

        panelMain.setMinSize(200, 200);
        panelMain.setPrefSize(400, 300);

        pane.setCenter(panelMain);
        pane.setBottom(createMainOptionsPane());

        pane.setPadding(new Insets(10, 10, 10, 10));
    }

    private void resetTitle() {
        comboPathClasses.setTitle(String.join(", ",
                comboPathClasses.getCheckModel()
                        .getCheckedItems()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(PathClass::toString)
                        .toList())
        );
    }

    @Override
    public ObjectProperty<PathTableData<T>> modelProperty() {
        return model;
    }

    @Override
    public String getName() {
        return QuPathResources.getString("Measure.MeasurementTable.boxPlot");
    }

    @Override
    public Pane getPane() {
        return pane;
    }

    @Override
    public void requestReplot() {
        var model = this.model.get();
        if (model == null || isUpdating) {
            return;
        }
        // Awkward - but SearchableComboBox tends to set values temporarily to null
        var y = comboNameY.getValue();
        if (y != null) {
            var items = model.getItems();
            var classes = new HashSet<>(comboPathClasses.getCheckModel().getCheckedItems());
            // only apply filter if one or more classes (including null class) are actually selected
            Predicate<T> objectFilter;
            Function<T, PathClass> classExtractor;
            if (baseClassOnly.get()) {
                objectFilter = po -> classes.stream().anyMatch(pc -> po.getPathClass().isDerivedFrom(pc));
                classExtractor = po -> po.getPathClass().getBaseClass();
            } else {
                objectFilter = po -> classes.contains(po.getPathClass());
                classExtractor = PathObject::getPathClass;
            }
            setDataFromTable(items, model, objectFilter, classExtractor, y);
        }
    }

    @Override
    public void setModel(PathTableData<T> model) {
        this.model.set(model);
    }

    @Override
    public PathTableData<T> getModel() {
        return model.get();
    }

    @Override
    public XYChart<String, Number> getChart() {
        return boxplot;
    }

    @Override
    public void plotColumns(String... columns) {
        if (columns.length != 1) {
            logger.debug("Only one column is valid for boxplot, supplied {}", columns.length);
            return;
        }
        if (comboNameY.getItems().contains(columns[0])) {
            comboNameY.getSelectionModel().select(columns[0]);
        }
        requestReplot();
    }

    /**
     * Set the data to display in the plot from a table model.
     * <p>
     * This calls {@link setData(Collection, Predicate, Function, Function)} in addition to setting the x and y labels.
     *
     * @param pathObjects the objects to display
     * @param model the table model containing the measurements
     * @param yMeasurement the column to use for y values
     */
    private void setDataFromTable(
            Collection<T> pathObjects,
            PathTableData<T> model,
            Predicate<T> objectFilter,
            Function<T, PathClass> classExtractor,
            String yMeasurement) {

        setData(pathObjects,
                objectFilter,
                classExtractor,
                p -> model.getNumericValue(p, yMeasurement));
        boxplot.getYAxis().setLabel(yMeasurement);
    }

    /**
     * Set the data to display in the plot.
     * @param objects the objects to display
     * @param yFun a function to extract the y value to plot
     */
    private void setData(
            Collection<T> objects,
            Predicate<T> objectFilter,
            Function<T, PathClass> classExtractor,
            Function<T, Number> yFun) {

        // todo if only base classes...?
        // find the represented classes & sort them

        // input: list of objects
        // filter list to contain
        // extract pathclass or base class
        // output: list of series for each pathclass

        var newData = objects
                .stream()
                .filter(objectFilter)
                .map(classExtractor)
                .distinct()
                .sorted(Comparator.nullsFirst(PathClass::compareTo))
                .map(pc -> {
                    // create a series for each class so they appear nicely in the legend
                    PathClass finalPc = pc == null ? PathClass.NULL_CLASS : pc;
                    return new XYChart.Series<>(
                            finalPc.toString(),
                            FXCollections.observableArrayList(objects.stream()
                                    .filter(po -> classExtractor.apply(po).equals(finalPc))
                                    .map(po -> new XYChart.Data<>(
                                            finalPc.toString(),
                                            yFun.apply(po), po))
                                    .toList())
                    );
                })
                .toList();

        boxplot.getData().setAll(newData);
    }

    private void handleModelChange(ObservableValue<? extends PathTableData<T>> observable,
                                   PathTableData<T> oldValue, PathTableData<T> newValue) {
        isUpdating = true;
        if (newValue != null) {
            updateForModel(newValue);
        }
        isUpdating = false;
        requestReplot();
    }

    private Region createMainOptionsPane() {
        return createDisplayOptionsPane();
    }

    private void updateForModel(PathTableData<T> newValue) {
        // todo derived classes or classifications
        updatePathClasses(newValue);
        comboNameY.getItems().setAll(newValue.getMeasurementNames());

        // Try to select the first column that isn't for 'centroids'...
        // but, always select something
        String selectColumnY = null;
        String defaultY = null;
        for (String name : newValue.getMeasurementNames()) {
            if (!name.toLowerCase().startsWith("centroid")) {
                if (selectColumnY == null) {
                    selectColumnY = name;
                    continue;
                } else {
                    break;
                }
            }
            if (defaultY == null) {
                defaultY = name;
            }
        }
        if (selectColumnY != null) {
            comboNameY.getSelectionModel().select(selectColumnY);
        }
    }

    private void updatePathClasses(PathTableData<T> newValue) {
        var objects = newValue.getItems();
        Function<PathObject, PathClass> collector = PathObject::getPathClass;
        if (baseClassOnly.get()) {
            collector = (po) -> po.getPathClass().getBaseClass();
        }
        var classes = new ArrayList<>(objects.stream().map(collector).sorted().distinct().toList());
        if (classes.contains(null)) {
            classes.remove(null);
            classes.add(PathClass.NULL_CLASS);
        }
        comboPathClasses.getItems().setAll(classes);
        comboPathClasses.getCheckModel().checkAll();
    }


    private Region createDisplayOptionsPane() {
        Spinner<Double> spinPointOpacity = new Spinner<>(
                0.05, 1.0, pointOpacity.get(), 0.05);
        pointOpacity.bind(Bindings.createDoubleBinding(spinPointOpacity::getValue,
                spinPointOpacity.getValueFactory().valueProperty()));
        spinPointOpacity.setEditable(true);
        spinPointOpacity.setMinWidth(80);
        FXUtils.resetSpinnerNullToPrevious(spinPointOpacity);

        Spinner<Double> spinPointRadius = new Spinner<>(
                0.5, 20.0, pointRadius.get(), 0.25);
        spinPointRadius.getValueFactory().valueProperty().bindBidirectional(pointRadius.asObject());
        pointRadius.bind(Bindings.createDoubleBinding(spinPointRadius::getValue,
                spinPointRadius.getValueFactory().valueProperty()));
        spinPointRadius.setEditable(true);
        spinPointRadius.setMinWidth(80);
        FXUtils.resetSpinnerNullToPrevious(spinPointRadius);

        CheckBox cbShowAll = new CheckBox(QuPathResources.getString("Charts.BoxPlotDisplay.showAllPoints"));
        cbShowAll.setTooltip(new Tooltip(QuPathResources.getString("Charts.BoxPlotDisplay.showAllPointsDescription")));
        cbShowAll.selectedProperty().bindBidirectional(showAllPoints);
        cbShowAll.setMinWidth(CheckBox.USE_PREF_SIZE);

        CheckBox cbBaseClassOnly = new CheckBox(QuPathResources.getString("Charts.BoxPlotDisplay.baseOnly"));
        cbBaseClassOnly.setTooltip(new Tooltip(QuPathResources.getString("Charts.BoxPlotDisplay.baseOnlyDescription")));
        cbBaseClassOnly.selectedProperty().bindBidirectional(baseClassOnly);
        cbBaseClassOnly.setMinWidth(CheckBox.USE_PREF_SIZE);
        cbBaseClassOnly.selectedProperty().addListener(_ -> updatePathClasses(model.get()));

        CheckBox cbDrawGrid = new CheckBox(QuPathResources.getString("Charts.ScatterPlotDisplay.showGrid"));
        cbDrawGrid.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.showGridDescription")));
        cbDrawGrid.selectedProperty().bindBidirectional(showGrid);
        cbDrawGrid.setMinWidth(CheckBox.USE_PREF_SIZE);

        CheckBox cbDrawAxes = new CheckBox(QuPathResources.getString("Charts.ScatterPlotDisplay.showAxes"));
        cbDrawAxes.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.showAxesDescription")));
        cbDrawAxes.selectedProperty().bindBidirectional(showAxes);
        cbDrawAxes.setMinWidth(CheckBox.USE_PREF_SIZE);

        var pane = new GridPane();
        int row = 0;

        pane.addRow(
                row++,
                createLabelFor(
                        spinPointOpacity,
                        QuPathResources.getString("Charts.ScatterPlotDisplay.pointOpacity"),
                        QuPathResources.getString("Charts.ScatterPlotDisplay.pointOpacityDescription")
                ),
                spinPointOpacity
        );

        pane.addRow(
                row++,
                createLabelFor(
                        spinPointRadius,
                        QuPathResources.getString("Charts.ScatterPlotDisplay.pointRadius"),
                        QuPathResources.getString("Charts.ScatterPlotDisplay.pointRadiusDescription")
                ),
                spinPointRadius
        );
        pane.setHgap(5);
        pane.setVgap(5);
        pane.setAlignment(Pos.CENTER);
        pane.setMaxHeight(Double.MAX_VALUE);

        var boxCheckboxes = new VBox(
                cbDrawGrid,
                cbDrawAxes,
                cbShowAll,
                cbBaseClassOnly
        );
        boxCheckboxes.setAlignment(Pos.CENTER_LEFT);
        boxCheckboxes.setSpacing(5);

        var hbox = new HBox(
                pane,
                new Separator(Orientation.VERTICAL),
                boxCheckboxes
        );
        hbox.setSpacing(10);

        return new TitledPane(QuPathResources.getString("Charts.ScatterPlotDisplay.display"), hbox);
    }

    private void initProperties() {
        pointOpacity.bindBidirectional(boxplot.markerOpacityProperty());
        pointRadius.bindBidirectional(boxplot.markerRadiusProperty());

        boxplot.verticalGridLinesVisibleProperty().bindBidirectional(showGrid);
        boxplot.horizontalGridLinesVisibleProperty().bindBidirectional(showGrid);

        boxplot.getXAxis().tickLabelsVisibleProperty().bindBidirectional(showAxes);
        boxplot.getYAxis().tickLabelsVisibleProperty().bindBidirectional(showAxes);

        boxplot.drawAllPointsProperty().bindBidirectional(showAllPoints);
    }

    private static Label createLabelFor(Node node, String text, String tooltip) {
        var label = new Label(text);
        label.setLabelFor(node);
        label.setMinWidth(Label.USE_PREF_SIZE);
        if (tooltip != null) {
            var tt = new Tooltip(tooltip);
            if (node instanceof Control control)
                control.setTooltip(tt);
            else
                Tooltip.install(node, tt);
            label.setTooltip(tt);
        }
        return label;
    }

}
