package qupath.lib.gui.charts.display;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
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
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import org.controlsfx.control.SearchableComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.charts.SnapshotTools;
import qupath.lib.gui.charts.builders.Charts;
import qupath.lib.gui.charts.impl.CanvasScatterChart;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathObject;

import java.text.MessageFormat;
import java.util.Collections;
import qupath.lib.objects.classes.PathClass;

/**
 * A wrapper around {@link CanvasScatterChart} for displaying data about PathObject measurements.
 */
public class ScatterPlotDisplay<T extends PathObject> implements PlotDisplay<T> {

    private static final Logger logger = LoggerFactory.getLogger(ScatterPlotDisplay.class);

    private final static String KEY = "scatter.plot.";

    private final static IntegerProperty PROP_MAX_POINTS = PathPrefs.createPersistentPreference(
            KEY + "maxPoints", 1_000_000
    );

    private final static IntegerProperty PROP_SEED = PathPrefs.createPersistentPreference(
            KEY + "seed", 42
    );

    private final static DoubleProperty PROP_POINT_OPACITY = PathPrefs.createPersistentPreference(
            KEY + "pointOpacity", 1.0
    );

    private final static DoubleProperty PROP_POINT_RADIUS = PathPrefs.createPersistentPreference(
            KEY + "pointRadius", 1.0
    );

    private final static BooleanProperty SHOW_AXES = PathPrefs.createPersistentPreference(
            KEY + "showAxes", true
    );

    private final static BooleanProperty SHOW_GRID = PathPrefs.createPersistentPreference(
            KEY + "showGrid", true
    );

    private final static BooleanProperty SHOW_LEGEND = PathPrefs.createPersistentPreference(
            KEY + "showLegend", true
    );

    private final static BooleanProperty AUTORANGE_FULL_DATA = PathPrefs.createPersistentPreference(
            KEY + "autorangeFullData", true
    );

    private boolean isUpdating = false;

    private final ObjectProperty<PathTableData<T>> model = new SimpleObjectProperty<>();
    private final SearchableComboBox<String> comboNameX = new SearchableComboBox<>();
    private final SearchableComboBox<String> comboNameY = new SearchableComboBox<>();
    private final BorderPane pane = new BorderPane();
    private final CanvasScatterChart<Number, Number> scatter;
//    private final PathObjectScatterChart scatter;

    // .asObject() required so we can bind to a spinner (and can't call inline or we'll be garbage-collected)
    private final ObjectProperty<Double> pointRadius = createWeakBoundProperty(PROP_POINT_RADIUS).asObject();
    private final ObjectProperty<Double> pointOpacity = createWeakBoundProperty(PROP_POINT_OPACITY).asObject();

    private final BooleanProperty showAxes = createWeakBoundProperty(SHOW_AXES);
    private final BooleanProperty showGrid = createWeakBoundProperty(SHOW_GRID);
    private final BooleanProperty showLegend = createWeakBoundProperty(SHOW_LEGEND);
    private final BooleanProperty autorangeFullData = createWeakBoundProperty(AUTORANGE_FULL_DATA);

    private final IntegerProperty maxPoints = createWeakBoundProperty(PROP_MAX_POINTS);
    private final IntegerProperty seed = createWeakBoundProperty(PROP_SEED);

    // Record of number of points that have been set
    private final IntegerProperty totalPoints = new SimpleIntegerProperty();

    /**
     * Create a scatter plot display with the specified model
     * @param model the data model
     */
    public ScatterPlotDisplay(PathTableData<T> model) {
        this();
        this.model.set(model);
    }

