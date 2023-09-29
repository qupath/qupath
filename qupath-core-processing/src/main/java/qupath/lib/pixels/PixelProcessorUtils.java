package qupath.lib.pixels;

import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import java.util.Collections;
import java.util.List;

public class PixelProcessorUtils {

    public static List<PathObject> clipObject(ROI roi, PathObject child) {
        if (roi == null)
            return Collections.singletonList(child);
        if (!childOnSamePlane(roi, child.getROI()))
            return Collections.emptyList();
        var geom = roi.getGeometry();
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

    public static List<PathObject> clipObjectAndSplit(ROI roi, PathObject child) {
        if (roi == null)
            return Collections.singletonList(child);
        if (!childOnSamePlane(roi, child.getROI()))
            return Collections.emptyList();
        var geom = roi.getGeometry();
        var childGeom = child.getROI().getGeometry();
        var geomOutput = GeometryTools.homogenizeGeometryCollection(geom.intersection(childGeom));
        if (geomOutput.isEmpty() || geomOutput.getDimension() < childGeom.getDimension())
            return Collections.emptyList();
        else if (childGeom.equals(geomOutput))
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
