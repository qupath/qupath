package qupath.lib.gui.viewer;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.viewer.overlays.ZProjectOverlay;
import qupath.lib.images.servers.ZProjectedImageServer;

import java.util.List;

class ZProjectOverlayControls {

    private static final Logger logger = LoggerFactory.getLogger(ZProjectOverlayControls.class);

    private final QuPathViewer viewer;
    private final ZProjectOverlay overlay;
    private final Node node;

    private final BooleanProperty showControls = new SimpleBooleanProperty(false);

    ZProjectOverlayControls(QuPathViewer viewer, BooleanProperty showControls) {
        this.viewer = viewer;
        this.overlay = ZProjectOverlay.create(viewer);
        this.node = createNode();
        this.showControls.bind(Bindings.createBooleanBinding(() -> {
            return showControls.get() && viewer.getServer() != null && viewer.getServer().nZSlices() > 1;
        }, viewer.imageDataProperty(), showControls));
        this.showControls.addListener(this::handleShowControlsChange);
        handleShowControlsChange(this.showControls, false, this.showControls.get());
    }

    private void handleShowControlsChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
        if (newValue)
            showControls();
        else
            hideControls();
    }

    private Node createNode() {

        var tilePane = new TilePane();
        var toggles = new ToggleGroup();
        ToggleButton selected = null;

        // Skip sum - should look the same as mean, but with different scaling
        for (var projection : List.of(
                ZProjectedImageServer.Projection.MEAN,
                ZProjectedImageServer.Projection.MEDIAN,
                ZProjectedImageServer.Projection.MAX,
                ZProjectedImageServer.Projection.MIN,
                ZProjectedImageServer.Projection.STANDARD_DEVIATION
                )) {
            var btn = createButton(projection);
            tilePane.getChildren().add(btn);
            toggles.getToggles().add(btn);
            if (overlay.getProjection() == projection) {
                selected = btn;
            }
        }
        toggles.selectedToggleProperty().addListener((v, o, n) -> {
            var proj = n == null ? null : n.getUserData();
            if (proj instanceof ZProjectedImageServer.Projection zp) {
                logger.debug("Setting projection to {}", proj);
                overlay.setProjection(zp);
            } else {
                logger.debug("Resetting z-projection {}", proj);
                overlay.setProjection(null);
            }
            viewer.repaint();
        });
        if (selected != null) {
            selected.setSelected(true);
        }

        var pane = new Group(tilePane);
        pane.getProperties().put("z-project-overlay", Boolean.TRUE);

        StackPane.setAlignment(pane, Pos.TOP_CENTER);
        StackPane.setMargin(pane, new Insets(10));

        return pane;
    }

    private void showControls() {
        logger.debug("Showing z-projection overlay control");
        viewer.getView().getChildren().add(node);
        viewer.getCustomOverlayLayers().setAll(overlay);
    }

    private void hideControls() {
        logger.debug("Hiding z-projection overlay control");
        viewer.getView()
                .getChildren()
                .remove(node);
        viewer.getCustomOverlayLayers().remove(overlay);
    }

    private static ToggleButton createButton(ZProjectedImageServer.Projection projection) {
        var btn = new ToggleButton(getName(projection));
        btn.setUserData(projection);
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private static String getName(ZProjectedImageServer.Projection projection) {
        return switch (projection) {
            case MAX -> "Max";
            case MIN -> "Min";
            case MEAN -> "Mean";
            case STANDARD_DEVIATION -> "Std.Dev";
            case SUM -> "Sum";
            case MEDIAN -> "Median";
        };
    }

}