    /**
     * Create a scatter plot from a table of PathObject measurements.
     */
    public ScatterPlotDisplay() {
        this.model.addListener(this::handleModelChange);
        BorderPane panelMain = new BorderPane();

        scatter = (CanvasScatterChart<Number, Number>) Charts.scatterChart()
                .viewer(QuPathGUI.getInstance().getViewer())
                .build();
        scatter.setMarkerRadius(pointRadius.get() * 2); // todo radius vs size
        scatter.setMarkerOpacity(pointOpacity.get());


        var popup = new ContextMenu();
        var miCopy = new MenuItem(QuPathResources.getString("Charts.ScatterPlotDisplay.copyToClipboard"));
        miCopy.setOnAction(_ -> SnapshotTools.copyScaledSnapshotToClipboard(scatter, 4));
        popup.getItems().add(miCopy);
        scatter.setOnContextMenuRequested(e -> popup.show(
                scatter.getScene().getWindow(), e.getScreenX(), e.getScreenY()));

        panelMain.setCenter(scatter);

        initProperties();

        comboNameX.getSelectionModel().selectedItemProperty().addListener(_ -> requestReplot());
        comboNameY.getSelectionModel().selectedItemProperty().addListener(_ -> requestReplot());

        var topPane = new GridPane();
        var labelX = new Label(QuPathResources.getString("Charts.ScatterPlotDisplay.x"));
        comboNameX.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.xDescription")));
        labelX.setLabelFor(comboNameX);
        topPane.addRow(0, labelX, comboNameX);

        var labelY = new Label(QuPathResources.getString("Charts.ScatterPlotDisplay.y"));
        comboNameY.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.yDescription")));
        labelY.setLabelFor(comboNameY);
        topPane.addRow(1, labelY, comboNameY);
        topPane.setHgap(5);

        pane.setTop(topPane);
        comboNameX.prefWidthProperty().bind(pane.widthProperty());
        comboNameY.prefWidthProperty().bind(pane.widthProperty());
        panelMain.setMinSize(200, 200);
        panelMain.setPrefSize(400, 300);

        pane.setCenter(panelMain);
        pane.setBottom(createMainOptionsPane());

        pane.setPadding(new Insets(10, 10, 10, 10));
    }

    @Override
    public void setModel(PathTableData<T> model) {
        this.model.set(model);
    }

    @Override
    public PathTableData<T> getModel() {
        return this.model.get();
    }

    @Override
    public ObjectProperty<PathTableData<T>> modelProperty() {
        return model;
    }

    @Override
    public String getName() {
        return QuPathResources.getString("Measure.MeasurementTable.scatterPlot");
    }

    @Override
    public Pane getPane() {
        return pane;
    }

    @Override
    public void requestReplot() {
        var model = this.model.get();
        if (model == null || isUpdating) {
            setScatterData(Collections.emptyList(), _ -> Double.NaN, _ -> Double.NaN);
            return;
        }
        // Awkward - but SearchableComboBox tends to set values temporarily to null
        var x = comboNameX.getValue();
        var y = comboNameY.getValue();
        if (x != null && y != null) {
            var items = model.getItems();
            setDataFromTable(items, model, x, y);
            totalPoints.set(items.size());
        }
    }

    /**
     * Set the data to display in the plot from a table model.
     * <p>
     * This calls {@link setScatterData(Collection, Function, Function)} in addition to setting the x and y labels.
     *
     * @param pathObjects the objects to display
     * @param model the table model containing the measurements
     * @param xMeasurement the column to use for x values
     * @param yMeasurement the column to use for y values
     */
    public void setDataFromTable(
            Collection<T> pathObjects,
            PathTableData<T> model,
            String xMeasurement, String yMeasurement) {

        setScatterData(pathObjects,
                    p -> model.getNumericValue(p, xMeasurement),
                    p -> model.getNumericValue(p, yMeasurement));
        scatter.getXAxis().setLabel(xMeasurement);
        scatter.getYAxis().setLabel(yMeasurement);
    }

