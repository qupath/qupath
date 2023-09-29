package qupath.lib.pixels;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.BinaryProcessor;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.tools.IJTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PixelProcessorImageJ extends PixelProcessor<ImagePlus, ByteProcessor, Object> {

    private double downsample = 1.0;
    private boolean setRoi = true;
    private boolean clearPreviousObjects = true;
    private boolean splitDetectedObjects = false;

    public PixelProcessorImageJ(Processor<ImagePlus, ByteProcessor, Object> processor) {
        super(processor);
    }

    @Override
    protected ImagePlus getImage(ImageData<BufferedImage> imageData, RegionRequest request,
                                 PathObject pathObject) throws IOException {
        var server = imageData.getServer();
        var pathImage = IJTools.convertToImagePlus(server, request);
        var imp = pathImage.getImage();
        if (setRoi && pathObject.hasROI())
            imp.setRoi(IJTools.convertToIJRoi(pathObject.getROI(), pathImage));
        return imp;
    }

    @Override
    protected ByteProcessor getMask(ImageData<BufferedImage> imageData, RegionRequest request,
                                    PathObject pathObject, ImagePlus image) {
        var imgMask = BufferedImageTools.createROIMask(image.getWidth(), image.getHeight(), pathObject.getROI(), request);
        return (ByteProcessor)IJTools.convertToImageProcessor(imgMask, 0);
    }

    @Override
    protected void handleOutput(ImageData<BufferedImage> imageData, RegionRequest request, PathObject pathObject,
                                ImagePlus image, Object output) {
        if (output != null) {
            var newObjects = outputToObjects(imageData, pathObject, image, output);
            if (newObjects != null)
                pathObject.addChildObjects(newObjects);
        }
    }

    protected Collection<? extends PathObject> outputToObjects(ImageData<BufferedImage> imageData, PathObject pathObject, ImagePlus image, Object output) {
        if (output == null)
            return Collections.emptyList();
        else if (output instanceof Overlay overlay)
            return handleOverlayOutput(imageData, pathObject, image, overlay);
        else if (output instanceof Roi roi)
            return handleRoiListOutput(imageData, pathObject, image, Collections.singletonList(roi));
        else if (output instanceof Roi[] roiArray)
            return handleRoiListOutput(imageData, pathObject, image, Arrays.asList(roiArray));
        else if (output instanceof List<?> roiList)
            return handleRoiListOutput(imageData, pathObject, image, (List<Roi>)roiList);
        else if (output instanceof ImagePlus imp)
            return handleImagePlusOutput(imageData, pathObject, image, imp);
        else if (output instanceof ImageProcessor ip)
            return handleImageProcessorOutput(imageData, pathObject, image, ip);
        else if (output instanceof String classification) {
            return handleStringOutput(imageData, pathObject, image, classification);
        } else
            throw new IllegalArgumentException("Unknown output type: " + output.getClass().getName());
    }

    protected Collection<? extends PathObject> handleStringOutput(ImageData<BufferedImage> imageData,
                                                                  PathObject pathObject, ImagePlus image,
                                                                  String classification) {
        if (classification == null || classification.isEmpty())
            pathObject.resetPathClass();
        else
            pathObject.setPathClass(PathClass.fromString(classification));
        return Collections.emptyList();
    }

    protected Collection<? extends PathObject> handleOverlayOutput(ImageData<BufferedImage> imageData,
                                                                   PathObject pathObject, ImagePlus image, Overlay overlay) {
        if (overlay == null)
            return Collections.emptyList();
        else
            return handleRoiListOutput(imageData, pathObject, image, Arrays.asList(overlay.toArray()));
    }

    protected Collection<? extends PathObject> handleImagePlusOutput(ImageData<BufferedImage> imageData,
                                                                     PathObject pathObject, ImagePlus image, ImagePlus imp) {
        var results = handleImageProcessorOutput(imageData, pathObject, image, imp.getProcessor());
        imp.close();
        return results;
    }

    protected Collection<? extends PathObject> handleImageProcessorOutput(ImageData<BufferedImage> imageData,
                                                                          PathObject pathObject, ImagePlus image,
                                                                          ImageProcessor ip) {
        if (ip instanceof BinaryProcessor && !ip.isThreshold())
            ip.setThreshold(0.5, Double.MAX_VALUE, ImageProcessor.NO_LUT_UPDATE);
        if (ip.isThreshold()) {
            var roi = new ThresholdToSelection().convert(ip);
            return handleRoiListOutput(imageData, pathObject, image, Collections.singletonList(roi));
        } if (ip instanceof ByteProcessor || ip instanceof ShortProcessor) {
            var rois = Arrays.stream(RoiLabeling.labelsToConnectedROIs(ip, (int)Math.round(ip.getStatistics().max) + 1))
                    .filter(r -> r != null)
                    .toList();
            return handleRoiListOutput(imageData, pathObject, image, rois);
        }
        return Collections.emptyList();
    }

    protected Collection<? extends PathObject> handleRoiListOutput(ImageData<BufferedImage> imageData,
                                                                   PathObject pathObject, ImagePlus image, List<Roi> output) {
        if (output == null)
            return null;
        if (clearPreviousObjects)
            pathObject.clearChildObjects();
        return output.stream()
                        .map(roi -> IJTools.convertToPathObject(roi, downsample, r -> PathObjects.createDetectionObject(r), image))
                           .flatMap(p -> splitDetectedObjects ? PixelProcessorUtils.clipObjectAndSplit(pathObject.getROI(), p).stream() :
                                   PixelProcessorUtils.clipObject(pathObject.getROI(), p).stream())
                                .toList();
    }

}
