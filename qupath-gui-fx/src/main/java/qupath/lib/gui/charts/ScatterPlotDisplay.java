package qupath.lib.gui.charts;

import javafx.geometry.Insets;
import javafx.scene.chart.ScatterChart;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;

public class ScatterPlotDisplay implements ParameterChangeListener {

    // todo: if using PathObjects instead of PathDataTable then scatterplots can be more useful!
    private final PathTableData<PathObject> model;
    private final ComboBox<String> comboNameX = new ComboBox<>();
    private final ComboBox<String> comboNameY = new ComboBox<>();
    private final BorderPane pane = new BorderPane();

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
        panelMain.setCenter(updateScatter());

        comboNameX.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
            panelMain.setCenter(updateScatter());
        });
        comboNameY.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
            panelMain.setCenter(updateScatter());
        });

        pane.setTop(new VBox(comboNameX, comboNameY));
        comboNameX.prefWidthProperty().bind(pane.widthProperty());
        comboNameY.prefWidthProperty().bind(pane.widthProperty());
        panelMain.setMinSize(200, 200);
        panelMain.setPrefSize(600, 400);
        pane.setCenter(panelMain);
        pane.setPadding(new Insets(10, 10, 10, 10));


    }

    /**
     * Get the pane containing the scatter plot and associated UI components, for addition to a scene.
     * @return A pane
     */
    public Pane getPane() {
        return pane;
    }

    private ScatterChart<Number, Number> updateScatter() {
        return Charts.scatterChart()
                .viewer(QuPathGUI.getInstance().getViewer())
                .measurements(model.getItems(), comboNameX.getValue(), comboNameY.getValue())
                .build();
    }

    @Override
    public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
        // todo: add parameters: logX, logY, ...?
    }
}
