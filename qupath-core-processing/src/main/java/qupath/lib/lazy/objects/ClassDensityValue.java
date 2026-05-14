package qupath.lib.lazy.objects;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.function.Function;

class ClassDensityValue extends DetectionCountValue {

    private final ImageData<?> imageData;
    private final PathClass pathClass;

    ClassDensityValue(Function<PathObject, DetectionPathClassCounts> countsFunction,
                      ImageData<?> imageData, PathClass pathClass) {
        super(countsFunction);
        this.imageData = imageData;
        this.pathClass = pathClass;
    }

    @Override
    public String getHelpText() {
        return "Density of detections with classification '" + pathClass + "' inside the selected objects";
    }

    @Override
    public String getName() {
        if (imageData != null && imageData.getServerMetadata().getPixelCalibration().hasPixelSizeMicrons())
            return String.format("Num %s per mm^2", pathClass.toString());
        else
            return String.format("Num %s per px^2", pathClass.toString());
    }

    @Override
    public Number computeValue(final PathObject pathObject, DetectionPathClassCounts counts) {
        // Only return density measurements for annotations
        if (pathObject.isAnnotation() || (pathObject.isTMACore() && pathObject.nChildObjects() == 1)) {
            return computeDensityPerMM(imageData, counts, pathObject, pathClass);
        }
        return Double.NaN;
    }

    private static double computeDensityPerMM(final ImageData<?> imageData, final DetectionPathClassCounts counts,
                                              final PathObject pathObject, final PathClass pathClass) {
        // If we have a TMA core, look for a single annotation inside
        // If we don't have that, we can't return counts since it's ambiguous where the
        // area should be coming from
        PathObject pathObjectTemp = pathObject;
        if (pathObject instanceof TMACoreObject) {
            var children = pathObject.getChildObjectsAsArray();
            if (children.length != 1)
                return Double.NaN;
            pathObjectTemp = children[0];
        }
        // We need an annotation to get a meaningful area
        if (pathObjectTemp == null || !(pathObjectTemp.isAnnotation() || pathObjectTemp.isRootObject()))
            return Double.NaN;

        int n = counts.getCountForAncestor(pathClass);
        ROI roi = pathObjectTemp.getROI();
        // For the root, we can measure density only for 2D images of a single time-point
        var serverMetadata = imageData.getServerMetadata();
        if (pathObjectTemp.isRootObject() && serverMetadata.getSizeZ() == 1 && serverMetadata.getSizeT() == 1)
            roi = ROIs.createRectangleROI(0, 0, serverMetadata.getWidth(), serverMetadata.getHeight(), ImagePlane.getDefaultPlane());

        if (roi != null && roi.isArea()) {
            double pixelWidth = 1;
            double pixelHeight = 1;
            PixelCalibration cal = serverMetadata == null ? null : serverMetadata.getPixelCalibration();
            if (cal != null && cal.hasPixelSizeMicrons()) {
                pixelWidth = cal.getPixelWidthMicrons() / 1000;
                pixelHeight = cal.getPixelHeightMicrons() / 1000;
            }
            return n / roi.getScaledArea(pixelWidth, pixelHeight);
        }
        return Double.NaN;
    }

}
