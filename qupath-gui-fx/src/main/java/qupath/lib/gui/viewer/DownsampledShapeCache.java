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

import qupath.lib.roi.ShapeSimplifier;

import java.awt.Shape;
import java.awt.geom.Path2D;

/**
 * Helper class for managing simplified versions of a shape for rendering shapes at lower resolutions.
 * @since v0.6.0
 */
class DownsampledShapeCache {

    private static final int pointCountThreshold = 10;

    private final Shape shape;

    private final double[] downsamples;
    private final Shape[] downsampledShapes;

    DownsampledShapeCache(Shape shape, double... downsamples) {
        this.shape = shape;
        this.downsamples = downsamples.clone();
        this.downsampledShapes = new Shape[downsamples.length];
    }

    Shape getForDownsample(double downsample) {
        Shape lastShape = shape;
        for (int i = 0; i < downsamples.length; i++) {
            if (downsamples[i] > downsample)
                return lastShape;
            var currentShape = downsampledShapes[i];
            if (currentShape == null) {
                // Progressively simplify the shape for downsamples
                currentShape = computeForDownsample(lastShape, downsamples[i]);
                downsampledShapes[i] = currentShape;
            }
            lastShape = currentShape;
        }
        return lastShape;
    }

    private static Shape computeForDownsample(Shape shape, double downsample) {
        var path = shape instanceof Path2D ? (Path2D)shape : new Path2D.Float(shape);
        boolean discardSmall = downsample > 8.0;
        return ShapeSimplifier.simplifyPath(path, downsample, pointCountThreshold, discardSmall ? downsample * 2 : -1);
    }


}
