package qupath.imagej.gui.macro;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.gui.macro.downsamples.DownsampleCalculator;
import qupath.imagej.gui.macro.downsamples.FixedDownsampleCalculator;
import qupath.imagej.gui.macro.downsamples.MaxDimensionDownsampleCalculator;
import qupath.imagej.gui.macro.downsamples.PixelCalibrationDownsampleCalculator;
import qupath.imagej.tools.IJTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.gui.images.servers.ChannelDisplayTransformServer;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupathj.QuPath_Send_Overlay_to_QuPath;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NewImageJMacroRunner {

    private static final Logger logger = LoggerFactory.getLogger(NewImageJMacroRunner.class);

    private enum PathObjectType {
        ANNOTATION, DETECTION, TILE
    }

    public static class MacroParameters {

        private List<ChannelDisplayInfo> channels;
        private String macroText;

        private DownsampleCalculator downsample = new MaxDimensionDownsampleCalculator(1024);
        private boolean setRoi = true;
        private boolean setOverlay = true;

        // Result parameters
        private boolean clearChildObjects = true;
        private PathObjectType activeRoiObjectType = PathObjectType.ANNOTATION;
        private PathObjectType overlayRoiObjectType = PathObjectType.DETECTION;

        private MacroParameters() {}

        private MacroParameters(MacroParameters params) {
            channels = params.channels == null ? Collections.emptyList() : List.copyOf(params.channels);
            macroText = params.macroText;
            setRoi = params.setRoi;
            setOverlay = params.setOverlay;
            clearChildObjects = params.clearChildObjects;
            activeRoiObjectType = params.activeRoiObjectType;
            overlayRoiObjectType = params.overlayRoiObjectType;
        }

        public List<ChannelDisplayInfo> getChannels() {
            return channels == null ? Collections.emptyList() : channels;
        }

        public String getMacroText() {
            return macroText;
        }

        public DownsampleCalculator getDownsample() {
            return downsample;
        }

        public boolean doSetRoi() {
            return setRoi;
        }

        public boolean doSetOverlay() {
            return setOverlay;
        }

        public boolean doRemoveChildObjects() {
            return clearChildObjects;
        }

        public Function<ROI, PathObject> getActiveRoiToObjectFunction() {
            return getObjectFunction(activeRoiObjectType);
        }

        public Function<ROI, PathObject> getOverlayRoiToObjectFunction() {
            return getObjectFunction(overlayRoiObjectType);
        }

        private Function<ROI, PathObject> getObjectFunction(PathObjectType type) {
            return switch (type) {
                case ANNOTATION -> PathObjects::createAnnotationObject;
                case DETECTION -> PathObjects::createDetectionObject;
                case TILE -> PathObjects::createTileObject;
            };
        }

    }

    public static class Builder {

        private MacroParameters params = new MacroParameters();

        private Builder() {}

        public Builder macroText(String macroText) {
            params.macroText = macroText;
            return this;
        }

        public Builder macro(File file) throws IOException {
            return macro(file.toPath());
        }

        public Builder macro(Path path) throws IOException {
            var text = Files.readString(path, StandardCharsets.UTF_8);
            return macroText(text);
        }

        public Builder setImageJRoi() {
            return setImageJRoi(true);
        }

        public Builder setImageJRoi(boolean doSet) {
            params.setRoi = doSet;
            return this;
        }

        public Builder setImageJOverlay() {
            return setImageJOverlay(true);
        }

        public Builder setImageJOverlay(boolean doSet) {
            params.setOverlay = doSet;
            return this;
        }

        public Builder roiToDetection() {
            params.activeRoiObjectType = PathObjectType.DETECTION;
            return this;
        }

        public Builder roiToAnnotation() {
            params.activeRoiObjectType = PathObjectType.ANNOTATION;
            return this;
        }

        public Builder roiToTile() {
            params.activeRoiObjectType = PathObjectType.TILE;
            return this;
        }

        public Builder overlayToAnnotations() {
            params.overlayRoiObjectType = PathObjectType.ANNOTATION;
            return this;
        }

        public Builder overlayToTiles() {
            params.overlayRoiObjectType = PathObjectType.TILE;
            return this;
        }

        public Builder overlayToDetections() {
            params.overlayRoiObjectType = PathObjectType.DETECTION;
            return this;
        }

        public Builder fixedDownsample(double downsample) {
            return downsample(new FixedDownsampleCalculator(downsample));
        }

        public Builder maxDimension(double maxDim) {
            return downsample(new MaxDimensionDownsampleCalculator(maxDim));
        }

        public Builder pixelSizeMicrons(double pixelSizeMicrons) {
            var cal = new PixelCalibration.Builder()
                    .pixelSizeMicrons(pixelSizeMicrons, pixelSizeMicrons)
                    .build();
            return pixelSize(cal);
        }

        public Builder pixelSize(PixelCalibration targetCalibration) {
            return downsample(new PixelCalibrationDownsampleCalculator(targetCalibration));
        }

        public Builder downsample(DownsampleCalculator downsample) {
            params.downsample = downsample;
            return this;
        }

        public NewImageJMacroRunner build() {
            return fromParams(params);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    public static NewImageJMacroRunner fromParams(MacroParameters params) {
        if (params == null)
            throw new IllegalArgumentException("Macro parameters cannot be null");
        if (params.getMacroText() == null)
            throw new IllegalArgumentException("Macro text cannot be null");
        return new NewImageJMacroRunner(params);
    }

    public static NewImageJMacroRunner fromJson(String json) {
        return fromParams(GsonTools.getInstance().fromJson(json, MacroParameters.class));
    }

    public static NewImageJMacroRunner fromMap(Map<String, ?> paramMap) {
        return fromJson(GsonTools.getInstance().toJson(paramMap));
    }


    private final MacroParameters params;

    public NewImageJMacroRunner(MacroParameters params) {
        this.params = params;
    }

    public void runMacro(final ImageData<BufferedImage> imageData, final PathObject pathObject) {

        // Don't try if interrupted
        if (Thread.currentThread().isInterrupted()) {
            logger.warn("Skipping macro for {} - thread interrupted", pathObject);
            return;
        }

        PathImage<ImagePlus> pathImage;

        // Extract parameters
        ROI pathROI = pathObject.getROI();

        ImageServer<BufferedImage> server = getServer(imageData);

        ImageRegion region = pathROI == null ? RegionRequest.createInstance(server) : ImageRegion.createInstance(pathROI);
        double downsampleFactor = params.getDownsample().getDownsample(server, region);
        RegionRequest request = RegionRequest.createInstance(server.getPath(), downsampleFactor, region);

        // Check the size of the region to extract - abort if it is too large of if ther isn't enough RAM
        try {
            IJTools.isMemorySufficient(request, imageData);
        } catch (Exception e1) {
            Dialogs.showErrorMessage("ImageJ macro error", e1.getMessage());
            return;
        }

        try {
            boolean sendROI = params.doSetRoi();
            if (params.doSetOverlay())
                pathImage = IJExtension.extractROIWithOverlay(server, pathObject, imageData.getHierarchy(), request, sendROI, null);
            else
                pathImage = IJExtension.extractROI(server, pathObject, request, sendROI);
        } catch (IOException e) {
            logger.error("Unable to extract image region {}", region, e);
            return;
        }

        // Determine a sensible argument to pass
        String argument;
        if (pathObject instanceof TMACoreObject || !pathObject.hasROI())
            argument = pathObject.getDisplayedName();
        else
            argument = String.format("Region (%d, %d, %d, %d)", region.getX(), region.getY(), region.getWidth(), region.getHeight());

        // Actually run the macro
        final ImagePlus imp = pathImage.getImage();
        imp.setProperty("QuPath region", argument);
        WindowManager.setTempCurrentImage(imp);
        IJExtension.getImageJInstance(); // Ensure we've requested an instance, since this also loads any required extra plugins

        try {
            boolean cancelled = false;
            ImagePlus impResult = null;
            try {
                IJ.redirectErrorMessages();
                Interpreter interpreter = new Interpreter();
                impResult = interpreter.runBatchMacro(params.getMacroText(), imp);

                // If we had an error, return
                if (interpreter.wasError()) {
                    Thread.currentThread().interrupt();
                    return;
                }

                // Get the resulting image, if available
                if (impResult == null)
                    impResult = WindowManager.getCurrentImage();
            } catch (RuntimeException e) {
                logger.error("Exception running ImageJ macro: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
                cancelled = true;
            } finally {
                WindowManager.setTempCurrentImage(null);
            }
            if (cancelled)
                return;


            // Get the current image when the macro has finished - which may or may not be the same as the original
            if (impResult == null)
                impResult = imp;


            boolean changes = false;
            if (params.doRemoveChildObjects()) {
                pathObject.clearChildObjects();
                changes = true;
            }
            var activeRoiToObject = params.getActiveRoiToObjectFunction();
            if (activeRoiToObject != null && impResult.getRoi() != null) {
                Roi roi = impResult.getRoi();
                Calibration cal = impResult.getCalibration();
                var pathObjectNew = createNewObject(activeRoiToObject, roi, cal, downsampleFactor, region.getImagePlane(), pathROI);
                if (pathObjectNew != null) {
                    pathObject.addChildObject(pathObjectNew);
                    changes = true;
                }
            }

            var overlayRoiToObject = params.getOverlayRoiToObjectFunction();
            if (overlayRoiToObject != null && impResult.getOverlay() != null) {
                var overlay = impResult.getOverlay();
                List<PathObject> childObjects = QuPath_Send_Overlay_to_QuPath.createObjectsFromROIs(imp,
                        List.of(overlay.toArray()), downsampleFactor, overlayRoiToObject, true, region.getImagePlane());
                if (!childObjects.isEmpty()) {
                    pathObject.addChildObjects(childObjects);
                    changes = true;
                }
            }

            if (changes) {
                FXUtils.runOnApplicationThread(() -> imageData.getHierarchy().fireHierarchyChangedEvent(null));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }


    private ImageServer<BufferedImage> getServer(ImageData<BufferedImage> imageData) {
        var server = imageData.getServer();
        var channels = params.getChannels();
        if (channels.isEmpty()) {
            return server;
        } else {
            return ChannelDisplayTransformServer.createColorTransformServer(server, channels);
        }
    }


    private static PathObject createNewObject(Function<ROI, PathObject> creator, Roi roi, Calibration cal,
                                              double downsampleFactor, ImagePlane plane, ROI clipROI) {
        ROI newROI = IJTools.convertToROI(roi, cal.xOrigin, cal.yOrigin, downsampleFactor, plane);
        if (newROI != null && clipROI != null && RoiTools.isShapeROI(clipROI) && RoiTools.isShapeROI(newROI)) {
            newROI = RoiTools.combineROIs(clipROI, newROI, RoiTools.CombineOp.INTERSECT);
        }
        if (newROI == null || newROI.isEmpty())
            return null;
        var pathObjectNew = creator.apply(newROI);
        if (pathObjectNew != null) {
            IJTools.calibrateObject(pathObjectNew, roi);
            pathObjectNew.setLocked(true);
            return pathObjectNew;
        } else {
            return null;
        }
    }

    /**
     * Function to create an annotation object from any {@link qupath.lib.roi.PointsROI}, and
     * detection object from any other {@link ROI}.
     * @param roi the input ROI (must not be null)
     * @return a new object with the specified ROI
     */
    public static PathObject createDetectionOrPointAnnotation(ROI roi) {
        if (roi.isPoint())
            return PathObjects.createAnnotationObject(roi);
        else
            return PathObjects.createDetectionObject(roi);
    }


}