    // todo consider making this generic for different chart types
    /**
     * Set the data to display in the plot.
     * @param pathObjects the objects to display
     * @param xFun a function to extract the x value to plot
     * @param yFun a function to extract the y value to plot
     */
    private void setScatterData(Collection<T> pathObjects,
                                Function<T, Number> xFun,
                                Function<T, Number> yFun) {

        // Find the represented classes & sort them
        var newData = pathObjects
                .stream()
                .map(PathObject::getPathClass)
                .distinct()
                .sorted(Comparator.nullsFirst(PathClass::compareTo))
                .map(pc -> {
                    // create a series for each class so they appear nicely in the legend
                    return new XYChart.Series<>(
                            pc == null ? PathClass.NULL_CLASS.toString() : pc.toString(),
                            FXCollections.observableArrayList(pathObjects.stream()
                                    .filter(po -> po.getPathClass() == pc)
                                    .map(po -> new XYChart.Data<>(xFun.apply(po), yFun.apply(po), po))
                                    .toList())
                    );
                })
                .toList();

        scatter.getData().setAll(newData);
    }

    private void initProperties() {
        pointOpacity.addListener((_, _, n) -> scatter.setMarkerOpacity(n));
        pointRadius.addListener((_, _, n) -> scatter.setMarkerRadius(n * 2));

        scatter.verticalGridLinesVisibleProperty().bindBidirectional(showGrid);
        scatter.horizontalGridLinesVisibleProperty().bindBidirectional(showGrid);

        scatter.getXAxis().tickLabelsVisibleProperty().bindBidirectional(showAxes);
        scatter.getYAxis().tickLabelsVisibleProperty().bindBidirectional(showAxes);

        scatter.legendVisibleProperty().bindBidirectional(showLegend);
        scatter.autorangeToFullDataProperty().bindBidirectional(autorangeFullData);

        maxPoints.addListener((_, _, n) -> scatter.setMaxPoints(n.intValue()));
        seed.addListener((_, _, n) -> scatter.setRandomSeed(n.intValue()));
    }

    private void handleModelChange(ObservableValue<? extends PathTableData<?>> observable,
                                   PathTableData<?> oldValue, PathTableData<?> newValue) {
        isUpdating = true;
        if (newValue != null) {
            updateForModel(newValue);
        }
        isUpdating = false;
        requestReplot();
    }

    private void updateForModel(PathTableData<?> newValue) {
        comboNameX.getItems().setAll(newValue.getMeasurementNames());
        comboNameY.getItems().setAll(newValue.getMeasurementNames());

        // Try to select the first column that isn't for 'centroids'...
        // but, always select something
        String selectColumnX = null, selectColumnY = null;
        String defaultX = null, defaultY = null;
        for (String name : newValue.getMeasurementNames()) {
            if (!name.toLowerCase().startsWith("centroid")) {
                if (selectColumnX == null) {
                    selectColumnX = name;
                    continue;
                } else if (selectColumnY == null) {
                    selectColumnY = name;
                    continue;
                } else {
                    break;
                }
            }
            if (defaultX == null) {
                defaultX = name;
            } else if (defaultY == null) {
                defaultY = name;
            }
        }
        if (selectColumnX != null) {
            comboNameX.getSelectionModel().select(selectColumnX);
        }
        if (selectColumnY != null) {
            comboNameY.getSelectionModel().select(selectColumnY);
        }
    }

    private Pane createMainOptionsPane() {
        return new VBox(
                createDisplayOptionsPane()
//                , createSamplingOptionPane()
        );
    }

