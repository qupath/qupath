package qupath.lib.pixels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.plugins.CommandLinePluginRunner;
import qupath.lib.plugins.PathTask;
import qupath.lib.plugins.PluginRunner;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * An abstract class for processing pixels corresponding to an image tile.
 * <p>
 * The aim of this class is to make it easy to write pixel-based processing algorithms using a variety
 * of different image representations, and also to run these in parallel.
 *
 * @param <S> The type of image to process
 * @param <T> The type of mask to use
 * @param <U> The type of output to generate
 * @since v0.5.0
 */
public abstract class PixelProcessor<S, T, U> {

    private final Processor<S, T, U> processor;

    private int padX = 0;
    private int padY = 0;
    private double downsample = 1.0;

    public PixelProcessor(Processor<S, T, U> processor) {
        this.processor = processor;
    }

    public void processObjects(PluginRunner runner, ImageData<BufferedImage> imageData, Collection<? extends PathObject> pathObjects) {
        if (runner == null)
            runner = new CommandLinePluginRunner();
        List<? extends Runnable> tasks = pathObjects.stream()
                .map(pathObject -> new ProcessorTask(imageData, pathObject, processor))
                .toList();
        runner.runTasks(tasks);
    }

    /**
     * Get the image to process.
     * @param imageData
     * @param pathObject
     * @return
     * @throws IOException
     */
    protected abstract S getImage(ImageData<BufferedImage> imageData, RegionRequest request, PathObject pathObject) throws IOException;

    /**
     * Get a mask corresponding to the image, depicting the ROI of the path object.
     * @param imageData
     * @param pathObject
     * @param image
     * @return
     */
    protected abstract T getMask(ImageData<BufferedImage> imageData, RegionRequest request, PathObject pathObject, S image);

    /**
     * Optional method to handle the output of the processor.
     * For example, this might assign classifications, add measurements, or add child objects.
     * @param imageData the image data
     * @param request the region request
     * @param pathObject the parent object
     * @param image the image tile
     * @param output the generated output
     */
    protected abstract void handleOutput(ImageData<BufferedImage> imageData, RegionRequest request, PathObject pathObject, S image, U output);


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
                var image = getImage(imageData, request, pathObject);
                var mask = getMask(imageData, request, pathObject, image);
                var output = processor.process(imageData, pathObject, image, mask);
                handleOutput(imageData, request, pathObject, image, output);
            } catch (Exception e) {
                logger.error("Error processing object", e);
            }
        }

        protected RegionRequest createRequest(ImageServer<?> server, PathObject pathObject) {
            return RegionRequest.createInstance(server.getPath(), downsample, pathObject.getROI()).pad2D(padX, padY);
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


    @FunctionalInterface
    public interface Processor<S, T, U> {

        U process(ImageData<BufferedImage> imageData, PathObject parent, S image, T mask);

    }

}
