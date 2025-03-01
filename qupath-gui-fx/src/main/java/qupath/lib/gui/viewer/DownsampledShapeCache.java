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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Helper class for managing simplified versions of a shape for rendering shapes at lower resolutions.
 * @since v0.6.0
 */
class DownsampledShapeCache {

    private static final int pointCountThreshold = 10;

    private final GeometryAndShape shape;

    private final List<Double> downsamples = new ArrayList<>();
    private final List<GeometryAndShape> downsampledShapes = new ArrayList<>();

    DownsampledShapeCache(ROI roi, double... downsamples) {
        this.shape = new GeometryAndShape(roi.getGeometry(), roi.getShape(), 1);
        for (double downsample : downsamples) {
            this.downsamples.add(downsample);
            this.downsampledShapes.add(null);
        }
    }

    Shape getForDownsample(double downsample) {
        GeometryAndShape lastShape = shape;
        double lastDownsample = lastShape.getDownsample();
        for (int i = 0; i < downsamples.size(); i++) {
            if (downsamples.get(i) > downsample)
                return lastShape.getShape();
            var currentShape = downsampledShapes.get(i);
            if (currentShape == null) {
                // Progressively simplify the shape for downsamples
                currentShape = computeForDownsample(lastShape, downsamples.get(i));
                downsampledShapes.set(i, currentShape);
            }
            lastShape = currentShape;
        }
        return lastShape.getShape();
    }

    private static GeometryAndShape computeForDownsample(GeometryAndShape shape, double downsample) {
        var reducer = new GeometryPrecisionReducer(new PrecisionModel(-downsample));
        reducer.setRemoveCollapsedComponents(true);
        reducer.setPointwise(true);
        reducer.setChangePrecisionModel(true);
        var geometry = reducer.reduce(shape.getGeometry());
        geometry = reducer.reduce(geometry);
        var geoms = new ArrayList<>();
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            var g = geometry.getGeometryN(i);
            if (g instanceof Polygon polygon) {
                var env = g.getEnvelopeInternal();
                if (env.getWidth() <= downsample || env.getHeight() <= downsample)
                    continue;
                var exterior = removeDuplicatePoints(polygon.getExteriorRing());

                int nHoles = polygon.getNumInteriorRing();
                var holes = new ArrayList<LinearRing>();
                for (var h = 0; h < nHoles; h++) {
                    var hole = polygon.getInteriorRingN(h);
                    var hEnv = hole.getEnvelopeInternal();
                    if (hEnv.getWidth() <= downsample || hEnv.getHeight() <= downsample)
                        continue;
                    holes.add(removeDuplicatePoints(hole));
                }
                polygon = polygon.getFactory()
                        .createPolygon(exterior, holes.toArray(new LinearRing[0]));
                geoms.add(polygon);
            }
        }
        geometry = geometry.getFactory().buildGeometry(geoms);
        return new GeometryAndShape(geometry, GeometryTools.geometryToShape(geometry), downsample);
    }

    private static LinearRing removeDuplicatePoints(LinearRing linearRing) {
        var coords = new ArrayList<Coordinate>();
        Coordinate lastCoord = null;
        for (int i = 0; i < linearRing.getNumPoints(); i++) {
            var coord = linearRing.getCoordinateN(i);
            if (!Objects.equals(lastCoord, coord)) {
                coords.add(coord);
            }
        }
        if (coords.size() < 3)
            return linearRing;
        else if (coords.size() == linearRing.getNumPoints())
            return linearRing;
        else
            return linearRing.getFactory().createLinearRing(coords.toArray(new Coordinate[0]));
    }


    private static class GeometryAndShape {

        private final Geometry geometry;
        private final Shape shape;
        private final double downsample;
        private final int nPoints;

        GeometryAndShape(Geometry geometry, Shape shape, double downsample) {
            this.geometry = geometry;
            this.shape = shape;
            this.downsample = downsample;
            this.nPoints = geometry.getNumPoints();
        }

        int getNumPoints() {
            return nPoints;
        }

        double getDownsample() {
            return downsample;
        }

        Shape getShape() {
            return shape;
        }

        Geometry getGeometry() {
            return geometry;
        }

    }

}
