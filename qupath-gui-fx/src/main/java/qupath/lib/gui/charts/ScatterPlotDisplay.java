package qupath.lib.gui.charts;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.controlsfx.control.SearchableComboBox;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;

/**
 * A wrapper around {@link ScatterPlotChart} for displaying data about PathObject measurements.
 */
public class ScatterPlotDisplay implements ParameterChangeListener {

    private final PathTableData<PathObject> model;
    private final SearchableComboBox<String> comboNameX = new SearchableComboBox<>();
    private final SearchableComboBox<String> comboNameY = new SearchableComboBox<>();
    private final BorderPane pane = new BorderPane();
    private final ParameterList paramsScatter = new ParameterList()
            .addIntParameter("nPoints", "Max number of points", 10000, null,  "Maximum number of points to be drawn (>=1)")
            .addIntParameter("randomSeed", "Random seed", 42, null, "Random seed for point subsampling")
            .addBooleanParameter("drawGrid", "Draw grid", true, "Whether to draw gridlines on the plot")
            .addBooleanParameter("drawAxes", "Draw axes", true, "Whether to draw axis ticks on the plot")
            .addDoubleParameter("pointOpacity", "Point opacity", 1, null, 0, 1, "The opacity of points displayed on the plot.")
            .addIntParameter("pointSize", "Point size", 4, "px", 1, 10, "The point size in pixels");
    private final ScatterPlotChart scatter;

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

        scatter = new ScatterPlotChart(QuPathGUI.getInstance().getViewer());
        scatter.setDataFromMeasurements(model.getItems(), comboNameX.getValue(), comboNameY.getValue());
        panelMain.setCenter(scatter);

        comboNameX.getSelectionModel().selectedItemProperty().addListener((v, o, n) ->
            scatter.setDataFromMeasurements(model.getItems(), n, comboNameY.getValue())
        );
        comboNameY.getSelectionModel().selectedItemProperty().addListener((v, o, n) ->
                scatter.setDataFromMeasurements(model.getItems(), comboNameX.getValue(), n)
        );

        var topPane = new GridPane();
        var labelX = new Label("X");
        comboNameX.setTooltip(new Tooltip("Y-axis measurement"));
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

        ParameterPanelFX panelParams = new ParameterPanelFX(paramsScatter);
        panelParams.addParameterChangeListener(this);
        panelParams.getPane().setPadding(new Insets(20, 5, 5, 5));
        panelParams.getPane().setMinWidth(Pane.USE_PREF_SIZE);

        GridPane panelSouth = new GridPane();
        panelSouth.add(panelParams.getPane(), 0, 0);
        GridPane.setHgrow(panelParams.getPane(), Priority.NEVER);

        pane.setCenter(panelMain);
        pane.setBottom(panelSouth);

        pane.setPadding(new Insets(10, 10, 10, 10));

    }

    /**
     * Get the pane containing the scatter plot and associated UI components, for addition to a scene.
     * @return A pane
     */
    public Pane getPane() {
        return pane;
    }

    @Override
    public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
        // todo: add parameters: logX, logY, ...?
        switch (key) {
            case "drawGrid" -> scatter.setDrawGrid(paramsScatter.getBooleanParameterValue("drawGrid"));
            case "drawAxes" -> scatter.setDrawAxes(paramsScatter.getBooleanParameterValue("drawAxes"));
            case "nPoints" -> scatter.setMaxPoints(paramsScatter.getIntParameterValue("nPoints"));
            case "pointOpacity" -> scatter.setPointOpacity(paramsScatter.getDoubleParameterValue("pointOpacity"));
            case "pointSize" -> scatter.setPointSize(paramsScatter.getIntParameterValue("pointSize"));
            case "randomSeed" -> scatter.setRNG(paramsScatter.getIntParameterValue("randomSeed"));
        }
    }

    /**
     * Refresh the scatter plot, in case the underlying data has been updated.
     */
    public void refreshScatterPlot() {
        // TODO: Fix this - it works only for values within a measurement list (e.g. not centroids or other 'live' values)
        scatter.setDataFromMeasurements(model.getItems(), comboNameX.getValue(), comboNameY.getValue());
    }

}
