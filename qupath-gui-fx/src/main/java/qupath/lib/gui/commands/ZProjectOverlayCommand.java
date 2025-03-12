package qupath.lib.gui.commands;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.overlays.ZProjectOverlay;
import qupath.lib.images.servers.ZProjectedImageServer;

import java.util.List;

public class ZProjectOverlayCommand {

    private final QuPathGUI qupath;

    public ZProjectOverlayCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public void run() {
        var viewer = qupath.getViewer();
        if (viewer == null) {
            return;
        }

        var overlay = ZProjectOverlay.create(viewer);
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
                overlay.setProjection(zp);
            } else {
                overlay.setProjection(null);
            }
            viewer.repaint();
        });
        if (selected != null) {
            selected.setSelected(true);
        }

        var pane = new Group(tilePane);
        pane.getProperties().put("z-project-overlay", Boolean.TRUE);

        viewer.getView()
                .getChildren()
                .removeIf(n -> n.getProperties().getOrDefault("z-project-overlay", Boolean.FALSE).equals(Boolean.TRUE));

        StackPane.setAlignment(pane, Pos.TOP_CENTER);
        StackPane.setMargin(pane, new Insets(10));

        viewer.getView().getChildren().add(pane);

        viewer.getCustomOverlayLayers().setAll(overlay);
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
