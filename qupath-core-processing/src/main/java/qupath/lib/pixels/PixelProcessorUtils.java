/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.pixels;

import org.locationtech.jts.geom.Geometry;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.util.Collections;
import java.util.List;

/**
 * Utility functions to help with the {@link PixelProcessor} class.
 * @since v0.5.0
 */
class PixelProcessorUtils {

    public static List<PathObject> maskObject(ROI parentROI, PathObject child) {
        if (parentROI == null)
            return Collections.singletonList(child);
        if (!childOnSamePlane(parentROI, child.getROI()) || parentROI.isEmpty())
            return Collections.emptyList();
        var geom = parentROI.getGeometry();
        var childGeom = child.getROI().getGeometry();
        var geomOutput = GeometryTools.homogenizeGeometryCollection(geom.intersection(childGeom));
        if (geomOutput.isEmpty() || geomOutput.getDimension() < childGeom.getDimension())
            return Collections.emptyList();
        else if (childGeom.equals(geomOutput))
            return Collections.singletonList(child);
        else {
            var newROI = GeometryTools.geometryToROI(geomOutput, child.getROI().getImagePlane());
            return Collections.singletonList(PathObjectTools.createLike(child, newROI));
        }
    }

    public static List<PathObject> maskObjectAndSplit(ROI parentROI, PathObject child) {
        if (!childOnSamePlane(parentROI, child.getROI()) || parentROI.isEmpty())
            return Collections.emptyList();
        Geometry geomOutput;
        var childGeom = child.getROI().getGeometry();
        if (parentROI != null) {
            var geom = parentROI.getGeometry();
            geomOutput = GeometryTools.homogenizeGeometryCollection(geom.intersection(childGeom));
        } else {
            geomOutput = GeometryTools.homogenizeGeometryCollection(childGeom);
        }
        if (geomOutput.isEmpty() || geomOutput.getDimension() < childGeom.getDimension())
            return Collections.emptyList();
        else if (childGeom.equals(geomOutput) && childGeom.getNumGeometries() == 1)
            return Collections.singletonList(child);
        else {
            var newROI = GeometryTools.geometryToROI(geomOutput, child.getROI().getImagePlane());
            return RoiTools.splitROI(newROI).stream().map(r -> PathObjectTools.createLike(child, r)).toList();
        }
    }

    public static boolean containsCentroid(ROI roi, PathObject child) {
        // No roi indicates the entire image... so them accept that
        if (roi == null)
            return false;
        var childROI = child.getROI();
        // Check plane and centroid
        return roi.getImagePlane().equals(childROI.getImagePlane()) &&
                roi.contains(childROI.getCentroidX(), childROI.getCentroidY());
    }

    private static boolean childOnSamePlane(ROI parent, ROI child) {
        if (child == null)
            return false;
        else if (parent == null)
            return true;
        return parent.getImagePlane().equals(child.getImagePlane());
    }

}
