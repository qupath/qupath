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
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Ellipse;

/**
 * Helper class to track a cursor within a parent node and draw circle around it.
 */
class BrushLimits extends Group {

    /**
     * Radius of the brush limits.
     */
    private final DoubleProperty radius = new SimpleDoubleProperty(-1);

    /**
     * X-coordinate of the cursor location.
     */
    private final DoubleProperty x = new SimpleDoubleProperty();

    /**
     * Y-coordinate of the cursor location.
     */
    private final DoubleProperty y = new SimpleDoubleProperty();

    BrushLimits() {
        super();
        init();
        setMouseTransparent(true);
    }

    double getRadius() {
        return radiusProperty().get();
    }

    void setRadius(double radius) {
        radiusProperty().set(radius);
    }

    DoubleProperty radiusProperty() {
        return radius;
    }

    void setCenter(double x, double y) {
        this.x.set(x);
        this.y.set(y);
        var bounds = getParent().getBoundsInLocal();
        if (bounds == null) {
            setVisible(false);
        } else {
            setVisible(true);
            setTranslateX(x - bounds.getCenterX());
            setTranslateY(y - bounds.getCenterY());
        }
    }

    private void init() {
        double dash = 1.5;
        double strokeWidth = 1;
        Ellipse ellipseInner = createEllipse(Color.BLACK, strokeWidth);
        ellipseInner.getStrokeDashArray().addAll(dash, dash*3);

        Ellipse ellipseOuter = createEllipse(Color.WHITE, strokeWidth);
        ellipseOuter.getStrokeDashArray().addAll(dash, dash*3);
        ellipseOuter.setStrokeDashOffset(dash*2);

        Effect effect = new DropShadow(3.0, 1, 1, Color.BLACK);
        ellipseOuter.setEffect(effect);
        ellipseInner.setEffect(effect);
        getChildren().setAll(ellipseOuter, ellipseInner);
    }

    private Ellipse createEllipse(Paint stroke, double strokeWidth) {
        Ellipse ellipse = new Ellipse();
        ellipse.setFill(null);
        ellipse.setStroke(stroke);
        ellipse.radiusXProperty().bind(radius);
        ellipse.radiusYProperty().bind(radius);
        ellipse.setStrokeWidth(strokeWidth);
        return ellipse;
    }

}
