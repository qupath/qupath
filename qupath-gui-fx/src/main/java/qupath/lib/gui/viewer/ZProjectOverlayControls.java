/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
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
        this.overlay.setProjection(null);
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
        // Try to make selection status more prominent
        btn.selectedProperty().addListener((v, o, n) -> {
            if (n)
                btn.setStyle("-fx-font-weight: bold;");
            else
                btn.setStyle(null);
        });
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
