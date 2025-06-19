/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 - 2025 QuPath developers, The University of Edinburgh
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

package qupath.imagej.gui.scripts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.macro.Interpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.tools.IJProperties;
import qupath.lib.images.servers.WrappedBufferedImageServer;
import qupath.lib.images.servers.downsamples.DownsampleCalculator;
import qupath.lib.images.servers.downsamples.DownsampleCalculators;
import qupath.imagej.tools.IJTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.TaskRunner;
import qupath.lib.plugins.TaskRunnerUtils;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.LoggingTools;
import qupath.lib.scripting.QP;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * Enum representing the objects that the script should be applied to.
     */
    public enum ApplyToObjects {
        SELECTED, IMAGE, ANNOTATIONS, DETECTIONS, TILES, CELLS, TMA_CORES;

        @Override
        public String toString() {
            return switch(this) {
                case IMAGE -> "Whole image";
                case SELECTED -> "Selected objects";
                case ANNOTATIONS -> "All annotations";
                case DETECTIONS -> "All detections";
                case TILES -> "All tiles";
                case CELLS -> "All cells";
                case TMA_CORES -> "All TMA cores";
            };
        }
    }

    private final ImageJScriptParameters params;

    private transient ScriptEngineManager scriptEngineManager;

    private static final Writer writer = LoggingTools.createLogWriter(logger, Level.INFO);
    private static final Writer errorWriter = LoggingTools.createLogWriter(logger, Level.ERROR);

    private final Map<Thread, ScriptEngine> engineMap = new WeakHashMap<>();

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
        var toProcess = getObjectsToProcess(imageData.getHierarchy());
        if (toProcess.isEmpty()) {
            return;
        }
        // Use the selected object, if it's compatible - otherwise use the first
        var selected = imageData.getHierarchy().getSelectionModel().getSelectedObject();
        if (!toProcess.contains(selected))
            selected = toProcess.getFirst();
        run(imageData, selected, true);
    }

    private void run(final ImageData<BufferedImage> imageData, final Collection<? extends PathObject> pathObjects) {
        if (pathObjects.isEmpty()) {
            logger.warn("No objects found for script (requested {})", params.applyToObjects);
            return;
        }

        var taskRunner = params.getTaskRunner();

        int[] idsBefore = WindowManager.getIDList();

        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        for (var parent : pathObjects) {
            tasks.add(() -> {
                if (run(imageData, parent, false))
                    successCount.incrementAndGet();
                else
                    failCount.incrementAndGet();
            });
        }
        taskRunner.runTasks("ImageJ scripts", tasks);

        if (params.doAddToWorkflow() && successCount.get() > 0) {
            addScriptToWorkflow(imageData, pathObjects);
        }

        int[] idsAfter = WindowManager.getIDList();
        int nBefore = idsBefore == null ? 0 : idsBefore.length;
        int nAfter = idsAfter == null ? 0 : idsAfter.length;
        if (nBefore != nAfter) {
            logger.warn("Number of ImageJ images open before: {}, images open after: {}", nBefore, nAfter);
            if (params.doCloseOpenImages()) {
                int nClosed = closeNewImages(idsBefore, idsAfter);
                if (nClosed > 0)
                    logger.debug("Closed {} ImageJ images", nClosed);
            }
        }
        FXUtils.runOnApplicationThread(() -> imageData.getHierarchy().fireHierarchyChangedEvent(this));
        engineMap.clear();

        if (failCount.get() > 0) {
            Dialogs.showErrorMessage("ImageJ script runner",
                    failCount.get() + "/" + tasks.size() + " tasks failed - see log for details");
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
        if (idsAfter == null)
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
        return getObjectsToProcess(hierarchy, params.getApplyToObjects());
    }


    /**
     * Query which objects in a hierarchy would be used with the specified {@link ApplyToObjects} value.
     * @param hierarchy
     * @param applyTo
     * @return a list of all objects that are compatible with the type
     */
    public static List<PathObject> getObjectsToProcess(PathObjectHierarchy hierarchy, ApplyToObjects applyTo) {
        if (applyTo == null || hierarchy == null)
            return Collections.emptyList();
        return switch (applyTo) {
            case IMAGE -> List.of(hierarchy.getRootObject());
            case ANNOTATIONS -> List.copyOf(hierarchy.getAnnotationObjects());
            case DETECTIONS -> List.copyOf(hierarchy.getDetectionObjects());
            case CELLS -> List.copyOf(hierarchy.getCellObjects());
            case TILES -> List.copyOf(hierarchy.getTileObjects());
            // TODO: Consider whether to filter out missing cores (or this can be done by passing selected objects)
            case TMA_CORES -> hierarchy.getTMAGrid() == null ? List.of() : List.copyOf(hierarchy.getTMAGrid().getTMACoreList());
            case SELECTED -> {
                // We want the main selected object to be first, so that it is the one used during testing
                var selected = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
                var mainSelected = hierarchy.getSelectionModel().getSelectedObject();
                if (mainSelected != null && !(!selected.isEmpty() && selected.getFirst() == mainSelected)) {
                    selected.remove(mainSelected);
                    selected.addFirst(mainSelected);
                }
                yield selected;
            }
        };
    }

    private boolean run(final ImageData<BufferedImage> imageData, final PathObject pathObject, boolean isTest) {

        if (imageData == null)
            throw new IllegalArgumentException("No image data available");

        // Don't try if interrupted
        if (Thread.currentThread().isInterrupted()) {
            logger.warn("Skipping macro for {} - thread interrupted", pathObject);
            return false;
        }

        // Extract parameters
        ROI pathROI = pathObject.getROI();

        ImageServer<BufferedImage> server = getServer(imageData);
        var request = createRequest(server, pathObject);
        if (request.getDownsample() <= 0) {
            logger.warn("Downsample must be > 0, but it was {}", request.getDownsample());
            return false;
        }

        // Check the size of the region to extract - abort if it is too large of if there isn't enough RAM
        try {
            int width = (int)Math.round(request.getWidth() / request.getDownsample());
            int height = (int)Math.round(request.getHeight() / request.getDownsample());
            String propName = "qupath.imagej.scripts.maxDim";
            int maxDim = Integer.parseInt(System.getProperty(propName, "10000"));
            if (width > maxDim || height > maxDim) {
                logger.error("Image would be {} x {} after downsampling, but max supported dimension is {}",
                        width, height, maxDim);
                logger.debug("Use System.setProperty(\"{}\", \"value\") to increase this value", propName);
                return false;
            }
            // Memory checks are expensive, so don't do them unless we have a big region
            double approxPixels = (double)width * height * server.nChannels();
            if (approxPixels > 4096 * 4096 * 8) {
                IJTools.isMemorySufficient(request, imageData);
            }
        } catch (Exception e1) {
            logger.error("Unable to process image: {}", e1.getMessage(), e1);
            return false;
        }

        // Maintain a map of Rois we sent, so that we don't create unnecessary duplicate objects
        Map<UUID, PathObject> sentObjects = new ConcurrentHashMap<>();

        ImagePlus imp;
        try {
            // Get hyperstack if passing the full image
            imp = extractImage(server, request,
                    params.activeRoiObjectType == PathObjectType.NONE && pathObject.isRootObject());
            // Create variable to store which Roi should be active on the image
            // If adding an overlay, we want to ensure the same Roi is reused
            Roi roiToSelect = null;
            // Create an overlay, if needed
            // Note that an overlay is always used for cells, because it is the only way to get boundary and nucleus
            // rois to both be passed
            var overlay = new Overlay();
            Set<PathObject> overlayObjects = new LinkedHashSet<>();
            // Add the selected object first
            if (params.doSetRoi() && pathObject.hasROI()) {
                overlayObjects.add(pathObject);
            }
            // Add the other objects from the region, if required
            if (params.doSetOverlay()) {
                imageData.getHierarchy().getAllObjectsForRegion(request, overlayObjects);
            }
            int count = 0;
            for (var temp : overlayObjects) {
                if (sentObjects.put(temp.getID(), temp) != null)
                    logger.warn("Duplicate object ID found: {}", temp.getID());
                List<Roi> tempRois;
                if (temp == pathObject && params.doSetRoi()) {
                    // Don't modify the main Roi name with a positive count value
                    // Do store it for activating on the image later
                    tempRois = createRois(temp, request, -1);
                    roiToSelect = tempRois.getFirst();
                } else {
                    tempRois = createRois(temp, request, ++count);
                }
                for (var roi : tempRois) {
                    overlay.add(roi);
                }
            }

            imp.setOverlay(overlay);
            // Add the main Roi, if needed
            imp.setRoi(roiToSelect);
        } catch (IOException e) {
            logger.error("Unable to extract image region {}", request, e);
            return false;
        }

        // Set some useful properties
        try {
            IJProperties.setImageBackground(imp, imageData.getImageType());
            IJProperties.setImageType(imp, imageData.getImageType());
            IJProperties.setImageRegion(imp, request);
            IJProperties.setRegionRequest(imp, request);
        } catch (Exception e) {
            logger.warn("Exception setting properties: {}", e.getMessage());
            logger.debug(e.getMessage(), e);
        }

        // Actually run the macro or script
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
                        imp.setProperty("qupath.imageData", imageData);
                        imp.setProperty("qupath.pathObject", pathObject);
                        imp.setProperty("qupath.request", request);
                        if (isTest)
                            imp.show();
                        engine.eval(script);
                    } catch (Exception e) {
                        Dialogs.showErrorNotification("ImageJ script", e);
                        Thread.currentThread().interrupt();
                        return false;
                    } finally {
                        imp.setProperty("qupath.imageData", null);
                        imp.setProperty("qupath.pathObject", null);
                        imp.setProperty("qupath.request", null);
                    }
                } else {
                    // ImageJ macro
                    Interpreter interpreter = new Interpreter();

                    if (isTest) {
                        imp.show();
                        interpreter.run(script);
                    } else {
                        // The error messages via AWT are really obtrusive - it's better to ignore them
                        // and log them for the user
                        interpreter.setIgnoreErrors(true);
                        // Running a batch macro can sometimes fail due to
                        // Cannot invoke "java.util.Vector.add(Object)" because "ij.macro.Interpreter.imageActivations" is null
                        // which seems to be due to the use of static methods and variables.
                        if (params.nThreads == 1) {
                            impResult = interpreter.runBatchMacro(script, imp);
                        } else {
                            // This appears to work more reliably with multithreading - UNLESS images are duplicated...
                            // in which case, I can't find any way to make multithreading reliable
                            WindowManager.setTempCurrentImage(imp);
                            interpreter.run(script, "");
                            impResult = WindowManager.getTempCurrentImage();
                            WindowManager.setTempCurrentImage(null);
                        }
                    }

                    // If we had an error, return
                    var errorMessage = interpreter.getErrorMessage();
                    if (interpreter.wasError() || errorMessage != null) {
                        Thread.currentThread().interrupt();
                        if (isTest) {
                            Dialogs.showErrorMessage("ImageJ", "Script failed at line " + interpreter.getLineNumber()
                                    + "\n" + errorMessage);
                        } else {
                            logger.error("Error running script: {}", errorMessage);
                        }
                        return false;
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
                return false;


            // Get the current image when the macro has finished - which may or may not be the same as the original
            if (impResult == null)
                impResult = imp;

            if (params.doRemoveChildObjects()) {
                pathObject.clearChildObjects();
            }
            var activeRoiToObject = params.getActiveRoiToObjectFunction();
            // If we set the ROI in ImageJ, use it to mask what we get back
            var maskROI = params.doSetRoi() ? pathROI : null;
            if (activeRoiToObject != null && impResult.getRoi() != null) {
                Roi roi = impResult.getRoi();
                var id = IJProperties.getObjectId(roi);
                var existingObject = id == null ? null : sentObjects.getOrDefault(id, null);
                if (existingObject == null) {
                    var pathObjectNew = createNewObject(activeRoiToObject, roi,  request, maskROI);
                    // Add an object if it isn't already there
                    if (pathObjectNew != null && !PathObjectTools.hierarchyContainsObject(imageData.getHierarchy(), pathObjectNew)) {
                        pathObject.addChildObject(pathObjectNew);
                        pathObject.setLocked(true); // Lock if we add anything
                    }
                } else {
                    IJTools.calibrateObject(existingObject, roi);
                }
            }

            var overlayRoiToObject = params.getOverlayRoiToObjectFunction();
            if (overlayRoiToObject != null && impResult.getOverlay() != null) {
                var overlay = impResult.getOverlay();
                var childObjects = Arrays.stream(overlay.toArray())
                        .map(r -> createOrUpdateObject(overlayRoiToObject, r, request, maskROI, sentObjects))
                        .filter(Objects::nonNull)
                        .toList();
                if (!childObjects.isEmpty()) {
                    pathObject.addChildObjects(childObjects);
                    pathObject.setLocked(true); // Lock if we add anything
                }
            }
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }

    }

    private RegionRequest createRequest(ImageServer<?> server, PathObject parent) {
        var pathROI = parent.getROI();
        ImageRegion fullImage = RegionRequest.createInstance(server);
        ImageRegion region = pathROI == null ? fullImage : ImageRegion.createInstance(pathROI);
        double downsampleFactor = params.getDownsample().getDownsample(server, region);
        var request = RegionRequest.createInstance(server.getPath(), downsampleFactor, region);
        // Only apply padding for ROIs
        if (!region.equals(fullImage) && params.getPadding() > 0) {
            int expand = (int)Math.round(params.getPadding() * downsampleFactor);
            request = request.pad2D(expand, expand);
        }
        // Make sure that the region is contained within the image
        return request.intersect2D(fullImage);
    }

    private static ImagePlus extractImage(ImageServer<BufferedImage> server, RegionRequest request, boolean requestHyperstack) throws IOException {
        if (requestHyperstack)
            return IJTools.extractHyperstack(server, request);
        else
            return IJTools.convertToImagePlus(server, request).getImage();
    }

    private static List<Roi> createRois(PathObject pathObject, RegionRequest request, int count) {
        var roi = IJTools.convertToIJRoi(pathObject.getROI(), request);
        String defaultName = PathObjectTools.getSuitableName(pathObject.getClass(), false);
        if (count > 0)
            defaultName += " (" + count + ")";
        roi.setName(defaultName);
        IJTools.calibrateRoi(roi, pathObject);
        if (pathObject instanceof PathCellObject cell) {
            var nucleusRoi = cell.getNucleusROI();
            String key = "qupath.object.type";
            roi.setProperty(key, "cell");
            if (nucleusRoi != null) {
                var roi2 = IJTools.convertToIJRoi(nucleusRoi, request);
                roi2.setName(defaultName);
                IJTools.calibrateRoi(roi2, pathObject);
                roi2.setName(roi2.getName() + "-nucleus");
                roi2.setProperty(key, "cell.nucleus");
                return List.of(roi, roi2);
            }
        }
        return Collections.singletonList(roi);
    }


    private ScriptEngine getScriptEngine() {
        if (params.scriptEngine == null ||
                params.scriptEngine.equalsIgnoreCase(ENGINE_NAME_MACRO) ||
                params.scriptEngine.equalsIgnoreCase("imagej"))
            return null;
        var engine = engineMap.computeIfAbsent(
                Thread.currentThread(), t -> getScriptEngineManager().getEngineByName(params.scriptEngine));
        if (engine != null) {
            var context = new SimpleScriptContext();
            context.setWriter(writer);
            context.setErrorWriter(errorWriter);
            engine.setContext(context);
        }
        return engine;
    }

    private ScriptEngineManager getScriptEngineManager() {
        if (scriptEngineManager != null)
            return scriptEngineManager;
        synchronized (this) {
            if (scriptEngineManager == null)
                scriptEngineManager = new ScriptEngineManager();
            return scriptEngineManager;
        }
    }



    private void addScriptToWorkflow(ImageData<?> imageData, Collection<? extends PathObject> parents) {
        var sb = new StringBuilder();
        if (!parents.isEmpty()) {
            if (parents.stream().allMatch(PathObject::isAnnotation)) {
                sb.append("// selectAnnotations()\n");
            } else if (parents.stream().allMatch(PathObject::isTile)) {
                sb.append("// selectTiles()\n");
            } else if (parents.stream().allMatch(PathObject::isTMACore)) {
                sb.append("// selectTMACores()\n");
            } else if (parents.stream().allMatch(PathObject::isCell)) {
                sb.append("// selectCells()\n");
            } else if (parents.stream().allMatch(PathObject::isDetection)) {
                sb.append("// selectDetections()\n");
            }
        }
        var gson = GsonTools.getInstance();
        var json = gson.toJson(params);
        var obj = gson.fromJson(json, JsonObject.class);
        var map = gson.fromJson(json, Map.class);

        sb.append(ImageJScriptRunner.class.getName()).append(".fromMap(\n");
        var groovyMap = toGroovy(obj);
        if (groovyMap.startsWith("[") && groovyMap.endsWith("]")) {
            groovyMap = groovyMap.substring(1, groovyMap.length()-1).strip();
        }
        if (!groovyMap.isEmpty()) {
            sb.append("  ");
            sb.append(groovyMap);
            sb.append("\n");
        }
        sb.append(").run()");

        var workflowScript = sb.toString();
        imageData.getHistoryWorkflow().addStep(
                new DefaultScriptableWorkflowStep("ImageJ script", map, workflowScript)
        );

        logger.debug(sb.toString());
    }

    private static String toGroovy(JsonElement element) {
        var sb = new StringBuilder();
        appendValue(sb, element, 1);
        return sb.toString();
    }


    /**
     * Example program to log a script.
     * @param args
     */
    public static void main(String[] args) {
        try {
            var server = new WrappedBufferedImageServer(
                    "Anything",
                    new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB));
            var imageData = new ImageData<>(server);
            imageData.setImageType(ImageData.ImageType.BRIGHTFIELD_H_E);
            new ImageJScriptRunner.Builder()
                    .text("""
                            print("Hello?");
                            print("Are you here?);
                            """)
                    .channels(
                            ColorTransforms.createChannelExtractor(0),
                            ColorTransforms.createChannelExtractor("Green"),
                            ColorTransforms.createColorDeconvolvedChannel(imageData.getColorDeconvolutionStains(), 1))
                    .build().addScriptToWorkflow(imageData, List.of());
            if (imageData.getHistoryWorkflow().getLastStep() instanceof DefaultScriptableWorkflowStep step) {
                logger.info(step.getScript());
            }
        } catch (Exception e) {
            logger.error("Error running ImageJScriptRunner", e);
        }
    }


    private static String appendValue(StringBuilder sb, JsonElement val, int indent) {
        switch (val) {
            case JsonPrimitive primitive -> {
                if (primitive.isString()) {
                    String str = primitive.getAsString();
                    String quote = "\"";
                    if (str.contains(quote)) {
                        if (str.contains("\"\"\"")) {
                            logger.warn("Triple-quotes found in script text - this will not be properly handled");
                        }
                        int sinceLastNewline = Math.max(1, sb.length() - 1 - Math.max(sb.lastIndexOf("\n"), 0));
                        quote = "\"\"\"";
                        // For long, multi-line strings it's more readable to include in triple quotes and indent
                        if (str.contains("\n")) {
                            if (!str.startsWith("\n"))
                                str = "\n" + str;
                            if (!str.endsWith("\n"))
                                str += "\n";
                            str = str.replace("\n", "\n" + " ".repeat(sinceLastNewline));
                        }
                    }
                    sb.append(quote).append(str).append(quote);
                } else
                    sb.append(primitive.getAsString());
            }
            case JsonArray array -> {
                sb.append("[");
                boolean isFirst = true;
                boolean isIndented = array.size() > 1 && indent > 0;
                for (int i = 0; i < array.size(); i++) {
                    if (!isFirst) {
                        sb.append(", ");
                    }
                    if (isIndented) {
                        sb.append("\n");
                        sb.append(" ".repeat(indent * 2));
                    }
                    // Indent 0 (keep on same row)
                    appendValue(sb, array.get(i), indent+1);
                    isFirst = false;
                }
                if (isIndented) {
                    sb.append("\n");
                    sb.append(" ".repeat(indent * 2));
                }
                sb.append("]");
            }
            case JsonObject obj -> {
                sb.append("[");
                String newlineAndIndent = indent <= 0 || obj.size() <= 1 ? "" : System.lineSeparator() + " ".repeat(indent * 2);
                sb.append(newlineAndIndent);
                boolean isFirst = true;
                for (var entry : obj.asMap().entrySet()) {
                    if (!isFirst) {
                        sb.append(", ");
                        sb.append(newlineAndIndent);
                    }
                    sb.append(entry.getKey()).append(": ");
                    appendValue(sb, entry.getValue(), indent+1);
                    isFirst = false;
                }
                sb.append(newlineAndIndent);
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

    private static PathObject createOrUpdateObject(Function<ROI, PathObject> creator, Roi roi, RegionRequest request,
                                                   ROI clipROI, Map<UUID, PathObject> existingObjects) {
        var existing = existingObjects.getOrDefault(IJProperties.getObjectId(roi), null);
        if (existing == null)
            return createNewObject(creator, roi, request, clipROI);
        else {
            synchronized (existing) {
                IJTools.calibrateObject(existing, roi);
                return null;
            }
        }
    }

    private static PathObject createNewObject(Function<ROI, PathObject> creator, Roi roi, RegionRequest request, ROI clipROI) {
        ROI newROI = IJTools.convertToROI(roi, request);
        if (RoiTools.isShapeROI(clipROI) && RoiTools.isShapeROI(newROI)) {
            newROI = RoiTools.combineROIs(clipROI, newROI, RoiTools.CombineOp.INTERSECT);
        } else if (RoiTools.isShapeROI(clipROI) && newROI != null && newROI.isPoint()) {
            newROI = ROIs.createPointsROI(
                    newROI.getAllPoints()
                            .stream()
                            .filter(p -> clipROI.contains(p.getX(), p.getY()))
                            .toList()
                    , newROI.getImagePlane());
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
        private int padding = 0;
        private boolean setRoi = true;
        private boolean setOverlay = false;
        private boolean closeOpenImages = false;

        // Result parameters
        private boolean clearChildObjects = false;
        private PathObjectType activeRoiObjectType = null;
        private PathObjectType overlayRoiObjectType = null;

        private ApplyToObjects applyToObjects = ApplyToObjects.SELECTED;

        private String scriptEngine;

        private boolean addToWorkflow = false;

        private int nThreads = -1;
        private transient TaskRunner taskRunner;

        private ImageJScriptParameters() {}

        private ImageJScriptParameters(ImageJScriptParameters params) {
            // Store null since then it'll be skipped with json serialization
            channels = params.channels == null || params.channels.isEmpty() ? null : List.copyOf(params.channels);
            text = params.text;
            downsample = params.downsample;
            padding = params.padding;
            setRoi = params.setRoi;
            setOverlay = params.setOverlay;
            closeOpenImages = params.closeOpenImages;
            clearChildObjects = params.clearChildObjects;
            activeRoiObjectType = params.activeRoiObjectType;
            overlayRoiObjectType = params.overlayRoiObjectType;
            applyToObjects = params.applyToObjects;
            scriptEngine = params.scriptEngine;
            addToWorkflow = params.addToWorkflow;
            nThreads = params.nThreads;
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
         * Get the padding to add around the ROI.
         * @return
         */
        public int getPadding() {
            return padding;
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
         * Request that any lingering open images are closed after the script has run.
         * @return
         */
        public boolean doCloseOpenImages() {
            return closeOpenImages;
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
         * Get the objects to which this script should be applied.
         * By default this is {@link ApplyToObjects#SELECTED}, but it is possible to request that
         * the script is always applied to objects of a specific type.
         * @return
         */
        public ApplyToObjects getApplyToObjects() {
            return applyToObjects;
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
        private static final Map<String, String> scriptEngineNameCache = new HashMap<>();

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
            return text(text);
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
         * Specify the objects that the script should be applied to.
         * @param objectType
         * @return this builder
         */
        public Builder applyToObjects(ApplyToObjects objectType) {
            params.applyToObjects = objectType;
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
         * Specify how much padding to add around the ROI.
         * @param padding number of pixels of padding to add (should be &geq; 0)
         * @return this builder
         */
        public Builder padding(int padding) {
            params.padding = padding;
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
