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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;
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
 * </p>
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

    private final ImageSupplier<S> imageSupplier;
    private final MaskSupplier<S, T> maskSupplier;
    private final OutputHandler<S, T, U> outputHandler;
    private final Processor<S, T, U> processor;

    private final Padding padding;
    private final double downsample;

    private PixelProcessor(ImageSupplier<S> imageSupplier,
                          MaskSupplier<S, T> maskSupplier,
                          OutputHandler<S, T, U> outputHandler,
                          Processor<S, T, U> processor,
                          Padding padding,
                          double downsample) {
        Objects.requireNonNull(imageSupplier, "Image supplier cannot be null");
        Objects.requireNonNull(processor, "Processor cannot be null");
        if (downsample <= 0)
            throw new IllegalArgumentException("Downsample must be > 0");
        this.imageSupplier = imageSupplier;
        this.maskSupplier = maskSupplier;
        this.outputHandler = outputHandler;
        this.processor = processor;
        this.padding = padding;
        this.downsample = downsample;
    }

    public void processObjects(ImageData<BufferedImage> imageData, Collection<? extends PathObject> pathObjects) {
        processObjects(new CommandLinePluginRunner(), imageData, pathObjects);
    }

    public void processObjects(PluginRunner runner, ImageData<BufferedImage> imageData, Collection<? extends PathObject> pathObjects) {
        List<? extends Runnable> tasks = pathObjects.stream()
                .map(pathObject -> new ProcessorTask(imageData, pathObject, processor))
                .toList();
        runner.runTasks(tasks);
    }

    private class ProcessorTask implements PathTask {

        private static Logger logger = LoggerFactory.getLogger(PixelProcessor.class);

        private final ImageData<BufferedImage> imageData;
        private final PathObject pathObject;
        private final Processor<S, T, U> processor;

        private ProcessorTask(ImageData<BufferedImage> imageData, PathObject pathObject, Processor<S, T, U> processor) {
            this.imageData = imageData;
            this.pathObject = pathObject;
            this.processor = processor;
        }

        @Override
        public void run() {
            try {
                var request = createRequest(imageData.getServer(), pathObject);
                Parameters.Builder<S, T> builder = Parameters.builder();
                Parameters<S, T> params = builder.imageData(imageData)
                        .imageFunction(imageSupplier)
                        .maskFunction(maskSupplier)
                        .region(request)
                        .parent(pathObject)
                        .build();
                var output = processor.process(params);
                if (outputHandler != null)
                    outputHandler.handleOutput(params, output);
            } catch (Exception e) {
                logger.error("Error processing object", e);
            }
        }

        protected RegionRequest createRequest(ImageServer<?> server, PathObject pathObject) {
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

        private Padding padding = Padding.empty();
        private double downsample = 1.0;

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
            this.downsample = downsample;
            return this;
        }

        /**
         * Build a {@link PixelProcessor} from the current state of the builder.
         * This will throw an exception if any of the required components are missing.
         * @return
         */
        public PixelProcessor<S, T, U> build() {
            return new PixelProcessor<>(imageSupplier, maskSupplier, outputHandler, processor,
                    padding, downsample);
        }


    }


}
