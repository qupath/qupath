package qupath.lib.gui.charts;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
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
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathObject;

/**
 * A wrapper around {@link PathObjectScatterChart} for displaying data about PathObject measurements.
 */
public class ScatterPlotDisplay {

    private final PathTableData<PathObject> model;
    private final SearchableComboBox<String> comboNameX = new SearchableComboBox<>();
    private final SearchableComboBox<String> comboNameY = new SearchableComboBox<>();
    private final BorderPane pane = new BorderPane();
    private final PathObjectScatterChart scatter;

    private final static String KEY = "scatter.plot.";

    private final static IntegerProperty PROP_MAX_POINTS = PathPrefs.createPersistentPreference(
            KEY + "maxPoints", 10_000
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

    // .asObject() required so we can bind to a spinner (and can't call inline or we'll be garbage-collected)
    private final ObjectProperty<Integer> maxPoints = createWeakBoundProperty(PROP_MAX_POINTS).asObject();
    private final ObjectProperty<Integer> seed = createWeakBoundProperty(PROP_SEED).asObject();
    private final ObjectProperty<Double> pointRadius = createWeakBoundProperty(PROP_POINT_RADIUS).asObject();
    private final ObjectProperty<Double> pointOpacity = createWeakBoundProperty(PROP_POINT_OPACITY).asObject();

    private final BooleanProperty showAxes = createWeakBoundProperty(SHOW_AXES);
    private final BooleanProperty showGrid = createWeakBoundProperty(SHOW_GRID);
    private final BooleanProperty showLegend = createWeakBoundProperty(SHOW_LEGEND);

    // Record of number of points that have been set
    private final IntegerProperty totalPoints = new SimpleIntegerProperty();

    private void initProperties() {
        pointOpacity.addListener((v, o, n) -> scatter.setPointOpacity(n));
        pointRadius.addListener((v, o, n) -> scatter.setPointRadius(n));
//        scatter.pointOpacityProperty().bindBidirectional(pointOpacity);
//        scatter.pointRadiusProperty().bindBidirectional(pointRadius);

        scatter.verticalGridLinesVisibleProperty().bindBidirectional(showGrid);
        scatter.horizontalGridLinesVisibleProperty().bindBidirectional(showGrid);

        scatter.getXAxis().tickLabelsVisibleProperty().bindBidirectional(showAxes);
        scatter.getYAxis().tickLabelsVisibleProperty().bindBidirectional(showAxes);

        scatter.legendVisibleProperty().bindBidirectional(showLegend);

        maxPoints.addListener((v, o, n) -> scatter.setMaxPoints(n.intValue()));
        seed.addListener((v, o, n) -> scatter.setRNG(n.intValue()));
    }


    private Pane createControls() {
        var box = new VBox(
                createDisplayPane(),
                createSamplingPane()
        );
//        box.setPadding(new Insets(5));
        return box;
    }

    private TitledPane createDisplayPane() {
        Spinner<Double> spinPointOpacity = new Spinner<>(
                0.05, 1.0, pointOpacity.get(), 0.05);
        spinPointOpacity.getValueFactory().valueProperty().bindBidirectional(pointOpacity);
        spinPointOpacity.setEditable(true);
        FXUtils.resetSpinnerNullToPrevious(spinPointOpacity);

        Spinner<Double> spinPointRadius = new Spinner<>(
                0.5, 20.0, pointRadius.get(), 0.25);
        spinPointRadius.getValueFactory().valueProperty().bindBidirectional(pointRadius);
        spinPointRadius.setEditable(true);
        FXUtils.resetSpinnerNullToPrevious(spinPointRadius);

        CheckBox cbDrawGrid = new CheckBox("Draw grid");
        cbDrawGrid.selectedProperty().bindBidirectional(showGrid);

        CheckBox cbDrawAxes = new CheckBox("Draw axes");
        cbDrawAxes.selectedProperty().bindBidirectional(showAxes);

        CheckBox cbShowLegend = new CheckBox("Show legend");
        cbShowLegend.selectedProperty().bindBidirectional(showLegend);

        var pane = new GridPane();
        int row = 0;

        pane.addRow(row++, createLabelFor(spinPointOpacity,
                        "Point opacity",
                        "Opacity of points to display (between 0 and 1)"),
                spinPointOpacity);

        pane.addRow(row++, createLabelFor(spinPointRadius,
                        "Point radius",
                        "Radius of points to display (in pixels)"),
                spinPointRadius);
        pane.setHgap(5);
        pane.setVgap(5);
        pane.setAlignment(Pos.CENTER);
        pane.setMaxHeight(Double.MAX_VALUE);

        var boxCheckboxes = new VBox(
                cbDrawGrid,
                cbDrawAxes,
                cbShowLegend
        );
        boxCheckboxes.setSpacing(5);

        var hbox = new HBox(
                pane,
                new Separator(Orientation.VERTICAL),
                boxCheckboxes
        );
        hbox.setSpacing(10);

        return new TitledPane("Display", hbox);
    }

    private TitledPane createSamplingPane() {
        var pane = new GridPane();
        int row = 0;

        var tfMaxPoints = new TextField();
        tfMaxPoints.setText(maxPoints.getValue().toString());
        // Handle changes when user pressed enter, or focus lost (e.g. pressing tab)
        tfMaxPoints.setOnAction(e -> setMaxPointsFromText(tfMaxPoints.getText()));
        tfMaxPoints.focusedProperty().addListener((v, o, n) -> {
            if (!n)
                setMaxPointsFromText(tfMaxPoints.getText());
        });

        Spinner<Integer> spinSeed = new Spinner<>(
                0, 1_000_000, seed.get(), 1);
        spinSeed.getValueFactory().valueProperty().bindBidirectional(seed);
        spinSeed.setEditable(true);

        pane.addRow(row++, createLabelFor(tfMaxPoints,
                        "Max points",
                        "Maximum number of points to display (for performance)"),
                tfMaxPoints);

        pane.addRow(row++, createLabelFor(spinSeed,
                        "RNG seed",
                        "Seed for the random number generated (RNG) when sampling"),
                spinSeed);

        pane.setHgap(5);
        pane.setVgap(5);

        var labelPoints = new Label();
        labelPoints.textProperty().bind(Bindings.createStringBinding(this::getNumPointsString,
                tfMaxPoints.textProperty(), maxPoints, totalPoints));
        labelPoints.setWrapText(true);
        labelPoints.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        labelPoints.setAlignment(Pos.CENTER);
        labelPoints.setTextAlignment(TextAlignment.CENTER);

        var hBox = new HBox(pane, labelPoints);
        hBox.setSpacing(10);
        HBox.setHgrow(labelPoints, Priority.SOMETIMES);

        return new TitledPane("Sampling", hBox);
    }

    private String getNumPointsString() {
        int currentPoints = totalPoints.get();
        int displayedPoints = GeneralTools.clipValue(maxPoints.getValue(), 0, currentPoints);
        if (displayedPoints == currentPoints) {
            if (displayedPoints == 0)
                return "No points to show";
            if (displayedPoints == 1)
                return "Showing 1 point";
            else
                return "Showing all points";
        }
        return "Showing " + displayedPoints + "/" + currentPoints + "\n(" +
                GeneralTools.formatNumber(displayedPoints*100.0/currentPoints, 1) + " %)";
    }

    private void setMaxPointsFromText(String text) {
        if (text == null || text.isBlank())
            return;
        try {
            int val = Integer.parseInt(text);
            if (val <= 1)
                maxPoints.set(0);
            else
                maxPoints.set(val);
        } catch (NumberFormatException ex) {
            LoggerFactory.getLogger(ScatterPlotDisplay.class).warn("Can't parse max points: {}",
                    ex.getMessage());
        }
    }


    private static Label createLabelFor(Node node, String text, String tooltip) {
        var label = new Label(text);
        label.setLabelFor(node);
        if (tooltip != null)
            Tooltip.install(node, new Tooltip(tooltip));
        return label;
    }




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

    /**
     * Create a scatter plot from a table of PathObject measurements.
     * @param model The table containing measurements
     */
    public ScatterPlotDisplay(PathTableData<PathObject> model) {
        this.model = model;
        comboNameX.getItems().setAll(model.getMeasurementNames());
        comboNameY.getItems().setAll(model.getMeasurementNames());

        // Try to select the first column that isn't for 'centroids'...
        // but, always select something
        String selectColumnX = null, selectColumnY = null;
        String defaultX = null, defaultY = null;
        for (String name : model.getMeasurementNames()) {
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

        BorderPane panelMain = new BorderPane();

        scatter = new PathObjectScatterChart(QuPathGUI.getInstance().getViewer());
        scatter.setPointRadius(pointRadius.get());
        scatter.setPointOpacity(pointOpacity.get());
        scatter.setRNG(seed.get());
        scatter.setMaxPoints(maxPoints.get());

        refreshScatterPlot();
        panelMain.setCenter(scatter);

        initProperties();

        comboNameX.getSelectionModel().selectedItemProperty().addListener((v, o, n) ->
                refreshScatterPlot()
        );
        comboNameY.getSelectionModel().selectedItemProperty().addListener((v, o, n) ->
                refreshScatterPlot()
        );

        var topPane = new GridPane();
        var labelX = new Label("X");
        comboNameX.setTooltip(new Tooltip("X-axis measurement"));
        labelX.setLabelFor(comboNameX);
        topPane.addRow(0, labelX, comboNameX);

        var labelY = new Label("Y");
        comboNameY.setTooltip(new Tooltip("Y-axis measurement"));
        labelY.setLabelFor(comboNameY);
        topPane.addRow(1, labelY, comboNameY);
        topPane.setHgap(5);

        pane.setTop(topPane);
        comboNameX.prefWidthProperty().bind(pane.widthProperty());
        comboNameY.prefWidthProperty().bind(pane.widthProperty());
        panelMain.setMinSize(200, 200);
        panelMain.setPrefSize(400, 300);

        pane.setCenter(panelMain);
        pane.setBottom(createControls());

        pane.setPadding(new Insets(10, 10, 10, 10));
    }


    /**
     * Get the pane containing the scatter plot and associated UI components, for addition to a scene.
     * @return A pane
     */
    public Pane getPane() {
        return pane;
    }

    /**
     * Refresh the scatter plot, in case the underlying data has been updated.
     */
    public void refreshScatterPlot() {
        var items = model.getItems();
        scatter.setDataFromTable(items, model, comboNameX.getValue(), comboNameY.getValue());
        totalPoints.set(items.size());
    }

}
