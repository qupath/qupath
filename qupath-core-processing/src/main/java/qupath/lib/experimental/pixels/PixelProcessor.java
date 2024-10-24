/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.experimental.pixels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.utils.ObjectMerger;
import qupath.lib.objects.utils.ObjectProcessor;
import qupath.lib.objects.utils.Tiler;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.TaskRunner;
import qupath.lib.plugins.TaskRunnerUtils;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A class for tiled image processing.
 * <p>
 *     The aim of this class is to make it easy to write pixel-based processing algorithms using a variety
 *     of different image representations, and also to run these in parallel.
 * </p>
 * <p>
 *     There are four main components:
 *     <ul>
 *         <li>An {@link ImageSupplier} that provides the image data</li>
 *         <li>A {@link MaskSupplier} that can generate binary masks that correspond to the image</li>
 *         <li>A {@link Processor} that processes the image data</li>
 *         <li>An {@link OutputHandler} that handles the output of the {@link Processor}</li>
 *     </ul>
 *     The idea is that the {@link Processor} is the only component that needs to be written for a specific task...
 *     and it is usually the fun bit to work on.
 * <p>
 *     The other components are often very tedious to write and hard to get working correctly; also, there is much
 *     overlap in the functionality required for different tasks.
 *     For these reasons, we provide several default implementations here, written to support different image
 *     representations.
 * </p>
 *
 * @param <S> The type of image to process
 * @param <T> The type of mask to use; often the same as <code>S</code>
 * @param <U> The type of output to generate
 * @since v0.5.0
 */
public class PixelProcessor<S, T, U> {

    private static final Logger logger = LoggerFactory.getLogger(PixelProcessor.class);

    private final ImageSupplier<S> imageSupplier;
    private final MaskSupplier<S, T> maskSupplier;
    private final OutputHandler<S, T, U> outputHandler;
    private final Processor<S, T, U> processor;

    private final Padding padding;
    private final DownsampleCalculator downsampleCalculator;

    private final Tiler tiler;
    private final ObjectProcessor objectProcessor;

    private PixelProcessor(ImageSupplier<S> imageSupplier,
                          MaskSupplier<S, T> maskSupplier,
                          OutputHandler<S, T, U> outputHandler,
                          Processor<S, T, U> processor,
                          Tiler tiler,
                          ObjectProcessor objectProcessor,
                          Padding padding,
                          DownsampleCalculator downsampleCalculator) {
        Objects.requireNonNull(imageSupplier, "Image supplier cannot be null");
        Objects.requireNonNull(processor, "Processor cannot be null");
        if (downsampleCalculator == null)
            throw new IllegalArgumentException("Downsample must be specified");
        this.imageSupplier = imageSupplier;
        this.maskSupplier = maskSupplier;
        this.outputHandler = outputHandler;
        this.processor = processor;
        this.tiler = tiler;
        this.objectProcessor = objectProcessor;
        this.padding = padding;
        this.downsampleCalculator = downsampleCalculator;
    }

    /**
     * Process objects using the default {@link TaskRunner}.
     * @param imageData
     * @param pathObjects
     */
    public void processObjects(ImageData<BufferedImage> imageData, Collection<? extends PathObject> pathObjects) {
        processObjects(TaskRunnerUtils.getDefaultInstance().createTaskRunner(), imageData, pathObjects);
    }

    /**
     * Process objects using the specified {@link TaskRunner}.
     * @param runner
     * @param imageData
     * @param pathObjects
     */
    public void processObjects(TaskRunner runner, ImageData<BufferedImage> imageData, Collection<? extends PathObject> pathObjects) {
        if (tiler != null) {
            processTiled(runner, tiler, imageData, pathObjects);
        } else {
            processUntiled(runner, imageData, pathObjects);
        }
    }

