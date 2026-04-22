/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer.tools.handlers;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Group;
import javafx.scene.shape.Ellipse;

/**
 * Helper class to track a cursor within a parent node and draw circle around it.
 * @since v0.8.0
 */
public class BrushLimits extends Group {

    /**
     * Radius of the brush limits.
     */
    private final DoubleProperty radius = new SimpleDoubleProperty(-1);

    public BrushLimits() {
        super();
        init();
        setMouseTransparent(true);
        getStyleClass().add("brush-limits");
    }

    public double getRadius() {
        return radiusProperty().get();
    }

    public void setRadius(double radius) {
        radiusProperty().set(radius);
    }

    public DoubleProperty radiusProperty() {
        return radius;
    }

    /**
     * Set the center location, defined in terms of the parent
     * coordinate system.
     * Typically, these are coordinates obtained from a {@link javafx.scene.input.MouseEvent}.
     * @param x the x-coordinate of the center
     * @param y the y-coordinate of the center
     */
    public void setCenter(double x, double y) {
        var bounds = getParent().getBoundsInLocal();
        if (bounds == null) {
            setVisible(false);
            setTranslateX(0);
            setTranslateY(0);
        } else {
            setVisible(true);
            setOpacity(0.6);
            setTranslateX(x - bounds.getCenterX());
            setTranslateY(y - bounds.getCenterY());
        }
    }

    private void init() {
        Ellipse ellipseInner = createEllipse();
        ellipseInner.getStyleClass().add("dark");

        Ellipse ellipseOuter = createEllipse();
        ellipseOuter.getStyleClass().add("light");

        getChildren().setAll(ellipseOuter, ellipseInner);
    }

    private Ellipse createEllipse() {
        Ellipse ellipse = new Ellipse();
        ellipse.getStyleClass().add("ellipse");
        ellipse.radiusXProperty().bind(radius);
        ellipse.radiusYProperty().bind(radius);
        return ellipse;
    }

}
