/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

import java.io.File;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.ImageCursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Ellipse;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Helper class to manage custom cursors that are associated with some tools.
 */
class CircleCursorCache extends CursorCache<Double> {

    private Group group;
    private final SnapshotParameters snapshotParameters;
    private final DoubleProperty radiusProperty = new SimpleDoubleProperty();

    CircleCursorCache() {
        super();
        snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);
        group = createGroup();
    }

    private Group createGroup() {
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
        return new Group(ellipseOuter, ellipseInner);
    }

    private Ellipse createEllipse(Paint stroke, double strokeWidth) {
        Ellipse ellipse = new Ellipse();
        ellipse.setFill(null);
        ellipse.setStroke(stroke);
        ellipse.radiusXProperty().bind(radiusProperty);
        ellipse.radiusYProperty().bind(radiusProperty);
        ellipse.setStrokeWidth(strokeWidth);
        return ellipse;
    }

    @Override
    protected Cursor createCursor(Double diameter) {
        group = createGroup(); // TODO: Remove this, it's temporary during development!!

        radiusProperty.set(diameter / 2.0);
        WritableImage image = group.snapshot(snapshotParameters, null);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "PNG", new File("/Users/pbankhea/Desktop/cursor.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ImageCursor(image, image.getWidth() / 2, image.getHeight() / 2);
    }

}