    private TitledPane createDisplayOptionsPane() {
        Spinner<Double> spinPointOpacity = new Spinner<>(
                0.05, 1.0, pointOpacity.get(), 0.05);
        spinPointOpacity.getValueFactory().valueProperty().bindBidirectional(pointOpacity);
        spinPointOpacity.setEditable(true);
        spinPointOpacity.setMinWidth(80);
        FXUtils.resetSpinnerNullToPrevious(spinPointOpacity);

        Spinner<Double> spinPointRadius = new Spinner<>(
                0.5, 20.0, pointRadius.get(), 0.25);
        spinPointRadius.getValueFactory().valueProperty().bindBidirectional(pointRadius);
        spinPointRadius.setEditable(true);
        spinPointRadius.setMinWidth(80);
        FXUtils.resetSpinnerNullToPrevious(spinPointRadius);

        CheckBox cbDrawGrid = new CheckBox(QuPathResources.getString("Charts.ScatterPlotDisplay.showGrid"));
        cbDrawGrid.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.showGridDescription")));
        cbDrawGrid.selectedProperty().bindBidirectional(showGrid);
        cbDrawGrid.setMinWidth(CheckBox.USE_PREF_SIZE);

        CheckBox cbDrawAxes = new CheckBox(QuPathResources.getString("Charts.ScatterPlotDisplay.showAxes"));
        cbDrawAxes.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.showAxesDescription")));
        cbDrawAxes.selectedProperty().bindBidirectional(showAxes);
        cbDrawAxes.setMinWidth(CheckBox.USE_PREF_SIZE);

        CheckBox cbShowLegend = new CheckBox(QuPathResources.getString("Charts.ScatterPlotDisplay.showLegend"));
        cbShowLegend.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.showLegendDescription")));
        cbShowLegend.selectedProperty().bindBidirectional(showLegend);
        cbShowLegend.setMinWidth(CheckBox.USE_PREF_SIZE);

        CheckBox cbAutorange = new CheckBox(QuPathResources.getString("Charts.ScatterPlotDisplay.setAxes"));
        cbAutorange.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.setAxesDescription")));
        cbAutorange.selectedProperty().bindBidirectional(autorangeFullData);
        cbAutorange.setMinWidth(CheckBox.USE_PREF_SIZE);

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
                cbShowLegend,
                cbDrawGrid,
                cbDrawAxes,
                cbAutorange
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

    // todo unused as canvas scatter doesn't currently support subsampling
    private TitledPane createSamplingOptionPane() {
        var pane = new GridPane();
        int row = 0;

        var tfMaxPoints = createIntTextField(
                maxPoints,
                Integer.toString(maxPoints.get()),
                QuPathResources.getString("Charts.ScatterPlotDisplay.typeInteger0")
        );

        var tfSeed = createIntTextField(seed, Integer.toString(seed.get()), QuPathResources.getString("Charts.ScatterPlotDisplay.typeInteger"));

        pane.addRow(
                row++,
                createLabelFor(
                        tfMaxPoints,
                        QuPathResources.getString("Charts.ScatterPlotDisplay.maxPoints"),
                        QuPathResources.getString("Charts.ScatterPlotDisplay.maxPointsDescription")
                ),
                tfMaxPoints
        );

        pane.addRow(
                row++,
                createLabelFor(
                        tfSeed,
                        QuPathResources.getString("Charts.ScatterPlotDisplay.rNGSeed"),
                        QuPathResources.getString("Charts.ScatterPlotDisplay.rNGSeedDescription")
                ),
                tfSeed
        );

        pane.setHgap(5);
        pane.setVgap(5);

        var labelWarning = new Label(QuPathResources.getString("Charts.ScatterPlotDisplay.showingLotsOfPoints"));
        labelWarning.setWrapText(true);
        labelWarning.setAlignment(Pos.CENTER);
        labelWarning.setTextAlignment(TextAlignment.CENTER);
        labelWarning.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        labelWarning.getStyleClass().add("warn-label-text");


        var labelPoints = new Label();
        labelPoints.textProperty().bind(Bindings.createStringBinding(this::getNumPointsString,
                tfMaxPoints.textProperty(), maxPoints, totalPoints));
        labelPoints.setWrapText(true);
        labelPoints.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        labelPoints.setAlignment(Pos.CENTER);
        labelPoints.setTextAlignment(TextAlignment.CENTER);

        var vBoxLabels = new VBox();
        vBoxLabels.setSpacing(2);
        int nWarningPoints = 1_000_000;
        vBoxLabels.getChildren().add(labelPoints);
        vBoxLabels.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(labelPoints, Priority.SOMETIMES);
        VBox.setVgrow(labelWarning, Priority.SOMETIMES);

        if (maxPoints.get() > nWarningPoints)
            vBoxLabels.getChildren().addFirst(labelWarning);
        maxPoints.addListener((_, _, n) -> {
            if (n.intValue() > nWarningPoints && !vBoxLabels.getChildren().contains(labelWarning))
                vBoxLabels.getChildren().addFirst(labelWarning);
            else
                vBoxLabels.getChildren().remove(labelWarning);
        });

        var hBox = new HBox(pane, vBoxLabels);
        hBox.setSpacing(10);
        HBox.setHgrow(labelWarning, Priority.SOMETIMES);
        HBox.setHgrow(labelPoints, Priority.SOMETIMES);

        return new TitledPane(QuPathResources.getString("Charts.ScatterPlotDisplay.sampling"), hBox);
    }

    /**
     * Create a text field for a user to input an integer value, setting the result in a property if it is valid.
     */
    private static TextField createIntTextField(IntegerProperty prop, String defaultText, String prompt) {
        var textField = new TextField();
        textField.setText(defaultText);
        textField.setPromptText(prompt);
        // Handle changes when user pressed enter, or focus lost (e.g. pressing tab)
        textField.setOnAction(_ -> setIntegerPropertyFromText(prop, textField.getText()));
        textField.focusedProperty().addListener((_, _, n) -> {
            if (!n)
                setIntegerPropertyFromText(prop, textField.getText());
        });
        return textField;
    }

    private static void setIntegerPropertyFromText(IntegerProperty prop, String text) {
        if (text == null || text.isBlank())
            return;
        try {
            int val = Integer.parseInt(text);
            if (val < 1)
                prop.set(0);
            else
                prop.set(val);
        } catch (NumberFormatException ex) {
            logger.warn("Can't parse integer from {}: {}",
                    text,
                    ex.getMessage());
        }
    }

    /**
     * Get the string used to represent the number of points being displayed.
     * This helps clarify when subsampling has been applied.
     */
    private String getNumPointsString() {
        int currentPoints = totalPoints.get();
        int displayedPoints = GeneralTools.clipValue(maxPoints.getValue(), 0, currentPoints);
        if (displayedPoints == currentPoints) {
            if (displayedPoints == 0)
                return QuPathResources.getString("Charts.ScatterPlotDisplay.noPoints");
            if (displayedPoints == 1)
                return QuPathResources.getString("Charts.ScatterPlotDisplay.showing1Point");
            else
                return QuPathResources.getString("Charts.ScatterPlotDisplay.showingAllPoints");
        }
        return MessageFormat.format(
                QuPathResources.getString("Charts.ScatterPlotDisplay.showingX"),
                displayedPoints,
                currentPoints,
                GeneralTools.formatNumber(displayedPoints*100.0/currentPoints, 1)
        );
    }

    /**
     * Helper function to create a label for a specific node, while also setting the tooltip text for the node.
     */
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

    // todo extract these to common
    private static BooleanProperty createWeakBoundProperty(BooleanProperty prop) {
        var prop2 = new SimpleBooleanProperty(prop.getValue());
        prop.bind(prop2);
        return prop2;
    }

    private static IntegerProperty createWeakBoundProperty(IntegerProperty prop) {
        var prop2 = new SimpleIntegerProperty(prop.getValue());
        prop.bind(prop2);
        return prop2;
    }

    private static DoubleProperty createWeakBoundProperty(DoubleProperty prop) {
        var prop2 = new SimpleDoubleProperty(prop.getValue());
        prop.bind(prop2);
        return prop2;
    }

}