    /**
     * Process objects without tiling. This is the 'easy' case where we just run tasks, and the output handler does the
     * rest.
     * @param runner
     * @param imageData
     * @param pathObjects
     */
    private void processUntiled(TaskRunner runner, ImageData<BufferedImage> imageData, Collection<? extends PathObject> pathObjects) {
        List<? extends Runnable> tasks = pathObjects.stream()
                .distinct()
                .map(pathObject -> new ProcessorTask(imageData, pathObject, processor, null))
                .toList();
        runner.runTasks(tasks);
    }


    /**
     * Process objects using tiling, then merge the detected objects across tiles.
     * @param runner
     * @param tiler
     * @param imageData
     * @param pathObjects
     */
    private void processTiled(TaskRunner runner, Tiler tiler, ImageData<BufferedImage> imageData, Collection<? extends PathObject> pathObjects) {
        if (tiler == null)
            throw new IllegalStateException("Tiler must be specified for tiled processing");
        List<ProcessorTask> tasks = new ArrayList<>();
        Map<PathObject, List<PathObject>> tempObjects = new LinkedHashMap<>();
        for (var pathObject : pathObjects) {
            // Skip duplicates
            if (tempObjects.containsKey(pathObject))
                continue;
            // Create temp objects for each tile
            List<PathObject> proxyList = new ArrayList<>();
            if (pathObject.isRootObject()) {
                // Root object means do everything
                var server = imageData.getServer();
                for (int t = 0; t < server.nTimepoints(); t++) {
                    for (int z = 0; z < server.nZSlices(); z++) {
                        var roi = ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getPlane(t, z));
                        proxyList.addAll(tiler.createAnnotations(roi));
                    }
                }
            } else {
                proxyList.addAll(tiler.createAnnotations(pathObject.getROI()));
            }
            tempObjects.put(pathObject, proxyList);
            for (var proxy : proxyList)
                tasks.add(new ProcessorTask(imageData, pathObject, processor, proxy));
        }
        // Run the tasks
        String message = tasks.size() == 1 ? "Processing 1 tile" : "Processing " + tasks.size() + " tiles";
        runner.runTasks(message, tasks);

