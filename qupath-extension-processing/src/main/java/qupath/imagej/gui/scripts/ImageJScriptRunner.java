package qupath.imagej.gui.scripts;

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
import qupath.imagej.gui.ImagePlusProperties;
import qupath.imagej.gui.scripts.downsamples.DownsampleCalculator;
import qupath.imagej.gui.scripts.downsamples.DownsampleCalculators;
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
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Class to run ImageJ macros and scripts.
 * @since v0.6.0
 */
public class ImageJScriptRunner {

    private static final Logger logger = LoggerFactory.getLogger(ImageJScriptRunner.class);

    /**
     * Script engine name to use to represent an ImageJ macro.
     */
    private static final String ENGINE_NAME_MACRO = "macro";

    /**
     * Script engine name to use to represent a Groovy script.
     */
    private static final String ENGINE_NAME_GROOVY = "groovy";

    public enum PathObjectType {
        NONE, ANNOTATION, DETECTION, TILE, CELL, TMA_CORE
    }

    public enum RunForObjects {
        IMAGE, SELECTED, ANNOTATIONS, DETECTIONS, TILES, CELLS, TMA_CORES
    }

    private final ImageJScriptParameters params;

    public ImageJScriptRunner(ImageJScriptParameters params) {
        this.params = params;
    }

    /**
     * Create a script runner from the specified parameters.
     * @param params
     * @return
     */
    public static ImageJScriptRunner fromParams(ImageJScriptParameters params) {
        if (params == null)
            throw new IllegalArgumentException("Macro parameters cannot be null");
        if (params.getText() == null)
            throw new IllegalArgumentException("Macro text cannot be null");
        return new ImageJScriptRunner(params);
    }

    /**
     * Create a script runner from a JSON representation of {@link ImageJScriptParameters}.
     * @param json
     * @return
     */
    public static ImageJScriptRunner fromJson(String json) {
        return fromParams(GsonTools.getInstance().fromJson(json, ImageJScriptParameters.class));
    }

    /**
     * Create a script runner from a map representation of {@link ImageJScriptParameters}.
     * <p>
     * This method is mostly available for convenience when writing a Groovy script.
     * @param paramMap
     * @return
     */
    public static ImageJScriptRunner fromMap(Map<String, ?> paramMap) {
        return fromJson(GsonTools.getInstance().toJson(paramMap));
    }

    /**
     * Run the script for the 'current' image data, as requested from {@link QP}.
     * @see #run()
     */
    public void run() {
        run(QP.getCurrentImageData());
    }

    /**
     * Run the script for the specified image data.
     * @see #run(ImageData)
     */
    public void run(final ImageData<BufferedImage> imageData) {
        if (imageData == null)
            throw new IllegalArgumentException("No image data available");
        run(imageData, getObjectsToProcess(imageData.getHierarchy()));
    }

    /**
     * Test the script for the 'current' image data, as requested from {@link QP}.
     * @see #run()
     * @see #test(ImageData)
     */
    public void test() {
        test(QP.getCurrentImageData());
    }

    /**
     * Test the script for the specified image data.
     * <p>
     * Testing will run the script for no more than one parent object, and show the images within ImageJ.
     * This is different from calling {@link #run(ImageData)}, which can process multiple parent objects and
     * does not show images by default.
     * @see #test()
     * @see #run(ImageData)
     */
    public void test(final ImageData<BufferedImage> imageData) {
        if (imageData == null)
            throw new IllegalArgumentException("No image data available");
        run(imageData, getObjectsToProcess(imageData.getHierarchy()).getFirst(), true);
    }

    private void run(final ImageData<BufferedImage> imageData, final Collection<? extends PathObject> pathObjects) {
        var taskRunner = params.getTaskRunner();

        int[] idsBefore = WindowManager.getIDList();

        List<Runnable> tasks = new ArrayList<>();
        for (var parent : pathObjects) {
            tasks.add(() -> run(imageData, parent, false));
        }
        taskRunner.runTasks("ImageJ scripts", tasks);

        if (params.doAddToWorkflow()) {
            addScriptToWorkflow(imageData, pathObjects);
        }

        int[] idsAfter = WindowManager.getIDList();
        int nBefore = idsBefore == null ? 0 : idsBefore.length;
        int nAfter = idsAfter == null ? 0 : idsAfter.length;
        if (nBefore != nAfter) {
            logger.warn("Number of ImageJ images open before: {}, images open after: {}", nBefore, nAfter);
            int nClosed = closeNewImages(idsBefore, idsAfter);
            if (nClosed > 0)
                logger.debug("Closed {} ImageJ images", nClosed);
        }
    }

