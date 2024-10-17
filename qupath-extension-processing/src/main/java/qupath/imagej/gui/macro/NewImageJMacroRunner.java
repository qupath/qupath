package qupath.imagej.gui.macro;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.gui.macro.downsamples.DownsampleCalculator;
import qupath.imagej.gui.macro.downsamples.DownsampleCalculators;
import qupath.imagej.tools.IJTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.plugins.TaskRunner;
import qupath.lib.plugins.TaskRunnerUtils;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.LoggingTools;
import qupath.lib.scripting.QP;
import qupathj.QuPath_Send_Overlay_to_QuPath;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

public class NewImageJMacroRunner {

    private static final Logger logger = LoggerFactory.getLogger(NewImageJMacroRunner.class);

    public enum PathObjectType {
        NONE, ANNOTATION, DETECTION, TILE, CELL, TMA_CORE
    }

    public enum RunForObjects {
        IMAGE, SELECTED, ANNOTATIONS, DETECTIONS, TILES, CELLS, TMA_CORES
    }

    private final MacroParameters params;

    public NewImageJMacroRunner(MacroParameters params) {
        this.params = params;
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

    public void run() {
        run(QP.getCurrentImageData());
    }

    public void run(final ImageData<BufferedImage> imageData) {
        if (imageData == null)
            throw new IllegalArgumentException("No image data available");
        var selected = List.copyOf(imageData.getHierarchy().getSelectionModel().getSelectedObjects());
        if (selected.isEmpty())
            selected = List.of(imageData.getHierarchy().getRootObject());
        run(imageData, selected);
    }

    public void run(final ImageData<BufferedImage> imageData, final Collection<? extends PathObject> pathObjects) {
        var taskRunner = params.getTaskRunner();

        List<Runnable> tasks = new ArrayList<>();
        for (var parent : pathObjects) {
            tasks.add(() -> run(imageData, parent));
        }
        taskRunner.runTasks("ImageJ scripts", tasks);

        if (params.doAddToWorkflow()) {
            addScriptToWorkflow(imageData, pathObjects);
        }
    }

    private void run(final ImageData<BufferedImage> imageData, final PathObject pathObject) {

        if (imageData == null)
            throw new IllegalArgumentException("No image data available");

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

                String script = params.getMacroText();
                var engine = getScriptEngine();
                if (engine != null) {
                    // We have a script engine (probably for Groovy)
                    try {
                        var context = new SimpleScriptContext();
                        var writer = LoggingTools.createLogWriter(logger, Level.INFO);
                        var errorWriter = LoggingTools.createLogWriter(logger, Level.ERROR);
                        context.setWriter(writer);
                        context.setErrorWriter(errorWriter);
                        engine.setContext(context);
                        engine.eval(script);
                    } catch (Exception e) {
                        Dialogs.showErrorNotification("ImageJ script", e);
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    // ImageJ macro
                    Interpreter interpreter = new Interpreter();
                    impResult = interpreter.runBatchMacro(script, imp);

                    // If we had an error, return
                    if (interpreter.wasError()) {
                        Thread.currentThread().interrupt();
                        return;
                    }
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
                    pathObject.setLocked(true); // Lock if we add anything
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
                    pathObject.setLocked(true); // Lock if we add anything
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


    private ScriptEngine getScriptEngine() {
        if (params.scriptEngine == null || params.scriptEngine.equalsIgnoreCase("imagej") || params.scriptEngine.equalsIgnoreCase("macro"))
            return null;
        return new ScriptEngineManager().getEngineByName(params.scriptEngine);
    }



    private void addScriptToWorkflow(ImageData<?> imageData, Collection<? extends PathObject> parents) {
        var sb = new StringBuilder();
        if (!parents.isEmpty()) {
            if (parents.stream().allMatch(PathObject::isAnnotation)) {
                sb.append("// selectAnnotations()\n");
            } else if (parents.stream().allMatch(PathObject::isDetection)) {
                sb.append("// selectDetections()\n");
            } else if (parents.stream().allMatch(PathObject::isTile)) {
                sb.append("// selectTiles()\n");
            } else if (parents.stream().allMatch(PathObject::isTMACore)) {
                sb.append("// selectTMACores()\n");
            } else if (parents.stream().allMatch(PathObject::isCell)) {
                sb.append("// selectCells()\n");
            }
        }
        var gson = GsonTools.getInstance();
        var json = gson.toJson(params);
        var obj = gson.fromJson(json, JsonObject.class);
        var map = gson.fromJson(json, Map.class);

        sb.append(NewImageJMacroRunner.class.getName()).append(".fromMap(");
        sb.append(toGroovy(obj));
//        boolean isFirst = true;
//        for (var entry : obj.asMap().entrySet()) {
//            if (!isFirst) {
//                sb.append(",");
//            }
//            sb.append("\n    ");
//            sb.append(entry.getKey());
//            sb.append(": ");
//            appendValue(sb, entry.getValue());
//            isFirst = false;
//        }
        sb.append(").run()");

        var workflowScript = sb.toString();
        imageData.getHistoryWorkflow().addStep(
                new DefaultScriptableWorkflowStep("ImageJ script", map, workflowScript)
        );

        logger.info(sb.toString());
    }

    private static String toGroovy(JsonElement element) {
        var sb = new StringBuilder();
        appendValue(sb, element);
        return sb.toString();
    }


    private static String appendValue(StringBuilder sb, JsonElement val) {
        switch (val) {
            case JsonPrimitive primitive -> {
                if (primitive.isString()) {
                    String str = val.getAsString();
                    String quote = "\"";
                    if (str.contains(quote))
                        quote = "\"\"\"";
                    sb.append(quote).append(primitive.getAsString()).append(quote);
                } else
                    sb.append(primitive.getAsString());
            }
            case JsonArray array -> {
                for (int i = 0; i < array.size(); i++) {
                    sb.append(appendValue(sb, array.get(i)));
                }
            }
            case JsonObject obj -> {
                sb.append("[");
                boolean isFirst = true;
                for (var entry : obj.asMap().entrySet()) {
                    if (!isFirst) {
                        sb.append(", ");
                    }
                    sb.append(entry.getKey()).append(": ");
                    appendValue(sb, entry.getValue());
                    isFirst = false;
                }
                sb.append("]");
            }
            case null, default -> {
                sb.append("null");
            }
        }
        return sb.toString();
    }


    private ImageServer<BufferedImage> getServer(ImageData<BufferedImage> imageData) {
        var server = imageData.getServer();
        var channels = params.getChannels();
        if (channels.isEmpty()) {
            return server;
        } else {
            return new TransformedServerBuilder(server)
                    .applyColorTransforms(channels.toArray(ColorTransforms.ColorTransform[]::new))
                    .build();
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


    public static class MacroParameters {

        private String macroText;
        private List<ColorTransforms.ColorTransform> channels;

        private DownsampleCalculator downsample = DownsampleCalculators.maxDimension(1024);
        private boolean setRoi = true;
        private boolean setOverlay = false;

        // Result parameters
        private boolean clearChildObjects = false;
        private PathObjectType activeRoiObjectType = null;
        private PathObjectType overlayRoiObjectType = null;

        private String scriptEngine;

        private boolean addToWorkflow = false;

        private int nThreads = -1;
        private transient TaskRunner taskRunner;

        private MacroParameters() {}

        private MacroParameters(MacroParameters params) {
            // Store null since then it'll be skipped with json serialization
            channels = params.channels == null || params.channels.isEmpty() ? null : List.copyOf(params.channels);
            macroText = params.macroText;
            setRoi = params.setRoi;
            setOverlay = params.setOverlay;
            clearChildObjects = params.clearChildObjects;
            activeRoiObjectType = params.activeRoiObjectType;
            overlayRoiObjectType = params.overlayRoiObjectType;
        }

        public List<ColorTransforms.ColorTransform> getChannels() {
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

        public boolean doAddToWorkflow() {
            return addToWorkflow;
        }

        public String getScriptEngineName() {
            return scriptEngine;
        }

        public TaskRunner getTaskRunner() {
            if (taskRunner == null)
                return TaskRunnerUtils.getDefaultInstance().createTaskRunner(nThreads);
            else
                return taskRunner;
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
                case CELL -> r -> PathObjects.createCellObject(r, null);
                case NONE -> null;
                case TMA_CORE -> throw new IllegalArgumentException("TMA core is not a valid object type!");
            };
        }

    }

    public static class Builder {

        // Cache of script engine names from file extensions, so that we don't need to
        // do a more expensive check
        private static Map<String, String> scriptEngineNameCache = new HashMap<>();

        static {
            scriptEngineNameCache.put(null, null);
            scriptEngineNameCache.put(".ijm", null);
            scriptEngineNameCache.put(".txt", null);
            scriptEngineNameCache.put(".groovy", "groovy");
        }

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
            updateScriptEngineFromFilename(path.getFileName().toString());
            return macroText(text);
        }

        private void updateScriptEngineFromFilename(String name) {
            if (params.scriptEngine != null)
                return;
            var ext = GeneralTools.getExtension(name).orElse(null);
            if (ext != null) {
                var engineName = scriptEngineNameCache.computeIfAbsent(ext.toLowerCase(), this::scriptEngineForExtension);
                if (engineName != null) {
                    params.scriptEngine = engineName;
                }
            }
        }

        private String scriptEngineForExtension(String ext) {
            var engine = new ScriptEngineManager().getEngineByExtension(ext);
            if (engine != null)
                return engine.getFactory().getEngineName();
            else
                return null;
        }

        public Builder scriptEngine(String scriptEngine) {
            params.scriptEngine = scriptEngine;
            return this;
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
            return roiToObject(PathObjectType.DETECTION);
        }

        public Builder roiToAnnotation() {
            return roiToObject(PathObjectType.ANNOTATION);
        }

        public Builder roiToTile() {
            return roiToObject(PathObjectType.TILE);
        }

        public Builder roiToObject(PathObjectType type) {
            params.activeRoiObjectType = type;
            return this;
        }

        public Builder overlayToAnnotations() {
            return overlayToObjects(PathObjectType.TILE);
        }

        public Builder overlayToTiles() {
            return overlayToObjects(PathObjectType.TILE);
        }

        public Builder overlayToDetections() {
            return overlayToObjects(PathObjectType.DETECTION);
        }

        public Builder overlayToObjects(PathObjectType type) {
            params.overlayRoiObjectType = type;
            return this;
        }

        public Builder clearChildObjects() {
            return clearChildObjects(true);
        }

        public Builder clearChildObjects(boolean doClear) {
            params.clearChildObjects = doClear;
            return this;
        }

        public Builder addToWorkflow() {
            return addToWorkflow(true);
        }

        public Builder addToWorkflow(boolean doAdd) {
            params.addToWorkflow = doAdd;
            return this;
        }

        public Builder fixedDownsample(double downsample) {
            return downsample(DownsampleCalculators.fixedDownsample(downsample));
        }

        public Builder maxDimension(int maxDim) {
            return downsample(DownsampleCalculators.maxDimension(maxDim));
        }

        public Builder pixelSizeMicrons(double pixelSizeMicrons) {
            return downsample(DownsampleCalculators.pixelSizeMicrons(pixelSizeMicrons));
        }

        public Builder pixelSize(PixelCalibration targetCalibration) {
            return downsample(DownsampleCalculators.pixelSize(targetCalibration));
        }

        public Builder downsample(DownsampleCalculator downsample) {
            params.downsample = downsample;
            return this;
        }

        public Builder nThreads(int nThreads) {
            params.nThreads = nThreads;
            return this;
        }

        public Builder taskRunner(TaskRunner taskRunner) {
            params.taskRunner = taskRunner;
            return this;
        }

        public Builder channelIndices(int... inds) {
            return channels(
                    IntStream.of(inds).mapToObj(ColorTransforms::createChannelExtractor).toList()
            );
        }

        public Builder channelNames(String... names) {
            return channels(
                    Arrays.stream(names).map(ColorTransforms::createChannelExtractor).toList()
            );
        }

        public Builder channels(ColorTransforms.ColorTransform channel, ColorTransforms.ColorTransform... channels) {
            var list = new ArrayList<ColorTransforms.ColorTransform>();
            list.add(channel);
            Collections.addAll(list, channels);
            return channels(list);
        }

        public Builder channels(Collection<? extends ColorTransforms.ColorTransform> channels) {
            params.channels = channels == null ? null : List.copyOf(channels);
            return this;
        }

        public NewImageJMacroRunner build() {
            return fromParams(params);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

}