        // Reassign the proxy objects to the parent
        // If merging is involved, this can be slow - so pass these as new tasks
        if (runner.isCancelled() || Thread.interrupted()) {
            logger.warn("Tiled processing cancelled before merging");
            return;
        }
        List<Runnable> mergeTasks = new ArrayList<>();
        for (var entry : tempObjects.entrySet()) {
            // Get the original parent object
            var pathObject = entry.getKey();
            // Get all new objects detected from the tile
            var proxyList = entry.getValue().stream()
                    .flatMap(proxy -> proxy.getChildObjects().stream())
                    .toList();
            if (objectProcessor != null) {
                // Use the merger if we have one
                mergeTasks.add(() -> postprocessObjects(objectProcessor, pathObject, proxyList));
            } else {
                // Just add the new objects if we have no merger
                pathObject.clearChildObjects();
                pathObject.addChildObjects(proxyList);
                pathObject.setLocked(true);
            }
        }
        if (!mergeTasks.isEmpty())
           runner.runTasks("Post-processing", mergeTasks);
    }

    /**
     * Postprocess the child objects and add them to the parent.
     * @param objectProcessor
     * @param parent
     * @param childObjects
     */
    private static void postprocessObjects(ObjectProcessor objectProcessor, PathObject parent, List<PathObject> childObjects) {
        var toAdd = objectProcessor.process(childObjects);
        parent.clearChildObjects();
        parent.addChildObjects(toAdd);
        parent.setLocked(true);
    }



    private class ProcessorTask implements PathTask {

        private static final Logger logger = LoggerFactory.getLogger(PixelProcessor.class);

        private final ImageData<BufferedImage> imageData;
        private final PathObject pathObject;
        private final PathObject parentProxy;
        private final Processor<S, T, U> processor;

        private ProcessorTask(ImageData<BufferedImage> imageData, PathObject pathObject, Processor<S, T, U> processor,
                              PathObject parentProxy) {
            this.imageData = imageData;
            this.pathObject = pathObject;
            this.processor = processor;
            this.parentProxy = parentProxy;
        }

        @Override
        public void run() {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    logger.trace("Thread interrupted - skipping task for {}", pathObject);
                    return;
                }
                // Use the proxy object, if available, otherwise use the path object
                RegionRequest request;
                if (parentProxy != null) {
                    request = createRequest(imageData.getServer(), parentProxy);
                } else {
                    request = createRequest(imageData.getServer(), pathObject);
                }
                Parameters.Builder<S, T> builder = Parameters.builder();
                Parameters<S, T> params = builder.imageData(imageData)
                        .imageFunction(imageSupplier)
                        .maskFunction(maskSupplier)
                        .region(request)
                        .parent(pathObject)
                        .parentProxy(parentProxy)
                        .build();
                var output = processor.process(params);
                if (outputHandler != null)
                    outputHandler.handleOutput(params, output);
            } catch (Exception e) {
                logger.error("Error processing object", e);
            }
        }

        protected RegionRequest createRequest(ImageServer<?> server, PathObject pathObject) {
            double downsample = downsampleCalculator.getDownsample(server.getPixelCalibration());
            return RegionRequest.createInstance(server.getPath(), downsample, pathObject.getROI())
                    .pad2D(padding)
                    .intersect2D(0, 0, server.getWidth(), server.getHeight());
        }

        @Override
        public void taskComplete(boolean wasCancelled) {
            PathTask.super.taskComplete(wasCancelled);
        }

        @Override
        public String getLastResultsDescription() {
            return "Completed " + pathObject;
        }
    }

    /**
     * Create a new builder to construct a {@link PixelProcessor}.
     * @return
     * @param <S> the image type
     * @param <T> the mask type
     * @param <U> the output type
     */
    public static <S, T, U> Builder<S, T, U> builder() {
        return new Builder<>();
    }

    /**
     * Builder class for a {@link PixelProcessor}
     * @param <S> the image type
     * @param <T> the mask type
     * @param <U> the output type
     */
    public static class Builder<S, T, U> {

        private ImageSupplier<S> imageSupplier;
        private MaskSupplier<S, T> maskSupplier;
        private OutputHandler<S, T, U> outputHandler;
        private Processor<S, T, U> processor;

        private Tiler tiler;
        private ObjectProcessor objectProcessor;
        private Padding padding = Padding.empty();
        private DownsampleCalculator downsampleCalculator = DownsampleCalculator.createForDownsample(1.0);

        /**
         * Set the image supplier. This is required if the processor is to have access to pixels.
         * @param imageSupplier
         * @return
         */
        public Builder<S, T, U> imageSupplier(ImageSupplier<S> imageSupplier) {
            this.imageSupplier = imageSupplier;
            return this;
        }

        /**
         * Set the mask supplier. This is optional, but without it masks will be null.
         * @param maskSupplier
         * @return
         */
        public Builder<S, T, U> maskSupplier(MaskSupplier<S, T> maskSupplier) {
            this.maskSupplier = maskSupplier;
            return this;
        }

        /**
         * Set the output handler. This is optional, for cases where the processor does not make updates to the
         * parent object itself.
         * @param outputHandler
         * @return
         */
        public Builder<S, T, U> outputHandler(OutputHandler<S, T, U> outputHandler) {
            this.outputHandler = outputHandler;
            return this;
        }

        /**
         * Set the processor. This is required to do any interesting work.
         * @param processor
         * @return
         */
        public Builder<S, T, U> processor(Processor<S, T, U> processor) {
            this.processor = processor;
            return this;
        }

        /**
         * Set the padding to use when extracting regions.
         * Note that this is defined in pixels at the full image resolution, not the downsampled resolution.
         * @param padding
         * @return
         */
        public Builder<S, T, U> padding(Padding padding) {
            this.padding = padding;
            return this;
        }

        /**
         * Set the padding to use when extracting regions, using a symmetric padding.
         * Note that this is defined in pixels at the full image resolution, not the downsampled resolution.
         * @param size
         * @return
         */
        public Builder<S, T, U> padding(int size) {
            this.padding = size <= 0 ? Padding.empty() : Padding.symmetric(size);
            return this;
        }

        /**
         * Set the downsample factor to use with requesting image regions.
         * @param downsample
         * @return
         */
        public Builder<S, T, U> downsample(double downsample) {
            if (downsample <= 0)
                throw new IllegalArgumentException("Downsample must be > 0!");
            this.downsampleCalculator = DownsampleCalculator.createForDownsample(downsample);
            return this;
        }

        /**
         * Set the requested pixel size to use with requesting image regions.
         * This will be converted to a downsample factor based on the image calibration.
         * @param pixelSize
         * @return
         */
        public Builder<S, T, U> pixelSize(double pixelSize) {
            if (pixelSize <= 0)
                throw new IllegalArgumentException("Requested pixel size must be > 0!");
            this.downsampleCalculator = DownsampleCalculator.createForRequestedPixelSize(pixelSize);
            return this;
        }

        /**
         * Set a tiler to use. This is required for large regions, so that the image can be processed in tiles.
         * @param tiler
         * @return
         */
        public Builder<S, T, U> tiler(Tiler tiler) {
            this.tiler = tiler;
            return this;
        }

        /**
         * Set a default tiler to use, with a specified tile size.
         * @param tileWidth
         * @param tileHeight
         * @return
         */
        public Builder<S, T, U> tile(int tileWidth, int tileHeight) {
            return tiler(Tiler.builder(tileWidth, tileHeight)
                    .alignCenter()
                    .filterByCentroid(false)
                    .cropTiles(false)
                    .build());
        }

        /**
         * Set a merger to use. This is currently only relevant when using a tiler.
         * @param merger
         * @return
         * @see #mergeSharedBoundaries(double)
         * @deprecated v0.6.0, use {@link #postProcess(ObjectProcessor)} instead
         */
        @Deprecated
        public Builder<S, T, U> merger(ObjectMerger merger) {
            return postProcess(merger);
        }

        /**
         * Set an object post-processor to apply to any objects created when using a tiler.
         * This may be handle overlaps, e.g. by merging or clipping.
         * @param objectProcessor
         * @return
         */
        public Builder<S, T, U> postProcess(ObjectProcessor objectProcessor) {
            this.objectProcessor = objectProcessor;
            return this;
        }

        /**
         * Convenience method to set a merger that merges objects based on their shared boundary.
         * @param threshold the shared boundary threshold; see {@link ObjectMerger#createSharedTileBoundaryMerger(double)}
         *                  for more information.
         * @return
         * @see #merger(ObjectMerger)
         */
        public Builder<S, T, U> mergeSharedBoundaries(double threshold) {
            return postProcess(ObjectMerger.createSharedTileBoundaryMerger(threshold));
        }

        /**
         * Build a {@link PixelProcessor} from the current state of the builder.
         * This will throw an exception if any of the required components are missing.
         * @return
         */
        public PixelProcessor<S, T, U> build() {
            return new PixelProcessor<>(imageSupplier, maskSupplier, outputHandler, processor,
                    tiler, objectProcessor, padding, downsampleCalculator);
        }


    }

    private static class DownsampleCalculator {

        private final boolean isRequestedPixelSize;
        private final double resolution;

        private DownsampleCalculator(double resolution, boolean isRequestedPixelSize) {
            this.isRequestedPixelSize = isRequestedPixelSize;
            this.resolution = resolution;
        }

        private static DownsampleCalculator createForDownsample(double downsample) {
            return new DownsampleCalculator(downsample, false);
        }

        private static DownsampleCalculator createForRequestedPixelSize(double pixelSize) {
            return new DownsampleCalculator(pixelSize, true);
        }

        public double getDownsample(PixelCalibration cal) {
            if (isRequestedPixelSize)
                return resolution / cal.getAveragedPixelSize().doubleValue();
            else
                return resolution;
        }

    }

}
