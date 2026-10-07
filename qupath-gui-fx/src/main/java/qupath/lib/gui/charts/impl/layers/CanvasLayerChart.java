package qupath.lib.gui.charts.impl.layers;

import javafx.animation.AnimationTimer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.GridPaneUtils;

public class CanvasLayerChart<X, Y> extends Region {
    private static final Logger logger = LoggerFactory.getLogger(CanvasLayerChart.class);
    private final Axis<X> xAxis;
    private final Axis<Y> yAxis;
    private final StackPane stackPane = new StackPane();
    private final GridPane gridPane = new GridPane();
    private final BorderPane borderPane = new BorderPane();
    private final Canvas baseCanvas = new Canvas();
    // todo draw top if not empty
    private final StringProperty titleProperty = new SimpleStringProperty("");
    private final ObjectProperty<Side> legendSide = new SimpleObjectProperty<>(Side.BOTTOM);

    private final ObservableList<PlotLayer<X, Y>> layers = FXCollections.observableArrayList();
    // todo per-layer?
    private boolean redrawNeeded;


    /**
     * Constructs a XYChart given the two axes. The initial content for the chart
     * plot background and plot area that includes vertical and horizontal grid
     * lines and fills, are added.
     *
     * @param xAxis X Axis for this XY chart
     * @param yAxis Y Axis for this XY chart
     */
    public CanvasLayerChart(Axis<X> xAxis, Axis<Y> yAxis) {
        this.xAxis = xAxis;
        this.yAxis = yAxis;
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        setPadding(new Insets(10, 30, 10, 30));
        getChildren().add(borderPane);

        borderPane.setCenter(gridPane);
//        borderPane.setMinSize(0, 0);
        BorderPane.setMargin(gridPane, new Insets(10, 10, 10, 10));

        gridPane.add(yAxis, 0, 0);
        gridPane.add(stackPane, 1, 0);
        gridPane.add(xAxis, 1, 1);
        yAxis.setSide(Side.LEFT);
        xAxis.setSide(Side.BOTTOM);
        GridPaneUtils.setToExpandGridPaneHeight(stackPane);
        GridPaneUtils.setToExpandGridPaneWidth(stackPane);
        baseCanvas.widthProperty().bind(stackPane.widthProperty());
        baseCanvas.heightProperty().bind(stackPane.heightProperty());
        stackPane.getChildren().add(baseCanvas);

        stackPane.setMinSize(0, 0);

        layers.addListener((ListChangeListener<PlotLayer<X, Y>>) c -> {
            if (c.next()) {
                c.getAddedSubList().forEach(layer -> {
                    gridPane.widthProperty().addListener(_ -> redrawNeeded = true);
                    gridPane.heightProperty().addListener(_ -> redrawNeeded = true);
                    var canvas = layer.getCanvas();
                    stackPane.getChildren().add(canvas);
                    canvas.widthProperty().bind(stackPane.widthProperty());
                    canvas.heightProperty().bind(stackPane.heightProperty());
                });
                updateAxisRange();
                updateLegend();
                updatePlot();
            }
        });
        borderPane.sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();;
        });
    }

    /**
     * Get the layers used to compose the plot
     * @return the observable list of layers
     */
    public ObservableList<PlotLayer<X, Y>> getLayers() {
        return layers;
    }

    /**
     * Get the x-axis
     * @return an axis, probably categorical or number type
     */
    public Axis<X> getXAxis() {
        return xAxis;
    }

    /**
     * Get the y-axis
     * @return an axis, probably categorical or number type
     */
    public Axis<Y> getYAxis() {
        return yAxis;
    }

    @Override
    protected void layoutChildren() {
        Insets insets = getPadding();
        double x = insets.getLeft();
        double y = insets.getTop();
        double w = getWidth() - insets.getLeft() - insets.getRight();
        double h = getHeight() - insets.getTop() - insets.getBottom();

        for (Node child: getManagedChildren()) {
            if (child.isManaged()) {
                layoutInArea(child, x, y, w, h, 0, HPos.CENTER, VPos.CENTER);
            }
        }
    }

    private void updatePlot() {
        for (PlotLayer<X, Y> layer : layers) {
            // todo redraw only if needed
            layer.updateCanvas(getXAxis(), getYAxis());
        }
    }

    private void updateAxisRange() {
        for (PlotLayer<X, Y> layer : layers) {
            layer.updateAxes(getXAxis(), getYAxis());
        }
        baseCanvas.getGraphicsContext2D().clearRect(0, 0, baseCanvas.getWidth(), baseCanvas.getHeight());
        var xTicks = xAxis.getTickMarks();
        // todo abstract this over x and y, plus enable line styles
        for (var tick: xTicks) {
            var g2d = baseCanvas.getGraphicsContext2D();
            g2d.setGlobalAlpha(0.2);
            var color = Color.GRAY;
            g2d.setStroke(color);
            g2d.beginPath();
            g2d.moveTo(tick.getPosition(), 0);
            g2d.lineTo(tick.getPosition(), baseCanvas.getHeight());
            g2d.stroke();
        }
        var yTicks = yAxis.getTickMarks();
        for (var tick: yTicks) {
            var g2d = baseCanvas.getGraphicsContext2D();
            g2d.setGlobalAlpha(0.2);
            var color = Color.GRAY;
            g2d.setStroke(color);
            g2d.beginPath();
            g2d.moveTo(0, tick.getPosition());
            g2d.lineTo(baseCanvas.getWidth(), tick.getPosition());
            g2d.stroke();
        }
    }

    private void updateLegend() {
        HBox legend = new HBox();
        legend.setAlignment(Pos.CENTER);
        legend.setSpacing(10);
        for (PlotLayer<X, Y> layer : layers) {
            legend.getChildren().add(layer.getLegend());
        }
        setLegend(legend);
    }

    private void setLegend(Node legend) {
        // todo move or change me pls
        BorderPane.setMargin(legend, new Insets(10, 10, 10, 10));
        borderPane.setBottom(legend);
    }


    static void setLegendItemListener(Pane legendItem, Canvas canvas) {
        canvas.visibleProperty().addListener(_ -> {
            if (canvas.isVisible()) {
                legendItem.setOpacity(1);
            } else {
                legendItem.setOpacity(0.2);
            }
        });
        legendItem.setOnMouseClicked(_ -> canvas.setVisible(!canvas.isVisible()));
    }


    private final AnimationTimer timer = new AnimationTimer() {

        @Override
        public void handle(long now) {
            handlePulse();
        }

    };

    private void handlePulse() {
        if (redrawNeeded) {
            updateAxisRange();
            updatePlot();
        }
        redrawNeeded = false;
    }
}
