package qupath.lib.objects.hierarchy;

import org.junit.jupiter.api.Test;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class TestPathObjectHierarchy {

    @Test
    public void testGetObjects() {

        // Define some boundaries to create objects
        var region1 = ImageRegion.createInstance(0, 0, 10, 20, 0, 0);
        var region2 = ImageRegion.createInstance(1000, 1000, 20, 10, 0, 0);

        var regions = List.of(region1, region2);

        var hierarchy = new PathObjectHierarchy();
        var cellRectangles = createObjects(regions, ROIs::createRectangleROI, r -> PathObjects.createCellObject(r, null));
        var cellEllipses = createObjects(regions, ROIs::createEllipseROI, r -> PathObjects.createCellObject(r, null));

        var tileRectangles = createObjects(regions, ROIs::createRectangleROI, PathObjects::createTileObject);
        var tileEllipses = createObjects(regions, ROIs::createEllipseROI, PathObjects::createTileObject);

        var detectionRectangles = createObjects(regions, ROIs::createRectangleROI, PathObjects::createDetectionObject);
        var detectionEllipses = createObjects(regions, ROIs::createEllipseROI, PathObjects::createDetectionObject);

        var annotationRectangles = createObjects(regions, ROIs::createRectangleROI, PathObjects::createAnnotationObject);
        var annotationEllipses = createObjects(regions, ROIs::createEllipseROI, PathObjects::createAnnotationObject);

        var defaultPlaneObjects = Stream.of(
                cellEllipses,
                cellRectangles,
                tileEllipses,
                tileRectangles,
                detectionEllipses,
                detectionRectangles,
                annotationEllipses,
                annotationRectangles
        ).flatMap(List::stream).toList();
        var z1Objects = defaultPlaneObjects.stream().map(p -> updateZ(p, 1)).toList();
        var t1Objects = defaultPlaneObjects.stream().map(p -> updateT(p, 1)).toList();

        hierarchy.addObjects(defaultPlaneObjects);
        hierarchy.addObjects(z1Objects);
        hierarchy.addObjects(t1Objects);
    }

    private static List<PathObject> createObjects(Collection<? extends ImageRegion> regions, Function<ImageRegion, ROI> roiCreator, Function<ROI, PathObject> objectCreator) {
        return regions.stream().map(r -> objectCreator.apply(roiCreator.apply(r))).toList();
    }

    private static PathObject updateZ(PathObject pathObject, int z) {
        return PathObjectTools.updatePlane(
                pathObject,
                ImagePlane.getPlane(z, pathObject.getROI().getT()),
                false, true);
    }

    private static PathObject updateT(PathObject pathObject, int t) {
        return PathObjectTools.updatePlane(
                pathObject,
                ImagePlane.getPlane(pathObject.getROI().getZ(), t),
                false, true);
    }

}