    /**
     * Close any new images that were opened when the macro was running.
     * @param idsBefore the image IDs from before the macro
     * @param idsAfter the image IDs from after the macro
     * @return the count of images that were closed
     */
    private static int closeNewImages(int[] idsBefore, int[] idsAfter) {
        int count = 0;
        if (idsAfter == null || idsAfter.length == 0)
            return count;
        for (int id : idsAfter) {
            if (idsBefore == null || Arrays.stream(idsBefore).noneMatch(i -> i == id)) {
                var impExtra = WindowManager.getImage(id);
                if (impExtra != null) {
                    impExtra.changes = false;
                    impExtra.close();
                    count++;
                }
            }
        }
        return count;
    }

    private List<PathObject> getObjectsToProcess(PathObjectHierarchy hierarchy) {
        var selected = List.copyOf(hierarchy.getSelectionModel().getSelectedObjects());
        if (selected.isEmpty())
           return List.of(hierarchy.getRootObject());
        else
            return selected;
    }

    private void run(final ImageData<BufferedImage> imageData, final PathObject pathObject, boolean isTest) {

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

        // Set some useful properties
        final ImagePlus imp = pathImage.getImage();
        ImagePlusProperties.setBackgroundProperty(imp, imageData.getImageType());
        ImagePlusProperties.setTypeProperty(imp, imageData.getImageType());
        ImagePlusProperties.setRegionProperty(imp, region);

        // Actually run the macro
        WindowManager.setTempCurrentImage(imp);
        IJExtension.getImageJInstance(); // Ensure we've requested an instance, since this also loads any required extra plugins

        try {
            boolean cancelled = false;
            ImagePlus impResult = null;
            try {
                IJ.redirectErrorMessages();

                String script = params.getText();
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

                        if (isTest)
                            imp.show();
                        engine.eval(script);

                    } catch (Exception e) {
                        Dialogs.showErrorNotification("ImageJ script", e);
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    // ImageJ macro
                    Interpreter interpreter = new Interpreter();
                    if (isTest) {
                        imp.show();
                        interpreter.run(script);
                    } else {
                        impResult = interpreter.runBatchMacro(script, imp);
                    }

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
        if (params.scriptEngine == null ||
                params.scriptEngine.equalsIgnoreCase(ENGINE_NAME_MACRO) ||
                params.scriptEngine.equalsIgnoreCase("imagej"))
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

        sb.append(ImageJScriptRunner.class.getName()).append(".fromMap(");
        var groovyMap = toGroovy(obj);
        if (groovyMap.startsWith("[") && groovyMap.endsWith("]")) {
            groovyMap = groovyMap.substring(1, groovyMap.length()-1);
        }
        sb.append(groovyMap);
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

    /**
     * Class to store parameters used to run ImageJ macros or scripts from QuPath.
     */
    public static class ImageJScriptParameters {

        private String text;
        private List<ColorTransforms.ColorTransform> channels;

        private DownsampleCalculator downsample = DownsampleCalculators.maxDimension(1024);
        private boolean setRoi = true;
        private boolean setOverlay = false;
        private boolean closeOpenImages = false;

        // Result parameters
        private boolean clearChildObjects = false;
        private PathObjectType activeRoiObjectType = null;
        private PathObjectType overlayRoiObjectType = null;

        private String scriptEngine;

        private boolean addToWorkflow = false;

        private int nThreads = -1;
        private transient TaskRunner taskRunner;

        private ImageJScriptParameters() {}

        private ImageJScriptParameters(ImageJScriptParameters params) {
            // Store null since then it'll be skipped with json serialization
            channels = params.channels == null || params.channels.isEmpty() ? null : List.copyOf(params.channels);
            text = params.text;
            setRoi = params.setRoi;
            setOverlay = params.setOverlay;
            clearChildObjects = params.clearChildObjects;
            activeRoiObjectType = params.activeRoiObjectType;
            overlayRoiObjectType = params.overlayRoiObjectType;
        }

        public List<ColorTransforms.ColorTransform> getChannels() {
            return channels == null ? Collections.emptyList() : channels;
        }

        /**
         * Get the text of the macro or script.
         * @return
         */
        public String getText() {
            return text;
        }

        /**
         * Get the calculator used to determine how much to downsample image regions that will be send to ImageJ.
         * @return
         */
        public DownsampleCalculator getDownsample() {
            return downsample;
        }

        /**
         * Query whether the Roi should be set when an image is passed to ImageJ.
         * If true, the Roi represents the QuPath parent object.
         * @return
         * @see #doSetOverlay()
         */
        public boolean doSetRoi() {
            return setRoi;
        }

        /**
         * Query whether the Overlay should be set when an image is passed to ImageJ.
         * If true, the Overlay contains any QuPath objects within the field of view being sent (excluding the
         * parent object).
         * @return
         * @see #doSetRoi()
         */
        public boolean doSetOverlay() {
            return setOverlay;
        }

        /**
         * Query whether child objects should be removed from the parent object after the script is complete.
         * <p>
         * This is useful when adding new objects from the ImageJ Roi or Overlay, to ensure that existing objects
         * are removed first.
         * @return
         */
        public boolean doRemoveChildObjects() {
            return clearChildObjects;
        }

        /**
         * Query whether the script should be logged in the history of the ImageData.
         * <p>
         * This is useful if the macro should be run as part of a batch processing script in the future.
         * @return
         */
        public boolean doAddToWorkflow() {
            return addToWorkflow;
        }

        /**
         * Get the name of the script engine to use, or null if no script engine is specified.
         * <p>
         * By default, it is assumed that any script represents an ImageJ macro.
         * However, it is possible to use other JSR 223 script engines, if available.
         * @return
         */
        public String getScriptEngineName() {
            return scriptEngine;
        }

        /**
         * Get a task runner for running script tasks.
         * Note that this is a transient property - it is not retained if the parameters are saved.
         * @return
         */
        public TaskRunner getTaskRunner() {
            if (taskRunner == null)
                return TaskRunnerUtils.getDefaultInstance().createTaskRunner(nThreads);
            else
                return taskRunner;
        }

        /**
         * Get a function to convert an ImageJ active Roi into a QuPath object.
         * <p>
         * Note that only one Roi may be active at the end of the script.
         * If multiple Rois need to be returned, these should be added to an Overlay instead.
         * @return
         * @see #getOverlayRoiToObjectFunction()
         */
        public Function<ROI, PathObject> getActiveRoiToObjectFunction() {
            return getObjectFunction(activeRoiObjectType);
        }

        /**
         * Get a function to convert an ImageJ Roi on an Overlay into a QuPath object.
         * @return
         * @see #getActiveRoiToObjectFunction()
         */
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
            scriptEngineNameCache.put(null, ENGINE_NAME_MACRO);
            scriptEngineNameCache.put(".ijm", ENGINE_NAME_MACRO);
            scriptEngineNameCache.put(".txt", ENGINE_NAME_MACRO);
            scriptEngineNameCache.put(".groovy", ENGINE_NAME_GROOVY);
        }

        private ImageJScriptParameters params = new ImageJScriptParameters();

        private Builder() {}

        private Builder(ImageJScriptParameters params) {
            this.params = new ImageJScriptParameters(params);
        }

        /**
         * Specify the exact text for an ImageJ macro.
         * This is equivalent to {@link #text(String)} and also setting the scripting language to specify
         * that we have an ImageJ macro.
         * @param macroText
         * @return this builder
         * @see #scriptFile(File)
         * @see #scriptFile(Path)
         */
        public Builder macroText(String macroText) {
            text(macroText);
            return scriptEngine(ENGINE_NAME_MACRO);
        }

        /**
         * Specify the exact text for an ImageJ macro.
         * This is equivalent to {@link #text(String)} and also setting the scripting language to specify
         * that we have a Groovy script.
         * @param groovy
         * @return this builder
         * @see #scriptFile(File)
         * @see #scriptFile(Path)
         */
        public Builder groovyText(String groovy) {
            text(groovy);
            return scriptEngine(ENGINE_NAME_GROOVY);
        }

        /**
         * Specify the exact text for the script or macro.
         * @param script
         * @return this builder
         * @see #scriptFile(File)
         * @see #scriptFile(Path)
         */
        public Builder text(String script) {
            params.text = script;
            return this;
        }

        /**
         * Specify the path to a file containing the script or macro.
         * The file extension will be used to determine the scripting language (the default is to assume an ImageJ macro).
         * @param path
         * @return this builder
         * @throws IOException if the script cannot be read
         * @see #macroText(String)
         * @see #scriptFile(Path)
         */
        public Builder file(String path) throws IOException {
            return scriptFile(Paths.get(path));
        }

        /**
         * Specify a file containing the script or macro.
         * The file extension will be used to determine the scripting language (the default is to assume an ImageJ macro).
         * @param file
         * @return this builder
         * @throws IOException if the script cannot be read
         * @see #macroText(String)
         * @see #scriptFile(Path)
         */
        public Builder scriptFile(File file) throws IOException {
            return scriptFile(file.toPath());
        }

        /**
         * Specify a path to the script or macro.
         * The file extension will be used to determine the scripting language (the default is to assume an ImageJ macro).
         * @param path
         * @return this builder
         * @throws IOException if the script cannot be read
         * @see #macroText(String)
         * @see #scriptFile(File)
         */
        public Builder scriptFile(Path path) throws IOException {
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

        /**
         * Specify the name of any script engine to use.
         * By default, the script is assumed to be an ImageJ macro.
         * This parameter can be used to run a script written with another JSR 223-supported scripting language.
         * @param scriptEngine
         * @return this builder
         */
        public Builder scriptEngine(String scriptEngine) {
            params.scriptEngine = scriptEngine;
            return this;
        }

        /**
         * Set the ROI for the QuPath object being set as a Roi on the ImagePlus.
         * @return this builder
         */
        public Builder setImageJRoi() {
            return setImageJRoi(true);
        }

        /**
         * Optionally set the ROI for the QuPath object being set as a Roi on the ImagePlus.
         * @param doSet
         * @return this builder
         */
        public Builder setImageJRoi(boolean doSet) {
            params.setRoi = doSet;
            return this;
        }

        /**
         * Add any QuPath objects within the field of view to an ImageJ overlay.
         * @return this builder
         */
        public Builder setImageJOverlay() {
            return setImageJOverlay(true);
        }

        /**
         * Optionally add any QuPath objects within the field of view to an ImageJ overlay.
         * @param doSet
         * @return this builder
         */
        public Builder setImageJOverlay(boolean doSet) {
            params.setOverlay = doSet;
            return this;
        }

        /**
         * Request that any images left open after the macro are closed.
         * @param doClose
         * @return this builder
         */
        public Builder closeOpenImages(boolean doClose) {
            params.closeOpenImages = doClose;
            return this;
        }

        /**
         * Convert any active Roi at the end of the script to a QuPath detection object.
         * @return this builder
         */
        public Builder roiToDetection() {
            return roiToObject(PathObjectType.DETECTION);
        }

        /**
         * Convert any active Roi at the end of the script to a QuPath annotation object.
         * @return this builder
         */
        public Builder roiToAnnotation() {
            return roiToObject(PathObjectType.ANNOTATION);
        }

        /**
         * Convert any active Roi at the end of the script to a QuPath tile object.
         * @return this builder
         */
        public Builder roiToTile() {
            return roiToObject(PathObjectType.TILE);
        }

        /**
         * Convert any active Roi at the end of the script to the specified QuPath object type.
         * @return this builder
         */
        public Builder roiToObject(PathObjectType type) {
            params.activeRoiObjectType = type;
            return this;
        }

        /**
         * Convert Rois on the overlay of the current image at the end of the script to QuPath annotation objects.
         * @return this builder
         */
        public Builder overlayToAnnotations() {
            return overlayToObjects(PathObjectType.TILE);
        }

        /**
         * Convert Rois on the overlay of the current image at the end of the script to QuPath detection objects.
         * @return this builder
         */
        public Builder overlayToDetections() {
            return overlayToObjects(PathObjectType.DETECTION);
        }

        /**
         * Convert Rois on the overlay of the current image at the end of the script to QuPath tile objects.
         * @return this builder
         */
        public Builder overlayToTiles() {
            return overlayToObjects(PathObjectType.TILE);
        }

        /**
         * Convert Rois on the overlay of the current image at the end of the script to the specified QuPath object type.
         * @return this builder
         */
        public Builder overlayToObjects(PathObjectType type) {
            params.overlayRoiObjectType = type;
            return this;
        }

        /**
         * Request that the child objects are removed from any objects that are passed to the script runner.
         * This is usually desirable when adding new objects, to avoid duplicate objects being created by accident
         * if the script is run multiple times.
         * @return this builder
         */
        public Builder clearChildObjects() {
            return clearChildObjects(true);
        }

        /**
         * Optionally request that the child objects are removed from any objects that are passed to the script runner.
         * This is usually desirable when adding new objects, to avoid duplicate objects being created by accident
         * if the script is run multiple times.
         * @param doClear
         * @return this builder
         */
        public Builder clearChildObjects(boolean doClear) {
            params.clearChildObjects = doClear;
            return this;
        }

        /**
         * Request that the script is stored in the workflow history when it is run for an {@link ImageData}.
         * This is useful to enable the script to be run in the future from a 'regular' Groovy script in QuPath.
         * @return this builder
         */
        public Builder addToWorkflow() {
            return addToWorkflow(true);
        }

        /**
         * Optionally request that the script is stored in the workflow history when it is run for an {@link ImageData}.
         * This is useful to enable the script to be run in the future from a 'regular' Groovy script in QuPath.
         * @param doAdd
         * @return this builder
         */
        public Builder addToWorkflow(boolean doAdd) {
            params.addToWorkflow = doAdd;
            return this;
        }

        /**
         * Use a fixed downsample value when passing images to ImageJ.
         * @param downsample
         * @return this builder
         */
        public Builder fixedDownsample(double downsample) {
            return downsample(DownsampleCalculators.fixedDownsample(downsample));
        }

        /**
         * Resize images to have a width and height &leq; a specified value when passing images to ImageJ.
         * @param maxDim
         * @return this builder
         */
        public Builder maxDimension(int maxDim) {
            return downsample(DownsampleCalculators.maxDimension(maxDim));
        }

        /**
         * Resize images to have a target pixel value in µm when passing images to ImageJ.
         * @param pixelSizeMicrons
         * @return this builder
         */
        public Builder pixelSizeMicrons(double pixelSizeMicrons) {
            return downsample(DownsampleCalculators.pixelSizeMicrons(pixelSizeMicrons));
        }

        /**
         * Resize images to have a target pixel value when passing images to ImageJ.
         * @param targetCalibration
         * @return this builder
         */
        public Builder pixelSize(PixelCalibration targetCalibration) {
            return downsample(DownsampleCalculators.pixelSize(targetCalibration));
        }

        /**
         * Specify how images should be downsampled when passing them to ImageJ.
         * @param downsample
         * @return this builder
         */
        public Builder downsample(DownsampleCalculator downsample) {
            params.downsample = downsample;
            return this;
        }

        /**
         * Specify the number of parallel threads to use.
         * This value is only used if a {@link TaskRunner} has not be provided.
         * @param nThreads
         * @return this builder
         * @see #taskRunner(TaskRunner) 
         */
        public Builder nThreads(int nThreads) {
            params.nThreads = nThreads;
            return this;
        }

        /**
         * Provide an optional task runner.
         * This can be used to show a progress dialog or log output.
         * @param taskRunner
         * @return this builder
         * @see #taskRunner(TaskRunner)
         */
        public Builder taskRunner(TaskRunner taskRunner) {
            params.taskRunner = taskRunner;
            return this;
        }

        /**
         * Optionally specify a subset of image channels to pass to ImageJ.
         * @param inds channel indices (zero-based)
         * @return this builder
         */
        public Builder channelIndices(int... inds) {
            return channels(
                    IntStream.of(inds).mapToObj(ColorTransforms::createChannelExtractor).toList()
            );
        }

        /**
         * Optionally specify a subset of image channels to pass to ImageJ, based on channel names.
         * @param names channel names
         * @return this builder
         */
        public Builder channelNames(String... names) {
            return channels(
                    Arrays.stream(names).map(ColorTransforms::createChannelExtractor).toList()
            );
        }

        /**
         * Optionally specify channels to pass to ImageJ.
         * @param channel the first channel to use
         * @param channels any additional channels
         * @return this builder
         */
        public Builder channels(ColorTransforms.ColorTransform channel, ColorTransforms.ColorTransform... channels) {
            var list = new ArrayList<ColorTransforms.ColorTransform>();
            list.add(channel);
            Collections.addAll(list, channels);
            return channels(list);
        }

        /**
         * Optionally specify channels to pass to ImageJ.
         * @param channels the channels to use
         * @return this builder
         */
        public Builder channels(Collection<? extends ColorTransforms.ColorTransform> channels) {
            params.channels = channels == null ? null : List.copyOf(channels);
            return this;
        }

        /**
         * Build a new {@link ImageJScriptRunner} with the parameters specified in this builder.
         * @return
         */
        public ImageJScriptRunner build() {
            return fromParams(params);
        }

    }

    /**
     * Create a new builder for an instance of {@link ImageJScriptRunner}.
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new builder for an instance of {@link ImageJScriptRunner}, initializing using the provided parameters.
     * @return
     */
    public static Builder builder(ImageJScriptParameters params) {
        return new Builder(params);
    }

}
