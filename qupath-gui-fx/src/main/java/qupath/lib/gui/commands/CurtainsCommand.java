package qupath.lib.gui.commands;

import java.util.Objects;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.controlsfx.control.RangeSlider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.OverlayOptions;

public class CurtainsCommand implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(CurtainsCommand.class);

    private final OverlayOptions options;
    private Stage stage;

    public CurtainsCommand(OverlayOptions options) {
        Objects.requireNonNull(options);
        this.options = options;
    }

    @Override
    public void run() {
        if (stage == null) {
            stage = createStage();
        }
        stage.show();
    }

    private Stage createStage() {
        var pane = createPane();
        var stage = new Stage();
        stage.initOwner(findOwner());
        stage.setTitle("Overlay curtains");
        stage.setScene(new Scene(pane));
        return stage;
    }

    private Stage findOwner() {
        var qupath = QuPathGUI.getInstance();
        return qupath == null ? null : qupath.getStage();
    }

    private Pane createPane() {
        var pane = new GridPane();

        var labelHorizontal = new Label("Horizontal");
        var sliderHorizontal = createRangeSlider(options.curtainMinXProperty(), options.curtainMaxXProperty());
        labelHorizontal.setLabelFor(sliderHorizontal);
        var btnResetHorizontal = new Button("Reset");
        btnResetHorizontal.setOnAction(e -> reset(sliderHorizontal));

        var labelVertical = new Label("Vertical");
        var sliderVertical = createRangeSlider(options.curtainMinYProperty(), options.curtainMaxYProperty());
        labelVertical.setLabelFor(sliderVertical);
        var btnResetVertical = new Button("Reset");
        btnResetVertical.setOnAction(e -> reset(sliderVertical));

        GridPaneUtils.addGridRow(pane, 0, 0, "Change the horizontal curtain effect", labelHorizontal, sliderHorizontal, btnResetHorizontal);
        GridPaneUtils.addGridRow(pane, 1, 0, "Change the vertical curtain effect", labelVertical, sliderVertical, btnResetVertical);

        pane.setHgap(5);
        pane.setVgap(5);
        pane.setPadding(new Insets(5));
        return pane;
    }

    private void reset(RangeSlider slider) {
        slider.setLowValue(slider.getMin());
        slider.setHighValue(slider.getMax());
    }

    private static RangeSlider createRangeSlider(DoubleProperty min, DoubleProperty max) {
        var slider = new RangeSlider();
        slider.setMin(0);
        slider.setMax(1);
        slider.lowValueProperty().bindBidirectional(min);
        slider.highValueProperty().bindBidirectional(max);
        return slider;
    }

}
